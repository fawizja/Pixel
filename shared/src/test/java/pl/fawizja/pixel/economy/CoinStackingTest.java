package pl.fawizja.pixel.economy;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.inventory.Container;
import pl.fawizja.pixel.inventory.InventoryResult;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.npc.Shop;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.npc.TradeResult;
import pl.fawizja.pixel.npc.TradeSystem;

/** Monety lacza sie same: 100 zlotych = 1 platynowa, 100 platynowych = 1 krysztalowa (jak w Tibii). */
class CoinStackingTest {

    static final int GOLD = Currency.GOLD.itemId();
    static final int PLAT = Currency.PLATINUM.itemId();
    static final int CRYSTAL = Currency.CRYSTAL.itemId();
    static final int TORCH = TestData.ITEMS.byKey("torch").id();

    private Container bp() { return Container.backpack(TestData.ITEMS); }

    private int coinSlots(Container c) {
        int n = 0;
        for (ItemStack s : c.contents()) if (Currency.isCoin(s.itemId())) n++;
        return n;
    }

    @Test
    void hundredGoldTurnsIntoPlatinumOnPickup() {
        Container c = bp();
        assertTrue(c.add(new ItemStack(GOLD, 60)).ok());
        assertTrue(c.add(new ItemStack(GOLD, 40)).ok());
        assertEquals(0, c.count(GOLD));
        assertEquals(1, c.count(PLAT));
        assertEquals(1, coinSlots(c));
        assertTrue(c.add(new ItemStack(GOLD, 75)).ok());
        assertTrue(c.add(new ItemStack(GOLD, 50)).ok());
        assertEquals(25, c.count(GOLD));
        assertEquals(2, c.count(PLAT));
        assertEquals(225, Money.totalValue(c));
        assertEquals(2, coinSlots(c), "one gold stack and one platinum stack");
    }

    @Test
    void hundredPlatinumTurnsIntoCrystalInACascade() {
        Container c = bp();
        c.add(new ItemStack(PLAT, 99));
        c.add(new ItemStack(GOLD, 99));
        assertTrue(c.add(ItemStack.of(GOLD)).ok()); // 100 zlotych -> 100. platynowa -> krysztalowa
        assertEquals(0, c.count(GOLD));
        assertEquals(0, c.count(PLAT));
        assertEquals(1, c.count(CRYSTAL));
        assertEquals(10_000, Money.totalValue(c));
        assertEquals(19, c.freeSlots());
    }

    @Test
    void crystalCoinsSimplyStack() {
        Container c = bp();
        c.add(new ItemStack(CRYSTAL, 70));
        c.add(new ItemStack(CRYSTAL, 50));
        assertEquals(120, c.count(CRYSTAL));
        assertEquals(2, coinSlots(c));
    }

    @Test
    void coinsFitAFullBackpackWhenTheyConvert() {
        Container c = bp();
        c.add(new ItemStack(GOLD, 90));
        for (int i = 0; i < 19; i++) c.add(ItemStack.of(TORCH));
        assertEquals(0, c.freeSlots());
        assertTrue(c.canAdd(GOLD, 10));
        assertTrue(c.add(new ItemStack(GOLD, 10)).ok(), "90 + 10 gold = 1 platinum in the same slot");
        assertEquals(1, c.count(PLAT));
        assertFalse(c.canAdd(GOLD, 150), "1 plat + 150 gold = 2 plat + 50 gold needs 2 slots, has 1");
        assertTrue(c.canAdd(PLAT, 5));
        // pelny plecak bez monet: miejsca brak
        Container full = bp();
        for (int i = 0; i < 20; i++) full.add(ItemStack.of(TORCH));
        assertEquals(InventoryResult.NO_SPACE, full.add(new ItemStack(GOLD, 5)));
        assertFalse(full.canAdd(GOLD, 5));
    }

    @Test
    void addThatDoesNotFitChangesNothing() {
        Container c = bp();
        c.add(new ItemStack(GOLD, 50));
        for (int i = 0; i < 19; i++) c.add(ItemStack.of(TORCH));
        // 50 + 60 = 110 gold -> 1 plat + 10 gold: 2 pola, wolne tylko 1 (zajete przez zlote) -> brak miejsca
        assertEquals(InventoryResult.NO_SPACE, c.add(new ItemStack(GOLD, 60)));
        assertEquals(50, c.count(GOLD));
        assertEquals(0, c.count(PLAT));
    }

    @Test
    void movingGoldOntoGoldConverts() {
        Container c = bp();
        c.add(new ItemStack(GOLD, 70));
        c.add(ItemStack.of(TORCH));
        // podzial stosu na dwa pola
        assertTrue(c.move(0, 5, 40).ok());
        assertEquals(30, c.get(0).count());
        assertEquals(40, c.get(5).count());
        c.putInSlot(7, new ItemStack(GOLD, 60));
        assertTrue(c.move(7, 5, 60).ok(), "60 onto 40 = 100 gold");
        assertEquals(30, c.count(GOLD));
        assertEquals(1, c.count(PLAT));
        assertEquals(130, Money.totalValue(c));
    }

    @Test
    void normalizeConvertsLegacyStacksAndKeepsValue() {
        Container c = bp();
        // stary zapis: kilka pelnych stosow zlota i platyny
        c.putInSlot(0, new ItemStack(GOLD, 100));
        c.putInSlot(3, new ItemStack(GOLD, 100));
        c.putInSlot(4, new ItemStack(GOLD, 55));
        c.putInSlot(9, new ItemStack(PLAT, 100));
        c.putInSlot(10, ItemStack.of(TORCH));
        long value = Money.totalValue(c);
        assertTrue(c.normalizeCoins());
        assertEquals(value, Money.totalValue(c));
        assertEquals(55, c.count(GOLD));
        assertEquals(2, c.count(PLAT));
        assertEquals(1, c.count(CRYSTAL));
        assertEquals(3, coinSlots(c));
        assertEquals(TORCH, c.get(10).itemId(), "other items stay where they were");
        assertFalse(c.normalizeCoins(), "already normalized");
    }

    @Test
    void payingUsesAllCoinTypesAndGivesChange() {
        Container c = bp();
        Money.add(c, 10_000 + 300 + 5); // 1 krysztalowa, 3 platynowe, 5 zlotych
        assertEquals(1, c.count(CRYSTAL));
        assertEquals(3, c.count(PLAT));
        assertEquals(5, c.count(GOLD));
        assertTrue(Money.pay(c, 450).ok()); // brak 450 bez rozmieniania
        assertEquals(10_305 - 450, Money.totalValue(c));
        assertEquals(0, c.count(CRYSTAL));
        assertEquals(98, c.count(PLAT));
        assertEquals(55, c.count(GOLD));
        assertEquals(2, coinSlots(c));
    }

    @Test
    void shopPurchaseAndSaleUseCoinsWithChange() {
        Container c = bp();
        ShopRegistry shops = ShopRegistry.loadDefault(TestData.ITEMS);
        Shop general = shops.get("varden_general");
        c.add(ItemStack.of(PLAT)); // 100 gp
        assertEquals(TradeResult.OK, TradeSystem.buy(c, TestData.ITEMS, general, TORCH, 1));
        assertEquals(1, c.count(TORCH));
        assertEquals(100 - general.offer(TORCH).buyPrice(), Money.totalValue(c));
        assertEquals(0, c.count(PLAT), "platinum was broken into gold change");
    }
}
