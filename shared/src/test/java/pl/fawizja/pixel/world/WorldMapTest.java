package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.movement.MoveResult;
import pl.fawizja.pixel.movement.MovementSystem;
import pl.fawizja.pixel.vocation.Vocation;

class WorldMapTest {

    private final WorldMap world = TestData.varden();
    private final int rope = TestData.ITEMS.byKey("rope").id();

    @Test
    void mapLoadsAllElevenFloors() {
        assertEquals(64, world.width());
        assertEquals(48, world.height());
        for (int z = -5; z <= 5; z++) {
            assertNotNull(world.floor(z), "floor " + z);
            assertTrue(world.floor(z).hasGround(), "floor " + z + " has tiles");
        }
        assertEquals(new Position(31, 16, 0), world.spawn());
    }

    @Test
    void wallsWaterAndObjectsBlock() {
        TileRegistry t = world.tiles();
        FloorMap f = world.floor(0);
        assertEquals(t.byKey("stone_wall").id(), f.get(Layer.WALL, 18, 15));
        assertFalse(f.isWalkable(18, 15), "town wall");
        assertFalse(f.isWalkable(53, 5), "river");
        assertTrue(f.isWalkable(53, 22), "bridge");
        assertFalse(f.isWalkable(31, 22), "fountain");
        assertFalse(f.isWalkable(-1, 0));
        assertFalse(f.isWalkable(64, 0));
        assertTrue(f.isWalkable(31, 16));
    }

    @Test
    void lightSourcesExistInTownAndUnderground() {
        assertFalse(world.floor(0).lightSources().isEmpty());
        assertFalse(world.floor(-1).lightSources().isEmpty());
        assertFalse(world.floor(-5).lightSources().isEmpty());
        assertEquals(6, world.floor(0).lightAt(29, 20), "street lamp radius");
    }

    @Test
    void movementRespectsCollisionsAndCorners() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        MovementSystem ms = new MovementSystem(world, rope);
        pc.setPosition(new Position(28, 14, 0)); // w swiatyni przy scianie
        assertEquals(MoveResult.BLOCKED, ms.step(pc, Direction.WEST));
        assertEquals(new Position(28, 14, 0), pc.position());
        assertEquals(MoveResult.MOVED, ms.step(pc, Direction.EAST));
        assertEquals(new Position(29, 14, 0), pc.position());
        // po skosie nie wolno scinac rogu sciany
        pc.setPosition(new Position(28, 18, 0));
        assertFalse(ms.canStep(pc.position(), Direction.SOUTH_WEST));
        pc.damage(Integer.MAX_VALUE);
        assertEquals(MoveResult.DEAD, ms.step(pc, Direction.EAST));
    }

    @Test
    void stairsChangeFloorAndKeepMapBinding() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        MovementSystem ms = new MovementSystem(world, rope);
        pc.setPosition(new Position(40, 30, 0));
        assertEquals(MoveResult.CHANGED_FLOOR, ms.step(pc, Direction.EAST));
        assertEquals(new Position(40, 30, 1), pc.position());
        assertTrue(world.isWalkable(pc.position()));
    }

    @Test
    void ladderNeedsUseAndRopeSpotNeedsRope() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        MovementSystem ms = new MovementSystem(world, rope);
        pc.setPosition(new Position(5, 30, -3));
        assertEquals(MoveResult.CHANGED_FLOOR, ms.use(pc));
        assertEquals(new Position(4, 29, -2), pc.position());
        pc.setPosition(new Position(31, 16, 0));
        assertEquals(MoveResult.NOTHING_TO_USE, ms.use(pc));
    }

    /** Przechodzi caly swiat logika ruchu (krok + uzycie) i sprawdza dostep do +5 i -5. */
    @Test
    void everyFloorFromPlus5ToMinus5IsReachableFromSpawn() {
        Map<Position, Position> parent = explore(world.spawn());
        for (int z = -5; z <= 5; z++) {
            final int fz = z;
            assertTrue(parent.keySet().stream().anyMatch(p -> p.z() == fz), "floor " + z + " reachable");
        }
        // i da sie wrocic: z najglebszego pietra do swiatyni
        Position deepest = parent.keySet().stream().filter(p -> p.z() == -5).findFirst().orElseThrow();
        assertTrue(explore(deepest).containsKey(world.spawn()), "way back from -5");
        Position top = parent.keySet().stream().filter(p -> p.z() == 5).findFirst().orElseThrow();
        assertTrue(explore(top).containsKey(world.spawn()), "way back from +5");
    }

    @Test
    void allTransitionsLandOnWalkableTiles() {
        int count = 0;
        for (FloorMap f : world.floors().values()) {
            for (Transition t : f.transitions().values()) {
                assertTrue(world.isWalkable(t.to()), t.toString());
                assertTrue(Math.abs(t.to().z() - t.from().z()) == 1, "transitions connect adjacent floors " + t);
                count++;
            }
        }
        assertEquals(20, count);
    }

    private Map<Position, Position> explore(Position start) {
        MovementSystem ms = new MovementSystem(world, rope);
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        Map<Position, Position> parent = new HashMap<>();
        ArrayDeque<Position> q = new ArrayDeque<>();
        parent.put(start, start);
        q.add(start);
        while (!q.isEmpty()) {
            Position p = q.poll();
            for (Direction d : Direction.values()) {
                pc.setPosition(p);
                if (ms.step(pc, d).moved() && parent.putIfAbsent(pc.position(), p) == null) q.add(pc.position());
            }
            pc.setPosition(p);
            if (ms.use(pc) == MoveResult.CHANGED_FLOOR && parent.putIfAbsent(pc.position(), p) == null) q.add(pc.position());
        }
        return parent;
    }
}
