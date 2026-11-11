package pl.fawizja.pixel.monster;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import pl.fawizja.pixel.combat.ConditionType;

/**
 * Stany, ktore potwory nakladaja ciosem (data/monster_conditions.csv), jak w Tibii: pajaki i weze zatruwaja,
 * zywiolaki ognia podpalaja, golemy krysztalowe porazaja, nieumarli przeklinaja, berserkerzy zadaja krwawiace rany.
 */
public final class MonsterConditions {

    /** Stan od ciosu: laczne obrazenia min..max, odstep tikow, szansa (procent trafien z obrazeniami). */
    public record Entry(ConditionType type, int min, int max, int intervalMs, int chance) {
        public Entry {
            if (type == null || min < 1 || max < min || intervalMs < 500 || chance < 1 || chance > 100) {
                throw new IllegalArgumentException("bad monster condition");
            }
        }

        /** Laczne obrazenia stanu (odmiana elitarna x1,3 jak jej ciosy). */
        public int rollTotal(Random rng, double scale) {
            return (int) Math.round((min + rng.nextInt(max - min + 1)) * scale);
        }
    }

    private static volatile MonsterConditions defaults;

    private final Map<String, Entry> byMonster;

    private MonsterConditions(Map<String, Entry> byMonster) { this.byMonster = Collections.unmodifiableMap(byMonster); }

    public static MonsterConditions defaults() {
        MonsterConditions d = defaults;
        if (d == null) {
            try {
                d = defaults = parse(MonsterRegistry.readLines("/data/monster_conditions.csv"));
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read monster conditions", e);
            }
        }
        return d;
    }

    public static MonsterConditions parse(List<String> lines) throws IOException {
        Map<String, Entry> m = new LinkedHashMap<>();
        int n = 0;
        for (String line : lines) {
            n++;
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] f = t.split(";");
            if (f.length != 5) throw new IOException("monster_conditions.csv line " + n + ": expected 5 fields");
            try {
                String[] mm = f[2].split("-");
                Entry e = new Entry(ConditionType.valueOf(f[1].trim()), Integer.parseInt(mm[0].trim()), Integer.parseInt(mm[1].trim()),
                        Integer.parseInt(f[3].trim()), Integer.parseInt(f[4].trim()));
                if (m.put(f[0].trim(), e) != null) throw new IOException("monster_conditions.csv line " + n + ": duplicate " + f[0]);
            } catch (IllegalArgumentException e) {
                throw new IOException("monster_conditions.csv line " + n + ": " + e.getMessage(), e);
            }
        }
        return new MonsterConditions(m);
    }

    /** Stan od ciosu potwora (po kluczu, takze odmian Kuzni) albo null. */
    public Entry of(MonsterType t) { return t == null ? null : byMonster.get(t.key()); }

    public Map<String, Entry> all() { return byMonster; }
}
