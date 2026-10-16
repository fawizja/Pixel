package pl.fawizja.pixel.settings;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ChatHistoryTest {
    @Test
    void arrowsWalkThroughSentMessages() {
        ChatHistory h = new ChatHistory();
        assertNull(h.older());
        h.add("/szukaj futro");
        h.add("  ");
        h.add("cześć");
        h.add("/łupy");
        assertEquals("/łupy", h.older());
        assertEquals("cześć", h.older());
        assertEquals("/szukaj futro", h.older());
        assertEquals("/szukaj futro", h.older(), "stays on the oldest");
        assertEquals("cześć", h.newer());
        assertEquals("/łupy", h.newer());
        assertEquals("", h.newer(), "past the newest is an empty draft");

        h.add("cześć"); // powtorzenie idzie na koniec
        assertEquals(3, h.size());
        assertEquals("cześć", h.last());
        for (int i = 0; i < ChatHistory.MAX + 5; i++) h.add("linia " + i);
        assertEquals(ChatHistory.MAX, h.size());
    }
}
