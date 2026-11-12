package pl.fawizja.pixel.character;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.combat.CombatSystem;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.GroundItems;
import pl.fawizja.pixel.world.Position;

/** Smierc jak w Tibii: strata doswiadczenia, umiejetnosci i magii oraz przedmiotow do ciala, zalezna od blogoslawienstw. */
class DeathLossTest {

    private static final String[] WORN = {"leather_helmet", "leather_armor", "leather_legs", "leather_boots", "hunting_spear", "wooden_shield"};
    private static final EquipSlot[] SLOTS = {EquipSlot.HEAD, EquipSlot.ARMOR, EquipSlot.LEGS, EquipSlot.FEET, EquipSlot.WEAPON, EquipSlot.SHIELD};

    private static int id(String key) { return TestData.ITEMS.byKey(key).id(); }

    /** Poziom 100 z pelnym ekwipunkiem (6 przedmiotow), plecakiem z 4 stosami i n zwyklymi blogoslawienstwami. */
    private static PlayerCharacter hero(int blessings) {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.addExperience(Balance.experienceForLevel(100) - pc.experience());
        pc.backpack().clear();
        for (EquipSlot s : EquipSlot.values()) pc.equipment().setDirect(s, null);
        for (int i = 0; i < WORN.length; i++) pc.equipment().setDirect(SLOTS[i], ItemStack.of(id(WORN[i])));
        assertTrue(pc.backpack().add(new ItemStack(id("gold_coin"), 50)).ok());
        assertTrue(pc.backpack().add(new ItemStack(id("rope"), 1)).ok());
        assertTrue(pc.backpack().add(new ItemStack(id("health_potion"), 7)).ok());
        assertTrue(pc.backpack().add(new ItemStack(id("wolf_pelt"), 3)).ok());
        int n = 0;
        for (Blessing b : Blessing.values()) {
            if (b.kind() == Blessing.Kind.REGULAR && n++ < blessings) pc.addBlessing(b);
        }
        assertEquals(blessings, pc.blessingCount());
        return pc;
    }

    private static int worn(PlayerCharacter pc) {
        int n = 0;
        for (EquipSlot s : EquipSlot.values()) if (pc.equipment().get(s) != null) n++;
        return n;
    }

    @Test
    void withoutBlessingsTheBackpackAlwaysDropsIntoTheBody() {
        for (int seed = 0; seed < 50; seed++) {
            PlayerCharacter pc = hero(0);
            DeathLoss loss = pc.applyDeath(8, 0, false, false, new Random(seed));
            assertTrue(loss.backpackDropped(), "100% without blessings");
            assertEquals(50 + 1 + 7 + 3, loss.dropped().stream().filter(s -> pc.backpack().registry().get(s.itemId()).slot() == EquipSlot.NONE)
                    .mapToInt(ItemStack::count).sum(), "every backpack stack");
            assertTrue(pc.backpack().contents().isEmpty(), "the backpack is empty afterwards");
        }
    }

    @Test
    void withoutBlessingsEachWornItemDropsAboutTenPercent() {
        int trials = 3000, dropped = 0;
        Random rng = new Random(77);
        for (int i = 0; i < trials; i++) {
            PlayerCharacter pc = hero(0);
            int before = worn(pc);
            pc.applyDeath(8, 0, false, false, rng);
            dropped += before - worn(pc);
        }
        double rate = dropped / (double) (trials * WORN.length);
        assertEquals(0.10, rate, 0.015, "about 10% of the items per death: " + rate);
    }

    @Test
    void blessingsLowerTheItemLossChancesLikeTibia() {
        double[] backpack = {1.0, 0.70, 0.45, 0.25, 0.10, 0.0};
        double[] item = {0.10, 0.07, 0.045, 0.025, 0.01, 0.0};
        int trials = 4000;
        for (int b = 0; b <= 5; b++) {
            assertEquals(backpack[b], DeathLoss.backpackChance(b), 1e-12);
            assertEquals(item[b], DeathLoss.itemChance(b), 1e-12);
            Random rng = new Random(1000 + b);
            int backpackDrops = 0, itemDrops = 0;
            for (int i = 0; i < trials; i++) {
                PlayerCharacter pc = hero(b);
                int before = worn(pc);
                DeathLoss loss = pc.applyDeath(8, 0, false, false, rng);
                if (loss.backpackDropped()) backpackDrops++;
                itemDrops += before - worn(pc);
            }
            assertEquals(backpack[b], backpackDrops / (double) trials, 0.03, b + " blessings: backpack");
            assertEquals(item[b], itemDrops / (double) (trials * WORN.length), 0.01, b + " blessings: worn item");
        }
    }

