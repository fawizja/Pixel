package pl.fawizja.pixel.gathering;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import pl.fawizja.pixel.world.FloorMap;
import pl.fawizja.pixel.world.Layer;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Zbieranie ziol z krzakow: UZYJ obok krzaka daje 1-2 ziela lecznicze, potem krzak odrasta
 * {@link #REGROW_MS}. Odrastanie liczy sie osobno dla kazdej postaci (kazdy gracz ma swoja sesje).
 */
public final class HerbGathering {

    public static final long REGROW_MS = 10 * 60_000L;

    public enum Outcome { NO_BUSH, REGROWING, GATHERED }

    public record Result(Outcome outcome, int count, long readyInMs) { }

    private final WorldMap world;
    private final int bushTile;
    private final Map<Position, Long> readyAt = new HashMap<>();

    public HerbGathering(WorldMap world) {
        this.world = world;
        this.bushTile = world.tiles().byKey("bush").id();
    }

    /** Krzak na polu gracza albo obok (8 kierunkow); najpierw gotowy do zbioru. */
    public Position bushNear(Position p, long now) {
        FloorMap f = world.floor(p.z());
        if (f == null || bushTile < 0) return null;
        Position found = null;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int x = p.x() + dx, y = p.y() + dy;
                if (!f.inBounds(x, y) || f.get(Layer.OBJECT, x, y) != bushTile) continue;
                Position b = new Position(x, y, p.z());
                if (BerryBushes.kindAt(world, b) != null) continue; // krzak z owocami: owoce zamiast ziol
                if (readyAt.getOrDefault(b, Long.MIN_VALUE) <= now) return b;
                if (found == null) found = b;
            }
        }
        return found;
    }

    public Result gather(Position from, Random rng, long now) {
        return gatherFrom(bushNear(from, now), rng, now);
    }

    /** Czy na polu rosnie krzak z ziolami (zwykly krzak bez owocow). */
    public boolean isHerbBush(Position b) {
        FloorMap f = world.floor(b.z());
        return f != null && bushTile >= 0 && f.inBounds(b.x(), b.y()) && f.get(Layer.OBJECT, b.x(), b.y()) == bushTile
                && BerryBushes.kindAt(world, b) == null;
    }

    /** Zbior z konkretnego krzaka (dotkniety krzak; odleglosc sprawdza wywolujacy). */
    public Result gatherAt(Position b, Random rng, long now) {
        return gatherFrom(isHerbBush(b) ? b : null, rng, now);
    }

    private Result gatherFrom(Position b, Random rng, long now) {
        if (b == null) return new Result(Outcome.NO_BUSH, 0, 0);
        long ready = readyAt.getOrDefault(b, Long.MIN_VALUE);
        if (ready > now) return new Result(Outcome.REGROWING, 0, ready - now);
        readyAt.put(b, now + REGROW_MS);
        return new Result(Outcome.GATHERED, 1 + rng.nextInt(2), 0);
    }

    /** Czy obok jest krzak gotowy do zbioru (podpowiedz na przycisku UZYJ). */
    public boolean readyNear(Position p, long now) {
        Position b = bushNear(p, now);
        return b != null && readyAt.getOrDefault(b, Long.MIN_VALUE) <= now;
    }
}
