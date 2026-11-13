package pl.fawizja.pixel.movement;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.world.Direction;

/** Spokojniejszy joystick (2026-10-29) z 8 rownymi sektorami bez opoznienia (2026-11-12). */
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
    void eightEqualSectorsAnswerImmediately() {
        Direction[] expect = {Direction.EAST, Direction.NORTH_EAST, Direction.NORTH, Direction.NORTH_WEST,
                Direction.WEST, Direction.SOUTH_WEST, Direction.SOUTH, Direction.SOUTH_EAST};
        for (int i = 0; i < 8; i++) {
            for (double off : new double[] {-20, 0, 20}) {
                StickFilter f = new StickFilter();
                assertEquals(expect[i], push(f, i * 45 + off, 1, 0), "sektor " + i + " przesuniecie " + off + " od razu, bez czekania");
            }
        }
        // nawet slabe wychylenie ponad martwa strefa daje skos (zadnego progu mocy dla skosu)
        assertEquals(Direction.NORTH_EAST, push(new StickFilter(), 45, 0.5, 0));
    }

    @Test
    void jitterAroundASectorBoundaryDoesNotFlicker() {
        StickFilter f = new StickFilter();
        assertEquals(Direction.EAST, push(f, 20, 1, 0));
        // granica E/NE lezy na 22,5 stopnia; drgania +-8 stopni wokol niej (14..31) nie zmieniaja kierunku
        for (int i = 0; i < 30; i++) assertEquals(Direction.EAST, push(f, i % 2 == 0 ? 15 : 31, 1, 16L * i));
        // wyrazne przejscie dalej niz histereza: zmiana od razu
        assertEquals(Direction.NORTH_EAST, push(f, 40, 1, 600));
        // i z powrotem: skos trzyma sie jeszcze za granica
        for (int i = 0; i < 30; i++) assertEquals(Direction.NORTH_EAST, push(f, i % 2 == 0 ? 25 : 15 + 12, 1, 700 + 16L * i));
        assertEquals(Direction.EAST, push(f, 5, 1, 1300));
    }
}
