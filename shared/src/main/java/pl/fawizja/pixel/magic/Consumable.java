package pl.fawizja.pixel.magic;

/** Efekt mikstury lub jedzenia (data/consumables.csv). */
public record Consumable(int itemId, int hpMin, int hpMax, int manaMin, int manaMax, int cooldownMs, int foodSeconds) {
    public Consumable {
        if (hpMin < 0 || hpMax < hpMin || manaMin < 0 || manaMax < manaMin || cooldownMs < 0 || foodSeconds < 0) {
            throw new IllegalArgumentException("bad consumable " + itemId);
        }
    }

    public boolean isFood() { return foodSeconds > 0; }
    public boolean restoresHealth() { return hpMax > 0; }
    public boolean restoresMana() { return manaMax > 0; }
}
