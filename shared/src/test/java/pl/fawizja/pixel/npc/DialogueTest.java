package pl.fawizja.pixel.npc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.world.Position;

class DialogueTest {

    static final DialogueRegistry D = DialogueRegistry.defaults();

    static Npc npc(String key, NpcRole role) {
        return new Npc(key, "Oren", role, "Varden", new Position(1, 1, 0), role == NpcRole.MERCHANT ? "varden_general" : null,
                "Witaj, wędrowcze.", "#ffffff");
    }

    @Test
    void keywordsMatchWithoutCaseOrPolishLettersAndOnlyTheOwnNpcAnswers() {
        Npc guide = npc("varden_guide", NpcRole.GUIDE);
        assertTrue(D.answer(guide, "Jak dojść do KOPALNI?").startsWith("Wejście do kopalni"));
        assertTrue(D.answer(npc("frost_guide", NpcRole.GUIDE), "Królowa").contains("Lodowych Jaskiń"));
        assertNull(D.answer(guide, "kopalniany"), "whole words only");
        assertNull(D.answer(guide, "Cześć!"), "no greeting chatter - the greeting is the window header");
        assertNull(D.answer(guide, "kim jesteś"), "no name chatter");
        assertNull(D.answer(npc("frost_guide", NpcRole.GUIDE), "kopalnia"), "another guide's topic");
        assertNull(D.answer(npc("frost_merchant", NpcRole.MERCHANT), "handel"), "trade is a window button, not talk");
        assertNull(D.answer(guide, "   "));
    }

    @Test
    void onlyGameplayInfoIsLeftAndEveryLineBelongsToAnExistingNpc() throws Exception {
        assertEquals(List.of("kopalnia"), D.topics(npc("varden_guide", NpcRole.GUIDE)));
        NpcRegistry all = NpcRegistry.load(pl.fawizja.pixel.world.WorldLayout.WORLD_MAP, pl.fawizja.pixel.TestData.world(),
                ShopRegistry.loadDefault(pl.fawizja.pixel.TestData.ITEMS));
        int withTopics = 0;
        for (Npc n : all.all()) {
            List<String> t = D.topics(n);
            if (n.role() == NpcRole.GUARD || n.role() == NpcRole.PRIEST || n.role() == NpcRole.BANKER
                    || n.role() == NpcRole.CARRIER || n.role() == NpcRole.CAPTAIN || n.role() == NpcRole.KING) {
                assertTrue(t.isEmpty(), n.key() + " should only greet and offer its buttons: " + t);
            }
            if (!t.isEmpty()) withTopics++;
            assertTrue(t.size() <= 2, n.key() + " " + t);
            for (String topic : t) assertNotNull(D.answer(n, topic), n.key() + " topic " + topic);
            assertFalse(n.greeting().isBlank(), n.key() + " keeps its one greeting line");
        }
        assertEquals(5 + 13 + 5 + 6 + 17 + 1, withTopics, "quest-location guides, the camp quartermaster, the 13 story quest wardens, the 5 island access givers, the 6 quest house wardens, the 17 premium line givers and the Postmaster");
    }

    @Test
    void badDataIsRejected() {
        assertThrows(java.io.IOException.class, () -> DialogueRegistry.parse(List.of("@WIZARD;a;b")));
        assertThrows(java.io.IOException.class, () -> DialogueRegistry.parse(List.of("*;,;b")));
        assertThrows(java.io.IOException.class, () -> DialogueRegistry.parse(List.of("*;a")));
    }
}
