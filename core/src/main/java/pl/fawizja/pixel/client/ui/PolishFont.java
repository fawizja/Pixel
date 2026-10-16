package pl.fawizja.pixel.client.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.ArrayList;
import java.util.List;

/**
 * Domyslna czcionka libGDX (lsans-15, z biblioteki) uzupelniona o polskie litery. Brakujace znaki sa skladane
 * w pamieci z istniejacych: litera bazowa + rysowana kreska / kropka nad nia, ogonek z odbitej cedylli z "ç",
 * kreska w "ł"/"Ł" rysowana. Nie dodajemy zadnych plikow z zewnatrz.
 */
public final class PolishFont {

    public static final String FNT = "com/badlogic/gdx/utils/lsans-15.fnt";
    public static final String PNG = "com/badlogic/gdx/utils/lsans-15.png";
    /** Litery, ktore skladamy (o/O z kreska sa juz w czcionce). */
    public static final String ADDED = "ąćęłńśźżĄĆĘŁŃŚŹŻ";

    /** Wiersze nad litera na kreske / kropke (ostatni pusty, odstep od litery). */
    private static final int MARK_ROWS = 4;
    /** Kreska (ukosna, z lewego dolu w prawo do gory) i kropka: krycie 0..255, wiersz po wierszu. */
    private static final int[][] ACUTE = {{0, 0, 140, 255}, {0, 150, 255, 90}, {120, 255, 90, 0}};
    private static final int[][] DOT = {{0, 0}, {230, 230}, {230, 230}};

    private PolishFont() {}

    /** Czcionka gotowa do rysowania (wymaga kontekstu graficznego). */
    public static BitmapFont create() {
        FileHandle fnt = Gdx.files.classpath(FNT);
        BitmapFontData data = new BitmapFontData(fnt, false);
        Pixmap page = new Pixmap(Gdx.files.classpath(PNG));
        Pixmap extended = extend(data, page);
        page.dispose();
        Texture tex = new Texture(extended);
        extended.dispose();
        return new BitmapFont(data, new TextureRegion(tex), true);
    }

    /** Jedna warstwa znaku: wiersze [rowFrom, rowTo) glifu src przesuniete o (dx, dy), opcjonalnie odbite. */
    private record Layer(Glyph src, int rowFrom, int rowTo, int dx, int dy, boolean mirror) {}

    /**
     * Dopisuje do {@code data} glify {@link #ADDED} i zwraca nowa strone tekstury (stara + pas na dole).
     * Bez GL - do testow wystarcza Pixmap.
     */
    public static Pixmap extend(BitmapFontData data, Pixmap page) {
        Glyph c = g(data, 'c'), cCedil = g(data, 'ç');
        int cedilRows = c.yoffset - cCedil.yoffset;
        Layer ogonek = new Layer(cCedil, cCedil.height - cedilRows, cCedil.height, 0, 0, true);

        List<Glyph> made = new ArrayList<>();
        List<int[]> pixels = new ArrayList<>();
        for (char ch : ADDED.toCharArray()) {
            Glyph base = g(data, base(ch));
            boolean upper = Character.isUpperCase(ch);
            List<Layer> layers = new ArrayList<>();
            layers.add(new Layer(base, 0, base.height, 0, 0, false));
            boolean ogon = "ąęĄĘ".indexOf(ch) >= 0;
            boolean stroke = ch == 'ł' || ch == 'Ł';
            boolean above = !ogon && !stroke; // kreska albo kropka nad litera
            if (ogon) layers.add(ogonekFor(ogonek, base, ch == 'ę' || ch == 'Ę' ? 0.55f : 0.8f));
            int[] box = bounds(layers);
            if (stroke) { // miejsce na kreske po obu stronach trzonu
                box[0] -= 1;
                box[2] += 1;
            }
            if (above) box[3] += MARK_ROWS;
            int w = box[2] - box[0] + 1, h = box[3] - box[1] + 1;
            int[] alpha = new int[w * h];
            for (Layer l : layers) blit(page, l, box, w, h, alpha);
            if (stroke) stroke(alpha, w, h, box, base, upper);
            if (above) mark(alpha, w, ch == 'ż' || ch == 'Ż' ? DOT : ACUTE);
            // ciemny obrys jak w oryginalnych literach (inaczej bialy znak znika na sniegu i piasku)
            int[] outlined = outline(alpha, w, h);
            Glyph out = new Glyph();
            out.id = ch;
            out.width = w + 2;
            out.height = h + 2;
            out.xoffset = box[0] - 1;
            out.yoffset = box[1] - 1;
            out.xadvance = base.xadvance + (ch == 'ł' ? 1 : 0);
            out.page = 0;
            made.add(out);
            pixels.add(outlined);
        }

        // pas na dole strony, glify jeden obok drugiego
        int rowH = 0, x = 0;
        for (Glyph gl : made) rowH = Math.max(rowH, gl.height);
        Pixmap out = new Pixmap(page.getWidth(), page.getHeight() + rowH + 2, Pixmap.Format.RGBA8888);
        out.setBlending(Pixmap.Blending.None);
        out.drawPixmap(page, 0, 0);
        for (int k = 0; k < made.size(); k++) {
            Glyph gl = made.get(k);
            int[] al = pixels.get(k);
            gl.srcX = x;
            gl.srcY = page.getHeight() + 1;
            for (int yy = 0; yy < gl.height; yy++) {
                for (int xx = 0; xx < gl.width; xx++) {
                    out.drawPixel(gl.srcX + xx, gl.srcY + yy, al[yy * gl.width + xx]);
                }
            }
            x += gl.width + 1;
            data.setGlyph(gl.id, gl);
        }
        return out;
    }

