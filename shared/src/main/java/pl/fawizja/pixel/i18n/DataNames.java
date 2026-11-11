package pl.fawizja.pixel.i18n;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Tlumaczenia nazw i opisow z tabel danych (przedmioty, potwory, NPC, czary, zadania, miejsca): pliki
 * {@code i18n/data_en.csv} i {@code i18n/data_es.csv}, wiersz {@code rodzaj;klucz;pole;tekst} ("name" = nazwa).
 * Zrodlem jest polski (CSV z danymi); brak tlumaczenia = polski tekst.
 */
public final class DataNames {

    private static final Map<Language, Map<String, String>> TABLES = new EnumMap<>(Language.class);

    private DataNames() {}

    private static synchronized Map<String, String> table(Language l) {
        Map<String, String> t = TABLES.get(l);
        if (t == null) {
            t = new HashMap<>();
            if (l != Language.PL) {
                String path = "/i18n/data_" + l.code + ".csv";
                try (InputStream in = DataNames.class.getResourceAsStream(path)) {
                    if (in != null) {
                        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                        String line;
                        while ((line = r.readLine()) != null) {
                            if (line.isBlank() || line.startsWith("#")) continue;
                            String[] f = line.split(";", 4);
                            if (f.length == 4) t.put(f[0] + ";" + f[1] + ";" + f[2], f[3]);
                        }
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("Nie mozna wczytac " + path, e);
                }
            }
            TABLES.put(l, t);
        }
        return t;
    }

    static String name(Language l, String kind, String key, String polish) { return field(l, kind, key, "name", polish); }

    static String field(Language l, String kind, String key, String field, String polish) {
        if (l == Language.PL) return polish;
        String s = table(l).get(kind + ";" + key + ";" + field);
        return s != null ? s : polish;
    }

    /** Czy jest tlumaczenie (testy pokrycia). */
    public static boolean has(Language l, String kind, String key, String field) {
        return l == Language.PL || table(l).containsKey(kind + ";" + key + ";" + field);
    }

    public static int size(Language l) { return table(l).size(); }
}
