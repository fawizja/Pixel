package pl.fawizja.pixel.client.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import java.util.ArrayList;
import java.util.List;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.wheel.DestinyWheel;
import pl.fawizja.pixel.wheel.WheelDomain;
import pl.fawizja.pixel.wheel.WheelGeometry;
import pl.fawizja.pixel.wheel.WheelSlice;

/**
 * Okno Kola Przeznaczenia w ramce, jak w Tibii (wlasciciel 2026-11-06: "kolo w ramce jak w prawdziwej Tibii"):
 * duze kolo z 36 polami jako wycinki pierscieni (50 w srodku, 75, 100, 150, 200 na zewnatrz), napelnienie kazdego
 * pola, naczynia klejnotow, postep objawien na oprawie i odznaki objawien w rogach, wolne punkty w piascie.
 * Dotkniecie pola pokazuje jego karte po prawej (przyciski +1/+10/do pelna/-1/oproznij), odznaki - karte objawienia,
 * piasta - podsumowanie. Tresc kart (wiersze WheelView) daje {@link WheelWindows}; okno zwraca identyfikatory akcji
 * (wheel:sel:KOD, wheel:rev:G, wheel:sum, wheel:add..., wheel:gems:0, wheel:close), ktore Hud przekazuje dalej.
 * Rysunek proceduralny (ksztalty i napisy), bez grafik z Tibii.
 */
final class WheelPanel {
    private static final String[] TOOL_IDS = {"wheel:gems:0", "wheel:presets", "wheel:perks", "wheel:resetask"};
    private static final String[] TOOL_LABELS = {"Klejnoty", "Zestawy", "Premie", "Wyczyść"};

    /** Przycisk: id akcji, napis, czy aktywny. */
    private record Btn(String id, String label, boolean enabled) {}

    /** Wiersz tresci karty po zawinieciu: napis, skala, kolor, wciecie, stan po prawej, akcja wpisu. */
    private static final class Line {
        String text;
        float scale;
        Color color;
        float indent;
        String right;
        Color rightColor;
        String action;
        float y;      // gorna krawedz wzgledem poczatku tresci (w dol)
        float h;
        int item;
    }

    private final BitmapFont font;
    private final GlyphLayout glyphs = new GlyphLayout();
    private final WheelPanelLayout lay;
    private boolean open;
    private WheelSlice selected;
    private WheelDomain revelation;
    private String cardTitle = "";
    private List<String[]> cardRows = List.of();
    private float scroll;
    private final List<Line> lines = new ArrayList<>();
    private float contentH;
    private final List<Btn> cardBtns = new ArrayList<>();
    private boolean wheelEmpty = true;
    /** Przeciaganie tresci karty: palec, poczatek, przewiniecie na starcie, czy juz przewija. */
    private int dragPointer = -1;
    private float dragY0, scroll0;
    private boolean dragged;
    private float lastW, lastH;

    WheelPanel(BitmapFont font, boolean mobile) {
        this.font = font;
        this.lay = new WheelPanelLayout(mobile);
    }

    boolean isOpen() { return open; }

    void open() {
        open = true;
        dragPointer = -1;
    }

    void close() {
        open = false;
        dragPointer = -1;
    }

    WheelSlice selected() { return selected; }

    WheelDomain revelation() { return revelation; }

    void select(WheelSlice s) {
        if (s != selected || revelation != null) scroll = 0;
        selected = s;
        revelation = null;
    }

    void showRevelation(WheelDomain d) {
        if (d != revelation) scroll = 0;
        revelation = d;
        selected = null;
    }

    void showSummary() {
        if (selected != null || revelation != null) scroll = 0;
        selected = null;
        revelation = null;
    }

    void setCard(String title, List<String[]> rows) {
        cardTitle = title == null ? "" : title;
        cardRows = rows == null ? List.of() : new ArrayList<>(rows);
    }

    WheelPanelLayout layout(float w, float h) {
        if (w != lastW || h != lastH) {
            lay.compute(w, h);
            lastW = w;
            lastH = h;
        }
        return lay;
    }

    // ------------------------------------------------------------------ dotyk

    /** Dotkniecie przy otwartym oknie: akcja, "" (obsluzone bez akcji) albo wheel:close. */
    String touchDown(float px, float py, int pointer) {
        if (lay.closeHit.contains(px, py)) return "wheel:close";
        if (!lay.win.contains(px, py)) return "wheel:close";
        for (int i = 0; i < WheelPanelLayout.TOOLS; i++) {
            if (lay.tools[i].contains(px, py)) return toolEnabled(i) ? TOOL_IDS[i] : "";
        }
        if (selected != null) {
            for (int i = 0; i < cardBtns.size() && i < WheelPanelLayout.CARD_BUTTONS; i++) {
                if (lay.cardButtons[i].contains(px, py)) return cardBtns.get(i).enabled ? cardBtns.get(i).id : "";
            }
        }
        WheelDomain b = lay.badgeAt(px, py);
        if (b != null) return "wheel:rev:" + b.code;
        float dx = px - lay.cx, dy = py - lay.cy;
        if (WheelGeometry.inHub(dx, dy, lay.radius)) return "wheel:sum";
        WheelSlice s = WheelGeometry.sliceAt(dx, dy, lay.radius);
        if (s != null) return "wheel:sel:" + s.code();
        if (body().contains(px, py)) {
            dragPointer = pointer;
            dragY0 = py;
            scroll0 = scroll;
            dragged = false;
        }
        return "";
    }

    boolean owns(int pointer) { return open && pointer == dragPointer; }

