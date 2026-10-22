package pl.fawizja.pixel.house;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.fawizja.pixel.magic.SpellRegistry;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Domy z /maps/&lt;mapa&gt;/houses.csv (tworzy go generator miast):
 * id;nazwa;miasto;x0;y0;x1;y1;z;drzwi x;drzwi y;cena. Wnetrze lezy w strefie ochronnej, drzwi sa przechodnie.
 */
public final class HouseRegistry {
    private final Map<String, House> byId;

    private HouseRegistry(Map<String, House> byId) { this.byId = Collections.unmodifiableMap(byId); }

    public static HouseRegistry empty() { return new HouseRegistry(new LinkedHashMap<>()); }

    public static HouseRegistry load(String mapName, WorldMap world) throws IOException {
        List<String> lines;
        try {
            lines = SpellRegistry.readLines("/maps/" + mapName + "/houses.csv");
        } catch (IOException e) {
            return empty();                                     // mapy bez domow (np. testowe)
        }
        return parse(lines, world);
    }

    public static HouseRegistry parse(List<String> lines, WorldMap world) throws IOException {
        Map<String, House> map = new LinkedHashMap<>();
        int n = 0;
        for (String line : lines) {
            n++;
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] f = t.split(";");
            if (f.length != 11) throw new IOException("houses.csv line " + n + ": expected 11 fields");
            try {
                int z = Integer.parseInt(f[7].trim());
                House h = new House(f[0].trim(), f[1].trim(), f[2].trim(), Integer.parseInt(f[3].trim()),
                        Integer.parseInt(f[4].trim()), Integer.parseInt(f[5].trim()), Integer.parseInt(f[6].trim()), z,
                        new Position(Integer.parseInt(f[8].trim()), Integer.parseInt(f[9].trim()), z), Long.parseLong(f[10].trim()));
                if (world.town(h.town()) == null) throw new IllegalArgumentException("unknown town " + h.town());
                if (!world.isWalkable(h.door())) throw new IllegalArgumentException("door not walkable " + h.door());
                if (h.contains(h.door())) throw new IllegalArgumentException("door inside " + h.id());
                for (House o : map.values()) {
                    if (o.z() == h.z() && o.x0() <= h.x1() && h.x0() <= o.x1() && o.y0() <= h.y1() && h.y0() <= o.y1()) {
                        throw new IllegalArgumentException(h.id() + " overlaps " + o.id());
                    }
                }
                if (map.put(h.id(), h) != null) throw new IllegalArgumentException("duplicate house " + h.id());
            } catch (IllegalArgumentException e) {
                throw new IOException("houses.csv line " + n + ": " + e.getMessage(), e);
            }
        }
        return new HouseRegistry(map);
    }

    public House get(String id) { return id == null ? null : byId.get(id); }

    public List<House> all() { return new ArrayList<>(byId.values()); }

    public List<House> inTown(String town) {
        List<House> out = new ArrayList<>();
        for (House h : byId.values()) if (h.town().equalsIgnoreCase(town)) out.add(h);
        return out;
    }

    /** Dom, ktorego wnetrze obejmuje pole (albo null). */
    public House at(Position p) {
        for (House h : byId.values()) if (h.contains(p)) return h;
        return null;
    }
}
