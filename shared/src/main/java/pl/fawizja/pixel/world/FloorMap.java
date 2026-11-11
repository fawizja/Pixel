package pl.fawizja.pixel.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Jedno pietro swiata: warstwy kafelkow, zrodla swiatla, cache kolizji i przejscia na inne pietra.
 *
 * <p>Kafelki sa trzymane w chunkach {@value #CHUNK_SIZE}x{@value #CHUNK_SIZE} tworzonych leniwie przy pierwszym
 * niepustym kafelku. Pusty obszar (wiekszosc wyzszych pieter i lochow) nie zajmuje pamieci, a renderowanie,
 * swiatla i {@link #hasGround()} pomijaja cale puste chunki - koszt zalezy od zawartosci, nie od rozmiaru swiata.
 * Kafelek bez chunka zachowuje sie jak pusty: nieprzechodni, nie swieci, nie zaslania widoku.
 */
public final class FloorMap {

    public static final int CHUNK_SHIFT = 4;
    public static final int CHUNK_SIZE = 1 << CHUNK_SHIFT;
    private static final int CHUNK_MASK = CHUNK_SIZE - 1;
    private static final int CHUNK_CELLS = CHUNK_SIZE * CHUNK_SIZE;
    private static final Layer[] LAYERS = Layer.values();

    /** Fragment pietra: warstwy short[] (male zuzycie pamieci), kolizje i swiatlo dla 16x16 kafelkow. */
    private static final class Chunk {
        final short[][] layers = new short[LAYERS.length][CHUNK_CELLS];
        final boolean[] blocked = new boolean[CHUNK_CELLS];
        final byte[] light = new byte[CHUNK_CELLS];
        int groundCells;
        int lightCells;

        Chunk() { java.util.Arrays.fill(blocked, true); }
    }

    private final int z;
    private final int width;
    private final int height;
    private final TileRegistry tiles;
    private final int chunksX;
    private final int chunksY;
    private final Chunk[] chunks;
    private int allocated;
    private final Map<Integer, Transition> transitions = new HashMap<>();

    public FloorMap(int z, int width, int height, TileRegistry tiles) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("bad size");
        this.z = z;
        this.width = width;
        this.height = height;
        this.tiles = tiles;
        this.chunksX = (width + CHUNK_MASK) >> CHUNK_SHIFT;
        this.chunksY = (height + CHUNK_MASK) >> CHUNK_SHIFT;
        this.chunks = new Chunk[chunksX * chunksY];
    }

    public int z() { return z; }
    public int width() { return width; }
    public int height() { return height; }

    public boolean inBounds(int x, int y) { return x >= 0 && y >= 0 && x < width && y < height; }

    private int idx(int x, int y) { return y * width + x; }

    private Chunk chunkAt(int x, int y) {
        return chunks[(y >> CHUNK_SHIFT) * chunksX + (x >> CHUNK_SHIFT)];
    }

    private static int cell(int x, int y) { return ((y & CHUNK_MASK) << CHUNK_SHIFT) | (x & CHUNK_MASK); }

    /** Liczba chunkow w poziomie i pionie. */
    public int chunksX() { return chunksX; }
    public int chunksY() { return chunksY; }

    /** Ile chunkow ma jakiekolwiek kafelki (reszta pietra nie zajmuje pamieci). */
    public int allocatedChunks() { return allocated; }

    /**
     * Czy chunk zawierajacy kafelek ma jakiekolwiek kafelki. Gdy nie - caly obszar {@value #CHUNK_SIZE}x
     * {@value #CHUNK_SIZE} jest pusty i mozna go pominac (np. {@code x |= CHUNK_SIZE - 1} w petli po wierszu).
     */
    public boolean hasChunkAt(int x, int y) {
        return inBounds(x, y) && chunkAt(x, y) != null;
    }

    public int get(Layer layer, int x, int y) {
        if (!inBounds(x, y)) return TileType.EMPTY;
        Chunk c = chunkAt(x, y);
        return c == null ? TileType.EMPTY : c.layers[layer.ordinal()][cell(x, y)];
    }

    public void set(Layer layer, int x, int y, int tileId) {
        if (!inBounds(x, y)) throw new IndexOutOfBoundsException(x + "," + y);
        if (tileId != TileType.EMPTY) {
            TileType t = tiles.get(tileId);
            if (t == null) throw new IllegalArgumentException("unknown tile " + tileId);
            if (t.layer() != layer) throw new IllegalArgumentException(t.key() + " belongs to " + t.layer() + " not " + layer);
        }
        int ci = (y >> CHUNK_SHIFT) * chunksX + (x >> CHUNK_SHIFT);
        Chunk c = chunks[ci];
        if (c == null) {
            if (tileId == TileType.EMPTY) return; // czyszczenie pustego obszaru nie tworzy chunka
            c = chunks[ci] = new Chunk();
            allocated++;
        }
        int i = cell(x, y);
        short[] arr = c.layers[layer.ordinal()];
        if (layer == Layer.GROUND && (arr[i] == 0) != (tileId == 0)) c.groundCells += tileId == 0 ? -1 : 1;
        arr[i] = (short) tileId;
        refreshCell(c, i);
    }

    private void refreshCell(Chunk c, int i) {
        TileType ground = tiles.get(c.layers[Layer.GROUND.ordinal()][i]);
        boolean block = ground == null || !ground.walkable();
        int light = ground == null ? 0 : ground.lightRadius();
        for (Layer l : new Layer[] {Layer.DECORATION, Layer.OBJECT, Layer.WALL}) {
            TileType t = tiles.get(c.layers[l.ordinal()][i]);
            if (t != null) {
                if (!t.walkable()) block = true;
                light = Math.max(light, t.lightRadius());
            }
        }
        c.blocked[i] = block;
        byte newLight = (byte) Math.min(Byte.MAX_VALUE, light);
        if ((c.light[i] > 0) != (newLight > 0)) c.lightCells += newLight > 0 ? 1 : -1;
        c.light[i] = newLight;
    }

    /** Czy kafelek jest przechodni (granice, podloze, obiekty, sciany i przeszkody). */
    public boolean isWalkable(int x, int y) {
        if (!inBounds(x, y)) return false;
        Chunk c = chunkAt(x, y);
        return c != null && !c.blocked[cell(x, y)];
    }

    /** Czy kafelek zaslania widok i pociski (sciany i przeszkody; woda nie zaslania). */
    public boolean blocksSight(int x, int y) {
        if (!inBounds(x, y)) return true;
        Chunk c = chunkAt(x, y);
        if (c == null) return false;
        int i = cell(x, y);
        if (c.layers[Layer.WALL.ordinal()][i] != 0) return true;
        TileType obj = tiles.get(c.layers[Layer.OBJECT.ordinal()][i]);
        return obj != null && !obj.walkable();
    }

    public int lightAt(int x, int y) {
        if (!inBounds(x, y)) return 0;
        Chunk c = chunkAt(x, y);
        return c == null ? 0 : c.light[cell(x, y)];
    }

    /** Zrodla swiatla na pietrze (pochodnie, lampy, ogniska, krysztaly) - tylko z chunkow, ktore je maja. */
    public List<int[]> lightSources() {
        List<int[]> list = new ArrayList<>();
        for (int cy = 0; cy < chunksY; cy++) {
            for (int cx = 0; cx < chunksX; cx++) {
                Chunk c = chunks[cy * chunksX + cx];
                if (c == null || c.lightCells == 0) continue;
                int bx = cx << CHUNK_SHIFT, by = cy << CHUNK_SHIFT;
                for (int i = 0; i < CHUNK_CELLS; i++) {
                    int r = c.light[i];
                    if (r > 0) list.add(new int[] {bx + (i & CHUNK_MASK), by + (i >> CHUNK_SHIFT), r});
                }
            }
        }
        // kolejnosc jak przy przegladaniu calego pietra wierszami
        list.sort((a, b) -> a[1] != b[1] ? Integer.compare(a[1], b[1]) : Integer.compare(a[0], b[0]));
        return list;
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
        for (Chunk c : chunks) if (c != null && c.groundCells > 0) return true;
        return false;
    }
}
