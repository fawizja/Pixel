package pl.fawizja.pixel.wheel;

import pl.fawizja.pixel.i18n.Messages;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Liczby perkow Kola Przeznaczenia na profesje.
 * <ul>
 *   <li>Oddanie za punkt (Canary io_wheel.cpp): HP rycerz 3 / paladyn 2 / magowie 1 / mnich 2, mana 1 / 3 / 6 / 2,
 *       udzwig 5 / 4 / 2 / 5, mitygacja 0,03% (MITIGATION_INCREASE) na polach mitygacji.</li>
 *   <li>Przekonanie: +1 umiejetnosci profesji, kradziez zycia +0,75% (HEALTH_LEECH_INCREASE), many +0,25%
 *       (MANA_LEECH_INCREASE), ulepszenia czarow (kazdy czar na 2 polach: 1 pelne pole = stopien 1, oba = stopien 2).</li>
 * </ul>
 * Czary z Tibii, ktorych w Pixelu nie ma, zastapiono najblizszym naszym czarem (pole {@link Upgrade#tibia} i opis
 * zamiennika); wartosci stopni wziete z Canary IOWheel::initializeXxxSpells, a gdzie premia nie ma sensu przy naszych
 * czasach odnowienia - przeskalowane (zaznaczone w {@link Upgrade#note}).
 */
public final class WheelPerks {
    /** Mitygacja (procent wzgledny) za punkt na polu mitygacji. */
    public static final double MITIGATION_PER_POINT = 0.03;
    public static final double LIFE_LEECH = 0.75;
    public static final double MANA_LEECH = 0.25;

    private WheelPerks() {}

    public static int hpPerPoint(Vocation v) {
        switch (v) {
            case KNIGHT: return 3;
            case PALADIN: case MONK: return 2;
            default: return 1;
        }
    }

    public static int manaPerPoint(Vocation v) {
        switch (v) {
            case KNIGHT: return 1;
            case PALADIN: return 3;
            case MONK: return 2;
            default: return 6;
        }
    }

    public static int capPerPoint(Vocation v) {
        switch (v) {
            case KNIGHT: case MONK: return 5;
            case PALADIN: return 4;
            default: return 2;
        }
    }

    /** Nazwa umiejetnosci z przekonania (+1): walka wrecz, dystans, magia albo piesci. */
    public static String skillLabel(Vocation v) {
        switch (v) {
            case KNIGHT: return Messages.get("wheel_perks.walka_wrecz_miecz_topor");
            case PALADIN: return Messages.get("wheel_perks.walka_na_dystans");
            case MONK: return Messages.get("wheel_perks.walka_piesciami");
            default: return Messages.get("wheel_perks.poziom_magii");
        }
    }

    /** Ulepszenie czaru z kola: czar Pixela, odpowiednik w Tibii, premia stopnia 1 i (dodatkowo) stopnia 2. */
    public static final class Upgrade {
        public final List<String> spells;
        public final String tibia;
        public final SpellAugment grade1;
        public final SpellAugment grade2;
        /** Uwaga o dopasowaniu (zamiennik, przeskalowanie) albo "". */
        public final String note;

        Upgrade(List<String> spells, String tibia, SpellAugment grade1, SpellAugment grade2, String note) {
            this.spells = spells;
            this.tibia = tibia;
            this.grade1 = grade1;
            this.grade2 = grade2;
            this.note = note;
        }

        public SpellAugment at(int grade) {
            if (grade <= 0) return SpellAugment.NONE;
            return grade == 1 ? grade1 : grade1.plus(grade2);
        }
    }

    private static Upgrade up(String spell, String tibia, SpellAugment g1, SpellAugment g2, String note) {
        return new Upgrade(Collections.singletonList(spell), tibia, g1, g2, note);
    }

    private static final Upgrade[] KNIGHT = {
            up("front_sweep", "Front Sweep", SpellAugment.lifeLeech(5), SpellAugment.damage(14), ""),
            up("groundshaker", "Groundshaker", SpellAugment.damage(12.5), SpellAugment.cooldown(2000), ""),
            up("chivalrous_challenge", "Chivalrous Challenge", SpellAugment.mana(20), SpellAugment.cooldown(2000),
                    Messages.get("wheel_perks.st_2_2_s")),
            up("intense_wound_cleansing", "Intense Wound Cleansing", SpellAugment.heal(125), SpellAugment.mana(10),
                    Messages.get("wheel_perks.st_2_10_many")),
            up("fierce_berserk", "Fierce Berserk", SpellAugment.mana(30), SpellAugment.damage(10), "")};

    private static final Upgrade[] PALADIN = {
            up("sharpshooter", "Sharpshooter", SpellAugment.cooldown(2000), SpellAugment.cooldown(2000),
                    Messages.get("wheel_perks.2_s_zamiast_8")),
            up("strong_ethereal_spear", "Strong Ethereal Spear", SpellAugment.cooldown(2000), SpellAugment.damage(38),
                    Messages.get("wheel_perks.st_2_38_zamiast")),
            up("divine_dazzle", "Divine Dazzle", SpellAugment.mana(20), SpellAugment.cooldown(1000), ""),
            up("swift_foot", "Swift Foot", SpellAugment.mana(10), SpellAugment.cooldown(2000),
                    Messages.get("wheel_perks.przyblizenie_10_many_i")),
            up("divine_caldera", "Divine Caldera", SpellAugment.mana(20), SpellAugment.damage(8.5), "")};

    private static final Upgrade[] SORCERER = {
            new Upgrade(List.of("hells_core", "rage_of_the_skies"), Messages.get("wheel_perks.dowolny_czar_skupienia"), SpellAugment.damage(5),
                    SpellAugment.cooldown(4000), ""),
            up("magic_shield", "Magic Shield", SpellAugment.mana(10), SpellAugment.cooldown(2000),
                    Messages.get("wheel_perks.przyblizenie_10_many_i_2")),
            up("ultimate_energy_strike", "Sap Strength", SpellAugment.mana(20), SpellAugment.damage(5),
                    Messages.get("wheel_perks.zamiennik_ostateczne_uderzenie")),
            up("energy_wave", "Energy Wave", SpellAugment.damage(5), SpellAugment.radius(1), ""),
            up("great_fire_wave", "Great Fire Wave", SpellAugment.crit(10, 15), SpellAugment.damage(5), "")};

    private static final Upgrade[] DRUID = {
            up("strong_ice_wave", "Strong Ice Wave", SpellAugment.manaLeech(3), SpellAugment.damage(10), ""),
            up("mass_healing", "Mass Healing", SpellAugment.heal(4), SpellAugment.heal(4),
                    Messages.get("wheel_perks.st_2_4_zamiast")),
            up("natures_embrace", Messages.get("supreme_gem_mod.nature_s_embrace"), SpellAugment.heal(11), SpellAugment.mana(10),
                    Messages.get("wheel_perks.st_2_10_many_2")),
            up("terra_wave", "Terra Wave", SpellAugment.damage(6.5), SpellAugment.lifeLeech(5), ""),
            up("heal_friend", "Heal Friend", SpellAugment.mana(10), SpellAugment.heal(5.5), "")};

    private static final Upgrade[] MONK = {
            up("sweeping_takedown", "Sweeping Takedown", SpellAugment.manaLeech(3), SpellAugment.crit(10, 25), ""),
            up("mass_spirit_mend", "Mass Spirit Mend", SpellAugment.heal(8), SpellAugment.radius(1), ""),
            up("mystic_repulse", "Mystic Repulse", SpellAugment.cooldown(4000), SpellAugment.damage(40), ""),
            up("chained_penance", "Chained Penance", SpellAugment.targets(1), SpellAugment.targets(1), ""),
            up("flurry_of_blows", "Flurry of Blows", SpellAugment.lifeLeech(5), SpellAugment.damage(12), "")};

    /** Ulepszenie czaru grupy 0..4 (pola SPELL_1..SPELL_5) dla profesji. */
    public static Upgrade upgrade(Vocation v, int group) {
        return table(v)[group];
    }

    private static Upgrade[] table(Vocation v) {
        switch (v) {
            case KNIGHT: return KNIGHT;
            case PALADIN: return PALADIN;
            case SORCERER: return SORCERER;
            case DRUID: return DRUID;
            default: return MONK;
        }
    }

    /** Pola z czarem danej grupy (zawsze dwa). */
    public static List<WheelSlice> slicesOfGroup(int group) {
        List<WheelSlice> out = new ArrayList<>(2);
        for (WheelSlice s : WheelSlice.values()) if (s.conviction.spellGroup() == group) out.add(s);
        return out;
    }

    /** Opis oddania pola po polsku dla profesji, za punkt: "+3 HP", "+6 many", "+5 udźwigu", "+0,03% mitygacji". */
    public static String dedication(WheelSlice s, Vocation v) {
        switch (s.stat) {
            case HP: return "+" + hpPerPoint(v) + Messages.get("wheel_perks.hp_za_punkt");
            case MANA: return "+" + manaPerPoint(v) + Messages.get("wheel_perks.many_za_punkt");
            case HP_MANA: return "+" + hpPerPoint(v) + " HP i +" + manaPerPoint(v) + Messages.get("wheel_perks.many_za_punkt");
            case CAP: return "+" + capPerPoint(v) + Messages.get("wheel_perks.udzwigu_za_punkt");
            default: return "+" + SpellAugment.num(MITIGATION_PER_POINT) + Messages.get("wheel_perks.mitygacji_za_punkt");
        }
    }

    /** Oddanie jednego punktu krotko: "+3 HP", "+3 HP, +1 many", "+0,03% mitygacji". */
    public static String perPoint(WheelSlice s, Vocation v) { return dedicationAt(s, v, 1); }

    /** Suma oddania przy p punktach w polu: "+150 HP", "+6% mitygacji". */
    public static String dedicationAt(WheelSlice s, Vocation v, int p) {
        switch (s.stat) {
            case HP: return "+" + p * hpPerPoint(v) + " HP";
            case MANA: return "+" + p * manaPerPoint(v) + " many";
            case HP_MANA: return "+" + p * hpPerPoint(v) + " HP, +" + p * manaPerPoint(v) + " many";
            case CAP: return "+" + p * capPerPoint(v) + Messages.get("basic_gem_mod.udzwigu");
            default: return "+" + SpellAugment.num(Math.round(p * MITIGATION_PER_POINT * 100) / 100.0) + Messages.get("basic_gem_mod.mitygacji");
        }
    }

    /** Krotka nazwa umiejetnosci z przekonania do list: "walka wręcz", "dystans", "magia", "pięści". */
    public static String skillShort(Vocation v) {
        switch (v) {
            case KNIGHT: return Messages.get("wheel_perks.walka_wrecz");
            case PALADIN: return "dystans";
            case MONK: return Messages.get("wheel_perks.piesci");
            default: return Messages.get("wheel_perks.poziom_magii");
        }
    }

    /** Angielska nazwa z Tibii z nazwy stalej: BATTLE_INSTINCT -> "Battle Instinct", AVATAR_OF_STEEL -> "Avatar of Steel". */
    static String tibiaName(String constant) {
        StringBuilder b = new StringBuilder();
        for (String w : constant.toLowerCase(java.util.Locale.ROOT).split("_")) {
            if (b.length() > 0) b.append(' ');
            boolean small = b.length() > 0 && (w.equals("of") || w.equals("the"));
            b.append(small ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1));
        }
        return b.toString();
    }

    /** Opis przekonania pola po polsku dla profesji. */
    public static String conviction(WheelSlice s, Vocation v) {
        switch (s.conviction) {
            case SKILL: return "+1 " + skillLabel(v);
            case LIFE_LEECH: return "+" + SpellAugment.num(LIFE_LEECH) + Messages.get("spell_augment.kradziezy_zycia");
            case MANA_LEECH: return "+" + SpellAugment.num(MANA_LEECH) + Messages.get("spell_augment.kradziezy_many");
            case PASSIVE_A: return WheelPassive.of(v, true).label() + ": " + WheelPassive.of(v, true).description();
            case PASSIVE_B: return WheelPassive.of(v, false).label() + ": " + WheelPassive.of(v, false).description();
            case VESSEL: return Messages.get("wheel_perks.naczynie_klejnotu_1_rezonansu");
            default: {
                Upgrade u = upgrade(v, s.conviction.spellGroup());
                return Messages.get("wheel_perks.ulepszenie_czaru") + u.tibia;
            }
        }
    }
}
