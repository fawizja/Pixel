package pl.fawizja.pixel.settings;

import java.util.Arrays;
import java.util.Set;

/**
 * Wlasny uklad paska czarow: kazdy z 4 przyciskow moze miec przypisany czar, a pusty przycisk
 * dziala automatycznie (najlepsze leczenie, atak, obszar, poswiata). Przypisanie nieznanego juz czaru
 * (np. po zmianie postaci) wraca do automatu. Zapis jako "klucz,klucz,-,-" (na kliencie w Preferences).
 */
public final class SpellBar {

    public static final int SLOTS = 4;

    private final String[] custom = new String[SLOTS];

    public String custom(int slot) { return slot >= 0 && slot < SLOTS ? custom[slot] : null; }

    /** Przypisuje czar do przycisku; ponowne przypisanie tego samego czaru przywraca automat. */
    public void assign(int slot, String spellKey) {
        if (slot < 0 || slot >= SLOTS) return;
        custom[slot] = spellKey == null || spellKey.equals(custom[slot]) ? null : spellKey;
    }

    public boolean isCustom() {
        for (String s : custom) if (s != null) return true;
        return false;
    }

    /** Ostateczny pasek: przypisany czar, jesli postac go zna, w przeciwnym razie wybor automatyczny. */
    public String[] resolve(String[] auto, Set<String> known) {
        String[] out = new String[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            out[i] = custom[i] != null && known.contains(custom[i]) ? custom[i] : (auto != null && i < auto.length ? auto[i] : null);
        }
        return out;
    }

    public String encode() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < SLOTS; i++) {
            if (i > 0) b.append(',');
            b.append(custom[i] == null ? "-" : custom[i]);
        }
        return b.toString();
    }

    public static SpellBar decode(String s) {
        SpellBar bar = new SpellBar();
        if (s == null) return bar;
        String[] p = s.split(",", -1);
        for (int i = 0; i < Math.min(SLOTS, p.length); i++) {
            String k = p[i].trim();
            if (!k.isEmpty() && !k.equals("-") && k.matches("[a-z0-9_]{1,40}")) bar.custom[i] = k;
        }
        return bar;
    }

    @Override
    public String toString() { return Arrays.toString(custom); }
}