    static char base(char ch) {
        switch (ch) {
            case 'ą': return 'a';
            case 'ć': return 'c';
            case 'ę': return 'e';
            case 'ł': return 'l';
            case 'ń': return 'n';
            case 'ś': return 's';
            case 'ź': case 'ż': return 'z';
            case 'Ą': return 'A';
            case 'Ć': return 'C';
            case 'Ę': return 'E';
            case 'Ł': return 'L';
            case 'Ń': return 'N';
            case 'Ś': return 'S';
            case 'Ź': case 'Ż': return 'Z';
            default: return ch;
        }
    }

    private static Glyph g(BitmapFontData data, char ch) {
        Glyph gl = data.getGlyph(ch);
        if (gl == null) throw new IllegalStateException("Brak znaku w czcionce: " + ch);
        return gl;
    }

    /** Gorna krawedz glifu w ukladzie "y w gore" (yoffset to dolna krawedz). */
    private static int topUp(Glyph gl) { return gl.yoffset + gl.height; }

    /** Ogonek pod litera, w ulamku jej szerokosci (0 = lewa krawedz, 1 = prawa). */
    private static Layer ogonekFor(Layer o, Glyph target, float at) {
        // srodek ogonka w ukladzie piora: xoffset + polowa szerokosci zrodla
        int srcCenter = o.src().xoffset + o.src().width / 2;
        int want = target.xoffset + Math.round(target.width * at);
        // dol "c" w zrodle (nad cedylla) trafia na dol litery docelowej
        int dy = target.yoffset - (o.src().yoffset + (o.rowTo() - o.rowFrom()));
        return new Layer(o.src(), o.rowFrom(), o.rowTo(), want - srcCenter, dy, true);
    }

    /** Znak nad litera: w gornych wierszach, nad srodkiem "atramentu" litery. */
    private static void mark(int[] alpha, int w, int[][] mask) {
        long sum = 0, weighted = 0;
        for (int y = MARK_ROWS; y < alpha.length / w; y++) {
            for (int x = 0; x < w; x++) {
                sum += alpha[y * w + x];
                weighted += (long) alpha[y * w + x] * x;
            }
        }
        float center = sum == 0 ? w / 2f : weighted / (float) sum + 0.5f;
        int mw = mask[0].length;
        int left = Math.round(center - mw / 2f);
        left = Math.max(0, Math.min(w - mw, left));
        for (int r = 0; r < mask.length; r++) {
            for (int k = 0; k < mw; k++) {
                int idx = r * w + left + k;
                alpha[idx] = Math.max(alpha[idx], mask[r][k]);
            }
        }
    }

