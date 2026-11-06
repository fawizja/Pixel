package pl.fawizja.pixel.client.render;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import pl.fawizja.pixel.vocation.Harmony;

/**
 * Harmonia mnicha nad postacia w swiecie gry, jak w Tibii: pod paskami zycia i many rzadek {@link Harmony#MAX}
 * malych kwadratowych kropek (pelne = zlote z jasnym pikselem odblasku, puste = ciemne), wspolna czarna obwodka,
 * a przy spokoju (Serene) obok niebieski rombik. Rysowane z bialego piksela w calych pikselach ekranu (piksel-art,
 * ostre przy kazdej skali), bez grafik.
 */
public final class HarmonyPips {

    private HarmonyPips() {}

    /** Wymiary w pikselach ekranu: pip = bok kropki, outline = obwodka/odstep, gapAbove = odstep od paskow. */
    public record Layout(int pip, int outline, int gapAbove) {
        /** Szerokosc rzadku kropek (bez rombika spokoju, zeby rzadek nie skakal przy jego zmianie). */
        public int width() { return Harmony.MAX * pip + (Harmony.MAX + 1) * outline; }
        public int height() { return pip + 2 * outline; }
        /** Rombik spokoju (bok kwadratu, nieparzysty): nieco wyzszy od rzadku, bo romb ma polowe pola kwadratu. */
        public int sereneSize() { return (height() + 2 * outline) | 1; }
    }

    /** Kropka ok. 8% pola (4..9 px): na polu 64 px - 5 px, na telefonie (pole 100+ px) - 9 px. */
    public static Layout layout(float tilePixels, float uiScale) {
        int pip = Math.round(Math.max(4f, Math.min(9f, tilePixels * 0.08f)));
        int outline = Math.max(1, Math.round(pip / 4f));
        int gap = Math.max(1, Math.round(uiScale));
        return new Layout(pip, outline, gap);
    }

    static final float[] OUTLINE = {0.05f, 0.04f, 0.03f, 0.9f};
    static final float[] FULL = {1f, 0.8f, 0.28f, 1f};
    static final float[] FULL_SHADE = {0.78f, 0.52f, 0.12f, 1f};
    static final float[] FULL_SHINE = {1f, 0.98f, 0.8f, 1f};
    static final float[] EMPTY = {0.26f, 0.22f, 0.18f, 1f};
    static final float[] SERENE = {0.42f, 0.74f, 1f, 1f};
    static final float[] SERENE_SHINE = {0.85f, 0.95f, 1f, 1f};

    /** Rysuje rzadek od (x, y) - lewy dolny rog, cale piksele. Batch ustawiony na piksele ekranu, kolor wraca na bialy. */
    public static void draw(SpriteBatch b, TextureRegion white, Layout l, float x, float y, int points, boolean serene) {
        int p = l.pip(), o = l.outline();
        rect(b, white, OUTLINE, x, y, l.width(), l.height());
        for (int i = 0; i < Harmony.MAX; i++) {
            float px = x + o + i * (p + o), py = y + o;
            if (i < points) {
                rect(b, white, FULL_SHADE, px, py, p, p);
                rect(b, white, FULL, px, py + o, p - o, p - o);
                int sh = Math.max(1, p / 3);
                rect(b, white, FULL_SHINE, px, py + p - sh, sh, sh);
            } else {
                rect(b, white, EMPTY, px, py, p, p);
            }
        }
        if (serene) {
            int s = l.sereneSize(), half = s / 2;
            float sx = x + l.width() + o, sy = y + (l.height() - s) / 2;
            diamond(b, white, OUTLINE, sx, sy, half, half);
            diamond(b, white, SERENE, sx, sy, half, half - o);
            diamond(b, white, SERENE_SHINE, sx, sy + Math.max(1, half / 3), half, Math.max(0, half / 3 - 1));
        }
        b.setColor(1f, 1f, 1f, 1f);
    }

    /** Romb ze schodkowych wierszy 1 px; (sx, sy) - lewy dolny rog kwadratu o boku 2*half+1, r - promien rombu. */
    private static void diamond(SpriteBatch b, TextureRegion white, float[] c, float sx, float sy, int half, int r) {
        if (r < 0) return;
        float cx = sx + half, cy = sy + half;
        for (int dy = -r; dy <= r; dy++) {
            int w = r - Math.abs(dy);
            rect(b, white, c, cx - w, cy + dy, 2 * w + 1, 1);
        }
    }

    private static void rect(SpriteBatch b, TextureRegion white, float[] c, float x, float y, float w, float h) {
        b.setColor(c[0], c[1], c[2], c[3]);
        b.draw(white, x, y, w, h);
    }
}
