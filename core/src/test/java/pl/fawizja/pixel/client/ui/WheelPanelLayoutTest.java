package pl.fawizja.pixel.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.fawizja.pixel.wheel.WheelDomain;
import pl.fawizja.pixel.wheel.WheelGeometry;
import pl.fawizja.pixel.wheel.WheelSlice;

class WheelPanelLayoutTest {

    private static HudLayout.Rect screen(float w, float h) {
        HudLayout.Rect r = new HudLayout.Rect();
        r.set(0, 0, w, h);
        return r;
    }

    @ParameterizedTest
    @CsvSource({"1280,720,false", "1920,1080,false", "1024,600,false", "800,360,true", "780,360,true", "852,393,true"})
    void windowFitsAndPartsDoNotOverlap(float w, float h, boolean mobile) {
        WheelPanelLayout l = new WheelPanelLayout(mobile);
        l.compute(w, h);
        String ctx = w + "x" + h;
        assertTrue(screen(w, h).encloses(l.win), ctx);
        assertTrue(l.win.encloses(l.wheelBox) && l.win.encloses(l.side) && l.win.encloses(l.title), ctx);
        assertFalse(l.wheelBox.overlaps(l.side), ctx);
        assertFalse(l.title.overlaps(l.wheelBox) || l.title.overlaps(l.side), ctx);
        for (HudLayout.Rect b : l.cardButtons) {
            assertTrue(l.side.encloses(b), ctx);
            assertFalse(b.overlaps(l.cardBody), ctx);
            for (HudLayout.Rect t : l.tools) assertFalse(b.overlaps(t), ctx);
        }
        for (HudLayout.Rect t : l.tools) assertTrue(l.side.encloses(t), ctx);
        assertTrue(l.cardBody.h >= (mobile ? 120 : 250), ctx + " karta " + l.cardBody);
        // kolo z oprawa miesci sie w kwadracie, odznaki objawien w rogach nie wchodza na oprawe
        assertTrue(l.radius + l.rim <= l.wheelBox.w / 2f, ctx);
        // 1024x600: okno ma tylko 580 wysokosci, wiec mniejsze kolo; od 720 wysokosci co najmniej 250
        assertTrue(l.radius >= (mobile ? 130 : h >= 720 ? 250 : 200), ctx + " promien " + l.radius);
        for (WheelDomain d : WheelDomain.values()) {
            float[] b = l.badge(d);
            double dist = Math.hypot(b[0] - l.cx, b[1] - l.cy);
            assertTrue(dist - l.badgeR > l.radius + l.rim + 2, ctx + " odznaka " + d);
            assertSame(d, l.badgeAt(b[0], b[1]), ctx);
            assertNull(WheelGeometry.sliceAt(b[0] - l.cx, b[1] - l.cy, l.radius), ctx);
        }
    }

    @ParameterizedTest
    @CsvSource({"800,360", "780,360"})
    void phoneButtonsAreFingerSized(float w, float h) {
        WheelPanelLayout l = new WheelPanelLayout(true);
        l.compute(w, h);
        for (HudLayout.Rect b : l.cardButtons) assertTrue(b.h >= MobileHudLayout.MIN_TOUCH && b.w >= MobileHudLayout.MIN_TOUCH, b.toString());
        for (HudLayout.Rect b : l.tools) assertTrue(b.h >= MobileHudLayout.MIN_TOUCH && b.w >= MobileHudLayout.MIN_TOUCH, b.toString());
        assertTrue(l.closeHit.w >= MobileHudLayout.MIN_TOUCH && l.closeHit.h >= MobileHudLayout.MIN_TOUCH);
        assertTrue(screen(w, h).encloses(l.closeHit));
        assertTrue(2 * l.badgeR >= 34);
        // srodek kazdego pola na ekranie trafia w to pole
        for (WheelSlice s : WheelSlice.values()) {
            float[] c = WheelGeometry.center(s);
            float px = l.cx + c[0] * l.radius, py = l.cy + c[1] * l.radius;
            assertTrue(l.wheelBox.contains(px, py));
            assertSame(s, WheelGeometry.sliceAt(px - l.cx, py - l.cy, l.radius), s.code());
        }
    }
}
