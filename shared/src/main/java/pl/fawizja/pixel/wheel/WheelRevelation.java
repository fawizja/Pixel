package pl.fawizja.pixel.wheel;

import pl.fawizja.pixel.i18n.Messages;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Objawienia (Revelation perks) jak w Canary (PlayerWheel::applyStageBonusForColor / applyRed/Purple/BlueStageBonus):
 * zielona domena - Dar Zycia dla wszystkich, czerwona - czar profesji, fioletowa - awatar, niebieska - pasywka
 * profesji. Stopien 1/2/3 od 250/500/1000 punktow w domenie. Opisy stopni po polsku.
 */
public enum WheelRevelation {
    GIFT_OF_LIFE("Dar Życia", null, gift(1), gift(2), gift(3)),
    EXECUTIONERS_THROW("Rzut kata", "executioners_throw",
            Messages.get("wheel_revelation.rzut_kata_100_obrazen"),
            Messages.get("wheel_revelation.125_obrazen_w_cel"),
            Messages.get("wheel_revelation.150_obrazen_w_cel")),
    DIVINE_GRENADE("Boski granat", "divine_grenade",
            Messages.get("wheel_revelation.odblokowuje_boski_granat_x1"),
            Messages.get("wheel_revelation.boski_granat_x1_6"),
            Messages.get("wheel_revelation.boski_granat_x2_obrazen")),
    BEAM_MASTERY("Mistrzostwo promienia", "great_death_beam",
            Messages.get("wheel_revelation.odblokowuje_wielki_promien_smi"),
            Messages.get("wheel_revelation.promien_7_pol_12"),
            Messages.get("wheel_revelation.promien_8_pol_14")),
    BLESSING_OF_THE_GROVE("Błogosławieństwo gaju", null,
            Messages.get("wheel_revelation.leczenie_6_przy_hp"),
            Messages.get("wheel_revelation.leczenie_9_przy_hp"),
            Messages.get("wheel_revelation.leczenie_12_przy_hp")),
    SPIRITUAL_OUTBURST("Wybuch ducha", "spiritual_outburst",
            Messages.get("wheel_revelation.wybuch_ducha_z_5"),
            Messages.get("wheel_revelation.drugie_uderzenie_50_4"),
            Messages.get("wheel_revelation.drugie_uderzenie_62_5")),
    AVATAR_OF_STEEL("Awatar stali", "avatar_of_steel", avatar(1), avatar(2), avatar(3)),
    AVATAR_OF_LIGHT("Awatar światła", "avatar_of_light", avatar(1), avatar(2), avatar(3)),
    AVATAR_OF_STORM("Awatar burzy", "avatar_of_storm", avatar(1), avatar(2), avatar(3)),
    AVATAR_OF_NATURE("Awatar natury", "avatar_of_nature", avatar(1), avatar(2), avatar(3)),
    AVATAR_OF_BALANCE("Awatar równowagi", "avatar_of_balance", avatar(1), avatar(2), avatar(3)),
    COMBAT_MASTERY("Mistrzostwo walki", null,
            Messages.get("wheel_revelation.bez_tarczy_4_obrazen"),
            Messages.get("wheel_revelation.bez_tarczy_8_obrazen"),
            Messages.get("wheel_revelation.bez_tarczy_12_obrazen")),
    DIVINE_EMPOWERMENT("Boskie wzmocnienie", "divine_empowerment",
            Messages.get("wheel_revelation.odblokowuje_boskie_wzmocnienie"),
            Messages.get("wheel_revelation.10_obrazen_4_s"),
            Messages.get("wheel_revelation.12_obrazen_8_s")),
    DRAIN_BODY("Wyssanie ciała", null,
            Messages.get("wheel_revelation.czary_ataku_3_kradziezy"),
            Messages.get("wheel_revelation.czary_ataku_4_kradziezy"),
            Messages.get("wheel_revelation.czary_ataku_5_kradziezy")),
    TWIN_BURST("Bliźniacze wybuchy", "terra_burst",
            Messages.get("wheel_revelation.odblokowuje_wybuch_ziemi_i"),
            Messages.get("wheel_revelation.40_w_cel_z"),
            Messages.get("wheel_revelation.60_w_cel_z")),
    ASCETIC("Asceta", null,
            Messages.get("wheel_revelation.premia_harmonii_1_pkt"),
            Messages.get("wheel_revelation.premia_harmonii_2_pkt"),
            Messages.get("wheel_revelation.premia_harmonii_3_pkt"));

    private final String label;

    public String label() { return Messages.tr(label); }
    /** Czar zwiazany z objawieniem (odblokowany albo ulepszany) albo null. */
    public final String spell;
    private final String[] stages;

    WheelRevelation(String label, String spell, String s1, String s2, String s3) {
        this.label = label;
        this.spell = spell;
        this.stages = new String[] {s1, s2, s3};
    }

    private static String gift(int stage) {
        return Messages.get("wheel_revelation.smiertelny_cios_leczy") + WheelEffects.GIFT_PERCENT[stage - 1] + "% HP" + (stage == 1 ? Messages.get("wheel_revelation.zamiast_zabic") : "")
                + " (odnowienie " + WheelEffects.GIFT_COOLDOWN_H[stage - 1] + Messages.get("wheel_revelation.h_gry");
    }

    private static String avatar(int stage) {
        long cd = DestinyWheel.AVATAR_COOLDOWN_MS - (stage - 1) * DestinyWheel.AVATAR_COOLDOWN_CUT_MS;
        return (stage == 1 ? Messages.get("wheel_revelation.odblokowuje_awatara") : "") + DestinyWheel.AVATAR_MS / 1000 + " s: -" + (5 * stage)
                + Messages.get("wheel_revelation.otrzymanych_obrazen_100_krytyk") + (5 * stage) + Messages.get("wheel_revelation.obrazen_krytycznych_odnowienie")
                + SpellAugment.num(cd / 3_600_000.0) + " h";
    }

    /** Nazwa w Tibii, np. "Executioner's Throw". */
    public String tibia() {
        switch (this) {
            case EXECUTIONERS_THROW: return Messages.get("supreme_gem_mod.executioner_s_throw");
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
