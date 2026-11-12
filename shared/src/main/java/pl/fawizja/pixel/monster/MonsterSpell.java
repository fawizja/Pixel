package pl.fawizja.pixel.monster;

import pl.fawizja.pixel.combat.DamageType;
import pl.fawizja.pixel.magic.SpellArea;

/**
 * Czar potwora z data/monster_spells.csv, jak czary potworow w Canary (data-canary/monster/*: fala ognia, wielka kula
 * ognia, oddech lodu). Potwor probuje czarow przy kazdym ataku (kazdy z wlasna szansa); gdy zaden nie wypadnie,
 * bije wrecz. Obszar to macierz Canary z {@link SpellArea}.
 *
 * @param mode      WAVE - fala albo promien przed potworem (obraca sie do celu), BALL - pocisk i wybuch wokol celu
 *                  (wielka kula ognia), SELF - wybuch wokol potwora
 * @param min       obrazenia od (przed obrona gracza), max - do
 * @param chance    szansa (procent) przy kazdym ataku potwora
 * @param range     zasieg w polach (od potwora do celu)
 * @param condition stan nakladany na trafionego (podpalenie, zamrozenie...) albo null
 * @param slowPercent spowolnienie trafionego gracza (jak paraliz w Tibii) w procentach, 0 = brak
 */
public record MonsterSpell(String key, Mode mode, SpellArea area, DamageType type, int min, int max, int chance, int range,
                           MonsterConditions.Entry condition, int slowPercent, long slowMs) {

    public enum Mode { WAVE, BALL, SELF }

    public MonsterSpell {
        if (key == null || key.isBlank() || mode == null || area == null || type == null) throw new IllegalArgumentException("bad monster spell");
        if (min < 0 || max < min || chance < 1 || chance > 100 || range < 1) throw new IllegalArgumentException("bad monster spell " + key);
        if (mode == Mode.WAVE && !area.directional()) throw new IllegalArgumentException("wave needs a directional area: " + key);
        if (mode != Mode.WAVE && area.directional()) throw new IllegalArgumentException("ball/self needs a plain area: " + key);
        if (slowPercent < 0 || slowPercent > 90 || (slowPercent > 0) != (slowMs > 0)) throw new IllegalArgumentException("bad slow " + key);
    }

    /** Odmiana elitarna (Kuznia) zadaje wiecej: obrazenia mnozone przez f, jak jej ciosy. */
    public MonsterSpell scaled(double f) {
        return new MonsterSpell(key, mode, area, type, (int) Math.round(min * f), (int) Math.round(max * f), chance, range,
                condition, slowPercent, slowMs);
    }
}
