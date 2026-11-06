package pl.fawizja.pixel.wheel;

import java.util.EnumSet;
import java.util.Set;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Premie z ulozenia punktow (liczone raz po kazdej zmianie, jak Canary PlayerWheel::loadDedicationAndConvictionPerks
 * + loadRevelationPerks). Niezmienne. {@link #NONE} gdy kolo nieaktywne (brak promocji, premium albo poziomu 51).
 */
public final class WheelBonus {
    public static final WheelBonus NONE = new WheelBonus();

    public final int health;
    public final int mana;
    public final int capacity;
    /** Mitygacja: procent wzgledny (np. 4,5 = obrona i pancerz pochlaniaja o 4,5% wiecej). */
    public final double mitigation;
    /** Premia do umiejetnosci profesji (walka wrecz / dystans / magia / piesci). */
    public final int skill;
    public final double lifeLeech;
    public final double manaLeech;
    /** Stopnie ulepszen czarow grup 0..4 (0, 1 albo 2). */
    private final int[] grades = new int[5];
    /** Stopnie objawienia domen (0..3). */
    private final int[] stages = new int[4];
    private final int[] domainPoints = new int[4];
    public final int flatDamage;
    public final int flatHealing;
    private final Set<WheelPassive> passives;
    /** Premie klejnotow w naczyniach (HP, mana, udzwig, mitygacja i kradziez sa juz wliczone w pola wyzej). */
    private final GemAtelier.Effects gems;

    private WheelBonus() {
        health = mana = capacity = skill = flatDamage = flatHealing = 0;
        mitigation = lifeLeech = manaLeech = 0;
        passives = EnumSet.noneOf(WheelPassive.class);
        gems = GemAtelier.Effects.NONE;
    }

    WheelBonus(Vocation v, int[] points) { this(v, points, GemAtelier.Effects.NONE); }

    WheelBonus(Vocation v, int[] points, GemAtelier.Effects gems) {
        this.gems = gems;
        int hp = 0, mp = 0, cap = 0, sk = 0;
        double mit = 0, ll = 0, ml = 0;
        passives = EnumSet.noneOf(WheelPassive.class);
        for (WheelSlice s : WheelSlice.values()) {
            int p = points[s.ordinal()];
            domainPoints[s.domain.ordinal()] += p;
            switch (s.stat) {
                case HP: hp += WheelPerks.hpPerPoint(v) * p; break;
                case MANA: mp += WheelPerks.manaPerPoint(v) * p; break;
                case HP_MANA:
                    hp += WheelPerks.hpPerPoint(v) * p;
                    mp += WheelPerks.manaPerPoint(v) * p;
                    break;
                case CAP: cap += WheelPerks.capPerPoint(v) * p; break;
                default: mit += WheelPerks.MITIGATION_PER_POINT * p;
            }
            if (p < s.capacity()) continue;
            switch (s.conviction) {
                case SKILL: sk++; break;
                case LIFE_LEECH: ll += WheelPerks.LIFE_LEECH; break;
                case MANA_LEECH: ml += WheelPerks.MANA_LEECH; break;
                case PASSIVE_A: passives.add(WheelPassive.of(v, true)); break;
                case PASSIVE_B: passives.add(WheelPassive.of(v, false)); break;
                case VESSEL: break;
                default: grades[s.conviction.spellGroup()]++;
            }
        }
        int flat = 0;
        for (WheelDomain d : WheelDomain.values()) {
            // Mistrzostwo objawienia (mod najwyzszy klejnotu) dodaje punkty tylko do liczenia etapu (Canary getPlayerSliceStage)
            stages[d.ordinal()] = WheelDomain.stage(domainPoints[d.ordinal()] + gems.revelation(d));
            if (stages[d.ordinal()] > 0) flat += WheelDomain.STAGE_FLAT[stages[d.ordinal()] - 1];
        }
        health = hp + gems.health;
        mana = mp + gems.mana;
        capacity = cap + gems.capacity;
        mitigation = mit + gems.mitigation;
        skill = sk;
        lifeLeech = ll + gems.lifeLeech;
        manaLeech = ml + gems.manaLeech;
        flatDamage = flat;
        flatHealing = flat;
    }

    public int grade(int group) { return grades[group]; }
    public int stage(WheelDomain d) { return stages[d.ordinal()]; }
    public int domainPoints(WheelDomain d) { return domainPoints[d.ordinal()]; }
    /** Premie klejnotow (odpornosci, unik, krytyk, czary, Mistrzostwo objawienia). */
    public GemAtelier.Effects gems() { return gems; }
    public boolean has(WheelPassive p) { return passives.contains(p); }
    public Set<WheelPassive> passives() { return java.util.Collections.unmodifiableSet(passives); }

    /** Stopien objawienia profesji (0..3) - np. stage(DIVINE_GRENADE) dla paladyna. */
    public int stage(WheelRevelation r, Vocation v) {
        for (WheelDomain d : WheelDomain.values()) if (WheelRevelation.of(d, v) == r) return stage(d);
        return 0;
    }
}
