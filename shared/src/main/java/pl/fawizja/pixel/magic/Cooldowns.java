package pl.fawizja.pixel.magic;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Czasy odnowienia: osobno dla kazdego czaru i dla grup. Stan ulotny (nie jest zapisywany). */
public final class Cooldowns {
    private final Map<String, Long> spells = new HashMap<>();
    private final Map<SpellGroup, Long> groups = new EnumMap<>(SpellGroup.class);

    public boolean ready(SpellType s, long now) {
        return remaining(s, now) == 0;
    }

    public long remaining(SpellType s, long now) {
        long a = spells.getOrDefault(s.key(), 0L) - now;
        long b = groups.getOrDefault(s.group(), 0L) - now;
        return Math.max(0, Math.max(a, b));
    }

    public boolean groupReady(SpellGroup g, long now) {
        return groups.getOrDefault(g, 0L) <= now;
    }

    /** Ile zostalo do konca odnowienia grupy (np. mikstur). */
    public long groupRemaining(SpellGroup g, long now) {
        return Math.max(0, groups.getOrDefault(g, 0L) - now);
    }

    public void trigger(SpellType s, long now) {
        spells.put(s.key(), now + s.cooldownMs());
        groups.put(s.group(), now + s.group().groupCooldownMs);
    }

    public void triggerGroup(SpellGroup g, long now, long ms) {
        groups.put(g, now + ms);
    }

    public void clear() {
        spells.clear();
        groups.clear();
    }
}
