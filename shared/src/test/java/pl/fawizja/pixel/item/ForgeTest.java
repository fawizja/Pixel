package pl.fawizja.pixel.item;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.combat.DamageType;

class ForgeTest {
    static final ItemRegistry ITEMS = TestData.ITEMS;

    static ItemType firstOfClass(int c, EquipSlot slot) {
        for (ItemType t : ITEMS.all()) if (Forge.classOf(t) == c && (slot == null || t.slot() == slot)) return t;
        throw new AssertionError("no item of class " + c + " in " + slot);
    }

    @Test
    void classComesFromRarityAndKind() {
        assertEquals(0, Forge.classOf(ITEMS.byKey("leather_armor")), "common items have no class");
        assertEquals(0, Forge.classOf(ITEMS.byKey("gold_coin")));
        for (ItemType t : ITEMS.all()) {
            int c = Forge.classOf(t);
            if (c == 0) continue;
            assertTrue(Forge.forgeable(t), t.key());
            assertEquals(1, t.maxStack(), t.key());
            int expected = Math.min(4, Rarity.tier(t) - 1 + (pl.fawizja.pixel.monster.LootRules.isBossOnly(t) ? 1 : 0));
            assertEquals(expected, c, t.key());
            assertTrue(t.slot() == EquipSlot.WEAPON || t.slot() == EquipSlot.HEAD || t.slot() == EquipSlot.ARMOR
                    || t.slot() == EquipSlot.LEGS || t.slot() == EquipSlot.FEET, t.key());
        }
        for (int c = 1; c <= 4; c++) firstOfClass(c, null);
        assertArrayEquals(new int[] {0, 1, 2, 3, 10}, Forge.MAX_TIER_BY_CLASS, "Tibia: class 4 up to tier 10");
        assertEquals(10, Forge.maxTier(firstOfClass(4, null)));
        assertEquals(1, Forge.maxTier(firstOfClass(1, null)));
        // nowe buty klasy 4 (bez nich Wzmocnienie nie mialoby wysokich tierow)
        assertEquals(4, Forge.classOf(ITEMS.byKey("abyssal_treads")));
    }

    @Test
    void goldFollowsTheDocumentedFormula() {
        for (int c = 1; c <= 4; c++) {
            ItemType t = firstOfClass(c, null);
            long t1 = Forge.fusionGold(t, 1);
            assertEquals(Math.max(Forge.MIN_GOLD, Math.round(t.value() * Forge.GOLD_VALUE_FACTOR)), t1, t.key());
            long prev = 0;
            for (int tier = 1; tier <= Forge.maxTier(t); tier++) {
                long g = Forge.fusionGold(t, tier);
                assertTrue(g >= prev, "higher tiers cost more: " + t.key() + " T" + tier);
                prev = g;
            }
            assertEquals(0, Forge.fusionGold(t, Forge.maxTier(t) + 1), "no tier above the class cap");
            assertEquals(Forge.fusionGold(t, 2), Forge.transferGold(t, 2), "transfer costs the donor tier price");
        }
        assertEquals(0, Forge.fusionGold(ITEMS.byKey("leather_armor"), 1));
    }

