package pl.fawizja.pixel.economy;

import pl.fawizja.pixel.inventory.Container;
import pl.fawizja.pixel.inventory.InventoryResult;

/** Konto bankowe postaci. Wplaty i wyplaty sa atomowe razem z plecakiem. */
public final class BankAccount {

    private long balance;

    public BankAccount(long balance) {
        if (balance < 0) throw new IllegalArgumentException("negative balance");
        this.balance = balance;
    }

    public long balance() { return balance; }

    public InventoryResult deposit(Container backpack, long amount) {
        if (amount <= 0) return InventoryResult.INVALID_AMOUNT;
        long newBalance;
        try {
            newBalance = Math.addExact(balance, amount);
        } catch (ArithmeticException e) {
            return InventoryResult.INVALID_AMOUNT;
        }
        InventoryResult r = Money.pay(backpack, amount);
        if (r.ok()) balance = newBalance;
        return r;
    }

    public InventoryResult depositAll(Container backpack) {
        long total = Money.totalValue(backpack);
        return total == 0 ? InventoryResult.INVALID_AMOUNT : deposit(backpack, total);
    }

    public InventoryResult withdraw(Container backpack, long amount) {
        if (amount <= 0) return InventoryResult.INVALID_AMOUNT;
        if (amount > balance) return InventoryResult.INSUFFICIENT_FUNDS;
        InventoryResult r = Money.add(backpack, amount);
        if (r.ok()) balance -= amount;
        return r;
    }

    /** Przelew miedzy kontami (np. handel bankowy). */
    public InventoryResult transferTo(BankAccount other, long amount) {
        if (other == this || amount <= 0) return InventoryResult.INVALID_AMOUNT;
        if (amount > balance) return InventoryResult.INSUFFICIENT_FUNDS;
        long target;
        try {
            target = Math.addExact(other.balance, amount);
        } catch (ArithmeticException e) {
            return InventoryResult.INVALID_AMOUNT;
        }
        balance -= amount;
        other.balance = target;
        return InventoryResult.OK;
    }
}
