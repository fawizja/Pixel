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
    /** Pulapki z kolcami: wpisane w obraz pietra jako pomaranczowe punkty. */
    private java.util.List<Position> hazards = java.util.List.of();

    void setHazards(java.util.List<Position> traps) {
        if (traps == null || traps.equals(hazards)) return;
        hazards = traps;
        for (Texture t : floors.values()) t.dispose();
        floors.clear();
    }

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
        for (Position h : hazards) {
            if (h.z() == z && f.inBounds(h.x(), h.y())) pm.drawPixel(h.x(), h.y(), Color.rgba8888(1f, 0.45f, 0.1f, 1f));
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

    /**
     * Duza mapa z przyblizeniem: zoom 1 = cale pietro wpisane w prostokat, 2, 4, 8... = wycinek wokol gracza (przy
     * brzegu swiata dosuniety do krawedzi) wypelniajacy prostokat. Zwraca {x, y, skala, wysokosc, kafelek x lewej
     * krawedzi, kafelek y gornej krawedzi, szerokosc} narysowanego obrazu (do podpisow i dotkniec) albo null.
     */
    float[] drawZoomed(SpriteBatch batch, WorldMap w, Position p, Position target, java.util.List<Position> marks,
                       float x, float y, float bw, float bh, int zoom) {
        Texture t = texture(w, p.z());
        if (t == null) return null;
        int tw = t.getWidth(), th = t.getHeight();
        float scale = Math.min(bw / tw, bh / th) * Math.max(1, zoom);
        int sw = Math.min(tw, (int) Math.floor(bw / scale)), sh = Math.min(th, (int) Math.floor(bh / scale));
        int sx = Math.max(0, Math.min(tw - sw, p.x() - sw / 2)), sy = Math.max(0, Math.min(th - sh, p.y() - sh / 2));
        float iw = sw * scale, ih = sh * scale;
        float ix = x + (bw - iw) / 2f, iy = y + (bh - ih) / 2f;
        batch.draw(t, ix, iy, iw, ih, sx, sy, sw, sh, false, false);
        float ms = Math.max(5, Math.min(12, scale * 2));
        for (Position m : marks) {
            if (m.z() == p.z() && in(m, sx, sy, sw, sh)) marker(batch, ix + (m.x() - sx + 0.5f) * scale, iy + ih - (m.y() - sy + 0.5f) * scale, ms, MARK);
        }
        if (target != null && target.z() == p.z() && in(target, sx, sy, sw, sh)) {
            marker(batch, ix + (target.x() - sx + 0.5f) * scale, iy + ih - (target.y() - sy + 0.5f) * scale, ms + 2, TARGET);
        }
        marker(batch, ix + (p.x() - sx + 0.5f) * scale, iy + ih - (p.y() - sy + 0.5f) * scale, ms, PLAYER);
        return new float[] {ix, iy, scale, ih, sx, sy, iw};
    }

    private static boolean in(Position m, int sx, int sy, int sw, int sh) {
        return m.x() >= sx && m.y() >= sy && m.x() < sx + sw && m.y() < sy + sh;
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
