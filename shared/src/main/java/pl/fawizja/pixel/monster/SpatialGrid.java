package pl.fawizja.pixel.monster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pl.fawizja.pixel.world.Position;

/**
 * Siatka przestrzenna potworow (komorki 8x8 na pietro). Zapytania o sasiedztwo
 * przegladaja tylko kilka komorek zamiast wszystkich potworow swiata.
 */
public final class SpatialGrid {

    public static final int CELL = 8;
    private final Map<Long, List<Monster>> cells = new HashMap<>();

    private static long key(int cx, int cy, int z) {
        return ((long) (z + 16) << 40) | ((long) (cx + 100_000) << 20) | (cy + 100_000);
    }

    private static long keyOf(Position p) {
        return key(Math.floorDiv(p.x(), CELL), Math.floorDiv(p.y(), CELL), p.z());
    }

    public void add(Monster m) {
        cells.computeIfAbsent(keyOf(m.position()), k -> new ArrayList<>()).add(m);
    }

    public void remove(Monster m, Position at) {
        List<Monster> l = cells.get(keyOf(at));
        if (l != null) {
            l.remove(m);
            if (l.isEmpty()) cells.remove(keyOf(at));
        }
    }

    public void moved(Monster m, Position from) {
        if (keyOf(from) == keyOf(m.position())) return;
        remove(m, from);
        add(m);
    }

    /** Potwory w promieniu (Chebyshev) na tym samym pietrze. */
    public List<Monster> query(Position center, int radius, List<Monster> out) {
        out.clear();
        int cx0 = Math.floorDiv(center.x() - radius, CELL), cx1 = Math.floorDiv(center.x() + radius, CELL);
        int cy0 = Math.floorDiv(center.y() - radius, CELL), cy1 = Math.floorDiv(center.y() + radius, CELL);
        for (int cy = cy0; cy <= cy1; cy++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                List<Monster> l = cells.get(key(cx, cy, center.z()));
                if (l == null) continue;
                for (Monster m : l) if (m.position().distanceTo(center) <= radius) out.add(m);
            }
        }
        return out;
    }

    public Monster at(Position p) {
        List<Monster> l = cells.get(keyOf(p));
        if (l == null) return null;
        for (Monster m : l) if (m.position().equals(p)) return m;
        return null;
    }
}
