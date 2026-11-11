package pl.fawizja.pixel.item;

import pl.fawizja.pixel.i18n.Messages;
import java.util.Random;

/**
 * Kuznia Wyniesienia (Exaltation Forge) jak w Tibii, u kowala. Liczby z Canary (config.lua.dist, player.cpp,
 * tools.cpp forgeBonus, item.cpp, data/scripts/systems/item_tiers.lua):
 * <ul>
 *   <li><b>Klasa</b> przedmiotu 1..4 i najwyzszy tier: klasa 1 - T1, 2 - T2, 3 - T3, 4 - T10. U nas klasa wynika z
 *       rzadkosci ({@link Rarity}): niepospolity 1, rzadki 2, epicki 3, legendarny 4; przedmiot tylko z bossow
 *       o klase wyzej (najwyzej 4). Klase maja bron, helm, zbroja, spodnie i buty (jak w Tibii).</li>
 *   <li><b>Fuzja</b>: dwa takie same przedmioty o tym samym tierze, 100 pylu, zloto; szansa 50%, z rdzeniem 65%.
 *       Porazka: pierwszy zostaje, drugi traci tier (z T0 przepada); drugi rdzen zmniejsza szanse straty do 50%.
 *       Premie po sukcesie (rzut 0..10000): 16% pyl zostaje, 5% rdzenie zostaja, 0,25% zloto zostaje, 0,25% drugi
 *       zostaje z tierem -1, 4% drugi zostaje bez zmian, 0,25% drugi zostaje z tierem +1, 0,25% +2 tiery.</li>
 *   <li><b>Transfer</b>: dawca z tierem >= 2 na przedmiot tej samej klasy i rodzaju z tierem 0; biorca dostaje
 *       tier - 1, dawca przepada; 100 pylu, rdzenie wg tieru wyniku (tabela Canary) i zloto tieru dawcy.</li>
 *   <li><b>Pyl</b>: limit 100, podniesienie o 1 kosztuje (limit - 75) pylu, najwyzej 225; 60 pylu = 3 odlamki,
 *       50 odlamkow = 1 rdzen.</li>
 *   <li><b>Efekty tierow</b> (szansa w %, a*t^2 + b*t + c): Natarcie (Onslaught, bron, +60% obrazen), Unik (Ruse,
 *       zbroja, unik ciosu), Rozped (Momentum, helm, -2 s odnowien), Transcendencja (spodnie, awatar na 7 s),
 *       Wzmocnienie (Amplification, buty: pozostale szanse x (1 + wzmocnienie / 100)).</li>
 * </ul>
 * <b>Zloto</b> (nasze ceny przedmiotow sa ~1000x nizsze niz w Tibii):
 * {@code max(1000, wartosc przedmiotu x 0,5 x R(klasa, tier))}, gdzie R = cena Canary za ten tier / cena Canary za T1
 * tej klasy (zachowuje tibijski przyrost kosztu, np. klasa 4: T1 x1, T2 x2,5, T5 x12,5, T10 x1875).
 */
public final class Forge {

    public static final int MAX_TIER = 10;
    public static final int[] MAX_TIER_BY_CLASS = {0, 1, 2, 3, 10};

    public static final int FUSION_DUST = 100;
    public static final int TRANSFER_DUST = 100;
    public static final int BASE_SUCCESS = 50;
    public static final int CORE_SUCCESS_BONUS = 15;
    public static final int TIER_LOSS_WITH_CORE = 50;

    public static final int DUST_START_LIMIT = 100;
    public static final int DUST_MAX_LIMIT = 225;
    public static final int SLIVER_DUST = 60;
    public static final int SLIVERS_PER_CONVERSION = 3;
    public static final int CORE_SLIVERS = 50;
    public static final int FIENDISH_MIN_SLIVERS = 3;
    public static final int FIENDISH_MAX_SLIVERS = 7;
    /** Pyl z zabicia: losowo od stopnia do 3 x stopien (forgeAmountMultiplier = 3). */
    public static final int DUST_MULTIPLIER = 3;

