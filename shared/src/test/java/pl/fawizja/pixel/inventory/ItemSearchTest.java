package pl.fawizja.pixel.inventory;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.vocation.Vocation;

class ItemSearchTest {
    @Test
    void findsItemsInBackpackEquipmentAndDepotIgnoringPolishLetters() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        int pelt = TestData.ITEMS.byKey("wolf_pelt").id();
        pc.backpack().add(new ItemStack(pelt, 3));
        pc.depot().add(new ItemStack(pelt, 9));
        String name = TestData.ITEMS.get(pelt).name();

        List<String[]> rows = ItemSearch.rows(pc, TestData.ITEMS, "FUTR");
        assertEquals(1, rows.size());
        assertEquals(name, rows.get(0)[0]);
        assertEquals("plecak 3, depozyt 9", rows.get(0)[1]);

        assertEquals(1, ItemSearch.rows(pc, TestData.ITEMS, "wolf_pe").size(), "the English key works too");
        assertTrue(ItemSearch.rows(pc, TestData.ITEMS, "x").isEmpty(), "too short");
        assertTrue(ItemSearch.rows(pc, TestData.ITEMS, "smocze jajo").isEmpty());

        // cos zalozonego z zestawu startowego
        boolean worn = false;
        for (pl.fawizja.pixel.item.EquipSlot s : pl.fawizja.pixel.item.EquipSlot.values()) {
            ItemStack st = pc.equipment().get(s);
            if (st == null) continue;
            String q = TestData.ITEMS.get(st.itemId()).key();
            worn |= ItemSearch.rows(pc, TestData.ITEMS, q).stream().anyMatch(r -> r[1].contains("założone"));
        }
        assertTrue(worn);
        assertEquals("zolw", ItemSearch.fold(" Żółw "));
    }
}
