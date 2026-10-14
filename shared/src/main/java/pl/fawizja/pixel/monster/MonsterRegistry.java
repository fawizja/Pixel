package pl.fawizja.pixel.monster;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.fawizja.pixel.combat.DamageType;
import pl.fawizja.pixel.item.ItemRegistry;

/** Rejestr potworow (data/monsters.csv) z tabelami lupow (data/loot.csv). */
public final class MonsterRegistry {

    private final Map<String, MonsterType> byKey;

    private MonsterRegistry(Map<String, MonsterType> byKey) {
        this.byKey = Collections.unmodifiableMap(byKey);
    }

    public static MonsterRegistry loadDefault(ItemRegistry items) {
        try {
            return parse(readLines("/data/monsters.csv"), readLines("/data/loot.csv"), items);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read monster data", e);
        }
    }

    public static MonsterRegistry parse(List<String> monsterLines, List<String> lootLines, ItemRegistry items) throws IOException {
        Map<String, List<LootEntry>> loot = new HashMap<>();
        int n = 0;
        for (String line : lootLines) {
            n++;
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] f = t.split(";");
            if (f.length != 5) throw new IOException("loot.csv line " + n + ": expected 5 fields");
            try {
                loot.computeIfAbsent(f[0].trim(), k -> new ArrayList<>()).add(new LootEntry(items.byKey(f[1].trim()).id(),
                        Integer.parseInt(f[2].trim()), Integer.parseInt(f[3].trim()), Integer.parseInt(f[4].trim())));
            } catch (IllegalArgumentException e) {
                throw new IOException("loot.csv line " + n + ": " + e.getMessage(), e);
            }
        }
        Map<String, MonsterType> map = new LinkedHashMap<>();
        n = 0;
        for (String line : monsterLines) {
            n++;
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] f = t.split(";", 23);
            if (f.length != 23) throw new IOException("monsters.csv line " + n + ": expected 23 fields, got " + f.length);
            try {
                String key = f[0].trim();
                MonsterType m = new MonsterType(key, f[1].trim(), i(f[2]), i(f[3]), Long.parseLong(f[4].trim()), i(f[5]),
                        i(f[6]), i(f[7]), DamageType.valueOf(f[8].trim()), i(f[9]), i(f[10]), i(f[11]),
                        parseResistances(f[12].trim()), MonsterBehavior.valueOf(f[13].trim()), i(f[14]), i(f[15]),
                        i(f[16]), "1".equals(f[17].trim()), f[18].trim(), f[19].trim(), f[20].trim(), activity(f[21].trim()), f[22].trim(),
                        loot.getOrDefault(key, Collections.emptyList()));
                if (map.put(key, m) != null) throw new IOException("duplicate monster " + key);
            } catch (IllegalArgumentException e) {
                throw new IOException("monsters.csv line " + n + ": " + e.getMessage(), e);
            }
        }
        for (String k : loot.keySet()) {
            if (!map.containsKey(k)) throw new IOException("loot.csv references unknown monster " + k);
        }
        return new MonsterRegistry(map);
    }

    private static boolean activity(String v) {
        if ("NOCTURNAL".equals(v)) return true;
        if ("-".equals(v) || v.isEmpty()) return false;
        throw new IllegalArgumentException("activity must be NOCTURNAL or -: " + v);
    }

    private static int i(String s) { return Integer.parseInt(s.trim()); }

    private static Map<DamageType, Integer> parseResistances(String s) {
        Map<DamageType, Integer> m = new EnumMap<>(DamageType.class);
        if (s.isEmpty()) return m;
        for (String part : s.split(",")) {
            String[] kv = part.split(":");
            m.put(DamageType.valueOf(kv[0].trim()), Integer.parseInt(kv[1].trim()));
        }
        return m;
    }

    public MonsterType get(String key) {
        MonsterType t = byKey.get(key);
        if (t == null) throw new IllegalArgumentException("Unknown monster " + key);
        return t;
    }

    public Collection<MonsterType> all() { return byKey.values(); }
    public boolean contains(String key) { return byKey.containsKey(key); }

    static List<String> readLines(String path) throws IOException {
        List<String> lines = new ArrayList<>();
        try (InputStream in = MonsterRegistry.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Missing resource " + path);
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String l;
            while ((l = br.readLine()) != null) lines.add(l);
        }
        return lines;
    }
}
