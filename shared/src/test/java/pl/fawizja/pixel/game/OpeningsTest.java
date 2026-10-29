package pl.fawizja.pixel.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.treasure.TreasureChests;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Openings;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/** Drzwi i okna otwierane dotknieciem, skrzynie skarbow, na ktore nie mozna wejsc. */
class OpeningsTest {

    private static final Position DOOR = new Position(510, 498, 0);

    private static GameSession at(WorldMap world, Position p) {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.setPosition(p);
        return new GameSession(TestData.ITEMS, world, pc);
    }

    @Test
    void doorOpensOnTapAndClosesOnSecondTap() {
        WorldMap world = TestData.world();
        Openings o = world.openings();
        o.set(DOOR, false);
        assertTrue(o.isDoor(DOOR));
        assertTrue(o.isClosedDoor(DOOR), "doors start closed");
        GameSession s = at(world, new Position(511, 498, 0));
        assertTrue(s.useAt(DOOR, 0));
        assertTrue(o.isOpen(DOOR));
        assertTrue(s.useAt(DOOR, 10));
        assertFalse(o.isOpen(DOOR));
        // za daleko: nic sie nie dzieje
        GameSession far = at(world, new Position(512, 498, 0));
        assertFalse(far.useAt(DOOR, 20));
        assertFalse(o.isOpen(DOOR));
    }

    @Test
    void cannotCloseADoorSomeoneStandsIn() {
        WorldMap world = TestData.world();
        Openings o = world.openings();
        o.set(DOOR, true);
        GameSession s = at(world, DOOR);
        assertTrue(s.useAt(DOOR, 0));
        assertTrue(o.isOpen(DOOR), "player stands in the doorway");
        o.set(DOOR, false);
    }

    @Test
    void walkingIntoAClosedDoorDoesNotOpenIt() {
        // jak w Tibii: drzwi otwiera dopiero uzycie (dotkniecie), wejscie w nie tylko zatrzymuje i podpowiada
        WorldMap world = TestData.world();
        Openings o = world.openings();
        o.set(DOOR, false);
        Position front = new Position(511, 498, 0);
        GameSession s = at(world, front);
        for (long t = 0; t < 3000; t += 50) s.update(t, Direction.WEST);
        assertEquals(front, s.character().position(), "a closed door stops the character");
        assertFalse(o.isOpen(DOOR), "walking into a door does not open it");
        long hints = s.log().latest(50).stream().filter(e -> e.text().equals(GameSession.DOOR_HINT)).count();
        assertEquals(1, hints, "one hint, not one per frame");
        assertFalse(new pl.fawizja.pixel.movement.MovementSystem(world, 0).canStep(front, Direction.WEST));
        // dotkniecie otwiera - teraz przejscie jest wolne
        assertTrue(s.useAt(DOOR, 3000));
        assertTrue(o.isOpen(DOOR));
        for (long t = 3000; t < 6000 && !s.character().position().equals(DOOR); t += 50) s.update(t, Direction.WEST);
        assertEquals(DOOR, s.character().position());
        assertTrue(o.isOpen(DOOR), "an open door stays open after walking through");
        o.set(DOOR, false);
    }

    @Test
    void doorStateChangesReachTheListenerOnce() {
        WorldMap world = TestData.world();
        Openings o = world.openings();
        o.set(DOOR, false);
        java.util.List<String> seen = new java.util.ArrayList<>();
        o.setListener(m -> seen.add(String.join(" ", m)));
        try {
            o.set(DOOR, true);
            o.set(DOOR, true);                                   // bez zmiany: bez komunikatu
            o.toggle(DOOR);
            o.apply(DOOR, true);                                 // stan z serwera u klienta: bez komunikatu
        } finally {
            o.setListener(null);
        }
        assertEquals(List.of("DOOR 510 498 0 1", "DOOR 510 498 0 0"), seen);
        assertTrue(o.openPositions().contains(DOOR));
        o.set(DOOR, false);
    }

