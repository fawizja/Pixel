package pl.fawizja.pixel.movement;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.world.Direction;

/** Klawiatura: dwa klawisze naraz = skos, blok numeryczny 1/3/7/9 = skosy. */
class KeyboardDirectionTest {

    @Test
    void singleKeysAreStraightAndTwoKeysMakeADiagonal() {
        assertNull(KeyboardDirection.of(false, false, false, false));
        assertEquals(Direction.WEST, KeyboardDirection.of(true, false, false, false));
        assertEquals(Direction.NORTH, KeyboardDirection.of(false, false, true, false));
        assertEquals(Direction.NORTH_EAST, KeyboardDirection.of(false, true, true, false));
        assertEquals(Direction.NORTH_WEST, KeyboardDirection.of(true, false, true, false));
        assertEquals(Direction.SOUTH_EAST, KeyboardDirection.of(false, true, false, true));
        assertEquals(Direction.SOUTH_WEST, KeyboardDirection.of(true, false, false, true));
    }

    @Test
    void oppositeKeysCancelOut() {
        assertNull(KeyboardDirection.of(true, true, false, false));
        assertEquals(Direction.NORTH, KeyboardDirection.of(true, true, true, false), "A+D+W = W");
        assertNull(KeyboardDirection.of(true, true, true, true));
    }

    @Test
    void numpadHasDiagonalsAndStraightKeys() {
        assertEquals(Direction.SOUTH_WEST, KeyboardDirection.of(false, false, false, false, 1));
        assertEquals(Direction.SOUTH, KeyboardDirection.of(false, false, false, false, 2));
        assertEquals(Direction.SOUTH_EAST, KeyboardDirection.of(false, false, false, false, 3));
        assertEquals(Direction.WEST, KeyboardDirection.of(false, false, false, false, 4));
        assertNull(KeyboardDirection.of(false, false, false, false, 5));
        assertEquals(Direction.EAST, KeyboardDirection.of(false, false, false, false, 6));
        assertEquals(Direction.NORTH_WEST, KeyboardDirection.of(false, false, false, false, 7));
        assertEquals(Direction.NORTH, KeyboardDirection.of(false, false, false, false, 8));
        assertEquals(Direction.NORTH_EAST, KeyboardDirection.of(false, false, false, false, 9));
        assertEquals(Direction.NORTH_EAST, KeyboardDirection.of(false, true, false, false, 8), "strzalka + numpad tez laczy");
    }
}
