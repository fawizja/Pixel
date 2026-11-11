package pl.fawizja.pixel.client.render;

import pl.fawizja.pixel.world.Direction;

/**
 * Wybor klatki stworzenia (potwora) jak w Tibii: strona (dol, gora, lewo, prawo) z kierunku ruchu albo ataku
 * oraz zastepstwo, gdy potwor nie ma grafiki danej strony. Czysta logika, bez grafiki - latwa do testowania.
 */
public final class CreatureFrames {
    /** Indeksy stron jak w {@link TileArt#DIRS}: przod (dol ekranu), tyl, lewo, prawo. */
    public static final int DOWN = 0, UP = 1, LEFT = 2, RIGHT = 3;

    private CreatureFrames() {}

    /** Wynik wyboru: indeks strony z plikami i czy obrazek odbic w poziomie. */
    public record Pick(int dir, boolean flip) {}

    /** Strona ekranu dla kierunku (skos patrzy w lewo albo w prawo, brak kierunku = przod). */
    public static int dirIndex(Direction facing) {
        if (facing == null) return DOWN;
        switch (facing.cardinal()) {
            case NORTH: return UP;
            case WEST: return LEFT;
            case EAST: return RIGHT;
            default: return DOWN;
        }
    }

    /**
     * Zastepstwo dla brakujacej strony: lewo/prawo odbija druga z tych stron, a gdy obu brak - przod odbity dla
     * lewej strony (jak dotad); tyl bez wlasnej grafiki zostaje przodem.
     */
    public static Pick pick(boolean[] has, Direction facing) {
        int d = dirIndex(facing);
        if (has != null && has[d]) return new Pick(d, false);
        if (d == LEFT) return has != null && has[RIGHT] ? new Pick(RIGHT, true) : new Pick(DOWN, true);
        if (d == RIGHT) return has != null && has[LEFT] ? new Pick(LEFT, true) : new Pick(DOWN, false);
        return new Pick(DOWN, false);
    }

    /** Czy potwor ma wlasna grafike strony (dol zawsze jest). */
    public static boolean hasOwn(boolean[] has, Direction facing) {
        int d = dirIndex(facing);
        return d == DOWN || (has != null && has[d]);
    }
}
