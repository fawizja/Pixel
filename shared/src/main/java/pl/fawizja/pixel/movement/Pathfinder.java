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

    /** Koszt prostego kroku w {@link #nextStepTimed}. */
    static final int TIMED_STEP = 100;
    /** Krok po skosie trwa tyle co {@link MovementSystem#DIAGONAL_STEP_FACTOR} prostych - tak tez go liczy poscig. */
    static final int TIMED_DIAGONAL = TIMED_STEP * MovementSystem.DIAGONAL_STEP_FACTOR;

    /**
     * Pierwszy krok najszybszej drogi (A* po czasie marszu) do pola w odleglosci stopDistance od celu: proste kroki,
     * a skos tam, gdzie sie oplaca albo jest jedynym przejsciem (szczelina miedzy drzewami/krzakami/glazami wg
     * {@link WorldMap#diagonalCornersOpen}). Ograniczone do maxNodes rozwinietych pol; null = brak drogi w limicie.
     */
    public static Direction nextStepTimed(WorldMap world, Position from, Position goal, int stopDistance,
                                          int maxNodes, Predicate<Position> blocked) {
        if (from.z() != goal.z()) return null;
        if (from.distanceTo(goal) <= stopDistance) return null;
        Map<Position, Integer> best = new HashMap<>();
        java.util.Set<Position> closed = new java.util.HashSet<>();
        // {f, -g, kolejnosc}: najmniejsze f, przy remisie glebszy wezel
        java.util.PriorityQueue<Object[]> open = new java.util.PriorityQueue<>((a, b) -> {
            int c = Integer.compare((int) a[0], (int) b[0]);
            if (c != 0) return c;
            c = Integer.compare((int) b[1], (int) a[1]);
            return c != 0 ? c : Integer.compare((int) a[4], (int) b[4]);
        });
        int seq = 0;
        best.put(from, 0);
        open.add(new Object[] {timedH(from, goal, stopDistance), 0, from, null, seq++});
        int expanded = 0;
        while (!open.isEmpty() && expanded < maxNodes) {
            Object[] top = open.poll();
            Position p = (Position) top[2];
            if (!closed.add(p)) continue;
            Direction first = (Direction) top[3];
            if (first != null && p.distanceTo(goal) <= stopDistance) return first;
            expanded++;
            int g = (int) top[1];
            for (Direction d : Direction.values()) {
                Position n = p.translate(d.dx, d.dy);
                if (closed.contains(n)) continue;
                if (!world.isWalkable(n)) continue;
                if (!world.diagonalCornersOpen(p, d)) continue;
                if (blocked.test(n)) continue;
                int ng = g + (d.diagonal() ? TIMED_DIAGONAL : TIMED_STEP);
                Integer old = best.get(n);
                if (old != null && old <= ng) continue;
                best.put(n, ng);
                open.add(new Object[] {ng + timedH(n, goal, stopDistance), ng, n, first == null ? d : first, seq++});
            }
        }
        return null;
    }

    private static int timedH(Position p, Position goal, int stop) {
        return TIMED_STEP * Math.max(0, p.distanceTo(goal) - stop);
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
                if (!world.diagonalCornersOpen(p, d)) continue;
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

    /** Koszt kroku prostego (N/S/E/W) w trasie marszu. */
    static final int STEP_COST = 100;
    /**
     * Koszt kroku po skosie: krok trwa jak trzy proste ({@link MovementSystem#DIAGONAL_STEP_FACTOR}), a do tego
     * zapas - skos tylko wtedy, gdy wyraznie skraca droge (przecisniecie miedzy drzewami), nigdy na otwartym polu.
     */
    static final int DIAGONAL_COST = 350;
    /** Drobna kara za zmiane kierunku: z rownie krotkich tras wygrywa prosta / w ksztalcie L, nie zygzak. */
    static final int TURN_COST = 1;

    /** Klasa kierunku kroku: 0 poziomo, 1 pionowo, 2 skos, 3 start (brak poprzedniego kroku). */
    private static int dirClass(Direction d) {
        return d.diagonal() ? 2 : d.dx != 0 ? 0 : 1;
    }

    /**
     * A* jak marsz w Tibii: kroki proste, skos tylko gdy bez niego droga jest wyraznie dluzsza (waskie przejscie
     * po skosie miedzy przeszkodami), proste odcinki zamiast zygzaka. Stan = pole + klasa ostatniego kroku.
     */
    private static List<Position> path(WorldMap world, Position from, Position goal, int maxNodes,
                                       java.util.function.Predicate<Position> avoid) {
        if (from.z() != goal.z() || from.equals(goal) || !world.isWalkable(goal)) return Collections.emptyList();
        int w = world.width(), h = world.height();
        if (goal.x() < 0 || goal.y() < 0 || goal.x() >= w || goal.y() >= h) return Collections.emptyList();
        int z = from.z();
        long startState = ((long) from.y() * w + from.x()) * 4 + 3;
        int target = goal.y() * w + goal.x();
        Map<Long, Integer> best = new HashMap<>();
        Map<Long, Long> parent = new HashMap<>();
        java.util.Set<Long> closed = new java.util.HashSet<>();
        // {f, -g, state}: najmniejsze f, przy remisie glebszy wezel (szybciej do celu)
        java.util.PriorityQueue<long[]> open = new java.util.PriorityQueue<>((a, b) ->
                a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
        best.put(startState, 0);
        open.add(new long[] {heuristic(from.x(), from.y(), goal), 0, startState});
        int expanded = 0;
        while (!open.isEmpty() && expanded < maxNodes) {
            long[] top = open.poll();
            long state = top[2];
            if (!closed.add(state)) continue;
            int g = (int) -top[1];
            int tile = (int) (state / 4), cls = (int) (state % 4);
            if (tile == target) {
                List<Position> out = new ArrayList<>();
                for (long s = state; s != startState; s = parent.get(s)) {
                    int t = (int) (s / 4);
                    out.add(new Position(t % w, t / w, z));
                }
                Collections.reverse(out);
                return out;
            }
            expanded++;
            int cx = tile % w, cy = tile / w;
            Position cp = new Position(cx, cy, z);
            for (Direction d : Direction.values()) {
                int nx = cx + d.dx, ny = cy + d.dy;
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                int n = ny * w + nx;
                int ncls = dirClass(d);
                long ns = (long) n * 4 + ncls;
                if (closed.contains(ns)) continue;
                Position np = new Position(nx, ny, z);
                if (!world.isWalkable(np)) continue;
                if (!world.diagonalCornersOpen(cp, d)) continue;
                if (n != target && world.transitionAt(np) != null) continue;
                if (n != target && avoid != null && avoid.test(np)) continue;
                int ng = g + (d.diagonal() ? DIAGONAL_COST : STEP_COST) + (cls != 3 && cls != ncls ? TURN_COST : 0);
                Integer old = best.get(ns);
                if (old != null && old <= ng) continue;
                best.put(ns, ng);
                parent.put(ns, state);
                open.add(new long[] {ng + heuristic(nx, ny, goal), -ng, ns});
            }
        }
        return Collections.emptyList();
    }

    private static long heuristic(int x, int y, Position goal) {
        return (long) STEP_COST * (Math.abs(goal.x() - x) + Math.abs(goal.y() - y));
    }
}
