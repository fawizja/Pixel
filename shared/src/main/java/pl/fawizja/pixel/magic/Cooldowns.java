package pl.fawizja.pixel.magic;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Czasy odnowienia jak w Canary: osobno dla kazdego czaru (CONDITION_SPELLCOOLDOWN), dla grupy glownej (atak,
 * leczenie, wsparcie - niezalezne od siebie, CONDITION_SPELLGROUPCOOLDOWN), dla drugiej grupy czaru
 * ({@link SpellGroups}) i dla uzycia przedmiotu ({@link SpellGroup#POTION}: mikstury i runy, Canary nextPotionAction).
 * Stan ulotny (nie jest zapisywany).
 */
public final class Cooldowns {
    private final Map<String, Long> spells = new HashMap<>();
    private final Map<SpellGroup, Long> groups = new EnumMap<>(SpellGroup.class);
    /** Drugie grupy (Canary secondaryGroup): nazwa -> koniec odnowienia. */
    private final Map<String, Long> secondary = new HashMap<>();

    public boolean ready(SpellType s, long now) {
        return remaining(s, now) == 0;
    }

    public long remaining(SpellType s, long now) {
        long a = spells.getOrDefault(s.key(), 0L) - now;
        long b = groups.getOrDefault(s.group(), 0L) - now;
        SpellGroups.Secondary sec = SpellGroups.secondary(s.key());
        long c = sec == null ? 0 : secondary.getOrDefault(sec.group(), 0L) - now;
        return Math.max(0, Math.max(a, Math.max(b, c)));
    }

    public boolean groupReady(SpellGroup g, long now) {
        return groups.getOrDefault(g, 0L) <= now;
    }

    /** Ile zostalo do konca odnowienia grupy (np. mikstur). */
    public long groupRemaining(SpellGroup g, long now) {
        return Math.max(0, groups.getOrDefault(g, 0L) - now);
    }

    public void trigger(SpellType s, long now) { trigger(s, now, s.cooldownMs()); }

    /** Jak wyzej z wlasnym odnowieniem czaru (premia Kola Przeznaczenia). */
    public void trigger(SpellType s, long now, long cooldownMs) {
        spells.put(s.key(), now + Math.max(0, cooldownMs));
        groups.put(s.group(), now + SpellGroups.primaryMs(s));
        SpellGroups.Secondary sec = SpellGroups.secondary(s.key());
        if (sec != null) secondary.put(sec.group(), now + sec.ms());
    }

    /** Ile zostalo do konca odnowienia drugiej grupy (np. "focus"). */
    public long secondaryRemaining(String group, long now) {
        return Math.max(0, secondary.getOrDefault(group, 0L) - now);
    }

    /** Przedluza odnowienie czaru do chwili until. */
    public void extend(SpellType s, long until) {
        spells.merge(s.key(), until, Math::max);
    }

    public void triggerGroup(SpellGroup g, long now, long ms) {
        groups.put(g, now + ms);
    }

    /** Skraca odnowienie jednego czaru (spender mnicha: czary budujace o 2 s za punkt harmonii); ms = Long.MAX_VALUE zeruje. */
    public void cut(SpellType s, long ms, long now) {
        Long until = spells.get(s.key());
        if (until == null || until <= now) return;
        spells.put(s.key(), ms >= until - now ? now : until - ms);
    }

    /**
     * Rozped z Kuzni (Momentum): odnowienia wszystkich czarow krotsze o ms, a z grup tylko drugie (np. cnoty mnicha) -
     * jak Canary triggerMomentum (grupy ataku, leczenia i wsparcia bez zmian). Uzywa go tez Dar Zycia z Kola
     * Przeznaczenia (-60 s, Canary reduceAllSpellsCooldownTimer).
     */
    public void cutAll(long ms, long now) {
        for (Map.Entry<String, Long> e : spells.entrySet()) {
            if (e.getValue() > now) e.setValue(Math.max(now, e.getValue() - ms));
        }
        for (Map.Entry<String, Long> e : secondary.entrySet()) {
            if (e.getValue() > now) e.setValue(Math.max(now, e.getValue() - ms));
        }
    }

    public void clear() {
        spells.clear();
        groups.clear();
        secondary.clear();
    }
}
