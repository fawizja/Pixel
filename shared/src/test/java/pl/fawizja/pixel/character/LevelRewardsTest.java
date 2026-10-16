package pl.fawizja.pixel.character;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.vocation.Vocation;

class LevelRewardsTest {
    @Test
    void milestonesPayOnceAndOnlyFromNowOn() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        GameSession s = new GameSession(TestData.ITEMS, TestData.world(), pc);
        int scroll = TestData.ITEMS.byKey("exp_scroll").id();
        s.update(500, null); // najpierw nagroda za wejscie do gry
        long bank = pc.bank().balance();
        pc.addExperience(Balance.experienceForLevel(31) - pc.experience());
        s.update(1000, null);
        long expected = LevelRewards.gold(10) + LevelRewards.gold(20) + LevelRewards.gold(30);
        assertEquals(bank + expected, pc.bank().balance(), "three milestones at once");
        assertEquals(LevelRewards.scrolls(20) + LevelRewards.scrolls(30), pc.backpack().count(scroll));
        assertEquals(31, pc.rewardedLevel());
        assertTrue(s.log().latest(10).stream().anyMatch(e -> e.text().startsWith("Nagroda za poziom 30: ")));

        // smierc i ponowny awans nie daja nagrody drugi raz
        pc.applyDeathPenalty(1);
        assertTrue(pc.level() < 31);
        pc.addExperience(Balance.experienceForLevel(31) - pc.experience());
        s.update(2000, null);
        assertEquals(bank + expected, pc.bank().balance());

        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        assertEquals(31, codec.decode(codec.encode(pc)).rewardedLevel());
        // stary zapis bez klucza "lvlrew": postac z konstruktora zaczyna od swojego poziomu (bez nagrod wstecz)
        PlayerCharacter veteran = new PlayerCharacter("v1", "Weteran", Vocation.DRUID, 120,
                pc.position(), TestData.ITEMS, 0, "varden");
        assertEquals(120, veteran.rewardedLevel());
    }

    @Test
    void rewardsGrowWithTheMilestone() {
        assertEquals(20, LevelRewards.next(19, 25));
        assertEquals(0, LevelRewards.next(20, 29));
        assertEquals(10, LevelRewards.next(8, 100), "the lowest pending milestone comes first");
        assertEquals(0, LevelRewards.scrolls(10));
        assertEquals(3, LevelRewards.scrolls(250));
        assertTrue(LevelRewards.gold(100) > LevelRewards.gold(50));
    }
}
