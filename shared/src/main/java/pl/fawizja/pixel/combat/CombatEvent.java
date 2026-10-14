package pl.fawizja.pixel.combat;

import pl.fawizja.pixel.world.Position;

/** Zdarzenie walki do wyswietlenia (liczby obrazen, leczenie, chybienia). */
public record CombatEvent(long timeMs, Position position, int amount, Kind kind) {
    public enum Kind { HIT_MONSTER, HIT_PLAYER, HEAL, BLOCKED, LEVEL_UP, DEATH }
}
