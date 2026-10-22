package pl.fawizja.pixel.house;

import pl.fawizja.pixel.world.Position;

/**
 * Dom do kupienia w miescie: wnetrze (prostokat bez murow), drzwi i cena w gp.
 * Do wnetrza wchodzi tylko wlasciciel (jak w Tibii); drzwi sa wspolne.
 */
public record House(String id, String name, String town, int x0, int y0, int x1, int y1, int z, Position door, long price) {

    public House {
        if (x1 < x0 || y1 < y0) throw new IllegalArgumentException("empty house " + id);
        if (price <= 0) throw new IllegalArgumentException("price " + id);
        if (door.z() != z) throw new IllegalArgumentException("door on other floor " + id);
    }

    public boolean contains(Position p) {
        return p.z() == z && p.x() >= x0 && p.x() <= x1 && p.y() >= y0 && p.y() <= y1;
    }

    /** Liczba pol wnetrza. */
    public int size() { return (x1 - x0 + 1) * (y1 - y0 + 1); }
}
