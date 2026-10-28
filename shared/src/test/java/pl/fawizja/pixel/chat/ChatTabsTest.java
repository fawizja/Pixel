package pl.fawizja.pixel.chat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.chat.ChatTabs.Channel;
import pl.fawizja.pixel.game.EventLog;

class ChatTabsTest {

    private static String titles(ChatTabs c) {
        StringBuilder sb = new StringBuilder();
        for (ChatTabs.Tab t : c.tabs()) sb.append(sb.length() == 0 ? "" : ",").append(t.title());
        return sb.toString();
    }

    @Test
    void startsWithLocalServerAndTradeAndLocalSelected() {
        ChatTabs c = new ChatTabs();
        assertEquals("Lokalny,Serwer,Handel", titles(c));
        assertEquals(Channel.LOCAL, c.active().channel());
    }

    @Test
    void serverChatFieldsAreRoutedByChannelOrSenderLabel() {
        assertEquals(Channel.LOCAL, ChatTabs.route("Ola", "hej", null, "Ula").channel());
        assertEquals(Channel.LOCAL, ChatTabs.route("Ola", "hej", "local", "Ula").channel());
        ChatTabs.Route pm = ChatTabs.route("[od Ola]", "psst", "pm", "Ula");
        assertEquals(Channel.PRIVATE, pm.channel());
        assertEquals("Ola", pm.peer());
        assertEquals("Ola: psst", pm.text());
        assertFalse(pm.own());
        ChatTabs.Route echo = ChatTabs.route("[do Ola]", "psst", null, "Ula");
        assertEquals("Ola", echo.peer());
        assertEquals("Ula: psst", echo.text());
        assertTrue(echo.own(), "my own private message does not light the tab");
        assertEquals("Ola", ChatTabs.route("[list] Ola", "jutro", null, "Ula").peer());
        assertEquals(Channel.PARTY, ChatTabs.route("[Drużyna] Ola", "wilki", null, "Ula").channel());
        assertEquals(Channel.TRADE, ChatTabs.route("[Handel] Ola", "sprzedam futro", null, "Ula").channel());
        assertEquals("Ola: sprzedam futro", ChatTabs.route("[Handel] Ola", "sprzedam futro", null, "Ula").text());
        assertEquals(Channel.GUILD, ChatTabs.route("[Wilcze Kly] Ola", "hej", null, "Ula").channel());
        // gildia nazwana jak kanal: rozstrzyga pole kanalu z serwera
        assertEquals(Channel.GUILD, ChatTabs.route("[Handel] Ola", "hej", "guild", "Ula").channel());
        assertEquals(Channel.SERVER, ChatTabs.route("[Ogłoszenie]", "restart", null, "Ula").channel());
        assertEquals(ChatTabs.Style.BROADCAST, ChatTabs.route("[Ogłoszenie]", "restart", "broadcast", "Ula").style());
        assertTrue(ChatTabs.route("ula", "hej", null, "Ula").own());
    }

    @Test
    void messageInAnotherTabMarksItUnreadUntilSelected() {
        ChatTabs c = new ChatTabs();
        c.setSelf("Ula");
        int trade = c.receive("[Handel] Ola", "sprzedam futro", "trade", 1);
        assertEquals(2, trade);
        assertTrue(c.tabs().get(trade).unread());
        assertFalse(c.tabs().get(0).unread());
        c.receive("Ola", "hej", null, 2);
        assertFalse(c.tabs().get(0).unread(), "the selected tab never becomes unread");
        c.select(trade);
        assertFalse(c.tabs().get(trade).unread());
        assertEquals(1, c.tabs().get(trade).size());
        // wlasna wiadomosc w niewybranej zakladce nie zapala znacznika
        c.receive("Ula", "hej", "local", 3);
        assertFalse(c.tabs().get(0).unread());
    }

    @Test
    void privateMessageOpensTabAndTypingThereWhispersThatPlayer() {
        ChatTabs c = new ChatTabs();
        c.setSelf("Ula");
        int i = c.receive("[od Ola]", "psst", "pm", 1);
        assertEquals("Lokalny,Serwer,Handel,Ola", titles(c));
        assertTrue(c.tabs().get(i).unread());
        assertTrue(c.tabs().get(i).closable());
        // echo wlasnej odpowiedzi trafia do tej samej zakladki (imie bez wielkosci liter)
        assertEquals(i, c.receive("[do ola]", "co?", "pm", 2));
        c.select(i);
        assertArrayEquals(new String[] {"PM", "Ola", "spotkajmy się"}, c.outgoing("  spotkajmy się "));
        assertNull(c.outgoing("/w Kuba, hej"), "commands keep their own meaning in every tab");
        assertTrue(c.close(i));
        assertEquals("Lokalny,Serwer,Handel", titles(c));
        assertEquals(Channel.TRADE, c.active().channel(), "closing selects the previous tab");
        assertFalse(c.close(0), "fixed channels cannot be closed");
    }

