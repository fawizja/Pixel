package pl.fawizja.pixel.combat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class LootLogTest {
    @Test
    void keepsTheNewestEntriesFirstUpToTheLimit() {
        LootLog log = new LootLog();
        log.record(0, "Wilk", List.of(), false);
        assertEquals("nic", log.rows(0).get(0)[1]);
        for (int i = 1; i <= LootLog.MAX + 5; i++) log.record(i * 1000L, "Goblin " + i, List.of("3 gp", "1x kość"), false);
        assertEquals(LootLog.MAX, log.size());
        String[] newest = log.rows(LootLog.MAX * 1000L + 5000).get(0);
        assertEquals("Goblin " + (LootLog.MAX + 5) + " (przed chwilą)", newest[0]);
        assertEquals("3 gp, 1x kość", newest[1]);
        assertEquals("2 h temu", LootLog.ago(2 * 3_600_000L + 5));
    }
}
