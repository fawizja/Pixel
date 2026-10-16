package pl.fawizja.pixel.settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ignorowani gracze: ich wiadomosci (czat ogolny, prywatne, gildia, druzyna) nie trafiaja do dziennika ani dymkow.
 * Lista jest na urzadzeniu (jak znaczniki), zapis "Imie;Imie". Ogloszen administratora nie da sie ukryc.
 */
public final class IgnoreList {

    public static final int MAX = 50;

    private final Map<String, String> names = new LinkedHashMap<>(); // klucz bez wielkosci liter -> imie

    public static IgnoreList decode(String s) {
        IgnoreList l = new IgnoreList();
        if (s == null || s.isBlank()) return l;
        for (String n : s.split(";")) if (!n.isBlank()) l.toggle(n);
        return l;
    }

    public String encode() { return String.join(";", names.values()); }

    public List<String> all() { return new ArrayList<>(names.values()); }

    /** Dodaje albo usuwa; zwraca true, gdy gracz jest teraz ignorowany. Pelna lista nie przyjmuje nowych imion. */
    public boolean toggle(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return false;
        String k = n.toLowerCase(Locale.ROOT);
        if (names.remove(k) != null) return false;
        if (names.size() >= MAX) return false;
        names.put(k, n);
        return true;
    }

    public boolean ignores(String name) {
        return name != null && names.containsKey(name.trim().toLowerCase(Locale.ROOT));
    }

    /** Nadawca z pola CHAT: "Ola", "[od Ola]", "[Gildia] Ola", "[Drużyna] Ola"; null dla wlasnych i ogloszen. */
    public static String sender(String from) {
        if (from == null) return null;
        if (from.startsWith("[od ") && from.endsWith("]")) return from.substring(4, from.length() - 1);
        if (from.startsWith("[")) {
            int end = from.indexOf("] ");
            return end < 0 ? null : from.substring(end + 2);
        }
        return from;
    }

    /** Czy wiadomosc od tego nadawcy (pole CHAT) ukryc. */
    public boolean hides(String from) { return ignores(sender(from)); }
}
