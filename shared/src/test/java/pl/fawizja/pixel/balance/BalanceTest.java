package pl.fawizja.pixel.balance;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pl.fawizja.pixel.vocation.Vocation;

class BalanceTest {

    @Test
    void experienceMatchesClassicCurveAtKeyLevels() {
        assertEquals(0, Balance.experienceForLevel(1));
        assertEquals(100, Balance.experienceForLevel(2));
        assertEquals(4200, Balance.experienceForLevel(8));
        assertEquals(15_694_800L, Balance.experienceForLevel(100));
    }

    @Test
    void experienceCurveIsStrictlyIncreasingAndExactUpTo1000() {
        long prevStep = 0;
        for (int l = 2; l <= Balance.MAX_LEVEL; l++) {
            long a = Balance.experienceForLevel(l - 1);
            long b = Balance.experienceForLevel(l);
            assertTrue(b > a, "level " + l);
            long poly = (long) l * l * l - 6L * l * l + 17L * l - 12L;
            assertEquals(0, (50L * poly) % 3, "formula must be integral at " + l);
            long step = b - a;
            assertTrue(step >= prevStep, "cost per level must not shrink at " + l);
            prevStep = step;
        }
        assertTrue(Balance.maxExperience() > 16_000_000_000L);
        assertTrue(Balance.maxExperience() < Long.MAX_VALUE / 1000);
    }

    @Test
    void levelForExperienceRoundTrips() {
        for (int l = 1; l <= Balance.MAX_LEVEL; l++) {
            long e = Balance.experienceForLevel(l);
            assertEquals(l, Balance.levelForExperience(e));
            if (l > 1) assertEquals(l - 1, Balance.levelForExperience(e - 1));
        }
        assertEquals(Balance.MAX_LEVEL, Balance.levelForExperience(Long.MAX_VALUE));
        assertEquals(1, Balance.levelForExperience(-5));
    }

    @Test
    void addExperienceSaturatesWithoutOverflow() {
        long max = Balance.maxExperience();
        assertEquals(max, Balance.addExperience(max - 1, Long.MAX_VALUE));
        assertEquals(max, Balance.addExperience(Long.MAX_VALUE - 1, Long.MAX_VALUE));
        assertEquals(10, Balance.addExperience(10, -100));
    }

    @ParameterizedTest
    @EnumSource(Vocation.class)
    void statsGrowMonotonicallyAndStayPositive(Vocation v) {
        int prevHp = 0, prevMp = 0;
        for (int l = 1; l <= Balance.MAX_LEVEL; l++) {
            int hp = Balance.maxHealth(v, l);
            int mp = Balance.maxMana(v, l);
            assertTrue(hp > 0 && mp > 0);
            assertTrue(hp >= prevHp && mp >= prevMp);
            prevHp = hp;
            prevMp = mp;
        }
        assertEquals(185, Balance.maxHealth(v, 8));
        assertEquals(90, Balance.maxMana(v, 8));
        assertTrue(Balance.maxHealth(v, 1000) < 100_000);
    }

    @Test
    void speedIsBoundedOnHighLevels() {
        assertTrue(Balance.speed(1000) > Balance.speed(8));
        assertTrue(Balance.tilesPerSecond(Balance.speed(1000)) <= Balance.MAX_TILES_PER_SECOND);
        assertTrue(Balance.stepDurationMs(Balance.speed(8), true) > Balance.stepDurationMs(Balance.speed(8), false));
    }

    @Test
    void bracketsCoverAllLevels() {
        assertEquals(LevelBracket.BEGINNER, LevelBracket.of(8));
        assertEquals(LevelBracket.BASIC, LevelBracket.of(21));
        assertEquals(LevelBracket.INTERMEDIATE, LevelBracket.of(100));
        assertEquals(LevelBracket.ENDGAME, LevelBracket.of(1000));
    }
}
