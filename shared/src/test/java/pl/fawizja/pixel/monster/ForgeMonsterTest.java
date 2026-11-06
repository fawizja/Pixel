package pl.fawizja.pixel.monster;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.net.RemoteEntity;
import pl.fawizja.pixel.world.Position;

/** Potwory pod wpływem (1..5) i diaboliczne z Kuzni (dawne "elity"). */
class ForgeMonsterTest {

    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);

    @Test
    void variantsUseCanaryMultipliersAndKeepTheKey() {
        MonsterType wolf = REG.get("wolf");
        assertFalse(wolf.isElite());
        assertSame(wolf, wolf.base());
        for (int s = 1; s <= 5; s++) {
            MonsterType v = wolf.influenced(s);
            assertTrue(v.isInfluenced() && v.isElite() && !v.isFiendish());
            assertEquals(s, v.forgeStack());
            assertSame(v, wolf.influenced(s), "cached");
            assertSame(v, v.influenced(s));
            assertSame(wolf, v.base());
            assertEquals(wolf.key(), v.key());
            assertEquals((int) Math.ceil(wolf.maxHealth() * (1 + (15.0 * s + 35) / 100)), v.maxHealth());
            assertEquals(Math.round(wolf.attackMax() * (1.35 + (s - 1) * 0.1)), v.attackMax());
            assertEquals(Math.round(wolf.experience() * (s + 10) / 10.0), v.experience());
            assertEquals(wolf.loot(), v.loot(), "no loot multiplier in Canary");
            assertEquals("pod wpływem (" + s + ")", v.forgeLabel());
            assertEquals("I" + s, MonsterManager.mark(v));
            assertSame(v, MonsterManager.variantFor(wolf, "I" + s));
        }
        MonsterType f = wolf.fiendish();
        assertTrue(f.isFiendish() && f.isElite() && !f.isInfluenced());
        assertEquals(MonsterType.FIENDISH_STACK, f.forgeStack());
        assertEquals((int) Math.ceil(wolf.maxHealth() * 3.6), f.maxHealth(), "stack 15: health x3.6");
        assertEquals("F", MonsterManager.mark(f));
        assertSame(f, MonsterManager.variantFor(wolf, "F"));
        assertSame(wolf, MonsterManager.variantFor(wolf, "-"));
        assertEquals("diaboliczny", f.forgeLabel());
        for (MonsterType t : REG.all()) {
            if (t.boss()) {
                assertSame(t, t.fiendish(), "bosses are never forge creatures");
                assertFalse(t.canBeForge());
            }
        }
    }

    @Test
    void aboutThreePercentOfSpawnsAreInfluencedWithTibiaStages() {
        int[] stages = new int[6];
        int n = 200_000;
        for (int id = 1; id <= n; id++) stages[MonsterType.forgeRoll(id)]++;
        int infl = n - stages[0];
        assertEquals(0.03, infl / (double) n, 0.003);
        double[] expected = {0.046, 0.253, 0.401, 0.253, 0.046};
        for (int s = 1; s <= 5; s++) assertEquals(expected[s - 1], stages[s] / (double) infl, 0.02, "stage " + s);
    }

    @Test
    void spawnsUseTheRollAndTheOnlineClientSeesTheMark() {
        int id = 1;
        while (MonsterType.forgeRoll(id) == 0) id++;
        // tyle potworow, by numer pod wpływem na pewno padl
        List<SpawnPoint> sps = new ArrayList<>();
        for (Position p : openTiles(id + 5)) sps.add(new SpawnPoint("wolf", p, 0, 1, 60));
        MonsterManager mm = new MonsterManager(TestData.varden(), REG, sps, new Random(4));
        mm.populate(null);
        assertEquals(id + 5, mm.monsters().size());
        for (Monster m : mm.monsters()) assertEquals(MonsterType.forgeRoll(m.id()), m.type().forgeStack(), "id " + m.id());
        mm.updateForge(0);
        assertTrue(mm.influencedAlive() >= 1);
        List<SpawnPoint> few = new ArrayList<>();
        for (Position p : openTiles(MonsterManager.FIENDISH_MIN_SPAWNS - 1)) few.add(new SpawnPoint("wolf", p, 0, 1, 60));
        MonsterManager tiny = new MonsterManager(TestData.varden(), REG, few, new Random(4));
        tiny.populate(null);
        tiny.updateForge(0);
        assertTrue(tiny.fiendishMonsters().isEmpty(), "tiny worlds (tests, arenas) have no fiendish creatures");

        MonsterManager client = new MonsterManager(TestData.varden(), REG, List.of(), new Random(1));
        client.syncRemote(List.of(new RemoteEntity("MO", 7, "wolf", "wolf", new Position(30, 30, 0), 100, "I4", null),
                new RemoteEntity("MO", 8, "wolf", "wolf", new Position(31, 30, 0), 100, "-", null),
                new RemoteEntity("MO", 9, "wolf", "wolf", new Position(32, 30, 0), 100, "F", null)), 0, 200);
        assertEquals(4, client.monsters().get(0).type().forgeStack());
        assertFalse(client.monsters().get(1).type().isElite());
        assertTrue(client.monsters().get(2).type().isFiendish());
        assertEquals(1, client.fiendishMonsters().size(), "the minimap of an online client");
        // serwer zmienia znacznik: klient podmienia rodzaj
        client.syncRemote(List.of(new RemoteEntity("MO", 9, "wolf", "wolf", new Position(32, 30, 0), 100, "-", null)), 300, 200);
        assertFalse(client.monsters().get(0).type().isElite());
    }

    /** Pierwsze wolne pola mapy Varden (poza strefa ochronna) na spawny o promieniu 0. */
    static List<Position> openTiles(int n) {
        pl.fawizja.pixel.world.WorldMap w = TestData.varden();
        MonsterManager probe = new MonsterManager(w, REG, List.of(), new Random(1));
        List<Position> out = new ArrayList<>();
        for (int y = 2; y < w.height() - 2 && out.size() < n; y += 2) {
            for (int x = 2; x < w.width() - 2 && out.size() < n; x += 2) {
                Position p = new Position(x, y, 0);
                if (probe.canOccupy(p, null)) out.add(p);
            }
        }
        assertEquals(n, out.size());
        return out;
    }

    @Test
    void fiendishCreaturesRotateHourlyAndReturnAfterAKill() {
        List<SpawnPoint> spawns = new ArrayList<>();
        for (Position p : openTiles(MonsterManager.FIENDISH_MIN_SPAWNS)) spawns.add(new SpawnPoint("wolf", p, 0, 1, 60));
        MonsterManager mm = new MonsterManager(TestData.varden(), REG, spawns, new Random(4));
        mm.setForgeRandom(new Random(9));
        mm.populate(null);
        mm.updateForge(1_000);
        List<Monster> f = mm.fiendishMonsters();
        assertEquals(MonsterManager.FIENDISH_LIMIT, f.size(), "monsters " + mm.monsters().size());
        Monster first = f.get(0);
        assertTrue(first.type().isFiendish());
        assertEquals(1_000 + MonsterManager.FIENDISH_MS, mm.fiendishUntil(first));
        assertEquals(1f, first.healthFraction(), 1e-6, "keeps its health fraction");

        // zabity: nowy diaboliczny dopiero po 270 s
        first.damage(first.health());
        mm.updateForge(5_000);
        assertEquals(MonsterManager.FIENDISH_LIMIT - 1, mm.fiendishMonsters().size());
        mm.updateForge(5_000 + MonsterManager.FIENDISH_RESPAWN_MS - 1_000);
        assertEquals(MonsterManager.FIENDISH_LIMIT - 1, mm.fiendishMonsters().size());
        mm.updateForge(5_000 + MonsterManager.FIENDISH_RESPAWN_MS);
        assertEquals(MonsterManager.FIENDISH_LIMIT, mm.fiendishMonsters().size());

        // po godzinie wracaja do zwyklych, a ich miejsce zajmuja inne
        Monster second = mm.fiendishMonsters().get(0);
        mm.updateForge(1_000 + MonsterManager.FIENDISH_MS);
        assertFalse(second.type().isFiendish());
        assertSame(second.type(), second.type().base());
        assertEquals(MonsterManager.FIENDISH_LIMIT, mm.fiendishMonsters().size());
        assertFalse(mm.fiendishMonsters().contains(second));
    }
}
