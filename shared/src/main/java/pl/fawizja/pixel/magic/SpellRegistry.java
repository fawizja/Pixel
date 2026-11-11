package pl.fawizja.pixel.magic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import pl.fawizja.pixel.combat.DamageType;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.vocation.Vocation;

/** Rejestr czarow (data/spells.csv). Sprawdza, ze przedmioty tworzone/zuzywane istnieja. */
public final class SpellRegistry {

    private final Map<String, SpellType> byKey;
    /** Inkantacje (data/incantations.csv): znormalizowane slowa -> klucz czaru. */
    private final Map<String, String> byWords = new LinkedHashMap<>();
    private final Map<String, String> wordsOf = new LinkedHashMap<>();

    private SpellRegistry(Map<String, SpellType> byKey) {
        this.byKey = Collections.unmodifiableMap(byKey);
    }

    public static SpellRegistry loadDefault(ItemRegistry items) {
        try {
            SpellRegistry r = parse(readLines("/data/spells.csv"), items);
            r.addIncantations(readLines("/data/incantations.csv"));
            return r;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read spell data", e);
        }
    }

    public static SpellRegistry parse(List<String> lines, ItemRegistry items) throws IOException {
        Map<String, SpellType> map = new LinkedHashMap<>();
        int n = 0;
        for (String line : lines) {
            n++;
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] f = t.split(";", 20);
            if (f.length != 20) throw new IOException("spells.csv line " + n + ": expected 20 fields, got " + f.length);
            // opcjonalne pole na koncu: rola wobec harmonii mnicha ";B" / ";S" (opis moze miec srednik w srodku)
            String role = "";
            java.util.regex.Matcher rm = java.util.regex.Pattern.compile("^(.*);\\s*([BS-])\\s*$").matcher(f[19]);
            if (rm.matches()) {
                f[19] = rm.group(1);
                role = rm.group(2);
            }
            try {
                String produces = emptyToNull(f[16]);
                String consumes = emptyToNull(f[17]);
                if (produces != null) items.byKey(produces);
                if (consumes != null) items.byKey(consumes);
                SpellKind kind = SpellKind.valueOf(f[8].trim());
                SpellType s = new SpellType(f[0].trim(), f[1].trim(), vocations(f[2].trim()), i(f[3]), i(f[4]), i(f[5]), i(f[6]),
                        SpellGroup.valueOf(f[7].trim()), kind, DamageType.valueOf(f[9].trim()), d(f[10]), d(f[11]),
                        d(f[12]), d(f[13]), i(f[14]), i(f[15]), produces, consumes, i(f[18]), f[19].trim(),
                        MonkRole.parse(role));
                if (kind.isRune()) items.byKey(s.key()); // efekt runy nosi klucz przedmiotu runy
                if (map.put(s.key(), s) != null) throw new IOException("duplicate spell " + s.key());
            } catch (IllegalArgumentException e) {
                throw new IOException("spells.csv line " + n + ": " + e.getMessage(), e);
            }
        }
        return new SpellRegistry(map);
    }

    /** Wczytuje inkantacje "czar;slowa"; czar musi istniec i nie byc efektem runy, slowa nie moga sie powtarzac. */
    public void addIncantations(List<String> lines) throws IOException {
        int n = 0;
        for (String line : lines) {
            n++;
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] f = t.split(";");
            if (f.length != 2) throw new IOException("incantations.csv line " + n + ": expected 2 fields");
            SpellType s = byKey.get(f[0].trim());
            if (s == null || s.kind().isRune()) throw new IOException("incantations.csv line " + n + ": no spell " + f[0]);
            String w = normalizeWords(f[1]);
            if (w.isEmpty() || byWords.put(w, s.key()) != null || wordsOf.put(s.key(), w) != null) {
                throw new IOException("incantations.csv line " + n + ": duplicate " + t);
            }
        }
    }

    /** Klucz czaru dla wypowiedzianych slow (np. "Utani  Gran Hur") albo null, gdy to nie inkantacja. */
    public String byWords(String said) {
        if (said == null) return null;
        String exact = byWords.get(normalizeWords(said));
        if (exact != null) return exact;
        Incantation i = incantation(said);
        return i == null ? null : i.key();
    }

    /** Inkantacja z parametrem: klucz czaru i dopisek (np. utevo res "wilk" -> summon_creature, "wilk"). */
    public record Incantation(String key, String param) {}

    /** Czary przyjmujace dopisek po slowach (imie, nazwa stworzenia). */
    public static boolean takesParam(SpellType s) {
        return s.kind() == SpellKind.SUMMON || s.kind() == SpellKind.ILLUSION || s.kind() == SpellKind.HEAL_OTHER;
    }

    /** Rozpoznaje inkantacje, takze z dopiskiem w cudzyslowie albo bez; null, gdy to nie czar. */
    public Incantation incantation(String said) {
        if (said == null) return null;
        String n = normalizeWords(said);
        String exact = byWords.get(n);
        if (exact != null) return new Incantation(exact, null);
        String best = null;
        for (Map.Entry<String, String> e : byWords.entrySet()) {
            String w = e.getKey();
            if (!n.startsWith(w + " ") && !n.startsWith(w + "\"")) continue;
            SpellType s = byKey.get(e.getValue());
            if (s == null || !takesParam(s)) continue;
            if (best == null || w.length() > best.length()) best = w;
        }
        if (best == null) return null;
        String param = n.substring(best.length()).trim().replace("\"", "").trim();
        return new Incantation(byWords.get(best), param.isEmpty() ? null : param);
    }

    /**
     * Stare klucze czarow sprzed 0.240 (zapisane paski czarow): czar z Tibii, ktory je zastapil.
     */
    private static final Map<String, String> LEGACY = Map.ofEntries(
            Map.entry("mend", "light_healing"), Map.entry("greater_mend", "intense_healing"),
            Map.entry("vital_surge", "ultimate_healing"), Map.entry("ultimate_mend", "restoration"),
            Map.entry("holy_mend", "salvation"), Map.entry("inner_calm", "light_healing"),
            Map.entry("deep_meditation", "intense_healing"), Map.entry("iron_will", "fair_wound_cleansing"),
            Map.entry("ember_bolt", "flame_strike"), Map.entry("spark_bolt", "energy_strike"),
            Map.entry("flame_ring", "great_fire_wave"), Map.entry("inferno_bolt", "strong_flame_strike"),
            Map.entry("storm_ring", "energy_wave"), Map.entry("meteor_bolt", "ultimate_flame_strike"),
            Map.entry("energy_strike_max", "ultimate_energy_strike"), Map.entry("rage_of_skies", "rage_of_the_skies"),
            Map.entry("frost_bolt", "ice_strike"), Map.entry("thorn_burst", "terra_strike"),
            Map.entry("frost_nova", "strong_ice_wave"), Map.entry("glacier_spike", "strong_ice_strike"),
            Map.entry("earth_quake", "terra_wave"), Map.entry("blizzard_spike", "ultimate_ice_strike"),
            Map.entry("earth_strike_max", "ultimate_terra_strike"), Map.entry("holy_bolt", "divine_missile"),
            Map.entry("sacred_nova", "divine_caldera"), Map.entry("piercing_throw", "ethereal_spear"),
            Map.entry("divine_spear", "holy_flash"), Map.entry("divine_wrath", "divine_dazzle"),
            Map.entry("light_blade", "divine_missile"), Map.entry("divine_judgement", "sharpshooter"),
            Map.entry("blade_storm", "front_sweep"), Map.entry("glow", "light"));

    /** Aktualny klucz dla starego (albo ten sam). */
    public static String current(String key) { return key == null ? null : LEGACY.getOrDefault(key, key); }

    /** Slowa czaru albo null, gdy czar nie ma inkantacji. */
    public String words(String key) { return wordsOf.get(current(key)); }

    static String normalizeWords(String s) {
        return s.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    }

    public SpellType get(String key) {
        SpellType s = byKey.get(current(key));
        if (s == null) throw new IllegalArgumentException("Unknown spell " + key);
        return s;
    }

    public SpellType find(String key) { return key == null ? null : byKey.get(current(key)); }

    public Collection<SpellType> all() { return byKey.values(); }

    /** Czary, ktore postac moze rzucac teraz lub pozniej (bez efektow run), w kolejnosci poziomu. */
    public List<SpellType> forVocation(Vocation v) {
        List<SpellType> out = new ArrayList<>();
        for (SpellType s : byKey.values()) if (!s.kind().isRune() && s.allowedFor(v)) out.add(s);
        out.sort((a, b) -> a.minLevel() != b.minLevel() ? Integer.compare(a.minLevel(), b.minLevel()) : a.key().compareTo(b.key()));
        return out;
    }

    private static Set<Vocation> vocations(String s) {
        if (s.equals("*") || s.isEmpty()) return EnumSet.allOf(Vocation.class);
        Set<Vocation> out = EnumSet.noneOf(Vocation.class);
        for (String p : s.split(",")) out.add(Vocation.valueOf(p.trim()));
        return out;
    }

    private static String emptyToNull(String s) { return s.trim().isEmpty() ? null : s.trim(); }
    private static int i(String s) { return Integer.parseInt(s.trim()); }
    private static double d(String s) { return Double.parseDouble(s.trim()); }

    public static List<String> readLines(String path) throws IOException {
        List<String> lines = new ArrayList<>();
        try (InputStream in = SpellRegistry.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Missing resource " + path);
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String l;
            while ((l = br.readLine()) != null) lines.add(l);
        }
        return lines;
    }
}
