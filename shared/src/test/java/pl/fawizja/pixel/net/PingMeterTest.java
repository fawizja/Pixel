package pl.fawizja.pixel.net;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.net.PingMeter.Quality;

/** Wskaznik pingu: progi kolorow (zielony < 100 ms, pomaranczowy 100-250, czerwony > 250), mediana, brak odpowiedzi. */
class PingMeterTest {

    @Test
    void thresholdsMatchTheBrief() {
        assertEquals(Quality.GOOD, PingMeter.classify(0));
        assertEquals(Quality.GOOD, PingMeter.classify(99));
        assertEquals(Quality.MEDIUM, PingMeter.classify(100));
        assertEquals(Quality.MEDIUM, PingMeter.classify(250));
        assertEquals(Quality.BAD, PingMeter.classify(251));
        assertEquals(Quality.BAD, PingMeter.classify(5000));
        assertEquals(Quality.UNKNOWN, PingMeter.classify(-1));
    }

    @Test
    void colorsAreGreenOrangeRed() {
        assertEquals(0x3fd94a, PingMeter.color(Quality.GOOD));
        assertEquals(0xf08a20, PingMeter.color(Quality.MEDIUM));
        assertEquals(0xe03a2a, PingMeter.color(Quality.BAD));
        assertEquals(0x8a8a8a, PingMeter.color(Quality.UNKNOWN));
    }

    @Test
    void medianOfLastFiveSamplesSmoothsSpikes() {
        PingMeter m = new PingMeter();
        assertEquals(-1, m.millis());
        assertEquals(Quality.UNKNOWN, m.quality(0));
        assertEquals("?", m.label(0));
        for (long v : new long[] {40, 45, 900, 50, 42}) m.record(v, 1000);
        assertEquals(45, m.millis(), "one spike does not turn the dot red");
        assertEquals(Quality.GOOD, m.quality(1000));
        assertEquals("45 ms", m.label(1000));
        for (long v : new long[] {300, 320, 310}) m.record(v, 2000);   // window of 5: 50, 42, 300, 320, 310
        assertEquals(300, m.millis());
        assertEquals(Quality.BAD, m.quality(2000));
    }

    @Test
    void silenceTurnsTheDotRedAndSchedulesPings() {
        PingMeter m = new PingMeter();
        assertTrue(m.due(0));
        m.sent(1000);
        assertFalse(m.due(1500));
        assertTrue(m.due(1000 + PingMeter.INTERVAL_MS));
        m.record(30, 1100);
        assertEquals(Quality.GOOD, m.quality(1200));
        m.sent(5000);
        assertFalse(m.timedOut(5000 + PingMeter.TIMEOUT_MS));
        assertTrue(m.timedOut(5000 + PingMeter.TIMEOUT_MS + 1));
        assertEquals(Quality.BAD, m.quality(5000 + PingMeter.TIMEOUT_MS + 1));
        assertEquals(">6 s", m.label(5000 + PingMeter.TIMEOUT_MS + 1));
        m.record(60, 12_000);
        assertFalse(m.timedOut(12_001));
    }
}
