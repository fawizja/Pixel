package pl.fawizja.pixel.audio;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.audio.SoundSynth.Sfx;
import pl.fawizja.pixel.combat.CombatEvent;
import pl.fawizja.pixel.world.Position;

class SoundTest {

    @Test
    void everyEffectIsAValidShortDeterministicWavWithoutClipping() {
        for (Sfx s : Sfx.values()) {
            byte[] wav = SoundSynth.wav(s);
            ByteBuffer b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
            assertEquals("RIFF", new String(wav, 0, 4), s.name());
            assertEquals("WAVE", new String(wav, 8, 4));
            assertEquals(wav.length - 8, b.getInt(4));
            assertEquals(1, b.getShort(20), "PCM");
            assertEquals(1, b.getShort(22), "mono");
            assertEquals(SoundSynth.RATE, b.getInt(24));
            assertEquals(16, b.getShort(34));
            assertEquals(wav.length - 44, b.getInt(40));

            short[] pcm = SoundSynth.pcm(s);
            double seconds = pcm.length / (double) SoundSynth.RATE;
            assertTrue(seconds > 0.05 && seconds <= 1.0, s + " lasts " + seconds);
            int peak = 0;
            for (short v : pcm) peak = Math.max(peak, Math.abs(v));
            assertTrue(peak > Short.MAX_VALUE * 0.5 && peak < Short.MAX_VALUE * 0.75, s + " peak " + peak);
            assertTrue(Math.abs(pcm[pcm.length - 1]) < 200, s + " ends silently (no click)");
            assertArrayEquals(pcm, SoundSynth.pcm(s), "same sound every time");
        }
        assertFalse(Arrays.equals(SoundSynth.pcm(Sfx.HIT), SoundSynth.pcm(Sfx.HEAL)));
    }

    @Test
    void eventsPlayOnceQuieterFarAwayAndOnlyOnTheListenersFloor() {
        Position me = new Position(10, 10, 0);
        SoundCues cues = new SoundCues();
        CombatEvent hit = new CombatEvent(1000, new Position(11, 10, 0), 12, CombatEvent.Kind.HIT_MONSTER, me, null);
        CombatEvent far = new CombatEvent(1000, new Position(18, 10, 0), 5, CombatEvent.Kind.HIT_PLAYER);
        CombatEvent otherFloor = new CombatEvent(1000, new Position(10, 10, 1), 5, CombatEvent.Kind.HEAL);
        CombatEvent tooFar = new CombatEvent(1000, new Position(30, 10, 0), 5, CombatEvent.Kind.LEVEL_UP);
        CombatEvent fire = CombatEvent.projectile(1000, me, new Position(14, 10, 0), "FIRE");

        List<SoundCues.Cue> first = cues.poll(List.of(hit, far, otherFloor, tooFar, fire), me, 1000);
        assertEquals(List.of(Sfx.HIT, Sfx.HURT, Sfx.SPELL_FIRE), first.stream().map(SoundCues.Cue::sfx).toList());
        assertTrue(first.get(0).volume() > first.get(1).volume(), "a hit next to you is louder");
        assertTrue(first.get(1).volume() >= 0.25f);

        assertTrue(cues.poll(List.of(hit, far, fire), me, 1016).isEmpty(), "already played");

        CombatEvent again = new CombatEvent(1020, new Position(11, 10, 0), 9, CombatEvent.Kind.HIT_MONSTER);
        assertTrue(cues.poll(List.of(hit, again), me, 1020).isEmpty(), "same effect is not repeated within 70 ms");
        CombatEvent later = new CombatEvent(1200, new Position(11, 10, 0), 9, CombatEvent.Kind.HIT_MONSTER);
        assertEquals(1, cues.poll(List.of(later), me, 1200).size());
    }

    @Test
    void aBigFightPlaysAtMostThreeSoundsPerFrame() {
        Position me = new Position(10, 10, 0);
        List<CombatEvent> burst = List.of(
                new CombatEvent(5, me, 1, CombatEvent.Kind.HIT_PLAYER),
                new CombatEvent(5, me, 1, CombatEvent.Kind.HEAL),
                new CombatEvent(5, me, 1, CombatEvent.Kind.BLOCKED),
                new CombatEvent(5, me, 1, CombatEvent.Kind.LEVEL_UP),
                CombatEvent.area(5, me, 2, "ICE"));
        assertEquals(SoundCues.MAX_PER_FRAME, new SoundCues().poll(burst, me, 5).size());
        assertEquals(Sfx.SHOOT, SoundCues.sfxFor(CombatEvent.projectile(0, me, me, null)));
        assertEquals(Sfx.SPELL_HOLY, SoundCues.sfxFor(CombatEvent.area(0, me, 1, "HOLY")));
    }
}