    public static final String SLIVER_KEY = "forge_sliver";
    public static final String CORE_KEY = "exalted_core";

    /** Najmniejsze zloto za fuzje i transfer. */
    public static final long MIN_GOLD = 1_000;
    public static final double GOLD_VALUE_FACTOR = 0.5;

    /** Ceny Canary (item_tiers.lua, "regular") za tier 1..: klasa -> tier -> zloto. */
    private static final long[][] CANARY_PRICE = {
            {},
            {0, 25_000L},
            {0, 750_000L, 5_000_000L},
            {0, 4_000_000L, 10_000_000L, 20_000_000L},
            {0, 8_000_000L, 20_000_000L, 40_000_000L, 65_000_000L, 100_000_000L, 250_000_000L, 750_000_000L,
                    2_500_000_000L, 8_000_000_000L, 15_000_000_000L},
    };
    /** Rdzenie za transfer wg tieru wyniku (Canary item_tiers.lua, "core"). */
    private static final int[][] CANARY_CORES = {
            {},
            {0, 1},
            {0, 1, 1},
            {0, 1, 2, 3},
            {0, 1, 2, 5, 10, 15, 25, 35, 50, 60, 85},
    };

    public static final String[] EFFECT_NAMES = {"Natarcie", "Unik", "Rozpęd", "Transcendencja", "Wzmocnienie"};

    private Forge() {}

    /** Czy przedmiot w ogole bierze udzial w Kuzni (bron, helm, zbroja, spodnie, buty; pojedyncze). */
    public static boolean forgeable(ItemType t) {
        if (t == null || t.maxStack() != 1) return false;
        if (t.category() == ItemCategory.WEAPON) return t.slot() == EquipSlot.WEAPON;
        if (t.category() != ItemCategory.ARMOR) return false;
        EquipSlot s = t.slot();
        return s == EquipSlot.HEAD || s == EquipSlot.ARMOR || s == EquipSlot.LEGS || s == EquipSlot.FEET;
    }

    /** Klasa 1..4 (0 = bez klasy: zwykly przedmiot albo nie ten rodzaj). */
    public static int classOf(ItemType t) {
        if (!forgeable(t)) return 0;
        int r = Rarity.tier(t);
        if (r < 2) return 0;
        int c = r - 1;
        if (pl.fawizja.pixel.monster.LootRules.isBossOnly(t)) c++;
        return Math.min(4, c);
    }

    public static int maxTier(ItemType t) { return MAX_TIER_BY_CLASS[classOf(t)]; }

    private static double ratio(int cls, int tier) {
        long[] p = CANARY_PRICE[cls];
        if (tier < 1 || tier >= p.length) return 0;
        return p[tier] / (double) p[1];
    }

    /** Zloto za fuzje do tieru (target 1..max) wg wzoru z opisu klasy. */
    public static long fusionGold(ItemType t, int targetTier) {
        int c = classOf(t);
        if (c == 0 || targetTier < 1 || targetTier > MAX_TIER_BY_CLASS[c]) return 0;
        return Math.max(MIN_GOLD, Math.round(t.value() * GOLD_VALUE_FACTOR * ratio(c, targetTier)));
    }

    /** Zloto za transfer: cena tieru dawcy (Canary: regularPrice tieru dawcy). */
    public static long transferGold(ItemType donor, int donorTier) { return fusionGold(donor, donorTier); }

    /** Rdzenie za transfer na tier wyniku. */
    public static int transferCores(ItemType t, int resultTier) {
        int c = classOf(t);
        if (c == 0 || resultTier < 1 || resultTier >= CANARY_CORES[c].length) return 0;
        return CANARY_CORES[c][resultTier];
    }

    /** Szansa fuzji w procentach. */
    public static int fusionChance(boolean coreForChance) { return BASE_SUCCESS + (coreForChance ? CORE_SUCCESS_BONUS : 0); }

    /** Koszt podniesienia limitu pylu o 1 (0, gdy limit juz najwyzszy). */
    public static int raiseLimitCost(int limit) { return limit >= DUST_MAX_LIMIT ? 0 : Math.max(0, limit - 75); }

