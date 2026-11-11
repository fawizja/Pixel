package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.monster.MonsterManager;
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
 * Srebrna Rzeka z delta pod Varden i Wulkan Pozogi (tools/mapgen/delta_volcano.py, Darek 2026-11-11): wulkan odciety
 * pierscieniem lawy - tylko statkiem (premium albo promocja), od przystani dojscie na kazde pietro -7..+7 pelne
 * silnych stad; drogi przez delte po mostach.
 */
class VolcanoDeltaTest {

    static final String VOLCANO = "Wulkan Pożogi", CONE = "Stożek Pożogi", DEEP = "Trzewia Pożogi";
    static final String DELTA = "Delta Srebrnej Rzeki";
    static WorldMap world;
    static List<SpawnPoint> spawns;
    static NpcRegistry npcs;
    static Npc captain;
    static Set<Position> fromHarbour;
    static final MonsterRegistry MONSTERS = MonsterRegistry.loadDefault(TestData.ITEMS);

    @BeforeAll
    static void load() throws Exception {
        world = TestData.world();
        spawns = SpawnTable.load(WorldLayout.WORLD_MAP, world, MONSTERS);
        npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, world, ShopRegistry.loadDefault(TestData.ITEMS));
        captain = npcs.get("pozoga_captain");
        assertNotNull(captain, "volcano captain");
        fromHarbour = explore(standNear(captain), 1000);
    }

    static Position standNear(Npc n) {
        for (Direction d : Direction.values()) {
            Position p = n.position().translate(d.dx, d.dy);
            if (!d.diagonal() && world.isWalkable(p) && npcs.at(p) == null && world.transitionAt(p) == null) return p;
        }
        throw new AssertionError("no free tile next to " + n.key());
    }

    static String region(Position p) {
        Region r = world.regionAt(p);
        return r == null ? "" : r.name();
    }

    /** BFS po logice ruchu (krok z otwieraniem drzwi i uzycie schodow, lina w plecaku). */
    static Set<Position> explore(Position start, int level) {
        MovementSystem ms = new MovementSystem(world, TestData.ITEMS.byKey("rope").id());
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        if (level > pc.level()) pc.addExperience(Balance.experienceForLevel(level) - pc.experience());
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

    static boolean near(Set<Position> seen, Position p) {
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) if (seen.contains(p.translate(dx, dy))) return true;
        return false;
    }

    @Test
    void theVolcanoIsCutOffByALavaRingAndReachedOnlyByShip() {
        Set<Position> onFoot = explore(world.spawn(), 1000);
        assertTrue(onFoot.stream().noneMatch(p -> region(p).equals(VOLCANO) || region(p).equals(CONE) || region(p).equals(DEEP)),
                "the volcano must not be reachable on foot from Varden");
        assertFalse(near(onFoot, captain.position()), "the volcano harbour is behind the lava");
        // pierscien lawy: od strony ladu (zachod i polnoc) miedzy popiolem wulkanu a reszta ladu jest lawa
        int lava = world.tiles().byKey("lava").id();
        int lavaTiles = 0;
        Position c = captain.position();
        for (int y = c.y() - 60; y <= c.y() + 60; y++) {
            for (int x = c.x() - 100; x <= c.x() + 10; x++) {
                if (world.floor(0).get(Layer.GROUND, x, y) == lava) lavaTiles++;
            }
        }
        assertTrue(lavaTiles >= 150, "lava ring tiles " + lavaTiles);
        assertEquals(VOLCANO, captain.town());
        assertTrue(world.isProtectionZone(captain.position()), "harbour is a protection zone");
        Set<String> from = npcs.all().stream().filter(n -> n.role() == NpcRole.CAPTAIN)
                .filter(n -> Travel.routes(npcs, n).stream().anyMatch(r -> r.to() == captain)).map(Npc::town).collect(Collectors.toSet());
        assertTrue(from.containsAll(List.of("Varden", "Valmir", "Selvaran", "Qasirah", "Frostholm", "Nerys")), "ships from " + from);
        for (Travel.Route r : Travel.routes(npcs, captain)) {
            assertTrue(Travel.routes(npcs, r.to()).stream().anyMatch(b -> b.to() == captain), "way back from " + r.to().key());
        }
    }

    @Test
    void fromTheHarbourEveryFloorFromMinus7ToPlus7IsReachable() {
        for (int z = -7; z <= 7; z++) {
            final int fz = z;
            String name = z < 0 ? DEEP : z > 0 ? CONE : VOLCANO;
            assertTrue(fromHarbour.stream().anyMatch(p -> p.z() == fz && region(p).equals(name)), name + " floor " + z);
        }
        for (SpawnPoint s : spawns) {
            String r = region(s.center());
            if (r.equals(VOLCANO) || r.equals(CONE) || r.equals(DEEP)) {
                assertTrue(fromHarbour.contains(s.center()) || near(fromHarbour, s.center()), "spawn reachable " + s);
            }
        }
    }

    static List<SpawnPoint> on(String region, int z) {
        return spawns.stream().filter(s -> s.center().z() == z && region(s.center()).equals(region)).toList();
    }

    static int count(List<SpawnPoint> f) {
        return f.stream().mapToInt(SpawnPoint::count).sum();
    }

    static double avgLevel(List<SpawnPoint> f) {
        return f.stream().mapToDouble(s -> MONSTERS.get(s.monsterKey()).level() * s.count()).sum() / Math.max(1, count(f));
    }

    @Test
    void underTheVolcanoVeryManyStrongMonstersGetStrongerWithDepth() {
        double prev = 0;
        for (int z = -1; z >= -7; z--) {
            List<SpawnPoint> f = on(DEEP, z);
            double avg = avgLevel(f);
            assertTrue(count(f) >= 40, "floor " + z + " crowded: " + count(f));
            assertTrue(avg > prev, "floor " + z + " stronger (" + avg + " vs " + prev + ")");
            assertTrue(f.stream().allMatch(s -> MONSTERS.get(s.monsterKey()).level() >= 300), "only strong monsters on " + z);
            assertTrue(f.stream().allMatch(SpawnPoint::pack), "party packs on " + z);
            prev = avg;
        }
        assertTrue(on(DEEP, -7).stream().anyMatch(s -> s.monsterKey().equals("winged_spawn") || s.monsterKey().equals("abyssal_demon")));
    }

    @Test
    void theConeHasVeryManyMixedMonstersUpToPlus7() {
        for (int z = 1; z <= 7; z++) {
            List<SpawnPoint> f = on(CONE, z);
            assertTrue(count(f) >= 25, "cone +" + z + " crowded: " + count(f));
            Set<String> kinds = f.stream().map(SpawnPoint::monsterKey).collect(Collectors.toSet());
            Set<String> classes = f.stream().map(s -> MONSTERS.get(s.monsterKey()).damageType().name()).collect(Collectors.toSet());
            assertTrue(kinds.size() >= 3 && classes.size() >= 2, "mixed monsters on +" + z + ": " + kinds);
            assertTrue(f.stream().allMatch(s -> MONSTERS.get(s.monsterKey()).level() >= 300), "party exp on +" + z);
        }
        assertTrue(count(on(VOLCANO, 0)) >= 20, "monsters around the cone");
    }

    @Test
    void onlyPremiumOrPromotedCharactersMaySail() throws Exception {
        Npc varden = npcs.get("varden_captain");
        MonsterManager mm = new MonsterManager(world, MONSTERS, List.of(), new Random(1));
        // konto darmowe: kapitan odmawia z jasnym komunikatem
        PlayerCharacter free = TestData.newCharacter(Vocation.SORCERER);
        free.backpack().add(new pl.fawizja.pixel.item.ItemStack(TestData.ITEMS.byKey("platinum_coin").id(), 50));
        GameSession s = new GameSession(TestData.ITEMS, world, free, mm, new Random(1), 8);
        s.setTownServices(npcs, ShopRegistry.loadDefault(TestData.ITEMS), null);
        s.teleport(standNear(varden), 0);
        assertFalse(s.travel(VOLCANO, 500));
        assertTrue(s.log().latest(5).stream().anyMatch(e -> e.text().contains("premium albo postaci z promocją")),
                "clear refusal for a free account");
        // sama promocja (bez premium) wystarcza
        PlayerCharacter promo = TestData.newCharacter(Vocation.KNIGHT);
        promo.setPromoted(true);
        promo.backpack().add(new pl.fawizja.pixel.item.ItemStack(TestData.ITEMS.byKey("platinum_coin").id(), 50));
        assertFalse(promo.isPremium());
        GameSession p = new GameSession(TestData.ITEMS, world, promo, mm, new Random(1), 8);
        p.setTownServices(npcs, ShopRegistry.loadDefault(TestData.ITEMS), null);
        p.teleport(standNear(varden), 0);
        assertTrue(p.travel(VOLCANO, 1000), "promoted characters sail");
        assertTrue(promo.position().distanceTo(captain.position()) <= 1, "at the volcano harbour " + promo.position());
        assertTrue(p.travel("Varden", 2000), "and back");
        // premium bez promocji tez plynie
        PlayerCharacter prem = TestData.newCharacter(Vocation.DRUID);
        prem.setPremium(true);
        prem.backpack().add(new pl.fawizja.pixel.item.ItemStack(TestData.ITEMS.byKey("platinum_coin").id(), 50));
        GameSession q = new GameSession(TestData.ITEMS, world, prem, mm, new Random(1), 8);
        q.setTownServices(npcs, ShopRegistry.loadDefault(TestData.ITEMS), null);
        q.teleport(standNear(varden), 0);
        assertTrue(q.travel(VOLCANO, 3000));
        assertTrue(prem.canSail() && promo.canSail() && !free.canSail());
    }

    @Test
    void theWideRiverEndsInADeltaWithBridgesForTheRoads() {
        int water = world.tiles().byKey("water").id();
        int bridge = world.tiles().byKey("bridge").id();
        Region delta = world.regions().stream().filter(r -> r.name().equals(DELTA)).findFirst()
                .orElseThrow(() -> new AssertionError("no delta region"));
        // szeroka rzeka: miedzy Varden a delta koryto ma co najmniej 14 pol szerokosci
        int widest = 0;
        for (int y = delta.y0() - 90; y < delta.y0(); y++) {
            int run = 0;
            for (int x = delta.x0() - 40; x <= delta.x1(); x++) {
                run = world.floor(0).get(Layer.GROUND, x, y) == water ? run + 1 : 0;
                widest = Math.max(widest, run);
            }
        }
        assertTrue(widest >= 14, "river width " + widest);
        // delta: na kilku poziomych liniach w poprzek wachlarza co najmniej trzy osobne koryta
        int best = 0;
        for (int y = delta.y0() + 40; y <= delta.y0() + 90; y++) {
            int arms = 0;
            boolean in = false;
            for (int x = delta.x0(); x <= delta.x1(); x++) {
                boolean w = world.floor(0).get(Layer.GROUND, x, y) == water;
                if (w && !in) arms++;
                in = w;
            }
            best = Math.max(best, arms);
        }
        assertTrue(best >= 3, "delta arms " + best);
        // mosty drog nad odnogami i droga Valmir - Selvaran pieszo przez delte
        int bridges = 0;
        for (int y = delta.y0(); y <= delta.y1(); y++) {
            for (int x = delta.x0(); x <= delta.x1(); x++) if (world.floor(0).get(Layer.GROUND, x, y) == bridge) bridges++;
        }
        assertTrue(bridges >= 20, "bridges in the delta " + bridges);
        Set<Position> valmir = explore(world.town("Valmir").temple(), 8);
        assertTrue(valmir.contains(world.town("Selvaran").temple()), "Valmir - Selvaran road crosses the delta");
        // ziemie premium (Valmir, Selvaran) sa odciete od kontynentu glownego kanalami: do Varden tylko statkiem (RealmsTest)
        assertFalse(valmir.contains(world.town("Varden").temple()), "no walking from the premium south to Varden");
        // lowisko: w delcie lowi sie wegorze (data/fishing.csv)
        assertTrue(pl.fawizja.pixel.gathering.FishingSystem.loadBiomeFish(TestData.ITEMS).stream()
                .anyMatch(f -> f.region().equals(DELTA)), "delta fishing");
    }
}
