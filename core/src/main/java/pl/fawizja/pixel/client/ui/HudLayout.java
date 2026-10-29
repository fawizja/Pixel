package pl.fawizja.pixel.client.ui;

/**
 * Uklad HUD-u jak w kliencie Tibii (2026-10-28, opis wlasciciela): lewa kolumna (minimapa, lista VIP, zwijane
 * umiejetnosci), prawa kolumna (ekwipunek z przyciskami obok, lista walki, pasek czarow), miedzy nimi u gory
 * paski HP/MP z poziomem, pod nimi widok gry 15x11 pol (cala liczba pikseli na pole) i pod widokiem konsola
 * z zakladkami czatu.
 *
 * <p>Telefon w poziomie (niski ekran, {@link #compact}, 2026-10-29 po przegladzie zrzutow z telefonow): widok gry
 * jest ograniczony wysokoscia (15x11, cale piksele na pole), wiec reszta szerokosci idzie do kolumn - przyciski,
 * pola paska czarow i ekwipunku maja co najmniej {@link #MIN_TOUCH} jednostek (dp), obok ekwipunku sa 2-3 kolumny
 * przyciskow (w nich tez ATAK, UZYJ i przyciski chwilowe), a lista walki miesci 5-6 wierszy. Lewa kolumna
 * dopasowuje sie do tresci: VIP zajmuje tyle, ile potrzebuje, umiejetnosci sa rozwiniete, gdy sie mieszcza,
 * a reszta wysokosci powieksza minimape.
 *
 * <p>Czysta matematyka bez libGDX (testy: HudLayoutTest). Jednostki interfejsu = piksele ekranu / uiScale (dp).
 * Prostokaty maja poczatek w lewym dolnym rogu (jak w libGDX).
 */
public final class HudLayout {

    public static final float VIEW_TILES_W = 15f;
    public static final float VIEW_TILES_H = 11f;
    public static final float PAD = 4f;
    public static final float GAP = 3f;
    /** Naglowek malego okna (ekwipunek, VIP, umiejetnosci, walka) na duzym ekranie. */
    public static final float HEADER = 23f;
    public static final float SKILL_ROW = 15f;
    public static final float VIP_ROW = 16f;
    /** Ramka wokol widoku gry. */
    public static final float BORDER = 2f;
    /** Przyciski w jednej kolumnie obok ekwipunku. */
    public static final int TOGGLE_ROWS = 4;
    /** Najwiecej kolumn przyciskow obok ekwipunku. */
    public static final int MAX_TOGGLE_COLS = 3;
    /** Pola paska czarow (5 x 2) i przyciski akcji w rzedzie. */
    public static final int HOTBAR_COLS = 5;
    /** Przyciski obok ekwipunku: bieg, tarcza, zadania, pochodnia, wyjscie, PvP, opcje (indeks wyjscia). */
    public static final int TOGGLE_COUNT = 7;
    public static final int LOGOUT_TOGGLE = 4;
    public static final int TORCH_TOGGLE = 3;
    /** Kolejnosc chowania przelacznikow w menu "Opcje" na waskim telefonie: najpierw wyjscie, potem pochodnia. */
    private static final int[] FOLD_ORDER = {LOGOUT_TOGGLE, TORCH_TOGGLE};
    /** Najmniejszy przycisk i pole na telefonie (dp) oraz najwiekszy. */
    public static final float MIN_TOUCH = 36f;
    public static final float MAX_TOUCH = 40f;

    /**
     * Czcionka (lsans-15): wysokosc wielkiej litery w pikselach czcionki i najmniejsza skala napisow - 7 dp
     * wielkiej litery (przeglad 2026-10-29: mniejszych napisow na telefonie nie da sie czytac).
     */
    public static final float FONT_CAP = 11f;
    public static final float FONT_LINE = 18f;
    public static final float MIN_FONT = 0.64f;

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
    /**
     * Pola przyciskow obok ekwipunku: [kolumna * TOGGLE_ROWS + wiersz], kolumna 0 blizej ekwipunku; nieuzywane
     * kolumny puste (szerokosc 0).
     */
    public final Rect[] toggles = new Rect[MAX_TOGGLE_COLS * TOGGLE_ROWS];
    public final Rect battle = new Rect();
    /** Przyciski akcji, ktore nie zmiescily sie obok ekwipunku: rzedy nad paskiem czarow, po HOTBAR_COLS. */
    public final Rect actions = new Rect();
    /** Pasek czarow 5 x 2. */
    public final Rect hotbar = new Rect();

