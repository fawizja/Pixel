package pl.fawizja.pixel.inventory;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.GroundItems;
import pl.fawizja.pixel.world.Position;

class ContainerTest {

    private static final int POTION = 30;
    private static final int TORCH = 32;
    private static final int ROPE = 33;

    private Container bp() { return Container.backpack(TestData.ITEMS); }

    @Test
    void backpackHas20Slots() {
        assertEquals(20, bp().size());
        assertEquals(20, bp().freeSlots());
    }

    @Test
    void stackablesMergeAndOverflowIntoNewSlots() {
        Container c = bp();
        assertTrue(c.add(new ItemStack(POTION, 60)).ok());
        assertTrue(c.add(new ItemStack(POTION, 60)).ok());
        assertEquals(120, c.count(POTION));
        assertEquals(18, c.freeSlots());
        assertEquals(100, c.get(0).count());
        assertEquals(20, c.get(1).count());
    }

    @Test
    void fullBackpackRejectsWithoutPartialChanges() {
        Container c = bp();
        for (int i = 0; i < 20; i++) assertTrue(c.add(ItemStack.of(TORCH)).ok());
        assertTrue(c.isFull());
        assertEquals(InventoryResult.NO_SPACE, c.add(ItemStack.of(ROPE)));
        assertEquals(0, c.count(ROPE));
        // stos ktory zmiescilby sie tylko czesciowo tez jest odrzucony w calosci
        Container d = bp();
        for (int i = 0; i < 19; i++) d.add(ItemStack.of(TORCH));
        assertEquals(InventoryResult.NO_SPACE, d.add(new ItemStack(POTION, 150)));
        assertEquals(0, d.count(POTION));
        assertTrue(d.add(new ItemStack(POTION, 100)).ok());
        assertEquals(InventoryResult.NO_SPACE, d.add(ItemStack.of(POTION)));
        assertEquals(100, d.count(POTION));
    }

    @Test
    void removeIsAllOrNothing() {
        Container c = bp();
        c.add(new ItemStack(POTION, 30));
        assertEquals(InventoryResult.NOT_ENOUGH_ITEMS, c.remove(POTION, 31));
        assertEquals(30, c.count(POTION));
        assertTrue(c.remove(POTION, 30).ok());
        assertEquals(0, c.count(POTION));
        assertEquals(20, c.freeSlots());
    }

    @Test
    void moveSwapsMergesAndSplits() {
        Container c = bp();
        c.add(new ItemStack(POTION, 50));        // slot 0
        c.add(ItemStack.of(TORCH));              // slot 1
        assertTrue(c.move(0, 5, 20).ok());       // split
        assertEquals(30, c.get(0).count());
        assertEquals(20, c.get(5).count());
        assertTrue(c.move(5, 0, 20).ok());       // merge
        assertEquals(50, c.get(0).count());
        assertNull(c.get(5));
        assertTrue(c.move(0, 1, 50).ok());       // swap
        assertEquals(TORCH, c.get(0).itemId());
        assertEquals(POTION, c.get(1).itemId());
        assertEquals(InventoryResult.EMPTY_SLOT, c.move(7, 0, 1));
        assertEquals(InventoryResult.INVALID_SLOT, c.move(0, 20, 1));
        assertEquals(InventoryResult.INVALID_AMOUNT, c.move(1, 2, 51));
        assertEquals(50, c.count(POTION));
    }

    @Test
    void compactMergesPartialStacks() {
        Container c = bp();
        c.putInSlot(3, new ItemStack(POTION, 40));
        c.putInSlot(9, new ItemStack(POTION, 40));
        c.putInSlot(12, new ItemStack(POTION, 40));
        c.compactStacks();
        assertEquals(120, c.count(POTION));
        assertEquals(18, c.freeSlots());
    }

