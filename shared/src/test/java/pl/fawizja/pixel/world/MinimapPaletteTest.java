package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;

class MinimapPaletteTest {

    private final WorldMap world = TestData.varden();

    @Test
    void wallsWaterAndTransitionsGetDistinctColors() {
        FloorMap f = world.floor(0);
        TileRegistry t = world.tiles();
        assertEquals(MinimapPalette.WALL, MinimapPalette.color(f, t, 18, 15), "town wall");
        int water = MinimapPalette.color(f, t, 53, 5);
        int bridge = MinimapPalette.color(f, t, 53, 22);
        assertNotEquals(water, bridge);
        assertEquals(0xff, water & 0xff, "opaque");
        assertTrue((water >>> 8 & 0xff) > (water >>> 24 & 0xff), "water is blue");
        Integer key = f.transitions().keySet().iterator().next();
        int tx = key % f.width(), ty = key / f.width();
        assertNotNull(f.transitionAt(tx, ty));
        assertEquals(MinimapPalette.TRANSITION, MinimapPalette.color(f, t, tx, ty));
        assertEquals(MinimapPalette.TRANSPARENT, MinimapPalette.color(f, t, -1, 0));
    }

    @Test
    void blockingObjectsAreDarkerThanTheGroundAround() {
        FloorMap f = world.floor(0);
        TileRegistry t = world.tiles();
        int fountain = MinimapPalette.color(f, t, 31, 22);
        int[] img = MinimapPalette.paint(f, t);
        assertEquals(f.width() * f.height(), img.length);
        assertEquals(fountain, img[22 * f.width() + 31]);
        int sum = (fountain >>> 24) + (fountain >>> 16 & 0xff) + (fountain >>> 8 & 0xff);
        int open = MinimapPalette.color(f, t, 31, 16);
        int sumOpen = (open >>> 24) + (open >>> 16 & 0xff) + (open >>> 8 & 0xff);
        assertTrue(sum < sumOpen, "fountain darker than spawn tile");
    }
}
