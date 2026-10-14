package pl.fawizja.pixel.game;

import java.util.ArrayList;
import java.util.List;
import pl.fawizja.pixel.movement.MovementSystem;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Position;

/**
 * Przewidywanie ruchu w kliencie online: postac rusza od razu, a pozycje z serwera tylko
 * potwierdzaja przewidziana sciezke. Gdy serwer pokaze inne miejsce (blokada, teleport,
 * smierc) albo dlugo nie potwierdza kroku - klient przyjmuje pozycje serwera.
 */
public final class MovePredictor {

    /** Ile krokow klient moze wyprzedzic serwer. */
    public static final int MAX_AHEAD = 2;
    /** Po tym czasie bez potwierdzenia przewidywanie jest porzucane. */
    public static final long CONFIRM_TIMEOUT_MS = 800;

    /** [pozycja wyjsciowa, krok 1, krok 2...] - pusta, gdy nic nie czeka na potwierdzenie. */
    private final List<Position> path = new ArrayList<>();
    private long lastPredictMs;
    private int corrections;

    /** Wywolywane co klatke z kierunkiem z joysticka/klawiatury. */
    public void tick(GameSession s, Direction dir, long now) {
        if (dir == null || s.isStepping(now) || path.size() > MAX_AHEAD) return;
        Position from = s.character().position();
        if (s.predictStep(dir, now)) {
            if (path.isEmpty()) path.add(from);
            path.add(s.character().position());
            lastPredictMs = now;
        }
    }

    /** Pozycja z klatki serwera. */
    public void onServer(GameSession s, Position server, Direction facing, long now) {
        if (!path.isEmpty()) {
            int k = path.indexOf(server);
            if (k > 0) {
                path.subList(0, k).clear();
                if (path.size() == 1) path.clear();
                return;
            }
            if (k == 0 && now - lastPredictMs <= CONFIRM_TIMEOUT_MS) return; // serwer jeszcze nie doszedl
            path.clear();
            corrections++;
        }
        Position cur = s.character().position();
        Direction d = Direction.fromDelta(Integer.signum(server.x() - cur.x()), Integer.signum(server.y() - cur.y()));
        long step = d == null ? 0 : MovementSystem.stepDurationMs(s.character(), d);
        s.applyRemotePosition(server, facing, now, step);
    }

    public boolean pending() { return !path.isEmpty(); }

    /** Ile razy serwer poprawil przewidywanie (diagnostyka). */
    public int corrections() { return corrections; }
}
