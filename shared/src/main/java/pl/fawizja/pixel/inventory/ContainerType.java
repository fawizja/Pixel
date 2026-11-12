package pl.fawizja.pixel.inventory;

/**
 * Rodzaje plecaka: kazdy ma stala liczbe pol. Standardowy plecak ma 20 pol; wieksze (nagrody z zadan) dodaja
 * pola, a okno pojemnika przewija je w pionie ({@link ContainerGrid}). Klucz to klucz przedmiotu w items.csv.
 */
public enum ContainerType {
    BACKPACK("backpack", 20),
    TRAVELER("backpack_traveler", 24),
    EXPLORER("backpack_explorer", 36);

    /** Gorny limit pol plecaka (stare zapisy z dodatkowymi "stronami" maja do 80 pol). */
    public static final int MAX_SLOTS = 80;

    private final String itemKey;
    private final int slots;

    ContainerType(String itemKey, int slots) {
        this.itemKey = itemKey;
        this.slots = slots;
    }

    public String itemKey() { return itemKey; }

    public int slots() { return slots; }

    /** Rodzaj plecaka dla klucza przedmiotu albo null (to nie jest plecak). */
    public static ContainerType ofItem(String itemKey) {
        for (ContainerType t : values()) if (t.itemKey.equals(itemKey)) return t;
        return null;
    }

    /** Rodzaj o dokladnie tylu polach albo null (np. plecak ze starego zapisu z 40 polami). */
    public static ContainerType ofSlots(int slots) {
        for (ContainerType t : values()) if (t.slots == slots) return t;
        return null;
    }
}
