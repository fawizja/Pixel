package pl.fawizja.pixel.achievement;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.vocation.Vocation;

class NewAchievementsTest {
    @Test
    void weeklyBossAndPlayTimeAchievements() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        assertFalse(Achievement.WEEKLY_BOSS.reached(pc));
        pc.setWeeklyBossWeek(2900);
        assertTrue(Achievement.WEEKLY_BOSS.reached(pc));

        pc.stats().addPlayTime(9 * 3_600_000L + 3_599_000L);
        assertEquals(9, Achievement.PLAYTIME_10.progress(pc));
        assertFalse(Achievement.PLAYTIME_10.reached(pc));
        pc.stats().addPlayTime(1000);
        assertTrue(Achievement.PLAYTIME_10.reached(pc));
        assertEquals("Stały bywalec", Achievement.titleOf("PLAYTIME_10"));
    }

    @Test
    void tenHuntingTasksMakeAProfessionalHunter() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        pc.hunt().restore("-;0;9");
        assertEquals(9, Achievement.HUNT_10.progress(pc));
        assertFalse(Achievement.HUNT_10.reached(pc));
        pc.hunt().complete();
        assertTrue(Achievement.HUNT_10.reached(pc));
    }
}
