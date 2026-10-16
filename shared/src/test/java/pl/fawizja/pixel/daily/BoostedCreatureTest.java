package pl.fawizja.pixel.daily;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.combat.DamageType;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

class BoostedCreatureTest {
    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);

    @Test
    void sameDaySameMonsterNeverABoss() {
        Set<String> seen = new HashSet<>();
        for (long day = 20_000; day < 20_060; day++) {
            MonsterType a = BoostedCreature.pick(day, REG.all());
            assertNotNull(a);
            assertFalse(a.boss(), "bosses are never boosted");
            assertSame(a, BoostedCreature.pick(day, List.copyOf(REG.all())), "independent of collection order");
            seen.add(a.key());
        }
        assertTrue(seen.size() >= 10, "the boost rotates: " + seen);
        assertNull(BoostedCreature.pick(1, List.of()));
    }

    @Test
    void bonusMath() {
        assertEquals(150, BoostedCreature.boostExp(100));
        assertEquals(30, BoostedCreature.boostExp(20));
        assertEquals(150, BoostedCreature.boostChance(100));
        assertEquals(1000, BoostedCreature.boostChance(900), "capped at 100%");
    }

    @Test
    void boostedKillPaysHalfMoreExperience() {
        // dzien, w ktorym wilki sa potworem dnia, i dzien, w ktorym nie sa
        List<MonsterType> types = List.of(REG.get("wolf"), REG.get("boar"));
        long wolfDay = -1, otherDay = -1;
        for (long d = 0; d < 100 && (wolfDay < 0 || otherDay < 0); d++) {
            if (BoostedCreature.pick(d, types).key().equals("wolf")) { if (wolfDay < 0) wolfDay = d; }
            else if (otherDay < 0) otherDay = d;
        }
        assertTrue(wolfDay >= 0 && otherDay >= 0);
        long boosted = killWolf(wolfDay, true);
        long plain = killWolf(otherDay, false);
        assertEquals(plain + plain / 2, boosted);
    }

    private static long killWolf(long day, boolean expectBoost) {
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        pc.setPosition(new Position(58, 70, 0));
        List<SpawnPoint> spawns = List.of(new SpawnPoint("wolf", new Position(58, 72, 0), 0, 1, 600),
                new SpawnPoint("boar", new Position(58, 72, 0), 3, 1, 600));
        MonsterManager mm = new MonsterManager(TestData.world(), REG, spawns, new Random(2));
        mm.populate(null);
        GameSession s = new GameSession(TestData.ITEMS, TestData.world(), pc, mm, new Random(3), 8);
        s.setToday(() -> day);
        s.update(1000, null);
        assertEquals(expectBoost, s.combat().isBoosted(REG.get("wolf")));
        assertTrue(s.log().latest(50).stream().anyMatch(e -> e.kind() == EventLog.Kind.SYSTEM && e.text().startsWith("Potwór dnia: ")),
                "the boost is announced");
        Monster wolf = null;
        for (Monster m : mm.monsters()) if (m.type().key().equals("wolf")) wolf = m;
        assertNotNull(wolf);
        long exp = pc.experience();
        s.combat().damageMonster(wolf, 100_000, DamageType.FIRE, 2000);
        assertTrue(wolf.isDead());
        return pc.experience() - exp;
    }
}
