package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.monster.SpawnTable;
import pl.fawizja.pixel.movement.MoveResult;
import pl.fawizja.pixel.movement.MovementSystem;
import pl.fawizja.pixel.treasure.TreasureChests;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Warownia Gorvalda (tools/mapgen/castle.py): zamek dla poziomow 20-70 na kontynencie glownym z wejsciem tylko przez
 * podziemia (jaskinia przed fosa, trzy pietra tuneli z orkami i rogaczami, piwnica pod twierdza), bez zadnego wejscia z
 * powierzchni; na szczycie (+3) za drzwiami poziomu 55 kasztelan Gorvald i skrzynia zadania.
 */
class CastleTest {

    static final String CASTLE = "Warownia Gorvalda";
    static final String UNDER = "Chodniki pod Warownią";
    static WorldMap world;
    static List<SpawnPoint> spawns;
    static final MonsterRegistry MONSTERS = MonsterRegistry.loadDefault(TestData.ITEMS);

    @BeforeAll
    static void load() throws Exception {
        world = TestData.world();
        spawns = SpawnTable.load(WorldLayout.WORLD_MAP, world, MONSTERS);
    }

    static boolean in(String name, Position p) {
        return world.regionAt(p) != null && world.regionAt(p).name().equals(name);
    }

    /** Pola osiagalne pieszo z Varden; blocked - pola, ktorych nie wolno uzyc (np. wejscie do jaskini). */
    static Set<Position> explore(int level, Set<Position> blocked) {
        MovementSystem ms = new MovementSystem(world, TestData.ITEMS.byKey("rope").id());
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.addExperience(Balance.experienceForLevel(level) - pc.experience());
        Position start = world.town("Varden").temple();
        Set<Position> seen = new HashSet<>();
        ArrayDeque<Position> q = new ArrayDeque<>();
        seen.add(start);
        q.add(start);
        while (!q.isEmpty()) {
            Position p = q.poll();
            for (Direction d : Direction.values()) {
                pc.setPosition(p);
                if (TestData.stepOpeningDoors(world, ms, pc, d).moved() && !blocked.contains(pc.position())
                        && world.requiredLevelAt(pc.position()) <= level && seen.add(pc.position())) q.add(pc.position());
            }
            pc.setPosition(p);
            if (ms.use(pc) == MoveResult.CHANGED_FLOOR && !blocked.contains(pc.position()) && seen.add(pc.position())) q.add(pc.position());
        }
        return seen;
    }

    static Region castleRegion() {
        return world.regions().stream().filter(r -> r.name().equals(CASTLE)).findFirst().orElseThrow();
    }

    @Test
    void theCastleIsForLevels20To70AndStandsOnTheMainContinentAwayFromTowns() {
        Region r = castleRegion();
        assertEquals(20, r.levelMin());
        assertEquals(70, r.levelMax());
        Position boss = spawns.stream().filter(s -> s.monsterKey().equals("castellan_gorvald")).findFirst().orElseThrow().center();
        assertEquals(3, boss.z(), "the castellan sits at the top");
        for (Town t : world.towns()) {
            if (t.temple().z() != 0) continue;
            assertTrue(t.temple().distanceTo(boss) > 90, "far from " + t.name());
        }
        Set<Position> all = explore(1000, Set.of());
        assertTrue(all.contains(boss), "reachable on foot from Varden (main continent)");
    }

