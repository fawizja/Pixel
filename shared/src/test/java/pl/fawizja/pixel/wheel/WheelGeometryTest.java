package pl.fawizja.pixel.wheel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WheelGeometryTest {

    private static final float R = 200f;

    @Test
    void everySliceIsReachableAtItsCentre() {
        for (WheelSlice s : WheelSlice.values()) {
            float[] c = WheelGeometry.center(s);
            assertSame(s, WheelGeometry.sliceAt(c[0] * R, c[1] * R, R), s.code());
        }
    }

    @Test
    void everySliceIsReachableNearItsCorners() {
        for (WheelSlice s : WheelSlice.values()) {
            int ring = WheelGeometry.ring(s);
            double r0 = WheelGeometry.innerRadius(ring) + 0.005, r1 = WheelGeometry.outerRadius(ring) - 0.005;
            double a0 = WheelGeometry.startDeg(s) + 0.3, a1 = WheelGeometry.startDeg(s) + WheelGeometry.sweepDeg(s) - 0.3;
            for (double r : new double[] {r0, r1}) {
                for (double a : new double[] {a0, a1}) {
                    float x = (float) (Math.cos(Math.toRadians(a)) * r * R), y = (float) (Math.sin(Math.toRadians(a)) * r * R);
                    assertSame(s, WheelGeometry.sliceAt(x, y, R), s.code() + " r=" + r + " a=" + a);
                }
            }
        }
    }

    @Test
    void sampledDiscCoversAll36SlicesAndOnlyHubOrOutsideIsEmpty() {
        Set<WheelSlice> seen = EnumSet.noneOf(WheelSlice.class);
        for (float x = -R - 5; x <= R + 5; x += 2) {
            for (float y = -R - 5; y <= R + 5; y += 2) {
                WheelSlice s = WheelGeometry.sliceAt(x, y, R);
                double d = Math.hypot(x, y) / R;
                if (d < WheelGeometry.HUB || d > 1) assertNull(s, x + "," + y);
                else {
                    assertNotNull(s, x + "," + y);
                    seen.add(s);
                }
            }
        }
        assertEquals(36, seen.size());
        assertTrue(WheelGeometry.inHub(0, 0, R));
        assertTrue(WheelGeometry.inHub(R * 0.2f, 0, R));
        assertFalse(WheelGeometry.inHub(R * 0.3f, 0, R));
    }

    @Test
    void quadrantsSitLikeInTibia() {
        // zielona lewa gora, czerwona prawa gora, fioletowa prawy dol, niebieska lewy dol
        float m = R * 0.6f;
        assertEquals(WheelDomain.GREEN, WheelGeometry.sliceAt(-m, m, R).domain);
        assertEquals(WheelDomain.RED, WheelGeometry.sliceAt(m, m, R).domain);
        assertEquals(WheelDomain.PURPLE, WheelGeometry.sliceAt(m, -m, R).domain);
        assertEquals(WheelDomain.BLUE, WheelGeometry.sliceAt(-m, -m, R).domain);
        // od srodka: 50, 75, 100, 150, 200 wzdluz przekatnej
        int[] caps = {50, 75, 100, 150, 200};
        for (int ring = 0; ring < 5; ring++) {
            double r = (WheelGeometry.innerRadius(ring) + WheelGeometry.outerRadius(ring)) / 2 * R;
            float x = (float) (-r * Math.cos(Math.toRadians(45)) + 0.01), y = (float) (r * Math.sin(Math.toRadians(45)) + 0.01);
            assertEquals(caps[ring], WheelGeometry.sliceAt(x, y, R).capacity(), "pierscien " + ring);
        }
    }

    @Test
    void ringsHoldTibiaCountsPerQuadrant() {
        int[] expected = {1, 2, 3, 2, 1};
        for (WheelDomain d : WheelDomain.values()) {
            int[] n = new int[5];
            for (WheelSlice s : WheelSlice.of(d)) n[WheelGeometry.ring(s)]++;
            assertArrayEquals(expected, n, d.name());
        }
        // wycinki jednego pierscienia w cwiartce nie nachodza na siebie i wypelniaja 90 stopni
        for (WheelDomain d : WheelDomain.values()) {
            for (int ring = 0; ring < 5; ring++) {
                float sum = 0;
                Set<Integer> idx = new java.util.HashSet<>();
                for (WheelSlice s : WheelSlice.of(d)) {
                    if (WheelGeometry.ring(s) != ring) continue;
                    sum += WheelGeometry.sweepDeg(s);
                    assertTrue(idx.add(WheelGeometry.angularIndex(s)), s.code());
                    assertTrue(WheelGeometry.startDeg(s) >= WheelGeometry.quadrantStart(d) - 1e-4);
                    assertTrue(WheelGeometry.startDeg(s) + WheelGeometry.sweepDeg(s) <= WheelGeometry.quadrantStart(d) + 90 + 1e-4);
                }
                assertEquals(90f, sum, 1e-3);
            }
        }
    }

    @Test
    void everyPrerequisiteTouchesItsSliceOnTheDrawing() {
        // otwarcie pola przez pelnego sasiada musi byc widac na kole (takze przez granice cwiartek)
        for (WheelSlice s : WheelSlice.values()) {
            for (WheelSlice p : s.prereqs()) assertTrue(WheelGeometry.touching(s, p), s.code() + " <- " + p.code());
        }
    }

    @Test
    void crossQuadrantNeighboursFaceEachOther() {
        assertTrue(WheelGeometry.touching(WheelSlice.GREEN_TOP_75, WheelSlice.RED_TOP_75));
        assertTrue(WheelGeometry.touching(WheelSlice.RED_BOTTOM_100, WheelSlice.PURPLE_TOP_100));
        assertTrue(WheelGeometry.touching(WheelSlice.PURPLE_BOTTOM_75, WheelSlice.BLUE_BOTTOM_75));
        assertTrue(WheelGeometry.touching(WheelSlice.BLUE_TOP_100, WheelSlice.GREEN_BOTTOM_100));
        // gorne 75 zielonej lezy przy osi pionowej u gory (kat tuz ponad 90 stopni)
        assertSame(WheelSlice.GREEN_TOP_75, at(91, WheelSlice.GREEN_TOP_75));
        assertSame(WheelSlice.RED_TOP_75, at(89, WheelSlice.RED_TOP_75));
        assertSame(WheelSlice.RED_BOTTOM_75, at(1, WheelSlice.RED_BOTTOM_75));
        assertSame(WheelSlice.PURPLE_TOP_75, at(359, WheelSlice.PURPLE_TOP_75));
        assertSame(WheelSlice.BLUE_TOP_75, at(181, WheelSlice.BLUE_TOP_75));
        assertSame(WheelSlice.BLUE_BOTTOM_75, at(269, WheelSlice.BLUE_BOTTOM_75));
    }

    @Test
    void slicesOfDifferentRingsAtTheSameAngleDoNotTouchUnlessAdjacent() {
        assertFalse(WheelGeometry.touching(WheelSlice.RED_50, WheelSlice.RED_200));
        assertFalse(WheelGeometry.touching(WheelSlice.RED_50, WheelSlice.RED_50));
        assertFalse(WheelGeometry.touching(WheelSlice.GREEN_50, WheelSlice.PURPLE_50));
        assertTrue(WheelGeometry.touching(WheelSlice.GREEN_50, WheelSlice.RED_50), "wspolna krawedz na osi");
    }

    /** Pole w pierscieniu danego pola, pod katem deg. */
    private static WheelSlice at(double deg, WheelSlice ringOf) {
        double r = WheelGeometry.midRadius(ringOf) * R;
        return WheelGeometry.sliceAt((float) (Math.cos(Math.toRadians(deg)) * r), (float) (Math.sin(Math.toRadians(deg)) * r), R);
    }
}
