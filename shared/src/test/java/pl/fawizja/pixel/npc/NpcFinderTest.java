package pl.fawizja.pixel.npc;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.world.Position;

class NpcFinderTest {
    @BeforeAll
    static void load() throws Exception {
        TownServicesTest.load();
    }

    @Test
    void findsTheNearestNpcByRoleOrName() {
        Position varden = TownServicesTest.npcs.get("varden_guide").position();
        Npc bank = NpcFinder.find(TownServicesTest.npcs, "Bank", varden);
        assertEquals(NpcRole.BANKER, bank.role());
        assertEquals("Varden", bank.town(), "the nearest one");
        assertEquals(NpcRole.PRIEST, NpcFinder.find(TownServicesTest.npcs, "kapłan", varden).role());
        Npc mage = TownServicesTest.npcs.get("varden_mage");
        assertEquals(mage, NpcFinder.find(TownServicesTest.npcs, "mirela", varden));
        assertNull(NpcFinder.find(TownServicesTest.npcs, "x", varden));
        assertNull(NpcFinder.find(TownServicesTest.npcs, "nikogo takiego", varden));
    }
}
