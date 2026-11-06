package pl.fawizja.pixel.wheel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.magic.CastResult;
import pl.fawizja.pixel.magic.ConsumableRegistry;
import pl.fawizja.pixel.magic.RecipeRegistry;
import pl.fawizja.pixel.magic.SpellRegistry;
import pl.fawizja.pixel.magic.SpellType;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

class WheelSpellsTest {
    static final MonsterRegistry MONSTERS = MonsterRegistry.loadDefault(TestData.ITEMS);
    static final SpellRegistry SPELLS = SpellRegistry.loadDefault(TestData.ITEMS);

    static GameSession session(PlayerCharacter pc, String monster, Position at) {
        List<SpawnPoint> sp = monster == null ? Collections.emptyList() : List.of(new SpawnPoint(monster, at, 0, 1, 600));
        MonsterManager mm = new MonsterManager(TestData.varden(), MONSTERS, sp, new Random(3));
        mm.populate(null);
        GameSession s = new GameSession(TestData.ITEMS, TestData.varden(), pc, mm, new Random(5), 8, SPELLS,
                ConsumableRegistry.loadDefault(TestData.ITEMS), RecipeRegistry.loadDefault(TestData.ITEMS));
        s.teleport(new Position(10, 12, 0), 0);
        return s;
    }

    @Test
    void everyWheelSpellIsInTheDataWithWordsAndAVocation() {
        for (String k : List.of("avatar_of_steel", "avatar_of_light", "avatar_of_storm", "avatar_of_nature", "avatar_of_balance",
                "divine_empowerment", "divine_grenade", "great_death_beam", "terra_burst", "ice_burst")) {
            SpellType s = SPELLS.find(k);
            assertNotNull(s, k);
            assertNotNull(WheelSpells.requiredRevelation(k), k);
            assertEquals(300, s.minLevel(), k);
        }
        for (Vocation v : Vocation.values()) {
            for (int g = 0; g < 5; g++) {
                for (String k : WheelPerks.upgrade(v, g).spells) {
                    SpellType s = SPELLS.find(k);
                    assertNotNull(s, v + " upgrade " + k);
                    assertTrue(s.vocations().contains(v), v + " can cast " + k);
                }
            }
            String avatar = WheelRevelation.of(WheelDomain.PURPLE, v).spell;
            assertTrue(SPELLS.get(avatar).vocations().contains(v), avatar);
        }
        assertEquals("avatar_of_steel", SPELLS.byWords("uteta res eq"));
        assertEquals("great_death_beam", SPELLS.byWords("exevo max mort"));
    }

    @Test
    void revelationSpellsStayLockedUntilTheirDomainReachesStageOne() {
        PlayerCharacter pc = WheelFixtures.hero(Vocation.KNIGHT, 400);
        GameSession s = session(pc, null, null);
        assertEquals(CastResult.WHEEL_LOCKED, s.castSpell("avatar_of_steel", 1000));
        assertFalse(s.spells().knownSpells().stream().anyMatch(t -> t.key().equals("avatar_of_steel")));
        WheelFixtures.fill(pc, "P"); // 350 pkt w fioletowej: etap 1
        assertTrue(s.spells().knownSpells().stream().anyMatch(t -> t.key().equals("avatar_of_steel")));
        int mana = pc.mana();
        assertEquals(CastResult.OK, s.castSpell("avatar_of_steel", 2000));
        assertEquals(mana - 800, pc.mana());
        assertTrue(pc.wheel().avatarActive());
        assertEquals(1, pc.wheel().avatarStage());
        assertEquals(DestinyWheel.AVATAR_COOLDOWN_MS, pc.wheel().avatarCooldownMs(), "stage 1: full 2 h");
        assertEquals(CastResult.COOLDOWN, s.castSpell("avatar_of_steel", 60_000));
    }

    @Test
    void savedAvatarCooldownBlocksTheSpellAfterRelog() {
        PlayerCharacter pc = WheelFixtures.hero(Vocation.MONK, 400);
        WheelFixtures.fill(pc, "P");
        pc.wheel().decode(pc.wheel().encode() + ";ac=600000");
        GameSession s = session(pc, null, null);
        assertEquals(CastResult.COOLDOWN, s.castSpell("avatar_of_balance", 1000));
    }

    @Test
    void convictionSliceUpgradesASpell() {
        PlayerCharacter pc = WheelFixtures.hero(Vocation.KNIGHT, 400);
        SpellType fierce = SPELLS.get("fierce_berserk");
        assertEquals(fierce.mana(), WheelSpells.mana(pc, fierce));
        // grupa 5 rycerza (Fierce Berserk): pola RED_50 i BLUE_200
        assertTrue(WheelPerks.slicesOfGroup(4).contains(WheelSlice.RED_50));
        pc.wheel().add(WheelSlice.RED_50, 50, pc.wheelPoints());
        assertEquals(1, pc.wheelBonus().grade(4));
        assertEquals(fierce.mana() - 30, WheelSpells.mana(pc, fierce));
        assertTrue(WheelSpells.augment(pc, SPELLS.get("berserk")).isEmpty());
    }

    @Test
    void twinBurstsShareACooldown() {
        PlayerCharacter pc = WheelFixtures.hero(Vocation.DRUID, 400);
        WheelFixtures.fill(pc, "B");
        GameSession s = session(pc, "boar", new Position(12, 12, 0));
        assertEquals(CastResult.OK, s.castSpell("terra_burst", 1000));
        assertFalse(s.spells().cooldowns().ready(SPELLS.get("ice_burst"), 3000));
    }

    @Test
    void beamHitsTheTargetForMoreThanNothing() {
        PlayerCharacter pc = WheelFixtures.hero(Vocation.SORCERER, 400);
        WheelFixtures.fill(pc, "R");
        GameSession s = session(pc, "boar", new Position(12, 12, 0));
        s.cycleTarget(500);
        Monster m = s.combat().target();
        assertNotNull(m);
        int hp = m.health();
        assertEquals(CastResult.OK, s.castSpell("great_death_beam", 1000));
        assertTrue(m.health() < hp || m.isDead());
    }
}