    @Test
    void typingSendsToTheSelectedChannel() {
        ChatTabs c = new ChatTabs();
        assertNull(c.outgoing("hej"), "local chat goes the usual way (NPC words, spells, CHAT)");
        assertTrue(c.localActive());
        c.select(c.indexOf(Channel.TRADE, null));
        assertArrayEquals(new String[] {"TCHAT", "sprzedam futro"}, c.outgoing("sprzedam futro"));
        assertFalse(c.localActive());
        c.ensure(Channel.PARTY);
        c.select(c.indexOf(Channel.PARTY, null));
        assertArrayEquals(new String[] {"PCHAT", "idziemy"}, c.outgoing("idziemy"));
        c.receive("[Wilcze Kly] Ola", "hej", "guild", 1);
        c.select(c.indexOf(Channel.GUILD, null));
        assertArrayEquals(new String[] {"GCHAT", "hej"}, c.outgoing("hej"));
        assertEquals("Lokalny,Serwer,Handel,Gildia,Drużyna", titles(c), "channels keep a fixed order");
        c.select(c.indexOf(Channel.SERVER, null));
        assertNull(c.outgoing("hej"), "the server log is read-only: text is said locally");
    }

    @Test
    void addingChannelTabsKeepsTheSelectedTab() {
        ChatTabs c = new ChatTabs();
        c.openPrivate("Ola");
        assertEquals("Ola", c.active().title());
        c.ensure(Channel.PARTY);
        assertEquals("Ola", c.active().title(), "a new channel inserted before does not change selection");
        c.cycle(1);
        assertEquals("Lokalny", c.active().title());
        c.cycle(-1);
        assertEquals("Ola", c.active().title());
    }

    @Test
    void sessionLogGoesToServerTabAndNpcTalkToLocal() {
        EventLog log = new EventLog(50);
        ChatTabs c = new ChatTabs();
        c.setSelf("Ula");
        log.add(1, EventLog.Kind.LOOT, "Łup: 5 gp");
        log.add(2, EventLog.Kind.CHAT, "Kupiec Jan: Witaj!");
        log.add(3, EventLog.Kind.CHAT, "[Gildia] Ola dołącza do gildii.");
        c.sync(log, 3);
        assertEquals(1, c.tabs().get(c.indexOf(Channel.SERVER, null)).size());
        assertTrue(c.tabs().get(c.indexOf(Channel.SERVER, null)).unread());
        assertEquals("Kupiec Jan: Witaj!", c.tabs().get(0).lines().get(0).text());
        assertEquals(ChatTabs.Style.NPC, c.tabs().get(0).lines().get(0).style());
        assertEquals("Ola dołącza do gildii.", c.tabs().get(c.indexOf(Channel.GUILD, null)).lines().get(0).text());
        c.sync(log, 4);
        assertEquals(1, c.tabs().get(0).size(), "entries are taken once");
        log.add(5, EventLog.Kind.WARNING, "Uwaga");
        c.sync(log, 5);
        assertEquals(2, c.tabs().get(c.indexOf(Channel.SERVER, null)).size());
    }

    @Test
    void tabKeepsOnlyRecentLinesAndPrivateTabsAreLimited() {
        ChatTabs c = new ChatTabs();
        for (int i = 0; i < ChatTabs.MAX_LINES + 20; i++) c.receive("Ola", "w" + i, null, i);
        assertEquals(ChatTabs.MAX_LINES, c.tabs().get(0).size());
        for (int i = 0; i < ChatTabs.MAX_PRIVATE + 3; i++) {
            int t = c.receive("[od P" + i + "]", "x", "pm", i);
            c.select(t);
            c.select(0);
        }
        long privates = c.tabs().stream().filter(t -> t.channel() == Channel.PRIVATE).count();
        assertEquals(ChatTabs.MAX_PRIVATE, privates);
    }
}
