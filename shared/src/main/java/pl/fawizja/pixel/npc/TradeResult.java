package pl.fawizja.pixel.npc;

/** Wynik handlu z komunikatem dla gracza. */
public enum TradeResult {
    OK("Gotowe."),
    NOT_SOLD_HERE("Ten kupiec tego nie sprzedaje."),
    NOT_BOUGHT_HERE("Ten kupiec tego nie skupuje."),
    NO_MONEY("Nie masz tyle pieniędzy w plecaku."),
    NO_SPACE("Brak miejsca w plecaku."),
    NOTHING_TO_SELL("Nie masz tego przedmiotu w plecaku."),
    INVALID_AMOUNT("Nieprawidłowa ilość."),
    TOO_FAR("Podejdź bliżej.");

    private final String message;

    TradeResult(String message) { this.message = message; }

    public String message() { return message; }
    public boolean ok() { return this == OK; }
}
