package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.movement.MoveResult;
import pl.fawizja.pixel.movement.MovementSystem;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.monster.SpawnTable;

/**
 * Miasto Rogatych (tools/mapgen/minotaur.py): wielkie nieregularne miasto rogatych na -7 pod wolnym kontynentem,
 * dwa kreta zejscia z powierzchni (-1..-6), potwory na poziomy 20-100 (slabsze na obrzezach, silniejsze w centrum),
 * boss przy tronie i skrzynia zadania.
 */
class MinotaurCityTest {

    static final String CITY = "Miasto Rogatych", CAVE = "Labirynt Rogatych";
    static final MonsterRegistry MONSTERS = MonsterRegistry.loadDefault(TestData.ITEMS);
    static WorldMap world;
    static List<SpawnPoint> spawns;
    static Set<Position> fromVarden;

    @BeforeAll
    static void load() throws Exception {
        world = TestData.world();
        spawns = SpawnTable.load(WorldLayout.WORLD_MAP, world, MONSTERS);
        fromVarden = explore(world.town("Varden").temple());
    }

    /** Pola osiagalne pieszo (kroki, drzwi, schody, lina) ze swiatyni Varden - jak HillsTest.explore. */
    static Set<Position> explore(Position start) {
        MovementSystem ms = new MovementSystem(world, TestData.ITEMS.byKey("rope").id());
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.addExperience(Balance.experienceForLevel(1000) - pc.experience());
        Set<Position> seen = new HashSet<>();
        ArrayDeque<Position> q = new ArrayDeque<>();
        seen.add(start);
        q.add(start);
        while (!q.isEmpty()) {
            Position p = q.poll();
            for (Direction d : Direction.values()) {
                pc.setPosition(p);
                if (TestData.stepOpeningDoors(world, ms, pc, d).moved() && seen.add(pc.position())) q.add(pc.position());
            }
            pc.setPosition(p);
            if (ms.use(pc) == MoveResult.CHANGED_FLOOR && seen.add(pc.position())) q.add(pc.position());
        }
        return seen;
    }

    static Region city() {
        return world.regions().stream().filter(r -> r.name().equals(CITY)).findFirst().orElseThrow();
    }

    static List<SpawnPoint> citySpawns() {
        Region r = city();
        return spawns.stream().filter(s -> s.center().z() == -7 && r.contains(s.center())).toList();
    }

