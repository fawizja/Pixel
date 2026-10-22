package pl.fawizja.pixel.npc;

import java.util.ArrayList;
import java.util.List;

import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.Reachability;

/**
 * Przejazdy woznicy miedzy miastami i rejsy kapitanow miedzy portami (trasy z ships.csv):
 * cena 3 gp za pole odleglosci miedzy NPC, co najmniej 100 gp.
 */
public final class Travel {

    public static final long GP_PER_TILE = 3;
    public static final long MIN_PRICE = 100;

    /** Cel przejazdu: woznica w innym miescie i cena. */
    public record Route(Npc to, long price) { }

    private Travel() { }

    public static long price(Position from, Position to) {
        long d = Math.abs((long) from.x() - to.x()) + Math.abs((long) from.y() - to.y());
        long p = (d * GP_PER_TILE + 5) / 10 * 10;
        return Math.max(MIN_PRICE, p);
    }

    /** Woznice w innych miastach (kolejnosc z npcs.csv) albo porty, do ktorych plynie statek kapitana. */
    public static List<Route> routes(NpcRegistry npcs, Npc from) {
        List<Route> out = new ArrayList<>();
        if (from.role() == NpcRole.CAPTAIN) {
            for (Npc n : npcs.seaRoutes(from)) out.add(new Route(n, price(from.position(), n.position())));
            return out;
        }
        for (Npc n : npcs.all()) {
            if (n.role() != NpcRole.CARRIER || n.town().equalsIgnoreCase(from.town())) continue;
            out.add(new Route(n, price(from.position(), n.position())));
        }
        return out;
    }

    /** Wszystkie przejazdy jako polaczenia do kontroli swiata (z pola NPC do pola NPC docelowego). */
    public static List<Reachability.Link> links(NpcRegistry npcs) {
        List<Reachability.Link> out = new ArrayList<>();
        for (Npc n : npcs.all()) {
            if (n.role() != NpcRole.CARRIER && n.role() != NpcRole.CAPTAIN) continue;
            for (Route r : routes(npcs, n)) out.add(new Reachability.Link(n.position(), r.to().position()));
        }
        return out;
    }
}
