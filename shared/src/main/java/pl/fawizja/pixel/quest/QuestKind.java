package pl.fawizja.pixel.quest;

/** Rodzaj zadania. */
public enum QuestKind {
    /** Pokonaj N potworow danego typu (liczone od przyjecia zadania). */
    KILL,
    /** Przynies N sztuk przedmiotu (zabierane przy oddaniu). */
    COLLECT,
    /** Dotrzyj do miejsca (region mapy o nazwie w polu celu): zaliczane przy wejsciu, zanim zadanie zostanie oddane. */
    VISIT
}
