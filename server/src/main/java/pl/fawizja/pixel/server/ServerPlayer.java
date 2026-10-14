package pl.fawizja.pixel.server;

import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.world.Direction;

/** Gracz w swiecie serwera: jego sesja gry i ostatni zamiar ruchu z klienta. */
final class ServerPlayer {
    final int id;
    final GameSession session;
    final ClientHandler client;
    Direction move;
    long logSent;
    long eventsSent;
    String lastSelf;
    long lastSelfMs = -1_000_000;
    long lastSaveMs;

    ServerPlayer(int id, GameSession session, ClientHandler client, long now) {
        this.id = id;
        this.session = session;
        this.client = client;
        this.logSent = session.log().total();
        this.eventsSent = session.combat().eventTotal();
        this.lastSaveMs = now;
    }

    String name() { return session.character().name(); }
}
