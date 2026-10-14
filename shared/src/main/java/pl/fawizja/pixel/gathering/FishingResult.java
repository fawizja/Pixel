package pl.fawizja.pixel.gathering;

/** Wynik proby lowienia (z komunikatem dla gracza). */
public enum FishingResult {
    CAUGHT("Zlowiono!"),
    NOTHING("Nic nie bierze."),
    NO_ROD("Potrzebujesz wedki w plecaku."),
    NO_BAIT("Potrzebujesz robakow na przynete."),
    NO_WATER("W poblizu nie ma lowiska."),
    DEPLETED("Ryby w poblizu sie wystraszyly - sprobuj gdzie indziej albo poczekaj."),
    COOLDOWN(""),
    BACKPACK_FULL("Plecak jest pelny.");

    public final String message;

    FishingResult(String message) { this.message = message; }
}
