package pl.fawizja.pixel.monster;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.world.WorldLayout;
import pl.fawizja.pixel.world.WorldMap;

class BossReportTest {

    @Test
    void bossesAreReportedAliveThenWithTheirRespawnTime() throws Exception {
        WorldMap world = TestData.world();
        MonsterRegistry reg = MonsterRegistry.loadDefault(TestData.ITEMS);
        MonsterManager mm = new MonsterManager(world, reg, SpawnTable.load(WorldLayout.WORLD_MAP, world, reg), new Random(3));
        mm.populate(null);
        List<MonsterManager.BossStatus> r = mm.bossReport(0);
        assertTrue(r.size() >= 3, "world has bosses: " + r);
        for (MonsterManager.BossStatus b : r) assertTrue(b.alive(), b.name());
        String line = MonsterManager.describe(r.get(0), 0);
        assertTrue(line.startsWith(r.get(0).name() + " (") && line.endsWith("): żyje"), line);

        Monster boss = null;
        for (Monster m : mm.monsters()) if (m.type().boss() && m.type().name().equals(r.get(0).name())) boss = m;
        assertNotNull(boss);
        mm.kill(boss, 1_000);
        MonsterManager.BossStatus after = mm.bossReport(1_000).get(0);
        assertFalse(after.alive());
        assertEquals(1_000 + boss.spawn().respawnSeconds() * 1000L, after.respawnAt());
        long secs = boss.spawn().respawnSeconds();
        assertTrue(MonsterManager.describe(after, 1_000).contains("odrodzi się za"), MonsterManager.describe(after, 1_000));
        assertTrue(MonsterManager.describe(after, 1_000 + secs * 1000L).endsWith("lada chwila"));
    }
}
