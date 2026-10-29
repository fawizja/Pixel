package pl.fawizja.pixel.client.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.util.ArrayList;
import java.util.List;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.client.render.TileArt;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.character.Skills;
import pl.fawizja.pixel.magic.CastResult;
import pl.fawizja.pixel.magic.CraftingSystem;
import pl.fawizja.pixel.magic.Recipe;
import pl.fawizja.pixel.magic.SpellKind;
import pl.fawizja.pixel.magic.SpellType;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.time.WorldClock;

/**
 * Interfejs mobilny rysowany w jednostkach niezaleznych od gestosci ekranu
 * (ScreenViewport ze skala zalezna od DPI), dzieki czemu przyciski i joystick
 * maja podobny fizyczny rozmiar na roznych telefonach.
 */
public final class Hud {

    public interface Actions {
        void onButton(String id);
    }

    private final ScreenViewport viewport = new ScreenViewport();
    private final TileArt art;
    private final BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();
    private final VirtualJoystick joystick = new VirtualJoystick();
    private final List<HudButton> buttons = new ArrayList<>();
    private final HudButton use = new HudButton("use", "Użyj");
    private final HudButton run = new HudButton("run", "");
    private final HudButton torch = new HudButton("torch", "");
    private final HudButton bag = new HudButton("bag", "Plecak");
    private final HudButton pick = new HudButton("pick", "Weź");
    private final HudButton diag = new HudButton("diag", "");
    private final HudButton target = new HudButton("target", "Atak");
    private final HudButton defensive = new HudButton("defensive", "");
    private final HudButton book = new HudButton("book", "KSIĘGA");
    private final HudButton fish = new HudButton("fish", "Łów");
    private final HudButton talk = new HudButton("talk", "Mów");
    private final HudButton mount = new HudButton("mount", "Koń");
    /** Przelacznik trybu PvP: biala czaszka = wylaczony, czerwona = wlaczony. */
    private final HudButton pvp = new HudButton("pvp", "");
    /** Dziennik zadan (dawny przycisk KSIEGA; ksiega czarow: klawisz K albo wybor pola paska). */
    private final HudButton quest = new HudButton("quest", "Zadania");
    /** Wylogowanie z potwierdzeniem (nie w trakcie walki - blokada PZ). */
    private final HudButton logout = new HudButton("logout", "Wyjdź");
    /** Sklep (konto premium, promocja, blogoslawienstwo...) za zloto z banku. */
    private final HudButton store = new HudButton("store", "Sklep");
    /** Uklad HUD-u jak w Tibii: kolumny, gorny pasek, widok gry, konsola. */
    private final HudLayout lay = new HudLayout();
    /**
     * Uklad na telefon (wariant B, 2026-10-29): zawsze na Androidzie, na komputerze z -Dpixel.mobileHud=1.
     * Widok gry na calym ekranie, ramka u gory, luk czarow, okna kategorii - {@link MobileHudLayout}.
     */
    private final boolean mobile = mobileHudWanted();
    private final MobileHudLayout mlay = new MobileHudLayout();
    /** Okno kategorii z ikony ramki (plecak, ekwipunek, umiejetnosci, VIP, ustawienia); null = brak. */
    private enum MobileWin { BAG, EQUIP, SKILLS, VIP, SETTINGS }
    private MobileWin mWin;
    /** Czat rozwiniety (telefon); zwiniety = zakladka "Czat" na dole. */
    private boolean mChat;
    private int mSettingsPage;
    /** Przewiniecie listy walki (wiersze) i przeciaganie palcem po niej. */
    private int mBattleScroll;
    private int mBattlePointer = -1;
    private float mBattleDownY;
    private boolean mBattleDragged;
    private int mBattleScrollAtDown;
    /** Strona okna wyboru akcji przycisku luku. */
    private int mPickPage;
    private final HudLayout.Rect mRect = new HudLayout.Rect();

    private static boolean mobileHudWanted() {
        String p = System.getProperty("pixel.mobileHud");
        if (p != null) return p.equals("1") || p.equalsIgnoreCase("true");
        return Gdx.app != null && Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;
    }

    /** Uklad na telefon (wariant B) zamiast ukladu jak w Tibii. */
    public boolean mobile() { return mobile; }
    /** Lista VIP i okno umiejetnosci rozwiniete (umiejetnosci: null = domyslnie, zwiniete na telefonie). */
    private boolean vipOpen = true;
    private Boolean skillsOpen;
    /** Znajomi do listy VIP (z GameScreen: session.friends() albo podglad). */
    private pl.fawizja.pixel.friends.FriendsView vipView;
    /** Inni gracze w poblizu do listy walki (online). */
    public record BattlePlayer(int id, String name, int healthPercent, String skull, boolean ally) {}
    private List<BattlePlayer> battlePlayers = List.of();
    /** Okienko potwierdzenia wylogowania. */
    private boolean logoutConfirm;
    /** Ikony czaszek (tools/art/skull_icons.py, assets/ui/pvp); null bez plikow - wtedy napis. */
    private TextureRegion skullWhite, skullRed, skullYellow, skullOrange, skullBlack, pvpOffIcon, pvpOnIcon;
    private boolean chatEnabled;
    /** Okno menu pokazuje rozmowe z NPC zamiast ksiegi. */
    private boolean npcMode;
    /** Otwarta wymiana z innym graczem (okno handlu) albo null. */
    private pl.fawizja.pixel.trade.TradeView trade;
    /** Menu innego gracza (po dotknieciu go): id, imie, czy wlasnie go atakuje. */
    private record PlayerMenu(int id, String name, boolean attacking, boolean ally) {}
    private PlayerMenu playerMenu;
    /** Okno rankingu z serwera (null = zamkniete). */
    private pl.fawizja.pixel.ranking.RankingView rankingView;
    /** Okno znajomych z serwera (null = zamkniete). */
    private pl.fawizja.pixel.friends.FriendsView friendsView;
    /** Wiersz premii druzyny w pustej zakladce Druzyna (null = brak druzyny). */
    private String partyLine;

    public void setPartyLine(String line) { partyLine = line; }
    /** Okno osiagniec (albo bestiariusza) - dane lokalnej postaci. */
    private boolean achievementsView;
    /** Trzeci widok okna osiagniec: dziennik zadan (aktywne, do oddania, dostepne). */
    private boolean journalMode;
    /** Okno informacyjne (np. bossowie): tytul i wiersze {etykieta, miejsce, stan}; null = brak. */
    private String infoTitle;
    private List<String[]> infoRows = new ArrayList<>();

    // ------------------------------------------------------------------ okno domu przy drzwiach

    /** Okno domu (informacja, okno wlasciciela) albo null. Lezy nad wszystkimi oknami, jak okienko w Tibii. */
    private HouseWindow houseWin;
    private float hwX, hwY, hwW, hwH, hwFieldY;
    private static final float HW_TITLE = 28, HW_LINE = 18, HW_GUEST = 24, HW_BTN = 26, HW_GAP = 6;

    /** Otwiera okno domu: informacja (wolny/zajety, cena, czynsz, Kup) albo okno wlasciciela. */
    public void openHouse(pl.fawizja.pixel.house.House house, pl.fawizja.pixel.house.HouseView view, boolean ownerWindow) {
        if (house == null || view == null || trade != null) return;
        houseWin = new HouseWindow(house, view, ownerWindow && view.access() == pl.fawizja.pixel.house.HouseAccess.OWNER);
        bagOpen = false;
    }

    /** Nowy stan domu (po kupnie, zaproszeniu, odpowiedzi serwera) dla otwartego okna tego domu. */
    public void updateHouse(pl.fawizja.pixel.house.HouseView view) {
        if (houseWin == null || view == null || !houseWin.house.id().equals(view.houseId())) return;
        houseWin.view = view;
        if (view.access() != pl.fawizja.pixel.house.HouseAccess.OWNER && houseWin.mode != HouseWindow.Mode.INFO) {
            houseWin.mode = HouseWindow.Mode.INFO;             // dom sprzedany albo odebrany: zwykla informacja
        }
    }

    public boolean houseOpen() { return houseWin != null; }

    /** Id domu w otwartym oknie albo null. */
    public String houseOpenId() { return houseWin == null ? null : houseWin.house.id(); }

    /** Okno czeka na imie goscia (klawiatura idzie do pola). */
    public boolean houseTyping() { return houseWin != null && houseWin.mode == HouseWindow.Mode.INVITE; }

    public void houseType(char c) { if (houseWin != null) houseWin.type(c); }

    /** Podglad (zrzuty): przycisk okna bez akcji gry i wpisane imie. */
    public void previewHouse(String button, String typed) {
        if (houseWin == null) return;
        if (button != null) houseWin.press(button);
        if (typed != null) for (char c : typed.toCharArray()) houseWin.type(c);
    }

    /** Enter w polu imienia: zaproszenie. */
    public void houseSubmit(Actions actions) { houseButton("house:invitesend", actions); }

    public void closeHouse() { houseWin = null; }

    private void houseButton(String id, Actions actions) {
        if (houseWin == null) return;
        String out = houseWin.press(id);
        if (out == null) {
            if (houseTyping() && Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android) askHouseName();
            return;
        }
        if (out.equals("house:close")) {
            houseWin = null;
            return;
        }
        actions.onButton(out);
    }

    /** Telefon: imie goscia z systemowej klawiatury. */
    private void askHouseName() {
        final HouseWindow win = houseWin;
        Gdx.input.getTextInput(new com.badlogic.gdx.Input.TextInputListener() {
            @Override
            public void input(String text) {
                win.name.setLength(0);
                for (char c : (text == null ? "" : text).toCharArray()) win.type(c);
            }

            @Override
            public void canceled() { }
        }, win.inviteLevel == pl.fawizja.pixel.house.HouseAccess.FULL ? "Zaproś (pełny dostęp)" : "Zaproś (wejście)",
                win.name.toString(), "Imię postaci");
    }

    /** Wymiary okna z liczby wierszy (przyciski po dwa w rzedzie, "Usuń" przy gosciach). */
    private void layoutHouse() {
        HouseWindow win = houseWin;
        int big = 0;
        for (HouseWindow.Button b : win.buttons()) if (!b.inline) big++;
        int btnRows = (big + 1) / 2;
        boolean field = win.mode == HouseWindow.Mode.INVITE;
        hwW = Math.min(w - 32, 400);
        hwH = HW_TITLE + 10 + win.lines().size() * HW_LINE + win.guestRows().size() * HW_GUEST + (field ? HW_BTN + 8 : 0)
                + btnRows * (HW_BTN + HW_GAP) + 12;
        hwX = (w - hwW) / 2f;
        hwY = Math.max(8, (h - hwH) / 2f);
        hwFieldY = hwY + hwH - HW_TITLE - 10 - win.lines().size() * HW_LINE - HW_BTN - 4;
    }

    /** Polozenie przyciskow (po layoutHouse). */
    private List<HouseWindow.Button> houseButtons() {
        HouseWindow win = houseWin;
        List<HouseWindow.Button> btns = win.buttons();
        int big = 0;
        for (HouseWindow.Button b : btns) if (!b.inline) big++;
        boolean field = win.mode == HouseWindow.Mode.INVITE;
        float y = hwY + hwH - HW_TITLE - 10 - win.lines().size() * HW_LINE;
        float bw = (hwW - 30 - HW_GAP) / 2f;
        float top = y - win.guestRows().size() * HW_GUEST - (field ? HW_BTN + 8 : 0) - 6;
        int gi = 0, bi = 0;
        for (HouseWindow.Button b : btns) {
            if (b.inline) {
                b.w = 64;
                b.h = HW_GUEST - 4;
                b.x = hwX + hwW - 15 - b.w;
                b.y = y - (gi + 1) * HW_GUEST + 2;
                gi++;
            } else {
                boolean lastAlone = bi == big - 1 && big % 2 == 1;
                b.w = lastAlone ? hwW - 30 : bw;
                b.h = HW_BTN;
                b.x = hwX + 15 + (lastAlone ? 0 : (bi % 2) * (bw + HW_GAP));
                b.y = top - (bi / 2 + 1) * (HW_BTN + HW_GAP) + HW_GAP;
                bi++;
            }
        }
        return btns;
    }

    private void refreshHouse(GameSession session) {
        PlayerCharacter pc = session.character();
        houseWin.bank = pc.bank().balance();
        houseWin.hasHouse = pc.house() != null;
        houseWin.doorOpen = session.world().openings().isOpen(houseWin.house.door());
    }

    private void drawHouseShapes(ShapeRenderer shapes, GameSession session) {
        refreshHouse(session);
        layoutHouse();
        if (!wstPanel(shapes, "wood", hwX, hwY, hwW, hwH, 24)) window(shapes, hwX, hwY, hwW, hwH, true);
        if (houseWin.mode == HouseWindow.Mode.INVITE) {
            shapes.setColor(0.05f, 0.05f, 0.04f, 0.95f);
            shapes.rect(hwX + 15, hwFieldY, hwW - 30, HW_BTN);
            shapes.setColor(BORDER);
            shapes.rect(hwX + 15, hwFieldY, hwW - 30, 1);
            shapes.rect(hwX + 15, hwFieldY + HW_BTN - 1, hwW - 30, 1);
        }
        for (HouseWindow.Button b : houseButtons()) {
            boolean buy = b.id.startsWith("house:buy:") || b.id.equals("house:invitesend");
            boolean danger = b.id.equals("house:leave") || b.id.equals("house:leaveask") || b.inline;
            if (!b.enabled) shapes.setColor(0.2f, 0.18f, 0.18f, 1f);
            else if (buy) shapes.setColor(0.2f, 0.42f, 0.22f, 1f);
            else if (danger) shapes.setColor(0.45f, 0.18f, 0.14f, 1f);
            else shapes.setColor(0.22f, 0.3f, 0.25f, 1f);
            shapes.rect(b.x, b.y, b.w, b.h);
            shapes.setColor(BORDER);
            shapes.rect(b.x, b.y, b.w, 1);
            shapes.rect(b.x, b.y + b.h - 1, b.w, 1);
        }
    }

    private void drawHouseText(SpriteBatch batch) {
        HouseWindow win = houseWin;
        layoutHouse();
        font.setColor(new Color(1f, 0.92f, 0.7f, 1f));
        text(batch, win.title(), hwX + 12, hwY + hwH - 9);
        font.getData().setScale(0.85f);
        float y = hwY + hwH - HW_TITLE - 12;
        for (String line : win.lines()) {
            boolean warn = line.startsWith("Zaległe") || line.contains("za mało") || line.startsWith("Masz już");
            font.setColor(warn ? new Color(1f, 0.55f, 0.45f, 1f) : line.startsWith("Stan: wolny") ? new Color(0.6f, 1f, 0.6f, 1f) : Color.WHITE);
            text(batch, line, hwX + 15, y);
            y -= HW_LINE;
        }
        float gy0 = hwY + hwH - HW_TITLE - 10 - win.lines().size() * HW_LINE;
        int gi = 0;
        for (String[] g : win.guestRows()) {
            font.setColor(Color.WHITE);
            text(batch, g[0], hwX + 22, gy0 - gi * HW_GUEST - 6);
            font.setColor(new Color(0.8f, 0.85f, 0.7f, 1f));
            text(batch, g[1], hwX + 22 + Math.min(150, hwW * 0.4f), gy0 - gi * HW_GUEST - 6);
            gi++;
        }
        font.setColor(Color.WHITE);
        if (win.mode == HouseWindow.Mode.INVITE) {
            boolean caret = (com.badlogic.gdx.utils.TimeUtils.millis() / 500) % 2 == 0;
            text(batch, win.name + (caret ? "_" : ""), hwX + 22, hwFieldY + HW_BTN - 7);
        }
        for (HouseWindow.Button b : houseButtons()) {
            font.setColor(b.enabled ? Color.WHITE : Color.GRAY);
            centered(batch, b.label, b.x + b.w / 2f, b.y + b.h / 2f);
        }
        font.setColor(Color.WHITE);
        font.getData().setScale(1f);
    }

    /** Dotkniecie przy otwartym oknie domu: przyciski, pole imienia; poza oknem zamyka je. */
    private boolean houseTouch(float px, float py, Actions actions) {
        layoutHouse();
        for (HouseWindow.Button b : houseButtons()) {
            if (px >= b.x && px <= b.x + b.w && py >= b.y && py <= b.y + b.h) {
                if (b.enabled) houseButton(b.id, actions);
                return true;
            }
        }
        if (houseWin.mode == HouseWindow.Mode.INVITE && py >= hwFieldY && py <= hwFieldY + HW_BTN
                && Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android) {
            askHouseName();
            return true;
        }
        if (px < hwX || px > hwX + hwW || py < hwY || py > hwY + hwH) houseWin = null;
        return true;
    }

    public void openInfo(String title, List<String[]> rows) {
        if (trade != null) return;
        achievementsView = false; // openAchievements nie zamknie wtedy okna
        menuOpen = false;
        openAchievements(false);
        infoTitle = title;
        infoRows = new ArrayList<>(rows);
    }

    /** Okno informacyjne o tytule zaczynajacym sie od prefix jest otwarte (np. do odswiezania na zywo). */
    public boolean infoOpen(String prefix) {
        return achievementsView && !journalMode && infoTitle != null && infoTitle.startsWith(prefix);
    }

    /** Tytul okna sklepu (ikona Sklep w ramce, Opcje na komputerze) zaczyna sie od tego slowa. */
    public static final String STORE_TITLE = "Sklep";

    /** Okno sklepu (lista albo potwierdzenie zakupu) jest otwarte. */
    public boolean storeOpen() { return infoOpen(STORE_TITLE); }

    /** Nowa tresc otwartego okna informacyjnego bez zamykania go. */
    public void updateInfo(String title, List<String[]> rows) {
        infoTitle = title;
        infoRows = new ArrayList<>(rows);
    }
    private boolean bestiaryMode;
    private java.util.function.Function<String, String> monsterNames = k -> k;
    /** Okno rynku z serwera (null = zamkniete). */
    private pl.fawizja.pixel.market.MarketView marketView;
    private pl.fawizja.pixel.npc.Npc npc;
    private int npcPage;
    /** U bankiera: widok depozytu przedmiotow zamiast konta. */
    private boolean depotView;
    /** Rozmowa z kupcem w widoku kowala (ulepszanie wyposazenia). */
    private boolean smithView;
    private int bookPage;
    /** Strona czarow w ksiedze (etap 16); -1 = strona z najwyzszym poznanym czarem. */
    private int spellPage = -1;
    /** Strona listy osiagniec (gdy nie mieszcza sie w dwoch kolumnach). */
    private int achPage;
    private int npcQty = 1;
    private static final int[] QUANTITIES = {1, 5, 25, 100};
    private boolean charOpen;
    /** Pasek akcji 5x2 jak w Tibii na telefonie: czar, mikstura albo runa w kazdym polu. */
    private final HudButton[] spellButtons = new HudButton[pl.fawizja.pixel.settings.SpellBar.SLOTS];
    /** Akcja kazdego pola ("cast:...", "item:...", "potion_hp"...) albo null dla pustego. */
    private final String[] barEntries = new String[pl.fawizja.pixel.settings.SpellBar.SLOTS];
    /** Dlugie przytrzymanie pola paska otwiera wybor akcji; krotkie dotkniecie uzywa jej po puszczeniu. */
    private static final long LONG_PRESS_MS = 450;
    private int pressSlot = -1, pressPointer = -1;
    private long pressStart;
    private Actions pressActions;
    /** Wybor akcji dla pola (-1 = zamkniety) i wybrany rodzaj (null = lista rodzajow). */
    private int pickSlot = -1;
    /** Odnowienie pol z mikstura albo runa (0..1, czesc zakryta od gory). */
    private final float[] itemCd = new float[pl.fawizja.pixel.settings.SpellBar.SLOTS];
    private String pickCat;
    /** Wlasny uklad paska (null = tylko automat) i przycisk, ktoremu przypisujemy czar w ksiedze (-1 = brak). */
    private pl.fawizja.pixel.settings.SpellBar customBar = new pl.fawizja.pixel.settings.SpellBar();
    private int assignSlot = -1;

    private boolean menuOpen;
    private float menuX, menuY, menuW, menuH, rowH;
    private final List<MenuRow> menuRows = new ArrayList<>();
    private final HudButton menuClose = new HudButton("menu_close", "X");

    /** Wiersz ksiegi: czar albo receptura. */
    private static final class MenuRow {
        final String action;
        final String label;
        final String detail;
        final boolean enabled;
        float x, y, w, h;

        MenuRow(String action, String label, String detail, boolean enabled) {
            this.action = action;
            this.label = label;
            this.detail = detail;
            this.enabled = enabled;
        }
    }
    private final Vector2 tmp = new Vector2();
    private float uiScale = 1f;
    private float w;
    private float h;

    private boolean bagOpen;
    private int selectedSlot = -1;
    /** Strona plecaka (glowny = 0, dodatkowe plecaki dalej); sloty w akcjach sa globalne: strona * 20 + pole. */
    private int bagPage;
    private int bagPages = 1;
    /** Przeciaganie przedmiotu w plecaku (etap 14): pole zrodlowe, palec i pozycja "ducha" przedmiotu. */
    private int dragSlot = -1, dragPointer = -1;
    private boolean dragging;
    private float dragX, dragY, dragStartX, dragStartY;
    private Actions dragActions;
    private GameSession dragSession;
    private final HudButton bagDetach = new HudButton("bag_detach", "ODEPNIJ");
    private float bagX, bagY, slotSize;
    private final HudButton bagEquip = new HudButton("bag_equip", "ZAŁÓŻ");
    private final HudButton bagDrop = new HudButton("bag_drop", "WYRZUĆ");
    private final HudButton bagUse = new HudButton("bag_use", "UŻYJ");
    private final HudButton bagClose = new HudButton("bag_close", "X");
    private final HudButton bagSort = new HudButton("bag_sort", "SORTUJ");
    private final HudButton bagKeep = new HudButton("bag_keep", "ZACHOWAJ");
    private boolean diagnosticsOpen;
    /** Minimapa w prawym gornym rogu i duza mapa calego pietra (dotkniecie minimapy / klawisz J). */
    private final Minimap minimap = new Minimap();
    private boolean bigMapOpen;
    private float mmX, mmY, mmSize;
    /** Ostatnio narysowana duza mapa {x, y, skala, wysokosc} - do zamiany dotkniecia na pole. */
    private float[] bigMapImg;
    private pl.fawizja.pixel.world.Position walkTarget;

    public void setWalkTarget(pl.fawizja.pixel.world.Position p) { walkTarget = p; }

    /** Wlasne znaczniki gracza (zolte kropki na minimapie i mapie pietra). */
    private pl.fawizja.pixel.settings.MapMarkers markers = new pl.fawizja.pixel.settings.MapMarkers();

    public void setMarkers(pl.fawizja.pixel.settings.MapMarkers m) { markers = m; }

    /** Wiersz wyzwania z serwera (online); null = brak. */
    private String onlineChallenge;
    public void setOnlineChallenge(String line) { onlineChallenge = line == null || line.equals("-") ? null : line; }

    /** Znaczniki gracza, bossa tygodnia (gdy znane jest jego miejsce) i miejsca ostatniej smierci. */
    public List<pl.fawizja.pixel.settings.MapMarkers.Marker> allMarkers(GameSession session) {
        List<pl.fawizja.pixel.settings.MapMarkers.Marker> out = new ArrayList<>(markers.all());
        pl.fawizja.pixel.world.Position boss = session.weeklyBossPlace();
        if (boss != null && session.weeklyBoss() != null) {
            out.add(new pl.fawizja.pixel.settings.MapMarkers.Marker("Boss tygodnia: " + session.weeklyBoss().name(), boss));
        }
        pl.fawizja.pixel.world.Position died = session.combat().lastDeathPlace();
        if (died != null) out.add(new pl.fawizja.pixel.settings.MapMarkers.Marker("Miejsce śmierci", died));
        long t = session.lastUpdateMs();
        if (t - huntGroundCheckedMs >= 1000 || t < huntGroundCheckedMs) {
            huntGroundCheckedMs = t; // szukanie po wszystkich spawnach - najwyzej raz na sekunde
            huntGround = session.huntingGround();
            turnIn = null;
            PlayerCharacter pc = session.character();
            long day = session.today();
            if (pc.daily().doneOn(day) && !pc.daily().claimed() || pc.hunt().active() && pc.hunt().done()) {
                // zadanie gotowe do oddania - najblizszy przewodnik na tym pietrze
                for (pl.fawizja.pixel.npc.Npc n : session.npcs().all()) {
                    if (n.role() != pl.fawizja.pixel.npc.NpcRole.GUIDE || n.position().z() != pc.position().z()) continue;
                    if (turnIn == null || n.position().distanceTo(pc.position()) < turnIn.position().distanceTo(pc.position())) turnIn = n;
                }
            }
        }
        if (turnIn != null) out.add(new pl.fawizja.pixel.settings.MapMarkers.Marker("Oddaj zadanie: " + turnIn.name(), turnIn.position()));
        if (huntGround != null) {
            out.add(new pl.fawizja.pixel.settings.MapMarkers.Marker("Łowisko: " + monsterNames.apply(huntGround.monsterKey()), huntGround.center()));
        }
        return out;
    }

    /** Najblizsze lowisko zlecenia albo zadania dnia (znacznik na mapie), odswiezane co sekunde. */
    private pl.fawizja.pixel.monster.SpawnPoint huntGround;
    /** Przewodnik, u ktorego czeka nagroda za wykonane zadanie dnia albo zlecenie. */
    private pl.fawizja.pixel.npc.Npc turnIn;
    private long huntGroundCheckedMs = Long.MIN_VALUE / 2;

    private List<pl.fawizja.pixel.world.Position> markerPositions(GameSession session) {
        List<pl.fawizja.pixel.world.Position> out = new ArrayList<>();
        for (pl.fawizja.pixel.settings.MapMarkers.Marker m : allMarkers(session)) out.add(m.at());
        return out;
    }
    private static final int MINIMAP_TILES = 60;
    /** Ustawienia gracza pokazywane pod diagnostyka (klikalne wiersze "settings:*"). */
    private pl.fawizja.pixel.settings.GameSettings settings;

    /** Ramki okien, przyciski i pad z OTClientV8 (MIT, assets/ui/otc); null = rysowanie ksztaltami. */
    private com.badlogic.gdx.graphics.g2d.NinePatch windowPatch, buttonPatch, buttonDownPatch;
    private com.badlogic.gdx.graphics.g2d.NinePatch barPatch, miniPatch, panelPatch;
    /** Pole przedmiotu i przyciski malych okien (zamknij, zwin) z OTClientV8. */
    private TextureRegion slotImg, closeImg, minimizeImg;
    private final List<float[]> barRects = new ArrayList<>();
    /** Batch biezacej klatki - okna z obrazkow rysowane sa w trakcie przebiegu ShapeRenderera. */
    private SpriteBatch uiBatch;
    private ShapeRenderer uiShapes;

    private static com.badlogic.gdx.graphics.Texture uiTexture(String name) {
        if (Gdx.files == null) return null;
        String dir = name.startsWith("wst/") ? "ui/" : "ui/otc/";
        com.badlogic.gdx.files.FileHandle f = Gdx.files.internal(dir + name);
        if (!f.exists()) f = Gdx.files.internal("assets/" + dir + name); // desktop z katalogu repozytorium
        if (!f.exists()) return null;
        com.badlogic.gdx.graphics.Texture t = new com.badlogic.gdx.graphics.Texture(f);
        // ramki WsT to pixel art: ostre piksele; skorka OTClient wygladzana
        com.badlogic.gdx.graphics.Texture.TextureFilter filter = name.startsWith("wst/")
                ? com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest : com.badlogic.gdx.graphics.Texture.TextureFilter.Linear;
        t.setFilter(filter, filter);
        return t;
    }

    /** Ramki pol z paczki WsT (22x22, ikona 16x16 w (3,3)); null = stare pole OTClient. */
    private TextureRegion wstSlot, wstEmpty, wstSelected, wstPressed;
    /** Ramki rzadkosci przedmiotu w plecaku i ekwipunku: zwykly, niepospolity, rzadki, epicki, legendarny. */
    private final TextureRegion[] wstRarity = new TextureRegion[5];
    /** Piktogram przycisku diagnostyki (ksiega z paczki WsT). */
    private TextureRegion infoIcon;

    private static TextureRegion wst(String name) {
        com.badlogic.gdx.graphics.Texture t = uiTexture("wst/" + name + ".png");
        return t == null ? null : new TextureRegion(t);
    }

    /** Odstep ikony od krawedzi pola: w ramce WsT ikona wypelnia wnetrze ramki. */
    private float slotPad(float cell) {
        return wstSlot != null ? cell * 3f / 22f : Math.max(2, cell * 0.08f);
    }

    /** Ramka pola dla przedmiotu: kolor rzadkosci wg ceny (puste pole - wglebione). */
    private TextureRegion slotFrame(ItemStack st, pl.fawizja.pixel.item.ItemRegistry items) {
        if (wstSlot == null) return slotImg;
        if (st == null) return wstEmpty != null ? wstEmpty : wstSlot;
        int tier = pl.fawizja.pixel.item.Rarity.tier(items.get(st.itemId()));
        TextureRegion r = tier > 0 ? wstRarity[tier - 1] : null;
        return r != null ? r : wstSlot;
    }

    /** Panele okien z paczki WsT: styl -> {cialo, pasek tytulu albo null} (piksele x2). */
    private final java.util.Map<String, Tiled9[]> wstPanels = new java.util.HashMap<>();
    private static final float WST_PANEL_SCALE = 2f;

    private void loadWstPanels() {
        for (String name : new String[] {"wood", "grim", "stone", "arcane", "classic", "gold", "royal"}) {
            com.badlogic.gdx.graphics.Texture body = uiTexture("wst/panel_" + name + ".png");
            if (body == null) continue;
            com.badlogic.gdx.graphics.Texture title = uiTexture("wst/panel_" + name + "_title.png");
            wstPanels.put(name, new Tiled9[] {new Tiled9(body, body.getWidth() >= 32 ? 10 : 5, body.getWidth() >= 32 ? 10 : 5),
                    title == null ? null : new Tiled9(title, 4, 3)});
        }
    }

    /**
     * Panel 9-slice z pixel artu: rogi bez zmian, krawedzie i srodek powtarzane (kafelkowane), nie rozciagane -
     * faktura drewna czy kamienia zostaje ostra (piksele x WST_PANEL_SCALE).
     */
    private static final class Tiled9 {
        final com.badlogic.gdx.graphics.Texture t;
        final int cx, cy;

        Tiled9(com.badlogic.gdx.graphics.Texture t, int cx, int cy) {
            this.t = t;
            this.cx = cx;
            this.cy = cy;
        }

        void draw(SpriteBatch b, float x, float y, float w, float h) {
            float s = WST_PANEL_SCALE;
            int tw = t.getWidth(), th = t.getHeight();
            float bx = Math.min(cx * s, w / 2f), by = Math.min(cy * s, h / 2f);
            int mw = tw - 2 * cx, mh = th - 2 * cy;
            // kolumny: lewa krawedz, srodek (kafelki), prawa; wiersze tak samo (y w teksturze od gory)
            for (int row = 0; row < 3; row++) {
                float ry = row == 0 ? y + h - by : row == 1 ? y + by : y;
                float rh = row == 1 ? h - 2 * by : by;
                int sy = row == 0 ? 0 : row == 1 ? cy : th - cy, sh = row == 1 ? mh : cy;
                for (int col = 0; col < 3; col++) {
                    float rx = col == 0 ? x : col == 1 ? x + bx : x + w - bx;
                    float rw = col == 1 ? w - 2 * bx : bx;
                    int sx = col == 0 ? 0 : col == 1 ? cx : tw - cx, sw = col == 1 ? mw : cx;
                    if (rw <= 0 || rh <= 0) continue;
                    float stepX = col == 1 ? sw * s : rw, stepY = row == 1 ? sh * s : rh;
                    for (float yy = 0; yy < rh - 0.01f; yy += stepY) {
                        float dh = Math.min(stepY, rh - yy);
                        int srcH = row == 1 ? Math.max(1, Math.round(dh / s)) : sh;
                        for (float xx = 0; xx < rw - 0.01f; xx += stepX) {
                            float dw = Math.min(stepX, rw - xx);
                            int srcW = col == 1 ? Math.max(1, Math.round(dw / s)) : sw;
                            // kafelek przyciety od gory: zachowuje gorny brzeg wzoru
                            b.draw(t, rx + xx, ry + rh - yy - dh, dw, dh, sx, sy, srcW, srcH, false, false);
                        }
                    }
                }
            }
        }
    }

    /**
     * Okno z panelem WsT w danym stylu (pasek tytulu wysokosci titleH u gory, 0 = bez paska); false, gdy brak
     * grafik - wtedy wolajacy rysuje stara skorke OTClient.
     */
    private boolean wstPanel(ShapeRenderer shapes, String style, float fx, float fy, float fw, float fh, float titleH) {
        Tiled9[] p = wstPanels.get(style);
        if (p == null || uiBatch == null) return false;
        shapes.end();
        uiBatch.begin();
        drawBarOverlays(uiBatch);
        uiBatch.setColor(1, 1, 1, 0.97f);
        p[0].draw(uiBatch, fx, fy, fw, fh);
        if (titleH > 0 && p[1] != null) p[1].draw(uiBatch, fx + 3, fy + fh - titleH - 2, fw - 6, titleH);
        uiBatch.setColor(Color.WHITE);
        uiBatch.end();
        resumeShapes(shapes);
        return true;
    }

    /** Styl okna menu wg zawartosci: handel zloty, rozmowa drewniana, osiagniecia krolewskie, ksiega magiczna. */
    private String menuStyle() {
        if (trade != null || marketView != null) return "gold";
        if (npcMode) return "wood";
        if (achievementsView) return "royal";
        if (pickSlot >= 0 || playerMenu != null || optionsMenu || rankingView != null || friendsView != null) return "stone";
        return "arcane";
    }

    private static TextureRegion pvpIcon(String name) {
        if (Gdx.files == null) return null;
        com.badlogic.gdx.files.FileHandle f = Gdx.files.internal("ui/pvp/" + name + ".png");
        if (!f.exists()) f = Gdx.files.internal("assets/ui/pvp/" + name + ".png");
        if (!f.exists()) return null;
        return new TextureRegion(new com.badlogic.gdx.graphics.Texture(f)); // pixel art: domyslnie Nearest
    }

    /** Ikona czaszki dla nazwy z protokolu (WHITE, YELLOW, ORANGE, RED, BLACK) albo null (brak czaszki lub grafiki). */
    public TextureRegion skullIcon(String skull) {
        if (skull == null) return null;
        switch (skull) {
            case "RED": return skullRed;
            case "WHITE": return skullWhite;
            case "YELLOW": return skullYellow;
            case "ORANGE": return skullOrange;
            case "BLACK": return skullBlack;
            default: return null;
        }
    }

    /** Kolor imienia gracza z dana czaszka (null = bez czaszki - kolor domyslny). */
    public static Color skullColor(String skull) {
        if (skull == null) return null;
        switch (skull) {
            case "RED": return new Color(1f, 0.4f, 0.35f, 1f);
            case "BLACK": return new Color(0.62f, 0.6f, 0.66f, 1f);
            case "ORANGE": return new Color(1f, 0.62f, 0.25f, 1f);
            case "YELLOW": return new Color(1f, 0.9f, 0.35f, 1f);
            case "WHITE": return new Color(1f, 1f, 1f, 1f);
            default: return null;
        }
    }

    /** Polska nazwa czaszki ("Czerwona czaszka"). */
    public static String skullName(pl.fawizja.pixel.pvp.PvpRules.Skull skull) {
        switch (skull) {
            case RED: return "Czerwona czaszka";
            case BLACK: return "Czarna czaszka";
            case ORANGE: return "Pomarańczowa czaszka";
            case YELLOW: return "Żółta czaszka";
            case WHITE: return "Biała czaszka";
            default: return "Brak czaszki";
        }
    }

    public TextureRegion whiteRegion() { return art.white(); }

    private void loadUiImages() {
        try {
            skullWhite = pvpIcon("skull_white");
            skullRed = pvpIcon("skull_red");
            skullYellow = pvpIcon("skull_yellow");
            skullOrange = pvpIcon("skull_orange");
            skullBlack = pvpIcon("skull_black");
            pvpOffIcon = pvpIcon("pvp_off");
            pvpOnIcon = pvpIcon("pvp_on");
        } catch (RuntimeException e) {
            Gdx.app.error("Hud", "Brak ikon czaszek: " + e.getMessage());
        }
        loadWstPanels();
        try {
            com.badlogic.gdx.graphics.Texture win = uiTexture("window.png");
            if (win != null) windowPatch = new com.badlogic.gdx.graphics.g2d.NinePatch(win, 6, 6, 27, 6);
            com.badlogic.gdx.graphics.Texture btn = uiTexture("button.png");
            if (btn != null) {
                buttonPatch = new com.badlogic.gdx.graphics.g2d.NinePatch(new TextureRegion(btn, 0, 0, 22, 23), 3, 3, 3, 3);
                buttonDownPatch = new com.badlogic.gdx.graphics.g2d.NinePatch(new TextureRegion(btn, 0, 46, 22, 23), 3, 3, 3, 3);
            }
            com.badlogic.gdx.graphics.Texture pb = uiTexture("progressbar.png");
            if (pb != null) barPatch = new com.badlogic.gdx.graphics.g2d.NinePatch(pb, 1, 1, 1, 1);
            com.badlogic.gdx.graphics.Texture mini = uiTexture("miniwindow.png");
            if (mini != null) miniPatch = new com.badlogic.gdx.graphics.g2d.NinePatch(mini, 4, 4, 23, 4);
            com.badlogic.gdx.graphics.Texture pm = uiTexture("panel_map.png");
            if (pm != null) panelPatch = new com.badlogic.gdx.graphics.g2d.NinePatch(pm, 2, 2, 2, 2);
            com.badlogic.gdx.graphics.Texture slot = uiTexture("item.png");
            if (slot != null) slotImg = new TextureRegion(slot);
            wstSlot = wst("slot");
            if (wstSlot != null) {
                slotImg = wstSlot;
                wstEmpty = wst("slot_empty");
                wstSelected = wst("slot_selected");
                wstPressed = wst("slot_pressed");
                String[] tiers = {"common", "uncommon", "rare", "epic", "legendary"};
                for (int i = 0; i < tiers.length; i++) wstRarity[i] = wst("slot_" + tiers[i]);
                infoIcon = wst("icon_info");
            }
            com.badlogic.gdx.graphics.Texture mb = uiTexture("miniwindow_buttons.png");
            if (mb != null) {
                closeImg = new TextureRegion(mb, 28, 0, 14, 14);
                minimizeImg = new TextureRegion(mb, 0, 0, 14, 14);
            }
        } catch (RuntimeException e) {
            Gdx.app.error("Hud", "Brak grafik interfejsu, rysuje ksztaltami: " + e.getMessage());
        }
    }

    public Hud(TileArt art, BitmapFont font) {
        this.art = art;
        this.font = font;
        loadUiImages();
        buttons.add(use);
        buttons.add(run);
        buttons.add(torch);
        buttons.add(bag);
        buttons.add(pick);
        buttons.add(diag);
        buttons.add(target);
        buttons.add(defensive);
        buttons.add(quest);
        buttons.add(logout);
        buttons.add(fish);
        buttons.add(talk);
        buttons.add(mount);
        buttons.add(pvp);
        buttons.add(store);
        for (int i = 0; i < spellButtons.length; i++) {
            spellButtons[i] = new HudButton("spell:" + i, "");
            buttons.add(spellButtons[i]);
        }
    }

    /** Przycisk czatu (tylko w grze online). */
    public void setChatEnabled(boolean enabled) { chatEnabled = enabled; }

    public void resize(int width, int height) {
        float density = Gdx.graphics.getDensity();
        String forced = System.getProperty("pixel.uiScale"); // podglad ukladu telefonu na komputerze
        if (forced != null) density = Float.parseFloat(forced);
        uiScale = Math.max(1f, Math.min(3.5f, density));
        viewport.setUnitsPerPixel(1f / uiScale);
        viewport.update(width, height, true);
        w = viewport.getWorldWidth();
        h = viewport.getWorldHeight();
        // uklad jak w kliencie Tibii (2026-10-28): lewa kolumna (minimapa, VIP, umiejetnosci), prawa (ekwipunek
        // z przyciskami obok, walka, akcje, pasek czarow), u gory paski HP/MP, widok gry 15x11, pod nim czat
        if (mobile) layoutMobile();
        else layoutSide();
        layoutRest();
    }

    /** Ktore przyciski akcji byly widoczne przy ostatnim ukladzie (bity; zmiana = nowy uklad panelu). */
    private int sideButtonsLaidOut = -1;

    private int visibleActionMask() {
        int m = 0;
        for (int i = 0; i < actionButtons.length; i++) if (actionButtons[i].visible) m |= 1 << i;
        return m;
    }

    /** Przyciski akcji nad paskiem czarow (dawniej obok ekwipunku): stale ATAK i UZYJ, reszta gdy ma sens. */
    private final HudButton[] actionButtons = {target, use, bag, talk, pick, fish, mount};
    /**
     * Dwie kolumny obok ekwipunku: 1. podazaj (auto chodzenie za celem), postawa obronna (tarcza), dziennik zadan,
     * pochodnia; 2. wylogowanie, tryb PvP (czaszka), ustawienia i diagnostyka, sklep.
     */
    private final HudButton[] toggleButtons = {run, defensive, quest, torch, logout, pvp, diag, store};

    private List<HudButton> visibleActions() {
        List<HudButton> out = new ArrayList<>();
        for (HudButton b : actionButtons) if (b.visible) out.add(b);
        return out;
    }

    /** Czy okno umiejetnosci jest rozwiniete (wybor gracza; domyslnie tak, gdy miesci sie w lewej kolumnie). */
    private boolean skillsExpanded() { return lay.skillsShown; }

    /** Naglowek okna umiejetnosci: zwija i rozwija (tez -Dpixel.button=hud:skills). */
    public void toggleSkills() {
        skillsOpen = !skillsExpanded();
        layoutSide();
    }

    /** Wiersze tresci listy VIP: znajomi albo zawiniety napis "brak znajomych" (do ukladu lewej kolumny). */
    private int vipContentRows() {
        int n = vipView == null ? 0 : vipView.rows().size();
        if (n > 0) return n;
        float lw = (h < 420 ? 150 : 200) - 2 * HudLayout.PAD - 14;
        font.getData().setScale(conScale());
        layout.setText(font, VIP_EMPTY, Color.WHITE, Math.max(40, lw), com.badlogic.gdx.utils.Align.left, true);
        int lines = 1 + Math.max(0, Math.round((layout.height - font.getCapHeight()) / font.getLineHeight()));
        font.getData().setScale(1f);
        return Math.min(3, lines);
    }

    private static final String VIP_EMPTY = "Brak znajomych. Dotknij +, aby dodać.";

    public void toggleVip() {
        vipOpen = !vipOpen;
        layoutSide();
    }

    /** Znajomi do listy VIP (null = brak danych). */
    public void setVip(pl.fawizja.pixel.friends.FriendsView view) {
        int before = vipView == null ? 0 : vipView.rows().size();
        vipView = view;
        if ((view == null ? 0 : view.rows().size()) != before && w > 0) layoutSide(); // VIP zajmuje tyle, ile potrzebuje
    }

    /** Inni gracze w poblizu do listy walki (dotkniecie otwiera okienko gracza). */
    public void setBattlePlayers(List<BattlePlayer> players) { battlePlayers = players == null ? List.of() : players; }

    /** Piksele ekranu na pole widoku gry (cala liczba z ukladu). */
    public float tilePixels() {
        // telefon: widok gry pokrywa caly ekran (15 kolumn na szerokosc albo 11 wierszy na wysokosc), cala liczba pikseli
        if (mobile) return (float) Math.ceil(Math.max(w * uiScale / HudLayout.VIEW_TILES_W, h * uiScale / HudLayout.VIEW_TILES_H) - 1e-3f);
        return lay.tilePixels;
    }

    /** Cala bryla HUD-u z {@link HudLayout}: panele, przyciski, pola paska, konsola i joystick. */
    private void layoutSide() {
        List<HudButton> acts = visibleActions();
        sideButtonsLaidOut = visibleActionMask();
        lay.compute(w, h, uiScale, acts.size(), vipOpen, vipContentRows(), skillsOpen, 2 + Skill.values().length);
        topH = lay.topBarHeight();
        gx = lay.view.x;
        gy = lay.view.y;
        gw = lay.view.w;
        gh = lay.view.h;
        panelX = lay.right.x;
        HudLayout.Rect r = new HudLayout.Rect();
        for (int i = 0; i < spellButtons.length; i++) {
            lay.hotbarCell(i, r);
            place(spellButtons[i], r.cx(), r.cy(), r.w);
        }
        abTop = lay.hotbar.top() + 4;
        mmX = lay.minimap.x;
        mmY = lay.minimap.y;
        mmSize = lay.minimap.w;
        for (int i = 0; i < toggleButtons.length; i++) {
            int slot = lay.toggleSlot[i];
            // waski telefon: wyjscie schowane w menu "Opcje"
            toggleButtons[i].visible = slot >= 0;
            if (slot < 0) continue;
            HudLayout.Rect t = lay.toggles[slot];
            place(toggleButtons[i], t.cx(), t.cy(), t.w);
        }
        // akcje: obok ekwipunku (telefon), reszta w rzedach nad paskiem czarow
        int inRow = 0;
        for (int i = 0; i < acts.size(); i++) {
            int slot = i < lay.actionSlot.length ? lay.actionSlot[i] : -1;
            if (slot >= 0) {
                HudLayout.Rect t = lay.toggles[slot];
                place(acts.get(i), t.cx(), t.cy(), t.w);
            } else {
                lay.actionCell(inRow++, lay.rowActions, r);
                place(acts.get(i), r.cx(), r.cy(), r.w);
            }
        }
        epTibia = true;
        epX = lay.equipment.x;
        epY = lay.equipment.y;
        epW = lay.equipment.w;
        epH = lay.equipment.h;
        epCell = lay.eqCell;
        blX = lay.battle.x;
        blY = lay.battle.y;
        blW = lay.battle.w;
        blH = lay.battle.h;
        pskX = lay.skills.x;
        pskY = lay.skills.y;
        pskW = lay.skills.w;
        pskH = lay.skills.h;
        conTabH = lay.tabH;
        conX = lay.console.x;
        conY = lay.console.y;
        conW = lay.console.w;
        conH = lay.console.h;
        // joystick: maly i prawie przezroczysty w lewym dolnym rogu widoku gry (na telefonie zwykle pod kciukiem)
        float jr = Math.min(34, Math.min(gw, gh) * 0.09f) * (settings != null ? settings.joystickScale() : 1f);
        joystick.layout(gx + jr * 1.5f, gy + jr * 1.5f, jr);
    }

    /** Pole przycisku panelu: kwadrat o boku s ze srodkiem w (cx, cy). */
    private static void place(HudButton b, float cx, float cy, float s) {
        b.x = cx;
        b.y = cy;
        b.r = s / 2f;
        b.hw = s / 2f;
    }

    /** Okna, plecak i paski - reszta ukladu po panelu i konsoli. */
    private void layoutRest() {
        menuGeometry();
        slotSize = Math.min(44, Math.min(w, h) / 9f);
        bagX = w / 2f - slotSize * 2.5f;
        bagY = h / 2f - slotSize * 2f;
        float bbr = slotSize * 0.55f;
        bagEquip.x = bagX + slotSize * 1.0f; bagEquip.y = bagY - bbr * 1.4f; bagEquip.r = bbr;
        bagDrop.x = bagX + slotSize * 4.0f; bagDrop.y = bagY - bbr * 1.4f; bagDrop.r = bbr;
        bagUse.x = bagX + slotSize * 2.5f; bagUse.y = bagY - bbr * 1.4f; bagUse.r = bbr;
        bagSort.x = bagUse.x; bagSort.y = bagUse.y; bagSort.r = bbr;
        bagDetach.x = bagDrop.x; bagDetach.y = bagDrop.y; bagDetach.r = bbr;
        bagKeep.x = bagUse.x; bagKeep.y = bagY - bbr * 3.0f; bagKeep.r = bbr;
        bagClose.x = bagX + slotSize * 5f + 4; bagClose.y = bagY + slotSize * 4f + 18; bagClose.r = 14;
    }

    /**
     * Polozenie okna: zwykle okna (ksiega, NPC, rynek...) nad widokiem gry, gdy sie miesci (kolumny zostaja
     * odsloniete); okienko gracza i potwierdzenie wylogowania - male, na srodku widoku gry.
     */
    private void menuGeometry() {
        if (mobile) {
            mobileMenuGeometry();
            return;
        }
        if (playerMenu != null || logoutConfirm || optionsMenu) {
            // ekran dotykowy: wiersze co najmniej 36 dp; gdy nie mieszcza sie jeden pod drugim - dwie kolumny
            rowH = uiScale >= 1.5f || lay.compact ? 39 : 24;
            int rows = logoutConfirm ? 2 : optionsMenu ? 2 + (lay.folded[HudLayout.TORCH_TOGGLE] ? 1 : 0)
                    + (lay.folded[HudLayout.STORE_TOGGLE] ? 1 : 0) : playerRowsCount;
            popupCols = 1;
            menuW = Math.min(240, Math.max(160, gw - 16));
            menuH = 24 + 20 + rows * rowH + 8;
            if (menuH > Math.min(h - 8, gh + 4) && rows > 1) {
                // dwie kolumny w szerokosci widoku gry: kolumny HUD-u zostaja odsloniete
                popupCols = 2;
                menuW = Math.max(menuW, Math.min(gw - 8, 400));
                menuH = 24 + 20 + ((rows + 1) / 2) * rowH + 8;
            }
            menuX = Math.max(4, Math.min(w - menuW - 4, gx + (gw - menuW) / 2f));
            menuY = Math.max(4, Math.min(h - menuH - 4, gy + (gh - menuH) / 2f));
        } else {
            menuW = Math.min(w - 40, 620);
            menuH = Math.min(h - 40, 400);
            menuX = gw >= menuW ? gx + (gw - menuW) / 2f : (w - menuW) / 2f;
            menuY = (h - menuH) / 2f;
            rowH = Math.max(26, Math.min(36, (menuH - 50) / 10f));
        }
        // krzyzyk okna: na ekranie dotykowym wiekszy (pole dotyku ok. 36 dp)
        menuClose.r = uiScale >= 1.5f || lay.compact ? 16 : 12;
        menuClose.x = menuX + menuW - 4 - menuClose.r; menuClose.y = menuY + menuH - 2 - menuClose.r;
    }

    /** Wiersze okienka gracza w ostatnim ukladzie (wysokosc okienka). */
    private int playerRowsCount = 8;

    /** Widok gry w ramce (jednostki interfejsu), lewa krawedz prawego panelu, wysokosc gornego paska. */
    private float gx, gy, gw, gh, panelX, topH;
    /** Okno umiejetnosci w panelu (pskH = 0: brak miejsca). */
    private float pskX, pskY, pskW, pskH;
    private static final float PSK_ROW = 15;

    private boolean panelSkillsVisible() { return pskH > 0 && !charOpen && !windowOverLeft(); }

    private boolean vipVisible() { return lay.vip.h > 0 && !windowOverLeft(); }

    /** Wiersze okna umiejetnosci: poziom, doswiadczenie do poziomu, potem skille (wartosc, postep 0..1). */
    private String[][] skillRows(PlayerCharacter pc) {
        String[][] rows = new String[2 + Skill.values().length][];
        rows[0] = new String[] {"Poziom", String.valueOf(pc.level())};
        rows[1] = new String[] {"Doświadczenie", (int) (pc.levelProgress() * 100) + "%"};
        int i = 2;
        for (Skill sk : Skill.values()) rows[i++] = new String[] {sk.label(), String.valueOf(pc.skills().level(sk))};
        return rows;
    }

    /** Trojkat zwijania przy naglowku: w dol (rozwiniete) albo w prawo (zwiniete). */
    private static void foldArrow(ShapeRenderer s, float x, float cy, boolean open) {
        s.setColor(0.85f, 0.8f, 0.65f, 1f);
        if (open) s.triangle(x, cy + 3, x + 8, cy + 3, x + 4, cy - 3);
        else s.triangle(x + 1, cy + 4, x + 1, cy - 4, x + 7, cy);
    }

    /** Napis naglowka malego okna: na telefonie mniejszy (naglowek 20 dp), nad srodkiem paska tytulu. */
    private void headerText(SpriteBatch batch, String title, float x, float top) {
        float sc = lay.compact ? 0.75f : 1f;
        font.getData().setScale(sc);
        layout.setText(font, title);
        font.draw(batch, title, x, top - (lay.header - layout.height) / 2f - 1);
        font.getData().setScale(1f);
    }

    /** Umiejetnosci w lewej kolumnie (jak okno Skills w Tibii): naglowek zwija okno, wiersze z paskiem postepu. */
    private void drawPanelSkills(SpriteBatch batch, PlayerCharacter pc) {
        if (!panelSkillsVisible()) return;
        headerText(batch, "Umiejętności", pskX + 18, pskY + pskH);
        if (!skillsExpanded()) return;
        float row = lay.skillRow;
        font.getData().setScale(conScale());
        float yy = pskY + pskH - lay.header - 3;
        for (String[] r : skillRows(pc)) {
            if (yy - row + 2 < pskY + 2) break;
            font.setColor(0.85f, 0.85f, 0.8f, 1f);
            text(batch, r[0], pskX + 8, yy);
            font.setColor(Color.WHITE);
            layout.setText(font, r[1]);
            text(batch, r[1], pskX + pskW - 8 - layout.width, yy);
            yy -= row;
        }
        font.getData().setScale(1f);
    }

    /** Okno umiejetnosci (w przebiegu ksztaltow): ramka, strzalka i paski postepu pod wierszami. */
    private void drawPanelSkillBars(ShapeRenderer shapes, PlayerCharacter pc) {
        if (!panelSkillsVisible()) return;
        miniWindow(shapes, "classic", pskX, pskY, pskW, pskH, lay.header - 2);
        foldArrow(shapes, pskX + 6, pskY + pskH - lay.header / 2f, skillsExpanded());
        if (!skillsExpanded()) return;
        float row = lay.skillRow;
        float yy = pskY + pskH - lay.header - 3;
        for (int i = 0; i < 2 + Skill.values().length; i++) {
            if (yy - row + 2 < pskY + 2) break;
            double prog = i == 0 ? -1 : i == 1 ? pc.levelProgress()
                    : (double) pc.skills().tries(Skill.values()[i - 2]) / Math.max(1, Skills.triesForNext(pc.skills().level(Skill.values()[i - 2])));
            if (prog >= 0) bar(shapes, pskX + 8, yy - row + 1, pskW - 16, 2, (float) prog,
                    i == 1 ? new Color(0.95f, 0.8f, 0.2f, 1) : new Color(0.3f, 0.75f, 0.4f, 1));
            yy -= row;
        }
    }

    /** "+" na liscie VIP: widoczny kwadrat i (wieksze) pole dotyku co najmniej 36 x 32 dp w prawym gornym rogu. */
    private float[] vipPlus() {
        HudLayout.Rect v = lay.vip;
        float sz = lay.compact ? 16 : 14;
        float cx = v.right() - 4 - sz / 2f - 2, cy = v.top() - lay.header / 2f;
        return new float[] {cx, cy, sz};
    }

    private boolean vipPlusHit(float px, float py) {
        HudLayout.Rect v = lay.vip;
        float[] p = vipPlus();
        return px >= v.right() - 40 && px <= v.right() + HudLayout.PAD && py >= p[1] - 18 && py <= v.top() + HudLayout.GAP + 1;
    }

    /** Lista VIP (znajomi) w lewej kolumnie: ramka, strzalka zwijania, "+" i kropki stanu (w przebiegu ksztaltow). */
    private void drawVipShapes(ShapeRenderer shapes) {
        if (!vipVisible()) return;
        HudLayout.Rect v = lay.vip;
        miniWindow(shapes, "classic", v.x, v.y, v.w, v.h, lay.header - 2);
        foldArrow(shapes, v.x + 6, v.top() - lay.header / 2f, vipOpen);
        // "+" dodaje znajomego (czat: /znajomi dodaj Imie)
        float[] p = vipPlus();
        float px = p[0], py = p[1], hs = p[2] / 2f;
        shapes.setColor(0.25f, 0.5f, 0.3f, 1f);
        shapes.rect(px - hs, py - hs, 2 * hs, 2 * hs);
        shapes.setColor(1f, 1f, 1f, 0.9f);
        shapes.rect(px - hs + 2, py - 1, 2 * hs - 4, 2);
        shapes.rect(px - 1, py - hs + 2, 2, 2 * hs - 4);
        if (!vipOpen || vipView == null) return;
        float row = lay.vipRow;
        float yy = v.top() - lay.header - 3;
        for (pl.fawizja.pixel.friends.FriendsView.Row r : vipRows()) {
            if (yy - row < v.y + 2) break;
            shapes.setColor(r.online() ? new Color(0.25f, 0.85f, 0.3f, 1f) : new Color(0.75f, 0.2f, 0.18f, 1f));
            shapes.circle(v.x + 11, yy - row / 2f + 1, 3f, 10);
            yy -= row;
        }
    }

    /** Znajomi: najpierw w grze, potem reszta (alfabetycznie w grupie). */
    private List<pl.fawizja.pixel.friends.FriendsView.Row> vipRows() {
        List<pl.fawizja.pixel.friends.FriendsView.Row> rows = new ArrayList<>(vipView == null ? List.of() : vipView.rows());
        rows.sort(java.util.Comparator.comparing((pl.fawizja.pixel.friends.FriendsView.Row r) -> !r.online())
                .thenComparing(r -> r.name().toLowerCase(java.util.Locale.ROOT)));
        return rows;
    }

    /** Imiona na liscie VIP: zielone w grze, czerwone poza gra (jak w Tibii), poziom po prawej. */
    private void drawVip(SpriteBatch batch) {
        if (!vipVisible()) return;
        HudLayout.Rect v = lay.vip;
        int online = vipView == null ? 0 : vipView.online(), all = vipView == null ? 0 : vipView.rows().size();
        String title = "VIP " + online + "/" + all;
        headerText(batch, title, v.x + 18, v.top());
        if (!vipOpen) return;
        float sc = conScale(), row = lay.vipRow;
        font.getData().setScale(sc);
        float yy = v.top() - lay.header - 3;
        if (all == 0) {
            font.setColor(0.7f, 0.7f, 0.65f, 1f);
            font.draw(batch, VIP_EMPTY, v.x + 8, yy, v.w - 14, com.badlogic.gdx.utils.Align.left, true);
        }
        for (pl.fawizja.pixel.friends.FriendsView.Row r : vipRows()) {
            if (yy - row < v.y + 2) break;
            String lvl = r.level() > 0 ? String.valueOf(r.level()) : "";
            layout.setText(font, lvl);
            float lw = layout.width;
            font.setColor(r.online() ? new Color(0.45f, 1f, 0.45f, 1f) : new Color(1f, 0.45f, 0.4f, 1f));
            float room = v.w - 20 - lw - 10;
            text(batch, clip(r.name(), room), v.x + 18, yy - 2);
            font.setColor(0.8f, 0.8f, 0.75f, 1f);
            if (!lvl.isEmpty()) text(batch, lvl, v.right() - 7 - lw, yy - 2);
            yy -= row;
        }
        font.setColor(Color.WHITE);
        font.getData().setScale(1f);
    }

    /** Dotkniecia w lewej kolumnie: naglowki zwijaja okna, "+" dodaje znajomego, wiersz VIP otwiera okienko gracza. */
    private boolean leftColumnTouch(float px, float py, Actions actions) {
        if (windowOverLeft()) return false;
        HudLayout.Rect v = lay.vip;
        if (vipVisible() && vipPlusHit(px, py)) {
            actions.onButton("vip:add");
            return true;
        }
        if (v.contains(px, py)) {
            if (py >= v.top() - lay.header) {
                toggleVip();
                return true;
            }
            if (!vipOpen) return true;
            int i = (int) ((v.top() - lay.header - 3 - py) / lay.vipRow);
            List<pl.fawizja.pixel.friends.FriendsView.Row> rows = vipRows();
            if (i >= 0 && i < rows.size()) openPlayerMenu(-1, rows.get(i).name(), false, false);
            return true;
        }
        if (!charOpen && lay.skills.contains(px, py)) {
            if (py >= lay.skills.top() - lay.header) toggleSkills();
            return true;
        }
        return false;
    }

    /** Lista walki w panelu i gorna krawedz paska akcji. */
    private float blX, blY, blW, blH, abTop;

    /** Widok gry w pikselach ekranu {x, y, szerokosc, wysokosc} - tam GameScreen rysuje swiat. */
    public float[] gameView() { return new float[] {gx * uiScale, gy * uiScale, gw * uiScale, gh * uiScale}; }

    private boolean inGameView(float px, float py) {
        return px >= gx && px <= gx + gw && py >= gy && py <= gy + gh;
    }

    /** Kwadratowe pola jak w Tibii (pasek akcji i przyciski obok ekwipunku). */
    private HudButton[] slotButtons() {
        HudButton[] out = java.util.Arrays.copyOf(spellButtons, spellButtons.length + toggleButtons.length + actionButtons.length);
        System.arraycopy(toggleButtons, 0, out, spellButtons.length, toggleButtons.length);
        System.arraycopy(actionButtons, 0, out, spellButtons.length + toggleButtons.length, actionButtons.length);
        return out;
    }

    private static void place(HudButton b, float cx, float cy, float dist, float deg, float r) {
        double a = Math.toRadians(deg);
        b.x = cx + (float) Math.cos(a) * dist;
        b.y = cy + (float) Math.sin(a) * dist;
        b.r = r;
    }

    public VirtualJoystick joystick() { return joystick; }

    /**
     * Joystick pod kciukiem (etap 17): przeciagniecie zaczete na lewej czesci widoku gry tworzy joystick w miejscu
     * dotkniecia. Prawa czesc zostaje dla dotkniec pol (cel, marsz, rozmowa).
     */
    public boolean floatJoystick(int sx, int sy, int pointer) {
        if (settings != null && !settings.floatingJoystick()) return false;
        Vector2 p = toUi(sx, sy);
        if (!inGameView(p.x, p.y) || p.x > gx + gw * 0.55f) return false;
        return joystick.floatAt(p.x, p.y, pointer);
    }
    public boolean bagOpen() { return bagOpen || mobile && mWin == MobileWin.BAG; }
    public void toggleBag() {
        if (mobile) {
            openMobileWin(MobileWin.BAG);
            return;
        }
        bagOpen = !bagOpen; selectedSlot = -1; dragSlot = -1; dragging = false; if (bagOpen) menuOpen = false;
    }

    /** Pokazuje strone plecaka (desktop: -Dpixel.bagPage). */
    public void showBagPage(int page) { bagPage = Math.max(0, page); }

    private static final int PAGE_SLOTS = pl.fawizja.pixel.inventory.Container.BACKPACK_SLOTS;

    /** Zakladka strony plecaka pod punktem albo -1 (pionowe zakladki z prawej strony pol, gdy jest wiecej niz 1 plecak). */
    private int bagTabAt(float x, float y) {
        if (bagPages < 2 || x < bagTabX() - 4 || x > bagTabX() + TAB + 4) return -1;
        for (int k = 0; k < bagPages; k++) {
            float ty = bagTabY(k);
            if (y >= ty - 4 && y <= ty + TAB + 4) return k;
        }
        return -1;
    }

    private static final float TAB = 26;
    private float bagTabX() { return bagX + slotSize * 5 + 12; }
    private float bagTabY(int k) { return bagY + slotSize * (3 - k) + (slotSize - TAB) / 2f; }

    private boolean inEquipPanel(float x, float y) {
        return epW > 0 && x >= epX && x <= epX + epW && y >= epY && y <= epY + epH;
    }
    /** Klawisz Wstecz / Esc: zamyka okno na wierzchu; false, gdy nic nie bylo otwarte (handel zamyka sie przyciskiem). */
    public boolean closeTop(GameSession session) {
        if (houseWin != null) {
            if (houseWin.mode == HouseWindow.Mode.INVITE || houseWin.mode == HouseWindow.Mode.CONFIRM_LEAVE) houseWin.press("house:back");
            else houseWin = null;
            return true;
        }
        if (trade != null) return true;
        if (bigMapOpen) {
            toggleMap();
            return true;
        }
        if (menuOpen) {
            if (npcMode) {
                toggleNpc(session);
            } else {
                logoutConfirm = false;
                optionsMenu = false;
                playerMenu = null;
                rankingView = null;
                friendsView = null;
                marketView = null;
                achievementsView = false;
                journalMode = false;
                assignSlot = -1;
                menuOpen = false;
            }
            return true;
        }
        if (bagOpen) {
            toggleBag();
            return true;
        }
        if (charOpen) {
            charOpen = false;
            return true;
        }
        if (mobile && mWin != null) {
            mWin = null;
            selectedSlot = -1;
            return true;
        }
        if (mobile && mChat) {
            mChat = false;
            return true;
        }
        return false;
    }

    public void toggleBook() {
        logoutConfirm = false;
        optionsMenu = false;
        if (trade != null) return; // okno handlu ma pierwszenstwo
        spellPage = -1;
        playerMenu = null;
        if (rankingView != null || friendsView != null || marketView != null || achievementsView) {
            rankingView = null;
            friendsView = null;
            marketView = null;
            achievementsView = false;
            menuOpen = false;
        }
        menuOpen = !(menuOpen && !npcMode);
        npcMode = false;
        assignSlot = -1;
        if (menuOpen) bagOpen = false;
    }

    /** Ksiega czarow w trybie przypisywania czaru do przycisku akcji nr slot (0-3). */
    public void openAssign(int slot) {
        if (trade != null) return;
        if (!book.active) toggleBook();
        if (!menuOpen) return;
        assignSlot = slot;
    }

    /** Otwiera lub zamyka rozmowe z NPC w zasiegu. */
    public void toggleNpc(GameSession session) {
        if (menuOpen && npcMode) {
            menuOpen = false;
            npcMode = false;
        } else {
            openNpc(session);
        }
    }

    /** Rozmowa z bankierem od razu w widoku depozytu (podglad GUI). */
    public void openDepot(GameSession session) {
        openNpc(session);
        depotView = npc != null && npc.role() == pl.fawizja.pixel.npc.NpcRole.BANKER;
    }

    /** Rozmowa z kupcem od razu w widoku kowala (podglad GUI). */
    public void openSmith(GameSession session) {
        openNpc(session);
        smithView = npc != null && npc.role() == pl.fawizja.pixel.npc.NpcRole.MERCHANT;
    }

    /** Podglad (zrzuty ekranu): strona listy kupca. */
    public void previewNpcPage(int page) { npcPage = page; }

    public void openNpc(GameSession session) {
        logoutConfirm = false;
        optionsMenu = false;
        pl.fawizja.pixel.npc.Npc n = session.npcNearby();
        if (n == null) return;
        playerMenu = null;
        if (n != npc) {
            npcPage = 0;
            depotView = false;
            smithView = false;
        }
        npc = n;
        if (pl.fawizja.pixel.npc.Npc.DEPOT_LOCKER.equals(n.key())) depotView = true;   // skrzynia: od razu depozyt
        npcMode = true;
        menuOpen = true;
        bagOpen = false;
    }

    public boolean npcOpen() { return menuOpen && npcMode; }

    /** Stan wymiany z serwera: otwiera okno handlu albo je zamyka (null). */
    public void setTrade(pl.fawizja.pixel.trade.TradeView view) {
        boolean wasOpen = trade != null;
        trade = view;
        if (view != null) {
            logoutConfirm = false;
            optionsMenu = false;
            menuOpen = true;
            npcMode = false;
            bagOpen = false;
        } else if (wasOpen) {
            menuOpen = false;
        }
    }

    public boolean tradeOpen() { return trade != null; }

    /** Menu gracza: atak, handel, druzyna, gildia - zamiast natychmiastowego ataku po dotknieciu. */
    public void openPlayerMenu(int id, String name, boolean attacking, boolean ally) {
        logoutConfirm = false;
        optionsMenu = false;
        if (trade != null) return;
        playerMenu = new PlayerMenu(id, name, attacking, ally);
        menuOpen = true;
        npcMode = false;
        bagOpen = false;
    }

    /** Ranking z serwera: otwiera okno (handel ma pierwszenstwo). */
    public void openRanking(pl.fawizja.pixel.ranking.RankingView view) {
        logoutConfirm = false;
        optionsMenu = false;
        if (view == null || trade != null) return;
        rankingView = view;
        friendsView = null;
        marketView = null;
        achievementsView = false;
        playerMenu = null;
        menuOpen = true;
        npcMode = false;
        bagOpen = false;
    }

    public boolean rankingOpen() { return menuOpen && rankingView != null; }

    /** Lista znajomych z serwera: otwiera okno (handel ma pierwszenstwo). */
    public void openFriends(pl.fawizja.pixel.friends.FriendsView view) {
        logoutConfirm = false;
        optionsMenu = false;
        if (view == null || trade != null) return;
        friendsView = view;
        rankingView = null;
        marketView = null;
        achievementsView = false;
        playerMenu = null;
        menuOpen = true;
        npcMode = false;
        bagOpen = false;
    }

    public boolean friendsOpen() { return menuOpen && friendsView != null; }

    /** Rynek z serwera: otwiera okno (handel ma pierwszenstwo). */
    public void openMarket(pl.fawizja.pixel.market.MarketView view) {
        logoutConfirm = false;
        optionsMenu = false;
        if (view == null || trade != null) return;
        marketView = view;
        rankingView = null;
        friendsView = null;
        achievementsView = false;
        playerMenu = null;
        menuOpen = true;
        npcMode = false;
        bagOpen = false;
    }

    public boolean marketOpen() { return menuOpen && marketView != null; }

    /** Nazwy potworow do bestiariusza (klucz z monsters.csv -> nazwa). */
    public void setMonsterNames(java.util.function.Function<String, String> names) { this.monsterNames = names; }
    /** Liczba gatunkow potworow w grze (naglowek bestiariusza; 0 = nieznana). */
    private int monsterSpecies;
    public void setMonsterSpecies(int n) { this.monsterSpecies = n; }

    /** Osiagniecia (bestiary = false) albo bestiariusz; ponowne wywolanie z tym samym widokiem zamyka okno. */
    public void openAchievements(boolean bestiary) {
        logoutConfirm = false;
        optionsMenu = false;
        if (trade != null) return;
        if (achievementsView && menuOpen && bestiaryMode == bestiary && !journalMode) {
            achievementsView = false;
            menuOpen = false;
            return;
        }
        achievementsView = true;
        journalMode = false;
        infoTitle = null;
        bestiaryMode = bestiary;
        rankingView = null;
        friendsView = null;
        marketView = null;
        playerMenu = null;
        menuOpen = true;
        npcMode = false;
        bagOpen = false;
    }

    public boolean achievementsOpen() { return menuOpen && achievementsView; }

    /** Dziennik zadan (klawisz Z, /zadania, wiersz w ksiedze); ponowne wywolanie zamyka. */
    public void openJournal() {
        if (trade != null) return;
        if (journalOpen()) {
            achievementsView = false;
            journalMode = false;
            menuOpen = false;
            return;
        }
        openAchievements(false);
        journalMode = true;
    }

    public boolean journalOpen() { return menuOpen && achievementsView && journalMode; }

    public void closeFriends() {
        if (friendsView != null) menuOpen = false;
        friendsView = null;
    }

    public void closePlayerMenu() {
        if (playerMenu != null) menuOpen = false;
        playerMenu = null;
    }

    public boolean playerMenuOpen() { return menuOpen && playerMenu != null; }
    public boolean bookOpen() { return menuOpen; }
    public void toggleCharacter() {
        if (mobile) openMobileWin(MobileWin.EQUIP);
        else charOpen = !charOpen;
    }
    public boolean characterOpen() { return charOpen || mobile && mWin == MobileWin.EQUIP; }

    /** Klucz czaru na pasku (0..3) albo null. */
    /** Akcja pola paska (do wyslania jak przycisk) albo null dla pustego pola. */
    public String barEntry(int i) { return i >= 0 && i < barEntries.length ? barEntries[i] : null; }

    /**
     * Automatyczny pasek: najlepsze leczenie, najlepszy atak na cel, atak obszarowy (lub drugi atak),
     * poswiata. Gracz nie musi niczego ustawiac na telefonie.
     */
    private long barUpdatedMs = Long.MIN_VALUE / 2;
    static final long BAR_REFRESH_MS = 150;

    public void updateBar(GameSession session) {
        List<SpellType> known = session.spells().knownSpells();
        SpellType heal = null, attack = null, attack2 = null, area = null, extra = null;
        for (SpellType sp : known) {
            switch (sp.kind()) {
                case HEAL_SELF: heal = sp; break;
                case ATTACK_TARGET: attack2 = attack; attack = sp; break;
                case ATTACK_AREA: area = sp; break;
                case LIGHT: if (extra == null || extra.kind() == SpellKind.LIGHT) extra = sp; break;
                case HASTE: extra = sp; break; // na 300+ przyspieszenie przydaje sie bardziej niz swiatlo
                default:
            }
        }
        SpellType[] bar = {heal, attack, area != null ? area : attack2, extra};
        String[] auto = new String[pl.fawizja.pixel.settings.SpellBar.SLOTS];
        for (int i = 0; i < bar.length; i++) auto[i] = bar[i] == null ? null : "cast:" + bar[i].key();
        auto[4] = "potion_hp";
        auto[5] = "potion_mp";
        // pierwsza runa z plecaka (bez pustej) trafia sama na wolne pole
        for (ItemStack st : session.character().backpack().contents()) {
            ItemType it = session.items().get(st.itemId());
            if (it.category() == pl.fawizja.pixel.item.ItemCategory.RUNE && session.spells().registry().find(it.key()) != null) {
                auto[6] = "item:" + it.key();
                break;
            }
        }
        if (mobile && auto[6] == null) {
            // telefon: piaty przycisk luku bez runy dostaje kolejny znany czar (najwyzszy, jeszcze nie na luku)
            // najpierw drugi atak, potem inne czary, na koncu leczenie
            java.util.List<String> used = java.util.Arrays.asList(auto);
            for (int pass = 0; pass < 3 && auto[6] == null; pass++) {
                for (int k = known.size() - 1; k >= 0 && auto[6] == null; k--) {
                    SpellType sp = known.get(k);
                    int kind = sp.kind().isOffensive() ? 0 : sp.kind().isHeal() ? 2 : 1;
                    if (kind == pass && sp.kind() != SpellKind.CONJURE && !used.contains("cast:" + sp.key())) auto[6] = "cast:" + sp.key();
                }
            }
        }
        java.util.Set<String> knownKeys = new java.util.HashSet<>();
        for (SpellType sp : known) knownKeys.add(sp.key());
        String[] resolved = customBar.resolve(auto, knownKeys);
        PlayerCharacter pc = session.character();
        for (int i = 0; i < spellButtons.length; i++) {
            String e = resolved[i];
            HudButton b = spellButtons[i];
            b.visible = true;
            b.icon = null;
            b.labelBottom = false;
            barEntries[i] = null;
            if (e != null && e.startsWith("cast:")) {
                SpellType sp = session.spells().registry().find(e.substring(5));
                if (sp != null) {
                    barEntries[i] = e;
                    b.label = shortName(sp.name());
                    b.labelBottom = true;
                }
            } else if (e != null) {
                String key = e.equals("potion_hp") ? session.quickPotion(false) : e.equals("potion_mp") ? session.quickPotion(true) : e.substring(5);
                if (session.items().containsKey(key)) {
                    barEntries[i] = e;
                    b.icon = itemIcon(session, key);
                    b.label = String.valueOf(pc.backpack().count(session.items().byKey(key).id()));
                }
            }
            // puste pole zostaje widoczne jako "+" - dotkniecie otwiera wybor akcji
            if (barEntries[i] == null) b.label = "+";
        }
        if (mobile) {
            // telefon: tylko pola pod przyciskami luku (5 czarow, 2 mikstury)
            for (HudButton b : spellButtons) b.visible = false;
            for (int s : MobileHudLayout.SPELL_SLOTS) spellButtons[s].visible = true;
            for (int s : MobileHudLayout.POTION_SLOTS) spellButtons[s].visible = true;
        }
    }

    public void setSpellBar(pl.fawizja.pixel.settings.SpellBar bar) { customBar = bar; }
    public pl.fawizja.pixel.settings.SpellBar spellBar() { return customBar; }

    private static String shortName(String name) {
        String first = name.split(" ")[0];
        return first.length() > 7 ? first.substring(0, 6) + "." : first;
    }

    private void rebuildMenu(GameSession session) {
        menuRows.clear();
        menuGeometry();
        if (trade != null) {
            rebuildTradeMenu(session);
            return;
        }
        if (optionsMenu) {
            // waski telefon: "Opcje" zbiera ustawienia i wyjscie z gry
            List<MenuRow> rows = new ArrayList<>();
            rows.add(new MenuRow("opt:diag", "Ustawienia", "", true));
            if (lay.folded[HudLayout.STORE_TOGGLE]) rows.add(new MenuRow("opt:store", "Sklep", "premium, promocja, błogosławieństwo", true));
            if (lay.folded[HudLayout.TORCH_TOGGLE]) {
                rows.add(new MenuRow("opt:torch", session.character().isTorchLit() ? "Zgaś pochodnię" : "Zapal pochodnię", "", true));
            }
            rows.add(new MenuRow("opt:logout", "Wyjdź z gry", "", true));
            layoutPopupRows(rows);
            return;
        }
        if (logoutConfirm && mobile) {
            // telefon (makieta): "Na pewno chcesz sie wylogowac?" i dwa przyciski obok siebie
            float bw = 110, bh = 36, by = menuY + 14;
            MenuRow yes = new MenuRow("logout:yes", "Wyloguj", "", true);
            MenuRow no = new MenuRow("logout:no", "Zostań", "", true);
            yes.x = menuX + menuW / 2f - 10 - bw; no.x = menuX + menuW / 2f + 10;
            yes.y = no.y = by;
            yes.w = no.w = bw;
            yes.h = no.h = bh;
            menuRows.add(yes);
            menuRows.add(no);
            return;
        }
        if (logoutConfirm) {
            List<MenuRow> rows = new ArrayList<>();
            rows.add(new MenuRow("logout:yes", "Wyloguj", "", true));
            rows.add(new MenuRow("logout:no", "Zostań w grze", "", true));
            layoutPopupRows(rows);
            return;
        }
        if (pickSlot >= 0 && mobile) {
            rebuildMobilePick(session);
            return;
        }
        if (pickSlot >= 0) {
            rebuildPickMenu(session);
            return;
        }
        if (playerMenu != null) {
            rebuildPlayerMenu(session);
            return;
        }
        if (rankingView != null) {
            rebuildRankingMenu();
            return;
        }
        if (friendsView != null) {
            rebuildFriendsMenu();
            return;
        }
        if (marketView != null) {
            rebuildMarketMenu(session);
            return;
        }
        if (achievementsView && journalMode) {
            rebuildJournalMenu(session);
            return;
        }
        if (achievementsView && infoTitle != null) {
            rebuildInfoMenu();
            return;
        }
        if (achievementsView) {
            rebuildAchievementsMenu(session);
            return;
        }
        if (npcMode) {
            rebuildNpcMenu(session);
            return;
        }
        PlayerCharacter pc = session.character();
        // tyle wierszy miesci kolumna okna; czary ponad to (najwyzsze poziomy) czekaja, az zwolni sie miejsce
        int bookRows = Math.max(5, (int) ((menuH - 40) / rowH));
        // czary profesji i zapowiedzi na nastepne 100 poziomow, stronami (wiersz "Kolejne czary" przewija)
        List<SpellType> all = new ArrayList<>();
        int lastKnown = 0;
        for (SpellType sp : session.spells().registry().forVocation(pc.vocation())) {
            if (sp.minLevel() > pc.level() + 100) continue;
            if (sp.minLevel() <= pc.level()) lastKnown = all.size();
            all.add(sp);
        }
        int spellsPerPage = all.size() > bookRows ? bookRows - 1 : bookRows;
        // spellPage to tu indeks pierwszego czaru; domyslnie pelna strona konczaca sie najwyzszym poznanym czarem
        if (spellPage < 0) spellPage = Math.max(0, Math.min(all.size(), lastKnown + 2) - spellsPerPage);
        if (spellPage >= all.size()) spellPage = 0;
        List<SpellType> spells = all.subList(spellPage, Math.min(all.size(), spellPage + spellsPerPage));
        int spellPages = all.size() > bookRows ? 2 : 1;
        for (SpellType sp : spells) {
            boolean ok = pc.level() >= sp.minLevel() && pc.skills().level(Skill.MAGIC) >= sp.minMagic();
            String words = session.spells().registry().words(sp.key());
            String detail = (words != null ? words + ", " : "") + sp.mana() + " many, poz. " + sp.minLevel()
                    + (sp.minMagic() > 0 ? ", magia " + sp.minMagic() : "");
            menuRows.add(new MenuRow("cast:" + sp.key(), sp.name(), detail, ok));
        }
        if (spellPages > 1) menuRows.add(new MenuRow("book:spells", "Kolejne czary", "czary " + (spellPage + 1) + "-"
                + (spellPage + spells.size()) + " z " + all.size(), true));
        // ustawianie paska: wybierz przycisk, potem dotknij czaru (ten sam czar drugi raz = automat)
        String assignLabel = assignSlot < 0 ? "Ustaw pasek czarów" : "Przycisk " + (assignSlot + 1) + ": dotknij czaru";
        String assignDetail = assignSlot < 0 ? (customBar.isCustom() ? "własny układ; dotknij, aby zmienić" : "pasek automatyczny; dotknij, aby zmienić")
                : "teraz: " + (customBar.custom(assignSlot) == null ? "automat" : customBar.custom(assignSlot)) + "; dotknij, by wybrać następny";
        menuRows.add(new MenuRow("book:assign", assignLabel, assignDetail, true));
        menuRows.add(new MenuRow("book:journal", "Dziennik zadań", "postęp zadań i drogi do zleceniodawców (Z)", true));
        menuRows.add(new MenuRow("book:hunt", "Analiza polowania", "doświadczenie, zabicia i złoto na godzinę (H)", true));
        // receptury: tylko na poziom postaci (+100 jako zapowiedz), stronami
        List<Recipe> shown = new ArrayList<>();
        for (Recipe rc : session.recipes().all()) if (rc.minLevel() <= pc.level() + 100) shown.add(rc);
        int perPage = Math.max(1, bookRows - 4); // 3 wiersze ksiegi + wiersz strony
        int bookPages = Math.max(1, (shown.size() + perPage - 1) / perPage);
        if (bookPage >= bookPages) bookPage = 0;
        for (Recipe rc : shown.subList(bookPage * perPage, Math.min(shown.size(), (bookPage + 1) * perPage))) {
            StringBuilder d = new StringBuilder();
            for (java.util.Map.Entry<Integer, Integer> e : rc.ingredients().entrySet()) {
                if (d.length() > 0) d.append(", ");
                d.append(e.getValue()).append("x ").append(session.items().get(e.getKey()).name());
            }
            if (rc.goldCost() > 0) d.append(", ").append(rc.goldCost()).append(" gp");
            boolean ok = CraftingSystem.canCraft(pc, rc) == CastResult.OK;
            menuRows.add(new MenuRow("craft:" + rc.key(),
                    rc.resultCount() + "x " + session.items().get(rc.resultItemId()).name(), d.toString(), ok));
        }
        if (bookPages > 1) menuRows.add(new MenuRow("book:page", "Następna strona receptur", (bookPage + 1) + " / " + bookPages, true));
        // dwie kolumny: czary po lewej, receptury po prawej
        float colW = (menuW - 30) / 2f;
        int spellsCount = 0;
        for (MenuRow row : menuRows) if (row.action.startsWith("cast:")) spellsCount++;
        int li = 0, ri = 0;
        for (MenuRow row : menuRows) {
            boolean left = row.action.startsWith("cast:") || row.action.equals("book:spells");
            int idx = left ? li++ : ri++;
            row.x = menuX + 10 + (left ? 0 : colW + 10);
            row.y = menuY + menuH - 40 - (idx + 1) * rowH;
            row.w = colW;
            row.h = rowH - 3;
        }
    }
    /** Otwiera wybor akcji dla pola paska (rodzaj, potem konkretny czar, mikstura albo runa). */
    public void openPicker(int slot) {
        logoutConfirm = false;
        optionsMenu = false;
        if (trade != null || slot < 0 || slot >= spellButtons.length) return;
        playerMenu = null;
        rankingView = null;
        friendsView = null;
        marketView = null;
        achievementsView = false;
        npcMode = false;
        assignSlot = -1;
        bagOpen = false;
        menuOpen = true;
        pickSlot = slot;
        pickCat = null;
    }

    public int pickerSlot() { return pickSlot; }

    /** Czy punkt lezy pod otwartym oknem (napisy pod nim nie przebijaja przez okno). */
    private boolean underMenu(float px, float py) {
        if (charOpen && (px >= eqX && px <= eqX + eqW && py >= eqY && py <= eqY + eqH
                || px >= skX && px <= skX + skW && py >= skY && py <= skY + skH)) return true;
        return (menuOpen || bigMapOpen) && px >= menuX && px <= menuX + menuW && py >= menuY && py <= menuY + menuH;
    }

    /** Rodzaj w otwartym wyborze akcji (heal, attack, potion, rune, support). */
    public void pickCategory(String cat) { if (pickSlot >= 0) pickCat = cat; }

    private static String pickTitle(String cat) {
        switch (cat) {
            case "heal": return "czar leczący";
            case "attack": return "czar atakujący";
            case "support": return "inny czar";
            case "potion": return "mikstura";
            default: return "runa";
        }
    }

    private void pickRow(String a, GameSession session, Actions actions) {
        if (a.startsWith("cat:")) {
            pickCat = a.substring(4);
            return;
        }
        if (a.equals("back")) {
            pickCat = null;
            return;
        }
        if (a.equals("none")) return; // wiersz tylko informuje
        if (a.equals("page")) {
            mPickPage++; // telefon: nastepna strona wyboru (po ostatniej od poczatku)
            return;
        }
        if (a.equals("book")) {
            // ksiega czarow i rzemiosla (dawny przycisk KSIEGA; klawisz K)
            menuOpen = false;
            pickSlot = -1;
            pickCat = null;
            toggleBook();
            return;
        }
        customBar.set(pickSlot, a.equals("auto") ? null : a.equals("clear") ? pl.fawizja.pixel.settings.SpellBar.NONE : a.substring(4));
        updateBar(session);
        actions.onButton("bar_saved");
        menuOpen = false;
        pickSlot = -1;
        pickCat = null;
    }

    private void rebuildPickMenu(GameSession session) {
        PlayerCharacter pc = session.character();
        List<MenuRow> rows = new ArrayList<>();
        if (pickCat == null) {
            rows.add(new MenuRow("pick:cat:heal", "Czar leczący", "leczenie siebie", true));
            rows.add(new MenuRow("pick:cat:attack", "Czar atakujący", "na cel albo obszarowy", true));
            rows.add(new MenuRow("pick:cat:potion", "Mikstura", "zdrowie albo mana z plecaka", true));
            rows.add(new MenuRow("pick:cat:rune", "Runa", "z plecaka, bez many", true));
            rows.add(new MenuRow("pick:cat:support", "Inny czar", "światło, tworzenie run i strzał", true));
            rows.add(new MenuRow("pick:auto", "Automat", "gra sama dobiera akcję pola", true));
            rows.add(new MenuRow("pick:clear", "Wyczyść pole", "pole zostaje puste", true));
            rows.add(new MenuRow("pick:book", "Księga czarów", "wszystkie czary i rzemiosło (klawisz K)", true));
        } else if (pickCat.equals("potion") || pickCat.equals("rune")) {
            boolean potion = pickCat.equals("potion");
            if (potion) {
                rows.add(new MenuRow("pick:set:potion_hp", "Najlepsza mikstura zdrowia", "najsilniejsza dozwolona z plecaka", true));
                rows.add(new MenuRow("pick:set:potion_mp", "Najlepsza mikstura many", "najsilniejsza dozwolona z plecaka", true));
            }
            List<ItemType> list = new ArrayList<>();
            for (ItemType it : session.items().all()) {
                boolean rune = it.category() == pl.fawizja.pixel.item.ItemCategory.RUNE && session.spells().registry().find(it.key()) != null;
                boolean pot = it.category() == pl.fawizja.pixel.item.ItemCategory.POTION;
                if ((potion ? pot : rune) && it.usableBy(pc.vocation())) list.add(it);
            }
            list.sort(java.util.Comparator.comparingInt(ItemType::minLevel).thenComparing(ItemType::name));
            for (ItemType it : list) {
                int n = pc.backpack().count(it.id());
                rows.add(new MenuRow("pick:set:item:" + it.key(), it.name(),
                        "w plecaku: " + n + (it.minLevel() > 0 ? ", poz. " + it.minLevel() : ""), pc.level() >= it.minLevel()));
            }
        } else {
            for (SpellType sp : session.spells().registry().forVocation(pc.vocation())) {
                pl.fawizja.pixel.magic.SpellKind k = sp.kind();
                boolean match = pickCat.equals("heal") ? k == pl.fawizja.pixel.magic.SpellKind.HEAL_SELF
                        : pickCat.equals("attack") ? k.isOffensive() : !k.isHeal() && !k.isOffensive();
                if (!match) continue;
                boolean ok = pc.level() >= sp.minLevel() && pc.skills().level(Skill.MAGIC) >= sp.minMagic();
                rows.add(new MenuRow(ok ? "pick:set:cast:" + sp.key() : "pick:none", sp.name(),
                        sp.mana() + " many, poz. " + sp.minLevel() + (sp.minMagic() > 0 ? ", magia " + sp.minMagic() : ""), ok));
            }
            if (rows.isEmpty()) rows.add(new MenuRow("pick:none", "Brak czarów tego rodzaju", "dla tej profesji", false));
        }
        if (pickCat != null) rows.add(new MenuRow("pick:back", "Wstecz", "inny rodzaj akcji", true));
        int perCol = Math.max(3, (int) ((menuH - 50) / rowH));
        int max = perCol * 2;
        if (rows.size() > max) rows.subList(max - (pickCat != null ? 1 : 0), rows.size() - (pickCat != null ? 1 : 0)).clear();
        float colW = (menuW - 30) / 2f;
        for (int i = 0; i < rows.size(); i++) {
            MenuRow row = rows.get(i);
            row.x = menuX + 10 + (i / perCol) * (colW + 10);
            row.y = menuY + menuH - 40 - (i % perCol + 1) * rowH;
            row.w = colW;
            row.h = rowH - 3;
            menuRows.add(row);
        }
    }

    /** Co wiadomo o graczu z okienka (odswiezane, gdy zmieni sie {@code social().version()}). */
    private pl.fawizja.pixel.social.PlayerInfo pmInfo;
    private long pmVersion = -1;
    private String pmInfoName;

    /**
     * Okienko gracza (dotkniecie gracza w swiecie, na liscie walki albo VIP): imie, poziom i profesja, potem
     * wiadomosc, znajomi, druzyna, handel, gildia, pojedynek, ogladanie i atak.
     */
    private void rebuildPlayerMenu(GameSession session) {
        PlayerMenu pm = playerMenu;
        long ver = session.social().version();
        if (pmInfo == null || ver != pmVersion || !pm.name().equals(pmInfoName)) {
            pmInfo = session.playerInfo(pm.name());
            pmVersion = ver;
            pmInfoName = pm.name();
        }
        boolean friend = false;
        for (String n : session.character().friends().names()) friend |= n.equalsIgnoreCase(pm.name());
        if (vipView != null) for (pl.fawizja.pixel.friends.FriendsView.Row r : vipView.rows()) friend |= r.name().equalsIgnoreCase(pm.name());
        List<MenuRow> rows = new ArrayList<>();
        rows.add(new MenuRow("player:pm:" + pm.name(), "Wiadomość", "", true));
        rows.add(friend ? new MenuRow("player:unfriend:" + pm.name(), "Usuń ze znajomych", "", true)
                : new MenuRow("player:friend:" + pm.name(), "Dodaj do znajomych", "", true));
        if (session.social().canInvite(pm.name())) rows.add(new MenuRow("player:party:" + pm.name(), "Zaproś do drużyny", "", true));
        rows.add(new MenuRow("player:trade:" + pm.name(), "Handel", "", true));
        rows.add(new MenuRow("player:guild:" + pm.name(), "Zaproś do gildii", "", true));
        rows.add(new MenuRow("player:duel:" + pm.name(), "Pojedynek", "", true));
        rows.add(new MenuRow("player:inspect:" + pm.name(), "Obejrzyj", "", true));
        if (pm.id() >= 0) {
            rows.add(new MenuRow(pm.ally() ? "player:none" : "player:attack:" + pm.id(), pm.attacking() ? "Przerwij atak" : "Atakuj", "", !pm.ally()));
        }
        if (rows.size() != playerRowsCount) {
            playerRowsCount = rows.size();
            menuGeometry();
        }
        layoutPopupRows(rows);
    }

    /** Kolumny wierszy malego okienka (2, gdy jedna kolumna nie miesci sie na niskim ekranie). */
    private int popupCols = 1;

    /** Wiersze malego okienka pod naglowkiem (tytul + linia opisu): jeden pod drugim albo w dwoch kolumnach. */
    private void layoutPopupRows(List<MenuRow> rows) {
        int perCol = popupCols == 2 ? (rows.size() + 1) / 2 : rows.size();
        float colW = (menuW - 16 - (popupCols - 1) * 6) / popupCols;
        for (int i = 0; i < rows.size(); i++) {
            MenuRow row = rows.get(i);
            int col = i / Math.max(1, perCol), r = i % Math.max(1, perCol);
            row.x = menuX + 8 + col * (colW + 6);
            row.y = menuY + menuH - 24 - 20 - (r + 1) * rowH;
            row.w = colW;
            row.h = rowH - 3;
            menuRows.add(row);
        }
    }

    /** Linia pod imieniem w okienku gracza: profesja i poziom (albo "wczytywanie" do odpowiedzi serwera). */
    private String playerInfoLine(GameSession session) {
        if (pmInfo == null) return "";
        if (pmInfo.known()) return pmInfo.describe();
        return session.social().online() ? "Wczytywanie..." : "Poziom i profesja widoczne online.";
    }

    /** Potwierdzenie wylogowania (przycisk WYJDZ). */
    public void confirmLogout() {
        if (trade != null) return;
        playerMenu = null;
        rankingView = null;
        friendsView = null;
        marketView = null;
        achievementsView = false;
        npcMode = false;
        pickSlot = -1;
        bagOpen = false;
        logoutConfirm = true;
        menuOpen = true;
        menuGeometry();
    }

    public boolean logoutConfirmOpen() { return menuOpen && logoutConfirm; }

    /** Menu "Opcje" (ustawienia i wyjscie) - na waskim telefonie zamiast osobnego przycisku wyjscia. */
    private boolean optionsMenu;

    public void openOptions() {
        if (trade != null) return;
        playerMenu = null;
        rankingView = null;
        friendsView = null;
        marketView = null;
        achievementsView = false;
        npcMode = false;
        pickSlot = -1;
        bagOpen = false;
        logoutConfirm = false;
        optionsMenu = true;
        menuOpen = true;
        menuGeometry();
    }

    public boolean optionsOpen() { return menuOpen && optionsMenu; }

    private void closeLogoutConfirm() {
        logoutConfirm = false;
        optionsMenu = false;
        menuOpen = false;
        menuGeometry();
    }

    /** Osiagniecia w dwoch kolumnach albo bestiariusz (najczesciej zabijane potwory). */
    private void rebuildInfoMenu() {
        int perCol = Math.max(3, (int) ((menuH - 70) / rowH));
        float colW = (menuW - 30) / 2f;
        List<MenuRow> rows = new ArrayList<>();
        // wiersz {etykieta, opis[, stan[, akcja]]}; bez akcji wiersz tylko informuje
        for (String[] r : infoRows) {
            String detail = r.length > 2 && !r[2].isEmpty() ? r[1] + " - " + r[2] : r[1];
            // piaty element "off": wiersz wyszarzony (np. oferta sklepu, ktorej teraz nie da sie kupic)
            rows.add(new MenuRow(r.length > 3 ? r[3] : "ach:none", r[0], detail, !(r.length > 4 && "off".equals(r[4]))));
        }
        if (rows.isEmpty()) rows.add(new MenuRow("ach:none", "Brak danych", "", false));
        for (int i = 0; i < rows.size() && i < perCol * 2; i++) {
            MenuRow row = rows.get(i);
            row.x = menuX + 10 + (i / perCol) * (colW + 10);
            row.y = menuY + menuH - 40 - (i % perCol + 1) * rowH;
            row.w = colW;
            row.h = rowH - 3;
            menuRows.add(row);
        }
    }

    /** Dziennik: zadanie dnia, zadania w toku i do oddania, potem dostepne; dotkniecie = marsz do zleceniodawcy. */
    private void rebuildJournalMenu(GameSession session) {
        PlayerCharacter pc = session.character();
        List<MenuRow> rows = new ArrayList<>();
        int perCol = Math.max(3, (int) ((menuH - 70) / rowH));
        pl.fawizja.pixel.daily.DailyTask d = pc.daily();
        long day = session.today();
        String dd;
        if (!d.activeOn(day)) dd = "do wzięcia u przewodnika w mieście";
        else if (d.claimed()) dd = "wykonane - następne jutro";
        else if (d.doneOn(day)) dd = "ODDAJ u przewodnika";
        else dd = monsterNames.apply(d.monster()) + ": " + d.progress() + " / " + d.target();
        boolean dailyHunting = d.activeOn(day) && !d.claimed() && !d.doneOn(day);
        rows.add(new MenuRow(dailyHunting ? "journal:hunt" : "journal:guide", "Zadanie dnia", dd, true));
        pl.fawizja.pixel.quest.HuntTask h = pc.hunt();
        if (h.active()) {
            rows.add(new MenuRow(h.done() ? "journal:guide" : "journal:hunt", "Zlecenie łowcy", h.done() ? "ODDAJ u przewodnika"
                    : monsterNames.apply(h.monster()) + ": " + h.progress() + " / " + h.target() + " - dotknij, aby iść", true));
        }
        pl.fawizja.pixel.quest.QuestSystem qs = session.quests();
        List<MenuRow> available = new ArrayList<>();
        if (qs != null) {
            for (pl.fawizja.pixel.quest.QuestType q : qs.registry().all()) {
                pl.fawizja.pixel.quest.QuestLog.State st = pc.quests().state(q.key());
                pl.fawizja.pixel.npc.Npc giver = session.npcs().get(q.giver());
                String who = giver == null ? q.giver() : giver.name() + " (" + giver.town() + ")";
                if (st == pl.fawizja.pixel.quest.QuestLog.State.ACTIVE) {
                    boolean ready = qs.ready(pc, q);
                    rows.add(new MenuRow("journal:goto:" + q.giver(), q.name(), ready ? "ODDAJ: " + who
                            : qs.progress(pc, q) + " / " + q.amount() + " - " + who, true));
                } else if (st == pl.fawizja.pixel.quest.QuestLog.State.AVAILABLE && qs.unlocked(pc, q) && pc.level() >= q.minLevel()) {
                    available.add(new MenuRow("journal:goto:" + q.giver(), "Nowe: " + q.name(), "u " + who + " - " + qs.rewardText(q), true));
                }
            }
        }
        if (rows.size() == (h.active() ? 2 : 1)) rows.add(new MenuRow("ach:none", "Brak zadań w toku", "przewodnicy w miastach mają zlecenia", false));
        rows.addAll(available);
        if (mobile) {
            // telefon: okno kategorii 336 dp, dwie kolumny wierszy pod paskiem tytulu
            float rh = 34, cw = (menuW - 20) / 2f;
            int pc2 = Math.max(1, (int) ((menuH - 30) / rh));
            for (int i = 0; i < rows.size() && i < pc2 * 2; i++) {
                MenuRow row = rows.get(i);
                row.x = menuX + 7 + (i / pc2) * (cw + 6);
                row.y = menuY + menuH - 28 - (i % pc2 + 1) * rh;
                row.w = cw;
                row.h = rh - 3;
                menuRows.add(row);
            }
            return;
        }
        float colW = (menuW - 30) / 2f;
        for (int i = 0; i < rows.size() && i < perCol * 2; i++) {
            MenuRow row = rows.get(i);
            row.x = menuX + 10 + (i / perCol) * (colW + 10);
            row.y = menuY + menuH - 40 - (i % perCol + 1) * rowH;
            row.w = colW;
            row.h = rowH - 3;
            menuRows.add(row);
        }
    }

    private void rebuildAchievementsMenu(GameSession session) {
        PlayerCharacter pc = session.character();
        List<MenuRow> rows = new ArrayList<>();
        int perCol = Math.max(3, (int) ((menuH - 70) / rowH));
        pl.fawizja.pixel.achievement.Bestiary b = pc.bestiary();
        if (!bestiaryMode) {
            rows.add(new MenuRow("ach:toggle", "Pokaż bestiariusz", String.format("%,d pokonanych, %d rodzajów", b.total(), b.species()), true));
            List<MenuRow> all = new ArrayList<>();
            for (pl.fawizja.pixel.achievement.Achievement a : pl.fawizja.pixel.achievement.Achievement.values()) {
                boolean has = pc.achievements().has(a);
                long p = Math.min(a.progress(pc), a.target());
                boolean worn = a.name().equals(pc.title());
                String detail = worn ? "twój tytuł (dotknij, aby zdjąć)"
                        : has ? "zdobyte: " + a.goal() + "; dotknij = tytuł" : a.goal() + String.format(" (%,d / %,d)", p, a.target());
                all.add(new MenuRow(has ? "ach:title:" + a.name() : "ach:none", (worn ? "> " : has ? "* " : "") + a.title(), detail, has));
            }
            int room = perCol * 2 - 1;
            if (all.size() <= room) {
                rows.addAll(all);
            } else {
                // strony: ostatnie miejsce na przycisk nastepnej strony
                int per = room - 1;
                int pages = (all.size() + per - 1) / per;
                if (achPage >= pages) achPage = 0;
                rows.addAll(all.subList(achPage * per, Math.min(all.size(), (achPage + 1) * per)));
                rows.add(new MenuRow("ach:page", "Strona " + (achPage + 1) + " / " + pages, "dotknij, aby zobaczyć następną", true));
            }
        } else {
            rows.add(new MenuRow("ach:toggle", "Pokaż osiągnięcia", String.format("%,d pokonanych, %d rodzajów, %d bossów",
                    b.total(), b.species(), b.bossesDefeated()), true));
            for (String k : b.mostKilled()) {
                if (rows.size() >= perCol * 2) break;
                rows.add(new MenuRow("best:" + k, monsterNames.apply(k) + (b.isBoss(k) ? " (boss)" : ""),
                        String.format("pokonano %,d; dotknij = karta", b.kills(k)), true));
            }
            if (rows.size() == 1) rows.add(new MenuRow("ach:none", "Jeszcze nic", "pokonane potwory pojawią się tutaj", false));
        }
        float colW = (menuW - 30) / 2f;
        for (int i = 0; i < rows.size() && i < perCol * 2; i++) {
            MenuRow row = rows.get(i);
            row.x = menuX + 10 + (i / perCol) * (colW + 10);
            row.y = menuY + menuH - 40 - (i % perCol + 1) * rowH;
            row.w = colW;
            row.h = rowH - 3;
            menuRows.add(row);
        }
    }

    /** Rynek: cudze oferty po lewej (dotkniecie = kup), wlasne po prawej (dotkniecie = wycofaj). */
    private void rebuildMarketMenu(GameSession session) {
        List<MenuRow> left = new ArrayList<>();
        List<MenuRow> right = new ArrayList<>();
        int perCol = Math.max(3, (int) ((menuH - 70) / rowH));
        long money = session.gold() + session.character().bank().balance();
        String found = marketView.search() == null ? "najnowsze oferty" : "szukano: " + marketView.search();
        left.add(new MenuRow("market:none", "Kup (dotknij oferty)", found + "; płacisz z konta albo plecaka", false));
        right.add(new MenuRow("market:none", "Twoje oferty (dotknij, aby wycofać)",
                "/rynek wystaw 5 wilcze futro 300", false));
        for (pl.fawizja.pixel.market.MarketView.Row r : marketView.rows()) {
            String label = r.count() + "x " + r.item();
            String price = String.format("%,d gp", r.price());
            if (r.mine()) {
                if (right.size() < perCol) right.add(new MenuRow("market:cancel:" + r.id(), label, price + " (#" + r.id() + ")", true));
            } else if (left.size() < perCol) {
                left.add(new MenuRow("market:buy:" + r.id(), label, price + " - " + r.seller() + " (#" + r.id() + ")", money >= r.price()));
            }
        }
        if (left.size() == 1) left.add(new MenuRow("market:none", "Brak ofert", "/rynek nazwa - szukanie przedmiotu", false));
        if (right.size() == 1) right.add(new MenuRow("market:none", "Nic nie wystawiasz",
                "prowizja rynku " + pl.fawizja.pixel.market.Market.FEE_PERCENT + "%", false));
        float colW = (menuW - 30) / 2f;
        for (int c = 0; c < 2; c++) {
            List<MenuRow> col = c == 0 ? left : right;
            for (int i = 0; i < col.size(); i++) {
                MenuRow row = col.get(i);
                row.x = menuX + 10 + c * (colW + 10);
                row.y = menuY + menuH - 40 - (i + 1) * rowH;
                row.w = colW;
                row.h = rowH - 3;
                menuRows.add(row);
            }
        }
    }

    /** Znajomi: w grze po lewej (dotkniecie = prywatna wiadomosc), poza gra po prawej. */
    private void rebuildFriendsMenu() {
        List<MenuRow> left = new ArrayList<>();
        List<MenuRow> right = new ArrayList<>();
        int perCol = Math.max(3, (int) ((menuH - 70) / rowH));
        left.add(new MenuRow("friend:none", "W grze (" + friendsView.online() + ")", "dotknij, aby napisać prywatnie", false));
        right.add(new MenuRow("friend:none", "Poza grą (" + (friendsView.rows().size() - friendsView.online()) + ")",
                "/znajomi usuń Imię - usuwa z listy", false));
        for (pl.fawizja.pixel.friends.FriendsView.Row r : friendsView.rows()) {
            String detail = r.level() > 0 ? r.vocation() + ", poziom " + r.level() : "postać nieznana";
            String ago = r.online() ? "" : pl.fawizja.pixel.friends.FriendsView.describeAgo(r.minutesAgo());
            if (!ago.isEmpty()) detail += ", " + ago;
            if (r.online() && left.size() < perCol) left.add(new MenuRow("friend:pm:" + r.name(), r.name(), detail, true));
            else if (!r.online() && right.size() < perCol) right.add(new MenuRow("friend:none", r.name(), detail, false));
        }
        if (friendsView.rows().isEmpty()) left.add(new MenuRow("friend:none", "Lista jest pusta",
                "/znajomi dodaj Imię albo dotknij gracza", false));
        float colW = (menuW - 30) / 2f;
        for (int c = 0; c < 2; c++) {
            List<MenuRow> col = c == 0 ? left : right;
            for (int i = 0; i < col.size(); i++) {
                MenuRow row = col.get(i);
                row.x = menuX + 10 + c * (colW + 10);
                row.y = menuY + menuH - 40 - (i + 1) * rowH;
                row.w = colW;
                row.h = rowH - 3;
                menuRows.add(row);
            }
        }
    }

    /** Ranking: miejsca 1-5 po lewej, 6-10 po prawej, na dole miejsce gracza. */
    private void rebuildRankingMenu() {
        pl.fawizja.pixel.ranking.RankingView v = rankingView;
        float colW = (menuW - 30) / 2f;
        List<pl.fawizja.pixel.ranking.RankingView.Row> rows = v.rows();
        boolean guilds = pl.fawizja.pixel.ranking.GuildRanking.TITLE.equals(v.title());
        boolean skillRank = v.title().startsWith(pl.fawizja.pixel.ranking.Ranking.SKILL_TITLE);
        boolean weeklyRank = pl.fawizja.pixel.ranking.WeeklyRanking.TITLE.equals(v.title());
        int perCol = Math.max(5, (rows.size() + 1) / 2);
        for (int i = 0; i < rows.size(); i++) {
            pl.fawizja.pixel.ranking.RankingView.Row r = rows.get(i);
            MenuRow row = new MenuRow("ranking:none", r.place() + ". " + r.name() + (r.place() == v.mine() ? (guilds ? "  (twoja)" : "  (ty)") : ""),
                    weeklyRank ? r.vocation() + String.format(", +%,d exp", r.level())
                            : r.vocation() + (guilds ? ", suma poziomów " : skillRank ? ", umiejętność " : ", poziom ") + r.level(),
                    true);
            row.x = menuX + 10 + (i / perCol) * (colW + 10);
            row.y = menuY + menuH - 40 - (i % perCol + 1) * rowH;
            row.w = colW;
            row.h = rowH - 3;
            menuRows.add(row);
        }
        String mine = guilds ? (v.mine() > 0 ? "Twoja gildia: " + v.mine() + ". miejsce z " + v.total() : "Nie należysz do gildii z rankingu")
                : v.mine() > 0 ? "Twoje miejsce: " + v.mine() + " z " + v.total() : "Nie ma cię w tym rankingu";
        MenuRow me = new MenuRow("ranking:none", mine, weeklyRank
                ? "w poniedziałek nagrody: 100 000 / 50 000 / 25 000 gp za miejsca 1-3"
                : "/ranking druid, paladin, mag, mnich, gildie, tydzień", true);
        me.x = menuX + 10;
        me.y = menuY + menuH - 40 - (perCol + 1) * rowH - 6;
        me.w = menuW - 20;
        me.h = rowH - 3;
        menuRows.add(me);
    }

    /** Okno handlu: lewa kolumna - oferty i decyzje, prawa - przedmioty z plecaka do dodania. */
    private void rebuildTradeMenu(GameSession session) {
        List<MenuRow> left = new ArrayList<>();
        List<MenuRow> right = new ArrayList<>();
        int perCol = Math.max(3, (int) ((menuH - 70) / rowH));
        left.add(new MenuRow("trade:none", "Ty dajesz" + (trade.mineAccepted() ? " (zaakceptowano)" : ""), trade.mine(), false));
        left.add(new MenuRow("trade:none", trade.partner() + " daje" + (trade.theirsAccepted() ? " (zaakceptował)" : ""),
                trade.theirs(), false));
        left.add(new MenuRow("trade:accept", trade.mineAccepted() ? "Czekam na drugą stronę" : "AKCEPTUJ",
                "zmiana oferty kasuje akceptacje obu stron", !trade.mineAccepted()));
        left.add(new MenuRow("trade:cancel", "ANULUJ", "nic nie zostanie wymienione", true));
        long gold = session.gold();
        for (long g : new long[] {100, 1_000, 10_000}) {
            left.add(new MenuRow("trade:offer:" + g + " gp", "+" + String.format("%,d gp", g), "dodaj złoto do oferty", gold >= g));
        }
        java.util.Map<Integer, Integer> counts = new java.util.LinkedHashMap<>();
        for (pl.fawizja.pixel.item.ItemStack st : session.character().backpack().contents()) {
            if (session.items().get(st.itemId()).category() == pl.fawizja.pixel.item.ItemCategory.CURRENCY) continue;
            counts.merge(st.itemId(), st.count(), Integer::sum);
        }
        for (java.util.Map.Entry<Integer, Integer> e : counts.entrySet()) {
            if (right.size() >= perCol) break;
            pl.fawizja.pixel.item.ItemType it = session.items().get(e.getKey());
            int n = Math.min(e.getValue(), 10_000);
            right.add(new MenuRow("trade:offer:" + n + " " + it.key(), "Oferuj " + n + "x " + it.name(), "cała ilość z plecaka", true));
        }
        if (right.isEmpty()) right.add(new MenuRow("trade:none", "Plecak bez przedmiotów", "możesz oferować złoto", false));
        float colW = (menuW - 30) / 2f;
        for (int c = 0; c < 2; c++) {
            List<MenuRow> col = c == 0 ? left : right;
            for (int i = 0; i < col.size() && i < perCol; i++) {
                MenuRow row = col.get(i);
                row.x = menuX + 10 + c * (colW + 10);
                row.y = menuY + menuH - 40 - (i + 1) * rowH;
                row.w = colW;
                row.h = rowH - 3;
                menuRows.add(row);
            }
        }
    }

    private static List<Integer> distinctIds(pl.fawizja.pixel.inventory.Container c) {
        java.util.Set<Integer> seen = new java.util.LinkedHashSet<>();
        for (ItemStack s : c.contents()) if (s != null) seen.add(s.itemId());
        return new ArrayList<>(seen);
    }

    private void rebuildNpcMenu(GameSession session) {
        PlayerCharacter pc = session.character();
        List<MenuRow> left = new ArrayList<>();
        List<MenuRow> right = new ArrayList<>();
        int perCol = Math.max(3, (int) ((menuH - 70) / rowH));
        switch (npc.role()) {
            case MERCHANT: {
                if (smithView) {
                    left.add(new MenuRow("npc:smith", "Wróć do sklepu", "ulepszanie zakładanego wyposażenia", true));
                    long money = session.gold();
                    for (pl.fawizja.pixel.item.EquipSlot slot : pl.fawizja.pixel.item.EquipSlot.values()) {
                        ItemStack worn = slot == pl.fawizja.pixel.item.EquipSlot.NONE ? null : pc.equipment().get(slot);
                        if (worn == null || left.size() >= perCol) continue;
                        pl.fawizja.pixel.item.ItemType t = session.items().get(worn.itemId());
                        if (!pl.fawizja.pixel.item.Upgrade.upgradable(t)) continue;
                        int next = worn.upgrade() + 1;
                        String label = "Ulepsz: " + pl.fawizja.pixel.item.Upgrade.name(t, worn.upgrade());
                        if (next > pl.fawizja.pixel.item.Upgrade.MAX) {
                            left.add(new MenuRow("npc:none", label, "ulepszone do końca", false));
                            continue;
                        }
                        long price = pl.fawizja.pixel.item.Upgrade.price(t, next);
                        left.add(new MenuRow("npc:upgrade:" + slot.name(), label, "do +" + next + ": " + price + " gp, szansa "
                                + pl.fawizja.pixel.item.Upgrade.chancePercent(next) + "%", money >= price));
                    }
                    if (left.size() == 1) left.add(new MenuRow("npc:none", "Nic do ulepszenia", "załóż broń, tarczę albo zbroję", false));
                    right.add(new MenuRow("npc:none", "Kowalstwo", "do +5; nieudana próba zabiera tylko złoto", false));
                    pl.fawizja.pixel.item.Enchantments ench = pc.enchantments();
                    for (pl.fawizja.pixel.item.Enchantments.Kind k : pl.fawizja.pixel.item.Enchantments.Kind.values()) {
                        if (!k.smith()) continue;
                        long price = pl.fawizja.pixel.item.Enchantments.price(k, pc.level());
                        String left0 = ench.active(k) ? ", zostało " + pl.fawizja.pixel.item.Enchantments.describe(ench.remainingMs(k)) : "";
                        right.add(new MenuRow("npc:enchant:" + k.name(), k.label() + " (1 h)", k.effect() + "; " + price + " gp" + left0,
                                money >= price && ench.canExtend(k)));
                    }
                    right.add(new MenuRow("npc:none", "Czas zaklęć", "płynie tylko poza strefą ochronną, do 3 h zapasu", false));
                    break;
                }
                pl.fawizja.pixel.npc.Shop shop = session.shopOf(npc);
                List<pl.fawizja.pixel.npc.ShopOffer> sold = new ArrayList<>();
                for (pl.fawizja.pixel.npc.ShopOffer o : shop.offers()) if (o.sold()) sold.add(o);
                left.add(new MenuRow("npc:qty", "Ilość: " + npcQty + " (zmień)", "dotknij towaru, aby kupić", true));
                int per = perCol - 2;
                int pages = Math.max(1, (sold.size() + per - 1) / per);
                if (npcPage >= pages) npcPage = 0;
                long gold = session.gold();
                for (int i = npcPage * per; i < Math.min(sold.size(), (npcPage + 1) * per); i++) {
                    pl.fawizja.pixel.npc.ShopOffer o = sold.get(i);
                    long cost = o.buyPrice() * npcQty;
                    pl.fawizja.pixel.item.ItemType it = session.items().get(o.itemId());
                    pl.fawizja.pixel.item.ItemStack worn = it.slot() == pl.fawizja.pixel.item.EquipSlot.NONE ? null : pc.equipment().get(it.slot());
                    String delta = pl.fawizja.pixel.item.ItemCompare.shopDelta(it, worn == null ? null : session.items().get(worn.itemId()),
                            worn == null ? 0 : worn.upgrade(), pc);
                    left.add(new MenuRow("npc:buy:" + o.itemId() + ":" + npcQty, it.name(),
                            o.buyPrice() + " gp" + (npcQty > 1 ? ", razem " + cost + " gp" : "") + (delta.isEmpty() ? "" : "; " + delta), gold >= cost));
                }
                if (pages > 1) left.add(new MenuRow("npc:page", "Następna strona", (npcPage + 1) + " / " + pages, true));
                right.add(new MenuRow("npc:smith", "Ulepsz wyposażenie", "kowal: broń, tarcza i zbroja do +5", true));
                right.add(new MenuRow("npc:refill", "Uzupełnij zapasy", "mikstury i runy do " + pl.fawizja.pixel.game.GameSession.REFILL_TO
                        + ", amunicja do " + pl.fawizja.pixel.game.GameSession.REFILL_AMMO_TO, true));
                right.add(new MenuRow("npc:sellloot", "Sprzedaj cały łup", "materiały bez przedmiotów do zadań; pojedynczo: dotknij", true));
                java.util.Set<Integer> seen = new java.util.LinkedHashSet<>();
                for (ItemStack s : pc.backpack().contents()) if (s != null) seen.add(s.itemId());
                for (int id : seen) {
                    long value = pl.fawizja.pixel.npc.TradeSystem.sellValue(pc.backpack(), shop, id);
                    if (value <= 0 || right.size() >= perCol) continue;
                    right.add(new MenuRow("npc:sell:" + id, pc.backpack().countPlain(id) + "x " + session.items().get(id).name(),
                            "sprzedaj za " + value + " gp", true));
                }
                if (right.size() == 2) right.add(new MenuRow("npc:none", "Nic do sprzedania", "kupiec skupuje łupy z potworów", false));
                break;
            }
            case BANKER: {
                if (pl.fawizja.pixel.npc.Npc.CRATE.equals(npc.key())) {
                    // zwykla skrzynia: lewa kolumna - zawartosc, prawa - plecak
                    java.util.List<ItemStack> inside = session.crateContents();
                    int used = inside.size();
                    left.add(new MenuRow("npc:none", npc.name() + ": " + used + " / " + pl.fawizja.pixel.world.Crates.SLOTS
                            + " miejsc", "dotknij, aby wyjąć do plecaka", false));
                    java.util.Map<Integer, Integer> counts = new java.util.LinkedHashMap<>();
                    for (ItemStack st : inside) counts.merge(st.itemId(), st.count(), Integer::sum);
                    for (java.util.Map.Entry<Integer, Integer> e : counts.entrySet()) {
                        if (left.size() >= perCol) break;
                        left.add(new MenuRow("npc:cratetake:" + e.getKey(), "Wyjmij " + e.getValue() + "x "
                                + session.items().get(e.getKey()).name(), "do plecaka", pc.backpack().spaceFor(e.getKey()) > 0));
                    }
                    if (left.size() == 1) left.add(new MenuRow("npc:none", "Pusto", "schowaj coś z plecaka", false));
                    right.add(new MenuRow("npc:none", "Plecak", "dotknij, aby schować", false));
                    for (int id : distinctIds(pc.backpack())) {
                        if (pl.fawizja.pixel.economy.Currency.isCoin(id) || right.size() >= perCol) continue;
                        right.add(new MenuRow("npc:crateput:" + id, "Schowaj " + pc.backpack().count(id) + "x "
                                + session.items().get(id).name(), "do schowka", session.crateSpaceFor(id) > 0));
                    }
                    if (right.size() == 1) right.add(new MenuRow("npc:none", "Nic do schowania", "monety trzymaj w banku", false));
                    break;
                }
                pl.fawizja.pixel.inventory.Container depot = pc.depot();
                if (depotView) {
                    left.add(new MenuRow("npc:depot", "Wróć do konta", "depozyt: " + (depot.size() - depot.freeSlots()) + " / " + depot.size() + " miejsc", true));
                    for (int id : distinctIds(depot)) {
                        if (left.size() >= perCol) break;
                        left.add(new MenuRow("npc:take:" + id, "Wyjmij " + depot.count(id) + "x " + session.items().get(id).name(),
                                "do plecaka", pc.backpack().spaceFor(id) > 0));
                    }
                    if (left.size() == 1) left.add(new MenuRow("npc:none", "Depozyt jest pusty", "odłóż coś z plecaka", false));
                    right.add(new MenuRow("npc:storeloot", "Odłóż cały łup", "materiały bez przedmiotów do zadań", true));
                    for (int id : distinctIds(pc.backpack())) {
                        if (pl.fawizja.pixel.economy.Currency.isCoin(id) || right.size() >= perCol) continue;
                        right.add(new MenuRow("npc:store:" + id, "Odłóż " + pc.backpack().count(id) + "x " + session.items().get(id).name(),
                                "do depozytu", depot.spaceFor(id) > 0));
                    }
                    if (right.size() == 1) right.add(new MenuRow("npc:none", "Nic do odłożenia", "monety wpłać na konto", false));
                    break;
                }
                left.add(new MenuRow("npc:market", "Rynek graczy", "kupno i sprzedaż, także gdy sprzedawca jest offline", true));
                right.add(new MenuRow("npc:orders", "Zamówienia kupna", "/kupię 20 wilcze futro 2000, /sprzedaj numer", true));
                left.add(new MenuRow("npc:depot", "Depozyt przedmiotów", (depot.size() - depot.freeSlots()) + " / " + depot.size() + " miejsc, wspólny dla miast", true));
                long bal = pc.bank().balance();
                left.add(new MenuRow("npc:none", "Saldo: " + String.format("%,d gp", bal), "konto wspólne dla wszystkich miast", false));
                left.add(new MenuRow("npc:deposit", "Wpłać wszystkie monety", String.format("w plecaku: %,d gp", session.gold()), session.gold() > 0));
                for (long a : new long[] {1_000, 10_000, 100_000, 1_000_000}) {
                    left.add(new MenuRow("npc:withdraw:" + a, String.format("Wypłać %,d gp", a), "monety trafią do plecaka", bal >= a));
                }
                break;
            }
            case KING: {
                long bal = pc.bank().balance();
                long prem = pl.fawizja.pixel.game.GameSession.PREMIUM_PRICE, promo = pl.fawizja.pixel.game.GameSession.PROMOTION_PRICE;
                left.add(new MenuRow("npc:premium", pc.isPremium() ? "Konto premium: masz" : "Kup konto premium",
                        pc.isPremium() ? "kapitanowie zabiorą cię na rejs" : String.format("%,d gp z banku - rejsy statkami między portami", prem),
                        !pc.isPremium() && bal >= prem));
                boolean canPromo = pc.isPremium() && !pc.isPromoted() && pc.level() >= pl.fawizja.pixel.game.GameSession.PROMOTION_LEVEL && bal >= promo;
                left.add(new MenuRow("npc:promote", pc.isPromoted() ? "Promocja: " + pc.vocationTitle() : "Promocja: " + pc.vocation().promotedName(),
                        pc.isPromoted() ? "znak promocji i szybsza regeneracja"
                                : String.format("%,d gp z banku, wymaga premium - znak przy postaci, regeneracja +50%%", promo), canPromo));
                left.add(new MenuRow("npc:none", String.format("Saldo: %,d gp", bal), "opłaty pobiera król z konta w banku", false));
                break;
            }
            case PRIEST: {
                left.add(new MenuRow("npc:heal", "Ulecz mnie", "pełne zdrowie i mana", pc.health() < pc.maxHealth() || pc.mana() < pc.maxMana()));
                boolean home = npc.town().equalsIgnoreCase(pc.homeTown());
                long bless = pl.fawizja.pixel.balance.Balance.blessingPrice(pc.level());
                left.add(new MenuRow("npc:bless", pc.isBlessed() ? "Błogosławieństwo: masz" : "Błogosławieństwo",
                        pc.isBlessed() ? "najbliższa śmierć zabierze połowę exp mniej"
                                : String.format("%,d gp - połowa straty exp przy najbliższej śmierci", bless),
                        !pc.isBlessed() && session.gold() >= bless));
                left.add(new MenuRow("npc:settle", "Zamieszkaj w " + npc.town(),
                        home ? "już tu mieszkasz" : "teraz: " + pc.homeTown() + " - tu będziesz się odradzać", !home));
                break;
            }
            case CARRIER:
            case CAPTAIN: {
                long gold = session.gold();
                String verb = npc.role() == pl.fawizja.pixel.npc.NpcRole.CAPTAIN ? "Płyń: " : "Jedź do ";
                for (pl.fawizja.pixel.npc.Travel.Route r : pl.fawizja.pixel.npc.Travel.routes(session.npcs(), npc)) {
                    boolean locked = npc.role() == pl.fawizja.pixel.npc.NpcRole.CAPTAIN && !pc.isPremium();
                    left.add(new MenuRow("npc:travel:" + r.to().town(), verb + r.to().town(),
                            String.format("%,d gp", r.price()) + (locked ? " - tylko z kontem premium (król w Varden)"
                                    : gold < r.price() ? " - za mało monet w plecaku" : ""), !locked && gold >= r.price()));
                }
                break;
            }
            case HOUSING: {
                pl.fawizja.pixel.house.House own = session.houses().get(pc.house());
                long bal = pc.bank().balance();
                left.add(new MenuRow("npc:none", own == null ? "Nie masz domu" : "Twój dom: " + own.name(),
                        own == null ? String.format("konto: %,d gp - dom kupisz za złoto z banku", bal) : own.town() + ", wchodzisz ty i goście", false));
                if (own != null) {
                    java.util.List<String> guests = session.houseGuests(own);
                    right.add(new MenuRow("npc:none", "Goście: " + (guests.isEmpty() ? "nikt" : String.join(", ", guests)),
                            "/dom wpuść Imię, /dom wyrzuć Imię (czat)", false));
                    right.add(new MenuRow("npc:none", "Sprzedaż graczowi", "/dom sprzedaj Imię, cena - kupujący wpisuje /dom kup", false));
                    right.add(new MenuRow("npc:houseleave", "Sprzedaj miastu", String.format("%,d gp na konto (%d%% ceny)",
                            own.price() * pl.fawizja.pixel.game.GameSession.HOUSE_SELLBACK_PERCENT / 100,
                            pl.fawizja.pixel.game.GameSession.HOUSE_SELLBACK_PERCENT), true));
                }
                for (pl.fawizja.pixel.house.House h : session.houses().inTown(npc.town())) {
                    if (left.size() >= perCol) break;
                    String owner = session.houseOwner(h);
                    String desc = owner != null ? "właściciel: " + owner
                            : String.format("%,d gp, %d pól, drzwi %d,%d%s", h.price(), h.size(), h.door().x(), h.door().y(),
                                    h.z() != 0 ? " (piętro " + h.z() + ")" : "");
                    left.add(new MenuRow("npc:housebuy:" + h.id(), (owner == null ? "Kup: " : "Zajęty: ") + h.name(), desc,
                            owner == null && own == null && bal >= h.price()));
                }
                break;
            }
            case GUIDE: {
                pl.fawizja.pixel.daily.DailyTask d = pc.daily();
                long day = session.today();
                String dl, dd;
                if (!d.activeOn(day)) {
                    dl = "Zadanie dnia";
                    dd = "dotknij, aby wziąć zadanie na dziś";
                } else if (d.claimed()) {
                    dl = "Zadanie dnia: wykonane";
                    dd = "następne jutro";
                } else if (d.doneOn(day)) {
                    dl = "Zadanie dnia: ODDAJ";
                    dd = d.target() + "x " + monsterNames.apply(d.monster()) + " - odbierz nagrodę";
                } else {
                    dl = "Zadanie dnia: " + monsterNames.apply(d.monster());
                    dd = "pokonano " + d.progress() + " / " + d.target();
                }
                left.add(new MenuRow("npc:daily", dl, dd, !d.activeOn(day) || d.doneOn(day) && !d.claimed()));
                if (d.canReroll(day) && !d.doneOn(day)) {
                    long price = pl.fawizja.pixel.daily.DailyTask.rerollPrice(pc.level());
                    left.add(new MenuRow("npc:dailyreroll", "Zmień zadanie dnia", String.format("inny potwór za %,d gp, raz dziennie", price),
                            session.gold() >= price));
                }
                pl.fawizja.pixel.quest.HuntTask hunt = pc.hunt();
                if (!hunt.active()) {
                    left.add(new MenuRow("npc:hunt", "Zlecenie łowcy", "50 potworów dla twojego poziomu, bez limitu dziennego", true));
                } else {
                    left.add(new MenuRow("npc:hunt", hunt.done() ? "Zlecenie łowcy: ODDAJ" : "Zlecenie: " + monsterNames.apply(hunt.monster()),
                            hunt.done() ? "odbierz nagrodę" : "pokonano " + hunt.progress() + " / " + hunt.target(), hunt.done()));
                    if (!hunt.done()) left.add(new MenuRow("npc:huntdrop", "Porzuć zlecenie", "możesz wziąć nowe", true));
                }
                left.add(new MenuRow("npc:bosses", "Bossowie", "kto żyje, kto odrodzi się i kiedy (/bossowie)", true));
                left.add(new MenuRow("npc:hunting", "Gdzie polować?", "potwory dla twojego poziomu (/łowiska)", true));
                pl.fawizja.pixel.quest.QuestSystem qs = session.quests();
                if (qs == null) break;
                // kolejnosc: do oddania, w toku, dostepne; z przyszlych tylko najblizsze, ukonczone jednym wierszem
                List<MenuRow> ready = new ArrayList<>(), active = new ArrayList<>(), avail = new ArrayList<>();
                pl.fawizja.pixel.quest.QuestType next = null;
                int done = 0;
                for (pl.fawizja.pixel.quest.QuestType q : qs.registry().byGiver(npc.key())) {
                    // dalsze etapy lancucha widac dopiero po ukonczeniu poprzedniego
                    if (pc.quests().state(q.key()) == pl.fawizja.pixel.quest.QuestLog.State.AVAILABLE && !qs.unlocked(pc, q)) continue;
                    String reward = qs.rewardText(q);
                    switch (pc.quests().state(q.key())) {
                        case DONE: done++; break;
                        case ACTIVE:
                            if (qs.ready(pc, q)) ready.add(new MenuRow("npc:quest:" + q.key(), q.name(), "ODDAJ - nagroda: " + reward, true));
                            else active.add(new MenuRow("npc:quest:" + q.key(), q.name(), "w toku: " + qs.progress(pc, q) + " / " + q.amount(), false));
                            break;
                        default:
                            if (pc.level() >= q.minLevel()) {
                                avail.add(new MenuRow("npc:quest:" + q.key(), q.name(), "przyjmij (poz. " + q.minLevel() + "): " + reward, true));
                            } else if (next == null || q.minLevel() < next.minLevel()) {
                                next = q;
                            }
                    }
                }
                List<MenuRow> questRows = new ArrayList<>(ready);
                questRows.addAll(active);
                questRows.addAll(avail);
                if (next != null) {
                    questRows.add(new MenuRow("npc:quest:" + next.key(), next.name(), "od poziomu " + next.minLevel() + ": " + qs.rewardText(next), false));
                }
                if (done > 0) questRows.add(new MenuRow("npc:none", "Ukończone zadania: " + done, "dziennik zadań: klawisz Z", false));
                for (MenuRow row : questRows) {
                    if (left.size() < perCol) left.add(row);
                    else right.add(row);
                }
                break;
            }
            default:
        }
        // tematy rozmowy (to samo mozna wpisac w czacie obok NPC)
        boolean depotRows = depotView && npc.role() == pl.fawizja.pixel.npc.NpcRole.BANKER
                || pl.fawizja.pixel.npc.Npc.CRATE.equals(npc.key())
                || smithView && npc.role() == pl.fawizja.pixel.npc.NpcRole.MERCHANT;
        for (String topic : depotRows ? java.util.List.<String>of() : pl.fawizja.pixel.npc.DialogueRegistry.defaults().topics(npc)) {
            MenuRow row = new MenuRow("npc:ask:" + topic, "Zapytaj: " + topic, "rozmowa", true);
            if (right.size() < perCol) right.add(row);
            else if (left.size() < perCol) left.add(row);
        }
        float colW = (menuW - 30) / 2f;
        for (int c = 0; c < 2; c++) {
            List<MenuRow> col = c == 0 ? left : right;
            for (int i = 0; i < col.size(); i++) {
                MenuRow row = col.get(i);
                row.x = menuX + 10 + c * (colW + 10);
                row.y = menuY + menuH - 52 - (i + 1) * rowH;
                row.w = colW;
                row.h = rowH - 3;
                menuRows.add(row);
            }
        }
    }

    public void toggleDiagnostics() { diagnosticsOpen = !diagnosticsOpen; }

    public void toggleMap() {
        bigMapOpen = !bigMapOpen;
        if (bigMapOpen) {
            menuOpen = false;
            bagOpen = false;
        }
    }
    public boolean mapOpen() { return bigMapOpen; }

    /** Czy otwarte okno (ksiega, NPC, mapa...) zachodzi na prawa kolumne. */
    private boolean windowOverPanel() {
        return (menuOpen || bigMapOpen) && menuX + menuW > lay.right.x;
    }

    /** Czy otwarte okno zachodzi na lewa kolumne (minimapa, VIP, umiejetnosci). */
    private boolean windowOverLeft() {
        return (menuOpen || bigMapOpen) && menuX < lay.left.right();
    }

    private boolean minimapVisible() {
        return mmSize >= 56 && !windowOverLeft();
    }

    public void dispose() { minimap.dispose(); }
    public boolean diagnosticsOpen() { return diagnosticsOpen; }
    public void setSettings(pl.fawizja.pixel.settings.GameSettings settings) { this.settings = settings; }

    private static final float SETTINGS_ROW_H = 22f;
    /** Panel diagnostyki z ustawieniami: w prawym gornym rogu widoku gry (kolumny zostaja odsloniete). */
    private float diagTop() { return gy + gh - 4; }
    private float settingsRowY(int i) { return diagTop() - 220 - i * (SETTINGS_ROW_H + 4); }
    private float settingsRowX() { return gx + gw - 242; }
    public int selectedSlot() { return selectedSlot; }
    public void clearSelection() { selectedSlot = -1; }
    public void selectSlot(int slot) { selectedSlot = slot; }

    // ------------------------------------------------------------------ wejscie

    private Vector2 toUi(int sx, int sy) {
        return viewport.unproject(tmp.set(sx, sy));
    }

    /** Zwraca true, jesli dotyk zostal obsluzony przez interfejs. */
    public boolean touchDown(int sx, int sy, int pointer, GameSession session, Actions actions) {
        Vector2 p = toUi(sx, sy);
        if (houseWin != null) return houseTouch(p.x, p.y, actions);
        if (mobile) return mobileTouchDown(p.x, p.y, pointer, session, actions);
        if (charOpen && charWindowsTouch(p.x, p.y, session, actions)) return true;
        if (statusTouch(p.x, p.y, session)) return true;
        if (equipPanelTouch(p.x, p.y, session, actions)) return true;
        if (leftColumnTouch(p.x, p.y, actions)) return true;
        if (!bagOpen && consoleTouch(p.x, p.y, actions)) return true;
        if (!bagOpen && battleTouch(p.x, p.y, actions)) return true;
        if (lay.topBar.contains(p.x, p.y)) {
            // dotkniecie paskow HP/MP: karta postaci
            toggleCharacter();
            return true;
        }
        if (bigMapOpen) {
            bigMapTap(p.x, p.y, session, actions);
            return true;
        }
        if (minimapVisible() && p.x >= mmX - 3 && p.x <= mmX + mmSize + 3 && p.y >= mmY - 3 && p.y <= mmY + mmSize + 3) {
            toggleMap();
            return true;
        }
        if (diagnosticsOpen && settings != null) {
            String[][] rows = settings.rows();
            for (int i = 0; i < rows.length; i++) {
                float ry = settingsRowY(i);
                if (p.x >= settingsRowX() && p.x <= settingsRowX() + 238 && p.y >= ry && p.y <= ry + SETTINGS_ROW_H) {
                    actions.onButton(rows[i][0]);
                    return true;
                }
            }
        }
        if (menuOpen) {
            if (menuClose.hit(p.x, p.y)) {
                menuCloseAction(actions);
                return true;
            }
            if (menuRowTouch(p.x, p.y, session, actions)) return true;
            boolean inMenu = p.x >= menuX && p.x <= menuX + menuW && p.y >= menuY && p.y <= menuY + menuH;
            if (optionsMenu && !inMenu) {
                // dotkniecie obok malego menu opcji zamyka je i dziala dalej (np. inny przycisk)
                optionsMenu = false;
                menuOpen = false;
            }
            if (inMenu) return true;
        }
        if (bagOpen) {
            if (bagClose.hit(p.x, p.y)) { toggleBag(); return true; }
            if (selectedSlot >= 0 && bagEquip.hit(p.x, p.y)) { actions.onButton("bag_equip"); return true; }
            if (selectedSlot >= 0 && bagDrop.hit(p.x, p.y)) { actions.onButton("bag_drop"); return true; }
            if (selectedSlot >= 0 && bagUse.hit(p.x, p.y)) { actions.onButton("bag_use"); return true; }
            if (selectedSlot >= 0 && bagKeep.hit(p.x, p.y)) { actions.onButton("bag_keep"); return true; }
            if (selectedSlot < 0 && bagSort.hit(p.x, p.y)) { actions.onButton("bag_sort"); return true; }
            bagPages = session.character().bagPages();
            if (selectedSlot < 0 && bagPages > 1 && bagPage == bagPages - 1 && bagDetach.hit(p.x, p.y)) {
                actions.onButton("bag_detach");
                bagPage = Math.max(0, bagPage - 1);
                return true;
            }
            int tab = bagTabAt(p.x, p.y);
            if (tab >= 0) {
                bagPage = tab;
                selectedSlot = -1;
                return true;
            }
            int local = slotAt(p.x, p.y);
            int slot = local < 0 ? -1 : bagPage * PAGE_SLOTS + local;
            if (slot >= session.character().backpack().size()) slot = -1;
            if (slot >= 0) {
                if (selectedSlot < 0) {
                    if (session.character().backpack().get(slot) != null) {
                        selectedSlot = slot;
                        // poczatek mozliwego przeciagania (staje sie nim po przesunieciu palca)
                        dragSlot = slot;
                        dragPointer = pointer;
                        dragging = false;
                        dragStartX = dragX = p.x;
                        dragStartY = dragY = p.y;
                        dragActions = actions;
                        dragSession = session;
                    }
                } else if (selectedSlot == slot) {
                    selectedSlot = -1;
                } else {
                    actions.onButton("slot_move:" + selectedSlot + ":" + slot);
                    selectedSlot = -1;
                }
                return true;
            }
        }
        for (int i = 0; i < spellButtons.length; i++) {
            if (!spellButtons[i].hit(p.x, p.y)) continue;
            if (barEntries[i] == null) {
                openPicker(i);
            } else {
                pressSlot = i;
                pressPointer = pointer;
                pressStart = com.badlogic.gdx.utils.TimeUtils.millis();
                pressActions = actions;
            }
            return true;
        }
        for (HudButton b : buttons) {
            if (b.hit(p.x, p.y)) {
                if (b == diag && lay.foldLogout) {
                    openOptions();
                    return true;
                }
                actions.onButton(b.id);
                return true;
            }
        }
        if (bagOpen && p.x >= bagX - 8 && p.x <= bagX + slotSize * 5 + (bagPages > 1 ? 44 : 8) && p.y >= bagY - 50 && p.y <= bagY + slotSize * 4 + 30) {
            return true;
        }
        if (joystick.touchDown(p.x, p.y, pointer)) return true; // pad w rogu ekranu, takze nad dolnym paskiem
        return !inGameView(p.x, p.y); // panele interfejsu nie przepuszczaja dotyku na mape
    }

    /** Krzyzyk otwartego okna: zamkniecie (handel = rezygnacja). */
    private void menuCloseAction(Actions actions) {
        if (trade != null) actions.onButton("trade:cancel"); // zamkniecie okna = rezygnacja
        else if (optionsMenu) {
            optionsMenu = false;
            menuOpen = false;
        } else if (logoutConfirm) closeLogoutConfirm();
        else if (playerMenu != null) closePlayerMenu();
        else {
            menuOpen = false;
            rankingView = null;
            friendsView = null;
            marketView = null;
            achievementsView = false;
        }
    }

    /** Dotkniecie wiersza otwartego okna; true, gdy trafil w wiersz. */
    private boolean menuRowTouch(float px, float py, GameSession session, Actions actions) {
        rebuildMenu(session);
        for (MenuRow row : menuRows) {
            if (px >= row.x && px <= row.x + row.w && py >= row.y && py <= row.y + row.h) {
                if (row.action.equals("npc:qty")) {
                    int i = 0;
                    while (i < QUANTITIES.length && QUANTITIES[i] != npcQty) i++;
                    npcQty = QUANTITIES[(i + 1) % QUANTITIES.length];
                } else if (row.action.equals("npc:page")) {
                    npcPage++;
                } else if (row.action.equals("ach:toggle")) {
                    bestiaryMode = !bestiaryMode;
                } else if (row.action.equals("npc:depot")) {
                    depotView = !depotView;
                } else if (row.action.equals("npc:smith")) {
                    smithView = !smithView;
                } else if (row.action.equals("ach:page")) {
                    achPage++;
                } else if (row.action.equals("book:page")) {
                    bookPage++;
                } else if (row.action.equals("book:spells")) {
                    spellPage = spellPage + Math.max(1, (int) ((menuH - 40) / rowH) - 1); // nastepna strona, po koncu od poczatku
                } else if (row.action.equals("book:journal")) {
                    openJournal();
                } else if (row.action.startsWith("opt:")) {
                    optionsMenu = false;
                    menuOpen = false;
                    actions.onButton(row.action.substring(4));
                } else if (row.action.equals("logout:no")) {
                    closeLogoutConfirm();
                } else if (row.action.equals("player:none")) {
                    // wiersz tylko informuje
                } else if (row.action.startsWith("pick:")) {
                    pickRow(row.action.substring(5), session, actions);
                } else if (row.action.equals("book:assign")) {
                    assignSlot = assignSlot >= spellButtons.length - 1 ? -1 : assignSlot + 1;
                } else if (assignSlot >= 0 && row.action.startsWith("cast:")) {
                    customBar.assign(assignSlot, row.action);
                    updateBar(session);
                    actions.onButton("bar_saved");
                } else if (!row.action.equals("npc:none") && !row.action.equals("trade:none") && !row.action.equals("friend:none") && !row.action.equals("market:none") && !row.action.equals("ach:none")) {
                    actions.onButton(row.action);
                }
                return true;
            }
        }
        return false;
    }

    public boolean touchDragged(int sx, int sy, int pointer) {
        Vector2 p = toUi(sx, sy);
        if (mobile && pointer == mBattlePointer) {
            // przewijanie listy walki palcem
            int d = Math.round((p.y - mBattleDownY) / MobileHudLayout.BATTLE_ROW);
            if (d != 0) mBattleDragged = true;
            mBattleScroll = Math.max(0, mBattleScrollAtDown + d);
            return true;
        }
        if (dragSlot >= 0 && pointer == dragPointer) {
            dragX = p.x;
            dragY = p.y;
            if (!dragging && Math.abs(p.x - dragStartX) + Math.abs(p.y - dragStartY) > slotSize * 0.3f) dragging = true;
            return true;
        }
        return joystick.drag(p.x, p.y, pointer);
    }

    /** Upuszczenie przeciaganego przedmiotu: na pole (przeniesienie), zakladke (inny plecak) albo ekwipunek (zalozenie). */
    private boolean dropDragged(float x, float y) {
        int from = dragSlot;
        dragSlot = -1;
        dragPointer = -1;
        if (!dragging) return true; // zwykle dotkniecie - zaznaczenie zostaje
        dragging = false;
        int local = slotAt(x, y);
        int tab = bagTabAt(x, y);
        int to = -1;
        if (local >= 0) {
            to = bagPage * PAGE_SLOTS + local;
        } else if (tab >= 0 && dragSession != null) {
            pl.fawizja.pixel.inventory.Container bp = dragSession.character().backpack();
            for (int i = tab * PAGE_SLOTS; i < Math.min(bp.size(), (tab + 1) * PAGE_SLOTS) && to < 0; i++) if (bp.get(i) == null) to = i;
            bagPage = tab;
        } else if (inEquipPanel(x, y)) {
            selectedSlot = from;
            dragActions.onButton("bag_equip");
            return true;
        }
        if (to >= 0 && to != from && dragSession != null && to < dragSession.character().backpack().size()) {
            dragActions.onButton("slot_move:" + from + ":" + to);
        }
        selectedSlot = -1;
        return true;
    }

    public boolean touchUp(int sx, int sy, int pointer) {
        if (mobile && pointer == mBattlePointer) {
            mBattlePointer = -1;
            if (!mBattleDragged && mBattleActions != null) mobileBattleTap(mBattleDownY);
            return true;
        }
        if (dragSlot >= 0 && pointer == dragPointer) {
            Vector2 p = toUi(sx, sy);
            return dropDragged(p.x, p.y);
        }
        if (pressSlot >= 0 && pointer == pressPointer) {
            // krotkie dotkniecie pola: uzyj akcji (dlugie juz otworzylo wybor i wyczyscilo pressSlot)
            pressActions.onButton("spell:" + pressSlot);
            pressSlot = -1;
            return true;
        }
        return joystick.touchUp(pointer);
    }

    private int slotAt(float x, float y) {
        if (x < bagX || y < bagY) return -1;
        int col = (int) ((x - bagX) / slotSize);
        int row = (int) ((y - bagY) / slotSize);
        if (col < 0 || col >= 5 || row < 0 || row >= 4) return -1;
        return (3 - row) * 5 + col;
    }

    // ------------------------------------------------------------------ rysowanie

    public void render(SpriteBatch batch, ShapeRenderer shapes, GameSession session, WorldClock clock,
                       long serverTimeMs, DiagnosticsInfo diagInfo) {
        // Litery w calych pikselach ekranu, nie w calych jednostkach interfejsu: przy skali 2.625 zaokraglanie
        // pozycji kazdej litery do jednostki (2-3 px) rozrywalo napisy ("TAR CZA", "Pięt ro"). Przy skali 1
        // jednostka = piksel, wiec zostaje ostre zaokraglanie.
        boolean intPos = font.usesIntegerPositions();
        font.setUseIntegerPositions(uiScale == 1f);
        try {
            renderHud(batch, shapes, session, clock, serverTimeMs, diagInfo);
        } finally {
            font.setUseIntegerPositions(intPos);
        }
    }

    private void renderHud(SpriteBatch batch, ShapeRenderer shapes, GameSession session, WorldClock clock,
                           long serverTimeMs, DiagnosticsInfo diagInfo) {
        viewport.apply();
        if (trade != null && !bagOpen) menuOpen = true; // po zamknieciu plecaka wraca okno handlu
        if (menuOpen || bagOpen) bigMapOpen = false;
        PlayerCharacter pc = session.character();
        run.active = pc.isRunning();
        torch.active = pc.isTorchLit();
        bag.active = bagOpen;
        // plecak otwiera sie z gniazda w ramce ekwipunku (2026-10-23); przycisk tylko gdy ramka jest zakryta
        bag.visible = !equipPanelVisible();
        pick.visible = session.itemsAtFeet();
        diag.active = diagnosticsOpen;
        defensive.active = pc.isDefensive();
        pvp.active = pc.pvpMode();
        // czaszka jak w Tibii: biala = PvP wylaczone, czerwona = wlaczone
        TextureRegion skullBtn = pc.pvpMode() ? skullRed : skullWhite;
        if (skullBtn == null) skullBtn = pc.pvpMode() ? pvpOnIcon : pvpOffIcon;
        pvp.icon = skullBtn;
        quest.active = journalOpen();
        logout.active = logoutConfirmOpen();
        store.active = storeOpen();
        if (!menuOpen) {
            logoutConfirm = false;
            optionsMenu = false;
        }
        if (lay.foldLogout) diag.active = diagnosticsOpen || optionsOpen();
        book.active = menuOpen && !npcMode && pickSlot < 0 && trade == null && playerMenu == null && rankingView == null && friendsView == null && marketView == null && !achievementsView;
        if (playerMenu != null && !menuOpen) playerMenu = null;
        if (rankingView != null && !menuOpen) rankingView = null;
        if (friendsView != null && !menuOpen) friendsView = null;
        if (marketView != null && !menuOpen) marketView = null;
        if (achievementsView && !menuOpen) achievementsView = false;
        fish.visible = session.canFish();
        mount.visible = pc.hasMount();
        mount.active = pc.isMounted();
        pl.fawizja.pixel.npc.Npc near = session.npcNearby();
        talk.visible = near != null;
        talk.active = npcOpen();
        if (npcMode && trade == null && near != npc) {
            menuOpen = false;
            npcMode = false;
        }
        // pasek akcji (czary, mikstury, liczniki) co 150 ms, nie co klatke: przeglad czarow i plecaka kosztuje
        long barNow = com.badlogic.gdx.utils.TimeUtils.millis();
        if (barNow - barUpdatedMs >= BAR_REFRESH_MS || barNow < barUpdatedMs) {
            barUpdatedMs = barNow;
            updateBar(session);
        }
        if (pressSlot >= 0 && com.badlogic.gdx.utils.TimeUtils.millis() - pressStart >= (mobile ? MOBILE_LONG_PRESS_MS : LONG_PRESS_MS)) {
            openPicker(pressSlot);
            pressSlot = -1;
        }
        if (!menuOpen) {
            pickSlot = -1;
            pickCat = null;
        }
        Monster tgt = session.combat().target();
        currentTarget = tgt;
        target.active = tgt != null;
        if (visibleActionMask() != sideButtonsLaidOut) {
            if (mobile) layoutMobile();
            else layoutSide(); // np. ROZMOWA obok NPC
        }
        layoutConsole(session, serverTimeMs);
        target.icon = itemIcon(session, "iron_sword");
        defensive.icon = itemIcon(session, "wooden_shield");
        torch.icon = itemIcon(session, "torch");
        run.icon = itemIcon(session, "leather_boots");
        quest.icon = itemIcon(session, "return_scroll");
        if (infoIcon != null) diag.icon = infoIcon;
        fish.icon = itemIcon(session, "fishing_rod");
        mount.icon = itemIcon(session, "saddle");
        // podpisy przyciskow obok ekwipunku: rysowane, gdy mieszcza sie przy progu 7 dp (inaczej sama ikona)
        run.label = "Podąż";
        store.label = "Sklep";
        store.icon = itemIcon(session, "crystal_coin");
        defensive.label = "Tarcza";
        quest.label = "Zadania";
        torch.label = "Poch.";
        pvp.label = pc.pvpMode() ? "PvP!" : "PvP";
        diag.label = diag.icon == null ? "i" : "Opcje";
        logout.label = "Wyjdź";
        logout.labelBottom = true;
        bag.icon = art.backpack();
        buffs(session, serverTimeMs);
        if (mobile) {
            renderMobile(batch, shapes, session, clock, serverTimeMs, diagInfo, tgt);
            return;
        }

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        batch.setProjectionMatrix(viewport.getCamera().combined);
        uiBatch = batch;
        uiShapes = shapes;
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // tlo bryly: wszystko poza widokiem gry, ramka wokol widoku i ciemne linie oddzielajace kolumny
        shapes.setColor(PANEL_BG);
        shapes.rect(0, 0, w, gy - 2);
        shapes.rect(0, gy + gh + 2, w, h - gy - gh - 2);
        shapes.rect(0, gy - 2, gx - 2, gh + 4);
        shapes.rect(gx + gw + 2, gy - 2, w - gx - gw - 2, gh + 4);
        shapes.setColor(0.09f, 0.085f, 0.08f, 1f);
        shapes.rect(lay.left.right() - 1, 0, 1, h);
        shapes.rect(lay.right.x, 0, 1, h);
        shapes.setColor(BORDER);
        shapes.rect(gx - 2, gy - 2, gw + 4, 2);
        shapes.rect(gx - 2, gy + gh, gw + 4, 2);
        shapes.rect(gx - 2, gy, 2, gh);
        shapes.rect(gx + gw, gy, 2, gh);
        statusIcons(session, serverTimeMs);
        drawTopBarShapes(shapes, pc);
        drawStatusIcons(shapes, statusX(), statusY());
        if (battleVisible()) miniWindow(shapes, "grim", blX, blY, blW, blH, lay.header - 2);
        drawVipShapes(shapes);
        drawPanelSkillBars(shapes, pc);
        frame(shapes, lay.hotbar.x - 4, HudLayout.PAD, lay.hotbar.w + 8, lay.hotbar.h + 8);

        // joystick: sama kulka, prawie przezroczysta (wyrazniejsza w trakcie ruchu); na telefonie zwykle pod kciukiem
        float jrad = joystick.radius();
        if (joystick.floating()) {
            shapes.setColor(1, 1, 1, 0.08f); // podstawa joysticka pod kciukiem
            shapes.circle(joystick.baseX(), joystick.baseY(), jrad, 36);
        }
        shapes.setColor(1, 1, 1, joystick.active() ? 0.16f : 0.05f);
        shapes.circle(joystick.knob().x, joystick.knob().y, jrad * 0.45f, 30);


        for (HudButton b : buttons) {
            if (!b.visible) continue;
            Color fill = b.active ? ACTIVE : b == target ? ATTACK : FILL;
            if (b.hw > 0 && buttonPatch != null && slotImg != null) {
                continue; // pola i przyciski paska z obrazkow - nizej, w jednym przebiegu
            } else if (b.hw > 0) {
                shapes.setColor(BORDER);
                shapes.rect(b.x - b.hw - 2, b.y - b.r - 2, b.hw * 2 + 4, b.r * 2 + 4);
                shapes.setColor(fill);
                shapes.rect(b.x - b.hw, b.y - b.r, b.hw * 2, b.r * 2);
            } else {
                shapes.setColor(BORDER);
                shapes.circle(b.x, b.y, b.r, 32);
                shapes.setColor(fill);
                shapes.circle(b.x, b.y, b.r - Math.max(2f, b.r * 0.08f), 32);
            }
        }
        if (buttonPatch != null) {
            shapes.end();
            batch.begin();
            if (slotImg != null) for (HudButton b : slotButtons()) {
                if (!b.visible) continue;
                batch.draw(b.active && wstPressed != null ? wstPressed : slotImg, b.x - b.hw, b.y - b.r, b.hw * 2, b.r * 2);
                if (b.active) outline(batch, b.x - b.hw, b.y - b.r, b.hw * 2, b.r * 2,
                        b == target ? new Color(0.95f, 0.25f, 0.2f, 1f) : new Color(0.3f, 0.9f, 0.35f, 1f));
            }
            batch.end();
            resumeShapes(shapes);
        }
        for (int i = 0; i < spellButtons.length; i++) {
            HudButton b = spellButtons[i];
            if (!b.visible || barEntries[i] == null || !barEntries[i].startsWith("cast:")) continue;
            SpellType sp = session.spells().registry().get(barEntries[i].substring(5));
            float in = b.r - 3;
            shapes.setColor(spellColor(sp));
            shapes.rect(b.x - in, b.y - in, in * 2, in * 2);
            spellGlyph(shapes, sp.kind(), b.x, b.y + in * 0.2f, in * 0.55f);
            long rem = session.spells().cooldowns().remaining(sp, serverTimeMs);
            if (rem > 0) {
                // odnowienie jak w Tibii: ciemna zaslona opadajaca z gory
                float frac = Math.min(1f, rem / (float) Math.max(sp.cooldownMs(), sp.group().groupCooldownMs));
                shapes.setColor(0, 0, 0, 0.6f);
                shapes.rect(b.x - in, b.y + in - in * 2 * frac, in * 2, in * 2 * frac);
            }
            if (pc.mana() < sp.mana()) {
                shapes.setColor(0.1f, 0.1f, 0.4f, 0.55f);
                shapes.rect(b.x - in, b.y - in, in * 2, in * 2);
            }
        }
        // mikstury i runy: ta sama opadajaca zaslona odnowienia
        java.util.Arrays.fill(itemCd, 0f);
        for (int i = 0; i < spellButtons.length; i++) {
            HudButton b = spellButtons[i];
            String e = barEntries[i];
            if (!b.visible || e == null || e.startsWith("cast:")) continue;
            long rem;
            float full;
            SpellType rune = e.startsWith("item:") ? session.spells().registry().find(e.substring(5)) : null;
            if (rune != null) {
                rem = session.spells().cooldowns().remaining(rune, serverTimeMs);
                full = Math.max(rune.cooldownMs(), rune.group().groupCooldownMs);
            } else {
                rem = session.spells().cooldowns().groupRemaining(pl.fawizja.pixel.magic.SpellGroup.POTION, serverTimeMs);
                full = pl.fawizja.pixel.magic.SpellGroup.POTION.groupCooldownMs;
            }
            itemCd[i] = rem <= 0 ? 0f : Math.min(1f, rem / Math.max(1f, full)); // rysowane na ikonie, w przebiegu obrazkow
        }

        if (logout.visible) {
            // znak wylaczenia (kolo z kreska) nad podpisem WYJDZ
            float r = logout.r * 0.42f, cx = logout.x, cy = logout.y + logout.r * 0.18f;
            shapes.setColor(0.95f, 0.45f, 0.4f, 1f);
            shapes.arc(cx, cy, r, 125, 290, 24);
            shapes.setColor(0.13f, 0.11f, 0.1f, 1f);
            shapes.circle(cx, cy, r * 0.62f, 20);
            shapes.setColor(0.95f, 0.45f, 0.4f, 1f);
            shapes.rect(cx - r * 0.16f, cy, r * 0.32f, r * 1.15f);
        }
        if (pressSlot >= 0) {
            // przytrzymanie pola: zolta ramka rosnie, az otworzy sie wybor akcji
            HudButton b = spellButtons[pressSlot];
            float f = Math.min(1f, (com.badlogic.gdx.utils.TimeUtils.millis() - pressStart) / (float) LONG_PRESS_MS);
            shapes.setColor(1f, 0.85f, 0.25f, 0.35f + 0.4f * f);
            float bh = b.r * 2 * f;
            shapes.rect(b.x - b.hw, b.y - b.r, 3, bh);
            shapes.rect(b.x + b.hw - 3, b.y - b.r, 3, bh);
            shapes.rect(b.x - b.hw, b.y - b.r, b.hw * 2 * f, 3);
        }
        if (tgt != null) {
            float tw = Math.min(200, gw * 0.4f);
            float tx = targetBarX(tw);
            frame(shapes, tx - 6, gy + gh - 42, tw + 12, 38);
            bar(shapes, tx, gy + gh - 36, tw, 10, tgt.healthFraction(), new Color(0.85f, 0.15f, 0.2f, 1));
        }
        if (charOpen) {
            layoutCharWindows();
            miniWindow(shapes, "wood", eqX, eqY, eqW, eqH);
            miniWindow(shapes, "classic", skX, skY, skW, skH);
            float yy = skY + skH - 23 - SK_ROW * (SK_INFO_ROWS + 1);
            for (Skill sk : Skill.values()) {
                double prog = (double) pc.skills().tries(sk) / Math.max(1, Skills.triesForNext(pc.skills().level(sk)));
                bar(shapes, skX + 8, yy - SK_ROW + 1, skW - 16, 3, (float) prog, new Color(0.3f, 0.75f, 0.4f, 1));
                yy -= SK_ROW;
            }
        }
        if (consoleVisible()) {
            if (!wstPanel(shapes, "classic", conX, conY, conW, conH, 0)) frame(shapes, conX, conY, conW, conH);
            drawTabShapes(shapes);
        }
        if (bagOpen) drawBagBackground(shapes, session);
        if (menuOpen) {
            rebuildMenu(session);
            if (!wstPanel(shapes, menuStyle(), menuX, menuY, menuW, menuH, 24)) window(shapes, menuX, menuY, menuW, menuH, true);
            for (MenuRow row : menuRows) {
                shapes.setColor(row.enabled ? new Color(0.22f, 0.3f, 0.25f, 1f) : new Color(0.2f, 0.18f, 0.18f, 1f));
                shapes.rect(row.x, row.y, row.w, row.h);
            }
            shapes.setColor(0.6f, 0.15f, 0.15f, 0.95f);
            shapes.circle(menuClose.x, menuClose.y, menuClose.r, 20);
        }
        if (equipPanelVisible()) {
            if (epTibia) miniWindow(shapes, "wood", epX, epY, epW, epH, lay.eqHeader > 0 ? 21 : 0);
            else frame(shapes, epX, epY, epW, epH);
        }
        if (minimapVisible()) {
            shapes.setColor(BORDER);
            shapes.rect(mmX - 3, mmY - 3, mmSize + 6, mmSize + 6);
            shapes.setColor(0.05f, 0.05f, 0.07f, 1f);
            shapes.rect(mmX - 1, mmY - 1, mmSize + 2, mmSize + 2);
        }
        if (bigMapOpen) {
            if (!wstPanel(shapes, "stone", menuX, menuY, menuW, menuH, 24)) window(shapes, menuX, menuY, menuW, menuH, true);
            mapZoomShapes(shapes);
        }
        if (diagnosticsOpen) {
            shapes.setColor(0, 0, 0, 0.75f);
            shapes.rect(settingsRowX() - 4, diagTop() - 216, 238, 216);
            if (settings != null) {
                int n = settings.rows().length;
                for (int i = 0; i < n; i++) {
                    shapes.setColor(0.2f, 0.26f, 0.32f, 0.9f);
                    shapes.rect(settingsRowX(), settingsRowY(i), 238, SETTINGS_ROW_H);
                }
            }
        }
        if (houseWin != null) drawHouseShapes(shapes, session);
        shapes.end();

        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        drawBarOverlays(batch);
        font.setColor(Color.WHITE);
        String floor = pc.position().z() == 0 ? "0" : (pc.position().z() > 0 ? "+" + pc.position().z() : String.valueOf(pc.position().z()));
        drawTopBarText(batch, session, clock.minuteOfDay(serverTimeMs), floor);
        drawBattle(batch);
        drawPanelSkills(batch, pc);
        drawVip(batch);

        drawConsole(batch);
        drawStatus(batch);

        for (HudButton b : buttons) {
            if (!b.visible || underMenu(b.x, b.y)) continue;
            float cell = 2 * (b.hw > 0 ? b.hw : b.r), room = cell - 3;
            if (b.labelBottom) {
                // znak (czar, wyjscie) nad malym podpisem u dolu pola
                float sc = fitScale(b.label, room, 0.7f);
                font.getData().setScale(sc > 0 ? sc : HudLayout.MIN_FONT);
                String lb = sc > 0 ? b.label : clip(b.label, room);
                layout.setText(font, lb);
                font.draw(batch, lb, b.x - layout.width / 2f, b.y - b.r + 3 + layout.height);
            } else if (b.icon != null && b.id.startsWith("spell:")) {
                // pole paska z mikstura albo runa: duza ikona, liczba sztuk w prawym dolnym rogu
                float is = wstSlot != null ? b.r * 2f * 16f / 22f : b.r * 1.7f;
                batch.draw(b.icon, b.x - is / 2f, b.y - is / 2f, is, is);
                float cd = itemCd[Integer.parseInt(b.id.substring(6))];
                if (cd > 0) {
                    float in = b.r - 3;
                    batch.setColor(0, 0, 0, 0.6f);
                    batch.draw(art.white(), b.x - in, b.y + in - in * 2 * cd, in * 2, in * 2 * cd);
                    batch.setColor(Color.WHITE);
                }
                font.getData().setScale(Math.max(HudLayout.MIN_FONT, Math.min(0.7f, b.r / 28f)));
                layout.setText(font, b.label);
                text(batch, b.label, b.x + b.hw - 3 - layout.width, b.y - b.r + 3 + layout.height);
            } else if (b.icon != null) {
                float sc = b.label.isEmpty() ? 0 : fitScale(b.label, room, 0.7f);
                if (sc <= 0) {
                    // piktogram bez napisu (podpis nie miesci sie przy progu 7 dp): ikona we wnetrzu ramki
                    float is = wstSlot != null ? b.r * 2f * 16f / 22f : b.r * 1.5f;
                    batch.draw(b.icon, b.x - is / 2f, b.y - is / 2f, is, is);
                } else {
                    font.getData().setScale(sc);
                    layout.setText(font, b.label);
                    float lh = layout.height;
                    float base = b.y - b.r + 3;
                    float is = Math.min(b.r * 1.2f, cell - lh - 9);
                    batch.draw(b.icon, b.x - is / 2f, base + lh + 2 + (b.y + b.r - 2 - (base + lh + 2) - is) / 2f, is, is);
                    font.draw(batch, b.label, b.x - layout.width / 2f, base + lh);
                }
            } else {
                // pole z samym napisem (Użyj, Mów, "+" paska): napis na srodku, nie mniejszy niz prog 7 dp
                float sc = fitScale(b.label, room, b.id.startsWith("spell:") ? 0.8f : 0.75f);
                font.getData().setScale(sc > 0 ? sc : HudLayout.MIN_FONT);
                centered(batch, sc > 0 ? b.label : clip(b.label, room), b.x, b.y);
            }
            font.getData().setScale(1f);
        }
        if (tgt != null) {
            float tw = Math.min(200, gw * 0.4f);
            centered(batch, tgt.type().name() + " (Lv " + tgt.type().level() + ")", targetBarX(tw) + tw / 2f, gy + gh - 14);
        }
        if (charOpen) drawCharacterSheet(batch, session);
        if (bagOpen) drawBagContents(batch, session);
        if (menuOpen) {
            if (trade == null && optionsMenu) {
                text(batch, "Opcje", menuX + 10, menuY + menuH - 7);
            } else if (trade == null && logoutConfirm) {
                text(batch, "Wylogować się?", menuX + 10, menuY + menuH - 7);
                font.getData().setScale(0.8f);
                font.setColor(0.85f, 0.85f, 0.7f, 1f);
                text(batch, "Postać zostanie zapisana.", menuX + 10, menuY + menuH - 30);
                font.setColor(Color.WHITE);
                font.getData().setScale(1f);
            } else if (trade == null && playerMenu != null) {
                text(batch, playerMenu.name(), menuX + 10, menuY + menuH - 7);
                font.getData().setScale(0.8f);
                font.setColor(0.85f, 0.85f, 0.7f, 1f);
                String info = playerInfoLine(session);
                layout.setText(font, info);
                if (layout.width > menuW - 16) font.getData().setScale(Math.max(HudLayout.MIN_FONT, 0.8f * (menuW - 16) / layout.width));
                info = clip(info, menuW - 16);
                text(batch, info, menuX + 10, menuY + menuH - 30);
                font.setColor(Color.WHITE);
                font.getData().setScale(1f);
            } else if (trade == null && achievementsView && infoTitle != null && !journalMode) {
                text(batch, infoTitle, menuX + 10, menuY + menuH - 14);
            } else if (trade == null && achievementsView && journalMode) {
                text(batch, "Dziennik zadań - ukończone: " + session.character().quests().done().size()
                        + " (dotknij zadania, aby iść do zleceniodawcy)", menuX + 10, menuY + menuH - 14);
            } else if (trade == null && achievementsView) {
                PlayerCharacter me = session.character();
                text(batch, bestiaryMode ? "Bestiariusz - " + me.name() + (monsterSpecies > 0
                                ? " (poznane gatunki: " + me.bestiary().species() + " / " + monsterSpecies + ")" : "")
                        : "Osiągnięcia " + me.achievements().count() + " / " + pl.fawizja.pixel.achievement.Achievement.values().length,
                        menuX + 10, menuY + menuH - 14);
            } else if (trade == null && pickSlot >= 0) {
                text(batch, "Pole " + (pickSlot + 1) + " paska: " + (pickCat == null ? "co ma robić?" : pickTitle(pickCat)),
                        menuX + 10, menuY + menuH - 14);
            } else if (trade == null && marketView != null) {
                text(batch, "Rynek graczy (" + marketView.total() + " ofert)", menuX + 10, menuY + menuH - 14);
            } else if (trade == null && friendsView != null) {
                text(batch, "Znajomi (" + friendsView.rows().size() + " z " + pl.fawizja.pixel.friends.FriendList.MAX + ")", menuX + 10, menuY + menuH - 14);
            } else if (trade == null && rankingView != null) {
                text(batch, rankingView.title() + " (wg doświadczenia, " + rankingView.total() + " postaci)", menuX + 10, menuY + menuH - 14);
            } else if (trade != null) {
                text(batch, "Handel z " + trade.partner(), menuX + 10, menuY + menuH - 14);
                text(batch, "Twój plecak (dotknij, aby dodać do oferty)", menuX + 20 + (menuW - 30) / 2f, menuY + menuH - 14);
            } else if (npcMode) {
                text(batch, npc.name() + " (" + npc.town() + ")", menuX + 10, menuY + menuH - 14);
                font.getData().setScale(0.85f);
                font.setColor(0.85f, 0.85f, 0.7f, 1f);
                text(batch, npc.greeting(), menuX + 10, menuY + menuH - 32);
                font.setColor(Color.WHITE);
                font.getData().setScale(1f);
            } else {
                text(batch, "Czary (dotknij, aby rzucić)", menuX + 10, menuY + menuH - 14);
                text(batch, "Rzemiosło (dotknij, aby wytworzyć)", menuX + 20 + (menuW - 30) / 2f, menuY + menuH - 14);
            }
            centered(batch, "X", menuClose.x, menuClose.y);
            for (MenuRow row : menuRows) {
                font.setColor(row.enabled ? Color.WHITE : Color.GRAY);
                // wiersz bez opisu (okienko gracza, opcje): napis na srodku wysokosci
                if (row.detail.isEmpty()) text(batch, clip(row.label, row.w - 8), row.x + 6, row.y + (row.h + font.getCapHeight()) / 2f);
                else text(batch, row.label, row.x + 4, row.y + row.h - 3);
                font.getData().setScale(0.8f);
                font.setColor(row.enabled ? new Color(0.8f, 0.85f, 0.7f, 1f) : Color.GRAY);
                text(batch, row.detail, row.x + 4, row.y + row.h * 0.45f);
                font.getData().setScale(1f);
            }
            font.setColor(Color.WHITE);
        }
        if (session.chests() != null) minimap.setHazards(session.chests().traps());
        if (equipPanelVisible()) drawEquipPanel(batch, session);
        if (minimapVisible()) minimap.drawAround(batch, session.world(), pc.position(), walkTarget, markerPositions(session), mmX, mmY, mmSize, MINIMAP_TILES);
        if (bigMapOpen) drawBigMap(batch, session, floor);
        if (diagnosticsOpen && diagInfo != null) {
            float dy = diagTop() - 10;
            for (String line : diagInfo.lines()) {
                text(batch, line, settingsRowX() + 2, dy);
                dy -= 16;
            }
            if (settings != null) {
                String[][] rows = settings.rows();
                font.getData().setScale(0.85f);
                for (int i = 0; i < rows.length; i++) text(batch, rows[i][1], settingsRowX() + 6, settingsRowY(i) + 16);
                font.getData().setScale(1f);
            }
        }
        if (houseWin != null) drawHouseText(batch);
        batch.end();
    }

    private static final Color BORDER = new Color(0.5f, 0.44f, 0.32f, 0.85f);
    /** Tlo paneli bryly (prawy panel, paski nad i pod widokiem gry). */
    private static final Color PANEL_BG = new Color(0.17f, 0.16f, 0.15f, 1f);
    private static final Color FILL = new Color(0.1f, 0.09f, 0.08f, 0.72f);
    private static final Color ACTIVE = new Color(0.15f, 0.6f, 0.25f, 0.88f);
    private static final Color ATTACK = new Color(0.4f, 0.1f, 0.07f, 0.8f);

    private TextureRegion itemIcon(GameSession session, String key) {
        try {
            return art.item(session.items().byKey(key).id());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Ciemne okienko z jasniejsza ramka (styl klienta mobilnego). */
    private void frame(ShapeRenderer shapes, float fx, float fy, float fw, float fh) {
        if (panelPatch != null && uiBatch != null) {
            patch(shapes, panelPatch, fx, fy, fw, fh, 0.92f);
            return;
        }
        shapes.setColor(BORDER);
        shapes.rect(fx, fy, fw, fh);
        shapes.setColor(0.07f, 0.065f, 0.06f, 0.78f);
        shapes.rect(fx + 2, fy + 2, fw - 4, fh - 4);
    }

    // ------------------------------------------------------------------ ikony stanu (jak w Tibii pod paskami)

    private enum Status {
        PZ("Strefa ochronna: odpoczynek, szybsza regeneracja", new Color(0.2f, 0.55f, 0.25f, 1f)),
        BATTLE("Walka: nie wejdziesz do strefy ochronnej", new Color(0.7f, 0.15f, 0.12f, 1f)),
        FED("Najedzony: szybsza regeneracja", new Color(0.55f, 0.4f, 0.2f, 1f)),
        HUNGRY("Głodny: zjedz coś, aby szybciej się regenerować", new Color(0.5f, 0.45f, 0.4f, 1f)),
        BLESSED("Błogosławieństwo: mniejsze straty po śmierci", new Color(0.75f, 0.62f, 0.2f, 1f)),
        RUNNING("Podążam za celem", new Color(0.2f, 0.4f, 0.7f, 1f)),
        HASTE("Przyspieszenie: szybkość zwiększona przez czar", new Color(0.15f, 0.55f, 0.6f, 1f)),
        LIGHT("Światło: pochodnia albo czar", new Color(0.7f, 0.6f, 0.15f, 1f)),
        ENCHANT("Zaklęcie broni od kowala aktywne", new Color(0.45f, 0.25f, 0.7f, 1f)),
        PREMIUM("Konto premium: rejsy statkami między portami", new Color(0.55f, 0.2f, 0.45f, 1f)),
        PROMOTED("Promocja od króla: szybsza regeneracja", new Color(0.62f, 0.48f, 0.08f, 1f));

        final String desc;
        final Color color;

        Status(String desc, Color color) {
            this.desc = desc;
            this.color = color;
        }
    }

    private static final float STATUS_ROW = 18;
    private static final float STATUS_SIZE = 14;
    private final List<Status> statuses = new ArrayList<>();

    private void statusIcons(GameSession session, long nowMs) {
        PlayerCharacter pc = session.character();
        statuses.clear();
        if (session.isResting(nowMs)) statuses.add(Status.PZ);
        if (nowMs < session.protectionZoneLockedUntil()) statuses.add(Status.BATTLE);
        // wzmocnienia (przyspieszenie, swiatlo, mikstury, jedzenie, blogoslawienstwo) sa w polu obok butow
        if (pc.fedMs() <= 0) statuses.add(Status.HUNGRY);
        if (pc.isPremium()) statuses.add(Status.PREMIUM);
        if (pc.isPromoted()) statuses.add(Status.PROMOTED);
        if (pc.isRunning()) statuses.add(Status.RUNNING);
    }

    // ------------------------------------------------------------------ gorny pasek

    /** Paski HP i MP na calej szerokosci gornego paska, pod nimi pasek postepu poziomu (w przebiegu ksztaltow). */
    private void drawTopBarShapes(ShapeRenderer shapes, PlayerCharacter pc) {
        HudLayout.Rect t = lay.topBar;
        float bh = topBarH();
        float half = (t.w - 4 - 6) / 2f;
        float y = topBarRowY();
        bar(shapes, t.x + 2, y, half, bh, pc.health() / (float) Math.max(1, pc.maxHealth()), new Color(0.85f, 0.15f, 0.2f, 1));
        bar(shapes, t.x + 2 + half + 6, y, half, bh, pc.mana() / (float) Math.max(1, pc.maxMana()), new Color(0.2f, 0.35f, 0.95f, 1));
        float[] ex = expBar(pc);
        bar(shapes, ex[0], statusY() + (STATUS_SIZE - 5) / 2f, ex[1], 5, (float) pc.levelProgress(), new Color(0.95f, 0.8f, 0.2f, 1));
    }

    private float topBarH() { return lay.compact ? 11 : 13; }
    private float topBarRowY() { return lay.topBar.top() - (lay.compact ? 2 : 3) - topBarH(); }

    /** Pasek postepu poziomu w drugim wierszu: {x, szerokosc} (za napisem "Lv N"). */
    private float[] expBar(PlayerCharacter pc) {
        font.getData().setScale(0.8f);
        layout.setText(font, "Lv " + pc.level());
        float x = lay.topBar.x + 4 + layout.width + 6;
        font.getData().setScale(1f);
        return new float[] {x, Math.min(160, lay.topBar.w * 0.22f)};
    }

    /** Liczby na paskach, poziom, procent do poziomu, godzina, pietro i zloto (gdy sie mieszcza). */
    private void drawTopBarText(SpriteBatch batch, GameSession session, int minute, String floor) {
        PlayerCharacter pc = session.character();
        HudLayout.Rect t = lay.topBar;
        float half = (t.w - 4 - 6) / 2f;
        float y = topBarRowY() + topBarH() / 2f;
        font.getData().setScale(lay.compact ? 0.72f : 0.8f);
        centered(batch, pc.health() + " / " + pc.maxHealth(), t.x + 2 + half / 2f, y);
        centered(batch, pc.mana() + " / " + pc.maxMana(), t.x + 2 + half * 1.5f + 6, y);
        float[] ex = expBar(pc);
        font.getData().setScale(0.8f);
        float ty = statusY() + STATUS_SIZE / 2f;
        layout.setText(font, "Lv " + pc.level());
        font.setColor(1f, 0.9f, 0.55f, 1f);
        font.draw(batch, "Lv " + pc.level(), t.x + 4, ty + layout.height / 2f);
        font.setColor(Color.WHITE);
        String pct = (int) (pc.levelProgress() * 100) + "%";
        float x = ex[0] + ex[1] + 5;
        layout.setText(font, pct);
        font.draw(batch, pct, x, ty + layout.height / 2f);
        x += layout.width + 14;
        String info = String.format("%02d:%02d   Piętro %s   %,d gp", minute / 60, minute % 60, floor, session.gold());
        layout.setText(font, info);
        if (x + layout.width >= statusX() - 6) {
            info = String.format("%,d gp", session.gold()); // waski pasek (telefon): samo zloto
            layout.setText(font, info);
        }
        if (x + layout.width < statusX() - 6) font.draw(batch, info, x, ty + layout.height / 2f);
        font.getData().setScale(1f);
    }

    // ------------------------------------------------------------------ wzmocnienia i czaszka w polu obok butow

    /** Wzmocnienie w polu ekwipunku: znak stanu (rysowany ksztaltami) albo ikona przedmiotu, opis po dotknieciu. */
    private record Buff(Status glyph, TextureRegion icon, String desc) {}
    private final List<Buff> buffs = new ArrayList<>();
    private pl.fawizja.pixel.pvp.PvpRules.Skull ownSkull = pl.fawizja.pixel.pvp.PvpRules.Skull.NONE;
    private long ownSkullMs;

    private static String minutes(long ms) { return Math.max(1, (ms + 59_999) / 60_000) + " min"; }

    /** Aktywne wzmocnienia i wlasna czaszka (co klatke, przed rysowaniem). */
    private void buffs(GameSession session, long nowMs) {
        PlayerCharacter pc = session.character();
        buffs.clear();
        ownSkull = pc.skull(nowMs);
        ownSkullMs = Math.max(pc.skullMs(nowMs), pc.pzBlockedMs(nowMs));
        if (pc.hasteMs() > 0) buffs.add(new Buff(Status.HASTE, null, "Przyspieszenie: szybszy krok (" + minutes(pc.hasteMs()) + ")"));
        for (pl.fawizja.pixel.item.Enchantments.Kind k : pl.fawizja.pixel.item.Enchantments.Kind.values()) {
            if (!pc.enchantments().active(k)) continue;
            TextureRegion icon = k.potion() != null ? itemIcon(session, k.potion())
                    : k == pl.fawizja.pixel.item.Enchantments.Kind.EXP ? itemIcon(session, "exp_scroll") : null;
            buffs.add(new Buff(Status.ENCHANT, icon, k.label() + ": " + k.effect() + " (" + minutes(pc.enchantments().remainingMs(k)) + ")"));
        }
        if (pc.lightBuffMs() > 0) buffs.add(new Buff(Status.LIGHT, null, "Światło z czaru (" + minutes(pc.lightBuffMs()) + ")"));
        else if (pc.isTorchLit()) buffs.add(new Buff(Status.LIGHT, itemIcon(session, "torch"), "Światło: pochodnia"));
        if (pc.fedMs() > 0) buffs.add(new Buff(Status.FED, itemIcon(session, "bread"), "Najedzony: szybsza regeneracja (" + minutes(pc.fedMs()) + ")"));
        if (pc.isBlessed()) buffs.add(new Buff(Status.BLESSED, null, Status.BLESSED.desc));
    }

    /** Pole na lewo od butow (wiersz 4, kolumna 1 lalki): {x, y}. */
    private float[] buffCellPos() {
        return new float[] {epX + epPad(), epY + epH - lay.eqHeader - 4 - epCell - 3 * (epCell + 3)};
    }

    /** Czas czaszki albo blokady PZ, np. "0:58" albo "9 min". */
    private static String skullTime(long ms) {
        long sec = (ms + 999) / 1000;
        if (sec >= 2 * 86_400) return (sec + 86_399) / 86_400 + " d"; // czerwona 30 dni, czarna 45 dni
        if (sec >= 2 * 3_600) return (sec + 3_599) / 3_600 + " h";
        return sec >= 600 ? (sec + 59) / 60 + " min" : (sec / 60) + ":" + String.format("%02d", sec % 60);
    }

    /**
     * Pole wzmocnien: rama jak pozostale pola, w srodku do 4 malych ikon (2 x 2); z czaszka - czaszka i czas
     * w gornym wierszu, wzmocnienia w dolnym. Rysowane w przebiegu batcha (znaki stanu krotka wstawka ksztaltow).
     */
    private void drawBuffCell(SpriteBatch batch) {
        float[] c = buffCellPos();
        float cell = epCell, x0 = c[0], y0 = c[1];
        if (slotImg != null) batch.draw(wstEmpty != null ? wstEmpty : slotImg, x0, y0, cell, cell);
        boolean skull = ownSkull != pl.fawizja.pixel.pvp.PvpRules.Skull.NONE || ownSkullMs > 0;
        float m = (cell - 4) / 2f;
        float[][] spots = {{x0 + 2, y0 + 2 + m}, {x0 + 2 + m, y0 + 2 + m}, {x0 + 2, y0 + 2}, {x0 + 2 + m, y0 + 2}};
        int first = skull ? 2 : 0;
        int n = Math.min(buffs.size(), spots.length - first);
        if (n == 0 && !skull) return;
        // znaki stanu (bez ikony przedmiotu): krotka wstawka ksztaltow
        boolean glyphs = false;
        for (int i = 0; i < n; i++) glyphs |= buffs.get(i).icon() == null;
        if (glyphs && uiShapes != null) {
            batch.end();
            resumeShapes(uiShapes);
            for (int i = 0; i < n; i++) {
                Buff b = buffs.get(i);
                if (b.icon() == null) drawStatusGlyph(uiShapes, b.glyph(), spots[first + i][0], spots[first + i][1], m);
            }
            uiShapes.end();
            batch.begin();
        }
        for (int i = 0; i < n; i++) {
            Buff b = buffs.get(i);
            if (b.icon() == null) continue;
            batch.setColor(0f, 0f, 0f, 0.55f);
            batch.draw(art.white(), spots[first + i][0], spots[first + i][1], m, m);
            batch.setColor(Color.WHITE);
            batch.draw(b.icon(), spots[first + i][0], spots[first + i][1], m, m);
        }
        if (skull) {
            TextureRegion icon = skullIcon(ownSkull.name());
            // czas krotko ("0:58", "9m", "30d"), przy progu 7 dp; czaszka mniejsza, gdy czas jest szeroki
            String time = skullTime(ownSkullMs).replace(" min", "m").replace(" h", "h").replace(" d", "d");
            font.getData().setScale(HudLayout.MIN_FONT);
            layout.setText(font, time);
            float is = icon == null ? 0 : Math.max(m * 0.6f, Math.min(m, cell - 5 - layout.width));
            if (icon != null) batch.draw(icon, x0 + 2, y0 + 2 + m + (m - is) / 2f, is, is);
            float room = cell - 4 - is;
            if (layout.width > room) {
                time = clip(time, room);
                layout.setText(font, time);
            }
            font.setColor(ownSkull == pl.fawizja.pixel.pvp.PvpRules.Skull.RED || ownSkull == pl.fawizja.pixel.pvp.PvpRules.Skull.BLACK
                    ? new Color(1f, 0.45f, 0.4f, 1f) : Color.WHITE);
            float tx = icon != null ? x0 + 2 + is + (room - layout.width) / 2f : x0 + (cell - layout.width) / 2f;
            font.draw(batch, time, tx, y0 + 2 + m + m / 2f + layout.height / 2f);
            font.setColor(Color.WHITE);
            font.getData().setScale(1f);
        }
    }

    /** Dotkniecie pola wzmocnien: opis kazdego (i czaszki) w konsoli. */
    private boolean buffCellTouch(float px, float py, GameSession session) {
        float[] c = buffCellPos();
        if (px < c[0] || px > c[0] + epCell || py < c[1] || py > c[1] + epCell) return false;
        long now = session.lastUpdateMs();
        if (ownSkull != pl.fawizja.pixel.pvp.PvpRules.Skull.NONE) {
            session.log().add(now, EventLog.Kind.INFO, skullName(ownSkull) + ": jeszcze " + skullTime(ownSkullMs) + ".");
        } else if (ownSkullMs > 0) {
            session.log().add(now, EventLog.Kind.INFO, "Blokada strefy ochronnej po walce: jeszcze " + skullTime(ownSkullMs) + ".");
        }
        for (Buff b : buffs) session.log().add(now, EventLog.Kind.INFO, b.desc());
        if (buffs.isEmpty() && ownSkullMs <= 0) session.log().add(now, EventLog.Kind.INFO, "Brak aktywnych wzmocnień.");
        return true;
    }


    /** Ikony stanu wyrownane do prawej krawedzi gornego paska. */
    private float statusX() { return lay.topBar.right() - 2 - statuses.size() * (STATUS_SIZE + 3); }

    /** Dolny wiersz gornego paska (ikony stanu, poziom, informacje). */
    private float statusY() { return lay.topBar.y + 2; }

    private void drawStatusIcons(ShapeRenderer s, float x0, float y0) {
        for (int i = 0; i < statuses.size(); i++) drawStatusGlyph(s, statuses.get(i), x0 + i * (STATUS_SIZE + 3), y0, STATUS_SIZE);
    }

    /** Ikona stanu: kolorowy kwadrat ze znakiem (rysunek dla 14 jednostek, przy mniejszym polu wysrodkowany). */
    private static void drawStatusGlyph(ShapeRenderer s, Status st, float x, float y0, float sz) {
        {
            float cx = x + sz / 2f, cy = y0 + sz / 2f;
            s.setColor(0, 0, 0, 0.8f);
            s.rect(x - 1, y0 - 1, sz + 2, sz + 2);
            s.setColor(st.color);
            s.rect(x, y0, sz, sz);
            s.setColor(1, 1, 1, 0.9f);
            switch (st) {
                case PZ: // tarcza
                    s.rect(cx - 4, cy - 1, 8, 5);
                    s.triangle(cx - 4, cy - 1, cx + 4, cy - 1, cx, cy - 6);
                    break;
                case BATTLE: // skrzyzowane miecze
                    s.rectLine(cx - 5, cy - 5, cx + 5, cy + 5, 1.6f);
                    s.rectLine(cx - 5, cy + 5, cx + 5, cy - 5, 1.6f);
                    break;
                case FED: // bochenek
                    s.ellipse(cx - 5, cy - 3, 10, 6);
                    break;
                case HUNGRY: // pusty talerz
                    s.circle(cx, cy, 5, 12);
                    s.setColor(st.color);
                    s.circle(cx, cy, 3.4f, 12);
                    break;
                case BLESSED: // gwiazda (romb)
                    s.triangle(cx, cy + 5, cx - 4, cy, cx + 4, cy);
                    s.triangle(cx, cy - 5, cx - 4, cy, cx + 4, cy);
                    break;
                case RUNNING: // >>
                    s.triangle(cx - 5, cy + 4, cx - 5, cy - 4, cx, cy);
                    s.triangle(cx, cy + 4, cx, cy - 4, cx + 5, cy);
                    break;
                case HASTE: // grot w prawo i linie ruchu
                    s.triangle(cx - 1, cy + 5, cx - 1, cy - 5, cx + 5, cy);
                    s.rect(cx - 6, cy + 2, 4, 1.5f);
                    s.rect(cx - 7, cy - 0.75f, 5, 1.5f);
                    s.rect(cx - 6, cy - 3.5f, 4, 1.5f);
                    break;
                case LIGHT: // plomien
                    s.triangle(cx - 3, cy - 3, cx + 3, cy - 3, cx, cy + 5);
                    s.circle(cx, cy - 3, 3, 10);
                    break;
                case PREMIUM: // korona
                    s.rect(cx - 5, cy - 4, 10, 3);
                    s.triangle(cx - 5, cy - 1, cx - 2, cy - 1, cx - 5, cy + 4);
                    s.triangle(cx - 2, cy - 1, cx + 2, cy - 1, cx, cy + 5);
                    s.triangle(cx + 2, cy - 1, cx + 5, cy - 1, cx + 5, cy + 4);
                    break;
                case PROMOTED: // gwiazda promocji
                    star(s, cx, cy, 6f);
                    break;
                default: // blysk zaklecia
                    s.rectLine(cx - 4, cy - 5, cx + 1, cy + 1, 1.6f);
                    s.rectLine(cx - 1, cy - 1, cx + 4, cy + 5, 1.6f);
            }
        }
    }

    /** Piecioramienna gwiazda (znak promocji) z trojkatow: srodek i piec ramion. */
    public static void star(ShapeRenderer s, float cx, float cy, float r) {
        float inner = r * 0.42f;
        for (int k = 0; k < 5; k++) {
            double a = Math.PI / 2 + k * 2 * Math.PI / 5;
            double l = a + Math.PI / 5, rr = a - Math.PI / 5;
            float tx = cx + (float) Math.cos(a) * r, ty = cy + (float) Math.sin(a) * r;
            float lx = cx + (float) Math.cos(l) * inner, ly = cy + (float) Math.sin(l) * inner;
            float rx = cx + (float) Math.cos(rr) * inner, ry = cy + (float) Math.sin(rr) * inner;
            s.triangle(tx, ty, lx, ly, rx, ry);
            s.triangle(cx, cy, lx, ly, rx, ry);
        }
    }

    /** Dotkniecie ikon stanu: opis kazdej w konsoli. */
    private boolean statusTouch(float px, float py, GameSession session) {
        // pole dotyku: cala wysokosc gornego paska (32 dp na telefonie) i co najmniej 36 dp szerokosci
        float x0 = Math.min(statusX() - 4, lay.topBar.right() - 36);
        if (statuses.isEmpty() || py < lay.topBar.y - HudLayout.BORDER || py > lay.topBar.top() || px < x0 || px > lay.topBar.right() + HudLayout.PAD) return false;
        for (Status st : statuses) session.log().add(session.lastUpdateMs(), EventLog.Kind.INFO, st.desc);
        return true;
    }

    /** Tlo pola czaru wg zywiolu, jak kolorowe ikony czarow w Tibii. */
    private static Color spellColor(SpellType sp) {
        if (sp.kind().isHeal()) return new Color(0.2f, 0.55f, 0.35f, 0.85f);
        if (sp.kind() == SpellKind.LIGHT) return new Color(0.6f, 0.52f, 0.15f, 0.85f);
        if (sp.kind() == SpellKind.HASTE) return new Color(0.15f, 0.5f, 0.55f, 0.85f);
        switch (sp.damageType()) {
            case FIRE: return new Color(0.7f, 0.25f, 0.1f, 0.85f);
            case ICE: return new Color(0.25f, 0.5f, 0.75f, 0.85f);
            case EARTH: return new Color(0.3f, 0.5f, 0.15f, 0.85f);
            case ENERGY: return new Color(0.45f, 0.25f, 0.7f, 0.85f);
            case HOLY: return new Color(0.75f, 0.65f, 0.25f, 0.85f);
            case DEATH: return new Color(0.3f, 0.2f, 0.3f, 0.85f);
            default: return new Color(0.45f, 0.35f, 0.25f, 0.85f);
        }
    }

    /** Prosty znak rodzaju czaru: krzyz (leczenie), pocisk (cel), wybuch (obszar), slonce (swiatlo), runa (tworzenie). */
    private static void spellGlyph(ShapeRenderer s, SpellKind kind, float cx, float cy, float r) {
        s.setColor(1f, 1f, 1f, 0.85f);
        float t = Math.max(2f, r * 0.28f);
        switch (kind) {
            case HEAL_SELF:
            case RUNE_HEAL:
                s.rect(cx - t / 2f, cy - r, t, r * 2);
                s.rect(cx - r, cy - t / 2f, r * 2, t);
                break;
            case ATTACK_TARGET:
            case RUNE_TARGET:
                s.rectLine(cx - r, cy - r, cx + r * 0.4f, cy + r * 0.4f, t);
                s.triangle(cx + r, cy + r, cx + r * 0.05f, cy + r * 0.7f, cx + r * 0.7f, cy + r * 0.05f);
                break;
            case ATTACK_AREA:
            case RUNE_AREA:
                s.circle(cx, cy, r * 0.45f, 16);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    s.rectLine(cx + (float) Math.cos(a) * r * 0.6f, cy + (float) Math.sin(a) * r * 0.6f,
                            cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, t * 0.7f);
                }
                break;
            case HASTE: // dwie strzalki do przodu
                s.triangle(cx - r, cy + r * 0.7f, cx - r, cy - r * 0.7f, cx, cy);
                s.triangle(cx, cy + r * 0.7f, cx, cy - r * 0.7f, cx + r, cy);
                break;
            case LIGHT:
                s.circle(cx, cy, r * 0.55f, 16);
                for (int i = 0; i < 4; i++) {
                    double a = Math.PI / 4 + Math.PI / 2 * i;
                    s.rectLine(cx + (float) Math.cos(a) * r * 0.7f, cy + (float) Math.sin(a) * r * 0.7f,
                            cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, t * 0.7f);
                }
                break;
            default:
                s.rect(cx - r * 0.6f, cy - r * 0.8f, r * 1.2f, r * 1.6f);
                s.setColor(0, 0, 0, 0.4f);
                s.rect(cx - r * 0.3f, cy - r * 0.4f, r * 0.6f, r * 0.8f);
        }
    }

    /** Wznawia ShapeRenderer po wstawce batcha (batch.end() wylacza mieszanie - polprzezroczystosc znika). */
    private static void resumeShapes(ShapeRenderer shapes) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
    }

    /** Rysuje 9-patch w trakcie przebiegu ShapeRenderera (przerywa go na chwile). */
    private void patch(ShapeRenderer shapes, com.badlogic.gdx.graphics.g2d.NinePatch np, float fx, float fy, float fw, float fh, float alpha) {
        shapes.end();
        uiBatch.begin();
        drawBarOverlays(uiBatch);
        uiBatch.setColor(1, 1, 1, alpha);
        np.draw(uiBatch, fx, fy, fw, fh);
        uiBatch.setColor(Color.WHITE);
        uiBatch.end();
        resumeShapes(shapes);
    }

    /** Male okno z naglowkiem 23 px (plecak, ekwipunek, umiejetnosci, konsola). */
    private void miniWindow(ShapeRenderer shapes, String style, float fx, float fy, float fw, float fh) {
        miniWindow(shapes, style, fx, fy, fw, fh, 21);
    }

    /** Male okno z paskiem tytulu wysokosci titleH (0 = bez paska, np. ekwipunek na telefonie). */
    private void miniWindow(ShapeRenderer shapes, String style, float fx, float fy, float fw, float fh, float titleH) {
        if (wstPanel(shapes, style, fx, fy, fw, fh, titleH)) return;
        if (miniPatch != null && uiBatch != null) patch(shapes, miniPatch, fx, fy, fw, fh, 0.96f);
        else window(shapes, fx, fy, fw, fh, true);
    }

    /** Ramka 2 px w batchu (zaznaczenie pola). */
    private void outline(SpriteBatch batch, float ox, float oy, float ow, float oh, Color c) {
        batch.setColor(c);
        TextureRegion px = art.white();
        batch.draw(px, ox, oy, ow, 2);
        batch.draw(px, ox, oy + oh - 2, ow, 2);
        batch.draw(px, ox, oy, 2, oh);
        batch.draw(px, ox + ow - 2, oy, 2, oh);
        batch.setColor(Color.WHITE);
    }

    /** Przycisk zamkniecia okna (obrazek z OTClientV8 albo tekst X). */
    private void closeButton(SpriteBatch batch, float cx, float cy) {
        if (closeImg != null) batch.draw(closeImg, cx - 7, cy - 7, 14, 14);
        else centered(batch, "X", cx, cy);
    }

    /** Okno w stylu klienta mobilnego: jasna ramka, ciemne wnetrze i (opcjonalnie) pasek tytulu u gory. */
    private void window(ShapeRenderer shapes, float fx, float fy, float fw, float fh, boolean titleBar) {
        if (windowPatch != null && uiBatch != null) {
            // ramka okna z OTClientV8 (naglowek 27 px u gory)
            shapes.end();
            uiBatch.begin();
            drawBarOverlays(uiBatch); // paski pod oknem dostaja nakladke przed zasloneciem
            uiBatch.setColor(1, 1, 1, 0.97f);
            windowPatch.draw(uiBatch, fx, fy, fw, fh);
            uiBatch.setColor(Color.WHITE);
            uiBatch.end();
            resumeShapes(shapes);
            return;
        }
        shapes.setColor(0.02f, 0.02f, 0.02f, 0.9f);
        shapes.rect(fx - 1, fy - 1, fw + 2, fh + 2);
        shapes.setColor(BORDER.r, BORDER.g, BORDER.b, 1f);
        shapes.rect(fx, fy, fw, fh);
        shapes.setColor(0.08f, 0.075f, 0.07f, 0.94f);
        shapes.rect(fx + 2, fy + 2, fw - 4, fh - 4);
        if (titleBar) {
            shapes.setColor(0.2f, 0.17f, 0.12f, 0.95f);
            shapes.rect(fx + 2, fy + fh - 24, fw - 4, 22);
            shapes.setColor(BORDER.r, BORDER.g, BORDER.b, 0.7f);
            shapes.rect(fx + 2, fy + fh - 25, fw - 4, 1);
        }
    }

    /** Pasek celu: na srodku, chyba ze zaslonilby panel postaci (waski ekran). */
    private float targetBarX(float tw) {
        return gx + gw / 2f - tw / 2f;
    }

    /** Przyblizenie duzej mapy (1 = caly swiat); domyslnie okolica ok. 250 pol, zeby palec trafial w miejsce. */
    private int bigMapZoom = 4;
    private static final int BIG_MAP_MAX_ZOOM = 16;

    /** Przyciski "-" i "+" w pasku tytulu duzej mapy (36 x 26 dp, prawy gorny rog; na komputerze przed krzyzykiem). */
    private HudLayout.Rect mapZoomButton(boolean plus, HudLayout.Rect out) {
        float bw = 36, bh = 24, right = menuX + menuW - (mobile ? 8 : 34);
        out.set(right - (plus ? bw : 2 * bw + 6), menuY + menuH - bh - 2, bw, bh);
        return out;
    }

    /**
     * Dotkniecie przy otwartej duzej mapie: "-" / "+" zmienia przyblizenie, dotkniecie obrazu - marsz na to pole
     * (GameScreen: "map:goto:x,y"), reszta zamyka mape.
     */
    private void bigMapTap(float px, float py, GameSession session, Actions actions) {
        HudLayout.Rect r = new HudLayout.Rect();
        if (mapZoomButton(false, r).contains(px, py)) {
            bigMapZoom = Math.max(1, bigMapZoom / 2);
            return;
        }
        if (mapZoomButton(true, r).contains(px, py)) {
            bigMapZoom = Math.min(BIG_MAP_MAX_ZOOM, bigMapZoom * 2);
            return;
        }
        bigMapOpen = false;
        float[] img = bigMapImg;
        if (img != null && px >= img[0] && px <= img[0] + img[6] && py >= img[1] && py <= img[1] + img[3]) {
            int tx = (int) (img[4] + (px - img[0]) / img[2]);
            int ty = (int) (img[5] + (img[1] + img[3] - py) / img[2]);
            if (tx >= 0 && tx < session.world().width() && ty >= 0 && ty < session.world().height()) actions.onButton("map:goto:" + tx + "," + ty);
        }
    }

    /** Ksztalty przyciskow przyblizenia (w przebiegu ksztaltow okna mapy). */
    private void mapZoomShapes(ShapeRenderer shapes) {
        HudLayout.Rect r = new HudLayout.Rect();
        mapZoomButton(false, r);
        woodButton(shapes, r.x, r.y, r.w, r.h, false);
        mapZoomButton(true, r);
        woodButton(shapes, r.x, r.y, r.w, r.h, false);
    }

    private void drawBigMap(SpriteBatch batch, GameSession session, String floor) {
        pl.fawizja.pixel.world.Position p = session.character().position();
        text(batch, "Mapa - piętro " + floor + (bigMapZoom > 1 ? " x" + bigMapZoom : "") + " (dotknij miejsca, aby tam iść)", menuX + 10, menuY + menuH - 8);
        HudLayout.Rect zr = new HudLayout.Rect();
        centered(batch, "-", mapZoomButton(false, zr).cx(), zr.cy());
        centered(batch, "+", mapZoomButton(true, zr).cx(), zr.cy());
        float[] img = minimap.drawZoomed(batch, session.world(), p, walkTarget, markerPositions(session), menuX + 8, menuY + 8, menuW - 16, menuH - 34, bigMapZoom);
        bigMapImg = img;
        if (img == null) return;
        float right = img[0] + img[6], top = img[1] + img[3];
        font.getData().setScale(0.85f);
        for (pl.fawizja.pixel.world.Town t : session.world().towns()) {
            if (t.temple().z() != p.z()) continue;
            float tx = img[0] + (t.temple().x() - img[4]) * img[2];
            float ty = top - (t.temple().y() - img[5]) * img[2];
            if (tx < img[0] || tx > right || ty < img[1] || ty > top) continue;
            text(batch, t.name(), tx - 20, ty + 14);
        }
        font.setColor(new Color(1f, 0.85f, 0.2f, 1f));
        for (pl.fawizja.pixel.settings.MapMarkers.Marker m : allMarkers(session)) {
            if (m.at().z() != p.z()) continue;
            float mx = img[0] + (m.at().x() - img[4]) * img[2], my = top - (m.at().y() - img[5]) * img[2];
            if (mx < img[0] || mx > right || my < img[1] || my > top) continue;
            text(batch, m.name(), mx + 6, my + 6);
        }
        font.setColor(Color.WHITE);
        font.getData().setScale(1f);
    }

    private static String capitalized(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------ konsola (czat i zdarzenia)

    /** Wiersz konsoli: tekst, kolor i wysokosc po zawinieciu. */
    private record ConLine(String text, Color color, float height) {}
    private final List<ConLine> conLines = new ArrayList<>();
    private float conX, conY, conW, conH;
    private final List<Monster> battle = new ArrayList<>();
    private static final float BATTLE_ROW = 18;
    private static final float CON_SCALE = 0.8f;

    /** Skala interfejsu (pikseli ekranu na jednostke, dp) - GameScreen dobiera do niej imiona nad postaciami. */
    public float uiScale() { return uiScale; }

    /** Napisy w oknach kolumn (umiejetnosci, VIP, walka): na telefonie mniejsze, ale nie ponizej progu 7 dp. */
    private float conScale() { return lay.compact ? 0.66f : CON_SCALE; }

    /** Wiersze konsoli i napisy zdarzen na widoku gry. */
    private float chatScale() { return mobile ? 0.66f : lay.compact ? 0.7f : CON_SCALE; }

    /**
     * Skala napisu, ktory ma sie zmiescic w szerokosci room: najwyzej preferred, nie mniej niz prog 7 dp wielkiej
     * litery ({@link HudLayout#MIN_FONT}); 0, gdy nie miesci sie nawet przy progu (wtedy bez napisu albo skrot).
     */
    private float fitScale(String s, float room, float preferred) {
        font.getData().setScale(1f);
        layout.setText(font, s);
        float sc = layout.width <= 0 ? preferred : Math.min(preferred, room / layout.width);
        return sc + 1e-4f >= HudLayout.MIN_FONT ? Math.max(HudLayout.MIN_FONT, sc) : 0f;
    }

    /** Napis przyciety z konca ("..") do szerokosci room przy biezacej skali. */
    private String clip(String s, float room) {
        layout.setText(font, s);
        if (layout.width <= room) return s;
        for (int n = s.length() - 1; n > 0; n--) {
            String t = s.substring(0, n).trim() + "..";
            layout.setText(font, t);
            if (layout.width <= room) return t;
        }
        return "";
    }

    /** Zakladki czatu jak w Tibii (Lokalny, Serwer, Handel, Gildia, Druzyna, rozmowy prywatne). 2026-10-24 (Darek:
     *  "w czacie na dole ma byc tylko zakladka czatu"): pozostale zdarzenia to tez krotkie napisy na widoku gry. */
    private final pl.fawizja.pixel.chat.ChatTabs chatTabs = new pl.fawizja.pixel.chat.ChatTabs();
    /** Wysokosc paska zakladek i ich prostokaty z ostatniej klatki {x, szerokosc, x krzyzyka albo -1}. */
    private float conTabH;
    private final List<float[]> tabRects = new ArrayList<>();
    private static final float TAB_SCALE = 0.8f;
    /** Napisy zdarzen na widoku gry: jak dlugo widoczne (ms) i ile naraz. */
    private static final long STATUS_KEEP_MS = 5_000;
    private static final int STATUS_LINES = 4;
    private final List<ConLine> statusLines = new ArrayList<>();

    public pl.fawizja.pixel.chat.ChatTabs chatTabs() { return chatTabs; }

    /** Wybiera zakladke czatu (opcja -Dpixel.conTab=N). */
    public void showConsoleTab(int tab) { chatTabs.select(Math.max(0, Math.min(chatTabs.tabs().size() - 1, tab))); }

    private static Color styleColor(pl.fawizja.pixel.chat.ChatTabs.Style st) {
        switch (st) {
            case SAY:
            case OWN:
            case CHANNEL: return new Color(1f, 0.92f, 0.55f, 1f); // mowa jak w Tibii: zolta
            case NPC: return new Color(0.55f, 0.8f, 1f, 1f);
            case PRIVATE_IN: return new Color(0.6f, 0.85f, 1f, 1f);
            case PRIVATE_OUT: return new Color(0.75f, 0.85f, 0.95f, 1f);
            case BROADCAST: return new Color(1f, 0.4f, 0.35f, 1f);
            case WARNING: return new Color(1f, 0.6f, 0.4f, 1f);
            case LOOT: return new Color(0.6f, 1f, 0.6f, 1f);
            default: return new Color(1f, 1f, 1f, 1f);
        }
    }

    private boolean consoleVisible() {
        return !((menuOpen || bigMapOpen) && menuY < conY + conH) && conH > 8;
    }

    /** Konsola: zakladki u gory, pod nimi wiersze wybranej zakladki (najnowsze na dole); zdarzenia jako napisy. */
    private void layoutConsole(GameSession session, long serverTimeMs) {
        PlayerCharacter pc = session.character();
        chatTabs.setSelf(pc.name());
        chatTabs.sync(session.log(), serverTimeMs);
        float maxBody = conH - 8 - conTabH;
        conLines.clear();
        battle.clear();
        for (Monster m : session.monsters().monsters()) {
            if (m.isDead() || m.position().z() != pc.position().z() || m.position().distanceTo(pc.position()) > 7) continue;
            battle.add(m);
        }
        battle.sort(java.util.Comparator.comparingInt(m -> m.position().distanceTo(pc.position())));
        float tw = conW - 14;
        font.getData().setScale(chatScale());
        float used = 0;
        List<pl.fawizja.pixel.chat.ChatTabs.Line> lines = chatTabs.active().lines();
        for (int i = lines.size() - 1; i >= 0; i--) {
            pl.fawizja.pixel.chat.ChatTabs.Line e = lines.get(i);
            ConLine l = conLine(e.text(), styleColor(e.style()), tw);
            if (used + l.height() > maxBody) break;
            used += l.height();
            conLines.add(0, l);
        }
        statusLines.clear();
        // na telefonie najwyzej 2 krotkie wiersze bez zawijania (reszta tylko w zakladce Serwer)
        int maxLines = lay.compact || mobile ? 2 : STATUS_LINES;
        float statusW = statusWidth();
        List<EventLog.Entry> last = session.log().latest(STATUS_LINES * 2);
        for (int i = last.size() - 1; i >= 0 && statusLines.size() < maxLines; i--) {
            EventLog.Entry e = last.get(i);
            long age = serverTimeMs - e.timeMs();
            if (e.kind() == EventLog.Kind.CHAT || age > STATUS_KEEP_MS) continue;
            float a = age > STATUS_KEEP_MS - 1000 ? Math.max(0f, (STATUS_KEEP_MS - age) / 1000f) : 1f;
            Color c = e.kind() == EventLog.Kind.WARNING ? new Color(1f, 0.55f, 0.35f, a)
                    : e.kind() == EventLog.Kind.LOOT ? new Color(0.55f, 1f, 0.55f, a) : new Color(1f, 1f, 1f, a);
            if (lay.compact || mobile) {
                String one = clip(e.text(), statusW);
                layout.setText(font, one);
                statusLines.add(0, new ConLine(one, c, layout.height + 4));
            } else {
                statusLines.add(0, conLine(e.text(), c, statusW));
            }
        }
        layoutTabs();
        font.getData().setScale(1f);
    }

    /** Szerokosci zakladek: wedlug nazwy, a gdy sie nie mieszcza - rowno sciesnione. */
    private void layoutTabs() {
        tabRects.clear();
        List<pl.fawizja.pixel.chat.ChatTabs.Tab> tabs = chatTabs.tabs();
        font.getData().setScale(TAB_SCALE);
        float[] want = new float[tabs.size()];
        float total = 0;
        float closeW = conTabH * 0.8f;
        for (int i = 0; i < tabs.size(); i++) {
            layout.setText(font, tabs.get(i).title());
            want[i] = layout.width + 14 + (tabs.get(i).closable() && i == chatTabs.activeIndex() ? closeW : 0);
            total += want[i];
        }
        float room = conW - 8 - 2 * (tabs.size() - 1);
        float k = total > room ? room / total : 1f;
        float x = conX + 4;
        for (int i = 0; i < tabs.size(); i++) {
            float tw = want[i] * k;
            boolean close = tabs.get(i).closable() && i == chatTabs.activeIndex();
            tabRects.add(new float[] {x, tw, close ? x + tw - closeW : -1});
            x += tw + 2;
        }
        font.getData().setScale(1f);
    }

    /** Tlo zakladek (w przebiegu ksztaltow): wybrana jasniejsza i polaczona z konsola. */
    private void drawTabShapes(ShapeRenderer shapes) {
        float ty = conY + conH - conTabH - 2;
        for (int i = 0; i < tabRects.size(); i++) {
            float[] r = tabRects.get(i);
            boolean act = i == chatTabs.activeIndex();
            shapes.setColor(0.05f, 0.04f, 0.03f, 0.9f);
            shapes.rect(r[0], ty, r[1], conTabH);
            shapes.setColor(act ? new Color(0.36f, 0.3f, 0.22f, 1f) : new Color(0.18f, 0.15f, 0.12f, 1f));
            shapes.rect(r[0] + 1, ty + (act ? 0 : 1), r[1] - 2, conTabH - (act ? 1 : 2));
        }
        // linia pod zakladkami oddziela je od wierszy czatu
        shapes.setColor(0.05f, 0.04f, 0.03f, 0.9f);
        shapes.rect(conX + 4, ty - 1, conW - 8, 1);
    }

    /** Nazwy zakladek: wybrana biala, z nieprzeczytanymi czerwona (jak w Tibii), pozostale szare. */
    private void drawTabTitles(SpriteBatch batch) {
        List<pl.fawizja.pixel.chat.ChatTabs.Tab> tabs = chatTabs.tabs();
        float ty = conY + conH - conTabH - 2;
        for (int i = 0; i < tabRects.size() && i < tabs.size(); i++) {
            float[] r = tabRects.get(i);
            pl.fawizja.pixel.chat.ChatTabs.Tab t = tabs.get(i);
            boolean act = i == chatTabs.activeIndex();
            float textW = r[2] >= 0 ? r[2] - r[0] : r[1];
            font.getData().setScale(TAB_SCALE);
            layout.setText(font, t.title());
            float sc = layout.width > textW - 6 ? TAB_SCALE * (textW - 6) / layout.width : TAB_SCALE;
            font.getData().setScale(Math.max(HudLayout.MIN_FONT, sc));
            if (act) font.setColor(1f, 1f, 1f, 1f);
            else if (t.unread()) font.setColor(1f, 0.32f, 0.25f, 1f);
            else font.setColor(0.7f, 0.68f, 0.62f, 1f);
            String title = clip(t.title(), textW - 4);
            layout.setText(font, title);
            font.draw(batch, title, r[0] + (textW - layout.width) / 2f, ty + conTabH / 2f + layout.height / 2f);
            if (r[2] >= 0) {
                font.setColor(0.85f, 0.5f, 0.45f, 1f);
                layout.setText(font, "x");
                font.draw(batch, "x", r[2] + (r[0] + r[1] - r[2] - layout.width) / 2f - 1, ty + conTabH / 2f + layout.height / 2f);
            }
        }
        font.setColor(Color.WHITE);
        font.getData().setScale(1f);
    }

    /** Zdarzenia (lup, poziom, osiagniecia, ostrzezenia) na dole widoku gry, wysrodkowane, z cieniem. */
    /** Szerokosc napisow zdarzen na widoku gry. */
    private float statusWidth() { return mobile ? mlay.chatPanel.w : lay.compact ? gw - 16 : Math.min(gw - 20, 520); }

    private void drawStatus(SpriteBatch batch) {
        if (statusLines.isEmpty() || menuOpen || bigMapOpen) return;
        font.getData().setScale(chatScale());
        float width = statusWidth();
        float x = gx + (gw - width) / 2f;
        float yy = gy + 10;
        for (int i = statusLines.size() - 1; i >= 0; i--) yy += statusLines.get(i).height();
        for (ConLine l : statusLines) {
            Color c = l.color();
            {
                // ciemne tlo pod wierszem - bialy napis czytelny tez na jasnej podlodze
                layout.setText(font, l.text(), c, width, com.badlogic.gdx.utils.Align.center, true);
                batch.setColor(0f, 0f, 0f, 0.5f * c.a);
                batch.draw(art.white(), x + (width - layout.width) / 2f - 4, yy - l.height() + 1, layout.width + 8, l.height());
                batch.setColor(Color.WHITE);
            }
            font.setColor(0f, 0f, 0f, c.a * 0.85f);
            font.draw(batch, l.text(), x + 1, yy - 1, width, com.badlogic.gdx.utils.Align.center, true);
            font.setColor(c);
            font.draw(batch, l.text(), x, yy, width, com.badlogic.gdx.utils.Align.center, true);
            yy -= l.height();
        }
        font.setColor(Color.WHITE);
        font.getData().setScale(1f);
    }

    private ConLine conLine(String text, Color c, float width) {
        layout.setText(font, text, c, width, com.badlogic.gdx.utils.Align.left, true);
        return new ConLine(text, c, layout.height + 4);
    }

    private void drawConsole(SpriteBatch batch) {
        if (!consoleVisible()) return;
        drawTabTitles(batch);
        font.getData().setScale(chatScale());
        float yy = conY + conH - 4 - conTabH;
        for (ConLine l : conLines) {
            if (yy - l.height() < conY - 2) break;
            font.setColor(l.color());
            font.draw(batch, l.text(), conX + 7, yy, conW - 14, com.badlogic.gdx.utils.Align.left, true);
            yy -= l.height();
        }
        if (conLines.isEmpty()) {
            font.setColor(0.6f, 0.6f, 0.55f, 1f);
            pl.fawizja.pixel.chat.ChatTabs.Tab t = chatTabs.active();
            String hint;
            switch (t.channel()) {
                case SERVER: hint = "Serwer: wiadomości gry i serwera."; break;
                case TRADE: hint = "Handel: kupno i sprzedaż. Dotknij, aby pisać."; break;
                case GUILD: hint = "Gildia: dotknij, aby pisać do gildii."; break;
                case PARTY: hint = partyLine != null ? partyLine + " Dotknij, aby pisać." : "Drużyna: dotknij, aby pisać do drużyny."; break;
                case PRIVATE: hint = "Rozmowa z " + t.peer() + ": dotknij, aby pisać."; break;
                default: hint = "Czat: dotknij, aby pisać";
            }
            text(batch, hint, conX + 7, yy);
        }
        font.setColor(Color.WHITE);
        font.getData().setScale(1f);
    }

    /** Dotkniecie zakladki ja wybiera (krzyzyk zamyka rozmowe), dotkniecie wierszy otwiera pisanie w tej zakladce. */
    private boolean consoleTouch(float px, float py, Actions actions) {
        // zakladki: pole dotyku siega do ramki widoku nad konsola i kilka dp w dol (razem co najmniej 28 dp)
        float tabTouch = Math.max(0, 28 - conTabH - 2 - HudLayout.GAP);
        if (!consoleVisible() || px < conX || px > conX + conW || py < conY || py > conY + conH + HudLayout.GAP) return false;
        if (py >= conY + conH - conTabH - 2 - tabTouch) {
            for (int i = 0; i < tabRects.size(); i++) {
                float[] r = tabRects.get(i);
                if (px < r[0] || px > r[0] + r[1]) continue;
                if (r[2] >= 0 && px >= r[2]) chatTabs.close(i);
                else chatTabs.select(i);
                layoutTabs();
                return true;
            }
            return true;
        }
        actions.onButton("chat");
        return true;
    }

    /** Dotkniecie listy walki: gracz - okienko gracza, potwor - cel. */
    private boolean battleTouch(float px, float py, Actions actions) {
        if (!battleVisible() || px < blX || px > blX + blW || py < blY || py > blY + blH) return false;
        int i = (int) ((blY + blH - lay.header - 4 - py) / lay.battleRow);
        if (i >= 0 && i < battlePlayers.size()) actions.onButton("bplayer:" + battlePlayers.get(i).id());
        else if (i >= battlePlayers.size() && i - battlePlayers.size() < battle.size()) actions.onButton("battle:" + battle.get(i - battlePlayers.size()).id());
        return true;
    }

    private boolean battleVisible() {
        return blH >= lay.header + lay.battleRow && !windowOverPanel() && !charOpen;
    }

    /** Lista walki w panelu (jak battle list w Tibii): gracze, potem potwory - imie, pasek zycia, ramka celu. */
    private void drawBattle(SpriteBatch batch) {
        if (!battleVisible()) return;
        headerText(batch, "Walka (" + (battle.size() + battlePlayers.size()) + ")", blX + 6, blY + blH);
        Monster tgt = currentTarget;
        float row = lay.battleRow;
        float yy = blY + blH - lay.header - 4;
        font.getData().setScale(conScale());
        if (battle.isEmpty() && battlePlayers.isEmpty()) {
            font.setColor(0.75f, 0.75f, 0.7f, 1f);
            text(batch, "Brak potworów w pobliżu.", blX + 8, yy - 2);
            font.setColor(Color.WHITE);
            font.getData().setScale(1f);
            return;
        }
        float bw = Math.min(70, blW * 0.3f), bx = blX + blW - 10 - bw;
        float ic = row - 3;
        float textY = yy - (row - font.getCapHeight()) / 2f + 0.5f;
        for (BattlePlayer bp : battlePlayers) {
            if (yy - row < blY + 2) break;
            float rowY = yy - row + 1;
            TextureRegion sk = skullIcon(bp.skull());
            if (sk != null) batch.draw(sk, blX + 7, rowY + 1, ic, ic);
            Color skc = skullColor(bp.skull());
            font.setColor(bp.ally() ? new Color(0.5f, 0.78f, 1f, 1f) : skc != null ? skc : Color.WHITE);
            float room = bx - 6 - (blX + 7 + row);
            text(batch, clip(bp.name(), room), blX + 7 + row, textY);
            font.setColor(Color.WHITE);
            healthBar(batch, bx, rowY + row / 2f - 3.5f, bw, bp.healthPercent() / 100f);
            yy -= row;
            textY -= row;
        }
        for (Monster m : battle) {
            if (yy - row < blY + 2) break;
            float rowY = yy - row + 1;
            if (m == tgt) outline(batch, blX + 4, rowY, blW - 8, row - 1, new Color(0.95f, 0.25f, 0.2f, 1f));
            TextureRegion icon = art.monster(m.type().key());
            if (icon != null) batch.draw(icon, blX + 7, rowY + 1, ic, ic);
            float room = bx - 6 - (blX + 7 + row);
            text(batch, clip(m.type().name(), room), blX + 7 + row, textY);
            healthBar(batch, bx, rowY + row / 2f - 3.5f, bw, m.healthFraction());
            yy -= row;
            textY -= row;
        }
        font.getData().setScale(1f);
    }

    /** Pasek zycia na liscie walki: czarne tlo i kolor wg procentu jak w Tibii. */
    private void healthBar(SpriteBatch batch, float x, float y, float bw, float f) {
        batch.setColor(0, 0, 0, 0.7f);
        batch.draw(art.white(), x - 1, y, bw + 2, 6);
        float[] c = BarColors.health(f);
        batch.setColor(c[0], c[1], c[2], 1f);
        batch.draw(art.white(), x, y + 1, bw * Math.max(0f, Math.min(1f, f)), 4);
        batch.setColor(Color.WHITE);
    }

    private Monster currentTarget;

    // ------------------------------------------------------------------ staly panel ekwipunku pod minimapa

    /** Uklad 5x2 na niskich ekranach: {kolumna, wiersz} dla EQ_SLOTS. */
    private static final int[][] EQ_COMPACT = {{1, 0}, {0, 0}, {0, 1}, {2, 0}, {1, 1}, {2, 1}, {3, 0}, {3, 1}, {4, 0}, {5, 0}};
    private float epX, epY, epW, epH, epCell;
    /** true = okno jak w Tibii (naglowek, 3x4), false = zwarty pasek 5x2 bez naglowka. */
    private boolean epTibia;

    private boolean equipPanelVisible() {
        return !charOpen && !windowOverPanel();
    }

    /** Gniazdo plecaka w panelu: jak w Tibii prawy gorny rog lalki, w ukladzie 5x2 ostatnie pole. */
    private float[] epBackpackPos() {
        if (epTibia) return new float[] {epX + epPad() + 2 * (epCell + 3), epY + epH - lay.eqHeader - 4 - epCell};
        return new float[] {epX + 4 + 4 * (epCell + 2), epY + epH - 4 - epCell - (epCell + 2)};
    }

    /** Lewy margines pol w oknie ekwipunku (telefon: wezszy, okno bez naglowka). */
    private float epPad() { return lay.compact ? 4 : 6; }

    private float[] epCellPos(int i) {
        if (epTibia) {
            return new float[] {epX + epPad() + EQ_CELLS[i][0] * (epCell + 3), epY + epH - lay.eqHeader - 4 - epCell - EQ_CELLS[i][1] * (epCell + 3)};
        }
        return new float[] {epX + 4 + EQ_COMPACT[i][0] * (epCell + 2), epY + epH - 4 - epCell - EQ_COMPACT[i][1] * (epCell + 2)};
    }

    /** Pierwsze dotkniecie zalozonego przedmiotu opisuje go w konsoli, drugie (do 2 s) zdejmuje do plecaka. */
    private int eqTapSlot = -1;
    private long eqTapMs;

    private void equipTap(int i, GameSession session, Actions actions) {
        ItemStack st = session.character().equipment().get(EQ_SLOTS[i]);
        if (EQ_SLOTS[i] == EquipSlot.LIGHT) {
            // pole pochodni: dotkniecie zapala i gasi (bez pochodni - podpowiedz)
            if (st == null) {
                session.log().add(session.lastUpdateMs(), EventLog.Kind.INFO,
                        "Pole pochodni. Dotknij pochodni w plecaku i wybierz \"Załóż\".");
            } else {
                actions.onButton("torch");
            }
            eqTapSlot = -1;
            return;
        }
        if (st == null) return;
        long now = com.badlogic.gdx.utils.TimeUtils.millis();
        if (eqTapSlot == i && now - eqTapMs <= 2000) {
            actions.onButton("unequip:" + EQ_SLOTS[i].name());
            eqTapSlot = -1;
            return;
        }
        eqTapSlot = i;
        eqTapMs = now;
        ItemType it = session.items().get(st.itemId());
        StringBuilder d = new StringBuilder(it.name());
        if (st.upgrade() > 0) d.append(" +").append(st.upgrade());
        if (it.attack() > 0) d.append(", atak ").append(it.attack());
        if (it.defense() > 0) d.append(", obrona ").append(it.defense());
        if (it.armor() > 0) d.append(", pancerz ").append(it.armor());
        d.append(". Dotknij ponownie, aby zdjąć.");
        session.log().add(session.lastUpdateMs(), EventLog.Kind.INFO, d.toString());
    }

    private boolean equipPanelTouch(float px, float py, GameSession session, Actions actions) {
        if (!equipPanelVisible() || px < epX || px > epX + epW || py < epY || py > epY + epH) return false;
        if (buffCellTouch(px, py, session)) return true;
        float[] bp = epBackpackPos();
        if (px >= bp[0] && px <= bp[0] + epCell && py >= bp[1] && py <= bp[1] + epCell) {
            toggleBag();
            return true;
        }
        for (int i = 0; i < EQ_SLOTS.length; i++) {
            float[] c = epCellPos(i);
            if (px >= c[0] && px <= c[0] + epCell && py >= c[1] && py <= c[1] + epCell) {
                equipTap(i, session, actions);
                break;
            }
        }
        return true;
    }

    private void drawEquipPanel(SpriteBatch batch, GameSession session) {
        if (epTibia && lay.eqHeader > 0) headerText(batch, "Ekwipunek", epX + 6, epY + epH);
        for (int i = 0; i < EQ_SLOTS.length; i++) {
            float[] c = epCellPos(i);
            drawEquipSlot(batch, session.character().equipment().get(EQ_SLOTS[i]), EQ_NAMES[i], c[0], c[1], epCell);
            if (EQ_SLOTS[i] == EquipSlot.LIGHT) drawTorchState(batch, session, c[0], c[1], epCell);
        }
        float[] bp = epBackpackPos();
        drawBackpackSlot(batch, bp[0], bp[1], epCell);
        drawBuffCell(batch);
    }

    /** Plecak w gniezdzie ekwipunku (dotkniecie otwiera plecak), podswietlony gdy plecak jest otwarty. */
    private void drawBackpackSlot(SpriteBatch batch, float x, float y, float cell) {
        if (slotImg != null) batch.draw(bagOpen && wstSelected != null ? wstSelected : slotImg, x, y, cell, cell);
        if (bagOpen) {
            batch.setColor(1f, 0.85f, 0.3f, 0.35f);
            batch.draw(art.white(), x + 1, y + 1, cell - 2, cell - 2);
            batch.setColor(Color.WHITE);
        }
        float pad = slotPad(cell);
        batch.draw(art.backpack(), x + pad, y + pad, cell - pad * 2, cell - pad * 2);
    }

    /**
     * Pole pochodni: zapalona swieci (cieply blask w polu i zolta ramka), w rogu pozostaly czas palenia
     * ("18m", ostatnia minuta w sekundach); zgaszona - czas szary.
     */
    private void drawTorchState(SpriteBatch batch, GameSession session, float x, float y, float cell) {
        PlayerCharacter pc = session.character();
        int torch = session.items().byKey("torch").id();
        if (!pc.hasTorchEquipped(torch)) return;
        boolean lit = pc.isTorchLit();
        if (lit) {
            batch.setColor(1f, 0.62f, 0.18f, 0.25f);
            batch.draw(art.white(), x + 2, y + 2, cell - 4, cell - 4);
            batch.setColor(1f, 0.8f, 0.3f, 0.9f);
            batch.draw(art.white(), x + 1, y + 1, cell - 2, 1);
            batch.draw(art.white(), x + 1, y + cell - 2, cell - 2, 1);
            batch.draw(art.white(), x + 1, y + 1, 1, cell - 2);
            batch.draw(art.white(), x + cell - 2, y + 1, 1, cell - 2);
            batch.setColor(Color.WHITE);
            ItemStack st = pc.equipment().get(EquipSlot.LIGHT);
            TextureRegion r = art.item(st.itemId());
            if (r != null) {
                float pad = slotPad(cell);
                batch.draw(r, x + pad, y + pad, cell - pad * 2, cell - pad * 2);
            }
        }
        long sec = (pc.torchRemainingMs(torch) + 999) / 1000;
        String t = sec >= 60 ? (sec + 59) / 60 + "m" : sec + "s";
        font.getData().setScale(Math.max(HudLayout.MIN_FONT, Math.min(0.7f, cell / 50f)));
        layout.setText(font, t);
        batch.setColor(0f, 0f, 0f, 0.6f);
        batch.draw(art.white(), x + cell - layout.width - 4, y + 1, layout.width + 3, layout.height + 4);
        batch.setColor(Color.WHITE);
        font.setColor(lit ? new Color(1f, 0.86f, 0.4f, 1f) : new Color(0.75f, 0.75f, 0.75f, 1f));
        font.draw(batch, t, x + cell - layout.width - 2.5f, y + 3 + layout.height);
        font.setColor(Color.WHITE);
        font.getData().setScale(1f);
    }

    /** Polozenie monet w kupce (ulamki pola), od spodu: wiecej monet - wyzsza kupka, jak w Tibii. */
    private static final float[][] COIN_PILE = {{-0.15f, -0.12f}, {0.15f, -0.12f}, {0f, -0.18f}, {-0.08f, 0.04f},
            {0.09f, 0.03f}, {0f, 0.19f}, {0.01f, -0.03f}};

    /**
     * Ikona stosu w polu plecaka: monety rysowane jako kupka (2, 3, 4 monety, od 5 - 5, od 10 - 6, od 25 - 7)
     * z jednej grafiki monety (OTSP), wysrodkowana w polu; pozostale przedmioty bez zmian.
     */
    private void drawStackIcon(SpriteBatch batch, TextureRegion r, ItemStack st, float x, float y, float size) {
        int n = st.count();
        if (!pl.fawizja.pixel.economy.Currency.isCoin(st.itemId()) || n < 2) {
            batch.draw(r, x, y, size, size);
            return;
        }
        int k = n >= 25 ? 7 : n >= 10 ? 6 : n >= 5 ? 5 : n;
        float cx = 0, cy = 0;
        for (int i = 0; i < k; i++) {
            cx += COIN_PILE[i][0];
            cy += COIN_PILE[i][1];
        }
        cx /= k;
        cy /= k;
        for (int i = 0; i < k; i++) {
            batch.draw(r, x + (COIN_PILE[i][0] - cx) * size, y + (COIN_PILE[i][1] - cy) * size, size, size);
        }
    }

    /** Pole ekwipunku: tlo z OTClientV8, przedmiot albo szara nazwa gniazda. */
    private void drawEquipSlot(SpriteBatch batch, ItemStack st, String name, float x, float y, float cell) {
        TextureRegion frame = slotFrame(st, art.itemRegistry());
        if (frame != null) batch.draw(frame, x, y, cell, cell);
        TextureRegion r = st == null ? null : art.item(st.itemId());
        if (r != null) {
            float pad = slotPad(cell);
            batch.draw(r, x + pad, y + pad, cell - pad * 2, cell - pad * 2);
            font.getData().setScale(Math.max(HudLayout.MIN_FONT, Math.min(0.7f, cell / 50f)));
            if (st.count() > 1) text(batch, String.valueOf(st.count()), x + 2, y + cell * 0.32f);
            if (st.upgrade() > 0) text(batch, "+" + st.upgrade(), x + cell * 0.55f, y + cell - 2);
            font.getData().setScale(1f);
        } else {
            // nazwa pustego gniazda tylko, gdy miesci sie przy progu 7 dp
            float sc = fitScale(name, cell - 4, 0.66f);
            font.getData().setScale(sc);
            font.setColor(0.6f, 0.6f, 0.6f, 1f);
            if (sc > 0) centered(batch, name, x + cell / 2f, y + cell / 2f);
            font.setColor(Color.WHITE);
            font.getData().setScale(1f);
        }
    }

    // ------------------------------------------------------------------ okna ekwipunku i umiejetnosci

    /** Uklad lalki ekwipunku: {kolumna, wiersz} od gory. */
    private static final EquipSlot[] EQ_SLOTS = {EquipSlot.AMULET, EquipSlot.HEAD, EquipSlot.WEAPON, EquipSlot.ARMOR,
            EquipSlot.SHIELD, EquipSlot.RING, EquipSlot.LEGS, EquipSlot.AMMO, EquipSlot.FEET, EquipSlot.LIGHT};
    /** Ostatnie pole (na prawo od butow): pochodnia. */
    private static final int[][] EQ_CELLS = {{0, 0}, {1, 0}, {0, 1}, {1, 1}, {2, 1}, {0, 2}, {1, 2}, {2, 2}, {1, 3}, {2, 3}};
    private static final String[] EQ_NAMES = {"Amulet", "Hełm", "Broń", "Zbroja", "Tarcza", "Pierśc.", "Nogi", "Amun.", "Buty", "Poch."};
    private static final float SK_ROW = 15;
    private static final int SK_INFO_ROWS = 9;
    private float eqX, eqY, eqW, eqH, eqCell, skX, skY, skW, skH;

    private void layoutCharWindows() {
        eqCell = Math.max(28, Math.min(40, (h - 110) / 5.2f));
        eqW = eqCell * 3 + 6 * 2 + 16;
        eqH = 23 + eqCell * 4 + 6 * 3 + 12;
        skW = Math.min(250, w - eqW - 40);
        skH = 23 + SK_ROW * (SK_INFO_ROWS + 1 + Skill.values().length) + 8;
        float total = eqW + 8 + skW;
        float x0 = Math.max(8, (w - total) / 2f);
        float top = Math.min(h - 8, h / 2f + Math.max(eqH, skH) / 2f);
        eqX = x0; eqY = top - eqH;
        skX = x0 + eqW + 8; skY = top - skH;
    }

    private float[] eqBackpackPos() {
        return new float[] {eqX + 8 + 2 * (eqCell + 6), eqY + eqH - 23 - 6 - eqCell};
    }

    private float[] eqCellPos(int i) {
        return new float[] {eqX + 8 + EQ_CELLS[i][0] * (eqCell + 6), eqY + eqH - 23 - 6 - eqCell - EQ_CELLS[i][1] * (eqCell + 6)};
    }

    /** Dotkniecie przy otwartych oknach postaci: zamkniecie, zdjecie przedmiotu albo nic (okno pod palcem). */
    private boolean charWindowsTouch(float px, float py, GameSession session, Actions actions) {
        if (inside(px, py, eqX + eqW - 12, eqY + eqH - 12, 11) || inside(px, py, skX + skW - 12, skY + skH - 12, 11)) {
            charOpen = false;
            return true;
        }
        float[] bp = eqBackpackPos();
        if (px >= bp[0] && px <= bp[0] + eqCell && py >= bp[1] && py <= bp[1] + eqCell) {
            charOpen = false;
            if (!bagOpen) toggleBag();
            return true;
        }
        for (int i = 0; i < EQ_SLOTS.length; i++) {
            float[] c = eqCellPos(i);
            if (px >= c[0] && px <= c[0] + eqCell && py >= c[1] && py <= c[1] + eqCell) {
                equipTap(i, session, actions);
                return true;
            }
        }
        return px >= eqX && px <= skX + skW && py >= Math.min(eqY, skY) && py <= Math.max(eqY + eqH, skY + skH);
    }

    private static boolean inside(float px, float py, float cx, float cy, float r) {
        return Math.abs(px - cx) <= r && Math.abs(py - cy) <= r;
    }

    private void drawCharacterSheet(SpriteBatch batch, GameSession session) {
        PlayerCharacter pc = session.character();
        // ekwipunek
        text(batch, "Ekwipunek", eqX + 8, eqY + eqH - 6);
        closeButton(batch, eqX + eqW - 12, eqY + eqH - 12);
        for (int i = 0; i < EQ_SLOTS.length; i++) {
            float[] c = eqCellPos(i);
            drawEquipSlot(batch, pc.equipment().get(EQ_SLOTS[i]), EQ_NAMES[i], c[0], c[1], eqCell);
            if (EQ_SLOTS[i] == EquipSlot.LIGHT) drawTorchState(batch, session, c[0], c[1], eqCell);
        }
        float[] bp = eqBackpackPos();
        drawBackpackSlot(batch, bp[0], bp[1], eqCell);
        font.getData().setScale(0.7f);
        font.setColor(0.8f, 0.8f, 0.75f, 1f);
        text(batch, "Dotknij, aby zdjąć", eqX + 8, eqY + 14);
        font.setColor(Color.WHITE);
        font.getData().setScale(1f);

        // umiejetnosci (jak okno Skills): stan postaci, potem umiejetnosci z paskiem postepu
        text(batch, "Umiejętności", skX + 8, skY + skH - 6);
        closeButton(batch, skX + skW - 12, skY + skH - 12);
        long next = pl.fawizja.pixel.balance.Balance.experienceForLevel(Math.min(1000, pc.level() + 1));
        pl.fawizja.pixel.combat.AttackProfile ap = pl.fawizja.pixel.combat.CombatFormulas.attackProfile(pc, session.items());
        String[][] info = {
                {"Poziom", String.valueOf(pc.level())},
                {"Doświadczenie", String.format("%,d", pc.experience())},
                {"Do poziomu", pl.fawizja.pixel.balance.Balance.percentToNextLevel(pc.experience(), pc.level()) + "% (" + String.format("%,d", Math.max(0, next - pc.experience())) + ")"},
                {"Zdrowie", pc.health() + " / " + pc.maxHealth()},
                {"Mana", pc.mana() + " / " + pc.maxMana()},
                {"Szybkość", String.valueOf(pc.speed())},
                {"Udźwig", String.valueOf(pc.capacity())},
                {"Atak", "do " + ap.maxDamage() + " (" + ap.skill().label().toLowerCase(java.util.Locale.ROOT) + ")"},
                {"Sytość", (pc.fedMs() / 60_000) + " min" + (pc.restedKills() > 0 ? ", wypoczęty " + pc.restedKills() : "")},
        };
        float yy = skY + skH - 23 - 3;
        font.getData().setScale(0.8f);
        for (String[] row : info) {
            row(batch, row[0], row[1], yy);
            yy -= SK_ROW;
        }
        yy -= SK_ROW; // odstep jak separator
        for (Skill sk : Skill.values()) {
            row(batch, sk.label(), pc.skills().level(sk) + "  (" + pc.skills().percentToNext(sk) + "%)", yy);
            yy -= SK_ROW;
        }
        font.getData().setScale(1f);
    }

    /** Wiersz okna umiejetnosci: etykieta z lewej, wartosc z prawej. */
    private void row(SpriteBatch batch, String label, String value, float yy) {
        font.setColor(0.85f, 0.85f, 0.8f, 1f);
        text(batch, label, skX + 8, yy);
        font.setColor(Color.WHITE);
        layout.setText(font, value);
        text(batch, value, skX + skW - 8 - layout.width, yy);
    }

    private float bagTipW() { return Math.min(w - 20, Math.max(slotSize * 5 + 16, 420)); }
    private float bagTipX() { return bagX + slotSize * 2.5f - bagTipW() / 2f; }

    private void drawBagBackground(ShapeRenderer shapes, GameSession session) {
        if (selectedSlot >= session.character().backpack().size()) selectedSlot = -1; // odpiety plecak
        float extra = selectedSlot >= 0 ? slotSize * 0.55f * 1.6f + 6 : 0;
        miniWindow(shapes, "wood", bagX - 8, bagY - 50 - extra, slotSize * 5 + 16, slotSize * 4 + 80 + extra);
        if (selectedSlot >= 0 && session.character().backpack().get(selectedSlot) != null) {
            shapes.setColor(0.06f, 0.05f, 0.04f, 0.9f);
            shapes.rect(bagTipX(), bagY + slotSize * 4 + 34, bagTipW(), 38);
        }
        bagPages = session.character().bagPages();
        if (bagPage >= bagPages) bagPage = bagPages - 1;
        for (int k = 0; k < bagPages && bagPages > 1; k++) {
            float tx = bagTabX(), ty = bagTabY(k);
            shapes.setColor(0.08f, 0.07f, 0.06f, 0.95f);
            shapes.rect(tx - 2, ty - 2, TAB + 4, TAB + 4);
            shapes.setColor(k == bagPage ? new Color(0.75f, 0.6f, 0.25f, 1f) : new Color(0.3f, 0.26f, 0.2f, 1f));
            shapes.rect(tx, ty, TAB, TAB);
        }
        int base = bagPage * PAGE_SLOTS;
        for (int i = 0; i < 20 && slotImg == null; i++) {
            float sx = bagX + (i % 5) * slotSize;
            float sy = bagY + (3 - i / 5) * slotSize;
            shapes.setColor(base + i == selectedSlot ? new Color(0.9f, 0.75f, 0.2f, 1f) : new Color(0.35f, 0.3f, 0.25f, 1f));
            shapes.rect(sx + 2, sy + 2, slotSize - 4, slotSize - 4);
            shapes.setColor(0.18f, 0.15f, 0.12f, 1f);
            shapes.rect(sx + 4, sy + 4, slotSize - 8, slotSize - 8);
            ItemStack st = session.character().backpack().get(base + i);
            if (st != null && session.character().isKept(st.itemId())) {
                // znaczek "zachowany": zloty rog slotu
                shapes.setColor(0.95f, 0.8f, 0.25f, 1f);
                shapes.triangle(sx + slotSize - 4, sy + slotSize - 4, sx + slotSize - 16, sy + slotSize - 4, sx + slotSize - 4, sy + slotSize - 16);
            }
        }
        if (selectedSlot >= 0) {
            for (HudButton b : new HudButton[] {bagEquip, bagDrop, bagUse}) {
                shapes.setColor(0.25f, 0.45f, 0.3f, 0.95f);
                shapes.rect(b.x - b.r * 1.3f, b.y - b.r * 0.6f, b.r * 2.6f, b.r * 1.2f);
            }
            shapes.setColor(0.55f, 0.45f, 0.15f, 0.95f);
            shapes.rect(bagKeep.x - bagKeep.r * 1.8f, bagKeep.y - bagKeep.r * 0.6f, bagKeep.r * 3.6f, bagKeep.r * 1.2f);
        } else {
            shapes.setColor(0.3f, 0.35f, 0.5f, 0.95f);
            shapes.rect(bagSort.x - bagSort.r * 1.6f, bagSort.y - bagSort.r * 0.6f, bagSort.r * 3.2f, bagSort.r * 1.2f);
            if (bagPages > 1 && bagPage == bagPages - 1) {
                shapes.setColor(0.45f, 0.3f, 0.2f, 0.95f);
                shapes.rect(bagDetach.x - bagDetach.r * 1.8f, bagDetach.y - bagDetach.r * 0.6f, bagDetach.r * 3.6f, bagDetach.r * 1.2f);
            }
        }
        if (closeImg == null) {
            shapes.setColor(0.6f, 0.15f, 0.15f, 0.95f);
            shapes.circle(bagClose.x, bagClose.y, bagClose.r, 20);
        }
    }

    private void drawBagContents(SpriteBatch batch, GameSession session) {
        PlayerCharacter pc = session.character();
        int base = bagPage * PAGE_SLOTS;
        int used = 0;
        for (int i = 0; i < PAGE_SLOTS; i++) if (pc.backpack().get(base + i) != null) used++;
        text(batch, (bagPages > 1 ? "Plecak " + (bagPage + 1) + "/" + bagPages : "Plecak") + " (" + used + "/20)", bagX, bagY + slotSize * 4 + 22);
        for (int k = 0; k < bagPages && bagPages > 1; k++) centered(batch, String.valueOf(k + 1), bagTabX() + TAB / 2f, bagTabY(k) + TAB / 2f);
        closeButton(batch, bagClose.x, bagClose.y);
        if (slotImg != null) {
            // pola plecaka z OTClientV8: zaznaczone - zolta ramka, zachowane - zloty rog
            for (int i = 0; i < 20; i++) {
                float sx = bagX + (i % 5) * slotSize;
                float sy = bagY + (3 - i / 5) * slotSize;
                ItemStack st = pc.backpack().get(base + i);
                batch.draw(base + i == selectedSlot && wstSelected != null ? wstSelected : slotFrame(st, art.itemRegistry()), sx + 1, sy + 1, slotSize - 2, slotSize - 2);
                if (base + i == selectedSlot) outline(batch, sx + 1, sy + 1, slotSize - 2, slotSize - 2, new Color(0.95f, 0.8f, 0.25f, 1f));
                if (st != null && pc.isKept(st.itemId())) {
                    batch.setColor(0.95f, 0.8f, 0.25f, 1f);
                    batch.draw(art.white(), sx + slotSize - 10, sy + slotSize - 10, 7, 7);
                    batch.setColor(Color.WHITE);
                }
            }
        }
        for (int i = 0; i < 20; i++) {
            ItemStack s = pc.backpack().get(base + i);
            if (s == null) continue;
            float sx = bagX + (i % 5) * slotSize;
            float sy = bagY + (3 - i / 5) * slotSize;
            TextureRegion r = art.item(s.itemId());
            if (dragging && base + i == dragSlot) batch.setColor(1f, 1f, 1f, 0.35f); // przeciagany - blady w miejscu zrodla
            float ip = 1 + slotPad(slotSize - 2);
            if (r != null) drawStackIcon(batch, r, s, sx + ip, sy + ip, slotSize - ip * 2);
            batch.setColor(Color.WHITE);
            if (s.count() > 1) text(batch, String.valueOf(s.count()), sx + 5, sy + 15);
            if (s.upgrade() > 0) text(batch, "+" + s.upgrade(), sx + slotSize - 24, sy + slotSize - 6);
        }
        if (dragging && dragSlot >= 0 && dragSlot < pc.backpack().size() && pc.backpack().get(dragSlot) != null) {
            TextureRegion r = art.item(pc.backpack().get(dragSlot).itemId());
            if (r != null) batch.draw(r, dragX - slotSize * 0.45f, dragY - slotSize * 0.45f, slotSize * 0.9f, slotSize * 0.9f);
        }
        if (selectedSlot >= 0) {
            ItemStack s = pc.backpack().get(selectedSlot);
            if (s != null) {
                ItemType t = session.items().get(s.itemId());
                // opis nad plecakiem: statystyki z roznica wzgledem zalozonego przedmiotu i wymagania
                ItemStack worn = t.slot() == EquipSlot.NONE ? null : pc.equipment().get(t.slot());
                ItemType wornType = worn == null ? null : session.items().get(worn.itemId());
                float ty = bagY + slotSize * 4 + 66;
                text(batch, pl.fawizja.pixel.item.ItemCompare.stats(t, s.upgrade(), wornType, worn == null ? 0 : worn.upgrade()), bagTipX() + 6, ty);
                String req = pl.fawizja.pixel.item.ItemCompare.requirements(t, pc);
                if (!req.isEmpty()) text(batch, req, bagTipX() + 6, ty - 17);
                if (t.slot() != EquipSlot.NONE) centered(batch, bagEquip.label, bagEquip.x, bagEquip.y);
                centered(batch, bagDrop.label, bagDrop.x, bagDrop.y);
                centered(batch, bagUse.label, bagUse.x, bagUse.y);
                centered(batch, pc.isKept(s.itemId()) ? "ODBLOKUJ" : bagKeep.label, bagKeep.x, bagKeep.y);
            }
        } else {
            centered(batch, bagSort.label, bagSort.x, bagSort.y);
            if (bagPages > 1 && bagPage == bagPages - 1) centered(batch, bagDetach.label, bagDetach.x, bagDetach.y);
        }
    }

    private void bar(ShapeRenderer s, float x, float y, float width, float height, float fill, Color c) {
        s.setColor(0, 0, 0, 0.55f);
        s.rect(x - 1, y - 1, width + 2, height + 2);
        s.setColor(c);
        s.rect(x, y, width * Math.max(0f, Math.min(1f, fill)), height);
        if (barPatch != null) barRects.add(new float[] {x - 1, y - 1, width + 2, height + 2});
    }

    /** Nakladka paska (cieniowanie i ramka z OTClientV8) na paski narysowane w tej klatce. */
    private void drawBarOverlays(SpriteBatch batch) {
        if (barPatch == null) return;
        for (float[] r : barRects) barPatch.draw(batch, r[0], r[1], r[2], r[3]);
        barRects.clear();
    }

    private void text(SpriteBatch batch, String s, float x, float y) {
        font.draw(batch, s, x, y);
    }

    private void centered(SpriteBatch batch, String s, float cx, float cy) {
        layout.setText(font, s);
        font.draw(batch, s, cx - layout.width / 2f, cy + layout.height / 2f);
    }

    // ------------------------------------------------------------------ telefon: uklad wariant B (MobileHudLayout)

    /** Przytrzymanie przycisku luku otwiera wybor akcji (makieta: 0,5 s). */
    private static final long MOBILE_LONG_PRESS_MS = 500;
    private Actions mBattleActions;

    private static Color hex(int rgb, float a) {
        return new Color(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, a);
    }

    /** Kolory makiety (drewno, pola, napisy). */
    private static final Color M_WOOD = hex(0x3b2618, 1f), M_DARK = hex(0x1e120b, 1f), M_LINE = hex(0x7a5236, 1f),
            M_BTN = hex(0x4a2f1d, 1f), M_TITLE = hex(0x5c3b25, 1f), M_CELL = hex(0x2a1a10, 1f), M_CELL_LINE = hex(0x120a05, 1f),
            M_TEXT = hex(0xefe4d4, 1f), M_MUTED = hex(0xb8a48c, 1f), M_GOOD = hex(0x7ee06f, 1f), M_SEL = hex(0x2f5a25, 1f),
            M_CLOSE = hex(0xa3231c, 1f), M_EDGE = hex(0x5c4330, 1f), M_RING = hex(0xe8d7bd, 0.75f), M_BG = hex(0x140c06, 0.72f);

    private static final String[] M_ICON_LABELS = {"Plecak", "Ekwipunek", "Umiejętn.", "VIP", "Zadania", "Ustawienia", "Sklep", "Wyloguj"};

    /** Polska nazwa profesji (tytul okna wyboru: "Rycerz, poz. 42"). */
    private static String vocationName(pl.fawizja.pixel.vocation.Vocation v) {
        switch (v) {
            case KNIGHT: return "Rycerz";
            case PALADIN: return "Paladyn";
            case DRUID: return "Druid";
            case SORCERER: return "Czarodziej";
            case MONK: return "Mnich";
            default: return capitalized(v.name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    /** Przycisk okragly w miejscu kola z ukladu (pole dotyku liczone z kola). */
    private static void placeRound(HudButton b, MobileHudLayout.Circle c) {
        b.x = c.x;
        b.y = c.y;
        b.r = c.r;
        b.hw = 0;
    }

    /** Przyciski chwilowe na telefonie (widoczne tylko, gdy maja sens): rozmowa, podniesienie, lowienie, kon. */
    private HudButton[] mobileContext() { return new HudButton[] {talk, pick, fish, mount}; }

    private void layoutMobile() {
        mlay.compute(w, h);
        gx = 0;
        gy = 0;
        gw = w;
        gh = h;
        topH = 0;
        panelX = w;
        for (int i = 0; i < MobileHudLayout.SPELL_SLOTS.length; i++) placeRound(spellButtons[MobileHudLayout.SPELL_SLOTS[i]], mlay.spells[i]);
        for (int i = 0; i < MobileHudLayout.POTION_SLOTS.length; i++) placeRound(spellButtons[MobileHudLayout.POTION_SLOTS[i]], mlay.potions[i]);
        placeRound(target, mlay.row[MobileHudLayout.ROW_ATTACK]);
        placeRound(run, mlay.row[MobileHudLayout.ROW_FOLLOW]);
        placeRound(defensive, mlay.row[MobileHudLayout.ROW_SHIELD]);
        placeRound(pvp, mlay.row[MobileHudLayout.ROW_PVP]);
        int k = 0;
        for (HudButton b : mobileContext()) {
            if (b.visible && k < MobileHudLayout.CONTEXT) placeRound(b, mlay.context[k++]);
            else b.x = -1000;
        }
        sideButtonsLaidOut = visibleActionMask();
        mmX = mlay.minimap.x;
        mmY = mlay.minimap.y;
        mmSize = mlay.minimap.w;
        blX = mlay.battle.x;
        blY = mlay.battle.y;
        blW = mlay.battle.w;
        blH = mlay.battle.h;
        // czat rozwiniety: zakladki u gory panelu, wiersze, linia pisania na dole (26 dp)
        conTabH = 17;
        conX = mlay.chatPanel.x;
        conY = 26;
        conW = mlay.chatPanel.w;
        conH = MobileHudLayout.CHAT_H - 26;
        float jr = MobileHudLayout.JOY_R * (settings != null ? settings.joystickScale() : 1f);
        joystick.layout(MobileHudLayout.JOY_X, MobileHudLayout.JOY_X, jr);
    }

    /** Polozenie okien na telefonie: okna kategorii pod ramka (336 dp), duze okna (NPC, ksiega) nad cala szerokoscia. */
    private void mobileMenuGeometry() {
        popupCols = 1;
        rowH = 36;
        if (pickSlot >= 0) {
            setMenuRect(mlay.picker(mRect));
            rowH = MobileHudLayout.PICK_ROW;
        } else if (logoutConfirm) {
            setMenuRect(mlay.window(mChat, true, mRect));
        } else if (achievementsView && journalMode) {
            setMenuRect(mlay.window(mChat, false, mRect));
            rowH = 34;
        } else if (playerMenu != null || optionsMenu) {
            int rows = optionsMenu ? 2 : playerRowsCount;
            float room = h - 46 - 6;
            menuW = 240;
            menuH = 24 + 20 + rows * rowH + 8;
            if (menuH > room && rows > 1) {
                popupCols = 2;
                menuW = Math.min(w - 16, 400);
                menuH = 24 + 20 + ((rows + 1) / 2) * rowH + 8;
            }
            menuX = (w - menuW) / 2f;
            menuY = Math.max(4, (h - 46 - menuH) / 2f);
        } else {
            menuW = Math.min(w - 16, 620);
            menuH = Math.min(h - 46 - 6, 400);
            menuX = (w - menuW) / 2f;
            menuY = h - 46 - menuH;
            rowH = Math.max(26, Math.min(36, (menuH - 50) / 10f));
        }
        // czerwony krzyzyk 19 x 18 w pasku tytulu; pole dotyku ok. 36 dp
        menuClose.r = 18;
        menuClose.x = menuX + menuW - 14.5f;
        menuClose.y = menuY + menuH - 13;
    }

    private void setMenuRect(HudLayout.Rect r) {
        menuX = r.x;
        menuY = r.y;
        menuW = r.w;
        menuH = r.h;
    }

    /** Czerwony krzyzyk okna: pole dotyku 36 x 30 w prawym gornym rogu. */
    private static boolean closeHit(HudLayout.Rect win, float px, float py) {
        return px >= win.right() - 36 && px <= win.right() + 2 && py >= win.top() - 30 && py <= win.top() + 2;
    }

    /** Otwiera okno kategorii (ponowne dotkniecie tej samej ikony zamyka); zamyka inne okna - tylko jedno naraz. */
    private void openMobileWin(MobileWin win) {
        selectedSlot = -1;
        if (mWin == win) {
            mWin = null;
            return;
        }
        if (trade != null) return; // okno handlu ma pierwszenstwo
        closeMenus();
        mWin = win;
        if (win == MobileWin.BAG) bagPage = 0;
    }

    /** Zamyka okna menu (ksiega, NPC, dziennik, wybor, okienka) i mape. */
    private void closeMenus() {
        menuOpen = false;
        logoutConfirm = false;
        optionsMenu = false;
        playerMenu = null;
        rankingView = null;
        friendsView = null;
        marketView = null;
        achievementsView = false;
        journalMode = false;
        npcMode = false;
        pickSlot = -1;
        pickCat = null;
        assignSlot = -1;
        bigMapOpen = false;
        charOpen = false;
        bagOpen = false;
    }

    /** Dotkniecie ikony ramki: okno kategorii, dziennik zadan albo wylogowanie (z blokada PvP w GameScreen). */
    private void mobileIcon(int icon, Actions actions) {
        switch (icon) {
            case MobileHudLayout.ICON_BAG: openMobileWin(MobileWin.BAG); break;
            case MobileHudLayout.ICON_EQUIP: openMobileWin(MobileWin.EQUIP); break;
            case MobileHudLayout.ICON_SKILLS: openMobileWin(MobileWin.SKILLS); break;
            case MobileHudLayout.ICON_VIP: openMobileWin(MobileWin.VIP); break;
            case MobileHudLayout.ICON_SETTINGS: openMobileWin(MobileWin.SETTINGS); break;
            case MobileHudLayout.ICON_QUESTS:
                mWin = null;
                if (!journalOpen()) closeMenus();
                openJournal();
                break;
            case MobileHudLayout.ICON_SHOP:
                mWin = null;
                if (storeOpen()) closeMenus();
                else actions.onButton("store");
                break;
            default:
                mWin = null;
                if (logoutConfirmOpen()) closeLogoutConfirm();
                else {
                    closeMenus();
                    actions.onButton("logout");
                }
        }
    }

    /** Czy ikona ramki jest wlaczona (jej okno otwarte). */
    private boolean mobileIconActive(int icon) {
        switch (icon) {
            case MobileHudLayout.ICON_BAG: return mWin == MobileWin.BAG;
            case MobileHudLayout.ICON_EQUIP: return mWin == MobileWin.EQUIP;
            case MobileHudLayout.ICON_SKILLS: return mWin == MobileWin.SKILLS;
            case MobileHudLayout.ICON_VIP: return mWin == MobileWin.VIP;
            case MobileHudLayout.ICON_SETTINGS: return mWin == MobileWin.SETTINGS;
            case MobileHudLayout.ICON_QUESTS: return journalOpen();
            case MobileHudLayout.ICON_SHOP: return storeOpen();
            default: return logoutConfirmOpen();
        }
    }

    /** Okno kategorii otwarte z ikon ramki (podglad: -Dpixel.open=mobile_win_skills itd.). */
    public void openMobileWindow(String name) {
        switch (name) {
            case "bag": openMobileWin(MobileWin.BAG); break;
            case "equip": openMobileWin(MobileWin.EQUIP); break;
            case "skills": openMobileWin(MobileWin.SKILLS); break;
            case "vip": openMobileWin(MobileWin.VIP); break;
            case "settings": openMobileWin(MobileWin.SETTINGS); break;
            default:
        }
    }

    /** Rozwija albo zwija czat (telefon; podglad: -Dpixel.open=mobile_chat). */
    public void setMobileChat(boolean open) { mChat = open; }

    /** Wybor akcji przycisku luku nr i (0..4 czary, 5..6 mikstury) - jak po przytrzymaniu. */
    public void openMobilePicker(int button) {
        int slot = button < MobileHudLayout.SPELL_SLOTS.length ? MobileHudLayout.SPELL_SLOTS[button]
                : MobileHudLayout.POTION_SLOTS[Math.min(MobileHudLayout.POTION_SLOTS.length - 1, button - MobileHudLayout.SPELL_SLOTS.length)];
        mWin = null;
        mPickPage = 0;
        openPicker(slot);
    }

    /** Numer przycisku luku (1..7) dla pola paska. */
    private static int arcNumber(int slot) {
        for (int i = 0; i < MobileHudLayout.SPELL_SLOTS.length; i++) if (MobileHudLayout.SPELL_SLOTS[i] == slot) return i + 1;
        for (int i = 0; i < MobileHudLayout.POTION_SLOTS.length; i++) if (MobileHudLayout.POTION_SLOTS[i] == slot) return MobileHudLayout.SPELL_SLOTS.length + i + 1;
        return slot + 1;
    }

    /** Pole paska pod przyciskiem: przytrzymanie = wybor akcji, krotkie dotkniecie = uzycie (po puszczeniu). */
    private void slotPress(int slot, int pointer, Actions actions) {
        if (barEntries[slot] == null) {
            mWin = null;
            mPickPage = 0;
            openPicker(slot);
            return;
        }
        pressSlot = slot;
        pressPointer = pointer;
        pressStart = com.badlogic.gdx.utils.TimeUtils.millis();
        pressActions = actions;
        mPickPage = 0;
    }

    private boolean mobileTouchDown(float px, float py, int pointer, GameSession session, Actions actions) {
        if (bigMapOpen) {
            bigMapTap(px, py, session, actions);
            return true;
        }
        if (menuOpen) {
            if (menuClose.hit(px, py)) {
                menuCloseAction(actions);
                return true;
            }
            if (menuRowTouch(px, py, session, actions)) return true;
            if (px >= menuX && px <= menuX + menuW && py >= menuY && py <= menuY + menuH) return true;
            if (pickSlot >= 0) {
                // dotkniecie obok okna wyboru zamyka je
                menuOpen = false;
                pickSlot = -1;
                return true;
            }
        }
        if (mWin != null) {
            HudLayout.Rect win = mlay.window(mChat, false, mRect);
            if (closeHit(win, px, py)) {
                mWin = null;
                selectedSlot = -1;
                return true;
            }
            if (win.contains(px, py)) {
                mobileWinTouch(win, px, py, session, actions);
                return true;
            }
        }
        int icon = mlay.iconAt(px, py);
        if (icon >= 0) {
            mobileIcon(icon, actions);
            return true;
        }
        if (mlay.frame.contains(px, py)) {
            // paski HP i many: okno umiejetnosci (jak karta postaci w starym ukladzie)
            openMobileWin(MobileWin.SKILLS);
            return true;
        }
        if (mobileStatusTouch(px, py, session)) return true;
        if (mlay.minimap.contains(px, py)) {
            toggleMap();
            menuGeometry();
            return true;
        }
        if (mBattleBox().contains(px, py)) {
            mBattlePointer = pointer;
            mBattleDownY = py;
            mBattleDragged = false;
            mBattleScrollAtDown = mBattleScroll;
            mBattleActions = actions;
            return true;
        }
        for (int i = 0; i < mlay.spells.length; i++) {
            if (mlay.spells[i].hit(px, py)) {
                slotPress(MobileHudLayout.SPELL_SLOTS[i], pointer, actions);
                return true;
            }
        }
        for (int i = 0; i < mlay.potions.length; i++) {
            if (mlay.potions[i].hit(px, py)) {
                slotPress(MobileHudLayout.POTION_SLOTS[i], pointer, actions);
                return true;
            }
        }
        for (HudButton b : new HudButton[] {target, run, defensive, pvp}) {
            if (roundHit(b, px, py)) {
                actions.onButton(b.id);
                return true;
            }
        }
        for (HudButton b : mobileContext()) {
            if (b.visible && roundHit(b, px, py)) {
                actions.onButton(b.id);
                return true;
            }
        }
        if (mChat) {
            if (mlay.chatHandleHit.contains(px, py)) {
                mChat = false;
                return true;
            }
            if (mlay.chatPanel.contains(px, py)) {
                if (py >= conY + conH - conTabH - 4) {
                    for (int i = 0; i < tabRects.size(); i++) {
                        float[] r = tabRects.get(i);
                        if (px < r[0] || px > r[0] + r[1]) continue;
                        if (r[2] >= 0 && px >= r[2]) chatTabs.close(i);
                        else chatTabs.select(i);
                        layoutTabs();
                        return true;
                    }
                    return true;
                }
                actions.onButton("chat");
                return true;
            }
        } else if (mlay.chatTabHit.contains(px, py)) {
            mChat = true;
            return true;
        }
        if (joystick.touchDown(px, py, pointer)) return true;
        return false; // reszta ekranu to widok gry
    }

    /** Pole dotyku przycisku okraglego: co najmniej 36 dp srednicy. */
    private static boolean roundHit(HudButton b, float px, float py) {
        float dx = px - b.x, dy = py - b.y, r = Math.max(b.r, MobileHudLayout.MIN_TOUCH / 2f);
        return b.x > -999 && dx * dx + dy * dy <= r * r;
    }

    /** Lista walki tak wysoka, jak jej wiersze (co najmniej jeden), zeby pusta czesc nie zaslaniala widoku gry. */
    private HudLayout.Rect mBattleBox() {
        HudLayout.Rect bl = mlay.battle;
        int rows = Math.max(1, Math.min(mlay.battleRowsVisible(), mobileBattleCount()));
        float bh = Math.min(bl.h, MobileHudLayout.BATTLE_HEAD + rows * MobileHudLayout.BATTLE_ROW + 4);
        mBattleRect.set(bl.x, bl.top() - bh, bl.w, bh);
        return mBattleRect;
    }

    private final HudLayout.Rect mBattleRect = new HudLayout.Rect();

    /** Wiersze listy walki: gracze, potem potwory. */
    private int mobileBattleCount() { return battlePlayers.size() + battle.size(); }

    /** Dotkniecie wiersza listy walki (po puszczeniu bez przewiniecia): gracz - okienko, potwor - cel. */
    private void mobileBattleTap(float py) {
        int i = (int) Math.floor((mlay.battle.top() - MobileHudLayout.BATTLE_HEAD - py) / MobileHudLayout.BATTLE_ROW);
        if (i < 0) return;
        i += mBattleScroll;
        if (i < battlePlayers.size()) mBattleActions.onButton("bplayer:" + battlePlayers.get(i).id());
        else if (i - battlePlayers.size() < battle.size()) mBattleActions.onButton("battle:" + battle.get(i - battlePlayers.size()).id());
    }

    /** Wiersz ikon stanu i wzmocnien pod ramka: dotkniecie opisuje je w konsoli (pole 36 dp wysokosci). */
    private boolean mobileStatusTouch(float px, float py, GameSession session) {
        int n = statuses.size() + buffs.size() + (ownSkull != pl.fawizja.pixel.pvp.PvpRules.Skull.NONE || ownSkullMs > 0 ? 1 : 0);
        if (n == 0) return false;
        float x0 = mlay.statusRow.x, x1 = x0 + Math.max(MobileHudLayout.MIN_TOUCH, n * (STATUS_SIZE + 3) + 4);
        if (px < x0 - 4 || px > x1 || py > mlay.frame.y || py < mlay.frame.y - MobileHudLayout.MIN_TOUCH) return false;
        long now = session.lastUpdateMs();
        for (Status st : statuses) session.log().add(now, EventLog.Kind.INFO, st.desc);
        if (ownSkull != pl.fawizja.pixel.pvp.PvpRules.Skull.NONE) {
            session.log().add(now, EventLog.Kind.INFO, skullName(ownSkull) + ": jeszcze " + skullTime(ownSkullMs) + ".");
        } else if (ownSkullMs > 0) {
            session.log().add(now, EventLog.Kind.INFO, "Blokada strefy ochronnej po walce: jeszcze " + skullTime(ownSkullMs) + ".");
        }
        for (Buff b : buffs) session.log().add(now, EventLog.Kind.INFO, b.desc());
        return true;
    }

    // ------------------------------------------------------------------ telefon: okna kategorii

    /** Pole plecaka nr i (10 x 2 na strone). */
    private HudLayout.Rect mBagCell(HudLayout.Rect win, int i, HudLayout.Rect out) {
        float cell = 30, pitch = 31.5f;
        float x0 = win.x + (win.w - (9 * pitch + cell)) / 2f;
        float top = win.top() - 26 - 4;
        out.set(x0 + (i % 10) * pitch, top - cell - (i / 10) * (cell + 3), cell, cell);
        return out;
    }

    /** Przycisk w oknie: akcja, napis, prostokat. */
    private record MBtn(String id, String label, float x, float y, float w, float h) {
        boolean hit(float px, float py) { return px >= x && px <= x + w && py >= y && py <= y + h; }
    }

    /** Przyciski pod plecakiem: z zaznaczonym przedmiotem Zaloz/Uzyj/Wyrzuc/Zachowaj, inaczej Sortuj i strony. */
    private List<MBtn> mBagButtons(HudLayout.Rect win, GameSession session) {
        List<MBtn> out = new ArrayList<>();
        PlayerCharacter pc = session.character();
        float by = win.y + 6, bh = 36, x = win.x + 8;
        ItemStack sel = selectedSlot >= 0 && selectedSlot < pc.backpack().size() ? pc.backpack().get(selectedSlot) : null;
        if (sel != null) {
            ItemType t = session.items().get(sel.itemId());
            float bw = (win.w - 16 - 3 * 6) / 4f;
            if (t.slot() != EquipSlot.NONE) out.add(new MBtn("bag_equip", "Załóż", x, by, bw, bh));
            out.add(new MBtn("bag_use", "Użyj", x + (bw + 6), by, bw, bh));
            out.add(new MBtn("bag_drop", "Wyrzuć", x + 2 * (bw + 6), by, bw, bh));
            out.add(new MBtn("bag_keep", pc.isKept(sel.itemId()) ? "Odblokuj" : "Zachowaj", x + 3 * (bw + 6), by, bw, bh));
            return out;
        }
        out.add(new MBtn("bag_sort", "Sortuj", x, by, 76, bh));
        bagPages = pc.bagPages();
        float px = x + 82;
        for (int k = 0; k < bagPages && bagPages > 1; k++) {
            out.add(new MBtn("mbag:page:" + k, String.valueOf(k + 1), px, by, 36, bh));
            px += 40;
        }
        if (bagPages > 1 && bagPage == bagPages - 1) out.add(new MBtn("bag_detach", "Odepnij", win.right() - 8 - 80, by, 80, bh));
        return out;
    }

    /** Pola okna ekwipunku: lalka 3 x 4 jak w Tibii (9 miejsc, plecak w prawym gornym rogu, wzmocnienia obok butow). */
    private float[] mEqCell(HudLayout.Rect win, int col, int row) {
        float cell = 36, gx0 = win.x + 70, top = win.top() - 26 - 4;
        return new float[] {gx0 + col * (cell + 6), top - cell - row * (cell + 2), cell};
    }

    /** Wpisy okna ustawien: {akcja, nazwa, wartosc}; pozostale przyciski starego ukladu tez tutaj. */
    private List<String[]> mSettingsEntries(GameSession session) {
        PlayerCharacter pc = session.character();
        List<String[]> out = new ArrayList<>();
        out.add(new String[] {"run", "Podążaj za celem", pc.isRunning() ? "włączone" : "wyłączone"});
        int torchId = session.items().byKey("torch").id();
        String torchState = !pc.hasTorchEquipped(torchId) ? "nie założona"
                : (pc.isTorchLit() ? "zapalona, " : "zgaszona, ") + GameSession.torchTime(pc.torchRemainingMs(torchId));
        out.add(new String[] {"torch", "Pochodnia", torchState});
        out.add(new String[] {"book", "Księga czarów", "czary i rzemiosło (K)"});
        out.add(new String[] {"diag", "Diagnostyka", diagnosticsOpen ? "pokazana" : "ukryta"});
        if (settings != null) {
            for (String[] r : settings.allRows()) {
                int c = r[1].indexOf(": ");
                out.add(c > 0 ? new String[] {r[0], r[1].substring(0, c), r[1].substring(c + 2)} : new String[] {r[0], r[1], ""});
            }
        }
        return out;
    }

    /** Wiersze okna ustawien na biezacej stronie (2 kolumny po 36 dp, ostatni wiersz "Dalej", gdy stron jest wiecej). */
    private List<MBtn> mSettingsButtons(HudLayout.Rect win, List<String[]> entries) {
        float rh = 36, gap = 3, cw = (win.w - 16 - 6) / 2f;
        int rows = Math.max(1, (int) ((win.h - 26 - 6 + gap) / (rh + gap)));
        int per = rows * 2;
        int pages = entries.size() <= per ? 1 : (entries.size() + per - 2) / (per - 1);
        if (mSettingsPage >= pages) mSettingsPage = 0;
        int shown = pages == 1 ? per : per - 1;
        List<MBtn> out = new ArrayList<>();
        int from = mSettingsPage * shown;
        int k = 0;
        for (int i = from; i < Math.min(entries.size(), from + shown); i++, k++) {
            out.add(new MBtn("set:" + i, "", win.x + 8 + (k % 2) * (cw + 6), win.top() - 26 - 2 - (k / 2 + 1) * rh - (k / 2) * gap, cw, rh));
        }
        if (pages > 1) {
            k = per - 1;
            out.add(new MBtn("set:page", "Dalej (" + (mSettingsPage + 1) + " z " + pages + ")",
                    win.x + 8 + (k % 2) * (cw + 6), win.top() - 26 - 2 - (k / 2 + 1) * rh - (k / 2) * gap, cw, rh));
        }
        return out;
    }

    /** Wiersze okna VIP (22 dp) i przycisk dodawania. */
    private static final float M_VIP_ROW = 22;

    private MBtn mVipAdd(HudLayout.Rect win) { return new MBtn("vip:add", "+ Dodaj znajomego", win.right() - 8 - 150, win.y + 6, 150, 36); }

    private void mobileWinTouch(HudLayout.Rect win, float px, float py, GameSession session, Actions actions) {
        PlayerCharacter pc = session.character();
        switch (mWin) {
            case BAG: {
                for (MBtn b : mBagButtons(win, session)) {
                    if (!b.hit(px, py)) continue;
                    if (b.id().startsWith("mbag:page:")) {
                        bagPage = Integer.parseInt(b.id().substring(10));
                        selectedSlot = -1;
                    } else {
                        actions.onButton(b.id());
                        if (b.id().equals("bag_detach")) bagPage = Math.max(0, bagPage - 1);
                    }
                    return;
                }
                HudLayout.Rect c = new HudLayout.Rect();
                for (int i = 0; i < PAGE_SLOTS; i++) {
                    if (!mBagCell(win, i, c).contains(px, py)) continue;
                    int slot = bagPage * PAGE_SLOTS + i;
                    if (slot >= pc.backpack().size()) return;
                    if (selectedSlot < 0) {
                        if (pc.backpack().get(slot) != null) selectedSlot = slot;
                    } else if (selectedSlot == slot) {
                        selectedSlot = -1;
                    } else {
                        actions.onButton("slot_move:" + selectedSlot + ":" + slot);
                        selectedSlot = -1;
                    }
                    return;
                }
                return;
            }
            case EQUIP: {
                for (int i = 0; i < EQ_SLOTS.length; i++) {
                    float[] c = mEqCell(win, EQ_CELLS[i][0], EQ_CELLS[i][1]);
                    if (px >= c[0] && px <= c[0] + c[2] && py >= c[1] && py <= c[1] + c[2]) {
                        equipTap(i, session, actions);
                        return;
                    }
                }
                float[] bp = mEqCell(win, 2, 0);
                if (px >= bp[0] && px <= bp[0] + bp[2] && py >= bp[1] && py <= bp[1] + bp[2]) {
                    openMobileWin(MobileWin.BAG);
                    return;
                }
                float[] bc = mEqCell(win, 0, 3);
                if (px >= bc[0] && px <= bc[0] + bc[2] && py >= bc[1] && py <= bc[1] + bc[2]) {
                    long now = session.lastUpdateMs();
                    for (Buff b : buffs) session.log().add(now, EventLog.Kind.INFO, b.desc());
                    if (buffs.isEmpty()) session.log().add(now, EventLog.Kind.INFO, "Brak aktywnych wzmocnień.");
                }
                return;
            }
            case VIP: {
                if (mVipAdd(win).hit(px, py)) {
                    actions.onButton("vip:add");
                    return;
                }
                List<pl.fawizja.pixel.friends.FriendsView.Row> rows = vipRows();
                int i = (int) ((win.top() - 26 - 4 - py) / M_VIP_ROW);
                if (i >= 0 && i < rows.size() && py > win.y + 46) {
                    mWin = null;
                    openPlayerMenu(-1, rows.get(i).name(), false, false);
                }
                return;
            }
            case SETTINGS: {
                List<String[]> entries = mSettingsEntries(session);
                for (MBtn b : mSettingsButtons(win, entries)) {
                    if (!b.hit(px, py)) continue;
                    if (b.id().equals("set:page")) {
                        mSettingsPage++;
                    } else {
                        String action = entries.get(Integer.parseInt(b.id().substring(4)))[0];
                        actions.onButton(action);
                    }
                    return;
                }
                return;
            }
            default:
        }
    }

    // ------------------------------------------------------------------ telefon: wybor akcji przycisku luku

    /**
     * Wybor akcji przycisku luku (makieta, przytrzymanie 0,5 s): czary i runy profesji (spells.csv, '*' = wszyscy)
     * i mikstury z plecaka w dwoch kolumnach; czary powyzej poziomu postaci szare ("od poziomu X") i niedostepne.
     */
    private void rebuildMobilePick(GameSession session) {
        PlayerCharacter pc = session.character();
        pl.fawizja.pixel.magic.SpellRegistry reg = session.spells().registry();
        int magic = pc.skills().level(Skill.MAGIC);
        List<MenuRow> ok = new ArrayList<>(), locked = new ArrayList<>();
        for (SpellType sp : reg.forVocation(pc.vocation())) {
            boolean lv = pc.level() >= sp.minLevel(), mg = magic >= sp.minMagic();
            if (lv && mg) ok.add(new MenuRow("pick:set:cast:" + sp.key(), sp.name(), "poziom " + sp.minLevel(), true));
            else if (sp.minLevel() <= pc.level() + 100) {
                locked.add(new MenuRow("pick:none", sp.name(), lv ? "od magii " + sp.minMagic() : "od poziomu " + sp.minLevel(), false));
            }
        }
        List<ItemType> runes = new ArrayList<>(), potions = new ArrayList<>();
        for (ItemType it : session.items().all()) {
            if (!it.usableBy(pc.vocation())) continue;
            if (it.category() == pl.fawizja.pixel.item.ItemCategory.RUNE && reg.find(it.key()) != null) runes.add(it);
            if (it.category() == pl.fawizja.pixel.item.ItemCategory.POTION && pc.backpack().count(it.id()) > 0) potions.add(it);
        }
        runes.sort(java.util.Comparator.comparingInt((ItemType it) -> reg.find(it.key()).minLevel()).thenComparing(ItemType::name));
        for (ItemType it : runes) {
            SpellType rs = reg.find(it.key());
            int lvl = Math.max(it.minLevel(), rs.minLevel());
            boolean lv = pc.level() >= lvl, mg = magic >= rs.minMagic();
            if (lv && mg) ok.add(new MenuRow("pick:set:item:" + it.key(), rs.name(), "poziom " + lvl, true));
            else if (lvl <= pc.level() + 100) locked.add(new MenuRow("pick:none", rs.name(), lv ? "od magii " + rs.minMagic() : "od poziomu " + lvl, false));
        }
        potions.sort(java.util.Comparator.comparingInt(ItemType::minLevel).thenComparing(ItemType::name));
        for (ItemType it : potions) {
            boolean lv = pc.level() >= it.minLevel();
            (lv ? ok : locked).add(new MenuRow(lv ? "pick:set:item:" + it.key() : "pick:none", it.name(),
                    lv ? "z plecaka (" + pc.backpack().count(it.id()) + ")" : "od poziomu " + it.minLevel(), lv));
        }
        // najlepsza mikstura z plecaka (automat) - dziala tez, gdy plecak jest pusty
        ok.add(new MenuRow("pick:set:potion_hp", "Najlepsza mikstura zdrowia", "z plecaka", true));
        ok.add(new MenuRow("pick:set:potion_mp", "Najlepsza mikstura many", "z plecaka", true));
        List<MenuRow> all = new ArrayList<>(ok);
        all.addAll(locked);
        HudLayout.Rect pr = new HudLayout.Rect();
        pr.set(menuX, menuY, menuW, menuH);
        int per = mlay.pickerRows(pr) * 2;
        int pages = all.size() <= per ? 1 : (all.size() + per - 2) / (per - 1);
        if (mPickPage >= pages) mPickPage = 0;
        int shown = pages == 1 ? per : per - 1;
        List<MenuRow> page = new ArrayList<>(all.subList(mPickPage * shown, Math.min(all.size(), (mPickPage + 1) * shown)));
        if (pages > 1) page.add(new MenuRow("pick:page", "Dalej", "strona " + (mPickPage + 1) + " z " + pages, true));
        for (int i = 0; i < page.size(); i++) {
            MenuRow row = page.get(i);
            row.w = 144;
            row.h = 24;
            row.x = menuX + 8 + (i % 2) * 150;
            row.y = menuY + menuH - 30 - (i / 2) * MobileHudLayout.PICK_ROW - row.h;
            menuRows.add(row);
        }
    }

    /** Ikona akcji ("cast:", "item:", "potion_hp"): przedmiot albo null (czar - rysowany ksztaltami). */
    private TextureRegion actionIcon(GameSession session, String e) {
        if (e == null || e.startsWith("cast:")) return null;
        String key = e.equals("potion_hp") ? session.quickPotion(false) : e.equals("potion_mp") ? session.quickPotion(true) : e.substring(5);
        if (key == null) key = e.equals("potion_mp") ? "mana_potion" : "health_potion";
        return session.items().containsKey(key) ? itemIcon(session, key) : null;
    }

    /** Czar akcji albo null. */
    private static SpellType actionSpell(GameSession session, String e) {
        return e != null && e.startsWith("cast:") ? session.spells().registry().find(e.substring(5)) : null;
    }

    // ------------------------------------------------------------------ telefon: rysowanie

    /** Ramka linii o grubosci t (w przebiegu ksztaltow). */
    private static void strokeRect(ShapeRenderer s, float x, float y, float w, float h, float t, Color c) {
        s.setColor(c);
        s.rect(x, y, w, t);
        s.rect(x, y + h - t, w, t);
        s.rect(x, y, t, h);
        s.rect(x + w - t, y, t, h);
    }

    /** Okrag (pierscien) grubosci t z odcinkow; od kata start (stopnie, przeciwnie do wskazowek) przez deg. */
    private static void ring(ShapeRenderer s, float cx, float cy, float r, float t, float start, float deg, Color c) {
        s.setColor(c);
        int seg = Math.max(8, (int) (Math.abs(deg) / 8));
        float prevX = 0, prevY = 0;
        for (int i = 0; i <= seg; i++) {
            double a = Math.toRadians(start + deg * i / seg);
            float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
            if (i > 0) s.rectLine(prevX, prevY, x, y, t);
            prevX = x;
            prevY = y;
        }
    }

    /** Okno w stylu makiety: cien, drewniane tlo, pasek tytulu i czerwony krzyzyk. */
    private void mWindowShapes(ShapeRenderer s, float x, float y, float ww, float wh, boolean close) {
        s.setColor(0, 0, 0, 0.45f);
        s.rect(x + 3, y - 4, ww, wh);
        s.setColor(M_WOOD);
        s.rect(x, y, ww, wh);
        strokeRect(s, x, y, ww, wh, 1.5f, M_DARK);
        s.setColor(M_TITLE);
        s.rect(x + 2, y + wh - 24, ww - 4, 22);
        if (close) {
            float cx = x + ww - 24, cy = y + wh - 22;
            s.setColor(hex(0x3a0a07, 1f));
            s.rect(cx - 0.5f, cy - 0.5f, 20, 19);
            s.setColor(M_CLOSE);
            s.rect(cx, cy, 19, 18);
            s.setColor(Color.WHITE);
            s.rectLine(cx + 5, cy + 4, cx + 14, cy + 13, 2f);
            s.rectLine(cx + 14, cy + 4, cx + 5, cy + 13, 2f);
        }
    }

    /** Drewniany przycisk (ikona ramki, przyciski okien). */
    private static void woodButton(ShapeRenderer s, float x, float y, float bw, float bh, boolean active) {
        s.setColor(M_BTN);
        s.rect(x, y, bw, bh);
        strokeRect(s, x, y, bw, bh, active ? 2f : 1.5f, active ? M_GOOD : M_DARK);
        strokeRect(s, x + 2, y + 2, bw - 4, bh - 4, 1f, M_LINE);
    }

    /** Okragly przycisk z makiety: polprzezroczyste tlo i jasny pierscien (wlaczony - grubszy, w kolorze). */
    private static void roundButton(ShapeRenderer s, float cx, float cy, float r, Color ringColor, boolean hot) {
        s.setColor(M_BG);
        s.circle(cx, cy, r, 40);
        ring(s, cx, cy, r, hot ? 3f : 1.6f, 0, 360, ringColor != null ? ringColor : M_RING);
    }

    /** Kolko zebate (Ustawienia). */
    private static void gearGlyph(ShapeRenderer s, float cx, float cy, float r) {
        s.setColor(hex(0xc9c2b6, 1f));
        for (int i = 0; i < 8; i++) s.rect(cx - 2.4f, cy + r - 3, 2.4f, -(r - 3), 4.8f, 6, 1, 1, i * 45);
        s.circle(cx, cy, r, 24);
        s.setColor(M_BTN);
        s.circle(cx, cy, r * 0.42f, 16);
    }

    /** Znak wylaczenia (Wyloguj). */
    private static void powerGlyph(ShapeRenderer s, float cx, float cy) {
        Color c = hex(0xff6a5a, 1f);
        ring(s, cx, cy, 7.5f, 2.6f, 125, 290, c);
        s.setColor(c);
        s.rectLine(cx, cy + 1, cx, cy + 9, 2.6f);
    }

    /** Postac z gwiazdka (VIP). */
    private static void vipGlyph(ShapeRenderer s, float cx, float cy) {
        s.setColor(hex(0xd9c6a5, 1f));
        s.circle(cx - 2, cy + 5, 4.6f, 16);
        s.arc(cx - 2, cy - 8, 8, 0, 180, 16);
        s.setColor(hex(0xf2c94c, 1f));
        star(s, cx + 7, cy + 7, 5f);
    }

    /** Napis w skali sc (z obrysem dla czytelnosci na mapie, gdy outline). */
    private void mText(SpriteBatch b, String s, float x, float y, float sc, Color c, boolean outline) {
        font.getData().setScale(sc);
        y += font.getCapHeight(); // y = linia bazowa (BitmapFont rysuje od gornej krawedzi liter)
        if (outline) {
            font.setColor(0, 0, 0, 0.85f * c.a);
            font.draw(b, s, x + 0.7f, y - 0.7f);
            font.draw(b, s, x - 0.7f, y + 0.7f);
        }
        font.setColor(c);
        font.draw(b, s, x, y);
        font.setColor(Color.WHITE);
        font.getData().setScale(1f);
    }

    /** Napis wysrodkowany na (cx, cy) - srodek wysokosci wielkiej litery. */
    private void mCentered(SpriteBatch b, String s, float cx, float cy, float sc, Color c, boolean outline) {
        font.getData().setScale(sc);
        layout.setText(font, s);
        float x = cx - layout.width / 2f, y = cy - font.getCapHeight() / 2f;
        font.getData().setScale(1f);
        mText(b, s, x, y, sc, c, outline);
    }

    /** Szerokosc napisu w skali sc. */
    private float mWidth(String s, float sc) {
        font.getData().setScale(sc);
        layout.setText(font, s);
        font.getData().setScale(1f);
        return layout.width;
    }

    /** Napis przyciety do szerokosci room w skali sc. */
    private String mClip(String s, float room, float sc) {
        font.getData().setScale(sc);
        String out = clip(s, room);
        font.getData().setScale(1f);
        return out;
    }

    /** Ramka w batchu (biala tekstura). */
    private void bStroke(SpriteBatch b, float x, float y, float bw, float bh, float t, Color c) {
        b.setColor(c);
        TextureRegion px = art.white();
        b.draw(px, x, y, bw, t);
        b.draw(px, x, y + bh - t, bw, t);
        b.draw(px, x, y, t, bh);
        b.draw(px, x + bw - t, y, t, bh);
        b.setColor(Color.WHITE);
    }

    private void bFill(SpriteBatch b, float x, float y, float bw, float bh, Color c) {
        b.setColor(c);
        b.draw(art.white(), x, y, bw, bh);
        b.setColor(Color.WHITE);
    }

    /** Odnowienie akcji pola paska: 0..1 (czesc zakryta). */
    private float slotCooldown(GameSession session, int slot, long now) {
        String e = barEntries[slot];
        if (e == null) return 0;
        SpellType sp = actionSpell(session, e);
        if (sp == null && e.startsWith("item:")) sp = session.spells().registry().find(e.substring(5));
        long rem;
        float full;
        if (sp != null) {
            rem = session.spells().cooldowns().remaining(sp, now);
            full = Math.max(sp.cooldownMs(), sp.group().groupCooldownMs);
        } else {
            rem = session.spells().cooldowns().groupRemaining(pl.fawizja.pixel.magic.SpellGroup.POTION, now);
            full = pl.fawizja.pixel.magic.SpellGroup.POTION.groupCooldownMs;
        }
        return rem <= 0 ? 0f : Math.min(1f, rem / Math.max(1f, full));
    }

    /** Okragle pole czaru: kolor zywiolu, znak rodzaju, zaslona odnowienia i brak many. */
    private void spellDisc(ShapeRenderer s, SpellType sp, float cx, float cy, float r, float cd, boolean noMana) {
        Color c = spellColor(sp);
        s.setColor(c.r, c.g, c.b, 1f);
        s.circle(cx, cy, r, 32);
        spellGlyph(s, sp.kind(), cx, cy, r * 0.55f);
        if (cd > 0) {
            s.setColor(0, 0, 0, 0.6f);
            s.arc(cx, cy, r, 90, 360 * cd, 32);
        }
        if (noMana) {
            s.setColor(0.1f, 0.1f, 0.4f, 0.55f);
            s.circle(cx, cy, r, 32);
        }
    }

    private void renderMobile(SpriteBatch batch, ShapeRenderer shapes, GameSession session, WorldClock clock,
                              long serverTimeMs, DiagnosticsInfo diagInfo, Monster tgt) {
        PlayerCharacter pc = session.character();
        if (menuOpen && mWin != null) mWin = null; // tylko jedno okno naraz: nowe okno menu zamyka okno kategorii
        if (trade != null) mWin = null;
        if (bigMapOpen) menuGeometry();
        statusIcons(session, serverTimeMs);
        barRects.clear();
        int nb = mobileBattleCount(), rowsVis = mlay.battleRowsVisible();
        mBattleScroll = Math.max(0, Math.min(mBattleScroll, nb - rowsVis));
        long nowMs = com.badlogic.gdx.utils.TimeUtils.millis();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        batch.setProjectionMatrix(viewport.getCamera().combined);
        uiBatch = batch;
        uiShapes = shapes;

        // ---- warstwa 1: stale elementy HUD-u (ksztalty)
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        HudLayout.Rect f = mlay.frame;
        shapes.setColor(M_WOOD);
        shapes.rect(f.x, f.y, f.w, f.h);
        strokeRect(shapes, f.x, f.y, f.w, f.h, 1.5f, M_DARK);
        strokeRect(shapes, f.x + 2, f.y + 2, f.w - 4, f.h - 4, 1f, M_LINE);
        mBar(shapes, mlay.hpBar, pc.health() / (float) Math.max(1, pc.maxHealth()), hex(0xc8312c, 1f));
        mBar(shapes, mlay.mpBar, pc.mana() / (float) Math.max(1, pc.maxMana()), hex(0x2f63d6, 1f));
        for (int i = 0; i < MobileHudLayout.ICONS; i++) {
            HudLayout.Rect r = mlay.icons[i];
            woodButton(shapes, r.x, r.y, r.w, r.h, mobileIconActive(i));
            if (i == MobileHudLayout.ICON_VIP) vipGlyph(shapes, r.cx(), r.y + 18);
            if (i == MobileHudLayout.ICON_SETTINGS) gearGlyph(shapes, r.cx(), r.y + 19, 6.5f);
            if (i == MobileHudLayout.ICON_LOGOUT) powerGlyph(shapes, r.cx(), r.y + 17);
        }
        // ikony stanu pod ramka po lewej
        float sx = mlay.statusRow.x, sy = mlay.statusRow.y;
        for (Status st : statuses) {
            drawStatusGlyph(shapes, st, sx, sy, STATUS_SIZE);
            sx += STATUS_SIZE + 3;
        }
        for (Buff b : buffs) {
            if (b.icon() == null) drawStatusGlyph(shapes, b.glyph(), sx, sy, STATUS_SIZE);
            sx += STATUS_SIZE + 3;
        }
        // minimapa i lista walki
        HudLayout.Rect mm = mlay.minimap;
        shapes.setColor(M_DARK);
        shapes.rect(mm.x - 1, mm.y - 1, mm.w + 2, mm.h + 2);
        HudLayout.Rect bl = mBattleBox();
        if (bl.h > 0) {
            shapes.setColor(hex(0x140c06, 0.82f));
            shapes.rect(bl.x, bl.y, bl.w, bl.h);
            strokeRect(shapes, bl.x, bl.y, bl.w, bl.h, 1f, M_EDGE);
            if (nb > rowsVis + mBattleScroll) {
                // strzalka: ponizej sa kolejne wiersze
                float ax = bl.right() - 5 - mWidth(String.valueOf(nb - rowsVis - mBattleScroll), 0.64f) - 10, ay = bl.top() - 7;
                shapes.setColor(M_MUTED);
                shapes.triangle(ax, ay + 2.5f, ax + 6, ay + 2.5f, ax + 3, ay - 2.5f);
            }
        }
        // tlo luku czarow
        shapes.setColor(0.04f, 0.024f, 0.012f, 0.22f);
        float rr = MobileHudLayout.R1 + 26;
        shapes.arc(mlay.arcX, mlay.arcY, rr, 90, 90, 40);
        shapes.rect(mlay.arcX - rr, 0, rr, mlay.arcY);
        shapes.rect(mlay.arcX, 0, w - mlay.arcX, mlay.arcY + rr);
        // przyciski luku: czary i mikstury
        for (int i = 0; i < mlay.spells.length; i++) mSlotShapes(shapes, session, pc, MobileHudLayout.SPELL_SLOTS[i], mlay.spells[i], false, serverTimeMs);
        for (int i = 0; i < mlay.potions.length; i++) mSlotShapes(shapes, session, pc, MobileHudLayout.POTION_SLOTS[i], mlay.potions[i], true, serverTimeMs);
        if (pressSlot >= 0) {
            // przytrzymanie: zielony pierscien rosnie, az otworzy sie wybor
            float fr = Math.min(1f, (nowMs - pressStart) / (float) MOBILE_LONG_PRESS_MS);
            HudButton b = spellButtons[pressSlot];
            ring(shapes, b.x, b.y, b.r + 5.5f, 3f, 90, -360 * fr, M_GOOD);
        }
        // Atak / Tarcza / PvP i przyciski chwilowe
        roundButton(shapes, target.x, target.y, target.r, target.active ? hex(0xff6a4a, 1f) : null, target.active);
        roundButton(shapes, run.x, run.y, run.r, run.active ? hex(0x6ab8ff, 1f) : null, run.active);
        roundButton(shapes, defensive.x, defensive.y, defensive.r, defensive.active ? M_GOOD : null, defensive.active);
        roundButton(shapes, pvp.x, pvp.y, pvp.r, pvp.active ? hex(0xff4a3a, 1f) : null, pvp.active);
        for (HudButton b : mobileContext()) if (b.visible && b.x > -999) roundButton(shapes, b.x, b.y, b.r, b.active ? M_GOOD : null, b.active);
        // joystick: polprzezroczysta podstawa i galka
        float jr = joystick.radius();
        shapes.setColor(1, 1, 1, 0.07f);
        shapes.circle(joystick.baseX(), joystick.baseY(), jr, 48);
        ring(shapes, joystick.baseX(), joystick.baseY(), jr, 1.5f, 0, 360, new Color(1, 1, 1, 0.3f));
        float kr = jr * 22f / 54f;
        shapes.setColor(1, 1, 1, joystick.active() ? 0.26f : 0.18f);
        shapes.circle(joystick.knob().x, joystick.knob().y, kr, 32);
        ring(shapes, joystick.knob().x, joystick.knob().y, kr, 1.5f, 0, 360, new Color(1, 1, 1, 0.45f));
        // czat
        if (mChat) {
            HudLayout.Rect cp = mlay.chatPanel, hd = mlay.chatHandle;
            shapes.setColor(M_WOOD);
            shapes.rect(hd.x, hd.y, hd.w, hd.h);
            strokeRect(shapes, hd.x, hd.y, hd.w, hd.h, 1f, M_DARK);
            shapes.setColor(hex(0x24180f, 0.92f));
            shapes.rect(cp.x, cp.y, cp.w, cp.h);
            strokeRect(shapes, cp.x, cp.y, cp.w, cp.h, 1f, M_DARK);
            drawTabShapes(shapes);
            shapes.setColor(hex(0x160e08, 1f));
            shapes.rect(cp.x + 4, 4, cp.w - 8, 22);
            strokeRect(shapes, cp.x + 4, 4, cp.w - 8, 22, 1f, M_EDGE);
            mTriangle(shapes, hd.cx() + 18, hd.cy(), false);
        } else {
            HudLayout.Rect t = mlay.chatTab;
            shapes.setColor(hex(0x24180f, 0.88f));
            shapes.rect(t.x, t.y, t.w, t.h);
            strokeRect(shapes, t.x, t.y, t.w, t.h, 1f, M_DARK);
            mTriangle(shapes, t.cx() + 16, t.cy(), true);
        }
        // pasek celu pod ramka
        if (tgt != null && mWin == null && !menuOpen) {
            float tw = 200, tx = w / 2f - tw / 2f, ty = mlay.frame.y - 4 - 30;
            shapes.setColor(hex(0x140c06, 0.75f));
            shapes.rect(tx - 6, ty, tw + 12, 30);
            shapes.setColor(0, 0, 0, 0.6f);
            shapes.rect(tx, ty + 4, tw, 7);
            shapes.setColor(0.85f, 0.15f, 0.2f, 1f);
            shapes.rect(tx, ty + 4, tw * Math.max(0, Math.min(1, tgt.healthFraction())), 7);
        }
        if (diagnosticsOpen && diagInfo != null) {
            shapes.setColor(0, 0, 0, 0.7f);
            shapes.rect(6, mlay.statusRow.y - 4 - 16 * diagInfo.lines().size() - 6, 250, 16 * diagInfo.lines().size() + 6);
        }
        shapes.end();

        // ---- warstwa 1: napisy i obrazki
        batch.begin();
        String lv = "Lv " + pc.level();
        mText(batch, lv, 12, mlay.frame.cy() - 4f, 0.72f, M_TEXT, false);
        mCentered(batch, "HP " + pc.health() + " / " + pc.maxHealth(), mlay.hpBar.cx(), mlay.hpBar.cy(), HudLayout.MIN_FONT, Color.WHITE, true);
        mCentered(batch, "Mana " + pc.mana() + " / " + pc.maxMana(), mlay.mpBar.cx(), mlay.mpBar.cy(), HudLayout.MIN_FONT, Color.WHITE, true);
        TextureRegion[] iconImg = {art.backpack(), helmetIcon(session), itemIcon(session, "exp_scroll"), null, itemIcon(session, "challenge_scroll"), null,
                itemIcon(session, "crystal_coin"), null};
        for (int i = 0; i < MobileHudLayout.ICONS; i++) {
            HudLayout.Rect r = mlay.icons[i];
            if (iconImg[i] != null) batch.draw(iconImg[i], r.x + 5, r.y + 8, 22, 22);
            String label = M_ICON_LABELS[i];
            float sc = Math.min(0.42f, 0.42f * 30f / Math.max(1f, mWidth(label, 0.42f)));
            mCentered(batch, label, r.cx(), r.y + 4.5f, Math.max(0.3f, sc), M_TEXT, false);
        }
        sx = mlay.statusRow.x + statuses.size() * (STATUS_SIZE + 3);
        for (Buff b : buffs) {
            if (b.icon() != null) {
                bFill(batch, sx, sy, STATUS_SIZE, STATUS_SIZE, new Color(0, 0, 0, 0.55f));
                batch.draw(b.icon(), sx, sy, STATUS_SIZE, STATUS_SIZE);
            }
            sx += STATUS_SIZE + 3;
        }
        if (ownSkull != pl.fawizja.pixel.pvp.PvpRules.Skull.NONE || ownSkullMs > 0) {
            TextureRegion sk = skullIcon(ownSkull.name());
            if (sk != null) batch.draw(sk, sx, sy, STATUS_SIZE, STATUS_SIZE);
            String time = skullTime(ownSkullMs);
            mText(batch, time, sx + (sk != null ? STATUS_SIZE + 2 : 0), sy + 3.5f, HudLayout.MIN_FONT, Color.WHITE, true);
        }
        if (session.chests() != null) minimap.setHazards(session.chests().traps());
        minimap.drawAround(batch, session.world(), pc.position(), walkTarget, markerPositions(session), mm.x, mm.y, mm.w, mm.h, Math.round(mm.w / 1.5f));
        bStroke(batch, mm.x, mm.y, mm.w, mm.h, 1.5f, M_LINE);
        drawMobileBattle(batch, rowsVis);
        for (int i = 0; i < mlay.spells.length; i++) mSlotIcon(batch, session, pc, MobileHudLayout.SPELL_SLOTS[i], mlay.spells[i], false, serverTimeMs);
        for (int i = 0; i < mlay.potions.length; i++) mSlotIcon(batch, session, pc, MobileHudLayout.POTION_SLOTS[i], mlay.potions[i], true, serverTimeMs);
        if (pressSlot >= 0) {
            HudButton b = spellButtons[pressSlot];
            mText(batch, "przytrzymaj", b.x - b.r - 8 - mWidth("przytrzymaj", 0.6f), b.y - 3.3f, 0.6f, M_GOOD, true);
        }
        mRoundLabel(batch, target, itemIcon(session, "iron_sword"), "Atak");
        mRoundLabel(batch, run, itemIcon(session, "leather_boots"), "Podążaj");
        mRoundLabel(batch, defensive, itemIcon(session, "wooden_shield"), "Tarcza");
        mRoundLabel(batch, pvp, pvp.icon, "PvP");
        for (HudButton b : mobileContext()) if (b.visible && b.x > -999) mRoundLabel(batch, b, b.icon, b.label);
        if (mChat) {
            drawTabTitles(batch);
            float yy = conY + conH - 4 - conTabH - 2;
            for (ConLine l : conLines) {
                if (yy - l.height() < conY - 2) break;
                font.getData().setScale(chatScaleMobile());
                font.setColor(l.color());
                font.draw(batch, l.text(), conX + 8, yy, conW - 16, com.badlogic.gdx.utils.Align.left, true);
                yy -= l.height();
            }
            font.setColor(Color.WHITE);
            font.getData().setScale(1f);
            mText(batch, "dotknij, aby pisać...", conX + 10, 11.4f, 0.66f, hex(0x8a7660, 1f), false);
            mCentered(batch, "Czat", mlay.chatHandle.cx() - 6, mlay.chatHandle.cy(), 0.7f, M_TEXT, false);
        } else {
            boolean unread = false;
            for (pl.fawizja.pixel.chat.ChatTabs.Tab t : chatTabs.tabs()) unread |= t.unread();
            mCentered(batch, "Czat", mlay.chatTab.cx() - 6, mlay.chatTab.cy(), 0.72f, unread ? hex(0xff8a7a, 1f) : M_TEXT, false);
        }
        drawMobileStatus(batch);
        if (tgt != null && mWin == null && !menuOpen) {
            float ty = mlay.frame.y - 4 - 30;
            String name = mClip(tgt.type().name() + " (Lv " + tgt.type().level() + ")", 196, 0.66f);
            mText(batch, name, w / 2f - mWidth(name, 0.66f) / 2f, ty + 15, 0.66f, Color.WHITE, true);
        }
        if (diagnosticsOpen && diagInfo != null) {
            float dy = mlay.statusRow.y - 8;
            for (String line : diagInfo.lines()) {
                mText(batch, mClip(line, 240, 0.64f), 10, dy, 0.64f, Color.WHITE, false);
                dy -= 16;
            }
        }
        batch.end();

        // ---- warstwa 2: okna (kategorie, menu, wybor, mapa, dom)
        if (mWin == null && !menuOpen && !bigMapOpen && houseWin == null) return;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        HudLayout.Rect win = mWin != null ? mlay.window(mChat, false, new HudLayout.Rect()) : null;
        if (win != null) {
            mWindowShapes(shapes, win.x, win.y, win.w, win.h, true);
            mWinShapes(shapes, win, session);
        }
        if (menuOpen) {
            rebuildMenu(session);
            mWindowShapes(shapes, menuX, menuY, menuW, menuH, true);
            String cur = pickSlot >= 0 ? barEntries[pickSlot] : null;
            for (MenuRow row : menuRows) {
                boolean sel = cur != null && row.action.equals("pick:set:" + cur);
                if (logoutConfirm) {
                    woodButton(shapes, row.x, row.y, row.w, row.h, false);
                    continue;
                }
                Color fill = sel ? M_SEL : row.enabled ? M_CELL : hex(0x332014, 1f);
                shapes.setColor(fill);
                shapes.rect(row.x, row.y, row.w, row.h);
                strokeRect(shapes, row.x, row.y, row.w, row.h, 1f, sel ? M_GOOD : row.enabled ? M_CELL_LINE : hex(0x271810, 1f));
                if (pickSlot >= 0) {
                    SpellType sp = actionSpell(session, row.action.startsWith("pick:set:") ? row.action.substring(9) : null);
                    if (sp == null && row.action.equals("pick:none")) sp = spellByName(session, row.label);
                    if (sp != null) {
                        spellDisc(shapes, sp, row.x + 12, row.y + row.h / 2f, 9.5f, 0, false);
                        if (!row.enabled) {
                            shapes.setColor(0.16f, 0.1f, 0.06f, 0.55f);
                            shapes.circle(row.x + 12, row.y + row.h / 2f, 9.5f, 20);
                        }
                    }
                }
            }
            if (pickSlot >= 0) {
                // przycisk, ktory wybieramy: zielony pierscien
                HudButton b = spellButtons[pickSlot];
                ring(shapes, b.x, b.y, b.r + 5.5f, 3f, 90, -290, M_GOOD);
            }
        }
        if (bigMapOpen) {
            mWindowShapes(shapes, menuX, menuY, menuW, menuH, false);
            mapZoomShapes(shapes);
        }
        if (houseWin != null) drawHouseShapes(shapes, session);
        shapes.end();
        batch.begin();
        if (win != null) {
            mText(batch, mWinTitle(), win.x + 10, win.top() - 17, 0.82f, Color.WHITE, false);
            mWinContent(batch, win, session);
        }
        if (menuOpen) drawMobileMenuText(batch, session);
        if (bigMapOpen) drawBigMap(batch, session, pc.position().z() == 0 ? "0" : (pc.position().z() > 0 ? "+" + pc.position().z() : String.valueOf(pc.position().z())));
        if (houseWin != null) drawHouseText(batch);
        batch.end();
    }

    /** Pasek HP albo many w ramce: ciemne tlo, wypelnienie, ciemna obwodka. */
    private static void mBar(ShapeRenderer s, HudLayout.Rect r, float frac, Color c) {
        s.setColor(0.08f, 0.05f, 0.03f, 1f);
        s.rect(r.x, r.y, r.w, r.h);
        s.setColor(c);
        s.rect(r.x, r.y, r.w * Math.max(0f, Math.min(1f, frac)), r.h);
        strokeRect(s, r.x, r.y, r.w, r.h, 1f, M_DARK);
    }

    /** Trojkat przy napisie "Czat": w gore (zwiniety) albo w dol (rozwiniety). */
    private static void mTriangle(ShapeRenderer s, float cx, float cy, boolean up) {
        s.setColor(M_TEXT);
        if (up) s.triangle(cx - 3.5f, cy - 2.5f, cx + 3.5f, cy - 2.5f, cx, cy + 3f);
        else s.triangle(cx - 3.5f, cy + 2.5f, cx + 3.5f, cy + 2.5f, cx, cy - 3f);
    }

    /** Helm na ikonie Ekwipunek. */
    private TextureRegion helmetIcon(GameSession session) {
        TextureRegion r = session.items().containsKey("obsidian_helmet") ? itemIcon(session, "obsidian_helmet") : null;
        return r != null ? r : itemIcon(session, "leather_helmet");
    }

    /** Czar o nazwie (zablokowany wiersz wyboru: ikona mimo braku akcji). */
    private static SpellType spellByName(GameSession session, String name) {
        for (SpellType sp : session.spells().registry().all()) if (sp.name().equals(name) && !sp.kind().isRune()) return sp;
        return null;
    }

    /** Tlo i pierscien przycisku luku oraz okragly znak czaru (w przebiegu ksztaltow). */
    private void mSlotShapes(ShapeRenderer s, GameSession session, PlayerCharacter pc, int slot, MobileHudLayout.Circle c, boolean potion, long now) {
        String e = barEntries[slot];
        Color ring = potion ? ("potion_mp".equals(e) || e != null && e.contains("mana") ? hex(0x7aa6ff, 1f) : hex(0xff7a6a, 1f)) : null;
        boolean hot = pickSlot == slot && menuOpen;
        roundButton(s, c.x, c.y, c.r, hot ? M_GOOD : ring, hot);
        SpellType sp = actionSpell(session, e);
        if (sp != null) spellDisc(s, sp, c.x, c.y, c.r - 4, slotCooldown(session, slot, now), pc.mana() < sp.mana());
        else if (e == null) {
            s.setColor(M_MUTED);
            s.rect(c.x - 6, c.y - 1, 12, 2);
            s.rect(c.x - 1, c.y - 6, 2, 12);
        }
    }

    /** Ikona przedmiotu (mikstura, runa) na przycisku luku, liczba sztuk i zaslona odnowienia (w batchu). */
    private void mSlotIcon(SpriteBatch b, GameSession session, PlayerCharacter pc, int slot, MobileHudLayout.Circle c, boolean potion, long now) {
        String e = barEntries[slot];
        TextureRegion icon = actionIcon(session, e);
        if (icon == null) return;
        float is = potion ? 24 : 28;
        b.draw(icon, c.x - is / 2f, c.y - is / 2f + (potion ? 1 : 0), is, is);
        float cd = slotCooldown(session, slot, now);
        if (cd > 0) bFill(b, c.x - c.r * 0.7f, c.y + c.r * 0.7f - c.r * 1.4f * cd, c.r * 1.4f, c.r * 1.4f * cd, new Color(0, 0, 0, 0.6f));
        String n = spellButtons[slot].label;
        if (n != null && !n.isEmpty() && !n.equals("+")) mCentered(b, n, c.x + c.r * 0.62f, c.y - c.r * 0.66f, 0.6f, Color.WHITE, true);
    }

    /** Ikona i maly podpis na okraglym przycisku (Atak, Tarcza, PvP, przyciski chwilowe). */
    private void mRoundLabel(SpriteBatch b, HudButton btn, TextureRegion icon, String label) {
        float r = btn.r;
        if (icon == null) {
            if (label != null && !label.isEmpty()) mCentered(b, label, btn.x, btn.y, 0.62f, M_TEXT, true);
            return;
        }
        float is = r * 1.25f;
        b.draw(icon, btn.x - is / 2f, btn.y - is * 0.3f, is, is);
        if (label != null && !label.isEmpty()) mCentered(b, label, btn.x, btn.y - r * 0.56f, 0.44f, M_TEXT, true);
    }

    /** Skala wierszy czatu na telefonie (makieta: 10 px). */
    private float chatScaleMobile() { return 0.66f; }

    /** Lista walki pod minimapa: "Walka (n)", gracze i potwory w wierszach 20 dp, cel w czerwonej ramce. */
    private void drawMobileBattle(SpriteBatch batch, int rowsVis) {
        HudLayout.Rect bl = mlay.battle;
        if (bl.h <= 0) return;
        int nb = mobileBattleCount();
        mText(batch, "Walka (" + nb + ")", bl.x + 5, bl.top() - 11, 0.66f, M_TEXT, false);
        int more = nb - rowsVis - mBattleScroll;
        if (more > 0) {
            String m = String.valueOf(more);
            mText(batch, m, bl.right() - 5 - mWidth(m, 0.64f), bl.top() - 11, 0.64f, M_MUTED, false);
        }
        if (nb == 0) {
            mText(batch, "Brak potworów.", bl.x + 5, bl.top() - MobileHudLayout.BATTLE_HEAD - 12, 0.66f, M_MUTED, false);
            return;
        }
        float row = MobileHudLayout.BATTLE_ROW;
        for (int k = 0; k < rowsVis && k + mBattleScroll < nb; k++) {
            int i = k + mBattleScroll;
            float top = bl.top() - MobileHudLayout.BATTLE_HEAD - k * row, base = top - 9.5f;
            String name;
            Color nc = M_TEXT;
            TextureRegion icon;
            float hp;
            if (i < battlePlayers.size()) {
                BattlePlayer bp = battlePlayers.get(i);
                name = bp.name();
                icon = skullIcon(bp.skull());
                Color skc = skullColor(bp.skull());
                if (bp.ally()) nc = new Color(0.5f, 0.78f, 1f, 1f);
                else if (skc != null) nc = skc;
                hp = bp.healthPercent() / 100f;
            } else {
                Monster m = battle.get(i - battlePlayers.size());
                name = m.type().name();
                icon = art.monster(m.type().key());
                hp = m.healthFraction();
                if (m == currentTarget) bStroke(batch, bl.x + 2, top - row + 1, bl.w - 4, row - 1, 1.5f, hex(0xff4a3a, 1f));
            }
            if (icon != null) batch.draw(icon, bl.x + 4, top - 18, 16, 16);
            // imie u gory wiersza, pod nim pasek zycia na cala szerokosc (jak w Tibii)
            float nx = bl.x + 23, nw = bl.right() - 5 - nx;
            mText(batch, mClip(name, nw, 0.66f), nx, base, 0.66f, nc, false);
            bFill(batch, nx, top - 17f, nw, 3.5f, M_DARK);
            float[] c = BarColors.health(hp);
            bFill(batch, nx, top - 17f, nw * Math.max(0, Math.min(1, hp)), 3.5f, new Color(c[0], c[1], c[2], 1f));
        }
    }

    /** Napisy zdarzen (lup, poziom, ostrzezenia) nad czatem, miedzy joystickiem a lukiem. */
    private void drawMobileStatus(SpriteBatch batch) {
        if (statusLines.isEmpty() || menuOpen || bigMapOpen || mWin != null) return;
        float width = mlay.chatPanel.w, x = mlay.chatPanel.x;
        float yy = (mChat ? MobileHudLayout.CHAT_H + MobileHudLayout.HANDLE_H + 6 : MobileHudLayout.TAB_H + 6);
        for (ConLine l : statusLines) yy += l.height();
        font.getData().setScale(chatScale());
        for (ConLine l : statusLines) {
            Color c = l.color();
            layout.setText(font, l.text(), c, width, com.badlogic.gdx.utils.Align.center, true);
            batch.setColor(0f, 0f, 0f, 0.5f * c.a);
            batch.draw(art.white(), x + (width - layout.width) / 2f - 4, yy - l.height() + 1, layout.width + 8, l.height());
            batch.setColor(Color.WHITE);
            font.setColor(0f, 0f, 0f, c.a * 0.85f);
            font.draw(batch, l.text(), x + 1, yy - 1, width, com.badlogic.gdx.utils.Align.center, true);
            font.setColor(c);
            font.draw(batch, l.text(), x, yy, width, com.badlogic.gdx.utils.Align.center, true);
            yy -= l.height();
        }
        font.setColor(Color.WHITE);
        font.getData().setScale(1f);
    }

    private String mWinTitle() {
        switch (mWin) {
            case BAG: return "Plecak";
            case EQUIP: return "Ekwipunek";
            case SKILLS: return "Umiejętności";
            case VIP: return "VIP - znajomi";
            default: return "Ustawienia";
        }
    }

    /** Ksztalty w oknie kategorii: pola, paski umiejetnosci, przyciski, kropki stanu znajomych. */
    private void mWinShapes(ShapeRenderer s, HudLayout.Rect win, GameSession session) {
        PlayerCharacter pc = session.character();
        switch (mWin) {
            case BAG:
                for (MBtn b : mBagButtons(win, session)) woodButton(s, b.x(), b.y(), b.w(), b.h(), b.id().equals("mbag:page:" + bagPage));
                break;
            case SKILLS: {
                String[][] rows = skillRows(pc);
                float rh = mSkillRow(win, rows.length);
                for (int i = 0; i < rows.length; i++) {
                    float yy = win.top() - 26 - 15 - i * rh;
                    double prog = i == 0 ? 1 : i == 1 ? pc.levelProgress()
                            : (double) pc.skills().tries(Skill.values()[i - 2]) / Math.max(1, Skills.triesForNext(pc.skills().level(Skill.values()[i - 2])));
                    s.setColor(M_DARK);
                    s.rect(win.x + 12, yy - 6, win.w - 24, 3);
                    s.setColor(hex(0x5fbf4a, 1f));
                    s.rect(win.x + 12, yy - 6, (win.w - 24) * (float) Math.max(0, Math.min(1, prog)), 3);
                }
                break;
            }
            case VIP: {
                float yy = win.top() - 26 - 4;
                for (pl.fawizja.pixel.friends.FriendsView.Row r : vipRows()) {
                    if (yy - M_VIP_ROW < win.y + 46) break;
                    s.setColor(r.online() ? hex(0x5fbf4a, 1f) : hex(0x7a6a5a, 1f));
                    s.circle(win.x + 18, yy - M_VIP_ROW / 2f, 4, 12);
                    yy -= M_VIP_ROW;
                }
                MBtn add = mVipAdd(win);
                woodButton(s, add.x(), add.y(), add.w(), add.h(), false);
                break;
            }
            case SETTINGS:
                for (MBtn b : mSettingsButtons(win, mSettingsEntries(session))) {
                    s.setColor(M_CELL);
                    s.rect(b.x(), b.y(), b.w(), b.h());
                    strokeRect(s, b.x(), b.y(), b.w(), b.h(), 1f, M_CELL_LINE);
                }
                break;
            case EQUIP: {
                float[] bc = mEqCell(win, 0, 3);
                float m = (bc[2] - 4) / 2f;
                float[][] spots = {{bc[0] + 2, bc[1] + 2 + m}, {bc[0] + 2 + m, bc[1] + 2 + m}, {bc[0] + 2, bc[1] + 2}, {bc[0] + 2 + m, bc[1] + 2}};
                for (int i = 0; i < Math.min(4, buffs.size()); i++) {
                    if (buffs.get(i).icon() == null) drawStatusGlyph(s, buffs.get(i).glyph(), spots[i][0], spots[i][1], m);
                }
                break;
            }
            default:
        }
    }

    /** Wysokosc wiersza okna umiejetnosci (makieta 16 dp; mniej, gdy okno jest nizsze). */
    private static float mSkillRow(HudLayout.Rect win, int rows) {
        return Math.min(16, (win.h - 26 - 15 - 24) / Math.max(1, rows - 1));
    }

    /** Tresc okna kategorii (napisy, przedmioty) - w batchu. */
    private void mWinContent(SpriteBatch b, HudLayout.Rect win, GameSession session) {
        PlayerCharacter pc = session.character();
        switch (mWin) {
            case SKILLS: {
                String[][] rows = skillRows(pc);
                float rh = mSkillRow(win, rows.length);
                for (int i = 0; i < rows.length; i++) {
                    float yy = win.top() - 26 - 15 - i * rh;
                    mText(b, rows[i][0], win.x + 12, yy, 0.72f, M_TEXT, false);
                    float vw = mWidth(rows[i][1], 0.72f);
                    mText(b, rows[i][1], win.right() - 12 - vw, yy, 0.72f, M_TEXT, false);
                }
                pl.fawizja.pixel.combat.AttackProfile ap = pl.fawizja.pixel.combat.CombatFormulas.attackProfile(pc, session.items());
                String info = "Zdrowie " + pc.health() + "/" + pc.maxHealth() + "  Mana " + pc.mana() + "/" + pc.maxMana()
                        + "  Szybkość " + pc.speed() + "  Udźwig " + pc.capacity() + "  Atak do " + ap.maxDamage();
                mText(b, mClip(info, win.w - 24, 0.56f), win.x + 12, win.y + 6, 0.56f, M_MUTED, false);
                break;
            }
            case BAG: {
                bagPages = pc.bagPages();
                if (bagPage >= bagPages) bagPage = bagPages - 1;
                int base = bagPage * PAGE_SLOTS, used = 0;
                HudLayout.Rect c = new HudLayout.Rect();
                for (int i = 0; i < PAGE_SLOTS; i++) {
                    int slot = base + i;
                    mBagCell(win, i, c);
                    ItemStack st = slot < pc.backpack().size() ? pc.backpack().get(slot) : null;
                    if (st != null) used++;
                    TextureRegion fr = slot == selectedSlot && wstSelected != null ? wstSelected : slotFrame(st, art.itemRegistry());
                    if (fr != null) b.draw(fr, c.x, c.y, c.w, c.h);
                    else bFill(b, c.x, c.y, c.w, c.h, M_CELL);
                    if (slot == selectedSlot) bStroke(b, c.x, c.y, c.w, c.h, 1.5f, hex(0xf2cc40, 1f));
                    if (st == null) continue;
                    TextureRegion r = art.item(st.itemId());
                    float ip = slotPad(c.w);
                    if (r != null) drawStackIcon(b, r, st, c.x + ip, c.y + ip, c.w - 2 * ip);
                    if (st.count() > 1) mText(b, String.valueOf(st.count()), c.right() - 2 - mWidth(String.valueOf(st.count()), 0.56f), c.y + 3, 0.56f, Color.WHITE, true);
                    if (pc.isKept(st.itemId())) bFill(b, c.right() - 6, c.top() - 6, 4, 4, hex(0xf2cc40, 1f));
                }
                float ty = c.y - 12;
                String head = (bagPages > 1 ? "Plecak " + (bagPage + 1) + "/" + bagPages + ": " : "Pojemność: ") + used + " / " + PAGE_SLOTS
                        + "  ·  " + String.format("%,d", session.gold()) + " gp";
                ItemStack sel = selectedSlot >= 0 && selectedSlot < pc.backpack().size() ? pc.backpack().get(selectedSlot) : null;
                if (sel != null) {
                    ItemType t = session.items().get(sel.itemId());
                    ItemStack worn = t.slot() == EquipSlot.NONE ? null : pc.equipment().get(t.slot());
                    ItemType wornType = worn == null ? null : session.items().get(worn.itemId());
                    String stats = pl.fawizja.pixel.item.ItemCompare.stats(t, sel.upgrade(), wornType, worn == null ? 0 : worn.upgrade());
                    String req = pl.fawizja.pixel.item.ItemCompare.requirements(t, pc);
                    mText(b, mClip(stats, win.w - 24, 0.6f), win.x + 12, ty, 0.6f, M_TEXT, false);
                    if (!req.isEmpty()) mText(b, mClip(req, win.w - 24, 0.56f), win.x + 12, ty - 13, 0.56f, hex(0xff8a7a, 1f), false);
                } else {
                    mText(b, head, win.x + 12, ty, 0.62f, M_MUTED, false);
                    mText(b, "Dotknij przedmiotu, potem pola, aby przenieść.", win.x + 12, ty - 13, 0.56f, M_MUTED, false);
                }
                for (MBtn bt : mBagButtons(win, session)) mCentered(b, bt.label(), bt.x() + bt.w() / 2f, bt.y() + bt.h() / 2f, 0.66f, M_TEXT, false);
                break;
            }
            case EQUIP: {
                for (int i = 0; i < EQ_SLOTS.length; i++) {
                    float[] c = mEqCell(win, EQ_CELLS[i][0], EQ_CELLS[i][1]);
                    drawEquipSlot(b, pc.equipment().get(EQ_SLOTS[i]), EQ_NAMES[i], c[0], c[1], c[2]);
                    if (EQ_SLOTS[i] == EquipSlot.LIGHT) drawTorchState(b, session, c[0], c[1], c[2]);
                }
                float[] bp = mEqCell(win, 2, 0);
                drawBackpackSlot(b, bp[0], bp[1], bp[2]);
                float[] bc = mEqCell(win, 0, 3);
                if (slotImg != null) b.draw(wstEmpty != null ? wstEmpty : slotImg, bc[0], bc[1], bc[2], bc[2]);
                float m = (bc[2] - 4) / 2f;
                float[][] spots = {{bc[0] + 2, bc[1] + 2 + m}, {bc[0] + 2 + m, bc[1] + 2 + m}, {bc[0] + 2, bc[1] + 2}, {bc[0] + 2 + m, bc[1] + 2}};
                for (int i = 0; i < Math.min(4, buffs.size()); i++) {
                    if (buffs.get(i).icon() != null) b.draw(buffs.get(i).icon(), spots[i][0], spots[i][1], m, m);
                }
                int atk = 0, def = 0, arm = 0;
                for (EquipSlot es : EQ_SLOTS) {
                    ItemStack st = pc.equipment().get(es);
                    if (st == null) continue;
                    ItemType t = session.items().get(st.itemId());
                    atk += t.attack();
                    def += t.defense();
                    arm += t.armor();
                }
                pl.fawizja.pixel.combat.AttackProfile ap = pl.fawizja.pixel.combat.CombatFormulas.attackProfile(pc, session.items());
                float tx = win.x + 70 + 3 * 42 + 14, ty = win.top() - 26 - 18;
                String[] lines = {"Atak " + atk, "Obrona " + def, "Pancerz " + arm, "Obrażenia do " + ap.maxDamage(), "Udźwig " + pc.capacity()};
                for (String l : lines) {
                    mText(b, l, tx, ty, 0.66f, M_TEXT, false);
                    ty -= 16;
                }
                mText(b, "Pochodnię dotknij, aby zapalić.", tx, win.y + 24, 0.52f, M_MUTED, false);
                mText(b, "Dotknij dwa razy, aby zdjąć.", tx, win.y + 10, 0.52f, M_MUTED, false);
                break;
            }
            case VIP: {
                List<pl.fawizja.pixel.friends.FriendsView.Row> rows = vipRows();
                float yy = win.top() - 26 - 4;
                if (rows.isEmpty()) {
                    font.getData().setScale(0.66f);
                    font.setColor(M_MUTED);
                    font.draw(b, VIP_EMPTY, win.x + 12, yy - 6, win.w - 24, com.badlogic.gdx.utils.Align.left, true);
                    font.setColor(Color.WHITE);
                    font.getData().setScale(1f);
                }
                for (pl.fawizja.pixel.friends.FriendsView.Row r : rows) {
                    if (yy - M_VIP_ROW < win.y + 46) break;
                    float cy = yy - M_VIP_ROW / 2f;
                    mText(b, r.name(), win.x + 30, cy - 4, 0.75f, r.online() ? M_TEXT : hex(0x9c876f, 1f), false);
                    String info = (r.vocation() == null ? "" : r.vocation() + " ") + (r.level() > 0 ? r.level() : "");
                    mText(b, info, win.right() - 12 - mWidth(info, 0.62f), cy - 3.4f, 0.62f, M_MUTED, false);
                    yy -= M_VIP_ROW;
                }
                MBtn add = mVipAdd(win);
                mCentered(b, add.label(), add.x() + add.w() / 2f, add.y() + add.h() / 2f, 0.66f, M_TEXT, false);
                int online = vipView == null ? 0 : vipView.online();
                mText(b, "W grze: " + online + " z " + rows.size(), win.x + 12, win.y + 20, 0.6f, M_MUTED, false);
                break;
            }
            case SETTINGS: {
                List<String[]> entries = mSettingsEntries(session);
                for (MBtn bt : mSettingsButtons(win, entries)) {
                    if (bt.id().equals("set:page")) {
                        mCentered(b, bt.label(), bt.x() + bt.w() / 2f, bt.y() + bt.h() / 2f, 0.66f, M_GOOD, false);
                        continue;
                    }
                    String[] e = entries.get(Integer.parseInt(bt.id().substring(4)));
                    mText(b, mClip(e[1], bt.w() - 12, 0.68f), bt.x() + 6, bt.y() + 22.5f, 0.68f, M_TEXT, false);
                    mText(b, mClip(e[2], bt.w() - 12, 0.58f), bt.x() + 6, bt.y() + 7, 0.58f, M_MUTED, false);
                }
                break;
            }
            default:
        }
    }

    /** Tytul okna menu na telefonie i wiersz pod nim (opis), jak napisy okien w starym ukladzie. */
    private void drawMobileMenuText(SpriteBatch batch, GameSession session) {
        String title, sub = null;
        if (trade != null) {
            title = "Handel z " + trade.partner();
            sub = "Twój plecak po prawej: dotknij, aby dodać do oferty";
        } else if (logoutConfirm) {
            title = "Wylogować się?";
        } else if (pickSlot >= 0) {
            PlayerCharacter pc = session.character();
            title = "Przycisk " + arcNumber(pickSlot) + ": wybierz (" + vocationName(pc.vocation()) + ", poz. " + pc.level() + ")";
        } else if (playerMenu != null) {
            title = playerMenu.name();
            sub = playerInfoLine(session);
        } else if (achievementsView && journalMode) {
            title = "Zadania (ukończone: " + session.character().quests().done().size() + ")";
        } else if (achievementsView && infoTitle != null) {
            title = infoTitle;
        } else if (achievementsView) {
            PlayerCharacter me = session.character();
            title = bestiaryMode ? "Bestiariusz" + (monsterSpecies > 0 ? " (" + me.bestiary().species() + " / " + monsterSpecies + ")" : "")
                    : "Osiągnięcia " + me.achievements().count() + " / " + pl.fawizja.pixel.achievement.Achievement.values().length;
        } else if (marketView != null) {
            title = "Rynek graczy (" + marketView.total() + " ofert)";
        } else if (friendsView != null) {
            title = "Znajomi (" + friendsView.rows().size() + " z " + pl.fawizja.pixel.friends.FriendList.MAX + ")";
        } else if (rankingView != null) {
            title = rankingView.title() + " (" + rankingView.total() + " postaci)";
        } else if (npcMode) {
            title = npc.name() + " (" + npc.town() + ")";
            sub = npc.greeting();
        } else if (optionsMenu) {
            title = "Opcje";
        } else {
            title = "Księga: czary (dotknij, aby rzucić) i rzemiosło";
        }
        mText(batch, mClip(title, menuW - 44, 0.76f), menuX + 10, menuY + menuH - 17, 0.76f, Color.WHITE, false);
        if (sub != null && !sub.isEmpty()) mText(batch, mClip(sub, menuW - 20, 0.62f), menuX + 10, menuY + menuH - 36, 0.62f, M_MUTED, false);
        if (logoutConfirm) mCentered(batch, "Na pewno chcesz się wylogować?", menuX + menuW / 2f, menuY + menuH - 50, 0.8f, M_TEXT, false);
        boolean pick = pickSlot >= 0;
        for (MenuRow row : menuRows) {
            Color lc = row.enabled ? M_TEXT : hex(0xefe4d4, 0.45f), dc = row.enabled ? M_MUTED : hex(0xb8a48c, 0.45f);
            if (logoutConfirm) {
                mCentered(batch, row.label, row.x + row.w / 2f, row.y + row.h / 2f, 0.76f,
                        row.action.equals("logout:yes") ? hex(0xff8a7a, 1f) : M_TEXT, false);
                continue;
            }
            float tx = row.x + (pick ? 26 : 6);
            if (pick) {
                TextureRegion ic = actionIcon(session, row.action.startsWith("pick:set:") ? row.action.substring(9) : null);
                if (ic == null && row.action.equals("pick:none")) ic = runeIconByName(session, row.label);
                if (ic != null) {
                    if (!row.enabled) batch.setColor(1, 1, 1, 0.45f);
                    batch.draw(ic, row.x + 2, row.y + 2, 20, 20);
                    batch.setColor(Color.WHITE);
                }
            }
            float room = row.x + row.w - 4 - tx;
            float ls = pick ? 0.6f : 0.7f, ds = pick ? 0.5f : 0.58f;
            if (row.detail.isEmpty()) {
                mText(batch, mClip(row.label, room, ls), tx, row.y + row.h / 2f - 5.5f * ls, ls, lc, false);
            } else {
                mText(batch, mClip(row.label, room, ls), tx, row.y + row.h - 3 - 11 * ls, ls, lc, false);
                mText(batch, mClip(row.detail, room, ds), tx, row.y + 3.5f, ds, dc, false);
            }
        }
    }

    /** Ikona runy po nazwie czaru runy (zablokowany wiersz wyboru). */
    private TextureRegion runeIconByName(GameSession session, String name) {
        for (SpellType sp : session.spells().registry().all()) {
            if (sp.kind().isRune() && sp.name().equals(name) && session.items().containsKey(sp.key())) return itemIcon(session, sp.key());
        }
        return null;
    }

    /** Dane do panelu diagnostyki. */
    public interface DiagnosticsInfo {
        List<String> lines();
    }
}
