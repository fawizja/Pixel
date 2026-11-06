package pl.fawizja.pixel.wheel;

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
            case KNIGHT: return "walka wręcz (miecz, topór, młot)";
            case PALADIN: return "walka na dystans";
            case MONK: return "walka pięściami";
            default: return "poziom magii";
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
            up("berserk", "Front Sweep", SpellAugment.lifeLeech(5), SpellAugment.damage(14), "zamiennik: Szał"),
            up("groundshaker", "Groundshaker", SpellAugment.damage(12.5), SpellAugment.cooldown(2000), ""),
            up("annihilation", "Chivalrous Challenge", SpellAugment.mana(20), SpellAugment.cooldown(2000),
                    "zamiennik: Unicestwienie (brak prowokacji); st. 2 -2 s zamiast +1 celu"),
            up("intense_wound_cleansing", "Intense Wound Cleansing", SpellAugment.heal(125), SpellAugment.mana(10),
                    "st. 2: -10 many zamiast -5 min (u nas odnowienie 1 s)"),
            up("fierce_berserk", "Fierce Berserk", SpellAugment.mana(30), SpellAugment.damage(10), "")};

    private static final Upgrade[] PALADIN = {
            up("divine_judgement", "Sharpshooter", SpellAugment.cooldown(2000), SpellAugment.cooldown(2000),
                    "zamiennik: Boski sąd; -2 s zamiast -8 s / -6 s"),
            up("strong_ethereal_spear", "Strong Ethereal Spear", SpellAugment.cooldown(2000), SpellAugment.damage(38),
                    "st. 2: +38% zamiast +380% z Canary (u nas czar ma już pełną moc)"),
            up("divine_wrath", "Divine Dazzle", SpellAugment.mana(20), SpellAugment.cooldown(1000),
                    "zamiennik: Boski gniew (brak oślepienia)"),
            up("holy_mend", "Swift Foot", SpellAugment.mana(10), SpellAugment.heal(10),
                    "zamiennik: Wielkie święte uzdrowienie (brak Swift Foot)"),
            up("divine_caldera", "Divine Caldera", SpellAugment.mana(20), SpellAugment.damage(8.5), "")};

    private static final Upgrade[] SORCERER = {
            new Upgrade(List.of("hells_core", "rage_of_skies"), "dowolny czar skupienia", SpellAugment.damage(5),
                    SpellAugment.cooldown(4000), ""),
            up("ultimate_mend", "Magic Shield", SpellAugment.heal(10), SpellAugment.mana(10),
                    "zamiennik: Wielkie uzdrowienie (brak tarczy many)"),
            up("energy_strike_max", "Sap Strength", SpellAugment.mana(20), SpellAugment.damage(5),
                    "zamiennik: Ostateczny grom (brak osłabienia)"),
            up("storm_ring", "Energy Wave", SpellAugment.damage(5), SpellAugment.radius(1), "zamiennik: Burza energii"),
            up("flame_ring", "Great Fire Wave", SpellAugment.crit(10, 15), SpellAugment.damage(5), "zamiennik: Krąg ognia")};

    private static final Upgrade[] DRUID = {
            up("frost_nova", "Strong Ice Wave", SpellAugment.manaLeech(3), SpellAugment.damage(10), "zamiennik: Mroźna fala"),
            up("vital_surge", "Mass Healing", SpellAugment.heal(4), SpellAugment.heal(4),
                    "zamiennik: Przypływ życia; st. 2 +4% zamiast obszaru"),
            up("ultimate_mend", "Nature's Embrace", SpellAugment.heal(11), SpellAugment.mana(10),
                    "zamiennik: Wielkie uzdrowienie; st. 2 -10 many zamiast -10 s"),
            up("earth_quake", "Terra Wave", SpellAugment.damage(6.5), SpellAugment.lifeLeech(5), "zamiennik: Gniew ziemi"),
            up("greater_mend", "Heal Friend", SpellAugment.mana(10), SpellAugment.heal(5.5), "zamiennik: Uzdrowienie")};

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
            case HP: return "+" + hpPerPoint(v) + " HP za punkt";
            case MANA: return "+" + manaPerPoint(v) + " many za punkt";
            case HP_MANA: return "+" + hpPerPoint(v) + " HP i +" + manaPerPoint(v) + " many za punkt";
            case CAP: return "+" + capPerPoint(v) + " udźwigu za punkt";
            default: return "+0,03% mitygacji za punkt";
        }
    }

    /** Opis przekonania pola po polsku dla profesji. */
    public static String conviction(WheelSlice s, Vocation v) {
        switch (s.conviction) {
            case SKILL: return "+1 " + skillLabel(v);
            case LIFE_LEECH: return "+0,75% kradzieży życia";
            case MANA_LEECH: return "+0,25% kradzieży many";
            case PASSIVE_A: return WheelPassive.of(v, true).label + ": " + WheelPassive.of(v, true).description;
            case PASSIVE_B: return WheelPassive.of(v, false).label + ": " + WheelPassive.of(v, false).description;
            case VESSEL: return "naczynie klejnotu (bez klejnotów)";
            default: {
                Upgrade u = upgrade(v, s.conviction.spellGroup());
                return "ulepszenie czaru " + u.tibia;
            }
        }
    }
}
