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
 * wchodzi sie tylko przez drzwi. Dachowka nad drewnem, lupek nad kamieniem, piaskowa dachowka nad piaskowcem. Dach przykrywa posadzke, drzwi i otaczajace mury; znika, gdy gracz stoi pod
 * dachem albo pod wyzszym pietrem, a dach jednego budynku takze wtedy, gdy gracz podchodzi do jego drzwi albo
 * staje tuz przy murze (widac mury i drzwi do dotkniecia, jak w Tibii przy wejsciu do domu).
 */
public final class Roofs {

    public static final byte NONE = 0, RED = 1, SLATE = 2, SAND = 3, WOOD = 4, BROWN = 5, SNOW = 6, ORANGE = 7, ROYAL = 8;
    /** Liczba rodzajow dachu (bez NONE). */
    public static final int KINDS = 8;
    /** Mury, ktore decyduja o dachu niezaleznie od posadzki: mur pruski i bale - gont, tynk - brazowa dachowka. */
    static final java.util.Map<String, Byte> WALL_ROOF = java.util.Map.of("timber_wall", WOOD, "log_wall", WOOD,
            "plaster_wall", BROWN, "whitewash_wall", BROWN);
    static final Set<String> INTERIOR = Set.of("wood_floor", "stone_floor", "sandstone_floor", "frozen_floor", "tomb_floor",
            "marble_floor");
    static final int MAX_BUILDING = 900;

    /**
     * Dach budynku znika, gdy gracz stoi najwyzej {@link #HIDE_RADIUS} pola od jego drzwi albo tuz przy murze
     * ({@link #WALL_RADIUS}) - widac wtedy mury i drzwi do dotkniecia, jak w Tibii przy wejsciu do domu. Mniejszy
     * zasieg przy murach, zeby na waskich ulicach miasta dachy nie znikaly na kazdym kroku.
     */
    public static final int HIDE_RADIUS = 2, WALL_RADIUS = 1;

    private final WorldMap world;
    private final Map<Integer, byte[]> roofs = new HashMap<>();
    /** Numer budynku pod dachem (0 = brak) - dach jednego budynku znika, gdy gracz do niego podchodzi. */
    private final Map<Integer, int[]> buildings = new HashMap<>();
    private int nextBuilding = 1;

    public Roofs(WorldMap world) {
        this.world = world;
        for (Map.Entry<Integer, FloorMap> e : world.floors().entrySet()) {
            if (e.getKey() < 0) continue;
            int[] ids = new int[e.getValue().width() * world.height()];
            roofs.put(e.getKey(), compute(e.getValue(), ids));
            buildings.put(e.getKey(), ids);
        }
    }

    /** Numer budynku z dachem nad polem (0 = brak dachu). */
    public int buildingAt(int x, int y, int z) {
        int[] b = buildings.get(z);
        if (b == null || x < 0 || y < 0 || x >= world.width() || y >= world.height()) return 0;
        return b[y * world.width() + x];
    }

    /**
     * Budynki, ktorych dach znika przy graczu (najwyzej {@link #HIDE_RADIUS} pola od drzwi, {@link #WALL_RADIUS}
     * od posadzki albo muru).
     * Gracz pod dachem i tak nie widzi zadnego dachu ({@link #covered}).
     */
    public java.util.Set<Integer> nearBuildings(Position p) {
        java.util.Set<Integer> out = new java.util.HashSet<>();
        for (int dy = -HIDE_RADIUS; dy <= HIDE_RADIUS; dy++) {
            for (int dx = -HIDE_RADIUS; dx <= HIDE_RADIUS; dx++) {
                int id = buildingAt(p.x() + dx, p.y() + dy, p.z());
                if (id == 0 || out.contains(id)) continue;
                if (Math.max(Math.abs(dx), Math.abs(dy)) <= WALL_RADIUS || world.openings().isDoor(p.translate(dx, dy))) out.add(id);
            }
        }
        return out;
    }

    /** Przezroczystosc dachu budynku (1 = zwykly dach, 0 = dach zdjety); brak wpisu = zwykly dach. */
    public static float alpha(Map<Integer, Float> fade, int building) {
        if (building == 0 || fade.isEmpty()) return 1f;
        Float a = fade.get(building);
        return a == null ? 1f : a;
    }

