package pl.fawizja.pixel.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Jedno pietro swiata: warstwy kafelkow, zrodla swiatla, cache kolizji i przejscia na inne pietra.
 * Dane trzymane w kawalkach 32x32 tworzonych dopiero przy pierwszym kafelku - puste obszary (morze poza mapa,
 * nieuzywane podziemia) nie zajmuja pamieci, wiec duzy swiat z 11 pietrami miesci sie na telefonie.
 */
public final class FloorMap {

    /** Bok kawalka w kafelkach (potega dwojki). */
    public static final int CHUNK = 32;
    private static final int SHIFT = 5;
    private static final int MASK = CHUNK - 1;
    private static final Layer[] LAYERS = Layer.values();

    private static final class Chunk {
        final short[][] layers = new short[LAYERS.length][CHUNK * CHUNK];
        final boolean[] blocked = new boolean[CHUNK * CHUNK];
        final byte[] light = new byte[CHUNK * CHUNK];
        int lights;
        int grounds;

        Chunk() {
            java.util.Arrays.fill(blocked, true);
        }
    }

    private final int z;
    private final int width;
    private final int height;
    private final int chunksX;
    private final TileRegistry tiles;
    private final Chunk[] chunks;
    private final Map<Integer, Transition> transitions = new HashMap<>();

