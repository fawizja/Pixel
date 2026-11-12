package pl.fawizja.pixel.monster;

import pl.fawizja.pixel.combat.DamageType;
import pl.fawizja.pixel.magic.SpellArea;

/**
 * Czar potwora z data/monster_spells.csv, jak czary potworow w Canary (data-canary/monster/*: fala ognia, wielka kula
 * ognia, oddech lodu, pioruny, leczenie, przywolanie, przyspieszenie). Potwor probuje czarow przy kazdym ataku (kazdy
 * z wlasna szansa); gdy zaden nie wypadnie, uzywa ataku podstawowego (cios wrecz albo pocisk). Obszar to macierz Canary
 * z {@link SpellArea}.
 *
 * @param mode      WAVE - fala albo promien przed potworem (obraca sie do celu), BALL - pocisk i wybuch wokol celu
 *                  (wielka kula ognia), SELF - wybuch wokol potwora, BOLT - pocisk w jeden cel (uderzenie energii,
 *                  ognia, strzal), HEAL - leczenie siebie, HASTE - przyspieszenie siebie, SUMMON - przywolanie pomocnikow
 * @param missile   nazwa grafiki pocisku (assets/art/missiles) albo null - wtedy wg rodzaju obrazen
 * @param summonKey potwor przywolywany (SUMMON), summonCap - najwyzej tylu naraz na jednego przywolujacego
 * @param hastePercent przyspieszenie ruchu w procentach (HASTE) na hasteMs
 * @param min       obrazenia od (przed obrona gracza), max - do
 * @param chance    szansa (procent) przy kazdym ataku potwora
 * @param range     zasieg w polach (od potwora do celu)
 * @param condition stan nakladany na trafionego (podpalenie, zamrozenie...) albo null
 * @param slowPercent spowolnienie trafionego gracza (jak paraliz w Tibii) w procentach, 0 = brak
 */
public record MonsterSpell(String key, Mode mode, SpellArea area, DamageType type, int min, int max, int chance, int range,
                           MonsterConditions.Entry condition, int slowPercent, long slowMs, String missile,
                           String summonKey, int summonCap, int hastePercent, long hasteMs) {

    public enum Mode {
        WAVE, BALL, SELF, BOLT, HEAL, HASTE, SUMMON;

        /** Czar zadajacy obrazenia graczowi (pozostale to wsparcie potwora: leczenie, przyspieszenie, przywolanie). */
        public boolean attack() { return this == WAVE || this == BALL || this == SELF || this == BOLT; }
    }

    /** Czar bez dodatkow wsparcia (stare wywolania). */
    public MonsterSpell(String key, Mode mode, SpellArea area, DamageType type, int min, int max, int chance, int range,
                        MonsterConditions.Entry condition, int slowPercent, long slowMs) {
        this(key, mode, area, type, min, max, chance, range, condition, slowPercent, slowMs, null, null, 0, 0, 0);
    }

    public boolean attack() { return mode.attack(); }

    public MonsterSpell {
        if (key == null || key.isBlank() || mode == null || area == null || type == null) throw new IllegalArgumentException("bad monster spell");
        if (min < 0 || max < min || chance < 1 || chance > 100 || range < 1) throw new IllegalArgumentException("bad monster spell " + key);
        if (mode == Mode.WAVE && !area.directional()) throw new IllegalArgumentException("wave needs a directional area: " + key);
        if (mode != Mode.WAVE && area.directional()) throw new IllegalArgumentException("ball/self needs a plain area: " + key);
        if (slowPercent < 0 || slowPercent > 90 || (slowPercent > 0) != (slowMs > 0)) throw new IllegalArgumentException("bad slow " + key);
        if (mode == Mode.HEAL && max <= 0) throw new IllegalArgumentException("heal needs an amount: " + key);
        if (mode == Mode.SUMMON && (summonKey == null || summonKey.isBlank() || summonCap < 1)) throw new IllegalArgumentException("bad summon " + key);
        if (mode == Mode.HASTE && (hastePercent < 10 || hastePercent > 200 || hasteMs <= 0)) throw new IllegalArgumentException("bad haste " + key);
    }

    /** Odmiana elitarna (Kuznia) zadaje wiecej: obrazenia mnozone przez f, jak jej ciosy. */
    public MonsterSpell scaled(double f) {
        return new MonsterSpell(key, mode, area, type, (int) Math.round(min * f), (int) Math.round(max * f), chance, range,
                condition, slowPercent, slowMs, missile, summonKey, summonCap, hastePercent, hasteMs);
    }
}
