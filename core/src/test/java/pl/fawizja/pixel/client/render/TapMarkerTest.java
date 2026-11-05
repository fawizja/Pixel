package pl.fawizja.pixel.client.render;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.world.Position;

/** Kropka dotkniecia mapy: rosnie z ease-out, blednie do zera, wersja "bez drogi" krotsza i mniejsza. */
class TapMarkerTest {

    @Test
    void progressClampsToDuration() {
        assertEquals(0f, TapMarker.progress(-5, 500));
        assertEquals(0f, TapMarker.progress(0, 500));
        assertEquals(0.5f, TapMarker.progress(250, 500), 1e-6);
        assertEquals(1f, TapMarker.progress(900, 500));
    }

    @Test
    void easeOutIsFastFirstAndMonotonic() {
        assertEquals(0f, TapMarker.easeOut(0f), 1e-6);
        assertEquals(1f, TapMarker.easeOut(1f), 1e-6);
        assertTrue(TapMarker.easeOut(0.25f) > 0.5f, "ease-out: polowa drogi w pierwszej cwiartce czasu");
        float prev = -1f;
        for (int i = 0; i <= 20; i++) {
            float e = TapMarker.easeOut(i / 20f);
            assertTrue(e >= prev);
            prev = e;
        }
    }

    @Test
    void dotGrowsWhileFadingOut() {
        float d0 = TapMarker.diameter(0f, false), d1 = TapMarker.diameter(1f, false);
        assertTrue(d0 < 0.2f, "zaczyna jako mala kropka");
        assertTrue(d1 > 0.5f && d1 < 0.8f, "konczy mniejsza niz pole");
        assertTrue(TapMarker.diameter(0.5f, false) > (d0 + d1) / 2f, "ease-out: szybciej na poczatku");
        assertTrue(TapMarker.alpha(0f) > 0.6f && TapMarker.alpha(0f) < 1f, "lekko przezroczysta");
        assertEquals(0f, TapMarker.alpha(1f), 1e-6);
        assertTrue(TapMarker.alpha(0.3f) > TapMarker.alpha(0.6f));
    }

    @Test
    void blockedPulseIsShorterAndSmaller() {
        assertTrue(TapMarker.duration(true) < TapMarker.duration(false));
        assertTrue(TapMarker.duration(false) >= 400 && TapMarker.duration(false) <= 600);
        assertTrue(TapMarker.diameter(1f, true) < TapMarker.diameter(1f, false));
    }

    @Test
    void pulseEndsAfterDuration() {
        TapMarker m = new TapMarker();
        assertFalse(m.active(0));
        m.pulse(new Position(10, 10, 0), false, 1000);
        assertTrue(m.active(1000));
        assertTrue(m.active(1000 + TapMarker.PULSE_MS - 1));
        assertFalse(m.active(1000 + TapMarker.PULSE_MS));
        m.pulse(new Position(10, 10, 0), true, 2000);
        assertTrue(m.blocked());
        assertFalse(m.active(2000 + TapMarker.BLOCKED_MS));
    }

    @Test
    void softEdgeTextures() {
        assertEquals(1f, TapMarker.dotAlpha(0f));
        assertEquals(0f, TapMarker.dotAlpha(1f));
        assertTrue(TapMarker.dotAlpha(0.7f) > 0f && TapMarker.dotAlpha(0.7f) < 1f, "miekki brzeg");
        assertEquals(1f, TapMarker.ringAlpha(0.82f), 1e-6);
        assertEquals(0f, TapMarker.ringAlpha(0.3f));
        assertEquals(0f, TapMarker.ringAlpha(1f));
    }
}
