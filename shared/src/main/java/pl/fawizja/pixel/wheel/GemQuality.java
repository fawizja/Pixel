package pl.fawizja.pixel.wheel;

/**
 * Jakosc klejnotu Kola Przeznaczenia (Canary WheelGemQuality_t): mniejszy ma 1 mod podstawowy, zwykly 2 podstawowe,
 * wiekszy 2 podstawowe i 1 najwyzszy. Ceny z Canary config.lua.dist (wheelAtelierReveal* / wheelAtelierRotate*) podzielone przez 4 jak reszta ekonomii,
 * okruchy z rozbicia jak PlayerWheel::destroyGem (mniejszy 1-5 mniejszych, zwykly 2-10 mniejszych, wiekszy 1-5 wiekszych).
 */
public enum GemQuality {
    LESSER('L', "mniejszy", "Mniejszy klejnot", 31_250, 31_250, 1, 5, false),
    REGULAR('R', "zwykły", "Klejnot", 250_000, 62_500, 2, 10, false),
    GREATER('G', "większy", "Większy klejnot", 1_500_000, 125_000, 1, 5, true);

    public final char code;
    public final String label;
    public final String title;
    /** Cena odkrycia modow w Pracowni klejnotow (gp). */
    public final long revealCost;
    /** Cena zmiany domeny klejnotu (gp). */
    public final long switchCost;
    /** Okruchy z rozbicia: od..do sztuk. */
    public final int fragMin;
    public final int fragMax;
    /** Rozbicie daje wieksze okruchy (inaczej mniejsze). */
    public final boolean greaterFragments;

    GemQuality(char code, String label, String title, long revealCost, long switchCost, int fragMin, int fragMax, boolean greaterFragments) {
        this.code = code;
        this.label = label;
        this.title = title;
        this.revealCost = revealCost;
        this.switchCost = switchCost;
        this.fragMin = fragMin;
        this.fragMax = fragMax;
        this.greaterFragments = greaterFragments;
    }

    /** Liczba modow podstawowych (1 albo 2). */
    public int basicMods() { return this == LESSER ? 1 : 2; }

    public boolean hasSupreme() { return this == GREATER; }

    public static GemQuality ofCode(char c) {
        for (GemQuality q : values()) if (q.code == c) return q;
        return null;
    }
}
