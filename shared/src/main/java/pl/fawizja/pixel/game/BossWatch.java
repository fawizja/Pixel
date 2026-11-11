package pl.fawizja.pixel.game;

import pl.fawizja.pixel.i18n.Messages;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pl.fawizja.pixel.monster.MonsterManager;

/**
 * Powiadomienia o odrodzeniu bossow: porownuje kolejne raporty bossow i zwraca tych, ktorzy
 * wlasnie znow zyja. Pierwszy raport tylko zapamietuje stan (bez komunikatow przy wejsciu do gry).
 */
public final class BossWatch {

    /** Odstep miedzy sprawdzeniami w GameSession. */
    public static final long CHECK_MS = 2000;

    private final Map<String, Boolean> alive = new HashMap<>();
    private boolean primed;

    public List<MonsterManager.BossStatus> update(List<MonsterManager.BossStatus> report) {
        List<MonsterManager.BossStatus> back = new ArrayList<>();
        for (MonsterManager.BossStatus b : report) {
            Boolean was = alive.put(b.name() + "@" + b.where(), b.alive());
            if (primed && b.alive() && Boolean.FALSE.equals(was)) back.add(b);
        }
        primed = true;
        return back;
    }

    /** Tekst komunikatu, np. "Grimtooth odrodzil sie (Krainy Varden, pietro -5)." */
    public static String message(MonsterManager.BossStatus b, long now) {
        String[] r = MonsterManager.row(b, now);
        return "Boss " + r[0] + Messages.get("boss_watch.odrodzil_sie") + r[1] + ").";
    }
}