    /** Piksele ekranu na pole widoku gry (cala liczba). */
    public float tilePixels;
    /** Niski ekran (telefon w poziomie): przyciski obok ekwipunku, ekwipunek bez naglowka, krotka konsola. */
    public boolean compact;
    public float eqCell;
    /** Bok przycisku obok ekwipunku (i przycisku akcji). */
    public float toggleSize;
    public float hotbarCell;
    /** Rzedy przyciskow akcji nad paskiem czarow (te, ktore nie zmiescily sie obok ekwipunku). */
    public int actionRows;
    /** Kolumny przyciskow obok ekwipunku (2 albo 3). */
    public int toggleCols;
    /** Wyjscie schowane w menu "Opcje" (waski telefon: ATAK i UZYJ zamiast niego obok ekwipunku). */
    public boolean foldLogout;
    /** Przelaczniki schowane w menu "Opcje" (wyjscie, potem pochodnia), gdy obok ekwipunku brakuje pol. */
    public final boolean[] folded = new boolean[TOGGLE_COUNT];
    /** Pole przycisku obok ekwipunku dla przycisku nr i (TOGGLE_COUNT) albo -1 (schowany). */
    public final int[] toggleSlot = new int[TOGGLE_COUNT];
    /** Pole obok ekwipunku dla przycisku akcji nr i albo -1 (rzad nad paskiem czarow); dlugosc = actionCount. */
    public int[] actionSlot = new int[0];
    /** Przyciski akcji w rzedach nad paskiem czarow (bez tych obok ekwipunku). */
    public int rowActions;
    /** Okno umiejetnosci rozwiniete (wybor gracza albo samo, gdy sie miesci). */
    public boolean skillsShown;

    /** Naglowek malego okna, wiersze okien i listy walki, naglowek ekwipunku (0 = bez naglowka). */
    public float header = HEADER;
    public float skillRow = SKILL_ROW;
    public float vipRow = VIP_ROW;
    public float battleRow = 18f;
    public float eqHeader = HEADER;
    /** Pasek zakladek czatu (widoczny) i wiersz konsoli. */
    public float tabH = 18f;
    public float conRow = 13f;

    public HudLayout() {
        for (int i = 0; i < toggles.length; i++) toggles[i] = new Rect();
    }

    /** Wysokosc gornego paska (HP i MP, pod nimi poziom, postep i informacje). */
    public float topBarHeight() { return compact ? 32f : 36f; }

    /** Najnizsza konsola: 2 wiersze na telefonie, 3 na niskim ekranie, 5 na wyzszym. */
    private float consoleMin(float h) {
        int lines = compact ? 2 : h < 600 ? 3 : 5;
        return lines * conRow + 8 + tabH;
    }

    /** Uklad z domyslnymi oknami (VIP na 2 wiersze, umiejetnosci wg wyboru). */
    public void compute(float w, float h, float uiScale, int actionCount, boolean vipOpen, boolean skillsOpen, int skillRows) {
        compute(w, h, uiScale, actionCount, vipOpen, 2, skillsOpen, skillRows);
    }

