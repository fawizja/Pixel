package pl.fawizja.pixel.house;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Wspolna lista wlascicieli (serwer), bezpieczna dla watkow. Tekst w liniach:
 * "id=wlasciciel", "guests:id=Imie:full,Imie:entry" (goscie z poziomem dostepu; stare zapisy "guests:id=Imie,Imie"
 * bez poziomu wczytuja sie jako pelny dostep) i "rent:id=oplacony_do_ms,zalegle" (czynsz).
 */
public final class HouseOwnersMap implements HouseOwners {
    private static final String GUESTS = "guests:";
    private static final String RENT = "rent:";
    private final Map<String, String> owners = new TreeMap<>();
    private final Map<String, Map<String, HouseAccess>> guests = new TreeMap<>();
    private final Map<String, long[]> rent = new TreeMap<>();

    @Override
    public synchronized String ownerOf(String houseId) { return owners.get(houseId); }

    @Override
    public synchronized boolean claim(String houseId, String owner) {
        if (owners.containsKey(houseId)) return false;
        owners.put(houseId, owner);
        guests.remove(houseId);
        rent.remove(houseId);
        return true;
    }

    @Override
    public synchronized void release(String houseId) {
        owners.remove(houseId);
        guests.remove(houseId);
        rent.remove(houseId);
    }

    @Override
    public synchronized List<String> guests(String houseId) {
        return List.copyOf(guests.getOrDefault(houseId, Map.of()).keySet());
    }

    /** Goscie z poziomami (kolejnosc dodania). */
    public synchronized Map<String, HouseAccess> guestLevels(String houseId) {
        return new LinkedHashMap<>(guests.getOrDefault(houseId, Map.of()));
    }

    @Override
    public synchronized HouseAccess guestAccess(String houseId, String name) {
        return find(guests.get(houseId), name);
    }

    @Override
    public synchronized boolean addGuest(String houseId, String name) { return addGuest(houseId, name, HouseAccess.FULL); }

    @Override
    public synchronized boolean addGuest(String houseId, String name, HouseAccess level) {
        if (!owners.containsKey(houseId) || name == null || name.isBlank() || name.trim().equals(owners.get(houseId))) return false;
        Map<String, HouseAccess> g = guests.computeIfAbsent(houseId, k -> new LinkedHashMap<>());
        return put(g, name, level, MAX_GUESTS);
    }

    @Override
    public synchronized boolean removeGuest(String houseId, String name) {
        Map<String, HouseAccess> g = guests.get(houseId);
        if (g == null || !g.keySet().removeIf(n -> n.equalsIgnoreCase(name == null ? "" : name.trim()))) return false;
        if (g.isEmpty()) guests.remove(houseId);
        return true;
    }

    @Override
    public synchronized boolean transfer(String houseId, String newOwner) {
        if (!owners.containsKey(houseId) || newOwner == null || newOwner.isBlank()) return false;
        owners.put(houseId, newOwner);
        guests.remove(houseId);
        return true;                                             // czynsz oplacony do konca okresu przechodzi z domem
    }

    @Override
    public synchronized long rentPaidUntil(String houseId) {
        long[] r = rent.get(houseId);
        return r == null ? 0 : r[0];
    }

    @Override
    public synchronized int rentMissed(String houseId) {
        long[] r = rent.get(houseId);
        return r == null ? 0 : (int) r[1];
    }

    @Override
    public synchronized void setRent(String houseId, long paidUntil, int missed) {
        if (!owners.containsKey(houseId)) return;
        rent.put(houseId, new long[] {paidUntil, Math.max(0, missed)});
    }

    /** Domy z wlascicielem (id -> wlasciciel). */
    public synchronized Map<String, String> owned() { return new TreeMap<>(owners); }

    // ------------------------------------------------------------------ poziomy gosci (wspolne z gra offline)

    /** Poziom goscia z mapy (bez wzgledu na wielkosc liter). */
    static HouseAccess find(Map<String, HouseAccess> g, String name) {
        if (g == null || name == null) return HouseAccess.NONE;
        for (Map.Entry<String, HouseAccess> e : g.entrySet()) if (e.getKey().equalsIgnoreCase(name.trim())) return e.getValue();
        return HouseAccess.NONE;
    }

    /** Dodaje goscia albo zmienia jego poziom; false, gdy bez zmian albo brak miejsca. */
    static boolean put(Map<String, HouseAccess> g, String name, HouseAccess level, int max) {
        if (level != HouseAccess.ENTRY && level != HouseAccess.FULL) return false;
        String who = name.trim();
        for (Map.Entry<String, HouseAccess> e : g.entrySet()) {
            if (e.getKey().equalsIgnoreCase(who)) {
                if (e.getValue() == level) return false;
                e.setValue(level);
                return true;
            }
        }
        if (g.size() >= max) return false;
        g.put(who, level);
        return true;
    }

    /** "Imie:full,Imie:entry". */
    public static String encodeGuests(Map<String, HouseAccess> g) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, HouseAccess> e : g.entrySet()) out.add(e.getKey() + ":" + e.getValue().code());
        return String.join(",", out);
    }

    /** Odwrotnosc {@link #encodeGuests}; imie bez poziomu (stary zapis) = pelny dostep. */
    public static Map<String, HouseAccess> decodeGuests(String text) {
        Map<String, HouseAccess> g = new LinkedHashMap<>();
        if (text == null) return g;
        for (String part : text.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) continue;
            int c = t.lastIndexOf(':');
            String name = c > 0 ? t.substring(0, c).trim() : t;
            HouseAccess level = c > 0 ? HouseAccess.guestLevel(t.substring(c + 1)) : HouseAccess.FULL;
            if (!name.isEmpty()) put(g, name, level, MAX_GUESTS);
        }
        return g;
    }

    // ------------------------------------------------------------------ zapis

    public synchronized String encode() {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : owners.entrySet()) b.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        for (Map.Entry<String, Map<String, HouseAccess>> e : guests.entrySet()) {
            if (!e.getValue().isEmpty()) b.append(GUESTS).append(e.getKey()).append('=').append(encodeGuests(e.getValue())).append('\n');
        }
        for (Map.Entry<String, long[]> e : rent.entrySet()) {
            b.append(RENT).append(e.getKey()).append('=').append(e.getValue()[0]).append(',').append(e.getValue()[1]).append('\n');
        }
        return b.toString();
    }

    public static HouseOwnersMap decode(String text) {
        HouseOwnersMap m = new HouseOwnersMap();
        List<String[]> guestLines = new ArrayList<>();
        List<String[]> rentLines = new ArrayList<>();
        for (String line : text.split("\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq).trim(), value = line.substring(eq + 1).trim();
            if (key.startsWith(GUESTS)) guestLines.add(new String[] {key.substring(GUESTS.length()), value});
            else if (key.startsWith(RENT)) rentLines.add(new String[] {key.substring(RENT.length()), value});
            else m.owners.put(key, value);
        }
        for (String[] g : guestLines) {
            for (Map.Entry<String, HouseAccess> e : decodeGuests(g[1]).entrySet()) m.addGuest(g[0], e.getKey(), e.getValue());
        }
        for (String[] r : rentLines) {
            try {
                String[] v = r[1].split(",");
                m.setRent(r[0], Long.parseLong(v[0].trim()), v.length > 1 ? Integer.parseInt(v[1].trim()) : 0);
            } catch (RuntimeException e) {
                // uszkodzona linia czynszu: dom zacznie nowy okres
            }
        }
        return m;
    }
}
