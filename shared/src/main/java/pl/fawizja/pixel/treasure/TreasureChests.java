package pl.fawizja.pixel.treasure;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import pl.fawizja.pixel.world.FloorMap;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.Transition;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Skrzynie skarbow: po 4 na kazdym pietrze lochow (-1..-5) i po 2 na wyzynach (+1..+3). Kazda postac otwiera
 * kazda skrzynie raz. Miejsca liczone powtarzalnie z mapy (klient i serwer dostaja te same): kafelki osiagalne
 * z miejsca startu (takze przez przejscia miedzy pietrami), poza strefa ochronna, co najmniej 8 pol od przejsc.
 */
public final class TreasureChests {

    /**
     * Zawartosc skrzyni: zloto i przedmiot (klucz z items.csv, null = samo zloto) w ilosci. Skrzynia nagrody zadania
     * moze nalezec do jednej profesji (vocation != null): inne profesje jej nie otworza.
     */
    public record Chest(String id, Position position, long gold, String itemKey, int count,
                        pl.fawizja.pixel.vocation.Vocation vocation) {
        public Chest(String id, Position position, long gold, String itemKey, int count) {
            this(id, position, gold, itemKey, count, null);
        }
    }

    public static final long SEED = 20261015L;

    /** Pulapki z kolcami na pietrach -2..-5: na pietrze tyle, ile wynosi glebokosc razy 4. */
    public static final int TRAPS_PER_DEPTH = 4;

    private final List<Chest> chests;
    private final Map<Position, Chest> byPosition = new HashMap<>();
    private final List<Position> traps;
    private final java.util.Set<Position> trapSet;

    public TreasureChests(List<Chest> chests) {
        this(chests, List.of());
    }

    public TreasureChests(List<Chest> chests, List<Position> traps) {
        this.chests = List.copyOf(chests);
        for (Chest c : chests) byPosition.put(c.position(), c);
        this.traps = List.copyOf(traps);
        this.trapSet = new java.util.HashSet<>(traps);
    }

    /** Pulapki z kolcami (widoczne): wejscie rani o czesc zdrowia, ale nie zabija. */
    public List<Position> traps() { return traps; }

    public boolean trapAt(Position p) { return trapSet.contains(p); }

    /** Obrazenia pulapki: 5% maks. zdrowia na kazde pietro glebokosci, zawsze zostaje co najmniej 1 HP. */
    public static int trapDamage(int maxHealth, int health, int z) {
        int dmg = Math.max(1, Math.round(maxHealth * 0.05f * Math.max(1, -z)));
        return Math.max(0, Math.min(dmg, health - 1));
    }

    public List<Chest> all() { return chests; }

    public Chest at(Position p) { return byPosition.get(p); }

