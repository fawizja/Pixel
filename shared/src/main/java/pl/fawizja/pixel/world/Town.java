package pl.fawizja.pixel.world;

/**
 * Miasto: nazwa i swiatynia (miejsce odrodzenia, kaplan pozwala tu zamieszkac). Miasta startowe mozna wybrac przy
 * tworzeniu postaci; miasto tylko do zamieszkania ({@code starting = false}, map.properties {@code residence_only=})
 * ma swiatynie i uslugi, ale trzeba do niego dojsc (ukryte miasto krasnoludow pod gorami, jak Kazordoon w Tibii).
 */
public record Town(String name, Position temple, boolean starting) {
    public static final int MAX_TOWNS = 8;

    public Town(String name, Position temple) { this(name, temple, true); }
}
