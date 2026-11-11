package pl.fawizja.pixel.wheel;

import pl.fawizja.pixel.i18n.Messages;
/**
 * Cwiartka (domena) Kola Przeznaczenia jak w Tibii: zielona (lewa gora), czerwona (prawa gora), fioletowa (prawy dol),
 * niebieska (lewy dol). Kazda ma 9 pol (razem 1000 punktow) i wlasne objawienie (Revelation) w 3 stopniach.
 */
public enum WheelDomain {
    GREEN("Zielona", 'G', 0x4caf50),
    RED("Czerwona", 'R', 0xd84a3a),
    PURPLE("Fioletowa", 'P', 0x9c5ccf),
    BLUE("Niebieska", 'B', 0x3f8fd8);

    /** Progi objawienia (Canary WheelStagePointsEnum_t): punkty w domenie dla stopnia 1, 2 i 3. */
    public static final int[] STAGE_POINTS = {250, 500, 1000};
    /** Plaska premia do obrazen i leczenia za stopien objawienia (Canary io_wheel.hpp Revelation.stats). */
    public static final int[] STAGE_FLAT = {4, 9, 20};

    private final String label;
    public String label() { return Messages.tr(label); }
    public final char code;
    /** Kolor domeny (RGB) do okna kola. */
    public final int rgb;

    WheelDomain(String label, char code, int rgb) {
        this.label = label;
        this.code = code;
        this.rgb = rgb;
    }

    /** Stopien objawienia (0..3) dla liczby punktow w domenie. */
    public static int stage(int points) {
        int s = 0;
        for (int t : STAGE_POINTS) if (points >= t) s++;
        return s;
    }

    /** Punkty potrzebne do nastepnego stopnia (0 = stopien 3 osiagniety). */
    public static int nextStagePoints(int points) {
        for (int t : STAGE_POINTS) if (points < t) return t;
        return 0;
    }

    public static WheelDomain ofCode(char c) {
        for (WheelDomain d : values()) if (d.code == c) return d;
        return null;
    }
}
