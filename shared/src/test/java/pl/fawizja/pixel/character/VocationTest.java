package pl.fawizja.pixel.character;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.vocation.Vocation;

class VocationTest {

    @Test
    void vocationsHaveDistinctProfiles() {
        Set<String> profiles = new HashSet<>();
        for (Vocation v : Vocation.values()) {
            profiles.add(v.hpPerLevel() + "/" + v.manaPerLevel() + "/" + v.meleeFactor() + "/" + v.distanceFactor()
                    + "/" + v.magicFactor() + "/" + v.healingFactor() + "/" + v.attackIntervalMs());
        }
        assertEquals(Vocation.values().length, profiles.size());
    }

    @Test
    void eachVocationIsBestAtItsRole() {
        assertEquals(Vocation.PALADIN, best("distance"));
        assertEquals(Vocation.SORCERER, best("magic"));
        assertEquals(Vocation.DRUID, best("healing"));
        assertEquals(Vocation.KNIGHT, best("melee"));
        assertEquals(Vocation.KNIGHT, best("hp"));
        assertEquals(Vocation.KNIGHT, best("defense"));
    }

    @Test
    void highLevelHealthOrderingReflectsRoles() {
        int lvl = 500;
        assertTrue(Balance.maxHealth(Vocation.KNIGHT, lvl) > Balance.maxHealth(Vocation.MONK, lvl));
        assertTrue(Balance.maxHealth(Vocation.MONK, lvl) > Balance.maxHealth(Vocation.PALADIN, lvl));
        assertTrue(Balance.maxHealth(Vocation.PALADIN, lvl) > Balance.maxHealth(Vocation.SORCERER, lvl));
        assertTrue(Balance.maxMana(Vocation.SORCERER, lvl) > Balance.maxMana(Vocation.PALADIN, lvl));
        assertTrue(Balance.maxMana(Vocation.PALADIN, lvl) > Balance.maxMana(Vocation.MONK, lvl));
    }

    private static Vocation best(String what) {
        Vocation best = null;
        double bestV = -1;
        for (Vocation v : Vocation.values()) {
            double x;
            switch (what) {
                case "distance": x = v.distanceFactor(); break;
                case "magic": x = v.magicFactor(); break;
                case "healing": x = v.healingFactor(); break;
                case "melee": x = v.meleeFactor(); break;
                case "defense": x = v.defenseFactor(); break;
                default: x = v.hpPerLevel();
            }
            if (x > bestV) { bestV = x; best = v; }
        }
        return best;
    }
}
