package pl.fawizja.pixel.monster;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.fawizja.pixel.combat.ConditionType;
import pl.fawizja.pixel.combat.DamageType;
import pl.fawizja.pixel.magic.SpellArea;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Czary potworow (data/monster_spells.csv): smoki zieja fala ognia, rzucaja wielkie kule ognia, oddychaja lodem
 * i piorunem zaleznie od koloru. Wiersz: {@code potwor;czar;tryb;obszar;typ;min;max;szansa;zasieg;dodatek}, gdzie
 * dodatek to {@code -}, stan {@code TYP:min-max:odstepMs:szansa} (np. BURNING:60-110:4000:60) albo spowolnienie
 * {@code SLOW:procent:ms}.
 */
public final class MonsterSpells {

    private static volatile MonsterSpells defaults;

    private final Map<String, List<MonsterSpell>> byMonster;

    private MonsterSpells(Map<String, List<MonsterSpell>> byMonster) { this.byMonster = Collections.unmodifiableMap(byMonster); }

    public static MonsterSpells defaults() {
        MonsterSpells d = defaults;
        if (d == null) {
            try {
                d = defaults = parse(MonsterRegistry.readLines("/data/monster_spells.csv"));
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read monster spells", e);
            }
        }
        return d;
    }

    public static MonsterSpells parse(List<String> lines) throws IOException {
        Map<String, List<MonsterSpell>> m = new LinkedHashMap<>();
        int n = 0;
        for (String line : lines) {
            n++;
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] f = t.split(";");
            if (f.length != 10) throw new IOException("monster_spells.csv line " + n + ": expected 10 fields");
            try {
                SpellArea area = SpellArea.byName(f[3].trim());
                if (area == null) throw new IllegalArgumentException("unknown area " + f[3]);
                MonsterConditions.Entry cond = null;
                int slow = 0;
                long slowMs = 0;
                String x = f[9].trim();
                if (!x.equals("-")) {
                    String[] p = x.split(":");
                    if (p[0].equals("SLOW")) {
                        slow = Integer.parseInt(p[1]);
                        slowMs = Long.parseLong(p[2]);
                    } else {
                        String[] mm = p[1].split("-");
                        cond = new MonsterConditions.Entry(ConditionType.valueOf(p[0]), Integer.parseInt(mm[0]), Integer.parseInt(mm[1]),
                                Integer.parseInt(p[2]), Integer.parseInt(p[3]));
                    }
                }
                m.computeIfAbsent(f[0].trim(), k -> new ArrayList<>()).add(new MonsterSpell(f[1].trim(),
                        MonsterSpell.Mode.valueOf(f[2].trim()), area, DamageType.valueOf(f[4].trim()), Integer.parseInt(f[5].trim()),
                        Integer.parseInt(f[6].trim()), Integer.parseInt(f[7].trim()), Integer.parseInt(f[8].trim()), cond, slow, slowMs));
            } catch (RuntimeException e) {
                throw new IOException("monster_spells.csv line " + n + ": " + e.getMessage(), e);
            }
        }
        Map<String, List<MonsterSpell>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<MonsterSpell>> e : m.entrySet()) out.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        return new MonsterSpells(out);
    }

    /** Czary potwora (po kluczu, takze odmian Kuzni) albo pusta lista. */
    public List<MonsterSpell> of(MonsterType t) {
        if (t == null) return Collections.emptyList();
        List<MonsterSpell> l = byMonster.get(t.key());
        return l == null ? Collections.emptyList() : l;
    }

    public boolean casts(MonsterType t) { return !of(t).isEmpty(); }

    /** Najdalszy zasieg czarow potwora (0 = nie rzuca czarow). */
    public int maxRange(MonsterType t) {
        int r = 0;
        for (MonsterSpell s : of(t)) r = Math.max(r, s.range());
        return r;
    }

    /** Zaplanowany rzut: czar, srodek obszaru (kotwica), kierunek fali, pole rzucajacego i trafione pola. */
    public record Cast(MonsterSpell spell, Position anchor, Direction dir, Position origin, List<Position> tiles) {
        public boolean hits(Position p) { return tiles.contains(p); }
    }

    /**
     * Obszar rzutu czaru z pola me na cel tp albo null, gdy cel nie stoi w obszarze (fala: wybiera prosty kierunek,
     * w ktorym cel jest w obszarze - najpierw ten ku celowi, potem pozostale, jak {@code SpellSystem.aimDirection};
     * kula i wybuch wokol potwora: linia wzroku). world == null - bez sprawdzania wzroku.
     */
    public static Cast plan(MonsterSpell sp, Position me, Position tp, Direction facing, WorldMap world) {
        SpellArea a = sp.area();
        switch (sp.mode()) {
            case BALL: {
                if (world != null && me.distanceTo(tp) > 1 && !world.hasLineOfSight(me, tp)) return null;
                return new Cast(sp, tp, null, tp, a.visibleTiles(tp, null, tp, world));
            }
            case SELF: {
                List<Position> tiles = a.visibleTiles(me, null, me, world);
                return tiles.contains(tp) ? new Cast(sp, me, null, me, tiles) : null;
            }
            default: {
                Direction dom = Direction.toward(me.x(), me.y(), tp.x(), tp.y(), facing == null ? Direction.SOUTH : facing.cardinal());
                List<Direction> order = new ArrayList<>(List.of(dom, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST));
                for (Direction d : order) {
                    Position anchor = me.translate(d.dx, d.dy);
                    List<Position> tiles = a.visibleTiles(anchor, d, me, world);
                    if (tiles.contains(tp)) return new Cast(sp, anchor, d, me, tiles);
                }
                return null;
            }
        }
    }

    public Map<String, List<MonsterSpell>> all() { return byMonster; }
}
