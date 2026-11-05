package pl.fawizja.pixel.magic;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.combat.CombatFormulas;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.vocation.Harmony;
import pl.fawizja.pixel.vocation.Harmony.Virtue;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

/** Harmonia mnicha jak w Tibii / Canary: punkty, czary budujace i wydajace, cnoty, spokoj, Skupienia, zapis i smierc. */
class HarmonyTest {

    static PlayerCharacter monk(int level) {
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        pc.addExperience(Balance.experienceForLevel(level) - pc.experience());
        pc.restoreMana(pc.maxMana());
        return pc;
    }

    /** Mnich obok bandyty (cel wybrany), poza miastem. */
    static GameSession fight(PlayerCharacter pc) {
        GameSession s = MagicTest.session(pc, "bandit", new Position(11, 12, 0));
        Monster m = s.monsters().monsters().get(0);
        s.combat().setTarget(m);
        return s;
    }

    @Test
    void spenderBonusFollowsCanary() {
        assertEquals(1.0, Harmony.bonus(0, Virtue.NONE, true), 1e-9);
        assertEquals(1.08, Harmony.bonus(1, Virtue.NONE, true), 1e-9);
        assertEquals(2.28, Harmony.bonus(5, Virtue.NONE, true), 1e-9, "8% * 2^4");
        assertEquals(2.92, Harmony.bonus(5, Virtue.HARMONY, false), 1e-9, "virtue of harmony: 12%");
        assertEquals(3.56, Harmony.bonus(5, Virtue.HARMONY, true), 1e-9, "virtue of harmony while serene: 16%");
        assertEquals(1.64, Harmony.bonus(4, Virtue.JUSTICE, true), 1e-9, "other virtues do not change the bonus");
    }

    @Test
    void flatBonusAndHarmonyHealFollowCanary() {
        assertEquals(20, Harmony.flatDamageHealing(100));
        assertEquals(100, Harmony.flatDamageHealing(500));
        assertEquals(117, Harmony.flatDamageHealing(600), "slower above level 500 (1/6)");
        assertEquals(10, Harmony.healMin(1, 1), "minimum 10");
        assertEquals(25, Harmony.healMax(1, 1), "minimum 25");
        assertEquals(42, Harmony.healMin(100, 1), "20 * 2 * 1.05");
        assertEquals(58, Harmony.healMax(100, 5), "20 * 2.3 * 1.25");
    }

    @Test
    void builderAddsOnePointAndHeals() {
        PlayerCharacter pc = monk(20);
        GameSession s = fight(pc);
        pc.damage(100);
        int hp = pc.health();
        assertEquals(CastResult.OK, s.castSpell("swift_jab", 0));
        assertEquals(1, pc.harmony().points());
        assertTrue(pc.health() > hp, "building harmony heals the monk");
        for (int i = 1; i <= 6; i++) {
            pc.restoreMana(100);
            s.castSpell("swift_jab", i * 2000L);
        }
        assertEquals(Harmony.MAX, pc.harmony().points(), "at most 5 points");
    }

    @Test
    void spenderConsumesAllPointsHitsHarderAndCutsBuilderCooldowns() {
        PlayerCharacter pc = monk(120);
        GameSession s = fight(pc);
        SpellType clash = s.spells().registry().get("tiger_strike");
        double plain = s.spells().expectedValue(clash);
        pc.harmony().build(5);
        assertEquals(plain * 2.28, s.spells().expectedValue(clash), 1e-6);
        assertEquals(CastResult.OK, s.castSpell("forceful_uppercut", 0)); // builder, cooldown 60 s (harmony already full)
        SpellType upper = s.spells().registry().get("forceful_uppercut");
        assertEquals(58_000, s.spells().cooldowns().remaining(upper, 2000));
        assertEquals(CastResult.OK, s.castSpell("tiger_strike", 2000));
        assertEquals(0, pc.harmony().points(), "the spender uses all points");
        assertEquals(48_000, s.spells().cooldowns().remaining(upper, 2000), "2 s less per spent point");
    }

    @Test
    void monkAttackSpellsUseTheCanaryFormula() {
        PlayerCharacter pc = monk(100);
        pc.skills().set(Skill.FIST, 80, 0);
        pc.equipment().setDirect(pl.fawizja.pixel.item.EquipSlot.WEAPON, pl.fawizja.pixel.item.ItemStack.of(MagicTest.id("desert_knuckles")));
        SpellType jab = MagicTest.SPELLS.get("double_jab");
        // 50 * 80/100 * 42/10 + 100/5
        assertEquals(50 * 0.8 * 4.2 + 20, SpellSystem.baseValue(jab, pc), 1e-6);
        assertEquals(MonkRole.BUILDER, jab.monkRole());
        assertEquals(MonkRole.SPENDER, MagicTest.SPELLS.get("devastating_knockout").monkRole());
        assertEquals("exori infir pug", MagicTest.SPELLS.words("swift_jab"));
    }

