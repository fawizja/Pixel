package pl.fawizja.pixel.stats;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.economy.Money;
import pl.fawizja.pixel.vocation.Vocation;

class HuntAnalyzerTest {

    @Test
    void measuresGainsPerHourSinceTheStartOfTheHunt() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        HuntAnalyzer hunt = new HuntAnalyzer(TestData.ITEMS);
        hunt.reset(1_000, pc);
        pc.addExperience(500);
        for (int i = 0; i < 6; i++) pc.bestiary().record("wolf", false);
        Money.add(pc.backpack(), 250);
        pc.backpack().add(new pl.fawizja.pixel.item.ItemStack(TestData.ITEMS.byKey("rope").id(), 2));
        HuntAnalyzer.Report r = hunt.report(1_000 + 30 * 60_000, pc); // pol godziny
        assertEquals(500, r.experience());
        assertEquals(1000, r.expPerHour());
        assertEquals(6, r.kills());
        assertEquals(12, r.killsPerHour());
        assertEquals(250, r.gold());
        assertEquals(500, r.goldPerHour());
        assertEquals(2 * TestData.ITEMS.byKey("rope").value(), r.loot(), "coins are not counted as items");
        assertEquals(4 * TestData.ITEMS.byKey("rope").value(), r.lootPerHour());
        assertEquals(Balance.experienceForLevel(pc.level() + 1) - pc.experience(), r.expToLevel());
        assertEquals((long) Math.ceil(r.expToLevel() * 3_600_000.0 / 1000), r.etaMs());
    }

    @Test
    void noRateDuringTheFirstMinuteAndResetStartsOver() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        HuntAnalyzer hunt = new HuntAnalyzer(TestData.ITEMS);
        assertFalse(hunt.started());
        HuntAnalyzer.Report first = hunt.report(0, pc);
        assertTrue(hunt.started(), "the first report starts the hunt");
        assertEquals(0, first.experience());
        pc.addExperience(100);
        HuntAnalyzer.Report early = hunt.report(30_000, pc);
        assertEquals(100, early.experience());
        assertEquals(0, early.expPerHour());
        assertEquals(-1, early.etaMs());
        hunt.reset(40_000, pc);
        assertEquals(0, hunt.report(50_000, pc).experience());
    }

    @Test
    void rowsAreReadable() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        HuntAnalyzer hunt = new HuntAnalyzer(TestData.ITEMS);
        hunt.reset(0, pc);
        Money.pay(pc.backpack(), 100);
        List<String[]> rows = HuntAnalyzer.rows(hunt.report(2 * 3_600_000L + 5 * 60_000, pc));
        assertEquals("2 h 5 min", rows.get(0)[1]);
        assertTrue(rows.get(3)[1].startsWith("-100 gp"), rows.get(3)[1]);
        assertEquals("Wartość przedmiotów", rows.get(4)[0]);
        assertEquals("brak danych (poluj dalej)", rows.get(6)[1]);
        assertEquals("45 s", HuntAnalyzer.duration(45_000));
        assertEquals("12 min", HuntAnalyzer.duration(12 * 60_000));
    }
}
