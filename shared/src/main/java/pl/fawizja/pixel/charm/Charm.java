package pl.fawizja.pixel.charm;

import pl.fawizja.pixel.combat.DamageType;

/**
 * 25 charmow bestiariusza jak w obecnej Tibii (przebudowa 2025: charmy Major i Minor, stopnie I / II / III).
 * Szanse (albo wartosci) i koszty kolejnych stopni z Canary data/scripts/systems/bestiary_charms.lua; Major kupuje sie
 * za punkty charmow, Minor za echa charmow (Minor Charm Echoes). Kolejnosc = identyfikatory Canary (charmRune_t).
 */
public enum Charm {
    WOUND("Rana", "Wound", Category.MAJOR, Kind.OFFENSIVE, DamageType.PHYSICAL,
            "zadaje 5% maks. zdrowia potwora jako obrażenia fizyczne (najwyżej 2 x twój poziom)", t(5, 10, 11), c(240, 360, 1200)),
    ENFLAME("Podpalenie", "Enflame", Category.MAJOR, Kind.OFFENSIVE, DamageType.FIRE,
            "zadaje 5% maks. zdrowia potwora jako obrażenia od ognia (najwyżej 2 x twój poziom)", t(5, 10, 11), c(400, 600, 2000)),
    POISON("Trucizna", "Poison", Category.MAJOR, Kind.OFFENSIVE, DamageType.EARTH,
            "zadaje 5% maks. zdrowia potwora jako obrażenia od ziemi (najwyżej 2 x twój poziom)", t(5, 10, 11), c(240, 360, 1200)),
    FREEZE("Zamrożenie", "Freeze", Category.MAJOR, Kind.OFFENSIVE, DamageType.ICE,
            "zadaje 5% maks. zdrowia potwora jako obrażenia od lodu (najwyżej 2 x twój poziom)", t(5, 10, 11), c(320, 480, 1600)),
    ZAP("Porażenie", "Zap", Category.MAJOR, Kind.OFFENSIVE, DamageType.ENERGY,
            "zadaje 5% maks. zdrowia potwora jako obrażenia od energii (najwyżej 2 x twój poziom)", t(5, 10, 11), c(320, 480, 1600)),
    CURSE("Klątwa", "Curse", Category.MAJOR, Kind.OFFENSIVE, DamageType.DEATH,
            "zadaje 5% maks. zdrowia potwora jako obrażenia od śmierci (najwyżej 2 x twój poziom)", t(5, 10, 11), c(360, 540, 1800)),
    CRIPPLE("Okaleczenie", "Cripple", Category.MINOR, Kind.OFFENSIVE, null,
            "paraliżuje trafionego potwora na 10 s", t(6, 9, 12), c(100, 150, 225)),
    PARRY("Riposta", "Parry", Category.MAJOR, Kind.DEFENSIVE, null,
            "odbija otrzymane obrażenia w atakującego potwora", t(5, 10, 11), c(400, 600, 2000)),
    DODGE("Unik", "Dodge", Category.MAJOR, Kind.DEFENSIVE, null,
            "unikasz ataku potwora bez żadnych obrażeń", t(5, 10, 11), c(240, 360, 1200)),
    ADRENALINE_BURST("Zastrzyk adrenaliny", "Adrenaline Burst", Category.MINOR, Kind.DEFENSIVE, null,
            "po otrzymaniu ciosu biegasz szybciej przez 10 s", t(6, 9, 12), c(100, 150, 225)),
    NUMB("Odrętwienie", "Numb", Category.MINOR, Kind.DEFENSIVE, null,
            "po ataku potwora paraliżujesz go na 10 s", t(6, 9, 12), c(100, 150, 225)),
    CLEANSE("Oczyszczenie", "Cleanse", Category.MINOR, Kind.DEFENSIVE, null,
            "oczyszcza cię z żywiołowego trafienia potwora - nie zadaje ono obrażeń", t(6, 9, 12), c(100, 150, 225)),
    BLESS("Błogosławieństwo", "Bless", Category.MINOR, Kind.PASSIVE, null,
            "mniejsza utrata doświadczenia, gdy zabije cię ten potwór", t(6, 9, 12), c(100, 150, 225)),
    SCAVENGE("Skórowanie", "Scavenge", Category.MINOR, Kind.PASSIVE, null,
            "większa szansa na najrzadsze trofeum z ciała potwora (skóra, łuska, esencja)", t(60, 90, 120), c(100, 150, 225)),
    GUT("Patroszenie", "Gut", Category.MINOR, Kind.PASSIVE, null,
            "większa szansa na produkty z potworów (materiały z łupu)", t(6, 9, 12), c(100, 150, 225)),
    LOW_BLOW("Niski cios", "Low Blow", Category.MAJOR, Kind.PASSIVE, null,
            "dodaje szansę na cios krytyczny przeciw temu potworowi", t(4, 8, 9), c(800, 1200, 4000)),
    DIVINE_WRATH("Boski gniew", "Divine Wrath", Category.MAJOR, Kind.OFFENSIVE, DamageType.HOLY,
            "zadaje 5% maks. zdrowia potwora jako obrażenia święte (najwyżej 2 x twój poziom)", t(5, 10, 11), c(600, 900, 3000)),
    VAMPIRIC_EMBRACE("Wampirzy uścisk", "Vampiric Embrace", Category.MINOR, Kind.PASSIVE, null,
            "dodaje kradzież życia przeciw temu potworowi (gdy masz już kradzież życia)", t(1.6, 2.4, 3.2), c(100, 150, 225)),
    VOIDS_CALL("Zew pustki", "Void's Call", Category.MINOR, Kind.PASSIVE, null,
            "dodaje kradzież many przeciw temu potworowi (gdy masz już kradzież many)", t(0.8, 1.2, 1.6), c(100, 150, 225)),
    SAVAGE_BLOW("Dziki cios", "Savage Blow", Category.MAJOR, Kind.PASSIVE, null,
            "dodaje obrażenia krytyczne przeciw temu potworowi", t(20, 40, 44), c(800, 1200, 4000)),
    FATAL_HOLD("Śmiertelny chwyt", "Fatal Hold", Category.MINOR, Kind.PASSIVE, null,
            "trafiony potwór nie ucieka przez 30 s", t(30, 45, 60), c(100, 150, 225)),
    VOID_INVERSION("Odwrócenie pustki", "Void Inversion", Category.MINOR, Kind.PASSIVE, null,
            "wyssanie many przez tego potwora daje ci manę zamiast ją zabierać", t(20, 30, 40), c(100, 150, 225)),
    CARNAGE("Rzeź", "Carnage", Category.MAJOR, Kind.OFFENSIVE, null,
            "po zabiciu potwora 15% jego maks. zdrowia rani potwory na 4 sąsiednich polach (najwyżej 6 x twój poziom)",
            t(10, 20, 22), c(600, 900, 3000)),
    OVERPOWER("Przytłoczenie", "Overpower", Category.MAJOR, Kind.OFFENSIVE, null,
            "zadaje 5% twojego maks. zdrowia jako obrażenia (najwyżej 8% maks. zdrowia potwora)", t(5, 10, 11), c(600, 900, 3000)),
    OVERFLUX("Przepływ many", "Overflux", Category.MAJOR, Kind.OFFENSIVE, null,
            "zadaje 2,5% twojej maks. many jako obrażenia (najwyżej 8% maks. zdrowia potwora)", t(5, 10, 11), c(600, 900, 3000));

