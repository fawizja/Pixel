package pl.fawizja.pixel.wheel;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Modyfikatory najwyzsze (supreme) wiekszych klejnotow (Canary WheelGemSupremeModifier_t, wartosci
 * z WheelModifierContext::addStrategies, listy profesji z player_wheel.cpp modsSupreme*Position).
 * <ul>
 *   <li>ogolne: unik 0,28%, obrazenia krytyczne +2%, kradziez zycia 2%, many 0,8%, Mistrzostwo objawienia
 *       (Dar Zycia) +150 punktow do zielonej domeny przy liczeniu etapu objawienia,</li>
 *   <li>profesji: obrazenia / obrazenia krytyczne / leczenie czaru w procentach, odnowienie czaru (nie rosnie ze stopniem),
 *       Mistrzostwo objawienia czerwonej, fioletowej albo niebieskiej domeny (+150 punktow).</li>
 * </ul>
 * Czary z Tibii bez odpowiednika w Pixel maja zamiennik (pole {@link #tibia} to nazwa z Tibii). Stopien I-IV mnozy
 * wartosc x1 / x1,1 / x1,2 / x1,5 jak mody podstawowe.
 */
public enum SupremeGemMod {
    // ---- wszystkie profesje
    DODGE(null, Kind.DODGE, 0.28, (String) null, "Dodge"),
    CRITICAL_DAMAGE(null, Kind.CRIT_DAMAGE, 2, (String) null, "Critical Damage"),
    LIFE_LEECH(null, Kind.LIFE_LEECH, 2, (String) null, "Life Leech"),
    MANA_LEECH(null, Kind.MANA_LEECH, 0.8, (String) null, "Mana Leech"),
    MASTERY_GIFT_OF_LIFE(null, Kind.REVELATION, 150, WheelDomain.GREEN, "Revelation Mastery: Gift of Life"),
    ULTIMATE_HEALING(voc(Vocation.SORCERER, Vocation.DRUID), Kind.SPELL_HEAL, 5, "ultimate_healing", "Ultimate Healing"),
    // ---- rycerz
    KNIGHT_AVATAR_COOLDOWN(voc(Vocation.KNIGHT), Kind.SPELL_COOLDOWN, 900_000, "avatar_of_steel", "Avatar of Steel"),
    KNIGHT_EXECUTIONERS_COOLDOWN(voc(Vocation.KNIGHT), Kind.SPELL_COOLDOWN, 2_000, "executioners_throw", "Executioner's Throw"),
    KNIGHT_EXECUTIONERS_DAMAGE(voc(Vocation.KNIGHT), Kind.SPELL_DAMAGE, 6, "executioners_throw", "Executioner's Throw"),
    KNIGHT_EXECUTIONERS_CRIT(voc(Vocation.KNIGHT), Kind.SPELL_CRIT, 12, "executioners_throw", "Executioner's Throw"),
    KNIGHT_FIERCE_BERSERK_DAMAGE(voc(Vocation.KNIGHT), Kind.SPELL_DAMAGE, 5, "fierce_berserk", "Fierce Berserk"),
    KNIGHT_FIERCE_BERSERK_CRIT(voc(Vocation.KNIGHT), Kind.SPELL_CRIT, 8, "fierce_berserk", "Fierce Berserk"),
    KNIGHT_BERSERK_DAMAGE(voc(Vocation.KNIGHT), Kind.SPELL_DAMAGE, 5, "berserk", "Berserk"),
    KNIGHT_BERSERK_CRIT(voc(Vocation.KNIGHT), Kind.SPELL_CRIT, 12, "berserk", "Berserk"),
    KNIGHT_FRONT_SWEEP_CRIT(voc(Vocation.KNIGHT), Kind.SPELL_CRIT, 12, "front_sweep", "Front Sweep"),
    KNIGHT_FRONT_SWEEP_DAMAGE(voc(Vocation.KNIGHT), Kind.SPELL_DAMAGE, 8, "front_sweep", "Front Sweep"),
    KNIGHT_GROUNDSHAKER_DAMAGE(voc(Vocation.KNIGHT), Kind.SPELL_DAMAGE, 6.5, "groundshaker", "Groundshaker"),
    KNIGHT_GROUNDSHAKER_CRIT(voc(Vocation.KNIGHT), Kind.SPELL_CRIT, 12, "groundshaker", "Groundshaker"),
    KNIGHT_ANNIHILATION_CRIT(voc(Vocation.KNIGHT), Kind.SPELL_CRIT, 15, "annihilation", "Annihilation"),
    KNIGHT_ANNIHILATION_DAMAGE(voc(Vocation.KNIGHT), Kind.SPELL_DAMAGE, 12, "annihilation", "Annihilation"),
    KNIGHT_WOUND_CLEANSING_HEAL(voc(Vocation.KNIGHT), Kind.SPELL_HEAL, 10, "wound_cleansing", "Fair Wound Cleansing"),
    KNIGHT_MASTERY_AVATAR(voc(Vocation.KNIGHT), Kind.REVELATION, 150, WheelDomain.PURPLE, "Revelation Mastery: Avatar of Steel"),
    KNIGHT_MASTERY_EXECUTIONERS(voc(Vocation.KNIGHT), Kind.REVELATION, 150, WheelDomain.RED, "Revelation Mastery: Executioner's Throw"),
    KNIGHT_MASTERY_COMBAT(voc(Vocation.KNIGHT), Kind.REVELATION, 150, WheelDomain.BLUE, "Revelation Mastery: Combat Mastery"),
    // ---- paladyn
    PALADIN_AVATAR_COOLDOWN(voc(Vocation.PALADIN), Kind.SPELL_COOLDOWN, 900_000, "avatar_of_light", "Avatar of Light"),
    PALADIN_DAZZLE_COOLDOWN(voc(Vocation.PALADIN), Kind.SPELL_COOLDOWN, 4_000, "divine_dazzle", "Divine Dazzle"),
    PALADIN_GRENADE_DAMAGE(voc(Vocation.PALADIN), Kind.SPELL_DAMAGE, 6, "divine_grenade", "Divine Grenade"),
    PALADIN_GRENADE_CRIT(voc(Vocation.PALADIN), Kind.SPELL_CRIT, 12, "divine_grenade", "Divine Grenade"),
    PALADIN_CALDERA_DAMAGE(voc(Vocation.PALADIN), Kind.SPELL_DAMAGE, 5, "divine_caldera", "Divine Caldera"),
    PALADIN_CALDERA_CRIT(voc(Vocation.PALADIN), Kind.SPELL_CRIT, 12, "divine_caldera", "Divine Caldera"),
    PALADIN_MISSILE_DAMAGE(voc(Vocation.PALADIN), Kind.SPELL_DAMAGE, 8, "divine_missile", "Divine Missile"),
    PALADIN_MISSILE_CRIT(voc(Vocation.PALADIN), Kind.SPELL_CRIT, 12, "divine_missile", "Divine Missile"),
    PALADIN_ETHEREAL_DAMAGE(voc(Vocation.PALADIN), Kind.SPELL_DAMAGE, 10, "ethereal_spear", "Ethereal Spear"),
    PALADIN_ETHEREAL_CRIT(voc(Vocation.PALADIN), Kind.SPELL_CRIT, 15, "ethereal_spear", "Ethereal Spear"),
    PALADIN_STRONG_ETHEREAL_DAMAGE(voc(Vocation.PALADIN), Kind.SPELL_DAMAGE, 8, "strong_ethereal_spear", "Strong Ethereal Spear"),
    PALADIN_STRONG_ETHEREAL_CRIT(voc(Vocation.PALADIN), Kind.SPELL_CRIT, 12, "strong_ethereal_spear", "Strong Ethereal Spear"),
    PALADIN_EMPOWERMENT_COOLDOWN(voc(Vocation.PALADIN), Kind.SPELL_COOLDOWN, 6_000, "divine_empowerment", "Divine Empowerment"),
    PALADIN_GRENADE_COOLDOWN(voc(Vocation.PALADIN), Kind.SPELL_COOLDOWN, 2_000, "divine_grenade", "Divine Grenade"),
    PALADIN_SALVATION_HEAL(voc(Vocation.PALADIN), Kind.SPELL_HEAL, 6, "salvation", "Salvation"),
    PALADIN_MASTERY_AVATAR(voc(Vocation.PALADIN), Kind.REVELATION, 150, WheelDomain.PURPLE, "Revelation Mastery: Avatar of Light"),
    PALADIN_MASTERY_GRENADE(voc(Vocation.PALADIN), Kind.REVELATION, 150, WheelDomain.RED, "Revelation Mastery: Divine Grenade"),
    PALADIN_MASTERY_EMPOWERMENT(voc(Vocation.PALADIN), Kind.REVELATION, 150, WheelDomain.BLUE, "Revelation Mastery: Divine Empowerment"),
    // ---- sorcerer
    SORCERER_AVATAR_COOLDOWN(voc(Vocation.SORCERER), Kind.SPELL_COOLDOWN, 900_000, "avatar_of_storm", "Avatar of Storm"),
    SORCERER_ENERGY_WAVE_COOLDOWN(voc(Vocation.SORCERER), Kind.SPELL_COOLDOWN, 1_000, "energy_wave", "Energy Wave"),
    SORCERER_DEATH_BEAM_DAMAGE(voc(Vocation.SORCERER), Kind.SPELL_DAMAGE, 10, "great_death_beam", "Great Death Beam"),
    SORCERER_DEATH_BEAM_CRIT(voc(Vocation.SORCERER), Kind.SPELL_CRIT, 15, "great_death_beam", "Great Death Beam"),
    SORCERER_HELLS_CORE_DAMAGE(voc(Vocation.SORCERER), Kind.SPELL_DAMAGE, 8, "hells_core", "Hell's Core"),
    SORCERER_HELLS_CORE_CRIT(voc(Vocation.SORCERER), Kind.SPELL_CRIT, 12, "hells_core", "Hell's Core"),
    SORCERER_ENERGY_WAVE_DAMAGE(voc(Vocation.SORCERER), Kind.SPELL_DAMAGE, 5, "energy_wave", "Energy Wave"),
    SORCERER_ENERGY_WAVE_CRIT(voc(Vocation.SORCERER), Kind.SPELL_CRIT, 12, "energy_wave", "Energy Wave"),
    SORCERER_FIRE_WAVE_DAMAGE(voc(Vocation.SORCERER), Kind.SPELL_DAMAGE, 5, "great_fire_wave", "Great Fire Wave"),
    SORCERER_FIRE_WAVE_CRIT(voc(Vocation.SORCERER), Kind.SPELL_CRIT, 8, "great_fire_wave", "Great Fire Wave"),
    SORCERER_RAGE_DAMAGE(voc(Vocation.SORCERER), Kind.SPELL_DAMAGE, 8, "rage_of_the_skies", "Rage of the Skies"),
    SORCERER_RAGE_CRIT(voc(Vocation.SORCERER), Kind.SPELL_CRIT, 12, "rage_of_the_skies", "Rage of the Skies"),
    SORCERER_ENERGY_BEAM_DAMAGE(voc(Vocation.SORCERER), Kind.SPELL_DAMAGE, 10, "great_energy_beam", "Great Energy Beam"),
    SORCERER_ENERGY_BEAM_CRIT(voc(Vocation.SORCERER), Kind.SPELL_CRIT, 15, "great_energy_beam", "Great Energy Beam"),
    SORCERER_MASTERY_AVATAR(voc(Vocation.SORCERER), Kind.REVELATION, 150, WheelDomain.PURPLE, "Revelation Mastery: Avatar of Storm"),
    SORCERER_MASTERY_BEAM(voc(Vocation.SORCERER), Kind.REVELATION, 150, WheelDomain.RED, "Revelation Mastery: Beam Mastery"),
    SORCERER_MASTERY_DRAIN_BODY(voc(Vocation.SORCERER), Kind.REVELATION, 150, WheelDomain.BLUE, "Revelation Mastery: Drain Body"),
    // ---- druid
    DRUID_AVATAR_COOLDOWN(voc(Vocation.DRUID), Kind.SPELL_COOLDOWN, 900_000, "avatar_of_nature", "Avatar of Nature"),
    DRUID_NATURES_EMBRACE_COOLDOWN(voc(Vocation.DRUID), Kind.SPELL_COOLDOWN, 5_000, "natures_embrace", "Nature's Embrace"),
    DRUID_TERRA_BURST_DAMAGE(voc(Vocation.DRUID), Kind.SPELL_DAMAGE, 7, "terra_burst", "Terra Burst"),
    DRUID_TERRA_BURST_CRIT(voc(Vocation.DRUID), Kind.SPELL_CRIT, 12, "terra_burst", "Terra Burst"),
    DRUID_ICE_BURST_DAMAGE(voc(Vocation.DRUID), Kind.SPELL_DAMAGE, 7, "ice_burst", "Ice Burst"),
    DRUID_ICE_BURST_CRIT(voc(Vocation.DRUID), Kind.SPELL_CRIT, 12, "ice_burst", "Ice Burst"),
    DRUID_ETERNAL_WINTER_CRIT(voc(Vocation.DRUID), Kind.SPELL_CRIT, 12, "eternal_winter", "Eternal Winter"),
    DRUID_ETERNAL_WINTER_DAMAGE(voc(Vocation.DRUID), Kind.SPELL_DAMAGE, 8, "eternal_winter", "Eternal Winter"),
    DRUID_TERRA_WAVE_DAMAGE(voc(Vocation.DRUID), Kind.SPELL_DAMAGE, 5, "terra_wave", "Terra Wave"),
    DRUID_TERRA_WAVE_CRIT(voc(Vocation.DRUID), Kind.SPELL_CRIT, 12, "terra_wave", "Terra Wave"),
    DRUID_ICE_WAVE_DAMAGE(voc(Vocation.DRUID), Kind.SPELL_DAMAGE, 8, "strong_ice_wave", "Strong Ice Wave"),
    DRUID_ICE_WAVE_CRIT(voc(Vocation.DRUID), Kind.SPELL_CRIT, 15, "strong_ice_wave", "Strong Ice Wave"),
    DRUID_HEAL_FRIEND_HEAL(voc(Vocation.DRUID), Kind.SPELL_HEAL, 5, "heal_friend", "Heal Friend"),
    DRUID_MASS_HEALING_HEAL(voc(Vocation.DRUID), Kind.SPELL_HEAL, 5, "mass_healing", "Mass Healing"),
    DRUID_MASTERY_AVATAR(voc(Vocation.DRUID), Kind.REVELATION, 150, WheelDomain.PURPLE, "Revelation Mastery: Avatar of Nature"),
    DRUID_MASTERY_GROVE(voc(Vocation.DRUID), Kind.REVELATION, 150, WheelDomain.RED, "Revelation Mastery: Blessing of the Grove"),
    DRUID_MASTERY_TWIN_BURSTS(voc(Vocation.DRUID), Kind.REVELATION, 150, WheelDomain.BLUE, "Revelation Mastery: Twin Bursts"),
    // ---- mnich
    MONK_AVATAR_COOLDOWN(voc(Vocation.MONK), Kind.SPELL_COOLDOWN, 900_000, "avatar_of_balance", "Avatar of Balance"),
    MONK_SPIRIT_MEND_HEAL(voc(Vocation.MONK), Kind.SPELL_HEAL_FLAT, 6, "spirit_mend", "Spirit Mend"),
    MONK_OUTBURST_DAMAGE(voc(Vocation.MONK), Kind.SPELL_DAMAGE, 5, "spiritual_outburst", "Spiritual Outburst"),
    MONK_OUTBURST_CRIT(voc(Vocation.MONK), Kind.SPELL_CRIT, 8, "spiritual_outburst", "Spiritual Outburst"),
    MONK_UPPERCUT_DAMAGE(voc(Vocation.MONK), Kind.SPELL_DAMAGE, 10, "forceful_uppercut", "Forceful Uppercut"),
    MONK_UPPERCUT_CRIT(voc(Vocation.MONK), Kind.SPELL_CRIT, 8, "forceful_uppercut", "Forceful Uppercut"),
    MONK_FLURRY_DAMAGE(voc(Vocation.MONK), Kind.SPELL_DAMAGE, 6.5, "flurry_of_blows", "Flurry of Blows"),
    MONK_FLURRY_CRIT(voc(Vocation.MONK), Kind.SPELL_CRIT, 8, "flurry_of_blows", "Flurry of Blows"),
    MONK_GREATER_FLURRY_DAMAGE(voc(Vocation.MONK), Kind.SPELL_DAMAGE, 5, "greater_flurry_of_blows", "Greater Flurry of Blows"),
    MONK_GREATER_FLURRY_CRIT(voc(Vocation.MONK), Kind.SPELL_CRIT, 8, "greater_flurry_of_blows", "Greater Flurry of Blows"),
    MONK_TAKEDOWN_DAMAGE(voc(Vocation.MONK), Kind.SPELL_DAMAGE, 5, "sweeping_takedown", "Sweeping Takedown"),
    MONK_TAKEDOWN_CRIT(voc(Vocation.MONK), Kind.SPELL_CRIT, 8, "sweeping_takedown", "Sweeping Takedown"),
    MONK_FOCUS_SERENITY_COOLDOWN(voc(Vocation.MONK), Kind.SPELL_COOLDOWN, 300_000, "focus_serenity", "Focus Serenity"),
    MONK_FOCUS_HARMONY_COOLDOWN(voc(Vocation.MONK), Kind.SPELL_COOLDOWN, 30_000, "focus_harmony", "Focus Harmony"),
    MONK_MASS_SPIRIT_MEND_HEAL(voc(Vocation.MONK), Kind.SPELL_HEAL, 5, "mass_spirit_mend", "Mass Spirit Mend"),
    MONK_MASTERY_AVATAR(voc(Vocation.MONK), Kind.REVELATION, 150, WheelDomain.PURPLE, "Revelation Mastery: Avatar of Balance"),
    MONK_MASTERY_OUTBURST(voc(Vocation.MONK), Kind.REVELATION, 150, WheelDomain.RED, "Revelation Mastery: Spiritual Outburst"),
    MONK_MASTERY_ASCETIC(voc(Vocation.MONK), Kind.REVELATION, 150, WheelDomain.BLUE, "Revelation Mastery: Ascetic");

    public enum Kind { DODGE, CRIT_DAMAGE, LIFE_LEECH, MANA_LEECH, REVELATION, SPELL_DAMAGE, SPELL_CRIT, SPELL_HEAL, SPELL_HEAL_FLAT, SPELL_COOLDOWN }

    /** Profesje (null = wszystkie). */
    private final Set<Vocation> vocations;
    public final Kind kind;
    /** Wartosc stopnia I: procent, punkty objawienia albo milisekundy odnowienia. */
    public final double base;
    /** Klucz czaru Pixel (mody czarow) albo null. */
    public final String spell;
    /** Domena objawienia (Mistrzostwo objawienia) albo null. */
    public final WheelDomain domain;
    /** Nazwa w Tibii. */
    public final String tibia;

    SupremeGemMod(Set<Vocation> vocations, Kind kind, double base, String spell, String tibia) {
        this(vocations, kind, base, spell, null, tibia);
    }

    SupremeGemMod(Set<Vocation> vocations, Kind kind, double base, WheelDomain domain, String tibia) {
        this(vocations, kind, base, null, domain, tibia);
    }

    SupremeGemMod(Set<Vocation> vocations, Kind kind, double base, String spell, WheelDomain domain, String tibia) {
        this.vocations = vocations;
        this.kind = kind;
        this.base = base;
        this.spell = spell;
        this.domain = domain;
        this.tibia = tibia;
    }

    private static Set<Vocation> voc(Vocation... vs) {
        return java.util.Collections.unmodifiableSet(EnumSet.copyOf(java.util.Arrays.asList(vs)));
    }

    public boolean allowed(Vocation v) { return vocations == null || vocations.contains(v); }

    /** Mody najwyzsze dostepne dla profesji (kolejnosc jak w Canary). */
    public static List<SupremeGemMod> of(Vocation v) {
        List<SupremeGemMod> out = new ArrayList<>();
        for (SupremeGemMod m : values()) if (m.allowed(v)) out.add(m);
        return out;
    }

    /** Wartosc przy stopniu 0..3 (odnowienie i leczenie Uleczenia ducha sie nie zmieniaja, jak w Canary). */
    public double value(int grade) {
        if (kind == Kind.SPELL_COOLDOWN || kind == Kind.SPELL_HEAL_FLAT) return base;
        return base * BasicGemMod.GRADE_MULT[Math.max(0, Math.min(3, grade))];
    }

    /** Opis po polsku; names zamienia klucz czaru na nazwe (moze byc null). */
    public String describe(int grade, java.util.function.Function<String, String> names) {
        double v = value(grade);
        String n = spell == null ? "" : names == null ? spell : names.apply(spell);
        switch (kind) {
            case DODGE: return "+" + num(v) + "% uniku";
            case CRIT_DAMAGE: return "+" + num(v) + "% obrażeń krytycznych";
            case LIFE_LEECH: return "+" + num(v) + "% kradzieży życia";
            case MANA_LEECH: return "+" + num(v) + "% kradzieży many";
            case REVELATION: return "Mistrzostwo objawienia: +" + Math.round(v) + " pkt " + domainGenitive(domain) + " domeny do etapu ("
                    + revelationLabel() + ")";
            case SPELL_DAMAGE: return n + ": +" + num(v) + "% obrażeń";
            case SPELL_CRIT: return n + ": +" + num(v) + "% obrażeń krytycznych";
            case SPELL_HEAL: case SPELL_HEAL_FLAT: return n + ": +" + num(v) + "% leczenia";
            default: return n + ": -" + cooldown((long) v) + " odnowienia";
        }
    }

    /** Krotka nazwa do list. */
    public String shortLabel(java.util.function.Function<String, String> names) {
        String n = spell == null ? "" : names == null ? spell : names.apply(spell);
        switch (kind) {
            case DODGE: return "Unik";
            case CRIT_DAMAGE: return "Obrażenia krytyczne";
            case LIFE_LEECH: return "Kradzież życia";
            case MANA_LEECH: return "Kradzież many";
            case REVELATION: return "Mistrzostwo: " + revelationLabel();
            case SPELL_DAMAGE: return n + " (obrażenia)";
            case SPELL_CRIT: return n + " (krytyk)";
            case SPELL_HEAL: case SPELL_HEAL_FLAT: return n + " (leczenie)";
            default: return n + " (odnowienie)";
        }
    }

    /** Premia czaru jako SpellAugment (NONE dla modow bez czaru). */
    public SpellAugment augment(int grade) {
        double v = value(grade);
        switch (kind) {
            case SPELL_DAMAGE: return SpellAugment.damage(v);
            case SPELL_CRIT: return SpellAugment.crit(0, v);
            case SPELL_HEAL: case SPELL_HEAL_FLAT: return SpellAugment.heal(v);
            case SPELL_COOLDOWN: return SpellAugment.cooldown((int) v);
            default: return SpellAugment.NONE;
        }
    }

    /** Nazwa objawienia domeny modu (Mistrzostwo objawienia): zielona = Dar Zycia, reszta wg profesji modu. */
    private String revelationLabel() {
        if (domain == WheelDomain.GREEN || vocations == null) return WheelRevelation.GIFT_OF_LIFE.label;
        return WheelRevelation.of(domain, vocations.iterator().next()).label;
    }

    private static String domainGenitive(WheelDomain d) {
        switch (d) {
            case GREEN: return "zielonej";
            case RED: return "czerwonej";
            case PURPLE: return "fioletowej";
            default: return "niebieskiej";
        }
    }

    static String num(double v) { return SpellAugment.num(Math.round(v * 100) / 100.0); }

    static String cooldown(long ms) {
        if (ms >= 60_000 && ms % 60_000 == 0) return ms / 60_000 + " min";
        return SpellAugment.num(ms / 1000.0) + " s";
    }

    public static SupremeGemMod parse(String s) {
        try {
            return valueOf(s);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