    @Test
    void sortMergesStacksGroupsByCategoryAndClosesGaps() {
        Container c = bp();
        c.putInSlot(13, new ItemStack(POTION, 30));
        c.putInSlot(2, new ItemStack(50, 1, 1));
        c.putInSlot(18, new ItemStack(31, 4));
        c.putInSlot(7, new ItemStack(POTION, 50));
        c.putInSlot(11, new ItemStack(11, 1));
        c.putInSlot(15, new ItemStack(50, 1, 3));
        c.putInSlot(19, new ItemStack(1, 7));
        assertTrue(c.sort());
        // monety, bron (najpierw +3), zbroja, mikstury zdrowia (polaczone), many
        assertEquals(new ItemStack(1, 7), c.get(0));
        assertEquals(new ItemStack(50, 1, 3), c.get(1));
        assertEquals(new ItemStack(50, 1, 1), c.get(2));
        assertEquals(new ItemStack(11, 1), c.get(3));
        assertEquals(new ItemStack(POTION, 80), c.get(4));
        assertEquals(new ItemStack(31, 4), c.get(5));
        assertEquals(14, c.freeSlots());
        for (int i = 6; i < 20; i++) assertNull(c.get(i));
        assertFalse(c.sort(), "drugie sortowanie niczego nie zmienia");
    }

    @Test
    void sortActionWorksThroughSessionLog() {
        pl.fawizja.pixel.game.GameSession s = new pl.fawizja.pixel.game.GameSession(TestData.ITEMS, TestData.varden(), TestData.newCharacter(Vocation.DRUID));
        Container b = s.character().backpack();
        b.clear();
        b.putInSlot(19, new ItemStack(POTION, 5));
        assertTrue(pl.fawizja.pixel.game.Actions.apply(s, "sort", 1000));
        assertEquals(new ItemStack(POTION, 5), b.get(0));
    }

    @Test
    void equipAndUnequipKeepItemsSafe() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        Container bp = pc.backpack();
        int sword = TestData.ITEMS.byKey("training_sword").id();
        int spear = pc.equipment().get(EquipSlot.WEAPON).itemId();
        assertTrue(bp.add(ItemStack.of(sword)).ok());
        int slot = indexOf(bp, sword);
        assertTrue(pc.equipment().equipFromBackpack(bp, slot, pc.vocation(), pc.level()).ok());
        assertEquals(sword, pc.equipment().get(EquipSlot.WEAPON).itemId());
        assertEquals(spear, bp.get(slot).itemId(), "previous weapon swapped into the freed slot");

        int wand = TestData.ITEMS.byKey("wand_of_embers").id();
        bp.add(ItemStack.of(wand));
        assertEquals(InventoryResult.WRONG_VOCATION,
                pc.equipment().equipFromBackpack(bp, indexOf(bp, wand), pc.vocation(), pc.level()));
        assertEquals(InventoryResult.WRONG_SLOT_TYPE,
                pc.equipment().equipFromBackpack(bp, indexOf(bp, TORCH), pc.vocation(), pc.level()));

        while (!bp.isFull()) bp.add(ItemStack.of(TORCH));
        assertEquals(InventoryResult.NO_SPACE, pc.equipment().unequipToBackpack(EquipSlot.HEAD, bp));
        assertNotNull(pc.equipment().get(EquipSlot.HEAD));
    }

    @Test
    void dropAndPickUpMoveTheSameItems() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        GroundItems ground = new GroundItems();
        Position here = TestData.varden().spawn();
        int potions = pc.backpack().count(POTION);
        int slot = indexOf(pc.backpack(), POTION);
        assertTrue(ground.dropFrom(pc.backpack(), slot, 2, here, TestData.varden()).ok());
        assertEquals(potions - 2, pc.backpack().count(POTION));
        assertEquals(1, ground.at(here).size());
        assertTrue(ground.pickUp(here, 0, pc.backpack()).ok());
        assertEquals(potions, pc.backpack().count(POTION));
        assertEquals(0, ground.totalStacks());
        assertEquals(InventoryResult.EMPTY_SLOT, ground.pickUp(here, 0, pc.backpack()));
    }

    @Test
    void autoLootFilterRemembersIgnoredItems() {
        AutoLootFilter f = new AutoLootFilter();
        f.ignore(TORCH);
        assertTrue(f.isIgnored(TORCH));
        f.allow(TORCH);
        assertFalse(f.isIgnored(TORCH));
    }

    static int indexOf(Container c, int itemId) {
        for (int i = 0; i < c.size(); i++) if (c.get(i) != null && c.get(i).itemId() == itemId) return i;
        return -1;
    }
}
