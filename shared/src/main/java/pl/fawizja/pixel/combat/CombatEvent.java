package pl.fawizja.pixel.combat;

import pl.fawizja.pixel.world.Position;

/**
 * Zdarzenie walki do wyswietlenia (liczby obrazen, leczenie, chybienia) oraz efekty graficzne:
 * PROJECTILE leci z {@code from} do {@code position}, AREA rozchodzi sie wokol {@code position}
 * w promieniu {@code amount}. effect = nazwa {@link DamageType} albo "HEAL".
 */
public record CombatEvent(long timeMs, Position position, int amount, Kind kind, Position from, String effect) {
    public enum Kind { HIT_MONSTER, HIT_PLAYER, HEAL, BLOCKED, LEVEL_UP, DEATH, PROJECTILE, AREA, EXP }

    /** Czas lotu pocisku i trwania efektu obszarowego (klient). */
    public static final long PROJECTILE_MS = 260L;
    public static final long AREA_MS = 450L;

    public CombatEvent(long timeMs, Position position, int amount, Kind kind) {
        this(timeMs, position, amount, kind, null, null);
    }

    public static CombatEvent projectile(long now, Position from, Position to, String effect) {
        return new CombatEvent(now, to, 0, Kind.PROJECTILE, from, effect);
    }

    public static CombatEvent area(long now, Position center, int radius, String effect) {
        return new CombatEvent(now, center, radius, Kind.AREA, null, effect);
    }

    public boolean isVisualEffect() { return kind == Kind.PROJECTILE || kind == Kind.AREA; }

    /** Cios wrecz (trafiony albo zablokowany) z pozycja atakujacego w {@code from} - klient animuje wypad. */
    public boolean isMeleeStrike() {
        return from != null && (kind == Kind.HIT_MONSTER || kind == Kind.HIT_PLAYER || kind == Kind.BLOCKED);
    }

    /** Czas wypadu atakujacego przy ciosie wrecz (klient). */
    public static final long LUNGE_MS = 180L;
}
