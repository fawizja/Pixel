package pl.fawizja.pixel.world;

/** Obszar swiata z zalecanym przedzialem poziomow (ostrzezenia dla graczy, testy balansu). */
public record Region(String name, int x0, int y0, int x1, int y1, int zMin, int zMax, int levelMin, int levelMax) {
    public Region {
        if (x1 < x0 || y1 < y0 || zMax < zMin || levelMax < levelMin || levelMin < 1) {
            throw new IllegalArgumentException("bad region " + name);
        }
    }

    public boolean contains(Position p) {
        return p.x() >= x0 && p.x() <= x1 && p.y() >= y0 && p.y() <= y1 && p.z() >= zMin && p.z() <= zMax;
    }
}
