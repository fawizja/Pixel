package pl.fawizja.pixel.perf;

import java.util.Arrays;
import java.util.Locale;

/**
 * Statystyki czasu klatek w oknie pomiarowym: srednie FPS, mediana, 95. percentyl i najdluzsza klatka.
 * Klient wypisuje je okresowo do logu (na Androidzie logcat, tag "Pixel"), co pozwala zmierzyc plynnosc
 * na telefonie albo emulatorze bez dodatkowych narzedzi.
 */
public final class FrameStats {

    private final float[] ms;
    private int count;
    private long total;

    public FrameStats(int capacity) {
        this.ms = new float[Math.max(1, capacity)];
    }

    /** Dodaje klatke (czas w sekundach, jak delta libGDX). Po zapelnieniu okna nowe klatki sa pomijane. */
    public void add(float deltaSeconds) {
        total++;
        if (count < ms.length) ms[count++] = deltaSeconds * 1000f;
    }

    public int count() { return count; }

    /** Laczna liczba klatek od utworzenia (takze po resetach). */
    public long total() { return total; }

    public float averageMs() {
        if (count == 0) return 0f;
        double s = 0;
        for (int i = 0; i < count; i++) s += ms[i];
        return (float) (s / count);
    }

    public float averageFps() {
        float a = averageMs();
        return a <= 0f ? 0f : 1000f / a;
    }

    /** Percentyl czasu klatki (0..100) w ms. */
    public float percentileMs(int p) {
        if (count == 0) return 0f;
        float[] sorted = Arrays.copyOf(ms, count);
        Arrays.sort(sorted);
        int idx = (int) Math.ceil(Math.max(0, Math.min(100, p)) / 100.0 * count) - 1;
        return sorted[Math.max(0, Math.min(count - 1, idx))];
    }

    public float maxMs() {
        float m = 0f;
        for (int i = 0; i < count; i++) m = Math.max(m, ms[i]);
        return m;
    }

    /** Jedna linia do logu, np. "Klatki: n=600 fps=59.8 p50=16.7ms p95=18.2ms max=33.1ms". */
    public String summary() {
        return String.format(Locale.ROOT, "Klatki: n=%d fps=%.1f p50=%.1fms p95=%.1fms max=%.1fms",
                count, averageFps(), percentileMs(50), percentileMs(95), maxMs());
    }

    public void reset() { count = 0; }
}
