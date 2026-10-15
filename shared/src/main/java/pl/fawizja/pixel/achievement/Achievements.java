package pl.fawizja.pixel.achievement;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import pl.fawizja.pixel.character.PlayerCharacter;

/** Zdobyte osiagniecia postaci (raz zdobyte zostaja, nawet gdy np. saldo spadnie). */
public final class Achievements {

    private final Set<Achievement> earned = EnumSet.noneOf(Achievement.class);

    public boolean has(Achievement a) { return earned.contains(a); }

    public int count() { return earned.size(); }

    /** Nowo zdobyte od ostatniego sprawdzenia (juz zapisane jako zdobyte). */
    public List<Achievement> check(PlayerCharacter pc) {
        List<Achievement> fresh = new ArrayList<>();
        for (Achievement a : Achievement.values()) {
            if (!earned.contains(a) && a.reached(pc)) {
                earned.add(a);
                fresh.add(a);
            }
        }
        return fresh;
    }

    /** Zapis: klucze po przecinku. Nieznane klucze (np. z nowszej wersji) sa pomijane. */
    public String encode() {
        StringBuilder sb = new StringBuilder();
        for (Achievement a : earned) {
            if (sb.length() > 0) sb.append(',');
            sb.append(a.name());
        }
        return sb.toString();
    }

    public void restore(String text) {
        if (text == null || text.isBlank()) return;
        for (String k : text.split(",")) {
            try {
                earned.add(Achievement.valueOf(k.trim()));
            } catch (IllegalArgumentException e) {
                // pomijamy
            }
        }
    }

    public void copyFrom(Achievements o) {
        earned.clear();
        earned.addAll(o.earned);
    }
}
