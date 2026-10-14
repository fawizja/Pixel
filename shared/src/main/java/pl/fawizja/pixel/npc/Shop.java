package pl.fawizja.pixel.npc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Sklep: lista ofert w kolejnosci z pliku. */
public final class Shop {
    private final String key;
    private final List<ShopOffer> offers = new ArrayList<>();

    Shop(String key) { this.key = key; }

    void add(ShopOffer o) {
        if (offer(o.itemId()) != null) throw new IllegalArgumentException("duplicate item " + o.itemId() + " in shop " + key);
        offers.add(o);
    }

    public String key() { return key; }
    public List<ShopOffer> offers() { return Collections.unmodifiableList(offers); }

    public ShopOffer offer(int itemId) {
        for (ShopOffer o : offers) if (o.itemId() == itemId) return o;
        return null;
    }
}
