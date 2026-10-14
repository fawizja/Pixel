package pl.fawizja.pixel.character;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.config.StartingConfig;
import pl.fawizja.pixel.economy.Money;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

class CharacterCreationTest {

    @ParameterizedTest
    @EnumSource(Vocation.class)
    void newCharacterStartsAtLevel8With10kGoldInBackpackNotBank(Vocation v) {
        PlayerCharacter pc = TestData.newCharacter(v);
        assertEquals(8, pc.level());
        assertEquals(Balance.experienceForLevel(8), pc.experience());
        assertEquals(10_000L, Money.totalValue(pc.backpack()));
        assertEquals(0L, pc.bank().balance());
        assertEquals(pc.maxHealth(), pc.health());
        assertEquals(pc.maxMana(), pc.mana());
        assertEquals("varden", pc.homeTown());
        assertNotNull(pc.equipment().get(EquipSlot.WEAPON), "weapon for " + v);
        assertNotNull(pc.equipment().get(EquipSlot.ARMOR));
        assertTrue(pc.backpack().freeSlots() > 10, "starter kit must leave room for loot");
        // kazda bron startowa musi byc uzywalna przez dana profesje
        assertTrue(TestData.ITEMS.get(pc.equipment().get(EquipSlot.WEAPON).itemId()).usableBy(v));
    }

    @Test
    void startingSettingsAreConfigurable() {
        Properties p = new Properties();
        p.setProperty("start.level", "20");
        p.setProperty("start.gold.backpack", "250");
        p.setProperty("start.gold.bank", "5000");
        StartingConfig cfg = StartingConfig.fromProperties(p);
        PlayerCharacter pc = new CharacterFactory(cfg, TestData.ITEMS).create("Configured", Vocation.MONK, new Position(1, 1, 0));
        assertEquals(20, pc.level());
        assertEquals(250, Money.totalValue(pc.backpack()));
        assertEquals(5000, pc.bank().balance());
    }

    @Test
    void invalidNamesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> TestData.newCharacterNamed("ab"));
        assertThrows(IllegalArgumentException.class, () -> TestData.newCharacterNamed("Bad<Name>"));
        assertThrows(IllegalArgumentException.class, () -> TestData.newCharacterNamed("x".repeat(30)));
    }

    @ParameterizedTest
    @EnumSource(Vocation.class)
    void levelingFrom8To1000IsConsistent(Vocation v) {
        PlayerCharacter pc = TestData.newCharacter(v);
        int levels = 0;
        // zdobywaj doswiadczenie duzymi porcjami az do maksimum
        for (int i = 0; i < 10_000 && pc.level() < Balance.MAX_LEVEL; i++) {
            levels += pc.addExperience(5_000_000L);
            assertTrue(pc.health() <= pc.maxHealth());
            assertTrue(pc.mana() <= pc.maxMana());
        }
        assertEquals(Balance.MAX_LEVEL, pc.level());
        assertEquals(Balance.MAX_LEVEL - 8, levels);
        assertEquals(Balance.maxExperience(), pc.experience());
        assertEquals(0, pc.addExperience(Long.MAX_VALUE));
        assertEquals(Balance.maxHealth(v, 1000), pc.maxHealth());
    }

    @Test
    void damageHealAndRegeneration() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        assertEquals(100, pc.damage(100));
        assertEquals(pc.maxHealth() - 100, pc.health());
        assertTrue(pc.spendMana(50));
        assertFalse(pc.spendMana(10_000));
        pc.tickRegeneration(Balance.REGEN_TICK_SECONDS * 1000L * 3);
        assertEquals(pc.maxHealth() - 100 + 3 * Balance.healthRegenPerTick(Vocation.DRUID, 8), pc.health());
        assertEquals(pc.maxHealth() - pc.health(), pc.heal(1_000_000));
        assertEquals(pc.maxHealth(), pc.health());
        pc.damage(Integer.MAX_VALUE);
        assertTrue(pc.isDead());
        assertEquals(0, pc.heal(10));
    }
}
