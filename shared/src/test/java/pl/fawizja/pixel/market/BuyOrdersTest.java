package pl.fawizja.pixel.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class BuyOrdersTest {

    @Test
    void ordersAreLimitedCheckedAndSaved() {
        BuyOrders b = new BuyOrders();
        assertEquals(BuyOrders.Result.BAD_PRICE, b.canPlace("Ola", 5, 0));
        assertEquals(BuyOrders.Result.BAD_PRICE, b.canPlace("Ola", 101, 100));
        for (int i = 0; i < BuyOrders.MAX_PER_BUYER; i++) b.place("Ola", 10, 5, 1000 + i, i);
        assertEquals(BuyOrders.Result.TOO_MANY, b.canPlace("ola", 1, 100));
        assertEquals(BuyOrders.Result.OWN_ORDER, b.checkFill(1, "OLA"));
        assertEquals(BuyOrders.Result.OK, b.checkFill(1, "Ula"));
        assertEquals(BuyOrders.Result.NOT_YOURS, b.checkCancel(1, "Ula"));
        assertEquals(BuyOrders.Result.NO_ORDER, b.checkFill(99, "Ula"));
        b.remove(2);
        BuyOrders back = BuyOrders.decode(b.encode() + "O zepsute\n", 0);
        assertEquals(4, back.size());
        assertNull(back.get(2));
        assertEquals(1004, back.get(5).price());
        assertEquals(6, back.place("Ula", 1, 1, 10, 100).id(), "ids continue after loading");
        assertEquals(5, back.newestFirst().get(1).id());
        assertEquals(4, back.get(5).createdMs(), "date saved");
    }

    @Test
    void oldOrdersExpireAndOldFilesStillLoad() {
        BuyOrders b = BuyOrders.decode("N 4\nO 3;10;5;900;Ola\n", 1_000);
        assertEquals("Ola", b.get(3).buyer());
        assertEquals(1_000, b.get(3).createdMs(), "undated line counts from loading");
        b.place("Ula", 10, 1, 50, 2_000);
        assertEquals(0, b.expire(1_000 + BuyOrders.EXPIRE_MS - 1).size());
        java.util.List<BuyOrders.Order> gone = b.expire(1_000 + BuyOrders.EXPIRE_MS);
        assertEquals(1, gone.size());
        assertEquals(900, gone.get(0).price());
        assertEquals(1, b.size());
    }
}
