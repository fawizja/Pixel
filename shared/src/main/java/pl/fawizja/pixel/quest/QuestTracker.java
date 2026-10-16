package pl.fawizja.pixel.quest;

import java.util.ArrayList;
import java.util.List;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.daily.DailyTask;

/** Sledzenie zadan na ekranie: najwyzej {@link #MAX_LINES} krotkie wiersze pod stanem postaci. */
public final class QuestTracker {
    public static final int MAX_LINES = 2;

    private QuestTracker() {}

    public static List<String> lines(PlayerCharacter pc, QuestSystem quests, long today, String dailyName) {
        return lines(pc, quests, today, dailyName, pc.hunt().monster());
    }

    /** dailyName / huntName: nazwy potworow zadania dnia i zlecenia lowcy (albo klucze); today: biezacy dzien. */
    public static List<String> lines(PlayerCharacter pc, QuestSystem quests, long today, String dailyName, String huntName) {
        List<String> out = new ArrayList<>();
        if (quests != null) {
            for (String key : pc.quests().active().keySet()) {
                if (out.size() >= MAX_LINES - 1) break;
                QuestType q = quests.registry().get(key);
                if (q == null) continue;
                out.add(quests.ready(pc, q) ? "Zadanie: " + q.name() + " - gotowe, wróć po nagrodę"
                        : "Zadanie: " + q.name() + " " + quests.progress(pc, q) + "/" + q.amount());
            }
        }
        DailyTask d = pc.daily();
        if (d.activeOn(today) && !d.claimed() && out.size() < MAX_LINES) {
            out.add(d.doneOn(today) ? "Zadanie dnia: gotowe, wróć do przewodnika"
                    : "Zadanie dnia: " + dailyName + " " + d.progress() + "/" + d.target());
        }
        HuntTask h = pc.hunt();
        if (h.active() && out.size() < MAX_LINES) {
            out.add(h.done() ? "Zlecenie łowcy: gotowe, wróć do przewodnika"
                    : "Zlecenie: " + huntName + " " + h.progress() + "/" + h.target());
        }
        return out;
    }
}
