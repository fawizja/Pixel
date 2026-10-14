package pl.fawizja.pixel.balance;

import pl.fawizja.pixel.vocation.Vocation;

/**
 * Centralne krzywe balansu. Wszystkie obliczenia sa w long/int z kontrola przepelnienia,
 * a wyniki sa ograniczane do sensownych zakresow.
 */
public final class Balance {

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 1000;
    /** Poziom, od ktorego licza sie przyrosty profesji. */
    public static final int VOCATION_LEVEL = 8;
    public static final int HP_AT_VOCATION_LEVEL = 185;
    public static final int MANA_AT_VOCATION_LEVEL = 90;
    public static final int CAPACITY_AT_VOCATION_LEVEL = 470;
    /** Przyrost ponizej poziomu 8 (przed wyborem profesji). */
    public static final int PRE_VOCATION_GAIN = 5;

    public static final int BASE_SPEED = 220;
    /** Gorny limit predkosci ruchu w kafelkach na sekunde (czytelnosc na telefonie). */
    public static final double MAX_TILES_PER_SECOND = 8.0;
    /** Co ile sekund nastepuje tik regeneracji. */
    public static final int REGEN_TICK_SECONDS = 6;

    private static final long[] EXP_TABLE = buildExpTable();

    private Balance() {}

    private static long[] buildExpTable() {
        long[] t = new long[MAX_LEVEL + 2];
        for (int l = 1; l <= MAX_LEVEL + 1; l++) {
            t[l] = computeExperienceForLevel(l);
        }
        return t;
    }

    /**
     * Calkowite doswiadczenie wymagane do osiagniecia poziomu: 50/3 * (L^3 - 6L^2 + 17L - 12).
     * Krzywa szescienna: koszt kolejnego poziomu rosnie kwadratowo, wiec postep spowalnia
     * w kontrolowany sposob. Poziom 8 = 4200 exp, poziom 1000 ~ 1,66e10 (miesci sie w long).
     */
    static long computeExperienceForLevel(int level) {
        if (level <= 1) return 0L;
        long l = level;
        long poly = Math.addExact(Math.subtractExact(Math.multiplyExact(Math.multiplyExact(l, l), l),
                Math.multiplyExact(6L, Math.multiplyExact(l, l))), Math.subtractExact(Math.multiplyExact(17L, l), 12L));
        return Math.multiplyExact(50L, poly) / 3L;
    }

    public static long experienceForLevel(int level) {
        if (level <= MIN_LEVEL) return 0L;
        if (level > MAX_LEVEL + 1) throw new IllegalArgumentException("level > " + (MAX_LEVEL + 1));
        return EXP_TABLE[level];
    }

    /** Maksymalne doswiadczenie, jakie moze miec postac (rowne progowi poziomu maksymalnego). */
    public static long maxExperience() {
        return EXP_TABLE[MAX_LEVEL];
    }

    /** Poziom wynikajacy z doswiadczenia (wyszukiwanie binarne w tablicy). */
    public static int levelForExperience(long experience) {
        if (experience <= 0) return MIN_LEVEL;
        int lo = MIN_LEVEL, hi = MAX_LEVEL;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (EXP_TABLE[mid] <= experience) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    private static int clampLevel(int level) {
        return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
    }

    private static int statAt(int level, int atVocationLevel, int perLevel) {
        int l = clampLevel(level);
        long v = l >= VOCATION_LEVEL
                ? atVocationLevel + (long) (l - VOCATION_LEVEL) * perLevel
                : atVocationLevel - (long) (VOCATION_LEVEL - l) * PRE_VOCATION_GAIN;
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, v));
    }

    public static int maxHealth(Vocation v, int level) {
        return statAt(level, HP_AT_VOCATION_LEVEL, v.hpPerLevel());
    }

    public static int maxMana(Vocation v, int level) {
        return statAt(level, MANA_AT_VOCATION_LEVEL, v.manaPerLevel());
    }

    public static int capacity(Vocation v, int level) {
        return statAt(level, CAPACITY_AT_VOCATION_LEVEL, v.capacityPerLevel());
    }

    /** Regeneracja HP na tik: baza profesji + skladnik rosnacy wolno z poziomem. */
    public static int healthRegenPerTick(Vocation v, int level) {
        return v.baseHpRegen() + clampLevel(level) * v.hpPerLevel() / 400;
    }

    public static int manaRegenPerTick(Vocation v, int level) {
        return v.baseManaRegen() + clampLevel(level) * v.manaPerLevel() / 400;
    }

    /** Szybkosc postaci: rosnie z pierwiastkiem poziomu, wiec na wysokich poziomach nie eksploduje. */
    public static int speed(int level) {
        int l = clampLevel(level);
        return BASE_SPEED + (int) Math.round(25.0 * Math.sqrt(Math.max(0, l - 7)));
    }

    public static double tilesPerSecond(int speed) {
        return Math.min(MAX_TILES_PER_SECOND, Math.max(1.0, speed / 80.0));
    }

    /** Czas jednego kroku w ms; ruch po skosie trwa dluzej. */
    public static long stepDurationMs(int speed, boolean diagonal) {
        double ms = 1000.0 / tilesPerSecond(speed);
        return Math.round(diagonal ? ms * Math.sqrt(2.0) : ms);
    }

    /** Dodawanie doswiadczenia z nasyceniem: nigdy nie przekracza maksimum ani nie przepelnia long. */
    public static long addExperience(long current, long gained) {
        if (gained <= 0) return current;
        long max = maxExperience();
        if (current >= max) return max;
        long sum = current + gained;
        if (sum < 0 || sum > max) return max;
        return sum;
    }
}
