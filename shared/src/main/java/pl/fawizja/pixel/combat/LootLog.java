package pl.fawizja.pixel.combat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Ostatnie lupy z pokonanych potworow (okno /lupy), takze to, co zostalo w ciele. Tylko w pamieci. */
public final class LootLog {
    public static final int MAX = 30;

    private record Entry(long timeMs, String monster, String items, boolean rare) {}

    private final Deque<Entry> entries = new ArrayDeque<>();

    public void record(long timeMs, String monster, List<String> items, boolean rare) {
        entries.addFirst(new Entry(timeMs, monster, items.isEmpty() ? "nic" : String.join(", ", items), rare));
        while (entries.size() > MAX) entries.removeLast();
    }

    public int size() { return entries.size(); }

    public void clear() { entries.clear(); }

    /** Wiersze {potwor i kiedy, lup}; najnowsze na gorze. */
    public List<String[]> rows(long nowMs) {
        List<String[]> out = new ArrayList<>();
        for (Entry e : entries) {
            out.add(new String[] {e.monster() + " (" + ago(nowMs - e.timeMs()) + ")", (e.rare() ? "rzadki łup! " : "") + e.items()});
        }
        return out;
    }

    static String ago(long ms) {
        long s = Math.max(0, ms) / 1000;
        if (s < 60) return "przed chwilą";
        if (s < 3600) return (s / 60) + " min temu";
        return (s / 3600) + " h temu";
    }
}
