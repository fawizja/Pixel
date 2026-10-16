package pl.fawizja.pixel.combat;

/**
 * Alarm niskiego zdrowia: czerwona ramka ekranu i sygnal dzwiekowy, gdy zycie spadnie ponizej {@link #PERCENT}%.
 * Sygnal przy wejsciu w niski stan i potem co {@link #REPEAT_MS}, dopoki zdrowie nie wroci.
 */
public final class LowHealthAlarm {
    public static final int PERCENT = 25;
    public static final long REPEAT_MS = 4000;

    private boolean low;
    private long nextBeepMs;

    public static boolean isLow(int health, int maxHealth) {
        return health > 0 && maxHealth > 0 && health * 100L < (long) maxHealth * PERCENT;
    }

    /** Aktualizacja stanu; zwraca true, gdy teraz zagrac sygnal. */
    public boolean update(int health, int maxHealth, long nowMs) {
        if (!isLow(health, maxHealth)) {
            low = false;
            return false;
        }
        if (!low || nowMs >= nextBeepMs) {
            low = true;
            nextBeepMs = nowMs + REPEAT_MS;
            return true;
        }
        return false;
    }

    public boolean low() { return low; }
}
