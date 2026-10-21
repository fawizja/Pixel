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
        for (int z : new int[] {-1, -2, -3, -4, -5}) {
            assertEquals(4, c.all().stream().filter(x -> x.position().z() == z).count(), "floor " + z);
        }
        for (int z : new int[] {1, 2, 3}) {
            assertEquals(2, c.all().stream().filter(x -> x.position().z() == z).count(), "floor +" + z);
        }
        for (TreasureChests.Chest x : c.all()) {
            assertTrue(world.isWalkable(x.position()), x.id());
            assertFalse(world.isProtectionZone(x.position()), x.id());
            assertTrue(reach.get(x.position().z()).get(x.position().y() * world.width() + x.position().x()), x.id());
            TestData.ITEMS.byKey(x.itemKey());
        }
        assertEquals(c.all(), TreasureChests.generate(world).all());
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
        int potions = pc.backpack().count(TestData.ITEMS.byKey(chest.itemKey()).id());
        s.useNearby(0);
        assertEquals(gold + chest.gold(), s.gold());
        assertEquals(potions + chest.count(), pc.backpack().count(TestData.ITEMS.byKey(chest.itemKey()).id()));
        assertTrue(pc.openedChests().contains(chest.id()));
        s.useNearby(0);
        assertEquals(gold + chest.gold(), s.gold(), "only once");
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
