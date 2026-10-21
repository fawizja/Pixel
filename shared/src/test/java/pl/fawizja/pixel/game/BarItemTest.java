package pl.fawizja.pixel.game;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.vocation.Vocation;

class BarItemTest {
    @Test
    void barSlotUsesAPotionOrRuneFromTheBackpack() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        GameSession s = new GameSession(TestData.ITEMS, TestData.varden(), pc, null, new java.util.Random(1), 8);
        int id = TestData.ITEMS.byKey("health_potion").id();
        pc.backpack().add(new ItemStack(id, 3));
        int before = pc.backpack().count(id);
        pc.damage(pc.maxHealth() / 2);
        assertTrue(Actions.apply(s, "item:health_potion", 0));
        assertEquals(before - 1, pc.backpack().count(id));
        assertFalse(Actions.apply(s, "item:gold_coin", 0), "only potions and runes go on the bar");
        assertFalse(Actions.apply(s, "item:no_such_item", 0));
    }
}
