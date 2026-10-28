package pl.fawizja.pixel.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.house.House;
import pl.fawizja.pixel.house.HouseOwnersMap;
import pl.fawizja.pixel.house.HouseRegistry;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.world.Crates;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Layer;
import pl.fawizja.pixel.world.MapLoader;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldLayout;
import pl.fawizja.pixel.world.WorldMap;

/** Skrzynie, beczki i worki: pchanie, otwieranie, chowanie rzeczy, zapis; lady i gabloty stoja w miejscu. */
class CratesTest {

    private static WorldMap fresh() throws Exception {
        return new MapLoader(TestData.TILES).load(WorldLayout.WORLD_MAP);
    }

    private static int object(WorldMap w, Position p) {
        return w.floor(p.z()).get(Layer.OBJECT, p.x(), p.y());
    }

    private static boolean freeFloor(WorldMap w, HouseRegistry houses, Position p) {
        return w.isWalkable(p) && object(w, p) == 0 && w.floor(p.z()).transitionAt(p.x(), p.y()) == null
                && houses.at(p) == null;
    }

    /** Skrzynia na ulicy, ktora mozna pchnac na wschod co najmniej dwa razy, z wolnym polem na zachod od niej. */
    private static Position streetCrate(WorldMap w, HouseRegistry houses) {
        Crates c = w.crates();
        for (int y = 1; y < w.floor(0).height() - 1; y++) {
            for (int x = 1; x < w.floor(0).width() - 3; x++) {
                Position p = new Position(x, y, 0);
                if (!c.isMovable(p) || houses.at(p) != null) continue;
                if (freeFloor(w, houses, p.translate(-1, 0)) && freeFloor(w, houses, p.translate(1, 0))
                        && freeFloor(w, houses, p.translate(2, 0))) return p;
            }
        }
        throw new AssertionError("no street crate");
    }

    private static GameSession at(WorldMap w, HouseRegistry houses, HouseOwnersMap owners, String name, Position p) {
        PlayerCharacter pc = TestData.newCharacterNamed(name);
        pc.setPosition(p);
        GameSession s = new GameSession(TestData.ITEMS, w, pc, null, new Random(1), 1);
        s.setHouses(houses, owners);
        return s;
    }

    @Test
    void walkingIntoACratePushesItAndThePlayerFollows() throws Exception {
        WorldMap w = fresh();
        HouseRegistry houses = HouseRegistry.load(WorldLayout.WORLD_MAP, w);
        Position crate = streetCrate(w, houses);
        int id = object(w, crate);
        GameSession s = at(w, houses, new HouseOwnersMap(), "Pchacz", crate.translate(-1, 0));
        for (long t = 0; t < 1500 && !s.character().position().equals(crate); t += 50) s.update(t, Direction.EAST);
        assertEquals(crate, s.character().position(), "player steps where the crate stood");
        assertEquals(0, object(w, crate));
        assertEquals(id, object(w, crate.translate(1, 0)), "crate moved one cell east");
        assertFalse(s.character().crates().isEmpty(), "offline move is kept in the save");
        // dalej niz MAX_DRIFT od miejsca z mapy nie pojedzie
        for (long t = 1500; t < 20000; t += 50) s.update(t, Direction.EAST);
        int moved = -1;
        for (int dx = 1; dx <= Crates.MAX_DRIFT + 1; dx++) if (object(w, crate.translate(dx, 0)) == id) moved = dx;
        assertTrue(moved >= 1 && moved <= Crates.MAX_DRIFT, "crate drift " + moved);
    }

    @Test
    void cratesDoNotGoDiagonallyIntoWallsOrOntoOtherObjects() throws Exception {
        WorldMap w = fresh();
        HouseRegistry houses = HouseRegistry.load(WorldLayout.WORLD_MAP, w);
        Position crate = streetCrate(w, houses);
        Crates c = w.crates();
        assertFalse(c.canPush(crate, Direction.NORTH_EAST, null));
        assertTrue(c.canPush(crate, Direction.EAST, null));
        assertFalse(c.canPush(crate, Direction.EAST, q -> true), "someone stands there");
        int id = object(w, crate);
        w.floor(0).set(Layer.OBJECT, crate.x() + 1, crate.y(), id);
        assertFalse(c.canPush(crate, Direction.EAST, null), "another object behind it");
        w.floor(0).set(Layer.OBJECT, crate.x() + 1, crate.y(), 0);
    }

