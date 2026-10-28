package pl.fawizja.pixel.house;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pl.fawizja.pixel.character.PlayerCharacter;

/**
 * Wlasciciele widziani z jednej postaci (gra offline i klient online): wlasna postac jest jedynym znanym
 * wlascicielem, goscie i czynsz jej domu siedza w zapisie postaci. Online serwer podaje dodatkowo, do ktorych
 * cudzych domow postac ma wstep ({@link #setRemoteAccess}), zeby klient od razu wiedzial, gdzie wejdzie.
 */
public final class CharacterHouseOwners implements HouseOwners {
    private final PlayerCharacter pc;
    private final Map<String, HouseAccess> remote = new HashMap<>();

    public CharacterHouseOwners(PlayerCharacter pc) { this.pc = pc; }

    private boolean own(String id) { return id != null && id.equals(pc.house()); }

    private Map<String, HouseAccess> levels() { return HouseOwnersMap.decodeGuests(pc.houseGuests()); }

    @Override
    public String ownerOf(String id) { return own(id) ? pc.name() : null; }

    @Override
    public boolean claim(String id, String owner) {
        if (ownerOf(id) != null) return false;
        pc.setHouseGuests("");
        pc.setRent(0, 0);
        return true;
    }

    @Override
    public void release(String id) {
        if (!own(id)) return;
        pc.setHouseGuests("");
        pc.setRent(0, 0);
    }

    @Override
    public List<String> guests(String id) { return own(id) ? List.copyOf(levels().keySet()) : List.of(); }

    /** Goscie wlasnego domu z poziomami. */
    public Map<String, HouseAccess> guestLevels(String id) { return own(id) ? levels() : Map.of(); }

    @Override
    public HouseAccess guestAccess(String id, String name) {
        if (own(id)) return HouseOwnersMap.find(levels(), name);
        if (name != null && name.equalsIgnoreCase(pc.name())) return remote.getOrDefault(id, HouseAccess.NONE);
        return HouseAccess.NONE;
    }

    @Override
    public boolean addGuest(String id, String name) { return addGuest(id, name, HouseAccess.FULL); }

    @Override
    public boolean addGuest(String id, String name, HouseAccess level) {
        if (!own(id) || name == null || name.isBlank() || name.trim().equalsIgnoreCase(pc.name())) return false;
        Map<String, HouseAccess> g = levels();
        if (!HouseOwnersMap.put(g, name, level, MAX_GUESTS)) return false;
        pc.setHouseGuests(HouseOwnersMap.encodeGuests(g));
        return true;
    }

    @Override
    public boolean removeGuest(String id, String name) {
        if (!own(id)) return false;
        Map<String, HouseAccess> g = levels();
        if (!g.keySet().removeIf(n -> n.equalsIgnoreCase(name == null ? "" : name.trim()))) return false;
        pc.setHouseGuests(HouseOwnersMap.encodeGuests(g));
        return true;
    }

    @Override
    public long rentPaidUntil(String id) { return own(id) ? pc.rentPaidUntil() : 0; }

    @Override
    public int rentMissed(String id) { return own(id) ? pc.rentMissed() : 0; }

    @Override
    public void setRent(String id, long paidUntil, int missed) {
        if (own(id)) pc.setRent(paidUntil, missed);
    }

    /** Klient online: cudze domy, do ktorych postac ma wstep (id -> poziom). */
    public void setRemoteAccess(Map<String, HouseAccess> access) {
        remote.clear();
        if (access != null) remote.putAll(access);
    }
}
