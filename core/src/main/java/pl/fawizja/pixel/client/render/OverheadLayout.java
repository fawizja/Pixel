package pl.fawizja.pixel.client.render;

/**
 * Wysokosc paska zycia, ikon i imienia nad potworem (jednostki swiata, y rosnie w gore od dolnej krawedzi pola).
 * Zwykly potwor ma pasek tuz nad polem ({@link #BAR_ABOVE_TILE}); duzy (smok 2x2: grafika 64x64 od prawego dolnego
 * rogu jego pola) ma go nad gorna krawedzia grafiki, jak w Tibii, a nie w polowie sprajta.
 */
public final class OverheadLayout {

    /** Pasek zycia zwyklego potwora: o tyle nad dolna krawedzia pola. */
    public static final float BAR_ABOVE_TILE = 1.02f;
    /** Grafika zwyklego potwora ma jedno pole wysokosci. */
    public static final float NORMAL_HEIGHT = 1f;

    private OverheadLayout() {}

    /** Ile pol wyzej niz u zwyklego potwora stoi pasek potwora o grafice wysokiej na spriteTiles pol (0 = zwykly). */
    public static float lift(float spriteTiles) {
        return Math.max(0f, spriteTiles - NORMAL_HEIGHT);
    }

    /** Dolny brzeg paska zycia nad dolna krawedzia pola potwora (baseWy = dolna krawedz jego pola). */
    public static float barY(float baseWy, float spriteTiles) {
        return baseWy + BAR_ABOVE_TILE + lift(spriteTiles);
    }

    /** Wysokosc grafiki potwora w polach: plik o wysokosci px pikseli (32 px = 1 pole); 64 px = 2x2. */
    public static float spriteTiles(int heightPx) {
        return Math.max(NORMAL_HEIGHT, Math.min(64, heightPx) / 32f);
    }
}
