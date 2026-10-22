package pl.fawizja.pixel.npc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.world.FloorMap;
import pl.fawizja.pixel.world.Layer;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.Town;
import pl.fawizja.pixel.world.WorldLayout;
import pl.fawizja.pixel.world.WorldMap;

/** Skrzynie depozytu (etap 6): w kazdym miescie, obok skrzyni dziala depozyt bez bankiera. */
class DepotLockerTest {

    static Position lockerNear(WorldMap world, Position temple, int radius) {
        int locker = world.tiles().byKey("depot_locker").id();
        FloorMap f = world.floor(0);
        for (int y = temple.y() - radius; y <= temple.y() + radius; y++) {
            for (int x = temple.x() - radius; x <= temple.x() + radius; x++) {
                if (f.get(Layer.OBJECT, x, y) == locker) return new Position(x, y, 0);
            }
        }
        return null;
    }

    @Test
    void everyTownHasDepotLockersAndTheyOpenTheDepot() throws Exception {
        WorldMap world = TestData.world();
        NpcRegistry npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, world, ShopRegistry.loadDefault(TestData.ITEMS));
        for (Town t : world.towns()) {
            Position locker = lockerNear(world, t.temple(), 70);
            assertNotNull(locker, t.name());
            if (!t.name().equals("Frostholm")) continue;
            Position stand = null;
            for (int dy = -1; dy <= 1 && stand == null; dy++) {
                for (int dx = -1; dx <= 1 && stand == null; dx++) {
                    Position p = locker.translate(dx, dy);
                    if (world.isWalkable(p) && npcs.at(p) == null) stand = p;
                }
            }
            PlayerCharacter pc = TestData.newCharacterNamed("Skarbnik");
            pc.setPosition(stand);
            GameSession s = new GameSession(TestData.ITEMS, world, pc, null, new Random(1), 1);
            s.setTownServices(npcs, ShopRegistry.loadDefault(TestData.ITEMS), null);
            Npc n = s.npcNearby();
            assertNotNull(n);
            assertEquals(Npc.DEPOT_LOCKER, n.key());
            assertSame(n, s.npcNearby(), "stable instance keeps the depot window open");
            int rope = TestData.ITEMS.byKey("rope").id();
            pc.backpack().add(new ItemStack(rope, 1));
            int have = pc.backpack().count(rope);
            assertEquals(have, s.depotStore(rope, 0));
            assertEquals(have, pc.depot().count(rope));
            assertEquals(0, pc.backpack().count(rope));
            assertEquals(have, s.depotTake(rope, 0));
        }
    }
}
