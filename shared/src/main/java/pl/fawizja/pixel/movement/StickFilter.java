package pl.fawizja.pixel.movement;

import pl.fawizja.pixel.world.Direction;

/**
 * Spokojniejszy joystick na telefon (2026-10-29, Darek: "joystick za czuly"): wektor galki 0..1 (os y w gore)
 * zamieniany na kierunek kroku z histereza.
 * <ul>
 *   <li>ruch rusza dopiero przy wyraznym wychyleniu ({@link #ENGAGE}), a gasnie ponizej {@link #RELEASE},</li>
 *   <li>kierunki proste maja pierwszenstwo: skos tylko w waskim klinie wokol 45 stopni ({@link #DIAGONAL_ENTER})
 *       i przy mocnym wychyleniu ({@link #DIAGONAL_PUSH}); raz wybrany skos trzyma sie w szerszym klinie,</li>
 *   <li>zmiana kierunku prostego na inny prosty wymaga przejscia {@link #HYSTERESIS} stopni za granice,</li>
 *   <li>nowy kierunek musi sie utrzymac {@link #SETTLE_MS} ms, zanim zastapi biezacy (bez drgan kciuka).</li>
 * </ul>
 */
public final class StickFilter {

    /** Wychylenie (0..1), od ktorego postac rusza. */
    public static final float ENGAGE = 0.45f;
    /** Wychylenie, ponizej ktorego postac staje (mniejsze niz ENGAGE - histereza). */
    public static final float RELEASE = 0.30f;
    /** Polowa klina skosu przy wejsciu w skos (stopnie wokol 45). */
    public static final double DIAGONAL_ENTER = 10.0;
    /** Polowa klina, w ktorym raz wybrany skos sie utrzymuje. */
    public static final double DIAGONAL_KEEP = 18.0;
    /** Minimalne wychylenie dla skosu. */
    public static final float DIAGONAL_PUSH = 0.6f;
    /** O ile stopni za granica 45 trzeba wyjsc, zeby zmienic kierunek prosty na sasiedni. */
    public static final double HYSTERESIS = 8.0;
    /** Ile ms nowy kierunek musi trwac, zanim zastapi poprzedni. */
    public static final long SETTLE_MS = 70;

    private Direction current;
    private Direction pending;
    private long pendingSinceMs;

    /** Kierunek dla wychylenia (vx, vy) w chwili nowMs albo null (postoj). */
    public Direction update(float vx, float vy, long nowMs) {
        double len = Math.sqrt(vx * vx + vy * vy);
        if (len < (current == null ? ENGAGE : RELEASE)) {
            reset();
            return null;
        }
        double angle = Math.toDegrees(Math.atan2(vy, vx));
        if (angle < 0) angle += 360;
        Direction want = classify(angle, len);
        if (current == null) {
            current = want;
            pending = null;
            return current;
        }
        if (want == current) {
            pending = null;
        } else if (want != pending) {
            pending = want;
            pendingSinceMs = nowMs;
        } else if (nowMs - pendingSinceMs >= SETTLE_MS) {
            current = want;
            pending = null;
        }
        return current;
    }

    /** Puszczona galka: nastepny ruch znow wymaga wyraznego wychylenia. */
    public void reset() {
        current = null;
        pending = null;
    }

    public Direction current() { return current; }

    private Direction classify(double angle, double len) {
        Direction diag = nearest(angle, true), card = nearest(angle, false);
        if (current != null && current.diagonal() && len >= RELEASE && dist(angle, current) <= DIAGONAL_KEEP) return current;
        if (len >= DIAGONAL_PUSH && dist(angle, diag) <= DIAGONAL_ENTER) return diag;
        if (current != null && !current.diagonal() && dist(angle, current) <= 45 + HYSTERESIS) return current;
        return card;
    }

    /** Kat srodka kierunku (0 = wschod, 90 = polnoc). */
    static double angleOf(Direction d) {
        return (Math.toDegrees(Math.atan2(-d.dy, d.dx)) + 360) % 360;
    }

    private static double dist(double angle, Direction d) {
        double a = Math.abs(angle - angleOf(d)) % 360;
        return a > 180 ? 360 - a : a;
    }

    private static Direction nearest(double angle, boolean diagonal) {
        Direction best = null;
        for (Direction d : Direction.values()) {
            if (d.diagonal() != diagonal) continue;
            if (best == null || dist(angle, d) < dist(angle, best)) best = d;
        }
        return best;
    }
}
