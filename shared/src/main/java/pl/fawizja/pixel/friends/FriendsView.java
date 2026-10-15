package pl.fawizja.pixel.friends;

import java.util.ArrayList;
import java.util.List;

/**
 * Lista znajomych widziana przez klienta: komunikat serwera "FRIENDS imie;online;profesja;poziom ...".
 * online = 1 albo 0; profesja "-" i poziom 0, gdy serwer nie zna postaci (np. usunieta).
 */
public record FriendsView(List<Row> rows) {

    public record Row(String name, boolean online, String vocation, int level) {}

    /** Pola komunikatu do wyslania (bez nazwy "FRIENDS"). */
    public String[] encode() {
        String[] out = new String[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            out[i] = r.name() + ";" + (r.online() ? 1 : 0) + ";" + r.vocation() + ";" + r.level();
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
            if (f.length != 4) continue;
            try {
                rows.add(new Row(f[0], "1".equals(f[1]), f[2], Integer.parseInt(f[3])));
            } catch (NumberFormatException e) {
                // pomijamy
            }
        }
        return new FriendsView(rows);
    }
}
