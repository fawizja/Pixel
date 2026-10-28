package pl.fawizja.pixel.client.ui;

/** Kolory paskow zycia jak w Tibii: zielony, jasnozielony, zolty, czerwony, ciemnoczerwony (wg procentu). */
public final class BarColors {

    private BarColors() {}

    /** {r, g, b} 0..1 dla zycia f (0..1). */
    public static float[] health(float f) {
        if (f > 0.92f) return new float[] {0f, 0.75f, 0f};
        if (f > 0.6f) return new float[] {0.38f, 0.75f, 0.38f};
        if (f > 0.3f) return new float[] {0.75f, 0.75f, 0f};
        if (f > 0.08f) return new float[] {0.75f, 0.19f, 0.19f};
        if (f > 0.03f) return new float[] {0.75f, 0f, 0f};
        return new float[] {0.38f, 0f, 0f};
    }

    /** Pasek many nad postacia gracza. */
    public static float[] mana() { return new float[] {0.25f, 0.4f, 1f}; }
}
