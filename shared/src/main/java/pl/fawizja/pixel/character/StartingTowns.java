package pl.fawizja.pixel.character;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.Town;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Miasta, w ktorych mozna zaczac gre (jak w Tibii: wybor miasta przy tworzeniu postaci). Kazde miasto swiata
 * ({@link WorldMap#towns()}) poza miastami tylko do zamieszkania ({@link Town#starting()} = false, np. ukryte
 * miasto krasnoludow - jak Kazordoon, trzeba do niego dojsc) jest miastem startowym; postac zaczyna w jego swiatyni i tam ma mieszkanie
 * (odrodzenie po smierci, zwoj powrotu). Zadne nie wymaga premium: do wysp Nerys plywa prom woznicy.
 * Nazwa spoza listy (albo pusta) oznacza miasto domyslne z {@code start.town}.
 */
public final class StartingTowns {

    /** Miasto do wyboru: klucz (male litery, jak {@link PlayerCharacter#homeTown()}), nazwa, swiatynia i opis. */
    public record Info(String key, String name, Position temple, String climate, String description) { }

    /** Klimat i jednozdaniowy opis miasta (po kluczu); miasta spoza tabeli dostaja opis ogolny. */
    private static final Map<String, String[]> DESCRIPTIONS = Map.of(
            "varden", new String[] {"umiarkowany",
                    "Stolica z zamkiem króla i dzielnicą królewską; drogi do wszystkich krain. Najlepsze na początek."},
            "frostholm", new String[] {"mróz i śnieg",
                    "Surowe miasto na północy u stóp Lodowych Szczytów: zamiecie, yeti i lodowe jaskinie."},
            "selvaran", new String[] {"dżungla",
                    "Parne miasto z portem w dżungli na południowym wschodzie: bagna, krokodyle i zarośnięte ruiny."},
            "qasirah", new String[] {"pustynia",
                    "Miasto z piaskowca na zachodniej pustyni: burze piaskowe, oazy i piramida na południu."},
            "valmir", new String[] {"wybrzeże",
                    "Port żeglarzy na południowym wybrzeżu: łagodny klimat, kanały i kopalnia miedzi."},
            "nerys", new String[] {"tropikalne wyspy",
                    "Białe wille wśród palm na Szmaragdowych Wyspach; na kontynent promem woźnicy albo statkiem."});

    private StartingTowns() { }

    /** Miasta startowe w kolejnosci ze swiata (map.properties). */
    public static List<Info> list(WorldMap world) {
        List<Info> out = new ArrayList<>();
        for (Town t : world.towns()) {
            if (!t.starting()) continue;
            String key = key(t.name());
            String[] d = DESCRIPTIONS.getOrDefault(key, new String[] {"", "Miasto ze świątynią, bankiem i sklepami."});
            out.add(new Info(key, t.name(), t.temple(), d[0], d[1]));
        }
        return out;
    }

    /** Czy swiat ma takie miasto startowe (bez rozrozniania wielkosci liter). */
    public static boolean isValid(WorldMap world, String town) {
        if (town == null || town.isBlank()) return false;
        Town t = world.town(town.trim());
        return t != null && t.starting();
    }

    /**
     * Klucz wybranego miasta albo {@code fallback}, gdy wybor jest pusty lub swiat nie ma takiego miasta
     * (stary klient, stary zapis, literowka).
     */
    public static String resolve(WorldMap world, String requested, String fallback) {
        return isValid(world, requested) ? key(requested.trim()) : fallback;
    }

    /** Opis miasta po kluczu albo nazwie (do ekranu tworzenia postaci) albo null. */
    public static Info find(WorldMap world, String town) {
        for (Info i : list(world)) if (i.key().equalsIgnoreCase(town == null ? "" : town.trim())) return i;
        return null;
    }

    static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
