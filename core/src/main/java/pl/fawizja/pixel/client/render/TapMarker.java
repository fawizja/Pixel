package pl.fawizja.pixel.client.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import pl.fawizja.pixel.world.Position;

/**
 * Znacznik dotkniecia mapy: miekka biala kropka, ktora rosnie i blednie (ok. pol sekundy), z delikatnym
 * pierscieniem; gdy nie ma drogi - krotsza, mniejsza, czerwonawa. Podczas marszu na polu celu zostaje mala,
 * przygaszona kropka (znika po dojsciu). Rysowana na ziemi, pod postaciami danego rzedu (z RowDrawer).
 * Wymiary w polach mapy, wiec skaluje sie z wielkoscia pola (telefon i desktop). Tekstury proceduralne.
 */
public final class TapMarker {

    /** Czas rozbłysku po udanym dotknieciu. */
    public static final long PULSE_MS = 520;
    /** Czas rozbłysku "nie ma tam drogi". */
    public static final long BLOCKED_MS = 380;

    private static final float START_D = 0.12f;  // srednica na poczatku (w polach)
    private static final float END_D = 0.62f;    // srednica na koncu
    private static final float BLOCKED_END_D = 0.44f;
    private static final float START_A = 0.85f;
    private static final float SHADOW_A = 0.42f;
    private static final float DEST_D = 0.16f;   // kropka celu podczas marszu

    private int x, y, z;
    private long startMs = -1;
    private boolean blocked;
    private Texture dot, ring;

    /** Nowy rozbłysk na polu p (blocked: czerwonawy, bez drogi). */
    public void pulse(Position p, boolean blocked, long nowMs) {
        x = p.x();
        y = p.y();
        z = p.z();
        this.blocked = blocked;
        startMs = nowMs;
    }

    public boolean active(long nowMs) {
        return startMs >= 0 && nowMs - startMs < duration(blocked) && nowMs >= startMs;
    }

    public boolean blocked() { return blocked; }

    static long duration(boolean blocked) { return blocked ? BLOCKED_MS : PULSE_MS; }

    /** Postep 0..1 rozbłysku. */
    static float progress(long elapsedMs, long durationMs) {
        if (elapsedMs <= 0) return 0f;
        if (elapsedMs >= durationMs) return 1f;
        return elapsedMs / (float) durationMs;
    }

    /** Wygaszanie ruchu (ease-out cubic): szybko na poczatku, lagodnie na koncu. */
    static float easeOut(float p) {
        float q = 1f - Math.max(0f, Math.min(1f, p));
        return 1f - q * q * q;
    }

    /** Srednica kropki (w polach) w chwili p. */
    static float diameter(float p, boolean blocked) {
        float end = blocked ? BLOCKED_END_D : END_D;
        return START_D + (end - START_D) * easeOut(p);
    }

    /** Krycie kropki w chwili p: od START_A do zera, gasnie lagodnie (kwadratowo). */
    static float alpha(float p) {
        float q = 1f - Math.max(0f, Math.min(1f, p));
        return START_A * q * q;
    }

    /**
     * Rysuje znacznik w rzedzie mapy row (wywolanie z RowDrawer, przed postaciami tego rzedu).
     * dest: cel trwajacego marszu albo null. Bez alokacji.
     */
    public void drawRow(SpriteBatch b, WorldRenderer wr, int row, int floor, long nowMs, Position dest) {
        boolean pulseHere = startMs >= 0 && y == row && z == floor && active(nowMs);
        boolean destHere = dest != null && dest.y() == row && dest.z() == floor;
        if (!pulseHere && !destHere) return;
        textures();
        if (destHere) {
            float cx = dest.x() + 0.5f, cy = wr.worldY(dest.y()) + 0.5f;
            // spokojne "oddychanie" kropki celu (bez nachalnego migania)
            float breathe = 0.5f + 0.5f * (float) Math.sin(nowMs / 420.0);
            float d = DEST_D * (0.92f + 0.08f * breathe);
            b.setColor(0f, 0f, 0f, 0.22f);
            b.draw(dot, cx - d * 0.75f, cy - d * 0.75f - 0.02f, d * 1.5f, d * 1.5f);
            b.setColor(1f, 0.98f, 0.93f, 0.55f + 0.12f * breathe);
            b.draw(dot, cx - d / 2f, cy - d / 2f, d, d);
        }
        if (pulseHere) {
            float p = progress(nowMs - startMs, duration(blocked));
            float d = diameter(p, blocked), a = alpha(p);
            float cx = x + 0.5f, cy = wr.worldY(y) + 0.5f;
            float g = blocked ? 0.36f : 0.98f, bl = blocked ? 0.3f : 0.93f;
            // lekki cien pod spodem: kropka czytelna tez na jasnym bruku i piasku
            float sd = d * 1.3f;
            b.setColor(0f, 0f, 0f, a * SHADOW_A);
            b.draw(dot, cx - sd / 2f, cy - sd / 2f - 0.015f, sd, sd);
            b.setColor(1f, g, bl, a);
            b.draw(dot, cx - d / 2f, cy - d / 2f, d, d);
            // drugi, slabszy pierscien troche dalej (opozniony), tylko przy udanym dotknieciu
            if (!blocked) {
                float rd = diameter(Math.min(1f, p * 1.15f), false) * 1.32f;
                b.setColor(1f, g, bl, a * 0.7f);
                b.draw(ring, cx - rd / 2f, cy - rd / 2f, rd, rd);
            }
        }
        b.setColor(1f, 1f, 1f, 1f);
    }

    private void textures() {
        if (dot != null) return;
        dot = make(false);
        ring = make(true);
    }

    /** Proceduralna miekka kropka (gaussowski brzeg) albo cienki miekki pierscien, biale z kanalem alfa. */
    private static Texture make(boolean ringShape) {
        int n = 64;
        Pixmap pm = new Pixmap(n, n, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        float c = (n - 1) / 2f;
        for (int py = 0; py < n; py++) {
            for (int px = 0; px < n; px++) {
                float dx = (px - c) / c, dy = (py - c) / c;
                float d = (float) Math.sqrt(dx * dx + dy * dy); // 0 srodek .. 1 brzeg
                float a = ringShape ? ringAlpha(d) : dotAlpha(d);
                pm.drawPixel(px, py, (0xFFFFFF00) | Math.round(a * 255f));
            }
        }
        Texture t = new Texture(pm);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pm.dispose();
        return t;
    }

    /** Krycie kropki w odleglosci d od srodka (0..1): pelne do polowy, miekko do zera przy brzegu. */
    static float dotAlpha(float d) {
        if (d >= 1f) return 0f;
        if (d <= 0.45f) return 1f;
        float t = (d - 0.45f) / 0.55f;
        return (1f - t) * (1f - t) * (1f + 2f * t); // smoothstep w dol
    }

    /** Krycie pierscienia: waski miekki pas przy d = 0.82. */
    static float ringAlpha(float d) {
        float w = Math.abs(d - 0.82f) / 0.14f;
        if (w >= 1f) return 0f;
        return (1f - w) * (1f - w) * (1f + 2f * w);
    }

    public void dispose() {
        if (dot != null) dot.dispose();
        if (ring != null) ring.dispose();
        dot = null;
        ring = null;
    }
}
