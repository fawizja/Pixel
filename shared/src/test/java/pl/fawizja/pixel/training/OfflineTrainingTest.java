package pl.fawizja.pixel.training;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.character.SkillSheet;
import pl.fawizja.pixel.training.OfflineTraining.Choice;
import pl.fawizja.pixel.vocation.Vocation;

/** Trening offline przy posagu (Canary: offline_training / skill_trainer): wzory, limit 12 godz., pasek, zapis. */
class OfflineTrainingTest {

    static final long H = 3_600_000L, MIN = 60_000L;

    @Test
    void meleeAndDistanceTriesFollowCanaryFormula() {
        for (Vocation v : Vocation.values()) {
            double modifier = v.attackIntervalMs() / 1000.0;
            long hour = 3600;
            assertEquals((long) ((hour / modifier) / 2), OfflineTraining.baseTries(v, 100, Choice.SWORD, H), v + " sword");
            assertEquals((long) ((hour / modifier) / 4), OfflineTraining.baseTries(v, 100, Choice.DISTANCE, H), v + " distance");
            assertEquals(OfflineTraining.baseTries(v, 100, Choice.AXE, H), OfflineTraining.baseTries(v, 100, Choice.CLUB, H));
            assertEquals(OfflineTraining.baseTries(v, 100, Choice.FIST, H), OfflineTraining.baseTries(v, 100, Choice.SWORD, H));
        }
    }

    @Test
    void shieldGetsAQuarterOfTheSecondsAndMagicUsesManaRegeneration() {
        assertEquals(900, OfflineTraining.shieldTries(H));
        assertEquals(0, OfflineTraining.shieldTries(3_000));
        long magic = OfflineTraining.baseTries(Vocation.SORCERER, 100, Choice.MAGIC, H);
        assertEquals((long) (3600 * OfflineTraining.manaPerSecond(Vocation.SORCERER, 100)), magic);
        assertTrue(magic > 0);
    }

    @Test
    void choiceMapsToTheTrainedSkill() {
        assertEquals(Skill.SWORD, Choice.SWORD.skill());
        assertEquals(Skill.DISTANCE, Choice.DISTANCE.skill());
        assertEquals(Skill.MAGIC, Choice.MAGIC.skill());
        assertEquals(Choice.FIST, Choice.of("FIST"));
        assertNull(Choice.of("-"));
        assertNull(Choice.of(null));
    }

