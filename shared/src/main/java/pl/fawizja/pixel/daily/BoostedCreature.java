package pl.fawizja.pixel.daily;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import pl.fawizja.pixel.monster.MonsterType;

/**
 * Potwor dnia: jeden rodzaj potwora (nie boss) wspolny dla wszystkich graczy, zmienia sie o polnocy.
 * Daje wiecej doswiadczenia i czestszy lup. Wybor zalezy tylko od dnia, wiec klient i serwer licza go tak samo.
 */
public final class BoostedCreature {
    public static final int EXP_PERCENT = 50;
    public static final int LOOT_PERCENT = 50;

    private BoostedCreature() {}

    /** Potwor dnia sposrod kandydatow (null, gdy brak). */
    public static MonsterType pick(long day, Collection<MonsterType> candidates) {
        List<MonsterType> ok = new ArrayList<>();
        for (MonsterType t : candidates) if (t != null && !t.boss()) ok.add(t);
        if (ok.isEmpty()) return null;
        ok.sort((a, b) -> a.key().compareTo(b.key()));
        // staly mieszacz, zeby kolejne dni nie dawaly sasiednich potworow
        Random r = new Random(day * 0x9E3779B97F4A7C15L + 0x5BD1E995L);
        return ok.get(r.nextInt(ok.size()));
    }

    public static long boostExp(long exp) { return exp + exp * EXP_PERCENT / 100; }

    /** Szansa lupu w promilach po premii (najwyzej 100%). */
    public static int boostChance(int permille) { return Math.min(1000, permille * (100 + LOOT_PERCENT) / 100); }

    public static String describe(MonsterType t) {
        return t == null ? "brak" : t.name() + " (+" + EXP_PERCENT + "% exp, +" + LOOT_PERCENT + "% szansy na łup)";
    }
}
