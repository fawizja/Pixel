package pl.fawizja.pixel.npc;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.fawizja.pixel.item.ItemCategory;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.magic.SpellRegistry;

/**
 * Sklepy z data/shops.csv. Zasady przeciw nieskonczonym pieniadzom: skup ponizej ceny sprzedazy
 * w kazdym sklepie, a monety, runy, mikstury i amunicja nie sa skupowane (mozna je tworzyc z many).
 */
public final class ShopRegistry {
    private final Map<String, Shop> shops;

    private ShopRegistry(Map<String, Shop> shops) { this.shops = Collections.unmodifiableMap(shops); }

    public static ShopRegistry loadDefault(ItemRegistry items) {
        try {
            return parse(SpellRegistry.readLines("/data/shops.csv"), items);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read shops", e);
        }
    }

    public static ShopRegistry parse(List<String> lines, ItemRegistry items) throws IOException {
        Map<String, Shop> map = new LinkedHashMap<>();
        int n = 0;
        for (String line : lines) {
            n++;
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] f = t.split(";");
            if (f.length != 4) throw new IOException("shops.csv line " + n + ": expected 4 fields");
            try {
                ItemType it = items.byKey(f[1].trim());
                long buy = Long.parseLong(f[2].trim());
                long sell = Long.parseLong(f[3].trim());
                if (sell > 0 && neverBought(it.category())) {
                    throw new IllegalArgumentException("shops must not buy " + it.category() + " (" + it.key() + ")");
                }
                map.computeIfAbsent(f[0].trim(), Shop::new).add(new ShopOffer(it.id(), buy, sell));
            } catch (IllegalArgumentException e) {
                throw new IOException("shops.csv line " + n + ": " + e.getMessage(), e);
            }
        }
        // ten sam przedmiot: najwyzszy skup w calym swiecie ponizej najnizszej ceny sprzedazy
        for (Shop a : map.values()) {
            for (ShopOffer o : a.offers()) {
                if (!o.bought()) continue;
                for (Shop b : map.values()) {
                    ShopOffer p = b.offer(o.itemId());
                    if (p != null && p.sold() && p.buyPrice() <= o.sellPrice()) {
                        throw new IOException("shops.csv: item " + o.itemId() + " bought by " + a.key()
                                + " for more than " + b.key() + " sells it");
                    }
                }
            }
        }
        return new ShopRegistry(map);
    }

    static boolean neverBought(ItemCategory c) {
        return c == ItemCategory.CURRENCY || c == ItemCategory.RUNE || c == ItemCategory.POTION
                || c == ItemCategory.AMMUNITION;
    }

    public Shop get(String key) {
        Shop s = shops.get(key);
        if (s == null) throw new IllegalArgumentException("unknown shop " + key);
        return s;
    }

    public boolean contains(String key) { return shops.containsKey(key); }
    public Collection<Shop> all() { return shops.values(); }
}
