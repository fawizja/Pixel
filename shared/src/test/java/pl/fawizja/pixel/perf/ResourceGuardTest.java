package pl.fawizja.pixel.perf;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResourceGuardTest {

    private static Map<Resource, Float> mem(float v) {
        Map<Resource, Float> m = new EnumMap<>(Resource.class);
        m.put(Resource.APP_MEMORY, v);
        m.put(Resource.CPU, Float.NaN); // niedostepne na tej platformie
        return m;
    }

    /** Podaje ta sama probke co 100 ms przez podany czas. */
    private static long feed(ResourceGuard g, long now, long durationMs, float v) {
        for (long t = 0; t < durationMs; t += 100) g.update(now + t, mem(v));
        return now + durationMs;
    }

    @Test
    void entersLimitedAtNinetyPercent() {
        ResourceGuard g = new ResourceGuard();
        long t = feed(g, 0, 2000, 0.50f);
        assertEquals(GuardMode.NORMAL, g.mode());
        t = feed(g, t, 1000, 0.90f);
        assertEquals(GuardMode.LIMITED, g.mode());
        assertEquals(Resource.APP_MEMORY, g.reason());
        assertTrue(g.describe().startsWith("LIMITED"));
        assertEquals(30, g.fpsCap(60));
        assertFalse(g.allowDeferrableWork());
        assertTrue(g.effectsQuality() < 0.5f);
    }

    @Test
    void shortSpikesDoNotTriggerLimitedMode() {
        ResourceGuard g = new ResourceGuard();
        long t = 0;
        for (int i = 0; i < 20; i++) {
            t = feed(g, t, 300, 0.97f);   // krotki skok ponizej czasu potwierdzenia
            t = feed(g, t, 500, 0.40f);
        }
        assertEquals(GuardMode.NORMAL, g.mode());
        assertEquals(0, g.transitionCount());
    }

    @Test
    void recoversGraduallyAfterDroppingBelowSeventyFive() {
        ResourceGuard g = new ResourceGuard();
        long t = feed(g, 0, 1000, 0.95f);
        assertEquals(GuardMode.LIMITED, g.mode());
        t = feed(g, t, 2000, 0.80f);            // ponizej 90%, ale powyzej 75%: zostaje LIMITED
        assertEquals(GuardMode.LIMITED, g.mode());
        t = feed(g, t, 3100, 0.60f);            // okres stabilizacji
        assertEquals(GuardMode.RECOVERING, g.mode());
        int midFps = g.fpsCap(60);
        t = feed(g, t, 2000, 0.60f);
        assertEquals(GuardMode.RECOVERING, g.mode());
        assertTrue(g.fpsCap(60) > midFps, "FPS is restored gradually");
        t = feed(g, t, 2500, 0.60f);
        assertEquals(GuardMode.NORMAL, g.mode());
        assertEquals(60, g.fpsCap(60));
        assertTrue(g.allowDeferrableWork());
        assertNull(g.reason());
    }

    @Test
    void sustainedOverloadStaysLimitedWithoutFlapping() {
        ResourceGuard g = new ResourceGuard();
        long t = feed(g, 0, 60_000, 0.93f);
        assertEquals(GuardMode.LIMITED, g.mode());
        assertEquals(1, g.transitionCount(), "no flapping between modes");
    }

    @Test
    void oscillationAroundThresholdDoesNotFlap() {
        ResourceGuard g = new ResourceGuard();
        long t = feed(g, 0, 1000, 0.95f);
        for (int i = 0; i < 50; i++) {
            t = feed(g, t, 500, 0.70f);
            t = feed(g, t, 500, 0.91f);
        }
        assertEquals(GuardMode.LIMITED, g.mode());
        assertEquals(1, g.transitionCount());
    }

    @Test
    void overloadDuringRecoveryReturnsToLimited() {
        ResourceGuard g = new ResourceGuard();
        long t = feed(g, 0, 1000, 0.95f);
        t = feed(g, t, 3100, 0.50f);
        assertEquals(GuardMode.RECOVERING, g.mode());
        g.update(t, mem(0.92f));
        assertEquals(GuardMode.LIMITED, g.mode());
    }

    @Test
    void unavailableMetricsDoNotTrigger() {
        ResourceGuard g = new ResourceGuard();
        Map<Resource, Float> m = new EnumMap<>(Resource.class);
        m.put(Resource.THERMAL, Float.NaN);
        for (long t = 0; t < 5000; t += 100) g.update(t, m);
        assertEquals(GuardMode.NORMAL, g.mode());
    }

    @Test
    void thresholdsAreConfigurable() {
        ResourceGuard g = new ResourceGuard(new ResourceGuard.Config(0.8f, 0.5f, 0, 0, 0));
        g.update(0, mem(0.81f));
        assertEquals(GuardMode.LIMITED, g.mode());
        g.update(10, mem(0.49f));
        assertEquals(GuardMode.RECOVERING, g.mode());
        g.update(20, mem(0.49f));
        assertEquals(GuardMode.NORMAL, g.mode());
        assertThrows(IllegalArgumentException.class, () -> new ResourceGuard.Config(0.7f, 0.8f, 0, 0, 0));
    }
}
