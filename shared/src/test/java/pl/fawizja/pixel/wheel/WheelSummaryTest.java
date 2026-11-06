package pl.fawizja.pixel.wheel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.vocation.Vocation;

class WheelSummaryTest {

    @Test
    void lowLevelCharacterSeesWhatIsMissing() {
        PlayerCharacter pc = WheelFixtures.hero(Vocation.KNIGHT, 8);
        pc.setPremium(false);
        String msg = WheelView.requirementsMessage(pc);
        assertNotNull(msg);
        assertTrue(msg.contains("poziomu 51 (masz 8)") && msg.contains("konta premium") && !msg.contains("promocji"), msg);
        List<String[]> rows = WheelView.summary(pc, true, k -> 0);
        assertEquals("Koło nieaktywne", rows.get(0)[0]);
        assertTrue(rows.stream().anyMatch(r -> r[0].equals("Poziom 51") && r[2].equals("brak")));
        assertTrue(rows.stream().anyMatch(r -> r[0].equals("Promocja") && r[2].equals("spełnione")));
    }

    @Test
    void eligibleSummaryLinksAllFourRevelations() {
        PlayerCharacter pc = WheelFixtures.hero(Vocation.KNIGHT, 400);
        assertNull(WheelView.requirementsMessage(pc));
        WheelFixtures.fill(pc, "P");
        List<String[]> rows = WheelView.summary(pc, false, k -> 0);
        for (WheelDomain d : WheelDomain.values()) {
            assertTrue(rows.stream().anyMatch(r -> r[3].equals("wheel:rev:" + d.code)), d.name());
        }
        assertTrue(rows.stream().anyMatch(r -> r[0].startsWith("Fioletowa") && r[2].equals("etap 1")));
    }
}