    /**
     * Liczy uklad.
     *
     * @param w,h          rozmiar ekranu w jednostkach interfejsu
     * @param uiScale      pikseli ekranu na jednostke (telefon 2..3.5)
     * @param actionCount  widoczne przyciski akcji (ATAK, UZYJ i chwilowe)
     * @param vipOpen      lista VIP rozwinieta
     * @param vipRows      wiersze tresci listy VIP (znajomi albo zawiniety napis "brak znajomych")
     * @param skillsOpen   okno umiejetnosci: true/false = wybor gracza, null = rozwiniete, gdy sie miesci
     * @param skillRows    wiersze okna umiejetnosci (poziom, doswiadczenie, skille)
     */
    public void compute(float w, float h, float uiScale, int actionCount, boolean vipOpen, int vipRows, Boolean skillsOpen, int skillRows) {
        compact = h < 420;
        header = compact ? 20f : HEADER;
        skillRow = compact ? 13f : SKILL_ROW;
        vipRow = compact ? 14f : VIP_ROW;
        battleRow = compact ? 15f : 18f;
        eqHeader = compact ? 0f : HEADER;
        tabH = compact ? 20f : h >= 500 ? 18f : 16f;
        conRow = compact ? 12f : 13f;
        float topH = topBarHeight();
        float ch = h - topH - 2 * BORDER - GAP - consoleMin(h) - PAD;
        float tileH = (float) Math.floor(ch * uiScale / VIEW_TILES_H);
        float colL, colR;
        if (compact) {
            // przyciski jak najwieksze (36..40 dp), byle lista walki miescila 6 wierszy (co najmniej 5)
            float s = MIN_TOUCH;
            for (float c = MAX_TOUCH; c > MIN_TOUCH; c -= 1f) {
                if (battleRowsFor(h, c, 0) >= 6) {
                    s = c;
                    break;
                }
            }
            toggleSize = s;
            eqCell = s;
            hotbarCell = s;
            float minL = 116, maxL = 210;
            // trzy kolumny przyciskow, chyba ze zabralyby widokowi gry piksele na pole - wtedy dwie
            float r3 = rightWidth(s, 3), r2 = rightWidth(s, 2);
            float t3 = Math.min(tileH, (float) Math.floor((w - minL - r3 - 2 * (PAD + BORDER)) * uiScale / VIEW_TILES_W));
            float t2 = Math.min(tileH, (float) Math.floor((w - minL - r2 - 2 * (PAD + BORDER)) * uiScale / VIEW_TILES_W));
            toggleCols = t3 >= t2 ? 3 : 2;
            tilePixels = Math.max(1f, toggleCols == 3 ? t3 : t2);
            colL = minL;
            colR = toggleCols == 3 ? r3 : r2;
            float vw = VIEW_TILES_W * tilePixels / uiScale;
            // reszta szerokosci: lewa kolumna (wieksza minimapa, dluzsze imiona), potem prawa (szersza lista walki)
            float extra = Math.max(0, w - colL - colR - 2 * (PAD + BORDER) - vw);
            float addL = Math.min(extra, maxL - colL);
            colL += addL;
            colR += extra - addL;
        } else {
            colL = 160;
            // prawa kolumna miesci ekwipunek z polami 36 i dwie kolumny przyciskow 36 (tablet: ekran dotykowy)
            colR = 212;
            float maxL = 250, maxR = 290;
            // widok gry: najwieksza cala liczba pikseli na pole, przy ktorej mieszcza sie kolumny i konsola
            float cw = w - colL - colR - 2 * (PAD + BORDER);
            tilePixels = Math.max(1f, Math.min(tileH, (float) Math.floor(cw * uiScale / VIEW_TILES_W)));
            float vw = VIEW_TILES_W * tilePixels / uiScale;
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
            toggleCols = 2;
        }
        float vw = VIEW_TILES_W * tilePixels / uiScale, vh = VIEW_TILES_H * tilePixels / uiScale;
        left.set(0, 0, colL, h);
        right.set(w - colR, 0, colR, h);
        float midX = left.right() + PAD, midW = right.x - PAD - midX;
        topBar.set(midX, h - topH, midW, topH);
        view.set(Math.round((left.right() + (right.x - left.right() - vw) / 2f) * uiScale) / uiScale, h - topH - BORDER - vh, vw, vh);
        console.set(midX, PAD, midW, view.y - BORDER - GAP - PAD);

        assignButtons(actionCount);
        layoutLeft(h, vipOpen, vipRows, skillsOpen, skillRows);
        layoutRight(h, actionCount);
    }

    /** Szerokosc prawej kolumny na telefonie: ekwipunek 3 pola i n kolumn przyciskow. */
    private static float rightWidth(float s, int cols) {
        return 2 * PAD + (3 * s + 2 * GAP + 8) + cols * (GAP + s);
    }

    /** Wiersze listy walki na telefonie przy polach s i danej liczbie rzedow akcji nad paskiem czarow. */
    private float battleRowsFor(float h, float s, int rows) {
        float eqH = 4 * s + 3 * GAP + 8;
        float hb = 2 * s + GAP + 8;
        float act = rows == 0 ? 0 : rows * s + (rows - 1) * GAP + GAP;
        float bh = h - 2 * PAD - eqH - hb - 2 * GAP - act;
        return (float) Math.floor((bh - header - 6) / battleRow);
    }

