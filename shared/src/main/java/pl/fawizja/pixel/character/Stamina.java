package pl.fawizja.pixel.character;

/**
 * Stamina jak w Tibii (TibiaWiki "Stamina"): najwyzej 42:00 godzin. Polowanie (zdobywanie doswiadczenia
 * z potworow) zuzywa 1 minute na kazda minute polowania. Ostatnie 3 godziny (powyzej 39:00, "happy hour")
 * daja kontom premium 150% doswiadczenia, ponizej 14:00 jest go o polowe mniej, a przy 0 - wcale.
 * Po wylogowaniu: pierwsze 10 minut nic, potem 1 minuta staminy na 3 minuty offline do 39:00 i 1 minuta
 * na 6 minut offline powyzej 39:00 (od 39 do 42 godzin trzeba 18 h 10 min offline).
 * Online stamina sie nie odnawia. Liczona na serwerze (sesja gracza), klient tylko ja pokazuje.
 */
public final class Stamina {

    public static final int MAX_MINUTES = 42 * 60;
    /** Powyzej tej wartosci (39:00) zielona stamina: premium dostaje 150% doswiadczenia. */
    public static final int BONUS_ABOVE_MINUTES = 39 * 60;
    /** Od tej wartosci (14:00) w dol pomaranczowa stamina: polowa doswiadczenia. */
    public static final int LOW_AT_MINUTES = 14 * 60;
    /** Odnawianie zaczyna sie po 10 minutach offline. */
    public static final long OFFLINE_GRACE_MS = 10 * 60_000L;
    /** Ponizej 39:00: 1 minuta staminy na 3 minuty offline. */
    public static final long REGEN_MS_PER_MINUTE = 3 * 60_000L;
    /** Powyzej 39:00: 1 minuta staminy na 6 minut offline. */
    public static final long BONUS_REGEN_MS_PER_MINUTE = 6 * 60_000L;
    /** Jedna minuta staminy na minute polowania. */
    public static final long USE_INTERVAL_MS = 60_000L;

    /** Strefa paska (kolor jak w Tibii). */
    public enum Zone { BONUS, NORMAL, LOW, EMPTY }

    private int minutes = MAX_MINUTES;
    /** Czas gry, od ktorego kolejne doswiadczenie z potwora zuzyje nastepna minute (nie zapisywany). */
    private long nextUseMs = Long.MIN_VALUE;
    /** Czas zegara sciennego zapisu (do odnowienia po zalogowaniu); 0 = brak. */
    private long savedAtWallMs;

    public int minutes() { return minutes; }

    public void setMinutes(int m) { minutes = Math.max(0, Math.min(MAX_MINUTES, m)); }

    public long savedAtWallMs() { return savedAtWallMs; }

    void setSavedAtWallMs(long t) { savedAtWallMs = Math.max(0, t); }

    public Zone zone() {
        if (minutes <= 0) return Zone.EMPTY;
        if (minutes <= LOW_AT_MINUTES) return Zone.LOW;
        if (minutes > BONUS_ABOVE_MINUTES) return Zone.BONUS;
        return Zone.NORMAL;
    }

    /** "hh:mm", np. "42:00". */
    public String display() { return format(minutes); }

    public static String format(int minutes) {
        int m = Math.max(0, minutes);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", m / 60, m % 60);
    }

    /** Doswiadczenie po modyfikatorze staminy (bez zuzycia). */
    public long modify(long exp, boolean premium) {
        if (exp <= 0) return exp;
        switch (zone()) {
            case EMPTY: return 0;
            case LOW: return exp / 2;
            case BONUS: return premium ? exp * 3 / 2 : exp;
            default: return exp;
        }
    }

    /**
     * Doswiadczenie z potwora: modyfikator wg obecnej staminy, potem zuzycie (najwyzej 1 minuta na minute gry).
     * Zwraca doswiadczenie do przyznania.
     */
    public long hunt(long exp, boolean premium, long nowMs) {
        long out = modify(exp, premium);
        if (exp > 0 && minutes > 0 && (nextUseMs == Long.MIN_VALUE || nowMs >= nextUseMs)) {
            minutes--;
            nextUseMs = nowMs + USE_INTERVAL_MS;
        }
        return out;
    }

    /** Odnowienie po czasie offline (ms) wg zasad Tibii; zwraca odzyskane minuty. */
    public int regenerateOffline(long offlineMs) {
        long t = offlineMs - OFFLINE_GRACE_MS;
        if (t <= 0 || minutes >= MAX_MINUTES) return 0;
        int before = minutes;
        long m = minutes;
        if (m < BONUS_ABOVE_MINUTES) {
            long gain = Math.min(BONUS_ABOVE_MINUTES - m, t / REGEN_MS_PER_MINUTE);
            m += gain;
            t -= gain * REGEN_MS_PER_MINUTE;
        }
        if (m >= BONUS_ABOVE_MINUTES && t > 0) m = Math.min(MAX_MINUTES, m + t / BONUS_REGEN_MS_PER_MINUTE);
        minutes = (int) m;
        return minutes - before;
    }

    /** Po zalogowaniu: odnowienie za czas od zapisu (zegar scienny); zwraca odzyskane minuty. */
    public int onLogin(long nowWallMs) {
        long saved = savedAtWallMs;
        savedAtWallMs = 0;
        if (saved <= 0 || nowWallMs <= saved) return 0;
        return regenerateOffline(nowWallMs - saved);
    }

    /** Postep paska 0..1. */
    public float fraction() { return minutes / (float) MAX_MINUTES; }

    /** Kopia stanu z serwera (klient tylko pokazuje): bez znacznika odnowienia offline. */
    void copyFrom(Stamina o) {
        minutes = o.minutes;
    }

    /**
     * Zapis: "minuty,czasZapisu". Postac wczytana, ale jeszcze niezalogowana (np. serwer pobiera czynsz od gracza
     * offline) zachowuje pierwotny czas wylogowania, zeby nie stracic odnowienia.
     */
    String encode(long wallMs) { return minutes + "," + (savedAtWallMs > 0 ? savedAtWallMs : wallMs); }

    /** Odczyt tolerancyjny: zle dane = pelna stamina. */
    void restore(String v) {
        if (v == null || v.isBlank()) return;
        try {
            String[] f = v.split(",");
            setMinutes(Integer.parseInt(f[0].trim()));
            if (f.length > 1) setSavedAtWallMs(Long.parseLong(f[1].trim()));
        } catch (NumberFormatException e) {
            minutes = MAX_MINUTES;
        }
    }
}
