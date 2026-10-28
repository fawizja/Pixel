package pl.fawizja.pixel.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Caly swiat: pietra od -5 do +5 o wspolnym ukladzie wspolrzednych. */
public final class WorldMap {

    private final String name;
    private final int width;
    private final int height;
    private final TileRegistry tiles;
    private final Map<Integer, FloorMap> floors = new TreeMap<>();
    private Position spawn;
    private final List<int[]> protectionZones = new ArrayList<>();
    private volatile java.util.Map<Integer, java.util.BitSet> pzCells;
    private final List<int[]> levelGates = new ArrayList<>();
    private final List<Town> towns = new ArrayList<>();
    private final List<Region> regions = new ArrayList<>();
    /** Towary wylozone na ladach sklepow: pole lady -> klucz przedmiotu (tylko grafika, nie do podniesienia). */
    private final Map<Position, String> displays = new java.util.HashMap<>();

    public WorldMap(String name, int width, int height, TileRegistry tiles) {
        this.name = name;
        this.width = width;
        this.height = height;
        this.tiles = tiles;
    }

    public String name() { return name; }
    public int width() { return width; }
    public int height() { return height; }
    public TileRegistry tiles() { return tiles; }

    private Openings openings;

    /** Drzwi i okna (otwarte / zamkniete), tworzone przy pierwszym uzyciu. */
    public synchronized Openings openings() {
        if (openings == null) openings = new Openings(this);
        return openings;
    }

    private Crates crates;

    /** Skrzynie, beczki i worki: przesuwanie i schowki, tworzone przy pierwszym uzyciu. */
    public synchronized Crates crates() {
        if (crates == null) crates = new Crates(this);
        return crates;
    }

    private WallLights wallLights;

    /** Pochodnie i latarnie na murach (zapalone / zgaszone), tworzone przy pierwszym uzyciu. */
    public synchronized WallLights wallLights() {
        if (wallLights == null) wallLights = new WallLights(this);
        return wallLights;
    }

    public List<Town> towns() { return Collections.unmodifiableList(towns); }
    public List<Region> regions() { return Collections.unmodifiableList(regions); }

    void addTown(Town t) {
        if (towns.size() >= Town.MAX_TOWNS) throw new IllegalArgumentException("at most " + Town.MAX_TOWNS + " towns");
        towns.add(t);
    }

    void addRegion(Region r) { regions.add(r); }

    void addDisplay(Position p, String itemKey) { displays.put(p, itemKey); }

    /** Przedmiot wylozony na ladzie w tym polu (do ogladania) albo null. */
    public String displayAt(Position p) { return displays.get(p); }

    public Map<Position, String> displays() { return Collections.unmodifiableMap(displays); }

    /** Miasto o nazwie (bez rozrozniania wielkosci liter) albo null. */
    public Town town(String name) {
        for (Town t : towns) if (t.name().equalsIgnoreCase(name)) return t;
        return null;
    }

    /** Swiatynia miasta rodzinnego albo punkt startu, gdy miasta brak (np. mapa testowa). */
    public Position templeOf(String townName) {
        Town t = townName == null ? null : town(townName);
        return t != null ? t.temple() : spawn;
    }

    /** Pierwszy region zawierajacy pozycje albo null. */
    public Region regionAt(Position p) {
        for (Region r : regions) if (r.contains(p)) return r;
        return null;
    }

    public FloorMap floor(int z) { return floors.get(z); }

    public FloorMap getOrCreateFloor(int z) {
        if (z < Position.MIN_FLOOR || z > Position.MAX_FLOOR) throw new IllegalArgumentException("floor " + z);
        return floors.computeIfAbsent(z, k -> new FloorMap(k, width, height, tiles));
    }

    public Map<Integer, FloorMap> floors() { return Collections.unmodifiableMap(floors); }

    public boolean isWalkable(Position p) {
        FloorMap f = floors.get(p.z());
        return f != null && f.isWalkable(p.x(), p.y());
    }

    public Transition transitionAt(Position p) {
        FloorMap f = floors.get(p.z());
        return f == null ? null : f.transitionAt(p.x(), p.y());
    }

