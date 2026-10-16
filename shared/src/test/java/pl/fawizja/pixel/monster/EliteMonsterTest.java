package pl.fawizja.pixel.monster;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.net.RemoteEntity;
import pl.fawizja.pixel.world.Position;

class EliteMonsterTest {

    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);

    @Test
    void theEliteVariantIsStrongerAndRicherButKeepsItsKey() {
        MonsterType wolf = REG.get("wolf");
        MonsterType e = wolf.eliteVariant();
        assertTrue(e.isElite());
        assertFalse(wolf.isElite());
        assertSame(e, wolf.eliteVariant(), "cached");
        assertSame(e, e.eliteVariant());
        assertEquals(wolf.key(), e.key());
        assertEquals(wolf.maxHealth() * 2, e.maxHealth());
        assertEquals(wolf.experience() * 3, e.experience());
        assertTrue(e.attackMax() > wolf.attackMax());
        assertTrue(e.name().endsWith("(elita)"));
        for (int i = 0; i < wolf.loot().size(); i++) {
            assertEquals(Math.min(1000, wolf.loot().get(i).chancePermille() * 2), e.loot().get(i).chancePermille());
        }
        for (MonsterType t : REG.all()) if (t.boss()) assertSame(t, t.eliteVariant(), "bosses have no elite form");
    }

    @Test
    void aboutFourPercentOfSpawnsAreElite() {
        int elite = 0;
        for (int id = 1; id <= 100_000; id++) if (MonsterType.eliteRoll(id)) elite++;
        assertTrue(elite > 3_500 && elite < 4_500, "elite " + elite);
    }

    @Test
    void spawnsUseTheRollAndTheOnlineClientSeesTheMark() {
        int eliteId = 1;
        while (!MonsterType.eliteRoll(eliteId)) eliteId++;
        // tyle spawnow, by numer elitarny na pewno padl
        SpawnPoint sp = new SpawnPoint("wolf", new Position(30, 30, 0), 6, eliteId + 5, 60);
        MonsterManager mm = new MonsterManager(TestData.varden(), REG, List.of(sp), new Random(4));
        mm.populate(null);
        for (Monster m : mm.monsters()) assertEquals(MonsterType.eliteRoll(m.id()), m.type().isElite(), "id " + m.id());
        MonsterManager client = new MonsterManager(TestData.varden(), REG, List.of(), new Random(1));
        client.syncRemote(List.of(new RemoteEntity("MO", 7, "wolf", "wolf", new Position(30, 30, 0), 100, "E", null),
                new RemoteEntity("MO", 8, "wolf", "wolf", new Position(31, 30, 0), 100, "-", null)), 0, 200);
        assertTrue(client.monsters().get(0).type().isElite());
        assertFalse(client.monsters().get(1).type().isElite());
    }
}
