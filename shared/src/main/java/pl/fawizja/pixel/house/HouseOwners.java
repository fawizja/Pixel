package pl.fawizja.pixel.house;

import java.util.List;

/**
 * Wlasciciele domow. Gra offline zna tylko swoja postac; serwer trzyma wspolna liste dla wszystkich graczy
 * (jeden dom = jeden wlasciciel), zapisywana na dysku. Wlasciciel moze wpuscic gosci (wchodza do srodka
 * i korzystaja ze skrzyn) i przekazac dom innemu graczowi.
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

    /** Wpuszcza goscia; false, gdy juz nim jest albo lista jest pelna. */
    default boolean addGuest(String houseId, String name) { return false; }

    /** Odbiera gosciowi wstep; false, gdy nie byl gosciem. */
    default boolean removeGuest(String houseId, String name) { return false; }

    /** Nowy wlasciciel (sprzedaz innemu graczowi); dotychczasowi goscie traca wstep. False, gdy dom nie ma wlasciciela. */
    default boolean transfer(String houseId, String newOwner) { return false; }

    /** Czy postac moze wejsc do domu: wlasciciel albo gosc. */
    default boolean mayEnter(String houseId, String name) {
        if (name == null) return false;
        if (name.equals(ownerOf(houseId))) return true;
        for (String g : guests(houseId)) if (g.equalsIgnoreCase(name)) return true;
        return false;
    }
}
