package pl.fawizja.pixel.house;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.npc.Npc;
import pl.fawizja.pixel.npc.NpcRegistry;
import pl.fawizja.pixel.npc.NpcRole;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldLayout;
import pl.fawizja.pixel.world.WorldMap;

/** Domy (etap 6): rejestr z mapy, kupno u zarzadcy za zloto z banku, wnetrze tylko dla wlasciciela, zapis. */
class HouseSystemTest {

    static WorldMap world;
    static NpcRegistry npcs;
    static HouseRegistry houses;

    @BeforeAll
    static void load() throws Exception {
        world = TestData.world();
        ShopRegistry shops = ShopRegistry.loadDefault(TestData.ITEMS);
        npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, world, shops);
        houses = HouseRegistry.load(WorldLayout.WORLD_MAP, world);
    }

    GameSession session(PlayerCharacter pc, HouseOwners owners) {
        GameSession s = new GameSession(TestData.ITEMS, world, pc, null, new Random(1), 1);
        s.setTownServices(npcs, ShopRegistry.loadDefault(TestData.ITEMS), null);
        s.setHouses(houses, owners);
        return s;
    }

    static Npc housing(String town) {
        return npcs.all().stream().filter(n -> n.role() == NpcRole.HOUSING && n.town().equals(town)).findFirst().orElseThrow();
    }

    /** Pole obok zarzadcy, z ktorego mozna z nim rozmawiac. */
    static Position nextTo(Npc n) {
        for (Direction d : Direction.values()) {
            Position p = n.position().translate(d.dx, d.dy);
            if (world.isWalkable(p) && npcs.at(p) == null && npcs.nearest(p, world) == n) return p;
        }
        throw new AssertionError("no spot next to " + n.key());
    }

    @Test
    void everyTownHasHousesWithReachableDoorsInsideProtectionZones() {
        for (String town : new String[] {"Varden", "Frostholm", "Selvaran", "Qasirah"}) {
            List<House> list = houses.inTown(town);
            assertFalse(list.isEmpty(), town);
            for (House h : list) {
                assertTrue(world.isWalkable(h.door()) && world.isProtectionZone(h.door()), h.id());
                int walkable = 0;
                for (int y = h.y0(); y <= h.y1(); y++) {
                    for (int x = h.x0(); x <= h.x1(); x++) {
                        Position p = new Position(x, y, h.z());
                        assertTrue(world.isProtectionZone(p), h.id() + " " + p);
                        if (world.isWalkable(p)) walkable++;
                        assertNull(npcs.at(p), "npc inside house " + h.id());
                    }
                }
                assertTrue(walkable >= 4, h.id());
                assertEquals(h.price(), h.size() * 5000L * (town.equals("Varden") ? 2 : 1), h.id());
            }
        }
    }

    @Test
    void buyingAHouseCostsBankGoldAndOnlyTheOwnerMayEnter() {
        Npc agent = housing("Frostholm");
        House h = houses.inTown("Frostholm").get(0);
        PlayerCharacter pc = TestData.newCharacterNamed("Ingrid");
        pc.bank().credit(h.price() + 500);
        pc.setPosition(nextTo(agent));
        GameSession s = session(pc, null);

        Position inside = null;
        for (int[] d : new int[][] {{0, 1}, {0, -1}, {1, 0}, {-1, 0}}) {
            Position q = h.door().translate(d[0], d[1]);
            if (h.contains(q)) inside = q;
        }
        assertNotNull(inside, "door leads into the house");
        assertFalse(s.mayEnter(inside), "closed before buying");
        assertTrue(s.buyHouse(h.id(), 0));
        assertEquals(h.id(), pc.house());
        assertEquals(500, pc.bank().balance());
        assertTrue(s.mayEnter(inside));

        assertFalse(s.buyHouse(houses.inTown("Frostholm").get(1).id(), 0), "one house per character");
        assertTrue(s.leaveHouse(0));
        assertNull(pc.house());
        assertFalse(s.mayEnter(inside));
    }

    @Test
    void sharedOwnersKeepAHouseForOnePlayerAndBlockOthers() {
        Npc agent = housing("Qasirah");
        House h = houses.inTown("Qasirah").get(0);
        HouseOwnersMap owners = new HouseOwnersMap();
        PlayerCharacter a = TestData.newCharacterNamed("Amira");
        PlayerCharacter b = TestData.newCharacterNamed("Basim");
        for (PlayerCharacter pc : List.of(a, b)) {
            pc.bank().credit(h.price());
            pc.setPosition(nextTo(agent));
        }
        GameSession sa = session(a, owners), sb = session(b, owners);
        assertTrue(sa.buyHouse(h.id(), 0));
        assertFalse(sb.buyHouse(h.id(), 0), "already owned");
        assertEquals(h.price(), b.bank().balance(), "no money taken on failure");

        // obcy nie wejdzie przez drzwi, wlasciciel tak
        Position inside = null;
        for (int[] d : new int[][] {{0, 1}, {0, -1}, {1, 0}, {-1, 0}}) {
            Position q = h.door().translate(d[0], d[1]);
            if (h.contains(q)) inside = q;
        }
        assertNotNull(inside, "door leads into the house");
        b.setPosition(h.door());
        assertFalse(sb.mayEnter(inside));
        assertTrue(sa.mayEnter(inside));
        assertEquals("Amira", HouseOwnersMap.decode(owners.encode()).ownerOf(h.id()));
    }

    @Test
    void tooLittleGoldInTheBankBuysNothing() {
        Npc agent = housing("Varden");
        House h = houses.inTown("Varden").get(0);
        PlayerCharacter pc = TestData.newCharacterNamed("Bolek");
        pc.bank().credit(h.price() - 1);
        pc.setPosition(nextTo(agent));
        assertFalse(session(pc, null).buyHouse(h.id(), 0));
        assertNull(pc.house());
        assertEquals(h.price() - 1, pc.bank().balance());
    }

    @Test
    void ownedHouseIsSavedAndAStrangerInsideIsMovedToTheDoor() throws Exception {
        House h = houses.inTown("Selvaran").get(0);
        PlayerCharacter pc = TestData.newCharacterNamed("Marek");
        pc.setHouse(h.id());
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        assertEquals(h.id(), codec.decode(codec.encode(pc)).house());

        // dom zajety przez kogos innego na serwerze: postac traci dom i wychodzi przed drzwi
        HouseOwnersMap owners = new HouseOwnersMap();
        owners.claim(h.id(), "Ktos");
        pc.setPosition(new Position(h.x0() + 1, h.y0() + 1, h.z()));
        session(pc, owners);
        assertNull(pc.house());
        assertEquals(h.door(), pc.position());
    }

    @Test
    void guestsMayEnterAndAreSavedWhileATransferResetsThem() {
        House h = houses.inTown("Selvaran").get(0);
        HouseOwnersMap owners = new HouseOwnersMap();
        assertFalse(owners.addGuest(h.id(), "Ola"), "no guests without an owner");
        assertTrue(owners.claim(h.id(), "Kasia"));
        assertTrue(owners.addGuest(h.id(), "Ola"));
        assertFalse(owners.addGuest(h.id(), "ola"), "already a guest");
        assertFalse(owners.addGuest(h.id(), "Kasia"), "owner is not a guest");
        HouseOwnersMap back = HouseOwnersMap.decode(owners.encode());
        assertEquals("Kasia", back.ownerOf(h.id()));
        assertEquals(List.of("Ola"), back.guests(h.id()));
        assertTrue(back.mayEnter(h.id(), "OLA"));
        assertFalse(back.mayEnter(h.id(), "Basia"));

        Position inside = null;
        for (int[] d : new int[][] {{0, 1}, {0, -1}, {1, 0}, {-1, 0}}) {
            Position q = h.door().translate(d[0], d[1]);
            if (h.contains(q)) inside = q;
        }
        GameSession guest = session(TestData.newCharacterNamed("Ola"), back);
        GameSession stranger = session(TestData.newCharacterNamed("Basia"), back);
        assertTrue(guest.mayEnter(inside));
        assertFalse(stranger.mayEnter(inside));

        assertTrue(back.transfer(h.id(), "Basia"));
        assertEquals("Basia", back.ownerOf(h.id()));
        assertTrue(back.guests(h.id()).isEmpty(), "new owner starts without guests");
        assertFalse(guest.mayEnter(inside));
        assertTrue(stranger.mayEnter(inside));
        back.release(h.id());
        assertNull(back.ownerOf(h.id()));
    }

    @Test
    void sellingAHouseToTheTownRefundsHalfThePrice() {
        Npc agent = housing("Frostholm");
        House h = houses.inTown("Frostholm").get(2);
        PlayerCharacter pc = TestData.newCharacterNamed("Halvar");
        pc.bank().credit(h.price());
        pc.setPosition(nextTo(agent));
        GameSession s = session(pc, new HouseOwnersMap());
        assertTrue(s.buyHouse(h.id(), 0));
        assertEquals(0, pc.bank().balance());
        assertTrue(s.addHouseGuest("Sven", 0));
        assertTrue(s.houseInfo().contains("Sven"));
        assertTrue(s.leaveHouse(0));
        assertEquals(h.price() * GameSession.HOUSE_SELLBACK_PERCENT / 100, pc.bank().balance());
    }

    @Test
    void houseChatCommands() {
        assertArrayEquals(new String[] {"HOUSE", "INFO"}, pl.fawizja.pixel.net.ChatCommands.toMessage("/dom"));
        assertArrayEquals(new String[] {"HOUSE", "GUEST", "Stary Wilk"}, pl.fawizja.pixel.net.ChatCommands.toMessage("/dom wpuść Stary Wilk"));
        assertArrayEquals(new String[] {"HOUSE", "UNGUEST", "Ola"}, pl.fawizja.pixel.net.ChatCommands.toMessage("/dom wyrzuc Ola"));
        assertArrayEquals(new String[] {"HOUSE", "SELL", "Ola", "150000"}, pl.fawizja.pixel.net.ChatCommands.toMessage("/dom sprzedaj Ola, 150k"));
        assertArrayEquals(new String[] {"HOUSE", "ACCEPT"}, pl.fawizja.pixel.net.ChatCommands.toMessage("/dom kup"));
        assertArrayEquals(new String[] {"HOUSE", "CANCEL"}, pl.fawizja.pixel.net.ChatCommands.toMessage("/dom anuluj"));
        assertNull(pl.fawizja.pixel.net.ChatCommands.toMessage("/dom sprzedaj Ola"));
    }
}
