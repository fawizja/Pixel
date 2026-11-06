package pl.fawizja.pixel.wheel;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.combat.CriticalHit;
import pl.fawizja.pixel.combat.Leech;
import pl.fawizja.pixel.vocation.Vocation;

class WheelPerksTest {

    @Test
    void dedicationPerPointFollowsTibiaPerVocation() {
        assertEquals(3, WheelPerks.hpPerPoint(Vocation.KNIGHT));
        assertEquals(1, WheelPerks.manaPerPoint(Vocation.KNIGHT));
        assertEquals(5, WheelPerks.capPerPoint(Vocation.KNIGHT));
        assertEquals(2, WheelPerks.hpPerPoint(Vocation.PALADIN));
        assertEquals(3, WheelPerks.manaPerPoint(Vocation.PALADIN));
        assertEquals(4, WheelPerks.capPerPoint(Vocation.PALADIN));
        for (Vocation mage : new Vocation[] {Vocation.SORCERER, Vocation.DRUID}) {
            assertEquals(1, WheelPerks.hpPerPoint(mage));
            assertEquals(6, WheelPerks.manaPerPoint(mage));
            assertEquals(2, WheelPerks.capPerPoint(mage));
        }
        assertEquals(2, WheelPerks.hpPerPoint(Vocation.MONK));
        assertEquals(2, WheelPerks.manaPerPoint(Vocation.MONK));
        assertEquals(5, WheelPerks.capPerPoint(Vocation.MONK));
    }

    @Test
    void pointsRaiseHealthManaAndCapacityOnlyWhileEligible() {
        PlayerCharacter pc = WheelFixtures.hero(Vocation.KNIGHT, 300);
        int hp = pc.maxHealth(), mana = pc.maxMana(), cap = pc.capacity();
        // zielony srodek: udzwig, gorne 75 zielone: mana
        pc.wheel().add(WheelSlice.GREEN_50, 50, pc.wheelPoints());
        pc.wheel().add(WheelSlice.GREEN_TOP_75, 75, pc.wheelPoints());
        pc.wheel().add(WheelSlice.GREEN_TOP_100, 100, pc.wheelPoints());
        assertEquals(cap + 50 * 5, pc.capacity());
        assertEquals(mana + 75, pc.maxMana());
        assertEquals(hp + 100 * 3, pc.maxHealth());
        pc.setPremium(false);
        assertEquals(hp, pc.maxHealth(), "free account: wheel dormant");
        assertEquals(WheelBonus.NONE, pc.wheelBonus());
    }

    @Test
    void fullSlicesGiveConvictionSkillLeechAndPassives() {
        PlayerCharacter pc = WheelFixtures.hero(Vocation.KNIGHT, 1000); // 950 pkt
        int sword = pc.effectiveSkill(Skill.SWORD);
        double life = Leech.lifePercent(pc), mana = Leech.manaPercent(pc);
        WheelFixtures.path(pc, WheelDomain.PURPLE); // fioletowe gorne 75: +1 walki wreczy
        WheelFixtures.fill(pc, "R");                 // czerwone dolne 75: kradziez zycia
        WheelBonus b = pc.wheelBonus();
        assertEquals(2, b.skill, "purple T75 + red T100");
        assertEquals(sword + b.skill, pc.effectiveSkill(Skill.SWORD));
        assertEquals(life + b.lifeLeech, Leech.lifePercent(pc), 1e-9);
        assertEquals(mana + b.manaLeech, Leech.manaPercent(pc), 1e-9);
        assertEquals(WheelPerks.LIFE_LEECH, b.lifeLeech, 1e-9);
        assertTrue(b.has(WheelPassive.BATTLE_HEALING), "purple 200 for a knight (Canary slotPurple200)");
        assertFalse(b.has(WheelPassive.BATTLE_INSTINCT));
        assertTrue(b.mitigation > 0);
    }

