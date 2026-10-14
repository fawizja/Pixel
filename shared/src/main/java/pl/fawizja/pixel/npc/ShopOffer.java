package pl.fawizja.pixel.npc;

/** Pozycja sklepu: cena kupna przez gracza (0 = nie sprzedaje) i cena skupu (0 = nie skupuje). */
public record ShopOffer(int itemId, long buyPrice, long sellPrice) {
    public ShopOffer {
        if (buyPrice < 0 || sellPrice < 0) throw new IllegalArgumentException("negative price");
        if (buyPrice > 0 && sellPrice >= buyPrice) throw new IllegalArgumentException("sell price must be below buy price");
    }

    public boolean sold() { return buyPrice > 0; }
    public boolean bought() { return sellPrice > 0; }
}
