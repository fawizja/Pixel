package pl.fawizja.pixel.achievement;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.fawizja.pixel.monster.MonsterType;

/**
 * Dane bestiariusza z data/bestiary.csv (klucz potwora -> {@link BestiaryEntry}), wczytywane raz przy pierwszym uzyciu.
 * Bossowie nie maja wpisu (w Tibii sa w osobnym Bosstiary), wiec nie maja etapow ani charmow.
 */
public final class BestiaryData {

    private static volatile Map<String, BestiaryEntry> entries;

    private BestiaryData() {}

    /** Wpis potwora albo null (boss, potwor bez wpisu). */
    public static BestiaryEntry get(String key) {
        return key == null ? null : all().get(key);
    }

    /** Wpis typu potwora (elita ma ten sam klucz co zwykly) albo null dla bossa. */
    public static BestiaryEntry of(MonsterType t) {
        return t == null || t.boss() ? null : get(t.key());
    }

    public static Map<String, BestiaryEntry> all() {
        Map<String, BestiaryEntry> e = entries;
        if (e == null) {
            synchronized (BestiaryData.class) {
                if (entries == null) entries = Collections.unmodifiableMap(load());
                e = entries;
            }
        }
        return e;
    }

    private static Map<String, BestiaryEntry> load() {
        try (InputStream in = BestiaryData.class.getResourceAsStream("/data/bestiary.csv")) {
            if (in == null) throw new IOException("Missing resource /data/bestiary.csv");
            List<String> lines = new ArrayList<>();
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String l;
            while ((l = br.readLine()) != null) lines.add(l);
            return parse(lines);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read bestiary data", e);
        }
    }

    /** "klucz;odpowiednik;gwiazdki;wystepowanie" (komentarze #). */
    public static Map<String, BestiaryEntry> parse(List<String> lines) {
        Map<String, BestiaryEntry> out = new LinkedHashMap<>();
        int n = 0;
        for (String line : lines) {
            n++;
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] f = t.split(";");
            if (f.length != 4) throw new IllegalArgumentException("bestiary.csv line " + n + ": expected 4 fields");
            String key = f[0].trim();
            if (out.containsKey(key)) throw new IllegalArgumentException("bestiary.csv line " + n + ": duplicate " + key);
            out.put(key, new BestiaryEntry(key, f[1].trim(), BestiaryDifficulty.ofStars(Integer.parseInt(f[2].trim())),
                    Integer.parseInt(f[3].trim())));
        }
        return out;
    }
}
