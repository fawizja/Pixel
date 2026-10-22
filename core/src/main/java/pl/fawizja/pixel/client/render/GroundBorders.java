package pl.fawizja.pixel.client.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import java.util.Map;
import java.util.Random;
import pl.fawizja.pixel.world.Layer;
import pl.fawizja.pixel.world.TileRegistry;
import pl.fawizja.pixel.world.TileType;

/**
 * Obrzeza podloza jak w Tibii: trawa nachodzi poszarpana krawedzia na ziemie, piasek na wode itd.
 * Podloze o wyzszym priorytecie "wylewa sie" na sasiednie pole o nizszym. Kawalki obrzezy sa skladane przy
 * starcie z tekstury podloza i maski (4 krawedzie + 4 rogi), wiec dzialaja dla kazdej grafiki podloza.
 * Posadzki budynkow, mosty i podobne nie maja obrzezy (priorytet -1).
 */
public final class GroundBorders implements Disposable {

    public static final int N = 0, E = 1, S = 2, W = 3, NE = 4, SE = 5, SW = 6, NW = 7;
    private static final int PX = 32;
    private static final int COLS = 8;

    /** Kolejnosc nakladania: wieksza liczba przykrywa mniejsza. */
    private static final Map<String, Integer> PRIORITY = Map.ofEntries(
            Map.entry("water", 0), Map.entry("ice", 1), Map.entry("lava", 1), Map.entry("swamp", 2),
            Map.entry("cave_floor", 3), Map.entry("abyss_floor", 3), Map.entry("ash", 4), Map.entry("dirt", 5),
            Map.entry("dark_sand", 6), Map.entry("sand", 7), Map.entry("cobblestone", 8), Map.entry("field", 9),
            Map.entry("blighted_grass", 9), Map.entry("grass", 10), Map.entry("jungle_grass", 11), Map.entry("snow", 12));

    private final int[] priority;
    private final TextureRegion[][] pieces;
    private Texture texture;

    /** Zrodlo grafiki podloza 32x32 dla danego kafelka (null = brak). */
    public interface Source {
        Pixmap ground32(TileType t);
    }

    public GroundBorders(TileRegistry registry, Source source) {
        int max = registry.maxId();
        priority = new int[max + 1];
        java.util.Arrays.fill(priority, -1);
        pieces = new TextureRegion[max + 1][];
        int count = 0;
        for (TileType t : registry.all()) {
            if (t.layer() != Layer.GROUND) continue;
            Integer p = PRIORITY.get(t.key());
            if (p != null) {
                priority[t.id()] = p;
                count++;
            }
        }
        if (count == 0) return;
        int rows = (count + COLS - 1) / COLS;
        Pixmap pm = new Pixmap(COLS * PX, rows * 8 * PX, Pixmap.Format.RGBA8888); // kolumna = 8 kawalkow jednego podloza
        pm.setBlending(Pixmap.Blending.None);
        float[][] masks = buildMasks();
        int slot = 0;
        int[][] slots = new int[max + 1][];
        for (TileType t : registry.all()) {
            if (priority[t.id()] < 0) continue;
            Pixmap src = source.ground32(t);
            if (src == null) {
                priority[t.id()] = -1;
                continue;
            }
            int col = slot % COLS, row = slot / COLS;
            slots[t.id()] = new int[] {col, row};
            for (int k = 0; k < 8; k++) paint(pm, src, masks[k], col * PX, row * 8 * PX + k * PX);
            src.dispose();
            slot++;
        }
        texture = new Texture(pm);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pm.dispose();
        for (int id = 0; id <= max; id++) {
            if (slots[id] == null) continue;
            pieces[id] = new TextureRegion[8];
            for (int k = 0; k < 8; k++) {
                pieces[id][k] = new TextureRegion(texture, slots[id][0] * PX, slots[id][1] * 8 * PX + k * PX, PX, PX);
            }
        }
    }

    /** Priorytet podloza (-1 = bez obrzezy). */
    public int priority(int tileId) {
        return tileId > 0 && tileId < priority.length ? priority[tileId] : -1;
    }

    /** Kawalek obrzeza podloza tileId wchodzacy na pole od strony side (N..NW) albo null. */
    public TextureRegion piece(int tileId, int side) {
        TextureRegion[] p = tileId > 0 && tileId < pieces.length ? pieces[tileId] : null;
        return p == null ? null : p[side];
    }

    private static void paint(Pixmap dst, Pixmap src, float[] mask, int ox, int oy) {
        for (int y = 0; y < PX; y++) {
            for (int x = 0; x < PX; x++) {
                float a = mask[y * PX + x];
                if (a <= 0f) {
                    dst.drawPixel(ox + x, oy + y, 0);
                    continue;
                }
                int c = src.getPixel(x, y);
                int r = (c >>> 24) & 0xff, g = (c >>> 16) & 0xff, b = (c >>> 8) & 0xff;
                if (a < 1f) { // brzeg maski przyciemniony jak obwodka obrzeza w Tibii
                    r = r * 3 / 4;
                    g = g * 3 / 4;
                    b = b * 3 / 4;
                }
                dst.drawPixel(ox + x, oy + y, (r << 24) | (g << 16) | (b << 8) | 0xff);
            }
        }
    }

    /** Maski: 1 = pelne podloze sasiada, 0.5 = obwodka, 0 = przezroczyste. Poszarpana krawedz (staly los). */
    static float[][] buildMasks() {
        float[][] m = new float[8][PX * PX];
        Random rnd = new Random(20261022L);
        int[][] depth = new int[4][PX];
        for (int side = 0; side < 4; side++) {
            int d = 6;
            for (int i = 0; i < PX; i++) {
                d += rnd.nextInt(3) - 1;
                d = Math.max(4, Math.min(9, d));
                depth[side][i] = d;
            }
        }
        for (int y = 0; y < PX; y++) {
            for (int x = 0; x < PX; x++) {
                m[N][y * PX + x] = edge(y, depth[N][x]);
                m[S][y * PX + x] = edge(PX - 1 - y, depth[S][x]);
                m[W][y * PX + x] = edge(x, depth[W][y]);
                m[E][y * PX + x] = edge(PX - 1 - x, depth[E][y]);
                m[NW][y * PX + x] = corner(x, y);
                m[NE][y * PX + x] = corner(PX - 1 - x, y);
                m[SW][y * PX + x] = corner(x, PX - 1 - y);
                m[SE][y * PX + x] = corner(PX - 1 - x, PX - 1 - y);
            }
        }
        return m;
    }

    private static float edge(int dist, int depth) {
        if (dist < depth) return 1f;
        if (dist == depth) return 0.5f;
        return 0f;
    }

    private static float corner(int dx, int dy) {
        double r = Math.sqrt((dx + 0.5) * (dx + 0.5) + (dy + 0.5) * (dy + 0.5));
        if (r < 8.5) return 1f;
        if (r < 9.6) return 0.5f;
        return 0f;
    }

    @Override
    public void dispose() {
        if (texture != null) texture.dispose();
    }
}
