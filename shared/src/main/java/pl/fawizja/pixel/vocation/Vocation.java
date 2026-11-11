package pl.fawizja.pixel.vocation;

import pl.fawizja.pixel.i18n.Messages;
/**
 * Piec profesji. Kazda ma inny styl gry, a wszystkie liczby sa w jednym miejscu,
 * zeby balans korygowac w danych, a nie wyjatkami w kodzie.
 */
public enum Vocation {

    /** Dystans i swieta magia; wszechstronny, srednie HP i mana. */
    PALADIN("Paladin",
            10, 15, 20,
            2, 3,
            0.85, 1.25, 0.60, 0.85, 1.00,
            2000,
            Messages.get("vocation.walczy_z_dystansu_wloczniami"),
            Messages.get("vocation.bezpieczny_dystans_dobra_przez"),
            Messages.get("vocation.slabsza_magia_obszarowa_niz")),

    /** Leczenie, lod i ziemia; najlepsze wsparcie druzyny. */
    DRUID("Druid",
            5, 30, 10,
            1, 4,
            0.50, 0.50, 1.00, 1.30, 0.80,
            2000,
            Messages.get("vocation.mag_natury_lod_i"),
            Messages.get("vocation.leczenie_kontrola_tlumu_duza"),
            Messages.get("vocation.niskie_hp_i_slaba")),

    /** Ogien i energia; najwyzsze obrazenia magiczne, najmniejsza wytrzymalosc. */
    SORCERER("Sorcerer",
            5, 30, 10,
            1, 4,
            0.50, 0.50, 1.25, 0.70, 0.75,
            2000,
            Messages.get("vocation.mag_zniszczenia_ogien_energia"),
            Messages.get("vocation.najwyzsze_obrazenia_obszarowe"),
            Messages.get("vocation.najnizsza_obrona_i_slabe")),

    /** Walka wrecz piesciami, szybkie ataki i kombinacje; wysoka wytrzymalosc. */
    MONK("Monk",
            13, 6, 22,
            3, 2,
            1.25, 0.40, 0.50, 0.90, 1.15,
            1400,
            Messages.get("vocation.mistrz_walki_wrecz_szybkie"),
            Messages.get("vocation.najwiecej_hp_najszybsze_ataki"),
            Messages.get("vocation.musi_stac_przy_celu")),

    /** Miecze, topory i mloty z tarcza; najwyzsza obrona i najwiecej HP (jak rycerz w Tibii). */
    KNIGHT("Knight",
            15, 5, 25,
            3, 1,
            1.35, 0.40, 0.35, 0.80, 1.30,
            1700,
            Messages.get("vocation.rycerz_w_ciezkiej_zbroi"),
            Messages.get("vocation.najwiecej_hp_i_najlepsza"),
            Messages.get("vocation.malo_many_slabe_leczenie"));

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

    /** Nazwa po promocji u krola (jak w Tibii). */
    public String promotedName() {
        switch (this) {
            case PALADIN: return "Royal Paladin";
            case DRUID: return "Elder Druid";
            case SORCERER: return "Master Sorcerer";
            case MONK: return "Exalted Monk";
            case KNIGHT: return "Elite Knight";
            default: return "Elite " + displayName;
        }
    }
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
