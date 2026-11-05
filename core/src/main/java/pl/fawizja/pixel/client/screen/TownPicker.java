package pl.fawizja.pixel.client.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import java.util.ArrayList;
import java.util.List;
import pl.fawizja.pixel.character.StartingTowns;
import pl.fawizja.pixel.world.FloorMap;
import pl.fawizja.pixel.world.MinimapPalette;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Wybor miasta startowego przy tworzeniu postaci (jak w Tibii): rzad kart z wycinkiem minimapy wokol swiatyni,
 * nazwa i klimat; pod spodem opis zaznaczonego miasta. Wspolny dla gry lokalnej i zakladania konta online.
 */
final class TownPicker implements Disposable {

    /** Wycinek minimapy na karcie: tyle kafelkow wokol swiatyni (w poziomie i w pionie). */
    static final int PREVIEW_TILES = 56;

    private final List<StartingTowns.Info> towns;
    private final List<Texture> previews = new ArrayList<>();
    private final List<Rectangle> cards = new ArrayList<>();
    private final GlyphLayout layout = new GlyphLayout();
    private int selected;
    private float descY;

    TownPicker(WorldMap world, String selectedTown) {
        towns = StartingTowns.list(world);
        FloorMap f = world.floor(0);
        for (StartingTowns.Info t : towns) {
            cards.add(new Rectangle());
            previews.add(f == null ? null : preview(world, f, t));
        }
        select(selectedTown);
    }

    private static Texture preview(WorldMap world, FloorMap f, StartingTowns.Info t) {
        int n = PREVIEW_TILES, x0 = t.temple().x() - n / 2, y0 = t.temple().y() - n / 2;
        Pixmap pm = new Pixmap(n, n, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int c = MinimapPalette.color(f, world.tiles(), x0 + x, y0 + y);
                pm.drawPixel(x, y, c == MinimapPalette.TRANSPARENT ? Color.rgba8888(0.05f, 0.05f, 0.08f, 1f) : c);
            }
        }
        // swiatynia: punkt startu w srodku wycinka
        pm.setColor(1f, 0.85f, 0.2f, 1f);
        pm.fillRectangle(n / 2 - 1, n / 2 - 1, 3, 3);
        Texture tex = new Texture(pm);
        tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pm.dispose();
        return tex;
    }

    List<StartingTowns.Info> towns() { return towns; }

    StartingTowns.Info selected() { return towns.isEmpty() ? null : towns.get(selected); }

    /** Klucz zaznaczonego miasta albo null (swiat bez miast, np. mapa testowa). */
    String selectedKey() { return towns.isEmpty() ? null : towns.get(selected).key(); }

    void select(String town) {
        for (int i = 0; i < towns.size(); i++) if (towns.get(i).key().equalsIgnoreCase(town == null ? "" : town)) selected = i;
    }

    void cycle(int step) {
        if (!towns.isEmpty()) selected = Math.floorMod(selected + step, towns.size());
    }

    /**
     * Uklad kart w pasie (x, y, w, h): karty w jednym rzedzie, pod nimi linijka opisu (descH). Zwraca wysokosc
     * zajeta naprawde (karty + opis).
     */
    void layout(float x, float y, float w, float cardH, float descH) {
        int n = Math.max(1, towns.size());
        float gap = 8, cw = (w - gap * (n - 1)) / n;
        for (int i = 0; i < cards.size(); i++) cards.get(i).set(x + i * (cw + gap), y + descH, cw, cardH);
        descY = y + descH / 2f;
    }

    /** Dotkniecie karty: zaznacza miasto i zwraca true. */
    boolean touch(float px, float py) {
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).contains(px, py)) {
                selected = i;
                return true;
            }
        }
        return false;
    }

    void drawShapes(ShapeRenderer shapes) {
        for (int i = 0; i < cards.size(); i++) {
            Rectangle r = cards.get(i);
            boolean sel = i == selected;
            if (sel) {
                shapes.setColor(0.85f, 0.68f, 0.2f, 1f);
                shapes.rect(r.x - 2, r.y - 2, r.width + 4, r.height + 4);
            }
            shapes.setColor(sel ? 0.3f : 0.16f, sel ? 0.25f : 0.15f, sel ? 0.14f : 0.2f, 1f);
            shapes.rect(r.x, r.y, r.width, r.height);
        }
    }

    void drawText(SpriteBatch batch, BitmapFont font, float descX, float descW) {
        for (int i = 0; i < cards.size(); i++) {
            Rectangle r = cards.get(i);
            StartingTowns.Info t = towns.get(i);
            float textH = 38;
            float s = Math.max(16, Math.min(r.width - 10, r.height - textH - 8));
            Texture p = previews.get(i);
            if (p != null) batch.draw(p, r.x + (r.width - s) / 2f, r.y + r.height - 5 - s, s, s);
            centered(batch, font, t.name(), r.x + r.width / 2f, r.y + 27, i == selected ? Color.GOLD : Color.WHITE);
            centered(batch, font, t.climate(), r.x + r.width / 2f, r.y + 10, Color.LIGHT_GRAY);
        }
        StartingTowns.Info sel = selected();
        if (sel != null) {
            font.setColor(Color.LIGHT_GRAY);
            font.draw(batch, sel.name() + ": " + sel.description(), descX, descY + 6, descW, Align.center, false);
            font.setColor(Color.WHITE);
        }
    }

    private void centered(SpriteBatch batch, BitmapFont font, String s, float cx, float cy, Color c) {
        font.setColor(c);
        layout.setText(font, s);
        font.draw(batch, s, cx - layout.width / 2f, cy + layout.height / 2f);
        font.setColor(Color.WHITE);
    }

    @Override
    public void dispose() {
        for (Texture t : previews) if (t != null) t.dispose();
        previews.clear();
    }
}
