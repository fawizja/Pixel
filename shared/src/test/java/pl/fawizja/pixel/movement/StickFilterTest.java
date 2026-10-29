package pl.fawizja.pixel.movement;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.world.Direction;

/** Spokojniejszy joystick (2026-10-29, Darek: "joystick za czuly"). */
class StickFilterTest {

    private static float[] at(double deg, double len) {
        double r = Math.toRadians(deg);
        return new float[] {(float) (Math.cos(r) * len), (float) (Math.sin(r) * len)};
    }

    private static Direction push(StickFilter f, double deg, double len, long t) {
        float[] v = at(deg, len);
        return f.update(v[0], v[1], t);
    }

    @Test
    void smallPushDoesNotMoveAndReleaseHasHysteresis() {
        StickFilter f = new StickFilter();
        assertNull(push(f, 0, 0.3, 0), "lekkie dotkniecie - bez ruchu");
        assertNull(push(f, 0, 0.44, 10));
        assertEquals(Direction.EAST, push(f, 0, 0.5, 20));
        assertEquals(Direction.EAST, push(f, 0, 0.35, 30), "lekkie cofniecie kciuka nie zatrzymuje");
        assertNull(push(f, 0, 0.2, 40));
        assertNull(push(f, 0, 0.4, 50), "po zatrzymaniu znow trzeba wyraznie wychylic");
    }

    @Test
    void straightDirectionsWinAndDiagonalNeedsAClearDiagonal() {
        StickFilter f = new StickFilter();
        assertEquals(Direction.EAST, push(f, 30, 1, 0));
        f.reset();
        assertEquals(Direction.NORTH, push(f, 60, 1, 0));
        f.reset();
        assertEquals(Direction.NORTH_EAST, push(f, 45, 1, 0));
        f.reset();
        assertEquals(Direction.NORTH, push(f, 45, 0.5, 0), "slabe wychylenie po skosie - prosto");
        f.reset();
        assertEquals(Direction.SOUTH_WEST, push(f, 228, 0.9, 0));
        f.reset();
        assertEquals(Direction.WEST, push(f, 200, 0.9, 0));
    }

    @Test
    void jitterAroundABoundaryDoesNotFlipDirections() {
        StickFilter f = new StickFilter();
        assertEquals(Direction.EAST, push(f, 20, 1, 0));
        // kciuk drga miedzy 25 a 34 stopniami (poza klinem skosu): zostaje wschod
        long t = 0;
        for (int i = 0; i < 20; i++) {
            t += 16;
            assertEquals(Direction.EAST, push(f, i % 2 == 0 ? 25 : 34, 1, t));
        }
        // slabe wychylenie drgajace wokol granicy 45 stopni (40/50): bez skakania wschod/polnoc
        for (int i = 0; i < 20; i++) {
            t += 100;
            assertEquals(Direction.EAST, push(f, i % 2 == 0 ? 40 : 50, 0.5, t));
        }
        // wyrazny skret na polnoc: zmiana dopiero po chwili trzymania
        assertEquals(Direction.EAST, push(f, 80, 1, t += 16));
        assertEquals(Direction.EAST, push(f, 80, 1, t += 30));
        assertEquals(Direction.NORTH, push(f, 80, 1, t += StickFilter.SETTLE_MS));
        // skos trzyma sie w szerszym klinie
        f.reset();
        assertEquals(Direction.NORTH_EAST, push(f, 45, 1, 0));
        assertEquals(Direction.NORTH_EAST, push(f, 60, 1, 200));
        assertEquals(Direction.NORTH_EAST, push(f, 60, 1, 400));
    }
}
