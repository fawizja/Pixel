package pl.fawizja.pixel.ranking;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Ranking postaci serwera: wedlug doswiadczenia (rowne - alfabetycznie), calosc albo jedna profesja.
 * Serwer buduje go przy starcie z zapisow postaci i aktualizuje przy kazdym zapisie, wiec obejmuje tez graczy offline.
 */
public final class Ranking {

    /** Tyle miejsc pokazuje polecenie /ranking. */
    public static final int SHOWN = 10;

    public record Entry(String name, Vocation vocation, int level, long experience) {}

    private static final Comparator<Entry> ORDER = Comparator.comparingLong(Entry::experience).reversed()
            .thenComparing(e -> e.name().toLowerCase(Locale.ROOT));

    private final Map<String, Entry> byName = new HashMap<>();

    public synchronized void put(Entry e) { byName.put(key(e.name()), e); }

    public synchronized void remove(String name) { byName.remove(key(name)); }

    public synchronized int size(Vocation filter) { return sorted(filter).size(); }

    /** Pierwsze {@code n} miejsc (filter null = wszystkie profesje). */
    public synchronized List<Entry> top(int n, Vocation filter) {
        List<Entry> all = sorted(filter);
        return new ArrayList<>(all.subList(0, Math.min(n, all.size())));
    }

    /** Miejsce postaci (od 1) albo 0, gdy jej nie ma w tym rankingu. */
    public synchronized int position(String name, Vocation filter) {
        List<Entry> all = sorted(filter);
        for (int i = 0; i < all.size(); i++) if (all.get(i).name().equalsIgnoreCase(name)) return i + 1;
        return 0;
    }

    private List<Entry> sorted(Vocation filter) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : byName.values()) if (filter == null || e.vocation() == filter) out.add(e);
        out.sort(ORDER);
        return out;
    }

    /** Profesja z tekstu gracza: "druid", "Mag" (Sorcerer), "mnich", "pal" - po nazwie albo jej poczatku; null gdy brak. */
    public static Vocation vocationFrom(String text) {
        if (text == null) return null;
        String t = plain(text.trim());
        if (t.isEmpty()) return null;
        String[][] aliases = {{"paladyn", "PALADIN"}, {"mag", "SORCERER"}, {"czarodziej", "SORCERER"}, {"mnich", "MONK"}};
        for (String[] a : aliases) if (a[0].startsWith(t) || t.equals(a[0])) return Vocation.valueOf(a[1]);
        for (Vocation v : Vocation.values()) {
            if (plain(v.displayName()).startsWith(t) || plain(v.name()).startsWith(t)) return v;
        }
        return null;
    }

    private static String key(String name) { return name.toLowerCase(Locale.ROOT); }

    private static String plain(String s) {
        String t = s.toLowerCase(Locale.ROOT).replace('ł', 'l');
        return Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
