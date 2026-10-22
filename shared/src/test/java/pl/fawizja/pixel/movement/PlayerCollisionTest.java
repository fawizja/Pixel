package pl.fawizja.pixel.movement;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/** Etap 12: poza strefa ochronna gracze sie blokuja (jak w Tibii), w PZ mozna przez siebie przejsc; potwory nie wchodza na graczy. */
class PlayerCollisionTest {

    static GameSession session(Position at) {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        GameSession s = new GameSession(TestData.ITEMS, TestData.varden(), pc, null, new Random(1), 8);
        s.teleport(at, 0);
        return s;
    }

    @Test
    void otherPlayersBlockOutsideProtectionZonesOnly() {
        WorldMap w = TestData.varden();
        Position wild = new Position(10, 12, 0);
        assertFalse(w.isProtectionZone(wild.translate(1, 0)));
        GameSession s = session(wild);
        s.setOtherPlayers(p -> p.equals(wild.translate(1, 0)));
        assertFalse(s.predictStep(Direction.EAST, 0), "blocked by a player in the wild");

        Position[] pz = pzPair(w);
        GameSession t = session(pz[0]);
        t.setOtherPlayers(p -> p.equals(pz[1]));
        Direction d = Direction.fromDelta(pz[1].x() - pz[0].x(), pz[1].y() - pz[0].y());
        assertTrue(t.predictStep(d, 0), "players pass through each other in a protection zone");
    }

    @Test
    void monstersDoNotStepOntoAnyPlayer() {
        MonsterManager mm = new MonsterManager(TestData.varden(), MonsterRegistry.loadDefault(TestData.ITEMS),
                Collections.emptyList(), new Random(2));
        Position a = new Position(10, 12, 0), b = new Position(12, 12, 0);
        MonsterManager.Target ta = target(a), tb = target(b);
        mm.update(0, List.of(ta, tb), null);
        assertFalse(mm.canOccupy(b, a), "the other player's tile is taken");
        assertTrue(mm.canOccupy(new Position(11, 12, 0), a));
    }

    static MonsterManager.Target target(Position p) {
        return new MonsterManager.Target() {
            public Position position() { return p; }
            public boolean attackable() { return true; }
        };
    }

    /** Dwa sasiednie, przechodnie pola strefy ochronnej. */
    static Position[] pzPair(WorldMap w) {
        for (int y = 1; y < w.height() - 1; y++) {
            for (int x = 1; x < w.width() - 2; x++) {
                Position p = new Position(x, y, 0), q = new Position(x + 1, y, 0);
                if (w.isProtectionZone(p) && w.isProtectionZone(q) && w.isWalkable(p) && w.isWalkable(q)
                        && w.transitionAt(q) == null) return new Position[] {p, q};
            }
        }
        throw new AssertionError("no protection zone in the test map");
    }
}