    void touchDragged(float px, float py, int pointer) {
        if (pointer != dragPointer) return;
        float d = py - dragY0;
        if (Math.abs(d) > 6) dragged = true;
        if (dragged) scroll = clampScroll(scroll0 + d);
    }

    /** Puszczenie palca na tresci karty: akcja wpisu (np. drugie pole czaru) albo null. */
    String touchUp(float px, float py, int pointer) {
        if (pointer != dragPointer) return null;
        dragPointer = -1;
        if (dragged) return null;
        HudLayout.Rect body = body();
        if (!body.contains(px, py)) return null;
        float off = body.top() - py + scroll;
        for (Line l : lines) {
            if (l.action != null && off >= l.y - 2 && off <= l.y + l.h + 2) return l.action;
        }
        return null;
    }

    /** Kolko myszy nad karta: przewijanie. */
    boolean scrolled(float px, float py, float amount) {
        if (!open || !lay.win.contains(px, py)) return false;
        scroll = clampScroll(scroll + amount * 24);
        return true;
    }

    private float clampScroll(float s) { return Math.max(0, Math.min(s, Math.max(0, contentH - body().h))); }

    private HudLayout.Rect body() { return selected != null ? lay.cardBody : lay.cardBodyFull; }

    private boolean toolEnabled(int i) { return i != 3 || !wheelEmpty; }

    // ------------------------------------------------------------------ rysowanie

    private static Color rgb(int rgb, float a) {
        return new Color(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, a);
    }

    private static final Color FRAME_DARK = rgb(0x1a110a, 1f), FRAME_WOOD = rgb(0x3d2716, 1f), GOLD = rgb(0xd4ab52, 1f),
            GOLD_DARK = rgb(0x7d5b26, 1f), BRONZE = rgb(0x8f6a34, 1f), BG = rgb(0x1c1512, 1f), PLATE = rgb(0x110c0a, 1f),
            SIDE = rgb(0x261b14, 1f), TEXT = rgb(0xf1e6d2, 1f), MUTED = rgb(0xb3a089, 1f), LABEL = rgb(0xe8c477, 1f),
            GOOD = rgb(0x8ee07a, 1f), BAD = rgb(0xe8806a, 1f), BTN = rgb(0x4a3220, 1f), BTN_OFF = rgb(0x2c2420, 1f),
            BTN_ADD = rgb(0x2f5a2c, 1f), BTN_REM = rgb(0x5e2c22, 1f), LINK = rgb(0x30241b, 1f);

    private static Color dom(WheelDomain d, float k) {
        return new Color(((d.rgb >> 16) & 255) / 255f * k, ((d.rgb >> 8) & 255) / 255f * k, (d.rgb & 255) / 255f * k, 1f);
    }

    private static Color lighten(Color c, float t) {
        return new Color(c.r + (1 - c.r) * t, c.g + (1 - c.g) * t, c.b + (1 - c.b) * t, c.a);
    }

    /** Wycinek pierscienia od r0 do r1, katy a0..a1 w stopniach. */
    private static void segment(ShapeRenderer s, float cx, float cy, float r0, float r1, float a0, float a1) {
        int n = Math.max(2, (int) Math.ceil((a1 - a0) / 3f));
        float step = (a1 - a0) / n;
        for (int i = 0; i < n; i++) {
            double b0 = Math.toRadians(a0 + i * step), b1 = Math.toRadians(a0 + (i + 1) * step);
            float c0 = (float) Math.cos(b0), s0 = (float) Math.sin(b0), c1 = (float) Math.cos(b1), s1 = (float) Math.sin(b1);
            s.triangle(cx + c0 * r0, cy + s0 * r0, cx + c0 * r1, cy + s0 * r1, cx + c1 * r1, cy + s1 * r1);
            s.triangle(cx + c0 * r0, cy + s0 * r0, cx + c1 * r1, cy + s1 * r1, cx + c1 * r0, cy + s1 * r0);
        }
    }

    private static void ringLine(ShapeRenderer s, float cx, float cy, float r, float t, Color c) {
        s.setColor(c);
        segment(s, cx, cy, r - t / 2f, r + t / 2f, 0, 360);
    }

    private static void stroke(ShapeRenderer s, HudLayout.Rect r, float t, Color c) {
        s.setColor(c);
        s.rect(r.x, r.y, r.w, t);
        s.rect(r.x, r.top() - t, r.w, t);
        s.rect(r.x, r.y, t, r.h);
        s.rect(r.right() - t, r.y, t, r.h);
    }

    private static void diamond(ShapeRenderer s, float x, float y, float r) {
        s.triangle(x - r, y, x + r, y, x, y + r * 1.2f);
        s.triangle(x - r, y, x + r, y, x, y - r * 1.2f);
    }

    /** Ikona kola (zakladka u gory, ikona ramki na telefonie): 4 kolorowe cwiartki, zlota obwodka, ciemna piasta. */
    static void glyph(ShapeRenderer s, float x, float y, float r) {
        s.setColor(GOLD_DARK);
        s.circle(x, y, r + 1.2f, 28);
        s.setColor(GOLD);
        s.circle(x, y, r + 0.4f, 28);
        for (WheelDomain d : WheelDomain.values()) {
            s.setColor(dom(d, 0.95f));
            segment(s, x, y, r * 0.38f, r - 0.6f, WheelGeometry.quadrantStart(d) + 4, WheelGeometry.quadrantStart(d) + 86);
        }
        s.setColor(FRAME_DARK);
        s.circle(x, y, r * 0.36f, 16);
        s.setColor(GOLD);
        s.circle(x, y, r * 0.16f, 12);
    }

