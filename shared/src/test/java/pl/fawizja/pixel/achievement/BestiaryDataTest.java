package pl.fawizja.pixel.achievement;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.vocation.Vocation;

/** Dane bestiariusza: kazdy zwykly potwor ma odpowiednik z Tibii, progi i punkty jak w Canary. */
class BestiaryDataTest {
    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);

    @Test
    void everyNonBossMonsterHasAnEntryAndBossesHaveNone() {
        List<String> bad = new ArrayList<>();
        for (MonsterType t : REG.all()) {
            BestiaryEntry e = BestiaryData.get(t.key());
            if (t.boss() && e != null) bad.add(t.key() + ": boss with bestiary entry");
            if (!t.boss() && e == null) bad.add(t.key() + ": no bestiary entry");
            if (!t.boss() && !BestiaryClasses.known(t.race())) bad.add(t.key() + ": unknown class " + t.race());
        }
        for (String k : BestiaryData.all().keySet()) if (!REG.contains(k)) bad.add(k + ": entry without monster");
        assertTrue(bad.isEmpty(), String.join("\n", bad));
    }

    @Test
    void thresholdsAndCharmPointsMatchTibia() {
        // Canary monster.Bestiary: Wolf 1*, Boar 2*, Dragon Lord 3*, Demon 4*, Brachiodemon 5*
        assertEquals(BestiaryDifficulty.TRIVIAL, BestiaryData.get("wolf").difficulty());
        assertEquals(250, BestiaryData.get("wolf").complete());
        assertEquals(5, BestiaryData.get("wolf").charmPoints());
        BestiaryEntry boar = BestiaryData.get("boar");
        assertEquals(25, boar.secondStage());
        assertEquals(250, boar.thirdStage());
        assertEquals(500, boar.complete());
        assertEquals(15, boar.charmPoints());
        assertEquals(25, BestiaryData.get("magma_drake").charmPoints());
        assertEquals(2500, BestiaryData.get("abyssal_demon").complete());
        assertEquals(100, BestiaryData.get("abyss_lord").charmPoints());
        assertEquals(5000, BestiaryData.get("abyss_lord").complete());
        // stage boundaries (Canary getKillStatus)
        assertEquals(0, boar.stage(0));
        assertEquals(1, boar.stage(24));
        assertEquals(2, boar.stage(25));
        assertEquals(3, boar.stage(250));
        assertEquals(4, boar.stage(500));
        // bardzo rzadkie (Occurrence 3): 2 / 3 / 5 zabic, 50 pkt dla 3 gwiazdek
        BestiaryEntry rare = new BestiaryEntry("x", "Yeti", BestiaryDifficulty.MEDIUM, 3);
        assertEquals(5, rare.complete());
        assertEquals(50, rare.charmPoints());
    }

    @Test
    void classListAndSpeciesRows() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        for (int i = 0; i < 250; i++) pc.bestiary().record("wolf", false);
        List<String[]> classes = BestiaryView.classRows(pc, REG.all());
        String[] mammals = null;
        for (String[] r : classes) if (r[0].equals("Ssaki")) mammals = r;
        assertNotNull(mammals);
        assertTrue(mammals[1].contains("ukończone 1"), mammals[1]);
        assertEquals("bestclass:Mammal", mammals[3]);
        List<String[]> species = BestiaryView.speciesRows(pc, REG.all(), "Mammal");
        boolean wolf = false;
        for (String[] r : species) if (r[0].equals("Wilk")) wolf = r[1].contains("ukończony");
        assertTrue(wolf);
        List<String[]> paged = BestiaryView.page(species, 0, 4, "p:");
        assertEquals(4, paged.size());
        assertEquals("p:1", paged.get(3)[3]);
    }
}
