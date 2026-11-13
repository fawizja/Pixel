package pl.fawizja.pixel.client.render;

import pl.fawizja.pixel.world.Direction;

/**
 * Wybor klatki stworzenia (potwora) jak w Tibii: strona (dol, gora, lewo, prawo) z kierunku ruchu albo ataku
 * oraz zastepstwo, gdy potwor nie ma grafiki danej strony. Czysta logika, bez grafiki - latwa do testowania.
 */
public final class CreatureFrames {
    /** Indeksy stron jak w {@link TileArt#DIRS}: przod (dol ekranu), tyl, lewo, prawo. */
    public static final int DOWN = 0, UP = 1, LEFT = 2, RIGHT = 3;
    /**
     * Opcjonalne grafiki skosow (8 kierunkow): pliki klucz_ne, _nw, _se, _sw (+ _walk1/_walk2). Tablica "has" o dlugosci
     * 8 niesie te strony pod indeksami NE..SW; bez nich (tablica krotsza albo false) skos patrzy poziomo jak w Tibii.
     */
    public static final int NE = 4, NW = 5, SE = 6, SW = 7;
    public static final int SIDES = 8;
    /** Przyrostki plikow stron w kolejnosci indeksow 1..7 (indeks 0 = przod bez przyrostka). */
    public static final String[] SIDE_SUFFIX = {"", "_up", "_left", "_right", "_ne", "_nw", "_se", "_sw"};

    /** Indeks strony skosu (NE, NW, SE, SW) albo -1 dla kierunku prostego / null. */
    public static int diagIndex(Direction facing) {
        if (facing == null || !facing.diagonal()) return -1;
        return facing.dx > 0 ? (facing.dy < 0 ? NE : SE) : (facing.dy < 0 ? NW : SW);
    }

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
        int di = diagIndex(facing);
        if (di >= 0 && has != null && has.length >= SIDES && has[di]) return new Pick(di, false);
        int d = dirIndex(facing);
        if (has != null && has[d]) return new Pick(d, false);
        if (d == LEFT) return has != null && has[RIGHT] ? new Pick(RIGHT, true) : new Pick(DOWN, true);
        if (d == RIGHT) return has != null && has[LEFT] ? new Pick(LEFT, true) : new Pick(DOWN, false);
        return new Pick(DOWN, false);
    }

    /** Czy potwor ma wlasna grafike strony (dol zawsze jest). */
    public static boolean hasOwn(boolean[] has, Direction facing) {
        int di = diagIndex(facing);
        if (di >= 0 && has != null && has.length >= SIDES && has[di]) return true;
        int d = dirIndex(facing);
        return d == DOWN || (has != null && has[d]);
    }

    /**
     * Ktore strony potwora maja komplet plikow (postoj + _walk1 + _walk2) wg testu istnienia pliku (sciezka wzgledna
     * w assets/art, np. "monsters/wolf_ne_walk1.png"). Indeks 0 (przod) zawsze true; strona bez kompletu = false.
     */
    public static boolean[] sidesPresent(String key, java.util.function.Predicate<String> exists) {
        boolean[] has = new boolean[SIDES];
        has[0] = true;
        for (int s = 1; s < SIDES; s++) {
            boolean all = true;
            for (int k = 0; k < 3 && all; k++) {
                all = exists.test("monsters/" + key + SIDE_SUFFIX[s] + (k == 0 ? "" : "_walk" + k) + ".png");
            }
            has[s] = all;
        }
        return has;
    }
}
