package pl.fawizja.pixel.charm;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.combat.CombatEvent;
import pl.fawizja.pixel.combat.CombatSystem;
import pl.fawizja.pixel.combat.CriticalHit;
import pl.fawizja.pixel.combat.DamageType;
import pl.fawizja.pixel.combat.Leech;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.item.Enchantments;
import pl.fawizja.pixel.monster.LootEntry;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.GroundItems;
import pl.fawizja.pixel.world.Position;

/** Dzialanie charmow w walce (wzory Canary). */
class CharmCombatTest {
    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);

    /** Zawsze 0 (kazdy rzut charmu i krytyka wypada) albo zawsze bound-1 (nic nie wypada). */
    static final class FixedRandom extends Random {
        final boolean high;
        FixedRandom(boolean high) { this.high = high; }
        @Override public int nextInt(int bound) { return high ? bound - 1 : 0; }
    }

    /** Postac z ukonczonym wpisem potwora i charmem na nim. */
    private static PlayerCharacter withCharm(String key, Charm... charms) {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.setPremium(true);
        for (int i = 0; i < 5000; i++) pc.bestiary().record(key, false);
        pc.charms().addPoints(100_000);
        pc.charms().addEchoes(10_000);
        for (Charm c : charms) {
            for (int i = 0; i < 3; i++) pc.charms().buy(c);
            assertTrue(pc.charms().assign(c, key, pc.bestiary(), true, key).ok(), c.name());
        }
        return pc;
    }

    private static Monster spawn(String key) {
        MonsterManager mm = new MonsterManager(TestData.varden(), REG,
                List.of(new SpawnPoint(key, new Position(11, 12, 0), 0, 1, 60)), new Random(3));
        mm.populate(null);
        return mm.monsters().get(0);
    }

    private static CombatSystem combat(PlayerCharacter pc, Monster m, boolean high) {
        pc.setPosition(new Position(10, 12, 0));
        MonsterManager mm = new MonsterManager(TestData.varden(), REG, List.of(), new Random(1));
        return new CombatSystem(pc, TestData.varden(), TestData.ITEMS, mm, new GroundItems(), new EventLog(50), new FixedRandom(high), 8);
    }

    @Test
    void damageFormulasFollowCanary() {
        assertEquals(400, CharmEffects.elementalDamage(200, 20_000), "2 x level caps 5% of 20 000");
        assertEquals(250, CharmEffects.elementalDamage(500, 5_000), "5% of max HP");
        assertEquals(80, CharmEffects.overpowerDamage(5_000, 1_000), "8% of target HP caps 5% of 5 000");
        assertEquals(50, CharmEffects.overfluxDamage(2_000, 10_000), "2.5% of max mana");
        assertEquals(600, CharmEffects.carnageDamage(100, 10_000), "6 x level caps 15%");
        assertEquals(50, CharmEffects.resisted(REG.get("wolf"), 50, null), "neutral ignores resistances");
        assertEquals(70, CharmEffects.resisted(REG.get("wolf"), 100, DamageType.HOLY), "wolf holy +30%");
    }

    @Test
    void woundProcsAfterAHitAndIsMarked() {
        PlayerCharacter pc = withCharm("boar", Charm.WOUND);
        Monster boar = spawn("boar");
        CombatSystem cs = combat(pc, boar, false);
        int before = boar.health();
        cs.damageMonster(boar, 10, DamageType.ENERGY, 1000, false, 1);
        boolean proc = false;
        for (CombatEvent e : cs.latestEvents(5)) proc |= CharmEffects.eventEffect(Charm.WOUND).equals(e.effect()) && e.amount() > 0;
        assertTrue(proc, "wound charm event");
        int wound = CharmEffects.elementalDamage(pc.level(), boar.type().maxHealth());
        assertTrue(before - boar.health() >= wound, "charm damage dealt");
    }

    @Test
    void noProcWhenRollFails() {
        PlayerCharacter pc = withCharm("boar", Charm.WOUND);
        Monster boar = spawn("boar");
        CombatSystem cs = combat(pc, boar, true);
        cs.damageMonster(boar, 10, DamageType.ENERGY, 1000, false, 1);
        for (CombatEvent e : cs.latestEvents(5)) assertNull(CharmEffects.fromEvent(e.effect()));
    }

    @Test
    void dodgePreventsTheAttack() {
        PlayerCharacter pc = withCharm("boar", Charm.DODGE);
        Monster boar = spawn("boar");
        CombatSystem cs = combat(pc, boar, false);
        int hp = pc.health();
        cs.monsterAttacks(boar, 1000);
        assertEquals(hp, pc.health());
        CombatEvent e = cs.latestEvents(1).get(0);
        assertEquals(CombatEvent.Kind.BLOCKED, e.kind());
        assertEquals(Charm.DODGE, CharmEffects.fromEvent(e.effect()));
    }

    @Test
    void numbAndCripppleParalyzeFatalHoldStopsFleeing() {
        PlayerCharacter pc = withCharm("wolf", Charm.NUMB);
        Monster wolf = spawn("wolf");
        CombatSystem cs = combat(pc, wolf, false);
        cs.monsterAttacks(wolf, 1000);
        assertTrue(wolf.paralyzed(1000 + CharmEffects.PARALYZE_MS - 1));
        assertFalse(wolf.paralyzed(1000 + CharmEffects.PARALYZE_MS));

        PlayerCharacter pc2 = withCharm("wolf", Charm.FATAL_HOLD);
        Monster w2 = spawn("wolf");
        CombatSystem cs2 = combat(pc2, w2, false);
        cs2.damageMonster(w2, 5, DamageType.ENERGY, 2000, false, 1);
        assertTrue(w2.fleeBlocked(2000 + CharmEffects.FATAL_HOLD_MS - 1));
    }

    @Test
    void lowBlowSavageBlowAndLeechCharmsOnlyAgainstTheirCreature() {
        PlayerCharacter pc = withCharm("boar", Charm.LOW_BLOW, Charm.VAMPIRIC_EMBRACE);
        MonsterType boar = REG.get("boar");
        MonsterType wolf = REG.get("wolf");
        assertEquals(9, CharmEffects.critChanceBonus(pc, boar), 1e-9);
        assertEquals(0, CharmEffects.critChanceBonus(pc, wolf), 1e-9);
        // brak kradziezy z przedmiotow = Wampirzy uscisk nie dziala (opis Tibii)
        assertEquals(0, Leech.lifePercent(pc, boar), 1e-9);
        pc.enchantments().extend(Enchantments.Kind.LIFE);
        assertEquals(Enchantments.LIFE_PERCENT + 3.2, Leech.lifePercent(pc, boar), 1e-9);
        assertEquals(Enchantments.LIFE_PERCENT, Leech.lifePercent(pc, wolf), 1e-9);
        // Niski cios: laczna szansa p + 9 (rzut uzupelniajacy l / (100 - p))
        Random rng = new Random(5);
        int crits = 0;
        double p = CriticalHit.chance(pc);
        for (int i = 0; i < 40_000; i++) {
            if (CriticalHit.roll(pc, rng) || CharmEffects.lowBlowReroll(pc, boar, p, rng)) crits++;
        }
        assertEquals((p + 9) / 100.0, crits / 40_000.0, 0.01);

        PlayerCharacter sav = withCharm("boar", Charm.SAVAGE_BLOW);
        assertEquals(CriticalHit.apply(sav, 1000, true, 44), Math.round(1000 * (1 + (CriticalHit.extraDamage(sav) + 44) / 100.0)));
        assertEquals(44, CharmEffects.critDamageBonus(sav, boar), 1e-9);
    }

    @Test
    void blessReducesExperienceLoss() {
        PlayerCharacter a = TestData.newCharacter(Vocation.KNIGHT);
        PlayerCharacter b = TestData.newCharacter(Vocation.KNIGHT);
        long exp = a.experience();
        a.applyDeathPenalty(1);
        b.applyDeathPenalty(1, 12);
        long lossA = exp - a.experience();
        long lossB = exp - b.experience();
        assertEquals(Math.round(lossA * 0.88), lossB, 1.0);
        PlayerCharacter pc = withCharm("wolf", Charm.BLESS);
        assertEquals(12, CharmEffects.blessReduction(pc, REG.get("wolf")), 1e-9);
        assertEquals(0, CharmEffects.blessReduction(pc, REG.get("boar")), 1e-9);
    }

    @Test
    void gutAndScavengeRaiseMaterialChances() {
        PlayerCharacter pc = withCharm("wolf", Charm.GUT);
        MonsterType wolf = REG.get("wolf");
        LootEntry pelt = null, gold = null;
        for (LootEntry e : wolf.loot()) {
            String k = TestData.ITEMS.get(e.itemId()).key();
            if (k.equals("wolf_pelt")) pelt = e;
            if (k.equals("gold_coin")) gold = e;
        }
        assertNotNull(pelt);
        assertEquals(120 + 15, CharmEffects.lootChance(pc, wolf, pelt, TestData.ITEMS, 120), "120 + ceil(120 * 12%)");
        assertEquals(800, CharmEffects.lootChance(pc, wolf, gold, TestData.ITEMS, 800), "gold is not a creature product");
        PlayerCharacter sc = withCharm("wolf", Charm.SCAVENGE);
        LootEntry trophy = CharmEffects.trophy(wolf, TestData.ITEMS);
        assertNotNull(trophy);
        assertEquals(Math.min(1000, Math.round(trophy.chancePermille() * 2.2)),
                CharmEffects.lootChance(sc, wolf, trophy, TestData.ITEMS, trophy.chancePermille()));
    }

    @Test
    void parryReflectsDamageAndVoidInversionGivesMana() {
        PlayerCharacter pc = withCharm("boar", Charm.PARRY);
        Monster boar = spawn("boar");
        CombatSystem cs = combat(pc, boar, false);
        int mhp = boar.health();
        int hp = pc.health();
        cs.monsterAttacks(boar, 1000);
        int taken = hp - pc.health();
        if (taken > 0) assertEquals(mhp - taken, boar.health(), "reflected exactly the damage taken");

        PlayerCharacter sorc = TestData.newCharacter(Vocation.SORCERER);
        for (int i = 0; i < 5; i++) sorc.bestiary().record("void_priest", false);
        sorc.charms().addPoints(10_000);
        sorc.charms().buy(Charm.WOUND); // echa
        sorc.charms().buy(Charm.WOUND);
        assertTrue(sorc.charms().buy(Charm.VOID_INVERSION).ok());
        assertTrue(sorc.charms().assign(Charm.VOID_INVERSION, "void_priest", sorc.bestiary(), false, "x").ok());
        Monster priest = spawn("void_priest");
        CombatSystem cs2 = combat(sorc, priest, false);
        sorc.spendMana(sorc.mana() / 2);
        int mana = sorc.mana();
        cs2.monsterAttacks(priest, 1000); // nextInt 0: wyssanie many i rzut charmu wypadaja
        assertTrue(sorc.mana() > mana, "mana drain inverted");
    }
}
