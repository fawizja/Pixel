package pl.fawizja.pixel.movement;

import pl.fawizja.pixel.world.Direction;

/**
 * Plywajacy joystick z 4 kierunkami (2026-11-05): wektor galki 0..1 (os y w gore) na kierunek kroku.
 * <ul>
 *   <li>martwa strefa z histereza ({@link ControlTuning#engage}, {@link ControlTuning#release}),</li>
 *   <li>domyslnie tylko 4 kierunki proste; zmiana prostego na sasiedni dopiero {@link #HYSTERESIS} stopni za granica,</li>
 *   <li>skos tylko, gdy galka stoi w waskim klinie skosu przez {@link ControlTuning#float4DiagonalHoldMs} ms,</li>
 *   <li>kierunek zablokowany na czas kroku: w trakcie kroku zwracany jest kierunek, ktorym krok sie zaczal.</li>
 * </ul>
 */
public final class FourWayStick {

    public static final double HYSTERESIS = 10.0;

    private float engage, release;
    private double diagonalHalf;
    private long diagonalHoldMs;

    private Direction current;
    private Direction locked;
    /** Skos, w ktorego klinie stoi galka, i od kiedy (-1: galka poza klinem skosu). */
    private Direction diagonalWanted;
    private long diagonalSinceMs;

    public FourWayStick() { setSensitivity(ControlTuning.DEFAULT); }

    public void setSensitivity(int s) {
        engage = ControlTuning.engage(s);
        release = ControlTuning.release(s);
        diagonalHalf = ControlTuning.float4Diagonal(s);
        diagonalHoldMs = ControlTuning.float4DiagonalHoldMs(s);
    }

    /**
     * Kierunek dla wychylenia (vx, vy) w chwili nowMs albo null (postoj). stepping: postac jest w trakcie kroku -
     * zwracany jest wtedy kierunek, ktorym ten krok sie zaczal.
     */
    public Direction update(float vx, float vy, long nowMs, boolean stepping) {
        float len2 = vx * vx + vy * vy;
        float min = current == null ? engage : release;
        if (len2 < min * min) {
            reset();
            return null;
        }
        double angle = StickFilter.angleOf(vx, vy);
        Direction diag = StickFilter.nearest(angle, true);
        Direction want;
        if (current != null && current.diagonal() && StickFilter.dist(angle, current) <= diagonalHalf + HYSTERESIS) {
            want = current; // raz wybrany skos trzyma sie w nieco szerszym klinie
        } else {
            boolean inWedge = StickFilter.dist(angle, diag) <= diagonalHalf;
            if (!inWedge) {
                diagonalWanted = null;
            } else if (diagonalWanted != diag) {
                diagonalWanted = diag;
                diagonalSinceMs = nowMs;
            }
            if (inWedge && nowMs - diagonalSinceMs >= diagonalHoldMs) want = diag;
            else if (current != null && !current.diagonal() && StickFilter.dist(angle, current) <= 45 + HYSTERESIS) want = current;
            else want = StickFilter.nearest(angle, false);
        }
        current = want;
        if (!stepping || locked == null) locked = current;
        return locked;
    }

    /** Puszczona galka. */
    public void reset() {
        current = null;
        locked = null;
        diagonalWanted = null;
    }

    public Direction current() { return current; }
}
