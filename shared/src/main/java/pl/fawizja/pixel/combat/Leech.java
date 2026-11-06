package pl.fawizja.pixel.combat;

import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.item.Enchantments;

/**
 * Kradziez zycia i many jak w Tibii (Canary Game::applyLifeLeech / applyManaLeech / calculateLeechAmount): zawsze
 * (100% szansy) z kazdego trafienia leczy obrazenia * % * (0,1 * n + 0,9) / n, gdzie n = liczba celow trafionych tym
 * samym rzutem - czar obszarowy w wiele celow daje mniej na cel (2 cele: 55% na cel, 8 celow: 21%). Wynik zaokraglony
 * (jak lround), najwyzej zadane obrazenia.
 * <p>Procent to suma przedmiotow ({@link pl.fawizja.pixel.item.ItemBonus}), zaklec kowala (LIFE / MANA) i nasycen
 * Wampiryzm / Vampirism (5/10/25%) i Pustka / Void (3/5/8%) z zalozonych przedmiotow ({@link pl.fawizja.pixel.item.Imbuement}).
 * Dziala na potwory i graczy (PvP), jak w Canary.
 */
public final class Leech {
    private Leech() {}

    /** Kradziez z jednego trafienia: dealt * percent% * (0,1n + 0,9) / n. */
    public static int amount(int dealt, double percent, int targets) {
        if (dealt <= 0 || percent <= 0) return 0;
        int n = Math.max(1, targets);
        double v = dealt * (percent / 100.0) * (0.1 * n + 0.9) / n;
        return (int) Math.max(0, Math.min(dealt, Math.round(v)));
    }

    /** Kradziez zycia postaci w procentach (przedmioty + nasycenia + zaklecie kowala). */
    public static double lifePercent(PlayerCharacter pc) {
        double p = pc.equipment().lifeLeech() + pc.equipment().imbuementSum(pl.fawizja.pixel.item.Imbuement.Category.LIFE_LEECH);
        if (pc.enchantments().active(Enchantments.Kind.LIFE)) p += Enchantments.LIFE_PERCENT;
        return p;
    }

    /** Kradziez many postaci w procentach (przedmioty + nasycenia + zaklecie kowala). */
    public static double manaPercent(PlayerCharacter pc) {
        double p = pc.equipment().manaLeech() + pc.equipment().imbuementSum(pl.fawizja.pixel.item.Imbuement.Category.MANA_LEECH);
        if (pc.enchantments().active(Enchantments.Kind.MANA)) p += Enchantments.MANA_PERCENT;
        return p;
    }

    /** Leczy postac i oddaje mane po trafieniu (dealt obrazen, targets celow tego rzutu). */
    public static void apply(PlayerCharacter pc, int dealt, int targets) {
        if (pc.isDead() || dealt <= 0) return;
        pc.heal(amount(dealt, lifePercent(pc), targets));
        if (!pc.isDead()) pc.restoreMana(amount(dealt, manaPercent(pc), targets));
    }
}
