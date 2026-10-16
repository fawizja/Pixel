package pl.fawizja.pixel.daily;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import pl.fawizja.pixel.monster.MonsterType;

/**
 * Boss tygodnia: jeden boss wspolny dla wszystkich graczy, zmienia sie co 7 dni.
 * Pierwsze pokonanie w tygodniu daje kazdej postaci zloto na konto. Wybor zalezy tylko od tygodnia.
 */
public final class WeeklyBoss {
    public static final long MIN_GOLD = 5_000;

    private WeeklyBoss() {}

    public static long week(long day) { return Math.floorDiv(day, 7); }

    /** Boss tygodnia sposrod kandydatow (null, gdy brak bossow). */
    public static MonsterType pick(long week, Collection<MonsterType> candidates) {
        List<MonsterType> ok = new ArrayList<>();
        for (MonsterType t : candidates) if (t != null && t.boss() && !t.isElite()) ok.add(t);
        if (ok.isEmpty()) return null;
        ok.sort((a, b) -> a.key().compareTo(b.key()));
        Random r = new Random(week * 0x2545F4914F6CDD1DL + 0x1B873593L);
        return ok.get(r.nextInt(ok.size()));
    }

    /** Nagroda: dwukrotnosc doswiadczenia bossa w zlocie, zaokraglona do 100, co najmniej {@link #MIN_GOLD}. */
    public static long rewardGold(MonsterType t) {
        return Math.max(MIN_GOLD, (t.experience() * 2 + 99) / 100 * 100);
    }
}
