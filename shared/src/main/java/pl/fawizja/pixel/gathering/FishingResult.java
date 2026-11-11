package pl.fawizja.pixel.gathering;

import pl.fawizja.pixel.i18n.Messages;
/** Wynik proby lowienia (z komunikatem dla gracza). */
public enum FishingResult {
    CAUGHT("Złowiono!"),
    NOTHING("Nic nie bierze."),
    NO_ROD("Nie masz wędki - weź ją do ręki albo noś w plecaku (kupisz u kupca z plecakami)."),
    NO_BAIT("Nie masz robaków w plecaku - kup je u kupca z plecakami (1 gp za sztukę). Każde zarzucenie zużywa jednego."),
    NO_WATER("W pobliżu nie ma łowiska."),
    DEPLETED("Ryby w pobliżu się wystraszyły - spróbuj gdzie indziej albo poczekaj."),
    COOLDOWN(""),
    BACKPACK_FULL("Plecak jest pełny.");

    private final String message;

    public String message() { return Messages.tr(message); }

    FishingResult(String message) { this.message = message; }
}
