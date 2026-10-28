package pl.fawizja.pixel.monster;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.economy.Money;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.npc.Shop;
import pl.fawizja.pixel.npc.ShopOffer;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.npc.TradeResult;
import pl.fawizja.pixel.npc.TradeSystem;
import pl.fawizja.pixel.vocation.Vocation;

/** Zasady lupow (LootRules): zloto i wartosc rosna z poziomem, najlepsze przedmioty tylko z bossow, robaki po 1 gp. */
class LootRulesTest {

    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);

    static int id(String key) { return TestData.ITEMS.byKey(key).id(); }

    @Test
    void everyLootTableFollowsTheRules() {
        assertEquals(List.of(), LootRules.audit(REG, TestData.ITEMS));
    }

    @Test
    void noNormalMonsterOrEliteDropsBossOnlyItems() {
        assertTrue(LootRules.bossOnlyKeys().size() >= 20);
        int bossDrops = 0;
        for (MonsterType t : REG.all()) {
            for (MonsterType v : List.of(t, t.eliteVariant())) {
                for (LootEntry e : v.loot()) {
                    boolean bossOnly = LootRules.isBossOnly(TestData.ITEMS.get(e.itemId()));
                    if (bossOnly) {
                        assertTrue(v.boss(), v.key() + " drops boss-only " + TestData.ITEMS.get(e.itemId()).key());
                        assertTrue(e.chancePermille() >= 10 && e.chancePermille() <= 50, "boss-only drops are rare (1-5%)");
                        bossDrops++;
                    }
                }
            }
        }
        assertTrue(bossDrops >= LootRules.bossOnlyKeys().size(), "every boss-only item drops from some boss");
        // opis w boss_items.csv zgodny z loot.csv
        for (Map.Entry<String, List<String>> e : LootRules.bossOnlySources().entrySet()) {
            int itemId = id(e.getKey());
            assertFalse(e.getValue().isEmpty(), e.getKey());
            for (MonsterType t : REG.all()) {
                boolean drops = t.loot().stream().anyMatch(l -> l.itemId() == itemId);
                assertEquals(e.getValue().contains(t.key()), drops, e.getKey() + " / " + t.key());
            }
        }
        for (ItemType it : TestData.ITEMS.all()) {
            if (it.value() >= LootRules.LEGENDARY_VALUE && REG.all().stream().anyMatch(t -> t.loot().stream().anyMatch(l -> l.itemId() == it.id()))) {
                assertTrue(LootRules.isBossOnly(it), it.key() + " is legendary and must be boss-only");
            }
        }
    }

    @Test
    void lootValueGrowsWithMonsterLevel() {
        List<MonsterType> normal = new ArrayList<>();
        for (MonsterType t : REG.all()) if (!t.boss()) normal.add(t);
        normal.sort((a, b) -> Integer.compare(a.level(), b.level()));
        int[] bands = {0, 25, 60, 150, 300, 500, 750, Integer.MAX_VALUE};
        double prev = -1;
        for (int i = 0; i + 1 < bands.length; i++) {
            double sum = 0;
            int n = 0;
            for (MonsterType t : normal) {
                if (t.level() >= bands[i] && t.level() < bands[i + 1]) {
                    sum += LootRules.expectedGold(t, TestData.ITEMS) + LootRules.expectedItemValue(t, TestData.ITEMS);
                    n++;
                }
            }
            assertTrue(n > 0, "monsters in band " + bands[i]);
            double avg = sum / n;
            assertTrue(avg > prev, "average loot grows: band " + bands[i] + " " + avg + " <= " + prev);
            prev = avg;
        }
        // slabe potwory: malo zlota i tanie przedmioty, mocne: duzo wiecej
        MonsterType rat = REG.get("sewer_rat");
        MonsterType lord = REG.get("abyss_lord");
        assertTrue(LootRules.expectedGold(rat, TestData.ITEMS) < 10);
        assertTrue(LootRules.expectedGold(lord, TestData.ITEMS) > 100 * LootRules.expectedGold(rat, TestData.ITEMS));
        for (LootEntry e : rat.loot()) assertTrue(TestData.ITEMS.get(e.itemId()).value() <= 10);
        // bossowie daja wiecej niz zwykle potwory na swoim poziomie
        for (MonsterType b : REG.all()) {
            if (b.boss()) assertTrue(LootRules.expectedGold(b, TestData.ITEMS) >= 8 * LootRules.targetGold(b.level()), b.key());
        }
    }

    @Test
    void rarityFallsAsValueRises() {
        assertEquals(1000, LootRules.maxChancePermille(5, false));
        assertTrue(LootRules.maxChancePermille(10_000, false) < LootRules.maxChancePermille(1_000, false));
        assertTrue(LootRules.maxChancePermille(500_000, false) < LootRules.maxChancePermille(10_000, false));
        assertTrue(LootRules.bossOnlyMaxPermille(900_000) < LootRules.bossOnlyMaxPermille(60_000));
        assertTrue(LootRules.maxItemValue(10) < LootRules.maxItemValue(500));
    }

    @Test
    void bossLootIsRolledSeparatelyForEveryPlayer() {
        MonsterType boss = REG.get("morgath");
        List<String> party = List.of("Ala", "Bartek", "Celina", "Darek", "Ewa");
        Map<String, List<ItemStack>> rolls = LootRules.rollBossLootPerPlayer(boss, TestData.ITEMS, party, new Random(7));
        assertEquals(party, new ArrayList<>(rolls.keySet()));
        for (List<ItemStack> r : rolls.values()) assertFalse(r.isEmpty(), "everyone gets gold at least");
        assertNotEquals(rolls.get("Ala"), rolls.get("Bartek"), "independent rolls");
        assertThrows(IllegalArgumentException.class, () -> LootRules.rollBossLootForPlayer(REG.get("wolf"), TestData.ITEMS, new Random(1)));

        // rzadkosc: korona Otchlani ~1% na gracza
        int crown = id("crown_of_the_abyss");
        Random rng = new Random(3);
        int hits = 0, n = 20_000;
        for (int i = 0; i < n; i++) {
            for (ItemStack s : LootRules.rollBossLootForPlayer(boss, TestData.ITEMS, rng)) if (s.itemId() == crown) hits++;
        }
        double rate = hits / (double) n;
        assertTrue(rate > 0.006 && rate < 0.014, "crown rate " + rate);
    }

    @Test
    void wormsCostOneGoldEachAtEveryBackpackSeller() {
        ShopRegistry shops = ShopRegistry.loadDefault(TestData.ITEMS);
        int worm = id("worm"), backpack = id("backpack"), rod = id("fishing_rod");
        int sellers = 0;
        for (Shop s : shops.all()) {
            ShopOffer b = s.offer(backpack);
            if (b == null || !b.sold()) continue;
            sellers++;
            ShopOffer w = s.offer(worm);
            assertNotNull(w, s.key() + " sells backpacks, so it sells worms");
            assertEquals(1, w.buyPrice(), s.key() + ": worm costs 1 gp");
            assertNotNull(s.offer(rod), s.key() + " sells a fishing rod");
            assertTrue(s.offer(rod).sold());
        }
        assertTrue(sellers >= 5);
        for (Shop s : shops.all()) if (s.offer(worm) != null && s.offer(worm).sold()) assertEquals(1, s.offer(worm).buyPrice(), s.key());

        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        Shop shop = shops.get("varden_general");
        long before = Money.totalValue(pc.backpack());
        int had = pc.backpack().count(worm);
        assertEquals(TradeResult.OK, TradeSystem.buy(pc.backpack(), TestData.ITEMS, shop, worm, 37));
        assertEquals(before - 37, Money.totalValue(pc.backpack()), "37 worms cost 37 gp");
        assertEquals(had + 37, pc.backpack().count(worm));
    }
}
