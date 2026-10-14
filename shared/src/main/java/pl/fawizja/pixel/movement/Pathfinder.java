package pl.fawizja.pixel.movement;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Ograniczone wyszukiwanie wszerz (BFS) na jednym pietrze. Zwraca pierwszy krok najkrotszej
 * drogi do kafelka w odleglosci stopDistance od celu. Limit wezlow chroni wydajnosc.
 */
public final class Pathfinder {

    private Pathfinder() {}

    public static Direction nextStep(WorldMap world, Position from, Position goal, int stopDistance,
                                     int maxNodes, Predicate<Position> blocked) {
        if (from.z() != goal.z()) return null;
        if (from.distanceTo(goal) <= stopDistance) return null;
        Map<Position, Direction> firstStep = new HashMap<>();
        ArrayDeque<Position> queue = new ArrayDeque<>();
        firstStep.put(from, null);
        queue.add(from);
        int visited = 0;
        while (!queue.isEmpty() && visited++ < maxNodes) {
            Position p = queue.poll();
            for (Direction d : Direction.values()) {
                Position n = p.translate(d.dx, d.dy);
                if (firstStep.containsKey(n)) continue;
                if (!world.isWalkable(n)) continue;
                if (d.diagonal() && (!world.isWalkable(p.translate(d.dx, 0)) || !world.isWalkable(p.translate(0, d.dy)))) continue;
                if (blocked.test(n)) continue;
                boolean reached = n.distanceTo(goal) <= stopDistance;
                Direction first = p.equals(from) ? d : firstStep.get(p);
                if (reached) return first;
                firstStep.put(n, first);
                queue.add(n);
            }
        }
        return null;
    }
}
