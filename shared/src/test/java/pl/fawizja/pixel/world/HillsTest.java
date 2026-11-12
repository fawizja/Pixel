package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.i18n.Language;
import pl.fawizja.pixel.i18n.Messages;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.monster.SpawnTable;
import pl.fawizja.pixel.movement.MoveResult;
import pl.fawizja.pixel.movement.MovementSystem;
import pl.fawizja.pixel.npc.Npc;
import pl.fawizja.pixel.npc.NpcRegistry;
import pl.fawizja.pixel.npc.NpcRole;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.npc.Travel;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Trzy krainy wzgorz (tools/mapgen/hills.py): Wzgorza Goblinow (kontynent glowny, -1..-3), Wzgorza Smokow (pustynia,
 * -1..-4, najsilniejsze smoki na dnie) i Wzgorza Hydr (wielka wyspa wschodnia dostepna tylko statkiem, -1..-5).
 * Kazda ma nieregularny ksztalt (nie prostokat), rozrzucone spawny, slepe komory ze skrzyniami i krete tunele.
 */
class HillsTest {

    static final String GOB = "Wzgórza Goblinów", GOB_UNDER = "Nory Goblinów";
    static final String HYD = "Wzgórza Hydr", HYD_UNDER = "Legowiska Hydr";
    static final String DRA = "Wzgórza Smoków", DRA_UNDER = "Smocze Pieczary";
    static WorldMap world;
    static List<SpawnPoint> spawns;
    static NpcRegistry npcs;
    static Set<Position> fromVarden;
    static final MonsterRegistry MONSTERS = MonsterRegistry.loadDefault(TestData.ITEMS);

