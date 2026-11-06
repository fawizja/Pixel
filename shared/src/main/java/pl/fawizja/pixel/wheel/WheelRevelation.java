package pl.fawizja.pixel.wheel;

import pl.fawizja.pixel.vocation.Vocation;

/**
 * Objawienia (Revelation perks) jak w Canary (PlayerWheel::applyStageBonusForColor / applyRed/Purple/BlueStageBonus):
 * zielona domena - Dar Zycia dla wszystkich, czerwona - czar profesji, fioletowa - awatar, niebieska - pasywka
 * profesji. Stopien 1/2/3 od 250/500/1000 punktow w domenie. Opisy stopni po polsku.
 */
public enum WheelRevelation {
    GIFT_OF_LIFE("Dar Życia", null, gift(1), gift(2), gift(3)),
    EXECUTIONERS_THROW("Rzut kata", "executioners_throw",
            "Rzut kata: +100% obrażeń w cel z HP do 30%",
            "+125% obrażeń w cel z HP do 30%, -2 s odnowienia",
            "+150% obrażeń w cel z HP do 30%, -4 s odnowienia"),
    DIVINE_GRENADE("Boski granat", "divine_grenade",
            "odblokowuje Boski granat (x1,3 obrażeń, odnowienie 26 s)",
            "Boski granat x1,6 obrażeń, odnowienie 20 s",
            "Boski granat x2 obrażeń, odnowienie 14 s"),
    BEAM_MASTERY("Mistrzostwo promienia", "great_death_beam",
            "odblokowuje Wielki promień śmierci (6 pól); promienie +10% obrażeń",
            "promień 7 pól, +12% obrażeń; Wielki promień: -2 s, +6%",
            "promień 8 pól, +14% obrażeń; Wielki promień: -4 s, +12%"),
    BLESSING_OF_THE_GROVE("Błogosławieństwo gaju", null,
            "leczenie +6% przy HP do 60%, +12% przy HP do 30%",
            "leczenie +9% przy HP do 60%, +18% przy HP do 30%",
            "leczenie +12% przy HP do 60%, +24% przy HP do 30%"),
    SPIRITUAL_OUTBURST("Wybuch ducha", "spiritual_outburst",
            "Wybuch ducha z 5 harmonii uderza drugi raz (37,5% obrażeń)",
            "drugie uderzenie 50%, -4 s odnowienia",
            "drugie uderzenie 62,5%, -8 s odnowienia"),
    AVATAR_OF_STEEL("Awatar stali", "avatar_of_steel", avatar(1), avatar(2), avatar(3)),
    AVATAR_OF_LIGHT("Awatar światła", "avatar_of_light", avatar(1), avatar(2), avatar(3)),
    AVATAR_OF_STORM("Awatar burzy", "avatar_of_storm", avatar(1), avatar(2), avatar(3)),
    AVATAR_OF_NATURE("Awatar natury", "avatar_of_nature", avatar(1), avatar(2), avatar(3)),
    AVATAR_OF_BALANCE("Awatar równowagi", "avatar_of_balance", avatar(1), avatar(2), avatar(3)),
    COMBAT_MASTERY("Mistrzostwo walki", null,
            "bez tarczy +4% obrażeń krytycznych, z tarczą +10 obrony",
            "bez tarczy +8% obrażeń krytycznych, z tarczą +20 obrony",
            "bez tarczy +12% obrażeń krytycznych, z tarczą +30 obrony"),
    DIVINE_EMPOWERMENT("Boskie wzmocnienie", "divine_empowerment",
            "odblokowuje Boskie wzmocnienie: pole 3x3 na 5 s, +8% obrażeń na nim",
            "+10% obrażeń, -4 s odnowienia",
            "+12% obrażeń, -8 s odnowienia"),
    DRAIN_BODY("Wyssanie ciała", null,
            "czary ataku: +3% kradzieży życia i +2% many",
            "czary ataku: +4% kradzieży życia i +3% many",
            "czary ataku: +5% kradzieży życia i +4% many"),
    TWIN_BURST("Bliźniacze wybuchy", "terra_burst",
            "odblokowuje Wybuch ziemi i Wybuch lodu (+20% w cel z HP ponad 60%)",
            "+40% w cel z HP ponad 60%, -4 s odnowienia",
            "+60% w cel z HP ponad 60%, -8 s odnowienia"),
    ASCETIC("Asceta", null,
            "premia harmonii +1 pkt procentowy bazy",
            "premia harmonii +2 pkt procentowe bazy",
            "premia harmonii +3 pkt procentowe bazy");

    public final String label;
    /** Czar zwiazany z objawieniem (odblokowany albo ulepszany) albo null. */
    public final String spell;
    private final String[] stages;

    WheelRevelation(String label, String spell, String s1, String s2, String s3) {
        this.label = label;
        this.spell = spell;
        this.stages = new String[] {s1, s2, s3};
    }

    private static String gift(int stage) {
        return "śmiertelny cios leczy " + WheelEffects.GIFT_PERCENT[stage - 1] + "% HP" + (stage == 1 ? " zamiast zabić" : "")
                + " (odnowienie " + WheelEffects.GIFT_COOLDOWN_H[stage - 1] + " h gry)";
    }

    private static String avatar(int stage) {
        long cd = DestinyWheel.AVATAR_COOLDOWN_MS - (stage - 1) * DestinyWheel.AVATAR_COOLDOWN_CUT_MS;
        return (stage == 1 ? "odblokowuje awatara: " : "") + DestinyWheel.AVATAR_MS / 1000 + " s: -" + (5 * stage)
                + "% otrzymanych obrażeń, 100% krytyków, +" + (5 * stage) + "% obrażeń krytycznych; odnowienie "
                + SpellAugment.num(cd / 3_600_000.0) + " h";
    }

    /** Nazwa w Tibii, np. "Executioner's Throw". */
    public String tibia() {
        switch (this) {
            case EXECUTIONERS_THROW: return "Executioner's Throw";
            case TWIN_BURST: return "Twin Bursts";
            default: return WheelPerks.tibiaName(name());
        }
    }

    /** Opis stopnia 1..3. */
    public String stage(int s) { return stages[Math.max(1, Math.min(3, s)) - 1]; }

    public boolean isAvatar() { return name().startsWith("AVATAR_"); }

    /** Objawienie domeny dla profesji. */
    public static WheelRevelation of(WheelDomain d, Vocation v) {
        switch (d) {
            case GREEN: return GIFT_OF_LIFE;
            case RED:
                switch (v) {
                    case KNIGHT: return EXECUTIONERS_THROW;
                    case PALADIN: return DIVINE_GRENADE;
                    case SORCERER: return BEAM_MASTERY;
                    case DRUID: return BLESSING_OF_THE_GROVE;
                    default: return SPIRITUAL_OUTBURST;
                }
            case PURPLE:
                switch (v) {
                    case KNIGHT: return AVATAR_OF_STEEL;
                    case PALADIN: return AVATAR_OF_LIGHT;
                    case SORCERER: return AVATAR_OF_STORM;
                    case DRUID: return AVATAR_OF_NATURE;
                    default: return AVATAR_OF_BALANCE;
                }
            default:
                switch (v) {
                    case KNIGHT: return COMBAT_MASTERY;
                    case PALADIN: return DIVINE_EMPOWERMENT;
                    case SORCERER: return DRAIN_BODY;
                    case DRUID: return TWIN_BURST;
                    default: return ASCETIC;
                }
        }
    }
}
