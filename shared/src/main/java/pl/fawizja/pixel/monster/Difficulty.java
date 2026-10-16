package pl.fawizja.pixel.monster;

/** Trudnosc potwora wzgledem poziomu postaci (kolor i opis imienia celu). */
public enum Difficulty {
    TRIVIAL("bardzo łatwy", 0.6f, 0.6f, 0.6f),
    EASY("łatwy", 0.85f, 0.95f, 0.85f),
    FAIR("wyrównany", 1f, 1f, 1f),
    HARD("trudny", 1f, 0.8f, 0.25f),
    DEADLY("śmiertelny", 1f, 0.3f, 0.25f);

    public final String label;
    public final float r, g, b;

    Difficulty(String label, float r, float g, float b) {
        this.label = label;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public static Difficulty of(int monsterLevel, int playerLevel) {
        int p = Math.max(1, playerLevel);
        if (monsterLevel < p / 4) return TRIVIAL;
        if (monsterLevel < p / 2) return EASY;
        if (monsterLevel <= p * 6 / 5 + 5) return FAIR;
        if (monsterLevel <= p * 2 + 10) return HARD;
        return DEADLY;
    }
}