    /** Czas znikania i pojawiania sie dachu (ms). */
    public static final long FADE_MS = 250;

    /**
     * Plynne zdejmowanie dachu: przezroczystosc kazdego budynku zmierza do 0 (gracz obok) albo 1 (gracz odszedl)
     * w {@link #FADE_MS}. Zwraca biezace przezroczystosci (budynki z pelnym dachem sa pomijane).
     */
    public static Map<Integer, Float> stepFade(Map<Integer, Float> fade, java.util.Set<Integer> hidden, long dtMs) {
        float step = Math.max(0f, Math.min(1f, dtMs / (float) FADE_MS));
        for (Integer id : hidden) fade.putIfAbsent(id, 1f);
        java.util.Iterator<Map.Entry<Integer, Float>> it = fade.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Float> e = it.next();
            float target = hidden.contains(e.getKey()) ? 0f : 1f;
            float a = e.getValue();
            a = a < target ? Math.min(target, a + step) : Math.max(target, a - step);
            if (a >= 1f && target >= 1f) it.remove();
            else e.setValue(a);
        }
        return fade;
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

    private byte[] compute(FloorMap f, int[] ids) {
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
                int stone = 0, sand = 0, frozen = 0, marble = 0;
                while (!q.isEmpty()) {
                    int[] c = q.poll();
                    cells.add(c);
                    String k = key(f, c[0], c[1]);
                    if (!k.equals("wood_floor")) stone++;
                    if (k.equals("sandstone_floor")) sand++;
                    if (k.equals("frozen_floor")) frozen++;
                    if (k.equals("marble_floor")) marble++;
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
                byte kind = sand * 2 > cells.size() ? SAND : stone * 2 > cells.size() ? SLATE : RED;
                Byte byWall = wallRoof(f, cells);
                if (byWall != null) kind = byWall;
                // miasto sniegu: osniezone dachy; palac (marmur): niebieska dachowka; czesc dachowek okragla pomaranczowa
                if (frozen * 2 > cells.size() || snowAround(f, cells)) kind = SNOW;
                else if (marble * 2 > cells.size()) kind = ROYAL;
                else if (kind == RED && ((x * 7 + y * 13) & 1) == 0) kind = ORANGE;
                int building = nextBuilding++;
                for (int[] c : cells) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = c[0] + dx, ny = c[1] + dy;
                            if (!f.inBounds(nx, ny)) continue;
                            if ((dx == 0 && dy == 0) || wall(f, nx, ny)) {
                                out[ny * w + nx] = kind;
                                ids[ny * w + nx] = building;
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    /** Budynek stoi na sniegu: wiekszosc pol tuz za murami to snieg. */
    private boolean snowAround(FloorMap f, List<int[]> cells) {
        int snow = 0, outside = 0;
        for (int[] c : cells) {
            for (int[] d : new int[][] {{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
                int nx = c[0] + d[0], ny = c[1] + d[1];
                if (!f.inBounds(nx, ny) || interior(f, nx, ny) || wall(f, nx, ny)) continue;
                outside++;
                if (key(f, nx, ny).equals("snow")) snow++;
            }
        }
        return outside > 0 && snow * 2 > outside;
    }

    /** Dach wg przewazajacego materialu murow wokol budynku (null = wg posadzki). */
    private Byte wallRoof(FloorMap f, List<int[]> cells) {
        Map<String, Integer> count = new HashMap<>();
        for (int[] c : cells) {
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = c[0] + d[0], ny = c[1] + d[1];
                if (!f.inBounds(nx, ny) || !wall(f, nx, ny)) continue;
                TileType t = world.tiles().get(f.get(Layer.WALL, nx, ny));
                if (t != null) count.merge(t.key(), 1, Integer::sum);
            }
        }
        String top = null;
        for (Map.Entry<String, Integer> e : count.entrySet()) {
            if (top == null || e.getValue() > count.get(top) || (e.getValue().equals(count.get(top)) && e.getKey().compareTo(top) < 0)) top = e.getKey();
        }
        return top == null ? null : WALL_ROOF.get(top);
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
