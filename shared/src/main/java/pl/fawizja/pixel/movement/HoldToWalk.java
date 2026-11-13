package pl.fawizja.pixel.movement;

import pl.fawizja.pixel.world.Direction;

/**
 * Wariant "Przytrzymanie na mapie" (2026-11-05): bez galki, palec przytrzymany na widoku gry prowadzi postac w strone
 * palca, jak chodzenie mysza w Tibii.
 * <ul>
 *   <li>krotkie dotkniecie (krocej niz {@link ControlTuning#holdDelayMs}) zostaje zwyklym dotknieciem pola:
 *       marsz do pola, cel, rozmowa, uzycie - {@link #release} zwraca wtedy true,</li>
 *   <li>po przytrzymaniu: kierunek od postaci do palca, 8 rownych sektorow po 45 stopni,</li>
 *   <li>palec na polu postaci ({@link #DEAD_ZONE}) = postoj,</li>
 *   <li>kierunek zablokowany na czas kroku.</li>
 * </ul>
 */
public final class HoldToWalk {

    /** Palec blizej niz tyle pol od srodka postaci w obu osiach: postoj. */
    public static final float DEAD_ZONE = 0.5f;

    private long delayMs = ControlTuning.holdDelayMs(ControlTuning.DEFAULT);
    private boolean down;
    private boolean walking;
    private long downMs;
    private Direction locked;

    public void setSensitivity(int s) { delayMs = ControlTuning.holdDelayMs(s); }

    public long delayMs() { return delayMs; }

    /** Palec dotknal widoku gry. */
    public void press(long nowMs) {
        down = true;
        walking = false;
        downMs = nowMs;
        locked = null;
    }

    /**
     * Kierunek marszu w chwili nowMs albo null. (dx, dy): palec wzgledem srodka postaci w polach mapy (x na wschod,
     * y na poludnie). stepping: postac jest w trakcie kroku - zwracany jest kierunek, ktorym krok sie zaczal.
     */
    public Direction update(float dx, float dy, long nowMs, boolean stepping) {
        if (!down) return null;
        if (!walking) {
            if (nowMs - downMs < delayMs) return null;
            walking = true;
        }
        if (stepping && locked != null) return locked;
        locked = classify(dx, dy);
        return locked;
    }

    /** Puszczenie palca: true, jesli to bylo krotkie dotkniecie (do obsluzenia jak dotkniecie pola). */
    public boolean release(long nowMs) {
        boolean tap = down && !walking && nowMs - downMs < delayMs;
        cancel();
        return tap;
    }

    /** Przerwanie bez dotkniecia pola (np. drugi palec, okno). */
    public void cancel() {
        down = false;
        walking = false;
        locked = null;
    }

    public boolean down() { return down; }
    /** Przytrzymanie przeszlo prog: palec prowadzi postac. */
    public boolean walking() { return walking; }
    /** Postep czekania na prog 0..1 (do rysowania pierscienia pod palcem). */
    public float progress(long nowMs) {
        if (!down) return 0f;
        if (walking) return 1f;
        return Math.min(1f, Math.max(0f, (nowMs - downMs) / (float) Math.max(1, delayMs)));
    }

    /** Kierunek od postaci do palca (dx na wschod, dy na poludnie, w polach) albo null na polu postaci. */
    public static Direction classify(float dx, float dy) {
        if (Math.abs(dx) < DEAD_ZONE && Math.abs(dy) < DEAD_ZONE) return null;
        double angle = StickFilter.angleOf(dx, -dy);
        return StickFilter.classify(angle, null);
    }
}
