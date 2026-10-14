package pl.fawizja.pixel.monster;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.world.Position;

class MonsterAiTest {

    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);

    /** Gracz-atrapa i licznik atakow. */
    static final class Dummy implements MonsterManager.Target, MonsterManager.AttackHandler {
        Position pos;
        int attacks;
        int heals;
        boolean alive = true;

        Dummy(Position p) { pos = p; }
        public Position position() { return pos; }
        public boolean attackable() { return alive; }
        public void monsterAttacks(Monster m, long now) { attacks++; }
        public void monsterHeals(Monster h, Monster t, int amount, long now) { heals++; }
    }

    private MonsterManager single(String key, Position at, int radius) {
        MonsterManager mm = new MonsterManager(TestData.varden(), REG, List.of(new SpawnPoint(key, at, radius, 1, 60)), new Random(11));
        mm.populate(null);
        return mm;
    }

    private static void run(MonsterManager mm, Dummy d, long from, long to) {
        for (long t = from; t < to; t += 50) mm.update(t, d, d);
    }

    @Test
    void meleeMonsterChasesAndAttacks() {
        MonsterManager mm = single("boar", new Position(42, 6, 0), 0);
        Monster boar = mm.monsters().get(0);
        Dummy d = new Dummy(new Position(45, 6, 0));
        run(mm, d, 0, 20_000);
        assertEquals(MonsterState.CHASE, boar.state());
        assertTrue(boar.position().distanceTo(d.pos) <= 1, "reached player: " + boar.position());
        assertTrue(d.attacks >= 5);
    }

    @Test
    void rangedMonsterKeepsDistance() {
        // korytarz na -1: gracz tuz obok goblina
        MonsterManager mm = single("goblin", new Position(12, 22, -1), 0);
        Monster g = mm.monsters().get(0);
        Dummy d = new Dummy(new Position(11, 22, -1));
        run(mm, d, 0, 15_000);
        int dist = g.position().distanceTo(d.pos);
        assertTrue(dist >= 2, "goblin backed off: " + dist);
        assertTrue(d.attacks > 0, "and still attacks from range");
    }

    @Test
    void skirmisherFleesWhenWounded() {
        MonsterManager mm = single("wolf", new Position(10, 8, 0), 0);
        Monster w = mm.monsters().get(0);
        Dummy d = new Dummy(w.position().translate(1, 0));
        run(mm, d, 0, 1_000);
        w.damage(w.type().maxHealth() - 5);
        int before = w.position().distanceTo(d.pos);
        run(mm, d, 1_000, 4_000);
        assertTrue(w.position().distanceTo(d.pos) > before, "wolf ran away");
        assertTrue(w.state() == MonsterState.FLEE || w.cornered(), "fleeing, or fighting back when cornered");
    }

    @Test
    void monstersNeverEnterProtectionZoneAndReturnHome() {
        MonsterManager mm = single("wolf", new Position(14, 30, 0), 0);
        Monster w = mm.monsters().get(0);
        Dummy d = new Dummy(new Position(20, 23, 0)); // za brama, w strefie ochronnej
        run(mm, d, 0, 30_000);
        assertFalse(TestData.varden().isProtectionZone(w.position()));
        assertEquals(0, d.attacks, "player in protection zone is safe");
        // gracz odchodzi daleko: potwor wraca i sie leczy
        w.damage(10);
        d.pos = new Position(31, 16, 0);
        run(mm, d, 30_000, 60_000);
        assertEquals(MonsterState.IDLE, w.state());
        assertEquals(w.type().maxHealth(), w.health());
        assertTrue(w.position().distanceTo(w.spawn().center()) <= w.spawn().radius());
    }

    @Test
    void healerHealsWoundedAlly() {
        MonsterManager mm = new MonsterManager(TestData.varden(), REG, List.of(
                new SpawnPoint("goblin_shaman", new Position(12, 21, -2), 0, 1, 60),
                new SpawnPoint("goblin", new Position(8, 28, -2), 1, 1, 60)), new Random(4));
        mm.populate(null);
        Monster goblin = mm.monsters().stream().filter(m -> m.type().key().equals("goblin")).findFirst().orElseThrow();
        Monster shaman = mm.monsters().stream().filter(m -> m.type().key().equals("goblin_shaman")).findFirst().orElseThrow();
        Dummy d = new Dummy(new Position(10, 21, -2)); // ten sam korytarz, widoczny dla szamana
        run(mm, d, 0, 500);
        // przenies rannego goblina obok szamana
        goblin.damage(goblin.type().maxHealth() - 5);
        mm.grid().remove(goblin, goblin.position());
        Position near = shaman.position().translate(1, 0);
        while (!mm.canOccupy(near, d.pos)) near = near.translate(0, 1);
        goblin.placeAt(near);
        mm.grid().add(goblin);
        int hp = goblin.health();
        run(mm, d, 500, 8_000);
        assertTrue(d.heals > 0 && goblin.health() > hp, "shaman healed the goblin");
    }

    @Test
    void bossHealsOnceAndEnrages() {
        MonsterManager mm = single("grimtooth", new Position(12, 27, -5), 0);
        Monster boss = mm.monsters().get(0);
        Dummy d = new Dummy(boss.position().translate(1, 0));
        boss.damage((int) (boss.type().maxHealth() * 0.75));
        run(mm, d, 0, 3_000);
        assertTrue(boss.healedOnce());
        assertEquals(1, d.heals);
        boss.damage((int) (boss.type().maxHealth() * 0.6));
        run(mm, d, 3_000, 6_000);
        assertEquals(1, d.heals, "heals only once");
    }

    @Test
    void monstersDoNotSpawnNextToPlayer() {
        Position center = new Position(10, 8, 0);
        MonsterManager mm = new MonsterManager(TestData.varden(), REG, List.of(new SpawnPoint("wolf", center, 4, 3, 60)), new Random(1));
        mm.populate(center);
        for (Monster m : mm.monsters()) assertTrue(m.position().distanceTo(center) > 5 || mm.monsters().isEmpty());
    }

    @Test
    void farMonstersAreNotSimulated() {
        MonsterManager mm = single("boar", new Position(42, 6, 0), 0);
        Monster boar = mm.monsters().get(0);
        Position start = boar.position();
        Dummy d = new Dummy(new Position(10, 40, 0));
        run(mm, d, 0, 30_000);
        assertEquals(start, boar.position(), "out of active radius - frozen");
    }
}
