package pl.fawizja.pixel.daily;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LoginStreakTest {

    @Test
    void streakGrowsDailyWrapsAfterAWeekAndResetsAfterAGap() {
        LoginStreak s = new LoginStreak();
        assertEquals(1, s.claim(100));
        assertEquals(0, s.claim(100), "once per day");
        for (int d = 2; d <= 7; d++) assertEquals(d, s.claim(99 + d));
        assertEquals(1, s.claim(107), "after day 7 a new week");
        assertEquals(2, s.claim(108));
        assertEquals(1, s.claim(110), "a missed day resets");
    }

    @Test
    void rewardsGrowWithTheDayAndLevelAndSurviveSaving() {
        assertEquals(300, LoginStreak.rewardGold(1, 8));
        assertEquals(5_000, LoginStreak.rewardGold(7, 8));
        assertEquals(15_000, LoginStreak.rewardGold(7, 35));
        assertEquals(0, LoginStreak.rewardGold(8, 50));
        LoginStreak s = new LoginStreak();
        s.claim(200);
        s.claim(201);
        LoginStreak r = new LoginStreak();
        r.restore(s.encode());
        assertEquals(201, r.lastDay());
        assertEquals(2, r.streak());
        assertEquals(3, r.claim(202));
        r.restore("zepsute");
        assertEquals(-1, r.lastDay());
        assertEquals("", new LoginStreak().encode());
    }
}
