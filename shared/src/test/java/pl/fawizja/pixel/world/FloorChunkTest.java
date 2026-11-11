package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;

class FloorChunkTest {

    private final WorldMap world = TestData.varden();
    private final TileRegistry tiles = world.tiles();

    @Test
    void emptyFloorAllocatesNothingAndBehavesLikeEmpty() {
        FloorMap f = new FloorMap(3, 100, 70, tiles);
        assertEquals(7, f.chunksX());
        assertEquals(5, f.chunksY());
        f.set(Layer.GROUND, 50, 50, TileType.EMPTY);
        assertEquals(0, f.allocatedChunks());
        assertFalse(f.hasGround());
        assertFalse(f.isWalkable(10, 10));
        assertFalse(f.blocksSight(10, 10));
        assertEquals(0, f.lightAt(10, 10));
        assertFalse(f.hasChunkAt(10, 10));
    }

    @Test
    void settingTileAllocatesOnlyItsChunk() {
        FloorMap f = new FloorMap(0, 100, 70, tiles);
        f.set(Layer.GROUND, 99, 69, tiles.byKey("grass").id());
        assertEquals(1, f.allocatedChunks());
        assertTrue(f.isWalkable(99, 69));
        assertFalse(f.isWalkable(98, 69));
        assertTrue(f.hasChunkAt(96, 64));
        assertFalse(f.hasChunkAt(95, 69));
        assertTrue(f.hasGround());
        f.set(Layer.GROUND, 99, 69, TileType.EMPTY);
        assertFalse(f.hasGround());
    }

    @Test
    void sparseUpperFloorsUseFewChunks() {
        WorldMap big = TestData.world();
        FloorMap top = big.floor(5);
        assertTrue(top.hasGround());
        assertTrue(top.allocatedChunks() < top.chunksX() * top.chunksY() / 4,
                "+5: " + top.allocatedChunks() + " chunkow");
    }
}
