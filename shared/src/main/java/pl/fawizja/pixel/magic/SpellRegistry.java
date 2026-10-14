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

    private SpellRegistry(Map<String, SpellType> byKey) {
        this.byKey = Collections.unmodifiableMap(byKey);
    }

    public static SpellRegistry loadDefault(ItemRegistry items) {
        try {
            return parse(readLines("/data/spells.csv"), items);
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
            try {
                String produces = emptyToNull(f[16]);
                String consumes = emptyToNull(f[17]);
                if (produces != null) items.byKey(produces);
                if (consumes != null) items.byKey(consumes);
                SpellKind kind = SpellKind.valueOf(f[8].trim());
                SpellType s = new SpellType(f[0].trim(), f[1].trim(), vocations(f[2].trim()), i(f[3]), i(f[4]), i(f[5]), i(f[6]),
                        SpellGroup.valueOf(f[7].trim()), kind, DamageType.valueOf(f[9].trim()), d(f[10]), d(f[11]),
                        d(f[12]), d(f[13]), i(f[14]), i(f[15]), produces, consumes, i(f[18]), f[19].trim());
                if (kind.isRune()) items.byKey(s.key()); // efekt runy nosi klucz przedmiotu runy
                if (map.put(s.key(), s) != null) throw new IOException("duplicate spell " + s.key());
            } catch (IllegalArgumentException e) {
                throw new IOException("spells.csv line " + n + ": " + e.getMessage(), e);
            }
        }
        return new SpellRegistry(map);
    }

    public SpellType get(String key) {
        SpellType s = byKey.get(key);
        if (s == null) throw new IllegalArgumentException("Unknown spell " + key);
        return s;
    }

    public SpellType find(String key) { return byKey.get(key); }

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

    static List<String> readLines(String path) throws IOException {
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
