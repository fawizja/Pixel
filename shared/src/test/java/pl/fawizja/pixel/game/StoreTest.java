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
        assertEquals(s.bulkBlessingPrice(), bless.price());
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

    @Test
    void tabsSplitOffersAndPremiumExplainsWhatItGives() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        GameSession s = session(pc);
        // kazda oferta w dokladnie jednej zakladce; premium i promocja w Premium, reszta w Uslugach
        int total = 0;
        for (Store.Tab t : Store.Tab.values()) total += Store.offers(s, t).size();
        assertEquals(Store.offers(s).size(), total);
        assertEquals(List.of("premium", "promote"), Store.offers(s, Store.Tab.PREMIUM).stream().map(Store.Offer::key).toList());
        assertTrue(Store.offers(s, Store.Tab.SERVICES).stream().anyMatch(o -> o.key().equals("bless")));
        assertTrue(Store.offers(s, Store.Tab.OUTFITS).isEmpty(), "stroje nie sa ofertami");

        for (Store.Tab tab : Store.Tab.values()) {
            List<String[]> rows = Store.rows(s, tab);
            // najpierw trzy zakladki, wybrana oznaczona
            for (int i = 0; i < Store.Tab.values().length; i++) {
                Store.Tab t = Store.Tab.values()[i];
                assertEquals(t.label(), rows.get(i)[0]);
                assertEquals(Store.TAB_ACTION + t.key, rows.get(i)[3]);
                assertEquals(t == tab ? "tab_on" : "tab", rows.get(i)[4]);
            }
            assertEquals(tab, Store.Tab.byKey(tab.key));
        }
        assertEquals(List.of("Usługi", "Premium", "Outfity"), java.util.Arrays.stream(Store.Tab.values()).map(t -> t.label()).toList());
        assertEquals(Store.Tab.SERVICES, Store.Tab.byKey("cos"));

        List<String[]> prem = Store.rows(s, Store.Tab.PREMIUM);
        assertTrue(prem.stream().anyMatch(r -> r[3].equals("store:ask:premium") && r[1].equals(String.format("%,d gp", GameSession.PREMIUM_PRICE))),
                "konto premium do kupienia w zakladce, cena jak u wladcy");
        assertTrue(prem.stream().anyMatch(r -> r[0].equals("Rejsy statkami") && r.length > 4 && r[4].equals("info")), "opis premium jako tekst");
        assertTrue(prem.stream().anyMatch(r -> r[0].equals("Twoje konto: darmowe")));
        assertTrue(Store.rows(s, Store.Tab.SERVICES).stream().noneMatch(r -> r[3].equals("store:ask:premium")));

        pc.bank().credit(GameSession.PREMIUM_PRICE);
        assertTrue(s.storeBuy("premium", 0), "zakup z zakladki - ta sama logika co dotad");
        assertTrue(Store.rows(s, Store.Tab.PREMIUM).stream().anyMatch(r -> r[0].equals("Twoje konto: premium")));
    }

    @Test
    void outfitsTabListsFreeColoursAndFutureOutfitsFromData() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        GameSession s = session(pc);
        List<String[]> rows = Store.rows(s, Store.Tab.OUTFITS);
        int colours = 0;
        for (String[] r : rows) if (r[3].startsWith("outfit:")) colours++;
        assertEquals(pl.fawizja.pixel.character.Outfit.count(), colours, "wszystkie kolory stroju za darmo");
        assertTrue(rows.stream().anyMatch(r -> r[3].equals("outfit:0") && r[1].equals("noszony teraz")));
        List<StoreOutfits.Entry> data = StoreOutfits.all();
        assertFalse(data.isEmpty(), "stroje na przyszlosc z data/store_outfits.csv");
        for (StoreOutfits.Entry e : data) {
            assertTrue(rows.stream().anyMatch(r -> r[3].equals("store:soon:" + e.key())), e.key());
            assertSame(e, StoreOutfits.find(e.key()));
        }
        assertTrue(rows.stream().filter(r -> r[3].startsWith("store:soon:")).allMatch(r -> r[0].endsWith("(wkrótce)")), "na razie wszystkie wkrotce");
        // nowy wiersz w pliku = nowy stroj; bledne wiersze pomijane
        List<StoreOutfits.Entry> parsed = StoreOutfits.parse(new java.io.BufferedReader(new java.io.StringReader(
                "# komentarz\nknight;Rycerz;zbroja;1000;dostepny\nzly;Zly;opis;abc;wkrotce\nkrotki;Krotki\n")));
        assertEquals(1, parsed.size());
        assertEquals(new StoreOutfits.Entry("knight", "Rycerz", "zbroja", 1000, false), parsed.get(0));
    }
}
