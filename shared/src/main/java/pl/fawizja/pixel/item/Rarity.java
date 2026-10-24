package pl.fawizja.pixel.item;

/** Rzadkosc przedmiotu wg ceny: kolor ramki pola w plecaku i ekwipunku (0 = zwykla ramka). */
public final class Rarity {

    /** Progi ceny (gp): zwykly, niepospolity, rzadki, epicki, legendarny. */
    static final long[] THRESHOLDS = {500, 3_000, 20_000, 100_000, 400_000};
    public static final String[] NAMES = {"", "zwykły", "niepospolity", "rzadki", "epicki", "legendarny"};

    private Rarity() {}

    public static int tier(ItemType t) {
        if (t == null || t.category() == ItemCategory.CURRENCY) return 0;
        int tier = 0;
        for (long th : THRESHOLDS) if (t.value() >= th) tier++;
        return tier;
    }
}
