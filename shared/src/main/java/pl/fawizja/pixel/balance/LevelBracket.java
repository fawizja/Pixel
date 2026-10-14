package pl.fawizja.pixel.balance;

/** Orientacyjne przedzialy rozwoju (nie sa sztywnymi blokadami). */
public enum LevelBracket {
    BEGINNER(8, 20, "Poczatkujacy"),
    BASIC(20, 60, "Podstawowe expowiska"),
    INTERMEDIATE(60, 150, "Srednio zaawansowane regiony"),
    HARD(150, 300, "Trudniejsze obszary"),
    ADVANCED(300, 500, "Zaawansowane expowiska"),
    VERY_HARD(500, 750, "Bardzo trudne regiony"),
    ENDGAME(750, 1000, "Endgame i bossowie");

    private final int fromLevel;
    private final int toLevel;
    private final String label;

    LevelBracket(int fromLevel, int toLevel, String label) {
        this.fromLevel = fromLevel;
        this.toLevel = toLevel;
        this.label = label;
    }

    public int fromLevel() { return fromLevel; }
    public int toLevel() { return toLevel; }
    public String label() { return label; }

    /** Przedzial dla poziomu; granice naleza do nizszego przedzialu, ponizej 8 = BEGINNER. */
    public static LevelBracket of(int level) {
        for (LevelBracket b : values()) {
            if (level <= b.toLevel) return b;
        }
        return ENDGAME;
    }
}
