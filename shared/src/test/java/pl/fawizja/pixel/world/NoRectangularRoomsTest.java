package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;

/**
 * Regula wlasciciela (2026-11-12): generator nie robi budynkow ani sal w ksztalcie idealnego prostokata (wyglada zle).
 * Test skanuje kazde pietro -7..+7: prostokat wnetrza wiekszy niz 4x4 (co najmniej 5x5), w calosci podloga bez muru,
 * otoczony murem ze wszystkich czterech stron (z najwyzej trzema przerwami na drzwi) to "pokoj prostokatny".
 * Dom zadan i nowe budowle musza byc nieregularne (zero prostokatow); starsze, reczne budowle sa na liscie LEGACY_MAX
 * (licznik tylko maleje: po poprawieniu kolejnego generatora zmniejsz stala).
 */
class NoRectangularRoomsTest {

    /** Liczba prostokatnych pokoi z czasow przed regula (nie wolno jej przekraczac). */
    static final int LEGACY_MAX = 104;

    record Rect(int z, int x0, int y0, int x1, int y1) {
        @Override public String toString() { return "z" + z + " (" + x0 + "," + y0 + ")-(" + x1 + "," + y1 + ")"; }
    }

    static List<Rect> rectangles(WorldMap world) {
        List<Rect> out = new ArrayList<>();
        for (int z = -7; z <= 7; z++) {
            FloorMap f = world.floor(z);
            if (f == null) continue;
            int w = f.width(), h = f.height();
            boolean[][] wall = new boolean[h][w];
            boolean[][] in = new boolean[h][w];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    wall[y][x] = f.get(Layer.WALL, x, y) != 0;
                    in[y][x] = !wall[y][x] && f.get(Layer.GROUND, x, y) != 0;
                }
            }
            for (int y = 1; y < h - 1; y++) {
                for (int x = 1; x < w - 1; x++) {
                    if (!in[y][x] || !wall[y][x - 1] || !wall[y - 1][x]) continue;
                    int maxW = 0;
                    while (x + maxW < w - 1 && in[y][x + maxW]) maxW++;
                    int maxH = 0;
                    while (y + maxH < h - 1 && in[y + maxH][x]) maxH++;
                    for (int rw = 5; rw <= Math.min(maxW, 60); rw++) {
                        if (!wall[y - 1][x + rw - 1] && countGaps(wall, x, y - 1, x + rw - 1, y - 1) > 3) continue;
                        for (int rh = 5; rh <= Math.min(maxH, 60); rh++) {
                            if (!in[y + rh - 1][x + rw - 1] || !filled(in, x, y, rw, rh)) continue;
                            int gaps = countGaps(wall, x, y - 1, x + rw - 1, y - 1) + countGaps(wall, x, y + rh, x + rw - 1, y + rh)
                                    + countGaps(wall, x - 1, y, x - 1, y + rh - 1) + countGaps(wall, x + rw, y, x + rw, y + rh - 1);
                            if (gaps <= 3) out.add(new Rect(z, x, y, x + rw - 1, y + rh - 1));
                        }
                    }
                }
            }
        }
        return out;
    }

    /** Przerwy w murze (drzwi): kazda szerokosci jednego pola; dluzsza przerwa to nie sciana tego prostokata. */
    private static int countGaps(boolean[][] wall, int x0, int y0, int x1, int y1) {
        int g = 0;
        boolean prev = false;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                boolean gap = !wall[y][x];
                if (gap) g += prev ? 100 : 1;
                prev = gap;
            }
        }
        return g;
    }

    private static boolean filled(boolean[][] in, int x, int y, int w, int h) {
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) if (!in[y + j][x + i]) return false;
        return true;
    }

    @Test
    void newBuildingsAreNeverPerfectRectangles() {
        WorldMap world = TestData.world();
        List<Rect> rects = rectangles(world);
        List<String> inHouses = new ArrayList<>();
        for (Rect r : rects) {
            for (Region reg : world.regions()) {
                if (reg.name().startsWith("Dom ") || reg.name().startsWith("Dwór Skarbnika")) {
                    if (r.z() >= reg.zMin() && r.z() <= reg.zMax() && r.x0() >= reg.x0() && r.x1() <= reg.x1()
                            && r.y0() >= reg.y0() && r.y1() <= reg.y1()) inHouses.add(r + " in " + reg.name());
                }
            }
        }
        System.out.println("rectangular rooms (> 4x4): " + rects.size() + " " + rects);
        assertTrue(inHouses.isEmpty(), "quest houses must be irregular: " + inHouses);
        assertTrue(rects.size() <= LEGACY_MAX, "rectangular rooms: " + rects.size() + " > " + LEGACY_MAX + ": "
                + rects.subList(0, Math.min(12, rects.size())));
    }
}
