package pl.fawizja.pixel.quest;

import pl.fawizja.pixel.character.PlayerCharacter;

/**
 * Zadanie Listonosza (darmowe, dziesiec misji, tools/mapgen/postman.py): listonosz Kevan z Varden zleca doreczenia do
 * skrzynek pocztowych w miastach i na polowiskach ({@link pl.fawizja.pixel.world.WorldMap.Mailbox}). Misje to zwykle
 * zadania z quests.csv; uzycie skrzynki o nazwie celu zalicza misje VISIT ({@link QuestSystem#onMailbox}). Oddanie
 * ostatniej misji ({@link #FINAL_MISSION}) daje range Listonosza ({@link #FLAG_RANK}): od tej pory skrzynki pocztowe
 * dzialaja jak okienko poczty - z kazdej mozna wyslac przesylke poleceniem /wyslij (serwer), bez szukania bankiera.
 */
public final class PostmanQuest {
    private PostmanQuest() {}

    public static final String GIVER = "postmaster_kevan";
    public static final String FINAL_MISSION = "postman_m10";
    /** Znacznik postaci: ranga Listonosza (skrzynki dzialaja jak poczta). */
    public static final String FLAG_RANK = "postman_rank";

    public static boolean isPostman(PlayerCharacter pc) { return pc.hasQuestFlag(FLAG_RANK); }
}
