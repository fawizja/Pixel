package pl.fawizja.pixel.client.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.util.HashMap;
import java.util.Map;
import pl.fawizja.pixel.world.FloorMap;
import pl.fawizja.pixel.world.MinimapPalette;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Minimapa: tekstura pietra (piksel na kafelek, budowana leniwie i trzymana w pamieci)
 * rysowana jako wycinek wokol gracza albo cala, dopasowana do prostokata (duza mapa).
 */
final class Minimap {

    private final Map<Integer, Texture> floors = new HashMap<>();
    private Texture dot;
    private WorldMap world;

    private Texture texture(WorldMap w, int z) {
        if (w != world) {
            dispose();
            world = w;
        }
        Texture t = floors.get(z);
        if (t != null) return t;
        FloorMap f = w.floor(z);
        if (f == null) return null;
        int[] img = MinimapPalette.paint(f, w.tiles());
        Pixmap pm = new Pixmap(f.width(), f.height(), Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < f.height(); y++) {
            for (int x = 0; x < f.width(); x++) pm.drawPixel(x, y, img[y * f.width() + x]);
        }
        t = new Texture(pm);
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pm.dispose();
        floors.put(z, t);
        return t;
    }

    private Texture dot() {
        if (dot == null) {
            Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pm.setColor(Color.WHITE);
            pm.fill();
            dot = new Texture(pm);
            pm.dispose();
        }
        return dot;
    }

    /** Wycinek tiles x tiles kafelkow wokol gracza w kwadracie (x, y, size). */
    void drawAround(SpriteBatch batch, WorldMap w, Position p, Position target, java.util.List<Position> marks,
                    float x, float y, float size, int tiles) {
        Texture t = texture(w, p.z());
        if (t != null) {
            int half = tiles / 2;
            int sx0 = p.x() - half, sy0 = p.y() - half;
            int cx0 = Math.max(0, sx0), cy0 = Math.max(0, sy0);
            int cx1 = Math.min(t.getWidth(), sx0 + tiles), cy1 = Math.min(t.getHeight(), sy0 + tiles);
            if (cx1 > cx0 && cy1 > cy0) {
                float px = size / tiles;
                // wiersz 0 tekstury = polnoc = gora okna
                float dx = x + (cx0 - sx0) * px;
                float dy = y + size - (cy1 - sy0) * px;
                batch.draw(t, dx, dy, (cx1 - cx0) * px, (cy1 - cy0) * px, cx0, cy0, cx1 - cx0, cy1 - cy0, false, false);
            }
        }
        float px = size / tiles;
        for (Position m : marks) {
            if (m.z() != p.z()) continue;
            int mx = m.x() - (p.x() - tiles / 2), my = m.y() - (p.y() - tiles / 2);
            if (mx >= 0 && my >= 0 && mx < tiles && my < tiles) {
                marker(batch, x + (mx + 0.5f) * px, y + size - (my + 0.5f) * px, Math.max(4, px * 2), MARK);
            }
        }
        if (target != null && target.z() == p.z()) {
            int tx = target.x() - (p.x() - tiles / 2), ty = target.y() - (p.y() - tiles / 2);
            if (tx >= 0 && ty >= 0 && tx < tiles && ty < tiles) {
                marker(batch, x + (tx + 0.5f) * px, y + size - (ty + 0.5f) * px, Math.max(4, px * 2), TARGET);
            }
        }
        marker(batch, x + (tiles / 2 + 0.5f) * px, y + size - (tiles / 2 + 0.5f) * px, Math.max(4, px * 2), PLAYER);
    }

    /** Cale pietro wpisane w prostokat; zwraca {x, y, skala} narysowanego obrazu (do podpisow). */
    float[] drawWhole(SpriteBatch batch, WorldMap w, Position p, Position target, java.util.List<Position> marks,
                      float x, float y, float bw, float bh) {
        Texture t = texture(w, p.z());
        if (t == null) return null;
        float scale = Math.min(bw / t.getWidth(), bh / t.getHeight());
        float iw = t.getWidth() * scale, ih = t.getHeight() * scale;
        float ix = x + (bw - iw) / 2f, iy = y + (bh - ih) / 2f;
        batch.draw(t, ix, iy, iw, ih, 0, 0, t.getWidth(), t.getHeight(), false, false);
        for (Position m : marks) {
            if (m.z() == p.z()) marker(batch, ix + (m.x() + 0.5f) * scale, iy + ih - (m.y() + 0.5f) * scale, Math.max(5, scale * 2), MARK);
        }
        if (target != null && target.z() == p.z()) {
            marker(batch, ix + (target.x() + 0.5f) * scale, iy + ih - (target.y() + 0.5f) * scale, Math.max(5, scale * 2), TARGET);
        }
        marker(batch, ix + (p.x() + 0.5f) * scale, iy + ih - (p.y() + 0.5f) * scale, Math.max(5, scale * 2), PLAYER);
        return new float[] {ix, iy, scale, ih};
    }

    private static final Color PLAYER = new Color(1f, 0.2f, 0.2f, 1f);
    private static final Color TARGET = new Color(0.3f, 0.9f, 1f, 1f);
    private static final Color MARK = new Color(1f, 0.85f, 0.2f, 1f);

    private void marker(SpriteBatch batch, float cx, float cy, float s, Color c) {
        Color old = batch.getColor().cpy();
        batch.setColor(0, 0, 0, 1);
        batch.draw(dot(), cx - s / 2f - 1, cy - s / 2f - 1, s + 2, s + 2);
        batch.setColor(c);
        batch.draw(dot(), cx - s / 2f, cy - s / 2f, s, s);
        batch.setColor(old);
    }

    void dispose() {
        for (Texture t : floors.values()) t.dispose();
        floors.clear();
    }
}
