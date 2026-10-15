package pl.fawizja.pixel.daily;

/**
 * Nagroda za codzienne wejscie do gry: seria 1..7 dni (po 7. dniu od nowa), przerwa zeruje serie.
 * Nagroda w zlocie na konto, rosnie z dniem serii i poziomem postaci. Zapis "dzien;seria".
 */
public final class LoginStreak {

    public static final int DAYS = 7;
    private static final long[] BASE = {300, 500, 800, 1_000, 1_500, 2_000, 5_000};

    private long lastDay = -1;
    private int streak;

    public long lastDay() { return lastDay; }
    public int streak() { return streak; }

    /** Odbior za dzien day; zwraca dzien serii (1..7) albo 0, gdy dzis juz odebrano. */
    public int claim(long day) {
        if (day == lastDay) return 0;
        streak = (lastDay >= 0 && day == lastDay + 1) ? streak % DAYS + 1 : 1;
        lastDay = day;
        return streak;
    }

    /** Wycofanie odbioru (np. konto pelne) - przywraca poprzedni stan. */
    public void restoreState(long day, int s) {
        lastDay = day;
        streak = s;
    }

    public static long rewardGold(int streakDay, int level) {
        if (streakDay < 1 || streakDay > DAYS) return 0;
        return BASE[streakDay - 1] * Math.max(1, level / 10);
    }

    public String encode() { return lastDay < 0 ? "" : lastDay + ";" + streak; }

    public void restore(String text) {
        lastDay = -1;
        streak = 0;
        if (text == null || text.isBlank()) return;
        String[] f = text.trim().split(";");
        try {
            long d = Long.parseLong(f[0]);
            int s = f.length > 1 ? Integer.parseInt(f[1]) : 1;
            if (s >= 1 && s <= DAYS) {
                lastDay = d;
                streak = s;
            }
        } catch (NumberFormatException e) {
            // uszkodzony wpis - seria od nowa
        }
    }

    public void copyFrom(LoginStreak o) {
        lastDay = o.lastDay;
        streak = o.streak;
    }
}
