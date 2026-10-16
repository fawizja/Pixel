package pl.fawizja.pixel.daily;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.CorpseTestAccess;
import pl.fawizja.pixel.game.EventsReport;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.vocation.Vocation;

class WeeklyBossTest {
    static final MonsterRegistry MONSTERS = MonsterRegistry.loadDefault(TestData.ITEMS);

    @Test
    void onlyBossesAreChosenAndTheChoiceChangesWeekly() {
        MonsterType wolf = MONSTERS.get("wolf");
        MonsterType grim = MONSTERS.get("grimtooth");
        assertNull(WeeklyBoss.pick(5, List.of(wolf)));
        assertEquals(grim, WeeklyBoss.pick(5, List.of(wolf, grim, grim.eliteVariant())));
        assertEquals(0, WeeklyBoss.week(6));
        assertEquals(1, WeeklyBoss.week(7));
        assertEquals(-1, WeeklyBoss.week(-1));
        assertTrue(WeeklyBoss.rewardGold(grim) >= WeeklyBoss.MIN_GOLD);
        assertEquals(0, WeeklyBoss.rewardGold(grim) % 100);
    }

    @Test
    void firstKillOfTheWeekPaysOnceAndIsSaved() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        long bank = pc.bank().balance();
        GameSession s = CorpseTestAccess.killBoss(pc);
        MonsterType grim = MONSTERS.get("grimtooth");
        long week = WeeklyBoss.week(s.today());
        assertEquals(grim.key(), s.weeklyBoss().key());
        assertEquals(bank + WeeklyBoss.rewardGold(grim), pc.bank().balance());
        assertEquals(week, pc.weeklyBossWeek());
        String row = EventsReport.rows(s, null).stream().filter(r -> r[0].equals("Boss tygodnia")).findFirst().orElseThrow()[1];
        assertTrue(row.contains("nagroda odebrana"), row);

        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        assertEquals(week, codec.decode(codec.encode(pc)).weeklyBossWeek());

        PlayerCharacter again = TestData.newCharacter(Vocation.PALADIN);
        again.setWeeklyBossWeek(WeeklyBoss.week(java.time.LocalDate.now().toEpochDay()));
        long before = again.bank().balance();
        CorpseTestAccess.killBoss(again);
        assertEquals(before, again.bank().balance(), "already rewarded this week");
    }
}
