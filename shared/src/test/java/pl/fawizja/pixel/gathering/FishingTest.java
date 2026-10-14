package pl.fawizja.pixel.gathering;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

class FishingTest {

    static int id(String key) { return TestData.ITEMS.byKey(key).id(); }

    /** Kafelek ladu przy jeziorze Varden (szukany w mapie, nie wpisany na sztywno). */
    static Position shore(FishingSystem fs) {
        for (int y = 0; y < TestData.varden().height(); y++) {
            for (int x = 0; x < TestData.varden().width(); x++) {
                Position p = new Position(x, y, 0);
                if (TestData.varden().isWalkable(p) && fs.isWater(p.translate(1, 0))) return p;
            }
        }
        throw new AssertionError("no lake on the map");
    }

    static PlayerCharacter fisher(Position at) {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        pc.setPosition(at);
        return pc;
    }

    @Test
    void chanceGrowsWithSkillAndIsCapped() {
        assertEquals(0.16, FishingSystem.catchChance(10), 1e-9);
        assertTrue(FishingSystem.catchChance(50) > FishingSystem.catchChance(20));
        assertEquals(FishingSystem.MAX_CHANCE, FishingSystem.catchChance(200), 1e-9);
    }

    @Test
    void requiresRodBaitAndWater() {
        FishingSystem fs = new FishingSystem(TestData.varden(), TestData.ITEMS, new Random(1));
        PlayerCharacter pc = fisher(TestData.varden().spawn());
        assertEquals(FishingResult.NO_WATER, fs.fish(pc, 0), "temple is far from water");
        pc.setPosition(shore(fs));
        pc.backpack().remove(id("worm"), pc.backpack().count(id("worm")));
        assertEquals(FishingResult.NO_BAIT, fs.fish(pc, 0));
        pc.backpack().remove(id("fishing_rod"), 1);
        assertEquals(FishingResult.NO_ROD, fs.fish(pc, 0));
    }

    @Test
    void catchingUsesAWormTrainsAndRestsTheSpot() {
        FishingSystem fs = new FishingSystem(TestData.varden(), TestData.ITEMS, new Random(2));
        PlayerCharacter pc = fisher(shore(fs));
        int worms = pc.backpack().count(id("worm"));
        long t = 0;
        FishingResult r;
        int attempts = 0;
        do {
            r = fs.fish(pc, t);
            t += FishingSystem.ATTEMPT_COOLDOWN_MS;
            attempts++;
        } while (r != FishingResult.CAUGHT && attempts < 200);
        assertEquals(FishingResult.CAUGHT, r);
        assertEquals(worms - 1, pc.backpack().count(id("worm")), "only a catch uses bait");
        assertEquals(1, pc.backpack().count(id("fish")));
        assertTrue(pc.skills().tries(Skill.FISHING) > 0 || pc.skills().level(Skill.FISHING) > 10);
        assertEquals(FishingResult.COOLDOWN, fs.fish(pc, t - FishingSystem.ATTEMPT_COOLDOWN_MS + 10));
    }

    @Test
    void oneSpotCannotBeFishedForever() {
        FishingSystem fs = new FishingSystem(TestData.varden(), TestData.ITEMS, new Random(3));
        PlayerCharacter pc = fisher(shore(fs));
        pc.skills().set(Skill.FISHING, 200, 0);
        pc.backpack().add(new ItemStack(id("worm"), 80));
        long t = 0;
        int caught = 0;
        FishingResult last = null;
        for (int i = 0; i < 400; i++) {
            last = fs.fish(pc, t);
            if (last == FishingResult.CAUGHT) caught++;
            if (last == FishingResult.DEPLETED) break;
            t += FishingSystem.ATTEMPT_COOLDOWN_MS;
        }
        assertEquals(FishingResult.DEPLETED, last, "all water in range rests after catches");
        assertTrue(caught < 50, "limited by water tiles in range: " + caught);
        assertNotEquals(FishingResult.DEPLETED, fs.fish(pc, t + FishingSystem.TILE_REST_MS + 1), "water recovers");
    }

    @Test
    void catchRateMatchesSkillAndPearlsNeedSkill() {
        FishingSystem fs = new FishingSystem(TestData.varden(), TestData.ITEMS, new Random(4));
        PlayerCharacter pc = fisher(shore(fs));
        pc.backpack().add(new ItemStack(id("worm"), 90));
        int caught = 0, attempts = 0;
        long t = 0;
        while (attempts < 600) {
            pc.skills().set(Skill.FISHING, 10, 0); // stala umiejetnosc w pomiarze
            FishingResult r = fs.fish(pc, t);
            t += FishingSystem.TILE_REST_MS + 1; // zawsze wypoczety kafelek
            if (r == FishingResult.CAUGHT) caught++;
            attempts++;
            pc.backpack().remove(id("fish"), pc.backpack().count(id("fish")));
            if (pc.backpack().count(id("worm")) < 5) pc.backpack().add(new ItemStack(id("worm"), 50));
        }
        double rate = caught / (double) attempts;
        assertTrue(rate > 0.11 && rate < 0.21, "rate " + rate);
        assertEquals(0, pc.backpack().count(id("pearl")), "no pearls below skill 30");
    }

    @Test
    void fullBackpackKeepsTheWorm() {
        FishingSystem fs = new FishingSystem(TestData.varden(), TestData.ITEMS, new Random(5));
        PlayerCharacter pc = fisher(shore(fs));
        pc.skills().set(Skill.FISHING, 200, 0);
        while (pc.backpack().freeSlots() > 0) pc.backpack().add(new ItemStack(id("leather_helmet"), 1));
        int worms = pc.backpack().count(id("worm"));
        FishingResult r;
        long t = 0;
        do {
            r = fs.fish(pc, t);
            t += FishingSystem.ATTEMPT_COOLDOWN_MS;
        } while (r == FishingResult.NOTHING);
        assertEquals(FishingResult.BACKPACK_FULL, r);
        assertEquals(worms, pc.backpack().count(id("worm")));
    }
}
