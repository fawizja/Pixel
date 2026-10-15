package pl.fawizja.pixel.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.client.ui.PolishFont;

/** Polskie litery skladane z domyslnej czcionki: kazda istnieje, jest wieksza od litery bazowej i ma piksele. */
class PolishFontTest {

    @BeforeAll
    static void natives() {
        GdxNativesLoader.load();
        Gdx.files = new Lwjgl3Files();
    }

    @Test
    void everyPolishLetterIsComposedFromTheBaseLetterAndAMark() {
        BitmapFontData data = new BitmapFontData(Gdx.files.classpath(PolishFont.FNT), false);
        Pixmap page = new Pixmap(Gdx.files.classpath(PolishFont.PNG));
        int pageH = page.getHeight();
        Pixmap out = PolishFont.extend(data, page);
        assertTrue(out.getHeight() > pageH, "nowy pas na dole strony");
        for (char ch : PolishFont.ADDED.toCharArray()) {
            Glyph g = data.getGlyph(ch);
            assertNotNull(g, "brak " + ch);
            Glyph base = data.getGlyph(baseOf(ch));
            assertTrue(ink(out, g) > ink(out, base), ch + ": znak diakrytyczny dodaje piksele");
            assertTrue(g.height >= base.height, ch + ": nie nizsza od litery bazowej");
            assertTrue(g.srcY >= pageH && g.srcX + g.width <= out.getWidth(), ch + ": w nowym pasie");
        }
        // stare litery nienaruszone, o z kreska bylo w czcionce juz wczesniej
        assertEquals('a', data.getGlyph('a').id);
        assertNotNull(data.getGlyph('ó'));
        assertNotNull(data.getGlyph('Ó'));
        PixmapIO.writePNG(Gdx.files.absolute(System.getProperty("user.dir") + "/build/polish-font-sample.png"),
                sample(data, out, "ąćęłńóśźż ĄĆĘŁŃÓŚŹŻ Łódź"));
        out.dispose();
        page.dispose();
    }

    private static char baseOf(char ch) {
        String from = PolishFont.ADDED;
        String to = "acelnszzACELNSZZ";
        return to.charAt(from.indexOf(ch));
    }

    private static int ink(Pixmap p, Glyph g) {
        int n = 0;
        for (int y = 0; y < g.height; y++) {
            for (int x = 0; x < g.width; x++) if ((p.getPixel(g.srcX + x, g.srcY + y) & 0xff) > 60) n++;
        }
        return n;
    }

    /** Podglad tekstu (do obejrzenia przez czlowieka): skala 3, czarne tlo. */
    private static Pixmap sample(BitmapFontData data, Pixmap page, String text) {
        int scale = 5, lineTop = 4;
        Pixmap img = new Pixmap(200 * scale, 26 * scale, Pixmap.Format.RGBA8888);
        img.setColor(0, 0, 0, 1);
        img.fill();
        int pen = 4;
        for (char ch : text.toCharArray()) {
            Glyph g = data.getGlyph(ch);
            if (g == null) continue;
            int top = lineTop - (g.yoffset + g.height); // y w gore, 0 = gora linii
            for (int y = 0; y < g.height; y++) {
                for (int x = 0; x < g.width; x++) {
                    int a = page.getPixel(g.srcX + x, g.srcY + y) & 0xff;
                    if (a == 0) continue;
                    int px = (pen + g.xoffset + x) * scale, py = (top + y) * scale;
                    img.setColor(a / 255f, a / 255f, a / 255f, 1);
                    img.fillRectangle(px, py, scale, scale);
                }
            }
            pen += g.xadvance;
        }
        return img;
    }
}
