package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.HashMap;
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
import pl.fawizja.pixel.npc.Npc;
import pl.fawizja.pixel.npc.NpcRegistry;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.npc.Travel;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Etap 5 duzej listy Darka (tools/mapgen/isles.py i wilds.py, 2026-11-11): powiekszona Wyspa Bursztynowa z pietrami
 * -6..+3 pelnymi silnych stad, Oboz Lowczyn w dzungli z piwnicami i skrzynia, Cyklopolis na gorzystej wyspie
 * z kuzniami i szescioma pietrami w dol, glebsze i bardziej urozmaicone lochy dziczy.
 */
class IslandsCampCyclopolisTest {

    static final String ISLE = "Wyspa Bursztynowa", ROCK = "Bursztynowa Skała", DEEP = "Bursztynowe Czeluście";
    static final String CAMP = "Obóz Łowczyń", CELLAR = "Piwnice Obozu Łowczyń";
    static final String CYC = "Cyklopolis", CYC_DEEP = "Kuźnie Cyklopolis";
    static WorldMap world;
    static List<SpawnPoint> spawns;
    static NpcRegistry npcs;
    static final MonsterRegistry MONSTERS = MonsterRegistry.loadDefault(TestData.ITEMS);

    @BeforeAll
    static void load() throws Exception {
        world = TestData.world();
        spawns = SpawnTable.load(WorldLayout.WORLD_MAP, world, MONSTERS);
        npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, world, ShopRegistry.loadDefault(TestData.ITEMS));
    }

    static String region(Position p) {
        Region r = world.regionAt(p);
        return r == null ? "" : r.name();
    }

    static Position standNear(Npc n) {
        for (Direction d : Direction.values()) {
            Position p = n.position().translate(d.dx, d.dy);
            if (!d.diagonal() && world.isWalkable(p) && npcs.at(p) == null && world.transitionAt(p) == null) return p;
        }
        throw new AssertionError("no free tile next to " + n.key());
    }

    /** BFS po logice ruchu (krok z otwieraniem drzwi, schody, lina); limit pol, zeby nie przeszukac calego swiata. */
    static Set<Position> explore(Position start, int level, int limit) {
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
                if (TestData.stepOpeningDoors(world, ms, pc, d).moved() && seen.add(pc.position())) q.add(pc.position());
            }
            pc.setPosition(p);
            if (ms.use(pc) == MoveResult.CHANGED_FLOOR && seen.add(pc.position())) q.add(pc.position());
        }
        return seen;
    }

    static boolean near(Set<Position> seen, Position p) {
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) if (seen.contains(p.translate(dx, dy))) return true;
        return false;
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

    static Npc ferryOf(String town) {
        return npcs.all().stream().filter(n -> n.role() == pl.fawizja.pixel.npc.NpcRole.CAPTAIN && n.town().equals(town))
                .findFirst().orElseThrow(() -> new AssertionError("no captain for " + town));
    }

    /** Przewaznie spotykane podloze pietra w regionie (styl pietra: grota, zalane sale, krypty, krysztaly...). */
    static String mainGround(String name, int z) {
        Map<Integer, Integer> n = new HashMap<>();
        for (Region r : world.regions()) {
            if (!r.name().equals(name) || z < r.zMin() || z > r.zMax()) continue;
            for (int y = r.y0(); y <= r.y1(); y++) {
                for (int x = r.x0(); x <= r.x1(); x++) {
                    Position p = new Position(x, y, z);
                    if (world.isWalkable(p)) n.merge(world.floor(z).get(Layer.GROUND, x, y), 1, Integer::sum);
                }
            }
        }
        return n.entrySet().stream().max(Map.Entry.comparingByValue()).map(e -> world.tiles().get(e.getKey()).key()).orElse("");
    }

    // ------------------------------------------------------------------ Wyspa Bursztynowa
    @Test
    void theAmberIsleIsBiggerAndEveryFloorFromMinus6ToPlus3IsReachableFromTheFerry() {
        Npc ferry = ferryOf(ISLE);
        assertFalse(Travel.routes(npcs, ferry).isEmpty(), "ferry routes");
        Set<Position> seen = explore(standNear(ferry), 1000, 400_000);
        assertFalse(seen.contains(world.templeOf("varden")), "the island is reached only by boat");
        long land = seen.stream().filter(p -> p.z() == 0).count();
        assertTrue(land >= 4200, "walkable island land " + land);
        for (int z = -6; z <= 3; z++) {
            final int fz = z;
            String name = z < 0 ? DEEP : ROCK;
            assertTrue(seen.stream().anyMatch(p -> p.z() == fz && region(p).equals(name)), name + " floor " + z);
        }
        for (SpawnPoint s : spawns) {
            String r = region(s.center());
            if (r.equals(ROCK) || r.equals(DEEP)) assertTrue(near(seen, s.center()), "spawn reachable " + s);
        }
        for (WorldMap.LootChest c : world.lootChests()) {
            if (region(c.position()).equals(DEEP)) assertTrue(near(seen, c.position()), "chest reachable " + c);
        }
    }

    @Test
    void underTheAmberRockSixVariedFloorsAreFullOfStrongPacks() {
        double prev = 0;
        Set<String> grounds = new HashSet<>();
        for (int z = -1; z >= -6; z--) {
            List<SpawnPoint> f = on(DEEP, z);
            double avg = avgLevel(f);
            assertTrue(count(f) >= 50, "floor " + z + " crowded: " + count(f));
            assertTrue(avg > prev, "floor " + z + " stronger (" + avg + " vs " + prev + ")");
            assertTrue(f.stream().allMatch(s -> MONSTERS.get(s.monsterKey()).level() >= 260), "strong monsters on " + z);
            assertTrue(f.stream().allMatch(SpawnPoint::pack), "party packs on " + z);
            assertTrue(f.stream().map(SpawnPoint::monsterKey).distinct().count() >= 3, "mixed monsters on " + z);
            grounds.add(mainGround(DEEP, z));
            prev = avg;
        }
        assertTrue(grounds.size() >= 5, "varied floors " + grounds);
        long chests = world.lootChests().stream().filter(c -> region(c.position()).equals(DEEP)).count();
        assertTrue(chests >= 6, "chests in dead-end chambers " + chests);
    }

    @Test
    void onTheRockUpToPlus3VeryManyStrongMonsters() {
        for (int z = 1; z <= 3; z++) {
            List<SpawnPoint> f = on(ROCK, z);
            assertTrue(count(f) >= 30, "+" + z + " crowded: " + count(f));
            assertTrue(f.stream().allMatch(s -> MONSTERS.get(s.monsterKey()).level() >= 300), "strong on +" + z);
            assertTrue(f.stream().allMatch(SpawnPoint::pack), "party packs on +" + z);
        }
        assertTrue(avgLevel(on(ROCK, 3)) > avgLevel(on(ROCK, 1)), "the top is the hardest");
    }

    // ------------------------------------------------------------------ Oboz Lowczyn
    @Test
    void theHuntressCampHasHutsACellarAndAQuestChestReachableOnFoot() {
        Region camp = world.regions().stream().filter(r -> r.name().equals(CAMP)).findFirst()
                .orElseThrow(() -> new AssertionError("no camp region"));
        Position gate = new Position((camp.x0() + camp.x1()) / 2, camp.y1(), 0);
        Set<Position> seen = explore(gate, 40, 300_000);
        assertTrue(seen.stream().anyMatch(p -> region(p).equals(CELLAR) && p.z() == -2), "cellar -2 reachable from the gate");
        WorldMap.LootChest reward = world.lootChests().stream().filter(c -> "elven_bow".equals(c.itemKey())
                && region(c.position()).equals(CELLAR)).findFirst().orElseThrow(() -> new AssertionError("no quest chest"));
        assertTrue(near(seen, reward.position()), "quest chest reachable");
        Set<String> kinds = spawns.stream().filter(s -> region(s.center()).equals(CAMP) || region(s.center()).equals(CELLAR))
                .map(SpawnPoint::monsterKey).collect(Collectors.toSet());
        assertTrue(kinds.containsAll(List.of("grove_huntress", "grove_shieldmaiden", "grove_crone", "grove_matriarch")), "camp monsters " + kinds);
        // za palisada tylko lowczynie (nie zwierzyna dzungli obnizona przez stopniowanie drog)
        Set<String> yard = spawns.stream().filter(s -> s.center().z() == 0 && region(s.center()).equals(CAMP))
                .map(SpawnPoint::monsterKey).collect(Collectors.toSet());
        assertTrue(yard.containsAll(List.of("grove_huntress", "grove_shieldmaiden", "grove_crone")), "camp yard " + yard);
        assertTrue(yard.stream().allMatch(k -> k.startsWith("grove_")), "only huntresses in the camp " + yard);
        assertTrue(world.levelDoors().entrySet().stream().anyMatch(e -> e.getValue() == 20 && e.getKey().z() == 0
                && camp.contains(e.getKey())), "camp gate is a level 20 door");
        for (SpawnPoint s : spawns) {
            String r = region(s.center());
            if (r.equals(CAMP) || r.equals(CELLAR)) {
                int lv = MONSTERS.get(s.monsterKey()).level();
                assertTrue(lv <= 50, "mid-level camp monster " + s);
                assertTrue(near(seen, s.center()), "camp spawn reachable " + s);
            }
        }
        // chaty: drzwi w palisadzie z bali i wnetrza z lozkami
        int beds = 0, logs = 0;
        int bed = world.tiles().byKey("bed").id(), log = world.tiles().byKey("log_wall").id();
        for (int y = camp.y0(); y <= camp.y1(); y++) {
            for (int x = camp.x0(); x <= camp.x1(); x++) {
                if (world.floor(0).get(Layer.OBJECT, x, y) == bed) beds++;
                if (world.floor(0).get(Layer.WALL, x, y) == log) logs++;
            }
        }
        assertTrue(beds >= 6 && logs >= 100, "huts " + beds + " palisade " + logs);
        // oboz osiagalny pieszo z Varden (droga do traktu)
        Set<Position> fromVarden = explore(world.templeOf("varden"), 40, 2_500_000);
        assertTrue(fromVarden.contains(gate) || near(fromVarden, gate), "camp reachable on foot from Varden");
    }

    // ------------------------------------------------------------------ Cyklopolis
    @Test
    void cyclopolisHasForgesAFerryAndSixFloorsOfMediumAndWeakMonsters() {
        Npc ferry = ferryOf(CYC);
        assertTrue(Travel.routes(npcs, ferry).stream().anyMatch(r -> r.to().town().equals("Valmir")), "boat to Valmir");
        Set<Position> seen = explore(standNear(ferry), 200, 300_000);
        Region city = world.regions().stream().filter(r -> r.name().equals(CYC)).findFirst().orElseThrow();
        int forge = world.tiles().byKey("forge").id(), anvil = world.tiles().byKey("anvil").id();
        int forges = 0, anvils = 0;
        for (int y = city.y0(); y <= city.y1(); y++) {
            for (int x = city.x0(); x <= city.x1(); x++) {
                int o = world.floor(0).get(Layer.OBJECT, x, y);
                if (o == forge) forges++;
                if (o == anvil) anvils++;
            }
        }
        assertTrue(forges >= 5 && anvils >= 5, "forges " + forges + " anvils " + anvils);
        Set<String> giants = new HashSet<>();
        for (int z = 0; z >= -6; z--) {
            String name = z == 0 ? CYC : CYC_DEEP;
            List<SpawnPoint> f = on(name, z);
            assertTrue(count(f) >= (z == 0 ? 20 : 30), name + " " + z + " crowded: " + count(f));
            for (SpawnPoint s : f) {
                assertTrue(MONSTERS.get(s.monsterKey()).level() <= 150, "medium or weak " + s);
                assertTrue(near(seen, s.center()), "reachable from the ferry " + s);
                if (s.monsterKey().startsWith("oneeye_")) giants.add(s.monsterKey());
            }
        }
        assertEquals(Set.of("oneeye_brute", "oneeye_hauler", "oneeye_smith"), giants);
        assertTrue(spawns.stream().anyMatch(s -> region(s.center()).equals(CYC_DEEP) && MONSTERS.get(s.monsterKey()).level() < 25),
                "weaker cave fauna under the city");
    }

    // ------------------------------------------------------------------ lochy dziczy
    @Test
    void wildDungeonsAreDeeperAndVaried() {
        Set<String> prefixes = Set.of("Jaskinia", "Grota", "Pieczara", "Nora", "Kopalnia", "Sztolnia", "Szyb",
                "Zapadlisko", "Stary", "Rozpadlina", "Dziura");
        Map<String, Region> wild = new HashMap<>();
        for (Region r : world.regions()) {
            if (r.zMax() == -1 && prefixes.contains(r.name().split(" ")[0]) && !wild.containsKey(r.name())) wild.put(r.name(), r);
        }
        assertTrue(wild.size() >= 60, "wild dungeons " + wild.size());
        long deep4 = wild.values().stream().filter(r -> r.zMin() <= -4).count();
        long deep6 = wild.values().stream().filter(r -> r.zMin() <= -6).count();
        assertTrue(deep4 >= 15 && deep6 >= 3, "deeper dungeons: -4 " + deep4 + ", -6 " + deep6);
        // recznie budowane miejsca (kopalnie Durnkar i Frostholm, kryjowka przemytnikow) maja po jednym pietrze
        Set<String> handBuilt = Set.of("Kopalnia Głębokiego Kilofa", "Kopalnia Żelaznej Brody", "Grota Przemytników",
                "Kopalnia Frostholm");
        assertTrue(wild.values().stream().filter(r -> !handBuilt.contains(r.name())).allMatch(r -> r.zMin() <= -2),
                "every generated wild dungeon has two floors or more");
        // kazde pietro z potworami; rozne style pieter w glebokich lochach
        int varied = 0;
        for (Region r : wild.values()) {
            if (r.zMin() > -4) continue;
            Set<String> g = new HashSet<>();
            for (int z = r.zMax(); z >= r.zMin(); z--) {
                final int fz = z;
                assertTrue(spawns.stream().anyMatch(s -> s.center().z() == fz && r.contains(s.center())), r.name() + " floor " + z);
                g.add(mainGround(r.name(), z));
            }
            if (g.size() >= 2) varied++;
        }
        assertTrue(varied >= deep4 / 2, "varied floor styles in " + varied + " of " + deep4);
    }
}
