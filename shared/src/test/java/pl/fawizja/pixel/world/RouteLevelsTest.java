package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.monster.SpawnTable;
import pl.fawizja.pixel.movement.MoveResult;
import pl.fawizja.pixel.movement.MovementSystem;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Drogi ze swiatyni Varden do znanych lowisk (tools/mapgen/route_levels.py): dojscia do slabych lowisk nie sa
 * strzezone przez silne potwory, a na drodze do mocniejszych poziom rosnie stopniowo.
 */
class RouteLevelsTest {

    /** Spawny tak blisko drogi (pola, ten sam poziom mapy) licza sie jako "na drodze". */
    static final int NEAR = 8;
    /** Ostatnie kroki przed celem to juz samo lowisko. */
    static final int SKIP_AT_TARGET = 10;
    static final int TOWN_STEPS = 30;

    static WorldMap world;
    static List<SpawnPoint> spawns;
    static final MonsterRegistry MONSTERS = MonsterRegistry.loadDefault(TestData.ITEMS);

    @BeforeAll
    static void load() throws Exception {
        world = TestData.world();
        spawns = SpawnTable.load(WorldLayout.WORLD_MAP, world, MONSTERS);
    }

    static Region region(String name) {
        return world.regions().stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow(() -> new AssertionError(name));
    }

    /** Najkrotsza droga (logika ruchu: kroki, drzwi, schody, lina) ze swiatyni Varden do pierwszego pola regionu. */
    static List<Position> route(Region target, int level) {
        MovementSystem ms = new MovementSystem(world, TestData.ITEMS.byKey("rope").id());
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        if (level > pc.level()) pc.addExperience(Balance.experienceForLevel(level) - pc.experience());
        Position start = world.templeOf("varden");
        Map<Position, Position> parent = new HashMap<>();
        ArrayDeque<Position> q = new ArrayDeque<>();
        parent.put(start, start);
        q.add(start);
        while (!q.isEmpty()) {
            Position p = q.poll();
            if (target.contains(p)) {
                List<Position> path = new ArrayList<>();
                for (Position c = p; !c.equals(start); c = parent.get(c)) path.add(0, c);
                path.add(0, start);
                return path;
            }
            List<Position> next = new ArrayList<>();
            for (Direction d : Direction.values()) {
                pc.setPosition(p);
                if (TestData.stepOpeningDoors(world, ms, pc, d).moved()) next.add(pc.position());
            }
            pc.setPosition(p);
            if (ms.use(pc) == MoveResult.CHANGED_FLOOR) next.add(pc.position());
            for (Position n : next) {
                if (parent.putIfAbsent(n, p) == null) q.add(n);
            }
        }
        throw new AssertionError("no route to " + target.name());
    }

    /** Najsilniejszy spawn (bez bossow) w zasiegu NEAR od odcinka drogi [from, to). */
    static SpawnPoint strongestNear(List<Position> path, int from, int to) {
        SpawnPoint best = null;
        for (SpawnPoint sp : spawns) {
            MonsterType m = MONSTERS.get(sp.monsterKey());
            if (m.boss() || (best != null && m.level() <= MONSTERS.get(best.monsterKey()).level())) continue;
            Position c = sp.center();
            for (int i = from; i < to; i++) {
                Position p = path.get(i);
                if (p.z() == c.z() && Math.abs(p.x() - c.x()) <= NEAR && Math.abs(p.y() - c.y()) <= NEAR) {
                    best = sp;
                    break;
                }
            }
        }
        return best;
    }

    static int levelOf(SpawnPoint sp) {
        return sp == null ? 0 : MONSTERS.get(sp.monsterKey()).level();
    }

    /** Ten sam zapas co route_levels.route_slack: 10 poziomow albo 25%. */
    static int slack(int level) {
        return Math.max(10, level / 4);
    }

    @Test
    void approachesToTheLowHuntingGroundsAroundVardenAreNotGuardedByStrongMonsters() {
        for (String name : new String[] {"Kanały Varden", "Rozpadlina Wisielców", "Kopalnia Soli", "Grota Pająków",
                "Pieczara Ciem", "Zielony Gaj", "Dziura Głazów", "Rozstaje"}) {
            Region target = region(name);
            List<Position> path = route(target, Math.max(8, target.levelMin()));
            SpawnPoint worst = strongestNear(path, 0, Math.max(0, path.size() - SKIP_AT_TARGET));
            int limit = target.levelMax() + slack(target.levelMax());
            assertTrue(levelOf(worst) <= limit, name + " (" + path.size() + " krokow, poziom do " + target.levelMax()
                    + "): na drodze " + (worst == null ? "-" : worst.monsterKey() + " " + levelOf(worst) + " @" + worst.center()));
        }
    }

    @Test
    void theRouteToAStrongerHuntingGroundRisesGradually() {
        for (String name : new String[] {"Pieczara Sów", "Grota Węży", "Zatopiona Świątynia"}) {
            Region target = region(name);
            List<Position> path = route(target, Math.max(8, target.levelMin()));
            int end = Math.max(0, path.size() - SKIP_AT_TARGET);
            assertTrue(levelOf(strongestNear(path, 0, Math.min(end, TOWN_STEPS))) <= 15, name + ": przy miescie tylko slabe potwory");
            int prev = 0;
            // kolejne czwarte czesci drogi: najsilniejszy spawn moze byc slabszy niz wczesniej, ale zaden nie
            // przekracza celu, a pierwsza polowa drogi jest wyraznie slabsza niz cel
            for (int part = 1; part <= 4; part++) {
                int from = end * (part - 1) / 4, to = end * part / 4;
                int lv = levelOf(strongestNear(path, from, to));
                assertTrue(lv <= target.levelMax() + slack(target.levelMax()), name + " czesc " + part + ": " + lv);
                if (part <= 2) assertTrue(lv <= Math.max(15, target.levelMax() * 3 / 4), name + " czesc " + part + " za silna: " + lv);
                prev = Math.max(prev, lv);
            }
            assertTrue(prev > 0, name + ": na drodze sa jakies potwory");
        }
    }
}
