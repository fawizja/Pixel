package pl.fawizja.pixel.quest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Postep zadan postaci: przyjete (z licznikiem) i ukonczone. Zapisywany razem z postacia. */
public final class QuestLog {

    public enum State { AVAILABLE, ACTIVE, DONE }

    private final Map<String, Integer> active = new LinkedHashMap<>();
    private final Map<String, Boolean> done = new LinkedHashMap<>();

    public State state(String key) {
        if (done.containsKey(key)) return State.DONE;
        return active.containsKey(key) ? State.ACTIVE : State.AVAILABLE;
    }

    public int progress(String key) { return active.getOrDefault(key, done.containsKey(key) ? Integer.MAX_VALUE : 0); }

    public Map<String, Integer> active() { return Collections.unmodifiableMap(active); }
    public java.util.Set<String> done() { return Collections.unmodifiableSet(done.keySet()); }

    void start(String key) { active.put(key, 0); }

    void advance(String key, int by, int cap) { active.computeIfPresent(key, (k, v) -> Math.min(cap, v + by)); }

    void finish(String key) {
        active.remove(key);
        done.put(key, Boolean.TRUE);
    }

    /** Kopia stanu z innej postaci (klient online). */
    public void mirror(QuestLog o) {
        active.clear();
        active.putAll(o.active);
        done.clear();
        done.putAll(o.done);
    }

    /** Odtworzenie z zapisu. */
    public void restore(String key, boolean finished, int progress) {
        if (finished) {
            active.remove(key);
            done.put(key, Boolean.TRUE);
        } else if (!done.containsKey(key)) {
            active.put(key, Math.max(0, progress));
        }
    }
}
