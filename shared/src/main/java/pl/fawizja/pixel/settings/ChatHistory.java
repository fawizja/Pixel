package pl.fawizja.pixel.settings;

import java.util.ArrayList;
import java.util.List;

/** Historia wyslanych wiadomosci czatu (strzalki w gore i w dol w polu czatu), najwyzej {@link #MAX}. */
public final class ChatHistory {
    public static final int MAX = 20;

    private final List<String> lines = new ArrayList<>();
    /** Pozycja przegladania: lines.size() = nowy szkic. */
    private int cursor;

    public void add(String text) {
        if (text == null || text.isBlank()) return;
        String t = text.trim();
        lines.remove(t); // powtorzenie przenosi na koniec
        lines.add(t);
        while (lines.size() > MAX) lines.remove(0);
        cursor = lines.size();
    }

    /** Starsza wiadomosc (zostaje na najstarszej) albo null, gdy historia pusta. */
    public String older() {
        if (lines.isEmpty()) return null;
        cursor = Math.max(0, cursor - 1);
        return lines.get(cursor);
    }

    /** Nowsza wiadomosc; za najnowsza zwraca pusty szkic. */
    public String newer() {
        if (cursor >= lines.size() - 1) {
            cursor = lines.size();
            return "";
        }
        return lines.get(++cursor);
    }

    public void resetCursor() { cursor = lines.size(); }

    public String last() { return lines.isEmpty() ? null : lines.get(lines.size() - 1); }

    public int size() { return lines.size(); }
}
