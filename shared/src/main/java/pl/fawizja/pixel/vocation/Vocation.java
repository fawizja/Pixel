package pl.fawizja.pixel.vocation;

/**
 * Cztery profesje. Kazda ma inny styl gry, a wszystkie liczby sa w jednym miejscu,
 * zeby balans korygowac w danych, a nie wyjatkami w kodzie.
 */
public enum Vocation {

    /** Dystans i swieta magia; wszechstronny, srednie HP i mana. */
    PALADIN("Paladin",
            10, 15, 20,
            2, 3,
            0.85, 1.25, 0.60, 0.85, 1.00,
            2000,
            "Walczy z dystansu wloczniami i strzalami, wspiera sie swieta magia.",
            "Bezpieczny dystans, dobra przezywalnosc, uniwersalne runy.",
            "Slabsza magia obszarowa niz magowie, zuzywa amunicje."),

    /** Leczenie, lod i ziemia; najlepsze wsparcie druzyny. */
    DRUID("Druid",
            5, 30, 10,
            1, 4,
            0.50, 0.50, 1.00, 1.30, 0.80,
            2000,
            "Mag natury: lod i ziemia, najsilniejsze leczenie siebie i innych.",
            "Leczenie, kontrola tlumu, duza mana.",
            "Niskie HP i slaba walka wrecz."),

    /** Ogien i energia; najwyzsze obrazenia magiczne, najmniejsza wytrzymalosc. */
    SORCERER("Sorcerer",
            5, 30, 10,
            1, 4,
            0.50, 0.50, 1.25, 0.70, 0.75,
            2000,
            "Mag zniszczenia: ogien, energia i smierc.",
            "Najwyzsze obrazenia obszarowe i pojedyncze.",
            "Najnizsza obrona i slabe leczenie."),

    /** Walka wrecz piesciami, szybkie ataki i kombinacje; wysoka wytrzymalosc. */
    MONK("Monk",
            13, 6, 22,
            3, 2,
            1.25, 0.40, 0.50, 0.90, 1.15,
            1400,
            "Mistrz walki wrecz: szybkie ciosy, kombinacje i samoleczenie skupieniem.",
            "Najwiecej HP, najszybsze ataki, dobra obrona.",
            "Musi stac przy celu, malo many, prawie brak magii dystansowej.");

    private final String displayName;
    private final int hpPerLevel;
    private final int manaPerLevel;
    private final int capacityPerLevel;
    private final int baseHpRegen;
    private final int baseManaRegen;
    private final double meleeFactor;
    private final double distanceFactor;
    private final double magicFactor;
    private final double healingFactor;
    private final double defenseFactor;
    private final int attackIntervalMs;
    private final String playstyle;
    private final String strengths;
    private final String weaknesses;

    Vocation(String displayName, int hpPerLevel, int manaPerLevel, int capacityPerLevel,
             int baseHpRegen, int baseManaRegen,
             double meleeFactor, double distanceFactor, double magicFactor,
             double healingFactor, double defenseFactor, int attackIntervalMs,
             String playstyle, String strengths, String weaknesses) {
        this.displayName = displayName;
        this.hpPerLevel = hpPerLevel;
        this.manaPerLevel = manaPerLevel;
        this.capacityPerLevel = capacityPerLevel;
        this.baseHpRegen = baseHpRegen;
        this.baseManaRegen = baseManaRegen;
        this.meleeFactor = meleeFactor;
        this.distanceFactor = distanceFactor;
        this.magicFactor = magicFactor;
        this.healingFactor = healingFactor;
        this.defenseFactor = defenseFactor;
        this.attackIntervalMs = attackIntervalMs;
        this.playstyle = playstyle;
        this.strengths = strengths;
        this.weaknesses = weaknesses;
    }

    public String displayName() { return displayName; }
    public int hpPerLevel() { return hpPerLevel; }
    public int manaPerLevel() { return manaPerLevel; }
    public int capacityPerLevel() { return capacityPerLevel; }
    public int baseHpRegen() { return baseHpRegen; }
    public int baseManaRegen() { return baseManaRegen; }
    public double meleeFactor() { return meleeFactor; }
    public double distanceFactor() { return distanceFactor; }
    public double magicFactor() { return magicFactor; }
    public double healingFactor() { return healingFactor; }
    public double defenseFactor() { return defenseFactor; }
    public int attackIntervalMs() { return attackIntervalMs; }
    public String playstyle() { return playstyle; }
    public String strengths() { return strengths; }
    public String weaknesses() { return weaknesses; }
}
