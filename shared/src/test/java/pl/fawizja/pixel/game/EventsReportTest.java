package pl.fawizja.pixel.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.item.Enchantments;
import pl.fawizja.pixel.vocation.Vocation;

class EventsReportTest {
    @Test
    void eventsWindowSummarisesTheDayForTheCharacter() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        GameSession s = new GameSession(TestData.ITEMS, TestData.world(), pc);
        s.update(1000, null); // nagroda za wejscie
        List<String[]> rows = EventsReport.rows(s, null);
        assertEquals("tylko w grze na serwerze", value(rows, "Inwazja"));
        assertEquals("weź u przewodnika w mieście", value(rows, "Zadanie dnia"));
        assertTrue(value(rows, "Seria logowania").startsWith("dzień 1 z 7"));
        assertEquals(String.format("%,d gp", pl.fawizja.pixel.character.LevelRewards.gold(10)), value(rows, "Nagroda za poziom 10"),
                "the next milestone above level 8");
        assertTrue(value(rows, "Zaklęcia").startsWith("brak"));

        pc.enchantments().extend(Enchantments.Kind.CRIT);
        rows = EventsReport.rows(s, "następna za 47 min");
        assertEquals("następna za 47 min", value(rows, "Inwazja"));
        assertEquals("Cios krytyczny 1 h 00 min", value(rows, "Zaklęcia"));
    }

    private static String value(List<String[]> rows, String label) {
        for (String[] r : rows) if (r[0].equals(label)) return r[1];
        fail("no row " + label);
        return null;
    }
}
