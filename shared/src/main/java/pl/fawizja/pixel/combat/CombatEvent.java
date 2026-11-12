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

    /** Efekt pocisku z nazwa grafiki: "TYP/pocisk", np. "PHYSICAL/spear" (wlocznia), "FIRE/fire" (kula ognia). */
    public static String withMissile(String type, String missile) {
        return missile == null || missile.isBlank() ? type : type + "/" + missile;
    }

    /** Rodzaj obrazen z efektu ("PHYSICAL/spear" -> "PHYSICAL") albo null. */
    public String effectType() {
        if (effect == null) return null;
        int i = effect.indexOf('/');
        return i < 0 ? effect : effect.substring(0, i);
    }

    /** Nazwa grafiki pocisku z efektu ("PHYSICAL/spear" -> "spear") albo null, gdy wybiera klient wg rodzaju obrazen. */
    public String missileName() {
        if (effect == null) return null;
        int i = effect.indexOf('/');
        return i < 0 ? null : effect.substring(i + 1);
    }

    public static CombatEvent area(long now, Position center, int radius, String effect) {
        return new CombatEvent(now, center, radius, Kind.AREA, null, effect);
    }

    /**
     * Efekt na polach obszaru czaru (ksztalt z Canary, {@link pl.fawizja.pixel.magic.SpellArea#encode}): amount = kod
     * ksztaltu i kierunku (ujemny), from = pole rzucajacego dla czarow kierunkowych (linia wzroku), inaczej null.
     */
    public static CombatEvent shaped(long now, Position anchor, int shapeCode, Position origin, String effect) {
        return new CombatEvent(now, anchor, shapeCode, Kind.AREA, origin == null || origin.equals(anchor) ? null : origin, effect);
    }

    /** Czy to efekt obszaru z ksztaltem czaru (a nie stary kwadrat o promieniu amount). */
    public boolean isShapedArea() { return kind == Kind.AREA && amount < 0; }

    public boolean isVisualEffect() { return kind == Kind.PROJECTILE || kind == Kind.AREA; }

    /** Cios wrecz (trafiony albo zablokowany) z pozycja atakujacego w {@code from} - klient animuje wypad. */
    public boolean isMeleeStrike() {
        return from != null && (kind == Kind.HIT_MONSTER || kind == Kind.HIT_PLAYER || kind == Kind.BLOCKED);
    }

    /** Czas wypadu atakujacego przy ciosie wrecz (klient). */
    public static final long LUNGE_MS = 180L;
}
