package pl.fawizja.pixel.monster;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Silne potwory (poziom >= 100 i bossy) rzucaja rozne czary: w jeden cel i obszarowe, jak w Tibii/Canary. */
class StrongMonsterSpellsTest {

    static final MonsterRegistry REG = MonsterAiTest.REG;

    @Test
    void everyStrongMonsterHasASingleTargetAndAnAreaSpell() {
        MonsterSpells spells = MonsterSpells.defaults();
        int checked = 0;
        for (MonsterType t : REG.all()) {
            if (t.level() < 100 && !t.boss()) continue;
            boolean bolt = false, area = false;
            for (MonsterSpell s : spells.of(t)) {
                switch (s.mode()) {
                    case BOLT: bolt = true; break;
                    case WAVE: case BALL: case SELF: area = true; break;
                    default:
                }
            }
            String k = t.key();
            // smoki i wyrmy maja wlasne zestawy zieni (DragonFamilyTest); tu tylko wymog ogolny dla pozostalych
            boolean dragonFamily = k.contains("dragon") || k.contains("drake") || k.contains("wyrm") || k.contains("whelp");
            if (dragonFamily) { assertTrue(area, k + " has an area spell"); checked++; continue; }
            assertTrue(bolt, k + " (lvl " + t.level() + ") has a single-target spell");
            assertTrue(area, k + " (lvl " + t.level() + ") has an area spell");
            checked++;
        }
        assertTrue(checked >= 90, "many strong monsters checked: " + checked);
    }

    @Test
    void monstersFrom300HaveAtLeastThreeSpells() {
        MonsterSpells spells = MonsterSpells.defaults();
        for (MonsterType t : REG.all()) {
            if (t.level() < 300) continue;
            String k = t.key();
            boolean dragonFamily = k.contains("dragon") || k.contains("drake") || k.contains("wyrm") || k.contains("whelp");
            if (dragonFamily) continue;
            assertTrue(spells.of(t).size() >= 3, k + " (lvl " + t.level() + ") has 3+ spells, has " + spells.of(t).size());
        }
    }

    @Test
    void everySpellMissileExists() throws Exception {
        Path dir = Path.of("../assets/art/missiles");
        if (!Files.isDirectory(dir)) dir = Path.of("assets/art/missiles");
        assertTrue(Files.isDirectory(dir), "missiles directory " + dir.toAbsolutePath());
        for (MonsterType t : REG.all()) {
            for (MonsterSpell s : MonsterSpells.defaults().of(t)) {
                if (s.missile() == null) continue;
                assertTrue(Files.exists(dir.resolve(s.missile() + "_00.png")) || Files.exists(dir.resolve(s.missile() + "_10.png")),
                        t.key() + " spell " + s.key() + " missile " + s.missile());
            }
        }
    }
}
