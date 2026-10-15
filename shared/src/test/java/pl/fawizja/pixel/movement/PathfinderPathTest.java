package pl.fawizja.pixel.movement;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

class PathfinderPathTest {

    private final WorldMap world = TestData.varden();

    @Test
    void pathIsContiguousWalkableAndEndsAtTheGoal() {
        Position from = world.spawn();
        Position goal = new Position(53, 22, 0); // most nad rzeka
        List<Position> path = Pathfinder.path(world, from, goal, 100_000);
        assertFalse(path.isEmpty());
        assertEquals(goal, path.get(path.size() - 1));
        Position prev = from;
        for (Position p : path) {
            assertTrue(world.isWalkable(p), "walkable " + p);
            assertTrue(Math.abs(p.x() - prev.x()) <= 1 && Math.abs(p.y() - prev.y()) <= 1, "adjacent " + prev + " " + p);
            if (!p.equals(goal)) assertNull(world.transitionAt(p), "no stairs on the way " + p);
            prev = p;
        }
    }

    @Test
    void unreachableOrBlockedGoalsGiveNoPath() {
        Position from = world.spawn();
        assertTrue(Pathfinder.path(world, from, new Position(53, 5, 0), 100_000).isEmpty(), "river");
        assertTrue(Pathfinder.path(world, from, new Position(18, 15, 0), 100_000).isEmpty(), "wall");
        assertTrue(Pathfinder.path(world, from, new Position(31, 16, -1), 100_000).isEmpty(), "other floor");
        assertTrue(Pathfinder.path(world, from, from, 100_000).isEmpty(), "already there");
        assertTrue(Pathfinder.path(world, from, new Position(53, 22, 0), 5).isEmpty(), "node limit");
    }
}
