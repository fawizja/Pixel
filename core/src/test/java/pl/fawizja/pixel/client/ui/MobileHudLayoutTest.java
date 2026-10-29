package pl.fawizja.pixel.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MobileHudLayoutTest {

    private static MobileHudLayout layout(float w, float h) {
        MobileHudLayout l = new MobileHudLayout();
        l.compute(w, h);
        return l;
    }

    private static HudLayout.Rect screen(float w, float h) {
        HudLayout.Rect s = new HudLayout.Rect();
        s.set(0, 0, w, h);
        return s;
    }

    /** Stale prostokaty w spoczynku (czat zwiniety): ramka, minimapa, lista walki, zakladka czatu. */
    private static List<HudLayout.Rect> restRects(MobileHudLayout l) {
        List<HudLayout.Rect> out = new ArrayList<>(List.of(l.frame, l.minimap, l.battle, l.chatTab));
        return out;
    }

    private static List<MobileHudLayout.Circle> allCircles(MobileHudLayout l) {
        List<MobileHudLayout.Circle> out = new ArrayList<>(List.of(l.fixedCircles()));
        out.addAll(List.of(l.context));
        return out;
    }

    private static void noOverlaps(String ctx, List<HudLayout.Rect> rects, List<MobileHudLayout.Circle> circles) {
        for (int i = 0; i < rects.size(); i++) {
            for (int j = i + 1; j < rects.size(); j++) {
                assertFalse(rects.get(i).overlaps(rects.get(j)), ctx + ": prostokaty " + rects.get(i) + " i " + rects.get(j));
            }
            for (MobileHudLayout.Circle c : circles) assertFalse(c.overlaps(rects.get(i)), ctx + ": kolo " + c + " i " + rects.get(i));
        }
        for (int i = 0; i < circles.size(); i++) {
            for (int j = i + 1; j < circles.size(); j++) {
                assertFalse(circles.get(i).overlaps(circles.get(j)), ctx + ": kola " + circles.get(i) + " i " + circles.get(j));
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"800,360", "780,360", "852,393", "960,600"})
    void restStateHasNoOverlapsAndStaysOnScreen(float w, float h) {
        MobileHudLayout l = layout(w, h);
        String ctx = w + "x" + h;
        HudLayout.Rect s = screen(w, h);
        List<HudLayout.Rect> rects = restRects(l);
        List<MobileHudLayout.Circle> circles = allCircles(l);
        noOverlaps(ctx, rects, circles);
        for (HudLayout.Rect r : rects) assertTrue(s.encloses(r), ctx + ": poza ekranem " + r);
        for (MobileHudLayout.Circle c : circles) assertTrue(s.encloses(c.bounds()), ctx + ": poza ekranem " + c);
        // paski i ikony w ramce, ikony nie nachodza na siebie ani na paski
        assertTrue(l.frame.encloses(l.hpBar) && l.frame.encloses(l.mpBar), ctx);
        assertFalse(l.hpBar.overlaps(l.mpBar), ctx);
        for (int i = 0; i < MobileHudLayout.ICONS; i++) {
            assertTrue(l.frame.encloses(l.icons[i]), ctx + ": ikona " + i + " poza ramka");
            assertFalse(l.icons[i].overlaps(l.hpBar) || l.icons[i].overlaps(l.mpBar), ctx + ": ikona " + i + " na paskach");
            for (int j = i + 1; j < MobileHudLayout.ICONS; j++) assertFalse(l.icons[i].overlaps(l.icons[j]), ctx);
            assertTrue(s.encloses(l.iconHits[i]), ctx + ": pole ikony " + i + " poza ekranem");
            assertFalse(l.iconHits[i].overlaps(l.minimap), ctx + ": pole ikony " + i + " na minimapie");
            assertFalse(l.iconHits[i].overlaps(l.battle), ctx + ": pole ikony " + i + " na liscie walki");
        }
        // pole dotyku ikony trafia w nia sama (sasiednie pola stykaja sie, wygrywa blizszy srodek)
        for (int i = 0; i < MobileHudLayout.ICONS; i++) {
            assertEquals(i, l.iconAt(l.icons[i].cx(), l.icons[i].cy()), ctx);
            assertEquals(i, l.iconAt(l.icons[i].x + 0.5f, l.icons[i].y + 0.5f), ctx);
            assertEquals(i, l.iconAt(l.icons[i].right() - 0.5f, l.icons[i].top() - 0.5f), ctx);
        }
        assertFalse(l.statusRow.overlaps(l.minimap) || l.statusRow.overlaps(l.frame) || l.statusRow.overlaps(l.battle), ctx);
        // pasek celu pod ramka (200 + 12 dp na srodku) nie wchodzi na liste walki
        assertTrue(w / 2f + 106 <= l.battle.x, ctx + ": pasek celu na liscie walki");
        // przycisk luku i mikstury przechodzi przez pole dotyku przycisku chwilowego, rzedu i joysticka tylko u siebie
        for (MobileHudLayout.Circle a : circles) {
            for (MobileHudLayout.Circle b : circles) {
                if (a != b) assertFalse(b.hit(a.x, a.y), ctx + ": srodek " + a + " w polu " + b);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"800,360", "780,360", "852,393", "960,600"})
    void touchTargetsAreAtLeast36dp(float w, float h) {
        MobileHudLayout l = layout(w, h);
        String ctx = w + "x" + h;
        for (MobileHudLayout.Circle c : allCircles(l)) assertTrue(2 * c.hitR() >= MobileHudLayout.MIN_TOUCH, ctx + ": " + c);
        for (MobileHudLayout.Circle c : l.fixedCircles()) {
            // pole dotyku (co najmniej 36 dp) nie wchodzi na inny przycisk
            for (MobileHudLayout.Circle o : allCircles(l)) {
                if (o == c) continue;
                float dx = c.x - o.x, dy = c.y - o.y;
                assertTrue(Math.sqrt(dx * dx + dy * dy) >= c.hitR() + o.r - 0.01f, ctx + ": pole " + c + " na " + o);
            }
        }
        for (HudLayout.Rect r : l.iconHits) assertTrue(r.w >= MobileHudLayout.MIN_TOUCH && r.h >= MobileHudLayout.MIN_TOUCH, ctx + " " + r);
        for (HudLayout.Rect r : new HudLayout.Rect[] {l.chatTabHit, l.chatHandleHit, l.minimap}) {
            assertTrue(r.w >= MobileHudLayout.MIN_TOUCH && r.h >= MobileHudLayout.MIN_TOUCH, ctx + " " + r);
        }
        // wiersz listy walki: co najmniej 20 dp wysokosci, cala szerokosc listy
        assertTrue(MobileHudLayout.BATTLE_ROW >= 20, "wiersz walki " + MobileHudLayout.BATTLE_ROW);
        for (HudLayout.Rect r : new HudLayout.Rect[] {l.battle}) {
            assertTrue(r.w >= MobileHudLayout.MIN_TOUCH && r.h >= MobileHudLayout.MIN_TOUCH, ctx + " " + r);
        }
        assertTrue(l.joystick.r * 2 >= MobileHudLayout.MIN_TOUCH);
        // pole dotyku zakladki czatu nie siega joysticka, rzedu ani luku
        for (MobileHudLayout.Circle c : allCircles(l)) assertFalse(c.overlaps(l.chatTabHit), ctx + ": " + c);
    }

    @ParameterizedTest
    @CsvSource({"800,360", "780,360", "852,393", "960,600"})
    void geometryMatchesMockup(float w, float h) {
        MobileHudLayout l = layout(w, h);
        // ramka y 2..42 od gory, ikony 32 dp co 35 dp z marginesem 8 dp, paski od x = 46
        assertEquals(h - 42, l.frame.y, 1e-3);
        assertEquals(40, l.frame.h, 1e-3);
        assertEquals(w - 8, l.icons[MobileHudLayout.ICONS - 1].right(), 1e-3);
        assertEquals(35, l.icons[1].x - l.icons[0].x, 1e-3);
        assertEquals(46, l.hpBar.x, 1e-3);
        assertEquals(l.icons[0].x - 8, l.hpBar.right(), 1e-3);
        assertTrue(l.hpBar.y > l.mpBar.y, "HP nad mana");
        // minimapa 120 dp pod ramka przy prawym brzegu, lista walki 120 dp na lewo od niej, od tej samej wysokosci
        assertEquals(w - 128, l.minimap.x, 1e-3);
        assertEquals(h - 166, l.minimap.y, 1e-3);
        assertEquals(120, l.minimap.w, 1e-3);
        assertEquals(120, l.battle.w, 1e-3);
        assertEquals(l.minimap.x - 4, l.battle.right(), 1e-3);
        assertEquals(l.minimap.top(), l.battle.top(), 1e-3);
        assertEquals(MobileHudLayout.BATTLE_MAX_ROWS, l.battleRowsVisible(), "lista walki miesci 6 wierszy");
        // luk: srodek (W-20, 20 od dolu), czary na promieniu 110 od dolnego brzegu do prawego
        assertEquals(w - 130, l.spells[0].x, 1e-3);
        assertEquals(20, l.spells[0].y, 1e-3);
        assertEquals(w - 20, l.spells[4].x, 1e-3);
        assertEquals(130, l.spells[4].y, 1e-3);
        for (MobileHudLayout.Circle c : l.potions) {
            assertEquals(58, Math.hypot(c.x - l.arcX, c.y - l.arcY), 1e-3);
        }
        // rzad Atak / Podazaj / Tarcza / PvP na y = 22 od dolu (od prawej)
        assertEquals(4, l.row.length);
        for (int i = 0; i < l.row.length; i++) {
            assertEquals(22, l.row[i].y, 1e-3);
            assertEquals(w - 20 - 160 - i * 42, l.row[i].x, 1e-3);
        }
        assertTrue(l.row[MobileHudLayout.ROW_PVP].x < l.row[MobileHudLayout.ROW_SHIELD].x
                && l.row[MobileHudLayout.ROW_SHIELD].x < l.row[MobileHudLayout.ROW_FOLLOW].x
                && l.row[MobileHudLayout.ROW_FOLLOW].x < l.row[MobileHudLayout.ROW_ATTACK].x, "PvP, Tarcza, Podazaj, Atak");
        assertEquals(78, l.joystick.x, 1e-3);
        assertEquals(78, l.joystick.y, 1e-3);
        assertEquals(54, l.joystick.r, 1e-3);
    }

    @ParameterizedTest
    @CsvSource({"800,360", "780,360", "852,393", "960,600"})
    void chatAndWindowsDoNotCoverControls(float w, float h) {
        MobileHudLayout l = layout(w, h);
        String ctx = w + "x" + h;
        HudLayout.Rect s = screen(w, h);
        // rozwiniety czat: miedzy joystickiem a rzedem, nie zaslania luku, rzedu, joysticka ani przyciskow chwilowych
        List<HudLayout.Rect> chat = new ArrayList<>(List.of(l.frame, l.minimap, l.battle, l.chatPanel, l.chatHandle));
        noOverlaps(ctx + " czat", chat, allCircles(l));
        assertEquals(MobileHudLayout.CHAT_X, l.chatPanel.x, 1e-3);
        assertEquals(l.rowLeft() - 8, l.chatPanel.right(), 1e-3);
        assertTrue(l.chatPanel.w >= 300, ctx + ": czat za waski " + l.chatPanel);
        assertTrue(s.encloses(l.chatPanel) && s.encloses(l.chatHandle), ctx);
        // okno kategorii: pod ramka, na srodku, nie na minimapie, liscie walki, luku, rzedzie, joysticku i czacie
        for (boolean chatOpen : new boolean[] {false, true}) {
            for (boolean small : new boolean[] {false, true}) {
                HudLayout.Rect win = l.window(chatOpen, small, new HudLayout.Rect());
                String c2 = ctx + " okno czat=" + chatOpen + " male=" + small;
                assertEquals(MobileHudLayout.WIN_W, win.w, 1e-3);
                // na srodku, a na waskim ekranie przesuniete w lewo tuz przed liste walki
                if (Math.round((w - MobileHudLayout.WIN_W) / 2f) + MobileHudLayout.WIN_W + 6 <= l.battle.x) assertEquals(w / 2f, win.cx(), 0.51);
                else assertEquals(l.battle.x - 6, win.right(), 0.51);
                assertTrue(Math.abs(win.cx() - w / 2f) <= 40, c2 + ": okno daleko od srodka");
                assertTrue(win.top() <= l.frame.y, c2 + ": na ramce");
                assertTrue(s.encloses(win), c2);
                List<HudLayout.Rect> rs = new ArrayList<>(List.of(l.minimap, l.battle, win));
                if (chatOpen) {
                    rs.add(l.chatPanel);
                    rs.add(l.chatHandle);
                } else {
                    rs.add(l.chatTab);
                }
                noOverlaps(c2, rs, allCircles(l));
            }
        }
        HudLayout.Rect pick = l.picker(new HudLayout.Rect());
        assertTrue(s.encloses(pick), ctx + ": wybor poza ekranem " + pick);
        assertTrue(pick.top() <= l.frame.y, ctx + ": wybor na ramce");
        assertTrue(l.pickerRows(pick) >= 5, ctx + ": za malo wierszy wyboru");
        for (MobileHudLayout.Circle c : l.spells) assertFalse(c.overlaps(pick), ctx + ": wybor zaslania " + c);
    }
}
