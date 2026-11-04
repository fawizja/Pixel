package pl.fawizja.pixel.character;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.vocation.Vocation;

/** Krzywe umiejetnosci i magii jak w Tibii (TibiaWiki "Formulae") oraz etapy serwera (Balance.*_STAGES). */
class TibiaSkillFormulaTest {

    @Test
    void firstLevelCostsTheSkillConstantForEveryVocation() {
        for (Vocation v : Vocation.values()) {
            assertEquals(50, Skills.triesForNext(v, Skill.SWORD, 10), v.name());
            assertEquals(50, Skills.triesForNext(v, Skill.FIST, 10), v.name());
            assertEquals(25, Skills.triesForNext(v, Skill.DISTANCE, 10), v.name());
            assertEquals(100, Skills.triesForNext(v, Skill.SHIELDING, 10), v.name());
            assertEquals(20, Skills.triesForNext(v, Skill.FISHING, 10), v.name());
            assertEquals(1600, Skills.triesForNext(v, Skill.MAGIC, 0), v.name());
        }
    }

    @Test
    void knownTibiaValuesPerVocation() {
        // rycerz: miecz 1.1 -> 50 * 1.1^40 = 2262,96; 50 * 1.1^70 = 39 487,0
        assertEquals(2262, Skills.triesForNext(Vocation.KNIGHT, Skill.SWORD, 50));
        assertEquals(39487, Skills.triesForNext(Vocation.KNIGHT, Skill.SWORD, 80));
        assertEquals(55, Skills.triesForNext(Vocation.KNIGHT, Skill.AXE, 11));
        assertEquals(35, Skills.triesForNext(Vocation.KNIGHT, Skill.DISTANCE, 11));   // 25 * 1.4
        assertEquals(110, Skills.triesForNext(Vocation.KNIGHT, Skill.SHIELDING, 11)); // 100 * 1.1
        assertEquals(4800, Skills.triesForNext(Vocation.KNIGHT, Skill.MAGIC, 1));     // 1600 * 3
        // paladyn: dystans 1.1, bron wrecz 1.2, magia 1.4
        assertEquals(1131, Skills.triesForNext(Vocation.PALADIN, Skill.DISTANCE, 50));
        assertEquals(60, Skills.triesForNext(Vocation.PALADIN, Skill.CLUB, 11));
        assertEquals(2240, Skills.triesForNext(Vocation.PALADIN, Skill.MAGIC, 1));
        // magowie: magia 1.1, bron 2.0 (sorc) / 1.8 (druid), piesci i tarcza 1.5
        assertEquals(1760, Skills.triesForNext(Vocation.SORCERER, Skill.MAGIC, 1));
        assertEquals(4149, Skills.triesForNext(Vocation.DRUID, Skill.MAGIC, 10));     // 1600 * 1.1^10 = 4149,99
        assertEquals(100, Skills.triesForNext(Vocation.SORCERER, Skill.SWORD, 11));
        assertEquals(90, Skills.triesForNext(Vocation.DRUID, Skill.SWORD, 11));
        assertEquals(75, Skills.triesForNext(Vocation.DRUID, Skill.FIST, 11));
        assertEquals(150, Skills.triesForNext(Vocation.SORCERER, Skill.SHIELDING, 11));
        // mnich: piesci 1.1, bron 1.5, dystans 2.0, tarcza 1.2, magia 1.3
        assertEquals(55, Skills.triesForNext(Vocation.MONK, Skill.FIST, 11));
        assertEquals(75, Skills.triesForNext(Vocation.MONK, Skill.SWORD, 11));
        assertEquals(120, Skills.triesForNext(Vocation.MONK, Skill.SHIELDING, 11));
        assertEquals(2080, Skills.triesForNext(Vocation.MONK, Skill.MAGIC, 1));
        // wedkarstwo takie samo dla wszystkich: 20 * 1.1^n
        assertEquals(Skills.triesForNext(Vocation.KNIGHT, Skill.FISHING, 40), Skills.triesForNext(Vocation.SORCERER, Skill.FISHING, 40));
    }

