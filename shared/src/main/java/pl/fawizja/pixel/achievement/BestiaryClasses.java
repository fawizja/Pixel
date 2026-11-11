package pl.fawizja.pixel.achievement;

import pl.fawizja.pixel.i18n.Messages;
import java.util.LinkedHashMap;
import java.util.Map;

/** Klasy bestiariusza Tibii (kolumna klasa(Tibia) w monsters.csv) z polskimi nazwami, w kolejnosci z Tibii. */
public final class BestiaryClasses {
    private static final Map<String, String> NAMES = new LinkedHashMap<>();

    static {
        NAMES.put("Amphibic", Messages.get("bestiary_classes.plazy"));
        NAMES.put("Aquatic", Messages.get("bestiary_classes.stwory_wodne"));
        NAMES.put("Bird", Messages.get("bestiary_classes.ptaki"));
        NAMES.put("Construct", Messages.get("bestiary_classes.konstrukty"));
        NAMES.put("Demon", Messages.get("bestiary_classes.demony"));
        NAMES.put("Dragon", Messages.get("bestiary_classes.smoki"));
        NAMES.put("Elemental", Messages.get("bestiary_classes.zywiolaki"));
        NAMES.put(Messages.get("bestiary_classes.extra_dimensional"), Messages.get("bestiary_classes.istoty_spoza_wymiaru"));
        NAMES.put("Fey", Messages.get("bestiary_classes.wrozki"));
        NAMES.put("Giant", Messages.get("bestiary_classes.olbrzymy"));
        NAMES.put("Human", Messages.get("bestiary_classes.ludzie"));
        NAMES.put("Humanoid", Messages.get("bestiary_classes.humanoidy"));
        NAMES.put("Lycanthrope", Messages.get("bestiary_classes.likantropy"));
        NAMES.put("Magical", Messages.get("bestiary_classes.istoty_magiczne"));
        NAMES.put("Mammal", Messages.get("bestiary_classes.ssaki"));
        NAMES.put("Plant", Messages.get("bestiary_classes.rosliny"));
        NAMES.put("Reptile", Messages.get("bestiary_classes.gady"));
        NAMES.put("Slime", Messages.get("bestiary_classes.szlamy"));
        NAMES.put("Undead", Messages.get("bestiary_classes.nieumarli"));
        NAMES.put("Vermin", Messages.get("bestiary_classes.robactwo"));
    }

    private BestiaryClasses() {}

    /** Polska nazwa klasy ("Ssaki"); nieznana klasa - jak w danych, pusta - "Inne". */
    public static String label(String tibiaClass) {
        if (tibiaClass == null || tibiaClass.isEmpty()) return Messages.get("bestiary_classes.inne");
        return NAMES.getOrDefault(tibiaClass, tibiaClass);
    }

    /** Klasy w kolejnosci Tibii. */
    public static Iterable<String> all() { return NAMES.keySet(); }

    public static boolean known(String tibiaClass) { return NAMES.containsKey(tibiaClass); }
}
