package pl.fawizja.pixel.perf;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FrameStatsTest {

    @Test
    void summarisesSmoothFramesAndCatchesStutter() {
        FrameStats s = new FrameStats(100);
        assertEquals(0f, s.averageFps());
        for (int i = 0; i < 95; i++) s.add(1f / 60f);
        for (int i = 0; i < 5; i++) s.add(0.1f);
        assertEquals(100, s.count());
        assertEquals(16.7f, s.percentileMs(50), 0.1f);
        assertEquals(16.7f, s.percentileMs(95), 0.1f, "5% slow frames sit just above p95");
        assertEquals(100f, s.percentileMs(96), 0.1f);
        assertEquals(100f, s.maxMs(), 0.1f);
        assertEquals(1000f / (0.95f * 16.667f + 0.05f * 100f), s.averageFps(), 0.5f);
        assertTrue(s.summary().startsWith("Klatki: n=100 fps="), s.summary());

        s.add(0.5f); // okno pelne - pomijana, ale liczona w total
        assertEquals(100, s.count());
        assertEquals(101, s.total());
        s.reset();
        assertEquals(0, s.count());
        assertEquals(0f, s.maxMs());
    }
}
