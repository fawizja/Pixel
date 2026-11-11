package pl.fawizja.pixel.world;

/**
 * Miasto: nazwa i swiatynia (miejsce odrodzenia, kaplan pozwala tu zamieszkac). Miasta startowe mozna wybrac przy
 * tworzeniu postaci; miasto tylko do zamieszkania ({@code starting = false}, map.properties {@code residence_only=})
 * ma swiatynie i uslugi, ale trzeba do niego dojsc (ukryte miasto krasnoludow pod gorami, jak Kazordoon w Tibii).
 * <p>
 * {@code landmass}: 0 = kontynent glowny (jedno polaczone ladem terytorium z Varden), 1+ = ziemie premium odciete
 * woda (map.properties {@code premium_towns=Miasto:numer}); na nie nie ma mostow ani drog z kontynentu - tylko statek
 * (kapitan, premium albo promocja). Woznice jezdza tylko w obrebie jednego ladu.
 */
public record Town(String name, Position temple, boolean starting, int landmass) {
    public static final int MAX_TOWNS = 8;

    public Town(String name, Position temple, boolean starting) { this(name, temple, starting, 0); }

    public Town(String name, Position temple) { this(name, temple, true, 0); }

    /** Miasto na ziemi premium (poza kontynentem glownym). */
    public boolean premium() { return landmass > 0; }
}
