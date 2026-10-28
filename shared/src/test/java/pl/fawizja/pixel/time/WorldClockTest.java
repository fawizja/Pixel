package pl.fawizja.pixel.time;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WorldClockTest {

    @Test
    void phasesCoverTheWholeDay() {
        assertEquals(DayPhase.NIGHT, WorldClock.phase(0));
        assertEquals(DayPhase.DAWN, WorldClock.phase(5 * 60 + 30));
        assertEquals(DayPhase.DAY, WorldClock.phase(12 * 60));
        assertEquals(DayPhase.DUSK, WorldClock.phase(17 * 60 + 30));
        assertEquals(DayPhase.NIGHT, WorldClock.phase(18 * 60));
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
    void threeHoursOfLightAndThreeHoursOfDarkness() {
        WorldClock c = WorldClock.standard();
        long light = 0;
        for (int m = 0; m < WorldClock.MINUTES_PER_DAY; m++) if (c.ambientLight(m) > (1f + c.nightAmbient()) / 2f) light++;
        long lightMs = light * WorldClock.REAL_DAY_MS / WorldClock.MINUTES_PER_DAY;
        assertEquals(3L * 60L * 60_000L, lightMs, 3L * 60_000L, "about three real hours of daylight");
        assertEquals(3L * 60L * 60_000L, WorldClock.REAL_DAY_MS - lightMs, 3L * 60_000L, "and about three hours of darkness");
        assertEquals(6L * 60L * 60_000L, WorldClock.REAL_DAY_MS);
        assertEquals(15L * 60_000L, WorldClock.GAME_HOUR_MS, "a game hour lasts 15 real minutes");
    }

    @Test
    void timeComesFromServerClockAndWraps() {
        WorldClock c = WorldClock.standard();
        long hour = WorldClock.REAL_DAY_MS;
        assertEquals(0, c.minuteOfDay(0));
        assertEquals(720, c.minuteOfDay(hour / 2));
        assertEquals(0, c.minuteOfDay(hour));
        assertEquals(c.minuteOfDay(123_456), c.minuteOfDay(123_456 + 5 * hour));
        assertTrue(c.minuteOfDay(-1000) >= 0);
    }
}
