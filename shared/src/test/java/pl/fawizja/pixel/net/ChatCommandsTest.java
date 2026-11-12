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
    void bankTransfersAcceptCommonAmountFormats() {
        assertArrayEquals(new String[] {"TRANSFER", "Stary Wilk", "5000"}, ChatCommands.toMessage("/przelew Stary Wilk, 5000"));
        assertArrayEquals(new String[] {"TRANSFER", "Ola", "12000"}, ChatCommands.toMessage("/przelew Ola, 12 000 gp"));
        assertArrayEquals(new String[] {"TRANSFER", "Ola", "5000"}, ChatCommands.toMessage("/przelew Ola, 5k"));
        assertArrayEquals(new String[] {"TRANSFER", "Ola", "2000000"}, ChatCommands.toMessage("/przelew Ola, 2kk"));
        assertNull(ChatCommands.toMessage("/przelew Ola 5000"), "the comma separates the name");
        assertNull(ChatCommands.toMessage("/przelew Ola, -5"));
        assertNull(ChatCommands.toMessage("/przelew Ola, dużo"));
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
        assertArrayEquals(new String[] {"ADMIN", "REPORTS"}, ChatCommands.toMessage("/zgłoszenia"));
        assertArrayEquals(new String[] {"ADMIN", "UNBAN", "Ola"}, ChatCommands.toMessage("/unban Ola"));
        assertArrayEquals(new String[] {"ADMIN", "WHO"}, ChatCommands.toMessage("/kto"));
        assertArrayEquals(new String[] {"ADMIN", "STATUS"}, ChatCommands.toMessage("/serwer"));
        assertArrayEquals(new String[] {"ADMIN", "SAVE"}, ChatCommands.toMessage("/zapisz"));
        assertArrayEquals(new String[] {"ADMIN", "MOTD"}, ChatCommands.toMessage("/motd"));
        assertArrayEquals(new String[] {"BOUNTY", "Stary Wilk", "5000"}, ChatCommands.toMessage("/nagroda Stary Wilk, 5k"));
        assertArrayEquals(new String[] {"BOUNTY"}, ChatCommands.toMessage("/nagroda"));
        assertArrayEquals(new String[] {"BOUNTY"}, ChatCommands.toMessage("/nagrody"));
        assertArrayEquals(new String[] {"ADMIN", "MOTD", "Event w sobotę"}, ChatCommands.toMessage("/motd Event w sobotę"));
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
        assertArrayEquals(new String[] {"WANT", "LIST"}, ChatCommands.toMessage("/kupię"));
        assertArrayEquals(new String[] {"LFG"}, ChatCommands.toMessage("/szukam"));
        assertArrayEquals(new String[] {"GUILD", "BANNER"}, ChatCommands.toMessage("/gildia sztandar"));
        assertArrayEquals(new String[] {"GUILD", "MEMBERS"}, ChatCommands.toMessage("/gildia członkowie"));
        assertArrayEquals(new String[] {"INSPECT", "Stary Wilk"}, ChatCommands.toMessage("/obejrzyj Stary Wilk"));
        assertArrayEquals(new String[] {"REPORT", "Stary Wilk", "wyzywa na czacie"}, ChatCommands.toMessage("/zgłoś Stary Wilk, wyzywa na czacie"));
        assertNull(ChatCommands.toMessage("/zgłoś Stary Wilk"));
        assertArrayEquals(new String[] {"WANT", "ADD", "20 wilcze futro 2000"}, ChatCommands.toMessage("/kupię 20 wilcze futro 2000"));
        assertArrayEquals(new String[] {"WANT", "CANCEL", "3"}, ChatCommands.toMessage("/kupie anuluj #3"));
        assertArrayEquals(new String[] {"WANT", "FILL", "7"}, ChatCommands.toMessage("/sprzedaj 7"));
        assertNull(ChatCommands.toMessage("/sprzedaj wszystko"));
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

    @org.junit.jupiter.api.Test
    void helpWindowListsRunnableAndTypedCommands() {
        java.util.List<String[]> rows = ChatCommands.helpRows();
        assertTrue(rows.size() >= 15 && rows.size() <= 18, "fits two columns: " + rows.size());
        long runnable = rows.stream().filter(r -> r.length > 3).count();
        assertTrue(runnable >= 10);
        for (String[] r : rows) {
            assertTrue(r[0].startsWith("/"), r[0]);
            if (r.length > 3) assertEquals("cmd:" + r[0], r[3], "a tap runs the listed command");
        }
    }

    @Test
    void godCommandsParseToGodMessagesWithCanaryShortFormsAndPolishAliases() {
        assertArrayEquals(new String[] {"GOD", "ITEM", "miecz 3"}, ChatCommands.toMessage("/i miecz 3"));
        assertArrayEquals(new String[] {"GOD", "ITEM", "gold coin 100"}, ChatCommands.toMessage("/item gold coin 100"));
        assertArrayEquals(new String[] {"GOD", "ITEM", "miecz"}, ChatCommands.toMessage("/przedmiot miecz"));
        assertNull(ChatCommands.toMessage("/i"), "an item needs a name");
        assertArrayEquals(new String[] {"GOD", "MONSTER", "Stary Wilk"}, ChatCommands.toMessage("/m Stary Wilk"));
        assertArrayEquals(new String[] {"GOD", "AHEAD", "5"}, ChatCommands.toMessage("/a 5"));
        assertNull(ChatCommands.toMessage("/a"));
        assertArrayEquals(new String[] {"GOD", "UP"}, ChatCommands.toMessage("/up"));
        assertArrayEquals(new String[] {"GOD", "DOWN"}, ChatCommands.toMessage("/Down"));
        assertArrayEquals(new String[] {"GOD", "GOTO", "Stary Wilk"}, ChatCommands.toMessage("/goto Stary Wilk"));
        assertArrayEquals(new String[] {"GOD", "GOTO", "501,499,0"}, ChatCommands.toMessage("/goto 501,499,0"));
        assertArrayEquals(new String[] {"GOD", "BRING", "Ola"}, ChatCommands.toMessage("/c Ola"));
        assertArrayEquals(new String[] {"GOD", "TEMPLE"}, ChatCommands.toMessage("/t"));
        assertArrayEquals(new String[] {"GOD", "TEMPLE", "Varden"}, ChatCommands.toMessage("/t Varden"));
        assertArrayEquals(new String[] {"GOD", "GHOST"}, ChatCommands.toMessage("/ghost"));
        assertArrayEquals(new String[] {"GOD", "GHOST"}, ChatCommands.toMessage("/duch"));
        assertArrayEquals(new String[] {"GOD", "GODMODE", "off"}, ChatCommands.toMessage("/godmode off"));
        assertArrayEquals(new String[] {"GOD", "LEVEL", "300"}, ChatCommands.toMessage("/level 300"));
        assertArrayEquals(new String[] {"GOD", "ADDEXP", "5k"}, ChatCommands.toMessage("/addexp 5k"));
        assertArrayEquals(new String[] {"GOD", "ADDSKILL", "miecz 10"}, ChatCommands.toMessage("/addskill miecz 10"));
        assertArrayEquals(new String[] {"GOD", "SETHP", "100"}, ChatCommands.toMessage("/sethp 100"));
        assertArrayEquals(new String[] {"GOD", "SETMANA", "50"}, ChatCommands.toMessage("/setmana 50"));
        assertArrayEquals(new String[] {"GOD", "HEAL"}, ChatCommands.toMessage("/heal"));
        assertArrayEquals(new String[] {"GOD", "MONEY", "2kk"}, ChatCommands.toMessage("/money 2kk"));
        assertArrayEquals(new String[] {"GOD", "PREMIUM", "Ola 30"}, ChatCommands.toMessage("/premium Ola 30"));
        assertArrayEquals(new String[] {"GOD", "PROMOTE", "Ola"}, ChatCommands.toMessage("/promote Ola"));
        assertArrayEquals(new String[] {"GOD", "BLESS", "Stary Wilk"}, ChatCommands.toMessage("/bless Stary Wilk"));
        assertArrayEquals(new String[] {"GOD", "FLAG", "Ola, star_sealed"}, ChatCommands.toMessage("/flag Ola, star_sealed"));
        assertArrayEquals(new String[] {"GOD", "CLEAN"}, ChatCommands.toMessage("/clean"));
        assertArrayEquals(new String[] {"GOD", "KILL"}, ChatCommands.toMessage("/zabij"));
        assertArrayEquals(new String[] {"GOD", "SPEED", "400"}, ChatCommands.toMessage("/speed 400"));
        assertArrayEquals(new String[] {"GOD", "INFO", "Ola"}, ChatCommands.toMessage("/info Ola"));
        assertArrayEquals(new String[] {"GOD", "POS"}, ChatCommands.toMessage("/pos"));
        assertArrayEquals(new String[] {"GOD", "HELP"}, ChatCommands.toMessage("/komendy"));
        assertArrayEquals(new String[] {"GOD", "HELP"}, ChatCommands.toMessage("/Komendy"));
        // aliasy istniejacych polecen administratora
        assertArrayEquals(new String[] {"ADMIN", "SAY", "restart"}, ChatCommands.toMessage("/broadcast restart"));
        assertArrayEquals(new String[] {"ADMIN", "INVASION"}, ChatCommands.toMessage("/raid"));
        assertArrayEquals(new String[] {"ADMIN", "INVASION", "Varden"}, ChatCommands.toMessage("/raid Varden"));
        assertArrayEquals(new String[] {"ADMIN", "SAVE"}, ChatCommands.toMessage("/save"));
        assertArrayEquals(new String[] {"ADMIN", "SHUTDOWN", "10"}, ChatCommands.toMessage("/shutdown 10"));
        assertArrayEquals(new String[] {"ADMIN", "SHUTDOWN"}, ChatCommands.toMessage("/shutdown"));
        // zwykly czat i znane polecenia sie nie zmieniaja
        assertArrayEquals(new String[] {"CHAT", "i miecz"}, ChatCommands.toMessage("i miecz"));
        assertArrayEquals(new String[] {"ADMIN", "WHO"}, ChatCommands.toMessage("/kto"));
        assertNull(ChatCommands.toMessage("/ighost"));
    }
}
