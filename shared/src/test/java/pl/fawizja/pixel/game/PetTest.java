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
    void petKeepersSellTheirTownsPetAndGeneralShopsNoLonger() {
        pl.fawizja.pixel.npc.ShopRegistry shops = pl.fawizja.pixel.npc.ShopRegistry.loadDefault(TestData.ITEMS);
        String[][] sold = {{"varden_pets", "pet_dog"}, {"selv_pets", "pet_cat"}, {"frost_pets", "pet_raven"}, {"qas_pets", "pet_fennec"},
                {"valmir_pets", "pet_gull"}, {"nerys_pets", "pet_parrot"}};
        for (String[] e : sold) {
            assertEquals(1, TestData.ITEMS.byKey(e[1]).maxStack());
            assertNotNull(shops.get(e[0]).offer(TestData.ITEMS.byKey(e[1]).id()), e[0] + " sells " + e[1]);
            assertEquals(1, shops.get(e[0]).offers().size(), e[0] + " sells only its pet");
            assertTrue(TestData.ITEMS.byKey(e[1]).description().contains("+5% doświadczenia"), e[1]);
        }
        for (pl.fawizja.pixel.npc.Shop sh : shops.all()) {
            if (sh.key().endsWith("_pets")) continue;
            for (pl.fawizja.pixel.npc.ShopOffer o : sh.offers()) {
                assertFalse(TestData.ITEMS.get(o.itemId()).key().startsWith("pet_"), sh.key() + " still sells a pet");
            }
        }
    }

    @Test
    void aFollowingPetGivesFivePercentMoreMonsterExperience() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        GameSession s = new GameSession(TestData.ITEMS, TestData.world(), pc);
        pl.fawizja.pixel.monster.MonsterType wolf = pl.fawizja.pixel.monster.MonsterRegistry.loadDefault(TestData.ITEMS).get("wolf");
        long plain = s.combat().killExperience(wolf, pc.level());
        assertEquals("exp " + pl.fawizja.pixel.balance.Balance.rateLabel(pl.fawizja.pixel.balance.Balance.expRate(pc.level())),
                pl.fawizja.pixel.character.SkillSheet.expRateLabel(pc));
        int gull = TestData.ITEMS.byKey("pet_gull").id();
        pc.backpack().add(new ItemStack(gull, 1));
        s.useSlot(slotOf(pc, gull), 1000);
        assertEquals("pet_gull", pc.pet(), "the new pets work like the old ones");
        assertEquals(5, pc.petExpPercent());
        assertEquals(plain * 105 / 100, s.combat().killExperience(wolf, pc.level()), "+5% after the server stages");
        assertTrue(pl.fawizja.pixel.character.SkillSheet.expRateLabel(pc).endsWith(", pupil +5%"));
        assertTrue(s.log().latest(5).stream().anyMatch(e -> e.text().contains("+5% doświadczenia")), "the summon message names the bonus");
        s.useSlot(slotOf(pc, gull), 1100);
        assertEquals(plain, s.combat().killExperience(wolf, pc.level()), "no pet, no bonus");
    }

    private static int slotOf(PlayerCharacter pc, int itemId) {
        for (int i = 0; i < pc.backpack().size(); i++) {
            ItemStack st = pc.backpack().get(i);
            if (st != null && st.itemId() == itemId) return i;
        }
        return -1;
    }
}
