package pl.fawizja.pixel.house;

import java.util.Map;
import java.util.TreeMap;

/** Wspolna lista wlascicieli (serwer): tekst "id=wlasciciel" w liniach, bezpieczna dla watkow. */
public final class HouseOwnersMap implements HouseOwners {
    private final Map<String, String> owners = new TreeMap<>();

    @Override
    public synchronized String ownerOf(String houseId) { return owners.get(houseId); }

    @Override
    public synchronized boolean claim(String houseId, String owner) {
        if (owners.containsKey(houseId)) return false;
        owners.put(houseId, owner);
        return true;
    }

    @Override
    public synchronized void release(String houseId) { owners.remove(houseId); }

    public synchronized String encode() {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : owners.entrySet()) b.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        return b.toString();
    }

    public static HouseOwnersMap decode(String text) {
        HouseOwnersMap m = new HouseOwnersMap();
        for (String line : text.split("\n")) {
            int eq = line.indexOf('=');
            if (eq > 0) m.owners.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
        return m;
    }
}
