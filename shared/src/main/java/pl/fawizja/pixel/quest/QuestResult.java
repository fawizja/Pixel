package pl.fawizja.pixel.quest;

/** Wynik akcji zadania z komunikatem. */
public enum QuestResult {
    ACCEPTED("Zadanie przyjete."),
    COMPLETED("Zadanie ukonczone!"),
    TOO_LOW_LEVEL("Masz za niski poziom na to zadanie."),
    LOCKED("Najpierw ukoncz poprzedni etap."),
    ALREADY_DONE("To zadanie jest juz ukonczone."),
    NOT_FINISHED("Zadanie nie jest jeszcze wykonane."),
    NO_SPACE("Brak miejsca w plecaku na nagrode.");

    private final String message;

    QuestResult(String message) { this.message = message; }

    public String message() { return message; }
}
