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

/** Plecak 20 pol na jednej stronie, wieksze kontenery 24/36 przewijane w pionie, monety 100 gp = 1 platinum, 100 platinum = 1 crystal. */
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
    void startsWithTwentySlotsAndOnlyBiggerContainersReplaceIt() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        GameSession s = session(pc);
        pc.backpack().clear();
        assertEquals(Container.BACKPACK_SLOTS, pc.backpack().size());
        assertEquals(ContainerType.BACKPACK, pc.backpackType());
        pc.backpack().add(ItemStack.of(id("backpack")));
        s.wearBag(slotOf(pc.backpack(), "backpack"), 1000);
        assertEquals(20, pc.backpack().size(), "an ordinary backpack is not an upgrade");
        assertEquals(1, pc.backpack().count(id("backpack")));
        pc.backpack().add(ItemStack.of(id("backpack_traveler")));
        s.wearBag(slotOf(pc.backpack(), "backpack_traveler"), 2000);
        assertEquals(24, pc.backpack().size());
        assertEquals(ContainerType.TRAVELER, pc.backpackType());
        assertEquals(0, pc.backpack().count(id("backpack_traveler")), "the new backpack is worn");
        assertEquals(2, pc.backpack().count(id("backpack")), "the old one returns to the bag");
        pc.backpack().add(ItemStack.of(id("backpack_explorer")));
        s.wearBag(slotOf(pc.backpack(), "backpack_explorer"), 3000);
        assertEquals(36, pc.backpack().size());
        pc.backpack().add(ItemStack.of(id("backpack_traveler")));
        s.wearBag(slotOf(pc.backpack(), "backpack_traveler"), 4000);
        assertEquals(36, pc.backpack().size(), "never downgrades");
        assertEquals(2, pc.backpack().count(id("backpack_traveler")), "the one returned by the explorer swap plus the new one");
    }

    @Test
    void containerTypesAreKnownByItemAndSize() {
        assertEquals(20, ContainerType.BACKPACK.slots());
        assertEquals(ContainerType.EXPLORER, ContainerType.ofItem("backpack_explorer"));
        assertEquals(ContainerType.TRAVELER, ContainerType.ofSlots(24));
        assertNull(ContainerType.ofItem("iron_sword"));
        for (ContainerType t : ContainerType.values()) assertTrue(TestData.ITEMS.containsKey(t.itemKey()), t.itemKey());
    }

    @Test
    void biggerBackpackKeepsItemsAndSurvivesSaveAndMirror() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        pc.setBackpackSlots(36);
        pc.backpack().putInSlot(33, new ItemStack(id("pearl"), 7));
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertEquals(36, back.backpack().size());
        assertEquals(7, back.backpack().get(33).count());
        PlayerCharacter copy = TestData.newCharacter(Vocation.SORCERER);
        copy.copyStateFrom(back);
        assertEquals(36, copy.backpack().size());
        assertEquals(id("pearl"), copy.backpack().get(33).itemId());
        assertFalse(pc.setBackpackSlots(20), "shrinking is refused while the cut slots hold items");
    }

    @Test
    void legacySavesWithExtraBagPagesBecomeOneScrollableBackpack() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        String text = codec.encode(pc);
        int crcAt = text.lastIndexOf("crc=");
        String body = text.substring(0, crcAt).replaceAll("(?m)^bagslots=.*\\n", "") + "bags=2\n";
        int head = body.indexOf('\n') + 1;
        java.lang.reflect.Method crc = CharacterCodec.class.getDeclaredMethod("crc", String.class);
        crc.setAccessible(true);
        String legacy = body + "crc=" + crc.invoke(null, body.substring(head)) + "\n";
        PlayerCharacter back = codec.decode(legacy);
        assertEquals(60, back.backpack().size());
    }

    @Test
    void coinsChangeUpByThemselvesLikeInTibia() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        GameSession s = session(pc);
        pc.backpack().clear();
        pc.backpack().add(new ItemStack(id("gold_coin"), 100));
        assertEquals(0, pc.backpack().count(id("gold_coin")));
        assertEquals(1, pc.backpack().count(id("platinum_coin")), "100 gp = 1 platinum");
        s.useSlot(0, 1000); // uzycie monety tylko opisuje wartosc
        assertEquals(1, pc.backpack().count(id("platinum_coin")));
        assertTrue(s.log().latest(1).get(0).text().contains("100 gp"));
        pc.backpack().clear();
        pc.backpack().add(new ItemStack(id("platinum_coin"), 100));
        assertEquals(1, pc.backpack().count(id("crystal_coin")), "100 platinum = 1 crystal");
        // stare stosy (np. z zapisu) - porzadkowanie wymienia pelne setki
        pc.backpack().clear();
        for (int i = 0; i < 3; i++) pc.backpack().putInSlot(i, new ItemStack(id("gold_coin"), 90));
        s.sortBackpack(3000);
        assertEquals(70, pc.backpack().count(id("gold_coin")));
        assertEquals(2, pc.backpack().count(id("platinum_coin")));
    }

    @Test
    void level300CharactersStartWithATravelerBackpack() {
        PlayerCharacter pc = new CharacterFactory(StartingConfig.defaults(), TestData.ITEMS).create("Wet", Vocation.DRUID, new Position(31, 16, 0), 300);
        assertEquals(24, pc.backpack().size());
    }
}
