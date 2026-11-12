package pl.fawizja.pixel.npc;

import java.util.Objects;
import pl.fawizja.pixel.i18n.Messages;
import pl.fawizja.pixel.world.Position;

/** Postac niezalezna stojaca w miescie. shopKey tylko dla kupcow. */
public record Npc(String key, String name, NpcRole role, String town, Position position, String shopKey,
                  String greeting, String color) {

    /** Imie (z tytulem) w biezacym jezyku. */
    @Override
    public String name() { return Messages.data("npc", key, name); }

    /** Powitanie w biezacym jezyku. */
    @Override
    public String greeting() { return Messages.data("npc", key, "greeting", greeting); }

    /** Maksymalna odleglosc rozmowy (w kafelkach, przez lade). */
    public static final int TALK_RANGE = 2;

    /** Klucz skrzyni depozytu (pole mapy dzialajace jak bankier w widoku depozytu). */
    public static final String DEPOT_LOCKER = "depot_locker";
    /** Otwarta zwykla skrzynia (schowek na mapie) - okno jak u bankiera w widoku depozytu. */
    public static final String CRATE = "crate";
    /** Posag treningowy offline w swiatyni (obiekt mapy training_statue): okno wyboru umiejetnosci. */
    public static final String TRAINING_STATUE = "training_statue";

    /**
     * Kowal (klucz "*_smith", np. varden_smith): ulepszenia, zaklecia, Kuznia i nasycenia. Inni kupcy (prowiant, magia,
     * skup) tego nie robia.
     */
    public boolean isSmith() { return role == NpcRole.MERCHANT && key.endsWith("_smith"); }

    /** Opiekun zwierzat (klucz "*_petkeeper"): kupiec, ktory sprzedaje tylko pupila swojego miasta i niczego nie skupuje. */
    public boolean isPetKeeper() { return role == NpcRole.MERCHANT && key.endsWith("_petkeeper"); }

    /** Nasycenia (imbuementy) robi kowal i zaklinacz miasta. */
    public boolean imbues() { return isSmith() || role == NpcRole.IMBUER; }

    public Npc {
        Objects.requireNonNull(key);
        Objects.requireNonNull(role);
        Objects.requireNonNull(position);
        if (role == NpcRole.MERCHANT && (shopKey == null || shopKey.isEmpty())) {
            throw new IllegalArgumentException("merchant without shop: " + key);
        }
    }
}