    public FloorMap(int z, int width, int height, TileRegistry tiles) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("bad size");
        this.z = z;
        this.width = width;
        this.height = height;
        this.tiles = tiles;
        this.chunksX = (width + MASK) >> SHIFT;
        this.chunks = new Chunk[chunksX * ((height + MASK) >> SHIFT)];
    }

    public int z() { return z; }
    public int width() { return width; }
    public int height() { return height; }

    public boolean inBounds(int x, int y) { return x >= 0 && y >= 0 && x < width && y < height; }

    private int idx(int x, int y) { return y * width + x; }

    private Chunk chunk(int x, int y) {
        return chunks[(y >> SHIFT) * chunksX + (x >> SHIFT)];
    }

    private static int cell(int x, int y) {
        return ((y & MASK) << SHIFT) | (x & MASK);
    }

    public int get(Layer layer, int x, int y) {
        if (!inBounds(x, y)) return TileType.EMPTY;
        Chunk c = chunk(x, y);
        return c == null ? TileType.EMPTY : c.layers[layer.ordinal()][cell(x, y)];
    }

    public void set(Layer layer, int x, int y, int tileId) {
        if (!inBounds(x, y)) throw new IndexOutOfBoundsException(x + "," + y);
        if (tileId != TileType.EMPTY) {
            TileType t = tiles.get(tileId);
            if (t == null) throw new IllegalArgumentException("unknown tile " + tileId);
            if (t.layer() != layer) throw new IllegalArgumentException(t.key() + " belongs to " + t.layer() + " not " + layer);
        }
        int ci = (y >> SHIFT) * chunksX + (x >> SHIFT);
        Chunk c = chunks[ci];
        if (c == null) {
            if (tileId == TileType.EMPTY) return;
            c = chunks[ci] = new Chunk();
        }
        int i = cell(x, y);
        short[] arr = c.layers[layer.ordinal()];
        if (layer == Layer.GROUND) c.grounds += (tileId != 0 ? 1 : 0) - (arr[i] != 0 ? 1 : 0);
        arr[i] = (short) tileId;
        refreshCell(c, i);
    }

    private void refreshCell(Chunk c, int i) {
        TileType ground = tiles.get(c.layers[Layer.GROUND.ordinal()][i]);
        boolean block = ground == null || !ground.walkable();
        int light = ground == null ? 0 : ground.lightRadius();
        for (int l = 1; l < LAYERS.length; l++) {
            TileType t = tiles.get(c.layers[l][i]);
            if (t != null) {
                if (!t.walkable()) block = true;
                light = Math.max(light, t.lightRadius());
            }
        }
        c.blocked[i] = block;
        int old = c.light[i];
        c.light[i] = (byte) Math.min(Byte.MAX_VALUE, light);
        c.lights += (c.light[i] > 0 ? 1 : 0) - (old > 0 ? 1 : 0);
    }

    /** Czy kafelek jest przechodni (granice, podloze, obiekty, sciany i przeszkody). */
    public boolean isWalkable(int x, int y) {
        if (!inBounds(x, y)) return false;
        Chunk c = chunk(x, y);
        return c != null && !c.blocked[cell(x, y)];
    }

    /**
     * Czy kafelek blokuje tylko przeszkoda z natury ({@link TileType#squeezable()}) na przechodnim podlozu, bez muru
     * - obok takiego pola wolno przejsc po skosie (przecisnac sie miedzy drzewami).
     */
    public boolean isSqueezable(int x, int y) {
        if (!inBounds(x, y)) return false;
        Chunk c = chunk(x, y);
        if (c == null) return false;
        int i = cell(x, y);
        TileType ground = tiles.get(c.layers[Layer.GROUND.ordinal()][i]);
        if (ground == null || !ground.walkable()) return false;
        TileType obj = tiles.get(c.layers[Layer.OBJECT.ordinal()][i]);
        if (obj == null || !obj.squeezable()) return false;
        for (Layer l : new Layer[] {Layer.DECORATION, Layer.WALL}) {
            TileType t = tiles.get(c.layers[l.ordinal()][i]);
            if (t != null && !t.walkable()) return false;
        }
        return true;
    }

    /** Czy kafelek zaslania widok i pociski (sciany i przeszkody; woda nie zaslania). */
    public boolean blocksSight(int x, int y) {
        if (!inBounds(x, y)) return true;
        Chunk c = chunk(x, y);
        if (c == null) return false;
        int i = cell(x, y);
        if (c.layers[Layer.WALL.ordinal()][i] != 0) return true;
        TileType obj = tiles.get(c.layers[Layer.OBJECT.ordinal()][i]);
        return obj != null && !obj.walkable();
    }

    public int lightAt(int x, int y) {
        if (!inBounds(x, y)) return 0;
        Chunk c = chunk(x, y);
        return c == null ? 0 : c.light[cell(x, y)];
    }

    /** Zrodla swiatla na pietrze (pochodnie, lampy, ogniska, krysztaly). */
    public List<int[]> lightSources() {
        return lightSources(0, 0, width - 1, height - 1);
    }

    /** Zrodla swiatla w prostokacie (przegladane tylko kawalki, ktore jakies maja). */
    public List<int[]> lightSources(int x0, int y0, int x1, int y1) {
        List<int[]> list = new ArrayList<>();
        int cx0 = Math.max(0, x0) >> SHIFT, cy0 = Math.max(0, y0) >> SHIFT;
        int cx1 = Math.min(width - 1, x1) >> SHIFT, cy1 = Math.min(height - 1, y1) >> SHIFT;
        for (int cy = cy0; cy <= cy1; cy++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                Chunk c = chunks[cy * chunksX + cx];
                if (c == null || c.lights == 0) continue;
                for (int i = 0; i < CHUNK * CHUNK; i++) {
                    int r = c.light[i];
                    if (r <= 0) continue;
                    int x = (cx << SHIFT) | (i & MASK), y = (cy << SHIFT) | (i >> SHIFT);
                    if (x >= x0 && x <= x1 && y >= y0 && y <= y1) list.add(new int[] {x, y, r});
                }
            }
        }
        return list;
    }

    /** Liczba utworzonych kawalkow 32x32 (diagnostyka pamieci). */
    public int allocatedChunks() {
        int n = 0;
        for (Chunk c : chunks) if (c != null) n++;
        return n;
    }

    public void addTransition(Transition t) {
        if (t.from().z() != z) throw new IllegalArgumentException("transition from other floor");
        if (!inBounds(t.from().x(), t.from().y())) throw new IllegalArgumentException("transition out of bounds " + t);
        transitions.put(idx(t.from().x(), t.from().y()), t);
    }

    public Transition transitionAt(int x, int y) {
        return inBounds(x, y) ? transitions.get(idx(x, y)) : null;
    }

    public Map<Integer, Transition> transitions() { return Collections.unmodifiableMap(transitions); }

    /** Czy pietro ma jakiekolwiek kafelki podloza (puste pietra nie sa renderowane). */
    public boolean hasGround() {
        for (Chunk c : chunks) if (c != null && c.grounds > 0) return true;
        return false;
    }
}
