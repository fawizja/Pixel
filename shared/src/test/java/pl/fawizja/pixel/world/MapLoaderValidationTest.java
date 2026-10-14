package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;

class MapLoaderValidationTest {

    @Test
    void tileRegistryRejectsDuplicates() {
        String csv = "1;a;GROUND;1;0;x;#000;p\n1;b;GROUND;1;0;x;#000;p\n";
        assertThrows(IllegalArgumentException.class, () -> TileRegistry.parse(new StringReader(csv)));
    }

    @Test
    void layerMismatchIsRejected() {
        FloorMap f = new FloorMap(0, 4, 4, TestData.TILES);
        int tree = TestData.TILES.byKey("tree").id();
        assertThrows(IllegalArgumentException.class, () -> f.set(Layer.GROUND, 0, 0, tree));
    }

    @Test
    void emptyTilesAreNotWalkable() {
        FloorMap f = new FloorMap(0, 4, 4, TestData.TILES);
        assertFalse(f.isWalkable(1, 1));
        f.set(Layer.GROUND, 1, 1, TestData.TILES.byKey("grass").id());
        assertTrue(f.isWalkable(1, 1));
        f.set(Layer.OBJECT, 1, 1, TestData.TILES.byKey("rock").id());
        assertFalse(f.isWalkable(1, 1));
        f.set(Layer.OBJECT, 1, 1, TileType.EMPTY);
        assertTrue(f.isWalkable(1, 1));
    }

    @Test
    void positionsOutsideFloorRangeAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Position(0, 0, 6));
        assertThrows(IllegalArgumentException.class, () -> new Position(0, 0, -6));
    }

    @Test
    void joystickVectorMapsToEightDirections() {
        assertEquals(Direction.EAST, Direction.fromVector(1, 0, 0.2f));
        assertEquals(Direction.NORTH, Direction.fromVector(0, 1, 0.2f));
        assertEquals(Direction.SOUTH_WEST, Direction.fromVector(-0.7f, -0.7f, 0.2f));
        assertNull(Direction.fromVector(0.05f, 0.05f, 0.2f));
    }
}
