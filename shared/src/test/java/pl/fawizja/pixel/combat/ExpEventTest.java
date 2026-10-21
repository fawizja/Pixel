package pl.fawizja.pixel.combat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.vocation.Vocation;

class ExpEventTest {
    @Test
    void experienceShowsAsFloatingNumberOverThePlayer() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        GameSession s = new GameSession(TestData.ITEMS, TestData.varden(), pc, null, new java.util.Random(1), 8);
        s.combat().awardExperience(123, "Test", 1000);
        CombatEvent e = s.combat().latestEvents(5).stream().filter(x -> x.kind() == CombatEvent.Kind.EXP).findFirst().orElseThrow();
        assertEquals(123, e.amount());
        assertEquals(pc.position(), e.position());
        s.combat().awardExperience(0, "Nic", 2000);
        assertEquals(1, s.combat().latestEvents(10).stream().filter(x -> x.kind() == CombatEvent.Kind.EXP).count(), "no event for 0 exp");
    }
}
