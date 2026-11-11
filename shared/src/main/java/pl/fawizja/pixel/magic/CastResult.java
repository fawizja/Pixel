package pl.fawizja.pixel.magic;

import pl.fawizja.pixel.i18n.Messages;
/** Wynik proby rzucenia czaru, uzycia runy, mikstury lub craftingu - z komunikatem dla gracza. */
public enum CastResult {
    OK("OK"),
    NOT_ALLOWED("Zasady PvP na to nie pozwalają."),
    BLACK_SKULL("Z czarną czaszką nie używasz czarów ani run obszarowych."),
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
    DEAD("Postać nie żyje."),
    WHEEL_LOCKED("Ten czar odblokujesz objawieniem Koła Przeznaczenia."),
    NO_CONDITION("Nie masz stanu, który leczy ten czar."),
    NO_ROOM("Nie ma tu miejsca."),
    TOO_MANY_SUMMONS("Nie możesz przywołać więcej stworzeń."),
    NOT_POSSIBLE("To niemożliwe."),
    ONLY_CREATURES("Tej runy możesz użyć tylko na stworzeniu."),
    ONLY_SELF("Tej runy możesz użyć tylko na sobie.");

    private final String message;

    public String message() { return Messages.tr(message); }

    CastResult(String message) { this.message = message; }
}