    @Test
    void virtueOfHarmonyGivesBackOnePoint() {
        PlayerCharacter pc = monk(30);
        GameSession s = fight(pc);
        assertEquals(CastResult.OK, s.castSpell("virtue_of_harmony", 0));
        assertEquals(Virtue.HARMONY, pc.harmony().virtue());
        pc.restoreMana(pc.maxMana());
        assertEquals(CastResult.COOLDOWN, s.castSpell("virtue_of_justice", 5000), "10 s between virtues");
        pc.harmony().build(5);
        assertEquals(CastResult.OK, s.castSpell("tiger_strike", 2000));
        assertEquals(1, pc.harmony().points());
    }

    @Test
    void virtuesNeedLevel20() {
        PlayerCharacter pc = monk(19);
        GameSession s = MagicTest.session(pc, null, null);
        assertEquals(CastResult.LEVEL_TOO_LOW, s.castSpell("virtue_of_sustain", 0));
    }

    @Test
    void virtueOfJusticeBoostsFistSkillMoreWhenSerene() {
        PlayerCharacter pc = monk(50);
        pc.skills().set(Skill.FIST, 100, 0);
        int before = CombatFormulas.attackProfile(pc, TestData.ITEMS).maxDamage();
        pc.harmony().setVirtue(Virtue.JUSTICE);
        assertEquals(130, pc.effectiveSkill(Skill.FIST), "serene: +30%");
        pc.harmony().update(0, true, 6);
        assertEquals(115, pc.effectiveSkill(Skill.FIST), "not serene: +15%");
        assertTrue(CombatFormulas.attackProfile(pc, TestData.ITEMS).maxDamage() > before);
        assertEquals(100, pc.skills().level(Skill.FIST), "the trained skill itself does not change");
    }

    @Test
    void virtueOfSustainBoostsMonkHealing() {
        PlayerCharacter pc = monk(50);
        GameSession s = MagicTest.session(pc, null, null);
        SpellType calm = s.spells().registry().get("inner_calm");
        double plain = s.spells().expectedValue(calm);
        pc.harmony().setVirtue(Virtue.SUSTAIN);
        assertEquals(plain * 1.70, s.spells().expectedValue(calm), 1e-6);
        pc.harmony().update(0, true, 8);
        assertEquals(plain * 1.35, s.spells().expectedValue(calm), 1e-6);
    }

    @Test
    void sereneIsLostOnlyWithAPartyMemberAndACrowd() {
        Harmony h = new Harmony();
        assertTrue(h.serene(), "alone: serene");
        h.update(100, false, 8);
        assertTrue(h.serene(), "no party member in view: serene even when surrounded");
        h.update(100, true, 5);
        assertTrue(h.serene(), "party member in view, 5 creatures around: serene");
        assertTrue(h.update(100, true, 6), "state change reported");
        assertFalse(h.serene());
        h.forceSerene(Harmony.FOCUS_SERENE_MS);
        h.update(3000, true, 6);
        assertTrue(h.serene(), "forced by Focus Serenity");
        h.update(4000, true, 6);
        assertFalse(h.serene(), "7 s later the crowd counts again");
    }

    @Test
    void focusSerenityFillsHarmonyResetsSpendersAndForcesSerene() {
        PlayerCharacter pc = monk(150);
        GameSession s = fight(pc);
        assertEquals(CastResult.OK, s.castSpell("tiger_strike", 0));
        SpellType clash = s.spells().registry().get("tiger_strike");
        assertTrue(s.spells().cooldowns().remaining(clash, 2000) > 0);
        assertEquals(CastResult.OK, s.castSpell("focus_serenity", 2000));
        assertEquals(Harmony.MAX, pc.harmony().points());
        assertEquals(Harmony.FOCUS_SERENE_MS, pc.harmony().forcedSereneMs());
        assertEquals(0, s.spells().cooldowns().remaining(clash, 4000), "spender cooldowns cleared");
        assertEquals(CastResult.LEVEL_TOO_LOW, s.castSpell("focus_harmony", 5000), "Focus Harmony from level 275");
    }

    @Test
    void harmonySurvivesLogoutButNotDeath() throws Exception {
        PlayerCharacter pc = monk(40);
        pc.harmony().build(3);
        pc.harmony().setVirtue(Virtue.SUSTAIN);
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertEquals(3, back.harmony().points());
        assertEquals(Virtue.SUSTAIN, back.harmony().virtue());
        back.revive(back.position());
        assertEquals(0, back.harmony().points(), "death empties harmony");
        assertEquals(Virtue.SUSTAIN, back.harmony().virtue(), "the virtue stays");
        PlayerCharacter fresh = TestData.newCharacter(Vocation.MONK);
        assertFalse(codec.encode(fresh).contains("monk="), "default state is not written");
    }

    @Test
    void onlineReplicaCopiesHarmony() throws Exception {
        PlayerCharacter server = monk(40);
        server.harmony().build(4);
        server.harmony().setVirtue(Virtue.JUSTICE);
        server.harmony().update(0, true, 7);
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter client = TestData.newCharacter(Vocation.MONK);
        client.copyStateFrom(codec.decode(codec.encode(server, false)));
        assertEquals(4, client.harmony().points());
        assertEquals(Virtue.JUSTICE, client.harmony().virtue());
        assertFalse(client.harmony().serene());
    }
}
