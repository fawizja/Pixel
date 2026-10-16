package pl.fawizja.pixel.world;

import java.util.Locale;

/**
 * Pogoda na powierzchni: okna po 20 min czasu rzeczywistego, w ok. 30% okien opady. Rodzaj zalezy od krainy
 * (mroz -> snieg, pustynia -> burza piaskowa, reszta -> deszcz). Liczona z zegara i nazwy krainy, wiec wszyscy
 * gracze w tej samej krainie widza to samo bez komunikatow z serwera. Pod ziemia zawsze pogodnie.
 */
public final class Weather {

    public enum Kind { CLEAR, RAIN, SNOW, SANDSTORM }

    public static final long WINDOW_MS = 20 * 60_000L;
    /** Procent okien z opadami. */
    public static final int CHANCE = 30;
    /** Narastanie i zanikanie opadow na brzegach okna. */
    static final long FADE_MS = 90_000L;

    private Weather() { }

    public static Kind at(long epochMs, Region region, int z) {
        if (z < 0 || region == null) return Kind.CLEAR;
        long window = Math.floorDiv(epochMs, WINDOW_MS);
        if (roll(window, region.name()) >= CHANCE) return Kind.CLEAR;
        return kindFor(region.name());
    }

    /** Sila opadow 0..1: pelna w srodku okna, narasta i zanika na brzegach. */
    public static float intensity(long epochMs) {
        long in = Math.floorMod(epochMs, WINDOW_MS);
        long edge = Math.min(in, WINDOW_MS - in);
        return Math.min(1f, edge / (float) FADE_MS);
    }

    /** Opady przyciemniaja swiat. */
    public static float ambientFactor(Kind kind, float intensity) {
        switch (kind) {
            case RAIN: return 1f - 0.18f * intensity;
            case SNOW: return 1f - 0.08f * intensity;
            case SANDSTORM: return 1f - 0.12f * intensity;
            default: return 1f;
        }
    }

    static Kind kindFor(String regionName) {
        String n = regionName.toLowerCase(Locale.ROOT);
        if (n.contains("mro") || n.contains("lod")) return Kind.SNOW;
        if (n.contains("pustynia") || n.contains("qasirah")) return Kind.SANDSTORM;
        return Kind.RAIN;
    }

    /** Powtarzalny los 0..99 dla okna i krainy (SplitMix64). */
    static int roll(long window, String regionName) {
        long x = window * 0x9E3779B97F4A7C15L + regionName.hashCode();
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        x ^= x >>> 31;
        return (int) Math.floorMod(x, 100L);
    }

    /** Nazwa do komunikatu, np. przy zmianie pogody. */
    public static String describe(Kind kind) {
        switch (kind) {
            case RAIN: return "Zaczyna padać deszcz.";
            case SNOW: return "Zaczyna padać śnieg.";
            case SANDSTORM: return "Nadciąga burza piaskowa.";
            default: return "Rozpogadza się.";
        }
    }

    /** "rain", "SNOW" itp. -> rodzaj; null gdy nieznany. */
    public static Kind parse(String s) {
        if (s == null) return null;
        try {
            return Kind.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
