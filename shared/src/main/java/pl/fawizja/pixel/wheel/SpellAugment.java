package pl.fawizja.pixel.wheel;

/**
 * Warstwa modyfikatorow czaru z Kola Przeznaczenia (Canary WheelSpellBoost_t): mniej many, krotsze odnowienie,
 * wiecej obrazen lub leczenia (procent), kradziez zycia/many czaru, szansa i obrazenia krytyczne, dodatkowe cele
 * i promien. Wartosci stopni sie dodaja (stopien 2 = stopien 1 + ulepszenie), jak Spell::getWheelOfDestinyBoost.
 * Niezmienny; {@link #NONE} = brak premii.
 */
public final class SpellAugment {
    public static final SpellAugment NONE = new SpellAugment(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    public final int manaCut;
    public final int cooldownCutMs;
    public final double damagePercent;
    public final double healPercent;
    public final double lifeLeech;
    public final double manaLeech;
    public final double critChance;
    public final double critDamage;
    public final int extraTargets;
    public final int extraRadius;

    public SpellAugment(int manaCut, int cooldownCutMs, double damagePercent, double healPercent, double lifeLeech,
                        double manaLeech, double critChance, double critDamage, int extraTargets, int extraRadius) {
        this.manaCut = manaCut;
        this.cooldownCutMs = cooldownCutMs;
        this.damagePercent = damagePercent;
        this.healPercent = healPercent;
        this.lifeLeech = lifeLeech;
        this.manaLeech = manaLeech;
        this.critChance = critChance;
        this.critDamage = critDamage;
        this.extraTargets = extraTargets;
        this.extraRadius = extraRadius;
    }

    public static SpellAugment mana(int v) { return new SpellAugment(v, 0, 0, 0, 0, 0, 0, 0, 0, 0); }
    public static SpellAugment cooldown(int ms) { return new SpellAugment(0, ms, 0, 0, 0, 0, 0, 0, 0, 0); }
    public static SpellAugment damage(double p) { return new SpellAugment(0, 0, p, 0, 0, 0, 0, 0, 0, 0); }
    public static SpellAugment heal(double p) { return new SpellAugment(0, 0, 0, p, 0, 0, 0, 0, 0, 0); }
    public static SpellAugment lifeLeech(double p) { return new SpellAugment(0, 0, 0, 0, p, 0, 0, 0, 0, 0); }
    public static SpellAugment manaLeech(double p) { return new SpellAugment(0, 0, 0, 0, 0, p, 0, 0, 0, 0); }
    public static SpellAugment crit(double chance, double dmg) { return new SpellAugment(0, 0, 0, 0, 0, 0, chance, dmg, 0, 0); }
    public static SpellAugment targets(int n) { return new SpellAugment(0, 0, 0, 0, 0, 0, 0, 0, n, 0); }
    public static SpellAugment radius(int n) { return new SpellAugment(0, 0, 0, 0, 0, 0, 0, 0, 0, n); }

    public SpellAugment plus(SpellAugment o) {
        if (o == null || o == NONE) return this;
        if (this == NONE) return o;
        return new SpellAugment(manaCut + o.manaCut, cooldownCutMs + o.cooldownCutMs, damagePercent + o.damagePercent,
                healPercent + o.healPercent, lifeLeech + o.lifeLeech, manaLeech + o.manaLeech, critChance + o.critChance,
                critDamage + o.critDamage, extraTargets + o.extraTargets, extraRadius + o.extraRadius);
    }

    public boolean isEmpty() {
        return manaCut == 0 && cooldownCutMs == 0 && damagePercent == 0 && healPercent == 0 && lifeLeech == 0 && manaLeech == 0
                && critChance == 0 && critDamage == 0 && extraTargets == 0 && extraRadius == 0;
    }

    /** Opis po polsku: "-30 many, +10% obrażeń". */
    public String describe() {
        StringBuilder b = new StringBuilder();
        if (manaCut != 0) part(b, "-" + manaCut + " many");
        if (cooldownCutMs != 0) part(b, "-" + num(cooldownCutMs / 1000.0) + " s odnowienia");
        if (damagePercent != 0) part(b, "+" + num(damagePercent) + "% obrażeń");
        if (healPercent != 0) part(b, "+" + num(healPercent) + "% leczenia");
        if (lifeLeech != 0) part(b, num(lifeLeech) + "% kradzieży życia");
        if (manaLeech != 0) part(b, num(manaLeech) + "% kradzieży many");
        if (critChance != 0) part(b, "+" + num(critChance) + "% szansy na krytyk");
        if (critDamage != 0) part(b, "+" + num(critDamage) + "% obrażeń krytycznych");
        if (extraTargets != 0) part(b, "+" + extraTargets + (extraTargets == 1 ? " cel" : " cele"));
        if (extraRadius != 0) part(b, "+" + extraRadius + " pole zasięgu");
        return b.toString();
    }

    private static void part(StringBuilder b, String s) {
        if (b.length() > 0) b.append(", ");
        b.append(s);
    }

    static String num(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v).replace('.', ',');
    }
}
