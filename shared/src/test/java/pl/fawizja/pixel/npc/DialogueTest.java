package pl.fawizja.pixel.npc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.world.Position;

class DialogueTest {

    static final DialogueRegistry D = DialogueRegistry.defaults();

    static Npc npc(String key, NpcRole role) {
        return new Npc(key, "Oren", role, "Varden", new Position(1, 1, 0), role == NpcRole.MERCHANT ? "varden_general" : null,
                "Witaj, wedrowcze.", "#ffffff");
    }

    @Test
    void keywordsMatchWithoutCaseOrPolishLettersAndTheMostSpecificLineWins() {
        Npc guide = npc("varden_guide", NpcRole.GUIDE);
        assertEquals("Witaj, wedrowcze.", D.answer(guide, "Cześć!"));
        assertTrue(D.answer(guide, "Jak dojść do KOPALNI?").startsWith("Wejscie do kopalni"));
        assertTrue(D.answer(guide, "masz jakąś pracę").startsWith("Ponizej masz moje zlecenia"), "role line");
        assertEquals("Nazywam sie Oren. Mieszkam w Varden.", D.answer(guide, "kim jestes"));
        assertNull(D.answer(guide, "kopalniany"), "whole words only");
        assertNull(D.answer(guide, "handel"), "merchant topic is not the guide's");
        assertNotNull(D.answer(npc("frost_merchant", NpcRole.MERCHANT), "handel"));
        assertNull(D.answer(guide, "   "));
    }

    @Test
    void topicsListOwnSubjectsFirstAndEveryNpcHasSome() throws Exception {
        List<String> topics = D.topics(npc("varden_guide", NpcRole.GUIDE));
        assertEquals("przemytnicy", topics.get(0));
        assertTrue(topics.containsAll(List.of("zadania", "czesc")));
        for (Npc n : NpcRegistry.load(pl.fawizja.pixel.world.WorldLayout.WORLD_MAP, pl.fawizja.pixel.TestData.world(),
                ShopRegistry.loadDefault(pl.fawizja.pixel.TestData.ITEMS)).all()) {
            List<String> t = D.topics(n);
            assertTrue(t.size() >= 4, n.key() + " " + t);
            for (String topic : t) assertNotNull(D.answer(n, topic), n.key() + " topic " + topic);
        }
    }

    @Test
    void badDataIsRejected() {
        assertThrows(java.io.IOException.class, () -> DialogueRegistry.parse(List.of("@WIZARD;a;b")));
        assertThrows(java.io.IOException.class, () -> DialogueRegistry.parse(List.of("*;,;b")));
        assertThrows(java.io.IOException.class, () -> DialogueRegistry.parse(List.of("*;a")));
    }
}
