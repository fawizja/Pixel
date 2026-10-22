package pl.fawizja.pixel.world;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Rejestr typow kafelkow. Id 0 jest zarezerwowane jako "brak kafelka". */
public final class TileRegistry {

    public static final String DEFAULT_RESOURCE = "/data/tiles.csv";

    private final TileType[] byId;
    private final Map<String, TileType> byKey;

    private TileRegistry(List<TileType> types) {
        int max = 0;
        for (TileType t : types) max = Math.max(max, t.id());
        byId = new TileType[max + 1];
        Map<String, TileType> keys = new HashMap<>();
        for (TileType t : types) {
            if (t.id() <= 0 || t.id() > Short.MAX_VALUE) throw new IllegalArgumentException("bad tile id " + t.id());
            if (byId[t.id()] != null) throw new IllegalArgumentException("duplicate tile id " + t.id());
            if (keys.put(t.key(), t) != null) throw new IllegalArgumentException("duplicate tile key " + t.key());
            byId[t.id()] = t;
        }
        byKey = Collections.unmodifiableMap(keys);
    }

    public static TileRegistry loadDefault() {
        try (InputStream in = TileRegistry.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) throw new IllegalStateException("Missing resource " + DEFAULT_RESOURCE);
            return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + DEFAULT_RESOURCE, e);
        }
    }

    /** Format: id;key;layer;walkable(1/0);light;biome;color;pattern */
    public static TileRegistry parse(Reader reader) throws IOException {
        List<TileType> list = new ArrayList<>();
        BufferedReader br = new BufferedReader(reader);
        String line;
        int n = 0;
        while ((line = br.readLine()) != null) {
            n++;
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] f = t.split(";", -1);
            if (f.length != 8) throw new IOException("tiles.csv line " + n + ": expected 8 fields");
            try {
                list.add(new TileType(Integer.parseInt(f[0].trim()), f[1].trim(), Layer.valueOf(f[2].trim()),
                        "1".equals(f[3].trim()), Integer.parseInt(f[4].trim()), f[5].trim(), f[6].trim(), f[7].trim()));
            } catch (IllegalArgumentException e) {
                throw new IOException("tiles.csv line " + n + ": " + e.getMessage(), e);
            }
        }
        return new TileRegistry(list);
    }

    public TileType get(int id) {
        if (id <= 0 || id >= byId.length || byId[id] == null) return null;
        return byId[id];
    }

    public TileType byKey(String key) {
        TileType t = byKey.get(key);
        if (t == null) throw new IllegalArgumentException("Unknown tile " + key);
        return t;
    }

    /** Kafelek o kluczu albo null (np. opcjonalne grafiki w kliencie). */
    public TileType byKeyOrNull(String key) { return byKey.get(key); }

    public int maxId() { return byId.length - 1; }

    public List<TileType> all() {
        List<TileType> list = new ArrayList<>();
        for (TileType t : byId) if (t != null) list.add(t);
        return list;
    }
}
