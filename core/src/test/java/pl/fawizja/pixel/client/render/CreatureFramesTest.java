package pl.fawizja.pixel.client.render;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.client.render.CreatureFrames.Pick;
import pl.fawizja.pixel.world.Direction;

/** Wybor strony i klatki potwora: kierunek -> plik, zastepstwa i klatki chodu. */
class CreatureFramesTest {
    private static final boolean[] FULL = {true, true, true, true};
    private static final boolean[] ONLY_DOWN = {true, false, false, false};
    private static final boolean[] NO_LEFT = {true, true, false, true};
    private static final boolean[] NO_RIGHT = {true, true, true, false};

    @Test
    void fourDirectionsMapToTheirOwnSides() {
        assertEquals(new Pick(CreatureFrames.UP, false), CreatureFrames.pick(FULL, Direction.NORTH));
        assertEquals(new Pick(CreatureFrames.DOWN, false), CreatureFrames.pick(FULL, Direction.SOUTH));
        assertEquals(new Pick(CreatureFrames.LEFT, false), CreatureFrames.pick(FULL, Direction.WEST));
        assertEquals(new Pick(CreatureFrames.RIGHT, false), CreatureFrames.pick(FULL, Direction.EAST));
    }

    @Test
    void diagonalsLookEastOrWestAndNullLooksDown() {
        assertEquals(CreatureFrames.RIGHT, CreatureFrames.dirIndex(Direction.NORTH_EAST));
        assertEquals(CreatureFrames.LEFT, CreatureFrames.dirIndex(Direction.SOUTH_WEST));
        assertEquals(CreatureFrames.DOWN, CreatureFrames.dirIndex(null));
    }

    @Test
    void missingSideFallsBackByMirroringTheOtherOne() {
        assertEquals(new Pick(CreatureFrames.RIGHT, true), CreatureFrames.pick(NO_LEFT, Direction.WEST));
        assertEquals(new Pick(CreatureFrames.LEFT, true), CreatureFrames.pick(NO_RIGHT, Direction.EAST));
    }

    @Test
    void singleFrameMonsterUsesFrontAndMirrorsWest() {
        assertEquals(new Pick(CreatureFrames.DOWN, true), CreatureFrames.pick(ONLY_DOWN, Direction.WEST));
        assertEquals(new Pick(CreatureFrames.DOWN, false), CreatureFrames.pick(ONLY_DOWN, Direction.EAST));
        assertEquals(new Pick(CreatureFrames.DOWN, false), CreatureFrames.pick(ONLY_DOWN, Direction.NORTH));
        assertEquals(new Pick(CreatureFrames.DOWN, false), CreatureFrames.pick(null, Direction.NORTH));
        assertFalse(CreatureFrames.hasOwn(ONLY_DOWN, Direction.WEST));
        assertTrue(CreatureFrames.hasOwn(FULL, Direction.WEST));
    }

    @Test
    void walkFrameIsIdleWhenStandingAndAlternatesLegsWhileMoving() {
        assertEquals(0, TileArt.walkFrame(5f, 5f, 5, 5));
        int first = TileArt.walkFrame(4.8f, 5f, 5, 5);
        int second = TileArt.walkFrame(4.2f, 5f, 5, 5);
        assertTrue(first >= 1 && first <= 2 && second >= 1 && second <= 2);
        assertNotEquals(first, second, "legs change halfway through the tile");
        assertNotEquals(TileArt.walkFrame(4.8f, 5f, 5, 5), TileArt.walkFrame(5.8f, 5f, 6, 5), "and on the next tile");
    }

    private static final boolean[] EIGHT_FULL = {true, true, true, true, true, true, true, true};
    private static final boolean[] ONLY_NE = {true, true, true, true, true, false, false, false};

