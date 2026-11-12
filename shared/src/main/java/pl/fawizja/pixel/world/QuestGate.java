package pl.fawizja.pixel.world;

import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.quest.QuestLog;

/**
 * Przejscie strzezone zadaniem (Pieczec Gasnacej Gwiazdy): pole drzwi albo portalu, przez ktore przejdzie tylko postac,
 * ktora spelnia wymaganie - jak drzwi zadan w Tibii. Wymaganie z pliku mapy star_quest.csv:
 * <ul>
 *   <li>{@code quest:klucz} - zadanie z quests.csv ukonczone (oddane przewodnikowi),</li>
 *   <li>{@code boss:klucz} - postac pokonala tego potwora-bossa (znacznik {@code boss.klucz} zapisuje sesja po zabiciu),</li>
 *   <li>{@code flag:nazwa} - znacznik postaci (np. ukonczenie calej wyprawy).</li>
 * </ul>
 * Drzwi sa przy tym zwyklymi drzwiami poziomu (wejscie od poziomu 300): ta klasa dodaje tylko wymaganie zadania.
 */
public record QuestGate(Position at, String requirement) {

    public enum Kind { QUEST, BOSS, FLAG }

    public QuestGate {
        int c = requirement.indexOf(':');
        if (c <= 0 || c == requirement.length() - 1) throw new IllegalArgumentException("bad gate requirement: " + requirement);
        Kind.valueOf(requirement.substring(0, c).toUpperCase(java.util.Locale.ROOT));
    }

    public Kind kind() { return Kind.valueOf(requirement.substring(0, requirement.indexOf(':')).toUpperCase(java.util.Locale.ROOT)); }

    /** Klucz zadania, potwora albo znacznika. */
    public String target() { return requirement.substring(requirement.indexOf(':') + 1); }

    /** Nazwa znacznika zapisywana w postaci po pokonaniu bossa. */
    public static String bossFlag(String monsterKey) { return "boss." + monsterKey; }

    public boolean passes(PlayerCharacter pc) {
        switch (kind()) {
            case QUEST: return pc.quests().state(target()) == QuestLog.State.DONE;
            case BOSS: return pc.hasQuestFlag(bossFlag(target()));
            default: return pc.hasQuestFlag(target());
        }
    }
}
