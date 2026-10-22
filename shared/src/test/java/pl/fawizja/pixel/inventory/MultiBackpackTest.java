package pl.fawizja.pixel.inventory;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.CharacterFactory;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.config.StartingConfig;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

/** Etap 14: plecak 20 pol, dodatkowe plecaki jako kolejne strony, monety 100 gp = 1 platinum, 100 platinum = 1 crystal. */
class MultiBackpackTest {

    static int id(String key) { return TestData.ITEMS.byKey(key).id(); }

    static GameSession session(PlayerCharacter pc) {
        GameSession s = new GameSession(TestData.ITEMS, TestData.varden(), pc);
        s.teleport(new Position(10, 12, 0), 0);
        return s;
    }

    static int slotOf(Container c, String key) {
        for (int i = 0; i < c.size(); i++) if (c.get(i) != null && c.get(i).itemId() == id(key)) return i;
        return -1;
    }

    @Test
    void usingABackpackAddsAPageOfTwentySlotsUpToThreeExtra() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        GameSession s = session(pc);
        assertEquals(Container.BACKPACK_SLOTS, pc.backpack().size());
        for (int i = 1; i <= PlayerCharacter.MAX_EXTRA_BAGS; i++) {
            pc.backpack().add(ItemStack.of(id("backpack")));
            s.useSlot(slotOf(pc.backpack(), "backpack"), i * 1000L);
            assertEquals(20 * (i + 1), pc.backpack().size());
            assertEquals(0, pc.backpack().count(id("backpack")), "the backpack became a page");
        }
        pc.backpack().add(ItemStack.of(id("backpack")));
        s.useSlot(slotOf(pc.backpack(), "backpack"), 9000);
        assertEquals(80, pc.backpack().size(), "no more than 3 extra backpacks");
        assertEquals(1, pc.backpack().count(id("backpack")));
    }

    @Test
    void itemsMoveBetweenPagesAndTheLastBackpackDetachesOnlyWhenEmpty() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        GameSession s = session(pc);
        pc.attachBag();
        int sword = pc.backpack().add(ItemStack.of(id("iron_sword"))).ok() ? slotOf(pc.backpack(), "iron_sword") : -1;
        assertTrue(s.moveSlot(sword, 25).ok(), "drag onto the second page");
        assertEquals(id("iron_sword"), pc.backpack().get(25).itemId());
        assertFalse(s.detachBag(1000), "page 2 is not empty");
        assertEquals(40, pc.backpack().size());
        assertTrue(s.moveSlot(25, slotFree(pc.backpack(), 0, 20)).ok());
        assertTrue(s.detachBag(2000));
        assertEquals(20, pc.backpack().size());
        assertEquals(1, pc.backpack().count(id("backpack")), "detached backpack goes back into the bag");
    }

    static int slotFree(Container c, int from, int to) {
        for (int i = from; i < to; i++) if (c.get(i) == null) return i;
        return -1;
    }

    @Test
    void extraBackpacksAndTheirItemsSurviveSaveAndMirror() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        pc.attachBag();
        pc.attachBag();
        pc.backpack().putInSlot(55, new ItemStack(id("pearl"), 7));
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertEquals(2, back.extraBags());
        assertEquals(60, back.backpack().size());
        assertEquals(7, back.backpack().get(55).count());
        PlayerCharacter copy = TestData.newCharacter(Vocation.SORCERER);
        copy.copyStateFrom(back);
        assertEquals(60, copy.backpack().size());
        assertEquals(id("pearl"), copy.backpack().get(55).itemId());
    }

    @Test
    void coinsChangeUpAndDownLikeInTibia() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        GameSession s = session(pc);
        pc.backpack().clear();
        pc.backpack().add(new ItemStack(id("gold_coin"), 100));
        s.useSlot(0, 0);
        assertEquals(0, pc.backpack().count(id("gold_coin")));
        assertEquals(1, pc.backpack().count(id("platinum_coin")), "100 gp = 1 platinum");
        s.useSlot(slotOf(pc.backpack(), "platinum_coin"), 1000);
        assertEquals(100, pc.backpack().count(id("gold_coin")), "1 platinum = 100 gp");
        pc.backpack().clear();
        pc.backpack().add(new ItemStack(id("platinum_coin"), 100));
        s.useSlot(0, 2000);
        assertEquals(1, pc.backpack().count(id("crystal_coin")), "100 platinum = 1 crystal");
        // porzadkowanie wymienia pelne setki
        pc.backpack().clear();
        for (int i = 0; i < 3; i++) pc.backpack().add(new ItemStack(id("gold_coin"), 90));
        s.sortBackpack(3000);
        assertEquals(70, pc.backpack().count(id("gold_coin")));
        assertEquals(2, pc.backpack().count(id("platinum_coin")));
    }

    @Test
    void level300CharactersStartWithTwoBackpacks() {
        PlayerCharacter pc = new CharacterFactory(StartingConfig.defaults(), TestData.ITEMS).create("Wet", Vocation.DRUID, new Position(31, 16, 0), 300);
        assertEquals(40, pc.backpack().size());
    }
}
