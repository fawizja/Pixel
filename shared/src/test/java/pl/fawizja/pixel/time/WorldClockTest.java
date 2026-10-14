package pl.fawizja.pixel.time;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WorldClockTest {

    @Test
    void phasesCoverTheWholeDay() {
        assertEquals(DayPhase.NIGHT, WorldClock.phase(0));
        assertEquals(DayPhase.DAWN, WorldClock.phase(6 * 60));
        assertEquals(DayPhase.DAY, WorldClock.phase(12 * 60));
        assertEquals(DayPhase.DUSK, WorldClock.phase(20 * 60));
        assertEquals(DayPhase.NIGHT, WorldClock.phase(23 * 60));
    }

    @Test
    void lightChangesSmoothlyAndNightStaysReadable() {
        WorldClock c = WorldClock.standard();
        float prev = c.ambientLight(0);
        float maxJump = 0;
        for (int m = 1; m < WorldClock.MINUTES_PER_DAY; m++) {
            float v = c.ambientLight(m);
            assertTrue(v >= c.nightAmbient() - 1e-6 && v <= 1f);
            maxJump = Math.max(maxJump, Math.abs(v - prev));
            prev = v;
        }
        assertTrue(maxJump < 0.02f, "no sudden jumps: " + maxJump);
        assertEquals(1f, c.ambientLight(12 * 60), 1e-6);
        assertTrue(c.nightAmbient() >= 0.3f, "night must stay playable on phones");
    }

    @Test
    void timeComesFromServerClockAndWraps() {
        WorldClock c = WorldClock.standard();
        long hour = 60L * 60L * 1000L;
        assertEquals(0, c.minuteOfDay(0));
        assertEquals(720, c.minuteOfDay(hour / 2));
        assertEquals(0, c.minuteOfDay(hour));
        assertEquals(c.minuteOfDay(123_456), c.minuteOfDay(123_456 + 5 * hour));
        assertTrue(c.minuteOfDay(-1000) >= 0);
    }
}
