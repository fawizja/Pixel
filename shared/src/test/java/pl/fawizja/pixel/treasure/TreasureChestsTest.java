package pl.fawizja.pixel.treasure;

import static org.junit.jupiter.api.Assertions.*;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.WorldMap;

class TreasureChestsTest {

    @Test
    void chestsAreReachableOutsideSafeZonesAndTheSameEveryTime() {
        WorldMap world = TestData.world();
        long t0 = System.nanoTime();
        TreasureChests c = TreasureChests.generate(world);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms < 5_000, "generation took " + ms + " ms");
        Map<Integer, BitSet> reach = TreasureChests.reachable(world);
        // skrzynie lupu z mapy moga lezec tez w lochach osiagalnych tylko statkiem (Trzewia Pozogi): od przystani
        java.util.List<pl.fawizja.pixel.world.Position> harbours = new java.util.ArrayList<>();
        try {
            for (pl.fawizja.pixel.npc.Npc n : pl.fawizja.pixel.npc.NpcRegistry.load(pl.fawizja.pixel.world.WorldLayout.WORLD_MAP, world,
                    pl.fawizja.pixel.npc.ShopRegistry.loadDefault(TestData.ITEMS)).all()) {
                if (n.role() != pl.fawizja.pixel.npc.NpcRole.CAPTAIN) continue;
                for (int[] d : new int[][] {{0, 1}, {1, 0}, {-1, 0}, {0, -1}}) {
                    pl.fawizja.pixel.world.Position q = n.position().translate(d[0], d[1]);
                    if (world.isWalkable(q)) harbours.add(q);
                }
            }
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        Map<Integer, BitSet> bySea = TreasureChests.reachable(world, harbours);
        for (int z : new int[] {-1, -2, -3, -4, -5}) {
            // losowe skrzynie (id "c..."); skrzynie lupu z mapy (chests.csv, id "d...") licza sie osobno
            assertEquals(4, c.all().stream().filter(x -> x.position().z() == z && x.vocation() == null
                    && !x.id().startsWith(TreasureChests.LOOT_PREFIX)).count(), "floor " + z);
        }
        for (int z : new int[] {1, 2, 3}) {
            assertEquals(2, c.all().stream().filter(x -> x.position().z() == z && !x.id().startsWith(TreasureChests.LOOT_PREFIX)).count(), "floor +" + z);
        }
        for (TreasureChests.Chest x : c.all()) {
            if (x.vocation() != null) continue; // nagrody zadan z oltarzami (AltarQuestTest): skarbiec tylko dzwignia
            assertTrue(world.isWalkable(x.position()), x.id());
            assertFalse(world.isProtectionZone(x.position()), x.id());
            Map<Integer, BitSet> r = x.id().startsWith(TreasureChests.LOOT_PREFIX) ? bySea : reach;
            assertTrue(r.get(x.position().z()).get(x.position().y() * world.width() + x.position().x()), x.id());
            TestData.ITEMS.byKey(x.itemKey());
        }
        assertEquals(c.all(), TreasureChests.generate(world).all());
        java.util.List<TreasureChests.Chest> loot = c.all().stream().filter(x -> x.id().startsWith(TreasureChests.LOOT_PREFIX)).toList();
        assertFalse(loot.isEmpty(), "loot chests in wild dungeons (chests.csv)");
        assertEquals(loot.size(), loot.stream().map(TreasureChests.Chest::id).distinct().count(), "loot chest ids unique");
        for (TreasureChests.Chest x : loot) {
            // skrzynie w dziczy leza pod ziemia i sa skromne; nagroda zadania za drzwiami poziomu (Warownia Gorvalda, +3) moze byc wyzej
            boolean quest = x.doorLevel() >= 20 || world.rewardChoiceAt(x.position()) != null; // sala nagrod wyprawy (wybor jednej skrzyni)
            assertTrue(x.gold() > 0 && (quest ? x.gold() <= 10_000 : x.position().z() < 0 && x.gold() < 5_000), x.id());
        }
        assertTrue(loot.stream().anyMatch(x -> x.position().z() == 3 && x.doorLevel() == 55), "the castle quest chest behind the level door");
        long g1 = c.all().stream().filter(x -> x.position().z() == -1).findFirst().get().gold();
        long g5 = c.all().stream().filter(x -> x.position().z() == -5).findFirst().get().gold();
        assertEquals(500, g1);
        assertEquals(40_500, g5, "deeper chests hold more");
    }

