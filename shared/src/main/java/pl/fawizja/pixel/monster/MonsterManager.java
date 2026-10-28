package pl.fawizja.pixel.monster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.movement.Pathfinder;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.Transition;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Potwory w swiecie: spawny, odradzanie, siatka przestrzenna i AI (aggro, poscig,
 * ucieczka, utrzymywanie dystansu, leczenie sojusznikow, powrot do spawnu).
 * Aktualizowane sa tylko potwory blisko postaci - reszta czeka bez kosztu CPU.
 */
public final class MonsterManager {

    /** Promien aktywnej symulacji wokol gracza. */
    public static final int ACTIVE_RADIUS = 20;
    /** Dodatkowy dystans od spawnu, po ktorym potwor rezygnuje z poscigu. */
    public static final int LEASH = 10;
    /** Najkrotszy zasieg wzroku: potwor widzi mniej wiecej caly ekran przed soba (jak w Tibii). */
    public static final int MIN_SIGHT = 7;
    /** Najdalej, skad potwor zauwazy gracza (zasieg ekranu w poziomie + 2). */
    public static final int MAX_SIGHT = 9;
    /** Jak dlugo potwor goni gracza, ktory zniknal mu z oczu (idzie tam, gdzie go ostatnio widzial). */
    public static final long MEMORY_MS = 5_000;

    /** Callback do walki - wywolywany, gdy potwor atakuje gracza. */
    public interface AttackHandler {
        void monsterAttacks(Monster m, long now);
        void monsterHeals(Monster healer, Monster target, int amount, long now);
    }

    /** Minimalny widok gracza potrzebny AI (w trybie online - wielu graczy). */
    public interface Target {
        Position position();
        boolean attackable();
    }

    private final WorldMap world;
    private final MonsterRegistry registry;
    private final List<SpawnPoint> spawns;
    private final Random rng;
    private final List<Monster> monsters = new ArrayList<>();
    private final SpatialGrid grid = new SpatialGrid();
    private final Map<SpawnPoint, List<Long>> respawnQueue = new HashMap<>();
    private final List<Monster> scratch = new ArrayList<>();
    private int nextId = 1;
    private boolean night;

    /** Dodatkowy zasieg aggro nocnych drapieznikow w nocy (powierzchnia). */
    public static final int NIGHT_AGGRO_BONUS = 3;

    public MonsterManager(WorldMap world, MonsterRegistry registry, List<SpawnPoint> spawns, Random rng) {
        this.world = world;
        this.registry = registry;
        this.spawns = new ArrayList<>(spawns);
        this.rng = rng;
    }

    /** Spawny jednorazowe (wydarzenia): potwory z nich sie nie odradzaja. */
    private final java.util.Set<SpawnPoint> eventSpawns = new java.util.HashSet<>();