    /** {minX, minY, maxX, maxY} w ukladzie piora (y w gore). */
    private static int[] bounds(List<Layer> layers) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Layer l : layers) {
            Glyph s = l.src();
            minX = Math.min(minX, s.xoffset + l.dx());
            maxX = Math.max(maxX, s.xoffset + l.dx() + s.width - 1);
            int bottom = s.yoffset + l.dy() + (s.height - l.rowTo());
            int top = s.yoffset + l.dy() + s.height - 1 - l.rowFrom();
            minY = Math.min(minY, bottom);
            maxY = Math.max(maxY, top);
        }
        return new int[] {minX, minY, maxX, maxY};
    }

    private static void blit(Pixmap page, Layer l, int[] box, int w, int h, int[] alpha) {
        Glyph s = l.src();
        for (int row = l.rowFrom(); row < l.rowTo(); row++) {
            int penY = s.yoffset + l.dy() + s.height - 1 - row;
            int outRow = box[3] - penY;
            for (int col = 0; col < s.width; col++) {
                int srcCol = l.mirror() ? s.width - 1 - col : col;
                int px = page.getPixel(s.srcX + srcCol, s.srcY + row);
                // tylko biala czesc litery (jasnosc x krycie); czarny obrys zrodla dorysuje outline()
                int av = (px & 0xff) * ((px >>> 24) & 0xff) / 255;
                if (av == 0) continue;
                int outCol = s.xoffset + l.dx() + col - box[0];
                if (outRow < 0 || outRow >= h || outCol < 0 || outCol >= w) continue;
                int idx = outRow * w + outCol;
                alpha[idx] = Math.max(alpha[idx], av);
            }
        }
    }

    /**
     * Gotowe piksele RGBA8888 glifu z marginesem 1 px: biala litera (krycie z alpha) na polprzezroczystym czarnym
     * obrysie (60% najwiekszego krycia sasiadow), jak w lsans-15.
     */
    static int[] outline(int[] alpha, int w, int h) {
        int ow = w + 2, oh = h + 2;
        int[] out = new int[ow * oh];
        for (int y = 0; y < oh; y++) {
            for (int x = 0; x < ow; x++) {
                int ink = (x >= 1 && x <= w && y >= 1 && y <= h) ? alpha[(y - 1) * w + (x - 1)] : 0;
                int near = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int sx = x - 1 + dx, sy = y - 1 + dy;
                        if (sx >= 0 && sx < w && sy >= 0 && sy < h) near = Math.max(near, alpha[sy * w + sx]);
                    }
                }
                int shadow = near * 6 / 10;
                // biel o kryciu ink nalozona na czern o kryciu shadow
                int a = ink + shadow * (255 - ink) / 255;
                int grey = a == 0 ? 0 : Math.min(255, 255 * ink / a);
                out[y * ow + x] = (grey << 24) | (grey << 16) | (grey << 8) | a;
            }
        }
        return out;
    }

    /** Ukosna kreska przez pionowa kreske litery l / L, na wysokosci ok. 55% litery. */
    private static void stroke(int[] alpha, int w, int h, int[] box, Glyph base, boolean upper) {
        // kolumna trzonu: najwieksza suma krycia
        int stem = 0, best = -1;
        for (int x = 0; x < w; x++) {
            int sum = 0;
            for (int y = 0; y < h; y++) sum += alpha[y * w + x];
            if (sum > best) {
                best = sum;
                stem = x;
            }
        }
        int baseTop = box[3] - (base.yoffset + base.height - 1);
        int baseBottom = box[3] - base.yoffset;
        int mid = baseTop + Math.round((baseBottom - baseTop) * 0.55f);
        // cienka kreska z lewego dolu w prawo do gory, 5 pikseli szerokosci
        int[] dy = {1, 1, 0, -1, -1};
        int from = upper ? -1 : -2;
        for (int k = 0; k < dy.length; k++) {
            int x = stem + from + k;
            int y = mid + dy[k];
            if (x >= 0 && x < w && y >= 0 && y < h) alpha[y * w + x] = 255;
        }
    }
}
