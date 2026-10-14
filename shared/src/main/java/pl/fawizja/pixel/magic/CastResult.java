package pl.fawizja.pixel.magic;

/** Wynik proby rzucenia czaru, uzycia runy, mikstury lub craftingu - z komunikatem dla gracza. */
public enum CastResult {
    OK("OK"),
    WRONG_VOCATION("Twoja profesja nie zna tego czaru."),
    LEVEL_TOO_LOW("Za niski poziom."),
    MAGIC_TOO_LOW("Za niski poziom magii."),
    NOT_ENOUGH_MANA("Za malo many."),
    COOLDOWN("Jeszcze nie mozesz tego uzyc."),
    NO_TARGET("Najpierw wybierz cel."),
    OUT_OF_RANGE("Cel jest poza zasiegiem."),
    NOT_VISIBLE("Nie widzisz celu."),
    PROTECTION_ZONE("Nie mozesz walczyc w strefie ochronnej."),
    MISSING_ITEM("Brakuje wymaganego przedmiotu."),
    BACKPACK_FULL("Plecak jest pelny."),
    NOT_ENOUGH_GOLD("Za malo zlota."),
    NOT_USABLE("Tego nie mozna uzyc."),
    FULL("Nie potrzebujesz tego teraz."),
    DEAD("Postac nie zyje.");

    public final String message;

    CastResult(String message) { this.message = message; }
}
