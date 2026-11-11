package pl.fawizja.pixel.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pola magiczne na ziemi jak w Tibii (Canary items.xml): pole ognia, trucizny i energii z run (takze bomby i sciany),
 * magiczna sciana i dziki wzrost. Pole ranicego rodzaju nakłada stan na kazdego, kto na nie wejdzie albo na kim
 * powstanie; magiczna sciana i dziki wzrost blokuja przejscie i pociski. Pola znikaja po czasie z Tibii.
 * Wspolne dla swiata (WorldMap), bezpieczne dla wielu sesji serwera.
 */
public final class Fields {

    /** Rodzaj pola; czasy trwania i obrazenia jak w Canary (pole ognia: 20 co 10 s, potem slabsze 10 co 10 s). */
    public enum Kind {
        FIRE("Pole ognia", 200_000L, false),
        /** Przygasajace pole ognia (Canary 2119): 10 obrazen na tik. */
        FIRE_WEAK("Dogasające pole ognia", 148_000L, false),
        POISON("Pole trucizny", 248_000L, false),
        ENERGY("Pole energii", 98_000L, false),
        MAGIC_WALL("Magiczna ściana", 20_000L, true),
        WILD_GROWTH("Dziki wzrost", 20_000L, true);

        public final String label;
        public final long durationMs;
        public final boolean blocking;

        Kind(String label, long durationMs, boolean blocking) {
            this.label = label;
            this.durationMs = durationMs;
            this.blocking = blocking;
        }

        /** Pole do zniszczenia runa niszczenia pol (ogien, trucizna, energia - nie magiczna sciana ani dziki wzrost). */
        public boolean destroyable() { return !blocking; }
    }

    /** Pole na kafelku; id rosnie z kazdym nowym polem (sesje rozpoznaja, czy postac juz w nim stala). */
    public record Field(Position position, Kind kind, long untilMs, String owner, long id) {}

    private final Map<Position, Field> fields = new ConcurrentHashMap<>();
    private long nextId = 1;
    private long lastExpireMs = Long.MIN_VALUE;

    /** Stawia pole (zastepuje inne pole na tym kafelku). Zwraca pole albo null, gdy kafelek nie jest przechodni. */
    public synchronized Field place(Position p, Kind kind, long now, String owner, WorldMap world) {
        if (world != null && !world.floorWalkable(p)) return null;
        Field f = new Field(p, kind, now + kind.durationMs, owner, nextId++);
        fields.put(p, f);
        return f;
    }

    public Field at(Position p) {
        Field f = fields.get(p);
        return f;
    }

    /** Czy pole blokuje przejscie (magiczna sciana, dziki wzrost). */
    public boolean blocks(Position p) {
        if (fields.isEmpty()) return false;
        Field f = fields.get(p);
        return f != null && f.kind().blocking;
    }

    public Field remove(Position p) { return fields.remove(p); }

    /** Usuwa wygasle pola; pole ognia przechodzi w slabsze. Najwyzej raz na chwile (wiele sesji serwera). */
    public synchronized void expire(long now) {
        if (now == lastExpireMs || fields.isEmpty()) return;
        lastExpireMs = now;
        for (Field f : new ArrayList<>(fields.values())) {
            if (now < f.untilMs()) continue;
            if (f.kind() == Kind.FIRE) {
                fields.put(f.position(), new Field(f.position(), Kind.FIRE_WEAK, f.untilMs() + Kind.FIRE_WEAK.durationMs, f.owner(), f.id()));
            } else {
                fields.remove(f.position(), f);
            }
        }
    }

    /** Pola w kwadracie o promieniu r wokol c (na tym samym pietrze), do rysowania. */
    public List<Field> near(Position c, int r) {
        List<Field> out = new ArrayList<>();
        if (fields.isEmpty()) return out;
        for (Field f : fields.values()) {
            Position p = f.position();
            if (p.z() == c.z() && Math.abs(p.x() - c.x()) <= r && Math.abs(p.y() - c.y()) <= r) out.add(f);
        }
        return out;
    }

    public int size() { return fields.size(); }

    public void clear() { fields.clear(); }

    /** Stan nakladany przez pole (null: pole bez obrazen - magiczna sciana, dziki wzrost). */
    public static pl.fawizja.pixel.combat.ConditionType condition(Kind k) {
        switch (k) {
            case FIRE: case FIRE_WEAK: return pl.fawizja.pixel.combat.ConditionType.BURNING;
            case POISON: return pl.fawizja.pixel.combat.ConditionType.POISON;
            case ENERGY: return pl.fawizja.pixel.combat.ConditionType.ELECTRIFIED;
            default: return null;
        }
    }

    /**
     * Tiki obrazen pola jak w Canary items.xml: ogien 7 x 20 co 10 s (dogasajacy 7 x 10), trucizna 100 obrazen
     * od 5 w gore co 5 s (generateDamageList), energia jednorazowo 25. Pierwszy tik od razu przy wejsciu.
     */
    public static List<Integer> ticks(Kind k) {
        switch (k) {
            case FIRE: return pl.fawizja.pixel.combat.Conditions.constant(7, 20);
            case FIRE_WEAK: return pl.fawizja.pixel.combat.Conditions.constant(7, 10);
            case POISON: return pl.fawizja.pixel.combat.Conditions.damageList(100, 5);
            case ENERGY: return pl.fawizja.pixel.combat.Conditions.constant(1, 25);
            default: return java.util.Collections.emptyList();
        }
    }

    /** Odstep tikow stanu z pola (ms). */
    public static long intervalMs(Kind k) { return k == Kind.POISON ? 5000L : 10_000L; }

    /** Rodzaj pola z run wg typu obrazen (ogien, trucizna = ziemia, energia). */
    public static Kind ofDamage(pl.fawizja.pixel.combat.DamageType t) {
        switch (t) {
            case FIRE: return Kind.FIRE;
            case EARTH: return Kind.POISON;
            case ENERGY: return Kind.ENERGY;
            default: return null;
        }
    }
}
