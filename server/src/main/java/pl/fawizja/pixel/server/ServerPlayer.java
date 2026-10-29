package pl.fawizja.pixel.server;

import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.world.Direction;

/** Gracz w swiecie serwera: jego sesja gry i ostatni zamiar ruchu z klienta. */
final class ServerPlayer {
    final int id;
    final GameSession session;
    final ClientHandler client;
    Direction move;
    /** Gracz atakowany w PvP (id) albo -1. */
    int pvpTarget = -1;
    final pl.fawizja.pixel.pvp.PvpRules.Status pvp;
    long logSent;
    /** Czasy ostatnich wiadomosci czatu (limit spamu). */
    final java.util.ArrayDeque<Long> chatTimes = new java.util.ArrayDeque<>();
    long eventsSent;
    /** Ostatnia wyslana klatka (bez znacznika czasu) i licznik klatek zastapionych przez KEEP. */
    String lastFrameBody;
    long framesKept;
    String lastSelf;
    long lastSelfMs = -1_000_000;
    long lastSaveMs;
    /** Ostatnie zgloszenie gracza (/zgłoś), limit 1 na minute. */
    long lastReportMs = -1_000_000;
    /** Ostatnio wyslany wiersz wyzwania ("-" = brak). */
    String lastChallenge = "-";
    /** Ostatnio wyslany wiersz PVP (tryb, czaszka, blokada PZ). */
    String lastPvp;
    /** Gracz oglosil, ze szuka druzyny (/szukam; tylko w pamieci). */
    boolean lookingForParty;
    /** Klient sie rozlaczyl, ale postac zostaje w swiecie do konca blokady wylogowania (walka z graczem). */
    boolean lingering;
    /** Ostatnio wyslany komunikat PARTYINFO (bez powtorzen przy odswiezaniu). */
    String lastPartyInfo = "";

    ServerPlayer(int id, GameSession session, ClientHandler client, long now, pl.fawizja.pixel.pvp.PvpRules.Status pvp) {
        this.pvp = pvp;
        this.id = id;
        this.session = session;
        this.client = client;
        this.logSent = session.log().total();
        this.eventsSent = session.combat().eventTotal();
        this.lastSaveMs = now;
    }

    String name() { return session.character().name(); }
}
