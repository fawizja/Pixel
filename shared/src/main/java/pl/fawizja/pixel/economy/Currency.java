package pl.fawizja.pixel.economy;

/** Waluty: 100 Gold = 1 Platinum, 100 Platinum = 1 Crystal. */
public enum Currency {
    GOLD(1, 1L, "Złota moneta"),
    PLATINUM(2, 100L, "Platynowa moneta"),
    CRYSTAL(3, 10_000L, "Kryształowa moneta");

    public static final int EXCHANGE_RATE = 100;

    private final int itemId;
    private final long value;
    private final String displayName;

    Currency(int itemId, long value, String displayName) {
        this.itemId = itemId;
        this.value = value;
        this.displayName = displayName;
    }

    public int itemId() { return itemId; }
    public long value() { return value; }
    public String displayName() { return displayName; }

    public static Currency byItemId(int itemId) {
        for (Currency c : values()) if (c.itemId == itemId) return c;
        return null;
    }

    public static boolean isCoin(int itemId) { return byItemId(itemId) != null; }
}
