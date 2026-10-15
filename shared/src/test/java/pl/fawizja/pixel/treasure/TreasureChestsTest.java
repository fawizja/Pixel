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
}
