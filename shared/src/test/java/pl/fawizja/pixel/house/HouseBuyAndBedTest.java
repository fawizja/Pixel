package pl.fawizja.pixel.house;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.economy.Money;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldLayout;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Domy na sprzedaz i lozka (2026-10-29, Darek: "domy na sprzedaz maja miec w srodku kilka lozek, dotkniecie lozka
 * kladzie postac i wylogowuje; system kupna domow ma dzialac; sporo domow w miastach i poza nimi, nawet na wioskach").
 */
class HouseBuyAndBedTest {

    static WorldMap world;
    static HouseRegistry houses;

    @BeforeAll
    static void load() throws Exception {
        world = TestData.world();
        houses = HouseRegistry.load(WorldLayout.WORLD_MAP, world);
    }

    static GameSession session(PlayerCharacter pc, HouseOwners owners, long[] clock) {
        GameSession s = new GameSession(TestData.ITEMS, world, pc, null, new Random(1), 1);
        s.setWallClock(() -> clock[0]);
        s.setHouses(houses, owners);
        return s;
    }

    /** Postac z podanym zlotem w banku i w plecaku (reszta monet zabrana). */
    static PlayerCharacter rich(String name, long bank, long cash) {
        PlayerCharacter pc = TestData.newCharacterNamed(name);
        if (pc.bank().balance() > 0) pc.bank().debit(pc.bank().balance());
        if (bank > 0) pc.bank().credit(bank);
        long have = Money.totalValue(pc.backpack());
        if (have > 0) assertTrue(Money.pay(pc.backpack(), have).ok());
        if (cash > 0) assertTrue(Money.add(pc.backpack(), cash).ok());
        return pc;
    }

    static Position outsideDoor(House h) {
        for (int[] d : new int[][] {{0, 1}, {0, -1}, {1, 0}, {-1, 0}}) {
            Position q = h.door().translate(d[0], d[1]);
            if (world.isWalkable(q) && !h.contains(q)) return q;
        }
        throw new AssertionError("no tile outside the door of " + h.id());
    }

    /** Wolne pole wnetrza obok lozka. */
    static Position besideBed(House h, Position bed) {
        for (int[] d : new int[][] {{0, 1}, {0, -1}, {1, 0}, {-1, 0}}) {
            Position q = bed.translate(d[0], d[1]);
            if (h.contains(q) && world.isWalkable(q)) return q;
        }
        throw new AssertionError("no free tile next to the bed in " + h.id());
    }

    static boolean logged(GameSession s, String part) {
        for (EventLog.Entry e : s.log().latest(50)) if (e.text().contains(part)) return true;
        return false;
    }

    static House cheapest() {
        return houses.all().stream().min((a, b) -> Long.compare(a.price(), b.price())).orElseThrow();
    }

    // ------------------------------------------------------------------ mapa

    @Test
    void manyHousesForSaleInEveryTownAndOutsideEachWithBedsAndADoorReachableInside() {
        Map<String, Integer> perTown = new HashMap<>();
        int outside = 0, villages = 0;
        GameSession probe = session(rich("Probe", 0, 0), null, new long[] {0});
        for (House h : houses.all()) {
            perTown.merge(h.town(), 1, Integer::sum);
            if (h.id().startsWith("wies_")) villages++;
            else if (h.id().startsWith("wild_") || h.id().matches(".*_p\\d+")) outside++;
            int beds = probe.houseBeds(h);
            assertTrue(beds >= 1 && beds <= 4, h.id() + " beds " + beds);
            if (h.size() > 20) assertTrue(beds >= 2, "bigger houses get more beds: " + h.id());
            assertTrue(world.openings().isDoor(h.door()), "door of " + h.id());
            assertTrue(probe.usableAt(h.door()), "door of " + h.id() + " opens with a tap");
            assertTrue(world.isProtectionZone(h.door()), "door of " + h.id() + " in a protection zone");
            // kazde wolne pole wnetrza osiagalne od drzwi (lozka i meble nie zamykaja przejscia)
            Set<Position> free = new HashSet<>();
            for (int y = h.y0() - 4; y <= h.y1() + 4; y++) {
                for (int x = h.x0() - 4; x <= h.x1() + 4; x++) {
                    Position p = new Position(x, y, h.z());
                    if (h.contains(p) && world.isWalkable(p)) free.add(p);
                }
            }
            Set<Position> seen = new HashSet<>();
            ArrayDeque<Position> q = new ArrayDeque<>();
            q.add(h.door());
            while (!q.isEmpty()) {
                Position p = q.poll();
                for (int[] d : new int[][] {{0, 1}, {0, -1}, {1, 0}, {-1, 0}}) {
                    Position n = p.translate(d[0], d[1]);
                    if (free.contains(n) && seen.add(n)) q.add(n);
                }
            }
            assertEquals(free, seen, "interior of " + h.id() + " reachable from the door");
        }
        for (String town : new String[] {"Varden", "Frostholm", "Selvaran", "Qasirah", "Valmir", "Nerys"}) {
            assertTrue(perTown.getOrDefault(town, 0) >= 5, town + " " + perTown);
        }
        assertTrue(villages >= 6, "houses in villages: " + villages);
        assertTrue(outside >= 10, "houses outside the town walls: " + outside);
        assertTrue(houses.all().size() >= 60, "houses: " + houses.all().size());
    }

    // ------------------------------------------------------------------ kupno

    @Test
    void buyingPaysFromTheBankFirstThenFromTheBackpack() {
        House h = cheapest();
        long[] clock = {1_000_000L};
        PlayerCharacter pc = rich("Kupiec", h.price() - 1500, 2000);
        pc.setPosition(outsideDoor(h));
        GameSession s = session(pc, null, clock);
        assertTrue(s.houseForSale(h));
        assertEquals(GameSession.DoorTap.INFO, s.houseDoorTap(h.door()), "a stranger's tap shows the house window");
        assertEquals(h.price() + 500, s.houseFunds());
        assertTrue(s.buyHouseAtDoor(h.id(), 0));
        assertEquals(h.id(), pc.house());
        assertEquals(0, pc.bank().balance(), "bank first");
        assertEquals(500, Money.totalValue(pc.backpack()), "the rest from the backpack");
        assertTrue(logged(s, "z konta i 1,500 gp z plecaka"));
        assertFalse(s.houseForSale(h));
        assertEquals(HouseAccess.OWNER, s.houseAccess(h));
        assertEquals(clock[0] + HouseRent.period(), pc.rentPaidUntil(), "first week included");
        // wlasciciel otwiera drzwi dotknieciem i wchodzi do srodka
        assertEquals(GameSession.DoorTap.TOGGLE, s.houseDoorTap(h.door()));
        assertTrue(s.mayEnter(new Position(h.x0(), h.y0(), h.z())));
    }

    @Test
    void tooLittleGoldInBankAndBackpackTogetherBuysNothing() {
        House h = cheapest();
        PlayerCharacter pc = rich("Biedak", h.price() / 2, h.price() / 2 - 1);
        pc.setPosition(outsideDoor(h));
        GameSession s = session(pc, null, new long[] {0});
        assertFalse(s.buyHouseAtDoor(h.id(), 0));
        assertNull(pc.house());
        assertEquals(h.price() / 2, pc.bank().balance());
        assertEquals(h.price() / 2 - 1, Money.totalValue(pc.backpack()));
        assertTrue(logged(s, "Dom kosztuje"));
        assertTrue(s.houseForSale(h));
    }

    @Test
    void anOwnedHouseCannotBeBoughtAndOnlyOneHousePerCharacter() {
        House h = cheapest();
        HouseOwnersMap owners = new HouseOwnersMap();
        owners.claim(h.id(), "Halvar");
        PlayerCharacter pc = rich("Drugi", h.price() * 3, 0);
        pc.setPosition(outsideDoor(h));
        GameSession s = session(pc, owners, new long[] {0});
        assertFalse(s.houseForSale(h));
        assertFalse(s.buyHouseAtDoor(h.id(), 0));
        assertTrue(logged(s, "ma już właściciela"));
        assertEquals(h.price() * 3, pc.bank().balance());

        // online klient: dom zajety wg serwera (HACC) - bez tabliczki i bez kupna
        PlayerCharacter online = rich("Trzeci", h.price() * 3, 0);
        online.setPosition(outsideDoor(h));
        GameSession c = session(online, null, new long[] {0});
        c.setTakenHouses(List.of(h.id()));
        assertFalse(c.houseForSale(h));
        assertFalse(c.buyHouseAtDoor(h.id(), 0));

        // drugi dom: najpierw trzeba opuscic pierwszy
        House other = houses.all().stream().filter(x -> !x.id().equals(h.id())).findFirst().orElseThrow();
        PlayerCharacter owner = rich("Posiadacz", other.price() + h.price(), 0);
        owner.setPosition(outsideDoor(other));
        GameSession o = session(owner, null, new long[] {0});
        assertTrue(o.buyHouseAtDoor(other.id(), 0));
        owner.setPosition(outsideDoor(h));
        assertFalse(o.buyHouseAtDoor(h.id(), 0));
        assertTrue(logged(o, "Masz już dom"));
        assertEquals(h.price(), owner.bank().balance());
    }

    @Test
    void theBoughtHouseSurvivesSaveAndLoad() throws Exception {
        House h = cheapest();
        PlayerCharacter pc = rich("Domator", h.price(), 0);
        pc.setPosition(outsideDoor(h));
        long[] clock = {5_000_000L};
        assertTrue(session(pc, null, clock).buyHouseAtDoor(h.id(), 0));
        Position inside = besideBed(h, session(pc, null, clock).bedsIn(h).get(0));
        pc.setPosition(inside);
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertEquals(h.id(), back.house());
        assertEquals(pc.rentPaidUntil(), back.rentPaidUntil());
        GameSession s = session(back, null, clock);
        assertEquals(h.id(), back.house(), "still the owner after loading");
        assertEquals(inside, back.position(), "the owner stays inside");
        assertEquals(HouseAccess.OWNER, s.houseAccess(h));
        assertFalse(s.houseForSale(h));
    }

    // ------------------------------------------------------------------ lozka

    @Test
    void theOwnerLiesDownInHisBedLogsOutAndWakesUpRested() throws Exception {
        House h = houses.all().stream().filter(x -> x.size() > 20).findFirst().orElseThrow();
        long[] clock = {10_000_000L};
        PlayerCharacter pc = rich("Spioch", 0, 0);
        pc.setHouse(h.id());
        GameSession s = session(pc, null, clock);
        List<Position> beds = s.bedsIn(h);
        assertTrue(beds.size() >= 2);
        Position bed = beds.get(0);
        assertTrue(s.usableAt(bed), "a bed is used by tapping it");
        pc.setPosition(besideBed(h, bed));
        pc.damage(pc.health() / 2);
        assertTrue(pc.spendMana(pc.mana() / 2));
        assertTrue(s.useAt(bed, 0));
        assertEquals(bed, s.sleepingIn());
        assertTrue(logged(s, "Kładziesz się do łóżka"));
        assertEquals(clock[0], pc.sleptAt());
        assertEquals(bed, s.takeSleep(), "the client (or server) logs the character out");
        assertNull(s.takeSleep());

        // zapis i pobudka po godzinie: pelne zycie i mana
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertEquals(clock[0], back.sleptAt());
        String wake = BedRest.wakeUp(back, clock[0] + 60 * 60_000L);
        assertNotNull(wake);
        assertTrue(wake.contains("wypoczęty"), wake);
        assertEquals(back.maxHealth(), back.health());
        assertEquals(back.maxMana(), back.mana());
        assertEquals(0, back.sleptAt());
        assertNull(BedRest.wakeUp(back, clock[0] + 120 * 60_000L), "only once");

        // krotki sen: czesc zycia (2% na minute)
        PlayerCharacter nap = rich("Drzemka", 0, 0);
        nap.damage(nap.health() - 1);
        nap.setSleptAt(1000);
        BedRest.wakeUp(nap, 1000 + 10 * 60_000L);
        assertEquals(1 + nap.maxHealth() * 20 / 100, nap.health());
    }

    @Test
    void aStrangersBedIsRefusedAndAGuestMaySleep() {
        House h = houses.all().stream().filter(x -> x.size() > 12).findFirst().orElseThrow();
        HouseOwnersMap owners = new HouseOwnersMap();
        owners.claim(h.id(), "Wlasciciel");
        PlayerCharacter stranger = rich("Obcy", 0, 0);
        GameSession s = session(stranger, owners, new long[] {0});
        Position bed = s.bedsIn(h).get(0);
        // z daleka: od razu odmowa, bez marszu pod okno
        stranger.setPosition(outsideDoor(h));
        assertTrue(s.refuseBed(bed, 0));
        assertTrue(logged(s, "To nie twoje łóżko."));
        // obok lozka (np. przez okno): tez odmowa i nikt sie nie wylogowuje
        stranger.setPosition(besideBed(h, bed));
        assertTrue(s.useAt(bed, 0));
        assertNull(s.takeSleep());
        assertEquals(0, stranger.sleptAt());

        // gosc z wstepem moze sie polozyc
        owners.addGuest(h.id(), "Gosc", HouseAccess.ENTRY);
        PlayerCharacter guest = rich("Gosc", 0, 0);
        GameSession g = session(guest, owners, new long[] {0});
        guest.setPosition(besideBed(h, bed));
        assertFalse(g.refuseBed(bed, 0));
        assertTrue(g.useAt(bed, 0));
        assertEquals(bed, g.takeSleep());
    }

    @Test
    void noSleepingDuringAFight() {
        House h = cheapest();
        PlayerCharacter pc = rich("Wojak", 0, 0);
        pc.setHouse(h.id());
        GameSession s = session(pc, null, new long[] {0});
        Position bed = s.bedsIn(h).get(0);
        pc.setPosition(besideBed(h, bed));
        pc.setLogoutBlockedUntil(60_000);
        assertTrue(s.useAt(bed, 0));
        assertNull(s.takeSleep());
        assertTrue(logged(s, "Nie możesz zasnąć w trakcie walki"));
    }
}
