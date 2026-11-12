package pl.fawizja.pixel.npc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.Actions;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.inventory.InventoryResult;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.Town;
import pl.fawizja.pixel.world.WorldLayout;
import pl.fawizja.pixel.world.WorldMap;
import pl.fawizja.pixel.world.Direction;

/** Okno depozytu jak w Tibii: uzycie skrzyni, przenoszenie plecak - depozyt, trwalosc w zapisie. */
class DepotWindowTest {

    static final WorldMap WORLD = TestData.world();

    static GameSession besideLocker(PlayerCharacter pc, Town t) {
        try {
            return besideLockerIo(pc, t);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    static GameSession besideLockerIo(PlayerCharacter pc, Town t) throws java.io.IOException {
        NpcRegistry npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, WORLD, ShopRegistry.loadDefault(TestData.ITEMS));
        Position locker = DepotLockerTest.lockerNear(WORLD, t.temple(), 70);
        Position stand = null;
        for (Direction d : Direction.values()) {
            Position p = locker.translate(d.dx, d.dy);
            if (WORLD.isWalkable(p) && npcs.at(p) == null) { stand = p; break; }
        }
        pc.setPosition(stand);
        GameSession s = new GameSession(TestData.ITEMS, WORLD, pc, null, new Random(1), 1);
        s.setTownServices(npcs, ShopRegistry.loadDefault(TestData.ITEMS), null);
        return s;
    }

    static int id(String k) { return TestData.ITEMS.byKey(k).id(); }

    @Test
    void usingALockerOnTheMapRequestsTheDepotWindowOnce() {
        PlayerCharacter pc = TestData.newCharacterNamed("Skrzynia");
        Town t = WORLD.towns().get(0);
        GameSession s = besideLocker(pc, t);
        Position locker = DepotLockerTest.lockerNear(WORLD, t.temple(), 70);
        assertTrue(s.isDepotLocker(locker));
        assertFalse(s.takeDepotOpen());
        s.useAt(locker, 1000);
        assertTrue(s.takeDepotOpen(), "the client opens the depot window");
        assertFalse(s.takeDepotOpen(), "read once");
    }

    @Test
    void dragOrTapMovesWholeStacksBothWaysAndStacksMerge() {
        PlayerCharacter pc = TestData.newCharacterNamed("Przenosi");
        GameSession s = besideLocker(pc, WORLD.towns().get(0));
        pc.backpack().clear();
        pc.backpack().putInSlot(2, new ItemStack(id("health_potion"), 7));
        pc.backpack().putInSlot(3, new ItemStack(id("iron_sword"), 1));
        assertEquals(InventoryResult.OK, s.depotTransfer(false, 2, true, 10, 1000));
        assertNull(pc.backpack().get(2));
        assertEquals(7, pc.depot().get(10).count(), "to the chosen depot slot");
        assertEquals(InventoryResult.OK, s.depotTransfer(false, 3, true, -1, 1100), "tap = first free slot");
        assertEquals(id("iron_sword"), pc.depot().get(0).itemId());
        pc.backpack().putInSlot(5, new ItemStack(id("health_potion"), 3));
        assertEquals(InventoryResult.OK, s.depotTransfer(false, 5, true, 10, 1200));
        assertEquals(10, pc.depot().get(10).count(), "stacks merge onto an occupied slot");
        assertEquals(InventoryResult.OK, s.depotTransfer(true, 10, false, 4, 1300));
        assertEquals(10, pc.backpack().get(4).count());
        assertNull(pc.depot().get(10));
        assertEquals(InventoryResult.EMPTY_SLOT, s.depotTransfer(true, 50, false, -1, 1400));
        assertEquals(InventoryResult.INVALID_SLOT, s.depotTransfer(true, 500, false, -1, 1500));
    }

    @Test
    void nothingMovesAwayFromTheLocker() {
        PlayerCharacter pc = TestData.newCharacterNamed("Daleko");
        GameSession s = besideLocker(pc, WORLD.towns().get(0));
        pc.backpack().putInSlot(0, new ItemStack(id("health_potion"), 2));
        pc.setPosition(pc.position().translate(20, 20));
        assertEquals(InventoryResult.INVALID_SLOT, s.depotTransfer(false, 0, true, -1, 1000));
        assertNotNull(pc.backpack().get(0));
    }

    @Test
    void actionStringsDriveTheSameTransfers() {
        PlayerCharacter pc = TestData.newCharacterNamed("Akcje");
        GameSession s = besideLocker(pc, WORLD.towns().get(0));
        pc.backpack().clear();
        pc.backpack().putInSlot(1, new ItemStack(id("mana_potion"), 4));
        assertTrue(Actions.apply(s, "depot_move:B:1:D:7", 1000));
        assertEquals(4, pc.depot().get(7).count());
        assertTrue(Actions.apply(s, "depot_move:D:7:B:-1", 1100));
        assertEquals(4, pc.backpack().count(id("mana_potion")));
        assertEquals(0, pc.depot().count(id("mana_potion")));
    }

    @Test
    void fullDepotRefusesAndKeepsTheItem() {
        PlayerCharacter pc = TestData.newCharacterNamed("Pelny");
        GameSession s = besideLocker(pc, WORLD.towns().get(0));
        for (int i = 0; i < pc.depot().size(); i++) pc.depot().putInSlot(i, new ItemStack(id("iron_sword"), 1));
        pc.backpack().clear();
        pc.backpack().putInSlot(0, new ItemStack(id("pearl"), 1));
        assertNotEquals(InventoryResult.OK, s.depotTransfer(false, 0, true, -1, 1000));
        assertEquals(1, pc.backpack().get(0).count(), "the item stays in the backpack");
    }

    @Test
    void depotIsSharedBetweenTownsAndSurvivesSaveLoadAndMirror() throws Exception {
        PlayerCharacter pc = TestData.newCharacterNamed("Trwaly");
        GameSession s = besideLocker(pc, WORLD.towns().get(0));
        pc.backpack().clear();
        pc.backpack().putInSlot(0, new ItemStack(id("pearl"), 12));
        assertEquals(InventoryResult.OK, s.depotTransfer(false, 0, true, 60, 1000));
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertEquals(PlayerCharacter.DEPOT_SLOTS, back.depot().size());
        assertEquals(12, back.depot().get(60).count());
        PlayerCharacter copy = TestData.newCharacterNamed("Trwaly");
        copy.copyStateFrom(back);
        assertEquals(12, copy.depot().get(60).count());
        // inne miasto: ten sam depozyt po zapisie
        Town other = WORLD.towns().get(WORLD.towns().size() - 1);
        GameSession s2 = besideLocker(back, other);
        assertEquals(InventoryResult.OK, s2.depotTransfer(true, 60, false, -1, 2000));
        assertEquals(12, back.backpack().count(id("pearl")));
    }
}
