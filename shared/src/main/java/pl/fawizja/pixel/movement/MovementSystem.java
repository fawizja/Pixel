package pl.fawizja.pixel.movement;

import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.Transition;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Ruch po kafelkach z kolizjami i przejsciami miedzy pietrami.
 * Ta sama logika ma dzialac na serwerze (walidacja) i lokalnie (przewidywanie).
 */
public final class MovementSystem {

    private final WorldMap world;
    private final int ropeItemId;

    public MovementSystem(WorldMap world, int ropeItemId) {
        this.world = world;
        this.ropeItemId = ropeItemId;
    }

    /** Czy krok z pozycji w danym kierunku jest dozwolony (bez scinania rogow po skosie). */
    public boolean canStep(Position from, Direction d) {
        Position to = from.translate(d.dx, d.dy);
        if (!world.isWalkable(to)) return false;
        if (d.diagonal()) {
            return world.isWalkable(from.translate(d.dx, 0)) && world.isWalkable(from.translate(0, d.dy));
        }
        return true;
    }

    /** Wykonuje krok. Wejscie na schody/dziure/tunel od razu przenosi na pietro docelowe. */
    public MoveResult step(PlayerCharacter pc, Direction d) {
        if (pc.isDead()) return MoveResult.DEAD;
        Position from = pc.position();
        if (!canStep(from, d)) return MoveResult.BLOCKED;
        Position to = from.translate(d.dx, d.dy);
        Transition t = world.transitionAt(to);
        if (t != null && t.type().triggeredByWalking()) {
            pc.setPosition(t.to());
            return MoveResult.CHANGED_FLOOR;
        }
        pc.setPosition(to);
        return MoveResult.MOVED;
    }

    /** Uzycie drabiny lub miejsca na line na kafelku postaci albo obok niej. */
    public MoveResult use(PlayerCharacter pc) {
        if (pc.isDead()) return MoveResult.DEAD;
        Position p = pc.position();
        Transition best = null;
        for (int dy = -1; dy <= 1 && best == null; dy++) {
            for (int dx = -1; dx <= 1 && best == null; dx++) {
                Transition t = world.transitionAt(p.translate(dx, dy));
                if (t != null && !t.type().triggeredByWalking()) best = t;
            }
        }
        if (best == null) return MoveResult.NOTHING_TO_USE;
        if (best.type().requiresRope() && pc.backpack().count(ropeItemId) == 0) return MoveResult.NEEDS_ROPE;
        pc.setPosition(best.to());
        return MoveResult.CHANGED_FLOOR;
    }

    /** Czas trwania kroku w ms dla postaci (bieganie skraca krok o 15%). */
    public static long stepDurationMs(PlayerCharacter pc, Direction d) {
        long ms = Balance.stepDurationMs(pc.speed(), d.diagonal());
        return pc.isRunning() ? Math.round(ms * 0.85) : ms;
    }
}
