package pl.fawizja.pixel.monster;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.combat.DamageType;

class MonsterDataTest {

    private final MonsterRegistry reg = MonsterRegistry.loadDefault(TestData.ITEMS);

    @Test
    void monstersAndLootLoad() {
        assertTrue(reg.all().size() >= 10);
        for (MonsterType t : reg.all()) {
            assertFalse(t.loot().isEmpty(), t.key() + " has loot");
            assertTrue(t.attackMax() > 0);
        }
        assertEquals(100, reg.get("skeleton").resistance(DamageType.DEATH));
        assertTrue(reg.get("skeleton").resistance(DamageType.HOLY) < 0, "skeletons are weak to holy");
        assertTrue(reg.get("grimtooth").boss());
    }

    @Test
    void monstersDifferInBehaviourNotOnlyStats() {
        Set<MonsterBehavior> behaviors = EnumSet.noneOf(MonsterBehavior.class);
        Set<DamageType> damage = EnumSet.noneOf(DamageType.class);
        for (MonsterType t : reg.all()) {
            behaviors.add(t.behavior());
            damage.add(t.damageType());
        }
        assertEquals(EnumSet.allOf(MonsterBehavior.class), behaviors, "every behaviour is used");
        assertTrue(damage.size() >= 4, "several damage types: " + damage);
    }

    @Test
    void spawnsAreValidAndOutsideProtectionZones() throws Exception {
        List<SpawnPoint> spawns = SpawnTable.load("varden", TestData.varden(), reg);
        assertTrue(spawns.size() >= 10);
        for (SpawnPoint sp : spawns) {
            assertTrue(TestData.varden().isWalkable(sp.center()));
            assertFalse(TestData.varden().isProtectionZone(sp.center()));
        }
        // glebsze pietra kopalni maja silniejsze potwory
        MonsterManager mm = new MonsterManager(TestData.varden(), reg, spawns, new java.util.Random(1));
        assertTrue(mm.maxMonsterLevelOnFloor(-5) > mm.maxMonsterLevelOnFloor(-3));
        assertTrue(mm.maxMonsterLevelOnFloor(-3) > mm.maxMonsterLevelOnFloor(-1));
    }

    @Test
    void nightSpawnsAreNocturnalSurfaceMonstersInEveryClimate() throws Exception {
        List<SpawnPoint> night = SpawnTable.load("world", TestData.world(), reg).stream().filter(SpawnPoint::nightOnly).toList();
        assertTrue(night.size() >= 5, "night spawns: " + night.size());
        Set<String> regions = new java.util.HashSet<>();
        for (SpawnPoint sp : night) {
            assertTrue(reg.get(sp.monsterKey()).nocturnal(), sp.monsterKey() + " should be nocturnal");
            assertTrue(sp.center().z() >= 0);
            pl.fawizja.pixel.world.Region r = TestData.world().regionAt(sp.center());
            assertNotNull(r, "night spawn outside regions: " + sp);
            assertTrue(reg.get(sp.monsterKey()).level() <= r.levelMax() + 20,
                    sp.monsterKey() + " too strong for " + r.name());
            regions.add(r.name());
        }
        assertTrue(regions.containsAll(Set.of("Krainy Varden", "Mrozne Pustkowia", "Pustynia Qasirah")), regions.toString());
    }
}
