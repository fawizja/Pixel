package pl.fawizja.pixel.pvp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SkullWatchTest {

    @Test
    void warnsOncePerAppearanceOfARedSkull() {
        SkullWatch w = new SkullWatch();
        assertTrue(w.update(Map.of("Ola", "NONE", "Ula", "WHITE")).isEmpty());
        assertEquals(1, w.update(Map.of("Ola", "RED", "Ula", "WHITE")).size(), "skull gained in view");
        assertTrue(w.update(Map.of("Ola", "RED")).isEmpty(), "no repeat while in view");
        assertTrue(w.update(Map.of()).isEmpty());
        assertTrue(w.update(Map.of("Ola", "RED")).get(0).contains("Ola"), "back in view warns again");
        assertTrue(w.update(Map.of("Ola", "RED", "Zbir", "BLACK")).get(0).contains("czarną"), "black skulls warn too");
        assertTrue(w.update(Map.of("Ola", "RED", "Zbir", "BLACK", "Ewa", "YELLOW", "Olek", "ORANGE")).isEmpty());
    }
}
