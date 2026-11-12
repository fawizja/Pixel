package pl.fawizja.pixel.net;

/**
 * Pomiar opoznienia (ping) do serwera: kolorowa kropka obok FPS. Klient co kilka sekund wysyla PING ze znacznikiem
 * czasu, serwer odsyla PONG z tym samym znacznikiem, a czas obiegu trafia tutaj. Pokazujemy mediane z kilku
 * ostatnich pomiarow, zeby kropka nie migala od jednego zakloconego pakietu; brak odpowiedzi dluzej niz
 * {@link #TIMEOUT_MS} to zly ping. Czysta logika (bez sieci i grafiki), testowana osobno.
 */
public final class PingMeter {

    /** Jakosc polaczenia - kolor kropki. */
    public enum Quality { UNKNOWN, GOOD, MEDIUM, BAD }

    /** Ponizej tej wartosci (ms) ping jest dobry (zielony). */
    public static final int GOOD_BELOW_MS = 100;
    /** Do tej wartosci wlacznie (ms) ping jest sredni (pomaranczowy); powyzej zly (czerwony). */
    public static final int MEDIUM_UP_TO_MS = 250;
    /** Co ile ms klient wysyla PING. */
    public static final long INTERVAL_MS = 2_000L;
    /** Bez PONG tak dlugo = polaczenie uznane za zle. */
    public static final long TIMEOUT_MS = 6_000L;
    private static final int WINDOW = 5;

    private final long[] samples = new long[WINDOW];
    private int count;
    private int next;
    private long lastSentMs = Long.MIN_VALUE;
    private long lastAnswerMs = Long.MIN_VALUE;

    /** Klasyfikacja pojedynczej wartosci: &lt;100 zielony, 100-250 pomaranczowy, &gt;250 czerwony. */
    public static Quality classify(long ms) {
        if (ms < 0) return Quality.UNKNOWN;
        if (ms < GOOD_BELOW_MS) return Quality.GOOD;
        return ms <= MEDIUM_UP_TO_MS ? Quality.MEDIUM : Quality.BAD;
    }

    /** Zapisuje pomiar (ms czasu obiegu) odebrany o chwili nowMs. */
    public synchronized void record(long rttMs, long nowMs) {
        samples[next] = Math.max(0, rttMs);
        next = (next + 1) % WINDOW;
        count = Math.min(WINDOW, count + 1);
        lastAnswerMs = nowMs;
    }

    /** Wyslano PING o chwili nowMs (do wykrycia braku odpowiedzi). */
    public synchronized void sent(long nowMs) {
        if (lastSentMs == Long.MIN_VALUE || lastAnswerMs >= lastSentMs) lastSentMs = nowMs;
    }

    /** Czy czas wyslac kolejny PING. */
    public synchronized boolean due(long nowMs) {
        return lastSentMs == Long.MIN_VALUE || nowMs - lastSentMs >= INTERVAL_MS || nowMs < lastSentMs;
    }

    /** Mediana ostatnich pomiarow (ms) albo -1, gdy jeszcze nic nie odebrano. */
    public synchronized long millis() {
        if (count == 0) return -1;
        long[] a = java.util.Arrays.copyOf(samples, count);
        java.util.Arrays.sort(a);
        return a[count / 2];
    }

    /** Czy PING czeka na odpowiedz dluzej niz {@link #TIMEOUT_MS}. */
    public synchronized boolean timedOut(long nowMs) {
        return lastSentMs != Long.MIN_VALUE && lastAnswerMs < lastSentMs && nowMs - lastSentMs > TIMEOUT_MS;
    }

    /** Biezaca jakosc: z mediany; po przekroczeniu czasu bez odpowiedzi zly; bez danych nieznana. */
    public synchronized Quality quality(long nowMs) {
        if (timedOut(nowMs)) return Quality.BAD;
        long ms = millis();
        return ms < 0 ? Quality.UNKNOWN : classify(ms);
    }

    /** Kolor kropki RGB (0xRRGGBB): zielony, pomaranczowy, czerwony, szary gdy nieznany. */
    public static int color(Quality q) {
        switch (q) {
            case GOOD: return 0x3fd94a;
            case MEDIUM: return 0xf08a20;
            case BAD: return 0xe03a2a;
            default: return 0x8a8a8a;
        }
    }

    /** Napis obok kropki: "45 ms", "?" gdy brak pomiaru, ">6 s" po przekroczeniu czasu. */
    public synchronized String label(long nowMs) {
        if (timedOut(nowMs)) return ">" + TIMEOUT_MS / 1000 + " s";
        long ms = millis();
        return ms < 0 ? "?" : ms + " ms";
    }
}
