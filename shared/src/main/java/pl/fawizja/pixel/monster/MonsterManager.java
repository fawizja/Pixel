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

    /** Wypelnia wszystkie spawny (start swiata). */
    public void populate(Position avoid) {
        for (SpawnPoint sp : spawns) {
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
            Monster m = new Monster(nextId++, registry.get(sp.monsterKey()), sp, p);
            m.setNextActionMs(now + 500 + rng.nextInt(2000));
            monsters.add(m);
            grid.add(m);
            return true;
        }
        return false;
    }

    public List<Monster> monsters() { return Collections.unmodifiableList(monsters); }
    public SpatialGrid grid() { return grid; }
    public List<SpawnPoint> spawns() { return Collections.unmodifiableList(spawns); }

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
    public boolean canOccupy(Position p, Position player) {
        if (!world.isWalkable(p) || world.isProtectionZone(p)) return false;
        if (player != null && player.equals(p)) return false;
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

    /** Usuwa zabitego potwora i planuje odrodzenie. */
    public void kill(Monster m, long now) {
        monsters.remove(m);
        grid.remove(m, m.position());
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
        for (Map.Entry<SpawnPoint, List<Long>> e : respawnQueue.entrySet()) {
            List<Long> times = e.getValue();
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

    private boolean validTarget(Monster m, Target t) {
        Position tp = t.position();
        return t.attackable() && tp.z() == m.position().z() && !world.isProtectionZone(tp)
                && tp.distanceTo(m.position()) <= ACTIVE_RADIUS;
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
            Monster m = new Monster(nextId++, registry.get(sp.monsterKey()), sp, p);
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
        int aggro = t.aggroRange() + (nightBonus ? NIGHT_AGGRO_BONUS : 0);
        boolean canSee = dist <= aggro + 6 && world.hasLineOfSight(mp, tp);
        boolean targetValid = target.attackable() && tp.z() == mp.z() && !world.isProtectionZone(tp);
        boolean tooFarFromHome = mp.distanceTo(m.spawn().center()) > m.spawn().radius() + LEASH
                || mp.z() != m.spawn().center().z();

        // wybor stanu
        switch (m.state()) {
            case IDLE:
                if (targetValid && dist <= aggro && canSee) m.setState(MonsterState.CHASE);
                break;
            case CHASE:
            case FLEE:
                if (!targetValid || !canSee || tooFarFromHome) m.setState(MonsterState.RETURN);
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
            case CHASE: step = chaseStep(m, tp, dist, canSee); break;
            case FLEE:
                step = fleeStep(m, tp);
                if (step == null) {
                    // nie ma dokad uciec: walczy dalej
                    m.setCornered(true);
                    m.setState(MonsterState.CHASE);
                    step = chaseStep(m, tp, dist, canSee);
                }
                break;
            case RETURN: step = Pathfinder.nextStep(world, mp, m.spawn().center(), Math.max(0, m.spawn().radius() - 1), 600,
                    blockedFor(tp)); break;
            case IDLE:
                // regeneracja poza walka: 5% zdrowia na kazdy "oddech" bezczynnosci
                if (m.health() < t.maxHealth()) m.heal(Math.max(1, t.maxHealth() / 20));
                if (rng.nextInt(3) == 0) step = Direction.values()[rng.nextInt(Direction.values().length)];
                m.setNextActionMs(now + 1500 + rng.nextInt(2500));
                break;
            default:
        }
        if (step != null) tryStep(m, step, now, tp);
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
            return Pathfinder.nextStep(world, m.position(), tp, Math.max(1, t.range() - 1), 400, blockedFor(tp));
        }
        if (dist <= 1) return null;
        return Pathfinder.nextStep(world, m.position(), tp, 1, 400, blockedFor(tp));
    }

    private Direction fleeStep(Monster m, Position tp) {
        Direction best = null;
        int bestDist = m.position().distanceTo(tp);
        for (Direction d : Direction.values()) {
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
