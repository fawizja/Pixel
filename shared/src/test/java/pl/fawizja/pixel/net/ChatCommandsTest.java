package pl.fawizja.pixel.net;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ChatCommandsTest {

    @Test
    void commandsBecomeProtocolMessagesAndPasswordsNeverReachTheChat() {
        assertArrayEquals(new String[] {"CHAT", "czesc"}, ChatCommands.toMessage("  czesc "));
        assertArrayEquals(new String[] {"PASSWD", "stare1", "nowe12"}, ChatCommands.toMessage("/haslo stare1 nowe12"));
        assertArrayEquals(new String[] {"DELETE", "sekret"}, ChatCommands.toMessage("/USUN-KONTO sekret"));
        assertNull(ChatCommands.toMessage("/haslo tylko-jedno"), "an incomplete command is not sent as chat");
        assertNull(ChatCommands.toMessage("/nieznane x"));
        assertNull(ChatCommands.toMessage("   "));
    }
}
