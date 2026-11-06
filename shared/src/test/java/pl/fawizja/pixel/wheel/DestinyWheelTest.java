package pl.fawizja.pixel.wheel;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.vocation.Vocation;

class DestinyWheelTest {

    @Test
    void pointsNeedLevel51PromotionAndPremium() {
        assertEquals(0, DestinyWheel.available(50, true, true));
        assertEquals(1, DestinyWheel.available(51, true, true));
        assertEquals(250, DestinyWheel.available(300, true, true));
        assertEquals(0, DestinyWheel.available(300, false, true), "no promotion");
        assertEquals(0, DestinyWheel.available(300, true, false), "no premium");
    }

    @Test
    void wheelHas36SlicesWithTibiaCapacities() {
        assertEquals(36, WheelSlice.values().length);
        int total = 0;
        for (WheelSlice s : WheelSlice.values()) total += s.capacity();
        assertEquals(4 * (50 + 75 * 2 + 100 * 3 + 150 * 2 + 200), total);
        for (WheelDomain d : WheelDomain.values()) assertEquals(9, WheelSlice.of(d).size());
        for (WheelSlice s : WheelSlice.values()) assertSame(s, WheelSlice.ofCode(s.code()), s.code());
    }

    @Test
    void sliceOpensOnlyNextToAFullSliceAndWithEnoughTotalPoints() {
        DestinyWheel w = new DestinyWheel();
        int av = 200;
        assertEquals(DestinyWheel.Result.LOCKED, w.add(WheelSlice.RED_TOP_75, 1, av), "middle slice not full yet");
        assertEquals(DestinyWheel.Result.OK, w.add(WheelSlice.RED_50, 49, av));
        assertEquals(DestinyWheel.Result.LOCKED, w.add(WheelSlice.RED_TOP_75, 1, av), "49/50 is not full");
        assertEquals(DestinyWheel.Result.OK, w.add(WheelSlice.RED_50, 5, av));
        assertEquals(50, w.points(WheelSlice.RED_50), "capped at capacity");
        assertEquals(DestinyWheel.Result.FULL, w.add(WheelSlice.RED_50, 1, av));
        assertEquals(DestinyWheel.Result.OK, w.add(WheelSlice.RED_TOP_75, 75, av));
        // 100-slices need 125 promotion points in total (Canary thresholds 50/125/225/375)
        assertEquals(DestinyWheel.Result.THRESHOLD, new DestinyWheel().add(WheelSlice.RED_TOP_75, 1, 40), "75-slices need 50 points");
        assertEquals(DestinyWheel.Result.OK, w.add(WheelSlice.RED_TOP_100, 10, av));
        assertEquals(DestinyWheel.Result.NO_POINTS, w.add(WheelSlice.RED_MIDDLE_100, 100, 135));
        assertEquals(DestinyWheel.Result.THRESHOLD, w.add(WheelSlice.RED_TOP_150, 1, 200));
        assertEquals(DestinyWheel.Result.NOT_ELIGIBLE, w.add(WheelSlice.GREEN_50, 1, 0));
    }

    @Test
    void neighbouringQuadrantsConnectAtTheirShared75And100Slices() {
        DestinyWheel w = new DestinyWheel();
        int av = 300;
        w.add(WheelSlice.RED_50, 50, av);
        w.add(WheelSlice.RED_TOP_75, 75, av);
        // GREEN_T <-> RED_T: a full red top 75 opens green top 75 without the green middle
        assertTrue(w.open(WheelSlice.GREEN_TOP_75, av));
        assertFalse(w.open(WheelSlice.BLUE_TOP_75, av));
        assertEquals(DestinyWheel.Result.OK, w.add(WheelSlice.GREEN_TOP_75, 1, av));
        assertTrue(WheelSlice.PURPLE_TOP_75.prereqs().contains(WheelSlice.RED_BOTTOM_75));
        assertTrue(WheelSlice.BLUE_BOTTOM_100.prereqs().contains(WheelSlice.PURPLE_BOTTOM_100));
        assertTrue(WheelSlice.GREEN_BOTTOM_75.prereqs().contains(WheelSlice.BLUE_TOP_75));
    }

