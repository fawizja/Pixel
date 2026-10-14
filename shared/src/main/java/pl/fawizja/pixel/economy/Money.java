package pl.fawizja.pixel.economy;

import pl.fawizja.pixel.inventory.Container;
import pl.fawizja.pixel.inventory.InventoryResult;
import pl.fawizja.pixel.item.ItemStack;

/**
 * Operacje na monetach w pojemniku. Wszystkie sa transakcyjne: liczone na kopii plecaka
 * i zatwierdzane dopiero, gdy calosc sie powiedzie, wiec pieniadze nie gina i sie nie mnoza.
 */
public final class Money {

    private Money() {}

    /** Laczna wartosc monet w gp. */
    public static long totalValue(Container c) {
        long sum = 0;
        for (Currency cur : Currency.values()) {
            sum = Math.addExact(sum, Math.multiplyExact((long) c.count(cur.itemId()), cur.value()));
        }
        return sum;
    }

    /** Rozklad kwoty na najmniejsza liczbe monet: [crystal, platinum, gold]. */
    public static long[] breakdown(long amount) {
        if (amount < 0) throw new IllegalArgumentException("negative amount");
        long crystal = amount / Currency.CRYSTAL.value();
        long rest = amount % Currency.CRYSTAL.value();
        long platinum = rest / Currency.PLATINUM.value();
        long gold = rest % Currency.PLATINUM.value();
        return new long[] {crystal, platinum, gold};
    }

    /** Dodaje kwote w najwyzszych nominalach albo nic, gdy nie ma miejsca. */
    public static InventoryResult add(Container c, long amount) {
        if (amount < 0) return InventoryResult.INVALID_AMOUNT;
        if (amount == 0) return InventoryResult.OK;
        Container tx = c.copy();
        InventoryResult r = addCoins(tx, amount);
        if (r.ok()) c.commitFrom(tx);
        return r;
    }

    private static InventoryResult addCoins(Container tx, long amount) {
        long[] parts = breakdown(amount);
        Currency[] order = {Currency.CRYSTAL, Currency.PLATINUM, Currency.GOLD};
        for (int i = 0; i < order.length; i++) {
            long n = parts[i];
            while (n > 0) {
                int chunk = (int) Math.min(n, 100);
                InventoryResult r = tx.add(new ItemStack(order[i].itemId(), chunk));
                if (!r.ok()) return r;
                n -= chunk;
            }
        }
        return InventoryResult.OK;
    }

    /**
     * Placi kwote monetami z pojemnika, wydajac reszte. Jesli brakuje pieniedzy albo
     * miejsca na reszte, pojemnik pozostaje bez zmian.
     */
    public static InventoryResult pay(Container c, long amount) {
        if (amount < 0) return InventoryResult.INVALID_AMOUNT;
        if (amount == 0) return InventoryResult.OK;
        long total = totalValue(c);
        if (total < amount) return InventoryResult.INSUFFICIENT_FUNDS;
        Container tx = c.copy();
        // Najpierw sprobuj zaplacic bez rozmieniania (gold -> platinum -> crystal),
        // zeby nie przestawiac niepotrzebnie monet w plecaku.
        long remaining = amount;
        for (Currency cur : new Currency[] {Currency.GOLD, Currency.PLATINUM, Currency.CRYSTAL}) {
            long need = remaining / cur.value();
            long have = tx.count(cur.itemId());
            long take = Math.min(need, have);
            if (take > 0) {
                tx.remove(cur.itemId(), (int) take);
                remaining -= take * cur.value();
            }
        }
        if (remaining > 0) {
            // Potrzebna rozmiana: zabierz wszystkie monety i oddaj reszte optymalnie.
            long left = totalValue(tx) - remaining;
            for (Currency cur : Currency.values()) {
                int n = tx.count(cur.itemId());
                if (n > 0) tx.remove(cur.itemId(), n);
            }
            InventoryResult r = addCoins(tx, left);
            if (!r.ok()) return r;
        }
        if (totalValue(tx) != total - amount) throw new IllegalStateException("money invariant broken");
        c.commitFrom(tx);
        return InventoryResult.OK;
    }

    /** Wymienia 100 monet danego nominalu na 1 monete wyzszego nominalu. */
    public static InventoryResult exchangeUp(Container c, Currency from) {
        if (from == Currency.CRYSTAL) return InventoryResult.INVALID_AMOUNT;
        if (c.count(from.itemId()) < Currency.EXCHANGE_RATE) return InventoryResult.NOT_ENOUGH_ITEMS;
        Currency to = Currency.values()[from.ordinal() + 1];
        Container tx = c.copy();
        tx.remove(from.itemId(), Currency.EXCHANGE_RATE);
        InventoryResult r = tx.add(ItemStack.of(to.itemId()));
        if (r.ok()) c.commitFrom(tx);
        return r;
    }

    /** Rozmienia 1 monete na 100 monet nizszego nominalu. */
    public static InventoryResult exchangeDown(Container c, Currency from) {
        if (from == Currency.GOLD) return InventoryResult.INVALID_AMOUNT;
        if (c.count(from.itemId()) < 1) return InventoryResult.NOT_ENOUGH_ITEMS;
        Currency to = Currency.values()[from.ordinal() - 1];
        Container tx = c.copy();
        tx.remove(from.itemId(), 1);
        InventoryResult r = tx.add(new ItemStack(to.itemId(), Currency.EXCHANGE_RATE));
        if (r.ok()) c.commitFrom(tx);
        return r;
    }
}
