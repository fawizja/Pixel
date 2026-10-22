package pl.fawizja.pixel.movement;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterFactory;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.config.StartingConfig;
import pl.fawizja.pixel.vocation.Vocation;
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
}
