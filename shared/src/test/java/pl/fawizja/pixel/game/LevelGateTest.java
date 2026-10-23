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

/** Straznik wejscia do Gniazda Wiwern (od poziomu 250): slabsza postac zawraca z ostrzezeniem, silniejsza wchodzi. */
class LevelGateTest {

    /** Pole tuz przed brama od polnocy (brama: przerwa w murze doliny wiwern). */
    static final Position BEFORE_GATE = new Position(167, 185, 0);

    static GameSession walkSouth(int level) {
        WorldMap w = TestData.world();
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        if (level > pc.level()) pc.addExperience(Balance.experienceForLevel(level) - pc.experience());
        assertEquals(250, w.requiredLevelAt(BEFORE_GATE.translate(0, 1)));
        assertEquals(0, w.requiredLevelAt(BEFORE_GATE));
        pc.setPosition(BEFORE_GATE);
        GameSession s = new GameSession(TestData.ITEMS, w, pc);
        for (long t = 0; t < 10_000; t += 50) s.update(t, Direction.SOUTH);
        return s;
    }

    @Test
    void aWeakerCharacterIsTurnedBackAtTheGate() {
        GameSession s = walkSouth(240);
        assertEquals(BEFORE_GATE.y(), s.character().position().y());
        assertTrue(s.log().latest(20).stream().anyMatch(e -> e.text().contains("od poziomu 250")));
        assertFalse(s.predictStep(Direction.SOUTH, 20_000), "the client must not predict a step onto the gate");
    }

    @Test
    void aVeteranEntersTheValley() {
        GameSession s = walkSouth(300);
        assertTrue(s.character().position().y() > BEFORE_GATE.y() + 1, "at " + s.character().position());
    }
}
