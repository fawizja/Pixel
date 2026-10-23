package pl.fawizja.pixel.training;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pl.fawizja.pixel.world.FloorMap;
import pl.fawizja.pixel.world.Layer;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.Town;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Manekiny treningowe: po 2 w strefie ochronnej kazdego miasta. Miejsca liczone powtarzalnie z mapy (klient
 * i serwer dostaja te same): najblizsze swiatyni wolne pola na otwartej przestrzeni (8 sasiadow przechodnich),
 * bez obiektow i przejsc, poza kamienna posadzka swiatyn, co najmniej 3 pola od swiatyni, 3 od siebie i 3 od NPC. Manekin blokuje ruch.
 */
public final class TrainingDummies {

    public static final int PER_TOWN = 2;
    static final int MIN_FROM_TEMPLE = 3;
    static final int MIN_APART = 3;
    private static final int SEARCH_LIMIT = 4000;

    private final List<Position> dummies;
    private final Set<Position> set;

    public TrainingDummies(List<Position> dummies) {
        this.dummies = List.copyOf(dummies);
        this.set = new HashSet<>(dummies);
    }

    public List<Position> all() { return dummies; }

    public boolean at(Position p) { return set.contains(p); }

    /** Manekin obok pola (8 kierunkow, bez samego pola); null gdy brak. */
    public Position near(Position p) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                Position q = p.translate(dx, dy);
                if (set.contains(q)) return q;
            }
        }
        return null;
    }

    public static TrainingDummies generate(WorldMap world, java.util.Collection<Position> avoid) {
        List<Position> out = new ArrayList<>();
        for (Town town : world.towns()) out.addAll(forTown(world, town.temple(), avoid));
        return new TrainingDummies(out);
    }

    /** BFS od swiatyni po polach przechodnich strefy ochronnej; kolejnosc sasiadow stala = wynik powtarzalny. */
    static List<Position> forTown(WorldMap world, Position temple, java.util.Collection<Position> avoid) {
        List<Position> found = new ArrayList<>();
        FloorMap f = world.floor(temple.z());
        if (f == null) return found;
        Set<Position> seen = new HashSet<>();
        ArrayDeque<Position> queue = new ArrayDeque<>();
        queue.add(temple);
        seen.add(temple);
        int visited = 0;
        while (!queue.isEmpty() && found.size() < PER_TOWN && visited++ < SEARCH_LIMIT) {
            Position p = queue.poll();
            if (suitable(world, f, p, temple, found, avoid)) found.add(p);
            for (int[] d : new int[][] {{0, -1}, {1, 0}, {0, 1}, {-1, 0}}) {
                Position q = p.translate(d[0], d[1]);
                if (seen.add(q) && world.isWalkable(q) && world.isProtectionZone(q)) queue.add(q);
            }
        }
        return found;
    }

    /** Posadzki wnetrz (swiatynia, domy): manekin ma stac na placu albo ulicy, nie przed oltarzem. */
    static final Set<String> INDOOR = Set.of("stone_floor", "marble_floor", "wood_floor", "sandstone_floor", "frozen_floor");

    private static boolean indoors(WorldMap world, FloorMap f, Position p) {
        if (world.tiles() == null) return false;
        pl.fawizja.pixel.world.TileType t = world.tiles().get(f.get(Layer.GROUND, p.x(), p.y()));
        return t != null && INDOOR.contains(t.key());
    }

    private static boolean suitable(WorldMap world, FloorMap f, Position p, Position temple, List<Position> found,
                                    java.util.Collection<Position> avoid) {
        if (chebyshev(p, temple) < MIN_FROM_TEMPLE) return false;
        for (Position o : found) if (chebyshev(p, o) < MIN_APART) return false;
        for (Position o : avoid) if (o.z() == p.z() && chebyshev(p, o) < MIN_APART) return false;
        if (f.get(Layer.OBJECT, p.x(), p.y()) != 0 || world.transitionAt(p) != null) return false;
        if (indoors(world, f, p)) return false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                Position q = p.translate(dx, dy);
                if (!world.isWalkable(q) || world.transitionAt(q) != null || !world.isProtectionZone(q)) return false;
            }
        }
        return true;
    }

    private static int chebyshev(Position a, Position b) {
        return Math.max(Math.abs(a.x() - b.x()), Math.abs(a.y() - b.y()));
    }
}