    public enum Category {
        MAJOR("Major"), MINOR("Minor");
        public final String label;
        Category(String label) { this.label = label; }
    }

    /** Kiedy charm dziala (Canary charm_t): po trafieniu potwora, po ciosie potwora albo stale. */
    public enum Kind { OFFENSIVE, DEFENSIVE, PASSIVE }

    public static final int MAX_TIER = 3;
    private static final String[] TIER_NAMES = {"", "I", "II", "III"};

    private final String label;
    private final String tibiaName;
    private final Category category;
    private final Kind kind;
    /** Rodzaj obrazen charmow zywiolow (Rana..Boski gniew); null = inne. */
    private final DamageType damageType;
    private final String effect;
    /** Szansa w procentach (albo wartosc: Niski cios, Dziki cios, Wampirzy uscisk, Zew pustki, Skorowanie) stopni I-III. */
    private final double[] values;
    /** Koszt stopni I-III: punkty charmow (Major) albo echa (Minor). */
    private final int[] costs;

    Charm(String label, String tibiaName, Category category, Kind kind, DamageType damageType, String effect, double[] values,
          int[] costs) {
        this.label = label;
        this.tibiaName = tibiaName;
        this.category = category;
        this.kind = kind;
        this.damageType = damageType;
        this.effect = effect;
        this.values = values;
        this.costs = costs;
    }

    private static double[] t(double a, double b, double c) { return new double[] {a, b, c}; }
    private static int[] c(int a, int b, int c) { return new int[] {a, b, c}; }

    public String label() { return label; }
    public String tibiaName() { return tibiaName; }
    public Category category() { return category; }
    public boolean major() { return category == Category.MAJOR; }
    public Kind kind() { return kind; }
    public DamageType damageType() { return damageType; }
    public String effect() { return effect; }

    /** Szansa/wartosc stopnia 1..3 (0 dla stopnia 0 - zablokowany). */
    public double value(int tier) { return tier <= 0 ? 0 : values[Math.min(MAX_TIER, tier) - 1]; }

    /** Koszt kupna stopnia tier (1..3). */
    public int cost(int tier) { return costs[Math.max(1, Math.min(MAX_TIER, tier)) - 1]; }

    /** Charm zywiolu (5% zdrowia potwora, limit 2 x poziom). */
    public boolean elemental() { return damageType != null; }

    /** Czy liczba stopnia to szansa w procentach (inaczej premia, np. +4% szansy na krytyk). */
    public boolean isChance() {
        return this != LOW_BLOW && this != SAVAGE_BLOW && this != VAMPIRIC_EMBRACE && this != VOIDS_CALL && this != SCAVENGE;
    }

    public static String tierName(int tier) { return TIER_NAMES[Math.max(0, Math.min(MAX_TIER, tier))]; }

    /** "Rana II" albo "Rana" dla zablokowanego. */
    public String labelWithTier(int tier) { return tier <= 0 ? label : label + " " + tierName(tier); }

    /** Opis wartosci stopnia: "10% szansy", "+8% szansy na krytyk", "+40% obrażeń krytycznych". */
    public String valueText(int tier) {
        String v = number(value(tier));
        switch (this) {
            case LOW_BLOW: return "+" + v + "% szansy na krytyk";
            case SAVAGE_BLOW: return "+" + v + "% obrażeń krytycznych";
            case VAMPIRIC_EMBRACE: return "+" + v + "% kradzieży życia";
            case VOIDS_CALL: return "+" + v + "% kradzieży many";
            case SCAVENGE: return "+" + v + "% szansy na trofeum";
            case BLESS: return "-" + v + "% utraty doświadczenia";
            case GUT: return "+" + v + "% szansy na materiały";
            default: return v + "% szansy";
        }
    }

    /** Liczba bez zbednych zer, z przecinkiem: 1,6 albo 10. */
    public static String number(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.valueOf(Math.round(v * 10) / 10.0).replace('.', ',');
    }

    /** Charm z nazwy enuma (akcje) albo null. */
    public static Charm parse(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
