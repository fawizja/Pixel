package pl.fawizja.pixel.settings;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SpellBarTest {

    static final String[] AUTO = {"mend", "ember_bolt", null, "glow"};

    @Test
    void emptySlotsStayAutomaticAndAssignedSpellsWinWhileKnown() {
        SpellBar bar = new SpellBar();
        assertArrayEquals(AUTO, bar.resolve(AUTO, Set.of("mend", "ember_bolt", "glow")));
        bar.assign(2, "spark_bolt");
        bar.assign(3, "mend");
        assertArrayEquals(new String[] {"mend", "ember_bolt", null, "mend"}, bar.resolve(AUTO, Set.of("mend", "ember_bolt", "glow")),
                "spark_bolt not learned yet: slot stays automatic");
        assertArrayEquals(new String[] {"mend", "ember_bolt", "spark_bolt", "mend"},
                bar.resolve(AUTO, Set.of("mend", "ember_bolt", "glow", "spark_bolt")));
        bar.assign(3, "mend");
        assertNull(bar.custom(3), "assigning the same spell again returns to automatic");
        bar.assign(7, "glow");
        assertTrue(bar.isCustom());
    }

    @Test
    void layoutSurvivesSavingAndIgnoresGarbage() {
        SpellBar bar = new SpellBar();
        bar.assign(0, "frost_bolt");
        bar.assign(2, "frost_nova");
        assertEquals("frost_bolt,-,frost_nova,-", bar.encode());
        SpellBar back = SpellBar.decode(bar.encode());
        assertEquals("frost_nova", back.custom(2));
        assertEquals(bar.encode(), back.encode());
        SpellBar junk = SpellBar.decode("DROP TABLE;--,,x y,ok_key,extra");
        assertEquals("-,-,-,ok_key", junk.encode());
        assertFalse(SpellBar.decode(null).isCustom());
    }
}
