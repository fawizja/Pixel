package pl.fawizja.pixel.wheel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.combat.DamageType;
import pl.fawizja.pixel.vocation.Vocation;

/** Klejnoty Kola: odkrywanie (mody jak Canary), blokada, rozbicie, zmiana domeny, rezonans naczyn, stopnie, zapis. */
class GemAtelierTest {

    private static void fillSlice(PlayerCharacter pc, WheelSlice s) {
        DestinyWheel.Result r = pc.wheel().add(s, s.capacity() - pc.wheel().points(s), pc.wheelPoints());
        assertEquals(DestinyWheel.Result.OK, r, s.code());
        pc.refreshWheel();
    }

    /** Rycerz z klejnotem w zielonym naczyniu (zapis pracowni podany jako tekst). */
    private static PlayerCharacter knightWith(String gems) {
        PlayerCharacter pc = WheelFixtures.hero(Vocation.KNIGHT, 1000);
        pc.wheel().gems().decode(gems);
        pc.refreshWheel();
        return pc;
    }

    @Test
    void revealedGemsHaveCanaryModSlots() {
        Random rng = new Random(11);
        GemAtelier a = new GemAtelier();
        for (int i = 0; i < 200; i++) {
            for (GemQuality q : GemQuality.values()) {
                WheelGem g = a.reveal(q, Vocation.DRUID, rng);
                if (g == null) {
                    a = new GemAtelier();
                    g = a.reveal(q, Vocation.DRUID, rng);
                }
                assertTrue(BasicGemMod.SLOT1.contains(g.basic1), "mod 1 from slot-1 list");
                if (q == GemQuality.LESSER) assertNull(g.basic2);
                else {
                    assertTrue(BasicGemMod.SLOT2.contains(g.basic2), "mod 2 from slot-2 list");
                    assertNotSame(g.basic1, g.basic2);
                }
                if (q == GemQuality.GREATER) assertTrue(g.supreme.allowed(Vocation.DRUID), "supreme mod of own vocation");
                else assertNull(g.supreme);
            }
        }
    }

    @Test
    void lockBlocksDestroyAndSwitch() {
        GemAtelier a = new GemAtelier();
        WheelGem g = a.reveal(GemQuality.REGULAR, Vocation.KNIGHT, new Random(1));
        assertEquals(GemAtelier.Result.OK, a.toggleLock(g.id));
        assertEquals(GemAtelier.Result.LOCKED, a.destroy(g.id));
        assertEquals(GemAtelier.Result.LOCKED, a.switchDomain(g.id));
        a.toggleLock(g.id);
        WheelDomain before = g.domain();
        assertEquals(GemAtelier.Result.OK, a.switchDomain(g.id));
        assertEquals(WheelDomain.values()[(before.ordinal() + 1) % 4], g.domain(), "Green -> Red -> Purple -> Blue -> Green");
        assertEquals(GemAtelier.Result.OK, a.destroy(g.id));
        assertEquals(0, a.count());
    }

    @Test
    void fragmentsStayInTibiaRanges() {
        Random rng = new Random(4);
        int[] seenLesser = new int[11];
        for (int i = 0; i < 5000; i++) {
            int l = GemAtelier.rollFragments(GemQuality.LESSER, rng);
            int r = GemAtelier.rollFragments(GemQuality.REGULAR, rng);
            int g = GemAtelier.rollFragments(GemQuality.GREATER, rng);
            assertTrue(l >= 1 && l <= 5);
            assertTrue(r >= 2 && r <= 10);
            assertTrue(g >= 1 && g <= 5);
            seenLesser[l]++;
        }
        for (int n = 1; n <= 5; n++) assertTrue(seenLesser[n] > 0, "every count possible: " + n);
        assertTrue(GemQuality.GREATER.greaterFragments && !GemQuality.REGULAR.greaterFragments);
    }

    @Test
    void switchingDomainTakesTheGemOutOfTheVessel() {
        GemAtelier a = new GemAtelier();
        a.decode("n=2;g=1,R,G,HEALTH,FIRE_RES,-,0;a=1,0,0,0");
        assertTrue(a.isSocketed(a.gem(1)));
        a.switchDomain(1);
        assertNull(a.socketed(WheelDomain.GREEN));
        assertEquals(WheelDomain.RED, a.gem(1).domain());
    }

    @Test
    void vesselResonanceActivatesModsOneByOne() {
        PlayerCharacter pc = knightWith("n=2;g=1,G,G,HEALTH,FIRE_RES,DODGE,0;a=1,0,0,0");
        fillSlice(pc, WheelSlice.GREEN_50);
        assertEquals(1, pc.wheel().resonance(WheelDomain.GREEN));
        GemAtelier.Effects e = pc.wheelBonus().gems();
        assertEquals(300, e.health, "knight: +300 HP from the full health mod");
        assertEquals(0, e.resist(DamageType.FIRE), 1e-9, "mod 2 needs resonance 2");
        fillSlice(pc, WheelSlice.GREEN_TOP_75);
        fillSlice(pc, WheelSlice.GREEN_TOP_100);
        assertEquals(2, pc.wheel().resonance(WheelDomain.GREEN));
        assertEquals(2.0, pc.wheelBonus().gems().resist(DamageType.FIRE), 1e-9);
        assertEquals(0, pc.wheelBonus().gems().dodge, 1e-9, "supreme mod needs resonance 3");
        fillSlice(pc, WheelSlice.GREEN_MIDDLE_100);
        fillSlice(pc, WheelSlice.GREEN_BOTTOM_150);
        assertEquals(3, pc.wheel().resonance(WheelDomain.GREEN));
        assertEquals(0.28, pc.wheelBonus().gems().dodge, 1e-9);
        assertEquals(0.28, WheelEffects.gemDodge(pc), 1e-9);
        // 100 obrazen od ognia przy 2% odpornosci: 100 - ceil(2) = 98
        assertEquals(98, WheelEffects.gemResist(pc, DamageType.FIRE, 100), 1e-9);
        assertEquals(100, WheelEffects.gemResist(pc, DamageType.ICE, 100), 1e-9);
    }

