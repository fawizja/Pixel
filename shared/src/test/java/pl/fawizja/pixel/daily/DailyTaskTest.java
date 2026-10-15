package pl.fawizja.pixel.daily;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.MonsterType;

class DailyTaskTest {

    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);

    @Test
    void theMonsterFitsTheLevelIsNeverABossAndRepeatsForTheSameDay() {
        List<MonsterType> all = new ArrayList<>(REG.all());
        for (int level : new int[] {8, 40, 150, 600, 1000}) {
            for (long day = 0; day < 20; day++) {
                MonsterType t = DailyTask.pick("Ula", day, level, all);
                assertFalse(t.boss());
                assertTrue(t.level() >= level / 2 && t.level() <= level * 6 / 5 + 5, level + " -> " + t.key());
                assertEquals(t, DailyTask.pick("ULA", day, level, all), "same name and day, same task");
            }
        }
        DailyTask d = new DailyTask();
        d.assign("Ula", 7, 8, List.of(REG.get("wolf")));
        assertEquals("wolf", d.monster());
        assertFalse(d.onKill("goblin", 7));
        assertFalse(d.onKill("wolf", 8), "yesterday's task does not count");
        for (int i = 1; i < d.target(); i++) assertFalse(d.onKill("wolf", 7));
        assertTrue(d.onKill("wolf", 7));
        assertFalse(d.onKill("wolf", 7), "no counting past the target");
        DailyTask back = new DailyTask();
        back.restore(d.encode());
        assertEquals(d.encode(), back.encode());
    }
}
