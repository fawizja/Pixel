package pl.fawizja.pixel.client.ui;

/**
 * Uklad HUD-u jak w kliencie Tibii (2026-10-28, opis wlasciciela): lewa kolumna (minimapa, lista VIP, zwijane
 * umiejetnosci), prawa kolumna (ekwipunek z dwiema kolumnami przyciskow obok, lista walki, przyciski akcji,
 * pasek czarow), miedzy nimi u gory paski HP/MP z poziomem, pod nimi widok gry 15x11 pol (cala liczba pikseli na
 * pole) i pod widokiem konsola z zakladkami czatu.
 *
 * <p>Czysta matematyka bez libGDX (testy: HudLayoutTest). Jednostki interfejsu = piksele ekranu / uiScale.
 * Prostokaty maja poczatek w lewym dolnym rogu (jak w libGDX).
 */
public final class HudLayout {

    public static final float VIEW_TILES_W = 15f;
    public static final float VIEW_TILES_H = 11f;
    public static final float PAD = 4f;
    public static final float GAP = 3f;
    /** Naglowek malego okna (ekwipunek, VIP, umiejetnosci, walka). */
    public static final float HEADER = 23f;
    public static final float SKILL_ROW = 15f;
    public static final float VIP_ROW = 16f;
    /** Ramka wokol widoku gry. */
    public static final float BORDER = 2f;
    /** Przyciski w jednej kolumnie obok ekwipunku. */
    public static final int TOGGLE_ROWS = 4;
    /** Pola paska czarow (5 x 2) i przyciski akcji w rzedzie. */
    public static final int HOTBAR_COLS = 5;

    /** Prostokat (x, y = lewy dolny rog). */
    public static final class Rect {
        public float x, y, w, h;

        void set(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        public float right() { return x + w; }
        public float top() { return y + h; }
        public float cx() { return x + w / 2f; }
        public float cy() { return y + h / 2f; }
        public boolean empty() { return w <= 0 || h <= 0; }

        public boolean contains(float px, float py) { return !empty() && px >= x && px <= x + w && py >= y && py <= y + h; }

        /** Wspolne wnetrze (stykajace sie krawedzie nie sa nakladaniem). */
        public boolean overlaps(Rect o) {
            if (empty() || o.empty()) return false;
            float e = 0.01f;
            return x < o.right() - e && o.x < right() - e && y < o.top() - e && o.y < top() - e;
        }

        /** Czy o lezy w calosci w tym prostokacie. */
        public boolean encloses(Rect o) {
            float e = 0.01f;
            return o.x >= x - e && o.y >= y - e && o.right() <= right() + e && o.top() <= top() + e;
        }

        @Override
        public String toString() { return String.format(java.util.Locale.ROOT, "[%.1f,%.1f %.1fx%.1f]", x, y, w, h); }
    }

    public final Rect left = new Rect();
    public final Rect right = new Rect();
    /** Gorny pasek: od lewej do prawej kolumny. */
    public final Rect topBar = new Rect();
    /** Widok gry: dokladnie 15 x 11 pol po {@link #tilePixels} pikseli (bez ramki). */
    public final Rect view = new Rect();
    /** Konsola z zakladkami czatu pod widokiem gry. */
    public final Rect console = new Rect();
    /** Wnetrze minimapy (ramka 3 jednostki wokol). */
    public final Rect minimap = new Rect();
    public final Rect vip = new Rect();
    public final Rect skills = new Rect();
    public final Rect equipment = new Rect();
    /** Przyciski obok ekwipunku: [kolumna * TOGGLE_ROWS + wiersz], kolumna 0 blizej ekwipunku. */
    public final Rect[] toggles = new Rect[2 * TOGGLE_ROWS];
    public final Rect battle = new Rect();
    /** Przyciski akcji (ATAK, UZYJ, ROZMOWA...) nad paskiem czarow, po HOTBAR_COLS w rzedzie. */
    public final Rect actions = new Rect();
    /** Pasek czarow 5 x 2. */
    public final Rect hotbar = new Rect();

    /** Piksele ekranu na pole widoku gry (cala liczba). */
    public float tilePixels;
    /** Niski ekran (telefon w poziomie): mniejsze paski i przyciski. */
    public boolean compact;
    public float eqCell;
    public float toggleSize;
    public float hotbarCell;
    public int actionRows;

    public HudLayout() {
        for (int i = 0; i < toggles.length; i++) toggles[i] = new Rect();
    }