    @Test
    void premiumIsRequiredLikeInCanary() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.setPremium(false);
        assertEquals("offline_training.need_premium", OfflineTraining.refusal(pc, 1000));
        pc.setPremium(true);
        assertNull(OfflineTraining.refusal(pc, 1000));
        pc.setLogoutBlockedUntil(5000);
        assertEquals("offline_training.in_combat", OfflineTraining.refusal(pc, 1000), "no logout while in combat");
        assertNull(OfflineTraining.refusal(pc, 6000));
    }

    @Test
    void barStartsFullRegeneratesOnlineAndNeverExceedsTwelveHours() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        OfflineTraining t = pc.offlineTraining();
        assertEquals(OfflineTraining.MAX_BAR_MS, t.barMs());
        assertEquals("12:00", t.display());
        t.setBarMs(H);
        assertEquals("01:00", t.display());
        t.regenerate(30 * MIN);
        assertEquals("01:30", t.display());
        assertEquals(1.5f / 12f, t.fraction(), 1e-6);
        t.regenerate(100 * H);
        assertEquals(OfflineTraining.MAX_BAR_MS, t.barMs(), "cap 12 h");
        t.regenerate(-5 * H);
        assertEquals(OfflineTraining.MAX_BAR_MS, t.barMs(), "negative time changes nothing");
    }

    @Test
    void skillsSheetShowsTheBarRightAfterStamina() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        pc.offlineTraining().setBarMs(5 * H + 30 * MIN);
        java.util.List<SkillSheet.Row> rows = SkillSheet.rows(pc);
        assertEquals(SkillSheet.HEAD_ROWS + Skill.values().length, rows.size());
        SkillSheet.Row r = rows.get(3);
        assertEquals(SkillSheet.Bar.OFFLINE_TRAINING, r.bar());
        assertEquals("05:30", r.value());
        assertEquals(5.5 / 12.0, r.progress(), 1e-6);
    }

    @Test
    void loginAfterEightHoursGrantsTriesAndSpendsTheBar() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        OfflineTraining t = pc.offlineTraining();
        t.setChoice(Choice.SWORD);
        t.restore("43200,SWORD,1000000");
        int swordBefore = pc.skills().level(Skill.SWORD);
        long triesBefore = pc.skills().tries(Skill.SWORD);
        OfflineTraining.Report r = t.onLogin(pc, 1_000_000L + 8 * H);
        assertEquals(8 * H, r.trainedMs());
        assertEquals(OfflineTraining.baseTries(Vocation.KNIGHT, pc.level(), Choice.SWORD, 8 * H), r.tries());
        assertTrue(pc.skills().level(Skill.SWORD) > swordBefore || pc.skills().tries(Skill.SWORD) > triesBefore, "the skill progressed");
        assertEquals(4 * H, t.barMs(), "12 h bar minus 8 h trained");
        assertNull(t.choice(), "the choice is consumed");
        assertFalse(r.messages().isEmpty());
    }

    @Test
    void trainingIsCappedByTheBarAndAnythingAboveRefillsIt() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        OfflineTraining t = pc.offlineTraining();
        t.restore("7200,DISTANCE,1000000");      // 2 h left in the bar
        OfflineTraining.Report r = t.onLogin(pc, 1_000_000L + 10 * H);
        assertEquals(2 * H, r.trainedMs(), "only what the bar holds");
        assertEquals(8 * H, t.barMs(), "the 8 h beyond the bar regenerate it");
        t = new OfflineTraining();
        t.restore("43200,SWORD,1000000");
        r = t.onLogin(pc, 1_000_000L + 30 * H);
        assertEquals(12 * H, r.trainedMs(), "never more than 12 h");
        assertEquals(12 * H, t.barMs(), "the 18 h beyond refill it back to the cap");
    }

    @Test
    void offlineLongerThanThreeWeeksIsClamped() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        OfflineTraining t = pc.offlineTraining();
        t.restore("0,-,1000000");
        OfflineTraining.Report r = t.onLogin(pc, 1_000_000L + 400L * 24 * H);
        assertEquals(OfflineTraining.MAX_OFFLINE_MS, r.offlineMs());
        assertEquals(OfflineTraining.MAX_BAR_MS, t.barMs());
    }

    @Test
    void shorterThanTenMinutesDoesNotTrainAndSaysSo() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        OfflineTraining t = pc.offlineTraining();
        t.restore("43200,AXE,1000000");
        int before = pc.skills().level(Skill.AXE);
        long triesBefore = pc.skills().tries(Skill.AXE);
        OfflineTraining.Report r = t.onLogin(pc, 1_000_000L + 9 * MIN);
        assertFalse(r.trained());
        assertEquals(0, r.tries());
        assertEquals(before, pc.skills().level(Skill.AXE));
        assertEquals(triesBefore, pc.skills().tries(Skill.AXE));
        assertEquals(1, r.messages().size());
        assertNull(t.choice());
    }

    @Test
    void withoutAChoiceOfflineTimeJustRefillsTheBar() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        OfflineTraining t = pc.offlineTraining();
        t.restore("3600,-,1000000");
        OfflineTraining.Report r = t.onLogin(pc, 1_000_000L + 2 * H);
        assertFalse(r.trained());
        assertEquals(3 * H, t.barMs());
        assertTrue(r.messages().isEmpty());
    }

    @Test
    void magicTrainingAddsManaSpentAndShieldFollowsWhenProgressChanged() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        OfflineTraining t = pc.offlineTraining();
        t.restore("43200,MAGIC,1000000");
        int shield = pc.skills().level(Skill.SHIELDING);
        long shieldTries = pc.skills().tries(Skill.SHIELDING);
        OfflineTraining.Report r = t.onLogin(pc, 1_000_000L + 12 * H);
        assertTrue(r.tries() > 0);
        assertTrue(r.shieldTries() > 0, "magic progressed, so the shield trains too");
        assertEquals(OfflineTraining.shieldTries(12 * H), r.shieldTries());
        assertTrue(pc.skills().level(Skill.SHIELDING) > shield || pc.skills().tries(Skill.SHIELDING) > shieldTries);
    }

    @Test
    void stateSurvivesSaveLoadAndTheWallClockStampIsKept() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        pc.offlineTraining().restore("7380,FIST,123456789");
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertEquals(7380_000L, back.offlineTraining().barMs());
        assertEquals(Choice.FIST, back.offlineTraining().choice());
        assertEquals(123456789L, back.offlineTraining().savedAtWallMs());
        PlayerCharacter copy = TestData.newCharacter(Vocation.MONK);
        copy.copyStateFrom(back);
        assertEquals(7380_000L, copy.offlineTraining().barMs());
        assertEquals(Choice.FIST, copy.offlineTraining().choice());
    }

    @Test
    void anOldSaveWithoutTheFieldHasAFullBar() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        pc.offlineTraining().setBarMs(0);
        assertEquals(0, pc.offlineTraining().barMs());
        assertEquals(OfflineTraining.MAX_BAR_MS, TestData.newCharacter(Vocation.MONK).offlineTraining().barMs());
    }
}
