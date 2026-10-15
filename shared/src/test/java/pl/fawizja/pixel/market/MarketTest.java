package pl.fawizja.pixel.market;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.economy.BankAccount;
import pl.fawizja.pixel.inventory.InventoryResult;

class MarketTest {

    @Test
    void listingsLimitsBuyingAndCancelling() {
        Market m = new Market();
        assertEquals(Market.Result.BAD_PRICE, m.canList("Ola", 0));
        assertEquals(Market.Result.BAD_PRICE, m.canList("Ola", Market.MAX_PRICE + 1));
        for (int i = 0; i < Market.MAX_PER_SELLER; i++) {
            assertEquals(Market.Result.OK, m.canList("Ola", 100));
            m.add("Ola", 10, 1, 100);
        }
        assertEquals(Market.Result.TOO_MANY, m.canList("ola", 100));
        assertEquals(Market.Result.OK, m.canList("Ula", 100));
        Market.Listing l = m.add("Ula", 11, 5, 1000);
        assertEquals(Market.Result.OWN, m.checkBuy(l.id(), "ULA"));
        assertEquals(Market.Result.NOT_OWNER, m.checkCancel(l.id(), "Ola"));
        assertEquals(Market.Result.OK, m.checkBuy(l.id(), "Ola"));
        assertEquals(l, m.sold(l.id()));
        assertEquals(Market.Result.NOT_FOUND, m.checkBuy(l.id(), "Ola"));
        assertEquals(l.id(), m.newestFirst().size() + 1, "ids keep growing");
        m.addEarnings("Ula", 980);
        m.addEarnings("ula", 20);
        assertEquals(1000, m.takeEarnings("ULA"));
        assertEquals(0, m.takeEarnings("Ula"), "paid once");
        assertEquals(20, Market.fee(1000));
    }

    @Test
    void theMarketSurvivesARestart() {
        Market m = new Market();
        m.add("Stary Wilk", 10, 3, 450);
        Market.Listing gone = m.add("Ula", 11, 1, 99);
        m.sold(gone.id());
        m.addEarnings("Stary Wilk", 1234);
        Market back = Market.decode(m.encode() + "L zepsute\n");
        assertEquals(m.newestFirst(), back.newestFirst());
        assertEquals(1234, back.takeEarnings("stary wilk"));
        assertTrue(back.add("Ola", 1, 1, 1).id() > gone.id(), "ids are not reused after a restart");
        back.forget("STARY WILK");
        assertTrue(back.bySeller("Stary Wilk").isEmpty());
    }

    @Test
    void viewRoundTripAndBankCreditDebit() {
        MarketView v = new MarketView(7, "futro", List.of(new MarketView.Row(3, "Wilcze futro", 5, 300, "Ula", false),
                new MarketView.Row(4, "Lina", 1, 50, "Stary Wilk", true)));
        String[] enc = v.encode();
        String[] msg = new String[enc.length + 1];
        msg[0] = "MARKET";
        System.arraycopy(enc, 0, msg, 1, enc.length);
        assertEquals(v, MarketView.parse(msg));
        BankAccount b = new BankAccount(100);
        assertEquals(InventoryResult.INSUFFICIENT_FUNDS, b.debit(101));
        assertEquals(InventoryResult.OK, b.debit(100));
        assertEquals(InventoryResult.OK, b.credit(Long.MAX_VALUE));
        assertEquals(InventoryResult.INVALID_AMOUNT, b.credit(1), "no overflow");
    }
}
