package pl.fawizja.pixel.house;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.economy.BankAccount;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.npc.NpcRegistry;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldLayout;
import pl.fawizja.pixel.world.WorldMap;

/** Czynsz za domy, okno przy drzwiach, poziomy dostepu gosci i wczytywanie starych zapisow (2026-10-28). */
class HouseRentAccessTest {

    static WorldMap world;
    static NpcRegistry npcs;
    static HouseRegistry houses;
    static final long WEEK = HouseRent.WEEK_MS;

    @BeforeAll
    static void load() throws Exception {
        world = TestData.world();
        npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, world, ShopRegistry.loadDefault(TestData.ITEMS));
        houses = HouseRegistry.load(WorldLayout.WORLD_MAP, world);
    }

    static GameSession session(PlayerCharacter pc, HouseOwners owners, long[] clock) {
        GameSession s = new GameSession(TestData.ITEMS, world, pc, null, new Random(1), 1);
        s.setTownServices(npcs, ShopRegistry.loadDefault(TestData.ITEMS), null);
        s.setWallClock(() -> clock[0]);
        s.setHouses(houses, owners);
        return s;
    }

    static PlayerCharacter named(String name, long bank) {
        PlayerCharacter pc = TestData.newCharacterNamed(name);
        if (pc.bank().balance() > 0) pc.bank().debit(pc.bank().balance());
        if (bank > 0) pc.bank().credit(bank);
        return pc;
    }

    /** Pole przed drzwiami (na ulicy) albo za nimi (w domu). */
    static Position nextToDoor(House h, boolean inside) {
        for (int[] d : new int[][] {{0, 1}, {0, -1}, {1, 0}, {-1, 0}}) {
            Position q = h.door().translate(d[0], d[1]);
            if (world.isWalkable(q) && h.contains(q) == inside && npcs.at(q) == null) return q;
        }
        throw new AssertionError("no tile next to door of " + h.id());
    }

    static boolean logged(GameSession s, String part) {
        for (EventLog.Entry e : s.log().latest(50)) if (e.text().contains(part)) return true;
        return false;
    }

    // ------------------------------------------------------------------ czynsz

    @Test
    void rentIsTwoPercentOfThePriceRoundedToTenGold() {
        House h = houses.inTown("Varden").get(0);
        assertEquals(Math.max(100, (h.price() * 2 / 100 + 5) / 10 * 10), HouseRent.rent(h));
        assertTrue(HouseRent.rent(h) > 0);
    }

    @Test
    void settleChargesEachWeekAndCountsMissedPayments() {
        BankAccount bank = new BankAccount(2_500);
        // nowy okres bez oplaty (dom kupiony przed czynszem)
        HouseRent.Result start = HouseRent.settle(0, 0, 1_000, WEEK, 1_000, bank);
        assertEquals(1_000 + WEEK, start.paidUntil());
        assertEquals(2_500, bank.balance());

        // przed terminem nic sie nie dzieje
        HouseRent.Result early = HouseRent.settle(start.paidUntil(), 0, start.paidUntil() - 1, WEEK, 1_000, bank);
        assertEquals(0, early.charged());
        // dwa terminy minely: dwa razy po 1000
        HouseRent.Result two = HouseRent.settle(start.paidUntil(), 0, start.paidUntil() + WEEK, WEEK, 1_000, bank);
        assertEquals(2_000, two.charged());
        assertEquals(500, bank.balance());
        assertEquals(start.paidUntil() + 2 * WEEK, two.paidUntil());
        assertEquals(0, two.missed());

        // brak zlota: jedna zaleglosc, ostrzezenie, dom zostaje
        HouseRent.Result miss = HouseRent.settle(two.paidUntil(), 0, two.paidUntil(), WEEK, 1_000, bank);
        assertEquals(1, miss.missed());
        assertEquals(1, miss.newlyMissed());
        assertFalse(miss.evicted());
        assertEquals(500, bank.balance(), "nothing taken when the rent does not fit");

        // zloto wrocilo: zaleglosc splaca sie przy nastepnym sprawdzeniu, przed terminem
        bank.credit(600);
        HouseRent.Result arrears = HouseRent.settle(miss.paidUntil(), 1, miss.paidUntil() - 10, WEEK, 1_000, bank);
        assertEquals(0, arrears.missed());
        assertEquals(1_000, arrears.charged());
        assertEquals(100, bank.balance());
    }

    @Test
    void twoMissedPaymentsReturnTheHouse() {
        BankAccount bank = new BankAccount(0);
        HouseRent.Result r = HouseRent.settle(10_000, 0, 10_000 + WEEK, WEEK, 1_000, bank);
        assertTrue(r.evicted());
        assertEquals(HouseRent.MAX_MISSED, r.missed());
        HouseRent.Result one = HouseRent.settle(10_000, 0, 10_000, WEEK, 1_000, bank);
        assertFalse(one.evicted());
        assertTrue(HouseRent.settle(one.paidUntil(), one.missed(), one.paidUntil(), WEEK, 1_000, bank).evicted());
    }

    @Test
    void periodCanBeShortenedForTests() {
        String old = System.getProperty("pixel.rentPeriodMs");
        try {
            System.setProperty("pixel.rentPeriodMs", "60000");
            assertEquals(60_000, HouseRent.period());
            System.setProperty("pixel.rentPeriodMs", "zle");
            assertEquals(WEEK, HouseRent.period());
        } finally {
            if (old == null) System.clearProperty("pixel.rentPeriodMs");
            else System.setProperty("pixel.rentPeriodMs", old);
        }
    }

    @Test
    void offlineRentIsChargedWarnedAndFinallyTheHouseGoesBackWithItemsInTheDepot() throws Exception {
        House h = houses.inTown("Frostholm").get(1);
        long rent = HouseRent.rent(h);
        long[] clock = {1_000_000};
        PlayerCharacter pc = named("Czynszowy", h.price() + rent);
        pc.setPosition(nextToDoor(h, false));
        GameSession s = session(pc, null, clock);   // gra offline: wlasciciel i czynsz w zapisie postaci

        assertTrue(s.buyHouseAtDoor(h.id(), 0));
        assertEquals(h.id(), pc.house());
        assertEquals(rent, pc.bank().balance());
        assertEquals(clock[0] + WEEK, pc.rentPaidUntil(), "first week is included in the price");

        // tydzien pozniej: czynsz z konta
        clock[0] += WEEK;
        s.checkHouseRent(1);
        assertEquals(0, pc.bank().balance());
        assertTrue(logged(s, "Pobrano czynsz"));
        assertEquals(1_000_000 + 2 * WEEK, pc.rentPaidUntil());

        // stan czynszu przetrwa zapis
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertEquals(pc.rentPaidUntil(), back.rentPaidUntil());

        // przedmiot na podlodze domu
        Position inside = nextToDoor(h, true);
        int potion = TestData.ITEMS.byKey("health_potion").id();
        s.ground().place(inside, new ItemStack(potion, 3));

        // kolejny tydzien bez zlota: ostrzezenie, dom zostaje
        clock[0] += WEEK;
        s.checkHouseRent(2);
        assertEquals(1, pc.rentMissed());
        assertEquals(h.id(), pc.house());
        assertTrue(logged(s, "Brak złota na czynsz"));

        // druga zaleglosc: dom wraca do miasta, rzeczy do depozytu
        clock[0] += WEEK;
        s.checkHouseRent(3);
        assertNull(pc.house());
        assertTrue(logged(s, "dom wrócił do miasta"));
        assertEquals(3, pc.depot().count(potion));
        assertTrue(s.ground().at(inside).isEmpty());
        assertFalse(s.mayEnter(inside));
    }

    @Test
    void sharedOwnersKeepRentStateAndTheOnlineOwnerPaysInHisSession() {
        House h = houses.inTown("Qasirah").get(1);
        long rent = HouseRent.rent(h);
        long[] clock = {5_000_000};
        HouseOwnersMap owners = new HouseOwnersMap();
        PlayerCharacter pc = named("Rashid", h.price() + 3 * rent);
        pc.setPosition(nextToDoor(h, false));
        GameSession s = session(pc, owners, clock);
        assertTrue(s.buyHouseAtDoor(h.id(), 0));
        assertEquals(clock[0] + WEEK, owners.rentPaidUntil(h.id()));
        clock[0] += 2 * WEEK;
        s.checkHouseRent(1);
        assertEquals(rent, pc.bank().balance());
        HouseOwnersMap back = HouseOwnersMap.decode(owners.encode());
        assertEquals(5_000_000 + 3 * WEEK, back.rentPaidUntil(h.id()));
        assertEquals(0, back.rentMissed(h.id()));
    }

    // ------------------------------------------------------------------ drzwi i okno domu

    @Test
    void tappingTheDoorShowsTheWindowToOthersAndOwnerOptionsInside() {
        House h = houses.inTown("Selvaran").get(1);
        long[] clock = {0};
        HouseOwnersMap owners = new HouseOwnersMap();
        PlayerCharacter stranger = named("Przechodzien", 0);
        stranger.setPosition(nextToDoor(h, false));
        GameSession s = session(stranger, owners, clock);
        assertEquals(GameSession.DoorTap.INFO, s.houseDoorTap(h.door()));
        HouseView free = s.houseView(h);
        assertTrue(free.free());
        assertFalse(s.buyHouseAtDoor(h.id(), 0), "no gold");
        assertTrue(logged(s, "Dom kosztuje"));

        owners.claim(h.id(), "Wlasciciel");
        assertEquals("Wlasciciel", s.houseView(h).owner());
        assertTrue(s.houseView(h).guests().isEmpty(), "strangers do not see the guest list");

        PlayerCharacter me = named("Wlasciciel", 0);
        me.setHouse(h.id());
        me.setPosition(nextToDoor(h, false));
        GameSession owner = session(me, owners, clock);
        assertEquals(GameSession.DoorTap.TOGGLE, owner.houseDoorTap(h.door()), "outside the owner just opens the door");
        me.setPosition(nextToDoor(h, true));
        assertEquals(GameSession.DoorTap.OWNER_WINDOW, owner.houseDoorTap(h.door()));
        assertTrue(owner.addHouseGuest("Ola", HouseAccess.ENTRY, 0));
        assertEquals(Map.of("Ola", HouseAccess.ENTRY), owner.houseView(h).guests());
        assertTrue(owner.addHouseGuest("Ola", HouseAccess.FULL, 0), "level can be raised");
        assertFalse(owner.addHouseGuest("ola", HouseAccess.FULL, 0), "same level again");

        // wiadomosc serwera: ten sam stan po drugiej stronie
        HouseView v = owner.houseView(h);
        assertEquals(v, HouseView.parse(v.toMessage()));

        // opuszczenie domu przy drzwiach: polowa ceny na konto
        assertTrue(owner.leaveHouseAtDoor(0));
        assertNull(me.house());
        assertNull(owners.ownerOf(h.id()));
        assertEquals(h.price() * GameSession.HOUSE_SELLBACK_PERCENT / 100, me.bank().balance());
        assertNotEquals(h.id(), houses.at(me.position()) == null ? null : houses.at(me.position()).id());
    }

    // ------------------------------------------------------------------ poziomy dostepu

    @Test
    void entryOnlyGuestsWalkInButMayNotHandleAnything() {
        House h = houses.inTown("Varden").get(2);
        long[] clock = {0};
        HouseOwnersMap owners = new HouseOwnersMap();
        owners.claim(h.id(), "Gospodarz");
        owners.addGuest(h.id(), "Wejscie", HouseAccess.ENTRY);
        owners.addGuest(h.id(), "Pelny", HouseAccess.FULL);
        Position inside = nextToDoor(h, true);
        world.openings().set(h.door(), false);

        PlayerCharacter entry = named("Wejscie", 0);
        entry.setPosition(inside);
        GameSession e = session(entry, owners, clock);
        assertEquals(inside, entry.position(), "entry guest may stay inside");
        assertTrue(e.mayEnter(inside));
        assertFalse(e.mayHandle(inside));
        assertEquals(GameSession.DoorTap.INFO, e.houseDoorTap(h.door()));
        // drzwi: brak
        assertTrue(e.useAt(h.door(), 0));
        assertFalse(world.openings().isOpen(h.door()));
        // wyrzucanie: brak
        int potion = TestData.ITEMS.byKey("health_potion").id();
        entry.backpack().add(new ItemStack(potion, 2));
        int slot = -1;
        for (int i = 0; i < entry.backpack().size(); i++) {
            if (entry.backpack().get(i) != null && entry.backpack().get(i).itemId() == potion) slot = i;
        }
        e.dropFromSlot(slot, 0);
        assertTrue(e.ground().at(inside).isEmpty());
        assertTrue(logged(e, "tylko wstęp"));
        // podnoszenie: brak
        e.ground().place(inside, new ItemStack(potion, 1));
        int before = entry.backpack().count(potion);
        e.pickUpHere(0);
        assertEquals(before, entry.backpack().count(potion));

        PlayerCharacter full = named("Pelny", 0);
        full.setPosition(inside);
        GameSession f = session(full, owners, clock);
        assertTrue(f.mayHandle(inside));
        assertEquals(GameSession.DoorTap.TOGGLE, f.houseDoorTap(h.door()));
        assertTrue(f.useAt(h.door(), 0));
        assertTrue(world.openings().isOpen(h.door()));
        world.openings().set(h.door(), false);
        f.ground().place(inside, new ItemStack(potion, 1));     // gra offline: kazda sesja ma swoja podloge
        int had = full.backpack().count(potion);
        f.pickUpHere(0);
        assertEquals(had + 1, full.backpack().count(potion));

        // obcy nie otworzy drzwi domu
        PlayerCharacter stranger = named("Obcy", 0);
        stranger.setPosition(nextToDoor(h, false));
        GameSession st = session(stranger, owners, clock);
        assertTrue(st.useAt(h.door(), 0));
        assertFalse(world.openings().isOpen(h.door()));
        assertFalse(st.mayEnter(inside));
    }

    @Test
    void entryOnlyGuestCannotOpenCratesInTheHouse() {
        House home = null;
        Position crate = null, stand = null;
        for (House h : houses.all()) {
            for (int y = h.y0(); y <= h.y1() && crate == null; y++) {
                for (int x = h.x0(); x <= h.x1() && crate == null; x++) {
                    Position p = new Position(x, y, h.z());
                    if (!world.crates().isStorage(p)) continue;
                    for (int[] d : new int[][] {{0, 1}, {0, -1}, {1, 0}, {-1, 0}}) {
                        Position q = p.translate(d[0], d[1]);
                        if (h.contains(q) && world.isWalkable(q)) {
                            crate = p;
                            stand = q;
                            home = h;
                            break;
                        }
                    }
                }
            }
            if (crate != null) break;
        }
        assertNotNull(crate, "houses are furnished with crates");
        HouseOwnersMap owners = new HouseOwnersMap();
        owners.claim(home.id(), "Gospodarz");
        owners.addGuest(home.id(), "Wejscie", HouseAccess.ENTRY);
        owners.addGuest(home.id(), "Pelny", HouseAccess.FULL);
        long[] clock = {0};
        PlayerCharacter a = named("Wejscie", 0);
        a.setPosition(stand);
        GameSession e = session(a, owners, clock);
        assertTrue(e.useCrate(crate, 0));
        assertNull(e.openCrate());
        assertTrue(logged(e, "tylko wstęp"));
        PlayerCharacter b = named("Pelny", 0);
        b.setPosition(stand);
        GameSession f = session(b, owners, clock);
        assertTrue(f.useCrate(crate, 0));
        assertEquals(crate, f.openCrate());
        f.useCrate(crate, 0);                                    // zamyka
    }

    // ------------------------------------------------------------------ stare zapisy

    @Test
    void oldGuestListsLoadAsFullAccessAndNewLevelsRoundTrip() {
        HouseOwnersMap old = HouseOwnersMap.decode("varden_1=Kasia\nguests:varden_1=Ola,Stary Wilk\n");
        assertEquals("Kasia", old.ownerOf("varden_1"));
        assertEquals(List.of("Ola", "Stary Wilk"), old.guests("varden_1"));
        assertEquals(HouseAccess.FULL, old.guestAccess("varden_1", "ola"));
        assertEquals(HouseAccess.FULL, old.guestAccess("varden_1", "Stary Wilk"));
        assertEquals(0, old.rentPaidUntil("varden_1"), "rent starts on the next check");
        assertEquals(HouseAccess.OWNER, old.access("varden_1", "Kasia"));
        assertEquals(HouseAccess.NONE, old.access("varden_1", "Obcy"));

        assertTrue(old.addGuest("varden_1", "Ula", HouseAccess.ENTRY));
        old.setRent("varden_1", 123_456, 1);
        String text = old.encode();
        assertTrue(text.contains("guests:varden_1=Ola:full,Stary Wilk:full,Ula:entry"), text);
        assertTrue(text.contains("rent:varden_1=123456,1"), text);
        assertTrue(text.contains("varden_1=Kasia"), text);
        HouseOwnersMap back = HouseOwnersMap.decode(text);
        assertEquals(HouseAccess.ENTRY, back.guestAccess("varden_1", "ula"));
        assertEquals(123_456, back.rentPaidUntil("varden_1"));
        assertEquals(1, back.rentMissed("varden_1"));
        assertTrue(back.mayEnter("varden_1", "Ula"));
    }

    @Test
    void oldCharacterSaveWithoutRentLoadsAndStartsTheRentPeriod() throws Exception {
        House h = houses.inTown("Frostholm").get(3);
        PlayerCharacter pc = named("Stary", 1_000);
        pc.setHouse(h.id());
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        String text = codec.encode(pc);
        assertFalse(text.contains("rent="), "no rent key before the first period");
        PlayerCharacter back = codec.decode(text);
        assertEquals(h.id(), back.house());
        assertEquals(0, back.rentPaidUntil());
        long[] clock = {77_000};
        back.setPosition(nextToDoor(h, false));
        GameSession s = session(back, null, clock);
        s.checkHouseRent(0);
        assertEquals(77_000 + WEEK, back.rentPaidUntil(), "first period starts now, free");
        assertEquals(1_000, back.bank().balance());
        assertEquals(h.id(), back.house());

        // offline goscie z poziomem w zapisie postaci; stary wpis bez poziomu = pelny dostep
        back.setHouseGuests("Ola,Ula:entry");
        assertEquals(HouseAccess.FULL, s.houseView(h).guests().get("Ola"));
        assertEquals(HouseAccess.ENTRY, s.houseView(h).guests().get("Ula"));
        assertEquals("Ola,Ula:entry", codec.decode(codec.encode(back)).houseGuests());
    }

    @Test
    void entryChatCommand() {
        assertArrayEquals(new String[] {"HOUSE", "GUEST", "Ola", "entry"}, pl.fawizja.pixel.net.ChatCommands.toMessage("/dom wstęp Ola"));
    }
}
