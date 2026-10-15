package pl.fawizja.pixel.achievement;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.vocation.Vocation;

class AchievementTest {

    @Test
    void theBestiaryCountsKillsSpeciesAndBosses() {
        Bestiary b = new Bestiary();
        for (int i = 0; i < 5; i++) b.record("wolf", false);
        b.record("goblin", false);
        b.record("grimtooth", true);
        b.record("grimtooth", true);
        assertEquals(8, b.total());
        assertEquals(3, b.species());
        assertEquals(1, b.bossesDefeated());
        assertEquals(List.of("wolf", "grimtooth", "goblin"), b.mostKilled());
        assertTrue(b.isBoss("grimtooth"));
        assertEquals("2/B", b.encodeValue("grimtooth"));
    }

    @Test
    void achievementsAreEarnedOnceAndStayEarned() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        assertTrue(pc.achievements().check(pc).isEmpty(), "a new level 8 character has nothing yet");
        pc.addExperience(Balance.experienceForLevel(20) - pc.experience());
        for (int i = 0; i < 100; i++) pc.bestiary().record("wolf", false);
        pc.bestiary().record("grimtooth", true);
        List<Achievement> got = pc.achievements().check(pc);
        assertEquals(List.of(Achievement.LEVEL_20, Achievement.KILLS_100, Achievement.BOSS_1), got);
        assertTrue(pc.achievements().check(pc).isEmpty(), "announced once");
        assertEquals(101, Achievement.KILLS_1000.progress(pc));
        assertFalse(Achievement.LEVEL_50.reached(pc));
    }

    @Test
    void bestiaryAndAchievementsSurviveSaving() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        for (int i = 0; i < 3; i++) pc.bestiary().record("wolf", false);
        pc.bestiary().record("grimtooth", true);
        pc.achievements().check(pc);
        assertTrue(pc.achievements().has(Achievement.BOSS_1));
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertEquals(3, back.bestiary().kills("wolf"));
        assertTrue(back.bestiary().isBoss("grimtooth"));
        assertTrue(back.achievements().has(Achievement.BOSS_1));
        assertEquals(pc.achievements().count(), back.achievements().count());
        Achievements a = new Achievements();
        a.restore("BOSS_1,NOWE_Z_PRZYSZLOSCI");
        assertEquals(1, a.count(), "unknown keys from a newer version are skipped");
    }
}
