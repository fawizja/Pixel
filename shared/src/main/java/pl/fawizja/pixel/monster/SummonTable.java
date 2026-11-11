package pl.fawizja.pixel.monster;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stworzenia, ktore mozna przywolac (utevo res) albo przekonac runa (data/summons.csv), z koszem many jak w Tibii.
 */
public final class SummonTable {
    /** Jak w Tibii: najwyzej dwa slugi naraz. */
    public static final int MAX_SUMMONS = 2;

    private static volatile SummonTable defaults;
    private final Map<String, Integer> mana;

    private SummonTable(Map<String, Integer> mana) { this.mana = Collections.unmodifiableMap(mana); }

    public static SummonTable defaults() {
        SummonTable d = defaults;
        if (d == null) {
            try {
                d = defaults = parse(MonsterRegistry.readLines("/data/summons.csv"));
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read summons", e);
            }
        }
        return d;
    }

    public static SummonTable parse(List<String> lines) throws IOException {
        Map<String, Integer> m = new LinkedHashMap<>();
        int n = 0;
        for (String line : lines) {
            n++;
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] f = t.split(";");
            if (f.length != 2) throw new IOException("summons.csv line " + n + ": expected 2 fields");
            int cost;
            try {
                cost = Integer.parseInt(f[1].trim());
            } catch (NumberFormatException e) {
                throw new IOException("summons.csv line " + n + ": bad mana", e);
            }
            if (cost <= 0 || m.put(f[0].trim(), cost) != null) throw new IOException("summons.csv line " + n + ": bad entry");
        }
        return new SummonTable(m);
    }

    /** Koszt many albo -1, gdy stworzenia nie mozna przywolac. */
    public int mana(String monsterKey) { return mana.getOrDefault(monsterKey, -1); }

    public boolean summonable(String monsterKey) { return mana.containsKey(monsterKey); }

    /** Stworzenia w kolejnosci kosztu. */
    public Map<String, Integer> all() { return mana; }
}
