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
        assertTrue(s.allRows()[5][1].contains("wyłączony"));
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
        assertTrue(s.allRows()[6][1].contains("dotknięciem"));
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
        assertTrue(s.allRows()[8][1].contains("70%"));
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
        assertTrue(s.allRows()[9][1].contains("50% MP"));
        assertEquals(50, GameSettings.fromMap(s.toMap()).autoMana());
        assertEquals(0, GameSettings.fromMap(Map.of("mana", "99")).autoMana());
    }

    @Test
    void autoEatIsSavedAndTheSecondPageHoldsTheRest() {
        GameSettings s = new GameSettings();
        assertFalse(s.autoEat());
        assertEquals(3, s.pages());
        String[][] first = s.rows();
        assertEquals(GameSettings.PAGE_SIZE + 1, first.length);
        assertEquals("settings:page", first[GameSettings.PAGE_SIZE][0]);
        assertTrue(s.apply("settings:page"));
        String[][] second = s.rows();
        assertEquals("settings:heal", second[0][0]);
        assertEquals("settings:mana", second[1][0]);
        assertEquals("settings:eat", second[2][0]);
        assertTrue(second[second.length - 1][1].contains("strona 2 z 3"));
        assertTrue(s.apply("settings:eat"));
        assertTrue(s.autoEat());
        assertTrue(GameSettings.fromMap(s.toMap()).autoEat());
        assertEquals("settings:target", second[3][0]);
        assertTrue(s.apply("settings:target"));
        assertTrue(GameSettings.fromMap(s.toMap()).autoTarget());
        assertEquals("settings:vibrate", second[4][0]);
        assertTrue(s.vibrate(), "on by default");
        assertTrue(s.apply("settings:vibrate"));
        assertFalse(GameSettings.fromMap(s.toMap()).vibrate());
        assertTrue(GameSettings.fromMap(new java.util.HashMap<>()).vibrate(), "old settings keep vibration on");
        s.apply("settings:page");
        s.apply("settings:page");
        assertEquals(0, s.page());
    }

    @Test
    void controlSchemeAndSensitivityAreSavedAndShown() {
        GameSettings s = new GameSettings();
        assertEquals(pl.fawizja.pixel.movement.ControlScheme.DPAD, s.controlScheme(), "krzyzyk domyslnie");
        assertEquals(3, s.sensitivity());
        assertTrue(java.util.Arrays.stream(s.allRows()).anyMatch(r -> r[1].equals("Sterowanie: Krzyżyk")));
        assertTrue(java.util.Arrays.stream(s.allRows()).noneMatch(r -> r[0].equals("settings:joyfloat")),
                "joystick pod kciukiem tylko dla klasycznego joysticka");
        assertTrue(s.apply("settings:controls"));
        assertEquals(pl.fawizja.pixel.movement.ControlScheme.FLOAT4, s.controlScheme());
        s.apply("settings:controls");
        s.apply("settings:controls");
        assertEquals(pl.fawizja.pixel.movement.ControlScheme.CLASSIC, s.controlScheme());
        assertTrue(java.util.Arrays.stream(s.allRows()).anyMatch(r -> r[0].equals("settings:joyfloat")));
        assertTrue(s.apply("settings:sens:5"));
        assertEquals(5, s.sensitivity());
        assertTrue(s.apply("settings:sens"));
        assertEquals(1, s.sensitivity(), "suwak w kolko 1..5");
        assertTrue(s.apply("settings:sens:9"));
        assertEquals(5, s.sensitivity(), "poza zakresem - najblizsza wartosc");
        assertFalse(s.apply("settings:sens:x"));
        GameSettings back = GameSettings.fromMap(s.toMap());
        assertEquals(pl.fawizja.pixel.movement.ControlScheme.CLASSIC, back.controlScheme());
        assertEquals(5, back.sensitivity());
        GameSettings broken = GameSettings.fromMap(Map.of("controls", "turbo", "sens", "17"));
        assertEquals(pl.fawizja.pixel.movement.ControlScheme.DPAD, broken.controlScheme());
        assertEquals(3, broken.sensitivity());
        assertEquals(pl.fawizja.pixel.movement.ControlScheme.DPAD, GameSettings.fromMap(Map.of("joyfloat", "true")).controlScheme(),
                "stare ustawienia dostaja krzyzyk");
    }

    @Test
    void newUiIsDefaultAndClassicIsRemembered() {
        GameSettings s = new GameSettings();
        assertTrue(s.newUi(), "nowy wyglad domyslnie");
        assertTrue(java.util.Arrays.stream(s.allRows()).anyMatch(r -> r[1].equals("Wygląd interfejsu: nowy")));
        assertTrue(s.apply("settings:ui"));
        assertFalse(s.newUi());
        assertTrue(java.util.Arrays.stream(s.allRows()).anyMatch(r -> r[1].equals("Wygląd interfejsu: klasyczny")));
        assertFalse(GameSettings.fromMap(s.toMap()).newUi());
        assertTrue(GameSettings.fromMap(new java.util.HashMap<>()).newUi(), "stare ustawienia - nowy wyglad");
        assertTrue(GameSettings.fromMap(Map.of("ui", "cos")).newUi());
        assertEquals(Boolean.FALSE, s.toggleState("settings:ui"));
        assertEquals(Boolean.TRUE, s.toggleState("settings:loot"));
        assertNull(s.toggleState("settings:fps"), "limit FPS ma kilka wartosci - bez przelacznika");
    }

    @Test
    void minimapZoomStepsStopAtTheEndsAndIsSaved() {
        GameSettings s = new GameSettings();
        assertEquals(GameSettings.MINIMAP_ZOOM_DEFAULT, s.minimapZoom());
        assertEquals(1.5f, s.minimapPixelsPerTile(), 1e-6, "domyslnie tyle pol co dawna minimapa");
        for (int i = 0; i < 10; i++) assertTrue(s.apply("settings:mmzoom+"));
        assertEquals(GameSettings.MINIMAP_ZOOM.length - 1, s.minimapZoom(), "najwieksze przyblizenie");
        assertFalse(s.canZoomMinimapIn());
        assertTrue(s.canZoomMinimapOut());
        assertEquals(GameSettings.MINIMAP_ZOOM.length - 1, GameSettings.fromMap(s.toMap()).minimapZoom(), "zapisane");
        for (int i = 0; i < 10; i++) s.apply("settings:mmzoom-");
        assertEquals(0, s.minimapZoom());
        assertFalse(s.canZoomMinimapOut());
        assertEquals(0, GameSettings.fromMap(s.toMap()).minimapZoom());
        assertTrue(s.apply("settings:mmzoom:3"));
        assertEquals(3, GameSettings.fromMap(s.toMap()).minimapZoom());
        assertFalse(s.apply("settings:mmzoom:x"));
        assertEquals(GameSettings.MINIMAP_ZOOM_DEFAULT, GameSettings.fromMap(Map.of("mmzoom", "99")).minimapZoom(), "bledna wartosc");
        assertEquals(GameSettings.MINIMAP_ZOOM_DEFAULT, GameSettings.fromMap(new java.util.HashMap<>()).minimapZoom(), "stare zapisy");
        for (int i = 1; i < GameSettings.MINIMAP_ZOOM.length; i++) assertTrue(GameSettings.MINIMAP_ZOOM[i] > GameSettings.MINIMAP_ZOOM[i - 1]);
    }

    @Test
    void languageDefaultsToEnglishAndPersists() {
        pl.fawizja.pixel.i18n.Language before = pl.fawizja.pixel.i18n.Messages.globalLanguage();
        try {
            assertEquals(pl.fawizja.pixel.i18n.Language.EN, new GameSettings().language(), "nowa instalacja: angielski");
            assertEquals(pl.fawizja.pixel.i18n.Language.EN, GameSettings.fromMap(new java.util.HashMap<>()).language(), "stare zapisy bez jezyka");
            for (pl.fawizja.pixel.i18n.Language l : pl.fawizja.pixel.i18n.Language.values()) {
                GameSettings s = new GameSettings();
                assertTrue(s.apply("settings:lang:" + l.code), l.code);
                assertEquals(l, pl.fawizja.pixel.i18n.Messages.globalLanguage(), "zmiana dziala od razu");
                assertEquals(l, GameSettings.fromMap(s.toMap()).language(), "zapis i odczyt");
            }
            GameSettings c = new GameSettings();
            c.setLanguage(pl.fawizja.pixel.i18n.Language.EN);
            c.apply("settings:lang");
            assertEquals(pl.fawizja.pixel.i18n.Language.PL, c.language(), "przelaczanie po kolei");
        } finally {
            pl.fawizja.pixel.i18n.Messages.setLanguage(before);
        }
    }
}
