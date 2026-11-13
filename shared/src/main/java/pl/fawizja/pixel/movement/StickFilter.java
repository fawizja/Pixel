package pl.fawizja.pixel.movement;

import pl.fawizja.pixel.world.Direction;

/**
 * Joystick na telefon, 8 kierunkow (2026-11-12, wlasciciel: "8 kierunkow jak w Tibii"): wektor galki 0..1 (os y w gore)
 * zamieniany na kierunek kroku. Zadnego czekania na skos - ruszenie i zmiana kierunku dzieja sie od razu.
 * <ul>
 *   <li>ruch rusza dopiero przy wyraznym wychyleniu ({@link #ENGAGE}), a gasnie ponizej {@link #RELEASE}
 *       (martwa strefa z histereza, spokojniejszy joystick z 2026-10-29),</li>
 *   <li>8 rownych sektorow po 45 stopni (kierunki proste i skosy tak samo szerokie),</li>
 *   <li>histereza kata: biezacy kierunek trzyma sie jeszcze {@link #HYSTERESIS} stopni za granica sektora, wiec
 *       drgajacy kciuk na granicy nie przeskakuje miedzy sasiednimi kierunkami.</li>
 * </ul>
 */
public final class StickFilter {

    /** Wychylenie (0..1), od ktorego postac rusza. */
    public static final float ENGAGE = 0.45f;
    /** Wychylenie, ponizej ktorego postac staje (mniejsze niz ENGAGE - histereza). */
    public static final float RELEASE = 0.30f;
    /** O ile stopni za granica sektora (22,5 od srodka kierunku) biezacy kierunek jeszcze sie trzyma. */
    public static final double HYSTERESIS = 10.0;

    /** Kopia Direction.values() - bez nowej tablicy w kazdej klatce. */
    private static final Direction[] DIRS = Direction.values();

    private Direction current;
    /** Progi z suwaka czulosci ({@link ControlTuning}); domyslnie stale powyzej (czulosc 3). */
    private float engage = ENGAGE, release = RELEASE;

    /** Czulosc 1..5 z ustawien (3 = stale {@link #ENGAGE}, {@link #RELEASE}). */
    public void setSensitivity(int s) {
        engage = ControlTuning.engage(s);
        release = ControlTuning.release(s);
    }

    /** Kierunek dla wychylenia (vx, vy) w chwili nowMs albo null (postoj). */
    public Direction update(float vx, float vy, long nowMs) {
        double len = Math.sqrt(vx * vx + vy * vy);
        if (len < (current == null ? engage : release)) {
            reset();
            return null;
        }
        current = classify(angleOf(vx, vy), current);
        return current;
    }

    /** Puszczona galka: nastepny ruch znow wymaga wyraznego wychylenia. */
    public void reset() {
        current = null;
    }

    public Direction current() { return current; }

    /** Sektor 8 kierunkow dla kata (0 = wschod, 90 = polnoc) z histereza wokol biezacego kierunku (moze byc null). */
    public static Direction classify(double angle, Direction current) {
        if (current != null && dist(angle, current) <= 22.5 + HYSTERESIS) return current;
        Direction best = null;
        for (Direction d : DIRS) if (best == null || dist(angle, d) < dist(angle, best)) best = d;
        return best;
    }

    /** Kat srodka kierunku (0 = wschod, 90 = polnoc). */
    static double angleOf(Direction d) {
        return (Math.toDegrees(Math.atan2(-d.dy, d.dx)) + 360) % 360;
    }

    /** Kat wektora (os y w gore) w stopniach 0..360 (0 = wschod, 90 = polnoc). */
    static double angleOf(float vx, float vy) {
        double angle = Math.toDegrees(Math.atan2(vy, vx));
        return angle < 0 ? angle + 360 : angle;
    }

    static double dist(double angle, Direction d) {
        double a = Math.abs(angle - angleOf(d)) % 360;
        return a > 180 ? 360 - a : a;
    }

    static Direction nearest(double angle, boolean diagonal) {
        Direction best = null;
        for (Direction d : DIRS) {
            if (d.diagonal() != diagonal) continue;
            if (best == null || dist(angle, d) < dist(angle, best)) best = d;
        }
        return best;
    }
}
