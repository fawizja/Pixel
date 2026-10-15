package pl.fawizja.pixel.daily;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import pl.fawizja.pixel.monster.MonsterType;

/**
 * Zadanie dnia od przewodnika: pokonaj N potworow jednego rodzaju dobranego do poziomu postaci.
 * Jedno na dzien (dzien = data kalendarzowa), nagroda: doswiadczenie i zloto na konto.
 */
public final class DailyTask {

    private long day = Long.MIN_VALUE;
    private String monster;
    private int target;
    private int progress;
    private boolean claimed;

    public long day() { return day; }
    public String monster() { return monster; }
    public int target() { return target; }
    public int progress() { return progress; }
    public boolean claimed() { return claimed; }

    public boolean activeOn(long today) { return day == today && monster != null; }
    public boolean doneOn(long today) { return activeOn(today) && progress >= target; }

    /** Nowe zadanie na dzisiaj (losowanie powtarzalne: imie + dzien). */
    public void assign(String owner, long today, int level, Collection<MonsterType> candidates) {
        MonsterType pick = pick(owner, today, level, candidates);
        Random r = new Random(owner.toLowerCase(java.util.Locale.ROOT).hashCode() * 31L + today);
        r.nextInt();
        this.day = today;
        this.monster = pick == null ? null : pick.key();
        this.target = 20 + r.nextInt(21);
        this.progress = 0;
        this.claimed = false;
    }

    /** Potwor dla poziomu: nie boss, poziom miedzy polowa a 120% poziomu postaci (+5); gdy brak - najblizszy poziomem. */
    static MonsterType pick(String owner, long today, int level, Collection<MonsterType> candidates) {
        List<MonsterType> ok = new ArrayList<>();
        MonsterType nearest = null;
        for (MonsterType t : candidates) {
            if (t.boss()) continue;
            if (t.level() >= Math.max(1, level / 2) && t.level() <= level * 6 / 5 + 5) ok.add(t);
            if (nearest == null || Math.abs(t.level() - level) < Math.abs(nearest.level() - level)) nearest = t;
        }
        if (ok.isEmpty()) return nearest;
        ok.sort((a, b) -> a.key().compareTo(b.key()));
        Random r = new Random(owner.toLowerCase(java.util.Locale.ROOT).hashCode() * 31L + today);
        return ok.get(r.nextInt(ok.size()));
    }

    /** Pokonany potwor; true, gdy wlasnie zaliczono cel. */
    public boolean onKill(String monsterKey, long today) {
        if (!activeOn(today) || claimed || progress >= target || !monsterKey.equals(monster)) return false;
        progress++;
        return progress == target;
    }

    public void claim() { claimed = true; }

    public static long rewardExp(MonsterType t, int count) { return t.experience() * count * 2L; }

    public static long rewardGold(MonsterType t, int count) { return (long) t.level() * count * 3L; }

    /** Zapis: "dzien;potwor;cel;postep;odebrane". */
    public String encode() {
        return day == Long.MIN_VALUE || monster == null ? "" : day + ";" + monster + ";" + target + ";" + progress + ";" + (claimed ? 1 : 0);
    }

    public void restore(String text) {
        if (text == null || text.isBlank()) return;
        String[] f = text.split(";");
        if (f.length != 5) return;
        day = Long.parseLong(f[0]);
        monster = f[1];
        target = Integer.parseInt(f[2]);
        progress = Integer.parseInt(f[3]);
        claimed = "1".equals(f[4]);
    }

    public void copyFrom(DailyTask o) {
        day = o.day;
        monster = o.monster;
        target = o.target;
        progress = o.progress;
        claimed = o.claimed;
    }
}
