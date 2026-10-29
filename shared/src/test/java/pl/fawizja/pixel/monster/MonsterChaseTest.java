package pl.fawizja.pixel.monster;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.world.FloorMap;
import pl.fawizja.pixel.world.Layer;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Poscig jak w Tibii (2026-10-29): potwor rusza na gracza, ktorego widzi w zasiegu wzroku (takze zza drzew),
 * obchodzi przeszkody szukajac drogi, przeciska sie po skosie miedzy drzewami, a strzelcy trzymaja dystans.
 */
class MonsterChaseTest {

    static final MonsterRegistry REG = MonsterAiTest.REG;

    /** Pole 24x24 z trawy; '#' = drzewo, 'W' = mur. */
    static WorldMap field(String... rows) {
        WorldMap w = new WorldMap("t", 24, 24, TestData.TILES);
        FloorMap f = w.getOrCreateFloor(0);
        int grass = TestData.TILES.byKey("grass").id();
        int tree = TestData.TILES.byKey("tree").id(), wall = TestData.TILES.byKey("stone_wall").id();
        for (int y = 0; y < 24; y++) for (int x = 0; x < 24; x++) f.set(Layer.GROUND, x, y, grass);
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                char c = rows[y].charAt(x);
                if (c == '#') f.set(Layer.OBJECT, x, y, tree);
                if (c == 'W') f.set(Layer.WALL, x, y, wall);
            }
        }
        return w;
    }

    static MonsterManager single(WorldMap w, String key, Position at) {
        MonsterManager mm = new MonsterManager(w, REG, List.of(new SpawnPoint(key, at, 0, 1, 60)), new Random(3));
        mm.populate(null);
        return mm;
    }

    /** Symulacja co 50 ms; zapisuje kolejne pola potwora. */
    static List<Position> run(MonsterManager mm, MonsterAiTest.Dummy d, long from, long to) {
        List<Position> trail = new ArrayList<>();
        Monster m = mm.monsters().get(0);
        trail.add(m.position());
        for (long t = from; t < to; t += 50) {
            mm.update(t, d, d);
            if (!m.position().equals(trail.get(trail.size() - 1))) trail.add(m.position());
        }
        return trail;
    }

    @Test
    void monsterSixTilesAwayBehindATreeLineWalksAroundAndReachesThePlayer() {
        // rzad drzew y=8 od x=0 do x=13 - gracz po drugiej stronie, bez linii wzroku
        String[] rows = new String[24];
        for (int y = 0; y < 24; y++) rows[y] = y == 8 ? "#".repeat(14) + ".".repeat(10) : ".".repeat(24);
        WorldMap w = field(rows);
        Position start = new Position(5, 5, 0);
        MonsterManager mm = single(w, "boar", start);
        Monster boar = mm.monsters().get(0);
        MonsterAiTest.Dummy d = new MonsterAiTest.Dummy(new Position(5, 11, 0));
        assertEquals(6, start.distanceTo(d.pos));
        assertFalse(w.hasLineOfSight(start, d.pos), "the tree line hides the player");
        List<Position> trail = run(mm, d, 0, 40_000);
        assertEquals(MonsterState.CHASE, boar.state());
        assertTrue(boar.position().distanceTo(d.pos) <= 1, "reached the player: " + trail);
        assertTrue(trail.stream().anyMatch(p -> p.x() >= 14), "went around the end of the tree line: " + trail);
        assertTrue(d.attacks > 0, "and attacks");
    }

    @Test
    void monsterSqueezesDiagonallyThroughATreeGap() {
        // sciana drzew z jedna szczelina po skosie: (5,5) -> (6,6) miedzy drzewami (6,5) i (5,6)
        String[] rows = new String[24];
        for (int y = 0; y < 24; y++) {
            StringBuilder b = new StringBuilder(".".repeat(24));
            if (y <= 5) b.setCharAt(6, '#');
            if (y >= 6) b.setCharAt(5, '#');
            rows[y] = b.toString();
        }
        WorldMap w = field(rows);
        MonsterManager mm = single(w, "boar", new Position(3, 5, 0));
        Monster boar = mm.monsters().get(0);
        MonsterAiTest.Dummy d = new MonsterAiTest.Dummy(new Position(8, 6, 0));
        List<Position> trail = run(mm, d, 0, 30_000);
        int i = trail.indexOf(new Position(5, 5, 0));
        assertTrue(i >= 0 && i + 1 < trail.size() && trail.get(i + 1).equals(new Position(6, 6, 0)),
                "diagonal step through the gap: " + trail);
        assertTrue(boar.position().distanceTo(d.pos) <= 1, "reached the player: " + trail);
    }

    @Test
    void diagonalStepTakesLongerThanAStraightOne() {
        // drzewa na (6,5) i (5,6): najkrotsza droga do gracza na (7,7) to skos przez szczeline
        WorldMap w = field("", "", "", "", "", "......#", ".....#");
        MonsterManager mm = single(w, "boar", new Position(5, 5, 0));
        Monster boar = mm.monsters().get(0);
        long straight = pl.fawizja.pixel.balance.Balance.stepDurationMs(boar.type().speed(), false);
        MonsterAiTest.Dummy d = new MonsterAiTest.Dummy(new Position(7, 7, 0));
        long t = 0;
        while (boar.position().equals(new Position(5, 5, 0)) && t < 10_000) {
            mm.update(t, d, d);
            t += 10;
        }
        assertEquals(new Position(6, 6, 0), boar.position(), "squeezed diagonally between the trees");
        long stepStart = t - 10;
        while (boar.isMoving(t)) t += 10;
        assertTrue(t - stepStart >= straight * pl.fawizja.pixel.movement.MovementSystem.DIAGONAL_STEP_FACTOR - 10,
                "diagonal step is slow like for players: " + (t - stepStart) + " vs " + straight);
    }

    @Test
    void rangedMonsterKeepsItsAttackDistance() {
        WorldMap w = field();
        MonsterType goblin = REG.get("goblin");
        assertTrue(MonsterManager.keepsDistance(goblin));
        int keep = goblin.range();
        MonsterManager mm = single(w, "goblin", new Position(10, 4, 0));
        Monster g = mm.monsters().get(0);
        MonsterAiTest.Dummy d = new MonsterAiTest.Dummy(new Position(10, 11, 0));
        run(mm, d, 0, 20_000);
        assertEquals(keep, g.position().distanceTo(d.pos), "came closer, but only to its range: " + g.position());
        assertTrue(d.attacks > 0, "shoots from there");
        // gracz podchodzi: goblin sie cofa do swojego dystansu
        d.pos = g.position().translate(0, 2);
        run(mm, d, 20_000, 35_000);
        assertEquals(keep, g.position().distanceTo(d.pos), "backed off to its range: " + g.position() + " vs " + d.pos);
    }

    @Test
    void monsterGivesUpWhenThePlayerIsOutOfSightForAWhile() {
        WorldMap w = field();
        MonsterManager mm = single(w, "boar", new Position(5, 5, 0));
        Monster boar = mm.monsters().get(0);
        MonsterAiTest.Dummy d = new MonsterAiTest.Dummy(new Position(5, 10, 0));
        run(mm, d, 0, 3_000);
        assertEquals(MonsterState.CHASE, boar.state());
        d.pos = new Position(22, 22, 0); // daleko poza zasiegiem wzroku
        run(mm, d, 3_000, 3_000 + MonsterManager.MEMORY_MS + 20_000);
        assertEquals(MonsterState.IDLE, boar.state(), "went home and idles");
        assertEquals(new Position(5, 5, 0), boar.position());
    }

    @Test
    void pathSearchesPerUpdateAreLimited() {
        // 120 potworow za murem, gracz nieosiagalny: kazdy chce szukac drogi, ale budzet na krok jest staly
        String[] rows = new String[24];
        for (int y = 0; y < 24; y++) rows[y] = y == 12 ? "W".repeat(24) : ".".repeat(24);
        WorldMap w = field(rows);
        List<SpawnPoint> spawns = new ArrayList<>();
        for (int y = 2; y < 8; y++) for (int x = 1; x < 23; x++) spawns.add(new SpawnPoint("boar", new Position(x, y, 0), 0, 1, 60));
        MonsterManager mm = new MonsterManager(w, REG, spawns, new Random(5));
        mm.populate(null);
        assertTrue(mm.monsters().size() >= 120);
        MonsterAiTest.Dummy d = new MonsterAiTest.Dummy(new Position(11, 15, 0));
        int updates = 0;
        for (long t = 0; t < 5_000; t += 50, updates++) {
            long before = mm.pathSearches();
            mm.update(t, d, d);
            assertTrue(mm.pathSearches() - before <= MonsterManager.MAX_PATH_SEARCHES_PER_UPDATE);
        }
        assertTrue(mm.pathSearches() < (long) updates * MonsterManager.MAX_PATH_SEARCHES_PER_UPDATE,
                "failed searches back off: " + mm.pathSearches());
    }
}
