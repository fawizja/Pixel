package pl.fawizja.pixel.movement;

import java.util.function.Predicate;
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
    private Predicate<Position> occupied = p -> false;

    public MovementSystem(WorldMap world, int ropeItemId) {
        this.world = world;
        this.ropeItemId = ropeItemId;
    }

    /** Ustawia test zajetosci kafelka (np. przez potwory lub innych graczy). */
    public void setOccupancy(Predicate<Position> occupied) {
        this.occupied = occupied;
    }

    /**
     * Czy krok z pozycji w danym kierunku jest dozwolony. Skos nie scina rogu muru ani wody, ale miedzy drzewami,
     * krzakami i glazami mozna sie przecisnac jak w Tibii ({@link WorldMap#diagonalCornersOpen}).
     */
    public boolean canStep(Position from, Direction d) {
        Position to = from.translate(d.dx, d.dy);
        if (!world.isWalkable(to) || occupied.test(to) || world.openings().isClosedDoor(to)) return false;
        return world.diagonalCornersOpen(from, d);
    }

    /**
     * Wykonuje krok. Wejscie na schody/dziure/tunel od razu przenosi na pietro docelowe. Zamkniete drzwi zatrzymuja
     * (jak w Tibii otwiera je dopiero uzycie - dotkniecie drzwi), drzwi poziomu przepuszczaja tylko od poziomu.
     */
    public MoveResult step(PlayerCharacter pc, Direction d) {
        if (pc.isDead()) return MoveResult.DEAD;
        Position from = pc.position();
        Position to = from.translate(d.dx, d.dy);
        if (world.levelDoorAt(to) > pc.level() && world.isWalkable(to) && !occupied.test(to)) return MoveResult.LEVEL_TOO_LOW;
        if (world.openings().isClosedDoor(to) && world.isWalkable(to) && !occupied.test(to)) return MoveResult.DOOR_CLOSED;
        if (!canStep(from, d)) return MoveResult.BLOCKED;
        if (world.requiredLevelAt(to) > pc.level()) return MoveResult.LEVEL_TOO_LOW;
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

    /** Krok gracza po skosie trwa jak trzy zwykle kroki (wyczerpanie po skosie jak w Tibii). */
    public static final int DIAGONAL_STEP_FACTOR = 3;

    /** Czas trwania kroku w ms dla postaci (bieganie skraca krok o 15%, skos x3). */
    public static long stepDurationMs(PlayerCharacter pc, Direction d) {
        long ms = Balance.stepDurationMs(pc.speed(), false) * (d.diagonal() ? DIAGONAL_STEP_FACTOR : 1);
        if (pc.enchantments().active(pl.fawizja.pixel.item.Enchantments.Kind.SWIFTNESS)) ms = Math.round(ms * pl.fawizja.pixel.item.Enchantments.SWIFT_STEP);
        return pc.isRunning() ? Math.round(ms * 0.85) : ms;
    }
}
