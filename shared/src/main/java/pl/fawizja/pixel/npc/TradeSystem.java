package pl.fawizja.pixel.npc;

import pl.fawizja.pixel.economy.Money;
import pl.fawizja.pixel.inventory.Container;
import pl.fawizja.pixel.inventory.InventoryResult;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;

/** Atomowy handel z kupcem: na kopii plecaka, zatwierdzany tylko w calosci. */
public final class TradeSystem {

    /** Najwieksza ilosc w jednej transakcji kupna. */
    public static final int MAX_BUY = 100;

    private TradeSystem() {}

    public static long cost(ShopOffer o, int count) { return o.buyPrice() * count; }

    /** Kupno count sztuk; placi monetami z plecaka (z reszta). */
    public static TradeResult buy(Container backpack, ItemRegistry items, Shop shop, int itemId, int count) {
        if (count < 1 || count > MAX_BUY) return TradeResult.INVALID_AMOUNT;
        ShopOffer o = shop.offer(itemId);
        if (o == null || !o.sold()) return TradeResult.NOT_SOLD_HERE;
        if (Money.totalValue(backpack) < cost(o, count)) return TradeResult.NO_MONEY;
        Container tx = backpack.copy();
        InventoryResult paid = Money.pay(tx, cost(o, count));
        if (!paid.ok()) return paid == InventoryResult.INSUFFICIENT_FUNDS ? TradeResult.NO_MONEY : TradeResult.NO_SPACE;
        ItemType t = items.get(itemId);
        int left = count;
        while (left > 0) {
            int chunk = t.stackable() ? Math.min(left, t.maxStack()) : 1;
            if (!tx.add(new ItemStack(itemId, chunk)).ok()) return TradeResult.NO_SPACE;
            left -= chunk;
        }
        backpack.commitFrom(tx);
        return TradeResult.OK;
    }

    /** Sprzedaz wszystkich sztuk przedmiotu z plecaka (wyposazenie zalozone nie jest ruszane). */
    public static TradeResult sellAll(Container backpack, Shop shop, int itemId) {
        ShopOffer o = shop.offer(itemId);
        if (o == null || !o.bought()) return TradeResult.NOT_BOUGHT_HERE;
        int have = backpack.countPlain(itemId); // ulepszonych nie sprzedajemy hurtem
        if (have == 0) return TradeResult.NOTHING_TO_SELL;
        Container tx = backpack.copy();
        if (!tx.remove(itemId, have).ok()) return TradeResult.NOTHING_TO_SELL;
        if (!Money.add(tx, o.sellPrice() * have).ok()) return TradeResult.NO_SPACE;
        backpack.commitFrom(tx);
        return TradeResult.OK;
    }

    /** Wartosc skupu wszystkich sztuk w plecaku (0, gdy kupiec nie skupuje). */
    public static long sellValue(Container backpack, Shop shop, int itemId) {
        ShopOffer o = shop.offer(itemId);
        return o == null || !o.bought() ? 0 : o.sellPrice() * backpack.countPlain(itemId);
    }
}