    @Test
    void countersShowcasesAndShelvesNeverMove() {
        for (String fixed : new String[] {"counter", "showcase", "shelf", "depot_locker", "altar", "throne"}) {
            assertFalse(Crates.MOVABLE.contains(fixed), fixed);
        }
    }

    @Test
    void openPutTakeCloseAndTheContentsSurviveASaveAndAMove() throws Exception {
        WorldMap w = fresh();
        HouseRegistry houses = HouseRegistry.load(WorldLayout.WORLD_MAP, w);
        Position crate = streetCrate(w, houses);
        GameSession s = at(w, houses, new HouseOwnersMap(), "Chomik", crate.translate(-1, 0));
        int potion = TestData.ITEMS.byKey("health_potion").id();
        s.character().backpack().add(new ItemStack(potion, 3));
        int n = s.character().backpack().count(potion);
        assertTrue(s.useAt(crate, 0));
        assertEquals(crate, s.openCrate());
        assertEquals(n, s.cratePut(potion, 10));
        assertEquals(0, s.character().backpack().count(potion));
        assertEquals(1, w.crates().peek(crate).size());
        assertEquals(n, s.crateTake(potion, 20));
        assertEquals(n, s.character().backpack().count(potion));
        assertEquals(n, s.cratePut(potion, 30));
        assertTrue(s.useAt(crate, 40), "second tap closes");
        assertNull(s.openCrate());
        assertEquals(0, s.cratePut(potion, 50), "closed crate takes nothing");
        // przepchniecie - zawartosc jedzie razem ze skrzynia
        for (long t = 100; t < 1600 && !s.character().position().equals(crate); t += 50) s.update(t, Direction.EAST);
        Position moved = crate.translate(1, 0);
        assertEquals(n, w.crates().peek(moved).get(0).count());

        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter loaded = codec.decode(codec.encode(s.character()));
        WorldMap w2 = fresh();
        GameSession s2 = new GameSession(TestData.ITEMS, w2, loaded, null, new Random(2), 2);
        assertEquals(0, object(w2, crate), "crate is not back at its map spot");
        assertTrue(w2.crates().isStorage(moved));
        assertEquals(n, w2.crates().peek(moved).get(0).count());
        assertNotNull(s2);
    }

    @Test
    void cratesInSomeoneElsesHouseStayClosed() throws Exception {
        WorldMap w = fresh();
        HouseRegistry houses = HouseRegistry.load(WorldLayout.WORLD_MAP, w);
        Position crate = null;
        Position stand = null;
        House home = null;
        for (House h : houses.all()) {
            for (int y = h.y0(); y <= h.y1() && crate == null; y++) {
                for (int x = h.x0(); x <= h.x1() && crate == null; x++) {
                    Position p = new Position(x, y, h.z());
                    if (!w.crates().isStorage(p)) continue;
                    for (Direction d : Direction.values()) {
                        Position q = p.translate(d.dx, d.dy);
                        if (!d.diagonal() && h.contains(q) && w.isWalkable(q) && object(w, q) == 0) {
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
        GameSession guest = at(w, houses, owners, "Obcy", stand);
        guest.character().setPosition(stand);   // np. stoi w oknie albo wszedl przed zmiana wlasciciela
        assertTrue(guest.useAt(crate, 0));
        assertNull(guest.openCrate(), "foreign house crate does not open");
        GameSession owner = at(w, houses, owners, "Gospodarz", stand);
        assertTrue(owner.useAt(crate, 0));
        assertEquals(crate, owner.openCrate());
    }

    @Test
    void serverStateTextRoundTrips() throws Exception {
        WorldMap w = fresh();
        HouseRegistry houses = HouseRegistry.load(WorldLayout.WORLD_MAP, w);
        Position crate = streetCrate(w, houses);
        Crates c = w.crates();
        java.util.List<String[]> sent = new java.util.ArrayList<>();
        c.setListener(sent::add);
        c.push(crate, Direction.EAST);
        assertEquals("CRATEMV", sent.get(0)[0]);
        Position moved = crate.translate(1, 0);
        c.open(moved, TestData.ITEMS).add(new ItemStack(TestData.ITEMS.byKey("health_potion").id(), 2));
        c.changed(moved);
        assertEquals("CRATE", sent.get(1)[0]);

        // klient online: swieza mapa dostaje te same komunikaty
        WorldMap client = fresh();
        client.crates().applyMove(crate, moved);
        String[] m = sent.get(1);
        client.crates().applyContents(moved, m[4], TestData.ITEMS);
        assertEquals(c.encode(), client.crates().encode());
    }
}
