package pl.fawizja.pixel.treasure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.world.Layer;
import pl.fawizja.pixel.world.FloorMap;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Tabliczki przy skrzyniach zadan (Darek 2026-11-12: "za skrzyniami albo przed ma byc tabliczka co jest w srodku").
 * Kazda skrzynia zadania ({@link ChestRewards.Kind#QUEST} albo skrzynia nagrody do wyboru z mapy) dostaje tabliczke
 * na sasiednim polu: wolnym, przechodnim, bez obiektu, przejscia, drzwi i innej skrzyni, najchetniej pod sciana i
 * obok tylko tej jednej skrzyni (przy rzedzie skrzyn nie wiadomo by, ktorej dotyczy). Tabliczka nie blokuje ruchu -
 * dotkniecie jej (jak drogowskazu) pokazuje, co jest w skrzyni (tekst liczy {@code GameSession.chestSignText},
 * bo nagroda zalezy od profesji). Miejsca licza sie powtarzalnie z mapy, wiec klient i serwer dostaja te same.
 */
public final class ChestSigns {

    /** Tabliczka: pole i skrzynia, ktorej dotyczy. */
    public record Sign(Position at, TreasureChests.Chest chest) {}

    private final List<Sign> signs;
    private final Map<Position, Sign> byPosition = new HashMap<>();

    private ChestSigns(List<Sign> signs) {
        this.signs = List.copyOf(signs);
        for (Sign s : signs) byPosition.put(s.at(), s);
    }

    public static final ChestSigns NONE = new ChestSigns(List.of());

    public List<Sign> all() { return Collections.unmodifiableList(signs); }

    public Sign at(Position p) { return byPosition.get(p); }

    public Sign forChest(String chestId) {
        for (Sign s : signs) if (s.chest().id().equals(chestId)) return s;
        return null;
    }

    /** Czy skrzynia ma tabliczke: skrzynia zadania albo nagroda do wyboru. */
    public static boolean needsSign(TreasureChests.Chest c, WorldMap world, MonsterManager monsters, ItemRegistry items) {
        if (world.rewardChoiceAt(c.position()) != null) return true;
        return ChestRewards.kind(c, world, monsters, items) == ChestRewards.Kind.QUEST;
    }

    public static ChestSigns compute(TreasureChests chests, WorldMap world, MonsterManager monsters, ItemRegistry items) {
        if (chests == null) return NONE;
        List<Sign> out = new ArrayList<>();
        Map<Position, Sign> taken = new HashMap<>();
        List<TreasureChests.Chest> quest = new ArrayList<>();
        for (TreasureChests.Chest c : chests.all()) if (needsSign(c, world, monsters, items)) quest.add(c);
        for (TreasureChests.Chest c : quest) {
            Position at = spot(c, chests, world, taken);
            if (at == null) continue;
            Sign s = new Sign(at, c);
            taken.put(at, s);
            out.add(s);
        }
        return new ChestSigns(out);
    }

    // kolejnosc prob: pod skrzynia (przed nia), obok, nad nia (za nia), potem dalej o 2 pola
    private static final int[][] NEAR = {{0, 1}, {1, 0}, {-1, 0}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
    private static final int[][] FAR = {{0, 2}, {2, 0}, {-2, 0}, {0, -2}};

    private static Position spot(TreasureChests.Chest c, TreasureChests chests, WorldMap world, Map<Position, Sign> taken) {
        Position best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int[][] ring : new int[][][] {NEAR, FAR}) {
            for (int[] d : ring) {
                Position p = c.position().translate(d[0], d[1]);
                if (!free(p, chests, world, taken)) continue;
                int score = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0) continue;
                        Position q = p.translate(dx, dy);
                        TreasureChests.Chest other = chests.at(q);
                        if (other != null && other != c) score -= 6;      // sasiad innej skrzyni: nie wiadomo, ktorej dotyczy
                        else if (!world.isWalkable(q)) score += 1;       // pod sciana
                        else if (taken.containsKey(q)) score -= 3;
                    }
                }
                if (d[0] != 0 && d[1] != 0) score -= 2;                  // wolimy pole obok, nie po przekatnej
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    private static boolean free(Position p, TreasureChests chests, WorldMap world, Map<Position, Sign> taken) {
        if (!world.isWalkable(p) || chests.at(p) != null || chests.trapAt(p) || taken.containsKey(p)) return false;
        if (world.transitionAt(p) != null || world.levelDoorAt(p) > 0 || world.questGateAt(p) != null) return false;
        if (world.openings().isDoor(p) || world.openings().isWindow(p) || world.wallLights().isWallLight(p)) return false;
        FloorMap f = world.floor(p.z());
        if (f == null || !f.inBounds(p.x(), p.y())) return false;
        return f.get(Layer.OBJECT, p.x(), p.y()) == 0;
    }
}
