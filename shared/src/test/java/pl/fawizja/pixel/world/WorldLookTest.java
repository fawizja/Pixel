package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;

/** Wyglad swiata (Darek, 2026-10-28): towary na ladach, stawy, roslinnosc wg klimatu, spalone wioski. */
class WorldLookTest {

    private final WorldMap world = TestData.world();

    @Test
    void shopCountersShowGoodsTheShopSells() {
        Map<Position, String> displays = world.displays();
        assertTrue(displays.size() >= 40, "towary na ladach: " + displays.size());
        Set<String> counters = Set.of("counter", "showcase", "altar"); // altar: przedmiot profesji (AltarQuest)
        for (Map.Entry<Position, String> e : displays.entrySet()) {
            Position p = e.getKey();
            assertNotNull(TestData.ITEMS.byKeyOrNull(e.getValue()), "nieznany przedmiot " + e.getValue());
            int obj = world.floor(p.z()).get(Layer.OBJECT, p.x(), p.y());
            assertTrue(counters.contains(world.tiles().get(obj).key()), "lada pod towarem " + p);
            assertFalse(world.isWalkable(p), "na lade nie mozna wejsc " + p);
        }
        // kupiec Varden (507,496) ma towary tuz przed soba
        assertTrue(displays.keySet().stream().anyMatch(p -> p.z() == 0 && Math.abs(p.x() - 507) <= 2
                && Math.abs(p.y() - 496) <= 2), "lada kupca Varden");
    }

    @Test
    void climateVegetationPondsAndRavagedVillages() {
        FloorMap f = world.floor(0);
        Map<String, Integer> count = new java.util.HashMap<>();
        for (int y = 0; y < world.height(); y++) {
            for (int x = 0; x < world.width(); x++) {
                for (Layer l : new Layer[] {Layer.OBJECT, Layer.DECORATION}) {
                    int id = f.get(l, x, y);
                    if (id != 0) count.merge(world.tiles().get(id).key(), 1, Integer::sum);
                }
            }
        }
        for (String k : new String[] {"jungle_tree", "jungle_bush", "frost_pine", "frost_bush", "dry_bush",
                "autumn_tree", "reeds", "mushrooms", "burnt_tree", "burnt_beam", "blood", "corpse", "debris"}) {
            assertTrue(count.getOrDefault(k, 0) > 0, "brak " + k);
        }
        assertTrue(count.get("jungle_tree") > 5000, "dzungla: " + count.get("jungle_tree"));
        assertTrue(count.get("frost_pine") > 1000, "oszronione swierki: " + count.get("frost_pine"));
    }
}
