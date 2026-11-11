package pl.fawizja.pixel.magic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.combat.CombatEvent;
import pl.fawizja.pixel.combat.CombatSystem;
import pl.fawizja.pixel.combat.ConditionType;
import pl.fawizja.pixel.combat.Conditions;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.monster.SummonTable;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Fields;
import pl.fawizja.pixel.world.GroundItems;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Czary i runy jak w Tibii 15.x (0.240), ktorych nie bylo w podstawowym {@link SpellSystem}: fale (exevo flam hur),
 * stany w cel (utori), leczenie stanow (exana), leczenie innych (exura sio), regeneracja (utura), tarcza many
 * (utamo vita), niewidzialnosc (utana vid), iluzja (utevo res ina), sludzy (utevo res, runa przekonania, ozywienie
 * zwlok), wyzwanie (exeta res), wzmocnienia (utito tempo, utamo tempo, utamo tempo san, utito tempo san), lewitacja
 * (exani hur), magiczna lina (exani tera) i runy pol (pole, bomba, sciana, magiczna sciana, dziki wzrost, niszczenie
 * pol, ogien duszy, paraliz). Stan efektow w czasie (tiki na potworach, AI slug, wejscie potworow w pola) liczy
 * {@link #update(long)}.
 */
public final class TibiaSpells {

    private final SpellSystem sys;
    /** Stany na potworach (utori, ogien duszy, pola wlasciciela). */
    private final Map<Monster, Conditions> monsterConditions = new HashMap<>();
    /** Ostatnie pole (id), na ktorym stal potwor - pole rani przy wejsciu albo gdy powstanie pod nim. */
    private final Map<Monster, Long> lastField = new HashMap<>();
    private final List<Monster> summons = new ArrayList<>();
    private static final Object NONE = new Object();
    /** Ostatnie podsumowanie smierci widziane przez sludzy (nowe = postac zginela). */
    private Object lastDeathSeen = NONE;

    TibiaSpells(SpellSystem sys) { this.sys = sys; }

    private PlayerCharacter pc() { return sys.pc(); }
    private WorldMap world() { return sys.world(); }
    private CombatSystem combat() { return sys.combat(); }
    private MonsterManager monsters() { return sys.monsters(); }
    private EventLog log() { return sys.log(); }

    /** Sludzy postaci (zywi). */
    public List<Monster> summons() {
        summons.removeIf(m -> m.isDead() || monsters().monsterAt(m.position()) != m);
        return java.util.Collections.unmodifiableList(new ArrayList<>(summons));
    }

    /** Stany na potworze (do rysowania i testow); null, gdy brak. */
    public Conditions conditionsOf(Monster m) {
        Conditions c = monsterConditions.get(m);
        return c == null || !c.any() ? null : c;
    }

    // ------------------------------------------------------------------ rzut

    CastResult apply(SpellType s, long now) {
        switch (s.kind()) {
            case ATTACK_WAVE: return wave(s, now);
            case CONDITION_TARGET:
            case RUNE_CONDITION: return conditionTarget(s, now);
            case CURE:
            case RUNE_CURE: return cure(s, now);
            case HEAL_OTHER: return healOther(s, now);
            case REGENERATION:
                pc().startRegeneration((int) Math.round(s.base()), Math.max(1, s.produceCount()) * 1000L, s.range() * 1000L);
                effect(now, "HEAL");
                log().add(now, EventLog.Kind.INFO, s.name() + ": " + (int) s.base() + " HP co " + s.produceCount() + " s przez " + s.range() + " s.");
                return CastResult.OK;
            case MAGIC_SHIELD: {
                int cap = magicShieldCapacity(s, pc());
                pc().setMagicShield(cap, s.range() * 1000L);
                effect(now, "MAGIC");
                log().add(now, EventLog.Kind.INFO, "Tarcza many: " + cap + " punktów na " + s.range() / 60 + " min.");
                return CastResult.OK;
            }
            case INVISIBLE:
                pc().setInvisible(s.range() * 1000L);
                effect(now, "MAGIC");
                if (combat().target() != null) combat().setTarget(null);
                log().add(now, EventLog.Kind.INFO, "Jesteś niewidzialny dla potworów (" + s.range() + " s).");
                return CastResult.OK;
            case ILLUSION: return illusion(s, now);
            case SUMMON: return summon(s, now);
            case CHALLENGE: return challenge(s, now);
            case BUFF: return buff(s, now);
            case LEVITATE: {
                SpellSystem.Mover mv = sys.mover();
                if (mv == null) return CastResult.NOT_POSSIBLE;
                return mv.levitate(s.key().endsWith("_up"), now) ? CastResult.OK : CastResult.NO_ROOM;
            }
            case MAGIC_ROPE: {
                SpellSystem.Mover mv = sys.mover();
                return mv != null && mv.magicRope(now) ? CastResult.OK : CastResult.NOT_POSSIBLE;
            }
            case RUNE_FIELD:
            case RUNE_WALL: return fieldRune(s, now);
            case RUNE_BARRIER: return barrier(s, now);
            case RUNE_DESTROY_FIELD: {
                Position p = sys.aim(s);
                Fields.Field f = p == null ? null : world().fields().at(p);
                if (f == null || !f.kind().destroyable()) return CastResult.NOT_POSSIBLE;
                world().fields().remove(p);
                combat().addEvent(CombatEvent.projectile(now, pc().position(), p, "MAGIC"));
                combat().addEvent(CombatEvent.area(now, p, 0, "POOF"));
                return CastResult.OK;
            }
            case RUNE_PARALYSE: {
                Monster m = combat().target();
                if (m == null || m.isDead()) return CastResult.NO_TARGET;
                m.paralyze(now + s.radius() * 1000L);
                combat().addEvent(CombatEvent.projectile(now, pc().position(), m.position(), "MAGIC"));
                combat().addEvent(CombatEvent.area(now, m.position(), 0, "MAGIC"));
                monsters().provoke(m);
                log().add(now, EventLog.Kind.INFO, m.type().name() + " jest sparaliżowany (" + s.radius() + " s).");
                return CastResult.OK;
            }
            case RUNE_CONVINCE: return convince(s, now);
            case RUNE_ANIMATE: return animate(s, now);
            default:
                return CastResult.NOT_USABLE;
        }
    }

    private void effect(long now, String fx) {
        combat().addEvent(CombatEvent.area(now, pc().position(), 0, fx));
    }

    /** Pojemnosc tarczy many jak w Canary utamo vita: 300 + 7,6 x poziom + 7 x magia, najwyzej maks. many. */
    public static int magicShieldCapacity(SpellType s, PlayerCharacter pc) {
        double v = s.base() + s.levelFactor() * pc.level() + s.magicFactor() * pc.skills().level(Skill.MAGIC);
        return (int) Math.max(1, Math.min(pc.maxMana(), Math.round(v)));
    }

    // ------------------------------------------------------------------ fale

    /** Kierunek fali: wybrany cel (gdy lezy w zasiegu fali), inaczej kierunek patrzenia; zawsze prosty (N/E/S/W). */
    Direction waveDirection(SpellType s) {
        Position me = pc().position();
        Position t = null;
        Monster m = combat().target();
        if (m != null && !m.isDead()) t = m.position();
        else if (sys.playerTargetPos() != null) t = sys.playerTargetPos();
        if (t != null && t.z() == me.z() && me.distanceTo(t) <= s.radius() && !t.equals(me)) {
            int dx = t.x() - me.x(), dy = t.y() - me.y();
            if (Math.abs(dx) >= Math.abs(dy)) return dx > 0 ? Direction.EAST : Direction.WEST;
            return dy > 0 ? Direction.SOUTH : Direction.NORTH;
        }
        Direction d = sys.facing();
        if (d.diagonal()) return d.dx > 0 ? Direction.EAST : Direction.WEST;
        return d;
    }

    /**
     * Pola fali (stozek jak w Tibii): w odleglosci d od postaci polszerokosc max(ilosc, min(2, d/2)); dlugosc = promien.
     * Tylko pola w zasiegu wzroku.
     */
    public List<Position> waveTiles(SpellType s) {
        return waveTiles(pc().position(), waveDirection(s), s.radius() + pc().wheel().castAugment().extraRadius, s.produceCount(), world());
    }

    public static List<Position> waveTiles(Position me, Direction d, int length, int minHalf, WorldMap world) {
        List<Position> out = new ArrayList<>();
        int sx = -d.dy, sy = d.dx; // prostopadle
        for (int i = 1; i <= length; i++) {
            int half = Math.max(minHalf, Math.min(2, i / 2));
            for (int w = -half; w <= half; w++) {
                Position p = new Position(me.x() + d.dx * i + sx * w, me.y() + d.dy * i + sy * w, me.z());
                if (world == null || world.hasLineOfSight(me, p)) out.add(p);
            }
        }
        return out;
    }

    List<Monster> waveTargets(SpellType s) {
        List<Position> tiles = waveTiles(s);
        List<Monster> out = new ArrayList<>();
        for (Monster m : sys.targetsAround(pc().position(), s.radius() + 2)) if (tiles.contains(m.position())) out.add(m);
        return out;
    }

    boolean playerInWave(SpellType s) {
        if (sys.playerTargeting() == null) return false;
        Position p = sys.playerTargeting().position();
        return p != null && waveTiles(s).contains(p);
    }

    private CastResult wave(SpellType s, long now) {
        List<Position> tiles = waveTiles(s);
        for (Position p : tiles) combat().addEvent(CombatEvent.area(now, p, 0, s.damageType().name()));
        List<Monster> hit = waveTargets(s);
        boolean player = playerInWave(s);
        boolean crit = combat().rollCritical();
        int targets = Math.max(1, hit.size() + (player ? 1 : 0));
        for (Monster m : hit) {
            combat().damageMonster(m, pl.fawizja.pixel.wheel.WheelSpells.vsTarget(pc(), s, m, sys.rollValue(s)), s.damageType(), now, crit, targets);
        }
        if (player) {
            sys.playerTargeting().strike(pl.fawizja.pixel.combat.CriticalHit.apply(pc(), sys.rollValue(s), crit), s.damageType(), crit, targets, now);
        }
        return CastResult.OK;
    }

    // ------------------------------------------------------------------ stany

    /** Utori flam/vis/pox/kor/mort/san i runa ognia duszy: baza = obrazenia tiku, promien = liczba tikow, ilosc = odstep (s). */
    private CastResult conditionTarget(SpellType s, long now) {
        Monster m = combat().target();
        if (m == null || m.isDead()) return CastResult.NO_TARGET;
        ConditionType type = ConditionType.of(s.damageType());
        int perTick = (int) Math.max(1, Math.round(s.base() * pc().outgoingFactor()));
        Conditions c = monsterConditions.computeIfAbsent(m, k -> new Conditions());
        c.add(type, Conditions.constant(Math.max(1, s.radius()), perTick), Math.max(1, s.produceCount()) * 1000L, true, now, pc().name());
        combat().addEvent(CombatEvent.projectile(now, pc().position(), m.position(), s.damageType().name()));
        combat().addEvent(CombatEvent.area(now, m.position(), 0, s.damageType().name()));
        monsters().provoke(m);
        log().add(now, EventLog.Kind.INFO, m.type().name() + ": " + type.label.toLowerCase(Locale.ROOT) + " ("
                + s.radius() + " x " + perTick + ").");
        return CastResult.OK;
    }

    private CastResult cure(SpellType s, long now) {
        ConditionType type = ConditionType.of(s.damageType());
        if (!pc().conditions().remove(type)) return CastResult.NO_CONDITION;
        effect(now, "HEAL");
        log().add(now, EventLog.Kind.INFO, type.label + " ustępuje.");
        return CastResult.OK;
    }

    // ------------------------------------------------------------------ leczenie innych

    /**
     * Exura sio "imie" i podobne: czlonek druzyny o tym imieniu (albo najbardziej ranny w zasiegu), bez druzyny albo
     * z wlasnym imieniem - rzucajacy.
     */
    private CastResult healOther(SpellType s, long now) {
        String name = sys.param();
        SpellSystem.Ally best = null;
        Position me = pc().position();
        int lowest = Integer.MAX_VALUE;
        for (SpellSystem.Ally a : sys.allies()) {
            if (a.health() <= 0 || a.position().z() != me.z() || me.distanceTo(a.position()) > Math.max(1, s.range())) continue;
            if (name != null) {
                if (a.name().equalsIgnoreCase(name)) { best = a; break; }
                continue;
            }
            if (a.health() < lowest) { best = a; lowest = a.health(); }
        }
        int amount = sys.rollValue(s);
        if (best == null || name == null && best.health() >= pc().health() && pc().health() < pc().maxHealth()) {
            if (name != null && !name.equalsIgnoreCase(pc().name())) return CastResult.NO_TARGET;
            int healed = pc().heal(amount);
            combat().addEvent(CombatEvent.area(now, me, 0, "HEAL"));
            combat().addEvent(new CombatEvent(now, me, healed, CombatEvent.Kind.HEAL));
            return CastResult.OK;
        }
        int healed = best.heal(amount);
        combat().addEvent(CombatEvent.projectile(now, me, best.position(), "HOLY"));
        combat().addEvent(CombatEvent.area(now, best.position(), 0, "HEAL"));
        if (healed > 0) combat().addEvent(new CombatEvent(now, best.position(), healed, CombatEvent.Kind.HEAL));
        return CastResult.OK;
    }

    // ------------------------------------------------------------------ iluzja, sludzy, wyzwanie

    /** Typ potwora po nazwie z dopisku (polska nazwa albo klucz), bez bossow; null, gdy brak. */
    private MonsterType byName(String name, boolean summonableOnly) {
        if (name == null) return null;
        String n = plain(name);
        for (MonsterType t : monsters().registry().all()) {
            if (t.boss() || t.isElite()) continue;
            if (summonableOnly && !SummonTable.defaults().summonable(t.key())) continue;
            if (plain(t.name()).equals(n) || t.key().equalsIgnoreCase(n.replace(' ', '_'))) return t;
        }
        return null;
    }

    /** Nazwa bez wielkosci liter i polskich znakow ("Śnieżny wilk" = "sniezny wilk"). */
    static String plain(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT).replace('ł', 'l').replaceAll("\\s+", " ");
        return java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private CastResult illusion(SpellType s, long now) {
        MonsterType t = byName(sys.param(), false);
        if (t == null && sys.param() == null) {
            Monster m = combat().target();
            if (m != null && !m.isDead() && !m.type().boss()) t = m.type().base() != null ? m.type().base() : m.type();
        }
        if (t == null) {
            log().add(now, EventLog.Kind.WARNING, "Podaj nazwę stworzenia: utevo res ina \"nazwa\" (albo wybierz cel).");
            return CastResult.NOT_POSSIBLE;
        }
        pc().setIllusion(t.key(), s.range() * 1000L);
        effect(now, "MAGIC");
        log().add(now, EventLog.Kind.INFO, "Wyglądasz jak " + t.name() + " (" + s.range() / 60 + " min).");
        return CastResult.OK;
    }

    /**
     * Utevo res "stworzenie": sluga z data/summons.csv za mane stworzenia (najwyzej 2). Bez dopisku - najsilniejsze
     * stworzenie, na ktore starcza many.
     */
    private CastResult summon(SpellType s, long now) {
        if (summons().size() >= SummonTable.MAX_SUMMONS) return CastResult.TOO_MANY_SUMMONS;
        SummonTable table = SummonTable.defaults();
        MonsterType t;
        if (sys.param() != null) {
            t = byName(sys.param(), true);
            if (t == null) {
                log().add(now, EventLog.Kind.WARNING, "Nie możesz przywołać tego stworzenia.");
                return CastResult.NOT_POSSIBLE;
            }
        } else {
            t = null;
            for (Map.Entry<String, Integer> e : table.all().entrySet()) {
                if (e.getValue() > pc().mana() || !monsters().registry().contains(e.getKey())) continue;
                if (t == null || e.getValue() > table.mana(t.key())) t = monsters().registry().get(e.getKey());
            }
            if (t == null) return CastResult.NOT_ENOUGH_MANA;
        }
        int cost = table.mana(t.key());
        if (pc().mana() < cost) return CastResult.NOT_ENOUGH_MANA;
        Monster m = monsters().spawnSummon(t.key(), pc().position(), pc().name(), now);
        if (m == null) return CastResult.NO_ROOM;
        summons.add(m);
        lastDeathSeen = combat().lastDeath();
        sys.addExtraMana(cost);
        combat().addEvent(CombatEvent.area(now, m.position(), 0, "MAGIC"));
        log().add(now, EventLog.Kind.INFO, "Przywołano: " + t.name() + " (" + cost + " many).");
        return CastResult.OK;
    }

    private CastResult convince(SpellType s, long now) {
        Monster m = combat().target();
        if (m == null || m.isDead()) return CastResult.NO_TARGET;
        int cost = SummonTable.defaults().mana(m.type().key());
        if (cost < 0 || m.type().boss() || m.isSummon() || m.type().isElite()) {
            log().add(now, EventLog.Kind.WARNING, "Tego stworzenia nie da się przekonać.");
            return CastResult.NOT_POSSIBLE;
        }
        if (summons().size() >= SummonTable.MAX_SUMMONS) return CastResult.TOO_MANY_SUMMONS;
        if (pc().mana() < cost) return CastResult.NOT_ENOUGH_MANA;
        combat().setTarget(null);
        monsters().convert(m, pc().name());
        summons.add(m);
        lastDeathSeen = combat().lastDeath();
        monsterConditions.remove(m);
        sys.addExtraMana(cost);
        combat().addEvent(CombatEvent.projectile(now, pc().position(), m.position(), "MAGIC"));
        combat().addEvent(CombatEvent.area(now, m.position(), 0, "MAGIC"));
        log().add(now, EventLog.Kind.INFO, m.type().name() + " służy ci teraz (" + cost + " many).");
        return CastResult.OK;
    }

    /** Ozywienie zwlok: cialo na celu albo obok wstaje jako szkielet-sluga; lup zostaje na ziemi. */
    private CastResult animate(SpellType s, long now) {
        if (summons().size() >= SummonTable.MAX_SUMMONS) return CastResult.TOO_MANY_SUMMONS;
        Position aim = sys.aim(s);
        GroundItems ground = combat().ground();
        if (aim == null || ground == null) return CastResult.NOT_POSSIBLE;
        Position[] best = {null};
        ground.forEachCorpseNear(aim, 1, (p, c) -> {
            if (best[0] == null || p.distanceTo(aim) < best[0].distanceTo(aim)) best[0] = p;
        });
        if (best[0] == null || !monsters().registry().contains("skeleton")) {
            log().add(now, EventLog.Kind.WARNING, "Tu nie ma zwłok do ożywienia.");
            return CastResult.NOT_POSSIBLE;
        }
        Monster m = monsters().spawnSummon("skeleton", best[0], pc().name(), now);
        if (m == null) return CastResult.NO_ROOM;
        ground.removeCorpse(best[0]);
        summons.add(m);
        lastDeathSeen = combat().lastDeath();
        combat().addEvent(CombatEvent.projectile(now, pc().position(), best[0], "DEATH"));
        combat().addEvent(CombatEvent.area(now, m.position(), 0, "DEATH"));
        log().add(now, EventLog.Kind.INFO, "Zwłoki wstają jako " + m.type().name() + ".");
        return CastResult.OK;
    }

    /** Exeta res, exeta amp res, boskie oslepienie: potwory w promieniu (najwyzej "ilosc") atakuja rzucajacego. */
    private CastResult challenge(SpellType s, long now) {
        List<Monster> near = sys.targetsAround(pc().position(), Math.max(1, s.radius()));
        if (s.produceCount() > 0 && near.size() > s.produceCount()) {
            Position me = pc().position();
            near.sort((a, b) -> Integer.compare(me.distanceTo(a.position()), me.distanceTo(b.position())));
            near = new ArrayList<>(near.subList(0, s.produceCount()));
        }
        String fx = s.damageType() == pl.fawizja.pixel.combat.DamageType.HOLY ? "HOLY" : "SPARKS";
        combat().addEvent(CombatEvent.area(now, pc().position(), Math.max(1, s.radius()), fx));
        for (Monster m : near) {
            monsters().provoke(m);
            m.holdFromFleeing(now + 6000);
        }
        log().add(now, EventLog.Kind.INFO, s.name() + ": " + near.size() + " potworów atakuje ciebie.");
        return CastResult.OK;
    }

    private CastResult buff(SpellType s, long now) {
        long ms = s.range() * 1000L;
        pc().addSpellBuff(s.key(), ms);
        if (PlayerCharacter.BUFF_SWIFT_FOOT.equals(s.key())) pc().addHaste((int) Math.round(s.base()), ms);
        effect(now, "HASTE");
        log().add(now, EventLog.Kind.INFO, s.name() + " (" + s.range() + " s).");
        return CastResult.OK;
    }

    // ------------------------------------------------------------------ runy pol

    /** Kierunek od postaci do pola (prosty, dominujaca os); przy tym samym polu - patrzenie. */
    private Direction towards(Position to) {
        Position me = pc().position();
        int dx = to.x() - me.x(), dy = to.y() - me.y();
        if (dx == 0 && dy == 0) {
            Direction d = sys.facing();
            return d.diagonal() ? (d.dx > 0 ? Direction.EAST : Direction.WEST) : d;
        }
        if (Math.abs(dx) >= Math.abs(dy)) return dx > 0 ? Direction.EAST : Direction.WEST;
        return dy > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** Pola runy: pole (1), bomba 3x3 (promien 1), sciana 5 pol w poprzek kierunku rzutu (promien 2). */
    public List<Position> fieldTiles(SpellType s, Position center) {
        List<Position> out = new ArrayList<>();
        if (s.kind() == SpellKind.RUNE_WALL) {
            Direction d = towards(center);
            int sx = -d.dy, sy = d.dx;
            for (int w = -s.radius(); w <= s.radius(); w++) out.add(center.translate(sx * w, sy * w));
        } else {
            for (int dy = -s.radius(); dy <= s.radius(); dy++)
                for (int dx = -s.radius(); dx <= s.radius(); dx++) out.add(center.translate(dx, dy));
        }
        out.removeIf(p -> !p.equals(center) && !world().hasLineOfSight(center, p));
        return out;
    }

    private CastResult fieldRune(SpellType s, long now) {
        Position center = sys.aim(s);
        Fields.Kind kind = Fields.ofDamage(s.damageType());
        if (center == null || kind == null) return CastResult.NOT_POSSIBLE;
        int placed = 0;
        for (Position p : fieldTiles(s, center)) {
            if (world().isProtectionZone(p) || world().fields().blocks(p)) continue;
            if (world().fields().place(p, kind, now, pc().name(), world()) != null) {
                placed++;
                combat().addEvent(CombatEvent.area(now, p, 0, s.damageType().name()));
            }
        }
        if (placed == 0) return CastResult.NO_ROOM;
        combat().addEvent(CombatEvent.projectile(now, pc().position(), center, s.damageType().name()));
        applyFieldsToMonsters(now);
        return CastResult.OK;
    }

    private CastResult barrier(SpellType s, long now) {
        Position p = sys.aim(s);
        if (p == null || p.equals(pc().position()) || monsters().monsterAt(p) != null || p.equals(sys.playerTargetPos())
                || world().isProtectionZone(p)) return CastResult.NO_ROOM;
        Fields.Kind kind = s.damageType() == pl.fawizja.pixel.combat.DamageType.EARTH ? Fields.Kind.WILD_GROWTH : Fields.Kind.MAGIC_WALL;
        if (world().fields().place(p, kind, now, pc().name(), world()) == null) return CastResult.NO_ROOM;
        combat().addEvent(CombatEvent.projectile(now, pc().position(), p, kind == Fields.Kind.WILD_GROWTH ? "EARTH" : "ENERGY"));
        combat().addEvent(CombatEvent.area(now, p, 0, kind == Fields.Kind.WILD_GROWTH ? "EARTH" : "MAGIC"));
        return CastResult.OK;
    }

    // ------------------------------------------------------------------ czas

    /** Krok efektow: wygasanie pol, potwory w polach rzucajacego, tiki stanow na potworach, AI slug. */
    public void update(long now) {
        world().fields().expire(now);
        applyFieldsToMonsters(now);
        tickMonsterConditions(now);
        updateSummons(now);
    }

    /** Potwor wchodzi w pole postaci (albo pole powstaje pod nim): stan pola jak w Tibii, pierwszy tik od razu. */
    private void applyFieldsToMonsters(long now) {
        Fields fields = world().fields();
        if (fields.size() == 0) {
            lastField.clear();
            return;
        }
        String me = pc().name();
        for (Monster m : monsters().near(pc().position(), 14)) {
            if (m.isDead() || m.isSummon()) continue;
            Fields.Field f = fields.at(m.position());
            Long last = lastField.get(m);
            if (f == null) {
                if (last != null) lastField.remove(m);
                continue;
            }
            if (last != null && last == f.id()) continue;
            lastField.put(m, f.id());
            ConditionType type = Fields.condition(f.kind());
            if (type == null || f.owner() != null && !f.owner().equals(me)) continue;
            monsterConditions.computeIfAbsent(m, k -> new Conditions())
                    .add(type, Fields.ticks(f.kind()), Fields.intervalMs(f.kind()), false, now, f.kind().label);
            monsters().provoke(m);
        }
    }

    private void tickMonsterConditions(long now) {
        if (monsterConditions.isEmpty()) return;
        for (Iterator<Map.Entry<Monster, Conditions>> it = monsterConditions.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Monster, Conditions> e = it.next();
            Monster m = e.getKey();
            if (m.isDead() || m.isSummon() || monsters().monsterAt(m.position()) != m) {
                it.remove();
                continue;
            }
            for (Conditions.Tick t : e.getValue().tick(now)) {
                combat().conditionHitMonster(m, t.amount(), t.type(), now);
                if (m.isDead()) break;
            }
            if (m.isDead() || !e.getValue().any()) it.remove();
        }
        lastField.keySet().removeIf(Monster::isDead);
    }

    /** Odwoluje wszystkie slugi (smierc postaci, wylogowanie). */
    public void dismissSummons() {
        for (Monster m : summons) monsters().dismiss(m);
        summons.clear();
    }

    /**
     * Sludzy jak w Tibii: ida za wlascicielem (po zmianie pietra albo z daleka - teleport obok), atakuja jego cel;
     * dziki potwor, ktory nie siega gracza, oddaje sludze, ktory go bije.
     */
    private void updateSummons(long now) {
        if (summons.isEmpty()) return;
        Object death = combat().lastDeath();
        if (pc().isDead() || death != lastDeathSeen && lastDeathSeen != NONE) {
            // smierc postaci: sludzy znikaja jak w Tibii
            lastDeathSeen = death;
            dismissSummons();
            return;
        }
        lastDeathSeen = death;
        Position me = pc().position();
        Monster target = combat().target();
        if (target != null && (target.isDead() || target.isSummon())) target = null;
        for (Monster s : summons()) {
            Position sp = s.position();
            if (sp.z() != me.z() || sp.distanceTo(me) > 10) {
                monsters().teleportSummon(s, me);
                continue;
            }
            if (target != null && target.position().z() == sp.z() && sp.distanceTo(target.position()) <= 8) {
                int reach = Math.max(1, s.type().range());
                boolean inReach = sp.distanceTo(target.position()) <= reach
                        && (reach == 1 || world().hasLineOfSight(sp, target.position()));
                if (inReach) {
                    if (monsters().attackReady(s, now)) {
                        combat().creatureHit(s, target, now);
                        monsters().markAttacked(s, now);
                    }
                    retaliate(target, s, now);
                } else {
                    monsters().stepToward(s, target.position(), me, now);
                }
            } else if (sp.distanceTo(me) > 2) {
                monsters().stepToward(s, me, me, now);
            }
        }
        summons.removeIf(m -> m.isDead() || monsters().monsterAt(m.position()) != m);
    }

    /** Odwet dzikiego potwora na sludze, gdy sam nie siega gracza (gracz za sluga albo poza zasiegiem). */
    private void retaliate(Monster wild, Monster summon, long now) {
        if (wild.isDead() || summon.isDead()) return;
        Position me = pc().position();
        int reach = Math.max(1, wild.type().range());
        boolean reachesPlayer = !pc().isInvisible() && wild.position().z() == me.z() && wild.position().distanceTo(me) <= reach;
        if (reachesPlayer) return;
        if (wild.position().distanceTo(summon.position()) > reach || !monsters().attackReady(wild, now)) return;
        combat().creatureHit(wild, summon, now);
        monsters().markAttacked(wild, now);
    }
}
