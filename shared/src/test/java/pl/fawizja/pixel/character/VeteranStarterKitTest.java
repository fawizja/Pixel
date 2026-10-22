package pl.fawizja.pixel.character;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.config.StartingConfig;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

/** Etap 11: postac startujaca na poziomie 300 dostaje wyposazenie na swoj poziom, a nie skore z poziomu 8. */
class VeteranStarterKitTest {

    static final CharacterFactory FACTORY = new CharacterFactory(StartingConfig.defaults(), TestData.ITEMS);

    @Test
    void level300CharactersStartWithObsidianGearTheyCanUse() {
        for (Vocation v : Vocation.values()) {
            PlayerCharacter pc = FACTORY.create("Weteran", v, new Position(31, 16, 0), 300);
            ItemStack weapon = pc.equipment().get(EquipSlot.WEAPON);
            assertNotNull(weapon, v + " has a weapon");
            for (EquipSlot slot : EquipSlot.values()) {
                ItemStack s = pc.equipment().get(slot);
                if (s == null) continue;
                ItemType t = TestData.ITEMS.get(s.itemId());
                assertTrue(t.minLevel() <= 300 && t.usableBy(v), v + " can use " + t.key());
            }
            assertEquals(250, TestData.ITEMS.get(weapon.itemId()).minLevel(), v + " weapon tier");
            assertEquals("obsidian_armor", TestData.ITEMS.get(pc.equipment().get(EquipSlot.ARMOR).itemId()).key());
            for (ItemStack s : pc.backpack().contents()) {
                if (s == null) continue;
                ItemType t = TestData.ITEMS.get(s.itemId());
                if (t.minLevel() > 0) assertTrue(t.minLevel() <= 300 && t.usableBy(v), v + " can use " + t.key());
            }
        }
    }

    @Test
    void lowLevelCharactersKeepTheBasicKit() {
        for (Vocation v : Vocation.values()) {
            PlayerCharacter pc = FACTORY.create("Nowy", v, new Position(31, 16, 0), 8);
            assertEquals("leather_armor", TestData.ITEMS.get(pc.equipment().get(EquipSlot.ARMOR).itemId()).key(), v.name());
        }
    }
}