    @Test
    void tappingALadderClimbsIt() {
        WorldMap world = TestData.world();
        pl.fawizja.pixel.world.Transition ladder = null;
        for (pl.fawizja.pixel.world.FloorMap f : world.floors().values()) {
            for (int y = 0; y < f.height() && ladder == null; y++)
                for (int x = 0; x < f.width() && ladder == null; x++) {
                    pl.fawizja.pixel.world.Transition t = f.transitionAt(x, y);
                    if (t != null && t.type() == pl.fawizja.pixel.world.TransitionType.LADDER) ladder = t;
                }
            if (ladder != null) break;
        }
        assertNotNull(ladder, "the world has ladders");
        Position spot = null;
        for (int dy = -1; dy <= 1 && spot == null; dy++)
            for (int dx = -1; dx <= 1 && spot == null; dx++) {
                Position q = ladder.from().translate(dx, dy);
                if ((dx != 0 || dy != 0) && world.isWalkable(q) && world.transitionAt(q) == null) spot = q;
            }
        assertNotNull(spot);
        GameSession s = at(world, spot);
        assertTrue(s.usableAt(ladder.from()), "a ladder is used by tapping it");
        assertTrue(s.useAt(ladder.from(), 0));
        assertEquals(ladder.to(), s.character().position(), "tapping the ladder climbs it");
    }

    @Test
    void signpostsTellTheWay() {
        WorldMap world = TestData.world();
        int sign = world.tiles().byKey("signpost").id();
        pl.fawizja.pixel.world.FloorMap f = world.floor(0);
        Position post = null, spot = null;
        for (int y = 1; y < f.height() - 1 && spot == null; y++)
            for (int x = 1; x < f.width() - 1 && spot == null; x++) {
                if (f.get(pl.fawizja.pixel.world.Layer.OBJECT, x, y) != sign) continue;
                for (int d = 0; d < 4 && spot == null; d++) {
                    Position q = new Position(x + (d == 0 ? 1 : d == 1 ? -1 : 0), y + (d == 2 ? 1 : d == 3 ? -1 : 0), 0);
                    if (world.isWalkable(q)) { post = new Position(x, y, 0); spot = q; }
                }
            }
        assertNotNull(spot, "the world has signposts");
        GameSession s = at(world, spot);
        assertTrue(s.usableAt(post));
        assertTrue(s.useAt(post, 0));
        assertTrue(s.log().latest(1).get(0).text().startsWith("Na drogowskazie"), s.log().latest(1).get(0).text());
    }

    @Test
    void windowsToggleToo() {
        WorldMap world = TestData.world();
        Openings o = world.openings();
        Position win = null;
        for (int y = 483; y < 523 && win == null; y++)
            for (int x = 478; x < 568 && win == null; x++)
                if (o.isWindow(new Position(x, y, 0))) win = new Position(x, y, 0);
        assertNotNull(win, "Varden has windows");
        o.set(win, false);
        Position next = null;
        for (int dy = -1; dy <= 1 && next == null; dy++)
            for (int dx = -1; dx <= 1 && next == null; dx++) {
                Position q = win.translate(dx, dy);
                if (world.isWalkable(q)) next = q;
            }
        assertNotNull(next);
        GameSession s = at(world, next);
        assertTrue(s.useAt(win, 0));
        assertTrue(o.isOpen(win));
        assertTrue(s.useAt(win, 5));
        assertFalse(o.isOpen(win));
    }

    @Test
    void treasureChestBlocksTheStepAndOpensOnTap() {
        WorldMap world = TestData.world();
        Position me = new Position(513, 503, 0);
        Position box = new Position(514, 503, 0);
        GameSession s = at(world, me);
        s.setChests(new TreasureChests(List.of(new TreasureChests.Chest("t1", box, 100, "health_potion", 1)), List.of()));
        assertTrue(s.chestAt(box));
        for (long t = 0; t < 1000; t += 50) s.update(t, Direction.EAST);
        assertEquals(me, s.character().position(), "cannot walk onto a chest");
        assertTrue(s.useAt(box, 1000));
        assertTrue(s.character().openedChests().contains("t1"));
    }

    @Test
    void buildingCornersCloseWithoutStubs() {
        // dom 512..517 x 507..511 w Varden: rogi NW slupek, NE poziomy (bez pionowego kikuta nad linia muru),
        // SW pionowy, SE naroznik
        WorldMap world = TestData.world();
        Openings o = world.openings();
        pl.fawizja.pixel.world.FloorMap f = world.floor(0);
        assertEquals(Openings.POLE, o.shape(f, 512, 507));
        assertEquals(Openings.H, o.shape(f, 515, 507));
        assertEquals(Openings.H, o.shape(f, 517, 507), "north-east corner");
        assertEquals(Openings.V, o.shape(f, 517, 509));
        assertEquals(Openings.V, o.shape(f, 512, 511), "south-west corner");
        assertEquals(Openings.CORNER, o.shape(f, 517, 511));
    }
}
