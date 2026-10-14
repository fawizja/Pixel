package pl.fawizja.pixel.world;

/** Kierunki ruchu. Os y rosnie w dol mapy (jak w plikach map). */
public enum Direction {
    NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0),
    NORTH_EAST(1, -1), SOUTH_EAST(1, 1), SOUTH_WEST(-1, 1), NORTH_WEST(-1, -1);

    public final int dx;
    public final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public boolean diagonal() { return dx != 0 && dy != 0; }

    /** Kierunek z wektora joysticka (os y w gore dodatnia) lub null w martwej strefie. */
    public static Direction fromVector(float vx, float vy, float deadZone) {
        double len = Math.sqrt(vx * vx + vy * vy);
        if (len < deadZone) return null;
        double angle = Math.toDegrees(Math.atan2(vy, vx));
        if (angle < 0) angle += 360;
        int sector = (int) Math.round(angle / 45.0) % 8;
        switch (sector) {
            case 0: return EAST;
            case 1: return NORTH_EAST;
            case 2: return NORTH;
            case 3: return NORTH_WEST;
            case 4: return WEST;
            case 5: return SOUTH_WEST;
            case 6: return SOUTH;
            default: return SOUTH_EAST;
        }
    }
}
