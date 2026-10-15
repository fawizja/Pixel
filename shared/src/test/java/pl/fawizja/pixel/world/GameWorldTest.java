package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

/** Pelny swiat: 4 miasta, biomy, polaczenia, spawny i ich poziomy. */
class GameWorldTest {

    static WorldMap world;
    static Set<Position> reachable;
    static final MonsterRegistry MONSTERS = MonsterRegistry.loadDefault(TestData.ITEMS);

    @BeforeAll
    static void load() {
        world = TestData.world();
        reachable = explore(world.spawn(), 1000);
    }

    /** BFS po logice ruchu (krok + uzycie, z lina w plecaku) od podanej pozycji. */
    static Set<Position> explore(Position start, int level) {
        MovementSystem ms = new MovementSystem(world, TestData.ITEMS.byKey("rope").id());
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        if (level > pc.level()) pc.addExperience(Balance.experienceForLevel(level) - pc.experience());
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

    @Test
    void fourTownsWithProtectedTemples() {
        assertEquals(4, world.towns().size());
        assertTrue(world.towns().size() <= Town.MAX_TOWNS);
        for (String name : new String[] {"Varden", "Frostholm", "Selvaran", "Qasirah"}) {
            Town t = world.town(name);
            assertNotNull(t, name);
            assertTrue(world.isWalkable(t.temple()) && world.isProtectionZone(t.temple()), name);
        }
        assertEquals(world.spawn(), world.templeOf("varden"), "new characters start in Varden");
    }

    @Test
    void everyTownAndDungeonIsReachableFromVarden() {
        for (Town t : world.towns()) assertTrue(reachable.contains(t.temple()), t.name());
        for (FloorMap f : world.floors().values()) {
            for (Transition tr : f.transitions().values()) {
                assertTrue(reachable.contains(tr.to()), "transition target " + tr);
            }
        }
        for (int z = -5; z <= 5; z++) {
            final int fz = z;
            assertTrue(reachable.stream().anyMatch(p -> p.z() == fz), "floor " + z);
        }
    }

    @Test
    void theBridgeGuardKeepsNewCharactersOutOfTheEndgame() {
        for (int level : new int[] {8, 120, 199}) {
            for (Position p : explore(world.spawn(), level)) {
                assertTrue(p.x() < 160, "level " + level + " reached the east continent at " + p);
            }
        }
        assertTrue(explore(world.spawn(), 200).stream().anyMatch(p -> p.x() >= 168), "level 200 may cross");
        for (Region r : world.regions()) {
            if (r.levelMin() >= 230) assertTrue(r.x0() >= 168, r.name() + " lies behind the bridge");
        }
    }

    @Test
    void biomesRunFromSnowToDesert() {
        String[] expected = {"north", "temperate", "equatorial", "desert"};
        int[][] bands = {{4, 56}, {66, 110}, {116, 166}, {178, 228}};
        FloorMap surface = world.floor(0);
        for (int b = 0; b < bands.length; b++) {
            java.util.Map<String, Integer> counts = new java.util.HashMap<>();
            for (int y = bands[b][0]; y <= bands[b][1]; y++) {
                for (int x = 2; x < world.width() - 2; x++) {
                    int id = surface.get(Layer.GROUND, x, y);
                    if (id != 0) counts.merge(world.tiles().get(id).biome(), 1, Integer::sum);
                }
            }
            String top = counts.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).orElseThrow().getKey();
            assertEquals(expected[b], top, "band " + b + " " + counts);
        }
    }

    @Test
    void vardenRegionIsIdenticalToTheVardenMap() {
        WorldMap v = TestData.varden();
        for (int z = -5; z <= 5; z++) {
            FloorMap a = v.floor(z), b = world.floor(z);
            for (int y = 0; y < v.height(); y++) {
                for (int x = 0; x < v.width(); x++) {
                    for (Layer l : Layer.values()) {
                        int va = a.get(l, x, y);
                        if (va == 0 && z != 0) continue;
                        assertEquals(va, b.get(l, x + WorldLayout.VARDEN_OFFSET_X, y + WorldLayout.VARDEN_OFFSET_Y),
                                "tile " + x + "," + y + "," + z + " " + l);
                    }
                }
            }
        }
    }

    @Test
    void spawnsAreValidAwayFromTownsAndMatchRegionLevels() throws Exception {
        List<SpawnPoint> spawns = SpawnTable.load(WorldLayout.WORLD_MAP, world, MONSTERS);
        assertTrue(spawns.size() > 40);
        for (SpawnPoint sp : spawns) {
            Position c = sp.center();
            assertTrue(world.isWalkable(c) && !world.isProtectionZone(c), "spawn " + sp);
            assertTrue(reachable.contains(c), "spawn reachable " + sp);
            MonsterType m = MONSTERS.get(sp.monsterKey());
            for (Town t : world.towns()) {
                // pod samymi murami tylko potwory dla poczatkujacych
                assertTrue(c.z() != 0 || m.level() <= 15 || c.distanceTo(t.temple()) > 14, sp + " too close to " + t.name());
            }
            Region r = world.regionAt(c);
            assertNotNull(r, "spawn outside any region " + sp);
            assertTrue(m.level() >= r.levelMin() * 0.8 && m.level() <= r.levelMax(), sp.monsterKey() + " (" + m.level()
                    + ") in " + r.name() + " " + r.levelMin() + "-" + r.levelMax());
        }
    }

    @Test
    void regionsWarnAboutDanger() {
        Region varden = world.regionAt(world.spawn());
        assertNotNull(varden);
        assertTrue(varden.levelMin() <= 8);
        for (Town t : world.towns()) {
            if (t.name().equals("Varden")) continue;
            Region r = world.regionAt(t.temple());
            assertNotNull(r, t.name());
            assertTrue(r.levelMin() > 8 + 15, t.name() + " region must warn new characters");
        }
    }

    @Test
    void newMonstersAreSensiblyScaled() {
        for (MonsterType m : MONSTERS.all()) {
            if (m.boss()) continue;
            double expPerHp = m.experience() / (double) m.maxHealth();
            assertTrue(expPerHp >= 0.3 && expPerHp <= 1.4, m.key() + " exp/hp " + expPerHp);
            assertTrue(m.maxHealth() >= m.level() * 5, m.key() + " has enough HP for its level");
        }
    }
}
