package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.house.HouseRegistry;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.monster.SpawnTable;
import pl.fawizja.pixel.movement.MoveResult;
import pl.fawizja.pixel.movement.MovementSystem;
import pl.fawizja.pixel.npc.Npc;
import pl.fawizja.pixel.npc.NpcRegistry;
import pl.fawizja.pixel.npc.NpcRole;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Durnkar, miasto krasnoludow na -5 pod gorami (ukryte wejscie przez wawoz i sztolnie -1..-4), i trzy kopalnie
 * krasnoludow do -7 z tools/mapgen/dwarves.py: wszystko osiagalne pieszo z Varden wg logiki ruchu.
 */
class DwarvesTest {

    static final String CITY = "Durnkar";
    static final String[] MINES = {"Kopalnia Żelaznej Brody", "Kopalnia Głębokiego Kilofa", "Stara Sztolnia Granitowa"};
    static WorldMap world;
    static List<SpawnPoint> spawns;
    static final MonsterRegistry MONSTERS = MonsterRegistry.loadDefault(TestData.ITEMS);
    static Set<Position> lv8;
    static NpcRegistry npcs;

    @BeforeAll
    static void load() throws Exception {
        world = TestData.world();
        spawns = SpawnTable.load(WorldLayout.WORLD_MAP, world, MONSTERS);
        lv8 = explore(world.spawn(), 8, false);
        npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, world, ShopRegistry.loadDefault(TestData.ITEMS));
    }

    static List<Region> regions(String name) {
        List<Region> out = world.regions().stream().filter(r -> r.name().equals(name)).toList();
        assertFalse(out.isEmpty(), name);
        return out;
    }

    static boolean in(String name, Position p) {
        return world.regionAt(p) != null && world.regionAt(p).name().equals(name);
    }

    /** Pola osiagalne krokami (z otwieraniem drzwi) i uzyciem schodow/drabin; sameFloor - bez zmiany pietra. */
    static Set<Position> explore(Position start, int level, boolean sameFloor) {
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
                if (TestData.stepOpeningDoors(world, ms, pc, d).moved() && (!sameFloor || pc.position().z() == p.z())
                        && seen.add(pc.position())) {
                    q.add(pc.position());
                }
            }
            if (sameFloor) continue;
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
    void theHiddenEntranceLeadsThroughWindingTunnelsToTheCityAtMinus5() {
        Town city = world.town(CITY);
        assertNotNull(city);
        assertEquals(-5, city.temple().z());
        assertTrue(lv8.contains(city.temple()), "Durnkar temple reachable on foot from Varden");
        for (int z = -4; z <= -1; z++) {
            final int fz = z;
            assertTrue(lv8.stream().anyMatch(p -> p.z() == fz && in("Sztolnie do Durnkaru", p)), "tunnel floor " + z);
        }
        // wejscie z powierzchni: schody w dol do sztolni, w gorach (pole otoczone skala), bez drogi tuz obok
        List<Transition> down = world.floor(0).transitions().values().stream()
                .filter(t -> t.to().z() == -1 && in("Sztolnie do Durnkaru", t.to())).toList();
        assertEquals(1, down.size(), "one hidden entrance");
        Position hidden = down.get(0).from();
        assertTrue(near(lv8, hidden), "hidden entrance reachable");
        int mountain = world.tiles().byKey("mountain").id();
        int rock = 0;
        int cobble = world.tiles().byKey("cobblestone").id();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (world.floor(0).get(Layer.WALL, hidden.x() + dx, hidden.y() + dy) == mountain) rock++;
                assertNotEquals(cobble, world.floor(0).get(Layer.GROUND, hidden.x() + dx, hidden.y() + dy));
            }
        }
        assertTrue(rock >= 12, "the entrance cave is inside the mountains (" + rock + " rock tiles around)");
        // nikt nie trafi do miasta prosta droga: z wejscia do swiatyni co najmniej 4 zmiany pieter i dluga droga
        assertTrue(Math.abs(hidden.x() - city.temple().x()) + Math.abs(hidden.y() - city.temple().y()) >= 10);
        // w sztolniach i w miescie nie ma potworow
        for (SpawnPoint s : spawns) {
            assertFalse(in("Sztolnie do Durnkaru", s.center()) || in(CITY, s.center()), "monster on the way to Durnkar " + s);
        }
    }

    @Test
    void durnkarHasATempleShopsDepotBankHousesAndDwarfNpcs() {
        Town city = world.town(CITY);
        assertTrue(world.isProtectionZone(city.temple()));
        assertFalse(city.starting(), "Durnkar is a residence town, not a starting town");
        List<Npc> dwarves = npcs.all().stream().filter(n -> n.town().equals(CITY)).toList();
        Set<NpcRole> roles = dwarves.stream().map(Npc::role).collect(Collectors.toSet());
        assertTrue(roles.containsAll(List.of(NpcRole.PRIEST, NpcRole.BANKER, NpcRole.HOUSING, NpcRole.KING, NpcRole.IMBUER,
                NpcRole.MERCHANT, NpcRole.GUARD, NpcRole.GUIDE)), "roles " + roles);
        long shops = dwarves.stream().filter(n -> n.shopKey() != null).count();
        assertTrue(shops >= 6, "smith, armourer, runes, tools, tavern, merchant, pets: " + shops);
        for (Npc n : dwarves) {
            assertEquals(-5, n.position().z(), n.key());
            assertTrue(world.isProtectionZone(n.position()), n.key() + " in PZ");
            assertTrue(lv8.stream().anyMatch(p -> p.z() == -5 && p.distanceTo(n.position()) <= Npc.TALK_RANGE), n.key() + " reachable");
        }
        // depozyt: skrzynie w banku przy swiatyni
        int locker = world.tiles().byKey("depot_locker").id();
        int lockers = 0;
        for (int y = city.temple().y() - 40; y <= city.temple().y() + 40; y++) {
            for (int x = city.temple().x() - 60; x <= city.temple().x() + 60; x++) {
                if (world.floor(-5).get(Layer.OBJECT, x, y) == locker) lockers++;
            }
        }
        assertTrue(lockers >= 3, "depot lockers " + lockers);
        // domy na sprzedaz pod ziemia
        HouseRegistry houses;
        try {
            houses = HouseRegistry.load(WorldLayout.WORLD_MAP, world);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        List<pl.fawizja.pixel.house.House> mine = houses.all().stream().filter(h -> h.town().equals(CITY)).toList();
        assertTrue(mine.size() >= 10, "houses " + mine.size());
        for (pl.fawizja.pixel.house.House h : mine) {
            assertEquals(-5, h.z());
            assertTrue(near(lv8, h.door()), "house door reachable " + h.id());
        }
    }

    @Test
    void thePriestOfDurnkarLetsYouLiveThere() {
        Npc priest = npcs.all().stream().filter(n -> n.town().equals(CITY) && n.role() == NpcRole.PRIEST).findFirst().orElseThrow();
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        GameSession s = new GameSession(TestData.ITEMS, world, pc, null, new Random(1), 1);
        s.setTownServices(npcs, ShopRegistry.loadDefault(TestData.ITEMS), null);
        Position stand = null;
        for (int dx = -1; dx <= 1 && stand == null; dx++) {
            for (int dy = -1; dy <= 1 && stand == null; dy++) {
                Position p = priest.position().translate(dx, dy);
                if ((dx != 0 || dy != 0) && world.isWalkable(p) && npcs.at(p) == null && world.transitionAt(p) == null) stand = p;
            }
        }
        s.teleport(stand, 1000);
        assertEquals(priest, s.npcNearby());
        s.settleHere(1100);
        assertEquals("durnkar", pc.homeTown());
        assertEquals(world.town(CITY).temple(), world.templeOf(pc.homeTown()), "respawn in the dwarf temple");
    }

    @Test
    void everyMineGoesDownToMinus7WithStrongerAndMoreMonstersOnEachFloor() {
        for (String mine : MINES) {
            double prev = 0;
            int prevCount = 0;
            for (int z = -1; z >= -7; z--) {
                final int fz = z;
                assertTrue(lv8.stream().anyMatch(p -> p.z() == fz && in(mine, p)), mine + " floor " + z + " reachable");
                List<SpawnPoint> f = spawns.stream().filter(s -> s.center().z() == fz && in(mine, s.center())).toList();
                double avg = f.stream().mapToDouble(s -> MONSTERS.get(s.monsterKey()).level() * s.count()).sum()
                        / Math.max(1, f.stream().mapToInt(SpawnPoint::count).sum());
                int count = f.stream().mapToInt(SpawnPoint::count).sum();
                assertTrue(avg > prev, mine + " floor " + z + " stronger (" + avg + " vs " + prev + ")");
                assertTrue(count >= prevCount * 0.8 && count >= 15, mine + " floor " + z + " crowded: " + count + " vs " + prevCount);
                prev = avg;
                prevCount = count;
                for (SpawnPoint s : f) assertTrue(lv8.contains(s.center()) || near(lv8, s.center()), "spawn reachable " + s);
            }
            Set<String> kinds = spawns.stream().filter(s -> in(mine, s.center())).map(SpawnPoint::monsterKey).collect(Collectors.toSet());
            assertTrue(kinds.containsAll(List.of("dwarf", "dwarf_miner", "dwarf_soldier", "dwarf_warden", "dwarf_earthcaller",
                    "mine_grub", "deep_worm", "rock_golem", "earth_spirit", "deep_behemot")), mine + " " + kinds);
            // na dnie behemoty, na gorze tylko slabe potwory dla poczatkujacych
            assertTrue(spawns.stream().anyMatch(s -> s.center().z() == -7 && in(mine, s.center()) && s.monsterKey().equals("deep_behemot")), mine);
            assertTrue(spawns.stream().filter(s -> s.center().z() == -1 && in(mine, s.center()))
                    .allMatch(s -> MONSTERS.get(s.monsterKey()).level() <= 12), mine + " -1 is for beginners");
        }
    }

    @Test
    void oneMineOpensIntoDurnkarThroughALevelDoorAtMinus5() {
        Position temple = world.town(CITY).temple();
        Map.Entry<Position, Integer> door = world.levelDoors().entrySet().stream()
                .filter(e -> e.getKey().z() == -5 && e.getKey().distanceTo(temple) < 120 && e.getKey().x() > temple.x())
                .findFirst().orElseThrow(() -> new AssertionError("no level door at the east gate"));
        Set<Position> weak = explore(temple, door.getValue() - 1, true);
        Set<Position> strong = explore(temple, door.getValue(), true);
        assertTrue(near(weak, door.getKey()), "the gate can be reached from the city");
        String mine = MINES[0];
        Position d = door.getKey();
        assertFalse(weak.stream().anyMatch(p -> p.x() > d.x() && in(mine, p)), "too weak - the door stays shut");
        assertTrue(strong.stream().anyMatch(p -> in(mine, p)), "from level " + door.getValue() + " the mine is right behind the gate");
        assertTrue(spawns.stream().anyMatch(s -> s.center().z() == -5 && in(mine, s.center()) && strong.contains(s.center())),
                "mine monsters behind the gate at " + d);
        // pozostale kopalnie nie lacza sie z miastem na tym pietrze
        for (int i = 1; i < MINES.length; i++) {
            final String m = MINES[i];
            assertFalse(strong.stream().anyMatch(p -> in(m, p)), m);
        }
    }

    @Test
    void newDwarfMonstersAreTibiaLike() {
        String[] order = {"mine_grub", "dwarf", "dwarf_miner", "dwarf_soldier", "deep_worm", "dwarf_warden", "rock_golem",
                "dwarf_earthcaller", "earth_spirit", "deep_behemot"};
        int prev = 0;
        for (String k : order) {
            assertNotNull(MONSTERS.get(k), k);
            assertTrue(MONSTERS.get(k).level() >= prev, k);
            prev = MONSTERS.get(k).level();
            assertFalse(MONSTERS.get(k).boss());
        }
        assertEquals(pl.fawizja.pixel.combat.DamageType.EARTH, MONSTERS.get("dwarf_earthcaller").damageType());
    }
}
