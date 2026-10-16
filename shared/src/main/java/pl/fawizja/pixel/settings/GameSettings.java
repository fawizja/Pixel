package pl.fawizja.pixel.settings;

import java.util.HashMap;
import java.util.Map;

/**
 * Ustawienia gracza (etap 15): limit klatek, oszczedzanie baterii, jakosc swiatla, liczby obrazen, glosnosc
 * automatyczne lupy, pogoda i automatyczna mikstura zdrowia.
 * Zapisywane jako pary tekstowe (na kliencie w Preferences); bledne wartosci wracaja do domyslnych.
 * Ochrona zasobow 90% / 75% dziala niezaleznie i moze tylko dodatkowo obnizyc te limity.
 */
public final class GameSettings {

    public static final int[] FPS_CHOICES = {30, 60, 90, 120};
    /** W trybie oszczedzania baterii: limit klatek i jakosc efektow. */
    public static final int BATTERY_FPS = 30;
    public static final float BATTERY_QUALITY = 0.5f;
    public static final float LOW_LIGHT_QUALITY = 0.5f;
    /** Glosnosc efektow w procentach; 0 = wylaczone. */
    public static final int[] VOLUME_CHOICES = {0, 30, 60, 100};
    /** Prog zdrowia (%) automatycznej mikstury; 0 = wylaczona. */
    public static final int[] AUTO_HEAL_CHOICES = {0, 30, 50, 70};

    private int fpsCap = 60;
    private boolean batterySaver;
    private boolean highLighting = true;
    private boolean damageNumbers = true;
    private int volume = 60;
    private boolean autoLoot = true;
    private boolean weather = true;
    private int autoHeal;
    private int autoMana;

    public int fpsCap() { return fpsCap; }
    public boolean batterySaver() { return batterySaver; }
    public boolean highLighting() { return highLighting; }
    public boolean damageNumbers() { return damageNumbers; }
    public int volume() { return volume; }
    /** Przeszukiwanie cial obok postaci bez dotykania (lup do plecaka). */
    public boolean autoLoot() { return autoLoot; }
    /** Rysowanie opadow (deszcz, snieg, burza piaskowa). */
    public boolean weather() { return weather; }
    /** Mikstura zdrowia sama, gdy zdrowie spadnie ponizej tylu procent; 0 = wylaczona. */
    public int autoHeal() { return autoHeal; }
    /** Mikstura many sama, gdy mana spadnie ponizej tylu procent; 0 = wylaczona (te same progi co zdrowie). */
    public int autoMana() { return autoMana; }
    /** Glosnosc 0..1 dla odtwarzacza. */
    public float volumeFactor() { return volume / 100f; }

    /** Limit klatek po uwzglednieniu oszczedzania baterii. */
    public int effectiveFpsCap() { return batterySaver ? Math.min(BATTERY_FPS, fpsCap) : fpsCap; }

    /** Gorny limit jakosci efektow (0..1), mnozony przez wynik ochrony zasobow. */
    public float qualityCap() {
        float q = 1f;
        if (!highLighting) q = Math.min(q, LOW_LIGHT_QUALITY);
        if (batterySaver) q = Math.min(q, BATTERY_QUALITY);
        return q;
    }

    public void cycleFps() {
        int i = 0;
        while (i < FPS_CHOICES.length && FPS_CHOICES[i] != fpsCap) i++;
        fpsCap = FPS_CHOICES[(i + 1) % FPS_CHOICES.length];
    }

    public void cycleVolume() {
        int i = 0;
        while (i < VOLUME_CHOICES.length && VOLUME_CHOICES[i] != volume) i++;
        volume = VOLUME_CHOICES[(i + 1) % VOLUME_CHOICES.length];
    }

    public void toggleBatterySaver() { batterySaver = !batterySaver; }
    public void toggleHighLighting() { highLighting = !highLighting; }
    public void toggleDamageNumbers() { damageNumbers = !damageNumbers; }
    public void toggleAutoLoot() { autoLoot = !autoLoot; }
    public void toggleWeather() { weather = !weather; }

    public void cycleAutoHeal() {
        int i = 0;
        while (i < AUTO_HEAL_CHOICES.length && AUTO_HEAL_CHOICES[i] != autoHeal) i++;
        autoHeal = AUTO_HEAL_CHOICES[(i + 1) % AUTO_HEAL_CHOICES.length];
    }

