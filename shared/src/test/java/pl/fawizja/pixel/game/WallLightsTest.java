package pl.fawizja.pixel.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.FloorMap;
import pl.fawizja.pixel.world.Layer;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WallLights;
import pl.fawizja.pixel.world.WorldMap;

/** Pochodnie i latarnie na murach: wisza na scianach, dotkniecie zapala i gasi, stan zapisuje sie jak skrzynie. */
class WallLightsTest {

    private final WorldMap world = TestData.world();

    @AfterEach
    void cleanUp() {
        world.wallLights().setListener(null);
        world.wallLights().reset();
    }

    /** Pierwsza pochodnia (albo latarnia) na pietrze z. */
    private Position first(int z, String key) {
        FloorMap f = world.floor(z);
        int id = world.tiles().byKey(key).id();
        for (int[] l : f.lightSources()) {
            if (f.get(Layer.OBJECT, l[0], l[1]) == id) return new Position(l[0], l[1], z);
        }
        throw new AssertionError("no " + key + " on floor " + z);
    }

    private GameSession at(Position p, PlayerCharacter pc) {
        pc.setPosition(p);
        return new GameSession(TestData.ITEMS, world, pc);
    }

    @Test
    void noTorchOrLanternStandsOnTheFloor() {
        java.util.Set<Integer> lights = new java.util.HashSet<>();
        for (String k : new String[] {"wall_torch", "wall_lantern", "street_lamp"}) lights.add(world.tiles().byKey(k).id());
        int torches = 0, lanterns = 0;
        int lantern = world.tiles().byKey("wall_lantern").id();
        for (int z = -5; z <= 5; z++) {
            FloorMap f = world.floor(z);
            if (f == null) continue;
            for (int[] l : f.lightSources()) {
                int obj = f.get(Layer.OBJECT, l[0], l[1]);
                if (!lights.contains(obj)) continue;
                String where = l[0] + "," + l[1] + "," + z;
                assertNotEquals(0, f.get(Layer.WALL, l[0], l[1]), "light on a wall " + where);
                assertFalse(f.isWalkable(l[0], l[1]), "nobody stands on a wall light " + where);
                int face = WallLights.face(f, l[0], l[1]);
                assertNotEquals(WallLights.FACE_NONE, face, "visible wall face " + where);
                int fx = l[0] + (face == WallLights.FACE_EAST ? 1 : 0), fy = l[1] + (face == WallLights.FACE_SOUTH ? 1 : 0);
                assertTrue(f.isWalkable(fx, fy), "floor in front of " + where);
                assertTrue(world.wallLights().isWallLight(new Position(l[0], l[1], z)));
                if (obj == lantern) lanterns++; else torches++;
            }
        }
        assertTrue(torches > 300, "torches in dungeons: " + torches);
        assertTrue(lanterns > 50, "lanterns on town walls: " + lanterns);
    }

    @Test
    void tappingAWallTorchTogglesItWithoutPickingItUp() {
        WallLights wl = world.wallLights();
        Position torch = first(-1, "wall_torch");
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        int torchItem = TestData.ITEMS.byKey("torch").id();
        int before = pc.backpack().count(torchItem);
        GameSession s = at(wl.front(torch), pc);
        assertTrue(wl.isLit(torch), "wall torches burn by default");
        assertTrue(s.useAt(torch, 0));
        assertFalse(wl.isLit(torch));
        assertEquals("Gasisz pochodnię.", s.log().latest(1).get(0).text());
        assertEquals(before, pc.backpack().count(torchItem), "the torch stays on the wall");
        assertTrue(wl.isWallLight(torch));
        assertTrue(s.useAt(torch, 10));
        assertTrue(wl.isLit(torch));
        assertEquals("Zapalasz pochodnię.", s.log().latest(1).get(0).text());
        // za daleko: nic sie nie dzieje
        GameSession far = at(new Position(torch.x() + 5, torch.y() + 5, torch.z()), TestData.newCharacter(Vocation.KNIGHT));
        assertFalse(far.useAt(torch, 20));
        assertTrue(wl.isLit(torch));
    }

    @Test
    void offlineStateIsSavedWithTheCharacter() throws Exception {
        WallLights wl = world.wallLights();
        Position lantern = first(0, "wall_lantern");
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        GameSession s = at(wl.front(lantern), pc);
        assertTrue(s.useAt(lantern, 0));
        assertFalse(wl.isLit(lantern));
        assertEquals("Gasisz latarnię.", s.log().latest(1).get(0).text());
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter loaded = codec.decode(codec.encode(pc));
        // inna postac: wszystko znow sie pali
        new GameSession(TestData.ITEMS, world, TestData.newCharacter(Vocation.KNIGHT));
        assertTrue(wl.isLit(lantern));
        // wczytana postac: latarnia zgaszona
        new GameSession(TestData.ITEMS, world, loaded);
        assertFalse(wl.isLit(lantern));
    }

    @Test
    void apiForQuestsAndServerSync() {
        WallLights wl = world.wallLights();
        Position torch = first(-2, "wall_torch");
        List<String[]> sent = new ArrayList<>();
        wl.setListener(sent::add);
        int v = wl.version();
        assertTrue(wl.setLit(torch.x(), torch.y(), torch.z(), false));
        assertFalse(wl.setLit(torch, false), "no change, no message");
        assertFalse(wl.isLit(torch.x(), torch.y(), torch.z()));
        assertTrue(wl.version() > v);
        assertEquals(1, sent.size());
        assertArrayEquals(new String[] {"WLIGHT", "" + torch.x(), "" + torch.y(), "" + torch.z(), "0"}, sent.get(0));
        assertTrue(wl.toggle(torch));
        assertEquals("1", sent.get(1)[4]);

        // stan domyslny ustawiany przez zadanie (np. pochodnie przy oltarzach): zgaszone do czasu zapalenia
        wl.setDefaultLit(torch, false);
        assertFalse(wl.isLit(torch));
        assertEquals("", wl.encode(), "default state is not saved");
        wl.setLit(torch, true);
        assertEquals(torch.x() + "," + torch.y() + "," + torch.z() + "=1", wl.encode());
        wl.setDefaultLit(torch, true);
        assertTrue(wl.isLit(torch));

        // zapis i odtworzenie (serwer: walllights.txt), stan z serwera na kliencie
        wl.setLit(torch, false);
        String saved = wl.encode();
        wl.reset();
        assertTrue(wl.isLit(torch));
        wl.restore(saved + "|bad|1,1,0=0");
        assertFalse(wl.isLit(torch));
        wl.apply(torch, true);
        assertTrue(wl.isLit(torch));
        assertTrue(wl.changes().isEmpty());

        // wyszukiwanie pochodni wokol oltarza
        List<Position> near = wl.lightsIn(torch.z(), torch.x() - 3, torch.y() - 3, torch.x() + 3, torch.y() + 3);
        assertTrue(near.contains(torch));
        for (Position p : near) assertTrue(wl.isWallLight(p));
        // poza pochodnia: nic do zapalenia
        Position floor = wl.front(torch);
        assertFalse(wl.isWallLight(floor));
        assertFalse(wl.setLit(floor, false));
        assertFalse(wl.isLit(floor));
        assertTrue(world.floor(torch.z()).lightAt(torch.x(), torch.y()) > 0, "wall light is a light source");
    }
}
