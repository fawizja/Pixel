package pl.fawizja.pixel.client.ui;

import pl.fawizja.pixel.wheel.WheelDomain;

/**
 * Uklad okna Kola Przeznaczenia w ramce (jak okno kola w Tibii): ozdobna rama z paskiem tytulu, po lewej kwadrat
 * z duzym kolem (w rogach odznaki objawien cwiartek), po prawej karta pola (tytul, tresc przewijana, rzad
 * przyciskow punktow) i pasek narzedzi (klejnoty, zestawy, premie, czyszczenie). Komputer: okno na srodku ekranu,
 * telefon (jednostki dp): prawie caly ekran, przyciski co najmniej 36 dp. Czysta matematyka (testy: WheelPanelLayoutTest).
 */
public final class WheelPanelLayout {
    /** Przyciski punktow pola (+1, +10, do pelna, -1, oproznij) i paska narzedzi. */
    public static final int CARD_BUTTONS = 5, TOOLS = 4;

    public final boolean mobile;
    public final HudLayout.Rect win = new HudLayout.Rect();
    public final HudLayout.Rect title = new HudLayout.Rect();
    public final HudLayout.Rect close = new HudLayout.Rect();
    public final HudLayout.Rect closeHit = new HudLayout.Rect();
    /** Kwadrat kola (z odznakami objawien w rogach). */
    public final HudLayout.Rect wheelBox = new HudLayout.Rect();
    public final HudLayout.Rect side = new HudLayout.Rect();
    public final HudLayout.Rect cardTitle = new HudLayout.Rect();
    /** Tresc karty z rzedem przyciskow punktow (gdy wybrane pole) i bez niego. */
    public final HudLayout.Rect cardBody = new HudLayout.Rect();
    public final HudLayout.Rect cardBodyFull = new HudLayout.Rect();
    public final HudLayout.Rect[] cardButtons = new HudLayout.Rect[CARD_BUTTONS];
    public final HudLayout.Rect[] tools = new HudLayout.Rect[TOOLS];
    public float frame, pad, titleH, buttonH, gap;
    /** Srodek i promien kola (bez pierscienia oprawy), grubosc oprawy, promien odznaki objawienia. */
    public float cx, cy, radius, rim, badgeR;

    public WheelPanelLayout(boolean mobile) {
        this.mobile = mobile;
        for (int i = 0; i < CARD_BUTTONS; i++) cardButtons[i] = new HudLayout.Rect();
        for (int i = 0; i < TOOLS; i++) tools[i] = new HudLayout.Rect();
    }

    public void compute(float w, float h) {
        float margin = mobile ? 4 : 10;
        float ww = mobile ? w - 2 * margin : Math.min(w - 2 * margin, 1000);
        float wh = mobile ? h - 2 * margin : Math.min(h - 2 * margin, 660);
        win.set(Math.round((w - ww) / 2f), Math.round((h - wh) / 2f), ww, wh);
        frame = mobile ? 5 : 7;
        pad = mobile ? 6 : 10;
        gap = mobile ? 4 : 6;
        titleH = mobile ? 24 : 30;
        buttonH = mobile ? MobileHudLayout.MIN_TOUCH : 28;
        title.set(win.x + frame, win.top() - frame - titleH, win.w - 2 * frame, titleH);
        float cs = titleH - 6;
        close.set(title.right() - 3 - cs, title.y + 3, cs, cs);
        float hs = Math.max(cs, mobile ? MobileHudLayout.MIN_TOUCH : cs);
        closeHit.set(close.cx() - hs / 2f, Math.min(close.cy() - hs / 2f, title.top() - hs), hs, hs);
        float bx = win.x + frame + pad, by = win.y + frame + pad;
        float bw = win.w - 2 * (frame + pad), bh = title.y - pad - by;
        float s = Math.min(bh, bw * (mobile ? 0.44f : 0.6f));
        wheelBox.set(bx, by + (bh - s) / 2f, s, s);
        rim = Math.max(7, s * 0.03f);
        badgeR = Math.max(mobile ? 17 : 14, s * 0.058f);
        cx = wheelBox.cx();
        cy = wheelBox.cy();
        radius = s / 2f - rim - 3;
        float sx = wheelBox.right() + pad;
        side.set(sx, by, bx + bw - sx, bh);
        float ctH = mobile ? 22 : 30;
        cardTitle.set(side.x + 6, side.top() - 6 - ctH, side.w - 12, ctH);
        float toolsY = side.y + 6;
        float tw = (side.w - 12 - (TOOLS - 1) * gap) / TOOLS;
        for (int i = 0; i < TOOLS; i++) tools[i].set(side.x + 6 + i * (tw + gap), toolsY, tw, buttonH);
        float rowY = toolsY + buttonH + gap + (mobile ? 2 : 4);
        float cw = (side.w - 12 - (CARD_BUTTONS - 1) * gap) / CARD_BUTTONS;
        for (int i = 0; i < CARD_BUTTONS; i++) cardButtons[i].set(side.x + 6 + i * (cw + gap), rowY, cw, buttonH);
        float bodyTop = cardTitle.y - 4;
        cardBodyFull.set(side.x + 6, rowY, side.w - 12, Math.max(0, bodyTop - rowY));
        float bodyY = rowY + buttonH + gap;
        cardBody.set(side.x + 6, bodyY, side.w - 12, Math.max(0, bodyTop - bodyY));
    }

    /** Srodek odznaki objawienia cwiartki: w rogu kwadratu kola po stronie cwiartki. */
    public float[] badge(WheelDomain d) {
        float in = badgeR + 4;
        boolean left = d == WheelDomain.GREEN || d == WheelDomain.BLUE;
        boolean top = d == WheelDomain.GREEN || d == WheelDomain.RED;
        return new float[] {left ? wheelBox.x + in : wheelBox.right() - in, top ? wheelBox.top() - in : wheelBox.y + in};
    }

    /** Cwiartka, ktorej odznake objawienia trafia punkt, albo null (pole dotyku co najmniej 36 dp na telefonie). */
    public WheelDomain badgeAt(float px, float py) {
        float hr = Math.max(badgeR + 3, mobile ? MobileHudLayout.MIN_TOUCH / 2f : 0);
        for (WheelDomain d : WheelDomain.values()) {
            float[] b = badge(d);
            float dx = px - b[0], dy = py - b[1];
            if (dx * dx + dy * dy <= hr * hr) return d;
        }
        return null;
    }
}
