package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WeatherTest {

    static final Region VARDEN = new Region("Krainy Varden", 0, 64, 159, 111, -5, 5, 8, 60);
    static final Region FROST = new Region("Mrozne Pustkowia", 0, 0, 159, 63, 0, 5, 40, 90);
    static final Region DESERT = new Region("Pustynia Qasirah", 0, 172, 159, 231, 0, 5, 45, 130);

    @Test
    void aboutThirtyPercentOfWindowsBringPrecipitationOfTheRegionsKind() {
        int wet = 0;
        int windows = 3000;
        for (int w = 0; w < windows; w++) {
            long t = w * Weather.WINDOW_MS + 1;
            Weather.Kind k = Weather.at(t, VARDEN, 0);
            if (k != Weather.Kind.CLEAR) {
                wet++;
                assertEquals(Weather.Kind.RAIN, k);
            }
            Weather.Kind f = Weather.at(t, FROST, 0);
            assertTrue(f == Weather.Kind.CLEAR || f == Weather.Kind.SNOW);
            Weather.Kind d = Weather.at(t, DESERT, 1);
            assertTrue(d == Weather.Kind.CLEAR || d == Weather.Kind.SANDSTORM);
        }
        double share = wet / (double) windows;
        assertTrue(share > 0.25 && share < 0.35, "share " + share);
    }

    @Test
    void theSameInsideAWindowAndClearUnderground() {
        for (int w = 0; w < 200; w++) {
            long start = w * Weather.WINDOW_MS;
            assertEquals(Weather.at(start, VARDEN, 0), Weather.at(start + Weather.WINDOW_MS - 1, VARDEN, 0));
            assertEquals(Weather.Kind.CLEAR, Weather.at(start, VARDEN, -1));
        }
        assertEquals(Weather.Kind.CLEAR, Weather.at(0, null, 0));
    }

    @Test
    void intensityFadesAtTheEdgesOfAWindow() {
        assertEquals(0f, Weather.intensity(0), 1e-6);
        assertEquals(1f, Weather.intensity(Weather.WINDOW_MS / 2), 1e-6);
        assertEquals(0.5f, Weather.intensity(Weather.FADE_MS / 2), 1e-3);
        assertTrue(Weather.ambientFactor(Weather.Kind.RAIN, 1f) < 1f);
        assertEquals(1f, Weather.ambientFactor(Weather.Kind.CLEAR, 1f));
        assertEquals(Weather.Kind.SNOW, Weather.parse("snow"));
        assertNull(Weather.parse("hail"));
    }
}
