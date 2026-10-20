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

    @Test
    void whenTheWeekEndsThePodiumIsReturnedOnce() {
        WeeklyRanking w = new WeeklyRanking();
        Ranking r = new Ranking();
        assertTrue(w.closeWeek(r, MONDAY).isEmpty(), "first use only starts tracking");
        w.observe("Ola", 1_000, MONDAY);
        w.observe("Ula", 5_000, MONDAY);
        w.observe("Kuba", 0, MONDAY);
        r.put(new Ranking.Entry("Ola", Vocation.PALADIN, 30, 41_000));
        r.put(new Ranking.Entry("Ula", Vocation.DRUID, 40, 15_000));
        r.put(new Ranking.Entry("Kuba", Vocation.MONK, 50, 0));
        assertTrue(w.closeWeek(r, MONDAY + 3 * DAY).isEmpty(), "same week");
        WeeklyRanking saved = WeeklyRanking.decode(w.encode());
        java.util.List<RankingView.Row> podium = saved.closeWeek(r, MONDAY + 7 * DAY);
        assertEquals(2, podium.size(), "no gain, no place");
        assertEquals("Ola", podium.get(0).name());
        assertEquals(40_000, podium.get(0).level());
        assertTrue(saved.closeWeek(r, MONDAY + 7 * DAY + 60_000).isEmpty(), "paid once");
    }
}
