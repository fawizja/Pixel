package pl.fawizja.pixel.monster;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldLayout;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Etap 16: budzet czasu petli serwera. 200 graczy rozstawionych przy spawnach calego swiata (256x232,
 * wszystkie pietra), pelna populacja potworow, 400 tikow AI. Mierzy sredni czas tiku; budzet jest
 * hojny (komputery CI bywaja wolne), ale wylapie przypadkowe O(n^2) po calym swiecie.
 */
class ServerLoadBudgetTest {

    record Player(Position position) implements MonsterManager.Target {
        public boolean attackable() { return true; }
    }

    @Test
    void twoHundredPlayersFitInTheServerTick() throws Exception {
        WorldMap world = TestData.world();
        MonsterRegistry reg = MonsterRegistry.loadDefault(TestData.ITEMS);
        List<SpawnPoint> spawns = SpawnTable.load(WorldLayout.WORLD_MAP, world, reg);
        MonsterManager mm = new MonsterManager(world, reg, spawns, new Random(1));
        mm.populate(null);
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            Position c = spawns.get(i % spawns.size()).center();
            players.add(new Player(c.translate(i % 3 - 1, 0)));
        }
        MonsterManager.AttackHandler none = new MonsterManager.AttackHandler() {
            public void monsterAttacks(Monster m, long now) {}
            public void monsterHeals(Monster healer, Monster target, int amount, long now) {}
        };
        for (long t = 0; t < 2_000; t += 50) mm.update(t, players, none); // rozgrzewka JIT
        long start = System.nanoTime();
        int ticks = 400;
        for (int i = 0; i < ticks; i++) mm.update(2_000 + i * 50L, players, none);
        double msPerTick = (System.nanoTime() - start) / 1e6 / ticks;
        System.out.printf("monsters=%d players=%d avg tick=%.3f ms%n", mm.monsters().size(), players.size(), msPerTick);
        assertTrue(mm.monsters().size() > 200, "full population " + mm.monsters().size());
        assertTrue(msPerTick < 25, "server AI tick " + msPerTick + " ms (budget 25 ms of a 50 ms tick)");
    }
}
