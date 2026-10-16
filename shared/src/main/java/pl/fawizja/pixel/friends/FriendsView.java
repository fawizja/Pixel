package pl.fawizja.pixel.friends;

import java.util.ArrayList;
import java.util.List;

/**
 * Lista znajomych widziana przez klienta: komunikat serwera "FRIENDS imie;online;profesja;poziom;minuty ...".
 * online = 1 albo 0; profesja "-" i poziom 0, gdy serwer nie zna postaci (np. usunieta);
 * minuty = ile minut temu postac byla w grze (-1 = nie wiadomo; starszy serwer wysyla 4 pola).
 */
public record FriendsView(List<Row> rows) {

    public record Row(String name, boolean online, String vocation, int level, long minutesAgo) {
        public Row(String name, boolean online, String vocation, int level) {
            this(name, online, vocation, level, -1);
        }
    }

    /** "przed chwila", "25 min temu", "3 h temu", "4 dni temu"; pusty tekst, gdy nie wiadomo. */
    public static String describeAgo(long minutes) {
        if (minutes < 0) return "";
        if (minutes < 2) return "przed chwilą";
        if (minutes < 60) return minutes + " min temu";
        if (minutes < 48 * 60) return minutes / 60 + " h temu";
        return minutes / (24 * 60) + " dni temu";
    }

    /** Pola komunikatu do wyslania (bez nazwy "FRIENDS"). */
    public String[] encode() {
        String[] out = new String[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            out[i] = r.name() + ";" + (r.online() ? 1 : 0) + ";" + r.vocation() + ";" + r.level() + ";" + r.minutesAgo();
        }
        return out;
    }

    public int online() {
        int n = 0;
        for (Row r : rows) if (r.online()) n++;
        return n;
    }

    /** Odczyt komunikatu; null dla obcej linii, zle wiersze sa pomijane. */
    public static FriendsView parse(String[] m) {
        if (m == null || m.length < 1 || !"FRIENDS".equals(m[0])) return null;
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < m.length; i++) {
            String[] f = m[i].split(";");
            if (f.length != 4 && f.length != 5) continue;
            try {
                rows.add(new Row(f[0], "1".equals(f[1]), f[2], Integer.parseInt(f[3]), f.length == 5 ? Long.parseLong(f[4]) : -1));
            } catch (NumberFormatException e) {
                // pomijamy
            }
        }
        return new FriendsView(rows);
    }
}
