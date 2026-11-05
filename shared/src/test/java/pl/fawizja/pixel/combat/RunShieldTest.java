package pl.fawizja.pixel.combat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.movement.ChaseMode;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

/** Przyciski Bieg (pogon za celem) i tryb walki defensywny (wiecej obrony z tarcza). */
class RunShieldTest {

    private static int taken(PlayerCharacter pc, FightMode mode) {
        Random rng = new Random(11);
        int sum = 0;
        for (int i = 0; i < 2000; i++) sum += CombatFormulas.mitigateOnPlayer(pc, 120, DamageType.PHYSICAL, mode, true, rng);
        return sum;
    }

    @Test
    void defensiveStanceGivesMoreWithAShield() {
        PlayerCharacter bare = TestData.newCharacter(Vocation.KNIGHT);
        bare.equipment().setDirect(EquipSlot.SHIELD, null);
        PlayerCharacter shielded = TestData.newCharacter(Vocation.KNIGHT);
        int shield = TestData.ITEMS.byKey("obsidian_shield").id();
        shielded.equipment().setDirect(EquipSlot.SHIELD, new ItemStack(shield, 1));
        assertTrue(shielded.equipment().shieldDefense() > 0);
        assertEquals(0, bare.equipment().shieldDefense());

        double gainBare = 1 - taken(bare, FightMode.DEFENSIVE) / (double) taken(bare, FightMode.BALANCED);
        double gainShield = 1 - taken(shielded, FightMode.DEFENSIVE) / (double) taken(shielded, FightMode.BALANCED);
        assertTrue(gainBare >= 0, "defensive never hurts: " + gainBare);
        assertTrue(gainShield > gainBare + 0.1, "defensive with a shield blocks more: " + gainShield + " vs " + gainBare);
        // zywioly: jak w TFS tryb walki ich nie zmienia (tarcza blokuje tylko fizyczne)
        Random rng = new Random(3);
        for (FightMode m : FightMode.values()) {
            assertEquals(100, CombatFormulas.mitigateOnPlayer(shielded, 100, DamageType.FIRE, m, true, rng));
            assertEquals(100, CombatFormulas.mitigateOnPlayer(bare, 100, DamageType.FIRE, m, true, rng));
        }
    }

    @Test
    void runModeChasesTheSelectedMonsterUntilNextToIt() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.setPosition(new Position(40, 6, 0));
        Monster m = new Monster(1, MonsterRegistry.loadDefault(TestData.ITEMS).get("boar"),
                new SpawnPoint("boar", new Position(45, 6, 0), 0, 1, 60), new Position(45, 6, 0));
        pc.setRunning(false);
        assertFalse(ChaseMode.wants(pc, m), "run off: no chase");
        pc.setRunning(true);
        assertTrue(ChaseMode.wants(pc, m), "run on: walk to the target");
        assertFalse(ChaseMode.wants(pc, null));
        pc.setPosition(new Position(44, 7, 0));
        assertFalse(ChaseMode.wants(pc, m), "already next to it");
        pc.setPosition(new Position(45 - ChaseMode.RANGE - 1, 6, 0));
        assertFalse(ChaseMode.wants(pc, m), "too far away");
        pc.setPosition(new Position(43, 6, 1));
        assertFalse(ChaseMode.wants(pc, m), "other floor");
    }
}
