package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.StartingTowns;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.movement.MoveResult;
import pl.fawizja.pixel.movement.MovementSystem;
import pl.fawizja.pixel.npc.Npc;
import pl.fawizja.pixel.npc.NpcRegistry;
import pl.fawizja.pixel.npc.NpcRole;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.npc.Travel;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Kontynent glowny i ziemie premium (tools/mapgen/realms.py): przy tworzeniu postaci tylko 3 miasta kontynentu glownego,
 * a Valmir, Selvaran i Nerys (premium) sa odciete woda - od Varden nie da sie do nich dojsc pieszo (ani mostem, ani
 * schodami, ani tunelem), tylko statkiem, a statkiem plyna konta premium albo po promocji.
 */
class RealmsTest {

    static final String[] PREMIUM = {"Valmir", "Selvaran", "Nerys"};
    static WorldMap world;
    static NpcRegistry npcs;
    static Set<Position> fromVarden;

    @BeforeAll
    static void load() throws Exception {
        world = TestData.world();
        npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, world, ShopRegistry.loadDefault(TestData.ITEMS));
        fromVarden = explore(world.town("Varden").temple());
    }

    /** Wszystko, co postac na poziomie 1000 osiaga pieszo (drzwi, schody, drabiny, liny) - bez statkow i wozow. */
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

    @Test
    void onlyTheThreeTownsOfTheMainContinentAreOnOneLandmass() {
        for (String n : new String[] {"Varden", "Frostholm", "Qasirah", "Durnkar"}) {
            assertEquals(0, world.town(n).landmass(), n);
            assertFalse(world.town(n).premium(), n);
        }
        assertEquals(1, world.town("Valmir").landmass());
        assertEquals(1, world.town("Selvaran").landmass(), "Valmir and Selvaran share the premium south");
        assertEquals(2, world.town("Nerys").landmass(), "Nerys is an island of its own");
        for (String n : PREMIUM) {
            assertTrue(world.town(n).premium(), n);
            assertFalse(world.town(n).starting(), n);
        }
        assertEquals(3, world.towns().stream().filter(Town::starting).count());
        assertEquals(3, StartingTowns.list(world).size());
    }

    @Test
    void thereIsNoWalkingFromTheMainContinentToAnyPremiumTown() {
        for (String n : new String[] {"Varden", "Frostholm", "Qasirah", "Durnkar"}) {
            assertTrue(fromVarden.contains(world.town(n).temple()), n + " is on the main continent: walk from Varden");
        }
        for (String n : PREMIUM) {
            assertFalse(fromVarden.contains(world.town(n).temple()), n + " must not be walkable from Varden");
            Position t = world.town(n).temple();
            // ani pole obok swiatyni: calkowicie odciete
            for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) {
                assertFalse(fromVarden.contains(t.translate(dx, dy)), n + " surroundings " + dx + "," + dy);
            }
        }
        // ziemia premium jest jedna (Valmir - Selvaran pieszo), ale nie dochodzi do niej zadna droga z polnocy
        Set<Position> fromValmir = explore(world.town("Valmir").temple());
        assertTrue(fromValmir.contains(world.town("Selvaran").temple()));
        assertFalse(fromValmir.contains(world.town("Varden").temple()));
        assertFalse(fromValmir.contains(world.town("Qasirah").temple()));
        assertFalse(fromValmir.contains(world.town("Frostholm").temple()));
        Set<Position> fromNerys = explore(world.town("Nerys").temple());
        assertFalse(fromNerys.contains(world.town("Varden").temple()));
        assertFalse(fromNerys.contains(world.town("Valmir").temple()));
    }

    @Test
    void cartsNeverCrossToAPremiumLand() {
        for (Npc c : npcs.all()) {
            if (c.role() != NpcRole.CARRIER) continue;
            int from = world.town(c.town()).landmass();
            for (Travel.Route r : Travel.routes(npcs, c)) {
                assertEquals(from, world.town(r.to().town()).landmass(), c.key() + " -> " + r.to().key());
            }
        }
        Npc varden = npcs.get("varden_carrier");
        List<String> towns = Travel.routes(npcs, varden).stream().map(r -> r.to().town()).toList();
        for (String n : PREMIUM) assertFalse(towns.contains(n), "no wagon from Varden to " + n);
    }

    @Test
    void everyPremiumTownHasAShipFromTheMainContinentAndTheShipNeedsPremiumOrPromotion() throws Exception {
        Npc vardenCaptain = npcs.get("varden_captain");
        List<String> reach = Travel.routes(npcs, vardenCaptain).stream().map(r -> r.to().town()).toList();
        assertTrue(reach.contains("Valmir") && reach.contains("Selvaran"), "Varden docks sail to the premium south: " + reach);
        assertTrue(Travel.routes(npcs, npcs.get("frost_captain")).stream().anyMatch(r -> r.to().town().equals("Nerys")), "Frostholm - Nerys");
        for (String n : PREMIUM) {
            assertTrue(npcs.all().stream().anyMatch(x -> x.role() == NpcRole.CAPTAIN && x.town().equals(n)), n + " has a captain");
        }
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        MonsterManager mm = new MonsterManager(world, MonsterRegistry.loadDefault(TestData.ITEMS), List.of(), new Random(1));
        GameSession s = new GameSession(TestData.ITEMS, world, pc, mm, new Random(1), 8);
        s.setTownServices(npcs, ShopRegistry.loadDefault(TestData.ITEMS), null);
        s.teleport(vardenCaptain.position().translate(0, -1), 0);
        Position dock = pc.position();
        assertFalse(pc.canSail());
        assertFalse(s.travel("Valmir", 100), "a free character cannot sail to a premium land");
        assertEquals(dock, pc.position());
        pc.setPremium(true);
        assertTrue(s.travel("Valmir", 500));
        assertTrue(pc.position().distanceTo(npcs.get("valmir_captain").position()) <= 1);
        assertFalse(fromVarden.contains(pc.position()), "the premium dock is cut off by water from the main continent");
        // ten sam rejs po promocji profesji (bez premium)
        PlayerCharacter promoted = TestData.newCharacter(Vocation.DRUID);
        promoted.setPromoted(true);
        assertTrue(promoted.canSail());
    }

    @Test
    void charactersThatLiveInAPremiumTownKeepTheirHome() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.setHomeTown("selvaran");
        assertEquals(world.town("Selvaran").temple(), world.templeOf(pc.homeTown()), "they still respawn in their temple");
    }
}
