package pl.fawizja.pixel.treasure;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.SpawnTable;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.WorldLayout;
import pl.fawizja.pixel.world.WorldMap;

/** Nagrody ze skrzyn rosna z trudnoscia miejsca; skrzynie zadan daja wybrany dobry przedmiot. */
class ChestRewardsTest {

    static final WorldMap WORLD = TestData.world();
    static final TreasureChests CHESTS = TreasureChests.generate(WORLD);
    static final MonsterManager MONSTERS;

    static {
        try {
            MonsterRegistry reg = MonsterRegistry.loadDefault(TestData.ITEMS);
            MONSTERS = new MonsterManager(WORLD, reg, SpawnTable.load(WorldLayout.WORLD_MAP, WORLD, reg), new Random(1));
        } catch (java.io.IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static long value(ChestRewards.Reward r) {
        long v = r.gold();
        for (ItemStack s : r.items()) v += TestData.ITEMS.get(s.itemId()).value() * s.count();
        return v;
    }

    @Test
    void goldFormulasRiseWithDifficulty() {
        long prevQ = 0, prevL = 0;
        for (int d = 5; d <= 900; d += 5) {
            assertTrue(ChestRewards.questGold(d) > prevQ, "quest gold at " + d);
            assertTrue(ChestRewards.lootGold(d) > prevL, "loot gold at " + d);
            assertTrue(ChestRewards.questGold(d) > ChestRewards.lootGold(d), "quest > loot at " + d);
            prevQ = ChestRewards.questGold(d);
            prevL = ChestRewards.lootGold(d);
        }
        assertEquals(6300, ChestRewards.questGold(50));
        assertEquals(640, ChestRewards.lootGold(50));
    }

    @Test
    void everyChestGivesSomethingWorthwhileForEveryVocation() {
        StringBuilder table = new StringBuilder();
        int quests = 0;
        for (TreasureChests.Chest c : CHESTS.all()) {
            for (Vocation v : Vocation.values()) {
                if (c.vocation() != null && c.vocation() != v) continue;
                ChestRewards.Reward r = ChestRewards.reward(c, WORLD, MONSTERS, TestData.ITEMS, v);
                assertTrue(r.gold() > 0, "gold in " + c.id());
                assertFalse(r.items().isEmpty(), "items in " + c.id());
                for (ItemStack s : r.items()) {
                    ItemType t = TestData.ITEMS.get(s.itemId());
                    assertTrue(s.count() > 0 && t.usableBy(v), c.id() + " gives " + t.key() + " to " + v);
                }
                assertTrue(r.gold() >= c.gold(), "map content is the minimum: " + c.id());
                if (r.kind() == ChestRewards.Kind.QUEST) {
                    assertTrue(r.items().stream().anyMatch(s -> ChestRewards.equipment(TestData.ITEMS.get(s.itemId()))),
                            "quest chest " + c.id() + " gives an item for " + v);
                    assertTrue(r.gold() >= ChestRewards.questGold(r.difficulty()));
                }
                if (v == Vocation.KNIGHT || c.vocation() == v) {
                    table.append(c.id()).append(' ').append(r.kind()).append(" d=").append(r.difficulty()).append(' ')
                            .append(ChestRewards.message(r, TestData.ITEMS)).append('\n');
                }
            }
            if (ChestRewards.kind(c, WORLD, MONSTERS, TestData.ITEMS) == ChestRewards.Kind.QUEST) quests++;
        }
        System.out.println(table);
        assertTrue(quests >= 6, "quest chests: " + quests);
    }

    @Test
    void harderChestsGiveMore() {
        // srednia wartosc nagrody w kazdym rodzaju rosnie z trudnoscia (porownanie dolnej i gornej polowy)
        for (ChestRewards.Kind k : ChestRewards.Kind.values()) {
            List<TreasureChests.Chest> list = CHESTS.all().stream()
                    .filter(c -> ChestRewards.kind(c, WORLD, MONSTERS, TestData.ITEMS) == k)
                    .sorted((a, b) -> Integer.compare(ChestRewards.difficulty(a, WORLD, MONSTERS), ChestRewards.difficulty(b, WORLD, MONSTERS)))
                    .toList();
            if (list.size() < 4) continue;
            double lo = 0, hi = 0;
            int half = list.size() / 2;
            for (int i = 0; i < list.size(); i++) {
                TreasureChests.Chest c = list.get(i);
                Vocation v = c.vocation() != null ? c.vocation() : Vocation.KNIGHT;
                long val = ChestRewards.reward(c, WORLD, MONSTERS, TestData.ITEMS, v).gold();
                if (i < half) lo += val; else hi += val;
            }
            assertTrue(hi / (list.size() - half) > lo / half, k + ": harder half gives more gold");
        }
        // ta sama skrzynia "przeniesiona" glebiej / za drzwi daje wiecej
        TreasureChests.Chest a = new TreasureChests.Chest("dtest", new pl.fawizja.pixel.world.Position(5, 5, -1), 0, null, 0, null, 30);
        TreasureChests.Chest b = new TreasureChests.Chest("dtest", new pl.fawizja.pixel.world.Position(5, 5, -1), 0, null, 0, null, 300);
        ChestRewards.Reward ra = ChestRewards.reward(a, null, null, TestData.ITEMS, Vocation.SORCERER);
        ChestRewards.Reward rb = ChestRewards.reward(b, null, null, TestData.ITEMS, Vocation.SORCERER);
        assertEquals(ChestRewards.Kind.QUEST, ra.kind(), "behind a level door = quest chest");
        assertTrue(value(rb) > value(ra) * 3, value(ra) + " vs " + value(rb));
        assertTrue(rb.gold() > ra.gold());
    }

    @Test
    void sameRewardOnClientAndServer() {
        TreasureChests.Chest c = CHESTS.all().get(CHESTS.all().size() / 2);
        assertEquals(ChestRewards.reward(c, WORLD, MONSTERS, TestData.ITEMS, Vocation.DRUID),
                ChestRewards.reward(c, WORLD, MONSTERS, TestData.ITEMS, Vocation.DRUID));
    }
}