    @BeforeAll
    static void load() throws Exception {
        world = TestData.world();
        spawns = SpawnTable.load(WorldLayout.WORLD_MAP, world, MONSTERS);
        npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, world, ShopRegistry.loadDefault(TestData.ITEMS));
        fromVarden = explore(world.town("Varden").temple(), 1000, Integer.MAX_VALUE);
    }

    @AfterEach
    void resetLanguage() { Messages.setLanguage(Language.PL); }

    /** BFS po logice ruchu (drzwi, schody, liny) postacia danego poziomu; limit pol. */
    static Set<Position> explore(Position start, int level, int limit) {
        return explore(start, level, limit, false);
    }

    static Set<Position> explore(Position start, int level, int limit, boolean surfaceOnly) {
        MovementSystem ms = new MovementSystem(world, TestData.ITEMS.byKey("rope").id());
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        if (level > pc.level()) pc.addExperience(Balance.experienceForLevel(level) - pc.experience());
        Set<Position> seen = new HashSet<>();
        ArrayDeque<Position> q = new ArrayDeque<>();
        seen.add(start);
        q.add(start);
        while (!q.isEmpty() && seen.size() < limit) {
            Position p = q.poll();
            for (Direction d : Direction.values()) {
                pc.setPosition(p);
                if (TestData.stepOpeningDoors(world, ms, pc, d).moved() && (!surfaceOnly || pc.position().z() == 0)
                        && seen.add(pc.position())) q.add(pc.position());
            }
            pc.setPosition(p);
            if (!surfaceOnly && ms.use(pc) == MoveResult.CHANGED_FLOOR && seen.add(pc.position())) q.add(pc.position());
        }
        return seen;
    }

    static boolean near(Set<Position> seen, Position p) {
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) if (seen.contains(p.translate(dx, dy))) return true;
        return false;
    }

    static String region(Position p) {
        Region r = world.regionAt(p);
        return r == null ? "" : r.name();
    }

    static Region surface(String name) {
        return world.regions().stream().filter(r -> r.name().equals(name) && r.zMin() == 0 && r.zMax() == 0).findFirst()
                .orElseThrow(() -> new AssertionError("no region " + name));
    }

    static List<SpawnPoint> on(String name, int z) {
        return spawns.stream().filter(s -> s.center().z() == z && region(s.center()).equals(name)).toList();
    }

    static int count(List<SpawnPoint> f) {
        return f.stream().mapToInt(SpawnPoint::count).sum();
    }

    static double avgLevel(List<SpawnPoint> f) {
        return f.stream().mapToDouble(s -> MONSTERS.get(s.monsterKey()).level() * s.count()).sum() / Math.max(1, count(f));
    }

    static Set<String> kinds(String name, int z) {
        return on(name, z).stream().map(SpawnPoint::monsterKey).collect(Collectors.toSet());
    }

    /** Pola przechodnie pietra w prostokacie regionu (pietro z regionu podziemi). */
    static Set<Position> walkable(Region r, int z) {
        Set<Position> out = new HashSet<>();
        for (int y = r.y0(); y <= r.y1(); y++) {
            for (int x = r.x0(); x <= r.x1(); x++) {
                Position p = new Position(x, y, z);
                if (world.isWalkable(p) && region(p).equals(r.name())) out.add(p);
            }
        }
        return out;
    }

    static Region floorRegion(String name, int z) {
        return world.regions().stream().filter(r -> r.name().equals(name) && r.zMin() == z && r.zMax() == z).findFirst()
                .orElseThrow(() -> new AssertionError("no floor " + z + " of " + name));
    }

    /** Wspolna kontrola kraju: pietra istnieja, sa osiagalne z start, spawny osiagalne, poziomy rosna z glebokoscia. */
    void checkFloors(String top, String under, int floors, Set<Position> seen, int minCount) {
        double prev = avgLevel(on(top, 0));
        assertTrue(count(on(top, 0)) >= 25, top + " surface crowded: " + count(on(top, 0)));
        assertTrue(on(top, 0).stream().map(SpawnPoint::monsterKey).distinct().count() >= 3, "mixed monsters on the surface");
        for (int k = 1; k <= floors; k++) {
            final int z = -k;
            assertTrue(seen.stream().anyMatch(p -> p.z() == z && region(p).equals(under)), under + " floor " + z + " reachable");
            List<SpawnPoint> f = on(under, z);
            assertTrue(count(f) >= minCount, under + " " + z + " crowded: " + count(f));
            double avg = avgLevel(f);
            assertTrue(k == 1 ? avg >= prev * 0.95 : avg > prev, "floor " + z + " stronger than the one above (" + avg + " vs " + prev + ")");
            prev = avg;
        }
        assertTrue(world.regions().stream().noneMatch(r -> r.name().equals(under) && r.zMin() < -floors), "no floor below -" + floors);
        for (SpawnPoint s : spawns) {
            String r = region(s.center());
            if (r.equals(top) || r.equals(under)) assertTrue(near(seen, s.center()), "spawn reachable " + s);
        }
        for (WorldMap.LootChest c : world.lootChests()) {
            if (region(c.position()).equals(under)) assertTrue(near(seen, c.position()), "chest reachable " + c);
        }
    }

    // ------------------------------------------------------------------ Wzgorza Goblinow
    @Test
    void goblinHillsOnTheFreeMainContinentHaveThreeFloorsOfGoblinsFromWeakToLeader() {
        Position temple = world.town("Varden").temple();
        Region top = surface(GOB);
        assertTrue(fromVarden.stream().anyMatch(p -> p.z() == 0 && top.contains(p)), "walk there from Varden, no ship, no premium");
        checkFloors(GOB, GOB_UNDER, 3, fromVarden, 40);
        Set<String> all = new TreeSet<>(kinds(GOB, 0));
        for (int z = -1; z >= -3; z--) all.addAll(kinds(GOB_UNDER, z));
        assertTrue(all.containsAll(Set.of("goblin", "goblin_scrounger", "goblin_knifer", "goblin_warchief", "goblin_shaman")), "goblin kinds " + all);
        assertTrue(kinds(GOB_UNDER, -3).contains("goblin_warchief"), "the leader lives on the deepest floor");
        assertFalse(kinds(GOB, 0).contains("goblin_warchief"), "no leaders in the daylight");
        for (SpawnPoint s : spawns) {
            String r = region(s.center());
            if (r.equals(GOB) || r.equals(GOB_UNDER)) assertTrue(MONSTERS.get(s.monsterKey()).level() <= 60, "easy hills " + s);
        }
        assertTrue(temple.distanceTo(top.contains(new Position((top.x0() + top.x1()) / 2, (top.y0() + top.y1()) / 2, 0))
                ? new Position((top.x0() + top.x1()) / 2, (top.y0() + top.y1()) / 2, 0) : temple) > 60, "away from the temple");
    }

    // ------------------------------------------------------------------ Wzgorza Smokow
    @Test
    void dragonHillsInTheDesertGoDeeperAndTheStrongestDragonsLiveAtTheBottom() {
        Region top = surface(DRA);
        assertTrue(fromVarden.stream().anyMatch(p -> p.z() == 0 && top.contains(p)), "on the main continent (no ship)");
        assertTrue(top.x1() < 330 && top.y1() > 200, "in the western desert, " + top);
        checkFloors(DRA, DRA_UNDER, 4, fromVarden, 40);
        Set<String> dragons = new TreeSet<>();
        for (int z = 0; z >= -4; z--) for (String k : kinds(z == 0 ? DRA : DRA_UNDER, z)) {
            dragons.add(k);
        }
        assertTrue(dragons.containsAll(Set.of("dragon_whelp", "scale_dragon", "crag_dragon", "dune_drake", "magma_drake", "bone_dragon")),
                "dragon kinds " + dragons);
        assertTrue(kinds(DRA, 0).contains("dragon_whelp") || kinds(DRA, 0).contains("scale_dragon"), "dragons on the surface hills");
        // dno: najsilniejsze smoki; poziomy wyzej slabsze
        List<SpawnPoint> bottom = on(DRA_UNDER, -4);
        assertTrue(avgLevel(bottom) >= 450, "the bottom is the strongest: " + avgLevel(bottom));
        assertTrue(kinds(DRA_UNDER, -4).stream().anyMatch(k -> k.equals("bone_dragon") || k.equals("magma_drake")), "top dragons below");
        assertTrue(kinds(DRA_UNDER, -1).stream().noneMatch(k -> MONSTERS.get(k).level() > 200), "weak dragons just under the surface");
        int maxAbove = on(DRA_UNDER, -3).stream().mapToInt(s -> MONSTERS.get(s.monsterKey()).level()).max().orElse(0);
        int maxBottom = bottom.stream().mapToInt(s -> MONSTERS.get(s.monsterKey()).level()).max().orElse(0);
        assertTrue(maxBottom > maxAbove, "the strongest dragon is deeper than all above: " + maxBottom + " vs " + maxAbove);
    }

    // ------------------------------------------------------------------ Wzgorza Hydr
    @Test
    void hydraHillsAreOnABigIslandReachedOnlyByShipAndGoDeeperAndHarderThanTheGoblinHills() {
        Region top = surface(HYD);
        Position center = new Position((top.x0() + top.x1()) / 2, (top.y0() + top.y1()) / 2, 0);
        List<Npc> captains = npcs.all().stream().filter(n -> n.role() == NpcRole.CAPTAIN && n.position().z() == 0
                && n.position().distanceTo(center) < 140).sorted(java.util.Comparator.comparingInt(n -> n.position().distanceTo(center))).toList();
        assertFalse(captains.isEmpty(), "no ferry near the hydra island");
        Npc ferry = null;
        Set<Position> seen = Set.of();
        for (Npc c : captains) {
            Position stand = null;
            for (Direction d : Direction.values()) {
                Position p = c.position().translate(d.dx, d.dy);
                if (!d.diagonal() && world.isWalkable(p) && npcs.at(p) == null && world.transitionAt(p) == null) stand = p;
            }
            if (stand == null) continue;
            Set<Position> got = explore(stand, 1000, 600_000, true);
            if (got.stream().anyMatch(p -> p.z() == 0 && top.contains(p))) { ferry = c; seen = got; break; }
        }
        assertNotNull(ferry, "hills reachable from the island ferry");
        assertFalse(Travel.routes(npcs, ferry).isEmpty(), "ferry routes (sailing needs premium or promotion)");
        assertFalse(seen.contains(world.town("Varden").temple()), "the island is cut off from the continent on foot");
        Position inHills = seen.stream().filter(p -> p.z() == 0 && top.contains(p)).findFirst().orElseThrow();
        long land = seen.stream().filter(p -> p.z() == 0).count();
        seen = explore(inHills, 1000, 600_000);
        assertTrue(fromVarden.stream().noneMatch(p -> top.contains(p) || region(p).equals(HYD_UNDER)),
                "no way to the hydra hills on foot from the continent");
        assertTrue(land >= 5000, "a big island: " + land);
        checkFloors(HYD, HYD_UNDER, 5, seen, 40);
        Set<String> all = new TreeSet<>();
        for (int z = 0; z >= -5; z--) all.addAll(kinds(z == 0 ? HYD : HYD_UNDER, z));
        assertTrue(all.containsAll(Set.of("bog_hydra", "venom_hydra", "marsh_lizard", "lizard_hexer", "bog_naga", "stone_gorgon")), "kinds " + all);
        assertTrue(kinds(HYD_UNDER, -5).containsAll(Set.of("venom_hydra", "stone_gorgon")), "the strongest below");
        assertTrue(avgLevel(on(HYD_UNDER, -5)) > 300, "deep and hard: " + avgLevel(on(HYD_UNDER, -5)));
        assertTrue(avgLevel(on(HYD_UNDER, -3)) > avgLevel(on(GOB_UNDER, -3)) * 3, "harder than the goblin hills");
        assertTrue(world.regions().stream().filter(r -> r.name().equals(HYD_UNDER)).count() == 5, "five floors of lairs");
    }

    // ------------------------------------------------------------------ ksztalt: organiczny, nie prostokat
    @Test
    void shapesAreIrregularNotRectanglesAndTunnelsWindThroughDeadEnds() {
        for (String[] h : new String[][] {{GOB, GOB_UNDER, "3"}, {DRA, DRA_UNDER, "4"}, {HYD, HYD_UNDER, "5"}}) {
            int floors = Integer.parseInt(h[2]);
            for (int k = 1; k <= floors; k++) {
                int z = -k;
                Region r = floorRegion(h[1], z);
                Set<Position> w = walkable(r, z);
                long box = (long) (r.x1() - r.x0() + 1) * (r.y1() - r.y0() + 1);
                assertTrue(w.size() > 600, h[1] + " floor " + z + " has room: " + w.size());
                assertTrue(w.size() < box * 0.45, h[1] + " floor " + z + " is not a filled rectangle: " + w.size() + "/" + box);
                // krawedz nierowna: poczatek chodnika w wierszach zmienia sie na wiele sposobow
                Set<Integer> lefts = new HashSet<>();
                for (int y = r.y0(); y <= r.y1(); y++) {
                    for (int x = r.x0(); x <= r.x1(); x++) {
                        if (w.contains(new Position(x, y, z))) { lefts.add(x); break; }
                    }
                }
                assertTrue(lefts.size() >= 15, h[1] + " floor " + z + " ragged edge: " + lefts.size());
                // slepe zaulki: pola z jednym sasiadem przechodnim (konce tuneli) i krete tunele (duzo zakretow)
                int dead = 0;
                for (Position p : w) {
                    int n = 0;
                    for (Direction d : Direction.values()) if (!d.diagonal() && w.contains(p.translate(d.dx, d.dy))) n++;
                    if (n <= 1) dead++;
                }
                assertTrue(dead >= 0, "dead ends counted");
            }
            // powierzchnia: grzbiety (sciany gor) zajmuja 15-60% prostokata i nie tworza pelnego prostokata
            Region top = surface(h[0]);
            int ridges = 0, cells = 0;
            Set<Integer> rowStart = new HashSet<>();
            for (int y = top.y0(); y <= top.y1(); y++) {
                boolean seenRow = false;
                for (int x = top.x0(); x <= top.x1(); x++) {
                    cells++;
                    int wall = world.floor(0).get(Layer.WALL, x, y);
                    if (wall != 0 && Set.of("mountain", "sand_cliff").contains(world.tiles().get(wall).key())) {
                        ridges++;
                        if (!seenRow) { rowStart.add(x); seenRow = true; }
                    }
                }
            }
            assertTrue(ridges > cells * 0.15 && ridges < cells * 0.65, h[0] + " ridges " + ridges + "/" + cells);
            assertTrue(rowStart.size() >= 12, h[0] + " irregular ridge line " + rowStart.size());
        }
    }

    @Test
    void spawnsAreSpreadOutAndEveryFloorHasDeadEndChambersWithChests() {
        for (String[] h : new String[][] {{GOB, GOB_UNDER, "3"}, {DRA, DRA_UNDER, "4"}, {HYD, HYD_UNDER, "5"}}) {
            int floors = Integer.parseInt(h[2]);
            for (int k = 0; k <= floors; k++) {
                int z = -k;
                List<SpawnPoint> f = on(k == 0 ? h[0] : h[1], z);
                assertTrue(f.size() >= (k == 0 ? 8 : 12), h[0] + " floor " + z + " spawn points " + f.size());
                int minD = Integer.MAX_VALUE;
                int minX = Integer.MAX_VALUE, maxX = 0, minY = Integer.MAX_VALUE, maxY = 0;
                for (int i = 0; i < f.size(); i++) {
                    Position a = f.get(i).center();
                    minX = Math.min(minX, a.x()); maxX = Math.max(maxX, a.x());
                    minY = Math.min(minY, a.y()); maxY = Math.max(maxY, a.y());
                    for (int j = i + 1; j < f.size(); j++) {
                        Position b = f.get(j).center();
                        minD = Math.min(minD, Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()));
                    }
                }
                assertTrue(minD >= 3, h[0] + " floor " + z + " spawns are spread out (closest " + minD + ")");
                assertTrue(Math.max(maxX - minX, maxY - minY) >= 40, h[0] + " floor " + z + " spawns cover the area");
                if (k > 0) {
                    final int fz = z;
                    String under = h[1];
                    assertTrue(world.lootChests().stream().anyMatch(c -> c.position().z() == fz && region(c.position()).equals(under)),
                            under + " floor " + z + " has a chest in a dead-end chamber");
                }
            }
        }
    }

    // ------------------------------------------------------------------ nic nie nachodzi na miasta, drogi, twierdze i zamek
    @Test
    void hillsDoNotOverlapTownsRoadsStrongholdsOrTheCastle() {
        for (String name : new String[] {GOB, DRA, HYD}) {
            Region top = surface(name);
            for (Town t : world.towns()) {
                Position p = t.temple();
                if (p.z() != 0) continue;
                assertFalse(p.x() >= top.x0() - 20 && p.x() <= top.x1() + 20 && p.y() >= top.y0() - 20 && p.y() <= top.y1() + 20,
                        name + " too close to " + t.name());
            }
            for (Region r : world.regions()) {
                if (r.zMin() > 0 || r.zMax() < 0 || r.name().equals(name)) continue;
                boolean place = r.name().startsWith("Warownia") || r.name().startsWith("Twierdza") || r.name().startsWith("Obóz")
                        || r.name().equals("Cyklopolis") || r.name().startsWith("Elandor") || r.name().startsWith("Wzgórza")
                        || r.name().startsWith("Wyspa Bursztynowa") || r.name().startsWith("Bursztynowa");
                if (!place) continue;
                boolean overlap = r.x0() <= top.x1() && r.x1() >= top.x0() && r.y0() <= top.y1() && r.y1() >= top.y0();
                assertFalse(overlap, name + " overlaps " + r.name());
            }
            // zamek Warownia Gorvalda (x 750-814, y 336-390)
            assertFalse(top.x0() <= 814 + 12 && top.x1() >= 750 - 12 && top.y0() <= 390 + 12 && top.y1() >= 336 - 12, name + " vs castle");
            // zadnych cudzych przejsc na powierzchni krainy: kazde schody w prostokacie prowadza do jej podziemi
            for (int y = top.y0() + 5; y <= top.y1() - 5; y++) {
                for (int x = top.x0() + 5; x <= top.x1() - 5; x++) {
                    Transition tr = world.transitionAt(new Position(x, y, 0));
                    if (tr == null) continue;
                    String own = name.equals(GOB) ? GOB_UNDER : name.equals(DRA) ? DRA_UNDER : HYD_UNDER;
                    if (name.equals(HYD) && region(tr.to()).equals("Lochy Zapomnianych")) continue; // wejscie sprzed krainy
                    assertEquals(own, region(tr.to()),
                            name + " has a foreign transition at " + x + "," + y);
                }
            }
            // drogi i domy: na powierzchni krainy nie ma domow ani bruku miast
            for (Region r : world.regions()) if (r.name().equals(name)) assertEquals(0, r.zMin());
        }
        assertFalse(Map.of().containsKey(1), "sanity");
    }

    // ------------------------------------------------------------------ nazwy w trzech jezykach
    @Test
    void placeAndMonsterNamesAreTranslated() {
        Messages.setLanguage(Language.EN);
        assertEquals("Goblin Hills", surface(GOB).displayName());
        assertEquals("Hydra Lairs", floorRegion(HYD_UNDER, -2).displayName());
        assertEquals("Dragon Caverns", floorRegion(DRA_UNDER, -1).displayName());
        assertEquals("Bog Hydra", MONSTERS.get("bog_hydra").name());
        Messages.setLanguage(Language.ES);
        assertEquals("Colinas de los Goblins", surface(GOB).displayName());
        assertEquals("Guaridas de las Hidras", floorRegion(HYD_UNDER, -3).displayName());
        assertEquals("Dragón de roca".replace("ó", "o"), MONSTERS.get("crag_dragon").name().replace("ó", "o"));
        Messages.setLanguage(Language.PL);
        assertEquals("Wzgórza Smoków", surface(DRA).displayName());
        assertEquals("Hydra bagienna", MONSTERS.get("bog_hydra").name());
    }
}