    @Test
    void withAllFiveBlessingsNothingDropsFromTheEquipment() {
        Random rng = new Random(5);
        for (int i = 0; i < 1500; i++) {
            PlayerCharacter pc = hero(5);
            DeathLoss loss = pc.applyDeath(8, 0, false, false, rng);
            assertTrue(loss.dropped().isEmpty(), "run " + i);
            assertEquals(WORN.length, worn(pc));
            assertEquals(4, pc.backpack().contents().size());
            assertEquals(5, loss.blessings());
        }
    }

    @Test
    void redAndBlackSkullLoseEverythingEvenWithFiveBlessings() {
        PlayerCharacter pc = hero(5);
        pc.addBlessing(Blessing.TWIST);
        pc.equipment().setDirect(EquipSlot.AMULET, ItemStack.of(id(DeathLoss.AMULET_OF_LOSS)));
        DeathLoss loss = pc.applyDeath(8, 0, true, true, new Random(1));
        assertEquals(0, worn(pc), "all equipment, the amulet too");
        assertTrue(pc.backpack().contents().isEmpty());
        assertTrue(loss.backpackDropped());
        assertEquals(WORN.length + 1 + 4, loss.dropped().size());
        assertFalse(loss.amuletUsed(), "it is not used up, it is lost with the rest");
        assertEquals(0, pc.blessingCount(), "the regular blessings are used up");
        assertTrue(pc.boughtBlessing(Blessing.TWIST), "twist does not help against a skull");
    }

    @Test
    void twistOfFateInPvpKeepsTheRegularBlessingsAndSoTheItems() {
        PlayerCharacter pc = hero(5);
        pc.addBlessing(Blessing.TWIST);
        long exp = pc.experience();
        DeathLoss loss = pc.applyDeath(8, 0, true, false, new Random(3));
        assertTrue(loss.twistUsed());
        assertTrue(loss.dropped().isEmpty(), "five blessings: nothing drops");
        assertEquals(5, pc.blessingCount(), "regular blessings kept");
        assertFalse(pc.boughtBlessing(Blessing.TWIST), "twist is used up");
        assertEquals(exp - (long) Math.ceil(exp * Balance.deathLossFraction(100, exp, 5, false)), pc.experience());
        // drugi zgon w PvP bez Zrzadzenia losu zabiera zwykle blogoslawienstwa
        DeathLoss again = pc.applyDeath(8, 0, true, false, new Random(3));
        assertFalse(again.twistUsed());
        assertEquals(0, pc.blessingCount());
        // smierc od potwora zostawia Zrzadzenie losu (jest tylko na PvP)
        PlayerCharacter pc2 = hero(5);
        pc2.addBlessing(Blessing.TWIST);
        pc2.applyDeath(8, 0, false, false, new Random(3));
        assertEquals(0, pc2.blessingCount());
        assertTrue(pc2.boughtBlessing(Blessing.TWIST), "kept for the PvP");
    }

