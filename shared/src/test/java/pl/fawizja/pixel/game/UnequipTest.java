package pl.fawizja.pixel.game;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.inventory.InventoryResult;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.vocation.Vocation;

class UnequipTest {
    @Test
    void unequipMovesTheItemToTheBackpackAndBack() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        GameSession s = new GameSession(TestData.ITEMS, TestData.varden(), pc, null, new java.util.Random(1), 8);
        EquipSlot slot = null;
        for (EquipSlot e : EquipSlot.values()) if (e != EquipSlot.NONE && pc.equipment().get(e) != null) { slot = e; break; }
        assertNotNull(slot, "starting kit wears something");
        int id = pc.equipment().get(slot).itemId();
        int free = pc.backpack().freeSlots();
        assertTrue(Actions.apply(s, "unequip:" + slot.name(), 0));
        assertNull(pc.equipment().get(slot));
        assertEquals(free - 1, pc.backpack().freeSlots());
        int back = -1;
        for (int i = 0; i < 20; i++) {
            ItemStack st = pc.backpack().get(i);
            if (st != null && st.itemId() == id) back = i;
        }
        s.equipFromSlot(back, 0);
        assertEquals(id, pc.equipment().get(slot).itemId());
        assertEquals(InventoryResult.EMPTY_SLOT, s.unequip(EquipSlot.NONE, 0));
        assertFalse(Actions.apply(s, "unequip:XYZ", 0), "bad slot name is ignored");
    }
}
