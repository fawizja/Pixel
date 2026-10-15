package pl.fawizja.pixel.magic;

/** Wynik proby rzucenia czaru, uzycia runy, mikstury lub craftingu - z komunikatem dla gracza. */
public enum CastResult {
    OK("OK"),
    NOT_ALLOWED("Zasady PvP na to nie pozwalają."),
    WRONG_VOCATION("Twoja profesja nie zna tego czaru."),
    LEVEL_TOO_LOW("Za niski poziom."),
    MAGIC_TOO_LOW("Za niski poziom magii."),
    NOT_ENOUGH_MANA("Za mało many."),
    COOLDOWN("Jeszcze nie możesz tego użyć."),
    NO_TARGET("Najpierw wybierz cel."),
    OUT_OF_RANGE("Cel jest poza zasięgiem."),
    NOT_VISIBLE("Nie widzisz celu."),
    PROTECTION_ZONE("Nie możesz walczyć w strefie ochronnej."),
    MISSING_ITEM("Brakuje wymaganego przedmiotu."),
    BACKPACK_FULL("Plecak jest pełny."),
    NOT_ENOUGH_GOLD("Za mało złota."),
    NOT_USABLE("Tego nie można użyć."),
    FULL("Nie potrzebujesz tego teraz."),
    DEAD("Postać nie żyje.");

    public final String message;

    CastResult(String message) { this.message = message; }
}
