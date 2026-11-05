package pl.fawizja.pixel.client.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import pl.fawizja.pixel.vocation.Harmony;

/**
 * Harmonia mnicha w HUD (rysowana ksztaltami, bez grafik): 5 kul jak w Tibii - pelne swieca zlotem, puste sa
 * ciemne; przy pelnej harmonii kule maja zlota obwodke (bez animacji - zrzuty regresji GUI sa powtarzalne). Za kulami znak cnoty (zloty krag harmonii, czerwona piesc
 * sprawiedliwosci, zielony listek wytrwalosci; pusty krag = brak cnoty), a niebieska poswiata pod kulami to spokoj.
 */
final class HarmonyHud {

    private HarmonyHud() {}

    static final Color ORB_FULL = new Color(1f, 0.86f, 0.42f, 1f);
    static final Color ORB_CORE = new Color(1f, 0.98f, 0.85f, 1f);
    static final Color ORB_EMPTY = new Color(0.16f, 0.13f, 0.1f, 0.95f);
    static final Color ORB_RIM = new Color(0.55f, 0.45f, 0.3f, 1f);
    static final Color SERENE = new Color(0.45f, 0.75f, 1f, 0.35f);

    /** Szerokosc pasa kul i znaku cnoty dla kuli o srednicy d. */
    static float width(float d) {
        return Harmony.MAX * (d + gap(d)) + d + 2;
    }

    private static float gap(float d) { return Math.max(2f, d * 0.25f); }

    /** Rysuje kule od (x, y) - lewy dolny rog pasa o wysokosci d. */
    static void draw(ShapeRenderer s, Harmony h, float x, float y, float d, long nowMs) {
        float r = d / 2f, cy = y + r;
        if (h.serene()) {
            // spokoj: niebieska poswiata pod rzedem kul
            s.setColor(SERENE);
            s.rect(x - 2, y - 2, Harmony.MAX * (d + gap(d)) - gap(d) + 4, d + 4);
        }
        for (int i = 0; i < Harmony.MAX; i++) {
            float cx = x + r + i * (d + gap(d));
            s.setColor(h.full() ? ORB_FULL : new Color(0, 0, 0, 0.85f));
            s.circle(cx, cy, r + 1f, 16);
            s.setColor(h.full() ? Color.WHITE : ORB_RIM);
            s.circle(cx, cy, r, 16);
            if (i < h.points()) {
                s.setColor(ORB_FULL);
                s.circle(cx, cy, r - 1f, 16);
                s.setColor(ORB_CORE);
                s.circle(cx - r * 0.25f, cy + r * 0.25f, Math.max(1f, r * 0.35f), 10);
            } else {
                s.setColor(ORB_EMPTY);
                s.circle(cx, cy, r - 1f, 16);
            }
        }
        virtueGlyph(s, h.virtue(), x + Harmony.MAX * (d + gap(d)) + r, cy, r);
    }

    /** Znak cnoty w kolku o promieniu r. */
    static void virtueGlyph(ShapeRenderer s, Harmony.Virtue v, float cx, float cy, float r) {
        s.setColor(0, 0, 0, 0.85f);
        s.circle(cx, cy, r + 1f, 16);
        switch (v) {
            case HARMONY: { // zloty krag z jasnym srodkiem (rownowaga)
                s.setColor(0.85f, 0.65f, 0.2f, 1f);
                s.circle(cx, cy, r, 16);
                s.setColor(1f, 0.95f, 0.7f, 1f);
                s.circle(cx, cy, r * 0.45f, 12);
                break;
            }
            case JUSTICE: { // czerwone tlo, jasna piesc (kwadrat z kciukiem)
                s.setColor(0.7f, 0.18f, 0.15f, 1f);
                s.circle(cx, cy, r, 16);
                s.setColor(1f, 0.9f, 0.8f, 1f);
                s.rect(cx - r * 0.45f, cy - r * 0.4f, r * 0.9f, r * 0.75f);
                s.rect(cx - r * 0.55f, cy - r * 0.05f, r * 0.25f, r * 0.45f);
                break;
            }
            case SUSTAIN: { // zielone tlo, jasny listek
                s.setColor(0.18f, 0.5f, 0.25f, 1f);
                s.circle(cx, cy, r, 16);
                s.setColor(0.8f, 1f, 0.75f, 1f);
                s.triangle(cx, cy + r * 0.7f, cx - r * 0.45f, cy - r * 0.1f, cx + r * 0.45f, cy - r * 0.1f);
                s.triangle(cx, cy - r * 0.7f, cx - r * 0.45f, cy - r * 0.1f, cx + r * 0.45f, cy - r * 0.1f);
                break;
            }
            default: { // brak cnoty: pusty krag
                s.setColor(ORB_RIM);
                s.circle(cx, cy, r, 16);
                s.setColor(ORB_EMPTY);
                s.circle(cx, cy, r - 1.5f, 16);
            }
        }
    }

    /** Opis do dziennika po dotknieciu kul. */
    static String describe(Harmony h) {
        String virtue = h.virtue() == Harmony.Virtue.NONE ? "bez cnoty" : h.virtue().label;
        return "Harmonia " + h.points() + "/" + Harmony.MAX + " (premia czaru wydającego x"
                + String.format(java.util.Locale.ROOT, "%.2f", h.bonus()) + "), " + virtue
                + (h.serene() ? ", spokój" : ", bez spokoju");
    }
}
