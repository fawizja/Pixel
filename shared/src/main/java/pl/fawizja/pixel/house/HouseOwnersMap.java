package pl.fawizja.pixel.house;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Wspolna lista wlascicieli (serwer): tekst "id=wlasciciel" w liniach i "guests:id=Imie,Imie" dla gosci,
 * bezpieczna dla watkow.
 */
public final class HouseOwnersMap implements HouseOwners {
    private static final String GUESTS = "guests:";
    private final Map<String, String> owners = new TreeMap<>();
    private final Map<String, List<String>> guests = new TreeMap<>();

    @Override
    public synchronized String ownerOf(String houseId) { return owners.get(houseId); }

    @Override
    public synchronized boolean claim(String houseId, String owner) {
        if (owners.containsKey(houseId)) return false;
        owners.put(houseId, owner);
        guests.remove(houseId);
        return true;
    }

    @Override
    public synchronized void release(String houseId) {
        owners.remove(houseId);
        guests.remove(houseId);
    }

    @Override
    public synchronized List<String> guests(String houseId) {
        return List.copyOf(guests.getOrDefault(houseId, List.of()));
    }

    @Override
    public synchronized boolean addGuest(String houseId, String name) {
        if (!owners.containsKey(houseId) || name == null || name.isBlank() || name.equals(owners.get(houseId))) return false;
        List<String> g = guests.computeIfAbsent(houseId, k -> new ArrayList<>());
        for (String n : g) if (n.equalsIgnoreCase(name)) return false;
        if (g.size() >= MAX_GUESTS) return false;
        g.add(name.trim());
        return true;
    }

    @Override
    public synchronized boolean removeGuest(String houseId, String name) {
        List<String> g = guests.get(houseId);
        if (g == null || !g.removeIf(n -> n.equalsIgnoreCase(name == null ? "" : name.trim()))) return false;
        if (g.isEmpty()) guests.remove(houseId);
        return true;
    }

    @Override
    public synchronized boolean transfer(String houseId, String newOwner) {
        if (!owners.containsKey(houseId) || newOwner == null || newOwner.isBlank()) return false;
        owners.put(houseId, newOwner);
        guests.remove(houseId);
        return true;
    }

    public synchronized String encode() {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : owners.entrySet()) b.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        for (Map.Entry<String, List<String>> e : guests.entrySet()) {
            if (!e.getValue().isEmpty()) b.append(GUESTS).append(e.getKey()).append('=').append(String.join(",", e.getValue())).append('\n');
        }
        return b.toString();
    }

    public static HouseOwnersMap decode(String text) {
        HouseOwnersMap m = new HouseOwnersMap();
        List<String[]> guestLines = new ArrayList<>();
        for (String line : text.split("\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq).trim(), value = line.substring(eq + 1).trim();
            if (key.startsWith(GUESTS)) guestLines.add(new String[] {key.substring(GUESTS.length()), value});
            else m.owners.put(key, value);
        }
        for (String[] g : guestLines) {
            for (String name : g[1].split(",")) if (!name.isBlank()) m.addGuest(g[0], name.trim());
        }
        return m;
    }
}