    /** Dodaje prostokatna strefe ochronna (bez walki, potwory nie wchodza). */
    public void addProtectionZone(int x0, int y0, int x1, int y1, int z) {
        protectionZones.add(new int[] {Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1), z});
        pzCells = null;
    }

    /** Przejscie strzezone: wejsc moga tylko postacie od danego poziomu. */
    public void addLevelGate(int x0, int y0, int x1, int y1, int z, int minLevel) {
        levelGates.add(new int[] {Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1), z, minLevel});
    }

    /** Wymagany poziom, aby wejsc na kafelek (0 = brak wymagan). */
    public int requiredLevelAt(Position p) {
        int need = 0;
        for (int[] r : levelGates) {
            if (p.z() == r[4] && p.x() >= r[0] && p.x() <= r[2] && p.y() >= r[1] && p.y() <= r[3]) need = Math.max(need, r[5]);
        }
        return need;
    }

    // ------------------------------------------------------------------ drzwi poziomu i zadania z oltarzami
    /** Drzwi poziomu: pole drzwi -> minimalny poziom (dane z level_doors.csv). */
    private final Map<Position, Integer> levelDoors = new java.util.LinkedHashMap<>();
    private final List<AltarQuest> altarQuests = new ArrayList<>();

    /**
     * Drzwi poziomu (jak w Tibii): zwykle drzwi w murze, przez ktore przejdzie tylko postac od danego poziomu.
     * Pole drzwi dostaje straznika poziomu ({@link #requiredLevelAt}); dotkniecie pokazuje napis z poziomem.
     */
    public void addLevelDoor(Position p, int minLevel) {
        if (minLevel <= 0) throw new IllegalArgumentException("level door needs a level: " + p);
        levelDoors.put(p, minLevel);
        addLevelGate(p.x(), p.y(), p.x(), p.y(), p.z(), minLevel);
    }

    /** Minimalny poziom drzwi poziomu w tym polu albo 0, gdy to nie sa drzwi poziomu. */
    public int levelDoorAt(Position p) {
        Integer v = levelDoors.get(p);
        return v == null ? 0 : v;
    }

    public Map<Position, Integer> levelDoors() { return Collections.unmodifiableMap(levelDoors); }

    /** Napis na drzwiach poziomu. */
    public static String levelDoorSign(int minLevel) { return "„Tylko dla poziomu " + minLevel + "+”"; }

    void addAltarQuest(AltarQuest q) { altarQuests.add(q); }

    public List<AltarQuest> altarQuests() { return Collections.unmodifiableList(altarQuests); }

    /** Zadanie, ktorego dzwignia stoi w tym polu, albo null. */
    public AltarQuest altarQuestByLever(Position p) {
        for (AltarQuest q : altarQuests) if (q.lever().equals(p)) return q;
        return null;
    }

    /** Oltarz zadania w tym polu albo null. */
    public AltarQuest.Slot altarAt(Position p) {
        for (AltarQuest q : altarQuests) {
            AltarQuest.Slot s = q.slotByAltar(p);
            if (s != null) return s;
        }
        return null;
    }

    /** Pochodnia sterowana przez zadanie (nie zapala sie dotknieciem). */
    public boolean isQuestTorch(Position p) {
        for (AltarQuest q : altarQuests) if (q.slotByTorch(p) != null) return true;
        return false;
    }

    /** Sala oltarzy albo skarbiec ktoregos zadania. */
    public boolean inAltarQuestRooms(Position p) {
        for (AltarQuest q : altarQuests) if (q.inQuestRooms(p)) return true;
        return false;
    }

    public boolean isProtectionZone(Position p) {
        if (p.x() < 0 || p.y() < 0 || p.x() >= width() || p.y() >= height()) {
            for (int[] r : protectionZones) {
                if (p.z() == r[4] && p.x() >= r[0] && p.x() <= r[2] && p.y() >= r[1] && p.y() <= r[3]) return true;
            }
            return false;
        }
        java.util.Map<Integer, java.util.BitSet> cells = pzCells;
        if (cells == null) cells = pzCells = protectionBits();
        java.util.BitSet b = cells.get(p.z());
        return b != null && b.get(p.y() * width() + p.x());
    }

    /** Strefy ochronne jako mapa bitowa pieter (miasta o nieregularnym ksztalcie maja setki prostokatow). */
    private java.util.Map<Integer, java.util.BitSet> protectionBits() {
        java.util.Map<Integer, java.util.BitSet> out = new java.util.HashMap<>();
        int w = width(), h = height();
        for (int[] r : protectionZones) {
            java.util.BitSet b = out.computeIfAbsent(r[4], k -> new java.util.BitSet(w * h));
            for (int y = Math.max(0, r[1]); y <= Math.min(h - 1, r[3]); y++) {
                if (r[0] > r[2] || r[0] >= w) continue;
                b.set(y * w + Math.max(0, r[0]), y * w + Math.min(w - 1, r[2]) + 1);
            }
        }
        return out;
    }

    /** Linia wzroku na tym samym pietrze (Bresenham, pomija punkt startowy i docelowy). */
    public boolean hasLineOfSight(Position a, Position b) {
        if (a.z() != b.z()) return false;
        FloorMap f = floors.get(a.z());
        if (f == null) return false;
        int x0 = a.x(), y0 = a.y(), x1 = b.x(), y1 = b.y();
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            if (x0 == x1 && y0 == y1) return true;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
            if ((x0 != x1 || y0 != y1) && f.blocksSight(x0, y0)) return false;
        }
    }

    public Position spawn() { return spawn; }

    public void setSpawn(Position spawn) { this.spawn = spawn; }
}
