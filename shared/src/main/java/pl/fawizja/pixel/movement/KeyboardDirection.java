package pl.fawizja.pixel.movement;

import pl.fawizja.pixel.world.Direction;

/**
 * Klawiatura, 8 kierunkow: dwa klawisze naraz (np. W + D) to skos, klawisze przeciwne znosza sie; blok numeryczny
 * ma osobne klawisze skosow (1 = poludniowy zachod, 3 = poludniowy wschod, 7 = polnocny zachod, 9 = polnocny wschod).
 * Czysta logika, bez libGDX - klawisze zbiera GameScreen.
 */
public final class KeyboardDirection {

    private KeyboardDirection() {}

    /** Kierunek dla stanu klawiszy (strzalki i WASD zlaczone) albo null (nic / przeciwne klawisze). */
    public static Direction of(boolean left, boolean right, boolean up, boolean down) {
        return of(left, right, up, down, 0);
    }

    /**
     * Jak {@link #of(boolean, boolean, boolean, boolean)} plus klawisz bloku numerycznego (1..9; 0 = brak):
     * 8 N, 2 S, 4 W, 6 E, 7 NW, 9 NE, 1 SW, 3 SE, 5 = nic.
     */
    public static Direction of(boolean left, boolean right, boolean up, boolean down, int numpad) {
        int dx = (right ? 1 : 0) - (left ? 1 : 0);
        int dy = (down ? 1 : 0) - (up ? 1 : 0);
        switch (numpad) {
            case 1: dx -= 1; dy += 1; break;
            case 2: dy += 1; break;
            case 3: dx += 1; dy += 1; break;
            case 4: dx -= 1; break;
            case 6: dx += 1; break;
            case 7: dx -= 1; dy -= 1; break;
            case 8: dy -= 1; break;
            case 9: dx += 1; dy -= 1; break;
            default: break;
        }
        return Direction.fromDelta(Integer.signum(dx), Integer.signum(dy));
    }
}
