package pl.fawizja.pixel.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Dziennik zdarzen gry (ostatnie komunikaty dla UI). */
public final class EventLog {

    public enum Kind { INFO, WARNING, LOOT, SYSTEM }

    public record Entry(long timeMs, Kind kind, String text) {}

    private final int capacity;
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private long total;

    public EventLog(int capacity) {
        this.capacity = capacity;
    }

    public void add(long timeMs, Kind kind, String text) {
        entries.addLast(new Entry(timeMs, kind, text));
        total++;
        while (entries.size() > capacity) entries.removeFirst();
    }

    public List<Entry> latest(int n) {
        List<Entry> all = new ArrayList<>(entries);
        return all.subList(Math.max(0, all.size() - n), all.size());
    }

    public int size() { return entries.size(); }

    /** Ile wpisow dodano od poczatku (serwer przesyla klientowi tylko nowe). */
    public long total() { return total; }
}
