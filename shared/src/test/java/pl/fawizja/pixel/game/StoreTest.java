package pl.fawizja.pixel.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.item.Enchantments;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.npc.NpcRegistry;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.WorldLayout;
import pl.fawizja.pixel.world.WorldMap;

/** Sklep z ramki: tylko istniejace uslugi, ceny jak u NPC, zloto z konta w banku, bez chodzenia do NPC. */
class StoreTest {

    static final WorldMap WORLD = TestData.world();

    static GameSession session(PlayerCharacter pc) throws Exception {
        ShopRegistry shops = ShopRegistry.loadDefault(TestData.ITEMS);
        NpcRegistry npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, WORLD, shops);
        GameSession s = new GameSession(TestData.ITEMS, WORLD, pc, new MonsterManager(WORLD, null, List.of(), new Random(1)),
                new Random(2), 8);
        s.setTownServices(npcs, shops, null);
        return s;
    }

    @Test
    void offersOnlyExistingServicesAtNpcPrices() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        GameSession s = session(pc);
        Store.Offer premium = Store.find(s, "premium"), promote = Store.find(s, "promote"), bless = Store.find(s, "bless");
        assertEquals(GameSession.PREMIUM_PRICE, premium.price());
        assertEquals(GameSession.PROMOTION_PRICE, promote.price());
        assertEquals(Balance.blessingPrice(pc.level()), bless.price());
        assertEquals("najpierw konto premium", promote.blocked());
        assertEquals(Enchantments.price(Enchantments.Kind.CRIT, pc.level()), Store.find(s, "enchant_crit").price());
        assertEquals(50_000, Store.find(s, "saddle").price(), "price of the merchants (shops.csv)");
        for (String pet : Store.PETS) assertEquals(5_000, Store.find(s, pet).price(), pet);
        assertNull(Store.find(s, "enchant_exp"), "the scroll is not a smith service");
        for (Store.Offer o : Store.offers(s)) assertTrue(o.price() > 0, o.key());
    }

    @Test
    void buyingPaysFromTheBankAndNeedsNoNpc() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        pc.addExperience(Balance.experienceForLevel(25) - pc.experience());
        pc.bank().debit(pc.bank().balance());
        GameSession s = session(pc);
        assertTrue(s.npcNearby() == null || s.npcNearby().role() != pl.fawizja.pixel.npc.NpcRole.KING, "not at the king");

        assertFalse(s.storeBuy("premium", 0), "no gold in the bank");
        assertFalse(pc.isPremium());
        pc.bank().credit(GameSession.PREMIUM_PRICE + GameSession.PROMOTION_PRICE + 60_000);
        assertFalse(s.storeBuy("promote", 1), "promotion needs premium");
        assertTrue(Actions.apply(s, "store:premium", 2));
        assertTrue(pc.isPremium());
        assertFalse(s.storeBuy("premium", 3), "only once");
        assertTrue(s.storeBuy("promote", 4));
        assertTrue(pc.isPromoted());
        assertEquals(60_000, pc.bank().balance());

        int saddle = TestData.ITEMS.byKey("saddle").id();
        long coins = pl.fawizja.pixel.economy.Money.totalValue(pc.backpack());
        assertTrue(s.storeBuy("saddle", 5));
        assertEquals(1, pc.backpack().count(saddle));
        assertEquals(10_000, pc.bank().balance());
        assertEquals(coins, pl.fawizja.pixel.economy.Money.totalValue(pc.backpack()), "backpack coins untouched");
        assertFalse(s.storeBuy("saddle", 6), "already in the backpack");

        assertTrue(s.storeBuy("enchant_life", 7));
        assertTrue(pc.enchantments().remainingMs(Enchantments.Kind.LIFE) > 0);
        assertFalse(s.storeBuy("nothing", 8));
    }
}
