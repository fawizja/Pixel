package pl.fawizja.pixel.wheel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Klejnoty w lupie (Canary data/libs/functions/gematelier.lua, Monster:generateGemAtelierLoot): tylko potwory pod
 * wplywem, diaboliczne i "archfoe" (u nas: bossowie od poziomu {@link #ARCHFOE_MIN_LEVEL}). Szanse na 100 000:
 * <pre>
 *                 pod wplywem  diaboliczny  archfoe   najwiecej
 *   mniejszy         9000         3000          0         2
 *   zwykly              0         3000       9000         2
 *   wiekszy             0         9000       3000         1
 * </pre>
 * Kolejny rzut tej samej jakosci jest tylko po udanym poprzednim (goto continue w Canary). Profesja klejnotu jest
 * losowa (5 rodzin), wiec klejnoty innych profesji mozna sprzedac albo wymienic.
 */
public final class GemDrops {
    /** Bossowie od tego poziomu licza sie jak archfoe z Tibii (slabsi bossowie jak "bane" nic nie daja). */
    public static final int ARCHFOE_MIN_LEVEL = 200;
    private static final int[][] CHANCE = {
            // influenced, fiendish, archfoe
            {9000, 3000, 0},
            {0, 3000, 9000},
            {0, 9000, 3000}};
    private static final int[] MAX_COUNT = {2, 2, 1};

    private GemDrops() {}

    /** Kategoria potwora: 0 pod wplywem, 1 diaboliczny, 2 archfoe, -1 brak klejnotow. */
    public static int category(MonsterType t) {
        if (t.isFiendish()) return 1;
        if (t.isInfluenced()) return 0;
        if (t.boss() && t.level() >= ARCHFOE_MIN_LEVEL) return 2;
        return -1;
    }

    /** Szansa (na 100 000) pierwszej sztuki jakosci q z potwora (0 = nigdy). */
    public static int chance(MonsterType t, GemQuality q) {
        int c = category(t);
        return c < 0 ? 0 : CHANCE[q.ordinal()][c];
    }

    /** Losuje klejnoty: klucz przedmiotu -> ilosc (pusta mapa = nic). */
    public static Map<String, Integer> roll(MonsterType t, Random rng) {
        Map<String, Integer> out = new LinkedHashMap<>();
        int c = category(t);
        if (c < 0) return out;
        Vocation[] vs = Vocation.values();
        for (GemQuality q : GemQuality.values()) {
            int chance = CHANCE[q.ordinal()][c];
            if (chance <= 0) continue;
            for (int i = 0; i < MAX_COUNT[q.ordinal()]; i++) {
                if (rng.nextInt(100_000) + 1 > chance) break;
                out.merge(GemAtelier.gemKey(vs[rng.nextInt(vs.length)], q), 1, Integer::sum);
            }
        }
        return out;
    }
}
