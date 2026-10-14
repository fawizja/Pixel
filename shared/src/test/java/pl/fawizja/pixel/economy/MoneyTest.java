package pl.fawizja.pixel.economy;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.inventory.Container;
import pl.fawizja.pixel.inventory.InventoryResult;
import pl.fawizja.pixel.item.ItemStack;

class MoneyTest {

    private Container bp() { return Container.backpack(TestData.ITEMS); }

    @Test
    void conversionRates() {
        assertEquals(100, Currency.PLATINUM.value() / Currency.GOLD.value());
        assertEquals(100, Currency.CRYSTAL.value() / Currency.PLATINUM.value());
        assertArrayEquals(new long[] {1, 23, 45}, Money.breakdown(12_345));
    }

    @Test
    void exchangeUpAndDownKeepsValue() {
        Container c = bp();
        c.add(new ItemStack(Currency.GOLD.itemId(), 100));
        assertTrue(Money.exchangeUp(c, Currency.GOLD).ok());
        assertEquals(1, c.count(Currency.PLATINUM.itemId()));
        assertEquals(0, c.count(Currency.GOLD.itemId()));
        assertTrue(Money.exchangeDown(c, Currency.PLATINUM).ok());
        assertEquals(100, c.count(Currency.GOLD.itemId()));
        assertEquals(100, Money.totalValue(c));
        assertEquals(InventoryResult.NOT_ENOUGH_ITEMS, Money.exchangeUp(c, Currency.PLATINUM));
        assertEquals(InventoryResult.INVALID_AMOUNT, Money.exchangeDown(c, Currency.GOLD));
    }

    @Test
    void payMakesChangeAndNeverLosesMoney() {
        Container c = bp();
        assertTrue(Money.add(c, 10_000).ok());
        assertEquals(1, c.count(Currency.CRYSTAL.itemId()));
        assertTrue(Money.pay(c, 1).ok());
        assertEquals(9_999, Money.totalValue(c));
        assertEquals(99, c.count(Currency.PLATINUM.itemId()));
        assertEquals(99, c.count(Currency.GOLD.itemId()));
        assertEquals(InventoryResult.INSUFFICIENT_FUNDS, Money.pay(c, 10_000));
        assertEquals(9_999, Money.totalValue(c));
        assertTrue(Money.pay(c, 9_999).ok());
        assertEquals(0, Money.totalValue(c));
        assertEquals(20, c.freeSlots());
    }

    @Test
    void payFailsAtomicallyWhenChangeDoesNotFit() {
        Container c = bp();
        c.add(ItemStack.of(Currency.CRYSTAL.itemId()));
        for (int i = 0; i < 19; i++) c.add(ItemStack.of(32)); // pochodnie
        // reszta 9999 wymaga 2 slotow, a wolny bedzie tylko 1
        assertEquals(InventoryResult.NO_SPACE, Money.pay(c, 1));
        assertEquals(10_000, Money.totalValue(c));
        assertEquals(1, c.count(Currency.CRYSTAL.itemId()));
    }

    @Test
    void addLargeAmountsUsesCrystalStacks() {
        Container c = bp();
        assertTrue(Money.add(c, 2_500_000).ok());
        assertEquals(250, c.count(Currency.CRYSTAL.itemId()));
        assertEquals(17, c.freeSlots());
        assertEquals(2_500_000, Money.totalValue(c));
    }

    @Test
    void bankDepositWithdrawAndTransfer() {
        Container c = bp();
        Money.add(c, 10_000);
        BankAccount bank = new BankAccount(0);
        assertTrue(bank.deposit(c, 2_500).ok());
        assertEquals(2_500, bank.balance());
        assertEquals(7_500, Money.totalValue(c));
        assertEquals(InventoryResult.INSUFFICIENT_FUNDS, bank.deposit(c, 100_000));
        assertEquals(InventoryResult.INSUFFICIENT_FUNDS, bank.withdraw(c, 3_000));
        assertTrue(bank.withdraw(c, 500).ok());
        assertEquals(2_000, bank.balance());
        assertEquals(8_000, Money.totalValue(c));
        BankAccount other = new BankAccount(0);
        assertTrue(bank.transferTo(other, 2_000).ok());
        assertEquals(0, bank.balance());
        assertEquals(2_000, other.balance());
        assertEquals(InventoryResult.INVALID_AMOUNT, bank.withdraw(c, -5));
        assertEquals(InventoryResult.INVALID_AMOUNT, bank.transferTo(bank, 1));
    }

    @Test
    void withdrawIntoFullBackpackKeepsBalance() {
        Container c = bp();
        for (int i = 0; i < 20; i++) c.add(ItemStack.of(32));
        BankAccount bank = new BankAccount(500);
        assertEquals(InventoryResult.NO_SPACE, bank.withdraw(c, 100));
        assertEquals(500, bank.balance());
    }

    @Test
    void bankOverflowIsRejected() {
        Container c = bp();
        Money.add(c, 100);
        BankAccount bank = new BankAccount(Long.MAX_VALUE - 10);
        assertEquals(InventoryResult.INVALID_AMOUNT, bank.deposit(c, 100));
        assertEquals(100, Money.totalValue(c));
    }
}
