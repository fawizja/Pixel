package pl.fawizja.pixel.client.render;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Zloty smok (dune_drake) ma oryginalne klatki 64x64, a renderer rysuje pliki od BIG_MONSTER_MIN px na 2x2 polach. */
class BigMonsterArtTest {

    private static File art(String rel) {
        for (String base : new String[] {"assets/art/", "../assets/art/"}) {
            File f = new File(base + rel);
            if (f.exists()) return f;
        }
        throw new AssertionError("brak pliku " + rel);
    }

    @Test
    void dragonFramesAreNativeSize() throws Exception {
        String[] names = {"", "_walk1", "_walk2", "_up", "_up_walk1", "_up_walk2", "_left", "_left_walk1", "_left_walk2",
                "_right", "_right_walk1", "_right_walk2"};
        for (String n : names) {
            var img = ImageIO.read(art("monsters/dune_drake" + n + ".png"));
            assertEquals(64, img.getWidth(), n);
            assertEquals(64, img.getHeight(), n);
        }
        assertTrue(64 >= TileArt.BIG_MONSTER_MIN);
    }

    @Test
    void otherBigMonstersAreAtLeastDragonClassSized() throws Exception {
        for (String k : new String[] {"ice_dragon", "bone_dragon", "scale_dragon", "abyssal_demon", "bog_hydra"}) {
            var img = ImageIO.read(art("monsters/" + k + ".png"));
            assertTrue(Math.max(img.getWidth(), img.getHeight()) >= TileArt.BIG_MONSTER_MIN, k);
        }
    }
}