    /** Ksztalty okna (po nich {@link #drawText}); pc - postac, ktorej kolo rysujemy. */
    void drawShapes(ShapeRenderer s, PlayerCharacter pc, float w, float h) {
        layout(w, h);
        DestinyWheel wh = pc.wheel();
        wheelEmpty = wh.isEmpty();
        int av = pc.wheelPoints();
        buildCard(pc);
        // przyciemnienie gry pod oknem
        s.setColor(0, 0, 0, 0.45f);
        s.rect(0, 0, w, h);
        drawFrame(s);
        // tablica kola: ciemny kwadrat z obwodka i nitami w rogach
        HudLayout.Rect box = lay.wheelBox;
        s.setColor(PLATE);
        s.rect(box.x, box.y, box.w, box.h);
        stroke(s, box, 1.5f, GOLD_DARK);
        float cx = lay.cx, cy = lay.cy, R = lay.radius, rim = lay.rim;
        // cien pod oprawa
        s.setColor(0, 0, 0, 0.5f);
        s.circle(cx + 2, cy - 2, R + rim + 2, 120);
        // oprawa: braz, zloty brzeg, w srodku postep objawien kazdej cwiartki (do 1000 pkt)
        s.setColor(BRONZE);
        s.circle(cx, cy, R + rim + 2, 120);
        ringLine(s, cx, cy, R + rim + 1.5f, 1.5f, GOLD);
        s.setColor(rgb(0x2a1c10, 1f));
        segment(s, cx, cy, R + 2.5f, R + rim - 0.5f, 0, 360);
        for (WheelDomain d : WheelDomain.values()) {
            float base = WheelGeometry.quadrantStart(d);
            int pts = wh.domainPoints(d) + pc.wheelBonus().gems().revelation(d);
            float frac = Math.min(1f, pts / 1000f);
            s.setColor(dom(d, 0.22f));
            segment(s, cx, cy, R + 3.5f, R + rim - 1.5f, base + 1.5f, base + 88.5f);
            if (frac > 0) {
                s.setColor(dom(d, 1f));
                segment(s, cx, cy, R + 3.5f, R + rim - 1.5f, base + 1.5f, base + 1.5f + 87f * frac);
            }
            // znaczniki etapow 250 / 500
            s.setColor(GOLD);
            for (int t : WheelDomain.STAGE_POINTS) {
                if (t >= 1000) continue;
                float a = base + 1.5f + 87f * t / 1000f;
                segment(s, cx, cy, R + 2.5f, R + rim - 0.5f, a - 0.4f, a + 0.4f);
            }
        }
        // nity na osiach
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(i * 90);
            float x = cx + (float) Math.cos(a) * (R + rim / 2f + 1), y = cy + (float) Math.sin(a) * (R + rim / 2f + 1);
            s.setColor(GOLD_DARK);
            s.circle(x, y, rim * 0.55f, 16);
            s.setColor(GOLD);
            s.circle(x, y, rim * 0.38f, 16);
        }
        // tarcza kola
        s.setColor(rgb(0x070505, 1f));
        s.circle(cx, cy, R + 1.5f, 120);
        float gapPx = lay.mobile ? 1.6f : 2.2f;
        for (WheelSlice sl : WheelSlice.values()) drawSlice(s, wh, sl, av, cx, cy, R, gapPx, false);
        if (selected != null) drawSlice(s, wh, selected, av, cx, cy, R, gapPx, true);
        // osie cwiartek
        s.setColor(rgb(0x3a2a16, 1f));
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(i * 90);
            float c = (float) Math.cos(a), sn = (float) Math.sin(a);
            s.rectLine(cx + c * R * WheelGeometry.HUB, cy + sn * R * WheelGeometry.HUB, cx + c * R, cy + sn * R, gapPx + 0.6f);
        }
        drawVessels(s, wh, cx, cy, R);
        drawConvictionMarks(s, wh, cx, cy, R);
        // piasta
        float hr = R * WheelGeometry.HUB;
        s.setColor(BRONZE);
        s.circle(cx, cy, hr - 1, 64);
        s.setColor(GOLD);
        segment(s, cx, cy, hr - 2.5f, hr - 1f, 0, 360);
        s.setColor(rgb(0x1f1610, 1f));
        s.circle(cx, cy, hr - 4, 64);
        s.setColor(rgb(0x2b2017, 1f));
        s.circle(cx, cy - hr * 0.12f, hr * 0.72f, 48);
        if (selected == null && revelation == null) ringLine(s, cx, cy, hr - 4.5f, 1.2f, GOOD);
        if (av <= 0) {
            // nieaktywne kolo: pas z wymaganiami pod piasta
            float bh = lay.mobile ? 20 : 26, bw = R * 1.5f, by = cy - hr - 12 - bh;
            s.setColor(0, 0, 0, 0.72f);
            s.rect(cx - bw / 2f, by, bw, bh);
            s.setColor(rgb(0x9a3a2a, 1f));
            s.rect(cx - bw / 2f, by, bw, 1.5f);
            s.rect(cx - bw / 2f, by + bh - 1.5f, bw, 1.5f);
        }
        drawBadges(s, pc);
        drawSide(s);
    }

    /** Wyglad nowy: ramka okna z paczki Zidane42 (null = rama rysowana ksztaltami). */
    private ZidaneSkin zSkin;
    private SpriteBatch zBatch;

    /** Ramka okna z paczki (wyglad nowy) albo null; batch do narysowania jej w trakcie przebiegu ksztaltow. */
    void useSkin(ZidaneSkin skin, SpriteBatch batch) {
        zSkin = skin;
        zBatch = batch;
    }

    private void drawFrame(ShapeRenderer s) {
        HudLayout.Rect wr = lay.win;
        s.setColor(0, 0, 0, 0.5f);
        s.rect(wr.x + 4, wr.y - 4, wr.w, wr.h);
        if (zSkin != null && zBatch != null) {
            drawSkinFrame(s, wr);
            return;
        }
        s.setColor(FRAME_DARK);
        s.rect(wr.x, wr.y, wr.w, wr.h);
        s.setColor(FRAME_WOOD);
        s.rect(wr.x + 2, wr.y + 2, wr.w - 4, wr.h - 4);
        // dwie zlote linie ramy
        HudLayout.Rect r = new HudLayout.Rect();
        r.set(wr.x + 2, wr.y + 2, wr.w - 4, wr.h - 4);
        stroke(s, r, 1.5f, GOLD);
        float f = lay.frame;
        r.set(wr.x + f - 1.5f, wr.y + f - 1.5f, wr.w - 2 * f + 3, wr.h - 2 * f + 3);
        stroke(s, r, 1f, GOLD_DARK);
        s.setColor(BG);
        s.rect(wr.x + f, wr.y + f, wr.w - 2 * f, wr.h - 2 * f);
        // pasek tytulu
        HudLayout.Rect t = lay.title;
        s.setColor(rgb(0x3a2617, 1f));
        s.rect(t.x, t.y, t.w, t.h);
        s.setColor(rgb(0x4a321d, 1f));
        s.rect(t.x, t.y + t.h * 0.55f, t.w, t.h * 0.45f);
        s.setColor(GOLD_DARK);
        s.rect(t.x, t.y, t.w, 1.5f);
        // ozdobne romby: rogi ramy i konce paska tytulu
        float[][] corners = {{wr.x + f / 2f + 1, wr.y + f / 2f + 1}, {wr.right() - f / 2f - 1, wr.y + f / 2f + 1},
            {wr.x + f / 2f + 1, wr.top() - f / 2f - 1}, {wr.right() - f / 2f - 1, wr.top() - f / 2f - 1}};
        for (float[] c : corners) {
            s.setColor(GOLD);
            diamond(s, c[0], c[1], f * 0.9f);
            s.setColor(FRAME_DARK);
            diamond(s, c[0], c[1], f * 0.35f);
        }
        float mid = t.cx(), ty = t.cy();
        float tw = textWidth(pl.fawizja.pixel.wheel.WheelView.TITLE, titleScale());
        s.setColor(GOLD);
        diamond(s, mid - tw / 2f - 14, ty, 3.5f);
        diamond(s, mid + tw / 2f + 14, ty, 3.5f);
        s.setColor(GOLD_DARK);
        s.rect(mid - tw / 2f - 60, ty - 0.75f, 40, 1.5f);
        s.rect(mid + tw / 2f + 20, ty - 0.75f, 40, 1.5f);
        // zamkniecie
        HudLayout.Rect c = lay.close;
        s.setColor(rgb(0x7a2418, 1f));
        s.rect(c.x, c.y, c.w, c.h);
        stroke(s, c, 1f, GOLD);
        s.setColor(TEXT);
        float k = c.w * 0.28f;
        s.rectLine(c.cx() - k, c.cy() - k, c.cx() + k, c.cy() + k, 2f);
        s.rectLine(c.cx() - k, c.cy() + k, c.cx() + k, c.cy() - k, 2f);
    }

    /** Rama z paczki: okno 9-slice, pasek tytulu jak w klasycznej, zamkniecie na czerwonym przycisku z paczki. */
    private void drawSkinFrame(ShapeRenderer s, HudLayout.Rect wr) {
        HudLayout.Rect c = lay.close;
        s.end();
        zBatch.begin();
        zSkin.window(zBatch, wr.x, wr.y, wr.w, wr.h);
        zSkin.button(zBatch, ZidaneSkin.Btn.RED, c.x, c.y, c.w, c.h);
        zBatch.end();
        com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        com.badlogic.gdx.Gdx.gl.glBlendFunc(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);
        s.begin(ShapeRenderer.ShapeType.Filled);
        HudLayout.Rect t = lay.title;
        float mid = t.cx(), ty = t.cy();
        float tw = textWidth(pl.fawizja.pixel.wheel.WheelView.TITLE, titleScale());
        s.setColor(GOLD);
        diamond(s, mid - tw / 2f - 14, ty, 3.5f);
        diamond(s, mid + tw / 2f + 14, ty, 3.5f);
        s.setColor(GOLD_DARK);
        s.rect(mid - tw / 2f - 60, ty - 0.75f, 40, 1.5f);
        s.rect(mid + tw / 2f + 20, ty - 0.75f, 40, 1.5f);
        s.setColor(TEXT);
        float k = c.w * 0.28f;
        s.rectLine(c.cx() - k, c.cy() - k, c.cx() + k, c.cy() + k, 2f);
        s.rectLine(c.cx() - k, c.cy() + k, c.cx() + k, c.cy() - k, 2f);
    }

    private void drawSlice(ShapeRenderer s, DestinyWheel w, WheelSlice sl, int av, float cx, float cy, float R, float gapPx, boolean outline) {
        int ring = WheelGeometry.ring(sl);
        float r0 = R * WheelGeometry.innerRadius(ring), r1 = R * WheelGeometry.outerRadius(ring);
        float a0 = WheelGeometry.startDeg(sl), a1 = a0 + WheelGeometry.sweepDeg(sl);
        float gDeg = (float) Math.toDegrees(gapPx / ((r0 + r1) / 2f)) / 2f;
        float g = gapPx / 2f;
        if (outline) {
            // wybrane pole: zlota obwodka i jasniejsze wnetrze
            s.setColor(GOLD);
            segment(s, cx, cy, r0 + g - 1.5f, r1 - g + 1.5f, a0 + gDeg - 0.8f, a1 - gDeg + 0.8f);
            s.setColor(rgb(0xfff2c0, 1f));
            segment(s, cx, cy, r0 + g - 0.5f, r1 - g + 0.5f, a0 + gDeg - 0.3f, a1 - gDeg + 0.3f);
            float inset = 2.5f, iDeg = (float) Math.toDegrees(inset / ((r0 + r1) / 2f));
            fillSlice(s, w, sl, av, cx, cy, r0 + g + inset, r1 - g - inset, a0 + gDeg + iDeg, a1 - gDeg - iDeg, true);
            return;
        }
        fillSlice(s, w, sl, av, cx, cy, r0 + g, r1 - g, a0 + gDeg, a1 - gDeg, false);
    }

    private static void fillSlice(ShapeRenderer s, DestinyWheel w, WheelSlice sl, int av, float cx, float cy, float r0, float r1,
                                  float a0, float a1, boolean sel) {
        int p = w.points(sl);
        int cap = sl.capacity();
        boolean open = av > 0 && w.open(sl, av);
        WheelDomain d = sl.domain;
        float lift = sel ? 0.12f : 0f;
        if (p >= cap) {
            s.setColor(lighten(dom(d, 0.92f), lift));
            segment(s, cx, cy, r0, r1, a0, a1);
            // polysk na zewnetrznej krawedzi pelnego pola
            s.setColor(lighten(dom(d, 1f), 0.35f + lift));
            segment(s, cx, cy, r1 - Math.max(1.5f, (r1 - r0) * 0.16f), r1, a0, a1);
            return;
        }
        Color empty = open || p > 0 ? dom(d, 0.36f) : lighten(dom(d, 0.17f), 0.04f);
        s.setColor(lighten(empty, lift));
        segment(s, cx, cy, r0, r1, a0, a1);
        if (p > 0) {
            float fr = r0 + (r1 - r0) * p / (float) cap;
            s.setColor(lighten(dom(d, 0.8f), lift));
            segment(s, cx, cy, r0, fr, a0, a1);
            s.setColor(lighten(dom(d, 1f), 0.45f));
            segment(s, cx, cy, Math.max(r0, fr - 1.2f), fr, a0, a1);
        }
    }

    private void drawVessels(ShapeRenderer s, DestinyWheel w, float cx, float cy, float R) {
        for (WheelSlice sl : WheelSlice.values()) {
            if (sl.conviction != WheelSlice.Conv.VESSEL) continue;
            int ring = WheelGeometry.ring(sl);
            float th = R * (WheelGeometry.outerRadius(ring) - WheelGeometry.innerRadius(ring));
            float size = Math.max(3.5f, th * 0.2f);
            float[] c = WheelGeometry.center(sl);
            boolean gem = w.gems().socketed(sl.domain) != null;
            boolean full = w.points(sl) >= sl.capacity();
            float x = cx + c[0] * R, y = cy + c[1] * R + (w.points(sl) > 0 && !full || sl == selected ? size * 0.8f : 0);
            s.setColor(rgb(0x0b0806, 1f));
            diamond(s, x, y, size + 1.8f);
            s.setColor(full ? GOLD : GOLD_DARK);
            diamond(s, x, y, size + 0.9f);
            if (gem) {
                Color g = full ? lighten(dom(sl.domain, 1f), 0.45f) : dom(sl.domain, 0.55f);
                s.setColor(g);
                diamond(s, x, y, size);
                s.setColor(1, 1, 1, full ? 0.75f : 0.3f);
                diamond(s, x - size * 0.3f, y + size * 0.35f, size * 0.28f);
            } else {
                s.setColor(rgb(0x1a1410, 1f));
                diamond(s, x, y, size);
            }
        }
    }

    /**
     * Znaki przekonania na polach (jak ikony pol w Tibii): czar - gwiazdka, umiejetnosc - miecz, kradziez zycia -
     * czerwona kropla, many - niebieska, pasywka profesji - duza gwiazda w kole. Jasne, gdy pole pelne.
     */
    private void drawConvictionMarks(ShapeRenderer s, DestinyWheel w, float cx, float cy, float R) {
        for (WheelSlice sl : WheelSlice.values()) {
            if (sl.conviction == WheelSlice.Conv.VESSEL) continue;
            int ring = WheelGeometry.ring(sl);
            float th = R * (WheelGeometry.outerRadius(ring) - WheelGeometry.innerRadius(ring));
            float k = Math.max(3f, th * 0.2f);
            float[] c = WheelGeometry.center(sl);
            int p = w.points(sl);
            boolean full = p >= sl.capacity();
            float x = cx + c[0] * R, y = cy + c[1] * R + (p > 0 && !full || sl == selected ? k * 0.9f : 0);
            float a = full ? 0.95f : 0.42f;
            switch (sl.conviction) {
                case SKILL: {
                    s.setColor(0, 0, 0, 0.5f * a);
                    s.rectLine(x - k + 0.8f, y - k - 0.8f, x + k + 0.8f, y + k - 0.8f, 2.6f);
                    s.setColor(0.93f, 0.9f, 0.82f, a);
                    s.rectLine(x - k, y - k, x + k, y + k, 1.8f);
                    s.rectLine(x - k * 0.65f, y - k * 0.05f, x + k * 0.05f, y - k * 0.65f, 1.8f);
                    break;
                }
                case LIFE_LEECH:
                case MANA_LEECH: {
                    boolean life = sl.conviction == WheelSlice.Conv.LIFE_LEECH;
                    s.setColor(0, 0, 0, 0.5f * a);
                    s.circle(x, y - k * 0.3f, k * 0.72f, 14);
                    if (life) s.setColor(0.95f, 0.3f, 0.28f, a);
                    else s.setColor(0.4f, 0.62f, 1f, a);
                    s.circle(x, y - k * 0.3f, k * 0.58f, 14);
                    s.triangle(x - k * 0.55f, y - k * 0.15f, x + k * 0.55f, y - k * 0.15f, x, y + k * 0.95f);
                    break;
                }
                case PASSIVE_A:
                case PASSIVE_B: {
                    s.setColor(0, 0, 0, 0.5f * a);
                    s.circle(x, y, k * 1.25f, 20);
                    s.setColor(0.95f, 0.8f, 0.38f, a);
                    star(s, x, y, k * 1.15f, k * 0.3f);
                    break;
                }
                default: {
                    s.setColor(0, 0, 0, 0.45f * a);
                    star(s, x + 0.7f, y - 0.7f, k, k * 0.26f);
                    s.setColor(0.98f, 0.94f, 0.78f, a);
                    star(s, x, y, k, k * 0.26f);
                }
            }
        }
    }

    /** Gwiazda o 4 ramionach (dlugosc k, grubosc q). */
    private static void star(ShapeRenderer s, float x, float y, float k, float q) {
        s.triangle(x - q, y, x + q, y, x, y + k);
        s.triangle(x - q, y, x + q, y, x, y - k);
        s.triangle(x, y - q, x, y + q, x + k, y);
        s.triangle(x, y - q, x, y + q, x - k, y);
    }

    private void drawBadges(ShapeRenderer s, PlayerCharacter pc) {
        for (WheelDomain d : WheelDomain.values()) {
            float[] b = lay.badge(d);
            int pts = pc.wheel().domainPoints(d) + pc.wheelBonus().gems().revelation(d);
            int st = WheelDomain.stage(pts);
            float r = lay.badgeR;
            if (revelation == d) {
                s.setColor(rgb(0xfff2c0, 1f));
                s.circle(b[0], b[1], r + 4, 40);
            }
            s.setColor(GOLD_DARK);
            s.circle(b[0], b[1], r + 2, 40);
            s.setColor(GOLD);
            s.circle(b[0], b[1], r + 0.8f, 40);
            s.setColor(st > 0 ? dom(d, 0.85f) : lighten(dom(d, 0.22f), 0.05f));
            s.circle(b[0], b[1], r - 0.8f, 40);
            // znak objawienia: gwiazda o 4 ramionach (jasna od 1. etapu), pod nia 3 kropki etapow
            s.setColor(st > 0 ? rgb(0xfff4d6, 1f) : rgb(0x6d6258, 1f));
            star(s, b[0], b[1] + r * 0.14f, r * 0.56f, r * 0.15f);
            float pr = Math.max(1.6f, r * 0.11f);
            for (int i = 0; i < 3; i++) {
                float px = b[0] + (i - 1) * r * 0.34f, py = b[1] - r * 0.56f;
                s.setColor(FRAME_DARK);
                s.circle(px, py, pr + 0.9f, 12);
                s.setColor(st > i ? GOLD : rgb(0x3d3128, 1f));
                s.circle(px, py, pr, 12);
            }
        }
    }

    private void drawSide(ShapeRenderer s) {
        HudLayout.Rect sd = lay.side;
        s.setColor(SIDE);
        s.rect(sd.x, sd.y, sd.w, sd.h);
        stroke(s, sd, 1.5f, GOLD_DARK);
        HudLayout.Rect ct = lay.cardTitle;
        Color head = selected != null ? dom(selected.domain, 0.42f) : revelation != null ? dom(revelation, 0.42f) : rgb(0x3a2a1c, 1f);
        s.setColor(head);
        s.rect(ct.x, ct.y, ct.w, ct.h);
        s.setColor(GOLD_DARK);
        s.rect(ct.x, ct.y, ct.w, 1f);
        HudLayout.Rect body = body();
        // wpisy z akcja: tlo pod calym wpisem
        int lastItem = -1;
        for (Line l : lines) {
            if (l.action == null || l.item == lastItem) continue;
            lastItem = l.item;
            float top = l.y, bottom = l.y + l.h;
            for (Line o : lines) if (o.item == l.item) bottom = Math.max(bottom, o.y + o.h);
            float y0 = body.top() + scroll - bottom - 2, y1 = body.top() + scroll - top + 1;
            y0 = Math.max(y0, body.y);
            y1 = Math.min(y1, body.top());
            if (y1 - y0 < 4) continue;
            s.setColor(LINK);
            s.rect(body.x - 2, y0, body.w + 4, y1 - y0);
            s.setColor(GOLD_DARK);
            s.rect(body.x - 2, y0, 2, y1 - y0);
        }
        // strzalki przewijania
        if (contentH > body.h + 1) {
            s.setColor(GOLD);
            float ax = body.right() - 6;
            if (scroll > 1) s.triangle(ax - 5, body.top() - 6, ax + 5, body.top() - 6, ax, body.top());
            if (scroll < contentH - body.h - 1) s.triangle(ax - 5, body.y + 6, ax + 5, body.y + 6, ax, body.y);
        }
        if (selected != null) {
            for (int i = 0; i < cardBtns.size() && i < WheelPanelLayout.CARD_BUTTONS; i++) {
                Btn b = cardBtns.get(i);
                button(s, lay.cardButtons[i], !b.enabled ? BTN_OFF : b.id.startsWith("wheel:add") ? BTN_ADD : BTN_REM);
            }
        }
        for (int i = 0; i < WheelPanelLayout.TOOLS; i++) button(s, lay.tools[i], toolEnabled(i) ? (i == 3 ? BTN_REM : BTN) : BTN_OFF);
    }

    private static void button(ShapeRenderer s, HudLayout.Rect r, Color fill) {
        s.setColor(FRAME_DARK);
        s.rect(r.x, r.y, r.w, r.h);
        s.setColor(fill);
        s.rect(r.x + 1, r.y + 1, r.w - 2, r.h - 2);
        s.setColor(lighten(fill, 0.18f));
        s.rect(r.x + 1, r.top() - 3, r.w - 2, 2);
        s.setColor(0, 0, 0, 0.25f);
        s.rect(r.x + 1, r.y + 1, r.w - 2, 2);
    }

    // ------------------------------------------------------------------ napisy

    private float titleScale() { return lay.mobile ? 0.8f : 1f; }

    private float textWidth(String t, float sc) {
        font.getData().setScale(sc);
        glyphs.setText(font, t);
        font.getData().setScale(1f);
        return glyphs.width;
    }

    /** Napis z linia bazowa w y. */
    private void text(SpriteBatch b, String t, float x, float y, float sc, Color c) {
        font.getData().setScale(sc);
        font.setColor(c);
        font.draw(b, t, x, y + font.getCapHeight());
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
    }

    private void centered(SpriteBatch b, String t, float cx, float cy, float sc, Color c) {
        font.getData().setScale(sc);
        glyphs.setText(font, t);
        float x = cx - glyphs.width / 2f, y = cy - font.getCapHeight() / 2f;
        font.getData().setScale(1f);
        text(b, t, x, y, sc, c);
    }

    private void outlined(SpriteBatch b, String t, float cx, float cy, float sc, Color c) {
        Color sh = new Color(0, 0, 0, 0.85f);
        centered(b, t, cx + 0.8f, cy - 0.8f, sc, sh);
        centered(b, t, cx - 0.8f, cy + 0.8f, sc, sh);
        centered(b, t, cx, cy, sc, c);
    }

    private float lineScale() { return lay.mobile ? 0.6f : 0.74f; }

    private float labelScale() { return lay.mobile ? 0.64f : 0.8f; }

    void drawText(SpriteBatch b, PlayerCharacter pc) {
        HudLayout.Rect t = lay.title;
        centered(b, pl.fawizja.pixel.wheel.WheelView.TITLE, t.cx(), t.cy(), titleScale(), LABEL);
        float cx = lay.cx, cy = lay.cy, R = lay.radius;
        int av = pc.wheelPoints();
        DestinyWheel wh = pc.wheel();
        float hr = R * WheelGeometry.HUB;
        float big = lay.mobile ? 0.95f : 1.35f, small = lay.mobile ? 0.5f : 0.62f;
        if (av > 0) {
            int free = Math.max(0, av - wh.spent());
            centered(b, String.valueOf(free), cx, cy + hr * 0.22f, big, free > 0 ? GOOD : TEXT);
            centered(b, "wolne", cx, cy - hr * 0.28f, small, MUTED);
            centered(b, "z " + av, cx, cy - hr * 0.6f, small * 0.9f, MUTED);
        } else {
            centered(b, "0", cx, cy + hr * 0.2f, big, MUTED);
            centered(b, "nieaktywne", cx, cy - hr * 0.35f, small * 0.85f, BAD);
        }
        if (av <= 0) {
            float bh = lay.mobile ? 20 : 26, by = cy - hr - 12 - bh;
            String req = requirementsShort(pc);
            float sc = lay.mobile ? 0.56f : 0.72f;
            while (sc > 0.4f && textWidth(req, sc) > R * 1.45f) sc -= 0.02f;
            centered(b, req, cx, by + bh / 2f, sc, BAD);
        }
        // punkty w polach czesciowo napelnionych i w wybranym polu
        float ps = lay.mobile ? 0.46f : 0.6f;
        for (WheelSlice sl : WheelSlice.values()) {
            int p = wh.points(sl);
            if (sl != selected && (p <= 0 || p >= sl.capacity())) continue;
            float[] c = WheelGeometry.center(sl);
            String s = sl == selected ? p + "/" + sl.capacity() : String.valueOf(p);
            int ring = WheelGeometry.ring(sl);
            float th = R * (WheelGeometry.outerRadius(ring) - WheelGeometry.innerRadius(ring));
            outlined(b, s, cx + c[0] * R, cy + c[1] * R - th * 0.25f, ps, TEXT);
        }
        // odznaki objawien: punkty cwiartki
        for (WheelDomain d : WheelDomain.values()) {
            float[] bp = lay.badge(d);
            int pts = wh.domainPoints(d) + pc.wheelBonus().gems().revelation(d);
            boolean top = d == WheelDomain.GREEN || d == WheelDomain.RED;
            float ty = top ? bp[1] - lay.badgeR - 10 : bp[1] + lay.badgeR + 10;
            outlined(b, pts + " pkt", bp[0], ty, lay.mobile ? 0.48f : 0.6f, WheelDomain.stage(pts) > 0 ? LABEL : MUTED);
        }
        drawSideText(b);
    }

    /** "Wymaga: poziom 51, promocja, premium" - tylko to, czego brakuje. */
    private static String requirementsShort(PlayerCharacter pc) {
        List<String> miss = new ArrayList<>();
        if (pc.level() < DestinyWheel.MIN_LEVEL) miss.add("poziom " + DestinyWheel.MIN_LEVEL);
        if (!pc.isPromoted()) miss.add("promocja");
        if (!pc.isPremium()) miss.add("premium");
        return miss.isEmpty() ? "Koło nieaktywne" : "Wymaga: " + String.join(", ", miss);
    }

    private void drawSideText(SpriteBatch b) {
        HudLayout.Rect ct = lay.cardTitle;
        float ts = lay.mobile ? 0.68f : 0.86f;
        String title = clip(cardTitle, ct.w - 12, ts);
        text(b, title, ct.x + 6, ct.cy() - capH(ts) / 2f, ts, TEXT);
        HudLayout.Rect body = body();
        for (Line l : lines) {
            float top = body.top() + scroll - l.y;
            if (top > body.top() + 0.5f || top - l.h < body.y - 0.5f) continue;
            float base = top - l.h + (l.h - capH(l.scale)) / 2f;
            text(b, l.text, body.x + 4 + l.indent, base, l.scale, l.color);
            if (l.right != null && !l.right.isEmpty()) {
                float rw = textWidth(l.right, l.scale * 0.92f);
                text(b, l.right, body.right() - 12 - rw, base, l.scale * 0.92f, l.rightColor);
            }
        }
        float bs = lay.mobile ? 0.58f : 0.72f;
        if (selected != null) {
            for (int i = 0; i < cardBtns.size() && i < WheelPanelLayout.CARD_BUTTONS; i++) {
                HudLayout.Rect r = lay.cardButtons[i];
                Btn bt = cardBtns.get(i);
                centered(b, clip(bt.label, r.w - 4, bs), r.cx(), r.cy(), bs, bt.enabled ? TEXT : MUTED);
            }
        }
        for (int i = 0; i < WheelPanelLayout.TOOLS; i++) {
            HudLayout.Rect r = lay.tools[i];
            centered(b, clip(TOOL_LABELS[i], r.w - 4, bs), r.cx(), r.cy(), bs, toolEnabled(i) ? TEXT : MUTED);
        }
    }

    private float capH(float sc) {
        font.getData().setScale(sc);
        float c = font.getCapHeight();
        font.getData().setScale(1f);
        return c;
    }

    private String clip(String s, float maxW, float sc) {
        if (textWidth(s, sc) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && textWidth(t + "...", sc) > maxW) t = t.substring(0, t.length() - 1);
        return t.trim() + "...";
    }

    // ------------------------------------------------------------------ tresc karty

    private static String buttonLabel(String label) {
        switch (label) {
            case "Dodaj 1": return "+1";
            case "Dodaj 10": return "+10";
            case "Dodaj do pełna": return "Do pełna";
            case "Odejmij 1": return "-1";
            case "Opróżnij pole": return "Opróżnij";
            default: return label;
        }
    }

    private static Color stateColor(String st) {
        if (st.startsWith("aktyw") || st.equals("pełne") || st.equals("spełnione") || st.equals("tak") || st.startsWith("etap")
                || st.equals("otwarte")) return GOOD;
        if (st.equals("brak") || st.equals("niespełnione") || st.equals("nie") || st.equals("nieaktywne") || st.equals("zamknięte")) return BAD;
        return MUTED;
    }

    /** Wiersze WheelView -> zawiniete linie karty i przyciski punktow. */
    private void buildCard(PlayerCharacter pc) {
        lines.clear();
        cardBtns.clear();
        HudLayout.Rect body = body();
        float maxW = body.w - 14;
        float ls = labelScale(), ds = lineScale();
        float lh = capH(ls) + (lay.mobile ? 6 : 8), dh = capH(ds) + (lay.mobile ? 5 : 6);
        List<String[]> items = new ArrayList<>();
        for (String[] r : cardRows) {
            String action = r.length > 3 ? r[3] : "ach:none";
            boolean off = r.length > 4 && "off".equals(r[4]);
            if (action.startsWith("wheel:add:") || action.startsWith("wheel:remove:")) {
                cardBtns.add(new Btn(action, buttonLabel(r[0]), !off));
                continue;
            }
            if (r[0].equals("Wróć")) continue;
            if (r[0].trim().equals("(cd.)") && !items.isEmpty()) {
                String[] prev = items.get(items.size() - 1);
                prev[1] = prev[1] + " " + r[1];
                continue;
            }
            items.add(new String[] {r[0], r[1], r.length > 2 ? r[2] : "", action, off ? "off" : ""});
        }
        float y = 2;
        int idx = 0;
        for (String[] it : items) {
            String action = it[3].equals("ach:none") || it[4].equals("off") ? null : it[3];
            Line head = new Line();
            head.scale = ls;
            head.color = action != null ? LABEL : rgb(0xd9b56a, 1f);
            head.right = it[2];
            head.rightColor = stateColor(it[2]);
            float rightW = it[2].isEmpty() ? 0 : textWidth(it[2], ls * 0.92f) + 10;
            head.text = clip(it[0] + (action != null ? "  >" : ""), maxW - rightW, ls);
            head.action = action;
            head.y = y;
            head.h = lh;
            head.item = idx;
            lines.add(head);
            y += lh;
            if (!it[1].isEmpty()) {
                for (String part : wrap(it[1], maxW - 8, ds)) {
                    Line l = new Line();
                    l.text = part;
                    l.scale = ds;
                    l.color = TEXT;
                    l.indent = 8;
                    l.action = action;
                    l.y = y;
                    l.h = dh;
                    l.item = idx;
                    lines.add(l);
                    y += dh;
                }
            }
            y += lay.mobile ? 3 : 5;
            idx++;
        }
        contentH = y;
        scroll = clampScroll(scroll);
    }

    private List<String> wrap(String s, float maxW, float sc) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String next = cur.length() == 0 ? word : cur + " " + word;
            if (cur.length() > 0 && textWidth(next, sc) > maxW) {
                out.add(cur.toString());
                cur.setLength(0);
                cur.append(word);
            } else {
                cur.setLength(0);
                cur.append(next);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }
}