    @Test
    void theAmuletOfLossProtectsEverythingAndBreaksOnPveDeath() {
        for (int seed = 0; seed < 40; seed++) {
            PlayerCharacter pc = hero(0);
            pc.equipment().setDirect(EquipSlot.AMULET, ItemStack.of(id(DeathLoss.AMULET_OF_LOSS)));
            DeathLoss loss = pc.applyDeath(8, 0, false, false, new Random(seed));
            assertTrue(loss.dropped().isEmpty(), "nothing drops with the amulet");
            assertTrue(loss.amuletUsed());
            assertNull(pc.equipment().get(EquipSlot.AMULET), "the amulet is destroyed");
            assertEquals(WORN.length, worn(pc));
            assertEquals(4, pc.backpack().contents().size(), "also the backpack stays");
        }
        // Zrzadzenie losu w PvP nie zuzywa blogoslawienstw, wiec amulet zostaje
        PlayerCharacter pc = hero(0);
        pc.addBlessing(Blessing.TWIST);
        pc.equipment().setDirect(EquipSlot.AMULET, ItemStack.of(id(DeathLoss.AMULET_OF_LOSS)));
        DeathLoss loss = pc.applyDeath(8, 0, true, false, new Random(1));
        assertTrue(loss.dropped().isEmpty());
        assertNotNull(pc.equipment().get(EquipSlot.AMULET), "it stays when only twist is used");
        assertFalse(loss.amuletUsed());
    }

