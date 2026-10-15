package pl.fawizja.pixel.market;

import java.util.ArrayList;
import java.util.List;

/**
 * Rynek widziany przez klienta: "MARKET wszystkich szukane id;przedmiot;ilosc;cena;sprzedawca;moja ...".
 * szukane = "-" gdy bez filtra; moja = 1 dla wlasnych ofert.
 */
public record MarketView(int total, String search, List<Row> rows) {

    public record Row(int id, String item, int count, long price, String seller, boolean mine) {}

    public String[] encode() {
        String[] out = new String[2 + rows.size()];
        out[0] = String.valueOf(total);
        out[1] = search == null || search.isBlank() ? "-" : search;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            out[2 + i] = r.id() + ";" + r.item() + ";" + r.count() + ";" + r.price() + ";" + (r.mine() ? 1 : 0) + ";" + r.seller();
        }
        return out;
    }

    public static MarketView parse(String[] m) {
        if (m == null || m.length < 3 || !"MARKET".equals(m[0])) return null;
        try {
            List<Row> rows = new ArrayList<>();
            for (int i = 3; i < m.length; i++) {
                String[] f = m[i].split(";", 6);
                if (f.length != 6) continue;
                rows.add(new Row(Integer.parseInt(f[0]), f[1], Integer.parseInt(f[2]), Long.parseLong(f[3]), f[5], "1".equals(f[4])));
            }
            return new MarketView(Integer.parseInt(m[1]), "-".equals(m[2]) ? null : m[2], rows);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
