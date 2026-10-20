package pl.fawizja.pixel.ranking;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.vocation.Vocation;

class WeeklyRankingTest {

    static final long MONDAY = java.time.Instant.parse("2026-10-19T08:00:00Z").toEpochMilli();
    static final long DAY = 24 * 3600_000L;

    @Test
    void ranksExperienceGainedSinceMondayAndStartsOverNextWeek() {
        WeeklyRanking w = new WeeklyRanking();
        Ranking r = new Ranking();
        w.observe("Ola", 1_000, MONDAY);
        w.observe("Ula", 5_000, MONDAY);
        w.observe("Ola", 9_999, MONDAY + DAY); // pozniejsza obserwacja nie przesuwa punktu odniesienia
        r.put(new Ranking.Entry("Ola", Vocation.PALADIN, 30, 41_000));
        r.put(new Ranking.Entry("Ula", Vocation.DRUID, 40, 15_000));
        r.put(new Ranking.Entry("Kuba", Vocation.MONK, 50, 99_000));
        RankingView v = w.view(r, "Ula", 10, MONDAY + 2 * DAY);
        assertEquals(WeeklyRanking.TITLE, v.title());
        assertEquals(2, v.total(), "Kuba was not seen this week");
        assertEquals("Ola", v.rows().get(0).name());
        assertEquals(40_000, v.rows().get(0).level());
        assertEquals(2, v.mine());
        assertEquals(0, w.gained("Ola", 41_000, MONDAY + 7 * DAY), "a new week starts from zero");
        WeeklyRanking back = WeeklyRanking.decode(w.encode());
        assertEquals(10_000, back.gained("Ula", 15_000, MONDAY + DAY));
        assertTrue(WeeklyRanking.isWeeklyArg("tydzień"));
        assertEquals("2026-43", WeeklyRanking.week(MONDAY));
    }
}
