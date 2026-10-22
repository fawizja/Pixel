package pl.fawizja.pixel.monster;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Region;
import pl.fawizja.pixel.world.WorldMap;

/** Etap 10: bossowie na dnie lochow z etapu 9 i zestaw koralowy (poziom 350) miedzy obsydianem (250) a lodem (450). */
class DungeonBossTest {

    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);
    static final WorldMap WORLD = TestData.world();

    @Test
    void everyNewDungeonEndsWithABossOnItsDeepestFloor() throws Exception {
        List<SpawnPoint> spawns = SpawnTable.load("world", WORLD, REG);
        Object[][] bosses = {{"bloodbeard", -3}, {"pyrrax", -4}, {"skaldra", -3}, {"morvena", -3}, {"wyvern_matriarch", -2}};
        for (Object[] b : bosses) {
            MonsterType t = REG.get((String) b[0]);
            assertTrue(t.boss(), t.key());
            SpawnPoint sp = spawns.stream().filter(s -> s.monsterKey().equals(t.key())).findFirst().orElseThrow();
            assertEquals((int) b[1], sp.center().z(), t.key() + " on the deepest floor");
            assertEquals(1, sp.count(), t.key() + " is alone");
            assertTrue(sp.respawnSeconds() >= 3600, t.key() + " respawns slowly");
            Region r = WORLD.regionAt(sp.center());
            assertTrue(t.level() >= r.levelMin() && t.level() <= r.levelMax(), t.key() + " fits " + r.name());
            assertTrue(t.loot().stream().anyMatch(l -> TestData.ITEMS.get(l.itemId()).minLevel() >= 350 && l.chancePermille() >= 200),
                    t.key() + " drops a level 350+ item often");
        }
    }

    @Test
    void theCoralSetFillsTheGapBetweenObsidianAndGlacialForEveryVocation() {
        for (Vocation v : Vocation.values()) {
            boolean weapon = false;
            for (ItemType it : TestData.ITEMS.all()) {
                if (it.minLevel() == 350 && it.usableBy(v) && it.key().matches("tidal_harpoon|wand_of_the_deep|rod_of_tides|reef_gauntlets")) weapon = true;
            }
            assertTrue(weapon, v + " has a level 350 weapon");
        }
        for (String k : new String[] {"coral_helmet", "coral_armor", "coral_legs", "coral_shield"}) {
            ItemType it = TestData.ITEMS.byKey(k);
            assertEquals(350, it.minLevel(), k);
            int dropped = 0;
            for (MonsterType t : REG.all()) for (var l : t.loot()) if (l.itemId() == it.id()) dropped++;
            assertTrue(dropped >= 2, k + " drops from a boss and from island monsters");
        }
    }
}
