package pl.fawizja.pixel.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.CharacterStats;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.vocation.Vocation;

class StatsReportTest {
    @Test
    void playTimeDeathsAndBestLevelAreCountedAndSaved() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        GameSession s = new GameSession(TestData.ITEMS, TestData.world(), pc);
        for (long t = 0; t <= 90_000; t += 500) s.update(t, null);
        assertEquals(90_000, pc.stats().playMs());
        int level = pc.level();
        assertEquals(level, pc.stats().bestLevel());

        pc.applyDeathPenalty(1);
        pc.applyDeathPenalty(1);
        assertEquals(2, pc.stats().deaths());
        pc.bestiary().record("wolf", false);
        pc.bestiary().record("wolf", false);

        List<String[]> rows = StatsReport.rows(pc);
        assertEquals("1 min", value(rows, "Czas gry"));
        assertEquals("2", value(rows, "Śmierci"));
        assertEquals("2 (gatunki: 1)", value(rows, "Pokonane potwory"));
        if (pc.level() < level) assertTrue(value(rows, "Poziom").contains("najwyższy " + level));

        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertEquals(pc.stats().encode(), back.stats().encode());
        PlayerCharacter mirror = TestData.newCharacter(Vocation.MONK);
        mirror.copyStateFrom(back);
        assertEquals(2, mirror.stats().deaths());
    }

    @Test
    void playTimeIsDescribedInMinutesHoursAndDays() {
        assertEquals("0 min", CharacterStats.describePlayTime(59_000));
        assertEquals("1 h 05 min", CharacterStats.describePlayTime(65 * 60_000L));
        assertEquals("2 d 3 h 00 min", CharacterStats.describePlayTime((51 * 60) * 60_000L));
        CharacterStats st = new CharacterStats();
        st.restore("zepsute");
        assertEquals("0,0,0,0,0,0,0", st.encode());
        st.restore("5000,3");
        assertEquals("5000,3,0,0,0,0,0", st.encode());
        st.restore("5000,3,40,2,7");
        assertEquals(7, st.restedKills());
        st.restore("5000,3,40,2,7,4");
        assertEquals(4, st.duelsWon());
        st.restore("5000,3,40,2,7,4,9");
        assertEquals(9, st.rareDrops());
    }

    private static String value(List<String[]> rows, String label) {
        for (String[] r : rows) if (r[0].equals(label)) return r[1];
        return null;
    }
}