    @Test
    void fusionChancesBonusesAndCores() {
        assertEquals(50, Forge.fusionChance(false));
        assertEquals(65, Forge.fusionChance(true));
        assertEquals(Forge.BONUS_NONE, Forge.bonus(0));
        assertEquals(Forge.BONUS_NONE, Forge.bonus(7399));
        assertEquals(Forge.BONUS_DUST, Forge.bonus(7400));
        assertEquals(Forge.BONUS_CORES, Forge.bonus(9000));
        assertEquals(Forge.BONUS_GOLD, Forge.bonus(9500));
        assertEquals(Forge.BONUS_SECOND_MINUS, Forge.bonus(9525));
        assertEquals(Forge.BONUS_SECOND_KEPT, Forge.bonus(9550));
        assertEquals(Forge.BONUS_SECOND_PLUS, Forge.bonus(9950));
        assertEquals(Forge.BONUS_TWO_TIERS, Forge.bonus(9975));
        assertEquals(Forge.BONUS_TWO_TIERS, Forge.bonus(10_000));

        Random rng = new Random(7);
        int n = 40_000, ok = 0, okCore = 0, keptProtected = 0, failsProtected = 0, twoTiers = 0;
        for (int i = 0; i < n; i++) {
            Forge.Fusion f = Forge.fuse(2, 10, false, false, rng);
            if (f.success()) {
                ok++;
                assertTrue(f.firstTier() == 3 || f.firstTier() == 4);
                if (f.firstTier() == 4) twoTiers++;
            } else {
                assertEquals(2, f.firstTier(), "the first item keeps its tier");
                assertEquals(1, f.secondTier(), "without a core the second item loses a tier");
            }
            if (Forge.fuse(2, 10, true, false, rng).success()) okCore++;
            Forge.Fusion p = Forge.fuse(2, 10, false, true, rng);
            if (!p.success()) {
                failsProtected++;
                if (p.secondTier() == 2) keptProtected++;
            }
        }
        assertEquals(0.50, ok / (double) n, 0.015);
        assertEquals(0.65, okCore / (double) n, 0.015);
        assertEquals(0.50, keptProtected / (double) failsProtected, 0.03, "a core halves the tier loss");
        assertEquals(0.0025, twoTiers / (double) ok, 0.002);
        // tier 0 bez ochrony: drugi przepada; na szczycie klasy dwa tiery przepadaja na zwykly sukces
        Forge.Fusion f0;
        do f0 = Forge.fuse(0, 1, false, false, rng); while (f0.success());
        assertEquals(-1, f0.secondTier());
        for (int i = 0; i < 2000; i++) {
            Forge.Fusion f = Forge.fuse(0, 1, false, false, rng);
            if (f.success()) assertEquals(1, f.firstTier(), "never above the cap");
        }
    }

    @Test
    void dustLimitAndConversions() {
        assertEquals(25, Forge.raiseLimitCost(100));
        assertEquals(149, Forge.raiseLimitCost(224));
        assertEquals(0, Forge.raiseLimitCost(225));
        int total = 0;
        for (int l = 100; l < 225; l++) total += Forge.raiseLimitCost(l);
        assertEquals(10_875, total, "25 + 26 + ... + 149 dust from 100 to 225");
        Random rng = new Random(3);
        for (int s : new int[] {1, 3, 5, 15}) {
            int min = Integer.MAX_VALUE, max = 0;
            for (int i = 0; i < 2000; i++) {
                int d = Forge.rollDust(s, rng);
                min = Math.min(min, d);
                max = Math.max(max, d);
            }
            assertEquals(s, min);
            assertEquals(3 * s, max);
        }
        assertEquals(0, Forge.rollDust(0, rng));
        assertEquals(60, Forge.SLIVER_DUST);
        assertEquals(3, Forge.SLIVERS_PER_CONVERSION);
        assertEquals(50, Forge.CORE_SLIVERS);
        assertEquals(225, Forge.DUST_MAX_LIMIT);
    }

    @Test
    void tierEffectsUseCanaryFormulas() {
        assertEquals(0, Forge.onslaught(0));
        assertEquals(0.5, Forge.onslaught(1), 1e-9);
        assertEquals(9.05, Forge.onslaught(10), 1e-9);
        assertEquals(2.0, Forge.momentum(1), 1e-9);
        assertEquals(24.05, Forge.momentum(10), 1e-9);
        assertEquals(0.4974546, Forge.ruse(1), 1e-6);
        assertEquals(2.3473, Forge.transcendence(10), 1e-9);
        assertEquals(2.5, Forge.amplification(1), 1e-9);
        assertEquals(57.4, Forge.amplification(10), 1e-9);
        assertEquals("Natarcie", Forge.EFFECT_NAMES[Forge.effectIndex(firstOfClass(4, EquipSlot.WEAPON))]);
        assertEquals("Unik", Forge.EFFECT_NAMES[Forge.effectIndex(firstOfClass(3, EquipSlot.ARMOR))]);
        assertEquals("Wzmocnienie", Forge.EFFECT_NAMES[Forge.effectIndex(ITEMS.byKey("abyssal_treads"))]);
        ItemType w = firstOfClass(4, EquipSlot.WEAPON);
        assertTrue(Forge.name(w, new ItemStack(w.id(), 1, 2, 3, Imbuements.NONE)).endsWith("+2 (T3)"));
        assertEquals(w.name(), Forge.name(w, new ItemStack(w.id(), 1)));
    }

