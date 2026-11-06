package pl.fawizja.pixel.combat;

import java.util.Random;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.item.Enchantments;

/**
 * Ciosy krytyczne jak w Tibii (od 2025; Canary Combat::applyExtensions, configmanager playerBaseCriticalChance
 * 0,05 i playerBaseCriticalDamage 0,1):
 * <ul>
 *   <li>kazda postac ma bazowo {@link #BASE_CHANCE}% szansy na krytyk i +{@link #BASE_DAMAGE}% obrazen krytycznych,</li>
 *   <li>przedmioty dodaja "critical hit chance" i "critical extra damage" ({@link pl.fawizja.pixel.item.ItemBonus}),
 *       zaklecie kowala "Cios krytyczny" dziala jak tibijski Powerful Strike: +{@link Enchantments#CRIT_CHANCE}%
 *       szansy i +{@link Enchantments#CRIT_BONUS_PERCENT}% obrazen; wszystko sie dodaje (nie mnozy),</li>
 *   <li>krytyk losuje sie raz na rzut: cios bronia, czar albo runa - przy czarze obszarowym ten sam wynik dla
 *       wszystkich celow; leczenie nie bywa krytyczne,</li>
 *   <li>dziala na potwory i na graczy (PvP), jak w Canary; charmy Niski cios (+szansa) i Dziki cios (+obrazenia)
 *       tylko przeciw potworowi, na ktory je zalozono ({@link pl.fawizja.pixel.charm.CharmEffects}).</li>
 * </ul>
 */
public final class CriticalHit {
    public static final double BASE_CHANCE = 5;
    public static final double BASE_DAMAGE = 10;
    /** Efekt zdarzenia trafienia (CombatEvent.effect) oznaczajacy cios krytyczny. */
    public static final String EVENT_EFFECT = "CRIT";

    private CriticalHit() {}

    /**
     * Awatar z Transcendencji (Kuznia, spodnie z tierem): do tej chwili (zegar gry) 100% krytyka i +15% obrazen
     * krytycznych. Ustawia CombatSystem; stan ulotny.
     */
    public static boolean avatar(PlayerCharacter pc) { return pc.avatarActive(); }

    /** Szansa na krytyk w procentach (baza + przedmioty + nasycenie Cios/Strike + zaklecie kowala), najwyzej 100. */
    public static double chance(PlayerCharacter pc) {
        if (avatar(pc)) return 100;
        double c = BASE_CHANCE + pc.equipment().critChanceBonus();
        int strikes = 0;
        for (pl.fawizja.pixel.item.ItemStack s : pc.equipment().all().values()) if (s.imbuements().hasStrike()) strikes++;
        c += strikes * pl.fawizja.pixel.item.Imbuement.STRIKE_CHANCE;
        if (pc.enchantments().active(Enchantments.Kind.CRIT)) c += Enchantments.CRIT_CHANCE;
        return Math.min(100, c);
    }

    /** Dodatkowe obrazenia krytyczne w procentach (baza + przedmioty + nasycenie Cios + zaklecie kowala + awatar). */
    public static double extraDamage(PlayerCharacter pc) {
        double d = BASE_DAMAGE + pc.equipment().critDamageBonus()
                + pc.equipment().imbuementSum(pl.fawizja.pixel.item.Imbuement.Category.CRITICAL);
        if (pc.enchantments().active(Enchantments.Kind.CRIT)) d += Enchantments.CRIT_BONUS_PERCENT;
        if (avatar(pc)) d += pl.fawizja.pixel.item.Forge.AVATAR_CRIT_DAMAGE;
        return d;
    }

    /** Jeden rzut na krytyk (szansa w setnych czesciach procenta, jak w Canary). */
    public static boolean roll(PlayerCharacter pc, Random rng) {
        return rng.nextInt(10_000) < (int) Math.round(chance(pc) * 100);
    }

    /** Obrazenia po krytyku: raw * (1 + obrazenia krytyczne). */
    public static int apply(PlayerCharacter pc, int raw, boolean critical) { return apply(pc, raw, critical, 0); }

    /** Jak wyzej z dodatkowymi obrazeniami krytycznymi w procentach (charm Dziki cios przeciw potworowi). */
    public static int apply(PlayerCharacter pc, int raw, boolean critical, double bonusDamage) {
        if (!critical || raw <= 0) return raw;
        return (int) Math.min(Integer.MAX_VALUE, Math.round(raw * (1 + (extraDamage(pc) + bonusDamage) / 100.0)));
    }

    /** Opis do okna postaci: "5% szansy, +10% obrażeń". */
    public static String describe(PlayerCharacter pc) {
        return pl.fawizja.pixel.item.ItemBonus.number(chance(pc)) + "% szansy, +"
                + pl.fawizja.pixel.item.ItemBonus.number(extraDamage(pc)) + "% obrażeń";
    }
}
