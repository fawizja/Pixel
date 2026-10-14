package pl.fawizja.pixel.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Position;

/** Klient z przewidywaniem kontra serwer z opoznieniem: zgodne pozycje, poprawki przy blokadzie. */
class MovePredictorTest {

    /** Symulacja: serwer co 50 ms, klatki co 100 ms docieraja po latencyMs; klient co 16 ms. */
    private static int simulate(GameSession server, GameSession client, MovePredictor mp, Direction dir,
                                long from, long to, long latencyMs, boolean[] clientMovedEarly) {
        ArrayDeque<long[]> inFlight = new ArrayDeque<>(); // {arrival, x, y, z}
        Direction serverDir = null;
        ArrayDeque<long[]> moveMsgs = new ArrayDeque<>(); // {arrival, dirOrdinal or -1}
        Direction sent = null;
        Position clientStart = client.character().position();
        for (long t = from; t <= to; t += 2) {
            if (t % 16 == 0) {
                if (dir != sent) {
                    moveMsgs.add(new long[] {t + latencyMs, dir == null ? -1 : dir.ordinal()});
                    sent = dir;
                }
                mp.tick(client, dir, t);
                if (clientMovedEarly != null && t <= from + 20 && !client.character().position().equals(clientStart)) {
                    clientMovedEarly[0] = true;
                }
            }
            while (!moveMsgs.isEmpty() && moveMsgs.peek()[0] <= t) {
                long d = moveMsgs.poll()[1];
                serverDir = d < 0 ? null : Direction.values()[(int) d];
            }
            if (t % 50 == 0) server.update(t, serverDir);
            if (t % 100 == 0) {
                Position p = server.character().position();
                inFlight.add(new long[] {t + latencyMs, p.x(), p.y(), p.z()});
            }
            while (!inFlight.isEmpty() && inFlight.peek()[0] <= t) {
                long[] f = inFlight.poll();
                mp.onServer(client, new Position((int) f[1], (int) f[2], (int) f[3]), server.facing(), t);
            }
        }
        return mp.corrections();
    }

    @Test
    void clientMovesImmediatelyAndAgreesWithTheServer() {
        PlayerCharacter sp = TestData.newCharacter(Vocation.PALADIN);
        PlayerCharacter cp = TestData.newCharacter(Vocation.PALADIN);
        Position start = new Position(48, 6, 0);
        sp.setPosition(start);
        cp.setPosition(start);
        GameSession server = new GameSession(TestData.ITEMS, TestData.varden(), sp);
        GameSession client = new GameSession(TestData.ITEMS, TestData.varden(), cp);
        MovePredictor mp = new MovePredictor();
        boolean[] early = new boolean[1];
        int corrections = simulate(server, client, mp, Direction.EAST, 0, 1500, 120, early);
        assertTrue(early[0], "step starts without waiting for the server");
        simulate(server, client, mp, null, 1502, 4000, 120, null);
        assertEquals(server.character().position(), client.character().position());
        assertTrue(server.character().position().x() > start.x() + 2, "walked " + server.character().position());
        assertTrue(corrections <= 1, "at most a release overshoot: " + corrections);
    }

    @Test
    void serverWinsWhenThePredictionIsWrong() {
        PlayerCharacter cp = TestData.newCharacter(Vocation.PALADIN);
        Position start = new Position(48, 6, 0);
        cp.setPosition(start);
        GameSession client = new GameSession(TestData.ITEMS, TestData.varden(), cp);
        MovePredictor mp = new MovePredictor();
        mp.tick(client, Direction.EAST, 0);
        assertEquals(start.translate(1, 0), cp.position());
        // serwer: postac stoi (np. zablokowana przez innego gracza) - po czasie potwierdzenia wraca
        mp.onServer(client, start, Direction.EAST, 100);
        assertEquals(start.translate(1, 0), cp.position(), "server may simply be behind");
        mp.onServer(client, start, Direction.EAST, 100 + MovePredictor.CONFIRM_TIMEOUT_MS + 1);
        assertEquals(start, cp.position());
        // teleport (np. smierc): od razu pozycja serwera
        mp.tick(client, Direction.SOUTH, 2000);
        Position temple = new Position(31, 16, 0);
        mp.onServer(client, temple, Direction.SOUTH, 2050);
        assertEquals(temple, cp.position());
        assertFalse(mp.pending());
    }
}
