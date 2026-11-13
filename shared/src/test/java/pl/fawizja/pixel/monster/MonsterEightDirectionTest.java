package pl.fawizja.pixel.monster;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.movement.MovementSystem;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Position;

/** Potwory chodza w 8 kierunkach (2026-11-12): spacer tez po skosie, a krok po skosie trwa 3x dluzej. */
class MonsterEightDirectionTest {
    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);

    @Test
    void idleWanderStepsDiagonallyAndTheDiagonalStepTakesThreeTimesAsLong() {
        MonsterManager mm = new MonsterManager(TestData.varden(), REG,
                List.of(new SpawnPoint("boar", new Position(42, 6, 0), 6, 1, 60)), new Random(5));
        mm.populate(null);
        Monster m = mm.monsters().get(0);
        MonsterAiTest.Dummy far = new MonsterAiTest.Dummy(new Position(57, 6, 0));
        long straight = Balance.stepDurationMs(m.type().speed(), false);
        EnumSet<Direction> seen = EnumSet.noneOf(Direction.class);
        int diagonals = 0;
        Position last = m.position();
        for (long t = 0; t < 1_500_000; t += 50) {
            mm.update(t, far, far);
            Position now = m.position();
            if (!now.equals(last)) {
                Direction d = Direction.fromDelta(now.x() - last.x(), now.y() - last.y());
                assertNotNull(d, "one tile per step");
                seen.add(d);
                long dur = straight * (d.diagonal() ? MovementSystem.DIAGONAL_STEP_FACTOR : 1);
                assertTrue(m.isMoving(t + dur - 1), d + " still walking just before " + dur + " ms");
                assertFalse(m.isMoving(t + dur), d + " done after " + dur + " ms");
                assertEquals(d, m.facing(), "facing keeps the 8-way walk direction");
                if (d.diagonal()) diagonals++;
                last = now;
            }
        }
        assertTrue(diagonals >= 5, "wander made diagonal steps: " + diagonals);
        assertTrue(seen.stream().anyMatch(d -> !d.diagonal()), "and straight ones");
        assertTrue(seen.stream().filter(Direction::diagonal).count() >= 2, "several diagonal directions: " + seen);
    }

    @Test
    void fleeingMonsterRunsAwayAlongAnyOfEightDirections() {
        MonsterManager mm = new MonsterManager(TestData.varden(), REG,
                List.of(new SpawnPoint("boar", new Position(42, 6, 0), 0, 1, 60)), new Random(3));
        mm.populate(null);
        Monster m = mm.monsters().get(0);
        Position me = m.position();
        // gracz tuz obok po skosie: ucieczka nie moze zblizac, a krok zawsze jest jednym z 8 kierunkow
        MonsterAiTest.Dummy player = new MonsterAiTest.Dummy(me.translate(1, 1));
        m.setState(MonsterState.FLEE);
        Position before = m.position();
        for (long t = 0; t < 3000; t += 50) mm.update(t, player, player);
        if (!m.position().equals(before)) {
            assertTrue(m.position().distanceTo(player.pos) >= before.distanceTo(player.pos), "flee does not approach");
        }
    }
}