    @Test
    void eachCharacterOpensEachChestOnceAndTheSaveRemembersIt() throws Exception {
        WorldMap world = TestData.world();
        TreasureChests c = TreasureChests.generate(world);
        TreasureChests.Chest chest = c.all().get(0);
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        GameSession s = new GameSession(TestData.ITEMS, world, pc,
                new MonsterManager(world, null, List.of(), new Random(1)), new Random(2), 8);
        s.setChests(c);
        assertFalse(s.openChest(0), "no chest next to the temple");
        s.teleport(chest.position(), 0);
        long gold = s.gold();
        ChestRewards.Reward r = s.chestReward(chest);
        assertTrue(r.gold() >= chest.gold(), "the map content is the minimum");
        int first = r.items().get(0).itemId();
        int had = pc.backpack().count(first);
        s.useNearby(0);
        assertEquals(gold + r.gold(), s.gold());
        assertEquals(had + r.items().get(0).count(), pc.backpack().count(first));
        assertTrue(pc.openedChests().contains(chest.id()));
        // dwa wiersze: zloto, potem przedmioty
        String head = s.log().latest(2).get(0).text(), list = s.log().latest(2).get(1).text();
        assertTrue(head.contains(String.format(java.util.Locale.ROOT, "%,d gp", r.gold()).replace(',', ' ')), "the gold is named: " + head);
        assertTrue(list.contains(TestData.ITEMS.get(first).name()), "the item is named: " + list);
        s.useNearby(0);
        assertEquals(gold + r.gold(), s.gold(), "only once");
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        assertTrue(codec.decode(codec.encode(pc)).openedChests().contains(chest.id()));
    }

    @Test
    void spikeTrapsLieDeeperHurtButNeverKill() {
        WorldMap world = TestData.world();
        TreasureChests c = TreasureChests.generate(world);
        assertTrue(c.traps().stream().noneMatch(p -> p.z() > -2), "no traps on the first dungeon floor");
        for (int z = -2; z >= -5; z--) {
            final int fz = z;
            long n = c.traps().stream().filter(p -> p.z() == fz).count();
            assertTrue(n > 0 && n <= TreasureChests.TRAPS_PER_DEPTH * -z, "floor " + z + ": " + n);
        }
        for (pl.fawizja.pixel.world.Position p : c.traps()) {
            assertTrue(world.isWalkable(p));
            assertFalse(world.isProtectionZone(p));
            assertNull(c.near(p), "trap next to a chest at " + p);
        }
        assertEquals(c.traps(), TreasureChests.generate(world).traps());
        assertEquals(10, TreasureChests.trapDamage(100, 100, -2));
        assertEquals(25, TreasureChests.trapDamage(100, 100, -5));
        assertEquals(4, TreasureChests.trapDamage(100, 5, -5), "a trap leaves 1 HP");
        assertEquals(0, TreasureChests.trapDamage(100, 1, -5));

        // wejscie na pulapke w symulacji ruchu
        pl.fawizja.pixel.world.Position trap = c.traps().get(0);
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        GameSession s = new GameSession(TestData.ITEMS, world, pc,
                new MonsterManager(world, null, List.of(), new Random(1)), new Random(2), 8);
        s.setChests(c);
        pl.fawizja.pixel.world.Direction in = null;
        for (pl.fawizja.pixel.world.Direction d : pl.fawizja.pixel.world.Direction.values()) {
            pl.fawizja.pixel.world.Position from = trap.translate(-d.dx, -d.dy);
            if (d.dx * d.dy == 0 && world.isWalkable(from) && world.transitionAt(from) == null) {
                s.teleport(from, 0);
                in = d;
                break;
            }
        }
        assertNotNull(in);
        int hp = pc.health();
        s.update(10_000, in);
        assertEquals(trap, pc.position());
        assertEquals(hp - TreasureChests.trapDamage(pc.maxHealth(), hp, trap.z()), pc.health());
    }
}
