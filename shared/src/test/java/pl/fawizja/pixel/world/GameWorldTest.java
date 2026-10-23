package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.npc.Npc;
import pl.fawizja.pixel.npc.NpcRegistry;
import pl.fawizja.pixel.npc.NpcRole;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.npc.Travel;
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
        reachable = exploreWithShips(1000);
    }

    /** Jak {@link #explore}, ale takze rejsami statkow od kapitanow, do ktorych da sie dojsc (wyspy). */
    static Set<Position> exploreWithShips(int level) {
        NpcRegistry npcs;
        try {
            npcs = NpcRegistry.load("world", world, ShopRegistry.loadDefault(TestData.ITEMS));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        Set<Position> seen = explore(world.spawn(), level);
        Set<Npc> sailed = new HashSet<>();
        boolean more = true;
        while (more) {
            more = false;
            for (Npc n : npcs.all()) {
                if (n.role() != NpcRole.CAPTAIN || sailed.contains(n)
                        || seen.stream().noneMatch(p -> p.z() == n.position().z() && p.distanceTo(n.position()) <= 1)) continue;
                sailed.add(n);
                for (Travel.Route r : Travel.routes(npcs, n)) {
                    for (Direction d : Direction.values()) {
                        Position p = r.to().position().translate(d.dx, d.dy);
                        if (!d.diagonal() && world.isWalkable(p) && !seen.contains(p)) {
                            seen.addAll(explore(p, level));
                            more = true;
                        }
                    }
                }
            }
        }
        return seen;
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
    void levelGatesKeepWeakerCharactersOut() {
        // straznik Gniazda Wiwern: od poziomu 250 (pozostale krainy kontynentu sa otwarte - start od poziomu 300)
        Position gate = new Position(167, 186, 0);
        assertEquals(250, world.requiredLevelAt(gate));
        assertFalse(explore(world.spawn(), 249).contains(gate), "level 249 passed the wyvern gate");
        assertTrue(explore(world.spawn(), 250).contains(gate), "level 250 may enter");
    }

    @Test
    void biomesFollowTheContinentSketch() {
        // szkic Darka: snieg i lod na polnocnym zachodzie, dzungla na polnocnym wschodzie, pustynia w srodku,
        // lasy i doliny na poludniu (woda nie liczy sie do zadnej krainy)
        String[] expected = {"north", "equatorial", "desert", "temperate"};
        int[][] boxes = {{20, 20, 120, 150}, {320, 60, 420, 200}, {200, 230, 250, 275}, {120, 300, 300, 420}};
        FloorMap surface = world.floor(0);
        int water = world.tiles().byKey("water").id();
        for (int b = 0; b < boxes.length; b++) {
            java.util.Map<String, Integer> counts = new java.util.HashMap<>();
            for (int y = boxes[b][1]; y <= boxes[b][3]; y++) {
                for (int x = boxes[b][0]; x <= boxes[b][2]; x++) {
                    int id = surface.get(Layer.GROUND, x, y);
                    if (id != 0 && id != water) counts.merge(world.tiles().get(id).biome(), 1, Integer::sum);
                }
            }
            String top = counts.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).orElseThrow().getKey();
            assertEquals(expected[b], top, "box " + b + " " + counts);
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

    @Test
    void islandsAndTheWyvernValleyAreHuntingGroundsForLevel300() throws Exception {
        List<SpawnPoint> spawns = SpawnTable.load(WorldLayout.WORLD_MAP, world, MONSTERS);
        for (String name : new String[] {"Wyspa Koralowa", "Wyspa Piratow", "Wyspa Mgiel", "Wyspa Latarni", "Lodowa Wyspa",
                "Wyspa Wulkaniczna", "Skaly Mewie", "Dolina Wiwern"}) {
            Region r = world.regions().stream().filter(x -> x.name().equals(name)).findFirst().orElseThrow();
            assertTrue(r.levelMin() >= 300, name);
            assertTrue(spawns.stream().filter(sp -> !sp.nightOnly()).anyMatch(sp -> world.regionAt(sp.center()) == r), name + " has monsters");
        }
        // Dolina Wiwern: straznik zawraca postacie ponizej poziomu 250
        Region valley = world.regions().stream().filter(x -> x.name().equals("Dolina Wiwern")).findFirst().orElseThrow();
        assertTrue(explore(world.spawn(), 249).stream().noneMatch(valley::contains), "level 249 kept out");
        assertTrue(explore(world.spawn(), 250).stream().anyMatch(valley::contains), "level 250 may enter");
    }

    @Test
    void islandDungeonsGoSeveralFloorsDownWithMonstersOnTheDeepestFloor() throws Exception {
        List<SpawnPoint> spawns = SpawnTable.load(WorldLayout.WORLD_MAP, world, MONSTERS);
        Object[][] dungeons = {{"Wyspa Piratow", -3}, {"Wyspa Wulkaniczna", -4}, {"Lodowa Wyspa", -3}, {"Wyspa Mgiel", -3},
                {"Gniazdo Wiwern", -2}};
        for (Object[] d : dungeons) {
            // na kontynencie wyspa i jej podziemia leza w roznych miejscach - region o tej nazwie ma kilka czesci
            List<Region> rs = world.regions().stream().filter(x -> x.name().equals(d[0])).toList();
            assertFalse(rs.isEmpty(), "region " + d[0]);
            java.util.function.Predicate<Position> in = p -> rs.stream().anyMatch(r -> r.contains(p));
            int deepest = (int) d[1];
            for (int z = -1; z >= deepest; z--) {
                int fz = z;
                assertTrue(reachable.stream().anyMatch(p -> p.z() == fz && in.test(p)), d[0] + " floor " + z);
            }
            assertTrue(spawns.stream().anyMatch(sp -> sp.center().z() == deepest && in.test(sp.center())), d[0] + " deepest floor has monsters");
        }
        // miedzy glebszymi pietrami takze dziura i drabina (nie tylko schody)
        long holes = world.floors().values().stream().flatMap(f -> f.transitions().values().stream())
                .filter(t -> t.type() == TransitionType.HOLE && world.regionAt(t.from()) != null
                        && world.regionAt(t.from()).levelMin() >= 300).count();
        assertTrue(holes >= 4, "holes in island dungeons: " + holes);
    }
}