    /** Skrzynia na kafelku gracza albo obok (8 kierunkow); null gdy brak. */
    public Chest near(Position p) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                Chest c = byPosition.get(p.translate(dx, dy));
                if (c != null) return c;
            }
        }
        return null;
    }

    /** Lup zalezy od pietra: im glebiej, tym wiecej. */
    static Chest stock(String id, Position p) {
        int z = p.z();
        if (z > 0) return new Chest(id, p, 300L * z, "health_potion", 2);
        int d = -z;
        long gold = 500L;
        for (int i = 1; i < d; i++) gold *= 3;
        switch (d) {
            case 1: return new Chest(id, p, gold, "health_potion", 5);
            case 2: return new Chest(id, p, gold, "mana_potion", 8);
            case 3: return new Chest(id, p, gold, "great_health_potion", 4);
            case 4: return new Chest(id, p, gold, "great_mana_potion", 6);
            default: return new Chest(id, p, gold, "supreme_health_potion", 3);
        }
    }

    public static TreasureChests generate(WorldMap world) {
        int w = world.width(), h = world.height();
        Map<Integer, BitSet> reach = reachable(world);
        List<Chest> out = new ArrayList<>();
        List<Position> traps = new ArrayList<>();
        int[] floors = {-1, -2, -3, -4, -5, 1, 2, 3};
        for (int z : floors) {
            BitSet r = reach.get(z);
            FloorMap fm = world.floor(z);
            if (r == null || fm == null) continue;
            List<Position> exits = new ArrayList<>();
            for (Transition t : fm.transitions().values()) exits.add(t.from());
            List<Position> cand = new ArrayList<>();
            for (int i = r.nextSetBit(0); i >= 0; i = r.nextSetBit(i + 1)) {
                Position p = new Position(i % w, i / w, z);
                if (world.isProtectionZone(p) || world.requiredLevelAt(p) > 0 || world.inAltarQuestRooms(p)) continue;
                boolean far = true;
                for (Position e : exits) {
                    if (Math.max(Math.abs(e.x() - p.x()), Math.abs(e.y() - p.y())) < 8) {
                        far = false;
                        break;
                    }
                }
                if (far) cand.add(p);
            }
            Collections.shuffle(cand, new Random(SEED + z));
            int want = z < 0 ? 4 : 2;
            List<Position> picked = new ArrayList<>();
            // odstep miedzy skrzyniami: 20 pol, a na malych pietrach mniej
            for (int gap = 20; gap >= 4 && picked.size() < want; gap /= 2) {
                picked.clear();
                for (Position p : cand) {
                    if (picked.size() >= want) break;
                    boolean spaced = true;
                    for (Position q : picked) if (Math.max(Math.abs(q.x() - p.x()), Math.abs(q.y() - p.y())) < gap) spaced = false;
                    if (spaced) picked.add(p);
                }
            }
            for (int i = 0; i < picked.size(); i++) out.add(stock("c" + z + "_" + i, picked.get(i)));
            if (z <= -2) {
                // pulapki: z dala od skrzyn (3 pola) i od siebie (5 pol); kolejnosc z innego ziarna
                Collections.shuffle(cand, new Random(SEED * 31 + z));
                List<Position> placed = new ArrayList<>();
                for (Position p : cand) {
                    if (placed.size() >= TRAPS_PER_DEPTH * -z) break;
                    boolean ok = true;
                    for (Position q : picked) if (Math.max(Math.abs(q.x() - p.x()), Math.abs(q.y() - p.y())) < 3) ok = false;
                    for (Position q : placed) if (Math.max(Math.abs(q.x() - p.x()), Math.abs(q.y() - p.y())) < 5) ok = false;
                    if (ok) placed.add(p);
                }
                traps.addAll(placed);
            }
        }
        // skrzynie nagrod zadan z oltarzami profesji: po jednej na profesje, samo zloto
        for (pl.fawizja.pixel.world.AltarQuest aq : world.altarQuests()) {
            for (pl.fawizja.pixel.world.AltarQuest.Slot s : aq.slots()) {
                out.add(new Chest(aq.chestId(s.vocation()), s.chest(), aq.gold(), null, 0, s.vocation()));
            }
        }
        return new TreasureChests(out, traps);
    }

    /** Kafelki osiagalne z miejsca startu: ruch w 4 kierunkach i kazde przejscie (schody, liny, tunele). */
    public static Map<Integer, BitSet> reachable(WorldMap world) {
        int w = world.width(), h = world.height();
        Map<Integer, BitSet> seen = new HashMap<>();
        ArrayDeque<Position> q = new ArrayDeque<>();
        Position start = world.spawn();
        mark(seen, start, w);
        q.add(start);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            Position p = q.poll();
            List<Position> next = new ArrayList<>(5);
            for (int[] d : dirs) next.add(p.translate(d[0], d[1]));
            Transition t = world.transitionAt(p);
            if (t != null) next.add(t.to());
            for (Position n : next) {
                if (n.x() < 0 || n.y() < 0 || n.x() >= w || n.y() >= h) continue;
                if (!world.isWalkable(n) && world.transitionAt(n) == null) continue;
                if (mark(seen, n, w)) q.add(n);
            }
        }
        return seen;
    }

    private static boolean mark(Map<Integer, BitSet> seen, Position p, int w) {
        BitSet b = seen.computeIfAbsent(p.z(), k -> new BitSet());
        int i = p.y() * w + p.x();
        if (b.get(i)) return false;
        b.set(i);
        return true;
    }
}
