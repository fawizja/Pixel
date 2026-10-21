package pl.fawizja.pixel.settings;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SpellBarTest {

    static final String[] AUTO = {"cast:mend", "cast:ember_bolt", null, "cast:glow", "potion_hp", "potion_mp", null, null, null, null};

    @Test
    void emptySlotsStayAutomaticAndAssignedSpellsWinWhileKnown() {
        SpellBar bar = new SpellBar();
        Set<String> known = Set.of("mend", "ember_bolt", "glow");
        assertArrayEquals(AUTO, bar.resolve(AUTO, known));
        bar.assign(2, "cast:spark_bolt");
        bar.assign(3, "cast:mend");
        String[] r = bar.resolve(AUTO, known);
        assertNull(r[2], "spark_bolt not learned yet: slot stays automatic");
        assertEquals("cast:mend", r[3]);
        assertEquals("cast:spark_bolt", bar.resolve(AUTO, Set.of("mend", "spark_bolt"))[2]);
        bar.assign(3, "cast:mend");
        assertNull(bar.custom(3), "assigning the same action again returns to automatic");
        bar.assign(17, "cast:glow");
        assertTrue(bar.isCustom());
    }

    @Test
    void slotsHoldPotionsRunesAndCanBeCleared() {
        SpellBar bar = new SpellBar();
        bar.set(4, "item:great_health_potion");
        bar.set(9, "item:sudden_death_rune");
        bar.set(1, SpellBar.NONE);
        String[] r = bar.resolve(AUTO, Set.of());
        assertEquals("item:great_health_potion", r[4]);
        assertEquals("item:sudden_death_rune", r[9], "items do not depend on known spells");
        assertNull(r[1], "cleared slot ignores the automatic choice");
        bar.set(4, "item:great_health_potion");
        assertEquals("item:great_health_potion", bar.custom(4), "set never toggles");
        bar.set(5, "drop table");
        assertNull(bar.custom(5));
    }

    @Test
    void layoutSurvivesSavingAndReadsOldFormat() {
        SpellBar bar = new SpellBar();
        bar.assign(0, "cast:frost_bolt");
        bar.set(6, "item:mana_potion");
        bar.set(7, SpellBar.NONE);
        assertEquals("cast:frost_bolt,-,-,-,-,-,item:mana_potion,none,-,-", bar.encode());
        SpellBar back = SpellBar.decode(bar.encode());
        assertEquals(bar.encode(), back.encode());
        SpellBar old = SpellBar.decode("frost_bolt,-,frost_nova,-");
        assertEquals("cast:frost_nova", old.custom(2), "old saves held bare spell keys");
        SpellBar junk = SpellBar.decode("DROP TABLE;--,,x y,ok_key,item:BAD,extra");
        assertEquals("-,-,-,cast:ok_key,-,cast:extra,-,-,-,-", junk.encode());
        assertFalse(SpellBar.decode(null).isCustom());
    }
}
