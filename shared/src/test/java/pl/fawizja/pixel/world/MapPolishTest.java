package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;

/**
 * Zasady szlifu mapy (2026-11-12): pochodnie wisza tylko na scianie widocznej z przechodniej podlogi, nie na
 * drzwiach i oknach; wnetrza budynkow maja swiatlo; schody w gore w lochach opieraja sie o sciane.
 */
class MapPolishTest {

    private final WorldMap world = TestData.world();
    private static final Set<String> INDOOR = Set.of("wood_floor", "stone_floor", "sandstone_floor", "frozen_floor",
            "marble_floor", "brick_paving");

    private boolean light(FloorMap f, int x, int y) {
        return world.wallLights().isLightTile(f.get(Layer.OBJECT, x, y));
    }

    @Test
    void everyWallLightHasAWallAndAWalkableTileInFront() {
        int checked = 0;
        for (FloorMap f : world.floors().values()) {
            for (int[] l : f.lightSources()) {
                int x = l[0], y = l[1];
                if (!light(f, x, y)) continue;
                Position p = new Position(x, y, f.z());
                assertNotEquals(0, f.get(Layer.WALL, x, y), "swiatlo bez muru " + p);
                int face = WallLights.face(f, x, y);
                assertNotEquals(WallLights.FACE_NONE, face, "swiatlo na scianie bez widocznej strony " + p);
                Position front = world.wallLights().front(p);
                assertNotEquals(p, front, "brak pola przed swiatlem " + p);
                assertTrue(world.isWalkable(front), "pole przed swiatlem nieprzechodnie " + p + " -> " + front);
                assertFalse(world.openings().isDoor(front), "swiatlo naprzeciw drzwi " + p);
                assertFalse(world.openings().isWindow(p), "swiatlo na oknie " + p);
                assertFalse(world.openings().isDoorTile(f.get(Layer.OBJECT, x, y)), "swiatlo na drzwiach " + p);
                checked++;
            }
        }
        assertTrue(checked > 3000, "pochodni na mapie: " + checked);
    }

    @Test
    void noObjectFloatsInsideAWall() {
        for (FloorMap f : world.floors().values()) {
            for (int y = 0; y < f.height(); y++) {
                for (int x = 0; x < f.width(); x++) {
                    if (f.get(Layer.WALL, x, y) == 0) continue;
                    int o = f.get(Layer.OBJECT, x, y);
                    if (o == 0 || light(f, x, y) || world.openings().isDoorTile(o)) continue;
                    fail("obiekt " + world.tiles().get(o).key() + " w murze " + x + "," + y + "," + f.z());
                }
            }
        }
    }

    /** Pokoje (zwarte wnetrza budynkow) bez zadnego swiatla w poblizu; pokoj bez mozliwej sciany dla pochodni nie liczy sie. */
    @Test
    void indoorRoomsAreNotLeftDark() {
        int rooms = 0, dark = 0;
        for (int z = 0; z <= 3; z++) {
            FloorMap f = world.floor(z);
            if (f == null) continue;
            boolean[] seen = new boolean[f.width() * f.height()];
            for (int y0 = 0; y0 < f.height(); y0++) {
                for (int x0 = 0; x0 < f.width(); x0++) {
                    if (seen[y0 * f.width() + x0] || !indoor(f, x0, y0)) continue;
                    ArrayDeque<int[]> q = new ArrayDeque<>();
                    java.util.List<int[]> cells = new java.util.ArrayList<>();
                    q.add(new int[] {x0, y0});
                    seen[y0 * f.width() + x0] = true;
                    while (!q.isEmpty()) {
                        int[] c = q.poll();
                        cells.add(c);
                        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                        for (int[] dd : d) {
                            int nx = c[0] + dd[0], ny = c[1] + dd[1];
                            if (f.inBounds(nx, ny) && !seen[ny * f.width() + nx] && indoor(f, nx, ny)) {
                                seen[ny * f.width() + nx] = true;
                                q.add(new int[] {nx, ny});
                            }
                        }
                    }
                    if (cells.size() < 14) continue;
                    rooms++;
                    boolean lit = false;
                    for (int[] c : cells) {
                        for (int dx = -1; dx <= 1 && !lit; dx++) {
                            for (int dy = -1; dy <= 1 && !lit; dy++) {
                                int nx = c[0] + dx, ny = c[1] + dy;
                                if (f.inBounds(nx, ny) && f.lightAt(nx, ny) > 0) lit = true;
                            }
                        }
                        if (lit) break;
                    }
                    if (!lit) dark++;
                }
            }
        }
        assertTrue(rooms > 50, "pokoi: " + rooms);
        assertTrue(dark <= rooms / 10, "ciemne pokoje: " + dark + " z " + rooms);
    }

    private boolean indoor(FloorMap f, int x, int y) {
        int g = f.get(Layer.GROUND, x, y);
        return g != 0 && f.get(Layer.WALL, x, y) == 0 && INDOOR.contains(world.tiles().get(g).key()) && f.isWalkable(x, y);
    }

    @Test
    void undergroundStairsUpLeanOnAWall() {
        int stairs = world.tiles().byKey("stairs_up").id(), ladder = world.tiles().byKey("ladder").id();
        int total = 0, backed = 0;
        for (FloorMap f : world.floors().values()) {
            if (f.z() >= 0) continue;
            for (int y = 1; y < f.height(); y++) {
                for (int x = 0; x < f.width(); x++) {
                    int o = f.get(Layer.OBJECT, x, y);
                    if (o != stairs && o != ladder) continue;
                    total++;
                    if (f.get(Layer.WALL, x, y - 1) != 0) backed++;
                }
            }
        }
        assertTrue(total > 400);
        assertTrue(backed >= total * 0.8, "schody oparte o sciane: " + backed + " z " + total);
    }
}
