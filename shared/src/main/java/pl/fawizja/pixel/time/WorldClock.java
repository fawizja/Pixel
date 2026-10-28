package pl.fawizja.pixel.time;

/**
 * Zegar swiata gry liczony z czasu serwera (nie z zegara telefonu).
 * Doba gry trwa 6 godzin czasu rzeczywistego: 3 h jasno (05:30-17:30 gry) i 3 h ciemno (wczesniej 1 h + 1 h).
 * Oswietlenie zmienia sie plynnie, a noc ma minimalna jasnosc dla czytelnosci.
 */
public final class WorldClock {

    public static final int MINUTES_PER_DAY = 24 * 60;
    /** Doba gry w ms czasu rzeczywistego: 3 h dnia + 3 h nocy (wlasciciel gry, 28.10). */
    public static final long REAL_DAY_MS = 6L * 60L * 60L * 1000L;
    /** Godzina gry w ms czasu rzeczywistego (15 min). */
    public static final long GAME_HOUR_MS = REAL_DAY_MS / 24;

    private final long epochMs;
    private final long realMsPerGameDay;
    private final float nightAmbient;

    public WorldClock(long epochMs, long realMsPerGameDay, float nightAmbient) {
        if (realMsPerGameDay <= 0) throw new IllegalArgumentException("day length");
        if (nightAmbient < 0f || nightAmbient > 1f) throw new IllegalArgumentException("nightAmbient");
        this.epochMs = epochMs;
        this.realMsPerGameDay = realMsPerGameDay;
        this.nightAmbient = nightAmbient;
    }

    public static WorldClock standard() {
        return new WorldClock(0L, REAL_DAY_MS, 0.38f);
    }

    /** Minuta doby gry (0..1439) dla czasu serwera w ms. */
    public int minuteOfDay(long serverTimeMs) {
        long elapsed = Math.floorMod(serverTimeMs - epochMs, realMsPerGameDay);
        return (int) (elapsed * MINUTES_PER_DAY / realMsPerGameDay);
    }

    public static DayPhase phase(int minuteOfDay) {
        int h = minuteOfDay / 60;
        if (h == 5) return DayPhase.DAWN;
        if (h >= 6 && h < 17) return DayPhase.DAY;
        if (h == 17) return DayPhase.DUSK;
        return DayPhase.NIGHT;
    }

    /** Jasnosc otoczenia 0..1 (1 = pelny dzien), plynnie interpolowana o swicie i zmierzchu. */
    public float ambientLight(int minuteOfDay) {
        int m = Math.floorMod(minuteOfDay, MINUTES_PER_DAY);
        float day = 1f;
        float night = nightAmbient;
        if (m >= 5 * 60 && m < 6 * 60) return lerp(night, day, smooth((m - 300) / 60f));
        if (m >= 6 * 60 && m < 17 * 60) return day;
        if (m >= 17 * 60 && m < 18 * 60) return lerp(day, night, smooth((m - 1020) / 60f));
        return night;
    }

    public float nightAmbient() { return nightAmbient; }

    private static float smooth(float t) {
        float c = Math.max(0f, Math.min(1f, t));
        return c * c * (3f - 2f * c);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
