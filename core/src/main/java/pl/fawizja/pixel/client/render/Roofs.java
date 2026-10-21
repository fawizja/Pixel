package pl.fawizja.pixel.client.render;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import pl.fawizja.pixel.world.FloorMap;
import pl.fawizja.pixel.world.Layer;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.TileType;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Dachy budynkow liczone z mapy (widok jak w Tibii): budynek to zamkniety murami obszar posadzki, do ktorego
 * wchodzi sie tylko przez drzwi. Dach przykrywa posadzke, drzwi i otaczajace mury; znika, gdy gracz stoi pod
 * dachem albo pod wyzszym pietrem.
 */
public final class Roofs {

    public static final byte NONE = 0, RED = 1, SLATE = 2;
    static final Set<String> INTERIOR = Set.of("wood_floor", "stone_floor", "sandstone_floor", "frozen_floor", "tomb_floor");
    static final int MAX_BUILDING = 900;

    private final WorldMap world;
    private final Map<Integer, byte[]> roofs = new HashMap<>();

    public Roofs(WorldMap world) {
        this.world = world;
        for (Map.Entry<Integer, FloorMap> e : world.floors().entrySet()) {
            if (e.getKey() >= 0) roofs.put(e.getKey(), compute(e.getValue()));
        }
    }

    /** Rodzaj dachu nad polem (NONE, RED, SLATE). */
    public byte at(int x, int y, int z) {
        byte[] r = roofs.get(z);
        if (r == null || x < 0 || y < 0 || x >= world.width() || y >= world.height()) return NONE;
        return r[y * world.width() + x];
    }

    /** Gracz pod dachem albo pod wyzszym pietrem: wtedy dachy i wyzsze pietra nie sa rysowane. */
    public boolean covered(Position p) {
        if (at(p.x(), p.y(), p.z()) != NONE) return true;
        for (Map.Entry<Integer, FloorMap> e : world.floors().entrySet()) {
            if (e.getKey() <= p.z()) continue;
            FloorMap f = e.getValue();
            if (f.get(Layer.GROUND, p.x(), p.y()) != TileType.EMPTY || f.get(Layer.WALL, p.x(), p.y()) != TileType.EMPTY) return true;
        }
        return false;
    }

    private byte[] compute(FloorMap f) {
        int w = f.width(), h = world.height();
        byte[] out = new byte[w * h];
        boolean[] seen = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (seen[y * w + x] || !interior(f, x, y) || wall(f, x, y) || door(f, x, y)) continue;
                List<int[]> cells = new ArrayList<>();
                ArrayDeque<int[]> q = new ArrayDeque<>();
                q.add(new int[] {x, y});
                seen[y * w + x] = true;
                boolean enclosed = true;
                int stone = 0;
                while (!q.isEmpty()) {
                    int[] c = q.poll();
                    cells.add(c);
                    if (!key(f, c[0], c[1]).equals("wood_floor")) stone++;
                    if (door(f, c[0], c[1])) continue;            // drzwi naleza do budynku, ale nie prowadza dalej
                    for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nx = c[0] + d[0], ny = c[1] + d[1];
                        if (!f.inBounds(nx, ny)) { enclosed = false; continue; }
                        if (wall(f, nx, ny) || seen[ny * w + nx]) continue;
                        if (!interior(f, nx, ny)) { enclosed = false; continue; }
                        seen[ny * w + nx] = true;
                        q.add(new int[] {nx, ny});
                    }
                }
                if (!enclosed || cells.size() > MAX_BUILDING) continue;
                byte kind = stone * 2 > cells.size() ? SLATE : RED;
                for (int[] c : cells) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = c[0] + dx, ny = c[1] + dy;
                            if (!f.inBounds(nx, ny)) continue;
                            if ((dx == 0 && dy == 0) || wall(f, nx, ny)) out[ny * w + nx] = kind;
                        }
                    }
                }
            }
        }
        return out;
    }

    private String key(FloorMap f, int x, int y) {
        TileType t = world.tiles().get(f.get(Layer.GROUND, x, y));
        return t == null ? "" : t.key();
    }

    private boolean interior(FloorMap f, int x, int y) { return INTERIOR.contains(key(f, x, y)); }

    private static boolean wall(FloorMap f, int x, int y) { return f.get(Layer.WALL, x, y) != TileType.EMPTY; }

    private boolean door(FloorMap f, int x, int y) {
        TileType t = world.tiles().get(f.get(Layer.OBJECT, x, y));
        return t != null && t.key().equals("door_open");
    }
}
