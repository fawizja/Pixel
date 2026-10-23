package pl.fawizja.pixel.movement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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

    /** Kierunki bez skosow (N, E, S, W - pierwsze cztery w {@link Direction}). */
    private static final Direction[] ORTHOGONAL = Arrays.copyOf(Direction.values(), 4);

    public static Direction nextStep(WorldMap world, Position from, Position goal, int stopDistance,
                                     int maxNodes, Predicate<Position> blocked) {
        return nextStep(world, from, goal, stopDistance, maxNodes, blocked, Direction.values());
    }

    /**
     * Krok potwora jak w Tibii: najpierw droga wylacznie N/S/E/W, a skos tylko wtedy, gdy bez niego nie da sie
     * dojsc do celu (waskie przejscie po skosie, zablokowane pola) w limicie wezlow.
     */
    public static Direction nextStepOrthogonalFirst(WorldMap world, Position from, Position goal, int stopDistance,
                                                    int maxNodes, Predicate<Position> blocked) {
        Direction d = nextStep(world, from, goal, stopDistance, maxNodes, blocked, ORTHOGONAL);
        return d != null ? d : nextStep(world, from, goal, stopDistance, maxNodes, blocked, Direction.values());
    }

    private static Direction nextStep(WorldMap world, Position from, Position goal, int stopDistance,
                                      int maxNodes, Predicate<Position> blocked, Direction[] dirs) {
        if (from.z() != goal.z()) return null;
        if (from.distanceTo(goal) <= stopDistance) return null;
        Map<Position, Direction> firstStep = new HashMap<>();
        ArrayDeque<Position> queue = new ArrayDeque<>();
        firstStep.put(from, null);
        queue.add(from);
        int visited = 0;
        while (!queue.isEmpty() && visited++ < maxNodes) {
            Position p = queue.poll();
            for (Direction d : dirs) {
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

    /**
     * Pelna najkrotsza droga (bez pola startowego, z celem na koncu) po jednym pietrze, albo pusta lista,
     * gdy celu nie da sie osiagnac w limicie wezlow. Przejscia na inne pietra (schody, drabiny) omija,
     * chyba ze sa celem. Tablice zamiast map - wystarcza na cale pietro przy jednym wywolaniu.
     */
    public static List<Position> path(WorldMap world, Position from, Position goal, int maxNodes) {
        return path(world, from, goal, maxNodes, null);
    }

    /**
     * Droga omijajaca pola {@code avoid} (np. pulapki), a gdy takiej nie ma - zwykla najkrotsza droga.
     */
    public static List<Position> pathAvoiding(WorldMap world, Position from, Position goal, int maxNodes,
                                              java.util.function.Predicate<Position> avoid) {
        List<Position> safe = path(world, from, goal, maxNodes, avoid);
        return safe.isEmpty() ? path(world, from, goal, maxNodes, null) : safe;
    }

    private static List<Position> path(WorldMap world, Position from, Position goal, int maxNodes,
                                       java.util.function.Predicate<Position> avoid) {
        if (from.z() != goal.z() || from.equals(goal) || !world.isWalkable(goal)) return Collections.emptyList();
        int w = world.width(), h = world.height();
        if (goal.x() < 0 || goal.y() < 0 || goal.x() >= w || goal.y() >= h) return Collections.emptyList();
        int[] parent = new int[w * h];
        Arrays.fill(parent, -1);
        int start = from.y() * w + from.x();
        int target = goal.y() * w + goal.x();
        parent[start] = start;
        int[] queue = new int[w * h];
        int head = 0, tail = 0;
        queue[tail++] = start;
        int z = from.z();
        int visited = 0;
        while (head < tail && visited++ < maxNodes) {
            int cur = queue[head++];
            int cx = cur % w, cy = cur / w;
            for (Direction d : Direction.values()) {
                int nx = cx + d.dx, ny = cy + d.dy;
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                int n = ny * w + nx;
                if (parent[n] != -1) continue;
                Position np = new Position(nx, ny, z);
                if (!world.isWalkable(np)) continue;
                if (d.diagonal() && (!world.isWalkable(new Position(cx + d.dx, cy, z))
                        || !world.isWalkable(new Position(cx, cy + d.dy, z)))) continue;
                if (n != target && world.transitionAt(np) != null) continue;
                if (n != target && avoid != null && avoid.test(np)) continue;
                parent[n] = cur;
                if (n == target) {
                    List<Position> out = new ArrayList<>();
                    for (int c = n; c != start; c = parent[c]) out.add(new Position(c % w, c / w, z));
                    Collections.reverse(out);
                    return out;
                }
                queue[tail++] = n;
            }
        }
        return Collections.emptyList();
    }
}
