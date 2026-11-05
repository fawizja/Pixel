package pl.fawizja.pixel.combat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.game.Actions;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.vocation.Vocation;

/** Tryby walki jak w Tibii (TFS Player::getAttackFactor / getDefenseFactor). */
class FightModeTest {

    @Test
    void factorsFollowTheForgottenServer() {
        assertEquals(1.0, FightMode.OFFENSIVE.tfsAttackFactor());
        assertEquals(1.2, FightMode.BALANCED.tfsAttackFactor());
        assertEquals(2.0, FightMode.DEFENSIVE.tfsAttackFactor());
        assertEquals(0.5, FightMode.OFFENSIVE.tfsDefenseFactor());
        assertEquals(0.75, FightMode.BALANCED.tfsDefenseFactor());
        assertEquals(1.0, FightMode.DEFENSIVE.tfsDefenseFactor());
        // zbalansowany = punkt odniesienia naszych formul
        assertEquals(1.2, FightMode.OFFENSIVE.damageFactor(), 1e-9);
        assertEquals(1.0, FightMode.BALANCED.damageFactor(), 1e-9);
        assertEquals(0.6, FightMode.DEFENSIVE.damageFactor(), 1e-9);
        assertEquals(0.5, FightMode.DEFENSIVE.damageFactor() / FightMode.OFFENSIVE.damageFactor(), 1e-9, "defensive hits half as hard");
        assertEquals(2.0 / 3, FightMode.OFFENSIVE.defenseFactor(true), 1e-9);
        assertEquals(1.0, FightMode.BALANCED.defenseFactor(true), 1e-9);
        assertEquals(4.0 / 3, FightMode.DEFENSIVE.defenseFactor(true), 1e-9);
        for (FightMode m : FightMode.values()) assertEquals(4.0 / 3, m.defenseFactor(false), 1e-9, "full defense when not attacking");
    }

    @Test
    void cyclesOffensiveBalancedDefensive() {
        assertEquals(FightMode.BALANCED, FightMode.OFFENSIVE.next());
        assertEquals(FightMode.DEFENSIVE, FightMode.BALANCED.next());
        assertEquals(FightMode.OFFENSIVE, FightMode.DEFENSIVE.next());
        assertEquals(FightMode.BALANCED, TestData.newCharacter(Vocation.KNIGHT).fightMode(), "balanced by default");
    }

    @Test
    void weaponDamageScalesButNotTheLevelBonusNorWands() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.equipment().setDirect(EquipSlot.WEAPON, ItemStack.of(TestData.ITEMS.byKey("battle_axe").id()));
        pc.skills().set(Skill.AXE, 60, 0);
        int[] max = new int[3];
        for (FightMode m : FightMode.values()) {
            pc.setFightMode(m);
            max[m.ordinal()] = CombatFormulas.attackProfile(pc, TestData.ITEMS).maxDamage();
        }
        double bonus = pc.level() / 5.0;
        assertEquals(1.2, (max[0] - bonus) / (max[1] - bonus), 0.03);
        assertEquals(0.6, (max[2] - bonus) / (max[1] - bonus), 0.03);
        PlayerCharacter sorc = TestData.newCharacter(Vocation.SORCERER);
        sorc.equipment().setDirect(EquipSlot.WEAPON, ItemStack.of(TestData.ITEMS.byKey("wand_of_embers").id()));
        int wand = CombatFormulas.attackProfile(sorc, TestData.ITEMS).maxDamage();
        for (FightMode m : FightMode.values()) {
            sorc.setFightMode(m);
            assertEquals(wand, CombatFormulas.attackProfile(sorc, TestData.ITEMS).maxDamage(), "wands ignore the fight mode");
        }
    }

    private static long taken(PlayerCharacter pc, FightMode m, boolean attacking) {
        Random rng = new Random(5);
        long sum = 0;
        for (int i = 0; i < 3000; i++) sum += CombatFormulas.mitigateOnPlayer(pc, 150, pl.fawizja.pixel.combat.DamageType.PHYSICAL, m, attacking, rng);
        return sum;
    }

    @Test
    void defenseIsWeakestOffensiveAndStrongestDefensive() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.equipment().setDirect(EquipSlot.SHIELD, ItemStack.of(TestData.ITEMS.byKey("bone_shield").id()));
        pc.skills().set(Skill.SHIELDING, 60, 0);
        long off = taken(pc, FightMode.OFFENSIVE, true), bal = taken(pc, FightMode.BALANCED, true), def = taken(pc, FightMode.DEFENSIVE, true);
        assertTrue(off > bal && bal > def, off + " > " + bal + " > " + def);
        // bez wlasnego ataku TFS daje pelna obrone w kazdym trybie (bez tarczy - bez dodatkowego bloku)
        PlayerCharacter bare = TestData.newCharacter(Vocation.KNIGHT);
        bare.equipment().setDirect(EquipSlot.SHIELD, null);
        assertEquals(taken(bare, FightMode.OFFENSIVE, false), taken(bare, FightMode.BALANCED, false));
        assertEquals(taken(bare, FightMode.BALANCED, false), taken(bare, FightMode.DEFENSIVE, false));
    }

    @Test
    void actionsSetTheModeAndItIsSaved() throws Exception {
        GameSession s = new GameSession(TestData.ITEMS, TestData.varden(), TestData.newCharacter(Vocation.KNIGHT));
        assertTrue(Actions.apply(s, "fightmode:OFFENSIVE", 0));
        assertEquals(FightMode.OFFENSIVE, s.character().fightMode());
        assertTrue(Actions.apply(s, "fightmode", 0));
        assertEquals(FightMode.BALANCED, s.character().fightMode(), "button cycles");
        assertTrue(Actions.apply(s, "defensive", 0), "old client action still works");
        assertEquals(FightMode.DEFENSIVE, s.character().fightMode());
        assertTrue(Actions.apply(s, "fightmode:nonsense", 0));
        assertEquals(FightMode.DEFENSIVE, s.character().fightMode(), "unknown mode is ignored");
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        for (FightMode m : FightMode.values()) {
            s.character().setFightMode(m);
            assertEquals(m, codec.decode(codec.encode(s.character())).fightMode());
        }
        // stary zapis: "D" (Tarcza) = defensywny, brak flagi = zbalansowany
        assertEquals(FightMode.DEFENSIVE, FightMode.fromFlags("RD"));
        assertEquals(FightMode.BALANCED, FightMode.fromFlags("RHM"));
        assertEquals(FightMode.OFFENSIVE, FightMode.fromFlags("OM"));
    }
}
