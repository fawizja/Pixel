package pl.fawizja.pixel.magic;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Odnowienia grup czarow jak w Canary (data/spell_groups.csv): wlasne odnowienie grupy glownej (czary skupienia
 * blokuja grupe ataku na 4 s zamiast 2 s) i druga grupa (np. "special": silne uderzenia i Blyskawica, "focus":
 * czary skupienia i utito tempo...). Grupy ataku, leczenia i wsparcia sa od siebie niezalezne - jak w Tibii mozna
 * w tej samej chwili rzucic czar ataku i leczenia.
 */
public final class SpellGroups {

    /** Druga grupa czaru: nazwa i czas odnowienia. */
    public record Secondary(String group, int ms) {}

    private record Entry(int primaryMs, Secondary secondary) {}

    private static volatile Map<String, Entry> table;

    private SpellGroups() {}

    private static Map<String, Entry> table() {
        Map<String, Entry> t = table;
        if (t == null) {
            try {
                t = parse(SpellRegistry.readLines("/data/spell_groups.csv"));
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read spell groups", e);
            }
            table = t;
        }
        return t;
    }

    static Map<String, Entry> parse(List<String> lines) throws IOException {
        Map<String, Entry> out = new LinkedHashMap<>();
        int n = 0;
        for (String line : lines) {
            n++;
            String l = line.trim();
            if (l.isEmpty() || l.startsWith("#")) continue;
            String[] f = l.split(";", -1);
            if (f.length != 4) throw new IOException("spell_groups.csv line " + n + ": expected 4 fields");
            try {
                int primary = f[1].isBlank() ? -1 : Integer.parseInt(f[1].trim());
                Secondary sec = f[2].isBlank() ? null : new Secondary(f[2].trim(), Integer.parseInt(f[3].trim()));
                if (out.put(f[0].trim(), new Entry(primary, sec)) != null) throw new IOException("duplicate " + f[0]);
            } catch (NumberFormatException e) {
                throw new IOException("spell_groups.csv line " + n + ": " + e.getMessage(), e);
            }
        }
        return Collections.unmodifiableMap(out);
    }

    /** Odnowienie grupy glownej po rzucie czaru (Canary groupCooldown, pierwsza liczba). */
    public static int primaryMs(SpellType s) {
        Entry e = table().get(s.key());
        return e != null && e.primaryMs() >= 0 ? e.primaryMs() : s.group().groupCooldownMs;
    }

    /** Druga grupa czaru albo null. */
    public static Secondary secondary(String key) {
        Entry e = table().get(key);
        return e == null ? null : e.secondary();
    }

    /** Klucze czarow z druga grupa (do testow i opisow). */
    public static java.util.Set<String> withSecondary() {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (Map.Entry<String, Entry> e : table().entrySet()) if (e.getValue().secondary() != null) out.add(e.getKey());
        return out;
    }
}
