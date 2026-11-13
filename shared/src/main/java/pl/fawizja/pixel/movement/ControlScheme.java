package pl.fawizja.pixel.movement;

import pl.fawizja.pixel.i18n.Messages;
/**
 * Warianty sterowania ruchem na ekranie dotykowym (2026-11-05, wlasciciel: "joystick za czuly" - kilka wariantow
 * do wyboru w ustawieniach). Klawiatura dziala zawsze, niezaleznie od wariantu.
 */
public enum ControlScheme {
    /** Staly krzyzyk w lewym dolnym rogu: 8 kierunkow (4 strzalki i skosy w rogach), przytrzymanie = krok za krokiem. */
    DPAD("dpad", "Krzyżyk", "Krzyżyk"),
    /** Joystick pod kciukiem tylko w 4 kierunkach (do wyboru w opcjach); skos dopiero po chwili trzymania w klinie skosu. */
    FLOAT4("float4", "Pływający joystick (4 kierunki)", "Pływający, 4 kierunki"),
    /** Bez galki: przytrzymanie palca na mapie prowadzi postac w strone palca (jak chodzenie mysza w Tibii). */
    HOLD("hold", "Przytrzymanie na mapie", "Przytrzymanie na mapie"),
    /** Joystick (staly albo pod kciukiem wg ustawien), 8 kierunkow od razu ({@link StickFilter}). */
    CLASSIC("classic", "Joystick (8 kierunków)", "Joystick, 8 kierunków");

    public static final ControlScheme DEFAULT = DPAD;

    public final String key;
    private final String label;
    public String label() { return Messages.tr(label); }
    /** Krotsza nazwa do waskich przyciskow. */
    private final String shortLabel;
    public String shortLabel() { return Messages.tr(shortLabel); }

    ControlScheme(String key, String label, String shortLabel) {
        this.key = key;
        this.label = label;
        this.shortLabel = shortLabel;
    }

    /** Wariant z galka (pracuje z wektorem wychylenia). */
    public boolean stick() { return this == FLOAT4 || this == CLASSIC; }

    public ControlScheme next() { return values()[(ordinal() + 1) % values().length]; }

    /** Wariant o kluczu (zapis ustawien) albo def dla nieznanego. */
    public static ControlScheme byKey(Object key, ControlScheme def) {
        if (key == null) return def;
        String k = key.toString().trim();
        for (ControlScheme s : values()) if (s.key.equalsIgnoreCase(k) || s.name().equalsIgnoreCase(k)) return s;
        return def;
    }
}
