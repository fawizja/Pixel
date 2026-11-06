package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.vocation.Vocation;

/** Mgla mapy: odkrywanie widoku, zapis (deflate + base64), migracja starych zapisow i odkrywanie w sesji. */
class ExploredMapTest {

    @Test
    void aFreshMapKnowsNothing() {
        ExploredMap m = new ExploredMap();
        assertTrue(m.isEmpty());
        assertFalse(m.isExplored(500, 500, 0));
        assertEquals("", m.encode());
        assertEquals(0, m.memoryBytes(), "no chunk allocated before the first reveal");
    }

    @Test
    void revealCoversTheGameWindowPlusOneTileAndOnlyThatFloor() {
        ExploredMap m = new ExploredMap();
        assertTrue(m.revealView(new Position(500, 500, 0)));
        int w = 2 * ExploredMap.VIEW_X + 1, h = 2 * ExploredMap.VIEW_Y + 1;
        assertEquals(17, w, "15 tiles of the game window + 1 on each side");
        assertEquals(13, h, "11 tiles of the game window + 1 on each side");
        assertEquals(w * h, m.count(0));
        assertTrue(m.isExplored(500 - 8, 500 - 6, 0));
        assertTrue(m.isExplored(500 + 8, 500 + 6, 0));
        assertFalse(m.isExplored(500 + 9, 500, 0));
        assertFalse(m.isExplored(500, 500 - 7, 0));
        assertEquals(0, m.count(-1), "other floors stay fogged");
        assertFalse(m.revealView(new Position(500, 500, 0)), "standing still reveals nothing new");
        m.revealView(new Position(501, 500, 0));
        assertEquals(w * h + h, m.count(0), "one step east reveals one new column");
    }

    @Test
    void revealAcrossChunkBordersAndAtTheWorldEdge() {
        ExploredMap m = new ExploredMap();
        m.revealView(new Position(64, 64, -3)); // rog czterech kawalkow
        assertEquals(17 * 13, m.count(-3));
        assertEquals(4 * 512, m.memoryBytes());
        m.revealView(new Position(0, 0, 2));
        assertEquals(9 * 7, m.count(2), "clipped at the map border");
        assertFalse(m.reveal(0, 0, 5, 5, 99), "floors outside the range are ignored");
        assertFalse(m.isExplored(-1, 0, 2));
    }

    @Test
    void encodeAndMergeRoundTripWithParts() {
        ExploredMap m = new ExploredMap();
        Random r = new Random(7);
        for (int i = 0; i < 400; i++) {
            m.revealView(new Position(r.nextInt(1000), r.nextInt(1000), r.nextInt(11) - 5));
        }
        ExploredMap back = new ExploredMap();
        back.merge(m.encode());
        for (int z = -5; z <= 5; z++) assertEquals(m.count(z), back.count(z), "floor " + z);
        for (int i = 0; i < 2000; i++) {
            int x = r.nextInt(1000), y = r.nextInt(1000), z = r.nextInt(11) - 5;
            assertEquals(m.isExplored(x, y, z), back.isExplored(x, y, z));
        }
        List<String> parts = m.encodeParts(64);
        assertTrue(parts.size() > 1, "many chunks: several network lines");
        ExploredMap fromParts = new ExploredMap();
        for (String p : parts) {
            assertTrue(p.length() < 48 * 1024, "each part fits a protocol line");
            fromParts.merge(p);
        }
        for (int z = -5; z <= 5; z++) assertEquals(m.count(z), fromParts.count(z));
    }

    @Test
    void aWalkedTrailIsSmallInTheSave() {
        ExploredMap m = new ExploredMap();
        for (int x = 100; x < 900; x++) m.revealView(new Position(x, 500 + (x / 50) % 3, 0));
        assertTrue(m.encode().length() < 4000, "800 tiles of trail: " + m.encode().length() + " chars");
    }

    @Test
    void mergeIsAnOrAndRejectsGarbage() {
        ExploredMap a = new ExploredMap();
        a.revealView(new Position(100, 100, 0));
        ExploredMap b = new ExploredMap();
        b.revealView(new Position(300, 300, 0));
        b.merge(a.encode());
        assertTrue(b.isExplored(100, 100, 0) && b.isExplored(300, 300, 0));
        assertThrows(IllegalArgumentException.class, () -> b.merge("bm9wZQ=="));
    }

