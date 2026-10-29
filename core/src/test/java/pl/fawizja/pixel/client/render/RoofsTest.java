package pl.fawizja.pixel.client.render;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.gathering.BerryBushes;
import pl.fawizja.pixel.world.MapLoader;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.TileRegistry;
import pl.fawizja.pixel.world.WorldMap;

/** Dach domu znika, gdy gracz do niego podchodzi; krzaki z owocami rysowane wariantami z owocami. */
class RoofsTest {

    private static WorldMap world;

    private static synchronized WorldMap world() throws Exception {
        if (world == null) world = new MapLoader(TileRegistry.loadDefault()).load("world");
        return world;
    }

    @Test
    void roofOfTheHouseNextToThePlayerIsHiddenOthersStay() throws Exception {
        WorldMap w = world();
        Roofs roofs = new Roofs(w);
        // drzwi wejsciowe domu w Varden: z jednej strony wnetrze pod dachem, z drugiej wolna ulica
        Position door = null, outside = null;
        int side = 0, house = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int y = 483; y < 523 && door == null; y++) {
            for (int x = 478; x < 568 && door == null; x++) {
                Position p = new Position(x, y, 0);
                if (!w.openings().isDoor(p) || roofs.buildingAt(x, y, 0) == 0) continue;
                for (int k = 0; k < 4 && door == null; k++) {
                    Position q = p.translate(dirs[k][0], dirs[k][1]);
                    if (w.isWalkable(q) && !roofs.covered(q)) { door = p; outside = q; side = k; house = roofs.buildingAt(x, y, 0); }
                }
            }
        }
        assertNotNull(door, "Varden has a roofed house with a street door");
        int dx = dirs[side][0], dy = dirs[side][1];
        assertFalse(roofs.covered(outside), "standing in front of the door is not under the roof");
        assertTrue(roofs.nearBuildings(outside).contains(house), "roof hidden in front of the door");
        assertTrue(roofs.nearBuildings(door.translate(dx * Roofs.HIDE_RADIUS, dy * Roofs.HIDE_RADIUS)).contains(house), "two fields away too");
        Position far = null;
        for (int d = 4; d < 40 && far == null; d++) {
            Position q = door.translate(dx * d, dy * d);
            if (!roofs.nearBuildings(q).contains(house) && !roofs.covered(q)) far = q;
        }
        assertNotNull(far, "far away the roof is drawn again");
        // plynne znikanie: po FADE_MS dach zdjety, po odejsciu wraca i znika z mapy przezroczystosci
        Map<Integer, Float> fade = new java.util.HashMap<>();
        Roofs.stepFade(fade, roofs.nearBuildings(outside), Roofs.FADE_MS / 2);
        float half = Roofs.alpha(fade, house);
        assertTrue(half > 0f && half < 1f, "fading: " + half);
        Roofs.stepFade(fade, roofs.nearBuildings(outside), Roofs.FADE_MS);
        assertEquals(0f, Roofs.alpha(fade, house));
        Roofs.stepFade(fade, roofs.nearBuildings(far), Roofs.FADE_MS);
        assertEquals(1f, Roofs.alpha(fade, house));
        assertFalse(fade.containsKey(house));
        assertEquals(1f, Roofs.alpha(fade, 0), "no building - nothing to hide");
    }

    @Test
    void fruitBushesUseFruitArtAndPlainBushesNever() {
        java.util.Set<Integer> fruit = new java.util.HashSet<>();
        for (int k : WorldRenderer.BUSH_BLUE) fruit.add(k);
        for (int k : WorldRenderer.BUSH_RED) fruit.add(k);
        for (int s = 0; s < 200; s++) {
            assertFalse(fruit.contains(WorldRenderer.bushVariant(null, false, s)), "plain bush without fruit art");
            int blue = WorldRenderer.bushVariant(BerryBushes.Kind.BLUEBERRY, false, s);
            int red = WorldRenderer.bushVariant(BerryBushes.Kind.RASPBERRY, false, s);
            assertTrue(fruit.contains(blue) && fruit.contains(red));
            assertFalse(fruit.contains(WorldRenderer.bushVariant(BerryBushes.Kind.BLUEBERRY, true, s)), "picked bush has no fruit");
            assertFalse(fruit.contains(WorldRenderer.bushVariant(BerryBushes.Kind.RASPBERRY, true, s)));
            assertTrue(WorldRenderer.bushVariant(null, false, s) < WorldRenderer.BUSH_VARIANTS);
        }
    }
}
