package pl.fawizja.pixel.gathering;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.ItemCategory;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.Region;
import pl.fawizja.pixel.world.WorldMap;

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

    /** Robaki w gniezdzie amunicji i w plecaku razem. */
    static int worms(PlayerCharacter pc) {
        ItemStack a = pc.equipment().get(EquipSlot.AMMO);
        return pc.backpack().count(id("worm")) + (a != null && a.itemId() == id("worm") ? a.count() : 0);
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
        assertEquals(10, fs.baitInSlot(pc), "starter worms sit in the ammo slot");
        pc.equipment().setDirect(EquipSlot.AMMO, null);
        pc.backpack().remove(id("worm"), pc.backpack().count(id("worm")));
        assertEquals(FishingResult.NO_BAIT, fs.fish(pc, 0));
        assertTrue(FishingResult.NO_BAIT.message.contains("robak"));
        pc.backpack().add(new ItemStack(id("worm"), 5));
        assertEquals(FishingResult.BAIT_NOT_IN_SLOT, fs.fish(pc, 0), "worms only in the backpack are not enough");
        pc.equipment().setDirect(EquipSlot.AMMO, new ItemStack(id("worm"), 5));
        pc.backpack().remove(id("fishing_rod"), 1);
        assertEquals(FishingResult.NO_ROD, fs.fish(pc, 0));
        assertTrue(FishingResult.NO_ROD.message.contains("wędk"));
        assertEquals(10, worms(pc), "failed attempts use no worms");
    }

    @Test
    void wormsCanBeWornInTheAmmoSlotAndRodInHandCounts() {
        FishingSystem fs = new FishingSystem(TestData.varden(), TestData.ITEMS, new Random(9));
        PlayerCharacter pc = fisher(shore(fs));
        assertEquals(EquipSlot.AMMO, TestData.ITEMS.byKey("worm").slot());
        pc.backpack().remove(id("fishing_rod"), 1);
        pc.equipment().setDirect(EquipSlot.WEAPON, new ItemStack(id("fishing_rod"), 1));
        assertTrue(fs.hasRod(pc));
        assertNotEquals(FishingResult.NO_ROD, fs.fish(pc, 0));
    }

    @Test
    void everyCastUsesOneWormAndTheSlotRefillsFromTheBackpack() {
        FishingSystem fs = new FishingSystem(TestData.varden(), TestData.ITEMS, new Random(8));
        PlayerCharacter pc = fisher(shore(fs));
        pc.equipment().setDirect(EquipSlot.AMMO, new ItemStack(id("worm"), 2));
        pc.backpack().add(new ItemStack(id("worm"), 3));
        long t = 0;
        int casts = 0;
        for (int i = 0; i < 5; i++) {
            FishingResult r = fs.fish(pc, t + i * (FishingSystem.TILE_REST_MS + 1));
            assertTrue(r == FishingResult.CAUGHT || r == FishingResult.NOTHING, r.toString());
            casts++;
            assertEquals(5 - casts, worms(pc));
            pc.backpack().remove(id("fish"), pc.backpack().count(id("fish")));
            pc.backpack().remove(id("pearl"), pc.backpack().count(id("pearl")));
        }
        assertNull(pc.equipment().get(EquipSlot.AMMO), "all worms used");
        assertEquals(FishingResult.NO_BAIT, fs.fish(pc, 10 * (FishingSystem.TILE_REST_MS + 1)));
    }

    @Test
    void catchingUsesAWormTrainsAndRestsTheSpot() {
        FishingSystem fs = new FishingSystem(TestData.varden(), TestData.ITEMS, new Random(2));
        PlayerCharacter pc = fisher(shore(fs));
        pc.backpack().add(new ItemStack(id("worm"), 100));
        pc.backpack().add(new ItemStack(id("worm"), 100));
        int worms = worms(pc);
        long t = 0;
        FishingResult r;
        int attempts = 0;
        do {
            r = fs.fish(pc, t);
            t += FishingSystem.ATTEMPT_COOLDOWN_MS;
            attempts++;
        } while (r != FishingResult.CAUGHT && attempts < 200);
        assertEquals(FishingResult.CAUGHT, r);
        assertEquals(worms - attempts, worms(pc), "every cast uses one worm");
        assertEquals(1, pc.backpack().count(id("fish")));
        assertTrue(pc.skills().tries(Skill.FISHING) > 0 || pc.skills().level(Skill.FISHING) > 10);
        assertEquals(FishingResult.COOLDOWN, fs.fish(pc, t - FishingSystem.ATTEMPT_COOLDOWN_MS + 10));
    }

    @Test
    void oneSpotCannotBeFishedForever() {
        FishingSystem fs = new FishingSystem(TestData.varden(), TestData.ITEMS, new Random(3));
        PlayerCharacter pc = fisher(shore(fs));
        pc.skills().set(Skill.FISHING, 200, 0);
        pc.backpack().add(new ItemStack(id("worm"), 100));
        pc.backpack().add(new ItemStack(id("worm"), 100));
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
            if (worms(pc) < 5) pc.backpack().add(new ItemStack(id("worm"), 50));
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
        FishingResult r;
        long t = 0;
        int before;
        do {
            before = worms(pc);
            r = fs.fish(pc, t);
            t += FishingSystem.ATTEMPT_COOLDOWN_MS;
        } while (r == FishingResult.NOTHING);
        assertEquals(FishingResult.BACKPACK_FULL, r);
        assertEquals(before, worms(pc), "a catch that does not fit keeps the worm");
    }

    /** Brzeg wody w glebi regionu (cala okolica w zasiegu wedki nalezy do regionu). */
    static Position shoreIn(FishingSystem fs, String region) {
        WorldMap w = TestData.world();
        for (int y = 0; y < w.height(); y++) {
            for (int x = 0; x < w.width(); x++) {
                Position p = new Position(x, y, 0);
                if (!w.isWalkable(p) || !fs.isWater(p.translate(1, 0))) continue;
                boolean inside = true;
                for (int d = -FishingSystem.RANGE; d <= FishingSystem.RANGE && inside; d++) {
                    for (int e = -FishingSystem.RANGE; e <= FishingSystem.RANGE && inside; e++) {
                        Region r = w.regionAt(p.translate(d, e));
                        inside = r != null && r.name().equals(region);
                    }
                }
                if (inside) return p;
            }
        }
        throw new AssertionError("no fishing water in " + region);
    }

    /** Wspolny zegar kolejnych serii lowienia (odnowienie proby przechodzi miedzy seriami). */
    private static long clock;

    static Map<Integer, Integer> catches(FishingSystem fs, PlayerCharacter pc, int skill, int attempts) {
        Map<Integer, Integer> got = new HashMap<>();
        for (int i = 0; i < attempts; i++) {
            pc.skills().set(Skill.FISHING, skill, 0);
            if (worms(pc) < 5) pc.backpack().add(new ItemStack(id("worm"), 50));
            if (fs.fish(pc, clock) == FishingResult.CAUGHT) got.merge(fs.lastCatchId(), 1, Integer::sum);
            for (int k = 0; k < 20; k++) {
                ItemStack st = pc.backpack().get(k);
                if (st != null && TestData.ITEMS.get(st.itemId()).category() == ItemCategory.FOOD) pc.backpack().remove(st.itemId(), st.count());
            }
            clock += FishingSystem.TILE_REST_MS + 1;
        }
        return got;
    }

    @Test
    void everyBiomeHasItsOwnFishAboveASkillThreshold() {
        FishingSystem fs = new FishingSystem(TestData.world(), TestData.ITEMS, new Random(6));
        assertEquals(3, fs.biomeFish().size());
        for (FishingSystem.BiomeFish b : fs.biomeFish()) {
            PlayerCharacter pc = fisher(shoreIn(fs, b.region()));
            Map<Integer, Integer> skilled = catches(fs, pc, 29, 400); // ponizej progu perel
            int special = skilled.getOrDefault(b.itemId(), 0);
            int plain = skilled.getOrDefault(id("fish"), 0);
            assertTrue(special > 0 && plain > 0, b.region() + ": " + skilled);
            double share = special / (double) (special + plain);
            assertEquals(b.permille() / 1000.0, share, 0.12, b.region() + " share");

            Map<Integer, Integer> novice = catches(fs, pc, b.minSkill() - 1, 200);
            assertEquals(0, novice.getOrDefault(b.itemId(), 0), b.region() + ": needs skill " + b.minSkill());
        }
        PlayerCharacter varden = fisher(shoreIn(fs, "Krainy Varden"));
        Map<Integer, Integer> lake = catches(fs, varden, 29, 200);
        assertEquals(Set.of(id("fish")), lake.keySet(), "Varden has only ordinary fish");
    }
}
