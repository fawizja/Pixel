package pl.fawizja.pixel.vocation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.magic.SpellKind;
import pl.fawizja.pixel.magic.SpellRegistry;
import pl.fawizja.pixel.magic.SpellSystem;

/** Rycerz (2026-10-23, Darek: "brakuje profesji knight"): bron, tarcza, czary exori i exura ico. */
class KnightTest {

    static final SpellRegistry SPELLS = SpellRegistry.loadDefault(TestData.ITEMS);

    @Test
    void knightStartsWithAnAxeAndAShieldAndFightsWithTheSwordSkill() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        ItemType weapon = TestData.ITEMS.get(pc.equipment().get(EquipSlot.WEAPON).itemId());
        assertEquals("hand_axe", weapon.key());
        assertNotNull(pc.equipment().get(EquipSlot.SHIELD), "knight carries a shield");
        assertEquals(Skill.SWORD, SpellSystem.weaponSkill(Vocation.KNIGHT));
        assertEquals("Elite Knight", Vocation.KNIGHT.promotedName());
    }

    @Test
    void knightHasWeaponsForEveryTierAndTibiaSpells() {
        for (int tier : new int[] {8, 20, 60, 100, 250, 350, 450, 700, 850}) {
            assertTrue(TestData.ITEMS.all().stream().anyMatch(i -> i.minLevel() == tier && i.usableBy(Vocation.KNIGHT)
                    && i.key().matches(".*(axe|sword|hammer|mace|scimitar|cleaver|blade).*")), "knight weapon at level " + tier);
        }
        assertEquals("berserk", SPELLS.byWords("exori"));
        assertEquals("brutal_strike", SPELLS.byWords("exori ico"));
        assertEquals("wound_cleansing", SPELLS.byWords("exura ico"));
        assertTrue(SPELLS.forVocation(Vocation.KNIGHT).stream().anyMatch(s -> s.kind() == SpellKind.ATTACK_AREA));
    }
}
