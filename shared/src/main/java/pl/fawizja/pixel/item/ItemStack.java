package pl.fawizja.pixel.item;

/**
 * Niezmienny stos przedmiotow. Zmiana ilosci zawsze tworzy nowy obiekt.
 * upgrade = poziom ulepszenia u kowala (0..{@link Upgrade#MAX}); tylko dla przedmiotow pojedynczych.
 */
public record ItemStack(int itemId, int count, int upgrade) {
    public ItemStack {
        if (itemId <= 0) throw new IllegalArgumentException("itemId must be > 0");
        if (count <= 0) throw new IllegalArgumentException("count must be > 0");
        if (upgrade < 0 || upgrade > Upgrade.MAX) throw new IllegalArgumentException("upgrade 0.." + Upgrade.MAX);
        if (upgrade > 0 && count != 1) throw new IllegalArgumentException("upgraded items are single");
    }

    public ItemStack(int itemId, int count) { this(itemId, count, 0); }

    public static ItemStack of(int itemId) { return new ItemStack(itemId, 1); }

    public ItemStack withCount(int newCount) { return new ItemStack(itemId, newCount, upgrade); }

    public ItemStack withUpgrade(int level) { return new ItemStack(itemId, count, level); }
}
