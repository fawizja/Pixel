package pl.fawizja.pixel.magic;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ktory obszar z Canary ma ktory czar albo runa (data/spell_areas.csv). Czary bez wpisu dostaja obszar z rodzaju
 * i promienia ({@link #of(SpellType, int)}).
 */
public final class SpellAreas {

    private static volatile Map<String, SpellArea> table;

    private SpellAreas() {}

    /** Tabela klucz czaru -> obszar z data/spell_areas.csv. */
    public static Map<String, SpellArea> table() {
        Map<String, SpellArea> t = table;
        if (t == null) {
            try {
                t = parse(SpellRegistry.readLines("/data/spell_areas.csv"));
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read spell areas", e);
            }
            table = t;
        }
        return t;
    }

    static Map<String, SpellArea> parse(List<String> lines) throws IOException {
        Map<String, SpellArea> out = new LinkedHashMap<>();
        int n = 0;
        for (String line : lines) {
            n++;
            String l = line.trim();
            if (l.isEmpty() || l.startsWith("#")) continue;
            String[] f = l.split(";");
            if (f.length != 2) throw new IOException("spell_areas.csv line " + n + ": expected 2 fields");
            SpellArea a = SpellArea.byName(f[1]);
            if (a == null) throw new IOException("spell_areas.csv line " + n + ": unknown area " + f[1]);
            if (out.put(f[0].trim(), a) != null) throw new IOException("spell_areas.csv line " + n + ": duplicate " + f[0]);
        }
        return Collections.unmodifiableMap(out);
    }

    /** Czar lancuchowy (Lancuch pokuty, Wybuch ducha): przeskakuje miedzy celami, nie ma obszaru. */
    public static boolean chain(SpellType s) {
        return s.kind() == SpellKind.ATTACK_AREA && s.produceCount() > 0 && !table().containsKey(s.key());
    }

    /**
     * Obszar czaru (extra = dodatkowe pola z Kola Przeznaczenia: dluzszy promien). null - czar bez obszaru.
     */
    public static SpellArea of(SpellType s, int extra) {
        SpellArea a = table().get(s.key());
        if (a != null) {
            if (extra > 0 && a.name().startsWith("BEAM")) return SpellArea.beam(Integer.parseInt(a.name().substring(4)) + extra);
            return a;
        }
        int r = s.radius() + Math.max(0, extra);
        switch (s.kind()) {
            case ATTACK_AREA: case RUNE_AREA: case HEAL_PARTY:
                return SpellArea.circle(r);
            case ATTACK_TARGET:
                return r > 0 ? SpellArea.circle(r) : null;
            case ATTACK_WAVE:
                return r <= 4 ? SpellArea.WAVE4 : SpellArea.WAVE7;
            case ATTACK_BEAM:
                return SpellArea.beam(r);
            case RUNE_FIELD:
                return r <= 0 ? SpellArea.POINT : SpellArea.SQUARE1X1;
            case RUNE_WALL:
                return r >= 3 ? SpellArea.WALLFIELD_ENERGY : SpellArea.WALLFIELD;
            default:
                return null;
        }
    }

    /**
     * Czy obszar czaru zaczyna sie przed rzucajacym (fale, promienie, ciosy mnicha przed soba) - jak w Canary czary
     * z needDirection(true), ktorych srodek to pole przed postacia.
     */
    public static boolean inFront(SpellType s, SpellArea a) {
        if (a == null || !a.directional()) return false;
        SpellKind k = s.kind();
        return k == SpellKind.ATTACK_WAVE || k == SpellKind.ATTACK_BEAM || k == SpellKind.ATTACK_AREA;
    }
}
