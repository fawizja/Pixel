package pl.fawizja.pixel.magic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.fawizja.pixel.item.ItemRegistry;

/** Receptury craftingu (data/recipes.csv). */
public final class RecipeRegistry {
    private final Map<String, Recipe> byKey;

    private RecipeRegistry(Map<String, Recipe> byKey) {
        this.byKey = Collections.unmodifiableMap(byKey);
    }

    public static RecipeRegistry loadDefault(ItemRegistry items) {
        try {
            return parse(SpellRegistry.readLines("/data/recipes.csv"), items);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read recipes", e);
        }
    }

    public static RecipeRegistry parse(List<String> lines, ItemRegistry items) throws IOException {
        Map<String, Recipe> map = new LinkedHashMap<>();
        int n = 0;
        for (String line : lines) {
            n++;
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] f = t.split(";", 7);
            if (f.length != 7) throw new IOException("recipes.csv line " + n + ": expected 7 fields");
            try {
                Map<Integer, Integer> ing = new LinkedHashMap<>();
                for (String part : f[3].split(",")) {
                    String[] kv = part.trim().split(":");
                    if (kv.length != 2) throw new IllegalArgumentException("bad ingredient '" + part + "'");
                    ing.merge(items.byKey(kv[0].trim()).id(), Integer.parseInt(kv[1].trim()), Integer::sum);
                }
                Recipe r = new Recipe(f[0].trim(), items.byKey(f[1].trim()).id(), Integer.parseInt(f[2].trim()), ing,
                        Long.parseLong(f[4].trim()), Integer.parseInt(f[5].trim()), f[6].trim());
                if (map.put(r.key(), r) != null) throw new IOException("duplicate recipe " + r.key());
            } catch (IllegalArgumentException e) {
                throw new IOException("recipes.csv line " + n + ": " + e.getMessage(), e);
            }
        }
        return new RecipeRegistry(map);
    }

    public Recipe get(String key) {
        Recipe r = byKey.get(key);
        if (r == null) throw new IllegalArgumentException("Unknown recipe " + key);
        return r;
    }

    public List<Recipe> all() { return new ArrayList<>(byKey.values()); }
}