    /** Ktore przyciski stoja obok ekwipunku: najpierw przelaczniki, akcje od zewnetrznej kolumny, reszta nad paskiem. */
    private void assignButtons(int actionCount) {
        int slots = toggleCols * TOGGLE_ROWS;
        // waski telefon (2 kolumny): wyjscie (i w razie potrzeby pochodnia) do menu "Opcje", zeby ATAK, UZYJ
        // i przycisk chwilowy (np. Mow) staly obok ekwipunku, a lista walki zachowala 5 wierszy
        java.util.Arrays.fill(folded, false);
        if (compact && toggleCols == 2) {
            int need = TOGGLE_COUNT + Math.min(3, actionCount) - slots;
            for (int f = 0; f < FOLD_ORDER.length && need > 0; f++, need--) folded[FOLD_ORDER[f]] = true;
        }
        foldLogout = folded[LOGOUT_TOGGLE];
        boolean[] used = new boolean[slots];
        int k = 0;
        for (int i = 0; i < TOGGLE_COUNT; i++) {
            if (folded[i]) {
                toggleSlot[i] = -1;
                continue;
            }
            toggleSlot[i] = k < slots ? k : -1;
            if (k < slots) used[k] = true;
            k++;
        }
        actionSlot = new int[Math.max(0, actionCount)];
        rowActions = 0;
        // kolejnosc wolnych pol: ostatnia kolumna od gory, potem pozostale wolne
        int[] order = new int[slots];
        int n = 0;
        for (int s = (toggleCols - 1) * TOGGLE_ROWS; s < slots; s++) order[n++] = s;
        for (int s = 0; s < (toggleCols - 1) * TOGGLE_ROWS; s++) order[n++] = s;
        int next = 0;
        for (int i = 0; i < actionSlot.length; i++) {
            int slot = -1;
            if (compact) {
                while (next < n && used[order[next]]) next++;
                if (next < n) {
                    slot = order[next];
                    used[slot] = true;
                }
            }
            actionSlot[i] = slot;
            if (slot < 0) rowActions++;
        }
    }

    private void layoutLeft(float h, boolean vipOpen, int vipRows, Boolean skillsOpen, int skillRows) {
        float lw = left.w - 2 * PAD;
        float top0 = h - PAD, bottom = PAD;
        float natural = header + skillRows * skillRow + 6;
        if (compact) {
            // VIP tyle, ile potrzebuje; umiejetnosci rozwiniete, gdy sie mieszcza; reszta dla minimapy
            float vipH = vipOpen ? header + Math.max(1, Math.min(vipRows, 8)) * vipRow + 6 : header;
            float avail = top0 - bottom - 6 - 2 * GAP;
            float mmMin = Math.min(lw - 6, 84);
            boolean fits = avail - vipH - natural >= mmMin;
            skillsShown = skillsOpen != null ? skillsOpen : fits;
            float skH = skillsShown ? Math.max(header, Math.min(natural, avail - vipH - mmMin)) : header;
            float mm = Math.max(0, Math.min(lw - 6, avail - vipH - skH));
            // minimapa u granicy szerokosci - reszte bierze VIP (wiecej znajomych naraz)
            vipH = Math.max(vipH, avail - mm - skH);
            minimap.set(left.x + PAD + 3 + (lw - 6 - mm) / 2f, top0 - 3 - mm, mm, mm);
            float top = minimap.y - 3 - GAP;
            vip.set(left.x + PAD, top - vipH, lw, vipH);
            skills.set(left.x + PAD, vip.y - GAP - skH, lw, skH);
            return;
        }
        skillsShown = skillsOpen == null || skillsOpen;
        float mm = Math.min(lw - 6, h * 0.3f);
        minimap.set(left.x + PAD + 3 + (lw - 6 - mm) / 2f, h - PAD - 3 - mm, mm, mm);
        float top = minimap.y - 3 - GAP;
        float avail = top - bottom;
        float vipMin = vipOpen ? header + 2 * vipRow + 6 : header;
        float skH = skillsShown ? Math.max(header, Math.min(natural, avail - vipMin - GAP)) : header;
        float vipH = vipOpen ? Math.max(header, avail - skH - GAP) : header;
        vip.set(left.x + PAD, top - vipH, lw, vipH);
        skills.set(left.x + PAD, vip.y - GAP - skH, lw, skH);
    }

