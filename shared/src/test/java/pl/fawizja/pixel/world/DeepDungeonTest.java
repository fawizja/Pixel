package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.monster.SpawnTable;
import pl.fawizja.pixel.movement.MoveResult;
import pl.fawizja.pixel.movement.MovementSystem;
import pl.fawizja.pixel.vocation.Vocation;

/** Etap 10: glebokie lochy biomow do pietra -5, liny, drabiny, swiatlo i bossowie. */
class DeepDungeonTest {

    static WorldMap world;
    static List<SpawnPoint> spawns;
    static final String[] BOSSES = {"frost_queen", "ruin_colossus", "pharaoh"};

    @BeforeAll
    static void load() throws Exception {
        world = TestData.world();
        spawns = SpawnTable.load(WorldLayout.WORLD_MAP, world, MonsterRegistry.loadDefault(TestData.ITEMS));
    }

    static Set<Position> explore(Position start, boolean withRope) {
        int rope = TestData.ITEMS.byKey("rope").id();
        MovementSystem ms = new MovementSystem(world, rope);
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        if (!withRope) pc.backpack().remove(rope, pc.backpack().count(rope));
        assertEquals(withRope, pc.backpack().count(rope) > 0);
        Set<Position> seen = new HashSet<>();
        ArrayDeque<Position> q = new ArrayDeque<>();
        seen.add(start);
        q.add(start);
        while (!q.isEmpty()) {
            Position p = q.poll();
            for (Direction d : Direction.values()) {
                pc.setPosition(p);
                if (ms.step(pc, d).moved() && seen.add(pc.position())) q.add(pc.position());
            }
            pc.setPosition(p);
            if (ms.use(pc) == MoveResult.CHANGED_FLOOR && seen.add(pc.position())) q.add(pc.position());
        }
        return seen;
    }

    static SpawnPoint boss(String key) {
        return spawns.stream().filter(s -> s.monsterKey().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void everyBiomeHasABossOnTheDeepestFloorWithAWayBackToVarden() {
        Set<Position> fromVarden = explore(world.spawn(), true);
        for (String key : BOSSES) {
            SpawnPoint b = boss(key);
            assertEquals(-5, b.center().z(), key);
            assertTrue(fromVarden.contains(b.center()), key + " reachable from Varden");
            assertTrue(explore(b.center(), true).contains(world.spawn()), key + " has a way back with a rope");
        }
    }

    @Test
    void withoutARopeTheDeepFloorsAreAOneWayTrip() {
        for (String key : BOSSES) {
            assertFalse(explore(boss(key).center(), false).contains(world.spawn()),
                    key + ": climbing out requires a rope");
        }
    }

    @Test
    void deepFloorsHaveLightSourcesInEveryDungeon() {
        int[][] areas = {{0, 0, 63, 63}, {30, 130, 90, 185}, {100, 185, 159, 231}};
        for (int z = -5; z <= -3; z++) {
            FloorMap f = world.floor(z);
            for (int[] a : areas) {
                int lights = 0;
                for (int[] s : f.lightSources()) {
                    if (s[0] >= a[0] && s[0] <= a[2] && s[1] >= a[1] && s[1] <= a[3]) lights++;
                }
                assertTrue(lights >= 3, "floor " + z + " area " + a[0] + "," + a[1] + " lights " + lights);
            }
        }
    }
}