    /** Pyl za zabicie potwora pod wpływem / diabolicznego (stopien s): s..3s. */
    public static int rollDust(int stack, Random rng) {
        if (stack <= 0) return 0;
        return stack + rng.nextInt(stack * (DUST_MULTIPLIER - 1) + 1);
    }

    // ------------------------------------------------------------------ premie fuzji

    public static final int BONUS_NONE = 0, BONUS_DUST = 1, BONUS_CORES = 2, BONUS_GOLD = 3, BONUS_SECOND_MINUS = 4,
            BONUS_SECOND_KEPT = 5, BONUS_SECOND_PLUS = 6, BONUS_TWO_TIERS = 7;

    /** Premia z rzutu 0..10000 (Canary forgeBonus). */
    public static int bonus(int roll) {
        if (roll < 7400) return BONUS_NONE;
        if (roll < 9000) return BONUS_DUST;
        if (roll < 9500) return BONUS_CORES;
        if (roll < 9525) return BONUS_GOLD;
        if (roll < 9550) return BONUS_SECOND_MINUS;
        if (roll < 9950) return BONUS_SECOND_KEPT;
        if (roll < 9975) return BONUS_SECOND_PLUS;
        return BONUS_TWO_TIERS;
    }

    public static String bonusText(int bonus) {
        switch (bonus) {
            case BONUS_DUST: return Messages.get("forge.pyl_nie_zostal_zuzyty");
            case BONUS_CORES: return Messages.get("forge.rdzenie_nie_zostaly_zuzyte");
            case BONUS_GOLD: return Messages.get("forge.zloto_nie_zostalo_zuzyte");
            case BONUS_SECOND_MINUS: return Messages.get("forge.drugi_przedmiot_ocalal_z");
            case BONUS_SECOND_KEPT: return Messages.get("forge_service.drugi_przedmiot_ocalal_bez");
            case BONUS_SECOND_PLUS: return Messages.get("forge.drugi_przedmiot_zyskal_tier");
            case BONUS_TWO_TIERS: return Messages.get("forge.przedmiot_zyskal_dwa_tiery");
            default: return "";
        }
    }

    /**
     * Wynik fuzji: tier pierwszego przedmiotu, tier drugiego (-1 = przepadl), co zostalo zuzyte i premia.
     */
    public record Fusion(boolean success, int bonus, int firstTier, int secondTier, boolean payDust, boolean payCores,
                         boolean payGold) {}

    /** Rzut fuzji przedmiotow o tierze tier (maxTier = limit klasy) - jak Game::playerForgeFuseItems + Player::forgeFuseItems. */
    public static Fusion fuse(int tier, int maxTier, boolean coreChance, boolean coreProtect, Random rng) {
        boolean success = rng.nextInt(100) + 1 <= fusionChance(coreChance);
        int bonusRoll = rng.nextInt(10_001);
        if (!success) {
            boolean lost = rng.nextInt(100) + 1 <= (coreProtect ? TIER_LOSS_WITH_CORE : 100);
            int second = !lost ? tier : tier >= 1 ? tier - 1 : -1;
            return new Fusion(false, BONUS_NONE, tier, second, true, true, true);
        }
        int b = bonus(bonusRoll);
        int first = tier + 1;
        if (b == BONUS_TWO_TIERS && tier + 2 <= maxTier) first = tier + 2;
        else if (b == BONUS_TWO_TIERS) b = BONUS_NONE;
        int second;
        switch (b) {
            case BONUS_SECOND_MINUS: second = Math.max(0, tier - 1); break;
            case BONUS_SECOND_KEPT: second = tier; break;
            case BONUS_SECOND_PLUS: second = Math.min(maxTier, tier + 1); break;
            default: second = -1;
        }
        return new Fusion(true, b, first, second, b != BONUS_DUST, b != BONUS_CORES, b != BONUS_GOLD);
    }

    // ------------------------------------------------------------------ efekty tierow (Canary item.cpp + config)