    @Test
    void adventurersBlessingKeepsTheItemsAndEverythingBelowLevelThirtyOne() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.addExperience(Balance.experienceForLevel(25) - pc.experience());
        pc.backpack().clear();
        assertTrue(pc.backpack().add(new ItemStack(id("gold_coin"), 10)).ok());
        pc.equipment().setDirect(EquipSlot.HEAD, ItemStack.of(id("leather_helmet")));
        for (int i = 0; i < 300; i++) {
            DeathLoss loss = pc.applyDeath(8, 0, false, false, new Random(i));
            assertTrue(loss.dropped().isEmpty());
        }
        assertNotNull(pc.equipment().get(EquipSlot.HEAD));
        assertEquals(5, pc.blessingCount());
    }

    @Test
    void experienceAndSkillsAreLostInProportionToTheBlessings() {
        long[] expLost = new long[6];
        long[] skillTriesLost = new long[6];
        long[] magicLost = new long[6];
        for (int b = 0; b <= 5; b++) {
            PlayerCharacter pc = hero(b);
            pc.skills().set(Skill.SWORD, 70, 500);
            pc.skills().set(Skill.MAGIC, 20, 300);
            long totalSword = total(pc.skills(), Skill.SWORD);
            long totalMagic = total(pc.skills(), Skill.MAGIC);
            long exp = pc.experience();
            DeathLoss loss = pc.applyDeath(8, 0, false, false, new Random(1));
            expLost[b] = exp - pc.experience();
            skillTriesLost[b] = totalSword - total(pc.skills(), Skill.SWORD);
            magicLost[b] = totalMagic - total(pc.skills(), Skill.MAGIC);
            double f = Balance.deathLossFraction(100, exp, b, false);
            assertEquals(f, loss.lossFraction(), 1e-12);
            assertEquals(Math.ceil(exp * f), expLost[b], 1.0, b + " blessings: exp");
            assertEquals(totalSword * f, skillTriesLost[b], totalSword * 1e-4 + 2, b + " blessings: sword");
            assertEquals(totalMagic * f, magicLost[b], totalMagic * 1e-4 + 2, b + " blessings: magic level");
            assertTrue(pc.skills().level(Skill.SWORD) < 70 || b == 5 || skillTriesLost[b] < 500, "levels fall when the loss is large");
        }
        for (int b = 1; b <= 5; b++) {
            assertEquals(expLost[0] * (100 - 8 * b) / 100.0, expLost[b], expLost[0] * 0.002 + 2, b + " blessings: exp");
            assertEquals(skillTriesLost[0] * (100 - 8 * b) / 100.0, skillTriesLost[b], skillTriesLost[0] * 0.002 + 2, b + " blessings: skill");
            assertEquals(magicLost[0] * (100 - 8 * b) / 100.0, magicLost[b], magicLost[0] * 0.002 + 2, b + " blessings: magic");
        }
        assertTrue(expLost[5] > 0 && expLost[5] < expLost[0] * 0.61);
    }

    private static long total(Skills s, Skill k) {
        long t = s.tries(k);
        for (int l = Skills.offset(k); l < s.level(k); l++) t += Skills.triesForNext(s.vocation(), k, l);
        return t;
    }

    @Test
    void skillsNeverFallBelowTheirStartingLevelAndPromotionHelps() {
        PlayerCharacter pc = hero(0);
        pc.skills().set(Skill.AXE, 11, 5);
        pc.skills().set(Skill.MAGIC, 0, 3);
        for (int i = 0; i < 30; i++) pc.skills().loseAll(0.9);
        assertEquals(Skills.START_SKILL, pc.skills().level(Skill.AXE));
        assertEquals(0, pc.skills().level(Skill.MAGIC));
        PlayerCharacter a = hero(0), b = hero(0);
        b.setPromoted(true);
        long expA = a.experience(), expB = b.experience();
        a.applyDeath(8, 0, false, false, new Random(1));
        b.applyDeath(8, 0, false, false, new Random(1));
        assertEquals((expA - a.experience()) * 0.7, expB - b.experience(), (expA - a.experience()) * 0.005 + 2, "promotion: -30%");
    }

    @Test
    void theLevelFloorStillHolds() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.addExperience(Balance.experienceForLevel(40) - pc.experience());
        long floor = Balance.experienceForLevel(40);
        pc.applyDeath(40, 0, false, false, new Random(1));
        assertEquals(floor, pc.experience(), "never below the starting threshold of the minimum level");
    }

    @Test
    void endToEndDeathLeavesABodyWithTheItemsOnTheGround() {
        for (int blessed = 0; blessed < 2; blessed++) {
            PlayerCharacter pc = hero(blessed * 5);
            Position at = new Position(12, 27, -5);
            pc.setPosition(at);
            GroundItems ground = new GroundItems();
            CombatSystem cs = new CombatSystem(pc, TestData.varden(), TestData.ITEMS,
                    new MonsterManager(TestData.varden(), MonsterRegistry.loadDefault(TestData.ITEMS), Collections.emptyList(), new Random(1)),
                    ground, new EventLog(50), new Random(1), 8);
            cs.diedFromPlayer("Kuba", false, 1000);
            GroundItems.Corpse body = ground.corpseAt(at);
            assertNotNull(body, "a body is always left");
            assertEquals(GroundItems.PLAYER_CORPSE, body.monsterKey());
            if (blessed == 0) {
                assertTrue(body.hasLoot());
                assertTrue(body.loot().stream().anyMatch(s -> s.itemId() == id("gold_coin") && s.count() == 50), "the backpack is in the body");
                assertTrue(cs.lastDeath().itemsLost() >= 4 && cs.lastDeath().backpackLost());
                assertTrue(pc.backpack().contents().isEmpty());
                // wlasciciel wraca po rzeczy: przeszukanie ciala oddaje stosy do plecaka
                List<ItemStack> back = ground.lootCorpse(at, pc.backpack());
                assertFalse(back.isEmpty());
                assertFalse(pc.backpack().contents().isEmpty());
            } else {
                assertFalse(body.hasLoot(), "five blessings: an empty body");
                assertEquals(0, cs.lastDeath().itemsLost());
                assertEquals(4, pc.backpack().contents().size());
            }
        }
    }

    @Test
    void theDeathSummaryMentionsLostItemsAndSkills() {
        PlayerCharacter pc = hero(0);
        pc.skills().set(Skill.SWORD, 70, 500);
        CombatSystem cs = new CombatSystem(pc, TestData.varden(), TestData.ITEMS,
                new MonsterManager(TestData.varden(), MonsterRegistry.loadDefault(TestData.ITEMS), Collections.emptyList(), new Random(1)),
                new GroundItems(), new EventLog(50), new Random(1), 8);
        cs.diedFromPlayer("Kuba", false, 1000);
        StringBuilder all = new StringBuilder();
        for (String[] r : pl.fawizja.pixel.combat.DeathRecap.rows(cs.lastDeath())) all.append(r[0]).append('=').append(r[1]).append('\n');
        assertTrue(all.toString().contains("Przedmioty w ciele"), all.toString());
        assertTrue(all.toString().contains("Umiejętności") || cs.lastDeath().skillLevelsLost() == 0, all.toString());
    }
}
