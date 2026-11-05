package pl.fawizja.pixel.combat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.Enchantments;
import pl.fawizja.pixel.item.ItemBonus;
import pl.fawizja.pixel.item.ItemCompare;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.GroundItems;
import pl.fawizja.pixel.world.Position;

/** Mechanika walki jak w Tibii: odpornosci klas, ochrony gracza, krytyki, kradziez, nowe rodzaje obrazen. */
class TibiaCombatTest {
    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);

    /** Generator, ktory zawsze zwraca 0 (krytyk zawsze wypada, obrazenia minimalne) albo zawsze bound-1. */
    static final class FixedRandom extends Random {
        final boolean high;
        FixedRandom(boolean high) { this.high = high; }
        @Override public int nextInt(int bound) { return high ? bound - 1 : 0; }
    }

    @Test
    void everyMonsterHasATibiaClassAndClassPatternsHold() {
        List<String> bad = new ArrayList<>();
        for (MonsterType t : REG.all()) {
            if (t.race().isEmpty()) bad.add(t.key() + ": no class");
            if (t.race().equals("Undead")) {
                // nieumarle (Tibia): odporne na smierc, wyssanie zycia i utoniecie, slabe na swiete
                for (DamageType d : new DamageType[] {DamageType.DEATH, DamageType.LIFE_DRAIN, DamageType.DROWN}) {
                    if (t.resistance(d) != 100) bad.add(t.key() + ": undead not immune to " + d);
                }
                if (t.resistance(DamageType.HOLY) >= 0) bad.add(t.key() + ": undead not weak to holy");
            }
            if (t.race().equals("Dragon") && t.resistance(DamageType.ICE) < 100 && t.resistance(DamageType.FIRE) < 80) {
                bad.add(t.key() + ": fire dragon without fire resistance");
            }
            if (t.race().equals("Elemental")) {
                DamageType own = t.damageType();
                if (t.resistance(own) != 100) bad.add(t.key() + ": elemental not immune to its element " + own);
            }
            for (int r : t.resistances().values()) {
                // slabosci lagodne jak w Tibii (zwykle -5..-25, najwyzej -30)
                if (r < -30) bad.add(t.key() + ": weakness " + r);
            }
        }
        assertTrue(bad.isEmpty(), String.join("\n", bad));
    }

    @Test
    void iceCreaturesAreNotAutomaticallyWeakToFire() {
        // Tibia: Ice Golem, Ice Witch i Frost Dragon sa odporne na ogien, Winter Wolf +10
        assertEquals(100, REG.get("ice_golem").resistance(DamageType.FIRE));
        assertEquals(50, REG.get("frost_witch").resistance(DamageType.FIRE));
        assertEquals(10, REG.get("snow_wolf").resistance(DamageType.FIRE));
        assertEquals(100, REG.get("abyssal_demon").resistance(DamageType.FIRE));
        assertEquals(-12, REG.get("abyssal_demon").resistance(DamageType.ICE));
    }

    @Test
    void newDamageTypesAreUsedByMonsters() {
        assertEquals(DamageType.LIFE_DRAIN, REG.get("skeleton").extraAttack().type());
        assertEquals(DamageType.MANA_DRAIN, REG.get("void_priest").extraAttack().type());
        assertEquals(DamageType.DROWN, REG.get("drowned_sailor").damageType());
        MonsterType elite = REG.get("skeleton").eliteVariant();
        assertEquals(Math.round(REG.get("skeleton").extraAttack().max() * 1.3), elite.extraAttack().max());
        assertEquals("Undead", elite.race());
    }

    @Test
    void protectionsSumAndReduceElementalDamage() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        // zestaw jak Magma Coat / Magma Legs: ogien +8 +6, lod -8 -6
        pc.equipment().setDirect(EquipSlot.ARMOR, new ItemStack(TestData.ITEMS.byKey("obsidian_armor").id(), 1));
        pc.equipment().setDirect(EquipSlot.LEGS, new ItemStack(TestData.ITEMS.byKey("obsidian_legs").id(), 1));
        assertEquals(14, pc.equipment().protection(DamageType.FIRE));
        assertEquals(-14, pc.equipment().protection(DamageType.ICE));
        Random rng = new Random(1);
        assertEquals(86, CombatFormulas.mitigateOnPlayer(pc, 100, DamageType.FIRE, true, rng));
        assertEquals(114, CombatFormulas.mitigateOnPlayer(pc, 100, DamageType.ICE, true, rng), "negative protection hurts");
        assertEquals(100, CombatFormulas.mitigateOnPlayer(pc, 100, DamageType.ENERGY, true, rng));
        pc.equipment().setDirect(EquipSlot.HEAD, new ItemStack(TestData.ITEMS.byKey("coral_helmet").id(), 1));
        assertEquals(0, CombatFormulas.mitigateOnPlayer(pc, 500, DamageType.DROWN, true, rng), "helmet of the deep");
    }

    @Test
    void itemLookShowsProtectionCritAndLeech() {
        String look = ItemCompare.stats(TestData.ITEMS.byKey("obsidian_armor"), null);
        assertTrue(look.contains("ochrona ogień +8%, lód -8%"), look);
        String blade = ItemCompare.stats(TestData.ITEMS.byKey("umbral_blade"), null);
        assertTrue(blade.contains("krytyk +10% szansy, +10% obrażeń"), blade);
        assertTrue(ItemCompare.stats(TestData.ITEMS.byKey("skull_axe"), null).contains("kradzież życia 5%, many 3%"));
        assertEquals(2.5, ItemBonus.parse("LIFELEECH:2.5").lifeLeech());
        assertEquals("2,5", ItemBonus.number(2.5));
    }

    @Test
    void criticalHitsLikeTibia() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        assertEquals(5, CriticalHit.chance(pc), 1e-9, "base 5%");
        assertEquals(10, CriticalHit.extraDamage(pc), 1e-9, "base +10%");
        assertEquals(110, CriticalHit.apply(pc, 100, true));
        assertEquals(100, CriticalHit.apply(pc, 100, false));
        pc.equipment().setDirect(EquipSlot.WEAPON, new ItemStack(TestData.ITEMS.byKey("abyss_hammer").id(), 1));
        pc.enchantments().extend(Enchantments.Kind.CRIT);
        // baza + przedmiot (+10% / +12%) + zaklecie kowala jak Powerful Strike (+10% / +50%): sumy, nie iloczyny
        assertEquals(25, CriticalHit.chance(pc), 1e-9);
        assertEquals(72, CriticalHit.extraDamage(pc), 1e-9);
        assertEquals(172, CriticalHit.apply(pc, 100, true));
        // szansa: okolo 25% z wielu rzutow
        Random rng = new Random(5);
        int crits = 0;
        for (int i = 0; i < 20_000; i++) if (CriticalHit.roll(pc, rng)) crits++;
        assertEquals(0.25, crits / 20_000.0, 0.015);
    }

    @Test
    void weaponCritIsMarkedOnTheHitEvent() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.setPosition(new Position(10, 12, 0));
        MonsterManager mm = new MonsterManager(TestData.varden(), REG,
                List.of(new SpawnPoint("boar", new Position(11, 12, 0), 0, 1, 60)), new Random(3));
        mm.populate(null);
        Monster boar = mm.monsters().get(0);
        CombatSystem cs = new CombatSystem(pc, TestData.varden(), TestData.ITEMS, mm, new GroundItems(), new EventLog(50),
                new FixedRandom(false), 8);
        cs.setTarget(boar);
        cs.update(10_000);
        CombatEvent hit = cs.latestEvents(1).get(0);
        assertEquals(CriticalHit.EVENT_EFFECT, hit.effect(), "nextInt 0 -> critical hit");
    }

    @Test
    void leechUsesTheTibiaMultiTargetFormula() {
        assertEquals(10, Leech.amount(100, 10, 1));
        assertEquals(6, Leech.amount(100, 10, 2), "2 targets: 10 * 1.1 / 2 = 5.5 -> 6");
        assertEquals(2, Leech.amount(100, 10, 8), "8 targets: 10 * 1.7 / 8 = 2.1");
        assertEquals(0, Leech.amount(0, 25, 1));
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.equipment().setDirect(EquipSlot.WEAPON, new ItemStack(TestData.ITEMS.byKey("skull_axe").id(), 1));
        pc.enchantments().extend(Enchantments.Kind.LIFE);
        assertEquals(11, Leech.lifePercent(pc), 1e-9, "item 5% + smith 6%");
        pc.damage(pc.maxHealth() - 1);
        int hp = pc.health();
        pc.spendMana(pc.mana());
        Leech.apply(pc, 1000, 1);
        assertEquals(hp + 110, pc.health());
        assertEquals(30, pc.mana(), "3% mana leech");
    }

    @Test
    void manaDrainTakesManaNotHealth() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        pc.setPosition(new Position(10, 12, 0));
        MonsterManager mm = new MonsterManager(TestData.varden(), REG,
                List.of(new SpawnPoint("void_priest", new Position(11, 12, 0), 0, 1, 60)), new Random(3));
        mm.populate(null);
        Monster priest = mm.monsters().get(0);
        // nextInt 0: atak dodatkowy (wyssanie many) zawsze wypada
        CombatSystem cs = new CombatSystem(pc, TestData.varden(), TestData.ITEMS, mm, new GroundItems(), new EventLog(50),
                new FixedRandom(false), 8);
        int hp = pc.health();
        int mana = pc.mana();
        cs.monsterAttacks(priest, 1000);
        assertEquals(hp, pc.health(), "mana drain does not hurt");
        assertTrue(pc.mana() < mana || mana == 0);
        CombatEvent e = cs.latestEvents(1).get(0);
        if (mana > 0) assertEquals(CombatSystem.MANA_EFFECT, e.effect());
    }
}
