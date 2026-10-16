package pl.fawizja.pixel.combat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class DeathRecapTest {

    @Test
    void sumsTheLastHalfMinuteBySourceBiggestFirst() {
        DeathRecap r = new DeathRecap();
        r.record("Wilk", 50, 0);           // za stare - pominiete
        r.record("Wilk", 20, 40_000);
        r.record("Goblin", 30, 45_000);
        r.record("Goblin", 25, 50_000);
        r.record("Wilk", 0, 51_000);       // zablokowane - pominiete
        r.record("Ula (gracz)", 10, 52_000);
        DeathRecap.Report rep = r.build("Goblin", 1234, true, 60_000);
        assertEquals(List.of(new DeathRecap.Row("Goblin", 55, 2), new DeathRecap.Row("Wilk", 20, 1),
                new DeathRecap.Row("Ula (gracz)", 10, 1)), rep.rows());
        assertTrue(r.build("x", 0, false, 61_000).rows().isEmpty(), "hits cleared after a death");
        List<String[]> rows = DeathRecap.rows(rep);
        assertEquals("Goblin", rows.get(0)[1]);
        assertTrue(rows.get(1)[1].contains("1234 exp") && rows.get(1)[1].contains("błogosławieństwu"));
        assertTrue(rows.get(2)[1].startsWith("55 obrażeń w 2 ciosach"), rows.get(2)[1]);
        assertTrue(rows.get(3)[1].startsWith("20 obrażeń w 1 ciosie"), rows.get(3)[1]);
    }

    @Test
    void rowsSurviveTheNetworkMessage() {
        List<String[]> rows = List.of(new String[] {"Zabójca", "Wilk; alfa"}, new String[] {"Strata", "10 exp"});
        String[] msg = new String[3];
        msg[0] = "DEATHLOG";
        System.arraycopy(DeathRecap.encode(rows), 0, msg, 1, 2);
        List<String[]> back = DeathRecap.decode(msg, 1);
        assertEquals(2, back.size());
        assertEquals("Wilk, alfa", back.get(0)[1]);
        assertEquals("10 exp", back.get(1)[1]);
    }
}
