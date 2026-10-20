package pl.fawizja.pixel.ranking;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ranking tygodnia: doswiadczenie zdobyte od poniedzialku (tydzien ISO, UTC). Serwer zapisuje stan postaci przy kazdym
 * zapisie ({@link #observe}); pierwsza obserwacja w tygodniu staje sie punktem odniesienia.
 * Zapis w weekly.txt: "tydzien;imie;doswiadczenie na poczatku" (np. "2026-43;Ola;1500000").
 */
public final class WeeklyRanking {

    public static final String TITLE = "Ranking tygodnia";

    private record Base(String week, String name, long exp) {}

    private final Map<String, Base> byName = new HashMap<>();

    /** Numer tygodnia ISO ("2026-43") dla chwili w ms epoki. */
    public static String week(long epochMs) {
        java.time.ZonedDateTime t = Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC);
        return t.get(IsoFields.WEEK_BASED_YEAR) + "-" + t.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    public static boolean isWeeklyArg(String arg) {
        if (arg == null) return false;
        String a = java.text.Normalizer.normalize(arg.trim().toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return a.equals("tydzien") || a.equals("tygodnia") || a.equals("week");
    }

    /** Zapamietuje punkt odniesienia, jesli w tym tygodniu postaci jeszcze nie bylo. */
    public synchronized void observe(String name, long exp, long nowMs) {
        String w = week(nowMs);
        Base b = byName.get(key(name));
        if (b == null || !b.week().equals(w)) byName.put(key(name), new Base(w, name, exp));
    }

    /** Doswiadczenie zdobyte w tym tygodniu (0 bez punktu odniesienia z tego tygodnia). */
    public synchronized long gained(String name, long exp, long nowMs) {
        Base b = byName.get(key(name));
        return b == null || !b.week().equals(week(nowMs)) ? 0 : Math.max(0, exp - b.exp());
    }

    public synchronized void remove(String name) { byName.remove(key(name)); }

    /** Miejsca wg zdobytego doswiadczenia (tylko > 0); level w wierszu = zdobyte doswiadczenie. */
    public synchronized RankingView view(Ranking ranking, String me, int shown, long nowMs) {
        record G(Ranking.Entry e, long gained) {}
        List<G> all = new ArrayList<>();
        for (Ranking.Entry e : ranking.top(Integer.MAX_VALUE, null)) {
            long g = gained(e.name(), e.experience(), nowMs);
            if (g > 0) all.add(new G(e, g));
        }
        all.sort((a, b) -> a.gained() != b.gained() ? Long.compare(b.gained(), a.gained()) : a.e().name().compareToIgnoreCase(b.e().name()));
        List<RankingView.Row> rows = new ArrayList<>();
        int mine = 0;
        for (int i = 0; i < all.size(); i++) {
            G g = all.get(i);
            if (g.e().name().equalsIgnoreCase(me)) mine = i + 1;
            if (i < shown) rows.add(new RankingView.Row(i + 1, g.e().name(), g.e().vocation().displayName(), (int) Math.min(Integer.MAX_VALUE, g.gained())));
        }
        return new RankingView(TITLE, mine, all.size(), rows);
    }

    public synchronized String encode() {
        StringBuilder sb = new StringBuilder();
        for (Base b : byName.values()) sb.append(b.week()).append(';').append(b.name()).append(';').append(b.exp()).append('\n');
        return sb.toString();
    }

    public static WeeklyRanking decode(String text) {
        WeeklyRanking w = new WeeklyRanking();
        if (text == null) return w;
        for (String line : text.split("\n")) {
            String[] f = line.split(";");
            if (f.length != 3 || f[1].isBlank()) continue;
            try {
                w.byName.put(key(f[1]), new Base(f[0], f[1].trim(), Long.parseLong(f[2].trim())));
            } catch (NumberFormatException e) {
                // pomijamy uszkodzona linie
            }
        }
        return w;
    }

    private static String key(String name) { return name.trim().toLowerCase(Locale.ROOT); }
}
