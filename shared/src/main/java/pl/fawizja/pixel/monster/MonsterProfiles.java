package pl.fawizja.pixel.monster;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Profile stylu walki wszystkich potworow (data/monster_behaviour.csv):
 * {@code potwor;archetyp;dystans;pocisk;ucieczka;opis}. Czary potworow sa w data/monster_spells.csv.
 */
public final class MonsterProfiles {

    private static volatile MonsterProfiles defaults;

    private final Map<String, MonsterProfile> byKey;

    private MonsterProfiles(Map<String, MonsterProfile> byKey) { this.byKey = Collections.unmodifiableMap(byKey); }

    public static MonsterProfiles defaults() {
        MonsterProfiles d = defaults;
        if (d == null) {
            try {
                d = defaults = parse(MonsterRegistry.readLines("/data/monster_behaviour.csv"));
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read monster behaviour", e);
            }
        }
        return d;
    }

    public static MonsterProfiles parse(List<String> lines) throws IOException {
        Map<String, MonsterProfile> m = new LinkedHashMap<>();
        int n = 0;
        for (String line : lines) {
            n++;
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] f = t.split(";");
            if (f.length < 5) throw new IOException("monster_behaviour.csv line " + n + ": expected 6 fields");
            try {
                String missile = f[3].trim().equals("-") ? null : f[3].trim();
                int run = f[4].trim().equals("-") ? -1 : Integer.parseInt(f[4].trim());
                MonsterProfile p = new MonsterProfile(f[0].trim(), MonsterProfile.Archetype.valueOf(f[1].trim()),
                        Integer.parseInt(f[2].trim()), missile, run);
                if (m.put(p.key(), p) != null) throw new IllegalArgumentException("duplicate " + p.key());
            } catch (RuntimeException e) {
                throw new IOException("monster_behaviour.csv line " + n + ": " + e.getMessage(), e);
            }
        }
        return new MonsterProfiles(m);
    }

    /** Profil rodzaju (takze odmian Kuzni - po kluczu); bez wpisu profil zastepczy wg zachowania. */
    public MonsterProfile of(MonsterType t) {
        MonsterProfile p = byKey.get(t.key());
        return p != null ? p : MonsterProfile.fallback(t);
    }

    public boolean has(String key) { return byKey.containsKey(key); }

    public Map<String, MonsterProfile> all() { return byKey; }
}