    /** Wysokosc gornego paska (HP i MP, pod nimi poziom, postep i informacje). */
    public float topBarHeight() { return compact ? 30f : 36f; }

    /** Najnizsza konsola: 3 wiersze na niskim ekranie, 5 na wyzszym. */
    private float consoleMin(float h) {
        float tab = h >= 500 ? 18 : 16;
        return (h < 600 ? 3 : 5) * 13 + 8 + tab;
    }

    /**
     * Liczy uklad.
     *
     * @param w,h          rozmiar ekranu w jednostkach interfejsu
     * @param uiScale      pikseli ekranu na jednostke (telefon 2..3.5)
     * @param actionCount  widoczne przyciski akcji (rzedy nad paskiem czarow)
     * @param vipOpen      lista VIP rozwinieta
     * @param skillsOpen   okno umiejetnosci rozwiniete
     * @param skillRows    wiersze okna umiejetnosci (poziom, doswiadczenie, skille)
     */
    public void compute(float w, float h, float uiScale, int actionCount, boolean vipOpen, boolean skillsOpen, int skillRows) {
        compact = h < 420;
        float topH = topBarHeight();
        float colL = compact ? 112 : 160, colR = compact ? 150 : 200;
        float maxL = compact ? 190 : 250, maxR = compact ? 230 : 290;
        // widok gry: najwieksza cala liczba pikseli na pole, przy ktorej mieszcza sie kolumny i konsola
        float cw = w - colL - colR - 2 * (PAD + BORDER);
        float ch = h - topH - 2 * BORDER - GAP - consoleMin(h) - PAD;
        tilePixels = Math.max(1f, (float) Math.floor(Math.min(cw * uiScale / VIEW_TILES_W, ch * uiScale / VIEW_TILES_H)));
        float vw = VIEW_TILES_W * tilePixels / uiScale, vh = VIEW_TILES_H * tilePixels / uiScale;
        // reszta szerokosci poszerza kolumny (do granicy), dalej zostaje jako margines wokol widoku
        float extra = Math.max(0, cw - vw);
        float addL = Math.min(extra / 2f, maxL - colL), addR = Math.min(extra / 2f, maxR - colR);
        float spare = extra - addL - addR;
        if (spare > 0) {
            // jedna kolumna u granicy - druga moze wziac reszte
            float moreL = Math.min(spare, maxL - colL - addL);
            addL += moreL;
            spare -= moreL;
            addR += Math.min(spare, maxR - colR - addR);
        }
        colL += addL;
        colR += addR;
        left.set(0, 0, colL, h);
        right.set(w - colR, 0, colR, h);
        float midX = left.right() + PAD, midW = right.x - PAD - midX;
        topBar.set(midX, h - topH, midW, topH);
        view.set(Math.round((left.right() + (right.x - left.right() - vw) / 2f) * uiScale) / uiScale, h - topH - BORDER - vh, vw, vh);
        console.set(midX, PAD, midW, view.y - BORDER - GAP - PAD);

        layoutLeft(h, vipOpen, skillsOpen, skillRows);
        layoutRight(h, actionCount);
    }

    private void layoutLeft(float h, boolean vipOpen, boolean skillsOpen, int skillRows) {
        float lw = left.w - 2 * PAD;
        float mm = Math.min(lw - 6, h * (compact ? 0.34f : 0.3f));
        minimap.set(left.x + PAD + 3 + (lw - 6 - mm) / 2f, h - PAD - 3 - mm, mm, mm);
        float top = minimap.y - 3 - GAP, bottom = PAD;
        float avail = top - bottom;
        float natural = HEADER + skillRows * SKILL_ROW + 6;
        float vipMin = vipOpen ? HEADER + 2 * VIP_ROW + 6 : HEADER;
        float skH = skillsOpen ? Math.max(HEADER, Math.min(natural, avail - vipMin - GAP)) : HEADER;
        float vipH = vipOpen ? Math.max(HEADER, avail - skH - GAP) : HEADER;
        vip.set(left.x + PAD, top - vipH, lw, vipH);
        skills.set(left.x + PAD, vip.y - GAP - skH, lw, skH);
    }

