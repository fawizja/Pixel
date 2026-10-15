package pl.fawizja.pixel.quest;

/** Wynik akcji zadania z komunikatem. */
public enum QuestResult {
    ACCEPTED("Zadanie przyjęte."),
    COMPLETED("Zadanie ukończone!"),
    TOO_LOW_LEVEL("Masz za niski poziom na to zadanie."),
    LOCKED("Najpierw ukończ poprzedni etap."),
    ALREADY_DONE("To zadanie jest już ukończone."),
    NOT_FINISHED("Zadanie nie jest jeszcze wykonane."),
    NO_SPACE("Brak miejsca w plecaku na nagrodę.");

    private final String message;

    QuestResult(String message) { this.message = message; }

    public String message() { return message; }
}
