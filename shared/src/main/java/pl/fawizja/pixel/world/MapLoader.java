package pl.fawizja.pixel.world;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Wczytuje swiat z zasobow /maps/&lt;nazwa&gt;/:
 * map.properties, legend.csv, floor_&lt;z&gt;.txt (np. floor_+1.txt, floor_0.txt, floor_-3.txt)
 * oraz transitions.csv. Mapy sa waliowane przy wczytaniu (nieznane znaki,
 * przejscia prowadzace w sciane lub na inne przejscie).
 */
public final class MapLoader {

    private final TileRegistry tiles;

    public MapLoader(TileRegistry tiles) {
        this.tiles = tiles;
    }

    private record LegendEntry(int ground, int decoration, int object, int wall) {}

    public WorldMap load(String mapName) throws IOException {
        String base = "/maps/" + mapName + "/";
        Properties props = new Properties();
        try (InputStream in = open(base + "map.properties")) {
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        int width = Integer.parseInt(props.getProperty("width").trim());
        int height = Integer.parseInt(props.getProperty("height").trim());
        WorldMap world = new WorldMap(props.getProperty("name", mapName).trim(), width, height, tiles);

        Map<Character, LegendEntry> legend = readLegend(base + "legend.csv");

        for (String zs : props.getProperty("floors").split(",")) {
            int z = Integer.parseInt(zs.trim());
            String file = base + "floor_" + (z > 0 ? "+" + z : String.valueOf(z)) + ".txt";
            FloorMap floor = world.getOrCreateFloor(z);
            List<String> lines = readLines(file);
            if (lines.size() > height) throw new IOException(file + ": " + lines.size() + " lines > height " + height);
            for (int y = 0; y < lines.size(); y++) {
                String row = lines.get(y);
                if (row.length() > width) throw new IOException(file + ":" + (y + 1) + " longer than width " + width);
                for (int x = 0; x < row.length(); x++) {
                    char c = row.charAt(x);
                    LegendEntry e = legend.get(c);
                    if (e == null) throw new IOException(file + ":" + (y + 1) + ":" + (x + 1) + " unknown map char '" + c + "'");
                    if (e.ground != 0) floor.set(Layer.GROUND, x, y, e.ground);
                    if (e.decoration != 0) floor.set(Layer.DECORATION, x, y, e.decoration);
                    if (e.object != 0) floor.set(Layer.OBJECT, x, y, e.object);
                    if (e.wall != 0) floor.set(Layer.WALL, x, y, e.wall);
                }
            }
        }

        String[] sp = props.getProperty("spawn").split(",");
        Position spawn = new Position(Integer.parseInt(sp[0].trim()), Integer.parseInt(sp[1].trim()), Integer.parseInt(sp[2].trim()));
        if (!world.isWalkable(spawn)) throw new IOException("spawn " + spawn + " is not walkable");
        world.setSpawn(spawn);

        String pz = props.getProperty("pz", "").trim();
        if (!pz.isEmpty()) {
            for (String rect : pz.split(";")) {
                String[] v = rect.trim().split(",");
                if (v.length != 5) throw new IOException("pz entry needs x0,y0,x1,y1,z: " + rect);
                world.addProtectionZone(Integer.parseInt(v[0].trim()), Integer.parseInt(v[1].trim()),
                        Integer.parseInt(v[2].trim()), Integer.parseInt(v[3].trim()), Integer.parseInt(v[4].trim()));
            }
        }
        if (!world.isProtectionZone(spawn)) throw new IOException("spawn must be inside a protection zone");

        String gates = props.getProperty("gates", "").trim();
        if (!gates.isEmpty()) {
            for (String rect : gates.split(";")) {
                String[] v = rect.trim().split(",");
                if (v.length != 6) throw new IOException("gate entry needs x0,y0,x1,y1,z,minLevel: " + rect);
                world.addLevelGate(i(v[0]), i(v[1]), i(v[2]), i(v[3]), i(v[4]), i(v[5]));
            }
        }

        String towns = props.getProperty("towns", "").trim();
        if (!towns.isEmpty()) {
            for (String entry : towns.split(";")) {
                String[] nv = entry.trim().split(":");
                if (nv.length != 2) throw new IOException("town entry needs name:x,y,z: " + entry);
                String[] v = nv[1].split(",");
                Position temple = new Position(Integer.parseInt(v[0].trim()), Integer.parseInt(v[1].trim()), Integer.parseInt(v[2].trim()));
                if (!world.isWalkable(temple) || !world.isProtectionZone(temple)) {
                    throw new IOException("temple of " + nv[0] + " must be walkable and protected: " + temple);
                }
                try {
                    world.addTown(new Town(nv[0].trim(), temple));
                } catch (IllegalArgumentException e) {
                    throw new IOException(e.getMessage(), e);
                }
            }
        }
        String regions = props.getProperty("regions", "").trim();
        if (!regions.isEmpty()) {
            for (String entry : regions.split("\\|")) {
                String[] f = entry.trim().split(";");
                if (f.length != 9) throw new IOException("region needs 9 fields: " + entry);
                try {
                    world.addRegion(new Region(f[0].trim(), i(f[1]), i(f[2]), i(f[3]), i(f[4]), i(f[5]), i(f[6]), i(f[7]), i(f[8])));
                } catch (IllegalArgumentException e) {
                    throw new IOException(e.getMessage(), e);
                }
            }
        }

        readTransitions(base + "transitions.csv", world);
        validateTransitions(world);
        return world;
    }

    private Map<Character, LegendEntry> readLegend(String path) throws IOException {
        Map<Character, LegendEntry> legend = new HashMap<>();
        for (String line : readLines(path)) {
            // w legendzie '#' jest symbolem sciany, wiec komentarze zaczynaja sie od "//"
            if (line.trim().isEmpty() || line.startsWith("//")) continue;
            String[] f = line.split(";", -1);
            if (f.length != 5) throw new IOException(path + ": expected 5 fields in '" + line + "'");
            String sym = f[0];
            char c = "space".equals(sym) ? ' ' : (sym.length() == 1 ? sym.charAt(0) : 0);
            if (c == 0) throw new IOException(path + ": bad symbol '" + sym + "'");
            legend.put(c, new LegendEntry(tileId(f[1]), tileId(f[2]), tileId(f[3]), tileId(f[4])));
        }
        return legend;
    }

    private int tileId(String key) {
        String k = key.trim();
        return k.isEmpty() || k.equals("-") ? 0 : tiles.byKey(k).id();
    }

    private void readTransitions(String path, WorldMap world) throws IOException {
        for (String line : readLines(path)) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] f = t.split(";");
            if (f.length != 7) throw new IOException(path + ": expected 7 fields in '" + line + "'");
            Position from = new Position(Integer.parseInt(f[0].trim()), Integer.parseInt(f[1].trim()), Integer.parseInt(f[2].trim()));
            Position to = new Position(Integer.parseInt(f[4].trim()), Integer.parseInt(f[5].trim()), Integer.parseInt(f[6].trim()));
            FloorMap floor = world.floor(from.z());
            if (floor == null) throw new IOException(path + ": no floor " + from.z());
            floor.addTransition(new Transition(from, TransitionType.valueOf(f[3].trim()), to));
        }
    }

    private void validateTransitions(WorldMap world) throws IOException {
        for (FloorMap f : world.floors().values()) {
            for (Transition t : f.transitions().values()) {
                if (!world.isWalkable(t.from())) throw new IOException("transition source not walkable: " + t);
                if (!world.isWalkable(t.to())) throw new IOException("transition target not walkable: " + t);
                Transition atTarget = world.transitionAt(t.to());
                if (atTarget != null && atTarget.type().triggeredByWalking()) {
                    throw new IOException("transition lands on another walk transition (loop): " + t);
                }
            }
        }
    }

    private static InputStream open(String path) throws IOException {
        InputStream in = MapLoader.class.getResourceAsStream(path);
        if (in == null) throw new IOException("Missing resource " + path);
        return in;
    }

    private static List<String> readLines(String path) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(open(path), StandardCharsets.UTF_8))) {
            String l;
            while ((l = br.readLine()) != null) lines.add(l);
        }
        return lines;
    }

    private static int i(String s) { return Integer.parseInt(s.trim()); }
}
