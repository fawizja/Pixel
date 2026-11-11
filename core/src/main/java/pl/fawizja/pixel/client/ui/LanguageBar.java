package pl.fawizja.pixel.client.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import pl.fawizja.pixel.i18n.Language;

/**
 * Wybor jezyka (English, Polski, Espanol) na ekranach przed gra: menu, logowanie, tworzenie postaci. Trzy przyciski
 * obok siebie; wybrany jest podswietlony, zmiana dziala od razu (klient zapisuje ja w ustawieniach).
 */
public final class LanguageBar {

    private final Rectangle[] buttons = new Rectangle[Language.values().length];
    private final GlyphLayout layout = new GlyphLayout();

    public LanguageBar() {
        for (int i = 0; i < buttons.length; i++) buttons[i] = new Rectangle();
    }

    /** Ustawia pasek wysrodkowany w poziomie na {@code cx}, z dolna krawedzia {@code y}. */
    public void layout(float cx, float y, float maxWidth) {
        float bw = Math.min(110f, (maxWidth - 2 * 8) / buttons.length), bh = 26f;
        float x = cx - (buttons.length * bw + (buttons.length - 1) * 8) / 2f;
        for (int i = 0; i < buttons.length; i++) buttons[i].set(x + i * (bw + 8), y, bw, bh);
    }

    public Rectangle bounds() {
        return new Rectangle(buttons[0].x, buttons[0].y, buttons[buttons.length - 1].x + buttons[buttons.length - 1].width - buttons[0].x, buttons[0].height);
    }

    /** Dotkniecie: zwraca wybrany jezyk albo null, gdy to nie pasek. */
    public Language hit(Vector2 p) {
        for (int i = 0; i < buttons.length; i++) if (buttons[i].contains(p)) return Language.values()[i];
        return null;
    }

    public void drawShapes(ShapeRenderer shapes, Language current) {
        for (int i = 0; i < buttons.length; i++) {
            Rectangle r = buttons[i];
            boolean sel = Language.values()[i] == current;
            shapes.setColor(sel ? 0.45f : 0.18f, sel ? 0.36f : 0.17f, sel ? 0.12f : 0.22f, 1f);
            shapes.rect(r.x, r.y, r.width, r.height);
        }
    }

    public void drawText(SpriteBatch batch, BitmapFont font, Language current) {
        for (int i = 0; i < buttons.length; i++) {
            Rectangle r = buttons[i];
            Language l = Language.values()[i];
            font.setColor(l == current ? Color.GOLD : Color.WHITE);
            layout.setText(font, l.label);
            font.draw(batch, l.label, r.x + (r.width - layout.width) / 2f, r.y + r.height / 2f + layout.height / 2f);
        }
        font.setColor(Color.WHITE);
    }
}
