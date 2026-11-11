package pl.fawizja.pixel.settings;

import java.util.Arrays;
import java.util.Set;

/**
 * Wlasny uklad paska akcji (10 pol jak w Tibii na telefonie). Kazde pole trzyma akcje:
 * "cast:klucz" (czar), "item:klucz" (mikstura albo runa z plecaka), "potion_hp"/"potion_mp"
 * (najsilniejsza mikstura) albo {@link #NONE} (pole celowo puste). Pole bez wpisu dziala automatycznie.
 * Przypisany czar, ktorego postac nie zna (np. po zmianie postaci), wraca do automatu.
 * Zapis jako "cast:mend,-,item:sudden_death,..." (od zapisu postaci v9 w postaci, wczesniej w Preferences klienta);
 * stary zapis samych kluczy czarow ("mend,-,-,-") jest czytany jako czary.
 *
 * <p>Jak paski akcji Tibii 12+: runa ma tryb uzycia - "item:klucz" na cel (domyslnie), "item:klucz:self" na siebie,
 * "item:klucz:aim" z celownikiem (nastepne dotkniecie pola mapy). Combo "combo:a+b+c" (do {@link #COMBO_MAX} czarow)
 * rzuca przy kazdym nacisnieciu pierwszy czar gotowy do rzucenia ({@code SpellSystem.castCombo}).
 */
public final class SpellBar {

    public static final int SLOTS = 10;
    /** Pole wyczyszczone przez gracza: nie bierze wyboru automatycznego. */
    public static final String NONE = "none";

    private static final String KEY = "[a-z0-9_]{1,40}";
    /** Najwiecej czarow w jednym polu combo. */
    public static final int COMBO_MAX = 4;
    /** Tryby uzycia runy z paska (jak "Use on target / yourself / with crosshair" w Tibii). */
    public static final String MODE_TARGET = "target", MODE_SELF = "self", MODE_AIM = "aim";

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
        return e.equals(NONE) || e.equals("potion_hp") || e.equals("potion_mp") || e.matches("(cast|item):" + KEY)
                || e.matches("item:" + KEY + ":(self|aim)") || e.matches("combo:" + KEY + "(\\+" + KEY + "){0," + (COMBO_MAX - 1) + "}");
    }

    /** Tryb runy w polu: {@link #MODE_TARGET} (tez dla wpisow bez trybu), {@link #MODE_SELF} albo {@link #MODE_AIM}. */
    public static String mode(String entry) {
        if (entry == null || !entry.startsWith("item:")) return MODE_TARGET;
        if (entry.endsWith(":self")) return MODE_SELF;
        if (entry.endsWith(":aim")) return MODE_AIM;
        return MODE_TARGET;
    }

    /** Wpis "item:klucz" bez trybu (klucz przedmiotu) albo sam wpis, gdy to nie przedmiot. */
    public static String base(String entry) {
        if (entry == null || !entry.startsWith("item:")) return entry;
        int c = entry.indexOf(':', 5);
        return c < 0 ? entry : entry.substring(0, c);
    }

    /** Ten sam przedmiot z innym trybem uzycia (cel = bez dopisku). */
    public static String withMode(String entry, String mode) {
        String b = base(entry);
        if (b == null || !b.startsWith("item:")) return entry;
        return MODE_SELF.equals(mode) || MODE_AIM.equals(mode) ? b + ":" + mode : b;
    }

    /** Klucze czarow pola combo (pusta lista dla innych wpisow). */
    public static java.util.List<String> comboSpells(String entry) {
        if (entry == null || !entry.startsWith("combo:")) return java.util.Collections.emptyList();
        return java.util.Arrays.asList(entry.substring(6).split("\\+"));
    }

    /** Wpis combo z listy czarow (bez powtorzen, najwyzej {@link #COMBO_MAX}); null dla pustej listy. */
    public static String combo(java.util.List<String> keys) {
        java.util.LinkedHashSet<String> u = new java.util.LinkedHashSet<>();
        for (String k : keys) if (k != null && k.matches(KEY) && u.size() < COMBO_MAX) u.add(k);
        return u.isEmpty() ? null : "combo:" + String.join("+", u);
    }

    /**
     * Ostateczny pasek: wpis gracza (czar tylko, gdy postac go zna), inaczej wybor automatyczny.
     * Puste pole ({@link #NONE} albo brak automatu) daje null.
     */
    public String[] resolve(String[] auto, Set<String> knownSpells) {
        String[] out = new String[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            String c = custom[i];
            if (c != null && c.startsWith("combo:")) {
                // combo: tylko czary znane postaci; bez zadnego - automat
                java.util.List<String> keep = new java.util.ArrayList<>();
                for (String k : comboSpells(c)) if (knownSpells.contains(k)) keep.add(k);
                c = combo(keep);
            }
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
