package pl.fawizja.pixel.combat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.GroundItems;
import pl.fawizja.pixel.world.Position;

class CombatTest {

    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);

    @Test
    void attackProfilesMatchVocations() {
        AttackProfile pal = CombatFormulas.attackProfile(TestData.newCharacter(Vocation.PALADIN), TestData.ITEMS);
        AttackProfile dru = CombatFormulas.attackProfile(TestData.newCharacter(Vocation.DRUID), TestData.ITEMS);
        AttackProfile sor = CombatFormulas.attackProfile(TestData.newCharacter(Vocation.SORCERER), TestData.ITEMS);
        AttackProfile monk = CombatFormulas.attackProfile(TestData.newCharacter(Vocation.MONK), TestData.ITEMS);
        assertEquals(Skill.DISTANCE, pal.skill());
        assertEquals(4, pal.range());
        assertEquals(DamageType.EARTH, dru.type());
        assertEquals(DamageType.FIRE, sor.type());
        assertEquals(Skill.MAGIC, sor.skill());
        assertEquals(Skill.FIST, monk.skill());
        assertEquals(1, monk.range());
        assertTrue(monk.intervalMs() < pal.intervalMs(), "monk attacks faster");
        assertTrue(sor.maxDamage() > dru.maxDamage(), "sorcerer hits harder than druid");
    }

    @Test
    void mitigationRespectsArmorAndResistances() {
        Random rng = new Random(7);
        assertEquals(0, CombatFormulas.mitigateOnMonster(REG.get("skeleton"), 100, DamageType.DEATH, rng), "immune");
        int holy = CombatFormulas.mitigateOnMonster(REG.get("skeleton"), 100, DamageType.HOLY, rng);
        assertEquals(130, holy, "weakness");
        for (int i = 0; i < 100; i++) {
            int phys = CombatFormulas.mitigateOnMonster(REG.get("cave_troll"), 100, DamageType.PHYSICAL, rng);
            assertTrue(phys >= 81 && phys <= 86, "armor 10 + 10% res: " + phys);
        }
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        int sumNormal = 0, sumDef = 0;
        for (int i = 0; i < 500; i++) {
            sumNormal += CombatFormulas.mitigateOnPlayer(pc, 40, DamageType.PHYSICAL, false, rng);
            sumDef += CombatFormulas.mitigateOnPlayer(pc, 40, DamageType.PHYSICAL, true, rng);
        }
        assertTrue(sumDef < sumNormal, "defensive stance reduces damage");
        assertTrue(sumNormal < 500 * 40, "armor reduces physical damage");
        assertEquals(40, CombatFormulas.mitigateOnPlayer(pc, 40, DamageType.FIRE, false, rng), "armor ignores elements");
    }

    @Test
    void experienceRewardShrinksForTrivialMonsters() {
        assertEquals(20, Balance.experienceReward(20, 8, 8));
        assertEquals(20, Balance.experienceReward(20, 8, 52));
        long high = Balance.experienceReward(20, 8, 500);
        assertTrue(high <= 3, "level 500 gets almost nothing from a wolf: " + high);
        assertEquals(4000, Balance.experienceReward(4000, 60, 100));
    }

    @Test
    void deathPenaltyNeverDropsBelowStartLevel() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        pc.applyDeathPenalty(8);
        assertEquals(8, pc.level());
        pc.addExperience(10_000_000);
        long before = pc.experience();
        pc.applyDeathPenalty(8);
        assertEquals(before - before / 20, pc.experience());
    }

    @Test
    void lootRollsAreDeterministicWithSeedAndRespectStacks() {
        CombatSystem cs = system(TestData.newCharacter(Vocation.PALADIN), new Random(42), null);
        int coins = 0;
        for (int i = 0; i < 1000; i++) {
            for (ItemStack s : cs.rollLoot(REG.get("grimtooth"))) {
                assertTrue(s.count() <= TestData.ITEMS.maxStack(s.itemId()));
                if (s.itemId() == 2) coins += s.count();
            }
        }
        assertTrue(coins >= 10_000 && coins <= 25_000, "boss always drops 10-25 platinum: " + coins);
    }

    @Test
    void killingAMonsterGivesExperienceAndLoot() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        pc.setPosition(new Position(10, 12, 0));
        SpawnPoint sp = new SpawnPoint("wolf", new Position(11, 12, 0), 0, 1, 60);
        MonsterManager mm = new MonsterManager(TestData.varden(), REG, List.of(sp), new Random(3));
        mm.populate(null);
        Monster wolf = mm.monsters().get(0);
        assertEquals(new Position(11, 12, 0), wolf.position());
        CombatSystem cs = system(pc, new Random(5), mm);
        cs.setTarget(wolf);
        long exp = pc.experience();
        for (long t = 0; t < 120_000 && !wolf.isDead(); t += 100) cs.update(t);
        assertTrue(wolf.isDead());
        assertEquals(exp + 20, pc.experience());
        assertTrue(mm.monsters().isEmpty(), "dead monster removed");
        assertEquals(1, cs.kills());
        assertTrue(pc.skills().tries(Skill.FIST) > 0 || pc.skills().level(Skill.FIST) > 10);
        assertNull(cs.target());
        // odrodzenie po czasie
        mm.update(200_000, new MonsterManager.Target() {
            public Position position() { return new Position(31, 16, 0); }
            public boolean attackable() { return true; }
        }, cs);
        assertEquals(1, mm.monsters().size(), "wolf respawned");
    }

    @Test
    void cannotAttackFromProtectionZone() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        pc.setPosition(new Position(20, 22, 0)); // wewnatrz murow
        SpawnPoint sp = new SpawnPoint("wolf", new Position(16, 22, 0), 0, 1, 60);
        MonsterManager mm = new MonsterManager(TestData.varden(), REG, List.of(sp), new Random(3));
        mm.populate(null);
        Monster wolf = mm.monsters().get(0);
        CombatSystem cs = system(pc, new Random(1), mm);
        cs.setTarget(wolf);
        for (long t = 0; t < 10_000; t += 100) cs.update(t);
        assertEquals(wolf.type().maxHealth(), wolf.health());
    }

    @Test
    void playerDeathRespawnsInTempleWithPenalty() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        pc.addExperience(500_000);
        int level = pc.level();
        pc.setPosition(new Position(12, 27, -5));
        SpawnPoint sp = new SpawnPoint("grimtooth", new Position(12, 28, -5), 0, 1, 1200);
        MonsterManager mm = new MonsterManager(TestData.varden(), REG, List.of(sp), new Random(9));
        mm.populate(null);
        Monster boss = mm.monsters().get(0);
        CombatSystem cs = system(pc, new Random(2), mm);
        for (int i = 0; i < 200 && pc.position().z() == -5; i++) cs.monsterAttacks(boss, i * 1000L);
        assertEquals(TestData.varden().spawn(), pc.position());
        assertEquals(pc.maxHealth(), pc.health());
        assertTrue(pc.level() <= level);
        assertTrue(pc.level() >= 8);
        DeathRecap.Report d = cs.lastDeath();
        assertNotNull(d, "death recap kept");
        assertEquals(boss.type().name(), d.killer());
        assertEquals(boss.type().name(), d.rows().get(0).source());
        assertTrue(d.rows().get(0).total() > 0 && d.rows().get(0).hits() > 0);
        assertTrue(d.expLost() > 0);
    }

    @Test
    void meleeStrikesCarryTheAttackersPositionForTheLungeAnimation() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        pc.setPosition(new Position(10, 12, 0));
        SpawnPoint sp = new SpawnPoint("wolf", new Position(11, 12, 0), 0, 1, 60);
        MonsterManager mm = new MonsterManager(TestData.varden(), REG, List.of(sp), new Random(3));
        mm.populate(null);
        Monster wolf = mm.monsters().get(0);
        CombatSystem cs = system(pc, new Random(5), mm);
        cs.setTarget(wolf);
        cs.update(0);
        CombatEvent strike = cs.latestEvents(1).get(0);
        assertTrue(strike.isMeleeStrike(), strike.toString());
        assertEquals(pc.position(), strike.from());
        assertEquals(wolf.position(), strike.position());

        cs.monsterAttacks(wolf, 50);
        CombatEvent bite = cs.latestEvents(1).get(0);
        assertTrue(bite.isMeleeStrike());
        assertEquals(wolf.position(), bite.from());
        assertFalse(bite.isVisualEffect(), "melee strikes are not drawn as projectiles");
    }

    static CombatSystem system(PlayerCharacter pc, Random rng, MonsterManager mm) {
        MonsterManager m = mm != null ? mm : new MonsterManager(TestData.varden(), REG, Collections.emptyList(), rng);
        return new CombatSystem(pc, TestData.varden(), TestData.ITEMS, m, new GroundItems(), new EventLog(50), rng, 8);
    }

    @Test
    void trivialMonstersDoNotTrainSkills() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        pc.addExperience(Balance.experienceForLevel(100) - pc.experience());
        pc.setPosition(new Position(10, 12, 0));
        SpawnPoint sp = new SpawnPoint("wolf", new Position(11, 12, 0), 0, 1, 60);
        MonsterManager mm = new MonsterManager(TestData.varden(), REG, List.of(sp), new Random(3));
        mm.populate(null);
        CombatSystem cs = system(pc, new Random(5), mm);
        Monster wolf = mm.monsters().get(0);
        assertTrue(cs.isTrivial(wolf.type()));
        cs.setTarget(wolf);
        long tries = pc.skills().tries(Skill.FIST);
        int lvl = pc.skills().level(Skill.FIST);
        for (long t = 0; t < 30_000 && !wolf.isDead(); t += 100) cs.update(t);
        assertTrue(wolf.isDead());
        assertEquals(tries, pc.skills().tries(Skill.FIST));
        assertEquals(lvl, pc.skills().level(Skill.FIST));
        assertFalse(system(TestData.newCharacter(Vocation.MONK), new Random(1), mm).isTrivial(REG.get("wolf")));
    }
}
