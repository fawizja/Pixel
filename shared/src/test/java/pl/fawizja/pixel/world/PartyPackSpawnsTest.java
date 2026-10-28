package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.combat.CombatSystem;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.monster.SpawnTable;

/** Etap 9: lepsze i glebsze lowiska maja stada dla druzyn (3-6 potworow na punkt), okolice miast bez zmian. */
class PartyPackSpawnsTest {

    static WorldMap world;
    static MonsterRegistry reg;
    static List<SpawnPoint> spawns;

    @BeforeAll
    static void load() throws Exception {
        world = TestData.world();
        reg = MonsterRegistry.loadDefault(TestData.ITEMS);
        spawns = SpawnTable.load(WorldLayout.WORLD_MAP, world, reg);
    }

    @Test
    void deepAndHighLevelSpotsHavePacksOfThreeToSix() {
        long packs = spawns.stream().filter(SpawnPoint::pack).count();
        assertTrue(packs > 300, "pack spawn points: " + packs);
        for (SpawnPoint sp : spawns) {
            if (!sp.pack()) continue;
            MonsterType t = reg.get(sp.monsterKey());
            assertTrue(sp.count() >= 3 && sp.count() <= 6, "pack size " + sp);
            assertFalse(t.boss(), "no boss packs " + sp);
            assertFalse(sp.nightOnly(), "night spawns unchanged " + sp);
            assertTrue(t.level() > 15, "beginner monsters are never packs " + sp);
        }
        // pietra ponizej -1: wiekszosc punktow to stada
        long deep = spawns.stream().filter(sp -> sp.center().z() <= -2 && !reg.get(sp.monsterKey()).boss()).count();
        long deepPacks = spawns.stream().filter(sp -> sp.center().z() <= -2 && sp.pack()).count();
        assertTrue(deepPacks * 2 > deep, deepPacks + " of " + deep + " deep spawn points are packs");
        double avgDeep = spawns.stream().filter(sp -> sp.center().z() <= -3 && sp.pack()).mapToInt(SpawnPoint::count).average().orElse(0);
        assertTrue(avgDeep >= 4, "floors -3 and below: packs of 4-6, avg " + avgDeep);
    }

    @Test
    void townsAndBeginnerAreasKeepTheirSpawns() {
        for (Town town : world.towns()) {
            Position temple = town.temple();
            for (SpawnPoint sp : spawns) {
                int d = Math.max(Math.abs(sp.center().x() - temple.x()), Math.abs(sp.center().y() - temple.y()));
                assertFalse(sp.pack() && d < 60, "no packs within 60 tiles of a temple " + sp);
            }
        }
    }

    @Test
    void packMonstersGiveSlightlyLessExperience() {
        SpawnPoint pack = spawns.stream().filter(SpawnPoint::pack).findFirst().orElseThrow();
        SpawnPoint plain = spawns.stream().filter(sp -> !sp.pack()).findFirst().orElseThrow();
        assertEquals(CombatSystem.PACK_EXP_PERCENT, CombatSystem.expPercent(new Monster(1, reg.get(pack.monsterKey()), pack, pack.center())));
        assertEquals(100, CombatSystem.expPercent(new Monster(2, reg.get(plain.monsterKey()), plain, plain.center())));
        assertTrue(CombatSystem.PACK_EXP_PERCENT >= 80 && CombatSystem.PACK_EXP_PERCENT < 100, "only slightly less");
    }
}
