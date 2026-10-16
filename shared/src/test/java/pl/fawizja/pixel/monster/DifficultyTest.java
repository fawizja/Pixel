package pl.fawizja.pixel.monster;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DifficultyTest {
    @Test
    void comparesTheMonsterWithTheCharacterLevel() {
        assertEquals(Difficulty.TRIVIAL, Difficulty.of(5, 100));
        assertEquals(Difficulty.EASY, Difficulty.of(40, 100));
        assertEquals(Difficulty.FAIR, Difficulty.of(8, 8));
        assertEquals(Difficulty.FAIR, Difficulty.of(125, 100));
        assertEquals(Difficulty.HARD, Difficulty.of(150, 100));
        assertEquals(Difficulty.DEADLY, Difficulty.of(300, 100));
        assertEquals(Difficulty.FAIR, Difficulty.of(1, 1), "a fresh character fights rats");
    }
}
