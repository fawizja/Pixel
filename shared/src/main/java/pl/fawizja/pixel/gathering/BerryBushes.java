package pl.fawizja.pixel.gathering;

import java.util.HashMap;
import java.util.Map;
import pl.fawizja.pixel.world.FloorMap;
import pl.fawizja.pixel.world.Layer;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Krzaki z jagodami i malinami (jak krzak borowki w Tibii): czesc zwyklych krzakow ("bush") na powierzchni nosi
 * owoce. Dotkniecie krzaka obok daje {@link #PER_PICK} owoce, po {@link #PICKS} zbiorach krzak jest pusty i odrasta
 * {@link #REGROW_MS}. Ktore krzaki owocuja, wynika z pozycji pola (bez zmian mapy) - te same krzaki widzi klient
 * (grafika z owocami) i serwer. Stan zbioru liczy sie osobno dla kazdej postaci (kazdy gracz ma swoja sesje).
 */
public final class BerryBushes {

    /** Krzak odrasta po godzinie (Tibia: pusty krzak borowki wraca po ok. godzinie). */
    public static final long REGROW_MS = 60 * 60_000L;
    /** Owoce z jednego dotkniecia (Tibia: 3 borowki z krzaka). */
    public static final int PER_PICK = 3;
    /** Ile razy mozna zrywac, zanim krzak bedzie pusty. */
    public static final int PICKS = 3;
    /** Co ktory krzak (srednio) nosi owoce. */
    static final int ONE_IN = 5;

    /** Rodzaj owocow: przedmiot z items.csv. */
    public enum Kind {
        BLUEBERRY("blueberry"), RASPBERRY("raspberry");

        public final String itemKey;

        Kind(String itemKey) { this.itemKey = itemKey; }
    }

    public enum Outcome { NO_BUSH, EMPTY, PICKED }

    /** Wynik zrywania: owoce, ile zbiorow zostalo i kiedy krzak odrosnie (gdy pusty). */
    public record Result(Outcome outcome, Kind kind, int count, int left, long readyInMs) { }

    private final WorldMap world;
    private final int bushTile;
    /** Krzak -> {zebrane razy, chwila oproznienia}. */
    private final Map<Position, long[]> state = new HashMap<>();

    public BerryBushes(WorldMap world) {
        this.world = world;
        this.bushTile = bushId(world);
    }

    private static int bushId(WorldMap world) {
        pl.fawizja.pixel.world.TileType t = world.tiles().byKeyOrNull("bush");
        return t == null ? -1 : t.id();
    }

    /** Rodzaj owocow krzaka na polu albo null, gdy to nie jest krzak z owocami. */
    public Kind kindAt(Position p) { return kindAt(world, bushTile, p); }

    /** Jak {@link #kindAt(Position)} dla dowolnego swiata (renderer). */
    public static Kind kindAt(WorldMap world, Position p) {
        return kindAt(world, bushId(world), p);
    }

    private static Kind kindAt(WorldMap world, int bushTile, Position p) {
        if (bushTile < 0 || p.z() < 0) return null;
        FloorMap f = world.floor(p.z());
        if (f == null || !f.inBounds(p.x(), p.y()) || f.get(Layer.OBJECT, p.x(), p.y()) != bushTile) return null;
        int h = hash(p.x(), p.y());
        if (h % ONE_IN != 0) return null;
        return (h / ONE_IN) % 3 == 0 ? Kind.RASPBERRY : Kind.BLUEBERRY;
    }

    /** Rozrzut pozycji (inny niz grafik, zeby owoce nie ukladaly sie we wzor). */
    static int hash(int x, int y) {
        int h = x * 0x2f6b3c1d ^ y * 0x1b873593 ^ 0x5eed;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        return h & 0x7fffffff;
    }

    /** Czy krzak jest teraz pusty (obrany i jeszcze nie odrosl). */
    public boolean isEmpty(Position p, long now) {
        long[] s = state.get(p);
        if (s == null) return false;
        if (s[0] >= PICKS && now - s[1] >= REGROW_MS) {
            state.remove(p);
            return false;
        }
        return s[0] >= PICKS;
    }

    /** Ile zbiorow zostalo na krzaku (0 = pusty). */
    public int picksLeft(Position p, long now) {
        if (kindAt(p) == null) return 0;
        if (isEmpty(p, now)) return 0;
        long[] s = state.get(p);
        return PICKS - (s == null ? 0 : (int) s[0]);
    }

    /** Zrywa owoce z krzaka na polu p (sprawdzenie odleglosci robi wywolujacy). */
    public Result pick(Position p, long now) {
        Kind k = kindAt(p);
        if (k == null) return new Result(Outcome.NO_BUSH, null, 0, 0, 0);
        if (isEmpty(p, now)) {
            long[] s = state.get(p);
            return new Result(Outcome.EMPTY, k, 0, 0, Math.max(0, s[1] + REGROW_MS - now));
        }
        long[] s = state.computeIfAbsent(p, q -> new long[2]);
        s[0]++;
        if (s[0] >= PICKS) s[1] = now;
        int left = (int) (PICKS - s[0]);
        return new Result(Outcome.PICKED, k, PER_PICK, left, left == 0 ? REGROW_MS : 0);
    }

    /** Cofa ostatni zbior (np. plecak pelny - owoce nie trafily do plecaka). */
    public void undo(Position p) {
        long[] s = state.get(p);
        if (s == null) return;
        s[0]--;
        if (s[0] <= 0) state.remove(p);
    }
}
