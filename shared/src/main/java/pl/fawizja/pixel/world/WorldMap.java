package pl.fawizja.pixel.world;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Caly swiat: pietra od -5 do +5 o wspolnym ukladzie wspolrzednych. */
public final class WorldMap {

    private final String name;
    private final int width;
    private final int height;
    private final TileRegistry tiles;
    private final Map<Integer, FloorMap> floors = new TreeMap<>();
    private Position spawn;

    public WorldMap(String name, int width, int height, TileRegistry tiles) {
        this.name = name;
        this.width = width;
        this.height = height;
        this.tiles = tiles;
    }

    public String name() { return name; }
    public int width() { return width; }
    public int height() { return height; }
    public TileRegistry tiles() { return tiles; }

    public FloorMap floor(int z) { return floors.get(z); }

    public FloorMap getOrCreateFloor(int z) {
        if (z < Position.MIN_FLOOR || z > Position.MAX_FLOOR) throw new IllegalArgumentException("floor " + z);
        return floors.computeIfAbsent(z, k -> new FloorMap(k, width, height, tiles));
    }

    public Map<Integer, FloorMap> floors() { return Collections.unmodifiableMap(floors); }

    public boolean isWalkable(Position p) {
        FloorMap f = floors.get(p.z());
        return f != null && f.isWalkable(p.x(), p.y());
    }

    public Transition transitionAt(Position p) {
        FloorMap f = floors.get(p.z());
        return f == null ? null : f.transitionAt(p.x(), p.y());
    }

    public Position spawn() { return spawn; }

    public void setSpawn(Position spawn) { this.spawn = spawn; }
}
