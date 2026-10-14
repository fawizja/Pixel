package pl.fawizja.pixel.item;

/** Niezmienny stos przedmiotow. Zmiana ilosci zawsze tworzy nowy obiekt. */
public record ItemStack(int itemId, int count) {
    public ItemStack {
        if (itemId <= 0) throw new IllegalArgumentException("itemId must be > 0");
        if (count <= 0) throw new IllegalArgumentException("count must be > 0");
    }

    public static ItemStack of(int itemId) { return new ItemStack(itemId, 1); }

    public ItemStack withCount(int newCount) { return new ItemStack(itemId, newCount); }
}