    /** Wydarzenie: count potworow wokol center (bez odradzania). Zwraca utworzone potwory. */
    public List<Monster> spawnEvent(String monsterKey, Position center, int radius, int count, long now) {
        SpawnPoint sp = new SpawnPoint(monsterKey, center, radius, count, 1);
        eventSpawns.add(sp);
        List<Monster> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (spawnOne(sp, (Position) null, now)) out.add(monsters.get(monsters.size() - 1));
        }
        return out;
    }

    /** Koniec wydarzenia: usuwa jego pozostale potwory. */
    public void clearEvent(java.util.Collection<Integer> ids) {
        for (Monster m : new ArrayList<>(monsters)) {
            if (ids.contains(m.id()) && eventSpawns.contains(m.spawn())) {
                monsters.remove(m);
                grid.remove(m, m.position());
            }
        }
    }

    /** Wypelnia wszystkie spawny (start swiata). */
    public void populate(Position avoid) {
        for (SpawnPoint sp : spawns) {
            if (sp.nightOnly()) continue; // nocne pojawiaja sie o zmroku (update)
            for (int i = 0; i < sp.count(); i++) spawnOne(sp, avoid, 0L);
        }
    }

    private boolean spawnOne(SpawnPoint sp, Position avoid, long now) {
        for (int attempt = 0; attempt < 30; attempt++) {
            int dx = sp.radius() == 0 ? 0 : rng.nextInt(sp.radius() * 2 + 1) - sp.radius();
            int dy = sp.radius() == 0 ? 0 : rng.nextInt(sp.radius() * 2 + 1) - sp.radius();
            Position p = sp.center().translate(dx, dy);
            if (!canOccupy(p, avoid)) continue;
            if (avoid != null && p.distanceTo(avoid) <= 5) continue; // nie pojawiaj sie na oczach gracza
            Monster m = new Monster(nextId, spawnType(sp, nextId), sp, p);
            nextId++;
            m.setNextActionMs(now + 500 + rng.nextInt(2000));
            monsters.add(m);
            grid.add(m);
            return true;
        }
        return false;
    }

    /** Rodzaj z punktu spawnu; czasem (wg numeru potwora) odmiana elitarna. */
    private MonsterType spawnType(SpawnPoint sp, int id) {
        MonsterType t = registry.get(sp.monsterKey());
        return MonsterType.eliteRoll(id) ? t.eliteVariant() : t;
    }

    public List<Monster> monsters() { return Collections.unmodifiableList(monsters); }
    public SpatialGrid grid() { return grid; }
    public MonsterRegistry registry() { return registry; }
    public List<SpawnPoint> spawns() { return Collections.unmodifiableList(spawns); }

    /** Rodzaje potworow ze stalych spawnow (np. do zadan dnia); pusta lista bez rejestru. */
    public List<MonsterType> spawnedTypes() {
        if (registry == null) return new ArrayList<>();
        if (spawnedTypes == null) {
            // stale spawny sie nie zmieniaja: liczone raz (HUD pyta o nazwy potworow co klatke)
            java.util.Map<String, MonsterType> out = new java.util.TreeMap<>();
            for (SpawnPoint sp : spawns) out.putIfAbsent(sp.monsterKey(), registry.get(sp.monsterKey()));
            spawnedTypes = Collections.unmodifiableList(new ArrayList<>(out.values()));
        }
        return new ArrayList<>(spawnedTypes);
    }

    private List<MonsterType> spawnedTypes;

    /** Rodzaj potwora ze stalych spawnow po kluczu albo null (bez kopiowania listy). */
    public MonsterType spawnedType(String key) {
        if (registry == null) return null;
        if (spawnedTypes == null) spawnedTypes();
        for (MonsterType t : spawnedTypes) if (t.key().equals(key)) return t;
        return null;
    }

    /** Stan bossa ze stalego spawnu: zyje albo czas (ms zegara gry) najblizszego odrodzenia; respawnAt <= now = zaraz. */
    public record BossStatus(String name, Position where, String region, boolean alive, long respawnAt) {}

    public List<BossStatus> bossReport(long now) {
        List<BossStatus> out = new ArrayList<>();
        if (registry == null) return out;
        for (SpawnPoint sp : spawns) {
            MonsterType t;
            try {
                t = registry.get(sp.monsterKey());
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (t == null || !t.boss()) continue;
            boolean alive = false;
            for (Monster m : monsters) if (m.spawn() == sp && !m.isDead()) alive = true;
            long next = Long.MAX_VALUE;
            for (long at : respawnQueue.getOrDefault(sp, Collections.emptyList())) next = Math.min(next, at);
            pl.fawizja.pixel.world.Region r = world.regionAt(sp.center());
            out.add(new BossStatus(t.name(), sp.center(), r == null ? null : r.name(), alive, alive ? 0 : next));
        }
        out.sort(java.util.Comparator.comparing(BossStatus::name));
        return out;
    }

    /** Jedna linia raportu do dziennika, np. "Grimtooth (Kopalnia Varden, piętro -3): żyje". */
    public static String describe(BossStatus b, long now) {
        String[] r = row(b, now);
        return r[0] + " (" + r[1] + "): " + r[2];
    }

    /** {imie, miejsce, stan} do okna bossow (i komunikatu BOSS). */
    public static String[] row(BossStatus b, long now) {
        int z = b.where().z();
        String place = (b.region() == null ? "" : b.region() + ", ") + (z == 0 ? "powierzchnia" : "piętro " + (z > 0 ? "+" + z : String.valueOf(z)));
        String state;
        if (b.alive()) state = "żyje";
        else if (b.respawnAt() == Long.MAX_VALUE || b.respawnAt() <= now) state = "odradza się lada chwila";
        else {
            long min = (b.respawnAt() - now + 59_999) / 60_000;
            state = "odrodzi się za " + (min >= 60 ? min / 60 + " h " + min % 60 + " min" : min + " min");
        }
        return new String[] {b.name(), place, state};
    }

    public Monster monsterAt(Position p) { return grid.at(p); }

    /** Noc na powierzchni: nocne drapiezniki widza dalej i atakuja szybciej. */
    public void setNight(boolean night) { this.night = night; }
    public boolean isNight() { return night; }

    /** Czy potwor korzysta z premii nocnej (nocny, noc, pietro 0 lub wyzej). */
    public boolean nightBonus(Monster m) {
        return night && m.type().nocturnal() && m.position().z() >= 0;
    }

    public List<Monster> near(Position p, int radius) {
        return new ArrayList<>(grid.query(p, radius, scratch));
    }

    /** Czy potwor moze stanac na kafelku (bez graczy, innych potworow, stref ochronnych i przejsc). */
    /** Gracze z ostatniej aktualizacji - potwory nie wchodza na ich pola. */
    private List<? extends Target> bodies = Collections.emptyList();
    /** Pola graczy na czas jednego update (wielu graczy: zamiast petli po liscie przy kazdym polu sciezki). */
    private java.util.Set<Position> bodyCells;

    public boolean canOccupy(Position p, Position player) {
        if (!world.isWalkable(p) || world.isProtectionZone(p) || world.openings().isClosedDoor(p)) return false;
        if (player != null && player.equals(p)) return false;
        if (bodyCells != null) {
            if (bodyCells.contains(p)) return false;
        } else {
            for (Target b : bodies) if (p.equals(b.position())) return false; // inni gracze tez zajmuja pole
        }
        Transition t = world.transitionAt(p);
        if (t != null) return false;
        return grid.at(p) == null;
    }

    /** Najwyzszy zalecany poziom potworow na pietrze (ostrzezenia o niebezpiecznych obszarach). */
    public int maxMonsterLevelOnFloor(int z) {
        int max = 0;
        for (SpawnPoint sp : spawns) {
            if (sp.center().z() == z) max = Math.max(max, registry.get(sp.monsterKey()).level());
        }
        return max;
    }

    /** Najwyzszy poziom potworow na tym pietrze w promieniu (kafelki) od pozycji. */
    public int maxMonsterLevelNear(Position p, int radius) {
        int max = 0;
        for (SpawnPoint sp : spawns) {
            Position c = sp.center();
            if (c.z() == p.z() && c.distanceTo(p) <= radius + sp.radius()) {
                max = Math.max(max, registry.get(sp.monsterKey()).level());
            }
        }
        return max;
    }

    /**
     * Klient online: odwzorowuje potwory z klatki stanu serwera (id, typ, pozycja, % zdrowia).
     * Brakujace w klatce znikaja, nowe pojawiaja sie, przesuniete ida plynnie przez stepMs.
     */
    public void syncRemote(List<pl.fawizja.pixel.net.RemoteEntity> seen, long now, long stepMs) {
        Map<Integer, Monster> byId = new HashMap<>();
        for (Monster m : monsters) byId.put(m.id(), m);
        java.util.Set<Integer> keep = new java.util.HashSet<>();
        for (pl.fawizja.pixel.net.RemoteEntity e : seen) {
            if (!registry.contains(e.key())) continue;
            keep.add(e.id());
            Monster m = byId.get(e.id());
            if (m == null) {
                MonsterType type = registry.get(e.key());
                if ("E".equals(e.mark())) type = type.eliteVariant(); // serwer oznacza elitarne
                m = new Monster(e.id(), type, new SpawnPoint(e.key(), e.position(), 0, 1, 60), e.position());
                monsters.add(m);
                grid.add(m);
            } else if (!m.position().equals(e.position())) {
                Position from = m.position();
                Direction d = Direction.fromDelta(Integer.signum(e.position().x() - from.x()), Integer.signum(e.position().y() - from.y()));
                if (from.distanceTo(e.position()) > 1 || from.z() != e.position().z() || d == null) m.placeAt(e.position());
                else m.moveTo(e.position(), d, now, stepMs);
                grid.moved(m, from);
            }
            m.setHealthPercent(e.healthPercent());
        }
        for (Monster m : new ArrayList<>(monsters)) {
            if (!keep.contains(m.id())) {
                monsters.remove(m);
                grid.remove(m, m.position());
            }
        }
    }

    /** Usuwa zabitego potwora i planuje odrodzenie. */
    public void kill(Monster m, long now) {
        monsters.remove(m);
        grid.remove(m, m.position());
        if (eventSpawns.contains(m.spawn())) return;
        respawnQueue.computeIfAbsent(m.spawn(), k -> new ArrayList<>()).add(now + m.spawn().respawnSeconds() * 1000L);
    }

    /** Potwor trafiony z daleka (czar, runa) przestaje stac bezczynnie i rusza na gracza. */
    public void provoke(Monster m) {
        if (!m.isDead() && m.state() == MonsterState.IDLE) m.setState(MonsterState.CHASE);
    }

    /** Zwalnia kafelek (np. po teleporcie gracza): potwor wraca do kolejki odrodzen bez lupu. */
    public void evict(Position p, long now) {
        Monster m = grid.at(p);
        if (m == null) return;
        monsters.remove(m);
        grid.remove(m, p);
        if (eventSpawns.contains(m.spawn())) return;
        respawnQueue.computeIfAbsent(m.spawn(), k -> new ArrayList<>()).add(now);
    }

    public void update(long now, Target target, AttackHandler handler) {
        update(now, Collections.singletonList(target), handler);
    }

    /**
     * Krok AI dla wielu graczy (serwer): kazdy potwor w promieniu ACTIVE_RADIUS od ktoregokolwiek gracza
     * jest aktualizowany raz i poluje na najblizszego widocznego gracza (trzyma sie obecnego celu,
     * dopoki ten jest poprawny). Atak trafia do handlera; cel jest w {@link Monster#target()}.
     */
    public void update(long now, List<? extends Target> targets, AttackHandler handler) {
        bodies = targets;
        if (targets.size() > 4) {
            bodyCells = new java.util.HashSet<>();
            for (Target tg : targets) bodyCells.add(tg.position());
        }
        try {
            updateMonsters(now, targets, handler);
        } finally {
            bodyCells = null;
        }
    }

    private void updateMonsters(long now, List<? extends Target> targets, AttackHandler handler) {
        updateNightSpawns(now, targets);
        for (Map.Entry<SpawnPoint, List<Long>> e : respawnQueue.entrySet()) {
            List<Long> times = e.getValue();
            if (e.getKey().nightOnly() && !night) continue;
            for (int i = times.size() - 1; i >= 0; i--) {
                if (times.get(i) <= now && spawnOne(e.getKey(), targets, now)) times.remove(i);
            }
        }
        java.util.LinkedHashSet<Monster> active = new java.util.LinkedHashSet<>();
        for (Target tg : targets) active.addAll(grid.query(tg.position(), ACTIVE_RADIUS, scratch));
        for (Monster m : new ArrayList<>(active)) {
            if (m.isDead()) continue;
            Target tg = chooseTarget(m, targets);
            if (tg != null) think(m, now, tg, handler);
        }
    }

    /** Dystans, z ktorego gracz widzi potwora - nocne potwory nie znikaja na jego oczach. */
    public static final int DESPAWN_SIGHT = 9;
    private boolean nightSpawnsUp;
    /** Za dnia: czy moga jeszcze byc nocne potwory do usuniecia (pomija petle, gdy juz ich nie ma). */
    private boolean nightLeftovers;

    /**
     * O zmroku nocne spawny dostaja pelna obsade (pojawia sie stopniowo, poza zasiegiem wzroku graczy),
     * za dnia ich potwory znikaja, gdy nie walcza i nikt ich nie widzi; zabite w nocy nie odradzaja sie za dnia.
     */
    private void updateNightSpawns(long now, List<? extends Target> targets) {
        if (night && !nightSpawnsUp) {
            nightSpawnsUp = true;
            Map<SpawnPoint, Integer> alive = new HashMap<>();
            for (Monster m : monsters) if (m.spawn().nightOnly()) alive.merge(m.spawn(), 1, Integer::sum);
            for (SpawnPoint sp : spawns) {
                if (!sp.nightOnly()) continue;
                List<Long> q = respawnQueue.computeIfAbsent(sp, k -> new ArrayList<>());
                q.clear();
                for (int i = alive.getOrDefault(sp, 0); i < sp.count(); i++) q.add(now);
            }
        } else if (!night && nightSpawnsUp) {
            nightSpawnsUp = false;
            for (SpawnPoint sp : spawns) if (sp.nightOnly()) respawnQueue.remove(sp);
            nightLeftovers = true;
        }
        if (night || !nightLeftovers) return;
        boolean left = false;
        for (Monster m : new ArrayList<>(monsters)) {
            if (!m.spawn().nightOnly() || m.isDead()) continue;
            left = true;
            if (m.state() != MonsterState.IDLE && m.state() != MonsterState.RETURN) continue;
            boolean seen = false;
            for (Target t : targets) {
                if (t.position().z() == m.position().z() && t.position().distanceTo(m.position()) <= DESPAWN_SIGHT) { seen = true; break; }
            }
            if (seen) continue;
            monsters.remove(m);
            grid.remove(m, m.position());
        }
        nightLeftovers = left;
    }

    private boolean validTarget(Monster m, Target t) {
        Position tp = t.position();
        // najpierw tanie warunki (pietro, odleglosc): serwer sprawdza kazdego gracza dla kazdego aktywnego potwora
        return tp.z() == m.position().z() && tp.distanceTo(m.position()) <= ACTIVE_RADIUS
                && t.attackable() && !world.isProtectionZone(tp);
    }

    private Target chooseTarget(Monster m, List<? extends Target> targets) {
        Target current = m.target();
        if (current != null && targets.contains(current) && validTarget(m, current)
                && m.state() != MonsterState.IDLE) {
            return current;
        }
        Target best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Target t : targets) {
            if (!validTarget(m, t)) continue;
            int d = t.position().distanceTo(m.position());
            if (d < bestDist) { bestDist = d; best = t; }
        }
        if (best == null) {
            // nikogo w poblizu: potwor wraca do spawnu wzgledem dowolnego gracza z listy
            best = current != null && targets.contains(current) ? current : null;
            if (best == null) {
                for (Target t : targets) {
                    if (t.position().distanceTo(m.position()) <= ACTIVE_RADIUS) { best = t; break; }
                }
            }
        }
        m.setTarget(best);
        return best;
    }

    private boolean spawnOne(SpawnPoint sp, List<? extends Target> players, long now) {
        for (int attempt = 0; attempt < 30; attempt++) {
            int dx = sp.radius() == 0 ? 0 : rng.nextInt(sp.radius() * 2 + 1) - sp.radius();
            int dy = sp.radius() == 0 ? 0 : rng.nextInt(sp.radius() * 2 + 1) - sp.radius();
            Position p = sp.center().translate(dx, dy);
            boolean seen = false;
            for (Target t : players) {
                if (t.position().equals(p) || p.distanceTo(t.position()) <= 5) seen = true;
            }
            if (seen || !canOccupy(p, null)) continue;
            Monster m = new Monster(nextId, spawnType(sp, nextId), sp, p);
            nextId++;
            m.setNextActionMs(now + 500 + rng.nextInt(2000));
            monsters.add(m);
            grid.add(m);
            return true;
        }
        return false;
    }

    private void think(Monster m, long now, Target target, AttackHandler handler) {
        MonsterType t = m.type();
        Position tp = target.position();
        Position mp = m.position();
        int dist = mp.distanceTo(tp);
        boolean nightBonus = nightBonus(m);
        int sight = sightRange(t, nightBonus);
        boolean canSee = dist <= sight + 4 && world.hasLineOfSight(mp, tp);
        boolean targetValid = target.attackable() && tp.z() == mp.z() && !world.isProtectionZone(tp);
        if (targetValid && canSee) m.saw(tp, now);
        // poscig z pamieci: gracz schowal sie za rogiem - potwor idzie tam, gdzie go ostatnio widzial
        boolean remembers = !canSee && m.lastSeen() != null && now - m.lastSeenMs() < MEMORY_MS
                && !mp.equals(m.lastSeen());
        boolean tooFarFromHome = mp.distanceTo(m.spawn().center()) > m.spawn().radius() + LEASH
                || mp.z() != m.spawn().center().z();

        // wybor stanu
        switch (m.state()) {
            case IDLE:
                if (targetValid && canSee && notices(m, tp, sight)) m.setState(MonsterState.CHASE);
                break;
            case CHASE:
            case FLEE:
                if (!targetValid || (!canSee && !remembers) || tooFarFromHome) m.setState(MonsterState.RETURN);
                else if (!m.cornered() && t.fleeHealthPercent() > 0 && m.healthFraction() * 100 < t.fleeHealthPercent()) {
                    m.setState(MonsterState.FLEE);
                }
                break;
            case RETURN:
                if (mp.distanceTo(m.spawn().center()) <= m.spawn().radius()) {
                    m.heal(t.maxHealth());
                    m.setCornered(false);
                    m.setState(MonsterState.IDLE);
                }
                break;
            default:
        }

        // mechaniki bossa: jednorazowe leczenie ponizej 30%
        if (t.boss() && !m.healedOnce() && m.healthFraction() < 0.3f && t.healAmount() > 0) {
            int healed = m.heal(t.healAmount());
            m.markHealed();
            handler.monsterHeals(m, m, healed, now);
        }

        // atak lub leczenie
        if (m.state() == MonsterState.CHASE || m.state() == MonsterState.FLEE) {
            if (now >= m.nextAttackMs()) {
                int interval = t.attackIntervalMs();
                if (t.boss() && m.healthFraction() < 0.5f) interval = interval * 7 / 10; // szal
                if (nightBonus) interval = interval * 8 / 10;
                boolean acted = false;
                if (t.behavior() == MonsterBehavior.HEALER && t.healAmount() > 0) {
                    Monster ally = weakestAlly(m);
                    if (ally != null) {
                        handler.monsterHeals(m, ally, ally.heal(t.healAmount()), now);
                        acted = true;
                    }
                }
                if (!acted && dist <= t.range() && canSee && m.state() == MonsterState.CHASE) {
                    handler.monsterAttacks(m, now);
                    acted = true;
                }
                if (acted) m.setNextAttackMs(now + interval);
            }
        }

        // ruch
        if (m.isMoving(now) || now < m.nextActionMs()) return;
        Direction step = null;
        switch (m.state()) {
            case CHASE: step = canSee ? chaseStep(m, tp, dist, true)
                    : Pathfinder.nextStepOrthogonalFirst(world, mp, m.lastSeen(), 0, 400, blockedFor(tp)); break;
            case FLEE:
                step = fleeStep(m, tp);
                if (step == null) {
                    // nie ma dokad uciec: walczy dalej
                    m.setCornered(true);
                    m.setState(MonsterState.CHASE);
                    step = chaseStep(m, tp, dist, canSee);
                }
                break;
            case RETURN: step = Pathfinder.nextStepOrthogonalFirst(world, mp, m.spawn().center(), Math.max(0, m.spawn().radius() - 1), 600,
                    blockedFor(tp)); break;
            case IDLE:
                // regeneracja poza walka: 5% zdrowia na kazdy "oddech" bezczynnosci
                if (m.health() < t.maxHealth()) m.heal(Math.max(1, t.maxHealth() / 20));
                if (rng.nextInt(3) == 0) step = Direction.values()[rng.nextInt(4)]; // spacer bez skosow
                else if (rng.nextInt(2) == 0) m.face(Direction.values()[rng.nextInt(4)]); // rozglada sie
                m.setNextActionMs(now + 1500 + rng.nextInt(2500));
                break;
            default:
        }
        if (step != null) tryStep(m, step, now, tp);
    }

    /**
     * Zasieg wzroku przed soba: zasieg aggro z danych + 2, od MIN_SIGHT do MAX_SIGHT (jak w Tibii: potwory widza
     * gracza z 7-9 pol, w pionie takze spoza ekranu 15x11); noca drapiezniki dalej, ale nie ponad MAX_SIGHT.
     */
    static int sightRange(MonsterType t, boolean nightBonus) {
        int base = Math.min(MAX_SIGHT, Math.max(MIN_SIGHT, t.aggroRange() + 2));
        return Math.min(MAX_SIGHT, base + (nightBonus ? NIGHT_AGGRO_BONUS : 0));
    }

    /**
     * Pole widzenia: przed soba (polplaszczyzna w strone, w ktora patrzy) pelny zasieg wzroku, za plecami o 2 pola
     * mniej (co najmniej 5), a gracza tuz obok (2 pola) zawsze zauwazy.
     */
    static boolean notices(Monster m, Position tp, int sight) {
        Position mp = m.position();
        int dist = mp.distanceTo(tp);
        if (dist <= 2) return true;
        int dx = tp.x() - mp.x(), dy = tp.y() - mp.y();
        boolean ahead = dx * m.facing().dx + dy * m.facing().dy >= 0;
        return dist <= (ahead ? sight : Math.max(5, sight - 2));
    }

    private Predicate<Position> blockedFor(Position player) {
        return p -> !canOccupy(p, player);
    }

    private Direction chaseStep(Monster m, Position tp, int dist, boolean canSee) {
        MonsterType t = m.type();
        boolean keepsDistance = t.behavior() == MonsterBehavior.RANGED || t.behavior() == MonsterBehavior.MAGIC
                || t.behavior() == MonsterBehavior.HEALER;
        if (keepsDistance && t.range() > 1) {
            if (dist < 2) return fleeStep(m, tp);
            if (dist <= t.range() && canSee) return null;
            return Pathfinder.nextStepOrthogonalFirst(world, m.position(), tp, Math.max(1, t.range() - 1), 400, blockedFor(tp));
        }
        if (dist <= 1) return null;
        return Pathfinder.nextStepOrthogonalFirst(world, m.position(), tp, 1, 400, blockedFor(tp));
    }

    private Direction fleeStep(Monster m, Position tp) {
        Direction best = null;
        int bestDist = m.position().distanceTo(tp);
        // ucieczka tez najpierw prosto (N/S/E/W), skos dopiero gdy prosto nie oddala od gracza
        for (Direction d : Direction.values()) {
            if (d.diagonal() && best != null) break;
            Position n = m.position().translate(d.dx, d.dy);
            if (!canOccupy(n, tp) || !cornerFree(m.position(), d)) continue;
            int nd = n.distanceTo(tp);
            if (nd > bestDist) {
                bestDist = nd;
                best = d;
            }
        }
        return best;
    }

    private boolean cornerFree(Position from, Direction d) {
        return !d.diagonal() || (world.isWalkable(from.translate(d.dx, 0)) && world.isWalkable(from.translate(0, d.dy)));
    }

    private void tryStep(Monster m, Direction d, long now, Position player) {
        Position from = m.position();
        Position to = from.translate(d.dx, d.dy);
        if (!canOccupy(to, player) || !cornerFree(from, d)) return;
        if (m.state() == MonsterState.IDLE && to.distanceTo(m.spawn().center()) > m.spawn().radius()) return;
        m.moveTo(to, d, now, Balance.stepDurationMs(m.type().speed(), d.diagonal()));
        grid.moved(m, from);
    }

    private Monster weakestAlly(Monster healer) {
        Monster best = null;
        for (Monster o : grid.query(healer.position(), 4, scratch)) {
            if (o.isDead() || o.healthFraction() >= 0.7f) continue;
            if (best == null || o.healthFraction() < best.healthFraction()) best = o;
        }
        return best;
    }
}
