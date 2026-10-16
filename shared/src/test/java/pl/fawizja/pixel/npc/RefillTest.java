package pl.fawizja.pixel.npc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.economy.Money;
import pl.fawizja.pixel.game.Actions;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.vocation.Vocation;

class RefillTest {
    @BeforeAll
    static void load() throws Exception {
        TownServicesTest.load();
    }

    @Test
    void oneTapTopsUpPotionsToTwentyAsFarAsMoneyAllows() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        GameSession s = new TownServicesTest().session(pc, List.of());
        int hp = TestData.ITEMS.byKey("health_potion").id();
        int mp = TestData.ITEMS.byKey("mana_potion").id();
        assertEquals(0, s.refillSupplies(1000), "only at a merchant");
        s.teleport(TownServicesTest.talkSpot(TownServicesTest.npcs.get("varden_merchant")), 1100);
        int haveHp = pc.backpack().count(hp);
        assertTrue(haveHp < GameSession.REFILL_TO);
        assertTrue(Actions.apply(s, "npc:refill", 1200));
        assertEquals(GameSession.REFILL_TO, pc.backpack().count(hp));
        assertEquals(GameSession.REFILL_TO, pc.backpack().count(mp));
        assertEquals(0, s.refillSupplies(1300), "already full");

        // bez monet: nic nie kupuje
        pc.backpack().remove(hp, 10);
        long coins = Money.totalValue(pc.backpack());
        assertTrue(Money.pay(pc.backpack(), coins).ok());
        assertEquals(0, s.refillSupplies(1400));
        assertEquals(GameSession.REFILL_TO - 10, pc.backpack().count(hp));
    }

    @Test
    void carriedAmmunitionIsToppedUpToOneHundred() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        GameSession s = new TownServicesTest().session(pc, List.of());
        int spear = TestData.ITEMS.byKey("spear").id();
        pl.fawizja.pixel.item.ItemStack worn = pc.equipment().get(pl.fawizja.pixel.item.EquipSlot.AMMO);
        int wornSpears = worn != null && worn.itemId() == spear ? worn.count() : 0;
        pc.backpack().add(new pl.fawizja.pixel.item.ItemStack(spear, 30));
        s.teleport(TownServicesTest.talkSpot(TownServicesTest.npcs.get("varden_merchant")), 1100);
        assertTrue(s.refillSupplies(1200) > 0);
        assertEquals(GameSession.REFILL_AMMO_TO, pc.backpack().count(spear) + wornSpears);
    }

    @Test
    void aStrongDruidGetsTheStrongestManaPotionsTheShopSells() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        pc.addExperience(10_000_000);
        GameSession s = new TownServicesTest().session(pc, List.of());
        pc.backpack().add(new pl.fawizja.pixel.item.ItemStack(TestData.ITEMS.byKey("platinum_coin").id(), 100));
        int great = TestData.ITEMS.byKey("great_mana_potion").id();
        s.teleport(TownServicesTest.talkSpot(TownServicesTest.npcs.get("varden_mage")), 1100);
        assertTrue(s.refillSupplies(1200) > 0);
        assertEquals(GameSession.REFILL_TO, pc.backpack().count(great), "druids may drink great mana potions");
        assertEquals(0, pc.backpack().count(TestData.ITEMS.byKey("great_health_potion").id()), "but not great health ones");
    }
}