    @Test
    void diagonalsFallBackToTheHorizontalSideLikeTibia() {
        // bez grafik skosow (tablica 4 albo 8 wpisow z false): NE/SE -> prawo, NW/SW -> lewo
        for (boolean[] has : new boolean[][] {FULL, {true, true, true, true, false, false, false, false}}) {
            assertEquals(new Pick(CreatureFrames.RIGHT, false), CreatureFrames.pick(has, Direction.NORTH_EAST));
            assertEquals(new Pick(CreatureFrames.RIGHT, false), CreatureFrames.pick(has, Direction.SOUTH_EAST));
            assertEquals(new Pick(CreatureFrames.LEFT, false), CreatureFrames.pick(has, Direction.NORTH_WEST));
            assertEquals(new Pick(CreatureFrames.LEFT, false), CreatureFrames.pick(has, Direction.SOUTH_WEST));
        }
        // brak lewej strony: skos w lewo = odbita prawa
        assertEquals(new Pick(CreatureFrames.RIGHT, true), CreatureFrames.pick(NO_LEFT, Direction.SOUTH_WEST));
    }

    @Test
    void optionalEightDirectionArtIsUsedWhenPresent() {
        assertEquals(new Pick(CreatureFrames.NE, false), CreatureFrames.pick(EIGHT_FULL, Direction.NORTH_EAST));
        assertEquals(new Pick(CreatureFrames.NW, false), CreatureFrames.pick(EIGHT_FULL, Direction.NORTH_WEST));
        assertEquals(new Pick(CreatureFrames.SE, false), CreatureFrames.pick(EIGHT_FULL, Direction.SOUTH_EAST));
        assertEquals(new Pick(CreatureFrames.SW, false), CreatureFrames.pick(EIGHT_FULL, Direction.SOUTH_WEST));
        assertEquals(new Pick(CreatureFrames.UP, false), CreatureFrames.pick(EIGHT_FULL, Direction.NORTH), "proste bez zmian");
        // tylko NE ma grafike: reszta skosow zostaje przy zastepstwie poziomym
        assertEquals(new Pick(CreatureFrames.NE, false), CreatureFrames.pick(ONLY_NE, Direction.NORTH_EAST));
        assertEquals(new Pick(CreatureFrames.RIGHT, false), CreatureFrames.pick(ONLY_NE, Direction.SOUTH_EAST));
        assertEquals(new Pick(CreatureFrames.LEFT, false), CreatureFrames.pick(ONLY_NE, Direction.NORTH_WEST));
        assertTrue(CreatureFrames.hasOwn(ONLY_NE, Direction.NORTH_EAST));
        assertEquals(CreatureFrames.SW, CreatureFrames.diagIndex(Direction.SOUTH_WEST));
        assertEquals(-1, CreatureFrames.diagIndex(Direction.EAST));
    }

    @Test
    void fixtureDirectoryWithDiagonalFilesIsDetected(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path monsters = java.nio.file.Files.createDirectories(dir.resolve("monsters"));
        for (String f : new String[] {"wolf", "wolf_ne", "wolf_ne_walk1", "wolf_ne_walk2", "wolf_up", "wolf_up_walk1", "wolf_up_walk2",
                "wolf_sw", "wolf_sw_walk1"}) {
            java.nio.file.Files.createFile(monsters.resolve(f + ".png"));
        }
        boolean[] has = CreatureFrames.sidesPresent("wolf", rel -> java.nio.file.Files.exists(dir.resolve(rel)));
        assertTrue(has[CreatureFrames.UP], "kompletna strona _up");
        assertFalse(has[CreatureFrames.LEFT]);
        assertTrue(has[CreatureFrames.NE], "komplet _ne");
        assertFalse(has[CreatureFrames.SW], "_sw bez _walk2 = niekompletny, nieuzywany");
        assertFalse(has[CreatureFrames.NW] || has[CreatureFrames.SE]);
        assertEquals(new Pick(CreatureFrames.NE, false), CreatureFrames.pick(has, Direction.NORTH_EAST), "art 8-kierunkowa uzyta");
        assertEquals(new Pick(CreatureFrames.DOWN, false), CreatureFrames.pick(has, Direction.SOUTH_EAST), "brak - zastepstwo (przod)");
        assertEquals(new Pick(CreatureFrames.DOWN, true), CreatureFrames.pick(has, Direction.SOUTH_WEST), "niekompletny _sw: lewo -> przod odbity");
    }
}
