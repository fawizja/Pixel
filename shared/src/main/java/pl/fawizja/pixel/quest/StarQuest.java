package pl.fawizja.pixel.quest;

import pl.fawizja.pixel.character.Blessing;
import pl.fawizja.pixel.character.PlayerCharacter;

/**
 * Pieczec Gasnacej Gwiazdy: bardzo dluga wyprawa endgame na Wyspie Gasnacej Gwiazdy (ziemie premium, tylko statkiem).
 * Strazniczka Pieczeci (NPC {@link #GIVER}, rola przewodnika) daje lancuch zadan z data/quests.csv i quest_chains.csv
 * (stan w {@code QuestLog} postaci, zapisywany); drzwi i portale lochu otwieraja ukonczone zadania i pokonani bossowie
 * ({@link pl.fawizja.pixel.world.QuestGate}, plik mapy star_quest.csv). Po oddaniu ostatniego zadania
 * ({@link #FINAL_MISSION}) postac dostaje znacznik {@link #FLAG_SEALED}: wtedy Herold w swiatyni Varden
 * ({@link #HERALD}) sprzedaje wszystkie blogoslawienstwa naraz za jedna cene ({@link #bundlePrice}).
 * Szesciu mnichow z kaplic dziala jak dotad.
 */
public final class StarQuest {
    private StarQuest() {}

    /** Zleceniodawca na wyspie (przewodnik). */
    public static final String GIVER = "star_warden";
    /** Herold w swiatyni Varden, sprzedajacy wszystkie blogoslawienstwa naraz. */
    public static final String HERALD = "varden_herald";
    /** Ostatnie zadanie lancucha: pokonanie Noctharionu. Jego oddanie konczy wyprawe. */
    public static final String FINAL_MISSION = "star_final";
    /** Znacznik postaci: wyprawa ukonczona, wszystkie blogoslawienstwa dostepne u Herolda. */
    public static final String FLAG_SEALED = "star_sealed";
    /** Grupa skrzyn sali nagrod (z grupy mozna otworzyc tylko jedna). */
    public static final String REWARD_GROUP = "star_reward";
    /** Znacznik grupy w {@code openedChests}: nagroda juz wybrana. */
    public static final String REWARD_TAKEN = "rcg_" + REWARD_GROUP;
    /** Cena zestawu: ulamek sumy cen brakujacych blogoslawienstw u mnichow. */
    public static final double BUNDLE_FACTOR = 0.8;

    /** Wywolywane po oddaniu zadania: ostatnie zadanie ustawia znacznik wyprawy. */
    static void onCompleted(PlayerCharacter pc, String questKey) {
        if (FINAL_MISSION.equals(questKey)) pc.setQuestFlag(FLAG_SEALED);
        if (PostmanQuest.FINAL_MISSION.equals(questKey)) pc.setQuestFlag(PostmanQuest.FLAG_RANK);
        String ship = pl.fawizja.pixel.npc.ShipAccess.grantedFlag(questKey);
        if (ship != null) pc.setQuestFlag(ship);
    }

    public static boolean completed(PlayerCharacter pc) { return pc.hasQuestFlag(FLAG_SEALED); }

    /** Ktorych blogoslawienstw postac jeszcze nie kupila (piec zwyklych i Zrzadzenie losu). */
    public static java.util.List<Blessing> missing(PlayerCharacter pc) {
        java.util.List<Blessing> out = new java.util.ArrayList<>();
        for (Blessing b : Blessing.values()) if (!pc.boughtBlessing(b)) out.add(b);
        return out;
    }

    /** Jedna cena za wszystkie brakujace blogoslawienstwa: {@link #BUNDLE_FACTOR} sumy cen u mnichow, w setkach gp. */
    public static long bundlePrice(PlayerCharacter pc) {
        long sum = 0;
        for (Blessing b : missing(pc)) sum += b.price(pc.level());
        return Math.round(sum * BUNDLE_FACTOR / 100.0) * 100;
    }
}
