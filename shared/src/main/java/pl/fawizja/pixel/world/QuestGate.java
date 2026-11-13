package pl.fawizja.pixel.world;

import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.quest.QuestLog;

/**
 * Przejscie strzezone zadaniem (Pieczec Gasnacej Gwiazdy): pole drzwi albo portalu, przez ktore przejdzie tylko postac,
 * ktora spelnia wymaganie - jak drzwi zadan w Tibii. Wymaganie z pliku mapy star_quest.csv:
 * <ul>
 *   <li>{@code quest:klucz} - zadanie z quests.csv ukonczone (oddane przewodnikowi),</li>
 *   <li>{@code boss:klucz} - postac pokonala tego potwora-bossa (znacznik {@code boss.klucz} zapisuje sesja po zabiciu),</li>
 *   <li>{@code party:N} - w druzynie jest co najmniej N graczy w grze (dzwignia w stylu annihilatora z Canary),</li>
 *   <li>{@code flag:nazwa} - znacznik postaci (np. ukonczenie calej wyprawy).</li>
 * </ul>
 * Wymagania mozna laczyc znakiem {@code +} ({@code flag:lv_a+flag:lv_b+party:3}): przejscie otwiera sie dopiero, gdy
 * spelnione sa wszystkie skladniki (kilka dzwigni, boss i zadanie naraz); {@link #parts()} rozbija zlozone wymaganie.
 * Drzwi sa przy tym zwyklymi drzwiami poziomu (wejscie od poziomu 300): ta klasa dodaje tylko wymaganie zadania.
 */
public record QuestGate(Position at, String requirement) {

    public enum Kind { QUEST, BOSS, FLAG, PARTY }

    public QuestGate {
        for (String part : requirement.split("\\+", -1)) {
            int c = part.indexOf(':');
            if (c <= 0 || c == part.length() - 1) throw new IllegalArgumentException("bad gate requirement: " + requirement);
            Kind k = Kind.valueOf(part.substring(0, c).toUpperCase(java.util.Locale.ROOT));
            if (k == Kind.PARTY && Integer.parseInt(part.substring(c + 1)) < 2) throw new IllegalArgumentException("party size must be >= 2: " + requirement);
        }
    }

    /** Skladniki wymagania rozdzielonego znakiem {@code +} (jeden element dla zwyklego wymagania). */
    public java.util.List<QuestGate> parts() {
        if (requirement.indexOf('+') < 0) return java.util.List.of(this);
        java.util.List<QuestGate> out = new java.util.ArrayList<>();
        for (String part : requirement.split("\\+")) out.add(new QuestGate(at, part));
        return out;
    }

    /** Pierwszy skladnik, ktorego postac nie spelnia (albo null, gdy przechodzi). */
    public QuestGate firstUnmet(PlayerCharacter pc) {
        for (QuestGate g : parts()) if (!passes(g.requirement, pc)) return g;
        return null;
    }

    /** Wymagana liczba graczy w druzynie (tylko {@link Kind#PARTY}). */
    public int partySize() { return Integer.parseInt(target()); }

    public Kind kind() { return Kind.valueOf(requirement.substring(0, requirement.indexOf(':')).toUpperCase(java.util.Locale.ROOT)); }

    /** Klucz zadania, potwora albo znacznika. */
    public String target() { return requirement.substring(requirement.indexOf(':') + 1); }

    /** Nazwa znacznika zapisywana w postaci po pokonaniu bossa. */
    public static String bossFlag(String monsterKey) { return "boss." + monsterKey; }

    public boolean passes(PlayerCharacter pc) { return passes(requirement, pc); }

    /** Czy postac spelnia wymaganie zapisane tekstem ({@code quest:..}, {@code boss:..}, {@code flag:..}). */
    public static boolean passes(String requirement, PlayerCharacter pc) {
        if (requirement.indexOf('+') >= 0) {
            for (String part : requirement.split("\\+")) if (!passes(part, pc)) return false;
            return true;
        }
        int c = requirement.indexOf(':');
        String target = requirement.substring(c + 1);
        switch (Kind.valueOf(requirement.substring(0, c).toUpperCase(java.util.Locale.ROOT))) {
            case QUEST: return pc.quests().state(target) == QuestLog.State.DONE;
            case BOSS: return pc.hasQuestFlag(bossFlag(target));
            case PARTY: return pc.partyOnline() >= Integer.parseInt(target);
            default: return pc.hasQuestFlag(target);
        }
    }
}
