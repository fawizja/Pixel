package pl.fawizja.pixel.magic;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pl.fawizja.pixel.item.ItemCategory;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemType;

/** Mikstury i jedzenie (data/consumables.csv). */
public final class ConsumableRegistry {
    private final Map<Integer, Consumable> byItem;

    private ConsumableRegistry(Map<Integer, Consumable> byItem) {
        this.byItem = Collections.unmodifiableMap(byItem);
    }

    public static ConsumableRegistry loadDefault(ItemRegistry items) {
        try {
            return parse(SpellRegistry.readLines("/data/consumables.csv"), items);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read consumables", e);
        }
    }

    public static ConsumableRegistry parse(List<String> lines, ItemRegistry items) throws IOException {
        Map<Integer, Consumable> map = new HashMap<>();
        int n = 0;
        for (String line : lines) {
            n++;
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] f = t.split(";");
            if (f.length != 7) throw new IOException("consumables.csv line " + n + ": expected 7 fields");
            try {
                ItemType it = items.byKey(f[0].trim());
                if (it.category() != ItemCategory.POTION && it.category() != ItemCategory.FOOD) {
                    throw new IllegalArgumentException(it.key() + " is not a potion or food");
                }
                Consumable c = new Consumable(it.id(), i(f[1]), i(f[2]), i(f[3]), i(f[4]), i(f[5]), i(f[6]));
                if (map.put(it.id(), c) != null) throw new IOException("duplicate consumable " + it.key());
            } catch (IllegalArgumentException e) {
                throw new IOException("consumables.csv line " + n + ": " + e.getMessage(), e);
            }
        }
        return new ConsumableRegistry(map);
    }

    public Consumable get(int itemId) { return byItem.get(itemId); }
    public Collection<Consumable> all() { return byItem.values(); }

    private static int i(String s) { return Integer.parseInt(s.trim()); }
}
