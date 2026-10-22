package pl.fawizja.pixel.character;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.config.StartingConfig;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

class OfflineStartTest {
    @Test
    void offlineCharacterStartsAtLevel300WithFullHpManaAndMatchingSkills() {
        StartingConfig cfg = StartingConfig.loadDefault();
        assertEquals(8, cfg.startLevel(), "server characters still start at 8");
        assertEquals(300, cfg.offlineStartLevel());
        CharacterFactory f = new CharacterFactory(cfg, TestData.ITEMS);
        PlayerCharacter sorc = f.create("Ola", Vocation.SORCERER, new Position(10, 10, 0), cfg.offlineStartLevel());
        assertEquals(300, sorc.level());
        assertEquals(Balance.experienceForLevel(300), sorc.experience());
        assertEquals(sorc.maxHealth(), sorc.health());
        assertEquals(sorc.maxMana(), sorc.mana());
        assertTrue(sorc.maxMana() > f.create("Ula", Vocation.SORCERER, new Position(10, 10, 0)).maxMana() * 10,
                "mana grows with level");
        assertTrue(sorc.skills().level(Skill.MAGIC) >= 60, "mage magic level fits level 300");
        PlayerCharacter pal = f.create("Iwo", Vocation.PALADIN, new Position(10, 10, 0), 300);
        assertTrue(pal.skills().level(Skill.DISTANCE) >= 80);
        assertTrue(pal.skills().level(Skill.MAGIC) < sorc.skills().level(Skill.MAGIC));
        PlayerCharacter low = f.create("Jan", Vocation.MONK, new Position(10, 10, 0));
        assertEquals(Skills.START_SKILL, low.skills().level(Skill.FIST), "normal start keeps starting skills");
    }

    @Test
    void offlineLevelDefaultsToTheNormalStartAndIsValidated() {
        Properties p = new Properties();
        p.setProperty("start.level", "8");
        assertEquals(8, StartingConfig.fromProperties(p).offlineStartLevel());
        p.setProperty("start.level.offline", "2000");
        assertThrows(IllegalArgumentException.class, () -> StartingConfig.fromProperties(p));
    }
}
