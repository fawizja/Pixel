package pl.fawizja.pixel.net;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ChatCommandsTest {

    @Test
    void commandsBecomeProtocolMessagesAndPasswordsNeverReachTheChat() {
        assertArrayEquals(new String[] {"CHAT", "cześć"}, ChatCommands.toMessage("  cześć "));
        assertArrayEquals(new String[] {"PASSWD", "stare1", "nowe12"}, ChatCommands.toMessage("/haslo stare1 nowe12"));
        assertArrayEquals(new String[] {"DELETE", "sekret"}, ChatCommands.toMessage("/USUN-KONTO sekret"));
        assertNull(ChatCommands.toMessage("/haslo tylko-jedno"), "an incomplete command is not sent as chat");
        assertNull(ChatCommands.toMessage("/nieznane x"));
        assertNull(ChatCommands.toMessage("   "));
    }

    @Test
    void privateMessagesAndModeratorCommands() {
        assertArrayEquals(new String[] {"PM", "Stary Wilk", "cześć, co słychać?"}, ChatCommands.toMessage("/w Stary Wilk, cześć, co słychać?"));
        assertArrayEquals(new String[] {"PM", "Ola", "hej tam"}, ChatCommands.toMessage("/w Ola hej tam"));
        assertNull(ChatCommands.toMessage("/w Ola"));
        assertArrayEquals(new String[] {"ADMIN", "SAY", "restart o 20:00"}, ChatCommands.toMessage("/ogloszenie restart o 20:00"));
        assertArrayEquals(new String[] {"ADMIN", "BAN", "Stary Wilk", "24"}, ChatCommands.toMessage("/ban Stary Wilk 24"));
        assertArrayEquals(new String[] {"ADMIN", "BAN", "Oszust"}, ChatCommands.toMessage("/ban Oszust"));
        assertArrayEquals(new String[] {"ADMIN", "MUTE", "Ola", "15"}, ChatCommands.toMessage("/wycisz Ola 15"));
        assertArrayEquals(new String[] {"ADMIN", "KICK", "Ola"}, ChatCommands.toMessage("/kick Ola"));
        assertArrayEquals(new String[] {"ADMIN", "UNBAN", "Ola"}, ChatCommands.toMessage("/unban Ola"));
        assertArrayEquals(new String[] {"ADMIN", "WHO"}, ChatCommands.toMessage("/kto"));
        assertArrayEquals(new String[] {"RANK"}, ChatCommands.toMessage("/ranking"));
        assertArrayEquals(new String[] {"FRIEND", "LIST"}, ChatCommands.toMessage("/znajomi"));
        assertArrayEquals(new String[] {"FRIEND", "ADD", "Stary Wilk"}, ChatCommands.toMessage("/znajomi dodaj Stary Wilk"));
        assertArrayEquals(new String[] {"FRIEND", "REMOVE", "Ola"}, ChatCommands.toMessage("/znajomi usuń Ola"));
        assertNull(ChatCommands.toMessage("/znajomi dodaj"));
        assertArrayEquals(new String[] {"MARKET", "LIST"}, ChatCommands.toMessage("/rynek"));
        assertArrayEquals(new String[] {"MARKET", "LIST", "wilcze futro"}, ChatCommands.toMessage("/rynek wilcze futro"));
        assertArrayEquals(new String[] {"MARKET", "SELL", "5 wilcze futro 300"}, ChatCommands.toMessage("/rynek wystaw 5 wilcze futro 300"));
        assertArrayEquals(new String[] {"MARKET", "BUY", "12"}, ChatCommands.toMessage("/rynek kup #12"));
        assertArrayEquals(new String[] {"MARKET", "CANCEL", "3"}, ChatCommands.toMessage("/rynek wycofaj 3"));
        assertNull(ChatCommands.toMessage("/rynek kup tanio"));
        assertArrayEquals(new String[] {"ADMIN", "INVASION"}, ChatCommands.toMessage("/inwazja"));
        assertArrayEquals(new String[] {"MAIL", "SEND", "Stary Wilk", "5 wilcze futro"}, ChatCommands.toMessage("/wyślij Stary Wilk, 5 wilcze futro"));
        assertNull(ChatCommands.toMessage("/wyslij Ola 5 lina"), "the comma separates the name");
        assertArrayEquals(new String[] {"ADMIN", "INVASION", "Frostholm"}, ChatCommands.toMessage("/inwazja Frostholm"));
        assertArrayEquals(new String[] {"RANK", "druid"}, ChatCommands.toMessage("/Ranking druid"));
        assertArrayEquals(new String[] {"DUEL", "REQUEST", "Stary Wilk"}, ChatCommands.toMessage("/pojedynek Stary Wilk"));
        assertArrayEquals(new String[] {"DUEL", "SURRENDER"}, ChatCommands.toMessage("/pojedynek poddaj"));
        assertArrayEquals(new String[] {"DUEL", "HELP"}, ChatCommands.toMessage("/pojedynek"));
        assertArrayEquals(new String[] {"BOSS"}, ChatCommands.toMessage("/bossowie"));
    }
}
