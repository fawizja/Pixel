package pl.fawizja.pixel.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import pl.fawizja.pixel.combat.DamageType;

/**
 * Statystyki walki przedmiotu jak w Tibii (kolumna "premie" w items.csv): ochrony zywiolow (absorbPercent w Canary,
 * ujemna = slabosc, np. Magma Coat "fire +8%, ice -8%"), szansa na cios krytyczny i obrazenia krytyczne
 * ("critical hit chance +X%", "critical extra damage +Y%") oraz kradziez zycia i many ("life leech", "mana leech").
 * Zapis: {@code FIRE:8,ICE:-8,CRIT:10,CRITDMG:12,LIFELEECH:5,MANALEECH:3}; puste pole albo "-" = brak premii.
 * Ulamki dozwolone ("LIFELEECH:2.5"). Imbuementy (Vampirism, Void, Strike) dodadza sie do tych samych sum
 * ({@link pl.fawizja.pixel.combat.CriticalHit}, {@link pl.fawizja.pixel.combat.Leech}).
 */
public final class ItemBonus {
    public static final ItemBonus NONE = new ItemBonus(new EnumMap<>(DamageType.class), 0, 0, 0, 0);

    private final EnumMap<DamageType, Integer> protection;
    private final double critChance;
    private final double critDamage;
    private final double lifeLeech;
    private final double manaLeech;

    public ItemBonus(Map<DamageType, Integer> protection, double critChance, double critDamage, double lifeLeech, double manaLeech) {
        this.protection = protection.isEmpty() ? new EnumMap<>(DamageType.class) : new EnumMap<>(protection);
        for (int v : this.protection.values()) {
            if (v < -100 || v > 100) throw new IllegalArgumentException("protection -100..100");
        }
        if (critChance < 0 || critDamage < 0 || lifeLeech < 0 || manaLeech < 0 || critChance > 100 || lifeLeech > 100 || manaLeech > 100) {
            throw new IllegalArgumentException("bad critical/leech bonus");
        }
        this.critChance = critChance;
        this.critDamage = critDamage;
        this.lifeLeech = lifeLeech;
        this.manaLeech = manaLeech;
    }

    /** Ochrona przed rodzajem obrazen w procentach (ujemna = otrzymuje wiecej). */
    public int protection(DamageType t) { return protection.getOrDefault(t, 0); }
    public Map<DamageType, Integer> protections() { return Collections.unmodifiableMap(protection); }
    /** Dodatkowa szansa na cios krytyczny (punkty procentowe). */
    public double critChance() { return critChance; }
    /** Dodatkowe obrazenia krytyczne (procent obrazen ciosu). */
    public double critDamage() { return critDamage; }
    public double lifeLeech() { return lifeLeech; }
    public double manaLeech() { return manaLeech; }

    public boolean isEmpty() {
        return protection.isEmpty() && critChance == 0 && critDamage == 0 && lifeLeech == 0 && manaLeech == 0;
    }

    public static ItemBonus parse(String s) {
        if (s == null) return NONE;
        s = s.trim();
        if (s.isEmpty() || s.equals("-")) return NONE;
        EnumMap<DamageType, Integer> prot = new EnumMap<>(DamageType.class);
        double cc = 0, cd = 0, ll = 0, ml = 0;
        for (String part : s.split(",")) {
            String[] kv = part.split(":");
            if (kv.length != 2) throw new IllegalArgumentException("bonus KEY:value: " + part);
            String k = kv[0].trim();
            String v = kv[1].trim();
            switch (k) {
                case "CRIT": cc = Double.parseDouble(v); break;
                case "CRITDMG": cd = Double.parseDouble(v); break;
                case "LIFELEECH": ll = Double.parseDouble(v); break;
                case "MANALEECH": ml = Double.parseDouble(v); break;
                default: prot.put(DamageType.valueOf(k), Integer.parseInt(v));
            }
        }
        return new ItemBonus(prot, cc, cd, ll, ml);
    }

    /** Liczba bez zbednego ",0": 5 albo 2,5. */
    public static String number(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.valueOf(Math.round(v * 10) / 10.0).replace('.', ',');
    }

    /** Ochrony jako tekst: "ogień +8%, lód -8%" (pusty, gdy brak). */
    public static String describeProtections(Map<DamageType, Integer> prot) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<DamageType, Integer> e : prot.entrySet()) {
            if (e.getValue() == 0) continue;
            parts.add(e.getKey().label() + " " + (e.getValue() > 0 ? "+" : "") + e.getValue() + "%");
        }
        return String.join(", ", parts);
    }

    /**
     * Opis do okna przedmiotu (jak "Look" w Tibii): "ochrona ogień +8%, lód -8%; krytyk +10% szansy, +12% obrażeń;
     * kradzież życia 5%, many 3%". Pusty, gdy brak premii.
     */
    public String describe() {
        List<String> parts = new ArrayList<>();
        String prot = describeProtections(protection);
        if (!prot.isEmpty()) parts.add("ochrona " + prot);
        if (critChance > 0 || critDamage > 0) {
            List<String> c = new ArrayList<>();
            if (critChance > 0) c.add("+" + number(critChance) + "% szansy");
            if (critDamage > 0) c.add("+" + number(critDamage) + "% obrażeń");
            parts.add("krytyk " + String.join(", ", c));
        }
        if (lifeLeech > 0 || manaLeech > 0) {
            List<String> l = new ArrayList<>();
            if (lifeLeech > 0) l.add("życia " + number(lifeLeech) + "%");
            if (manaLeech > 0) l.add("many " + number(manaLeech) + "%");
            parts.add("kradzież " + String.join(", ", l));
        }
        return String.join("; ", parts);
    }
}
