package pl.fawizja.pixel.gathering;

/** Wynik proby lowienia (z komunikatem dla gracza). */
public enum FishingResult {
    CAUGHT("Złowiono!"),
    NOTHING("Nic nie bierze."),
    NO_ROD("Nie masz wędki - weź ją do ręki albo do plecaka (kupisz u kupca z plecakami)."),
    NO_BAIT("Nie masz robaków na przynętę - kup je u kupca z plecakami (1 gp za sztukę) i włóż do gniazda amunicji obok spodni."),
    BAIT_NOT_IN_SLOT("Włóż robaki do gniazda amunicji (obok spodni), żeby łowić."),
    NO_WATER("W pobliżu nie ma łowiska."),
    DEPLETED("Ryby w pobliżu się wystraszyły - spróbuj gdzie indziej albo poczekaj."),
    COOLDOWN(""),
    BACKPACK_FULL("Plecak jest pełny.");

    public final String message;

    FishingResult(String message) { this.message = message; }
}