    public void cycleAutoMana() {
        int i = 0;
        while (i < AUTO_HEAL_CHOICES.length && AUTO_HEAL_CHOICES[i] != autoMana) i++;
        autoMana = AUTO_HEAL_CHOICES[(i + 1) % AUTO_HEAL_CHOICES.length];
    }

    /** Obsluga przycisku z panelu ustawien ("settings:fps" itd.); false dla nieznanej akcji. */
    public boolean apply(String action) {
        switch (action) {
            case "settings:fps": cycleFps(); return true;
            case "settings:battery": toggleBatterySaver(); return true;
            case "settings:light": toggleHighLighting(); return true;
            case "settings:numbers": toggleDamageNumbers(); return true;
            case "settings:sound": cycleVolume(); return true;
            case "settings:loot": toggleAutoLoot(); return true;
            case "settings:weather": toggleWeather(); return true;
            case "settings:heal": cycleAutoHeal(); return true;
            case "settings:mana": cycleAutoMana(); return true;
            default: return false;
        }
    }

    public Map<String, String> toMap() {
        Map<String, String> m = new HashMap<>();
        m.put("fps", String.valueOf(fpsCap));
        m.put("battery", String.valueOf(batterySaver));
        m.put("light", String.valueOf(highLighting));
        m.put("numbers", String.valueOf(damageNumbers));
        m.put("volume", String.valueOf(volume));
        m.put("loot", String.valueOf(autoLoot));
        m.put("weather", String.valueOf(weather));
        m.put("heal", String.valueOf(autoHeal));
        m.put("mana", String.valueOf(autoMana));
        return m;
    }

    public static GameSettings fromMap(Map<String, ?> m) {
        GameSettings s = new GameSettings();
        if (m == null) return s;
        s.fpsCap = choice(m.get("fps"), FPS_CHOICES, s.fpsCap);
        s.volume = choice(m.get("volume"), VOLUME_CHOICES, s.volume);
        s.batterySaver = bool(m.get("battery"), false);
        s.highLighting = bool(m.get("light"), true);
        s.damageNumbers = bool(m.get("numbers"), true);
        s.autoLoot = bool(m.get("loot"), true);
        s.weather = bool(m.get("weather"), true);
        s.autoHeal = choice(m.get("heal"), AUTO_HEAL_CHOICES, 0);
        s.autoMana = choice(m.get("mana"), AUTO_HEAL_CHOICES, 0);
        return s;
    }

    private static int choice(Object v, int[] choices, int def) {
        if (v == null) return def;
        try {
            int x = Integer.parseInt(v.toString().trim());
            for (int c : choices) if (c == x) return x;
        } catch (NumberFormatException ignored) {
            // zostaje domyslne
        }
        return def;
    }

    private static boolean bool(Object v, boolean def) {
        if (v == null) return def;
        String t = v.toString().trim();
        if (t.equalsIgnoreCase("true")) return true;
        if (t.equalsIgnoreCase("false")) return false;
        return def;
    }

    /** Wiersze panelu: akcja i opis. */
    public String[][] rows() {
        return new String[][] {
            {"settings:fps", "Limit FPS: " + fpsCap + (batterySaver ? " (bateria: " + effectiveFpsCap() + ")" : "")},
            {"settings:battery", "Oszczędzanie baterii: " + (batterySaver ? "włączone" : "wyłączone")},
            {"settings:light", "Światło: " + (highLighting ? "wysoka jakość" : "niska jakość")},
            {"settings:numbers", "Liczby obrażeń: " + (damageNumbers ? "pokazuj" : "ukryj")},
            {"settings:sound", "Dźwięk: " + (volume == 0 ? "wyłączony" : volume + "%")},
            {"settings:loot", "Łupy z ciał: " + (autoLoot ? "automatycznie" : "dotknięciem")},
            {"settings:weather", "Pogoda: " + (weather ? "pokazuj" : "ukryj")},
            {"settings:heal", "Mikstura zdrowia sama: " + (autoHeal == 0 ? "wyłączona" : "poniżej " + autoHeal + "% HP")},
            {"settings:mana", "Mikstura many sama: " + (autoMana == 0 ? "wyłączona" : "poniżej " + autoMana + "% MP")},
        };
    }
}
