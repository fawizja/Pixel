package pl.fawizja.pixel.ranking;

import java.util.ArrayList;
import java.util.List;

/**
 * Ranking widziany przez klienta: komunikat serwera "RANK tytul mojeMiejsce wszystkich miejsce;imie;profesja;poziom ...".
 * mojeMiejsce = 0, gdy pytajacego nie ma w tym rankingu (np. inna profesja).
 */
public record RankingView(String title, int mine, int total, List<Row> rows) {

    public record Row(int place, String name, String vocation, int level) {}

    /** Pola komunikatu do wyslania (bez nazwy "RANK"). */
    public String[] encode() {
        String[] out = new String[3 + rows.size()];
        out[0] = title;
        out[1] = String.valueOf(mine);
        out[2] = String.valueOf(total);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            out[3 + i] = r.place() + ";" + r.name() + ";" + r.vocation() + ";" + r.level();
        }
        return out;
    }

    /** Odczyt komunikatu; null dla blednej linii (zle wiersze sa pomijane). */
    public static RankingView parse(String[] m) {
        if (m == null || m.length < 4 || !"RANK".equals(m[0])) return null;
        try {
            int mine = Integer.parseInt(m[2]);
            int total = Integer.parseInt(m[3]);
            List<Row> rows = new ArrayList<>();
            for (int i = 4; i < m.length; i++) {
                String[] f = m[i].split(";");
                if (f.length != 4) continue;
                rows.add(new Row(Integer.parseInt(f[0]), f[1], f[2], Integer.parseInt(f[3])));
            }
            return new RankingView(m[1], mine, total, rows);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
