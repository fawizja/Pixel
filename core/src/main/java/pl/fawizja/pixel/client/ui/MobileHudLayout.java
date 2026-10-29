package pl.fawizja.pixel.client.ui;

/**
 * Uklad HUD-u na telefon, "wariant B" z makiety zatwierdzonej przez wlasciciela (2026-10-29,
 * scratchpad/mock/template3.html, funkcja phone()): widok gry na calym ekranie pod HUD-em, u gory jedna drewniana
 * ramka (poziom, paski HP i many, 8 ikon kategorii), pod nia po prawej minimapa 120 dp i obok niej (na lewo) lista
 * walki, w prawym dolnym rogu luk 5 czarow i 2 mikstur, obok rzad Atak / Podazaj / Tarcza / PvP, joystick w lewym
 * dolnym rogu, zwijany czat na dole.
 *
 * <p>Makieta ma poczatek w lewym gornym rogu; tu wszystko jest przeliczone na libGDX (poczatek w lewym dolnym
 * rogu, y w gore). 1 jednostka = 1 dp. Czysta matematyka bez libGDX (testy: MobileHudLayoutTest).
 */
public final class MobileHudLayout {

    /** Ikony kategorii w ramce: Plecak, Ekwipunek, Umiejetnosci, VIP, Zadania, Ustawienia, Sklep, Wyloguj. */
    public static final int ICONS = 8;
    public static final int ICON_BAG = 0, ICON_EQUIP = 1, ICON_SKILLS = 2, ICON_VIP = 3, ICON_QUESTS = 4, ICON_SETTINGS = 5, ICON_SHOP = 6,
            ICON_LOGOUT = 7;
    public static final float ICON = 32f, ICON_GAP = 3f, ICON_MARGIN = 8f;
    /** Gorna ramka: y 2..42 od gory ekranu, 3 dp od bokow. */
    public static final float FRAME_TOP = 2f, FRAME_H = 40f;
    /** Paski HP i many: od x = 46 do 8 dp przed pierwsza ikona, po 13 dp wysokosci. */
    public static final float BAR_X = 46f, BAR_H = 13f;
    /** Minimapa (kwadrat) i lista walki obok niej: po 120 dp szerokosci (2026-10-29: 90 bylo za waskie). */
    public static final float MINIMAP = 120f, BATTLE_W = 120f;
    /** Lista walki miesci najwyzej tyle wierszy (dalej przewijanie palcem), zeby nie zaslaniac widoku gry. */
    public static final int BATTLE_MAX_ROWS = 6;
    /** Luk czarow: srodek 20 dp od prawego i dolnego brzegu, promienie lukow i przyciskow. */
    public static final float ARC_INSET = 20f, R1 = 110f, R2 = 58f, SPELL_R = 19.5f, POTION_R = 16f, ROW_R = 19f;
    public static final float[] SPELL_DEG = {180f, 202.5f, 225f, 247.5f, 270f};
    public static final float[] POTION_DEG = {196f, 254f};
    /** Pola paska akcji pod przyciskami luku (czary 1-5) i miksturami (6-7) - pierwsze 7 pol paska. */
    public static final int[] SPELL_SLOTS = {1, 2, 6, 0, 3};
    public static final int[] POTION_SLOTS = {4, 5};
    /** Rzad (od prawej) Atak / Podazaj / Tarcza / PvP: y = 22 od dolu, x = luk - 50 - i * 42. */
    public static final float ROW_Y = 22f, ROW_STEP = 42f;
    public static final int ROW_ATTACK = 0, ROW_FOLLOW = 1, ROW_SHIELD = 2, ROW_PVP = 3, ROW_COUNT = 4;
    /** Przyciski chwilowe (Mow, Wez, Low, Kon): nad rzedem, czwarty jeszcze wyzej. */
    public static final int CONTEXT = 4;
    public static final float JOY_X = 78f, JOY_R = 54f;
    public static final float WIN_W = 336f, WIN_TOP = 48f, WIN_H = 196f, WIN_SMALL_H = 120f;
    public static final float CHAT_X = 142f, CHAT_H = 128f;
    public static final float TAB_W = 100f, TAB_H = 22f, HANDLE_W = 110f, HANDLE_H = 19f;
    /** Wiersz listy walki 21 dp (latwo trafic palcem) i naglowek "Walka (n)". */
    public static final float BATTLE_ROW = 21f, BATTLE_HEAD = 16f;
    public static final float PICK_W = 312f, PICK_MAX_H = 236f, PICK_ROW = 27f;
    /** Najmniejsze pole dotyku (dp). */
    public static final float MIN_TOUCH = 36f;

    /** Kolo (srodek, promien) z polem dotyku co najmniej {@link #MIN_TOUCH}. */
    public static final class Circle {
        public float x, y, r;

        void set(float x, float y, float r) {
            this.x = x;
            this.y = y;
            this.r = r;
        }

