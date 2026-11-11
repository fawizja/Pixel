package pl.fawizja.pixel.game;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stroje do zakladki "Outfity" w Sklepie (2026-11-11, Darek: "dodaj w zakladce sklep pozycje outfity na przyszlosc"):
 * wczytywane z data/store_outfits.csv (klucz;nazwa;opis;cena;stan). Na razie wszystkie sa "wkrotce" - grafiki strojow
 * jeszcze nie ma; nowy stroj dodaje sie wierszem w pliku, bez zmian w kodzie okna.
 */
public final class StoreOutfits {

    public static final String RESOURCE = "/data/store_outfits.csv";

    /** Stroj w sklepie; soon = widoczny, ale jeszcze nie do kupienia. */
    public record Entry(String key, String name, String desc, long price, boolean soon) {}

    private static List<Entry> all;

    private StoreOutfits() {}

    /** Stroje z pliku w kolejnosci wierszy (pusta lista, gdy pliku brak). */
    public static synchronized List<Entry> all() {
        if (all == null) all = load();
        return all;
    }

    static List<Entry> load() {
        try (InputStream in = StoreOutfits.class.getResourceAsStream(RESOURCE)) {
            if (in == null) return List.of();
            return Collections.unmodifiableList(parse(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))));
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Wiersze "klucz;nazwa;opis;cena;stan"; komentarze (#) i bledne wiersze pomijane. */
    static List<Entry> parse(BufferedReader r) throws IOException {
        List<Entry> out = new ArrayList<>();
        for (String line; (line = r.readLine()) != null; ) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split(";", -1);
            if (f.length < 5 || f[0].isBlank() || f[1].isBlank()) continue;
            long price;
            try {
                price = Long.parseLong(f[3].trim());
            } catch (NumberFormatException e) {
                continue;
            }
            out.add(new Entry(f[0].trim(), f[1].trim(), f[2].trim(), Math.max(0, price), !"dostepny".equals(f[4].trim())));
        }
        return out;
    }

    public static Entry find(String key) {
        for (Entry e : all()) if (e.key().equals(key)) return e;
        return null;
    }
}
