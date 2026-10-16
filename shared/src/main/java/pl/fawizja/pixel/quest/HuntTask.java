package pl.fawizja.pixel.quest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import pl.fawizja.pixel.monster.MonsterType;

/**
 * Zlecenie lowcy u przewodnika: pokonaj COUNT potworow jednego rodzaju dobranego do poziomu. W odroznieniu
 * od zadania dnia mozna je brac bez konca (jedno naraz) i porzucic; nagroda jest mniejsza.
 */
public final class HuntTask {

    public static final int COUNT = 50;

    private String monster;
    private int progress;
    private int completed;

    public String monster() { return monster; }
    public int progress() { return progress; }
    public int target() { return COUNT; }
    /** Liczba oddanych zlecen (do statystyk i osiagniec w przyszlosci). */
    public int completed() { return completed; }
    public boolean active() { return monster != null; }
    public boolean done() { return active() && progress >= COUNT; }

    /** Nowe zlecenie: losowy potwor (nie boss, nie elita) z przedzialu poziomu jak w zadaniu dnia. */
    public boolean assign(int level, Collection<MonsterType> candidates, Random rng) {
        MonsterType pick = pick(level, candidates, rng);
        if (pick == null) return false;
        monster = pick.key();
        progress = 0;
        return true;
    }

    /** Potwor dla poziomu: nie boss ani elita, poziom od polowy do 120% (+5) poziomu postaci; gdy brak - najblizszy. */
    public static MonsterType pick(int level, Collection<MonsterType> candidates, Random rng) {
        List<MonsterType> ok = new ArrayList<>();
        MonsterType nearest = null;
        for (MonsterType t : candidates) {
            if (t.boss() || t.isElite()) continue;
            if (t.level() >= Math.max(1, level / 2) && t.level() <= level * 6 / 5 + 5) ok.add(t);
            if (nearest == null || Math.abs(t.level() - level) < Math.abs(nearest.level() - level)) nearest = t;
        }
        ok.sort((a, b) -> a.key().compareTo(b.key()));
        return ok.isEmpty() ? nearest : ok.get(rng.nextInt(ok.size()));
    }

    /** Pokonany potwor; true, gdy wlasnie osiagnieto cel. */
    public boolean onKill(String monsterKey) {
        if (!active() || progress >= COUNT || !monsterKey.equals(monster)) return false;
        progress++;
        return progress == COUNT;
    }

    public void drop() {
        monster = null;
        progress = 0;
    }

    /** Oddanie wykonanego zlecenia (licznik oddanych rosnie). */
    public void complete() {
        drop();
        completed++;
    }

    public static long rewardExp(MonsterType t) { return t.experience() * COUNT; }

    public static long rewardGold(MonsterType t) { return (long) t.level() * COUNT * 2L; }

    /** Zapis: "potwor;postep;oddane" ("-" gdy brak zlecenia). */
    public String encode() {
        return monster == null && completed == 0 ? "" : (monster == null ? "-" : monster) + ";" + progress + ";" + completed;
    }

    public void restore(String text) {
        if (text == null || text.isBlank()) return;
        String[] f = text.split(";");
        if (f.length != 3) return;
        monster = f[0].equals("-") ? null : f[0];
        progress = Math.max(0, Math.min(COUNT, Integer.parseInt(f[1])));
        completed = Math.max(0, Integer.parseInt(f[2]));
    }

    public void copyFrom(HuntTask o) {
        monster = o.monster;
        progress = o.progress;
        completed = o.completed;
    }
}
