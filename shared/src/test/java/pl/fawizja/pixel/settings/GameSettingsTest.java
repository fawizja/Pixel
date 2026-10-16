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

    @Test
    void volumeCyclesThroughLevelsAndIsSaved() {
        GameSettings s = new GameSettings();
        assertEquals(60, s.volume());
        assertTrue(s.apply("settings:sound"));
        assertEquals(100, s.volume());
        s.cycleVolume();
        assertEquals(0, s.volume());
        assertEquals(0f, s.volumeFactor());
        assertTrue(s.rows()[4][1].contains("wyłączony"));
        assertEquals(0, GameSettings.fromMap(s.toMap()).volume());
        assertEquals(60, GameSettings.fromMap(Map.of("volume", "55")).volume(), "unsupported level falls back");
    }

    @Test
    void autoLootIsOnByDefaultTogglesAndIsSaved() {
        GameSettings s = new GameSettings();
        assertTrue(s.autoLoot());
        assertTrue(s.apply("settings:loot"));
        assertFalse(s.autoLoot());
        assertTrue(s.rows()[5][1].contains("dotknięciem"));
        assertFalse(GameSettings.fromMap(s.toMap()).autoLoot());
        assertTrue(GameSettings.fromMap(Map.of("loot", "?")).autoLoot(), "bad value falls back");
    }

    @Test
    void weatherAndAutoHealAreSaved() {
        GameSettings s = new GameSettings();
        assertTrue(s.weather());
        assertEquals(0, s.autoHeal(), "off by default");
        assertTrue(s.apply("settings:weather"));
        assertFalse(s.weather());
        assertTrue(s.apply("settings:heal"));
        assertEquals(30, s.autoHeal());
        s.apply("settings:heal");
        s.apply("settings:heal");
        assertEquals(70, s.autoHeal());
        assertTrue(s.rows()[7][1].contains("70%"));
        GameSettings back = GameSettings.fromMap(s.toMap());
        assertFalse(back.weather());
        assertEquals(70, back.autoHeal());
        s.apply("settings:heal");
        assertEquals(0, s.autoHeal(), "cycles back to off");
        assertEquals(0, GameSettings.fromMap(Map.of("heal", "45")).autoHeal(), "unsupported value falls back");
    }
}
