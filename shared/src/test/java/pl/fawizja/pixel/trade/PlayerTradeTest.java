package pl.fawizja.pixel.trade;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.economy.Money;
import pl.fawizja.pixel.inventory.Container;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.net.ChatCommands;
import pl.fawizja.pixel.trade.PlayerTrade.Result;
import pl.fawizja.pixel.world.Position;

class PlayerTradeTest {

    static final ItemRegistry REG = TestData.ITEMS;

    static Container bag(long gold, ItemStack... items) {
        Container c = Container.backpack(REG);
        for (ItemStack s : items) assertTrue(c.add(s).ok());
        assertTrue(Money.add(c, gold).ok());
        return c;
    }

    @Test
    void bothSidesMustAcceptAndAnyChangeResetsAcceptance() {
        Container ola = bag(0, new ItemStack(30, 20), ItemStack.of(33));
        Container ula = bag(5_000);
        PlayerTrade t = new PlayerTrade("Ola", "Ula", 0);
        assertEquals(Result.EMPTY, t.accept("Ola"));
        assertEquals(Result.OK, t.offer("Ola", ola, REG, "5 health potion", 10));
        assertEquals(Result.OK, t.offer("Ola", ola, REG, "rope", 10));
        assertEquals(Result.NOT_ENOUGH, t.offer("Ola", ola, REG, "16 health_potion", 10), "only 15 potions left to offer");
        assertEquals(Result.UNKNOWN_ITEM, t.offer("Ola", ola, REG, "3 excalibur", 10));
        assertEquals(Result.NOT_PARTICIPANT, t.offer("Ela", ola, REG, "rope", 10));
        assertEquals(Result.WAITING, t.accept("Ola"));
        assertEquals(Result.OK, t.offer("Ula", ula, REG, "1200 gp", 20));
        assertFalse(t.accepted("Ola"), "a changed offer must be accepted again");
        assertEquals(Result.NOT_ENOUGH, t.offer("Ula", ula, REG, "4000 gp", 20));
        assertEquals(Result.WAITING, t.accept("Ula"));
        assertEquals(Result.DONE, t.accept("Ola"));

        assertEquals(Result.DONE, t.execute(ola, ula));
        assertEquals(15, ola.count(30));
        assertEquals(0, ola.count(33));
        assertEquals(1_200, Money.totalValue(ola));
        assertEquals(5, ula.count(30));
        assertEquals(1, ula.count(33));
        assertEquals(3_800, Money.totalValue(ula));
    }

    @Test
    void theExchangeIsAllOrNothing() {
        Container ola = bag(0, new ItemStack(30, 10));
        Container ula = bag(1_000);
        PlayerTrade t = new PlayerTrade("Ola", "Ula", 0);
        t.offer("Ola", ola, REG, "10 health potion", 0);
        t.offer("Ula", ula, REG, "1000 gp", 0);
        // Ola zuzyla mikstury po wystawieniu oferty
        ola.remove(30, 3);
        assertEquals(Result.CHANGED, t.execute(ola, ula));
        assertEquals(7, ola.count(30));
        assertEquals(1_000, Money.totalValue(ula), "nothing moved");

        // pelny plecak odbiorcy
        Container full = Container.backpack(REG);
        for (int i = 0; i < Container.BACKPACK_SLOTS; i++) full.add(ItemStack.of(33));
        Container seller = bag(0, new ItemStack(31, 4));
        PlayerTrade t2 = new PlayerTrade("A", "B", 0);
        t2.offer("A", seller, REG, "4 mana potion", 0);
        assertEquals(Result.NO_SPACE, t2.execute(seller, full));
        assertEquals(4, seller.count(31));
        assertEquals(0, full.count(31));
    }

    @Test
    void partnersMustStandCloseAndOffersParse() {
        Position p = new Position(10, 10, 0);
        assertTrue(PlayerTrade.closeEnough(p, new Position(12, 9, 0)));
        assertFalse(PlayerTrade.closeEnough(p, new Position(13, 10, 0)));
        assertFalse(PlayerTrade.closeEnough(p, new Position(10, 10, 1)));
        assertEquals(new PlayerTrade.Parsed(-1, 250), PlayerTrade.parse("250 zloto", REG));
        assertEquals(new PlayerTrade.Parsed(33, 1), PlayerTrade.parse("Rope", REG));
        assertEquals(new PlayerTrade.Parsed(59, 2), PlayerTrade.parse("2 great health", REG), "unique prefix");
        assertNull(PlayerTrade.parse("2 great", REG), "ambiguous prefix");
        assertNull(PlayerTrade.parse("0 rope", REG));
        assertNull(PlayerTrade.parse("99999 rope", REG));
        PlayerTrade t = new PlayerTrade("A", "B", 0);
        assertTrue(t.expired(PlayerTrade.TIMEOUT_MS + 1));

        assertArrayEquals(new String[] {"TRADE", "REQUEST", "Ula"}, ChatCommands.toMessage("/handel Ula"));
        assertArrayEquals(new String[] {"TRADE", "OFFER", "5 health potion"}, ChatCommands.toMessage("/oferuj 5 health potion"));
        assertArrayEquals(new String[] {"TRADE", "ACCEPT"}, ChatCommands.toMessage("/akceptuj"));
        assertArrayEquals(new String[] {"TRADE", "CANCEL"}, ChatCommands.toMessage("/anuluj"));
    }
}
