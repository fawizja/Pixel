package pl.fawizja.pixel.world;

import java.util.HashSet;
import java.util.Set;

/**
 * Drzwi i okna w murach budynkow: dotkniecie otwiera, drugie zamyka. Na poczatku wszystko jest zamkniete.
 * Okno to prosty odcinek muru budynku (co trzecie pole, mur tego samego rodzaju po obu stronach, wnetrze z jednej
 * strony), drzwi to obiekt "door_open" z mapy. Kto wchodzi w zamkniete drzwi, otwiera je; potwory przez zamkniete
 * drzwi nie przechodza. Ten sam uklad liczy klient (grafika) i serwer.
 */
public final class Openings {

    /** Ksztalty muru: jak w TileArt (pionowy, poziomy, slupek, naroznik). */
    public static final int V = 0, H = 1, POLE = 2, CORNER = 3;
    private static final Set<String> INDOOR = Set.of("wood_floor", "stone_floor", "sandstone_floor", "frozen_floor",
            "marble_floor", "brick_paving");

    private final WorldMap world;
    private final boolean[] doorTile;
    private final boolean[] indoorGround;
    private final Set<Position> open = new HashSet<>();

    public Openings(WorldMap world) {
        this.world = world;
        int max = 0;
        for (TileType t : world.tiles().all()) max = Math.max(max, t.id());
        doorTile = new boolean[max + 1];
        indoorGround = new boolean[max + 1];
        for (TileType t : world.tiles().all()) {
            doorTile[t.id()] = t.key().startsWith("door");
            indoorGround[t.id()] = INDOOR.contains(t.key());
        }
    }

    public boolean isDoorTile(int id) { return id > 0 && id < doorTile.length && doorTile[id]; }

    public boolean isDoor(Position p) {
        FloorMap f = world.floor(p.z());
        return f != null && f.inBounds(p.x(), p.y()) && isDoorTile(f.get(Layer.OBJECT, p.x(), p.y()));
    }

    public boolean isWindow(Position p) {
        FloorMap f = world.floor(p.z());
        if (f == null || !f.inBounds(p.x(), p.y())) return false;
        int id = f.get(Layer.WALL, p.x(), p.y());
        return id != 0 && hasWindow(f, id, p.x(), p.y(), shape(f, p.x(), p.y()));
    }

    public synchronized boolean isOpen(Position p) { return open.contains(p); }

    public boolean isClosedDoor(Position p) { return isDoor(p) && !isOpen(p); }

    /** Otwiera albo zamyka; zwraca nowy stan. */
    public synchronized boolean set(Position p, boolean opened) {
        if (opened) open.add(p); else open.remove(p);
        return opened;
    }

    public synchronized boolean toggle(Position p) { return set(p, !open.contains(p)); }

    /** Drzwi pionowe (mur na polnoc albo poludnie), inaczej poziome. */
    public boolean doorVertical(FloorMap f, int x, int y) {
        if (wallAt(f, x - 1, y) && wallAt(f, x + 1, y)) return false; // drzwi w murze poziomym przy murze wewnetrznym
        return wallAt(f, x, y - 1) || wallAt(f, x, y + 1);
    }

    private static boolean wallAt(FloorMap f, int x, int y) {
        return f.inBounds(x, y) && f.get(Layer.WALL, x, y) != 0;
    }

    /** Mur, w ktorym stoja drzwi (sasiad wzdluz drzwi); 0 gdy brak. */
    public int doorWall(FloorMap f, int x, int y) {
        int[][] around = doorVertical(f, x, y) ? new int[][] {{0, -1}, {0, 1}} : new int[][] {{-1, 0}, {1, 0}};
        for (int[] d : around) {
            int nx = x + d[0], ny = y + d[1];
            if (f.inBounds(nx, ny) && f.get(Layer.WALL, nx, ny) != 0) return f.get(Layer.WALL, nx, ny);
        }
        return 0;
    }

    /**
     * Ksztalt muru z sasiadow (drzwi lacza mury): mur na polnoc i zachod - naroznik, na polnoc - pionowy, na zachod -
     * poziomy, sam mur na poludnie (koniec muru) - pionowy, inaczej slupek (rog polnocno-zachodni).
     */
    public int shape(FloorMap f, int x, int y) {
        boolean n = joins(f, x, y - 1), s = joins(f, x, y + 1), w = joins(f, x - 1, y);
        if (n && w) return CORNER;
        // mur do gory: pionowy (rog poludniowo-zachodni budynku tez); mur na zachod: poziomy - takze w rogu
        // polnocno-wschodnim, gdzie pionowy odcinek wystawalby pole nad linie muru
        if (n) return V;
        if (w) return H;
        if (s && !joins(f, x + 1, y)) return V;
        return POLE;
    }

    /** Okno w murze budynku (tylko na powierzchni i wyzej, przy wnetrzu). */
    public boolean hasWindow(FloorMap f, int id, int x, int y, int shape) {
        if (f.z() < 0) return false;
        if (shape == V) {
            if (Math.floorMod(y + x, 3) != 1) return false;
            if (!sameWall(f, id, x, y - 1) || !sameWall(f, id, x, y + 1)) return false;
            return indoor(f, x, y) || indoor(f, x + 1, y);
        }
        if (shape == H) {
            if (Math.floorMod(x + y, 3) != 1) return false;
            if (!sameWall(f, id, x - 1, y) || !sameWall(f, id, x + 1, y)) return false;
            return indoor(f, x, y) || indoor(f, x, y + 1);
        }
        return false;
    }

    private boolean sameWall(FloorMap f, int id, int x, int y) {
        return f.inBounds(x, y) && f.get(Layer.WALL, x, y) == id;
    }

    /**
     * Ozdoba na murze wnetrza (obraz, gobelin): numer do wyboru grafiki albo -1. Tylko prosty mur (bez okna,
     * drzwi i naroznika) z wnetrzem od poludnia (mur poziomy) albo od wschodu (pionowy), co czwarty wg pozycji.
     */
    public int wallDecor(FloorMap f, int x, int y, int shape) {
        int id = f.get(Layer.WALL, x, y);
        if (id == 0 || hasWindow(f, id, x, y, shape)) return -1;
        int h = (x * 73856093) ^ (y * 19349663);
        if (((h >>> 4) & 3) != 0) return -1;
        if (shape == H) {
            if (!sameWall(f, id, x - 1, y) || !sameWall(f, id, x + 1, y)) return -1;
            if (!indoor(f, x, y + 1) || f.get(Layer.WALL, x, y + 1) != 0) return -1;
        } else if (shape == V) {
            if (!sameWall(f, id, x, y - 1) || !sameWall(f, id, x, y + 1)) return -1;
            if (!indoor(f, x + 1, y) || f.get(Layer.WALL, x + 1, y) != 0) return -1;
        } else {
            return -1;
        }
        return (h >>> 8) & 0xffff;
    }

    private boolean indoor(FloorMap f, int x, int y) {
        if (!f.inBounds(x, y)) return false;
        int g = f.get(Layer.GROUND, x, y);
        return g > 0 && g < indoorGround.length && indoorGround[g];
    }

    private boolean joins(FloorMap f, int x, int y) {
        if (!f.inBounds(x, y)) return false;
        return f.get(Layer.WALL, x, y) != 0 || isDoorTile(f.get(Layer.OBJECT, x, y));
    }
}
