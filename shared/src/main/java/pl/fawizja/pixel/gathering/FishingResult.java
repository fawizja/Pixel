package pl.fawizja.pixel.gathering;

/** Wynik proby lowienia (z komunikatem dla gracza). */
public enum FishingResult {
    CAUGHT("Złowiono!"),
    NOTHING("Nic nie bierze."),
    NO_ROD("Potrzebujesz wędki w plecaku."),
    NO_BAIT("Potrzebujesz robaków na przynętę."),
    NO_WATER("W pobliżu nie ma łowiska."),
    DEPLETED("Ryby w pobliżu się wystraszyły - spróbuj gdzie indziej albo poczekaj."),
    COOLDOWN(""),
    BACKPACK_FULL("Plecak jest pełny.");

    public final String message;

    FishingResult(String message) { this.message = message; }
}
