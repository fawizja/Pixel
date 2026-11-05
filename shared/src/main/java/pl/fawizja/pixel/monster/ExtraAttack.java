package pl.fawizja.pixel.monster;

import pl.fawizja.pixel.combat.DamageType;

/**
 * Drugi atak potwora (kolumna "atak dodatkowy" w monsters.csv), jak czary potworow w Tibii: zamiast zwyklego ataku
 * w percent% atakow potwor zadaje min..max obrazen typu type (np. szkielet w Tibii: cios wrecz i wyssanie zycia
 * 7-13). Zasieg i odstep ataku jak ataku glownego. Zapis: {@code LIFE_DRAIN:20-40:30}; "-" = brak.
 */
public record ExtraAttack(DamageType type, int min, int max, int percent) {
    public ExtraAttack {
        if (min < 0 || max < min || percent < 1 || percent > 100) throw new IllegalArgumentException("bad extra attack");
    }

    public static ExtraAttack parse(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty() || s.equals("-")) return null;
        String[] f = s.split(":");
        if (f.length != 3) throw new IllegalArgumentException("extra attack TYPE:min-max:percent: " + s);
        String[] mm = f[1].split("-");
        return new ExtraAttack(DamageType.valueOf(f[0].trim()), Integer.parseInt(mm[0].trim()), Integer.parseInt(mm[1].trim()),
                Integer.parseInt(f[2].trim()));
    }

    /** Odmiana elitarna: obrazenia x1,3 jak atak glowny. */
    ExtraAttack scaled(double f) {
        return new ExtraAttack(type, (int) Math.round(min * f), (int) Math.round(max * f), percent);
    }
}
