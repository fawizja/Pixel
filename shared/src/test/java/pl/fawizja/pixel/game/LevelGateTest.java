package pl.fawizja.pixel.game;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/** Straznik mostu na wschodni kontynent: nowa postac zawraca z ostrzezeniem, wysoki poziom przechodzi. */
class LevelGateTest {

    static GameSession walkEast(int level) {
        WorldMap w = TestData.world();
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        if (level > pc.level()) pc.addExperience(Balance.experienceForLevel(level) - pc.experience());
        int need = w.requiredLevelAt(new Position(160, 86, 0));
        assertEquals(200, need);
        pc.setPosition(new Position(158, 86, 0));
        GameSession s = new GameSession(TestData.ITEMS, w, pc);
        for (long t = 0; t < 10_000; t += 50) s.update(t, Direction.EAST);
        return s;
    }

    @Test
    void aNewCharacterIsTurnedBackAtTheBridge() {
        GameSession s = walkEast(8);
        assertEquals(159, s.character().position().x());
        assertTrue(s.log().latest(20).stream().anyMatch(e -> e.text().contains("od poziomu 200")));
        assertFalse(s.predictStep(Direction.EAST, 20_000), "the client must not predict a step onto the bridge");
    }

    @Test
    void aVeteranCrossesToTheCamp() {
        GameSession s = walkEast(250);
        assertTrue(s.character().position().x() > 168, "at " + s.character().position());
    }
}
