package pl.fawizja.pixel.mail;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Przesylki czekajace na odbiorcow (serwer): trafiaja do depozytu odbiorcy, gdy jest w grze i ma miejsce,
 * a w przeciwnym razie czekaja tutaj do jego nastepnego wejscia. Zapis: "P odbiorca;nadawca;przedmiot;ilosc".
 */
public final class Mailbox {

    public record Parcel(String to, String from, int itemId, int count) {}

    /** Najwiecej czekajacych przesylek na odbiorce. */
    public static final int MAX_WAITING = 50;

    private final Map<String, List<Parcel>> waiting = new LinkedHashMap<>();

    public synchronized boolean canAccept(String to) { return waiting(to).size() < MAX_WAITING; }

    public synchronized void add(Parcel p) {
        waiting.computeIfAbsent(key(p.to()), k -> new ArrayList<>()).add(p);
    }

    public synchronized List<Parcel> waiting(String to) {
        List<Parcel> l = waiting.get(key(to));
        return l == null ? new ArrayList<>() : new ArrayList<>(l);
    }

    /** Zabiera wszystkie przesylki odbiorcy (niedostarczone wywolujacy oddaje przez {@link #add}). */
    public synchronized List<Parcel> take(String to) {
        List<Parcel> l = waiting.remove(key(to));
        return l == null ? new ArrayList<>() : l;
    }

    public synchronized void forget(String name) { waiting.remove(key(name)); }

    public synchronized String encode() {
        StringBuilder sb = new StringBuilder();
        for (List<Parcel> l : waiting.values()) {
            for (Parcel p : l) {
                sb.append("P ").append(p.itemId()).append(';').append(p.count()).append(';').append(p.from().replace(";", ""))
                        .append(';').append(p.to()).append('\n');
            }
        }
        return sb.toString();
    }

    public static Mailbox decode(String text) {
        Mailbox m = new Mailbox();
        if (text == null) return m;
        for (String line : text.split("\n")) {
            if (!line.startsWith("P ")) continue;
            try {
                String[] f = line.substring(2).split(";", 4);
                m.add(new Parcel(f[3], f[2], Integer.parseInt(f[0]), Integer.parseInt(f[1])));
            } catch (RuntimeException e) {
                // pomijamy uszkodzona linie
            }
        }
        return m;
    }

    private static String key(String name) { return name.toLowerCase(Locale.ROOT); }
}
