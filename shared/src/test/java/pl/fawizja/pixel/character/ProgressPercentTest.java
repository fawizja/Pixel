package pl.fawizja.pixel.character;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.balance.Balance;

class ProgressPercentTest {
    @Test
    void experienceAndSkillProgressArePercentages() {
        long cur = Balance.experienceForLevel(20), next = Balance.experienceForLevel(21);
        assertEquals(0, Balance.percentToNextLevel(cur, 20));
        assertEquals(50, Balance.percentToNextLevel(cur + (next - cur) / 2, 20));
        assertEquals(99, Balance.percentToNextLevel(next - 1, 20));
        assertEquals(100, Balance.percentToNextLevel(0, Balance.MAX_LEVEL));

        Skills s = new Skills();
        assertEquals(0, s.percentToNext(Skill.SWORD));
        s.addTries(Skill.SWORD, Skills.triesForNext(s.level(Skill.SWORD)) / 4);
        assertEquals(25, s.percentToNext(Skill.SWORD), 1);
    }
}
