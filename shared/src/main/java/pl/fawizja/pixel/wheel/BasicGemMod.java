package pl.fawizja.pixel.wheel;

import pl.fawizja.pixel.i18n.Messages;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import pl.fawizja.pixel.combat.DamageType;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Modyfikatory podstawowe klejnotow (Canary WheelGemBasicModifier_t, wartosci z WheelModifierContext::addStrategies
 * i WheelGemUtils). Odpornosci w setnych czesciach procenta (100 = 1%), slabosci (ujemne) nie rosna ze stopniem.
 * HP, mana i udzwig zaleza od profesji. Mod 1 klejnotu losowany z {@link #SLOT1}, mod 2 z {@link #SLOT2} (inny niz 1).
 * Stopien modu (I-IV) mnozy wartosc x1 / x1,1 / x1,2 / x1,5 i jest wspolny dla wszystkich klejnotow postaci.
 */
public enum BasicGemMod {
    PHYSICAL_RES(res(DamageType.PHYSICAL, 100)),
    HOLY_RES(res(DamageType.HOLY, 100)),
    DEATH_RES(res(DamageType.DEATH, 100)),
    FIRE_RES(res(DamageType.FIRE, 200)),
    EARTH_RES(res(DamageType.EARTH, 200)),
    ICE_RES(res(DamageType.ICE, 200)),
    ENERGY_RES(res(DamageType.ENERGY, 200)),
    HOLY_RES_DEATH_WEAK(res(DamageType.HOLY, 150), weak(DamageType.DEATH, 100)),
    DEATH_RES_HOLY_WEAK(res(DamageType.DEATH, 150), weak(DamageType.HOLY, 100)),
    FIRE_EARTH_RES(res(DamageType.FIRE, 100), res(DamageType.EARTH, 100)),
    FIRE_ICE_RES(res(DamageType.FIRE, 100), res(DamageType.ICE, 100)),
    FIRE_ENERGY_RES(res(DamageType.FIRE, 100), res(DamageType.ENERGY, 100)),
    EARTH_ICE_RES(res(DamageType.EARTH, 100), res(DamageType.ICE, 100)),
    EARTH_ENERGY_RES(res(DamageType.EARTH, 100), res(DamageType.ENERGY, 100)),
    ICE_ENERGY_RES(res(DamageType.ICE, 100), res(DamageType.ENERGY, 100)),
    FIRE_RES_EARTH_WEAK(res(DamageType.FIRE, 300), weak(DamageType.EARTH, 200)),
    FIRE_RES_ICE_WEAK(res(DamageType.FIRE, 300), weak(DamageType.ICE, 200)),
    FIRE_RES_ENERGY_WEAK(res(DamageType.FIRE, 300), weak(DamageType.ENERGY, 200)),
    EARTH_RES_FIRE_WEAK(res(DamageType.EARTH, 300), weak(DamageType.FIRE, 200)),
    EARTH_RES_ICE_WEAK(res(DamageType.EARTH, 300), weak(DamageType.ICE, 200)),
    EARTH_RES_ENERGY_WEAK(res(DamageType.EARTH, 300), weak(DamageType.ENERGY, 200)),
    ICE_RES_EARTH_WEAK(res(DamageType.ICE, 300), weak(DamageType.EARTH, 200)),
    ICE_RES_FIRE_WEAK(res(DamageType.ICE, 300), weak(DamageType.FIRE, 200)),
    ICE_RES_ENERGY_WEAK(res(DamageType.ICE, 300), weak(DamageType.ENERGY, 200)),
    ENERGY_RES_EARTH_WEAK(res(DamageType.ENERGY, 300), weak(DamageType.EARTH, 200)),
    ENERGY_RES_ICE_WEAK(res(DamageType.ENERGY, 300), weak(DamageType.ICE, 200)),
    ENERGY_RES_FIRE_WEAK(res(DamageType.ENERGY, 300), weak(DamageType.FIRE, 200)),
    MANA_DRAIN_RES(res(DamageType.MANA_DRAIN, 300)),
    LIFE_DRAIN_RES(res(DamageType.LIFE_DRAIN, 300)),
    DRAIN_RES(res(DamageType.MANA_DRAIN, 150), res(DamageType.LIFE_DRAIN, 150)),
    MITIGATION(new Part(Kind.MITIGATION, null, 500, true)),
    HEALTH(voc(Kind.HEALTH, true)),
    MANA_FIRE_RES(voc(Kind.MANA, false), res(DamageType.FIRE, 100)),
    MANA_ENERGY_RES(voc(Kind.MANA, false), res(DamageType.ENERGY, 100)),
    MANA_EARTH_RES(voc(Kind.MANA, false), res(DamageType.EARTH, 100)),
    MANA_ICE_RES(voc(Kind.MANA, false), res(DamageType.ICE, 100)),
    MANA(voc(Kind.MANA, true)),
    HEALTH_FIRE_RES(voc(Kind.HEALTH, false), res(DamageType.FIRE, 100)),
    HEALTH_ENERGY_RES(voc(Kind.HEALTH, false), res(DamageType.ENERGY, 100)),
    HEALTH_EARTH_RES(voc(Kind.HEALTH, false), res(DamageType.EARTH, 100)),
    HEALTH_ICE_RES(voc(Kind.HEALTH, false), res(DamageType.ICE, 100)),
    CAPACITY_FIRE_RES(voc(Kind.CAPACITY, false), res(DamageType.FIRE, 100)),
    CAPACITY_ENERGY_RES(voc(Kind.CAPACITY, false), res(DamageType.ENERGY, 100)),
    CAPACITY_EARTH_RES(voc(Kind.CAPACITY, false), res(DamageType.EARTH, 100)),
    CAPACITY_ICE_RES(voc(Kind.CAPACITY, false), res(DamageType.ICE, 100)),
    CAPACITY(voc(Kind.CAPACITY, true));

    /** Mnoznik stopnia I..IV (Canary gradeMultiplier). */
    static final double[] GRADE_MULT = {1.0, 1.1, 1.2, 1.5};

    /** Mozliwe mody 1 (Canary wheelGemBasicSlot1Allowed). */
    public static final List<BasicGemMod> SLOT1 = Collections.unmodifiableList(List.of(
            FIRE_RES, ICE_RES, ENERGY_RES, EARTH_RES, MITIGATION, HEALTH, MANA, CAPACITY,
            HEALTH_FIRE_RES, HEALTH_ICE_RES, HEALTH_ENERGY_RES, HEALTH_EARTH_RES,
            MANA_FIRE_RES, MANA_ENERGY_RES, MANA_EARTH_RES, MANA_ICE_RES,
            CAPACITY_FIRE_RES, CAPACITY_ENERGY_RES, CAPACITY_EARTH_RES, CAPACITY_ICE_RES));

    /** Mozliwe mody 2 (Canary wheelGemBasicSlot2Allowed). */
    public static final List<BasicGemMod> SLOT2 = Collections.unmodifiableList(List.of(
            FIRE_RES, ICE_RES, ENERGY_RES, EARTH_RES, PHYSICAL_RES, HOLY_RES, HOLY_RES_DEATH_WEAK, DEATH_RES_HOLY_WEAK,
            FIRE_EARTH_RES, FIRE_ICE_RES, FIRE_ENERGY_RES, EARTH_ICE_RES, EARTH_ENERGY_RES, ICE_ENERGY_RES,
            FIRE_RES_EARTH_WEAK, FIRE_RES_ICE_WEAK, FIRE_RES_ENERGY_WEAK, EARTH_RES_FIRE_WEAK, EARTH_RES_ICE_WEAK,
            EARTH_RES_ENERGY_WEAK, ICE_RES_EARTH_WEAK, ICE_RES_FIRE_WEAK, ICE_RES_ENERGY_WEAK, ENERGY_RES_EARTH_WEAK,
            ENERGY_RES_ICE_WEAK, ENERGY_RES_FIRE_WEAK, MANA_DRAIN_RES, LIFE_DRAIN_RES, DRAIN_RES, MITIGATION));

    enum Kind { RESIST, MITIGATION, HEALTH, MANA, CAPACITY }

    /** Czesc modu: rodzaj, rodzaj obrazen (odpornosc), wartosc bazowa i czy rosnie ze stopniem (slabosc nie rosnie). */
    record Part(Kind kind, DamageType type, int base, boolean scales) {}

    private final List<Part> parts;

    BasicGemMod(Part... parts) { this.parts = List.of(parts); }

    private static Part res(DamageType t, int v) { return new Part(Kind.RESIST, t, v, true); }
    private static Part weak(DamageType t, int v) { return new Part(Kind.RESIST, t, -v, false); }
    /** full = pelny mod (HP/mana/udzwig sam), inaczej polowa (z odpornoscia). */
    private static Part voc(Kind k, boolean full) { return new Part(k, null, full ? 2 : 1, true); }

    List<Part> parts() { return parts; }

    /** Wartosc HP / many / udzwigu dla profesji (Canary WheelGemUtils; udzwig mnicha jak rycerza - niezweryfikowane). */
    static int vocationValue(Kind k, Vocation v, int half2full) {
        int full;
        switch (k) {
            case HEALTH:
                full = v == Vocation.KNIGHT ? 300 : v == Vocation.PALADIN || v == Vocation.MONK ? 200 : 100;
                break;
            case MANA:
                full = v == Vocation.KNIGHT ? 100 : v == Vocation.PALADIN ? 300 : v == Vocation.MONK ? 200 : 600;
                break;
            default:
                full = v == Vocation.KNIGHT || v == Vocation.MONK ? 500 : v == Vocation.PALADIN ? 400 : 200;
        }
        return half2full == 2 ? full : full / 2;
    }

    /** Wartosc czesci przy stopniu (0..3) dla profesji: odpornosc w setnych procenta, mitygacja w setnych procenta. */
    static double value(Part p, Vocation v, int grade) {
        double base = p.kind == Kind.RESIST || p.kind == Kind.MITIGATION ? p.base : vocationValue(p.kind, v, p.base);
        return p.scales ? base * GRADE_MULT[Math.max(0, Math.min(3, grade))] : base;
    }

    /** Opis po polsku dla profesji i stopnia, np. "+330 HP, +1,1% odporności na ogień". */
    public String describe(Vocation v, int grade) {
        List<String> out = new ArrayList<>();
        for (Part p : parts) {
            double val = value(p, v, grade);
            switch (p.kind) {
                case HEALTH: out.add("+" + Math.round(val) + " HP"); break;
                case MANA: out.add("+" + Math.round(val) + " many"); break;
                case CAPACITY: out.add("+" + Math.round(val) + Messages.get("basic_gem_mod.udzwigu")); break;
                case MITIGATION: out.add("+" + pct(val) + Messages.get("basic_gem_mod.mitygacji")); break;
                default:
                    out.add((val >= 0 ? "+" : "") + pct(val) + Messages.get("basic_gem_mod.odp") + p.type.label() + ")");
            }
        }
        return String.join(", ", out);
    }

    /** Krotka nazwa do list: "Ogień", "HP + ogień", "Święte / słabość: śmierć". */
    public String shortLabel() {
        StringBuilder b = new StringBuilder();
        for (Part p : parts) {
            String s;
            switch (p.kind) {
                case HEALTH: s = "HP"; break;
                case MANA: s = "mana"; break;
                case CAPACITY: s = Messages.get("basic_gem_mod.udzwig"); break;
                case MITIGATION: s = "mitygacja"; break;
                default: s = p.base < 0 ? Messages.get("basic_gem_mod.slabosc") + p.type.label() : p.type.label();
            }
            if (b.length() > 0) b.append(p.base < 0 ? " / " : " + ");
            b.append(s);
        }
        return Character.toUpperCase(b.charAt(0)) + b.substring(1);
    }

    /** Setne czesci procenta jako tekst procentow: 110 -> "1,1". */
    static String pct(double hundredths) { return SpellAugment.num(Math.round(hundredths) / 100.0); }

    public static BasicGemMod parse(String s) {
        try {
            return valueOf(s);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
