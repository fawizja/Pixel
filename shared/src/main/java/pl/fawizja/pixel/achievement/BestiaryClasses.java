package pl.fawizja.pixel.achievement;

import java.util.LinkedHashMap;
import java.util.Map;

/** Klasy bestiariusza Tibii (kolumna klasa(Tibia) w monsters.csv) z polskimi nazwami, w kolejnosci z Tibii. */
public final class BestiaryClasses {
    private static final Map<String, String> NAMES = new LinkedHashMap<>();

    static {
        NAMES.put("Amphibic", "Płazy");
        NAMES.put("Aquatic", "Stwory wodne");
        NAMES.put("Bird", "Ptaki");
        NAMES.put("Construct", "Konstrukty");
        NAMES.put("Demon", "Demony");
        NAMES.put("Dragon", "Smoki");
        NAMES.put("Elemental", "Żywiołaki");
        NAMES.put("Extra Dimensional", "Istoty spoza wymiaru");
        NAMES.put("Fey", "Wróżki");
        NAMES.put("Giant", "Olbrzymy");
        NAMES.put("Human", "Ludzie");
        NAMES.put("Humanoid", "Humanoidy");
        NAMES.put("Lycanthrope", "Likantropy");
        NAMES.put("Magical", "Istoty magiczne");
        NAMES.put("Mammal", "Ssaki");
        NAMES.put("Plant", "Rośliny");
        NAMES.put("Reptile", "Gady");
        NAMES.put("Slime", "Szlamy");
        NAMES.put("Undead", "Nieumarli");
        NAMES.put("Vermin", "Robactwo");
    }

    private BestiaryClasses() {}

    /** Polska nazwa klasy ("Ssaki"); nieznana klasa - jak w danych, pusta - "Inne". */
    public static String label(String tibiaClass) {
        if (tibiaClass == null || tibiaClass.isEmpty()) return "Inne";
        return NAMES.getOrDefault(tibiaClass, tibiaClass);
    }

    /** Klasy w kolejnosci Tibii. */
    public static Iterable<String> all() { return NAMES.keySet(); }

    public static boolean known(String tibiaClass) { return NAMES.containsKey(tibiaClass); }
}
