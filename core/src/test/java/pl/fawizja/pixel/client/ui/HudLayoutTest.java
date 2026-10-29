package pl.fawizja.pixel.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HudLayoutTest {

    /** Telefony z przegladu zrzutow (2026-10-29) w poziomie, tablet i komputer: px, px, skala. */
    private static final String[] PHONES = {"2340,1080,2.625", "2340,1080,3.0", "2400,1080,2.75", "2400,1080,2.625",
        "1600,720,2.0", "1280,720,2.0", "2532,1170,3.0"};

    private static HudLayout layout(int px, int py, float scale, int actions, boolean vip, Boolean skills) {
        HudLayout l = new HudLayout();
        l.compute(px / scale, py / scale, scale, actions, vip, 2, skills, 10);
        return l;
    }

    @ParameterizedTest
    @CsvSource({"1280,720,1", "1920,1080,1", "2400,1080,2", "1280,720,2", "1366,768,1", "2340,1080,2.75", "800,600,1",
        "2340,1080,2.625", "2340,1080,3.0", "2400,1080,2.625", "1600,720,2.0", "2532,1170,3.0", "2000,1200,2.0"})
    void panelsDoNotOverlapAndStayOnScreen(int px, int py, float scale) {
        for (int actions = 0; actions <= 7; actions++) {
            for (boolean vip : new boolean[] {true, false}) {
                for (Boolean skills : new Boolean[] {true, false, null}) {
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
                    for (HudLayout.Rect r : new HudLayout.Rect[] {l.equipment, l.battle, l.hotbar}) assertTrue(l.right.encloses(r), ctx + " prawa " + r);
                    if (l.actionRows > 0) assertTrue(l.right.encloses(l.actions), ctx + " akcje " + l.actions);
                    for (HudLayout.Rect r : l.toggles) if (!r.empty()) assertTrue(l.right.encloses(r), ctx + " przycisk " + r);
                    assertFalse(l.left.overlaps(l.right), ctx);
                    // gorny pasek i konsola od kolumny do kolumny, widok gry miedzy nimi
                    assertTrue(l.view.x >= l.left.right() && l.view.right() <= l.right.x, ctx);
                    assertTrue(l.console.y + l.console.h <= l.view.y, ctx);
                    assertTrue(l.topBar.y >= l.view.top(), ctx);
                    assertTrue(l.battleRowsVisible() >= (actions > 5 || (l.foldLogout && actions > 3) ? 2 : 3), ctx + " walka " + l.battle);
                    assertTrue(l.console.h >= 2 * l.conRow + 8 + l.tabH, ctx + " konsola " + l.console);
                    // kazdy przycisk ma miejsce: przelaczniki (bez schowanego wyjscia) i akcje obok ekwipunku albo w rzedzie
                    int placed = 0;
                    java.util.Set<Integer> slots = new java.util.HashSet<>();
                    for (int i = 0; i < HudLayout.TOGGLE_COUNT; i++) {
                        int s = l.toggleSlot[i];
                        if (s < 0) {
                            assertTrue(l.folded[i] && (i == HudLayout.LOGOUT_TOGGLE || i == HudLayout.TORCH_TOGGLE), ctx + " przycisk " + i + " bez pola");
                            continue;
                        }
                        assertFalse(l.toggles[s].empty(), ctx);
                        assertTrue(slots.add(s), ctx + " dwa przyciski w polu " + s);
                    }
                    for (int s : l.actionSlot) {
                        if (s < 0) placed++;
                        else assertTrue(slots.add(s) && !l.toggles[s].empty(), ctx + " akcja w polu " + s);
                    }
                    assertEquals(l.rowActions, placed, ctx);
                }
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"1280,720,1", "1920,1080,1", "2400,1080,2", "1280,720,2", "2340,1080,2.625", "2340,1080,3.0",
        "2400,1080,2.75", "2400,1080,2.625", "1600,720,2.0", "2532,1170,3.0", "2000,1200,2.0"})
    void gameViewIsExactly15x11WholePixelTiles(int px, int py, float scale) {
        HudLayout l = layout(px, py, scale, 2, true, null);
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

    /** Telefon: przyciski, pola paska i ekwipunku >= 36 dp, lista walki >= 5 wierszy, bez pustych pasow obok widoku. */
    @Test
    void phonesHaveBigTouchTargetsAndAFullBattleList() {
        for (String p : PHONES) {
            String[] v = p.split(",");
            int px = Integer.parseInt(v[0]), py = Integer.parseInt(v[1]);
            float sc = Float.parseFloat(v[2]);
            for (int actions = 2; actions <= 3; actions++) {
                HudLayout l = layout(px, py, sc, actions, true, null);
                String ctx = p + " actions=" + actions;
                assertTrue(l.compact, ctx);
                assertTrue(l.toggleSize >= HudLayout.MIN_TOUCH, ctx + " przycisk " + l.toggleSize);
                assertTrue(l.hotbarCell >= HudLayout.MIN_TOUCH, ctx + " pasek " + l.hotbarCell);
                assertTrue(l.eqCell >= HudLayout.MIN_TOUCH, ctx + " ekwipunek " + l.eqCell);
                for (HudLayout.Rect r : l.toggles) if (!r.empty()) assertTrue(r.w >= HudLayout.MIN_TOUCH && r.h >= HudLayout.MIN_TOUCH, ctx + " " + r);
                // ATAK i UZYJ zawsze obok ekwipunku, nie w rzedzie zabierajacym miejsce liscie walki
                assertTrue(l.actionSlot[0] >= 0 && l.actionSlot[1] >= 0, ctx + " ATAK/UZYJ obok ekwipunku");
                if (actions == 2) assertEquals(0, l.actionRows, ctx);
                // lista walki: co najmniej 5 wierszy na telefonie 360 dp, 6 na wyzszym
                assertTrue(l.battleRowsVisible() >= (py / sc < 380 ? 5 : 6), ctx + " walka " + l.battleRowsVisible() + " " + l.battle);
                // reszta szerokosci w kolumnach: miedzy widokiem a kolumna najwyzej pol pola
                float gap = l.view.x - l.left.right() - HudLayout.PAD - HudLayout.BORDER;
                assertTrue(gap <= l.tilePixels / sc / 2f + 1f, ctx + " pusty pas " + gap);
            }
            // umiejetnosci rozwiniete domyslnie (mieszcza sie), VIP bez znajomych tylko na naglowek i napis
            HudLayout l = layout(px, py, sc, 2, true, null);
            assertTrue(l.skillsShown, p + " umiejetnosci zwiniete");
            assertTrue(l.skills.h >= l.header + 10 * l.skillRow, p + " umiejetnosci " + l.skills);
            assertTrue(l.vip.h <= l.left.h * 0.3f, p + " VIP " + l.vip);
            assertTrue(l.minimap.w >= 90, p + " minimapa " + l.minimap);
        }
    }

    /** Prog czcionki: wiersze okien mieszcza 7 dp wielkiej litery, podpisy przyciskow bez ikony mieszcza sie przy progu. */
    @Test
    void textNeverGoesBelowTheFontFloor() throws Exception {
        assertTrue(HudLayout.MIN_FONT * HudLayout.FONT_CAP >= 7f - 0.01f, "prog " + HudLayout.MIN_FONT * HudLayout.FONT_CAP);
        Map<Integer, Integer> adv = advances();
        for (String p : PHONES) {
            String[] v = p.split(",");
            float sc = Float.parseFloat(v[2]);
            HudLayout l = layout(Integer.parseInt(v[0]), Integer.parseInt(v[1]), sc, 3, true, null);
            float cap = HudLayout.MIN_FONT * HudLayout.FONT_CAP;
            for (float row : new float[] {l.skillRow, l.vipRow, l.battleRow, l.conRow, l.tabH}) assertTrue(row >= cap + 4, p + " wiersz " + row);
            assertTrue(l.header >= HudLayout.FONT_CAP * 0.75f + 6, p + " naglowek " + l.header);
            // przyciski bez ikony (Użyj, Mów, Weź) i podpisy przelacznikow mieszcza sie w polu przy progu
            for (String s : new String[] {"Użyj", "Mów", "Weź", "Bieg", "Tarcza", "Zadania", "Poch.", "Wyjdź", "PvP!", "Opcje", "Atak", "Łów", "Koń"}) {
                float wpx = width(adv, s) * HudLayout.MIN_FONT;
                assertTrue(wpx <= l.toggleSize - 3, p + " podpis " + s + " " + wpx + " > " + (l.toggleSize - 3));
            }
            // naglowki okien w kolumnach przy skali 0.75
            for (String s : new String[] {"Umiejętności", "VIP 99/99", "Walka (12)"}) {
                assertTrue(width(adv, s) * 0.75f + 24 <= l.left.w - 2 * HudLayout.PAD || s.startsWith("Walka"), p + " " + s);
            }
            // umiejetnosci: najdluzsza nazwa i wartosc w jednym wierszu przy skali okien (0.66)
            assertTrue(width(adv, "Doświadczenie") * 0.66f + width(adv, "100%") * 0.66f + 20 <= l.skills.w, p + " umiejetnosci " + l.skills.w);
        }
    }

    @Test
    void desktopKeepsBigTilesAndPhoneIsCompact() {
        HudLayout desk = layout(1280, 720, 1, 2, true, true);
        assertFalse(desk.compact);
        assertTrue(desk.tilePixels >= 48, "pole " + desk.tilePixels);
        assertTrue(desk.toggleSize >= 34, "przycisk " + desk.toggleSize);
        assertEquals(2, desk.toggleCols);
        assertTrue(desk.battleRowsVisible() >= 5, "walka " + desk.battleRowsVisible());
        HudLayout tablet = layout(2000, 1200, 2, 2, true, null);
        assertFalse(tablet.compact);
        assertTrue(tablet.toggleSize >= HudLayout.MIN_TOUCH && tablet.eqCell >= HudLayout.MIN_TOUCH, "tablet " + tablet.toggleSize);
        HudLayout full = layout(1920, 1080, 1, 2, true, true);
        assertTrue(full.tilePixels >= 80, "pole " + full.tilePixels);
        HudLayout phone = layout(1280, 720, 2, 2, true, false);
        assertTrue(phone.compact);
        assertTrue(phone.toggleSize * 2 >= 72, "przycisk " + phone.toggleSize);
        assertTrue(phone.hotbarCell * 2 >= 72, "pasek " + phone.hotbarCell);
        // waski telefon 16:9: dwie kolumny przyciskow, wyjscie w menu "Opcje"
        assertEquals(2, phone.toggleCols);
        assertTrue(phone.foldLogout);
        // szeroki telefon: trzy kolumny, wszystkie przelaczniki widoczne
        HudLayout wide = layout(2340, 1080, 3f, 2, true, null);
        assertEquals(3, wide.toggleCols);
        assertFalse(wide.foldLogout);
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
        // telefon: zwiniete umiejetnosci oddaja miejsce minimapie albo VIP
        HudLayout phoneOpen = layout(2340, 1080, 3f, 2, true, null), phoneClosed = layout(2340, 1080, 3f, 2, true, false);
        assertEquals(phoneClosed.header, phoneClosed.skills.h, 0.01f);
        assertTrue(phoneClosed.vip.h + phoneClosed.minimap.h > phoneOpen.vip.h + phoneOpen.minimap.h);
    }

    // ------------------------------------------------------------------ szerokosci liter z lsans-15.fnt (bez GL)

    private static final String POLISH = "ąćęłńóśźżĄĆĘŁŃÓŚŹŻ", BASE = "acelnoszzACELNOSZZ";

    private static Map<Integer, Integer> advances() throws Exception {
        Map<Integer, Integer> adv = new HashMap<>();
        try (InputStream in = HudLayoutTest.class.getClassLoader().getResourceAsStream(PolishFont.FNT)) {
            assertNotNull(in, "czcionka " + PolishFont.FNT);
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            Pattern ch = Pattern.compile("char id=(\\d+).*xadvance=(-?\\d+)");
            for (String line; (line = r.readLine()) != null; ) {
                Matcher m = ch.matcher(line);
                if (m.find()) adv.put(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
            }
        }
        return adv;
    }

    /** Szerokosc napisu w pikselach czcionki (polskie litery jak litera bazowa, +1 na "ł" jak w PolishFont). */
    private static float width(Map<Integer, Integer> adv, String s) {
        float w = 0;
        for (char c : s.toCharArray()) {
            int i = POLISH.indexOf(c);
            char b = i >= 0 ? BASE.charAt(i) : c;
            w += adv.getOrDefault((int) b, 9) + (c == 'ł' ? 1 : 0);
        }
        return w;
    }
}
