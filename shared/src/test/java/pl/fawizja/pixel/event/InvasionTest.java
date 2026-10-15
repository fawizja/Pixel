package pl.fawizja.pixel.event;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.Town;
import pl.fawizja.pixel.world.WorldMap;

class InvasionTest {

    @Test
    void killsAreCountedOnceAndRewardsFollowThem() {
        Invasion inv = new Invasion(Invasion.PLANS.get(0), new Position(1, 1, 0), List.of(1, 2, 3, 4), 4, 0, 1000);
        assertTrue(inv.onKill("Ola", 1));
        assertFalse(inv.onKill("Ula", 1), "already dead");
        assertFalse(inv.onKill("Ula", 99), "not an invader");
        assertTrue(inv.onKill("Ula", 2));
        assertTrue(inv.onKill("Ula", 4));
        assertEquals("Ula", inv.leaderSlayer());
        assertFalse(inv.over(999));
        assertTrue(inv.over(1000), "time is up");
        assertEquals("Ula", inv.ranking().get(0).getKey());
        assertEquals(2 * Invasion.REWARD_PER_KILL + Invasion.LEADER_BONUS, inv.reward("Ula"));
        assertEquals(Invasion.REWARD_PER_KILL, inv.reward("Ola"));
        assertEquals(0, inv.reward("Kuba"));
        inv.onKill("Ola", 3);
        assertTrue(inv.defended());
    }

    @Test
    void everyTownHasAGatherPointOutsideItsWallsAndTheMonstersExist() {
        WorldMap world = TestData.world();
        MonsterRegistry reg = MonsterRegistry.loadDefault(TestData.ITEMS);
        for (Invasion.Plan plan : Invasion.PLANS) {
            Town t = world.town(plan.town());
            assertNotNull(t, plan.town());
            reg.get(plan.monster());
            reg.get(plan.leader());
            Position p = Invasion.gatherPoint(world, t, new Random(7));
            assertNotNull(p, plan.town());
            assertTrue(world.isWalkable(p) && !world.isProtectionZone(p));
            assertTrue(Math.max(Math.abs(p.x() - t.temple().x()), Math.abs(p.y() - t.temple().y())) >= 14);
        }
        assertEquals("od wschodu", Invasion.direction(new Position(10, 10, 0), new Position(30, 12, 0)));
        assertEquals("od północy", Invasion.direction(new Position(10, 10, 0), new Position(12, 0, 0)));
    }

    @Test
    void eventMonstersDoNotRespawnAndCanBeCleared() {
        WorldMap world = TestData.world();
        MonsterManager mm = new MonsterManager(world, MonsterRegistry.loadDefault(TestData.ITEMS), List.of(), new Random(1));
        Town varden = world.town("Varden");
        Position at = Invasion.gatherPoint(world, varden, new Random(3));
        List<Monster> got = mm.spawnEvent("bandit", at, 6, 10, 0);
        assertEquals(10, got.size());
        mm.kill(got.get(0), 0);
        mm.update(10_000_000L, List.of(), new MonsterManager.AttackHandler() {
            @Override
            public void monsterAttacks(Monster m, long t) {}

            @Override
            public void monsterHeals(Monster h, Monster m, int a, long t) {}
        });
        assertEquals(9, mm.monsters().size(), "no respawn for event monsters");
        mm.clearEvent(List.of(got.get(1).id(), got.get(2).id()));
        assertEquals(7, mm.monsters().size());
    }
}
