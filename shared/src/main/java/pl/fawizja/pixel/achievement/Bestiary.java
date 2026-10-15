package pl.fawizja.pixel.achievement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bestiariusz postaci: ile potworow kazdego rodzaju zabila (klucze z monsters.csv) i ktore z nich to bossowie. */
public final class Bestiary {

    private final Map<String, Integer> kills = new LinkedHashMap<>();
    private final Set<String> bosses = new LinkedHashSet<>();

    public void record(String monsterKey, boolean boss) {
        kills.merge(monsterKey, 1, (a, b) -> a == Integer.MAX_VALUE ? a : a + b);
        if (boss) bosses.add(monsterKey);
    }

    public int kills(String monsterKey) { return kills.getOrDefault(monsterKey, 0); }

    public long total() {
        long t = 0;
        for (int n : kills.values()) t += n;
        return t;
    }

    /** Ile roznych rodzajow potworow zabito. */
    public int species() { return kills.size(); }

    /** Ilu roznych bossow pokonano. */
    public int bossesDefeated() { return bosses.size(); }

    public boolean isBoss(String monsterKey) { return bosses.contains(monsterKey); }

    /** Rodzaje potworow od najczesciej zabijanych. */
    public List<String> mostKilled() {
        List<String> out = new ArrayList<>(kills.keySet());
        out.sort((a, b) -> Integer.compare(kills.get(b), kills.get(a)));
        return out;
    }

    /** Odczyt zapisu ("kill.wolf" = "12" albo "3/B" dla bossa). */
    public void restore(String monsterKey, String value) {
        boolean boss = value.endsWith("/B");
        int n = Integer.parseInt(boss ? value.substring(0, value.length() - 2) : value);
        if (n <= 0) return;
        kills.put(monsterKey, n);
        if (boss) bosses.add(monsterKey);
    }

    public String encodeValue(String monsterKey) { return kills(monsterKey) + (bosses.contains(monsterKey) ? "/B" : ""); }

    public Set<String> keys() { return kills.keySet(); }

    public void copyFrom(Bestiary o) {
        kills.clear();
        kills.putAll(o.kills);
        bosses.clear();
        bosses.addAll(o.bosses);
    }
}