    @Test
    void steepCurvesSaturateWithoutOverflow() {
        long t = Skills.triesForNext(Vocation.SORCERER, Skill.SWORD, 199);
        assertTrue(t > 0 && t <= Skills.MAX_TRIES);
        Skills s = new Skills(Vocation.SORCERER);
        s.addTries(Skill.SWORD, Long.MAX_VALUE);
        assertTrue(s.level(Skill.SWORD) > 10 && s.tries(Skill.SWORD) >= 0);
    }

    @Test
    void sameTrainingGivesKnightMoreSwordThanSorcerer() {
        PlayerCharacter knight = TestData.newCharacter(Vocation.KNIGHT);
        PlayerCharacter sorc = TestData.newCharacter(Vocation.SORCERER);
        for (int i = 0; i < 2000; i++) {
            knight.skills().train(Skill.SWORD, 1);
            sorc.skills().train(Skill.SWORD, 1);
        }
        assertTrue(knight.skills().level(Skill.SWORD) > sorc.skills().level(Skill.SWORD) + 5,
                knight.skills().level(Skill.SWORD) + " vs " + sorc.skills().level(Skill.SWORD));
    }

    @Test
    void stageTablesMatchTheServerSettings() {
        assertEquals(50, Balance.expRate(1));
        assertEquals(50, Balance.expRate(8));
        assertEquals(80, Balance.expRate(9));
        assertEquals(80, Balance.expRate(50));
        assertEquals(60, Balance.expRate(51));
        assertEquals(15, Balance.expRate(201));
        assertEquals(3, Balance.expRate(1000));
        assertEquals(1.5, Balance.expRate(1201));
        assertEquals(1.2, Balance.expRate(5000));
        assertEquals(10, Balance.skillRate(10));
        assertEquals(10, Balance.skillRate(80));
        assertEquals(7, Balance.skillRate(81));
        assertEquals(4, Balance.skillRate(120));
        assertEquals(2, Balance.skillRate(121));
        assertEquals(10, Balance.magicRate(0));
        assertEquals(7, Balance.magicRate(100));
        assertEquals(3, Balance.magicRate(130));
        assertEquals(2, Balance.magicRate(131));
        assertEquals("x80", Balance.rateLabel(80));
        assertEquals("x1.5", Balance.rateLabel(1.5));
        assertEquals(15, Balance.applyRate(10, 1.5));
        assertEquals(0, Balance.applyRate(0, 80));
    }

    @Test
    void trainMultipliesTriesByTheCurrentStage() {
        Skills s = new Skills(Vocation.KNIGHT);
        s.train(Skill.SWORD, 1);
        assertEquals(10, s.tries(Skill.SWORD));            // poziom 10: x10
        s.set(Skill.SWORD, 85, 0);
        s.train(Skill.SWORD, 1);
        assertEquals(7, s.tries(Skill.SWORD));             // poziom 81-100: x7
        s.set(Skill.MAGIC, 125, 0);
        s.train(Skill.MAGIC, 100);
        assertEquals(300, s.tries(Skill.MAGIC));           // magia 121-130: x3
        s.addTries(Skill.FIST, 3);
        assertEquals(3, s.tries(Skill.FIST));              // surowe proby bez mnoznika
    }

    @Test
    void oldSaveKeepsLevelsAndClampsProgress() throws Exception {
        // stara krzywa (50 * 1.1^n dla wszystkiego): wedkarstwo 20 z 100 probami - nowa krzywa wymaga 20 * 1.1^10 = 51
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        pc.skills().set(Skill.FISHING, 20, 100);
        pc.skills().set(Skill.SWORD, 25, 400);
        pc.skills().set(Skill.MAGIC, 40, 90);
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertEquals(20, back.skills().level(Skill.FISHING));
        assertEquals(100, back.skills().tries(Skill.FISHING));
        assertEquals(99, back.skills().percentToNext(Skill.FISHING));
        assertTrue(back.skills().progress(Skill.FISHING) < 1.0);
        assertEquals(25, back.skills().level(Skill.SWORD));
        assertEquals(40, back.skills().level(Skill.MAGIC));
        assertEquals(Vocation.SORCERER, back.skills().vocation());
        // nadmiar prob awansuje przy nastepnej probie, poziom nigdy nie spada
        back.skills().train(Skill.FISHING, 1);
        assertTrue(back.skills().level(Skill.FISHING) > 20);
    }
}
