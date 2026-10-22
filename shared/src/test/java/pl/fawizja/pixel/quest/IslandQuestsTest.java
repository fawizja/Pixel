package pl.fawizja.pixel.quest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.monster.LootEntry;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.npc.Npc;
import pl.fawizja.pixel.npc.NpcRegistry;
import pl.fawizja.pixel.npc.Shop;
import pl.fawizja.pixel.npc.ShopOffer;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.world.WorldLayout;
import pl.fawizja.pixel.world.WorldMap;

/** Etap 15: zlecenia Admirala Wendy na wyspach, produkty potworow wysp i ich skup w Przystani Wschodniej. */
class IslandQuestsTest {

    static final MonsterRegistry MONSTERS = MonsterRegistry.loadDefault(TestData.ITEMS);
    static final List<String> PRODUCTS = List.of("wyvern_scale", "crab_pincer", "pirate_hook", "powder_horn",
            "drowned_ring", "mist_essence", "drake_horn");
    static WorldMap world;
    static ShopRegistry shops;
    static NpcRegistry npcs;
    static QuestRegistry quests;

    @BeforeAll
    static void load() throws Exception {
        world = TestData.world();
        shops = ShopRegistry.loadDefault(TestData.ITEMS);
        npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, world, shops);
        quests = QuestRegistry.loadDefault(TestData.ITEMS, MONSTERS);
    }

    @Test
    void theAdmiralGivesLevel300QuestsForEveryIslandBoss() {
        Npc admiral = npcs.get("isle_guide");
        assertNotNull(admiral);
        assertTrue(world.isProtectionZone(admiral.position()), "admiral stands on the protected pier");
        List<QuestType> list = quests.byGiver("isle_guide");
        assertTrue(list.size() >= 12);
        for (QuestType q : list) assertTrue(q.minLevel() >= 300, q.key());
        for (String boss : List.of("wyvern_matriarch", "bloodbeard", "skaldra", "morvena", "pyrrax")) {
            assertTrue(list.stream().anyMatch(q -> q.kind() == QuestKind.KILL && q.target().equals(boss)), "quest for " + boss);
        }
        long coral = list.stream().filter(q -> q.rewardItem() != null && q.rewardItem().startsWith("coral_")).count();
        assertTrue(coral >= 3, "chains end with coral gear");
        assertEquals("isle_gunners", quests.get("isle_bloodbeard").requires());
    }

    @Test
    void everyIslandProductDropsAndIsBoughtAtTheHarbour() {
        Shop shop = shops.get(npcs.get("isle_merchant").shopKey());
        for (String key : PRODUCTS) {
            int id = TestData.ITEMS.byKey(key).id();
            boolean drops = false;
            for (MonsterType m : MONSTERS.all()) {
                for (LootEntry e : m.loot()) if (e.itemId() == id && !m.boss()) drops = true;
            }
            assertTrue(drops, key + " drops from a normal monster");
            ShopOffer o = shop.offer(id);
            assertNotNull(o, key + " in the harbour shop");
            assertTrue(o.bought() && !o.sold(), key + " is bought, not sold");
            assertEquals(TestData.ITEMS.byKey(key).value(), o.sellPrice(), key + " bought at item value");
        }
    }
}
