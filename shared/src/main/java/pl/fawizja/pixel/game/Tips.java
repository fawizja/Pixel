package pl.fawizja.pixel.game;

import java.util.ArrayList;
import java.util.List;
import pl.fawizja.pixel.character.PlayerCharacter;

/**
 * Podpowiedzi dla nowych postaci: kazda pokazuje sie raz, gdy pasuje do sytuacji (pierwszy potwor obok,
 * pierwsze cialo, niskie zdrowie, pelny plecak...). Pokazane zapisuja sie w postaci jako maska bitowa.
 * Postacie od poziomu {@link #MAX_LEVEL} nie dostaja juz podpowiedzi.
 */
public final class Tips {

    public static final int MAX_LEVEL = 30;

    public enum Tip {
        WELCOME("Witaj! Chodzisz joystickiem (na komputerze WASD lub strzałki). Dotknij NPC, aby porozmawiać; /pomoc pokazuje polecenia."),
        MONSTER("Potwór w pobliżu: dotknij go albo przycisku CEL, a postać zacznie walczyć sama."),
        LOOT("Pokonany potwór zostawia ciało: dotknij go albo stań obok, a łupy trafią do plecaka (przycisk PLECAK)."),
        LOW_HEALTH("Mało zdrowia! Przycisk HP pije miksturę (klawisz Q); w ustawieniach jest też leczenie samo."),
        QUESTS("Zadania i nagrody daje przewodnik w mieście: wpisz /idź przewodnik albo otwórz dziennik (klawisz Z)."),
        BAG_FULL("Plecak prawie pełny: u kupca „Sprzedaj cały łup”, a u bankiera odłożysz rzeczy do depozytu (/idź kupiec).");

        public final String text;

        Tip(String text) { this.text = text; }
    }

    private Tips() {}

    /** Stan sytuacji do podpowiedzi (liczony w GameSession). */
    public record Situation(boolean monsterNear, long kills, int healthPercent, int freeSlots) {}

    /** Podpowiedzi do pokazania teraz (i oznaczone jako pokazane). */
    public static List<String> due(PlayerCharacter pc, Situation s) {
        List<String> out = new ArrayList<>();
        if (pc.level() >= MAX_LEVEL) return out;
        check(pc, Tip.WELCOME, true, out);
        check(pc, Tip.MONSTER, s.monsterNear(), out);
        check(pc, Tip.LOOT, s.kills() > 0, out);
        check(pc, Tip.LOW_HEALTH, s.healthPercent() < 40, out);
        check(pc, Tip.QUESTS, pc.level() >= 3, out);
        check(pc, Tip.BAG_FULL, s.freeSlots() <= 2, out);
        return out;
    }

    private static void check(PlayerCharacter pc, Tip t, boolean when, List<String> out) {
        if (!when || pc.tipShown(t.ordinal()) || !out.isEmpty()) return; // najwyzej jedna naraz
        pc.markTip(t.ordinal());
        out.add("Podpowiedź: " + t.text);
    }
}