    @Test
    void battleInstinctNeedsFiveAdjacentMonsters() {
        PlayerCharacter pc = WheelFixtures.hero(Vocation.KNIGHT, 700);
        WheelFixtures.path(pc, WheelDomain.GREEN); // zielone 200 = Instynkt bitewny (Canary slotGreen200)
        assertTrue(pc.wheelBonus().has(WheelPassive.BATTLE_INSTINCT));
        int shield = pc.effectiveSkill(Skill.SHIELDING);
        pc.wheel().setAdjacentMonsters(4);
        assertEquals(shield, pc.effectiveSkill(Skill.SHIELDING));
        pc.wheel().setAdjacentMonsters(7);
        assertEquals(shield + 18, pc.effectiveSkill(Skill.SHIELDING));
    }

    @Test
    void revelationStagesAndFlatBonusFollowDomainPoints() {
        assertEquals(0, WheelDomain.stage(249));
        assertEquals(1, WheelDomain.stage(250));
        assertEquals(2, WheelDomain.stage(500));
        assertEquals(3, WheelDomain.stage(1000));
        PlayerCharacter pc = WheelFixtures.hero(Vocation.PALADIN, 700); // 650 punktow
        WheelFixtures.fill(pc, "RGPB");
        assertEquals(2, pc.wheelBonus().stage(WheelDomain.RED));
        assertEquals(0, pc.wheelBonus().stage(WheelDomain.GREEN));
        assertEquals(9, pc.wheelBonus().flatDamage, "+9 for one stage-2 domain");
        assertEquals(WheelRevelation.DIVINE_GRENADE, WheelRevelation.of(WheelDomain.RED, Vocation.PALADIN));
        for (Vocation v : Vocation.values()) {
            assertTrue(WheelRevelation.of(WheelDomain.PURPLE, v).isAvatar(), v + " avatar is purple");
            assertEquals(WheelRevelation.GIFT_OF_LIFE, WheelRevelation.of(WheelDomain.GREEN, v));
        }
    }

    @Test
    void avatarGivesSureCriticalAndCutsDamageTaken() {
        PlayerCharacter pc = WheelFixtures.hero(Vocation.SORCERER, 400);
        WheelFixtures.fill(pc, "PRGB");
        double baseCrit = CriticalHit.chance(pc);
        assertTrue(baseCrit < 100);
        pc.wheel().startAvatar(2, DestinyWheel.AVATAR_COOLDOWN_MS);
        assertTrue(CriticalHit.chance(pc) >= 100);
        assertEquals(90, WheelEffects.incoming(pc, 100), 1e-9);
        pc.wheel().tick(DestinyWheel.AVATAR_MS);
        assertFalse(pc.wheel().avatarActive());
        assertEquals(100, WheelEffects.incoming(pc, 100), 1e-9);
        assertTrue(pc.wheel().avatarCooldownMs() > 0);
    }

    @Test
    void giftOfLifeSavesFromALethalBlowOncePerCooldown() {
        PlayerCharacter pc = WheelFixtures.hero(Vocation.DRUID, 400); // 350 pkt -> zielona etap 1
        WheelFixtures.fill(pc, "GRPB");
        assertEquals(1, pc.wheelBonus().stage(WheelDomain.GREEN));
        int max = pc.maxHealth();
        pc.damage(pc.health() - 100);
        pc.damage(100 + max / 10); // przebicie 10% < 20%
        assertFalse(pc.isDead());
        assertTrue(pc.health() > 1);
        assertTrue(pc.wheel().consumeGift());
        assertEquals(30 * 3_600_000L, pc.wheel().giftCooldownMs());
        pc.damage(pc.health() + max / 10);
        assertTrue(pc.isDead(), "on cooldown");

        PlayerCharacter other = WheelFixtures.hero(Vocation.DRUID, 400);
        WheelFixtures.fill(other, "GRPB");
        other.damage(other.health() + other.maxHealth() / 2); // przebicie 50% > 20%
        assertTrue(other.isDead(), "overkill too large");
    }
}
