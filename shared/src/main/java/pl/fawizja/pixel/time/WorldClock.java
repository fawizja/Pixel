package pl.fawizja.pixel.time;

/**
 * Zegar swiata gry liczony z czasu serwera (nie z zegara telefonu).
 * Domyslnie pelna doba gry trwa 60 minut czasu rzeczywistego.
 * Oswietlenie zmienia sie plynnie, a noc ma minimalna jasnosc dla czytelnosci.
 */
public final class WorldClock {

    public static final int MINUTES_PER_DAY = 24 * 60;

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
        return new WorldClock(0L, 60L * 60L * 1000L, 0.38f);
    }

    /** Minuta doby gry (0..1439) dla czasu serwera w ms. */
    public int minuteOfDay(long serverTimeMs) {
        long elapsed = Math.floorMod(serverTimeMs - epochMs, realMsPerGameDay);
        return (int) (elapsed * MINUTES_PER_DAY / realMsPerGameDay);
    }

    public static DayPhase phase(int minuteOfDay) {
        int h = minuteOfDay / 60;
        if (h >= 5 && h < 7) return DayPhase.DAWN;
        if (h >= 7 && h < 19) return DayPhase.DAY;
        if (h >= 19 && h < 21) return DayPhase.DUSK;
        return DayPhase.NIGHT;
    }

    /** Jasnosc otoczenia 0..1 (1 = pelny dzien), plynnie interpolowana o swicie i zmierzchu. */
    public float ambientLight(int minuteOfDay) {
        int m = Math.floorMod(minuteOfDay, MINUTES_PER_DAY);
        float day = 1f;
        float night = nightAmbient;
        if (m >= 5 * 60 && m < 7 * 60) return lerp(night, day, smooth((m - 300) / 120f));
        if (m >= 7 * 60 && m < 19 * 60) return day;
        if (m >= 19 * 60 && m < 21 * 60) return lerp(day, night, smooth((m - 1140) / 120f));
        return night;
    }

    public float nightAmbient() { return nightAmbient; }

    private static float smooth(float t) {
        float c = Math.max(0f, Math.min(1f, t));
        return c * c * (3f - 2f * c);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
