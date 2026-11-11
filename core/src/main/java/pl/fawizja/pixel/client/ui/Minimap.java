package pl.fawizja.pixel.client.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.util.HashMap;
import java.util.Map;
import pl.fawizja.pixel.world.ExploredMap;
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
    /** Mgla: odkryta mapa postaci, ktorej obraz jest w teksturach, i kursor jej zmian (odkryc). */
    private ExploredMap fog;
    private long fogCursor;
    private final int[] dirtyRect = new int[5];
    /** Male obrazy do dosylania zmienionych blokow tekstury (klucz: szerokosc * 4096 + wysokosc). */
    private final Map<Integer, Pixmap> patches = new HashMap<>();
    /** Blok aktualizacji tekstury (kafelki) i szerokosc miekkiej krawedzi mgly. */
    private static final int BLOCK = 32, FEATHER = 2;
    private static final int FOG_R = 8, FOG_G = 8, FOG_B = 12;

    /** Diaboliczne potwory (Kuznia): fioletowe znaczniki widoczne takze we mgle, jak ikona na mapie w Tibii. */
    private java.util.List<Position> fiendish = java.util.List.of();

    void setFiendish(java.util.List<Position> where) { fiendish = where == null ? java.util.List.of() : where; }

    void setHazards(java.util.List<Position> traps) {
        if (traps == null || traps.equals(hazards)) return;
        hazards = traps;
        disposeFloors();
    }

    /**
     * Wiaze obraz z odkryta mapa i dosyla do tekstur bloki z nowymi odkryciami (bez alokacji, gdy nic sie nie
     * zmienilo; zgubione zmiany - wypadly z bufora - przebudowuja tekstury).
     */
    private void syncFog(ExploredMap e) {
        if (e != fog) {
            fog = e;
            fogCursor = e == null ? 0 : e.dirtyTotal();
            disposeFloors();
            return;
        }
        if (e == null) return;
        long total = e.dirtyTotal();
        while (fogCursor < total) {
            if (!e.dirty(fogCursor, dirtyRect)) {
                disposeFloors();
                fogCursor = total;
                return;
            }
            fogCursor++;
            Texture t = floors.get(dirtyRect[0]);
            if (t != null) refresh(t, dirtyRect[0], dirtyRect[1] - FEATHER, dirtyRect[2] - FEATHER,
                    dirtyRect[3] + FEATHER, dirtyRect[4] + FEATHER);
        }
    }

    /** Przerysowuje bloki BLOCK x BLOCK tekstury pietra z pokrywajace prostokat (wlacznie). */
    private void refresh(Texture t, int z, int x0, int y0, int x1, int y1) {
        FloorMap f = world.floor(z);
        if (f == null) return;
        int w = t.getWidth(), h = t.getHeight();
        x0 = Math.max(0, x0);
        y0 = Math.max(0, y0);
        x1 = Math.min(w - 1, x1);
        y1 = Math.min(h - 1, y1);
        for (int by = y0 / BLOCK * BLOCK; by <= y1; by += BLOCK) {
            for (int bx = x0 / BLOCK * BLOCK; bx <= x1; bx += BLOCK) {
                int pw = Math.min(BLOCK, w - bx), ph = Math.min(BLOCK, h - by);
                Pixmap pm = patches.get(pw * 4096 + ph);
                if (pm == null) {
                    pm = new Pixmap(pw, ph, Pixmap.Format.RGBA8888);
                    pm.setBlending(Pixmap.Blending.None);
                    patches.put(pw * 4096 + ph, pm);
                }
                for (int y = 0; y < ph; y++) {
                    for (int x = 0; x < pw; x++) pm.drawPixel(x, y, pixel(f, bx + x, by + y, z));
                }
                t.draw(pm, bx, by);
            }
        }
    }

    /** Kolor piksela: odkryte pole w kolorze mapy, nieodkryte - mgla, przy odkrytych polach rozjasniona (krawedz). */
    private int pixel(FloorMap f, int x, int y, int z) {
        ExploredMap e = fog;
        if (e == null || e.isExplored(x, y, z)) return tileColor(f, x, y, z);
        int noise = ((x * 73_856_093) ^ (y * 19_349_663)) >>> 28 & 3;
        int fr = FOG_R + noise, fg = FOG_G + noise, fb = FOG_B + noise;
        int d = FEATHER + 1;
        if (e.chunkTouched((x - FEATHER) >> 6, (y - FEATHER) >> 6, z) || e.chunkTouched((x + FEATHER) >> 6, (y - FEATHER) >> 6, z)
                || e.chunkTouched((x - FEATHER) >> 6, (y + FEATHER) >> 6, z) || e.chunkTouched((x + FEATHER) >> 6, (y + FEATHER) >> 6, z)) {
            for (int dy = -FEATHER; dy <= FEATHER && d > 1; dy++) {
                for (int dx = -FEATHER; dx <= FEATHER; dx++) {
                    int dd = Math.max(Math.abs(dx), Math.abs(dy));
                    if (dd < d && e.isExplored(x + dx, y + dy, z)) d = dd;
                }
            }
        }
        if (d > FEATHER) return MinimapPalette.rgba(fr, fg, fb);
        // miekka krawedz: 1 pole od odkrytych - polowa koloru mapy, 2 pola - slad
        int c = MinimapPalette.color(f, world.tiles(), x, y);
        float k = d == 1 ? 0.45f : 0.2f;
        int cr = c == MinimapPalette.TRANSPARENT ? fr : c >>> 24, cg = c == MinimapPalette.TRANSPARENT ? fg : c >>> 16 & 0xff,
                cb = c == MinimapPalette.TRANSPARENT ? fb : c >>> 8 & 0xff;
        return MinimapPalette.rgba(Math.round(fr + (cr - fr) * k), Math.round(fg + (cg - fg) * k), Math.round(fb + (cb - fb) * k));
    }

    private int tileColor(FloorMap f, int x, int y, int z) {
        for (int i = 0; i < hazards.size(); i++) {
            Position h = hazards.get(i);
            if (h.x() == x && h.y() == y && h.z() == z) return Color.rgba8888(1f, 0.45f, 0.1f, 1f);
        }
        return MinimapPalette.color(f, world.tiles(), x, y);
    }

    private Texture texture(WorldMap w, int z) {
        if (w != world) {
            disposeFloors();
            world = w;
        }
        Texture t = floors.get(z);
        if (t != null) return t;
        FloorMap f = w.floor(z);
        if (f == null) return null;
        Pixmap pm = new Pixmap(f.width(), f.height(), Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        java.util.List<Position> traps = hazards;
        hazards = java.util.List.of(); // pulapki dorysowane nizej (bez szukania przy kazdym pikselu)
        for (int y = 0; y < f.height(); y++) {
            for (int x = 0; x < f.width(); x++) pm.drawPixel(x, y, pixel(f, x, y, z));
        }
        hazards = traps;
        for (Position h : hazards) {
            if (h.z() == z && f.inBounds(h.x(), h.y()) && explored(h)) pm.drawPixel(h.x(), h.y(), Color.rgba8888(1f, 0.45f, 0.1f, 1f));
        }
        t = new Texture(pm);
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pm.dispose();
        floors.put(z, t);
        return t;
    }

    private boolean explored(Position p) { return fog == null || fog.isExplored(p); }


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
    void drawAround(SpriteBatch batch, WorldMap w, ExploredMap fog, Position p, Position target, java.util.List<Position> marks,
                    float x, float y, float size, int tiles) {
        drawAround(batch, w, fog, p, target, marks, x, y, size, size, tiles);
    }

    /**
     * Wycinek wokol gracza w prostokacie (x, y, bw, bh): tilesW kafelkow w poziomie, w pionie tyle, ile wejdzie przy
     * kwadratowych kafelkach (telefon: minimapa 120 x 80 dp). Gracz na srodku.
     */
    void drawAround(SpriteBatch batch, WorldMap w, ExploredMap fog, Position p, Position target, java.util.List<Position> marks,
                    float x, float y, float bw, float bh, int tilesW) {
        syncFog(fog);
        float px = bw / tilesW;
        int tilesH = Math.max(1, Math.round(bh / px));
        float top = y + bh;
        int sx0 = p.x() - tilesW / 2, sy0 = p.y() - tilesH / 2;
        Texture t = texture(w, p.z());
        if (t != null) {
            int cx0 = Math.max(0, sx0), cy0 = Math.max(0, sy0);
            int cx1 = Math.min(t.getWidth(), sx0 + tilesW), cy1 = Math.min(t.getHeight(), sy0 + tilesH);
            if (cx1 > cx0 && cy1 > cy0) {
                // wiersz 0 tekstury = polnoc = gora okna
                float dx = x + (cx0 - sx0) * px;
                float dy = top - (cy1 - sy0) * px;
                batch.draw(t, dx, Math.max(y, dy), (cx1 - cx0) * px, Math.min(top - Math.max(y, dy), (cy1 - cy0) * px),
                        cx0, cy0, cx1 - cx0, cy1 - cy0, false, false);
            }
        }
        float ms = Math.max(4, px * 2);
        for (Position m : marks) {
            int mx = m.x() - sx0, my = m.y() - sy0;
            if (m.z() == p.z() && explored(m) && mx >= 0 && my >= 0 && mx < tilesW && my < tilesH) marker(batch, x + (mx + 0.5f) * px, top - (my + 0.5f) * px, ms, MARK);
        }
        for (Position f : fiendish) {
            int fx = f.x() - sx0, fy = f.y() - sy0;
            if (f.z() == p.z() && fx >= 0 && fy >= 0 && fx < tilesW && fy < tilesH) marker(batch, x + (fx + 0.5f) * px, top - (fy + 0.5f) * px, ms + 2, FIEND);
        }
        if (target != null && target.z() == p.z()) {
            int tx = target.x() - sx0, ty = target.y() - sy0;
            if (tx >= 0 && ty >= 0 && tx < tilesW && ty < tilesH) marker(batch, x + (tx + 0.5f) * px, top - (ty + 0.5f) * px, ms, TARGET);
        }
        marker(batch, x + (p.x() - sx0 + 0.5f) * px, top - (p.y() - sy0 + 0.5f) * px, ms, PLAYER);
    }

    /**
     * Okragla minimapa (wyglad nowy, 2026-11-11): wycinek wokol gracza w kole o srodku (cx, cy) i promieniu r, px
     * jednostek ekranu na pole (przyblizenie z przyciskow + i -). Bez shaderow i bufora: kolo skladane z poziomych pasow
     * wysokosci 1 jednostki, kazdy z wlasnym wycinkiem tekstury (cieciwa kola), pod spodem ciemne tlo w tym samym
     * ksztalcie. Gracz na srodku kola, znaczniki tylko wewnatrz kola.
     */
    void drawCircle(SpriteBatch batch, WorldMap w, ExploredMap fog, Position p, Position target, java.util.List<Position> marks,
                    float cx, float cy, float r, float px) {
        syncFog(fog);
        Texture t = texture(w, p.z());
        Color old = batch.getColor().cpy();
        int rows = Math.max(1, (int) Math.ceil(r));
        // tlo: ciemne kolo (poza swiatem i pod przezroczystymi polami)
        batch.setColor(FOG_R / 255f, FOG_G / 255f, FOG_B / 255f, 1f);
        for (int j = -rows; j < rows; j++) {
            float hc = chord(r, j);
            if (hc > 0) batch.draw(dot(), cx - hc, cy + j, 2 * hc, 1);
        }
        batch.setColor(old);
        if (t != null) {
            float tw = t.getWidth(), th = t.getHeight();
            // srodek kola = srodek pola gracza
            float ox = p.x() + 0.5f, oy = p.y() + 0.5f;
            for (int j = -rows; j < rows; j++) {
                float hc = chord(r, j);
                if (hc <= 0) continue;
                float x0 = cx - hc, x1 = cx + hc;
                // pole (x, y) tekstury pod lewym i prawym koncem pasa; y w dol (wiersz 0 = polnoc = gora)
                float u0 = ox + (x0 - cx) / px, u1 = ox + (x1 - cx) / px;
                float vTop = oy - (j + 1) / px, vBot = oy - j / px;
                if (vBot <= 0 || vTop >= th) continue;
                if (u0 < 0) {
                    x0 += -u0 * px;
                    u0 = 0;
                }
                if (u1 > tw) {
                    x1 -= (u1 - tw) * px;
                    u1 = tw;
                }
                if (x1 <= x0) continue;
                float y0 = cy + j, hh = 1;
                if (vTop < 0) {
                    hh -= -vTop * px;
                    vTop = 0;
                }
                if (vBot > th) {
                    y0 += (vBot - th) * px;
                    hh -= (vBot - th) * px;
                    vBot = th;
                }
                if (hh <= 0) continue;
                batch.draw(t, x0, y0, x1 - x0, hh, u0 / tw, vBot / th, u1 / tw, vTop / th);
            }
        }
        float ms = Math.max(4, Math.min(7, px * 1.5f));
        for (Position m : marks) {
            if (m.z() == p.z() && explored(m)) circleMarker(batch, m, p, cx, cy, r, px, ms, MARK);
        }
        for (Position f : fiendish) {
            if (f.z() == p.z()) circleMarker(batch, f, p, cx, cy, r, px, ms + 2, FIEND);
        }
        if (target != null && target.z() == p.z()) circleMarker(batch, target, p, cx, cy, r, px, ms, TARGET);
        marker(batch, cx, cy, ms, PLAYER);
    }

    /** Polowa cieciwy kola o promieniu r na wysokosci srodka pasa j (pas od j do j + 1 nad srodkiem). */
    private static float chord(float r, int j) {
        float yc = j + 0.5f;
        float d = r * r - yc * yc;
        return d <= 0 ? 0 : (float) Math.sqrt(d);
    }

    private void circleMarker(SpriteBatch batch, Position m, Position p, float cx, float cy, float r, float px, float s, Color c) {
        float mx = cx + (m.x() - p.x()) * px, my = cy - (m.y() - p.y()) * px;
        float dx = mx - cx, dy = my - cy, lim = r - s / 2f - 1;
        if (dx * dx + dy * dy <= lim * lim) marker(batch, mx, my, s, c);
    }

    /**
     * Duza mapa z przyblizeniem: zoom 1 = cale pietro wpisane w prostokat, 2, 4, 8... = wycinek wokol gracza (przy
     * brzegu swiata dosuniety do krawedzi) wypelniajacy prostokat. Zwraca {x, y, skala, wysokosc, kafelek x lewej
     * krawedzi, kafelek y gornej krawedzi, szerokosc} narysowanego obrazu (do podpisow i dotkniec) albo null.
     */
    float[] drawZoomed(SpriteBatch batch, WorldMap w, ExploredMap fog, Position p, Position target, java.util.List<Position> marks,
                       float x, float y, float bw, float bh, int zoom) {
        syncFog(fog);
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
            if (m.z() == p.z() && explored(m) && in(m, sx, sy, sw, sh)) marker(batch, ix + (m.x() - sx + 0.5f) * scale, iy + ih - (m.y() - sy + 0.5f) * scale, ms, MARK);
        }
        for (Position f : fiendish) {
            if (f.z() == p.z() && in(f, sx, sy, sw, sh)) marker(batch, ix + (f.x() - sx + 0.5f) * scale, iy + ih - (f.y() - sy + 0.5f) * scale, ms + 3, FIEND);
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
    private static final Color FIEND = new Color(0.85f, 0.2f, 1f, 1f);

    private void marker(SpriteBatch batch, float cx, float cy, float s, Color c) {
        Color old = batch.getColor().cpy();
        batch.setColor(0, 0, 0, 1);
        batch.draw(dot(), cx - s / 2f - 1, cy - s / 2f - 1, s + 2, s + 2);
        batch.setColor(c);
        batch.draw(dot(), cx - s / 2f, cy - s / 2f, s, s);
        batch.setColor(old);
    }

    private void disposeFloors() {
        for (Texture t : floors.values()) t.dispose();
        floors.clear();
    }

    void dispose() {
        disposeFloors();
        for (Pixmap pm : patches.values()) pm.dispose();
        patches.clear();
    }
}
