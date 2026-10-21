package pl.fawizja.pixel.npc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.Actions;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.vocation.Vocation;

class SellLootTest {
    @BeforeAll
    static void load() throws Exception {
        TownServicesTest.load();
    }

    @Test
    void oneTapSellsAllLootButKeepsQuestItemsAndEquipment() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        GameSession s = new TownServicesTest().session(pc, List.of());
        int pelt = TestData.ITEMS.byKey("wolf_pelt").id();
        int ear = TestData.ITEMS.byKey("goblin_ear").id();
        int sword = TestData.ITEMS.byKey("iron_sword").id();
        pc.backpack().add(new ItemStack(pelt, 7));
        pc.backpack().add(new ItemStack(ear, 4));
        pc.backpack().add(new ItemStack(sword, 1));
        pc.quests().restore("varden_ears", false, 0); // aktywne zadanie zbierania uszu goblinow

        assertEquals(0, s.sellLoot(1000), "only at a merchant");
        s.teleport(TownServicesTest.talkSpot(TownServicesTest.npcs.get("varden_merchant")), 1100);
        long gold = s.gold();
        assertTrue(Actions.apply(s, "npc:sellloot", 1200));
        assertEquals(0, pc.backpack().count(pelt));
        assertEquals(4, pc.backpack().count(ear), "quest items stay");
        assertEquals(1, pc.backpack().count(sword), "equipment is not loot");
        assertEquals(gold + 7 * 12, s.gold());
        assertEquals(0, s.sellLoot(1300), "nothing left to sell");
    }

    @Test
    void keptItemsAreNeitherSoldInBulkNorDropped() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        GameSession s = new TownServicesTest().session(pc, List.of());
        int pelt = TestData.ITEMS.byKey("wolf_pelt").id();
        pc.backpack().clear();
        pc.backpack().putInSlot(0, new ItemStack(pelt, 5));
        assertTrue(Actions.apply(s, "slot_keep:0", 900));
        assertTrue(pc.isKept(pelt));

        Actions.apply(s, "slot_drop:0", 950);
        assertEquals(5, pc.backpack().count(pelt), "a kept item is not dropped");

        s.teleport(TownServicesTest.talkSpot(TownServicesTest.npcs.get("varden_merchant")), 1000);
        assertEquals(0, s.sellLoot(1100));
        assertEquals(5, pc.backpack().count(pelt));

        Actions.apply(s, "slot_keep:0", 1200);
        assertFalse(pc.isKept(pelt));
        assertEquals(5, s.sellLoot(1300));
    }
}
