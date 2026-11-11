package pl.fawizja.pixel.character;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.config.StartingConfig;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Town;
import pl.fawizja.pixel.world.WorldMap;

/** Wybor miasta startowego przy tworzeniu postaci (jak w Tibii): kazde miasto swiata, zle = miasto domyslne. */
class StartingTownTest {

    private final WorldMap world = TestData.world();
    private final StartingConfig cfg = StartingConfig.loadDefault();
    private final CharacterFactory factory = new CharacterFactory(cfg, TestData.ITEMS);

    @Test
    void everyTownOfTheWorldIsAStartingTownWithAPolishDescription() {
        List<StartingTowns.Info> towns = StartingTowns.list(world);
        // ukryte miasto krasnoludow (jak Kazordoon) ma swiatynie, ale gry sie w nim nie zaczyna
        assertEquals(world.towns().size() - 1, towns.size());
        assertFalse(world.town("Durnkar").starting());
        assertNull(StartingTowns.find(world, "durnkar"));
        assertFalse(StartingTowns.isValid(world, "Durnkar"));
        assertEquals("varden", StartingTowns.resolve(world, "Durnkar", "varden"));
        for (String k : new String[] {"varden", "frostholm", "selvaran", "qasirah", "valmir", "nerys"}) {
            StartingTowns.Info i = StartingTowns.find(world, k);
            assertNotNull(i, k);
            assertFalse(i.climate().isBlank(), k);
            assertTrue(i.description().length() > 30 && i.description().length() < 110, k + ": " + i.description());
        }
        assertEquals("varden", cfg.startTown(), "default town unchanged");
        assertNotNull(StartingTowns.find(world, cfg.startTown()));
    }

    @Test
    void aCharacterCreatedInEachTownLivesThereAndStartsInItsTemple() {
        for (Town t : world.towns()) {
            if (!t.starting()) continue;
            for (int level : new int[] {cfg.startLevel(), cfg.offlineStartLevel()}) {
                PlayerCharacter pc = factory.createIn(world, "Nowy", Vocation.DRUID, t.name(), level);
                assertEquals(t.name().toLowerCase(Locale.ROOT), pc.homeTown());
                assertEquals(t.temple(), pc.position());
                assertEquals(t.temple(), world.templeOf(pc.homeTown()), "respawn in the same temple");
                assertEquals(level, pc.level());
            }
        }
        // wielkosc liter i spacje nie maja znaczenia
        assertEquals("frostholm", factory.createIn(world, "Nowy", Vocation.KNIGHT, "  FrostHolm ", 8).homeTown());
    }

    @Test
    void anUnknownOrMissingTownFallsBackToTheDefault() {
        for (String bad : new String[] {null, "", "  ", "Atlantyda", "varden2", "-"}) {
            PlayerCharacter pc = factory.createIn(world, "Nowy", Vocation.PALADIN, bad, 8);
            assertEquals(cfg.startTown(), pc.homeTown(), String.valueOf(bad));
            assertEquals(world.templeOf(cfg.startTown()), pc.position());
            assertFalse(StartingTowns.isValid(world, bad));
        }
        assertEquals("varden", StartingTowns.resolve(world, "Atlantyda", "varden"));
        assertEquals("nerys", StartingTowns.resolve(world, "Nerys", "varden"));
    }

    @Test
    void theOldCreateKeepsTheDefaultTownAndTheChosenTownSurvivesSaving() throws Exception {
        PlayerCharacter old = factory.create("Stary", Vocation.MONK, world.templeOf("varden"));
        assertEquals("varden", old.homeTown());
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter nerys = factory.createIn(world, "Wyspiarz", Vocation.MONK, "Nerys", 8);
        PlayerCharacter back = codec.decode(codec.encode(nerys));
        assertEquals("nerys", back.homeTown());
        assertEquals(world.town("Nerys").temple(), back.position());
        // zapis sprzed wyboru miasta (bez linii hometown) -> varden: SaveTest
    }
}
