package pl.fawizja.pixel.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LettersTest {

    @Test
    void lettersWaitAreLimitedAndSurviveSaving() {
        Letters l = new Letters();
        l.add("Ula", "Ola", "spotkajmy się; przy moście");
        assertEquals(1, l.waiting("ULA").size());
        Letters back = Letters.decode(l.encode() + "zepsuta linia\n");
        Letters.Letter got = back.take("ula").get(0);
        assertEquals("Ola", got.from());
        assertEquals("spotkajmy się; przy moście", got.text());
        assertTrue(back.take("Ula").isEmpty(), "taken once");
        for (int i = 0; i < Letters.MAX_WAITING; i++) l.add("Iwo", "Ola", "x" + i);
        assertFalse(l.canAccept("iwo"));
        l.add("Jan", "Ola", "a".repeat(500));
        assertEquals(Letters.MAX_TEXT, l.waiting("Jan").get(0).text().length());
    }
}