    @Test
    void removingASliceOthersHangOnIsRefused() {
        DestinyWheel w = new DestinyWheel();
        int av = 200;
        w.add(WheelSlice.BLUE_50, 50, av);
        w.add(WheelSlice.BLUE_TOP_75, 30, av);
        assertEquals(DestinyWheel.Result.DEPENDENT, w.remove(WheelSlice.BLUE_50, 1, av));
        assertEquals(DestinyWheel.Result.OK, w.remove(WheelSlice.BLUE_TOP_75, 30, av));
        assertEquals(DestinyWheel.Result.OK, w.remove(WheelSlice.BLUE_50, 1, av));
        assertEquals(DestinyWheel.Result.EMPTY, w.remove(WheelSlice.BLUE_TOP_75, 1, av));
        assertEquals(DestinyWheel.Result.OK, w.reset());
        assertTrue(w.isEmpty());
    }

    @Test
    void losingLevelsTrimsPointsFromTheOutsideAndKeepsTheWheelValid() {
        PlayerCharacter pc = WheelFixtures.hero(Vocation.KNIGHT, 450);
        WheelFixtures.fill(pc, "RPBG");
        assertEquals(400, pc.wheel().spent());
        assertTrue(pc.wheel().valid(400));
        assertTrue(pc.wheel().trim(150));
        assertTrue(pc.wheel().valid(150));
        assertTrue(pc.wheel().spent() <= 150);
        assertEquals(50, pc.wheel().points(WheelSlice.RED_50), "the core stays");
        assertEquals(0, pc.wheel().points(WheelSlice.RED_200), "outer slices go first");
    }

    @Test
    void presetsSaveAndLoadWithinAvailablePoints() {
        DestinyWheel w = new DestinyWheel();
        w.add(WheelSlice.GREEN_50, 50, 100);
        w.add(WheelSlice.GREEN_TOP_75, 40, 100);
        w.savePreset(1);
        assertEquals(90, w.presetPoints(1));
        w.reset();
        assertEquals(DestinyWheel.Result.NO_PRESET, w.loadPreset(0, 100));
        assertEquals(DestinyWheel.Result.PRESET_TOO_BIG, w.loadPreset(1, 80));
        assertEquals(DestinyWheel.Result.OK, w.loadPreset(1, 100));
        assertEquals(40, w.points(WheelSlice.GREEN_TOP_75));
    }

    @Test
    void wheelSurvivesSaveUnderItsOwnKeyAndOldSavesLoadEmpty() throws Exception {
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter pc = WheelFixtures.hero(Vocation.DRUID, 400);
        WheelFixtures.fill(pc, "GBRP");
        pc.wheel().savePreset(2);
        pc.wheel().setGiftCooldown(3_600_000L);
        pc.wheel().startAvatar(2, 5_000_000L);
        int maxHp = pc.maxHealth();
        pc.heal(maxHp);
        String text = codec.encode(pc);
        assertTrue(text.contains("\nwheel="), "separate key, no version bump");
        PlayerCharacter back = codec.decode(text);
        for (WheelSlice s : WheelSlice.values()) assertEquals(pc.wheel().points(s), back.wheel().points(s), s.code());
        assertEquals(pc.wheel().presetPoints(2), back.wheel().presetPoints(2));
        assertEquals(3_600_000L, back.wheel().giftCooldownMs());
        assertTrue(back.wheel().avatarActive());
        assertEquals(5_000_000L, back.wheel().avatarCooldownMs());
        assertEquals(maxHp, back.maxHealth());
        assertEquals(maxHp, back.health(), "health above the base maximum is kept");

        PlayerCharacter plain = WheelFixtures.hero(Vocation.DRUID, 400);
        String legacy = codec.encode(plain);
        assertFalse(legacy.contains("wheel="));
        assertTrue(codec.decode(legacy).wheel().isEmpty());
    }

    @Test
    void decodeIgnoresGarbageAndInvalidLayouts() {
        DestinyWheel w = new DestinyWheel();
        w.decode("p=G50:50,GT75:999,XX:5;s0=bad;g=-5;a=zz");
        assertEquals(50, w.points(WheelSlice.GREEN_50));
        assertTrue(w.points(WheelSlice.GREEN_TOP_75) <= WheelSlice.GREEN_TOP_75.capacity());
        w.decode(null);
        w.decode("");
    }
}
