package pl.fawizja.pixel.movement;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/** Sterowanie (2026-10-24, Darek: "popraw mechanikę sterowania postacią"): joystick i slizg po murze. */
class ControlFeelTest {

    @Test
    void stickPrefersStraightDirectionsAndKeepsANarrowDiagonal() {
        assertEquals(Direction.EAST, Direction.fromStick(1f, 0.3f, 0.25f));      // ~17 stopni: prosto
        assertEquals(Direction.NORTH, Direction.fromStick(0.35f, 1f, 0.25f));    // ~71 stopni: prosto
        assertEquals(Direction.NORTH_EAST, Direction.fromStick(1f, 1f, 0.25f));  // 45 stopni: skos
        assertEquals(Direction.SOUTH_WEST, Direction.fromStick(-0.8f, -0.9f, 0.25f));
        assertNull(Direction.fromStick(0.1f, 0.1f, 0.25f));
    }

    @Test
    void diagonalIntoAWallSlidesAlongIt() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        GameSession s = new GameSession(TestData.ITEMS, TestData.varden(), pc);
        WorldMap w = s.world();
        Position spot = null;
        // pole, z ktorego krok na polnocny wschod jest zablokowany murem od polnocy, a na wschod wolny
        for (int y = 2; y < w.height() - 2 && spot == null; y++) {
            for (int x = 2; x < w.width() - 2 && spot == null; x++) {
                Position p = new Position(x, y, 0);
                if (w.isWalkable(p) && !w.isWalkable(p.translate(0, -1)) && !w.isWalkable(p.translate(1, -1))
                        && w.isWalkable(p.translate(1, 0)) && w.transitionAt(p.translate(1, 0)) == null
                        && !w.isProtectionZone(p)) spot = p;
            }
        }
        assertNotNull(spot, "no wall spot in Varden");
        s.teleport(spot, 0);
        s.update(10_000, Direction.NORTH_EAST);
        assertEquals(spot.translate(1, 0), pc.position(), "slides east along the wall");
    }
}