    @Test
    void dirtyRectsAreTrackedForTheMinimap() {
        ExploredMap m = new ExploredMap();
        long before = m.dirtyTotal();
        m.revealView(new Position(200, 200, 1));
        assertEquals(before + 1, m.dirtyTotal());
        int[] r = new int[5];
        assertTrue(m.dirty(before, r));
        assertArrayEquals(new int[] {1, 192, 194, 208, 206}, r);
        for (int i = 0; i < 100; i++) m.revealView(new Position(10 + i, 400, 0));
        assertFalse(m.dirty(before, r), "fell out of the ring: the minimap rebuilds");
        m.revealAll();
        assertTrue(m.isExplored(999, 999, -5));
    }

    @Test
    void theSaveKeepsTheExploredMap() throws Exception {
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.explored().revealView(new Position(480, 470, -2));
        pc.explored().revealView(new Position(700, 300, 0));
        String text = codec.encode(pc);
        assertTrue(text.startsWith("PIXELSAVE " + CharacterCodec.CURRENT_VERSION + "\n"));
        assertTrue(text.contains("\nfog="));
        PlayerCharacter back = codec.decode(text);
        assertEquals(pc.explored().count(-2), back.explored().count(-2));
        assertTrue(back.explored().isExplored(700, 300, 0));
        assertFalse(back.explored().takeLegacy(), "a v7 save is not migrated");
        assertFalse(codec.encode(pc, false).contains("fog="), "the server's SELF line carries no map");
    }

    @Test
    void oldSavesStartWithTheirHomeTempleSurroundings() throws Exception {
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        WorldMap world = TestData.world();
        Town home = world.towns().get(0);
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        pc.setHomeTown(home.name().toLowerCase(java.util.Locale.ROOT));
        Position far = new Position(home.temple().x() + 200 < 990 ? home.temple().x() + 200 : home.temple().x() - 200,
                home.temple().y(), home.temple().z());
        pc.setPosition(far);
        String v7 = codec.encode(pc);
        String body = v7.substring(v7.indexOf('\n') + 1, v7.lastIndexOf("crc="));
        CRC32 c = new CRC32();
        c.update(body.getBytes(StandardCharsets.UTF_8));
        String v6 = "PIXELSAVE 6\n" + body + "crc=" + Long.toHexString(c.getValue()) + "\n";
        PlayerCharacter old = codec.decode(v6);
        assertTrue(old.explored().isEmpty());
        GameSession s = new GameSession(TestData.ITEMS, world, old, null, new Random(1), 8);
        Position t = home.temple();
        assertTrue(old.explored().isExplored(t), "temple known");
        assertTrue(old.explored().isExplored(t.x() + GameSession.LEGACY_REVEAL_X, t.y() - GameSession.LEGACY_REVEAL_Y, t.z()));
        assertFalse(old.explored().isExplored(t.x() + GameSession.LEGACY_REVEAL_X + 1, t.y(), t.z()));
        assertTrue(old.explored().isExplored(s.character().position()), "and where it stands");
        PlayerCharacter again = codec.decode(codec.encode(old));
        GameSession s2 = new GameSession(TestData.ITEMS, world, again, null, new Random(1), 8);
        assertEquals(old.explored().count(t.z()), s2.character().explored().count(t.z()), "migrated once");
    }

    @Test
    void walkingInASessionRevealsTheTrail() {
        WorldMap world = TestData.world();
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        Position temple = world.towns().get(0).temple();
        pc.setPosition(temple);
        GameSession s = new GameSession(TestData.ITEMS, world, pc, null, new Random(1), 8);
        int start = pc.explored().count(temple.z());
        assertEquals(17 * 13, start, "a new character knows only what it sees");
        s.teleport(new Position(temple.x() + 60, temple.y(), temple.z()), 0);
        s.update(100, null);
        assertTrue(pc.explored().isExplored(temple.x() + 60 + 8, temple.y(), temple.z()), "teleport target revealed");
        assertFalse(pc.explored().isExplored(temple.x() + 30, temple.y(), temple.z()), "nothing in between");
    }
}
