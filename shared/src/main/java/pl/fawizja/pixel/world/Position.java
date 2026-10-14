package pl.fawizja.pixel.world;

/** Pozycja kafelka w swiecie. z: od -5 (najglebsze podziemia) do +5 (szczyty), 0 = powierzchnia. */
public record Position(int x, int y, int z) {
    public static final int MIN_FLOOR = -5;
    public static final int MAX_FLOOR = 5;

    public Position {
        if (z < MIN_FLOOR || z > MAX_FLOOR) throw new IllegalArgumentException("floor out of range: " + z);
    }

    public Position translate(int dx, int dy) { return new Position(x + dx, y + dy, z); }

    public Position withFloor(int floor) { return new Position(x, y, floor); }

    /** Odleglosc Chebysheva na tym samym pietrze (Integer.MAX_VALUE dla roznych pieter). */
    public int distanceTo(Position o) {
        if (o.z != z) return Integer.MAX_VALUE;
        return Math.max(Math.abs(o.x - x), Math.abs(o.y - y));
    }

    @Override
    public String toString() { return "(" + x + "," + y + "," + (z > 0 ? "+" : "") + z + ")"; }
}