    private void layoutRight(float h, int actionCount) {
        float iw = right.w - 2 * PAD;
        float x0 = right.x + PAD;
        for (Rect t : toggles) t.set(0, 0, 0, 0);
        if (compact) {
            // ekwipunek 3 x 4 pola bez naglowka, obok toggleCols kolumn przyciskow rowno z wierszami pol
            float s = toggleSize;
            float eqW = 3 * s + 2 * GAP + 8, eqH = 4 * s + 3 * GAP + 8;
            float used = eqW + toggleCols * (GAP + s);
            float ex = x0 + Math.max(0, (iw - used) / 2f);
            equipment.set(ex, h - PAD - eqH, eqW, eqH);
            for (int c = 0; c < toggleCols; c++) {
                float bx = equipment.right() + GAP + c * (s + GAP);
                for (int r = 0; r < TOGGLE_ROWS; r++) {
                    toggles[c * TOGGLE_ROWS + r].set(bx, equipment.top() - 4 - s - r * (s + GAP), s, s);
                }
            }
        } else {
            // ekwipunek 3 x 4 pola z naglowkiem, obok dwie kolumny po 4 przyciski (wysokosc ekwipunku)
            float maxCell = 36, maxBtn = 36;
            eqCell = Math.min(maxCell, (float) Math.floor((iw - 18 - 2 * GAP) / 4.7f));
            float eqW = 3 * eqCell + 2 * GAP + 12, eqH = eqHeader + 4 * eqCell + 3 * GAP + 8;
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
            hotbarCell = Math.min(44, (float) Math.floor((iw - 8 - (HOTBAR_COLS - 1) * GAP) / HOTBAR_COLS));
        }
        // pasek czarow 5 x 2 na dole, nad nim przyciski akcji, ktore nie stoja obok ekwipunku
        float hbW = HOTBAR_COLS * hotbarCell + (HOTBAR_COLS - 1) * GAP;
        hotbar.set(x0 + (iw - hbW) / 2f, PAD + 4, hbW, 2 * hotbarCell + GAP);
        float ac = actionCellSize();
        actionRows = rowActions <= 0 ? 0 : (rowActions + HOTBAR_COLS - 1) / HOTBAR_COLS;
        float ah = actionRows == 0 ? 0 : actionRows * ac + (actionRows - 1) * GAP;
        float aw = HOTBAR_COLS * ac + (HOTBAR_COLS - 1) * GAP;
        actions.set(x0 + (iw - aw) / 2f, hotbar.top() + 4 + GAP + (compact ? 0 : 2), aw, ah);
        float battleBottom = (actionRows == 0 ? hotbar.top() + 4 : actions.top()) + GAP + (compact ? 0 : 2);
        battle.set(x0, battleBottom, iw, equipment.y - GAP - battleBottom);
    }

    /** Bok przycisku akcji w rzedzie nad paskiem czarow. */
    private float actionCellSize() { return compact ? toggleSize : hotbarCell; }

    /** Wiersze listy walki, ktore sie mieszcza (naglowek, odstep 4 u gory i 2 u dolu). */
    public int battleRowsVisible() {
        return Math.max(0, (int) Math.floor((battle.h - header - 6) / battleRow));
    }

    /** Pole przycisku akcji nr i sposrod tych w rzedach (po HOTBAR_COLS, ostatni rzad wysrodkowany). */
    public Rect actionCell(int i, int count, Rect out) {
        float ac = actionCellSize();
        int row = i / HOTBAR_COLS, col = i % HOTBAR_COLS;
        int inRow = Math.min(HOTBAR_COLS, count - row * HOTBAR_COLS);
        float rowW = inRow * ac + (inRow - 1) * GAP;
        float x = actions.x + (actions.w - rowW) / 2f + col * (ac + GAP);
        float y = actions.top() - ac - row * (ac + GAP);
        out.set(x, y, ac, ac);
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
        java.util.List<Rect> out = new java.util.ArrayList<>();
        Rect framed = new Rect();
        framed.set(view.x - BORDER, view.y - BORDER, view.w + 2 * BORDER, view.h + 2 * BORDER);
        Rect mm = new Rect();
        mm.set(minimap.x - 3, minimap.y - 3, minimap.w + 6, minimap.h + 6);
        out.add(topBar);
        out.add(framed);
        out.add(console);
        out.add(mm);
        out.add(vip);
        out.add(skills);
        out.add(equipment);
        out.add(battle);
        out.add(actions);
        Rect hb = new Rect();
        hb.set(hotbar.x - 4, PAD, hotbar.w + 8, hotbar.h + 8);
        out.add(hb);
        for (Rect t : toggles) if (!t.empty()) out.add(t);
        return out.toArray(new Rect[0]);
    }
}
