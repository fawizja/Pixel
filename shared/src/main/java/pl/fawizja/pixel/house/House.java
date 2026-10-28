package pl.fawizja.pixel.house;

import pl.fawizja.pixel.world.Position;

/**
 * Dom do kupienia w miescie: wnetrze (prostokat bez murow, w domach w ksztalcie litery L plus skrzydla), drzwi i cena
 * w gp. Do wnetrza wchodzi tylko wlasciciel i jego goscie (jak w Tibii); drzwi sa wspolne.
 * wings: kolejne czworki x0, y0, x1, y1 dodatkowych prostokatow wnetrza (pusta tablica dla zwyklego domu).
 */
public record House(String id, String name, String town, int x0, int y0, int x1, int y1, int z, Position door, long price,
                    int[] wings) {

    public House {
        if (x1 < x0 || y1 < y0) throw new IllegalArgumentException("empty house " + id);
        if (price <= 0) throw new IllegalArgumentException("price " + id);
        if (door.z() != z) throw new IllegalArgumentException("door on other floor " + id);
        wings = wings == null ? new int[0] : wings.clone();
        if (wings.length % 4 != 0) throw new IllegalArgumentException("wings " + id);
        for (int i = 0; i < wings.length; i += 4) {
            if (wings[i + 2] < wings[i] || wings[i + 3] < wings[i + 1]) throw new IllegalArgumentException("empty wing " + id);
        }
    }

    public House(String id, String name, String town, int x0, int y0, int x1, int y1, int z, Position door, long price) {
        this(id, name, town, x0, y0, x1, y1, z, door, price, new int[0]);
    }

    public boolean contains(Position p) {
        if (p.z() != z) return false;
        if (p.x() >= x0 && p.x() <= x1 && p.y() >= y0 && p.y() <= y1) return true;
        for (int i = 0; i < wings.length; i += 4) {
            if (p.x() >= wings[i] && p.x() <= wings[i + 2] && p.y() >= wings[i + 1] && p.y() <= wings[i + 3]) return true;
        }
        return false;
    }

    /** Liczba pol wnetrza. */
    public int size() {
        int n = (x1 - x0 + 1) * (y1 - y0 + 1);
        for (int i = 0; i < wings.length; i += 4) n += (wings[i + 2] - wings[i] + 1) * (wings[i + 3] - wings[i + 1] + 1);
        return n;
    }

    @Override
    public int[] wings() { return wings.clone(); }
}