    static boolean near(Position p) {
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) if (fromVarden.contains(p.translate(dx, dy))) return true;
        return false;
    }

    @Test
    void cityIsOnTheDeepestFloorAndReachableFromVarden() {
        Region r = city();
        assertEquals(-7, r.zMin());
        assertEquals(-7, r.zMax());
        assertTrue(r.levelMin() == 20 && r.levelMax() == 100, "levels " + r.levelMin() + "-" + r.levelMax());
        long reach = fromVarden.stream().filter(p -> p.z() == -7 && r.contains(p)).count();
        assertTrue(reach >= 2500, "walkable city fields reachable from the Varden temple: " + reach);
    }

    @Test
    void cityHasAnIrregularOutlineNotARectangle() {
        Region r = city();
        FloorMap f = world.floor(-7);
        int w = r.x1() - r.x0() + 1, h = r.y1() - r.y0() + 1, ground = 0;
        for (int y = r.y0(); y <= r.y1(); y++) for (int x = r.x0(); x <= r.x1(); x++) if (f.isWalkable(x, y)) ground++;
        assertTrue(ground < w * h * 0.55, "walkable " + ground + " of " + w * h + " (not a filled rectangle)");
        // cztery rogi prostokata sa puste
        int[][] cs = {{r.x0(), r.y0()}, {r.x1() - 7, r.y0()}, {r.x0(), r.y1() - 7}, {r.x1() - 7, r.y1() - 7}};
        int empty = 0;
        for (int[] c : cs) {
            boolean any = false;
            for (int y = c[1]; y < c[1] + 8; y++) for (int x = c[0]; x < c[0] + 8; x++) any |= f.isWalkable(x, y);
            if (!any) empty++;
        }
        assertTrue(empty >= 2, "empty bbox corners " + empty);
    }

    @Test
    void monstersCoverLevels20To100WeakAtTheEdgeStrongInTheCentre() {
        List<SpawnPoint> sp = citySpawns();
        assertTrue(sp.size() >= 40, "spawn points " + sp.size());
        int min = 1000, max = 0;
        Set<String> kinds = new HashSet<>();
        for (SpawnPoint s : sp) {
            int lv = MONSTERS.get(s.monsterKey()).level();
            min = Math.min(min, lv);
            max = Math.max(max, lv);
            kinds.add(s.monsterKey());
            assertTrue(lv >= 20 && lv <= 100, s + " level " + lv);
        }
        assertEquals(20, min, "weakest");
        assertEquals(100, max, "boss");
        assertTrue(kinds.size() >= 8, "kinds " + kinds);
        SpawnPoint boss = sp.stream().filter(s -> MONSTERS.get(s.monsterKey()).boss()).findFirst().orElseThrow();
        assertEquals("horn_overlord", boss.monsterKey());
        // slabi przy zejsciach (obrzeza), silni w glebi: porownanie wg odleglosci od najblizszego zejscia
        List<Position> doors = new ArrayList<>();
        for (Transition t : world.floor(-6).transitions().values()) if (t.to().z() == -7 && city().contains(t.to())) doors.add(t.to());
        assertEquals(2, doors.size(), "two city entrances");
        List<SpawnPoint> byDist = new ArrayList<>(sp.stream().filter(s -> !MONSTERS.get(s.monsterKey()).boss()).toList());
        byDist.sort(Comparator.comparingInt(s -> doors.stream().mapToInt(d -> Math.abs(s.center().x() - d.x()) + Math.abs(s.center().y() - d.y())).min().orElse(0)));
        int third = byDist.size() / 3;
        double near = byDist.subList(0, third).stream().mapToInt(s -> MONSTERS.get(s.monsterKey()).level()).average().orElse(0);
        double far = byDist.subList(byDist.size() - third, byDist.size()).stream().mapToInt(s -> MONSTERS.get(s.monsterKey()).level()).average().orElse(0);
        assertTrue(far > near + 10, "levels at the entrances " + near + " vs deep inside " + far);
    }

    @Test
    void bossAndQuestChestAreReachable() {
        SpawnPoint boss = citySpawns().stream().filter(s -> s.monsterKey().equals("horn_overlord")).findFirst().orElseThrow();
        assertTrue(near(boss.center()), "boss spot reachable " + boss.center());
        WorldMap.LootChest quest = world.lootChests().stream().filter(c -> c.position().z() == -7 && c.itemKey().equals("obsidian_armor"))
                .findFirst().orElseThrow();
        assertTrue(near(quest.position()), "quest chest reachable " + quest.position());
        assertTrue(Math.abs(quest.position().x() - boss.center().x()) + Math.abs(quest.position().y() - boss.center().y()) < 25,
                "quest chest near the boss");
        for (SpawnPoint s : citySpawns()) assertTrue(near(s.center()), "spawn " + s + " reachable");
    }

    @Test
    void windingCaveDescendsFrom1To6AndEntersTheCityTwice() {
        Set<Integer> floors = new HashSet<>();
        for (Position p : fromVarden) {
            Region r = world.regionAt(p);
            if (r != null && r.name().equals(CAVE)) floors.add(p.z());
        }
        assertEquals(Set.of(-1, -2, -3, -4, -5, -6), floors, "cave floors reached on foot");
        List<Transition> into = new ArrayList<>();
        for (Transition t : world.floor(-6).transitions().values()) if (t.to().z() == -7 && city().contains(t.to())) into.add(t);
        assertTrue(into.size() >= 2, "stairs from -6 into the city: " + into.size());
        for (Transition t : into) assertTrue(near(t.from()) && fromVarden.contains(t.to()), "stairs reachable " + t);
        // dwa zejscia z powierzchni
        int mouths = 0;
        for (Transition t : world.floor(0).transitions().values()) if (t.to().z() == -1 && world.regionAt(t.to()) != null
                && world.regionAt(t.to()).name().equals(CAVE)) mouths++;
        assertTrue(mouths >= 2, "surface entrances " + mouths);
    }

    @Test
    void cityDoesNotOverlapOtherUndergroundRegions() {
        Region r = city();
        FloorMap f = world.floor(-7);
        for (int y = r.y0(); y <= r.y1(); y++) {
            for (int x = r.x0(); x <= r.x1(); x++) {
                if (!f.isWalkable(x, y)) continue;
                Region at = world.regionAt(new Position(x, y, -7));
                assertEquals(CITY, at == null ? "" : at.name(), "field " + x + "," + y + " belongs to another region");
            }
        }
    }

    @Test
    void noWalkingRouteFromTheMainContinentToPremiumLand() {
        for (Town t : world.towns()) {
            if (t.premium()) assertFalse(fromVarden.contains(t.temple()), t.name() + " must stay reachable only by ship");
        }
    }
}
