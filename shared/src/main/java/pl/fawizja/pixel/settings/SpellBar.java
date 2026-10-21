package pl.fawizja.pixel.settings;

import java.util.Arrays;
import java.util.Set;

/**
 * Wlasny uklad paska akcji (10 pol jak w Tibii na telefonie). Kazde pole trzyma akcje:
 * "cast:klucz" (czar), "item:klucz" (mikstura albo runa z plecaka), "potion_hp"/"potion_mp"
 * (najsilniejsza mikstura) albo {@link #NONE} (pole celowo puste). Pole bez wpisu dziala automatycznie.
 * Przypisany czar, ktorego postac nie zna (np. po zmianie postaci), wraca do automatu.
 * Zapis jako "cast:mend,-,item:sudden_death,..." (na kliencie w Preferences); stary zapis
 * samych kluczy czarow ("mend,-,-,-") jest czytany jako czary.
 */
public final class SpellBar {

    public static final int SLOTS = 10;
    /** Pole wyczyszczone przez gracza: nie bierze wyboru automatycznego. */
    public static final String NONE = "none";

    private static final String KEY = "[a-z0-9_]{1,40}";

    private final String[] custom = new String[SLOTS];

    public String custom(int slot) { return slot >= 0 && slot < SLOTS ? custom[slot] : null; }

    /** Przypisuje akcje do pola; ponowne przypisanie tej samej akcji (albo null) przywraca automat. */
    public void assign(int slot, String entry) {
        if (slot < 0 || slot >= SLOTS) return;
        custom[slot] = entry == null || entry.equals(custom[slot]) || !valid(entry) ? null : entry;
    }

    /** Ustawia akcje bez przelaczania (wybor z listy zawsze ustawia). */
    public void set(int slot, String entry) {
        if (slot < 0 || slot >= SLOTS) return;
        custom[slot] = entry == null || !valid(entry) ? null : entry;
    }

    public boolean isCustom() {
        for (String s : custom) if (s != null) return true;
        return false;
    }

    /** Czy tekst jest poprawna akcja pola (chroni przed smieciami w zapisie). */
    public static boolean valid(String e) {
        return e.equals(NONE) || e.equals("potion_hp") || e.equals("potion_mp") || e.matches("(cast|item):" + KEY);
    }

    /**
     * Ostateczny pasek: wpis gracza (czar tylko, gdy postac go zna), inaczej wybor automatyczny.
     * Puste pole ({@link #NONE} albo brak automatu) daje null.
     */
    public String[] resolve(String[] auto, Set<String> knownSpells) {
        String[] out = new String[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            String c = custom[i];
            boolean ok = c != null && (!c.startsWith("cast:") || knownSpells.contains(c.substring(5)));
            String e = ok ? c : (auto != null && i < auto.length ? auto[i] : null);
            out[i] = NONE.equals(e) ? null : e;
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
            if (k.isEmpty() || k.equals("-")) continue;
            if (valid(k)) bar.custom[i] = k;
            else if (k.matches(KEY)) bar.custom[i] = "cast:" + k; // stary zapis: sam klucz czaru
        }
        return bar;
    }

    @Override
    public String toString() { return Arrays.toString(custom); }
}
