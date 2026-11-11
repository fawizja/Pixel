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

/**
 * Wybor miasta startowego przy tworzeniu postaci (jak w Tibii): tylko trzy miasta kontynentu glownego (Varden, Frostholm,
 * Qasirah); miasta premium (Valmir, Selvaran, Nerys) i Durnkar nie sa do wyboru, zle = miasto domyslne.
 */
class StartingTownTest {

    private final WorldMap world = TestData.world();
    private final StartingConfig cfg = StartingConfig.loadDefault();
    private final CharacterFactory factory = new CharacterFactory(cfg, TestData.ITEMS);

    @Test
    void exactlyTheThreeTownsOfTheMainContinentAreStartingTownsWithAPolishDescription() {
        List<StartingTowns.Info> towns = StartingTowns.list(world);
        assertEquals(3, towns.size());
        assertEquals(java.util.Set.of("varden", "frostholm", "qasirah"),
                towns.stream().map(i -> i.name().toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet()));
        // ukryte miasto krasnoludow (jak Kazordoon) ma swiatynie, ale gry sie w nim nie zaczyna; miasta premium tez nie
        for (String no : new String[] {"Durnkar", "Valmir", "Selvaran", "Nerys"}) {
            assertFalse(world.town(no).starting(), no);
            assertNull(StartingTowns.find(world, no.toLowerCase(Locale.ROOT)), no);
            assertFalse(StartingTowns.isValid(world, no), no);
            assertEquals("varden", StartingTowns.resolve(world, no, "varden"), no);
        }
        for (String k : new String[] {"varden", "frostholm", "qasirah"}) {
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
        assertEquals("qasirah", StartingTowns.resolve(world, "Qasirah", "varden"));
        assertEquals("varden", StartingTowns.resolve(world, "Nerys", "varden"), "premium towns are not for new characters");
    }

    @Test
    void theOldCreateKeepsTheDefaultTownAndTheChosenTownSurvivesSaving() throws Exception {
        PlayerCharacter old = factory.create("Stary", Vocation.MONK, world.templeOf("varden"));
        assertEquals("varden", old.homeTown());
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter qasirah = factory.createIn(world, "Pustynnik", Vocation.MONK, "Qasirah", 8);
        PlayerCharacter back = codec.decode(codec.encode(qasirah));
        assertEquals("qasirah", back.homeTown());
        assertEquals(world.town("Qasirah").temple(), back.position());
        // postac mieszkajaca w miescie premium (wykupiony dom) zachowuje dom po zapisie
        PlayerCharacter islander = factory.create("Wyspiarz", Vocation.MONK, world.templeOf("varden"));
        islander.setHomeTown("nerys");
        assertEquals("nerys", codec.decode(codec.encode(islander)).homeTown());
        // zapis sprzed wyboru miasta (bez linii hometown) -> varden: SaveTest
    }
}
