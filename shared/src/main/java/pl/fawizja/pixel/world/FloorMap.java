package pl.fawizja.pixel.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Jedno pietro swiata: warstwy kafelkow (tablice short - male zuzycie pamieci),
 * zrodla swiatla, cache kolizji i przejscia na inne pietra.
 */
public final class FloorMap {

    private final int z;
    private final int width;
    private final int height;
    private final TileRegistry tiles;
    private final EnumMap<Layer, short[]> layers = new EnumMap<>(Layer.class);
    private final boolean[] blocked;
    private final byte[] lightEmission;
    private final Map<Integer, Transition> transitions = new HashMap<>();

    public FloorMap(int z, int width, int height, TileRegistry tiles) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("bad size");
        this.z = z;
        this.width = width;
        this.height = height;
        this.tiles = tiles;
        for (Layer l : Layer.values()) layers.put(l, new short[width * height]);
        this.blocked = new boolean[width * height];
        this.lightEmission = new byte[width * height];
        java.util.Arrays.fill(blocked, true);
    }

    public int z() { return z; }
    public int width() { return width; }
    public int height() { return height; }

    public boolean inBounds(int x, int y) { return x >= 0 && y >= 0 && x < width && y < height; }

    private int idx(int x, int y) { return y * width + x; }

    public int get(Layer layer, int x, int y) {
        if (!inBounds(x, y)) return TileType.EMPTY;
        return layers.get(layer)[idx(x, y)];
    }

    public void set(Layer layer, int x, int y, int tileId) {
        if (!inBounds(x, y)) throw new IndexOutOfBoundsException(x + "," + y);
        if (tileId != TileType.EMPTY) {
            TileType t = tiles.get(tileId);
            if (t == null) throw new IllegalArgumentException("unknown tile " + tileId);
            if (t.layer() != layer) throw new IllegalArgumentException(t.key() + " belongs to " + t.layer() + " not " + layer);
        }
        layers.get(layer)[idx(x, y)] = (short) tileId;
        refreshCell(x, y);
    }

    private void refreshCell(int x, int y) {
        int i = idx(x, y);
        TileType ground = tiles.get(layers.get(Layer.GROUND)[i]);
        boolean block = ground == null || !ground.walkable();
        int light = ground == null ? 0 : ground.lightRadius();
        for (Layer l : new Layer[] {Layer.DECORATION, Layer.OBJECT, Layer.WALL}) {
            TileType t = tiles.get(layers.get(l)[i]);
            if (t != null) {
                if (!t.walkable()) block = true;
                light = Math.max(light, t.lightRadius());
            }
        }
        blocked[i] = block;
        lightEmission[i] = (byte) Math.min(Byte.MAX_VALUE, light);
    }

    /** Czy kafelek jest przechodni (granice, podloze, obiekty, sciany i przeszkody). */
    public boolean isWalkable(int x, int y) {
        return inBounds(x, y) && !blocked[idx(x, y)];
    }

    public int lightAt(int x, int y) {
        return inBounds(x, y) ? lightEmission[idx(x, y)] : 0;
    }

    /** Zrodla swiatla na pietrze (pochodnie, lampy, ogniska, krysztaly). */
    public List<int[]> lightSources() {
        List<int[]> list = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = lightEmission[idx(x, y)];
                if (r > 0) list.add(new int[] {x, y, r});
            }
        }
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
        for (short s : layers.get(Layer.GROUND)) if (s != 0) return true;
        return false;
    }
}
