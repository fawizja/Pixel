package pl.fawizja.pixel.house;

import java.util.List;

/**
 * Wlasciciele domow. Gra offline zna tylko swoja postac; serwer trzyma wspolna liste dla wszystkich graczy
 * (jeden dom = jeden wlasciciel), zapisywana na dysku. Wlasciciel moze wpuscic gosci z pelnym dostepem albo
 * tylko z wejsciem ({@link HouseAccess}) i przekazac dom innemu graczowi. Kazdy dom ma tez stan czynszu
 * (oplacony do, ile platnosci zaleglych) - patrz {@link HouseRent}.
 */
public interface HouseOwners {

    /** Ilu gosci moze miec jeden dom. */
    int MAX_GUESTS = 10;

    /** Nazwa wlasciciela domu albo null, gdy dom jest wolny. */
    String ownerOf(String houseId);

    /** Przypisuje dom wlascicielowi; false, gdy ktos go juz ma. */
    boolean claim(String houseId, String owner);

    /** Zwalnia dom (goscie traca wstep). */
    void release(String houseId);

    /** Goscie domu (bez wlasciciela). */
    default List<String> guests(String houseId) { return List.of(); }

    /** Poziom dostepu goscia (NONE, gdy nie jest gosciem). */
    default HouseAccess guestAccess(String houseId, String name) {
        if (name == null) return HouseAccess.NONE;
        for (String g : guests(houseId)) if (g.equalsIgnoreCase(name.trim())) return HouseAccess.FULL;
        return HouseAccess.NONE;
    }

    /** Goscie z poziomami dostepu (kolejnosc dodania). */
    default java.util.Map<String, HouseAccess> guestLevels(String houseId) {
        java.util.Map<String, HouseAccess> out = new java.util.LinkedHashMap<>();
        for (String g : guests(houseId)) out.put(g, guestAccess(houseId, g));
        return out;
    }

    /** Wpuszcza goscia z pelnym dostepem; false, gdy juz nim jest albo lista jest pelna. */
    default boolean addGuest(String houseId, String name) { return false; }

    /**
     * Wpuszcza goscia z danym poziomem (ENTRY albo FULL) albo zmienia poziom dotychczasowego goscia; false, gdy
     * nic sie nie zmienilo albo lista jest pelna.
     */
    default boolean addGuest(String houseId, String name, HouseAccess level) {
        return level == HouseAccess.FULL && addGuest(houseId, name);
    }

    /** Odbiera gosciowi wstep; false, gdy nie byl gosciem. */
    default boolean removeGuest(String houseId, String name) { return false; }

    /** Nowy wlasciciel (sprzedaz innemu graczowi); dotychczasowi goscie traca wstep. False, gdy dom nie ma wlasciciela. */
    default boolean transfer(String houseId, String newOwner) { return false; }

    /** Czynsz oplacony do (czas w ms od 1970); 0 = jeszcze nie liczony. */
    default long rentPaidUntil(String houseId) { return 0; }

    /** Ile kolejnych platnosci czynszu jest zaleglych. */
    default int rentMissed(String houseId) { return 0; }

    /** Zapisuje stan czynszu domu. */
    default void setRent(String houseId, long paidUntil, int missed) { }

    /** Dostep postaci do domu: wlasciciel, gosc (pelny albo wejscie) albo brak. */
    default HouseAccess access(String houseId, String name) {
        if (name == null) return HouseAccess.NONE;
        if (name.equals(ownerOf(houseId))) return HouseAccess.OWNER;
        return guestAccess(houseId, name);
    }

    /** Czy postac moze wejsc do domu: wlasciciel albo gosc. */
    default boolean mayEnter(String houseId, String name) {
        return access(houseId, name).mayEnter();
    }
}
