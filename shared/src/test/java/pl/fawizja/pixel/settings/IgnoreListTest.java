package pl.fawizja.pixel.settings;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class IgnoreListTest {

    @Test
    void hidesEveryKindOfMessageFromAnIgnoredPlayerButNotAnnouncements() {
        IgnoreList l = new IgnoreList();
        assertTrue(l.toggle("Ola"));
        assertTrue(l.hides("ola"));
        assertTrue(l.hides("[od Ola]"));
        assertTrue(l.hides("[Rycerze] Ola"));
        assertTrue(l.hides("[Drużyna] Ola"));
        assertFalse(l.hides("[do Ola]"), "own private message stays");
        assertFalse(l.hides("[Ogłoszenie]"));
        assertFalse(l.hides("Ula"));
    }

    @Test
    void toggleRemovesAndTheListSurvivesSaving() {
        IgnoreList l = IgnoreList.decode("Ola;Ula");
        assertEquals("Ola;Ula", l.encode());
        assertFalse(l.toggle("ULA"));
        assertEquals("Ola", IgnoreList.decode(l.encode()).encode());
        assertTrue(IgnoreList.decode(null).all().isEmpty());
    }
}
