package pl.fawizja.pixel.mail;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Listy do graczy offline: wiadomosc prywatna do kogos, kogo nie ma w grze, czeka i jest pokazywana przy wejsciu.
 * Zapis w letters.txt jako linie "do;od;tekst" (tekst moze zawierac srednik).
 */
public final class Letters {

    /** Tyle listow moze czekac na jednego gracza. */
    public static final int MAX_WAITING = 20;
    public static final int MAX_TEXT = 200;

    public record Letter(String to, String from, String text) {}

    private final Map<String, List<Letter>> waiting = new LinkedHashMap<>();

    public synchronized boolean canAccept(String to) { return waiting(to).size() < MAX_WAITING; }

    public synchronized void add(String to, String from, String text) {
        String t = text.replace('\n', ' ').trim();
        if (t.length() > MAX_TEXT) t = t.substring(0, MAX_TEXT);
        waiting.computeIfAbsent(key(to), k -> new ArrayList<>()).add(new Letter(to.trim(), from.trim(), t));
    }

    public synchronized List<Letter> waiting(String to) {
        return new ArrayList<>(waiting.getOrDefault(key(to), List.of()));
    }

    /** Odbior: zwraca i usuwa czekajace listy. */
    public synchronized List<Letter> take(String to) {
        List<Letter> out = waiting.remove(key(to));
        return out == null ? new ArrayList<>() : out;
    }

    public synchronized void forget(String name) { waiting.remove(key(name)); }

    public synchronized String encode() {
        StringBuilder sb = new StringBuilder();
        for (List<Letter> list : waiting.values()) {
            for (Letter l : list) sb.append(l.to()).append(';').append(l.from()).append(';').append(l.text()).append('\n');
        }
        return sb.toString();
    }

    public static Letters decode(String text) {
        Letters out = new Letters();
        if (text == null) return out;
        for (String line : text.split("\n")) {
            String[] f = line.split(";", 3);
            if (f.length != 3 || f[0].isBlank() || f[1].isBlank() || f[2].isBlank()) continue;
            out.add(f[0], f[1], f[2]);
        }
        return out;
    }

    private static String key(String name) { return name.trim().toLowerCase(Locale.ROOT); }
}