        /** Promien pola dotyku: rysowany, ale nie mniej niz polowa {@link #MIN_TOUCH}. */
        public float hitR() { return Math.max(r, MIN_TOUCH / 2f); }

        public boolean hit(float px, float py) {
            float dx = px - x, dy = py - y, hr = hitR();
            return dx * dx + dy * dy <= hr * hr;
        }

        public boolean overlaps(Circle o) {
            float dx = x - o.x, dy = y - o.y, rr = r + o.r - 0.01f;
            return dx * dx + dy * dy < rr * rr;
        }

        public boolean overlaps(HudLayout.Rect o) {
            float nx = Math.max(o.x, Math.min(x, o.right())), ny = Math.max(o.y, Math.min(y, o.top()));
            float dx = x - nx, dy = y - ny, rr = r - 0.01f;
            return dx * dx + dy * dy < rr * rr;
        }

        /** Kwadrat opisany na kole (do sprawdzania, czy lezy na ekranie). */
        public HudLayout.Rect bounds() {
            HudLayout.Rect b = new HudLayout.Rect();
            b.set(x - r, y - r, 2 * r, 2 * r);
            return b;
        }

        @Override
        public String toString() { return String.format(java.util.Locale.ROOT, "(%.1f,%.1f r%.1f)", x, y, r); }
    }

    public float w, h;
    public final HudLayout.Rect frame = new HudLayout.Rect();
    public final HudLayout.Rect hpBar = new HudLayout.Rect();
    public final HudLayout.Rect mpBar = new HudLayout.Rect();
    /** Ikony 32 x 32 i ich pola dotyku (36 dp szerokosci, od ramki do minimapy; sasiednie stykaja sie o 1 dp). */
    public final HudLayout.Rect[] icons = new HudLayout.Rect[ICONS];
    public final HudLayout.Rect[] iconHits = new HudLayout.Rect[ICONS];
    /** Wiersz ikon stanu i wzmocnien pod ramka po lewej. */
    public final HudLayout.Rect statusRow = new HudLayout.Rect();
    public final HudLayout.Rect minimap = new HudLayout.Rect();
    public final HudLayout.Rect battle = new HudLayout.Rect();
    public float arcX, arcY;
    public final Circle[] spells = new Circle[SPELL_DEG.length];
    public final Circle[] potions = new Circle[POTION_DEG.length];
    public final Circle[] row = new Circle[ROW_COUNT];
    public final Circle[] context = new Circle[CONTEXT];
    public final Circle joystick = new Circle();
    /** Zwiniety czat ("Czat ▲") i jego pole dotyku (36 dp wysokosci). */
    public final HudLayout.Rect chatTab = new HudLayout.Rect();
    public final HudLayout.Rect chatTabHit = new HudLayout.Rect();
    /** Rozwiniety czat: panel od x = 142 do 8 dp przed rzedem Atak/Tarcza/PvP, uchwyt "Czat ▼" nad nim. */
    public final HudLayout.Rect chatPanel = new HudLayout.Rect();
    public final HudLayout.Rect chatHandle = new HudLayout.Rect();
    public final HudLayout.Rect chatHandleHit = new HudLayout.Rect();

    public MobileHudLayout() {
        for (int i = 0; i < ICONS; i++) {
            icons[i] = new HudLayout.Rect();
            iconHits[i] = new HudLayout.Rect();
        }
        for (int i = 0; i < spells.length; i++) spells[i] = new Circle();
        for (int i = 0; i < potions.length; i++) potions[i] = new Circle();
        for (int i = 0; i < row.length; i++) row[i] = new Circle();
        for (int i = 0; i < context.length; i++) context[i] = new Circle();
    }

    /** Lewa krawedz pierwszej ikony. */
    public float iconsX() { return w - ICON_MARGIN - (ICONS * ICON + (ICONS - 1) * ICON_GAP); }

    /** Prostokat z gornym brzegiem yTop (od gory ekranu, jak w makiecie). */
    private void top(HudLayout.Rect r, float x, float yTop, float rw, float rh) {
        r.set(x, h - yTop - rh, rw, rh);
    }

    /** Punkt luku: kat jak w makiecie (y w dol), wynik w libGDX (y w gore). */
    private void onArc(Circle c, float radius, float deg, float r) {
        double a = Math.toRadians(deg);
        c.set(arcX + (float) Math.cos(a) * radius, arcY - (float) Math.sin(a) * radius, r);
    }

