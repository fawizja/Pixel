package pl.fawizja.pixel.monster;

/** Pozycja tabeli lupow: szansa w promilach (0..1000) i zakres ilosci. */
public record LootEntry(int itemId, int chancePermille, int min, int max) {
    public LootEntry {
        if (chancePermille < 0 || chancePermille > 1000) throw new IllegalArgumentException("chance");
        if (min < 1 || max < min) throw new IllegalArgumentException("amount");
    }
}
