package pl.fawizja.pixel.net;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EmotesTest {

    @Test
    void emoteCommandsBecomeChatActions() {
        assertArrayEquals(new String[] {"CHAT", "* tańczy *"}, ChatCommands.toMessage("/taniec"));
        assertArrayEquals(new String[] {"CHAT", "* kłania się *"}, ChatCommands.toMessage("/ukłon"));
        assertArrayEquals(new String[] {"CHAT", "* kłania się *"}, ChatCommands.toMessage("/uklon"));
        assertArrayEquals(new String[] {"CHAT", "* ostrzy miecz *"}, ChatCommands.toMessage("/ja ostrzy miecz"));
        assertNull(Emotes.text("/ja"), "empty /ja sends nothing");
        assertNull(Emotes.text("/taniec z Ulą"), "fixed emotes take no text");
        assertNull(Emotes.text("taniec"));
        assertNull(Emotes.text("/gracze"));
    }

    @Test
    void emoteTextIsRecognisedForTheBubbleColour() {
        assertTrue(Emotes.isEmote(Emotes.text("/machaj")));
        assertFalse(Emotes.isEmote("cześć *"));
        assertFalse(Emotes.isEmote("* *"));
        assertTrue(Emotes.text("/ja " + "a".repeat(200)).length() <= 84, "long /ja is trimmed");
    }
}
