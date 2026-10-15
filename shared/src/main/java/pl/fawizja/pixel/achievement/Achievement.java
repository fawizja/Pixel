package pl.fawizja.pixel.achievement;

import pl.fawizja.pixel.character.PlayerCharacter;

/** Osiagniecia postaci: liczone ze stanu postaci, zdobyte zapamietywane w zapisie. */
public enum Achievement {
    LEVEL_20("Wędrowiec", "osiągnij poziom 20", Kind.LEVEL, 20),
    LEVEL_50("Weteran", "osiągnij poziom 50", Kind.LEVEL, 50),
    LEVEL_100("Bohater", "osiągnij poziom 100", Kind.LEVEL, 100),
    LEVEL_300("Legenda", "osiągnij poziom 300", Kind.LEVEL, 300),
    KILLS_100("Łowca", "pokonaj 100 potworów", Kind.KILLS, 100),
    KILLS_1000("Pogromca", "pokonaj 1 000 potworów", Kind.KILLS, 1_000),
    KILLS_10000("Postrach potworów", "pokonaj 10 000 potworów", Kind.KILLS, 10_000),
    SPECIES_10("Badacz", "pokonaj 10 różnych potworów", Kind.SPECIES, 10),
    SPECIES_30("Znawca bestii", "pokonaj 30 różnych potworów", Kind.SPECIES, 30),
    BOSS_1("Pogromca bossa", "pokonaj pierwszego bossa", Kind.BOSSES, 1),
    BOSS_5("Łowca bossów", "pokonaj 5 różnych bossów", Kind.BOSSES, 5),
    QUESTS_5("Pomocna dłoń", "ukończ 5 zadań", Kind.QUESTS, 5),
    QUESTS_20("Bohater ludu", "ukończ 20 zadań", Kind.QUESTS, 20),
    RICH("Bogacz", "zgromadź 1 000 000 gp na koncie", Kind.BANK, 1_000_000),
    FRIENDS_5("Towarzyski", "miej 5 znajomych", Kind.FRIENDS, 5);

    public enum Kind { LEVEL, KILLS, SPECIES, BOSSES, QUESTS, BANK, FRIENDS }

    private final String title;
    private final String goal;
    private final Kind kind;
    private final long target;

    Achievement(String title, String goal, Kind kind, long target) {
        this.title = title;
        this.goal = goal;
        this.kind = kind;
        this.target = target;
    }

    public String title() { return title; }
    public String goal() { return goal; }
    public Kind kind() { return kind; }
    public long target() { return target; }

    /** Biezacy postep (moze przekroczyc cel). */
    public long progress(PlayerCharacter pc) {
        switch (kind) {
            case LEVEL: return pc.level();
            case KILLS: return pc.bestiary().total();
            case SPECIES: return pc.bestiary().species();
            case BOSSES: return pc.bestiary().bossesDefeated();
            case QUESTS: return pc.quests().done().size();
            case BANK: return pc.bank().balance();
            case FRIENDS: return pc.friends().size();
            default: return 0;
        }
    }

    public boolean reached(PlayerCharacter pc) { return progress(pc) >= target; }
}
