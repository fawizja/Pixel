package pl.fawizja.pixel.settings;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.perf.GuardMode;
import pl.fawizja.pixel.perf.ResourceGuard;

class GameSettingsTest {

    @Test
    void batterySaverCapsFramesAndEffectsAndSettingsSurviveSaving() {
        GameSettings s = new GameSettings();
        assertEquals(60, s.effectiveFpsCap());
        assertEquals(1f, s.qualityCap());
        s.cycleFps();
        assertEquals(90, s.fpsCap());
        assertTrue(s.apply("settings:battery"));
        assertEquals(GameSettings.BATTERY_FPS, s.effectiveFpsCap());
        assertEquals(GameSettings.BATTERY_QUALITY, s.qualityCap());
        assertFalse(s.apply("settings:unknown"));

        GameSettings back = GameSettings.fromMap(s.toMap());
        assertEquals(90, back.fpsCap());
        assertTrue(back.batterySaver());
        assertTrue(back.highLighting());

        GameSettings broken = GameSettings.fromMap(Map.of("fps", "7", "battery", "tak", "light", "false"));
        assertEquals(60, broken.fpsCap(), "unsupported value falls back to the default");
        assertFalse(broken.batterySaver());
        assertFalse(broken.highLighting());
    }

    @Test
    void theResourceGuardCanOnlyLowerTheUsersLimit() {
        GameSettings s = new GameSettings();
        s.apply("settings:battery");
        ResourceGuard g = new ResourceGuard();
        assertEquals(GuardMode.NORMAL, g.mode());
        assertTrue(g.fpsCap(s.effectiveFpsCap()) <= s.effectiveFpsCap());
        assertTrue(Math.min(s.qualityCap(), g.effectsQuality()) <= s.qualityCap());
    }
}
