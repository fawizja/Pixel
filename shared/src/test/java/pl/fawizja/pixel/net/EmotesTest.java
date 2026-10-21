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

    @Test
    void danceHopsBowCrouchesAndPlainChatStandsStill() {
        assertEquals(0f, Emotes.rise("cześć", 200));
        assertTrue(Emotes.rise("* tańczy *", 200) > 0.1f);
        assertEquals(0f, Emotes.rise("* tańczy *", 0), 1e-6);
        assertTrue(Emotes.rise("* kłania się *", 200) < 0f);
        assertEquals(0f, Emotes.rise("* ostrzy miecz *", 200));
    }

    @Test
    void emoteWindowRowsSendTheirEmoteWhenTapped() {
        java.util.List<String[]> rows = Emotes.rows();
        assertEquals(11, rows.size());
        for (String[] r : rows) {
            if (r.length < 4 || r[3].equals("cmd:/kostka")) continue;
            assertTrue(r[3].startsWith("cmd:/"));
            assertNotNull(Emotes.text(r[3].substring(4)), r[3]);
        }
        assertTrue(rows.get(0)[1].startsWith("tańczy"));
    }

    @Test
    void diceRollIsAskedFromTheServer() {
        assertArrayEquals(new String[] {"ROLL", "6"}, ChatCommands.toMessage("/kostka"));
        assertArrayEquals(new String[] {"ROLL", "20"}, ChatCommands.toMessage("/kostka 20"));
        assertArrayEquals(new String[] {"ROLL", "20"}, ChatCommands.toMessage("/kostka k20"));
        assertEquals(100, Emotes.diceSides("/kostka 5000"));
        assertEquals(2, Emotes.diceSides("/kostka 1"));
        assertEquals(-1, Emotes.diceSides("/kostka abc"));
        assertEquals(-1, Emotes.diceSides("/taniec"));
        assertEquals("* rzuca kostką k6: 4 *", Emotes.diceText(6, 4));
        assertTrue(Emotes.isEmote(Emotes.diceText(6, 4)));
    }
}
