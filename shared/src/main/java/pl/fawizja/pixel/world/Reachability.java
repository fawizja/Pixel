package pl.fawizja.pixel.world;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Do ktorych pol da sie dojsc z punktu startu: pola przechodnie (4 kierunki), wszystkie przejscia miedzy pietrami
 * (schody, dziury, drabiny, liny - zakladamy line w plecaku) oraz dodatkowe polaczenia, np. trasy statkow.
 * Sluzy do kontroli swiata (testy): kazda swiatynia, spawn i NPC musi byc osiagalny, a kazde przejscie miec droge powrotu.
 */
public final class Reachability {

    /** Polaczenie jednokierunkowe (np. rejs statkiem z portu do portu). */
    public record Link(Position from, Position to) {}

    private final WorldMap world;
    private final Map<Integer, BitSet> visited = new HashMap<>();

    private Reachability(WorldMap world) {
        this.world = world;
    }

    public static Reachability from(WorldMap world, Position start, List<Link> links) {
        Reachability r = new Reachability(world);
        Map<Position, List<Position>> extra = new HashMap<>();
        for (Link l : links) extra.computeIfAbsent(l.from(), k -> new java.util.ArrayList<>()).add(l.to());
        ArrayDeque<Position> queue = new ArrayDeque<>();
        r.visit(start, queue);
        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        while (!queue.isEmpty()) {
            Position p = queue.poll();
            Transition t = world.transitionAt(p);
            if (t != null) r.visit(t.to(), queue);
            List<Position> more = extra.get(p);
            if (more != null) for (Position q : more) r.visit(q, queue);
            for (int k = 0; k < 4; k++) {
                Position n = new Position(p.x() + dx[k], p.y() + dy[k], p.z());
                // drabina i lina dzialaja tez z pola obok
                Transition nt = world.transitionAt(n);
                if (nt != null && !nt.type().triggeredByWalking()) r.visit(nt.to(), queue);
                r.visit(n, queue);
            }
        }
        return r;
    }

    private void visit(Position p, ArrayDeque<Position> queue) {
        FloorMap f = world.floor(p.z());
        if (f == null || !f.inBounds(p.x(), p.y()) || !world.isWalkable(p)) return;
        BitSet b = visited.computeIfAbsent(p.z(), k -> new BitSet());
        int i = p.y() * f.width() + p.x();
        if (b.get(i)) return;
        b.set(i);
        queue.add(p);
    }

    public boolean reached(Position p) {
        FloorMap f = world.floor(p.z());
        BitSet b = visited.get(p.z());
        return f != null && b != null && f.inBounds(p.x(), p.y()) && b.get(p.y() * f.width() + p.x());
    }

    /** Czy osiagniete jest pole p albo ktores z 8 sasiednich (NPC i obiekty stoja na polach nieprzechodnich). */
    public boolean reachedNear(Position p) {
        return reachedWithin(p, 1);
    }

    /** Czy osiagniete jest jakies pole w kwadracie o promieniu r wokol p (np. rozmowa z NPC przez lade). */
    public boolean reachedWithin(Position p, int r) {
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                if (reached(new Position(p.x() + dx, p.y() + dy, p.z()))) return true;
            }
        }
        return false;
    }

    /** Liczba osiagnietych pol na pietrze z. */
    public int count(int z) {
        BitSet b = visited.get(z);
        return b == null ? 0 : b.cardinality();
    }
}
