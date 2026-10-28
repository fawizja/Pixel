package pl.fawizja.pixel.game;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.vocation.Vocation;

/** Etap 11: zegar swiata w sesji, noc i jej wplyw na potwory. */
class DayNightTest {

    /** Godzina gry = 15 min czasu rzeczywistego (doba = 6 h: 3 h dnia i 3 h nocy). */
    static final long GAME_HOUR_MS = pl.fawizja.pixel.time.WorldClock.GAME_HOUR_MS;

    @Test
    void sessionStartsAtNineAndNightFallsAtSix() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        GameSession s = new GameSession(TestData.ITEMS, TestData.varden(), pc);
        assertEquals(9 * 60, s.clock().minuteOfDay(0));
        assertFalse(s.isNight(0));
        long nightAt = 9 * GAME_HOUR_MS; // 18:00
        assertFalse(s.isNight(nightAt - 60_000));
        assertTrue(s.isNight(nightAt));
        assertTrue(s.isNight(nightAt + 10 * GAME_HOUR_MS + GAME_HOUR_MS / 2), "04:30 is still night");
        assertFalse(s.isNight(nightAt + 11 * GAME_HOUR_MS), "05:00 is dawn");
    }

    @Test
    void nightfallIsAnnouncedAndMonstersAreTold() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        GameSession s = new GameSession(TestData.ITEMS, TestData.varden(), pc);
        long t = 9 * GAME_HOUR_MS - 2_000;
        s.update(t, null);
        assertFalse(s.monsters().isNight());
        s.update(t + 3_000, null);
        assertTrue(s.monsters().isNight());
        assertTrue(s.log().latest(3).stream().anyMatch(e -> e.text().startsWith("Zapada noc")));
        s.update(t + 11 * GAME_HOUR_MS + 3_000, null);
        assertFalse(s.monsters().isNight());
        assertTrue(s.log().latest(3).stream().anyMatch(e -> e.text().equals("Wstaje dzień.")));
    }

    @Test
    void floorWarningOnlyCountsNearbySpawnsInTheBigWorld() throws Exception {
        pl.fawizja.pixel.world.WorldMap world = TestData.world();
        pl.fawizja.pixel.monster.MonsterRegistry reg = pl.fawizja.pixel.monster.MonsterRegistry.loadDefault(TestData.ITEMS);
        pl.fawizja.pixel.monster.MonsterManager mm = new pl.fawizja.pixel.monster.MonsterManager(world, reg,
                pl.fawizja.pixel.monster.SpawnTable.load(pl.fawizja.pixel.world.WorldLayout.WORLD_MAP, world, reg),
                new java.util.Random(3));
        assertTrue(mm.maxMonsterLevelNear(world.spawn(), GameSession.DANGER_RADIUS) <= 15,
                "Varden surface is calm although the north and the desert share floor 0");
        assertTrue(mm.maxMonsterLevelNear(new pl.fawizja.pixel.world.Position(420, 100, -5), GameSession.DANGER_RADIUS) >= 150);
    }
}
