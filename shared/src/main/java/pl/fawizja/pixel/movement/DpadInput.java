package pl.fawizja.pixel.movement;

import pl.fawizja.pixel.world.Direction;

/**
 * Krzyzyk (D-pad): punkt dotyku wzgledem srodka krzyzyka (os y w gore) na kierunek. Ramiona krzyza maja szerokosc
 * 2/3 promienia; rogi miedzy ramionami to skosy. Maly srodek to postoj.
 */
public final class DpadInput {

    /** Promien martwego srodka jako ulamek promienia krzyzyka. */
    public static final float CENTER = 0.18f;
    /** Polowa szerokosci ramienia jako ulamek promienia (ramie = 2/3 promienia, jak srodkowa kolumna siatki 3 x 3). */
    public static final float ARM = 1f / 3f;
    /** Pole dotyku krzyzyka (ulamek promienia) - wieksze niz rysowane kolo. */
    public static final float TOUCH = 1.4f;

    private DpadInput() {}

    /** Kierunek dla punktu (dx, dy) od srodka krzyzyka o promieniu r albo null (srodek). */
    public static Direction classify(float dx, float dy, float r) {
        if (r <= 0 || dx * dx + dy * dy < CENTER * CENTER * r * r) return null;
        float ax = Math.abs(dx), ay = Math.abs(dy), arm = ARM * r;
        boolean east = dx > 0, north = dy > 0;
        if (ax > arm && ay > arm) {
            return north ? (east ? Direction.NORTH_EAST : Direction.NORTH_WEST) : (east ? Direction.SOUTH_EAST : Direction.SOUTH_WEST);
        }
        if (ax >= ay) return east ? Direction.EAST : Direction.WEST;
        return north ? Direction.NORTH : Direction.SOUTH;
    }

    /** Czy punkt (dx, dy) od srodka lezy w polu dotyku krzyzyka o promieniu r. */
    public static boolean hit(float dx, float dy, float r) {
        return dx * dx + dy * dy <= TOUCH * TOUCH * r * r;
    }
}