    @Test
    void stacksKeepTierAndImbuementsInText() {
        ItemType w = firstOfClass(4, EquipSlot.WEAPON);
        ItemStack plain = new ItemStack(w.id(), 1);
        assertTrue(plain.isPlain());
        assertEquals(plain, ItemStack.parse(plain.encode()));
        Imbuements im = Imbuements.NONE.with(Imbuement.VAMPIRISM, 3).with(Imbuement.STRIKE, 2);
        ItemStack rich = new ItemStack(w.id(), 1, 4, 7, im);
        assertFalse(rich.isPlain());
        assertFalse(plain.withTier(1).isPlain());
        ItemStack back = ItemStack.parse(rich.encode());
        assertEquals(7, back.tier());
        assertEquals(4, back.upgrade());
        assertEquals(2, back.imbuements().size());
        assertEquals(Imbuement.DURATION_MS, back.imbuements().get(Imbuement.VAMPIRISM).msLeft());
        assertEquals(3, back.imbuements().get(Imbuement.VAMPIRISM).level());
        assertThrows(IllegalArgumentException.class, () -> new ItemStack(w.id(), 2, 0, 1, Imbuements.NONE), "tiered items are single");
        assertEquals(new ItemStack(ITEMS.byKey("gold_coin").id(), 57), ItemStack.parse("1x57"));
    }

    @Test
    void imbuementSlotsKindsAndIngredients() {
        for (Imbuement k : Imbuement.values()) {
            for (int level = 1; level <= 3; level++) {
                assertEquals(level, k.ingredients(level).size());
                for (Imbuement.Ingredient in : k.ingredients(level)) {
                    assertNotNull(ITEMS.byKeyOrNull(in.itemKey()), k + " needs unknown " + in.itemKey());
                    assertTrue(in.count() > 0);
                }
            }
            assertTrue(k.value(1) < k.value(2) && k.value(2) < k.value(3), k.name());
        }
        // wartosci Tibii
        assertEquals(25, Imbuement.VAMPIRISM.value(3));
        assertEquals(8, Imbuement.VOID.value(3));
        assertEquals(50, Imbuement.STRIKE.value(3));
        assertEquals(15, Imbuement.DRAGON_HIDE.value(3));
        assertEquals(10, Imbuement.LICH_SHROUD.value(3));
        assertEquals(20 * 3_600_000L, Imbuement.DURATION_MS);

        ItemType w = firstOfClass(4, EquipSlot.WEAPON);
        ItemType a = firstOfClass(2, EquipSlot.ARMOR);
        ItemType legs = firstOfClass(2, EquipSlot.LEGS);
        assertEquals(3, Imbuement.slots(w));
        assertEquals(1, Imbuement.slots(a));
        assertEquals(0, Imbuement.slots(legs));
        assertEquals(0, Imbuement.slots(ITEMS.byKey("leather_armor")));
        assertTrue(Imbuement.VAMPIRISM.fits(w) && Imbuement.VAMPIRISM.fits(a));
        assertFalse(Imbuement.STRIKE.fits(a));
        assertTrue(Imbuement.DRAGON_HIDE.fits(a));
        assertFalse(Imbuement.DRAGON_HIDE.fits(w));

        Imbuements im = Imbuements.NONE.with(Imbuement.VAMPIRISM, 2).with(Imbuement.DRAGON_HIDE, 3);
        assertTrue(im.conflicts(Imbuement.VAMPIRISM));
        assertFalse(im.conflicts(Imbuement.SNAKE_SKIN), "different elements stack");
        assertTrue(Imbuements.NONE.with(Imbuement.SCORCH, 1).conflicts(Imbuement.FROST), "one element conversion per item");
        assertEquals(10, im.sum(Imbuement.Category.LIFE_LEECH));
        assertEquals(15, im.protection(DamageType.FIRE));
        assertEquals(0, im.protection(DamageType.ICE));
        Imbuements later = im.tick(Imbuement.DURATION_MS - 1);
        assertEquals(2, later.size());
        assertTrue(later.tick(1).isEmpty(), "expired after 20 h of fighting");
        Imbuements back = Imbuements.parse(im.encode());
        assertEquals(im.encode(), back.encode());
        assertTrue(Imbuements.parse("NOPE3@20~VOID2@5").get(Imbuement.VOID) != null, "unknown kinds are skipped");
    }
}
