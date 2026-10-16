package pl.fawizja.pixel.npc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.item.ItemType;

class PriceCheckTest {
    @BeforeAll
    static void load() throws Exception {
        TownServicesTest.load();
    }

    @Test
    void findsItemsByPolishNameWithoutDiacriticsAndListsBestBuyersFirst() {
        ItemType potion = PriceCheck.find(TestData.ITEMS, "mikstura zdrowia");
        assertEquals("health_potion", potion.key());
        assertEquals("great_mana_potion", PriceCheck.find(TestData.ITEMS, "silna mikstura many").key());
        assertNull(PriceCheck.find(TestData.ITEMS, "x"));
        assertNull(PriceCheck.find(TestData.ITEMS, "nie ma takiego"));

        List<String[]> rows = PriceCheck.rows(potion, TownServicesTest.npcs, TownServicesTest.shops);
        assertEquals("Wartość", rows.get(0)[0]);
        assertTrue(rows.size() > 2, "several merchants sell potions");
        assertTrue(rows.stream().anyMatch(r -> r[1].contains("sprzedaje za 50 gp")), "Varden sells it for 50");
        assertTrue(rows.size() <= PriceCheck.MAX_ROWS);
    }
}
