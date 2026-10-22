package pl.fawizja.pixel.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterFactory;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

/** Zalozenia serwera z briefu: kazda profesja startuje na poziomie 300 z 10 000 000 gp w banku. */
class StartingConfigFileTest {
    @Test
    void shippedConfigStartsAtLevel300WithTenMillionInBank() throws Exception {
        Properties p = new Properties();
        try (InputStream in = StartingConfig.class.getResourceAsStream("/data/starting.properties")) {
            p.load(in);
        }
        StartingConfig c = StartingConfig.fromProperties(p);
        assertEquals(300, c.startLevel());
        assertEquals(300, c.offlineStartLevel());
        assertEquals(10_000_000L, c.startBankBalance());
    }

    @Test
    void serverCharacterAtLevel300GetsMatchingSkillsAndBankBalance() throws Exception {
        Properties p = new Properties();
        try (InputStream in = StartingConfig.class.getResourceAsStream("/data/starting.properties")) {
            p.load(in);
        }
        CharacterFactory f = new CharacterFactory(StartingConfig.fromProperties(p), TestData.ITEMS);
        PlayerCharacter pal = f.create("Test", Vocation.PALADIN, new Position(79, 80, 0));
        assertEquals(300, pal.level());
        assertEquals(10_000_000L, pal.bank().balance());
        assertTrue(pal.skills().level(Skill.DISTANCE) >= 80, "paladin 300 has distance fitting the level");
        assertEquals(pal.maxHealth(), pal.health());
    }
}
