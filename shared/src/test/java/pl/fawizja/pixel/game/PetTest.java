package pl.fawizja.pixel.game;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.vocation.Vocation;

class PetTest {
    @Test
    void petFollowsWhileItsItemIsInTheBackpack() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        GameSession s = new GameSession(TestData.ITEMS, TestData.world(), pc);
        int dog = TestData.ITEMS.byKey("pet_dog").id();
        int cat = TestData.ITEMS.byKey("pet_cat").id();
        pc.backpack().add(new ItemStack(dog, 1));
        pc.backpack().add(new ItemStack(cat, 1));
        int dogSlot = slotOf(pc, dog), catSlot = slotOf(pc, cat);

        s.useSlot(dogSlot, 1000);
        assertEquals("pet_dog", pc.pet());
        assertEquals(1, pc.backpack().count(dog), "the pet item is not used up");
        s.useSlot(catSlot, 1100);
        assertEquals("pet_cat", pc.pet(), "another pet replaces the first");
        s.useSlot(catSlot, 1200);
        assertNull(pc.pet(), "using it again sends it back");

        s.useSlot(dogSlot, 1300);
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        assertEquals("pet_dog", codec.decode(codec.encode(pc)).pet(), "the pet is saved");

        pc.backpack().takeFromSlot(dogSlot, 1);
        s.update(1400, null);
        assertNull(pc.pet(), "without its item the pet leaves");
    }

    @Test
    void shopsSellAPetInEveryTown() {
        pl.fawizja.pixel.npc.ShopRegistry shops = pl.fawizja.pixel.npc.ShopRegistry.loadDefault(TestData.ITEMS);
        String[][] sold = {{"varden_general", "pet_dog"}, {"selv_shop", "pet_cat"}, {"frost_shop", "pet_raven"}, {"qas_shop", "pet_fennec"}};
        for (String[] e : sold) {
            assertEquals(1, TestData.ITEMS.byKey(e[1]).maxStack());
            assertNotNull(shops.get(e[0]).offer(TestData.ITEMS.byKey(e[1]).id()), e[0] + " sells " + e[1]);
        }
    }

    private static int slotOf(PlayerCharacter pc, int itemId) {
        for (int i = 0; i < pc.backpack().size(); i++) {
            ItemStack st = pc.backpack().get(i);
            if (st != null && st.itemId() == itemId) return i;
        }
        return -1;
    }
}
