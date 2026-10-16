package pl.fawizja.pixel.achievement;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.vocation.Vocation;

class TitleTest {
    @Test
    void onlyEarnedAchievementsBecomeTitles() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        GameSession s = new GameSession(TestData.ITEMS, TestData.world(), pc);
        assertFalse(s.chooseTitle("KILLS_100", 1000));
        assertFalse(s.chooseTitle("NO_SUCH", 1000));
        assertNull(pc.title());

        pc.achievements().restore("KILLS_100");
        assertTrue(s.chooseTitle("KILLS_100", 1100));
        assertEquals("KILLS_100", pc.title());
        assertEquals("Łowca", Achievement.titleOf(pc.title()));

        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        assertEquals("KILLS_100", codec.decode(codec.encode(pc)).title(), "the title is saved");

        assertTrue(s.chooseTitle("-", 1200));
        assertNull(pc.title());
        assertNull(Achievement.titleOf("FROM_A_NEWER_VERSION"));
    }
}
