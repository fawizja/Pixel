package pl.fawizja.pixel.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wyprawa na bossa w piec postaci (Darek 2026-10-28): za drzwiami poziomu loch, na jego koncu przedsionek z piecioma
 * oznaczonymi polami i dzwignia, obok zamknieta arena. Dzwignia dziala tylko, gdy na wszystkich pieciu polach stoja
 * postacie (dowolne profesje), ktore jeszcze nie ukonczyly tej wyprawy: przenosi je na arene i budzi bossa.
 * Po zabiciu bossa kazdy uczestnik dostaje wlasny lup, a w rogu areny otwiera sie portal z powrotem do przedsionka.
 * <p>
 * Dane z pliku mapy boss_quests.csv (zapisuje go generator); przebieg (stan areny) prowadzi
 * {@code pl.fawizja.pixel.game.BossQuestRuntime}.
 */
public final class BossQuest {

    /** Pole w przedsionku i cel teleportu na arenie. */
    public record Square(Position stand, Position target) {}

    public static final int PARTY = 5;

    private final String id;
    private final String name;
    private final int minLevel;
    private final String bossKey;
    private final Position lever;
    /** Przedsionek i arena: x0, y0, x1, y1, z (wlacznie, bez murow). */
    private final int[] lobby;
    private final int[] arena;
    private final Position bossSpawn;
    private final Position exitPortal;
    private final Position exitTarget;
    private final Position levelDoor;
    private final List<Square> squares = new ArrayList<>();

    public BossQuest(String id, String name, int minLevel, String bossKey, Position lever, int[] lobby, int[] arena,
                     Position bossSpawn, Position exitPortal, Position exitTarget, Position levelDoor) {
        if (lobby.length != 5 || arena.length != 5) throw new IllegalArgumentException("rect needs x0,y0,x1,y1,z");
        this.id = id;
        this.name = name;
        this.minLevel = minLevel;
        this.bossKey = bossKey;
        this.lever = lever;
        this.lobby = lobby.clone();
        this.arena = arena.clone();
        this.bossSpawn = bossSpawn;
        this.exitPortal = exitPortal;
        this.exitTarget = exitTarget;
        this.levelDoor = levelDoor;
        if (!in(this.arena, bossSpawn) || !in(this.arena, exitPortal)) throw new IllegalArgumentException("boss and portal must be in the arena of " + id);
        if (in(this.arena, exitTarget)) throw new IllegalArgumentException("exit target inside the arena of " + id);
    }

    void addSquare(Square s) {
        if (squares.size() >= PARTY) throw new IllegalArgumentException("more than " + PARTY + " squares in " + id);
        if (!in(arena, s.target())) throw new IllegalArgumentException("square target outside the arena: " + s);
        for (Square o : squares) {
            if (o.stand().equals(s.stand()) || o.target().equals(s.target())) throw new IllegalArgumentException("duplicate square " + s);
        }
        squares.add(s);
    }

    public String id() { return id; }
    public String name() { return name; }
    /** Minimalny poziom (napis na drzwiach poziomu przed lochem). */
    public int minLevel() { return minLevel; }
    /** Klucz bossa z monsters.csv. */
    public String bossKey() { return bossKey; }
    public Position lever() { return lever; }
    public Position bossSpawn() { return bossSpawn; }
    public Position exitPortal() { return exitPortal; }
    /** Pole w przedsionku, na ktore portal przenosi po walce (i dokad trafia postac wylogowana na arenie). */
    public Position exitTarget() { return exitTarget; }
    public Position levelDoor() { return levelDoor; }
    public List<Square> squares() { return Collections.unmodifiableList(squares); }

    public Square squareAt(Position p) {
        for (Square s : squares) if (s.stand().equals(p)) return s;
        return null;
    }

    public boolean inArena(Position p) { return in(arena, p); }

    public boolean inLobby(Position p) { return in(lobby, p); }

    /** x0, y0, x1, y1, z areny (kopie). */
    public int[] arenaRect() { return arena.clone(); }

    public int[] lobbyRect() { return lobby.clone(); }

    private static boolean in(int[] r, Position p) {
        return p.z() == r[4] && p.x() >= r[0] && p.x() <= r[2] && p.y() >= r[1] && p.y() <= r[3];
    }
}
