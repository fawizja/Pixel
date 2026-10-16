package pl.fawizja.pixel.character;

/**
 * Nagrody za progi poziomu: zloto na konto i zwoje nauki. Kazdy prog raz na postac; postacie sprzed
 * wprowadzenia nagrod zaczynaja od swojego obecnego poziomu (bez nagrod wstecz).
 */
public final class LevelRewards {
    public static final int[] MILESTONES = {10, 20, 30, 50, 75, 100, 150, 200, 250};

    private LevelRewards() {}

    /** Najwyzszy prog z przedzialu (from, to], albo 0. */
    public static int next(int from, int to) {
        for (int m : MILESTONES) if (m > from && m <= to) return m;
        return 0;
    }

    public static long gold(int milestone) { return 10L * milestone * milestone; }

    public static int scrolls(int milestone) { return milestone < 20 ? 0 : Math.min(3, milestone / 50 + 1); }
}
