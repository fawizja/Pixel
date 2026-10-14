package pl.fawizja.pixel.character;

import java.util.EnumMap;

/**
 * Umiejetnosci rosnace z uzyciem. Kazdy poziom wymaga wiecej prob (krzywa wykladnicza
 * z ograniczeniem), poziom umiejetnosci jest ograniczony do MAX_SKILL.
 */
public final class Skills {

    public static final int START_SKILL = 10;
    public static final int MAX_SKILL = 200;

    private final EnumMap<Skill, Integer> levels = new EnumMap<>(Skill.class);
    private final EnumMap<Skill, Long> tries = new EnumMap<>(Skill.class);

    public Skills() {
        for (Skill s : Skill.values()) {
            levels.put(s, s == Skill.MAGIC ? 0 : START_SKILL);
            tries.put(s, 0L);
        }
    }

    public int level(Skill s) { return levels.get(s); }
    public long tries(Skill s) { return tries.get(s); }

    /** Liczba prob potrzebna do przejscia z poziomu level na level+1. */
    public static long triesForNext(int level) {
        double v = 50.0 * Math.pow(1.1, Math.max(0, level - START_SKILL));
        return (long) Math.min(1e15, Math.ceil(v));
    }

    /** Dodaje proby i zwraca liczbe zdobytych poziomow. */
    public int addTries(Skill s, long amount) {
        if (amount <= 0) return 0;
        int lvl = levels.get(s);
        long t = tries.get(s) + amount;
        int gained = 0;
        while (lvl < MAX_SKILL && t >= triesForNext(lvl)) {
            t -= triesForNext(lvl);
            lvl++;
            gained++;
        }
        if (lvl >= MAX_SKILL) t = 0;
        levels.put(s, lvl);
        tries.put(s, t);
        return gained;
    }

    public void set(Skill s, int level, long progress) {
        if (level < 0 || level > MAX_SKILL || progress < 0) throw new IllegalArgumentException("bad skill " + s);
        levels.put(s, level);
        tries.put(s, progress);
    }
}
