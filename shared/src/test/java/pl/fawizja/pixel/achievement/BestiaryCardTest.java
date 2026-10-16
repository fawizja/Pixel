package pl.fawizja.pixel.achievement;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.MonsterType;

class BestiaryCardTest {
    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);

    private static String find(List<String[]> rows, String label) {
        for (String[] r : rows) if (r[0].equals(label)) return r[1];
        return null;
    }

    @Test
    void detailsUnlockWithKills() {
        MonsterType wolf = REG.get("wolf");
        List<String[]> none = BestiaryCard.rows(wolf, 0, TestData.ITEMS);
        assertEquals(2, none.size(), "an unknown monster shows nothing more");

        List<String[]> few = BestiaryCard.rows(wolf, 3, TestData.ITEMS);
        assertEquals("poziom 8, 55 HP", find(few, "Poziom i zdrowie"));
        assertEquals("20 exp", find(few, "Doświadczenie"));
        assertEquals("pokonaj jeszcze 7, aby je poznać", find(few, "Odporności"));
        assertEquals("pokonaj jeszcze 22, aby je poznać", find(few, "Łupy"));

        List<String[]> many = BestiaryCard.rows(wolf, 25, TestData.ITEMS);
        assertNull(find(many, "Łupy"), "the loot list replaces the hint");
        assertEquals("80% 1-8 szt.", find(many, TestData.ITEMS.byKey("gold_coin").name()));
        assertEquals("12%", find(many, TestData.ITEMS.byKey("wolf_pelt").name()));
    }

    @Test
    void percentsAndResistances() {
        assertEquals("0,5%", BestiaryCard.percent(5));
        assertEquals("100%", BestiaryCard.percent(1000));
        assertEquals("pancerz 2", BestiaryCard.resistances(REG.get("wolf")), "no resistances, only armor");
        String golem = BestiaryCard.resistances(REG.get("ice_golem"));
        assertFalse(golem.isEmpty());
        assertTrue(golem.contains("lód") || golem.contains("pancerz"), golem);
    }
}
