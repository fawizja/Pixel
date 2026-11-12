package pl.fawizja.pixel.inventory;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.Actions;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.GroundItems;
import pl.fawizja.pixel.world.Position;

/** Plecak w plecaku jak w Tibii: otwieranie, bez limitu glebokosci, udzwig, zapis, przepelnienie lupu do plecaka z miejscem. */
class NestedBackpackTest {

    static int id(String key) { return TestData.ITEMS.byKey(key).id(); }

    static PlayerCharacter hero() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.backpack().clear();
        return pc;
    }

    static GameSession session(PlayerCharacter pc) {
        GameSession s = new GameSession(TestData.ITEMS, TestData.varden(), pc);
        s.teleport(new Position(10, 12, 0), 0);
        return s;
    }

    @Test
    void aBagInsideABagOpensAsAChildContainerWithItsOwnSlots() {
        PlayerCharacter pc = hero();
        Container root = pc.backpack();
        assertTrue(root.add(ItemStack.of(id("backpack"))).ok());
        assertTrue(root.add(ItemStack.of(id("backpack_traveler"))).ok());
        assertTrue(root.isBag(0));
        assertTrue(root.isBag(1));
        Container a = root.bagAt(0), b = root.bagAt(1);
        assertEquals(20, a.size());
        assertEquals(24, b.size(), "the traveller bag has 24 slots");
        // plecak w plecaku A, a w nim kolejny: bez limitu glebokosci
        assertEquals(InventoryResult.OK, Container.moveBetween(root, 1, a, 0, 1));
        assertNull(root.get(1));
        assertSame(b, a.bagAt(0), "the same bag object, only moved");
        assertEquals(0, root.freeSlots() - (root.size() - 1), "only the outer bag is left in the main backpack");
        Container cur = a;
        for (int depth = 0; depth < 12; depth++) {
            assertTrue(cur.add(ItemStack.of(id("backpack"))).ok() || cur.freeSlots() == 0);
            Container next = null;
            for (int i = 0; i < cur.size() && next == null; i++) if (cur.isBag(i) && cur.bagAt(i).isEmptyDeep() && cur.bagAt(i) != b) next = cur.bagAt(i);
            assertNotNull(next, "depth " + depth);
            cur = next;
        }
        assertSame(b, root.resolve(new int[] {0, 0}));
        assertNull(root.resolve(new int[] {5, 0}), "path through an empty slot");
        assertEquals("0.3.1", Container.pathText(Container.parsePath("0.3.1")));
        assertEquals(0, Container.parsePath("-").length);
        assertNull(Container.parsePath("x.1"));
    }

    @Test
    void aBagNeverEntersItselfOrItsOwnContents() {
        PlayerCharacter pc = hero();
        Container root = pc.backpack();
        root.add(ItemStack.of(id("backpack")));
        root.add(ItemStack.of(id("backpack")));
        Container inner = root.bagAt(0);
        // plecak 1 do plecaka 0: dobrze; potem plecak 0 do wlasnego potomka: odmowa
        assertEquals(InventoryResult.OK, Container.moveBetween(root, 1, inner, 0, 1));
        assertEquals(InventoryResult.CYCLE, Container.moveBetween(root, 0, inner.bagAt(0), 0, 1));
        assertEquals(InventoryResult.CYCLE, root.putIntoBag(0, 0));
        // zamiana plecaka nadrzednego z potomkiem tez tworzylaby petle
        assertEquals(InventoryResult.CYCLE, Container.moveBetween(inner, 0, root, 0, 1));
        assertSame(inner, root.bagAt(0));
    }

    @Test
    void capLimitsWhatYouCanPutInTheBagsAndTheirWeightCounts() {
        PlayerCharacter pc = hero();
        Container root = pc.backpack();
        long cap0 = (long) pc.capacity() * ItemWeights.PER_OZ;
        assertEquals(cap0, root.weightLimit());
        long cap;
        assertTrue(root.add(ItemStack.of(id("backpack"))).ok());
        Container bag = root.bagAt(0);
        long bagOnly = root.weight();
        assertEquals(ItemWeights.unit(TestData.ITEMS, id("backpack")), bagOnly);
        // zawartosc plecaka w plecaku liczy sie do wagi
        assertTrue(root.add(ItemStack.of(id("leather_armor"))).ok());
        Container.moveBetween(root, 1, bag, 0, 1);
        assertTrue(root.weight() > bagOnly, "nested contents weigh");
        // zapychamy do limitu ciezkimi przedmiotami (limit obnizony, zeby wolnych pol bylo wiecej niz miesci waga)
        root.setWeightLimit(() -> 20000);
        cap = 20000;
        int swords = 0;
        while (root.add(ItemStack.of(id("hunting_spear"))).ok()) {
            swords++;
            assertTrue(swords < 100, "the cap must stop us");
        }
        assertTrue(swords > 0);
        assertEquals(InventoryResult.TOO_HEAVY, root.add(ItemStack.of(id("hunting_spear"))));
        assertFalse(root.canAdd(id("hunting_spear"), 1));
        assertTrue(root.weight() <= cap);
        assertTrue(root.freeSlots() > 0, "slots were free: the weight was the limit");
        // lekki przedmiot jeszcze wchodzi, jesli zmiesci sie w limicie
        assertEquals(root.freeWeight() >= ItemWeights.unit(TestData.ITEMS, id("gold_coin")), root.add(new ItemStack(id("gold_coin"), 1)).ok());
        // przenoszenie w obrebie drzewa nie zmienia wagi, wiec cap tego nie blokuje
        long before = root.weight();
        int slot = 0;
        for (int i = 0; i < root.size(); i++) if (root.get(i) != null && root.get(i).itemId() == id("hunting_spear")) { slot = i; break; }
        assertEquals(InventoryResult.OK, Container.moveBetween(root, slot, bag, 5, 1));
        assertEquals(before, root.weight());
    }

    @Test
    void threeLevelsOfBagsSurviveSaveAndLoadAndTheOldVersionStillLoads() throws Exception {
        PlayerCharacter pc = hero();
        Container root = pc.backpack();
        root.add(ItemStack.of(id("backpack_explorer")));
        Container l1 = root.bagAt(0);
        l1.add(ItemStack.of(id("backpack_traveler")));
        l1.add(new ItemStack(id("health_potion"), 33));
        Container l2 = l1.bagAt(0);
        l2.add(ItemStack.of(id("backpack")));
        l2.add(ItemStack.of(id("hunting_spear")).withUpgrade(2));
        Container l3 = l2.bagAt(0);
        l3.add(new ItemStack(id("gold_coin"), 40));
        l3.add(ItemStack.of(id("rope")));
        root.add(new ItemStack(id("mana_potion"), 5));
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        String text = codec.encode(pc);
        assertTrue(text.startsWith(CharacterCodec.MAGIC + " 10\n"), "save version 10 carries bag contents");
        PlayerCharacter back = codec.decode(text);
        Container r2 = back.backpack();
        assertEquals(root, r2, "deep equality of the whole tree");
        assertEquals(33, r2.bagAt(0).get(1).count());
        Container b3 = r2.bagAt(0).bagAt(0).bagAt(0);
        assertEquals(40, b3.count(id("gold_coin")));
        assertEquals(1, b3.count(id("rope")));
        assertEquals(2, r2.bagAt(0).bagAt(0).get(1).upgrade());
        assertEquals(root.weight(), r2.weight());
        // a second round trip is stable
        assertEquals(text, codec.encode(back));
        // zapis sprzed v10 (bez zawartosci plecakow) wczytuje sie bez zmian
        PlayerCharacter plain = hero();
        plain.backpack().add(ItemStack.of(id("backpack")));
        plain.backpack().add(new ItemStack(id("health_potion"), 3));
        String v10 = codec.encode(plain);
        String v9 = v10.replaceFirst(CharacterCodec.MAGIC + " 10\n", CharacterCodec.MAGIC + " 9\n");
        PlayerCharacter old = codec.decode(v9);
        assertEquals(1, old.backpack().count(id("backpack")));
        assertTrue(old.backpack().isBag(0));
        assertTrue(old.backpack().bagAt(0).isEmptyDeep(), "old bags come back empty");
        // mirror dla klienta online
        PlayerCharacter mirror = TestData.newCharacter(Vocation.KNIGHT);
        mirror.copyStateFrom(back);
        assertEquals(root, mirror.backpack());
    }

    @Test
    void lootOverflowsIntoTheFirstBagWithFreeSpaceDepthFirst() {
        PlayerCharacter pc = hero();
        Container root = pc.backpack();
        root.add(ItemStack.of(id("backpack")));
        root.add(ItemStack.of(id("backpack")));
        // reszte glownego plecaka zapychamy lekkimi, niestackowalnymi przedmiotami (torba pelna)
        while (root.freeSlots() > 0) root.putInSlot(firstFree(root), ItemStack.of(id("rope")));
        assertTrue(root.isFull());
        Container first = root.bagAt(0), second = root.bagAt(1);
        // pierwszy plecak ma w srodku plecak i jest prawie pelny, wiec lup idzie najpierw do niego (glebokosc najpierw)
        first.add(ItemStack.of(id("backpack")));
        Container deep = first.bagAt(0);
        while (first.freeSlots() > 0) first.putInSlot(firstFree(first), ItemStack.of(id("rope")));
        assertTrue(first.isFull());
        assertEquals(InventoryResult.OK, root.add(ItemStack.of(id("hunting_spear"))));
        assertEquals(1, deep.count(id("hunting_spear")), "first container with space, depth first");
        assertEquals(0, second.count(id("hunting_spear")));
        // gdy wszystko pelne, nic sie nie dzieje
        long total = root.count(id("rope"));
        PlayerCharacter pc2 = hero();
        Container r2 = pc2.backpack();
        while (r2.freeSlots() > 0) r2.putInSlot(firstFree(r2), ItemStack.of(id("rope")));
        assertEquals(InventoryResult.NO_SPACE, r2.add(ItemStack.of(id("hunting_spear"))));
        assertFalse(r2.canAdd(id("hunting_spear"), 1));
        assertTrue(total > 0);
    }

    @Test
    void corpseLootAndGroundPickupUseTheBagsToo() {
        PlayerCharacter pc = hero();
        Container root = pc.backpack();
        root.add(ItemStack.of(id("backpack")));
        while (root.freeSlots() > 0) root.putInSlot(firstFree(root), ItemStack.of(id("rope")));
        GroundItems ground = new GroundItems();
        Position at = new Position(10, 12, 0);
        ground.addCorpse(at, "wolf", java.util.List.of(ItemStack.of(id("hunting_spear")), new ItemStack(id("gold_coin"), 30)), 0);
        java.util.List<ItemStack> taken = ground.lootCorpse(at, root);
        assertEquals(2, taken.size(), "full main backpack, but the bag takes the loot");
        assertEquals(1, root.bagAt(0).count(id("hunting_spear")));
        assertEquals(30, root.count(id("gold_coin")));
    }

    @Test
    void stackablesMergeIntoExistingStacksBeforeUsingFreeSlots() {
        PlayerCharacter pc = hero();
        Container root = pc.backpack();
        root.add(ItemStack.of(id("backpack")));
        Container bag = root.bagAt(0);
        bag.add(new ItemStack(id("health_potion"), 50));
        assertEquals(50, bag.get(0).count());
        // w glownym plecaku sa wolne pola, ale stos w plecaku ma pierwszenstwo
        assertTrue(root.freeSlots() > 5);
        assertTrue(root.add(new ItemStack(id("health_potion"), 20)).ok());
        assertEquals(70, bag.get(0).count(), "merged into the stack inside the bag");
        for (int i = 0; i < root.size(); i++) {
            if (root.get(i) != null) assertNotEquals(id("health_potion"), root.get(i).itemId(), "no new stack in the main backpack");
        }
        // przepelnienie stosu (max 100): reszta do wolnego pola, nie ginie
        assertTrue(root.add(new ItemStack(id("health_potion"), 60)).ok());
        assertEquals(100, bag.get(0).count());
        assertEquals(130, root.count(id("health_potion")));
        // usuwanie sztuk z glebi dziala
        assertTrue(root.remove(id("health_potion"), 125).ok());
        assertEquals(5, root.count(id("health_potion")));
        assertEquals(InventoryResult.NOT_ENOUGH_ITEMS, root.remove(id("health_potion"), 6));
    }

    @Test
    void droppedBagKeepsItsContentsAndTransactionsDoNotLeak() {
        PlayerCharacter pc = hero();
        Container root = pc.backpack();
        root.add(ItemStack.of(id("backpack")));
        root.bagAt(0).add(new ItemStack(id("health_potion"), 9));
        // transakcja na kopii nie zmienia oryginalnego plecaka w plecaku
        Container tx = root.copy();
        assertTrue(tx.remove(id("health_potion"), 9).ok());
        assertEquals(9, root.count(id("health_potion")));
        root.commitFrom(tx);
        assertEquals(0, root.count(id("health_potion")));
        root.bagAt(0).add(new ItemStack(id("health_potion"), 9));
        // wyrzucenie plecaka z zawartoscia i podniesienie
        GroundItems ground = new GroundItems();
        Position where = new Position(10, 12, 0);
        assertEquals(InventoryResult.OK, ground.dropFrom(root, 0, 1, where, TestData.varden()));
        assertEquals(0, root.count(id("health_potion")));
        assertEquals(InventoryResult.OK, ground.pickUp(where, 0, root));
        assertEquals(9, root.count(id("health_potion")));
        // plecak z zawartoscia nie jest "zwykly" - nie da sie go sprzedac razem z zawartoscia
        assertEquals(0, root.countPlain(id("backpack")));
        // smierc: caly plecak z zawartoscia trafia do ciala, a z ciala do nowego plecaka
        for (int seed = 0; seed < 5; seed++) {
            PlayerCharacter dead = hero();
            dead.backpack().add(ItemStack.of(id("backpack")));
            dead.backpack().bagAt(0).add(new ItemStack(id("health_potion"), 4));
            pl.fawizja.pixel.character.DeathLoss loss = dead.applyDeath(8, 0, false, true, new Random(seed)); // czaszka: traci wszystko
            assertTrue(loss.backpackDropped());
            ItemStack bag = loss.dropped().stream().filter(s -> s.contents() != null).findFirst().orElseThrow();
            assertEquals(4, bag.contents().count(id("health_potion")));
            assertEquals(0, dead.backpack().count(id("health_potion")));
            PlayerCharacter heir = hero();
            assertTrue(heir.backpack().add(bag).ok());
            assertEquals(4, heir.backpack().count(id("health_potion")));
        }
    }

    @Test
    void sessionMovesBetweenParentAndChildAndDropOnABagPutsItInside() {
        PlayerCharacter pc = hero();
        GameSession s = session(pc);
        Container root = pc.backpack();
        root.add(ItemStack.of(id("backpack")));
        root.add(ItemStack.of(id("hunting_spear")));
        root.add(ItemStack.of(id("rope")));
        // upuszczenie miecza (pole 1) na pole z plecakiem (0): trafia do srodka
        assertEquals(InventoryResult.OK, s.moveSlot(1, 0));
        assertNull(root.get(1));
        assertEquals(1, root.bagAt(0).count(id("hunting_spear")));
        // akcja sieciowa: z plecaka (sciezka 0, pole 0) do glownego pola 5
        assertTrue(Actions.apply(s, "bag_move:0:0:-:5", 1000));
        assertEquals(id("hunting_spear"), root.get(5).itemId());
        assertEquals(0, root.bagAt(0).count(id("hunting_spear")));
        // i z powrotem przez bag_move do plecaka
        assertTrue(Actions.apply(s, "bag_move:-:5:0:3", 1000));
        assertEquals(id("hunting_spear"), root.bagAt(0).get(3).itemId());
        // uzycie plecaka nie zastepuje juz glownego plecaka (Tibia: otwiera sie), zalozenie to osobna akcja
        int size = pc.backpackSlots();
        assertEquals(pl.fawizja.pixel.magic.CastResult.OK, s.useSlot(0, 2000));
        assertEquals(size, pc.backpackSlots());
        assertEquals(1, root.count(id("backpack")));
    }

    @Test
    void nestedPotionIsUsedFromTheBag() {
        PlayerCharacter pc = hero();
        GameSession s = session(pc);
        Container root = pc.backpack();
        root.add(ItemStack.of(id("backpack")));
        Container bag = root.bagAt(0);
        bag.add(new ItemStack(id("health_potion"), 3));
        pc.damage(50);
        int hp = pc.health();
        s.bagUse(new int[] {0}, 0, 5000);
        assertEquals(2, bag.get(0).count());
        assertTrue(pc.health() > hp);
    }

    private static int firstFree(Container c) {
        for (int i = 0; i < c.size(); i++) if (c.get(i) == null) return i;
        return -1;
    }
}
