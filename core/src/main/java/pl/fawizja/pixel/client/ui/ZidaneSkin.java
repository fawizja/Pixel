package pl.fawizja.pixel.client.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * Wyglad interfejsu "nowy" (2026-11-07): elementy z kupionej paczki "Pixel RPG UI Kit" (Zidane42, itch.io), wyciete
 * skryptem tools/art/zidane_import.py do assets/ui/zidane/. Kazdy element sklada sie z kawalkow 9-slice (rogi bez
 * zmian, srodek rozciagany - w paczce jednolity), wiec pasek, okno, przycisk czy pole maja dowolny rozmiar; 1 piksel
 * grafiki = 1 jednostka interfejsu (na telefonie 1 dp), czyli ostre piksele przy calkowitej skali interfejsu.
 * Rysowane w SpriteBatchu; brak plikow = null z {@link #load()} i Hud zostaje przy wygladzie klasycznym.
 */
final class ZidaneSkin {

    /** Rodzaj paska (kolor wypelnienia z paczki). */
    enum Bar { LIFE, MANA, EXP }

    /** Kolor przycisku. */
    enum Btn { YELLOW, GREEN, RED, DISABLED }

    /** Grubosc ramki paska (piksele grafiki) - wnetrze paska jest o tyle mniejsze z kazdej strony. */
    static final float BAR_BORDER = 4f;

    private final Texture barFrame, barNotch, barEmpty, window, divider;
    private final Texture[] barFill = new Texture[Bar.values().length];
    private final Texture[] buttons = new Texture[Btn.values().length];
    final TextureRegion slot, slotHover;
    /** Ramki rzadkosci: zwykly, niepospolity, rzadki, epicki, legendarny, mityczny. */
    final TextureRegion[] rarity = new TextureRegion[6];
    final TextureRegion toggleOn, toggleOff;
    /** Okragla ramka minimapy 95 x 95 z przyciskami + i - oraz same przyciski (22 x 21) do przyciemnienia/rozjasnienia. */
    private final Texture minimapRing, minimapPlus, minimapMinus;

    /** Bok ramki minimapy (piksele grafiki). */
    static final float RING = 95f;
    /** Srodek kola mapy i promien wnetrza (piksele grafiki od lewego dolnego rogu ramki; wnetrze 6..88 px). */
    static final float RING_CX = 47.5f, RING_CY = 47.5f, RING_INNER = 41.5f;
    /** Srodki przyciskow + i - (od lewego dolnego rogu ramki) i ich promien. */
    static final float PLUS_CX = 84f, PLUS_CY = 84.5f, MINUS_CX = 84f, MINUS_CY = 10.5f, BUTTON_R = 10.5f;

    /** Stan przycisku ramki minimapy: zwykly, wcisniety (jasniejszy), nieczynny (koniec skali, ciemniejszy). */
    enum RingButton { NORMAL, PRESSED, DISABLED }

    private ZidaneSkin() {
        barFrame = tex("bar_frame");
        barNotch = tex("bar_notch");
        barEmpty = tex("bar_empty");
        String[] fills = {"life", "mana", "exp"};
        for (int i = 0; i < fills.length; i++) barFill[i] = tex("bar_fill_" + fills[i]);
        window = tex("window");
        divider = tex("divider");
        String[] colors = {"yellow", "green", "red", "disabled"};
        for (int i = 0; i < colors.length; i++) buttons[i] = tex("button_" + colors[i]);
        slot = reg("slot");
        slotHover = reg("slot_hover");
        String[] tiers = {"common", "unusual", "rare", "epic", "legendary", "mythical"};
        for (int i = 0; i < tiers.length; i++) rarity[i] = reg("rarity_" + tiers[i]);
        toggleOn = reg("toggle_on");
        toggleOff = reg("toggle_off");
        minimapRing = tex("minimap_ring");
        minimapPlus = tex("minimap_plus");
        minimapMinus = tex("minimap_minus");
    }

    /** Grafiki z assets/ui/zidane albo null, gdy ich brak (wtedy tylko wyglad klasyczny). */
    static ZidaneSkin load() {
        if (Gdx.files == null || file("bar_frame") == null) return null;
        try {
            return new ZidaneSkin();
        } catch (RuntimeException e) {
            Gdx.app.error("Hud", "Brak grafik wygladu nowego: " + e.getMessage());
            return null;
        }
    }

    private static FileHandle file(String name) {
        FileHandle f = Gdx.files.internal("ui/zidane/" + name + ".png");
        if (!f.exists()) f = Gdx.files.internal("assets/ui/zidane/" + name + ".png"); // desktop z katalogu repozytorium
        return f.exists() ? f : null;
    }

    private static Texture tex(String name) {
        FileHandle f = file(name);
        if (f == null) throw new IllegalStateException("brak ui/zidane/" + name + ".png");
        Texture t = new Texture(f);
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return t;
    }

    private static TextureRegion reg(String name) {
        return new TextureRegion(tex(name));
    }

    /**
     * 9-slice z fragmentu tekstury (sx, sy, sw, sh; y od gory): rogi l/r/t/b pikseli bez skalowania (mniejsze, gdy
     * prostokat jest mniejszy od rogow), srodek i krawedzie rozciagane.
     */
    static void nine(SpriteBatch b, Texture t, int sx, int sy, int sw, int sh, int l, int r, int top, int bot,
                     float x, float y, float w, float h) {
        if (w <= 0 || h <= 0) return;
        float lw = l, rw = r, th = top, bh = bot;
        if (lw + rw > w) {
            lw = w * l / (float) (l + r);
            rw = w - lw;
        }
        if (th + bh > h) {
            th = h * top / (float) (top + bot);
            bh = h - th;
        }
        float[] cx = {x, x + lw, x + w - rw};
        float[] cw = {lw, w - lw - rw, rw};
        int[] csx = {sx, sx + l, sx + sw - r};
        int[] csw = {l, sw - l - r, r};
        float[] ry = {y + h - th, y + bh, y};
        float[] rh = {th, h - th - bh, bh};
        int[] rsy = {sy, sy + top, sy + sh - bot};
        int[] rsh = {top, sh - top - bot, bot};
        for (int i = 0; i < 3; i++) {
            if (rh[i] <= 0 || rsh[i] <= 0) continue;
            for (int j = 0; j < 3; j++) {
                if (cw[j] <= 0 || csw[j] <= 0) continue;
                b.draw(t, cx[j], ry[i], cw[j], rh[i], csx[j], rsy[i], csw[j], rsh[i], false, false);
            }
        }
    }

    private static float snap(float v) {
        return Math.round(v);
    }

    /**
     * Pasek w ramce z paczki: prostokat (x, y, w, h) to zewnetrzna krawedz ramki, wnetrze jest o {@link #BAR_BORDER}
     * mniejsze. Wypelnienie ma dowolna dlugosc (frac 0..1), naciecia ramki co 25%.
     */
    void bar(SpriteBatch b, Bar kind, float x, float y, float w, float h, float frac) {
        x = snap(x);
        y = snap(y);
        w = snap(w);
        h = snap(h);
        float ix = x + BAR_BORDER, iy = y + BAR_BORDER, iw = w - 2 * BAR_BORDER, ih = h - 2 * BAR_BORDER;
        nine(b, barEmpty, 0, 0, 3, 4, 1, 1, 2, 1, ix, iy, iw, ih);
        float f = Math.max(0f, Math.min(1f, frac));
        Texture fill = barFill[kind.ordinal()];
        if (f >= 1f) {
            nine(b, fill, 0, 0, 3, 4, 1, 1, 2, 1, ix, iy, iw, ih);
        } else {
            float fw = Math.round(iw * f);
            if (f > 0 && fw < 1) fw = 1; // resztka zdrowia zawsze widoczna
            nine(b, fill, 0, 0, 2, 4, 1, 0, 2, 1, ix, iy, fw, ih);
        }
        nine(b, barFrame, 0, 0, 17, 13, 8, 8, 6, 6, x, y, w, h);
        if (iw >= 48) {
            for (int q = 1; q <= 3; q++) {
                float nx = snap(ix + iw * q / 4f);
                b.draw(barNotch, nx, y + h - BAR_BORDER, 1, BAR_BORDER, 0, 0, 1, 4, false, false);
                b.draw(barNotch, nx, y, 1, BAR_BORDER, 0, 4, 1, 4, false, false);
            }
        }
    }

    /** Okno: rogi 16 px, krawedzie 8 px, ciemne tlo. */
    void window(SpriteBatch b, float x, float y, float w, float h) {
        nine(b, window, 0, 0, 40, 40, 16, 16, 16, 16, snap(x), snap(y), snap(w), snap(h));
    }

    /** Cienka listwa (3 zewnetrzne piksele krawedzi okna) pod paskiem tytulu; y = dolna krawedz listwy. */
    void divider(SpriteBatch b, float x, float y, float w) {
        b.draw(divider, snap(x), snap(y), snap(w), 3, 0, 0, 1, 3, false, false);
    }

    /** Przycisk dowolnej szerokosci i wysokosci (krawedzie 6 px). */
    void button(SpriteBatch b, Btn c, float x, float y, float w, float h) {
        nine(b, buttons[c.ordinal()], 0, 0, 13, 32, 6, 6, 6, 6, snap(x), snap(y), snap(w), snap(h));
    }

    /** Przycisk-wiersz listy (sklep, ksiega, opcje): przyciemniony, zeby jasny napis i opis byly czytelne. */
    void listButton(SpriteBatch b, Btn c, float x, float y, float w, float h) {
        if (c == Btn.GREEN) b.setColor(LIST_TINT, LIST_TINT, LIST_TINT, 1f);
        button(b, c, x, y, w, h);
        b.setColor(1f, 1f, 1f, 1f);
    }

    private static final float LIST_TINT = 0.72f;

    /** Ramka pola (zwykla, wskazana, rzadkosci) w dowolnym rozmiarze: rogi 8 px, srodek rozciagany. */
    void slot(SpriteBatch b, TextureRegion frame, float x, float y, float w, float h) {
        nine(b, frame.getTexture(), 0, 0, 32, 32, 8, 8, 8, 8, snap(x), snap(y), snap(w), snap(h));
    }

    /** Czy obrazek jest ramka pola z tej paczki (rysowana jako 9-slice, nie rozciagana w calosci). */
    boolean isSlot(TextureRegion r) {
        if (r == null) return false;
        if (r == slot || r == slotHover) return true;
        for (TextureRegion x : rarity) if (r == x) return true;
        return false;
    }

    /**
     * Ramka rzadkosci: tier 1..5 (Rarity: zwykly, niepospolity, rzadki, epicki, legendarny), przedmiot tylko z bossow
     * o stopien wyzej (jak klasa Kuzni) - legendarny z bossa ma ramke mityczna; 0 = zwykle pole.
     */
    TextureRegion rarityFrame(int tier, boolean bossOnly) {
        if (tier <= 0) return slot;
        return rarity[Math.min(6, tier + (bossOnly ? 1 : 0)) - 1];
    }

    /**
     * Ramka okraglej minimapy w skali s (1 = 95 x 95) z lewym dolnym rogiem (x, y); przyciski + i - rysowane jeszcze
     * raz w swoim stanie (wycinek ma przezroczyste rogi, wiec zwykly stan wyglada jak sama ramka).
     */
    void minimapRing(SpriteBatch b, float x, float y, float s, RingButton plus, RingButton minus) {
        b.draw(minimapRing, x, y, RING * s, RING * s);
        ringButton(b, minimapPlus, x + 73 * s, y + (RING - 21) * s, s, plus);
        ringButton(b, minimapMinus, x + 73 * s, y, s, minus);
    }

    private static void ringButton(SpriteBatch b, Texture t, float x, float y, float s, RingButton st) {
        if (st == RingButton.NORMAL) return;
        if (st == RingButton.DISABLED) {
            b.setColor(0.42f, 0.4f, 0.38f, 1f);
            b.draw(t, x, y, 22 * s, 21 * s);
        } else {
            // wcisniety: przycisk dodany drugi raz (mieszanie addytywne) - jasniejszy
            int src = b.getBlendSrcFunc(), dst = b.getBlendDstFunc();
            b.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE);
            b.setColor(1f, 1f, 1f, 0.45f);
            b.draw(t, x, y, 22 * s, 21 * s);
            b.setBlendFunction(src, dst);
        }
        b.setColor(1f, 1f, 1f, 1f);
    }

    /** Przelacznik wl./wyl. (48 x 24) o wysokosci h; x, y = lewy dolny rog. */
    void toggle(SpriteBatch b, boolean on, float x, float y, float h) {
        b.draw(on ? toggleOn : toggleOff, snap(x), snap(y), h * 2, h);
    }
}
