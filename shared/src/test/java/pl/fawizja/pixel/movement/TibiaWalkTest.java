package pl.fawizja.pixel.movement;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.FloorMap;
import pl.fawizja.pixel.world.Layer;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Marsz jak w Tibii (2026-10-29, Darek): trasa po dotknieciu mapy idzie N/S/E/W bez zygzaka, skos tylko przy
 * przecisnieciu sie miedzy drzewami; miedzy drzewami po skosie wolno przejsc, przez rog muru - nie.
 */
class TibiaWalkTest {

    /** Pole 20x20 z trawy; mapa '#' = drzewo, 'W' = mur, '~' = woda. */
    static WorldMap field(String... rows) {
        WorldMap w = new WorldMap("t", 20, 20, TestData.TILES);
        FloorMap f = w.getOrCreateFloor(0);
        int grass = TestData.TILES.byKey("grass").id(), water = TestData.TILES.byKey("water").id();
        int tree = TestData.TILES.byKey("tree").id(), wall = TestData.TILES.byKey("stone_wall").id();
        for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) f.set(Layer.GROUND, x, y, grass);
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                char c = rows[y].charAt(x);
                if (c == '#') f.set(Layer.OBJECT, x, y, tree);
                if (c == 'W') f.set(Layer.WALL, x, y, wall);
                if (c == '~') f.set(Layer.GROUND, x, y, water);
            }
        }
        return w;
    }

    private static int diagonals(Position from, List<Position> path) {
        int n = 0;
        Position prev = from;
        for (Position p : path) {
            if (p.x() != prev.x() && p.y() != prev.y()) n++;
            prev = p;
        }
        return n;
    }

    private static int turns(Position from, List<Position> path) {
        int n = 0, ldx = 0, ldy = 0;
        Position prev = from;
        for (Position p : path) {
            int dx = p.x() - prev.x(), dy = p.y() - prev.y();
            if ((ldx != 0 || ldy != 0) && (dx != ldx || dy != ldy)) n++;
            ldx = dx;
            ldy = dy;
            prev = p;
        }
        return n;
    }

    @Test
    void openFieldPathIsStraightOrLShapedWithoutDiagonals() {
        WorldMap w = field();
        Position from = new Position(2, 2, 0);
        List<Position> diag = Pathfinder.path(w, from, new Position(9, 7, 0), 10_000);
        assertEquals(12, diag.size(), "7 + 5 krokow prostych");
        assertEquals(0, diagonals(from, diag));
        assertEquals(1, turns(from, diag), "jeden zakret (ksztalt L), nie zygzak: " + diag);
        List<Position> straight = Pathfinder.path(w, from, new Position(2, 12, 0), 10_000);
        assertEquals(10, straight.size());
        assertEquals(0, turns(from, straight));
        assertEquals(new Position(2, 12, 0), straight.get(straight.size() - 1));
    }

    @Test
    void diagonalGapBetweenTreesIsSqueezedThrough() {
        // sciana drzew z jedna szczelina po skosie: (5,5) -> (6,6) miedzy drzewami (6,5) i (5,6)
        String[] rows = new String[20];
        for (int y = 0; y < 20; y++) {
            StringBuilder b = new StringBuilder(".".repeat(20));
            if (y <= 5) b.setCharAt(6, '#');
            if (y >= 6) b.setCharAt(5, '#');
            rows[y] = b.toString();
        }
        WorldMap w = field(rows);
        Position from = new Position(3, 5, 0), goal = new Position(8, 6, 0);
        List<Position> path = Pathfinder.path(w, from, goal, 10_000);
        assertFalse(path.isEmpty(), "przejscie po skosie miedzy drzewami");
        assertEquals(1, diagonals(from, path), "dokladnie jeden skos - w szczelinie: " + path);
        int i = path.indexOf(new Position(5, 5, 0));
        assertTrue(i >= 0 && path.get(i + 1).equals(new Position(6, 6, 0)), "skos w szczelinie: " + path);
        // ten sam krok recznie (joystick) tez przechodzi
        MovementSystem m = new MovementSystem(w, -1);
        assertTrue(m.canStep(new Position(5, 5, 0), Direction.SOUTH_EAST));
        assertTrue(m.canStep(new Position(6, 6, 0), Direction.NORTH_WEST));
    }

    @Test
    void diagonalStillCannotCutWallOrWaterCorners() {
        WorldMap w = field("......", "...W..", "..W...", "......", "...~..", "..~...");
        MovementSystem m = new MovementSystem(w, -1);
        assertFalse(m.canStep(new Position(2, 1, 0), Direction.SOUTH_EAST), "rog muru");
        assertFalse(m.canStep(new Position(2, 4, 0), Direction.SOUTH_EAST), "rog wody");
        // za murem po skosie nie ma skrotu: trasa obchodzi mur
        List<Position> path = Pathfinder.path(w, new Position(2, 1, 0), new Position(3, 2, 0), 10_000);
        assertFalse(path.isEmpty());
        assertTrue(path.size() > 1);
    }

    @Test
    void diagonalPathUsedOnlyWhenItSavesTimeAroundATreeCorner() {
        // pojedyncze drzewo w rogu: obejscie prostymi krokami (2 kroki) zamiast skosu przez rog drzewa
        WorldMap w = field("......", "..#...", "......");
        Position from = new Position(1, 1, 0);
        List<Position> path = Pathfinder.path(w, from, new Position(2, 2, 0), 10_000);
        assertEquals(2, path.size());
        assertEquals(0, diagonals(from, path));
    }
}