    private static double poly(double a, double b, double c, int t) { return t <= 0 ? 0 : a * t * t + b * t + c; }

    /** Natarcie (bron): szansa na +60% obrazen. */
    public static double onslaught(int t) { return poly(0.05, 0.4, 0.05, t); }
    /** Unik (zbroja): szansa na unikniecie ciosu. */
    public static double ruse(int t) { return poly(0.0307576, 0.440697, 0.026, t); }
    /** Rozped (helm): szansa co 2 s walki na -2 s odnowien czarow. */
    public static double momentum(int t) { return poly(0.05, 1.9, 0.05, t); }
    /** Transcendencja (spodnie): szansa co 2 s na awatara (7 s). */
    public static double transcendence(int t) { return poly(0.0127, 0.1070, 0.0073, t); }
    /** Wzmocnienie (buty): procent, o ktory rosna pozostale szanse. */
    public static double amplification(int t) { return poly(0.4, 1.7, 0.4, t); }

    /** Premia natarcia: +60% obrazen (combat.cpp). */
    public static final double ONSLAUGHT_BONUS = 0.6;
    /** Rozped skraca odnowienia o 2 s. */
    public static final long MOMENTUM_CUT_MS = 2_000;
    /** Efekty Rozpedu i Transcendencji losuja sie co 2 s. */
    public static final long EFFECT_CHECK_MS = 2_000;
    /** Awatar z Transcendencji: 7 s (transcendenceAvatarDuration). */
    public static final long AVATAR_MS = 7_000;
    /** Awatar (Canary checkAvatarSkill stopien 3): -15% obrazen, 100% krytyka, +15% obrazen krytycznych. */
    public static final int AVATAR_REDUCTION = 15;
    public static final double AVATAR_CRIT_DAMAGE = 15;

    /** Nazwa z ulepszeniem i tierem: "Miecz +2 (T3)". */
    public static String name(ItemType t, ItemStack s) {
        String n = Upgrade.name(t, s.upgrade());
        return s.tier() > 0 ? n + " (T" + s.tier() + ")" : n;
    }

    /** Indeks efektu tieru dla rodzaju przedmiotu (0..4) albo -1. */
    public static int effectIndex(ItemType t) {
        if (!forgeable(t)) return -1;
        switch (t.slot()) {
            case WEAPON: return 0;
            case ARMOR: return 1;
            case HEAD: return 2;
            case LEGS: return 3;
            case FEET: return 4;
            default: return -1;
        }
    }

    /** Szansa efektu tieru (bez wzmocnienia butow). */
    public static double effectChance(ItemType t, int tier) {
        switch (effectIndex(t)) {
            case 0: return onslaught(tier);
            case 1: return ruse(tier);
            case 2: return momentum(tier);
            case 3: return transcendence(tier);
            case 4: return amplification(tier);
            default: return 0;
        }
    }

    /** Opis do okna przedmiotu: "klasa 4, tier 3/10; Natarcie 1,7% (+60% obrażeń)". */
    public static String describe(ItemType t, int tier) {
        int c = classOf(t);
        if (c == 0) return "";
        StringBuilder b = new StringBuilder("klasa ").append(c).append(Messages.get("forge.tier")).append(tier).append('/').append(MAX_TIER_BY_CLASS[c]);
        int e = effectIndex(t);
        if (tier > 0 && e >= 0) {
            b.append("; ").append(Messages.tr(EFFECT_NAMES[e])).append(' ').append(ItemBonus.number(Math.round(effectChance(t, tier) * 100) / 100.0)).append('%');
            switch (e) {
                case 0: b.append(Messages.get("forge.60_obrazen")); break;
                case 1: b.append(Messages.get("forge.unik_ciosu")); break;
                case 2: b.append(Messages.get("forge.2_s_odnowien")); break;
                case 3: b.append(Messages.get("forge.awatar_7_s")); break;
                case 4: b.append(Messages.get("forge.szans_tierow")); break;
                default:
            }
        }
        return b.toString();
    }
}
