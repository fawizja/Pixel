package pl.fawizja.pixel.quest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.vocation.Vocation;

class QuestTrackerTest {
    @Test
    void showsActiveQuestProgressAndTheDailyTask() {
        QuestSystem quests = new QuestSystem(QuestRegistry.loadDefault(TestData.ITEMS, MonsterRegistry.loadDefault(TestData.ITEMS)), TestData.ITEMS);
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        assertEquals(List.of(), QuestTracker.lines(pc, quests, 100, "Wilk"));

        QuestType kill = null;
        for (QuestType q : quests.registry().all()) if (q.kind() == QuestKind.KILL) { kill = q; break; }
        assertNotNull(kill);
        pc.quests().restore(kill.key(), false, 2);
        assertEquals(List.of("Zadanie: " + kill.name() + " 2/" + kill.amount()), QuestTracker.lines(pc, quests, 100, "Wilk"));
        pc.quests().restore(kill.key(), false, kill.amount());
        assertTrue(QuestTracker.lines(pc, quests, 100, "Wilk").get(0).endsWith("gotowe, wróć po nagrodę"));
        pc.hunt().restore("wolf;7;0");
        assertEquals("Zlecenie: Wilk 7/50", QuestTracker.lines(pc, quests, 100, "x", "Wilk").get(1), "the hunting task fills the free line");
    }
}
