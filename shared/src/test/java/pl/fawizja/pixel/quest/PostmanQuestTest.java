package pl.fawizja.pixel.quest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.npc.Npc;
import pl.fawizja.pixel.npc.NpcRegistry;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.WorldLayout;
import pl.fawizja.pixel.world.WorldMap;

/** Zadanie Listonosza (tools/mapgen/postman.py): dziesiec misji, Poczmistrz Kevan, skrzynki pocztowe i ranga listonosza. */
class PostmanQuestTest {

    static WorldMap world;
    static NpcRegistry npcs;
    static ShopRegistry shops;
    static QuestRegistry quests;
    static QuestLines lines;
    static MonsterRegistry monsters;

    @BeforeAll
    static void load() throws Exception {
        world = TestData.world();
        shops = ShopRegistry.loadDefault(TestData.ITEMS);
        npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, world, shops);
        monsters = MonsterRegistry.loadDefault(TestData.ITEMS);
        quests = QuestRegistry.loadDefault(TestData.ITEMS, monsters);
        lines = QuestLines.loadDefault(quests);
    }

    private static PlayerCharacter hero(int level) {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.addExperience(Balance.experienceForLevel(level) - pc.experience());
        return pc;
    }

    @Test
    void theLineHasTenFreeMissionsFromPostmasterKevanInVarden() {
        QuestLines.Line l = lines.get("postman_m1");
        assertNotNull(l);
        assertFalse(l.premium());
        assertEquals(10, l.missions().size());
        Npc kevan = npcs.get(PostmanQuest.GIVER);
        assertNotNull(kevan);
        assertEquals("Postmaster Kevan", kevan.name());
        assertEquals("Varden", kevan.town());
        long visits = l.missions().stream().filter(q -> q.kind() == QuestKind.VISIT).count();
        assertTrue(visits >= 6 && l.missions().stream().anyMatch(q -> q.kind() == QuestKind.COLLECT)
                && l.missions().stream().anyMatch(q -> q.kind() == QuestKind.KILL));
        assertEquals("postman_m10", l.missions().get(9).key());
    }

    @Test
    void mailboxesStandInSixTownsAndAtLeastSixHuntingPlaces() {
        List<WorldMap.Mailbox> all = List.copyOf(world.mailboxes());
        assertEquals(6, all.stream().filter(m -> !m.hunting()).count());
        assertTrue(all.stream().filter(WorldMap.Mailbox::hunting).count() >= 6);
        Set<String> names = new HashSet<>();
        for (WorldMap.Mailbox m : all) {
            assertTrue(names.add(m.name()), "unique name " + m.name());
            assertEquals("mailbox", world.tiles().get(world.floor(m.position().z()).get(pl.fawizja.pixel.world.Layer.OBJECT, m.position().x(), m.position().y())).key());
            assertSame(m, world.mailboxNear(m.position()));
        }
        for (String town : List.of("Varden", "Frostholm", "Qasirah", "Selvaran", "Valmir", "Nerys")) {
            WorldMap.Mailbox m = world.mailboxByKey("mb_" + town.toLowerCase());
            assertNotNull(m, town);
            assertTrue(m.position().distanceTo(world.town(town).temple()) < 80, town);
        }
    }

    @Test
    void everyVisitTargetIsAMailboxAndTheChainRunsToThePostmanRank() {
        QuestSystem qs = new QuestSystem(quests, TestData.ITEMS);
        PlayerCharacter pc = hero(40);
        assertFalse(PostmanQuest.isPostman(pc));
        for (QuestType q : lines.get("postman_m1").missions()) {
            assertEquals(QuestResult.ACCEPTED, qs.interact(pc, q), q.key());
            switch (q.kind()) {
                case VISIT -> {
                    assertTrue(world.mailboxes().stream().anyMatch(m -> m.name().equals(q.target())), q.key() + " target is a mailbox");
                    assertNull(qs.onEnter(pc, "Nigdzie"));
                    assertEquals(q, qs.onMailbox(pc, q.target()));
                }
                case KILL -> {
                    for (int i = 0; i < q.amount(); i++) qs.onKill(pc, q.target());
                }
                case COLLECT -> {
                    pc.backpack().add(new pl.fawizja.pixel.item.ItemStack(TestData.ITEMS.byKey(q.target()).id(), q.amount()));
                }
            }
            assertEquals(QuestResult.COMPLETED, qs.interact(pc, q), q.key());
        }
        assertEquals(QuestLog.State.DONE, pc.quests().state("postman_m10"));
    }

    @Test
    void lootDropsTheLostBagFromBanditsAndLettersFromSnowWolves() {
        assertTrue(monsters.get("bandit").loot().stream().anyMatch(e -> e.itemId() == TestData.ITEMS.byKey("lost_mailbag").id()));
        assertTrue(monsters.get("snow_wolf").loot().stream().anyMatch(e -> e.itemId() == TestData.ITEMS.byKey("sodden_letters").id()));
    }
}
