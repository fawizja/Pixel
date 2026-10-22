package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;

/** Pietra w kawalkach 32x32: pusty obszar nie zajmuje pamieci, a odczyt dziala jak dla pelnej tablicy. */
class FloorChunkTest {
    private final TileRegistry tiles = TestData.TILES;

    @Test
    void emptyLargeFloorAllocatesNothing() {
        FloorMap f = new FloorMap(-3, 2048, 2048, tiles);
        assertEquals(0, f.allocatedChunks());
        assertFalse(f.isWalkable(1000, 1000));
        assertFalse(f.hasGround());
        f.set(Layer.WALL, 5, 5, TileType.EMPTY);
        assertEquals(0, f.allocatedChunks(), "clearing an empty tile does not allocate");
    }

    @Test
    void tilesAcrossChunkBordersBehaveLikeBefore() {
        FloorMap f = new FloorMap(0, 100, 70, tiles);
        int grass = tiles.byKey("grass").id();
        int wall = tiles.byKey("stone_wall").id();
        int torch = tiles.byKey("wall_torch").id();
        for (int x = 30; x <= 33; x++) f.set(Layer.GROUND, x, 31, grass);
        f.set(Layer.WALL, 32, 31, wall);
        f.set(Layer.OBJECT, 99, 69, torch);
        assertEquals(3, f.allocatedChunks());
        assertTrue(f.isWalkable(31, 31));
        assertFalse(f.isWalkable(32, 31), "wall blocks");
        assertTrue(f.blocksSight(32, 31));
        assertEquals(grass, f.get(Layer.GROUND, 33, 31));
        assertTrue(f.hasGround());
        assertEquals(1, f.lightSources().size());
        assertEquals(1, f.lightSources(90, 60, 99, 69).size());
        assertEquals(0, f.lightSources(0, 0, 50, 50).size());
        f.set(Layer.OBJECT, 99, 69, TileType.EMPTY);
        assertEquals(0, f.lightSources().size());
    }
}
