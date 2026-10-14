package pl.fawizja.pixel.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private final List<int[]> protectionZones = new ArrayList<>();

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

    /** Dodaje prostokatna strefe ochronna (bez walki, potwory nie wchodza). */
    public void addProtectionZone(int x0, int y0, int x1, int y1, int z) {
        protectionZones.add(new int[] {Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1), z});
    }

    public boolean isProtectionZone(Position p) {
        for (int[] r : protectionZones) {
            if (p.z() == r[4] && p.x() >= r[0] && p.x() <= r[2] && p.y() >= r[1] && p.y() <= r[3]) return true;
        }
        return false;
    }

    /** Linia wzroku na tym samym pietrze (Bresenham, pomija punkt startowy i docelowy). */
    public boolean hasLineOfSight(Position a, Position b) {
        if (a.z() != b.z()) return false;
        FloorMap f = floors.get(a.z());
        if (f == null) return false;
        int x0 = a.x(), y0 = a.y(), x1 = b.x(), y1 = b.y();
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            if (x0 == x1 && y0 == y1) return true;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
            if ((x0 != x1 || y0 != y1) && f.blocksSight(x0, y0)) return false;
        }
    }

    public Position spawn() { return spawn; }

    public void setSpawn(Position spawn) { this.spawn = spawn; }
}
