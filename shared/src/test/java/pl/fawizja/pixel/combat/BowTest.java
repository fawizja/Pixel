package pl.fawizja.pixel.combat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.vocation.Vocation;

/** Luki i kusze paladyna z paczki OTSP (2026-10-24): strzelaja z dystansu bez amunicji, sa na kazdym progu. */
class BowTest {

    @Test
    void bowShootsWithDistanceAndIgnoresSpearsInTheAmmoSlot() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        ItemType bow = TestData.ITEMS.byKey("hunting_bow");
        pc.equipment().setDirect(EquipSlot.WEAPON, new ItemStack(bow.id(), 1));
        pc.equipment().setDirect(EquipSlot.AMMO, new ItemStack(TestData.ITEMS.byKey("spear").id(), 50));
        AttackProfile p = CombatFormulas.attackProfile(pc, TestData.ITEMS);
        assertEquals(Skill.DISTANCE, p.skill());
        assertEquals(7, p.range());
        assertNull(CombatFormulas.ammunition(pc, TestData.ITEMS), "a bow does not throw spears");
    }

    @Test
    void paladinHasABowForEveryTier() {
        for (int tier : new int[] {0, 15, 25, 40, 60, 100, 150, 250, 350, 450, 550, 700, 850}) {
            assertTrue(TestData.ITEMS.all().stream().anyMatch(i -> i.minLevel() == tier && i.usableBy(Vocation.PALADIN)
                    && i.key().endsWith("bow") && i.range() == 7), "paladin bow at level " + tier);
        }
    }
}
