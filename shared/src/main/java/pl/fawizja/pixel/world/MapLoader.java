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
        readDisplays(base + "displays.csv", world);
        readLevelDoors(base + "level_doors.csv", world);
        readAltarQuests(base + "altar_quests.csv", world);
        readBossQuests(base + "boss_quests.csv", world);
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

    /** Towary na ladach sklepow (plik opcjonalny): x;y;z;przedmiot, tylko na polu z lada albo gablota. */
    private void readDisplays(String path, WorldMap world) throws IOException {
        if (MapLoader.class.getResource(path) == null) return;
        for (String line : readLines(path)) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] f = t.split(";");
            if (f.length != 4) throw new IOException(path + ": expected 4 fields in '" + line + "'");
            Position p = new Position(i(f[0]), i(f[1]), i(f[2]));
            FloorMap floor = world.floor(p.z());
            if (floor == null || !floor.inBounds(p.x(), p.y()) || floor.get(Layer.OBJECT, p.x(), p.y()) == 0) {
                throw new IOException(path + ": display needs a counter at " + p);
            }
            world.addDisplay(p, f[3].trim());
        }
    }

    /** Drzwi poziomu (plik opcjonalny): x;y;z;minimalny poziom - tylko na polu z drzwiami. */
    private void readLevelDoors(String path, WorldMap world) throws IOException {
        if (MapLoader.class.getResource(path) == null) return;
        for (String line : readLines(path)) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] f = t.split(";");
            if (f.length != 4) throw new IOException(path + ": expected 4 fields in '" + line + "'");
            Position p = new Position(i(f[0]), i(f[1]), i(f[2]));
            if (!world.openings().isDoor(p)) throw new IOException(path + ": level door needs a door at " + p);
            world.addLevelDoor(p, i(f[3]));
        }
    }

    /**
     * Zadania z oltarzami profesji (plik opcjonalny). Wiersze:
     * quest;id;nazwa;zloto;dzwignia x,y,z;sala x0,y0,x1,y1,z;skarbiec x0,y0,x1,y1,z
     * altar;id;PROFESJA;oltarz x,y,z;pole x,y,z;pochodnia x,y,z;przedmiot;cel x,y,z;skrzynia x,y,z
     * Pochodnie zadan sa domyslnie zgaszone.
     */
    private void readAltarQuests(String path, WorldMap world) throws IOException {
        if (MapLoader.class.getResource(path) == null) return;
        Map<String, AltarQuest> byId = new HashMap<>();
        for (String line : readLines(path)) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] f = t.split(";");
            try {
                if (f[0].equals("quest") && f.length == 7) {
                    AltarQuest q = new AltarQuest(f[1].trim(), f[2].trim(), Long.parseLong(f[3].trim()), pos(f[4]),
                            ints(f[5], 5), ints(f[6], 5));
                    if (world.floor(q.lever().z()) == null || world.floor(q.lever().z()).get(Layer.OBJECT, q.lever().x(), q.lever().y()) == 0) {
                        throw new IOException(path + ": lever needs an object at " + q.lever());
                    }
                    byId.put(q.id(), q);
                    world.addAltarQuest(q);
                } else if (f[0].equals("altar") && f.length == 9) {
                    AltarQuest q = byId.get(f[1].trim());
                    if (q == null) throw new IOException(path + ": altar before its quest: " + line);
                    AltarQuest.Slot s = new AltarQuest.Slot(pl.fawizja.pixel.vocation.Vocation.valueOf(f[2].trim()),
                            pos(f[3]), pos(f[4]), pos(f[5]), f[6].trim(), pos(f[7]), pos(f[8]));
                    if (!world.wallLights().isWallLight(s.torch())) throw new IOException(path + ": no wall torch at " + s.torch());
                    if (!world.isWalkable(s.stand()) || !world.isWalkable(s.target())) {
                        throw new IOException(path + ": altar square or target not walkable: " + line);
                    }
                    q.addSlot(s);
                    world.wallLights().setDefaultLit(s.torch(), false);
                } else {
                    throw new IOException(path + ": bad line '" + line + "'");
                }
            } catch (IllegalArgumentException e) {
                throw new IOException(path + ": " + e.getMessage() + " in '" + line + "'", e);
            }
        }
    }

    /**
     * Wyprawy na bossa w piec postaci (plik opcjonalny). Wiersze:
     * quest;id;nazwa;poziom;boss;dzwignia x,y,z;przedsionek x0,y0,x1,y1,z;arena x0,y0,x1,y1,z;boss x,y,z;
     * portal x,y,z;cel wyjscia x,y,z;drzwi poziomu x,y,z
     * square;id;pole x,y,z;cel na arenie x,y,z
     */
    private void readBossQuests(String path, WorldMap world) throws IOException {
        if (MapLoader.class.getResource(path) == null) return;
        Map<String, BossQuest> byId = new HashMap<>();
        for (String line : readLines(path)) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] f = t.split(";");
            try {
                if (f[0].equals("quest") && f.length == 12) {
                    BossQuest q = new BossQuest(f[1].trim(), f[2].trim(), i(f[3]), f[4].trim(), pos(f[5]), ints(f[6], 5),
                            ints(f[7], 5), pos(f[8]), pos(f[9]), pos(f[10]), pos(f[11]));
                    FloorMap fl = world.floor(q.lever().z());
                    if (fl == null || fl.get(Layer.OBJECT, q.lever().x(), q.lever().y()) == 0) {
                        throw new IOException(path + ": lever needs an object at " + q.lever());
                    }
                    if (!world.isWalkable(q.bossSpawn()) || !world.isWalkable(q.exitPortal()) || !world.isWalkable(q.exitTarget())) {
                        throw new IOException(path + ": boss, portal or exit not walkable: " + line);
                    }
                    if (byId.put(q.id(), q) != null) throw new IOException(path + ": duplicate quest " + q.id());
                    world.addBossQuest(q);
                } else if (f[0].equals("square") && f.length == 4) {
                    BossQuest q = byId.get(f[1].trim());
                    if (q == null) throw new IOException(path + ": square before its quest: " + line);
                    BossQuest.Square s = new BossQuest.Square(pos(f[2]), pos(f[3]));
                    if (!world.isWalkable(s.stand()) || !world.isWalkable(s.target())) {
                        throw new IOException(path + ": square or target not walkable: " + line);
                    }
                    q.addSquare(s);
                } else {
                    throw new IOException(path + ": bad line '" + line + "'");
                }
            } catch (IllegalArgumentException e) {
                throw new IOException(path + ": " + e.getMessage() + " in '" + line + "'", e);
            }
        }
        for (BossQuest q : byId.values()) {
            if (q.squares().size() != BossQuest.PARTY) throw new IOException(path + ": " + q.id() + " needs " + BossQuest.PARTY + " squares");
        }
    }

    private static Position pos(String s) {
        int[] v = ints(s, 3);
        return new Position(v[0], v[1], v[2]);
    }

    private static int[] ints(String s, int n) {
        String[] f = s.trim().split(",");
        if (f.length != n) throw new IllegalArgumentException("expected " + n + " numbers: " + s);
        int[] out = new int[n];
        for (int k = 0; k < n; k++) out[k] = i(f[k]);
        return out;
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
