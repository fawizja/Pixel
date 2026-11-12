package pl.fawizja.pixel.client.render;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Pasek zycia i imie duzego potwora (smok 2x2) leza nad grafika, zwyklego tuz nad polem. */
class OverheadLayoutTest {

    @Test
    void normalMonsterKeepsItsBarJustAboveItsTile() {
        assertEquals(0f, OverheadLayout.lift(1f));
        assertEquals(10f + OverheadLayout.BAR_ABOVE_TILE, OverheadLayout.barY(10f, 1f), 1e-6);
    }

    @Test
    void bigMonsterBarSitsAboveTheTopOfItsSprite() {
        float tiles = OverheadLayout.spriteTiles(64);
        assertEquals(2f, tiles);
        assertEquals(1f, OverheadLayout.lift(tiles));
        float bar = OverheadLayout.barY(10f, tiles);
        assertTrue(bar >= 10f + tiles, "bar " + bar + " is not above the 2 tile sprite top " + (10f + tiles));
        assertTrue(bar > OverheadLayout.barY(10f, 1f) + 0.9f, "a whole tile higher than for a normal monster");
        // 48 px: jeden i pol pola
        assertEquals(1.5f, OverheadLayout.spriteTiles(48));
        assertEquals(0.5f, OverheadLayout.lift(OverheadLayout.spriteTiles(48)), 1e-6);
        assertEquals(1f, OverheadLayout.spriteTiles(20), "small sprites count as one tile");
        assertEquals(2f, OverheadLayout.spriteTiles(200), "never above 64 px");
    }

    @Test
    void theNewDragonsHave64PixelFramesSoTheyAreBig() throws Exception {
        for (String k : new String[] {"emerald_dragon", "hoarfrost_dragon", "coal_dragon", "dune_drake", "pearl_dragon"}) {
            java.io.File f = new java.io.File("../assets/art/monsters/" + k + ".png");
            if (!f.exists()) f = new java.io.File("assets/art/monsters/" + k + ".png");
            var img = javax.imageio.ImageIO.read(f);
            assertEquals(64, img.getWidth(), k);
            assertEquals(2f, OverheadLayout.spriteTiles(img.getHeight()), k);
        }
    }
}
