package pl.fawizja.pixel.achievement;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.vocation.Vocation;

class NewAchievementsTest {

    @Test
    void eliteKillsSkillAndChestsCountAndEliteKillsAreSaved() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        assertFalse(Achievement.ELITE_1.reached(pc));
        pc.bestiary().recordElite();
        assertTrue(Achievement.ELITE_1.reached(pc));
        assertEquals(1, Achievement.ELITE_25.progress(pc));
        pc.skills().set(Skill.FIST, 41, 0);
        assertTrue(Achievement.SKILL_40.reached(pc));
        for (int i = 0; i < 10; i++) pc.openedChests().add("c" + i);
        assertTrue(Achievement.CHESTS_10.reached(pc));
        assertTrue(pc.achievements().check(pc).contains(Achievement.CHESTS_10));
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertEquals(1, back.bestiary().eliteKills());
        assertTrue(back.achievements().has(Achievement.ELITE_1));
    }
}
