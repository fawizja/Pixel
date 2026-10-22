package pl.fawizja.pixel.house;

/**
 * Wlasciciele domow. Gra offline zna tylko swoja postac; serwer trzyma wspolna liste dla wszystkich graczy
 * (jeden dom = jeden wlasciciel), zapisywana na dysku.
 */
public interface HouseOwners {

    /** Nazwa wlasciciela domu albo null, gdy dom jest wolny. */
    String ownerOf(String houseId);

    /** Przypisuje dom wlascicielowi; false, gdy ktos go juz ma. */
    boolean claim(String houseId, String owner);

    /** Zwalnia dom. */
    void release(String houseId);
}
