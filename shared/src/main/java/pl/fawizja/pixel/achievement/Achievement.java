package pl.fawizja.pixel.achievement;

import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;

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
    FRIENDS_5("Towarzyski", "miej 5 znajomych", Kind.FRIENDS, 5),
    ELITE_1("Czempion", "pokonaj potwora elitarnego", Kind.ELITES, 1),
    ELITE_25("Łowca elity", "pokonaj 25 potworów elitarnych", Kind.ELITES, 25),
    SKILL_40("Mistrz broni", "osiągnij 40 w umiejętności walki", Kind.SKILL, 40),
    CHESTS_10("Poszukiwacz skarbów", "otwórz 10 skrzyń skarbów", Kind.CHESTS, 10),
    WEEKLY_BOSS("Łowca tygodnia", "pokonaj bossa tygodnia", Kind.WEEKLY, 1),
    PLAYTIME_10("Stały bywalec", "spędź w grze 10 godzin", Kind.PLAYTIME, 10),
    HUNT_10("Zawodowy łowca", "oddaj 10 zleceń łowcy", Kind.HUNTS, 10),
    CHALLENGE_5("Pogromca fal", "ukończ 5 wyzwań ze zwojów", Kind.CHALLENGES, 5),
    RESTED_100("Wypoczęty łowca", "pokonaj 100 potworów z premią wypoczęcia", Kind.RESTED, 100),
    DUELS_10("Mistrz pojedynków", "wygraj 10 pojedynków", Kind.DUELS, 10),
    RARE_1("Szczęściarz", "zdobądź rzadki łup", Kind.RARE, 1),
    RARE_10("Ulubieniec losu", "zdobądź 10 rzadkich łupów", Kind.RARE, 10);

    public enum Kind { LEVEL, KILLS, SPECIES, BOSSES, QUESTS, BANK, FRIENDS, ELITES, SKILL, CHESTS, WEEKLY, PLAYTIME, HUNTS, CHALLENGES, RESTED, DUELS, RARE }

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
            case ELITES: return pc.bestiary().eliteKills();
            case SKILL: {
                // najlepsza umiejetnosc walki (piesci, miecz, dystans, magia)
                int best = 0;
                for (Skill s : new Skill[] {Skill.FIST, Skill.SWORD, Skill.DISTANCE, Skill.MAGIC}) best = Math.max(best, pc.skills().level(s));
                return best;
            }
            case CHESTS: return pc.openedChests().size();
            case WEEKLY: return pc.weeklyBossWeek() >= 0 ? 1 : 0;
            case PLAYTIME: return pc.stats().playMs() / 3_600_000L; // pelne godziny
            case HUNTS: return pc.hunt().completed();
            case CHALLENGES: return pc.stats().challenges();
            case RESTED: return pc.stats().restedKills();
            case DUELS: return pc.stats().duelsWon();
            case RARE: return pc.stats().rareDrops();
            default: return 0;
        }
    }

    public boolean reached(PlayerCharacter pc) { return progress(pc) >= target; }

    /** Tytul osiagniecia po kluczu (null = brak albo nieznany klucz, np. z nowszej wersji). */
    public static String titleOf(String key) {
        if (key == null) return null;
        for (Achievement a : values()) if (a.name().equals(key)) return a.title;
        return null;
    }
}
