package pl.fawizja.pixel.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.vocation.Vocation;

class TipsTest {
    static final Tips.Situation CALM = new Tips.Situation(false, 0, 100, 20);

    @Test
    void eachTipIsShownOnceOneAtATimeWhenItFits() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        assertTrue(Tips.due(pc, CALM).get(0).contains("Joystick"), "welcome first");
        assertTrue(Tips.due(pc, CALM).get(0).contains("przewodnik"), "level 8: quests next");
        assertEquals(List.of(), Tips.due(pc, CALM), "nothing else fits");
        Tips.Situation fight = new Tips.Situation(true, 1, 30, 1);
        assertTrue(Tips.due(pc, fight).get(0).contains("CEL"));
        assertTrue(Tips.due(pc, fight).get(0).contains("ciało"));
        assertTrue(Tips.due(pc, fight).get(0).contains("HP"));
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertTrue(Tips.due(back, fight).get(0).contains("prawie pełny"), "shown tips are saved, the next one comes");
    }

    @Test
    void tipsCanBeTurnedOff() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        GameSession s = new GameSession(TestData.ITEMS, TestData.varden(), pc, null, new java.util.Random(1), 8);
        assertTrue(Actions.apply(s, "tips_off", 0));
        assertEquals(List.of(), Tips.due(pc, new Tips.Situation(true, 5, 10, 0)));
    }

    @Test
    void experiencedCharactersGetNoTips() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        pc.addExperience(50_000_000);
        assertTrue(pc.level() >= Tips.MAX_LEVEL);
        assertEquals(List.of(), Tips.due(pc, CALM));
    }
}