    private void layoutRight(float h, int actionCount) {
        float iw = right.w - 2 * PAD;
        float x0 = right.x + PAD;
        // ekwipunek 3 x 4 pola z naglowkiem, obok dwie kolumny po 4 przyciski (wysokosc ekwipunku)
        float maxCell = compact ? 30 : 36, maxBtn = compact ? 28 : 34;
        eqCell = Math.min(maxCell, (float) Math.floor((iw - 18 - 2 * GAP) / 4.7f));
        float eqW = 3 * eqCell + 2 * GAP + 12, eqH = HEADER + 4 * eqCell + 3 * GAP + 8;
        toggleSize = Math.min(maxBtn, Math.min((iw - eqW - 2 * GAP) / 2f, (eqH - (TOGGLE_ROWS - 1) * GAP) / TOGGLE_ROWS));
        float used = eqW + 2 * (GAP + toggleSize);
        float ex = x0 + Math.max(0, (iw - used) / 2f);
        equipment.set(ex, h - PAD - eqH, eqW, eqH);
        float step = (eqH - toggleSize) / (TOGGLE_ROWS - 1);
        for (int c = 0; c < 2; c++) {
            float bx = equipment.right() + GAP + c * (toggleSize + GAP);
            for (int r = 0; r < TOGGLE_ROWS; r++) {
                toggles[c * TOGGLE_ROWS + r].set(bx, equipment.top() - toggleSize - r * step, toggleSize, toggleSize);
            }
        }
        // pasek czarow 5 x 2 na dole (pola wieksze niz dawniej), nad nim przyciski akcji
        hotbarCell = Math.min(compact ? 30 : 44, (float) Math.floor((iw - 8 - (HOTBAR_COLS - 1) * GAP) / HOTBAR_COLS));
        float hbW = HOTBAR_COLS * hotbarCell + (HOTBAR_COLS - 1) * GAP;
        hotbar.set(x0 + (iw - hbW) / 2f, PAD + 4, hbW, 2 * hotbarCell + GAP);
        actionRows = actionCount <= 0 ? 0 : (actionCount + HOTBAR_COLS - 1) / HOTBAR_COLS;
        float ah = actionRows == 0 ? 0 : actionRows * hotbarCell + (actionRows - 1) * GAP;
        actions.set(hotbar.x, hotbar.top() + 4 + GAP + 2, hbW, ah);
        float battleBottom = (actionRows == 0 ? hotbar.top() + 4 : actions.top()) + GAP + 2;
        battle.set(x0, battleBottom, iw, equipment.y - GAP - battleBottom);
    }

    /** Pole przycisku akcji nr i (rzedami od gory, po HOTBAR_COLS, ostatni rzad wysrodkowany). */
    public Rect actionCell(int i, int count, Rect out) {
        int row = i / HOTBAR_COLS, col = i % HOTBAR_COLS;
        int inRow = Math.min(HOTBAR_COLS, count - row * HOTBAR_COLS);
        float rowW = inRow * hotbarCell + (inRow - 1) * GAP;
        float x = actions.x + (actions.w - rowW) / 2f + col * (hotbarCell + GAP);
        float y = actions.top() - hotbarCell - row * (hotbarCell + GAP);
        out.set(x, y, hotbarCell, hotbarCell);
        return out;
    }

    /** Pole paska czarow nr i (0..4 gorny rzad, 5..9 dolny). */
    public Rect hotbarCell(int i, Rect out) {
        int row = i / HOTBAR_COLS, col = i % HOTBAR_COLS;
        out.set(hotbar.x + col * (hotbarCell + GAP), hotbar.top() - hotbarCell - row * (hotbarCell + GAP), hotbarCell, hotbarCell);
        return out;
    }

    /** Wszystkie stale elementy ukladu (do sprawdzania nakladania w testach). */
    public Rect[] fixedPanels() {
        Rect framed = new Rect();
        framed.set(view.x - BORDER, view.y - BORDER, view.w + 2 * BORDER, view.h + 2 * BORDER);
        Rect mm = new Rect();
        mm.set(minimap.x - 3, minimap.y - 3, minimap.w + 6, minimap.h + 6);
        Rect[] out = new Rect[10 + toggles.length];
        int i = 0;
        out[i++] = topBar;
        out[i++] = framed;
        out[i++] = console;
        out[i++] = mm;
        out[i++] = vip;
        out[i++] = skills;
        out[i++] = equipment;
        out[i++] = battle;
        out[i++] = actions;
        Rect hb = new Rect();
        hb.set(hotbar.x - 4, PAD, hotbar.w + 8, hotbar.h + 8);
        out[i++] = hb;
        for (Rect t : toggles) out[i++] = t;
        return out;
    }
}