    @Test
    void gemInAnotherDomainDoesNothing() {
        PlayerCharacter pc = knightWith("n=2;g=1,L,R,HEALTH,-,-,0;a=0,1,0,0");
        fillSlice(pc, WheelSlice.GREEN_50);
        assertEquals(0, pc.wheelBonus().gems().health, "red gem needs red vessel slices");
    }

    @Test
    void gradesMultiplyAndMaxedModsGivePromotionPoints() {
        PlayerCharacter pc = knightWith("n=2;g=1,L,G,HEALTH,-,-,0;a=1,0,0,0;bg=HEALTH:1");
        int points = pc.wheelPoints();
        fillSlice(pc, WheelSlice.GREEN_50);
        assertEquals(330, pc.wheelBonus().gems().health, "grade II: x1.1");
        pc.wheel().gems().decode("n=2;g=1,L,G,HEALTH,-,-,0;a=1,0,0,0;bg=HEALTH:3");
        pc.refreshWheel();
        assertEquals(450, pc.wheelBonus().gems().health, "grade IV: x1.5");
        assertEquals(points + 1, pc.wheelPoints(), "+1 promotion point per mod at grade IV");
        assertNull(pc.wheel().gems().basicGradeCost(BasicGemMod.HEALTH));
        assertEquals(GemAtelier.Result.MAX_GRADE, pc.wheel().gems().gradeUp(BasicGemMod.HEALTH));
    }

    @Test
    void weaknessDoesNotGrowWithGrade() {
        BasicGemMod m = BasicGemMod.FIRE_RES_EARTH_WEAK;
        assertEquals("+4,5% odp. (" + DamageType.FIRE.label() + "), -2% odp. (" + DamageType.EARTH.label() + ")",
                m.describe(Vocation.DRUID, 3));
    }

    @Test
    void gradeCostsAreCanaryDividedByFour() {
        GemAtelier a = new GemAtelier();
        assertArrayEquals(new long[] {500_000, 5}, a.basicGradeCost(BasicGemMod.MANA));
        a.gradeUp(BasicGemMod.MANA);
        assertArrayEquals(new long[] {1_250_000, 15}, a.basicGradeCost(BasicGemMod.MANA));
        a.gradeUp(BasicGemMod.MANA);
        assertArrayEquals(new long[] {7_500_000, 30}, a.basicGradeCost(BasicGemMod.MANA));
        assertArrayEquals(new long[] {1_250_000, 5}, a.supremeGradeCost(SupremeGemMod.DODGE));
        assertEquals(GemAtelier.Result.WRONG_VOCATION, a.gradeUp(SupremeGemMod.KNIGHT_MASTERY_AVATAR, Vocation.DRUID));
    }

    @Test
    void revelationMasteryRaisesTheDomainStage() {
        PlayerCharacter pc = knightWith("n=2;g=1,G,G,HEALTH,FIRE_RES,MASTERY_GIFT_OF_LIFE,0;a=1,0,0,0");
        for (WheelSlice s : new WheelSlice[] {WheelSlice.GREEN_50, WheelSlice.GREEN_TOP_75, WheelSlice.GREEN_TOP_100,
                WheelSlice.GREEN_MIDDLE_100, WheelSlice.GREEN_BOTTOM_150}) fillSlice(pc, s);
        assertEquals(475, pc.wheel().domainPoints(WheelDomain.GREEN));
        assertEquals(150, pc.wheelBonus().gems().revelation(WheelDomain.GREEN));
        assertEquals(2, pc.wheelBonus().stage(WheelDomain.GREEN), "475 + 150 = 625 points: stage 2");
    }

    @Test
    void saveRoundTripAndOnlineCopy() {
        GemAtelier a = new GemAtelier();
        Random rng = new Random(9);
        a.reveal(GemQuality.LESSER, Vocation.PALADIN, rng);
        WheelGem g = a.reveal(GemQuality.GREATER, Vocation.PALADIN, rng);
        a.toggleLock(1);
        a.socket(g.id);
        a.gradeUp(BasicGemMod.FIRE_RES);
        a.gradeUp(g.supreme, Vocation.PALADIN);
        String text = a.encode();
        GemAtelier b = new GemAtelier();
        b.decode(text);
        assertEquals(text, b.encode());
        assertTrue(b.gem(1).locked());
        assertEquals(g.id, b.socketed(g.domain()).id);
        GemAtelier c = new GemAtelier();
        c.copyFrom(b);
        assertEquals(text, c.encode());
        assertEquals("", new GemAtelier().encode(), "empty atelier is not saved");
        GemAtelier bad = new GemAtelier();
        bad.decode("n=3;g=1,L,G,HEALTH,-,-,0/x,y;a=0,1,0,0");
        assertEquals(1, bad.count(), "broken entries are skipped");
        assertNull(bad.socketed(WheelDomain.RED), "a vessel only holds a gem of its own domain");
    }
}
