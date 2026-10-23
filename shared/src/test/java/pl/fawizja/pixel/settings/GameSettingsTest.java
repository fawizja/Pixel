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
    void soundIsOffByDefaultCyclesThroughLevelsAndIsSaved() {
        GameSettings s = new GameSettings();
        assertEquals(0, s.volume());
        assertEquals(0f, s.volumeFactor());
        assertTrue(s.allRows()[4][1].contains("wyłączony"));
        assertEquals(0, GameSettings.fromMap(Map.of("volume", "60")).volume(), "old saved volume does not turn sound on");
        assertTrue(s.apply("settings:sound"));
        assertEquals(30, s.volume());
        assertEquals(30, GameSettings.fromMap(s.toMap()).volume());
        assertEquals(0, GameSettings.fromMap(Map.of("sound", "55")).volume(), "unsupported level falls back");
    }

    @Test
    void autoLootIsOnByDefaultTogglesAndIsSaved() {
        GameSettings s = new GameSettings();
        assertTrue(s.autoLoot());
        assertTrue(s.apply("settings:loot"));
        assertFalse(s.autoLoot());
        assertTrue(s.allRows()[5][1].contains("dotknięciem"));
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
        assertTrue(s.allRows()[7][1].contains("70%"));
        GameSettings back = GameSettings.fromMap(s.toMap());
        assertFalse(back.weather());
        assertEquals(70, back.autoHeal());
        s.apply("settings:heal");
        assertEquals(0, s.autoHeal(), "cycles back to off");
        assertEquals(0, GameSettings.fromMap(Map.of("heal", "45")).autoHeal(), "unsupported value falls back");
    }

    @Test
    void autoManaHasItsOwnThresholdAndIsSaved() {
        GameSettings s = new GameSettings();
        assertEquals(0, s.autoMana(), "off by default");
        assertTrue(s.apply("settings:mana"));
        s.apply("settings:mana");
        assertEquals(50, s.autoMana());
        assertEquals(0, s.autoHeal(), "independent from the health potion");
        assertTrue(s.allRows()[8][1].contains("50% MP"));
        assertEquals(50, GameSettings.fromMap(s.toMap()).autoMana());
        assertEquals(0, GameSettings.fromMap(Map.of("mana", "99")).autoMana());
    }

    @Test
    void autoEatIsSavedAndTheSecondPageHoldsTheRest() {
        GameSettings s = new GameSettings();
        assertFalse(s.autoEat());
        assertEquals(2, s.pages());
        String[][] first = s.rows();
        assertEquals(GameSettings.PAGE_SIZE + 1, first.length);
        assertEquals("settings:page", first[GameSettings.PAGE_SIZE][0]);
        assertTrue(s.apply("settings:page"));
        String[][] second = s.rows();
        assertEquals("settings:mana", second[0][0]);
        assertEquals("settings:eat", second[1][0]);
        assertTrue(second[second.length - 1][1].contains("strona 2 z 2"));
        assertTrue(s.apply("settings:eat"));
        assertTrue(s.autoEat());
        assertTrue(GameSettings.fromMap(s.toMap()).autoEat());
        assertEquals("settings:target", second[2][0]);
        assertTrue(s.apply("settings:target"));
        assertTrue(GameSettings.fromMap(s.toMap()).autoTarget());
        assertEquals("settings:vibrate", second[3][0]);
        assertTrue(s.vibrate(), "on by default");
        assertTrue(s.apply("settings:vibrate"));
        assertFalse(GameSettings.fromMap(s.toMap()).vibrate());
        assertTrue(GameSettings.fromMap(new java.util.HashMap<>()).vibrate(), "old settings keep vibration on");
        s.apply("settings:page");
        assertEquals(0, s.page());
    }
}
