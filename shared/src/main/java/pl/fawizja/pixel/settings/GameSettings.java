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
    /** Rozmiar joysticka w procentach domyslnego (etap 17: sterowanie mobilne). */
    public static final int[] JOY_SIZE_CHOICES = {80, 100, 125, 150};

    private int fpsCap = 60;
    private boolean batterySaver;
    private boolean highLighting = true;
    private boolean damageNumbers = true;
    private int volume = 60;
    private boolean autoLoot = true;
    private boolean weather = true;
    private int autoHeal;
    private int autoMana;
    private boolean autoEat;
    /** Wibracja telefonu przy alarmie niskiego zdrowia (na komputerze bez skutku). */
    private boolean vibrate = true;
    private boolean autoTarget;
    /** Joystick pod kciukiem: przeciagniecie po lewej czesci widoku gry tworzy joystick tam, gdzie jest palec. */
    private boolean floatingJoystick = true;
    private int joystickSize = 100;
    /** Strona panelu ustawien (nie zapisywana). */
    private int page;
    /** Wierszy ustawien na stronie panelu (ostatni wiersz strony przelacza strone). */
    public static final int PAGE_SIZE = 8;

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
    /** Jedzenie z plecaka samo, gdy sytosc konczy sie (mniej niz minuta). */
    public boolean autoEat() { return autoEat; }
    public void toggleAutoEat() { autoEat = !autoEat; }
    public boolean vibrate() { return vibrate; }
    public void toggleVibrate() { vibrate = !vibrate; }
    /** Cel sam: najblizszy potwor obok postaci, gdy nie ma celu (poza strefa ochronna). */
    public boolean autoTarget() { return autoTarget; }
    public void toggleAutoTarget() { autoTarget = !autoTarget; }
    public boolean floatingJoystick() { return floatingJoystick; }
    public void toggleFloatingJoystick() { floatingJoystick = !floatingJoystick; }
    public int joystickSize() { return joystickSize; }
    public float joystickScale() { return joystickSize / 100f; }

    public void cycleJoystickSize() {
        int i = 0;
        while (i < JOY_SIZE_CHOICES.length && JOY_SIZE_CHOICES[i] != joystickSize) i++;
        joystickSize = JOY_SIZE_CHOICES[(i + 1) % JOY_SIZE_CHOICES.length];
    }

    public int page() { return page; }
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
            case "settings:eat": toggleAutoEat(); return true;
            case "settings:vibrate": toggleVibrate(); return true;
            case "settings:target": toggleAutoTarget(); return true;
            case "settings:joyfloat": toggleFloatingJoystick(); return true;
            case "settings:joysize": cycleJoystickSize(); return true;
            case "settings:page": page = (page + 1) % pages(); return true;
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
        m.put("eat", String.valueOf(autoEat));
        m.put("vibrate", String.valueOf(vibrate));
        m.put("target", String.valueOf(autoTarget));
        m.put("joyfloat", String.valueOf(floatingJoystick));
        m.put("joysize", String.valueOf(joystickSize));
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
        s.autoEat = bool(m.get("eat"), false);
        s.vibrate = bool(m.get("vibrate"), true);
        s.autoTarget = bool(m.get("target"), false);
        s.floatingJoystick = bool(m.get("joyfloat"), true);
        s.joystickSize = choice(m.get("joysize"), JOY_SIZE_CHOICES, 100);
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

    public int pages() { return (allRows().length + PAGE_SIZE - 1) / PAGE_SIZE; }

    /** Wiersze biezacej strony panelu i na koncu wiersz zmiany strony (gdy stron jest wiecej niz jedna). */
    public String[][] rows() {
        String[][] all = allRows();
        if (all.length <= PAGE_SIZE + 1) return all;
        int from = page * PAGE_SIZE, to = Math.min(all.length, from + PAGE_SIZE);
        String[][] out = new String[to - from + 1][];
        System.arraycopy(all, from, out, 0, to - from);
        out[to - from] = new String[] {"settings:page", "Więcej ustawień (strona " + (page + 1) + " z " + pages() + ")"};
        return out;
    }

    /** Wszystkie ustawienia: akcja i opis. */
    public String[][] allRows() {
        return new String[][] {
            {"settings:fps", "Limit FPS: " + fpsCap + (batterySaver ? " (bateria: " + effectiveFpsCap() + ")" : "")},
            {"settings:battery", "Oszczędzanie baterii: " + (batterySaver ? "włączone" : "wyłączone")},
            {"settings:light", "Światło: " + (highLighting ? "wysoka jakość" : "niska jakość")},
            {"settings:numbers", "Liczby obrażeń: " + (damageNumbers ? "pokazuj" : "ukryj")},
            {"settings:sound", "Dźwięk: " + (volume == 0 ? "wyłączony" : volume + "%")},
            {"settings:loot", "Łupy z ciał: " + (autoLoot ? "automatycznie" : "dotknięciem")},
            {"settings:weather", "Pogoda: " + (weather ? "pokazuj" : "ukryj")},
            {"settings:heal", "Leczenie samo: " + (autoHeal == 0 ? "wyłączone" : "poniżej " + autoHeal + "% HP")},
            {"settings:mana", "Mikstura many sama: " + (autoMana == 0 ? "wyłączona" : "poniżej " + autoMana + "% MP")},
            {"settings:eat", "Jedzenie samo: " + (autoEat ? "gdy kończy się sytość" : "wyłączone")},
            {"settings:target", "Cel sam: " + (autoTarget ? "najbliższy potwór obok" : "wyłączony")},
            {"settings:vibrate", "Wibracje: " + (vibrate ? "przy niskim zdrowiu" : "wyłączone")},
            {"settings:joyfloat", "Joystick: " + (floatingJoystick ? "pod kciukiem (przeciągnij po mapie)" : "tylko w rogu ekranu")},
            {"settings:joysize", "Rozmiar joysticka: " + joystickSize + "%"},
        };
    }
}
