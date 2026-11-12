package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;

/**
 * Obozy potworow w dziczy (tools/mapgen/camps.py): nieregularny obrys (nie prostokat), spore odstepy miedzy obozami
 * (Darek: "obozy orkow i lowiska nie maja byc kwadratowe i jedne obok drugich, daj je daleko od siebie").
 */
class CampsTest {

    /** Oboz -> rodzina potworow (jak camps.SPECS) i czy obrys to palisada/ruiny (kamienne pierscienie sprawdza tylko odstep). */
    static final Map<String, String> FAMILY = Map.ofEntries(
            Map.entry("Kamienny Bród", "orc"), Map.entry("Wilczy Jar", "orc"), Map.entry("Czerwony Kurhan", "orc"),
            Map.entry("Zielony Gaj", "goblin"), Map.entry("Kozi Przysiółek", "goblin"),
            Map.entry("Rozstaje", "bandit"), Map.entry("Gniazdo Rabusiów", "bandit"),
            Map.entry("Martwa Wieś", "undead"), Map.entry("Trollowe Leże", "troll"), Map.entry("Bagienna Osada", "lizard"),
            Map.entry("Lodowy Kurhan", "yeti"), Map.entry("Piaskowy Obóz", "nomad"));
    static final Set<String> FENCED = Set.of("Kamienny Bród", "Wilczy Jar", "Czerwony Kurhan", "Zielony Gaj", "Kozi Przysiółek",
            "Rozstaje", "Gniazdo Rabusiów", "Martwa Wieś", "Bagienna Osada");
    static final int MIN_ANY = 50, MIN_FAMILY = 70;

    static WorldMap world;

    @BeforeAll
    static void load() throws Exception {
        world = TestData.world();
    }

    static Region camp(String name) {
        return world.regions().stream().filter(r -> r.name().equals(name) && r.zMin() == 0 && r.zMax() == 0).findFirst()
                .orElseThrow(() -> new AssertionError("no camp " + name));
    }

    static int cx(Region r) { return (r.x0() + r.x1()) / 2; }

    static int cy(Region r) { return (r.y0() + r.y1()) / 2; }

    static String key(int id) {
        pl.fawizja.pixel.world.TileType t = id <= 0 ? null : world.tiles().get(id);
        return t == null ? "" : t.key();
    }

    /** Pole obrzeza obozu: belka palisady, drewniana sciana albo resztka muru. */
    static boolean ring(int x, int y) {
        FloorMap f = world.floor(0);
        return key(f.get(Layer.WALL, x, y)).matches("log_wall|wood_wall") || key(f.get(Layer.OBJECT, x, y)).equals("broken_wall");
    }

    @Test
    void campsAreFarApartAndFamiliesEvenFarther() {
        List<String> names = new ArrayList<>(FAMILY.keySet());
        for (int i = 0; i < names.size(); i++) {
            for (int j = i + 1; j < names.size(); j++) {
                Region a = camp(names.get(i)), b = camp(names.get(j));
                int d = Math.max(Math.abs(cx(a) - cx(b)), Math.abs(cy(a) - cy(b)));
                assertTrue(d >= MIN_ANY, names.get(i) + " / " + names.get(j) + " only " + d + " tiles apart");
                if (FAMILY.get(names.get(i)).equals(FAMILY.get(names.get(j)))) {
                    assertTrue(d >= MIN_FAMILY, names.get(i) + " / " + names.get(j) + " same kind, only " + d + " apart");
                }
            }
        }
    }

    @Test
    void campOutlinesAreIrregularNotRectangles() {
        for (String name : FENCED) {
            Region r = camp(name);
            int cells = 0, longest = 0, corners = 0;
            for (int y = r.y0(); y <= r.y1(); y++) {
                int run = 0;
                for (int x = r.x0(); x <= r.x1(); x++) {
                    if (ring(x, y)) {
                        cells++;
                        run++;
                        longest = Math.max(longest, run);
                    } else {
                        run = 0;
                    }
                }
            }
            // cztery naroza prostokata obejmujacego oboz (blok 6x6): obrys nieregularny omija wiekszosc z nich
            int[][] cs = {{r.x0(), r.y0()}, {r.x1() - 5, r.y0()}, {r.x0(), r.y1() - 5}, {r.x1() - 5, r.y1() - 5}};
            for (int[] c : cs) {
                boolean any = false;
                for (int y = c[1]; y < c[1] + 6; y++) for (int x = c[0]; x < c[0] + 6; x++) any |= ring(x, y);
                if (any) corners++;
            }
            assertTrue(cells >= 40, name + ": palisade/ruin cells " + cells);
            assertTrue(corners <= 2, name + ": " + corners + " of 4 bbox corners hold the outline (a rectangle has 4, a blob at most 2)");
            assertTrue(longest <= 16, name + ": straight wall run of " + longest);
        }
    }

    @Test
    void everyCampIsInsideItsOwnRegionAndOnLand() {
        for (String name : FAMILY.keySet()) {
            Region r = camp(name);
            assertTrue(world.isWalkable(new Position(cx(r), cy(r), 0)) || world.floor(0).isWalkable(cx(r) + 1, cy(r)),
                    name + ": centre is walkable (campfire clearing)");
        }
    }
}
