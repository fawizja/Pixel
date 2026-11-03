package pl.fawizja.pixel.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tarasy gor: brzeg skaly zostaje nizko (zbocza z paczki), dalej poziomy rosna o najwyzej jeden na pole. */
class TerraceLevelsTest {

    private static final int W = WorldRenderer.WIN, M = WorldRenderer.MARGIN, B = WorldRenderer.BLOCK;
    private static final int ROCK = 88, OTHER = 89;

    private static byte[] levels(int[] ids) {
        return WorldRenderer.levelsOf(ids, new int[W * W], id -> id == ROCK || id == OTHER, 100, 100);
    }

    /** Okno z kwadratem skaly od (a, a) do (b, b) w ukladzie okna, reszta bez muru. */
    private static int[] square(int a, int b) {
        int[] ids = new int[W * W];
        for (int y = a; y <= b; y++) for (int x = a; x <= b; x++) ids[y * W + x] = ROCK;
        return ids;
    }

    @Test
    void rimStaysLowAndNeighboursDifferByAtMostOne() {
        byte[] lv = levels(square(M, M + B - 1));
        int max = 0;
        for (int y = 0; y < B; y++) {
            for (int x = 0; x < B; x++) {
                int l = lv[y * B + x];
                max = Math.max(max, l);
                int edge = Math.min(Math.min(x, y), Math.min(B - 1 - x, B - 1 - y));
                if (edge < WorldRenderer.RIM) assertEquals(0, l, "brzeg " + x + "," + y);
                assertTrue(l <= WorldRenderer.MAX_LEVEL);
                if (x + 1 < B) assertTrue(Math.abs(l - lv[y * B + x + 1]) <= 1, "skok w poziomie " + x + "," + y);
                if (y + 1 < B) assertTrue(Math.abs(l - lv[(y + 1) * B + x]) <= 1, "skok w pionie " + x + "," + y);
                if (x + 1 < B && y + 1 < B) assertTrue(Math.abs(l - lv[(y + 1) * B + x + 1]) <= 1, "skok po skosie");
            }
        }
        assertTrue(max >= 1, "duzy masyw ma tarasy");
    }

    @Test
    void thinRidgeHasNoTerraces() {
        byte[] lv = levels(square(M + 10, M + 13));
        for (byte l : lv) assertEquals(0, l);
    }

    @Test
    void otherRockCountsAsEdge() {
        int[] ids = square(M, M + B - 1);
        for (int y = M; y < M + B; y++) ids[y * W + M + 16] = OTHER;   // pas innej skaly przez srodek
        byte[] lv = levels(ids);
        for (int y = 0; y < B; y++) {
            assertEquals(0, lv[y * B + 15]);
            assertEquals(0, lv[y * B + 17]);
        }
    }

    @Test
    void mountainHeightIsWithinRange() {
        for (int y = -20; y < 200; y += 3) {
            for (int x = -20; x < 200; x += 3) {
                int h = WorldRenderer.mountainHeight(x, y);
                assertTrue(h >= 1 && h <= WorldRenderer.MAX_LEVEL);
            }
        }
    }
}
