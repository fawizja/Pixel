package pl.fawizja.pixel.movement;

/**
 * Suwak "Czulosc" (1..5) przeliczony na progi sterowania. Wyzsza czulosc = mniejsza martwa strefa, szerszy klin skosu,
 * krotsze czekanie. Poziom 3 to dotychczasowe wartosci {@link StickFilter}.
 */
public final class ControlTuning {

    public static final int MIN = 1, MAX = 5, DEFAULT = 3;

    private static final float[] ENGAGE = {0.60f, 0.52f, 0.45f, 0.38f, 0.30f};
    private static final double[] FLOAT4_DIAGONAL = {8, 10, 12, 15, 18};
    private static final long[] FLOAT4_DIAGONAL_HOLD = {450, 350, 250, 180, 120};
    private static final long[] HOLD_DELAY = {300, 250, 200, 160, 120};

    private ControlTuning() {}

    public static int clamp(int s) { return Math.max(MIN, Math.min(MAX, s)); }

    private static int i(int s) { return clamp(s) - MIN; }

    /** Wychylenie galki (0..1), od ktorego postac rusza. */
    public static float engage(int s) { return ENGAGE[i(s)]; }

    /** Wychylenie, ponizej ktorego postac staje (2/3 progu ruszenia - histereza). */
    public static float release(int s) { return engage(s) * 2f / 3f; }

    /** Plywajacy joystick (4 kierunki): polowa klina skosu (stopnie wokol 45). */
    public static double float4Diagonal(int s) { return FLOAT4_DIAGONAL[i(s)]; }

    /** Plywajacy joystick: ile ms galka musi stac w klinie skosu, zanim postac pojdzie po skosie. */
    public static long float4DiagonalHoldMs(int s) { return FLOAT4_DIAGONAL_HOLD[i(s)]; }

    /** Przytrzymanie na mapie: po ilu ms dotkniecie staje sie marszem (krotsze = zwykle dotkniecie pola). */
    public static long holdDelayMs(int s) { return HOLD_DELAY[i(s)]; }
}
