package pl.fawizja.pixel.client.render;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.client.render.CreatureFrames.Pick;
import pl.fawizja.pixel.world.Direction;

/** Wybor strony i klatki potwora: kierunek -> plik, zastepstwa i klatki chodu. */
class CreatureFramesTest {
    private static final boolean[] FULL = {true, true, true, true};
    private static final boolean[] ONLY_DOWN = {true, false, false, false};
    private static final boolean[] NO_LEFT = {true, true, false, true};
    private static final boolean[] NO_RIGHT = {true, true, true, false};

    @Test
    void fourDirectionsMapToTheirOwnSides() {
        assertEquals(new Pick(CreatureFrames.UP, false), CreatureFrames.pick(FULL, Direction.NORTH));
        assertEquals(new Pick(CreatureFrames.DOWN, false), CreatureFrames.pick(FULL, Direction.SOUTH));
        assertEquals(new Pick(CreatureFrames.LEFT, false), CreatureFrames.pick(FULL, Direction.WEST));
        assertEquals(new Pick(CreatureFrames.RIGHT, false), CreatureFrames.pick(FULL, Direction.EAST));
    }

    @Test
    void diagonalsLookEastOrWestAndNullLooksDown() {
        assertEquals(CreatureFrames.RIGHT, CreatureFrames.dirIndex(Direction.NORTH_EAST));
        assertEquals(CreatureFrames.LEFT, CreatureFrames.dirIndex(Direction.SOUTH_WEST));
        assertEquals(CreatureFrames.DOWN, CreatureFrames.dirIndex(null));
    }

    @Test
    void missingSideFallsBackByMirroringTheOtherOne() {
        assertEquals(new Pick(CreatureFrames.RIGHT, true), CreatureFrames.pick(NO_LEFT, Direction.WEST));
        assertEquals(new Pick(CreatureFrames.LEFT, true), CreatureFrames.pick(NO_RIGHT, Direction.EAST));
    }

    @Test
    void singleFrameMonsterUsesFrontAndMirrorsWest() {
        assertEquals(new Pick(CreatureFrames.DOWN, true), CreatureFrames.pick(ONLY_DOWN, Direction.WEST));
        assertEquals(new Pick(CreatureFrames.DOWN, false), CreatureFrames.pick(ONLY_DOWN, Direction.EAST));
        assertEquals(new Pick(CreatureFrames.DOWN, false), CreatureFrames.pick(ONLY_DOWN, Direction.NORTH));
        assertEquals(new Pick(CreatureFrames.DOWN, false), CreatureFrames.pick(null, Direction.NORTH));
        assertFalse(CreatureFrames.hasOwn(ONLY_DOWN, Direction.WEST));
        assertTrue(CreatureFrames.hasOwn(FULL, Direction.WEST));
    }

    @Test
    void walkFrameIsIdleWhenStandingAndAlternatesLegsWhileMoving() {
        assertEquals(0, TileArt.walkFrame(5f, 5f, 5, 5));
        int first = TileArt.walkFrame(4.8f, 5f, 5, 5);
        int second = TileArt.walkFrame(4.2f, 5f, 5, 5);
        assertTrue(first >= 1 && first <= 2 && second >= 1 && second <= 2);
        assertNotEquals(first, second, "legs change halfway through the tile");
        assertNotEquals(TileArt.walkFrame(4.8f, 5f, 5, 5), TileArt.walkFrame(5.8f, 5f, 6, 5), "and on the next tile");
    }
}
