package pl.fawizja.pixel.character;

/**
 * Umiejetnosci postaci w kolejnosci okna Skills z Tibii: piesci, maczuga, miecz, topor, dystans, tarcza,
 * wedkarstwo, a na koncu magia. Kolejnosc stalych = kolejnosc wierszy w oknie umiejetnosci.
 */
public enum Skill {
    FIST("Pięści"), CLUB("Maczuga"), SWORD("Miecz"), AXE("Topór"), DISTANCE("Dystans"), SHIELDING("Tarcza"),
    FISHING("Wędkarstwo"), MAGIC("Magia");

    private final String label;

    Skill(String label) { this.label = label; }

    /** Nazwa w grze. */
    public String label() { return label; }

    /** Umiejetnosci walki wrecz bronia (miecz, topor, maczuga) - rycerz zaczyna z kazda na tym samym poziomie. */
    public boolean isMeleeWeapon() { return this == SWORD || this == AXE || this == CLUB; }
}
