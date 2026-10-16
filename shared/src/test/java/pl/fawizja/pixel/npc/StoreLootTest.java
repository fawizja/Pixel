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

class StoreLootTest {
    @BeforeAll
    static void load() throws Exception {
        TownServicesTest.load();
    }

    @Test
    void oneTapStoresAllLootButKeepsQuestItems() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        GameSession s = new TownServicesTest().session(pc, List.of());
        int pelt = TestData.ITEMS.byKey("wolf_pelt").id();
        int ear = TestData.ITEMS.byKey("goblin_ear").id();
        int hp = TestData.ITEMS.byKey("health_potion").id();
        pc.backpack().add(new ItemStack(pelt, 7));
        pc.backpack().add(new ItemStack(ear, 4));
        int potions = pc.backpack().count(hp);
        pc.quests().restore("varden_ears", false, 0);

        assertEquals(0, s.depotStoreLoot(1000), "only at a banker");
        s.teleport(TownServicesTest.talkSpot(TownServicesTest.npcs.get("varden_banker")), 1100);
        assertTrue(Actions.apply(s, "npc:storeloot", 1200));
        assertEquals(0, pc.backpack().count(pelt));
        assertEquals(7, pc.depot().count(pelt));
        assertEquals(4, pc.backpack().count(ear), "quest items stay");
        assertEquals(potions, pc.backpack().count(hp), "supplies stay");
        assertEquals(0, s.depotStoreLoot(1300), "nothing left");
    }
}
