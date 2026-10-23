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

    @Test
    void walkingRouteAvoidsTrapsWhenThereIsAnotherWay() {
        Position from = world.spawn();
        Position goal = new Position(53, 22, 0);
        List<Position> plain = Pathfinder.path(world, from, goal, 100_000);
        Position trap = plain.get(plain.size() / 2); // pulapka w polowie najkrotszej drogi
        List<Position> safe = Pathfinder.pathAvoiding(world, from, goal, 100_000, trap::equals);
        assertFalse(safe.isEmpty());
        assertFalse(safe.contains(trap));
        assertEquals(goal, safe.get(safe.size() - 1));
        // gdy kazda droga prowadzi przez pulapke, marsz i tak dochodzi do celu
        List<Position> forced = Pathfinder.pathAvoiding(world, from, goal, 100_000, p -> !p.equals(goal));
        assertEquals(plain, forced);
        assertEquals(List.of(goal).get(0), Pathfinder.pathAvoiding(world, from, goal, 100_000, goal::equals).get(plain.size() - 1));
    }

    @Test
    void monstersStepWithoutDiagonalsUnlessTheyMust() {
        // otwarte pole: cel po skosie, a pierwszy krok jest prosty
        Position from = world.spawn();
        Position goal = from.translate(4, 4);
        pl.fawizja.pixel.world.Direction d = Pathfinder.nextStepOrthogonalFirst(world, from, goal, 1, 400, p -> false);
        assertNotNull(d);
        assertFalse(d.diagonal(), "first step " + d);
        // gdy prosta droga jest zablokowana (wszystkie pola N/S/E/W zajete), wolno pojsc po skosie
        java.util.Set<Position> blocked = new java.util.HashSet<>();
        for (pl.fawizja.pixel.world.Direction o : new pl.fawizja.pixel.world.Direction[] {
                pl.fawizja.pixel.world.Direction.NORTH, pl.fawizja.pixel.world.Direction.EAST,
                pl.fawizja.pixel.world.Direction.SOUTH, pl.fawizja.pixel.world.Direction.WEST}) {
            blocked.add(from.translate(o.dx, o.dy));
        }
        assertTrue(Pathfinder.nextStepOrthogonalFirst(world, from, goal, 1, 400, blocked::contains).diagonal());
        assertNull(Pathfinder.nextStepOrthogonalFirst(world, from, goal, 1, 400, p -> true));
    }
}
