package pl.fawizja.pixel.settings;

import java.util.HashMap;
import java.util.Map;

/**
 * Ustawienia gracza (etap 15): limit klatek, oszczedzanie baterii, jakosc swiatla i liczby obrazen.
 * Zapisywane jako pary tekstowe (na kliencie w Preferences); bledne wartosci wracaja do domyslnych.
 * Ochrona zasobow 90% / 75% dziala niezaleznie i moze tylko dodatkowo obnizyc te limity.
 */
public final class GameSettings {

    public static final int[] FPS_CHOICES = {30, 60, 90, 120};
    /** W trybie oszczedzania baterii: limit klatek i jakosc efektow. */
    public static final int BATTERY_FPS = 30;
    public static final float BATTERY_QUALITY = 0.5f;
    public static final float LOW_LIGHT_QUALITY = 0.5f;

    private int fpsCap = 60;
    private boolean batterySaver;
    private boolean highLighting = true;
    private boolean damageNumbers = true;

    public int fpsCap() { return fpsCap; }
    public boolean batterySaver() { return batterySaver; }
    public boolean highLighting() { return highLighting; }
    public boolean damageNumbers() { return damageNumbers; }

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

    public void toggleBatterySaver() { batterySaver = !batterySaver; }
    public void toggleHighLighting() { highLighting = !highLighting; }
    public void toggleDamageNumbers() { damageNumbers = !damageNumbers; }

    /** Obsluga przycisku z panelu ustawien ("settings:fps" itd.); false dla nieznanej akcji. */
    public boolean apply(String action) {
        switch (action) {
            case "settings:fps": cycleFps(); return true;
            case "settings:battery": toggleBatterySaver(); return true;
            case "settings:light": toggleHighLighting(); return true;
            case "settings:numbers": toggleDamageNumbers(); return true;
            default: return false;
        }
    }

    public Map<String, String> toMap() {
        Map<String, String> m = new HashMap<>();
        m.put("fps", String.valueOf(fpsCap));
        m.put("battery", String.valueOf(batterySaver));
        m.put("light", String.valueOf(highLighting));
        m.put("numbers", String.valueOf(damageNumbers));
        return m;
    }

    public static GameSettings fromMap(Map<String, ?> m) {
        GameSettings s = new GameSettings();
        if (m == null) return s;
        Object fps = m.get("fps");
        if (fps != null) {
            try {
                int v = Integer.parseInt(fps.toString().trim());
                for (int c : FPS_CHOICES) if (c == v) s.fpsCap = v;
            } catch (NumberFormatException ignored) {
                // zostaje domyslne
            }
        }
        s.batterySaver = bool(m.get("battery"), false);
        s.highLighting = bool(m.get("light"), true);
        s.damageNumbers = bool(m.get("numbers"), true);
        return s;
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
            {"settings:battery", "Oszczedzanie baterii: " + (batterySaver ? "wlaczone" : "wylaczone")},
            {"settings:light", "Swiatlo: " + (highLighting ? "wysoka jakosc" : "niska jakosc")},
            {"settings:numbers", "Liczby obrazen: " + (damageNumbers ? "pokazuj" : "ukryj")},
        };
    }
}
