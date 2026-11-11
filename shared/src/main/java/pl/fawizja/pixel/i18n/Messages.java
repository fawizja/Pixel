package pl.fawizja.pixel.i18n;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Teksty gry w trzech jezykach (paczki {@code i18n/messages_en|pl|es.properties}, UTF-8) wspolne dla klienta
 * i serwera.
 * <ul>
 * <li>Biezacy jezyk: na kliencie ustawiany z ustawien ({@link #setLanguage}); na serwerze kazde polecenie gracza
 * jest wykonywane w jego jezyku ({@link #runAs}), wiec wiadomosci dla niego powstaja w jego jezyku.</li>
 * <li>Brak klucza w paczce jezyka: wraca polski (zrodlo), a gdy i tam brak - sam klucz.</li>
 * <li>Argumenty: {@code {0}}, {@code {1}}... (zwykle podstawienie, bez zasad MessageFormat).</li>
 * <li>Nazwy danych (przedmioty, potwory, NPC, czary...): {@link #data} - tlumaczenia w {@link DataNames}.</li>
 * </ul>
 * Domyslny jezyk biezacy w bibliotece to polski (zgodnosc ze starymi klientami i testami); klient ustawia
 * jezyk z ustawien (domyslnie angielski dla nowych instalacji).
 */
public final class Messages {

    private static final Map<Language, Properties> BUNDLES = new EnumMap<>(Language.class);
    private static volatile Language current = Language.PL;
    private static final ThreadLocal<Language> OVERRIDE = new ThreadLocal<>();

    private Messages() {}

    private static synchronized Properties bundle(Language l) {
        Properties p = BUNDLES.get(l);
        if (p == null) {
            p = new Properties();
            String path = "/i18n/messages_" + l.code + ".properties";
            try (InputStream in = Messages.class.getResourceAsStream(path)) {
                if (in != null) p.load(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
            } catch (IOException e) {
                throw new IllegalStateException("Nie mozna wczytac " + path, e);
            }
            BUNDLES.put(l, p);
        }
        return p;
    }

    /** Biezacy jezyk watku (nadpisanie z {@link #runAs}) albo globalny. */
    public static Language language() {
        Language o = OVERRIDE.get();
        return o != null ? o : current;
    }

    /** Jezyk globalny (klient: z ustawien). Dziala od razu. */
    public static void setLanguage(Language l) { current = l == null ? Language.DEFAULT : l; }

    public static Language globalLanguage() { return current; }

    /** Wykonuje kod w podanym jezyku tylko dla tego watku (serwer: jezyk gracza). */
    public static void runAs(Language l, Runnable r) {
        Language prev = OVERRIDE.get();
        OVERRIDE.set(l);
        try {
            r.run();
        } finally {
            if (prev == null) OVERRIDE.remove(); else OVERRIDE.set(prev);
        }
    }

    public static <T> T callAs(Language l, Supplier<T> s) {
        Language prev = OVERRIDE.get();
        OVERRIDE.set(l);
        try {
            return s.get();
        } finally {
            if (prev == null) OVERRIDE.remove(); else OVERRIDE.set(prev);
        }
    }

    /** Tekst o kluczu w biezacym jezyku; {@code {0}}, {@code {1}}... zastepowane argumentami. */
    public static String get(String key, Object... args) { return get(language(), key, args); }

    public static String get(Language l, String key, Object... args) {
        String s = bundle(l).getProperty(key);
        if (s == null && l != Language.PL) s = bundle(Language.PL).getProperty(key);
        if (s == null) s = key;
        return args == null || args.length == 0 ? s : format(s, args);
    }

    private static volatile Map<String, String> reverse;

    /**
     * Tlumaczy polski tekst zrodlowy (taki sam jak w paczce polskiej) na biezacy jezyk; nieznany tekst wraca bez
     * zmian. Dla tekstow, ktore stoja w stalych i enumach jako literaly (np. opisy w enumach).
     */
    public static String tr(String polish) {
        if (polish == null) return null;
        Map<String, String> r = reverse;
        if (r == null) {
            r = new java.util.HashMap<>();
            for (String k : bundle(Language.PL).stringPropertyNames()) r.put(bundle(Language.PL).getProperty(k), k);
            reverse = r;
        }
        if (language() == Language.PL) return polish;
        String key = r.get(polish);
        return key == null ? polish : get(key);
    }

    /** Tlumaczenie polskiego tekstu zrodlowego na podany jezyk (nieznany tekst wraca bez zmian). */
    public static String tr(Language l, String polish) {
        if (polish == null || l == Language.PL) return polish;
        tr("");
        String key = reverse.get(polish);
        return key == null ? polish : get(l, key);
    }

    /** Czy tekst jest polskim zrodlem albo jego tlumaczeniem w ktoryms jezyku (porownania niezalezne od jezyka). */
    public static boolean is(String text, String polish) {
        if (text == null) return false;
        for (Language l : Language.values()) if (text.equals(tr(l, polish))) return true;
        return false;
    }

    public static boolean startsWithAny(String text, String polish) {
        if (text == null) return false;
        for (Language l : Language.values()) if (text.startsWith(tr(l, polish))) return true;
        return false;
    }

    public static boolean containsAny(String text, String polish) {
        if (text == null) return false;
        for (Language l : Language.values()) if (text.contains(tr(l, polish))) return true;
        return false;
    }

    public static boolean has(Language l, String key) { return bundle(l).getProperty(key) != null; }

    public static Set<String> keys(Language l) { return bundle(l).stringPropertyNames(); }

    /** Podstawia {0}, {1}... (liczby calkowite z separatorem tysiecy jezyka). */
    static String format(String pattern, Object[] args) {
        StringBuilder sb = new StringBuilder(pattern.length() + 16);
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '{') {
                int end = pattern.indexOf('}', i);
                if (end > i + 1) {
                    try {
                        int n = Integer.parseInt(pattern.substring(i + 1, end));
                        if (n >= 0 && n < args.length) {
                            Object a = args[n];
                            sb.append(a instanceof Long || a instanceof Integer ? number(((Number) a).longValue()) : a);
                            i = end;
                            continue;
                        }
                    } catch (NumberFormatException ignored) {
                        // zwykly nawias
                    }
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** Liczba z separatorem tysiecy wedlug jezyka: 1,234 (en), 1 234 (pl), 1.234 (es). */
    public static String number(long n) { return number(language(), n); }

    public static String number(Language l, long n) {
        String digits = Long.toString(Math.abs(n));
        // Hiszpanski (RAE): liczby do 4 cyfr bez separatora; polski: spacja od 5 cyfr nie jest wymagana - tu zawsze
        StringBuilder sb = new StringBuilder();
        char sep = l == Language.EN ? ',' : l == Language.ES ? '.' : ' ';
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) sb.append(sep);
            sb.append(digits.charAt(i));
        }
        return (n < 0 ? "-" : "") + sb;
    }

    /** Liczba zmiennoprzecinkowa z podana liczba miejsc po przecinku (kropka dziesietna: en; przecinek: pl, es). */
    public static String decimal(double v, int places) {
        NumberFormat f = NumberFormat.getNumberInstance(language().locale);
        f.setMinimumFractionDigits(places);
        f.setMaximumFractionDigits(places);
        f.setGroupingUsed(false);
        return f.format(v);
    }

    /** Nazwa danych (przedmiot, potwor, NPC...) w biezacym jezyku; {@code polish} to nazwa zrodlowa z danych. */
    public static String data(String kind, String key, String polish) {
        return DataNames.name(language(), kind, key, polish);
    }

    /** Jak {@link #data}, dla pola innego niz nazwa (np. "desc", "greeting"). */
    public static String data(String kind, String key, String field, String polish) {
        return DataNames.field(language(), kind, key, field, polish);
    }
}