    @Test
    void thereIsNoEntranceFromTheSurfaceOnlyTheCaveBeforeTheMoat() {
        Position boss = spawns.stream().filter(s -> s.monsterKey().equals("castellan_gorvald")).findFirst().orElseThrow().center();
        // jedyne przejscie z powierzchni do podziemi pod zamkiem: schody w jaskini w skale
        List<Transition> caves = world.floor(0).transitions().values().stream()
                .filter(t -> t.to().z() == -1 && in(UNDER, t.to()) && !in(CASTLE, t.from())).toList();
        assertEquals(1, caves.size(), "one cave entrance");
        Position cave = caves.get(0).from();
        assertFalse(in(CASTLE, cave), "the cave lies outside the castle region");
        Set<Position> blocked = new HashSet<>();
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) blocked.add(cave.translate(dx, dy));
        Set<Position> without = explore(1000, blocked);
        Region rg = castleRegion();   // obszar to mur z fosa i 6 pol brzegu dookola; mury leza w srodku
        assertFalse(without.stream().anyMatch(p -> p.z() >= 0 && p.x() >= rg.x0() + 6 && p.x() <= rg.x1() - 6
                && p.y() >= rg.y0() + 6 && p.y() <= rg.y1() - 6), "without the cave nobody gets inside the walls");
        assertFalse(without.contains(boss));
        Set<Position> with = explore(1000, Set.of());
        assertTrue(with.stream().anyMatch(p -> p.z() == 0 && in(CASTLE, p)), "through the cave the courtyard is open");
        // droga: powierzchnia 0 -> -1 -> -2 -> -3 -> -2 -> -1 (piwnica) -> 0 (zamek)
        for (int z = -3; z <= -1; z++) {
            final int fz = z;
            assertTrue(with.stream().anyMatch(p -> p.z() == fz && in(UNDER, p)), "tunnel floor " + z);
        }
        // wewnatrz fosy i murow jedyne schody w dol to te z sali schodow do piwnicy
        long down = world.floor(0).transitions().values().stream().filter(t -> in(CASTLE, t.from()) && t.to().z() < 0).count();
        assertEquals(1, down, "one stair between the keep and the cellar");
    }

    @Test
    void theTunnelsHoldOrcsMinotaursAndOtherMonstersThatGrowStrongerWithDepth() {
        double prev = 0;
        for (int z : new int[] {-1, -2, -3}) {
            final int fz = z;
            List<SpawnPoint> f = spawns.stream().filter(s -> s.center().z() == fz && in(UNDER, s.center())).toList();
            assertFalse(f.isEmpty(), "floor " + z);
            f = f.stream().filter(s -> !s.monsterKey().startsWith("keep_")).toList();   // piwnica pod twierdza (-1) to straz zamku
            double avg = f.stream().mapToDouble(s -> MONSTERS.get(s.monsterKey()).level() * s.count()).sum()
                    / f.stream().mapToInt(SpawnPoint::count).sum();
            assertTrue(avg > prev, "floor " + z + " stronger: " + avg + " vs " + prev);
            prev = avg;
        }
        Set<String> kinds = spawns.stream().filter(s -> in(UNDER, s.center())).map(SpawnPoint::monsterKey).collect(Collectors.toSet());
        assertTrue(kinds.stream().anyMatch(k -> k.startsWith("orc_")), "orcs " + kinds);
        assertTrue(kinds.stream().anyMatch(k -> k.startsWith("horn_")), "horned (minotaur-like) warriors " + kinds);
        assertTrue(kinds.contains("cave_troll"), "other monsters " + kinds);
        assertTrue(kinds.stream().anyMatch(k -> k.startsWith("keep_")), "the cellar is guarded by castle soldiers " + kinds);
        Set<Position> reach = explore(1000, Set.of());
        for (SpawnPoint s : spawns) {
            if (in(UNDER, s.center()) || in(CASTLE, s.center())) assertTrue(reach.contains(s.center()), "spawn reachable " + s);
        }
    }

    @Test
    void theKeepIsDefendedOnEveryFloorAndTheBossGuardsAQuestChestBehindTheLevelDoor() {
        for (int z = 0; z <= 3; z++) {
            final int fz = z;
            assertTrue(spawns.stream().anyMatch(s -> s.center().z() == fz && in(CASTLE, s.center())), "defenders on +" + z);
        }
        Position boss = spawns.stream().filter(s -> s.monsterKey().equals("castellan_gorvald")).findFirst().orElseThrow().center();
        Map.Entry<Position, Integer> door = world.levelDoors().entrySet().stream()
                .filter(e -> e.getKey().z() == 2 && in(CASTLE, e.getKey())).findFirst().orElseThrow();
        assertEquals(55, door.getValue().intValue());
        assertFalse(explore(54, Set.of()).contains(boss), "below level 55 the throne room stays closed");
        assertTrue(explore(55, Set.of()).contains(boss), "from level 55 the way up is open");
        TreasureChests chests = TreasureChests.generate(world);
        TreasureChests.Chest chest = chests.all().stream().filter(c -> c.id().startsWith(TreasureChests.LOOT_PREFIX) && c.position().z() == 3 && in(CASTLE, c.position()))
                .findFirst().orElseThrow();
        assertEquals(55, chest.doorLevel());
        assertTrue(chest.position().distanceTo(boss) < 10, "the quest chest is next to the boss");
        assertTrue(MONSTERS.get("castellan_gorvald").boss());
        assertEquals(70, MONSTERS.get("castellan_gorvald").level());
    }

    @Test
    void theNewMonstersAreOriginalAndFitTheLevelBand() {
        String[] order = {"horn_raider", "horn_bowman", "horn_guard", "horn_mystic", "keep_hound", "keep_crossbow", "keep_sentry",
                "keep_chaplain", "castellan_gorvald"};
        int prev = 0;
        for (String k : order) {
            assertNotNull(MONSTERS.get(k), k);
            assertTrue(MONSTERS.get(k).level() >= prev, k);
            assertTrue(MONSTERS.get(k).level() >= 20 && MONSTERS.get(k).level() <= 70, k);
            prev = MONSTERS.get(k).level();
        }
    }
}