    public void compute(float w, float h) {
        this.w = w;
        this.h = h;
        top(frame, 3, FRAME_TOP, w - 6, FRAME_H);
        float ix0 = iconsX();
        float bw = ix0 - 8 - BAR_X;
        top(hpBar, BAR_X, 8, bw, BAR_H);
        top(mpBar, BAR_X, 24, bw, BAR_H);
        for (int i = 0; i < ICONS; i++) {
            float x = ix0 + i * (ICON + ICON_GAP);
            top(icons[i], x, 6, ICON, ICON);
            float hx = x + ICON / 2f - MIN_TOUCH / 2f;
            top(iconHits[i], hx, FRAME_TOP, MIN_TOUCH, 46 - FRAME_TOP);
        }
        float mx = w - ICON_MARGIN - MINIMAP;
        top(minimap, mx, 46, MINIMAP, MINIMAP);
        arcX = w - ARC_INSET;
        arcY = ARC_INSET;
        float x0 = arcX - R1 - 50;
        for (int i = 0; i < spells.length; i++) onArc(spells[i], R1, SPELL_DEG[i], SPELL_R);
        for (int i = 0; i < potions.length; i++) onArc(potions[i], R2, POTION_DEG[i], POTION_R);
        for (int i = 0; i < row.length; i++) row[i].set(x0 - i * ROW_STEP, ROW_Y, ROW_R);
        for (int i = 0; i < 3; i++) context[i].set(x0 - i * ROW_STEP, ROW_Y + ROW_STEP, ROW_R);
        context[3].set(x0, ROW_Y + 2 * ROW_STEP, ROW_R);
        // lista walki na lewo od minimapy (pod minimapa 120 dp na telefonie 360 dp nie ma miejsca nad lukiem):
        // od ramki w dol, najwyzej BATTLE_MAX_ROWS wierszy i nie nizej niz 4 dp nad przyciskami chwilowymi
        float bx = mx - 4 - BATTLE_W;
        float bottomTop = h - (context[3].y + ROW_R) - 4;
        float bh = Math.min(BATTLE_HEAD + BATTLE_MAX_ROWS * BATTLE_ROW + 4, bottomTop - 46);
        top(battle, bx, 46, BATTLE_W, Math.max(0, bh));
        top(statusRow, 8, 46, Math.max(0, bx - 8 - 8), 14);
        joystick.set(JOY_X, JOY_X, JOY_R);
        chatTab.set(w / 2f - TAB_W / 2f, 0, TAB_W, TAB_H - 1);
        chatTabHit.set(chatTab.x, 0, TAB_W, MIN_TOUCH);
        float chatRight = rowLeft() - 8;
        chatPanel.set(CHAT_X, 0, Math.max(0, chatRight - CHAT_X), CHAT_H);
        chatHandle.set(chatPanel.cx() - HANDLE_W / 2f, CHAT_H, HANDLE_W, HANDLE_H);
        chatHandleHit.set(chatHandle.x, CHAT_H, HANDLE_W, MIN_TOUCH);
    }

    /** Lewa krawedz rzedu Atak / Podazaj / Tarcza / PvP. */
    public float rowLeft() { return row[row.length - 1].x - ROW_R; }

    /**
     * Okno kategorii (336 dp, pod ramka, na srodku); przy rozwinietym czacie nizsze, zeby go nie zaslaniac. Na
     * waskim ekranie (780-800 dp) przesuniete w lewo tak, zeby nie zaslanialo listy walki.
     */
    public HudLayout.Rect window(boolean chatOpen, boolean small, HudLayout.Rect out) {
        float wh = small ? WIN_SMALL_H : chatOpen ? Math.min(168, h - CHAT_H - 24 - WIN_TOP) : WIN_H;
        float x = Math.min(Math.round((w - WIN_W) / 2f), Math.round(battle.x - 6 - WIN_W));
        top(out, x, WIN_TOP, WIN_W, wh);
        return out;
    }

    /** Okno wyboru akcji przycisku luku (po przytrzymaniu): nad lukiem, na lewo od niego. */
    public HudLayout.Rect picker(HudLayout.Rect out) {
        float ph = Math.min(h - 60, PICK_MAX_H);
        float px = Math.max(140, w - 170 - PICK_W);
        float py = Math.max(44, h - 16 - ph);
        top(out, px, py, PICK_W, ph);
        return out;
    }

    /** Wiersze wyboru w jednej kolumnie okna wyboru. */
    public int pickerRows(HudLayout.Rect picker) { return Math.max(1, (int) Math.floor((picker.h - 32) / PICK_ROW)); }

    /** Wiersze listy walki, ktore sie mieszcza. */
    public int battleRowsVisible() { return Math.max(0, (int) Math.floor((battle.h - BATTLE_HEAD) / BATTLE_ROW)); }

    /** Ikona pod palcem (najblizszy srodek, gdy pola sasiadow sie stykaja) albo -1. */
    public int iconAt(float px, float py) {
        int best = -1;
        float bd = Float.MAX_VALUE;
        for (int i = 0; i < ICONS; i++) {
            if (!iconHits[i].contains(px, py)) continue;
            float d = Math.abs(px - icons[i].cx());
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    /** Okragle przyciski stale widoczne (luk, mikstury, rzad, joystick). */
    public Circle[] fixedCircles() {
        Circle[] out = new Circle[spells.length + potions.length + row.length + 1];
        int k = 0;
        for (Circle c : spells) out[k++] = c;
        for (Circle c : potions) out[k++] = c;
        for (Circle c : row) out[k++] = c;
        out[k] = joystick;
        return out;
    }
}
