package pl.fawizja.pixel.audio;

import java.io.ByteArrayOutputStream;
import java.util.Random;

/**
 * Efekty dzwiekowe syntezowane w kodzie (wlasne, bez plikow z zewnatrz): proste fale, szum i obwiednie.
 * Wynik jest deterministyczny (ziarno = rodzaj efektu), wiec ten sam efekt brzmi zawsze tak samo.
 * PCM 16 bit, mono, {@link #RATE} Hz; {@link #wav(Sfx)} zwraca gotowy plik WAV.
 */
public final class SoundSynth {

    public static final int RATE = 22050;
    /** Szczyt amplitudy (ok. -3 dB), zeby kilka efektow naraz nie przesterowalo. */
    private static final double PEAK = 0.7;

    public enum Sfx {
        HIT, HURT, BLOCK, SHOOT, HEAL,
        SPELL_FIRE, SPELL_ICE, SPELL_ENERGY, SPELL_EARTH, SPELL_HOLY, SPELL_DEATH,
        LEVEL_UP, DEATH;

        /** Nazwa pliku w pamieci podrecznej klienta. */
        public String fileName() { return name().toLowerCase() + ".wav"; }
    }

    private SoundSynth() {}

    public static short[] pcm(Sfx sfx) {
        Random rng = new Random(9100L + sfx.ordinal());
        switch (sfx) {
            case HIT: return render(0.14, (t, d) -> (noise(rng) * 0.6 + tone(110 - 300 * t, t) * 0.7) * env(t, d, 0.004, 0.10));
            case HURT: return render(0.22, (t, d) -> (square(220 - 400 * t, t) * 0.45 + noise(rng) * 0.25) * env(t, d, 0.005, 0.18));
            case BLOCK: return render(0.12, (t, d) -> (tone(900, t) * 0.5 + tone(1340, t) * 0.35) * env(t, d, 0.002, 0.11));
            case SHOOT: return render(0.15, (t, d) -> (tone(700 - 2600 * t, t) * 0.5 + noise(rng) * 0.2) * env(t, d, 0.003, 0.12));
            case HEAL: return render(0.45, (t, d) -> (tone(523 + 400 * t, t) * 0.5 + tone(784 + 600 * t, t) * 0.3) * env(t, d, 0.05, 0.30));
            case SPELL_FIRE: return render(0.35, (t, d) -> (noise(rng) * 0.65 + tone(90 + 60 * t, t) * 0.35) * env(t, d, 0.03, 0.25));
            case SPELL_ICE: return render(0.32, (t, d) -> (tone(1800 - 900 * t, t) * 0.35 + tone(2400, t) * 0.2 + noise(rng) * 0.15) * env(t, d, 0.01, 0.25));
            case SPELL_ENERGY: return render(0.28, (t, d) -> (square(140 + 40 * Math.sin(t * 90), t) * 0.4 + noise(rng) * 0.25) * env(t, d, 0.005, 0.22));
            case SPELL_EARTH: return render(0.34, (t, d) -> (tone(60 - 20 * t, t) * 0.7 + noise(rng) * 0.35) * env(t, d, 0.01, 0.28));
            case SPELL_HOLY: return render(0.5, (t, d) -> (tone(660, t) * 0.35 + tone(880, t) * 0.3 + tone(1320, t) * 0.15) * env(t, d, 0.06, 0.35));
            case SPELL_DEATH: return render(0.42, (t, d) -> (tone(150 - 90 * t, t) * 0.5 + square(75, t) * 0.25) * env(t, d, 0.04, 0.32));
            case LEVEL_UP: return render(0.7, (t, d) -> arpeggio(t) * env(t, d, 0.01, 0.2));
            case DEATH: return render(0.9, (t, d) -> (tone(330 - 220 * t / d, t) * 0.5 + tone(165 - 110 * t / d, t) * 0.3) * env(t, d, 0.02, 0.6));
            default: throw new IllegalArgumentException(sfx.name());
        }
    }

    /** Plik WAV (RIFF, PCM 16 bit, mono). */
    public static byte[] wav(Sfx sfx) {
        short[] s = pcm(sfx);
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + s.length * 2);
        int dataLen = s.length * 2;
        ascii(out, "RIFF");
        le32(out, 36 + dataLen);
        ascii(out, "WAVE");
        ascii(out, "fmt ");
        le32(out, 16);
        le16(out, 1);          // PCM
        le16(out, 1);          // mono
        le32(out, RATE);
        le32(out, RATE * 2);   // bajty na sekunde
        le16(out, 2);          // wyrownanie bloku
        le16(out, 16);         // bity
        ascii(out, "data");
        le32(out, dataLen);
        for (short v : s) le16(out, v);
        return out.toByteArray();
    }

    private interface Wave { double at(double t, double duration); }

    private static short[] render(double seconds, Wave w) {
        int n = (int) Math.round(seconds * RATE);
        double[] raw = new double[n];
        double max = 1e-9;
        for (int i = 0; i < n; i++) {
            raw[i] = w.at(i / (double) RATE, seconds);
            max = Math.max(max, Math.abs(raw[i]));
        }
        double gain = PEAK / max;
        short[] out = new short[n];
        for (int i = 0; i < n; i++) out[i] = (short) Math.round(raw[i] * gain * Short.MAX_VALUE);
        return out;
    }

    /** Obwiednia: narastanie, potem wykladnicze wygaszanie do zera na koncu. */
    private static double env(double t, double d, double attack, double decay) {
        double a = t < attack ? t / attack : 1.0;
        double r = Math.exp(-(t - Math.min(t, attack)) / Math.max(0.01, decay) * 2.2);
        double tail = Math.min(1.0, (d - t) / 0.01); // bez trzasku na koncu
        return a * r * Math.max(0, tail);
    }

    private static double tone(double hz, double t) { return Math.sin(2 * Math.PI * Math.max(20, hz) * t); }

    private static double square(double hz, double t) { return tone(hz, t) >= 0 ? 1 : -1; }

    private static double noise(Random rng) { return rng.nextDouble() * 2 - 1; }

    private static double arpeggio(double t) {
        double[] notes = {523.25, 659.25, 783.99, 1046.5};
        int i = Math.min(notes.length - 1, (int) (t / 0.12));
        return tone(notes[i], t) * 0.5 + tone(notes[i] * 2, t) * 0.15;
    }

    private static void ascii(ByteArrayOutputStream o, String s) {
        for (int i = 0; i < s.length(); i++) o.write(s.charAt(i));
    }

    private static void le16(ByteArrayOutputStream o, int v) {
        o.write(v & 0xff);
        o.write((v >> 8) & 0xff);
    }

    private static void le32(ByteArrayOutputStream o, int v) {
        le16(o, v & 0xffff);
        le16(o, (v >>> 16) & 0xffff);
    }
}
