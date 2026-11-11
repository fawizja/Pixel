package pl.fawizja.pixel.monster;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.net.RemoteEntity;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Position;

/** Zwrot potworow w 4 kierunkach (N, E, S, W): z ruchu, z ataku i z klatek serwera (klient online). */
class MonsterFacingTest {
    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);

    @Test
    void cardinalKeepsStraightAndMapsDiagonalsToEastOrWest() {
        assertEquals(Direction.NORTH, Direction.NORTH.cardinal());
        assertEquals(Direction.SOUTH, Direction.SOUTH.cardinal());
        assertEquals(Direction.EAST, Direction.NORTH_EAST.cardinal());
        assertEquals(Direction.EAST, Direction.SOUTH_EAST.cardinal());
        assertEquals(Direction.WEST, Direction.SOUTH_WEST.cardinal());
        assertEquals(Direction.WEST, Direction.NORTH_WEST.cardinal());
    }

    @Test
    void towardPicksTheDominantAxis() {
        assertEquals(Direction.EAST, Direction.toward(0, 0, 3, 1, null));
        assertEquals(Direction.WEST, Direction.toward(5, 5, 1, 4, null));
        assertEquals(Direction.NORTH, Direction.toward(0, 5, 1, 0, null));
        assertEquals(Direction.SOUTH, Direction.toward(0, 0, 0, 2, null));
        assertEquals(Direction.EAST, Direction.toward(0, 0, 2, 2, null), "tie goes horizontal");
        assertEquals(Direction.NORTH, Direction.toward(4, 4, 4, 4, Direction.NORTH), "same tile keeps the fallback");
    }

    @Test
    void lookAtTurnsWithoutMovingAndIgnoresOtherFloors() {
        Position p = new Position(30, 30, 0);
        Monster m = new Monster(1, REG.get("boar"), new SpawnPoint("boar", p, 0, 1, 60), p);
        assertEquals(Direction.SOUTH, m.facing4(), "default faces the viewer");
        m.lookAt(p.translate(-2, 0));
        assertEquals(Direction.WEST, m.facing4());
        m.lookAt(p.translate(0, -1));
        assertEquals(Direction.NORTH, m.facing4());
        m.lookAt(new Position(31, 30, 1));
        assertEquals(Direction.NORTH, m.facing4(), "other floor is ignored");
        assertEquals(p, m.position());
    }

    @Test
    void remoteMonsterFacesItsWalkDirectionFromPositionDeltas() {
        MonsterManager mm = new MonsterManager(TestData.varden(), REG, List.of(), new java.util.Random(1));
        Position a = new Position(42, 6, 0);
        mm.syncRemote(List.of(new RemoteEntity("MO", 7, "boar", "boar", a, 100)), 0, 300);
        Monster boar = mm.monsters().get(0);
        mm.syncRemote(List.of(new RemoteEntity("MO", 7, "boar", "boar", a.translate(1, 0), 100)), 1000, 300);
        assertEquals(Direction.EAST, boar.facing4());
        mm.syncRemote(List.of(new RemoteEntity("MO", 7, "boar", "boar", a.translate(1, -1), 100)), 2000, 300);
        assertEquals(Direction.NORTH, boar.facing4());
        mm.syncRemote(List.of(new RemoteEntity("MO", 7, "boar", "boar", a.translate(0, -1), 100)), 3000, 300);
        assertEquals(Direction.WEST, boar.facing4());
        mm.syncRemote(List.of(new RemoteEntity("MO", 7, "boar", "boar", a.translate(0, 0), 100)), 4000, 300);
        assertEquals(Direction.SOUTH, boar.facing4());
    }

    @Test
    void faceAdjacentTurnsStandingMonstersTowardThePlayerButNotWalkingOnes() {
        MonsterManager mm = new MonsterManager(TestData.varden(), REG, List.of(), new java.util.Random(1));
        Position a = new Position(42, 6, 0);
        mm.syncRemote(List.of(new RemoteEntity("MO", 7, "boar", "boar", a, 100),
                new RemoteEntity("MO", 8, "boar", "boar", a.translate(5, 0), 100)), 0, 300);
        mm.faceAdjacent(a.translate(-1, 0), 10_000);
        assertEquals(Direction.WEST, mm.monsters().get(0).facing4());
        assertEquals(Direction.SOUTH, mm.monsters().get(1).facing4(), "far monster is not touched");
        mm.syncRemote(List.of(new RemoteEntity("MO", 7, "boar", "boar", a.translate(0, 1), 100)), 20_000, 300);
        Direction walking = mm.monsters().get(0).facing4();
        mm.faceAdjacent(a.translate(-1, 1), 20_100);
        assertEquals(walking, mm.monsters().get(0).facing4(), "mid-step keeps the walk direction");
    }

    /** Atak w zwarciu: w chwili ciosu potwor patrzy na gracza (w momencie ataku stoi, wiec kierunek jest widoczny). */
    @Test
    void attackingMonsterFacesItsTarget() {
        Position at = new Position(42, 6, 0);
        MonsterManager mm = new MonsterManager(TestData.varden(), REG, List.of(new SpawnPoint("boar", at, 0, 1, 60)),
                new java.util.Random(11));
        mm.populate(null);
        Monster boar = mm.monsters().get(0);
        final Direction[] atHit = new Direction[1];
        MonsterAiTest.Dummy d = new MonsterAiTest.Dummy(new Position(45, 6, 0)) {
            @Override public void monsterAttacks(Monster m, long now) { atHit[0] = m.facing4(); super.monsterAttacks(m, now); }
        };
        for (long t = 0; t < 20_000 && atHit[0] == null; t += 50) mm.update(t, d, d);
        assertNotNull(atHit[0], "boar attacked");
        assertEquals(Direction.toward(boar.position().x(), boar.position().y(), d.pos.x(), d.pos.y(), null), atHit[0]);
        assertEquals(Direction.EAST, atHit[0]);
    }
}
