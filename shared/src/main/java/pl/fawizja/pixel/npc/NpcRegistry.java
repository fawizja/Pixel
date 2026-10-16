package pl.fawizja.pixel.npc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import pl.fawizja.pixel.magic.SpellRegistry;
import pl.fawizja.pixel.world.FloorMap;
import pl.fawizja.pixel.world.Layer;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/**
 * NPC mapy z /maps/&lt;mapa&gt;/npcs.csv. NPC stoja na przechodnich kafelkach w strefie ochronnej miasta,
 * nie na przejsciach, kazdy na innym kafelku; kupiec musi miec istniejacy sklep.
 */
public final class NpcRegistry {
    private final List<Npc> npcs;

    private NpcRegistry(List<Npc> npcs) { this.npcs = Collections.unmodifiableList(npcs); }

    public static NpcRegistry empty() { return new NpcRegistry(new ArrayList<>()); }

    public static NpcRegistry load(String mapName, WorldMap world, ShopRegistry shops) throws IOException {
        return parse(SpellRegistry.readLines("/maps/" + mapName + "/npcs.csv"), world, shops);
    }

    public static NpcRegistry parse(List<String> lines, WorldMap world, ShopRegistry shops) throws IOException {
        List<Npc> list = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        Set<Position> taken = new HashSet<>();
        int n = 0;
        for (String line : lines) {
            n++;
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] f = t.split(";", 10);
            if (f.length != 10) throw new IOException("npcs.csv line " + n + ": expected 10 fields");
            try {
                Position p = new Position(Integer.parseInt(f[4].trim()), Integer.parseInt(f[5].trim()), Integer.parseInt(f[6].trim()));
                String shop = f[7].trim();
                Npc npc = new Npc(f[0].trim(), f[1].trim(), NpcRole.valueOf(f[2].trim()), f[3].trim(), p,
                        shop.equals("-") ? null : shop, f[9].trim(), f[8].trim());
                if (!world.isWalkable(p)) throw new IllegalArgumentException("not walkable " + p);
                if (!world.isProtectionZone(p)) throw new IllegalArgumentException("outside protection zone " + p);
                if (world.transitionAt(p) != null) throw new IllegalArgumentException("on a transition " + p);
                // kaplan (odrodzenie) i woznica tylko w miescie; kupiec, bankier i przewodnik moga stac w obozie poza miastem
                if (npc.town().isEmpty() || ((npc.role() == NpcRole.PRIEST || npc.role() == NpcRole.CARRIER) && world.town(npc.town()) == null)) {
                    throw new IllegalArgumentException("unknown town " + npc.town());
                }
                if (npc.shopKey() != null && !shops.contains(npc.shopKey())) {
                    throw new IllegalArgumentException("unknown shop " + npc.shopKey());
                }
                if (!keys.add(npc.key())) throw new IllegalArgumentException("duplicate npc " + npc.key());
                if (!taken.add(p)) throw new IllegalArgumentException("two npcs on " + p);
                list.add(npc);
            } catch (IllegalArgumentException e) {
                throw new IOException("npcs.csv line " + n + ": " + e.getMessage(), e);
            }
        }
        return new NpcRegistry(list);
    }

    public List<Npc> all() { return npcs; }

    /** Czy miedzy sasiednimi kafelkami (odleglosc 2) stoi sciana na kazdej mozliwej drodze. */
    static boolean wallBetween(WorldMap world, Position a, Position b) {
        if (a.distanceTo(b) <= 1) return false;
        FloorMap f = world.floor(a.z());
        int dx = b.x() - a.x(), dy = b.y() - a.y();
        int[] xs = Math.abs(dx) == 2 ? new int[] {a.x() + dx / 2} : new int[] {a.x(), b.x()};
        int[] ys = Math.abs(dy) == 2 ? new int[] {a.y() + dy / 2} : new int[] {a.y(), b.y()};
        for (int x : xs) {
            for (int y : ys) {
                if (f.get(Layer.WALL, x, y) == 0) return false;
            }
        }
        return true;
    }

    public Npc get(String key) {
        for (Npc n : npcs) if (n.key().equals(key)) return n;
        return null;
    }

    public Npc at(Position p) {
        for (Npc n : npcs) if (n.position().equals(p)) return n;
        return null;
    }

    /** Najblizszy NPC w zasiegu rozmowy na tym samym pietrze, nie za sciana (lada nie przeszkadza). */
    public Npc nearest(Position p, WorldMap world) {
        Npc best = null;
        for (Npc n : npcs) {
            Position q = n.position();
            if (q.z() != p.z() || q.distanceTo(p) > Npc.TALK_RANGE || wallBetween(world, p, q)) continue;
            if (best == null || q.distanceTo(p) < best.position().distanceTo(p)) best = n;
        }
        return best;
    }
}
