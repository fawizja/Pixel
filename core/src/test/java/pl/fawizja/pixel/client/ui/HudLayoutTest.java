package pl.fawizja.pixel.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HudLayoutTest {

    private static HudLayout layout(int px, int py, float scale, int actions, boolean vip, boolean skills) {
        HudLayout l = new HudLayout();
        l.compute(px / scale, py / scale, scale, actions, vip, skills, 8);
        return l;
    }

    @ParameterizedTest
    @CsvSource({"1280,720,1", "1920,1080,1", "2400,1080,2", "1280,720,2", "1366,768,1", "2340,1080,2.75", "800,600,1"})
    void panelsDoNotOverlapAndStayOnScreen(int px, int py, float scale) {
        for (int actions = 0; actions <= 7; actions += 7) {
            for (boolean vip : new boolean[] {true, false}) {
                for (boolean skills : new boolean[] {true, false}) {
                    HudLayout l = layout(px, py, scale, actions, vip, skills);
                    HudLayout.Rect screen = new HudLayout.Rect();
                    screen.x = 0;
                    screen.y = 0;
                    screen.w = px / scale;
                    screen.h = py / scale;
                    HudLayout.Rect[] all = l.fixedPanels();
                    String ctx = px + "x" + py + "@" + scale + " actions=" + actions + " vip=" + vip + " skills=" + skills;
                    for (int i = 0; i < all.length; i++) {
                        assertTrue(screen.encloses(all[i]), ctx + ": poza ekranem " + i + " " + all[i]);
                        for (int j = i + 1; j < all.length; j++) {
                            assertFalse(all[i].overlaps(all[j]), ctx + ": nakladaja sie " + i + " " + all[i] + " i " + j + " " + all[j]);
                        }
                    }
                    // kolumny: lewa trzyma minimape, VIP i umiejetnosci; prawa ekwipunek, przyciski, walke i pasek
                    for (HudLayout.Rect r : new HudLayout.Rect[] {l.vip, l.skills, l.minimap}) assertTrue(l.left.encloses(r), ctx + " lewa " + r);
                    for (HudLayout.Rect r : new HudLayout.Rect[] {l.equipment, l.battle, l.hotbar, l.actions}) assertTrue(l.right.encloses(r), ctx + " prawa " + r);
                    for (HudLayout.Rect r : l.toggles) assertTrue(l.right.encloses(r), ctx + " przycisk " + r);
                    assertFalse(l.left.overlaps(l.right), ctx);
                    // gorny pasek i konsola od kolumny do kolumny, widok gry miedzy nimi
                    assertTrue(l.view.x >= l.left.right() && l.view.right() <= l.right.x, ctx);
                    assertTrue(l.console.y + l.console.h <= l.view.y, ctx);
                    assertTrue(l.topBar.y >= l.view.top(), ctx);
                    assertTrue(l.battle.h >= HudLayout.HEADER + (l.compact && actions > 5 ? 2 : 3) * 18, ctx + " walka " + l.battle);
                    assertTrue(l.console.h >= 3 * 13 + 8 + 16, ctx + " konsola " + l.console);
                }
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"1280,720,1", "1920,1080,1", "2400,1080,2", "1280,720,2"})
    void gameViewIsExactly15x11WholePixelTiles(int px, int py, float scale) {
        HudLayout l = layout(px, py, scale, 2, true, true);
        assertEquals(Math.floor(l.tilePixels), l.tilePixels, 0f);
        assertEquals(15 * l.tilePixels, l.view.w * scale, 0.01f);
        assertEquals(11 * l.tilePixels, l.view.h * scale, 0.01f);
        // widok wysrodkowany miedzy kolumnami (do piksela)
        float leftGap = l.view.x - l.left.right(), rightGap = l.right.x - l.view.right();
        assertEquals(leftGap, rightGap, 1f / scale + 0.01f);
        // GameScreen liczy z prostokata to samo pole
        float ppu = (float) Math.floor(Math.min(l.view.w * scale / 15f, l.view.h * scale / 11f));
        assertEquals(l.tilePixels, ppu, 0f);
    }

    @Test
    void desktopKeepsBigTilesAndPhoneIsCompact() {
        HudLayout desk = layout(1280, 720, 1, 2, true, true);
        assertFalse(desk.compact);
        assertTrue(desk.tilePixels >= 48, "pole " + desk.tilePixels);
        HudLayout full = layout(1920, 1080, 1, 2, true, true);
        assertTrue(full.tilePixels >= 80, "pole " + full.tilePixels);
        HudLayout phone = layout(1280, 720, 2, 2, true, false);
        assertTrue(phone.compact);
        // przyciski na telefonie nie mniejsze niz ~44 px ekranu
        assertTrue(phone.toggleSize * 2 >= 44, "przycisk " + phone.toggleSize);
        assertTrue(phone.hotbarCell * 2 >= 52, "pasek " + phone.hotbarCell);
    }

    @Test
    void hotbarIsBiggerThanBeforeAndActionRowsWrap() {
        HudLayout l = layout(1280, 720, 1, 7, true, true);
        assertTrue(l.hotbarCell > 40, "pole paska " + l.hotbarCell);
        assertEquals(2, l.actionRows);
        HudLayout.Rect a = l.actionCell(0, 7, new HudLayout.Rect());
        HudLayout.Rect b = l.actionCell(5, 7, new HudLayout.Rect());
        assertTrue(b.top() <= a.y, "drugi rzad pod pierwszym");
        assertTrue(l.actions.encloses(a) && l.actions.encloses(b));
        HudLayout.Rect h0 = l.hotbarCell(0, new HudLayout.Rect()), h9 = l.hotbarCell(9, new HudLayout.Rect());
        assertTrue(l.hotbar.encloses(h0) && l.hotbar.encloses(h9));
    }

    @Test
    void collapsingWindowsFreesSpace() {
        HudLayout open = layout(1280, 720, 1, 2, true, true);
        HudLayout closed = layout(1280, 720, 1, 2, true, false);
        assertEquals(HudLayout.HEADER, closed.skills.h, 0.01f);
        assertTrue(closed.vip.h > open.vip.h);
        assertTrue(open.skills.h >= HudLayout.HEADER + 8 * HudLayout.SKILL_ROW, "cale umiejetnosci " + open.skills);
        HudLayout noVip = layout(1280, 720, 1, 2, false, true);
        assertEquals(HudLayout.HEADER, noVip.vip.h, 0.01f);
        assertTrue(noVip.skills.top() <= noVip.vip.y);
    }
}
