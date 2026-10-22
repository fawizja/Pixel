package pl.fawizja.pixel.audio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import pl.fawizja.pixel.audio.SoundSynth.Sfx;
import pl.fawizja.pixel.combat.CombatEvent;
import pl.fawizja.pixel.world.Position;

/**
 * Zamienia zdarzenia walki na efekty dzwiekowe: kazde zdarzenie gra raz, ciszej z daleka, tylko na pietrze
 * sluchacza i w promieniu {@link #HEARING}. Ten sam efekt nie powtarza sie czesciej niz co
 * {@link #REPEAT_MS} ms, a w jednej klatce gra najwyzej {@link #MAX_PER_FRAME} (bez kakofonii przy duzych walkach).
 */
public final class SoundCues {

    public static final int HEARING = 9;
    public static final long REPEAT_MS = 70;
    public static final int MAX_PER_FRAME = 3;

    public record Cue(Sfx sfx, float volume) {}

    private Set<CombatEvent> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Sfx, Long> lastPlayed = new EnumMap<>(Sfx.class);

    /** Efekt dla zdarzenia albo null (np. smierc gra tylko u gracza, ktory zginal). */
    public static Sfx sfxFor(CombatEvent e) {
        switch (e.kind()) {
            case HIT_MONSTER: return Sfx.HIT;
            case HIT_PLAYER: return Sfx.HURT;
            case BLOCKED: return Sfx.BLOCK;
            case HEAL: return Sfx.HEAL;
            case LEVEL_UP: return Sfx.LEVEL_UP;
            case DEATH: return Sfx.DEATH;
            case PROJECTILE:
            case AREA: return elementSfx(e.effect());
            default: return null;
        }
    }

    static Sfx elementSfx(String effect) {
        if (effect == null) return Sfx.SHOOT;
        switch (effect) {
            case "FIRE": return Sfx.SPELL_FIRE;
            case "ICE": return Sfx.SPELL_ICE;
            case "ENERGY": return Sfx.SPELL_ENERGY;
            case "EARTH": return Sfx.SPELL_EARTH;
            case "HOLY": return Sfx.SPELL_HOLY;
            case "DEATH": return Sfx.SPELL_DEATH;
            case "HEAL": return Sfx.HEAL;
            case "HASTE": return Sfx.SPELL_ENERGY;
            default: return Sfx.SHOOT;
        }
    }

    /**
     * Nowe dzwieki od ostatniego wywolania. {@code events} to biezace zdarzenia (np. z ostatnich 1,2 s);
     * zdarzenia juz zagrane sa rozpoznawane po tozsamosci obiektu.
     */
    public List<Cue> poll(List<CombatEvent> events, Position listener, long now) {
        Set<CombatEvent> current = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Cue> out = new ArrayList<>();
        for (CombatEvent e : events) {
            current.add(e);
            if (seen.contains(e)) continue;
            Position p = e.position();
            if (listener == null || p.z() != listener.z()) continue;
            int dist = p.distanceTo(listener);
            if (e.kind() == CombatEvent.Kind.PROJECTILE && e.from() != null) dist = Math.min(dist, e.from().distanceTo(listener));
            if (dist > HEARING) continue;
            Sfx sfx = sfxFor(e);
            if (sfx == null || out.size() >= MAX_PER_FRAME) continue;
            Long last = lastPlayed.get(sfx);
            if (last != null && now - last < REPEAT_MS && now >= last) continue;
            lastPlayed.put(sfx, now);
            out.add(new Cue(sfx, volumeAt(dist)));
        }
        seen = current;
        return out;
    }

    /** Glosnosc 1.0 tuz obok, liniowo do 0.25 na granicy slyszenia. */
    static float volumeAt(int distance) {
        float f = Math.max(0, Math.min(HEARING, distance)) / (float) HEARING;
        return 1f - 0.75f * f;
    }
}
