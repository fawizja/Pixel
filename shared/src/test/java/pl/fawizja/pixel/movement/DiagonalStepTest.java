package pl.fawizja.pixel.movement;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterFactory;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.config.StartingConfig;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.WorldMap;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Position;

class DiagonalStepTest {
    @Test
    void diagonalStepCostsThreeStraightStepsLikeTibia() {
        PlayerCharacter pc = new CharacterFactory(StartingConfig.loadDefault(), TestData.ITEMS)
                .create("Ola", Vocation.PALADIN, new Position(10, 10, 0));
        long straight = MovementSystem.stepDurationMs(pc, Direction.EAST);
        assertEquals(straight * 3, MovementSystem.stepDurationMs(pc, Direction.NORTH_EAST));
        assertEquals(straight * 3, MovementSystem.stepDurationMs(pc, Direction.SOUTH_WEST));
    }

    /** Ta sama sciezka (GameSession.update) obsluguje klienta offline i serwer: skos x3, rogi jak w WorldMap. */
    @Test
    void sessionStepsDiagonallyAndBlocksTheNextStepForThreeStraightDurations() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        GameSession s = new GameSession(TestData.ITEMS, TestData.varden(), pc);
        WorldMap w = s.world();
        Position spot = null;
        for (int y = 3; y < w.height() - 6 && spot == null; y++) {
            for (int x = 3; x < w.width() - 6 && spot == null; x++) {
                Position p = new Position(x, y, 0);
                boolean open = !w.isProtectionZone(p);
                for (int dy = 0; dy <= 3 && open; dy++) {
                    for (int dx = 0; dx <= 3 && open; dx++) {
                        Position q = p.translate(dx, dy);
                        open = w.isWalkable(q) && w.transitionAt(q) == null && !w.isProtectionZone(q) && w.requiredLevelAt(q) <= pc.level();
                    }
                }
                if (open) spot = p;
            }
        }
        assertNotNull(spot);
        s.teleport(spot, 0);
        long t0 = 10_000, straight = MovementSystem.stepDurationMs(pc, Direction.EAST);
        s.update(t0, Direction.SOUTH_EAST);
        assertEquals(spot.translate(1, 1), pc.position());
        s.update(t0 + straight * 3 - 1, Direction.SOUTH_EAST);
        assertEquals(spot.translate(1, 1), pc.position(), "still inside the 3x diagonal step");
        s.update(t0 + straight * 3, Direction.SOUTH_EAST);
        assertEquals(spot.translate(2, 2), pc.position(), "next diagonal step after 3x");
        long t1 = t0 + straight * 3;
        s.update(t1 + straight - 1, Direction.EAST);
        assertEquals(spot.translate(2, 2), pc.position(), "a straight step also waits for the diagonal to finish");
    }
}
