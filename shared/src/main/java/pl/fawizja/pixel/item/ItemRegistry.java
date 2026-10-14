package pl.fawizja.pixel.item;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import pl.fawizja.pixel.combat.DamageType;
import pl.fawizja.pixel.vocation.Vocation;

/** Rejestr wszystkich typow przedmiotow. Dane w data/items.csv (separator ';'). */
public final class ItemRegistry {

    public static final String DEFAULT_RESOURCE = "/data/items.csv";

    private final Map<Integer, ItemType> byId;
    private final Map<String, ItemType> byKey;

    private ItemRegistry(Map<Integer, ItemType> byId) {
        this.byId = Collections.unmodifiableMap(byId);
        Map<String, ItemType> k = new HashMap<>();
        for (ItemType t : byId.values()) {
            if (k.put(t.key(), t) != null) throw new IllegalStateException("duplicate item key " + t.key());
        }
        this.byKey = Collections.unmodifiableMap(k);
    }

    public static ItemRegistry loadDefault() {
        try (InputStream in = ItemRegistry.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) throw new IllegalStateException("Missing resource " + DEFAULT_RESOURCE);
            return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + DEFAULT_RESOURCE, e);
        }
    }

    /** Format: id;key;name;category;maxStack;value;slot;attack;defense;armor;minLevel;vocations;light;range;damageType;color;description */
    public static ItemRegistry parse(Reader reader) throws IOException {
        Map<Integer, ItemType> map = new LinkedHashMap<>();
        BufferedReader br = new BufferedReader(reader);
        String line;
        int lineNo = 0;
        while ((line = br.readLine()) != null) {
            lineNo++;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            String[] f = trimmed.split(";", 17); // opis moze zawierac ";"
            if (f.length != 17) throw new IOException("items.csv line " + lineNo + ": expected 17 fields, got " + f.length);
            try {
                int id = Integer.parseInt(f[0].trim());
                int maxStack = Integer.parseInt(f[4].trim());
                ItemType t = new ItemType(id, f[1].trim(), f[2].trim(),
                        ItemCategory.valueOf(f[3].trim()), maxStack > 1, maxStack,
                        Long.parseLong(f[5].trim()), EquipSlot.valueOf(f[6].trim()),
                        Integer.parseInt(f[7].trim()), Integer.parseInt(f[8].trim()), Integer.parseInt(f[9].trim()),
                        Integer.parseInt(f[10].trim()), parseVocations(f[11].trim()),
                        Integer.parseInt(f[12].trim()), Integer.parseInt(f[13].trim()),
                        DamageType.valueOf(f[14].trim()), f[15].trim(), f[16].trim());
                if (map.put(id, t) != null) throw new IOException("items.csv line " + lineNo + ": duplicate id " + id);
            } catch (IllegalArgumentException e) {
                throw new IOException("items.csv line " + lineNo + ": " + e.getMessage(), e);
            }
        }
        return new ItemRegistry(map);
    }

    private static Set<Vocation> parseVocations(String s) {
        Set<Vocation> set = EnumSet.noneOf(Vocation.class);
        if (s.isEmpty() || s.equals("*")) return set;
        for (String part : s.split(",")) set.add(Vocation.valueOf(part.trim()));
        return set;
    }

    public ItemType get(int id) {
        ItemType t = byId.get(id);
        if (t == null) throw new IllegalArgumentException("Unknown item id " + id);
        return t;
    }

    public ItemType byKey(String key) {
        ItemType t = byKey.get(key);
        if (t == null) throw new IllegalArgumentException("Unknown item key " + key);
        return t;
    }

    public boolean contains(int id) { return byId.containsKey(id); }

    public int maxStack(int id) { return get(id).maxStack(); }

    public Collection<ItemType> all() { return byId.values(); }
}
