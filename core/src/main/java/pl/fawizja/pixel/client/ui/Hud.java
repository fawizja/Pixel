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
    private final HudButton use = new HudButton("use", "UŻYJ");
    private final HudButton run = new HudButton("run", "BIEG");
    private final HudButton torch = new HudButton("torch", "POCH");
    private final HudButton bag = new HudButton("bag", "PLECAK");
    private final HudButton pick = new HudButton("pick", "PODNIEŚ");
    private final HudButton diag = new HudButton("diag", "i");
    private final HudButton target = new HudButton("target", "ATAK");
    private final HudButton defensive = new HudButton("defensive", "TARCZA");
    private final HudButton book = new HudButton("book", "KSIĘGA");
    private final HudButton fish = new HudButton("fish", "ŁÓW");
    private final HudButton talk = new HudButton("talk", "ROZMOWA");
    private final HudButton chat = new HudButton("chat", "CZAT");
    private final HudButton mount = new HudButton("mount", "KOŃ");
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
    /** Okno osiagniec (albo bestiariusza) - dane lokalnej postaci. */
    private boolean achievementsView;
    /** Trzeci widok okna osiagniec: dziennik zadan (aktywne, do oddania, dostepne). */
    private boolean journalMode;
    /** Okno informacyjne (np. bossowie): tytul i wiersze {etykieta, miejsce, stan}; null = brak. */
    private String infoTitle;
    private List<String[]> infoRows = new ArrayList<>();

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

    private void loadUiImages() {
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
        buttons.add(book);
        buttons.add(fish);
        buttons.add(talk);
        buttons.add(chat);
        buttons.add(mount);
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
        // jedna bryla jak klasyczny klient Tibii: widok gry w ramce, po prawej panel (minimapa, ekwipunek,
        // lista walki, pasek akcji), u gory paski HP/MP, na dole przyciski i konsola
        float pad = 4, gap = 3;
        // panel tylko tak szeroki jak minimapa: wiecej miejsca na widok gry
        float pw = Math.max(150, Math.min(210, w * 0.17f));
        panelX = w - pw;
        float iw = pw - pad * 2;
        // pasek akcji 5x2 na dole panelu
        int cols = spellButtons.length / 2;
        float cell = Math.min(40, (iw - 8 - gap * (cols - 1)) / cols);
        float abW = cell * cols + gap * (cols - 1);
        float abX = panelX + pad + (iw - abW) / 2f;
        for (int i = 0; i < spellButtons.length; i++) {
            HudButton b = spellButtons[i];
            b.x = abX + (i % cols) * (cell + gap) + cell / 2f;
            b.y = pad + 4 + (1 - i / cols) * (cell + gap) + cell / 2f;
            b.r = cell / 2f;
            b.hw = cell / 2f;
        }
        abTop = pad + 8 + cell * 2 + gap;
        // u gory minimapa na cala szerokosc panelu
        mmSize = Math.min(iw - 6, h * 0.3f);
        mmX = w - pad - 3 - mmSize;
        mmY = h - pad - 3 - mmSize;
        // pod minimapa ekwipunek, obok niego kolumna: diagnostyka, bieg, postawa obronna, pochodnia
        float sc = Math.max(28, Math.min(34, cell * 0.9f)); // dotykowe pola co najmniej ~28 dp
        HudButton[] col = {diag, run, defensive, torch};
        float eqTop = mmY - 3 - pad;
        float eqBottom = abTop + pad + 23 + BATTLE_ROW * 2 + 6;
        layoutEquipPanel(eqTop, eqBottom, iw - sc - gap);
        if (epTibia) {
            // ekwipunek i kolumna razem wysrodkowane w panelu
            float groupW = epW + gap + sc;
            epX = panelX + pad + (iw - groupW) / 2f;
            float cx = epX + epW + gap + sc / 2f;
            float step = Math.min(sc + gap, (epH - sc) / (col.length - 1));
            for (int i = 0; i < col.length; i++) {
                col[i].x = cx;
                col[i].y = epY + epH - sc / 2f - i * step;
            }
        } else {
            // niski ekran: przyciski w rzedzie pod minimapa, pod nimi zwarty ekwipunek
            float rowW = col.length * sc + (col.length - 1) * gap;
            float rx = panelX + pad + (iw - rowW) / 2f;
            for (int i = 0; i < col.length; i++) {
                col[i].x = rx + sc / 2f + i * (sc + gap);
                col[i].y = eqTop - sc / 2f;
            }
            layoutEquipPanel(eqTop - sc - gap, eqBottom, iw);
        }
        for (HudButton b : col) {
            b.r = sc / 2f;
            b.hw = sc / 2f;
        }
        blX = panelX + pad;
        blW = iw;
        blY = abTop + pad;
        blH = epY - pad - blY;
        // wysoki ekran: okno umiejetnosci pod ekwipunkiem (jak w Tibii), lista walki pod nim
        float skH = 23 + PSK_ROW * (2 + Skill.values().length) + 6;
        if (blH - skH - pad >= 23 + BATTLE_ROW * 3 + 4) {
            pskX = blX; pskW = blW; pskH = skH;
            pskY = epY - pad - skH;
            blH -= skH + pad;
        } else {
            pskH = 0;
        }
        // dolny pasek pod widokiem gry: rzad przyciskow i konsola
        topH = 24;
        float hbH = h >= 500 ? 34 : 32;
        float conBody = (h >= 500 ? 6 : 3) * 13 + 8;
        gx = pad;
        gy = pad + conBody + 4 + hbH + 4;
        gw = panelX - pad - gx;
        gh = h - topH - gy;
        // pad w lewym dolnym rogu ekranu (na widoku gry i dolnym pasku), konsola i przyciski zaczynaja sie za nim
        float jr = Math.min(60, Math.min(w, h) * 0.14f) * (settings != null ? settings.joystickScale() : 1f);
        joystick.layout(pad + 10 + jr * 1.3f, pad + 10 + jr * 1.3f, jr);
        float padW = pad + 10 + jr * 2.6f + 6;
        conX = padW;
        conY = pad;
        conW = panelX - pad - padW;
        conH = conBody;
        hbLeft = padW;
        hbRight = panelX - pad;
        hbHalfH = hbH / 2f;
        hbY = conY + conH + 4 + hbHalfH;
        hbSlotHw = h >= 500 ? 48 : 34;
        // gorny pasek: HP i MP nad widokiem gry
        barX = gx + 4;
        barW = Math.min(170, gw * 0.28f);
        menuW = Math.min(w - 40, 620);
        menuH = Math.min(h - 40, 400);
        // na szerokim ekranie okna stoja nad widokiem gry, a prawy panel zostaje odsloniety
        menuX = panelX - 8 >= menuW ? (panelX - menuW) / 2f : (w - menuW) / 2f;
        menuY = (h - menuH) / 2f;
        rowH = Math.max(26, Math.min(36, (menuH - 50) / 10f));
        menuClose.x = menuX + menuW - 16; menuClose.y = menuY + menuH - 16; menuClose.r = 14;
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

    /** Widok gry w ramce (jednostki interfejsu), lewa krawedz prawego panelu, wysokosc gornego paska. */
    private float gx, gy, gw, gh, panelX, topH;
    /** Okno umiejetnosci w panelu (pskH = 0: brak miejsca). */
    private float pskX, pskY, pskW, pskH;
    private static final float PSK_ROW = 15;

    private boolean panelSkillsVisible() { return pskH > 0 && !charOpen && !windowOverPanel(); }

    /** Umiejetnosci w panelu: poziom, doswiadczenie i skille z paskiem postepu. */
    private void drawPanelSkills(SpriteBatch batch, PlayerCharacter pc) {
        if (!panelSkillsVisible()) return;
        text(batch, "Umiejętności", pskX + 6, pskY + pskH - 6);
        font.getData().setScale(CON_SCALE);
        float yy = pskY + pskH - 23 - 3;
        String[][] rows = new String[2 + Skill.values().length][];
        rows[0] = new String[] {"Poziom", String.valueOf(pc.level())};
        rows[1] = new String[] {"Doświadczenie", (int) (pc.levelProgress() * 100) + "%"};
        int i = 2;
        for (Skill sk : Skill.values()) rows[i++] = new String[] {sk.label(), String.valueOf(pc.skills().level(sk))};
        for (String[] r : rows) {
            text(batch, r[0], pskX + 8, yy);
            layout.setText(font, r[1]);
            text(batch, r[1], pskX + pskW - 8 - layout.width, yy);
            yy -= PSK_ROW;
        }
        font.getData().setScale(1f);
    }

    /** Paski postepu skilli pod ich wierszami (w przebiegu ksztaltow). */
    private void drawPanelSkillBars(ShapeRenderer shapes, PlayerCharacter pc) {
        if (!panelSkillsVisible()) return;
        miniWindow(shapes, pskX, pskY, pskW, pskH);
        float yy = pskY + pskH - 23 - 3 - PSK_ROW * 2;
        for (Skill sk : Skill.values()) {
            double prog = (double) pc.skills().tries(sk) / Math.max(1, Skills.triesForNext(pc.skills().level(sk)));
            bar(shapes, pskX + 8, yy - PSK_ROW + 2, pskW - 16, 2, (float) prog, new Color(0.3f, 0.75f, 0.4f, 1));
            yy -= PSK_ROW;
        }
    }

    /** Lista walki w panelu i gorna krawedz paska akcji. */
    private float blX, blY, blW, blH, abTop;

    /** Widok gry w pikselach ekranu {x, y, szerokosc, wysokosc} - tam GameScreen rysuje swiat. */
    public float[] gameView() { return new float[] {gx * uiScale, gy * uiScale, gw * uiScale, gh * uiScale}; }

    private boolean inGameView(float px, float py) {
        return px >= gx && px <= gx + gw && py >= gy && py <= gy + gh;
    }

    /** Dolny pasek przyciskow (granice) i polowa wysokosci / szerokosci jego pola. */
    private float hbLeft, hbRight, hbY, hbHalfH, hbSlotHw;
    /** Paski HP/MP obok minimapy. */
    private float barX, barW;
    private final HudButton[] hotbar = {target, use, bag, book, chat, talk, pick, fish, mount};
    /** Kwadratowe pola jak w Tibii (pasek akcji i przyciski obok ekwipunku). */
    private HudButton[] slotButtons() {
        HudButton[] out = java.util.Arrays.copyOf(spellButtons, spellButtons.length + 4);
        HudButton[] rest = {diag, run, defensive, torch};
        System.arraycopy(rest, 0, out, spellButtons.length, rest.length);
        return out;
    }

    private static void place(HudButton b, float cx, float cy, float dist, float deg, float r) {
        double a = Math.toRadians(deg);
        b.x = cx + (float) Math.cos(a) * dist;
        b.y = cy + (float) Math.sin(a) * dist;
        b.r = r;
    }

    /** Rozklada widoczne przyciski dolnego paska od srodka wolnego miejsca. */
    private void layoutHotbar() {
        int n = 0;
        for (HudButton b : hotbar) if (b.visible) n++;
        if (n == 0) return;
        float gap = 6;
        float hw = Math.min(hbSlotHw, ((hbRight - hbLeft) - gap * (n - 1)) / (2f * n));
        float x = hbLeft + hw;
        for (HudButton b : hotbar) {
            if (!b.visible) continue;
            b.x = x; b.y = hbY; b.r = hbHalfH; b.hw = hw;
            x += hw * 2 + gap;
        }
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
    public boolean bagOpen() { return bagOpen; }
    public void toggleBag() { bagOpen = !bagOpen; selectedSlot = -1; dragSlot = -1; dragging = false; if (bagOpen) menuOpen = false; }

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
        if (trade != null) return true;
        if (bigMapOpen) {
            toggleMap();
            return true;
        }
        if (menuOpen) {
            if (npcMode) {
                toggleNpc(session);
            } else {
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
        return false;
    }

    public void toggleBook() {
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
        if (trade != null) return;
        playerMenu = new PlayerMenu(id, name, attacking, ally);
        menuOpen = true;
        npcMode = false;
        bagOpen = false;
    }

    /** Ranking z serwera: otwiera okno (handel ma pierwszenstwo). */
    public void openRanking(pl.fawizja.pixel.ranking.RankingView view) {
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
    public void toggleCharacter() { charOpen = !charOpen; }
    public boolean characterOpen() { return charOpen; }

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
    }

    public void setSpellBar(pl.fawizja.pixel.settings.SpellBar bar) { customBar = bar; }
    public pl.fawizja.pixel.settings.SpellBar spellBar() { return customBar; }

    private static String shortName(String name) {
        String first = name.split(" ")[0];
        return first.length() > 7 ? first.substring(0, 6) + "." : first;
    }

    private void rebuildMenu(GameSession session) {
        menuRows.clear();
        if (trade != null) {
            rebuildTradeMenu(session);
            return;
        }
        if (pickSlot >= 0) {
            rebuildPickMenu(session);
            return;
        }
        if (playerMenu != null) {
            rebuildPlayerMenu();
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

    private void rebuildPlayerMenu() {
        PlayerMenu pm = playerMenu;
        List<MenuRow> rows = new ArrayList<>();
        rows.add(new MenuRow("player:trade:" + pm.name(), "Handel", "wymiana przedmiotów i złota (stojąc obok)", true));
        rows.add(new MenuRow("player:party:" + pm.name(), "Zaproś do drużyny", "wspólne doświadczenie, bez walki między sobą", true));
        rows.add(new MenuRow("player:guild:" + pm.name(), "Zaproś do gildii", "tylko lider gildii", true));
        rows.add(new MenuRow("player:friend:" + pm.name(), "Dodaj do znajomych", "powiadomienie, gdy wejdzie do gry", true));
        rows.add(new MenuRow("player:duel:" + pm.name(), "Pojedynek", "do pierwszej krwi, bez czaszek i śmierci", true));
        rows.add(new MenuRow("player:inspect:" + pm.name(), "Obejrzyj", "poziom, gildia i założony sprzęt", true));
        rows.add(new MenuRow("player:attack:" + pm.id(), pm.attacking() ? "Przerwij atak" : "Atakuj",
                pm.attacking() ? "koniec walki z tym graczem" : "PvP: biała czaszka, zasady serwera", !pm.ally()));
        float colW = (menuW - 30) / 2f;
        for (int i = 0; i < rows.size(); i++) {
            MenuRow row = rows.get(i);
            row.x = menuX + 10 + (i % 2) * (colW + 10);
            row.y = menuY + menuH - 40 - (i / 2 + 1) * rowH;
            row.w = colW;
            row.h = rowH - 3;
            menuRows.add(row);
        }
    }

    /** Osiagniecia w dwoch kolumnach albo bestiariusz (najczesciej zabijane potwory). */
    private void rebuildInfoMenu() {
        int perCol = Math.max(3, (int) ((menuH - 70) / rowH));
        float colW = (menuW - 30) / 2f;
        List<MenuRow> rows = new ArrayList<>();
        // wiersz {etykieta, opis[, stan[, akcja]]}; bez akcji wiersz tylko informuje
        for (String[] r : infoRows) {
            String detail = r.length > 2 && !r[2].isEmpty() ? r[1] + " - " + r[2] : r[1];
            rows.add(new MenuRow(r.length > 3 ? r[3] : "ach:none", r[0], detail, true));
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
                        own == null ? String.format("konto: %,d gp - dom kupisz za złoto z banku", bal) : own.town() + ", wejdziesz do niego tylko ty", false));
                if (own != null) {
                    right.add(new MenuRow("npc:houseleave", "Oddaj dom", "bez zwrotu ceny; ktoś inny będzie mógł go kupić", true));
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

    /** Czy otwarte okno (ksiega, NPC, mapa...) zachodzi na prawy panel. */
    private boolean windowOverPanel() {
        // diagnostyka z ustawieniami stoi na prawym panelu: minimapa i ekwipunek nie moga przez nia przeswitywac
        return diagnosticsOpen || (menuOpen || bigMapOpen) && menuX + menuW > panelX;
    }

    private boolean minimapVisible() {
        return mmSize >= 56 && !windowOverPanel();
    }

    public void dispose() { minimap.dispose(); }
    public boolean diagnosticsOpen() { return diagnosticsOpen; }
    public void setSettings(pl.fawizja.pixel.settings.GameSettings settings) { this.settings = settings; }

    private static final float SETTINGS_ROW_H = 22f;
    private float settingsRowY(int i) { return h - 260 - i * (SETTINGS_ROW_H + 4); }
    private float settingsRowX() { return w - 250; }
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
        if (charOpen && charWindowsTouch(p.x, p.y, session, actions)) return true;
        if (statusTouch(p.x, p.y, session)) return true;
        if (equipPanelTouch(p.x, p.y, session, actions)) return true;
        if (!bagOpen && consoleTouch(p.x, p.y, actions)) return true;
        if (!bagOpen && battleTouch(p.x, p.y, actions)) return true;
        if (p.x >= barX - 4 && p.x <= barX + barW * 2 + 12 && p.y >= h - topH) {
            // dotkniecie paskow HP/MP: karta postaci
            toggleCharacter();
            return true;
        }
        if (bigMapOpen) {
            bigMapOpen = false;
            float[] img = bigMapImg;
            if (img != null && p.x >= img[0] && p.y >= img[1] && p.y <= img[1] + img[3]) {
                int tx = (int) ((p.x - img[0]) / img[2]);
                int ty = (int) ((img[1] + img[3] - p.y) / img[2]);
                if (tx < session.world().width() && ty >= 0 && ty < session.world().height()) {
                    actions.onButton("map:goto:" + tx + "," + ty);
                }
            }
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
                if (trade != null) actions.onButton("trade:cancel"); // zamkniecie okna = rezygnacja
                else if (playerMenu != null) closePlayerMenu();
                else {
                    menuOpen = false;
                    rankingView = null;
                    friendsView = null;
                    marketView = null;
                    achievementsView = false;
                }
                return true;
            }
            rebuildMenu(session);
            for (MenuRow row : menuRows) {
                if (p.x >= row.x && p.x <= row.x + row.w && p.y >= row.y && p.y <= row.y + row.h) {
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
            if (p.x >= menuX && p.x <= menuX + menuW && p.y >= menuY && p.y <= menuY + menuH) return true;
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

    public boolean touchDragged(int sx, int sy, int pointer) {
        Vector2 p = toUi(sx, sy);
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
        run.visible = defensive.visible = torch.visible = !diagnosticsOpen; // pod panelem diagnostyki
        defensive.active = pc.isDefensive();
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
        chat.visible = chatEnabled || near != null;
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
        if (pressSlot >= 0 && com.badlogic.gdx.utils.TimeUtils.millis() - pressStart >= LONG_PRESS_MS) {
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
        layoutHotbar();
        layoutConsole(session, serverTimeMs);
        target.icon = itemIcon(session, "iron_sword");
        defensive.icon = itemIcon(session, "wooden_shield");
        torch.icon = itemIcon(session, "torch");
        fish.icon = itemIcon(session, "fishing_rod");

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        batch.setProjectionMatrix(viewport.getCamera().combined);
        uiBatch = batch;
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // tlo bryly: prawy panel, dolny pasek, gorny pasek i ramka wokol widoku gry
        float margin = 16;
        shapes.setColor(PANEL_BG);
        shapes.rect(panelX, 0, w - panelX, h);
        shapes.rect(0, 0, panelX, gy);
        shapes.rect(0, gy + gh, panelX, h - gy - gh);
        shapes.rect(0, gy, gx, gh);
        shapes.setColor(BORDER);
        shapes.rect(gx - 2, gy - 2, gw + 4, 2);
        shapes.rect(gx - 2, gy + gh, gw + 4, 2);
        shapes.rect(gx - 2, gy, 2, gh);
        shapes.rect(gx + gw, gy, 2, gh);
        // gorny pasek: HP i MP obok siebie, pod nimi doswiadczenie, po prawej ikony stanu
        float x = barX, y = h - 4 - 13;
        statusIcons(session, serverTimeMs);
        bar(shapes, x, y, barW, 13, pc.health() / (float) pc.maxHealth(), new Color(0.85f, 0.15f, 0.2f, 1));
        bar(shapes, x + barW + 6, y, barW, 13, pc.mana() / (float) Math.max(1, pc.maxMana()), new Color(0.2f, 0.35f, 0.95f, 1));
        bar(shapes, x, y - 5, barW * 2 + 6, 3, (float) pc.levelProgress(), new Color(0.95f, 0.8f, 0.2f, 1));
        drawStatusIcons(shapes, statusX(), h - 4 - STATUS_SIZE);
        miniWindow(shapes, blX, blY, blW, blH);
        drawPanelSkillBars(shapes, pc);
        frame(shapes, panelX + 4, 4, w - panelX - 8, abTop - 4);

        // pad jak w Tibii mobile: sama kulka, w 90% przezroczysta (wyrazniejsza w trakcie ruchu)
        float jrad = joystick.radius();
        if (joystick.floating()) {
            shapes.setColor(1, 1, 1, 0.12f); // podstawa joysticka pod kciukiem
            shapes.circle(joystick.baseX(), joystick.baseY(), jrad, 36);
        }
        shapes.setColor(1, 1, 1, joystick.active() ? 0.22f : 0.1f);
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
            for (HudButton b : hotbar) {
                if (!b.visible) continue;
                if (b.active) batch.setColor(0.6f, 1f, 0.6f, 1f);
                (b.active ? buttonDownPatch : buttonPatch).draw(batch, b.x - b.hw, b.y - b.r, b.hw * 2, b.r * 2);
                batch.setColor(Color.WHITE);
            }
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
            miniWindow(shapes, eqX, eqY, eqW, eqH);
            miniWindow(shapes, skX, skY, skW, skH);
            float yy = skY + skH - 23 - SK_ROW * (SK_INFO_ROWS + 1);
            for (Skill sk : Skill.values()) {
                double prog = (double) pc.skills().tries(sk) / Math.max(1, Skills.triesForNext(pc.skills().level(sk)));
                bar(shapes, skX + 8, yy - SK_ROW + 1, skW - 16, 3, (float) prog, new Color(0.3f, 0.75f, 0.4f, 1));
                yy -= SK_ROW;
            }
        }
        if (consoleVisible()) {
            frame(shapes, conX, conY, conW, conH);
        }
        if (bagOpen) drawBagBackground(shapes, session);
        if (menuOpen) {
            rebuildMenu(session);
            window(shapes, menuX, menuY, menuW, menuH, true);
            for (MenuRow row : menuRows) {
                shapes.setColor(row.enabled ? new Color(0.22f, 0.3f, 0.25f, 1f) : new Color(0.2f, 0.18f, 0.18f, 1f));
                shapes.rect(row.x, row.y, row.w, row.h);
            }
            shapes.setColor(0.6f, 0.15f, 0.15f, 0.95f);
            shapes.circle(menuClose.x, menuClose.y, menuClose.r, 20);
        }
        if (equipPanelVisible()) {
            if (epTibia) miniWindow(shapes, epX, epY, epW, epH);
            else frame(shapes, epX, epY, epW, epH);
        }
        if (minimapVisible()) {
            shapes.setColor(BORDER);
            shapes.rect(mmX - 3, mmY - 3, mmSize + 6, mmSize + 6);
            shapes.setColor(0.05f, 0.05f, 0.07f, 1f);
            shapes.rect(mmX - 1, mmY - 1, mmSize + 2, mmSize + 2);
        }
        if (bigMapOpen) {
            window(shapes, menuX, menuY, menuW, menuH, true);
        }
        if (diagnosticsOpen) {
            shapes.setColor(0, 0, 0, 0.75f);
            shapes.rect(w - 250, h - 256, 238, 216);
            if (settings != null) {
                int n = settings.rows().length;
                for (int i = 0; i < n; i++) {
                    shapes.setColor(0.2f, 0.26f, 0.32f, 0.9f);
                    shapes.rect(settingsRowX(), settingsRowY(i), 238, SETTINGS_ROW_H);
                }
            }
        }
        shapes.end();

        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        drawBarOverlays(batch);
        font.setColor(Color.WHITE);
        int minute = clock.minuteOfDay(serverTimeMs);
        String floor = pc.position().z() == 0 ? "0" : (pc.position().z() > 0 ? "+" + pc.position().z() : String.valueOf(pc.position().z()));
        font.getData().setScale(0.8f);
        centered(batch, pc.health() + " / " + pc.maxHealth(), x + barW / 2f, y + 6.5f);
        centered(batch, pc.mana() + " / " + pc.maxMana(), x + barW * 1.5f + 6, y + 6.5f);
        String info = "Lv " + pc.level() + "   " + String.format("%02d:%02d   Piętro %s   %,d gp", minute / 60, minute % 60, floor, session.gold());
        layout.setText(font, info);
        if (x + barW * 2 + 14 + layout.width >= statusX() - 6) {
            // waski ekran (telefon): sam poziom i zloto
            info = "Lv " + pc.level() + "  " + String.format("%,d gp", session.gold());
            layout.setText(font, info);
        }
        if (x + barW * 2 + 14 + layout.width < statusX() - 6) text(batch, info, x + barW * 2 + 14, h - 7);
        font.getData().setScale(1f);
        drawBattle(batch);
        drawPanelSkills(batch, pc);

        drawConsole(batch);

        for (HudButton b : buttons) {
            if (!b.visible || underMenu(b.x, b.y)) continue;
            layout.setText(font, b.label);
            float room = (b.hw > 0 ? b.hw : b.r) * 1.8f;
            float fit = layout.width > room ? room / layout.width : 1f;
            if (b.labelBottom) {
                font.getData().setScale(Math.min(fit, Math.min(0.65f, b.r / 32f)));
                centered(batch, b.label, b.x, b.y - b.r * 0.6f);
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
                font.getData().setScale(Math.min(0.7f, b.r / 28f));
                layout.setText(font, b.label);
                text(batch, b.label, b.x + b.hw - 3 - layout.width, b.y - b.r + 3 + layout.height);
            } else if (b.icon != null) {
                float is = b.r * 1.15f;
                batch.draw(b.icon, b.x - is / 2f, b.y - is * 0.3f, is, is);
                font.getData().setScale(Math.min(fit, Math.min(0.7f, b.r / 30f)));
                centered(batch, b.label, b.x, b.y - b.r * 0.6f);
            } else {
                font.getData().setScale(fit);
                centered(batch, b.label, b.x, b.y);
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
            if (trade == null && playerMenu != null) {
                text(batch, "Gracz " + playerMenu.name(), menuX + 10, menuY + menuH - 14);
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
                text(batch, row.label, row.x + 4, row.y + row.h - 3);
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
            float dy = h - 50;
            for (String line : diagInfo.lines()) {
                text(batch, line, w - 244, dy);
                dy -= 16;
            }
            if (settings != null) {
                String[][] rows = settings.rows();
                font.getData().setScale(0.85f);
                for (int i = 0; i < rows.length; i++) text(batch, rows[i][1], settingsRowX() + 6, settingsRowY(i) + 16);
                font.getData().setScale(1f);
            }
        }
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
        RUNNING("Bieg: idę do celu", new Color(0.2f, 0.4f, 0.7f, 1f)),
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
        statuses.add(pc.fedMs() > 0 ? Status.FED : Status.HUNGRY);
        if (pc.isBlessed()) statuses.add(Status.BLESSED);
        if (pc.isPremium()) statuses.add(Status.PREMIUM);
        if (pc.isPromoted()) statuses.add(Status.PROMOTED);
        if (pc.isRunning()) statuses.add(Status.RUNNING);
        if (pc.hasteMs() > 0) statuses.add(Status.HASTE);
        if (pc.isTorchLit() || pc.lightBuffMs() > 0) statuses.add(Status.LIGHT);
        for (pl.fawizja.pixel.item.Enchantments.Kind k : pl.fawizja.pixel.item.Enchantments.Kind.values()) {
            if (pc.enchantments().active(k)) { statuses.add(Status.ENCHANT); break; }
        }
    }

    /** Ikony stanu wyrownane do prawej krawedzi gornego paska. */
    private float statusX() { return gx + gw - 2 - statuses.size() * (STATUS_SIZE + 3); }

    private void drawStatusIcons(ShapeRenderer s, float x0, float y0) {
        float sz = STATUS_SIZE;
        for (int i = 0; i < statuses.size(); i++) {
            Status st = statuses.get(i);
            float x = x0 + i * (sz + 3), cx = x + sz / 2f, cy = y0 + sz / 2f;
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
                case HASTE: // skrzydlaty but: stopa i dwa piora
                    s.rect(cx - 4, cy - 5, 6, 3);
                    s.rect(cx - 4, cy - 2, 3, 6);
                    s.triangle(cx - 1, cy + 4, cx + 5, cy + 5, cx - 1, cy + 1);
                    s.triangle(cx - 1, cy + 1, cx + 5, cy + 1, cx - 1, cy - 1);
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
        float y0 = h - 4 - STATUS_SIZE;
        if (statuses.isEmpty() || py < y0 - 3 || py > h || px < statusX() - 4 || px > gx + gw) return false;
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
    private void miniWindow(ShapeRenderer shapes, float fx, float fy, float fw, float fh) {
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

    private void drawBigMap(SpriteBatch batch, GameSession session, String floor) {
        pl.fawizja.pixel.world.Position p = session.character().position();
        text(batch, "Mapa - piętro " + floor + " (dotknij miejsca, aby tam iść)", menuX + 10, menuY + menuH - 8);
        float[] img = minimap.drawWhole(batch, session.world(), p, walkTarget, markerPositions(session), menuX + 8, menuY + 8, menuW - 16, menuH - 34);
        bigMapImg = img;
        if (img == null) return;
        font.getData().setScale(0.85f);
        for (pl.fawizja.pixel.world.Town t : session.world().towns()) {
            if (t.temple().z() != p.z()) continue;
            float tx = img[0] + t.temple().x() * img[2];
            float ty = img[1] + img[3] - t.temple().y() * img[2];
            text(batch, t.name(), tx - 20, ty + 14);
        }
        font.setColor(new Color(1f, 0.85f, 0.2f, 1f));
        for (pl.fawizja.pixel.settings.MapMarkers.Marker m : allMarkers(session)) {
            if (m.at().z() != p.z()) continue;
            text(batch, m.name(), img[0] + m.at().x() * img[2] + 6, img[1] + img[3] - m.at().y() * img[2] + 6);
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
    /** Jak dlugo zdarzenie zostaje w konsoli (ms). */
    private static final long CON_KEEP_MS = 30_000;

    /** Filtry konsoli (etap 16): wszystko, czat, lup, dziennik serwera. Bez zakladek na ekranie (2026-10-23:
     *  zbedne nad czatem) - konsola zawsze pokazuje wszystko, filtr zostaje dla -Dpixel.conTab. */
    private static final String[] CON_TABS = {"Ogólne", "Czat", "Łup", "Serwer"};
    private static final float CON_TAB_H = 0;
    private int conTab;

    public void showConsoleTab(int tab) { conTab = Math.max(0, Math.min(CON_TABS.length - 1, tab)); }

    private static boolean inTab(int tab, EventLog.Kind k) {
        switch (tab) {
            case 1: return k == EventLog.Kind.CHAT;
            case 2: return k == EventLog.Kind.LOOT;
            case 3: return k == EventLog.Kind.INFO || k == EventLog.Kind.WARNING || k == EventLog.Kind.SYSTEM;
            default: return true;
        }
    }

    private boolean consoleVisible() {
        return !((menuOpen || bigMapOpen) && menuY < conY + conH) && conH > 8;
    }

    /** Konsola pod panelem postaci: zadania i wyzwanie na gorze, potem ostatnie zdarzenia i czat (najnowsze na dole). */
    private void layoutConsole(GameSession session, long serverTimeMs) {
        PlayerCharacter pc = session.character();
        float maxBody = conH - 8 - CON_TAB_H;
        conLines.clear();
        battle.clear();
        for (Monster m : session.monsters().monsters()) {
            if (m.isDead() || m.position().z() != pc.position().z() || m.position().distanceTo(pc.position()) > 7) continue;
            battle.add(m);
        }
        battle.sort(java.util.Comparator.comparingInt(m -> m.position().distanceTo(pc.position())));
        float tw = conW - 14;
        font.getData().setScale(CON_SCALE);
        List<ConLine> fixed = new ArrayList<>();
        String chLine = session.challenge() != null ? session.challenge().line(session.lastUpdateMs()) : onlineChallenge;
        if (conTab == 0 && chLine != null) fixed.add(conLine(chLine, new Color(1f, 0.75f, 0.45f, 1f), tw));
        for (String q : conTab != 0 ? java.util.List.<String>of() : pl.fawizja.pixel.quest.QuestTracker.lines(pc, session.quests(), session.today(),
                session.monsterName(pc.daily().monster() == null ? "" : pc.daily().monster()),
                session.monsterName(pc.hunt().monster() == null ? "" : pc.hunt().monster()))) {
            fixed.add(conLine(q, new Color(0.85f, 0.9f, 1f, 1f), tw));
        }
        float used = 0;
        for (ConLine l : fixed) used += l.height();
        List<ConLine> recent = new ArrayList<>();
        List<EventLog.Entry> entries = session.log().latest(conTab == 0 ? 12 : 60);
        for (int i = entries.size() - 1; i >= 0; i--) {
            EventLog.Entry e = entries.get(i);
            if (!inTab(conTab, e.kind())) continue;
            long age = serverTimeMs - e.timeMs();
            // zakladka "Ogolne" znika po 30 s jak dotad; pozostale zakladki to historia kanalu (bez wygaszania)
            if (conTab == 0 && age > CON_KEEP_MS) continue;
            float a = conTab != 0 ? 1f : age > CON_KEEP_MS - 5000 ? Math.max(0.3f, (CON_KEEP_MS - age) / 5000f) : 1f;
            Color c = e.kind() == EventLog.Kind.WARNING ? new Color(1f, 0.6f, 0.4f, a)
                    : e.kind() == EventLog.Kind.LOOT ? new Color(0.6f, 1f, 0.6f, a)
                    : e.kind() == EventLog.Kind.CHAT ? new Color(1f, 0.92f, 0.55f, a) : new Color(1f, 1f, 1f, a);
            ConLine l = conLine(e.text(), c, tw);
            if (used + l.height() > maxBody) break;
            used += l.height();
            recent.add(0, l);
        }
        font.getData().setScale(1f);
        conLines.addAll(fixed);
        conLines.addAll(recent);
    }

    private ConLine conLine(String text, Color c, float width) {
        layout.setText(font, text, c, width, com.badlogic.gdx.utils.Align.left, true);
        return new ConLine(text, c, layout.height + 4);
    }

    private void drawConsole(SpriteBatch batch) {
        if (!consoleVisible()) return;
        font.getData().setScale(CON_SCALE);
        float yy = conY + conH - 4 - CON_TAB_H;
        for (ConLine l : conLines) {
            if (yy - l.height() < conY - 2) break;
            font.setColor(l.color());
            font.draw(batch, l.text(), conX + 7, yy, conW - 14, com.badlogic.gdx.utils.Align.left, true);
            yy -= l.height();
        }
        if (conLines.isEmpty()) {
            font.setColor(0.6f, 0.6f, 0.55f, 1f);
            text(batch, "Konsola: dotknij, aby pisać", conX + 7, yy);
        }
        font.setColor(Color.WHITE);
        font.getData().setScale(1f);
    }

    /** Dotkniecie konsoli otwiera czat. */
    private boolean consoleTouch(float px, float py, Actions actions) {
        if (!consoleVisible() || px < conX || px > conX + conW || py < conY || py > conY + conH) return false;
        actions.onButton("chat");
        return true;
    }

    /** Dotkniecie listy walki wybiera cel. */
    private boolean battleTouch(float px, float py, Actions actions) {
        if (!battleVisible() || px < blX || px > blX + blW || py < blY || py > blY + blH) return false;
        int i = (int) ((blY + blH - 23 - 4 - py) / BATTLE_ROW);
        if (i >= 0 && i < battle.size()) actions.onButton("battle:" + battle.get(i).id());
        return true;
    }

    private boolean battleVisible() {
        return blH >= 23 + BATTLE_ROW && !windowOverPanel() && !charOpen;
    }

    /** Lista walki w panelu (jak battle list w Tibii): imie potwora, pasek zycia, czerwona ramka przy celu. */
    private void drawBattle(SpriteBatch batch) {
        if (!battleVisible()) return;
        text(batch, "Walka (" + battle.size() + ")", blX + 6, blY + blH - 6);
        Monster tgt = currentTarget;
        float yy = blY + blH - 23 - 4;
        font.getData().setScale(CON_SCALE);
        if (battle.isEmpty()) {
            font.setColor(0.75f, 0.75f, 0.7f, 1f);
            text(batch, "Brak potworów w pobliżu.", blX + 8, yy);
            font.setColor(Color.WHITE);
            font.getData().setScale(1f);
            return;
        }
        for (Monster m : battle) {
            if (yy - BATTLE_ROW < blY + 2) break;
            float rowY = yy - BATTLE_ROW + 1;
            if (m == tgt) outline(batch, blX + 4, rowY, blW - 8, BATTLE_ROW - 1, new Color(0.95f, 0.25f, 0.2f, 1f));
            TextureRegion icon = art.monster(m.type().key());
            if (icon != null) batch.draw(icon, blX + 7, rowY + 1, BATTLE_ROW - 3, BATTLE_ROW - 3);
            float bw = Math.min(70, blW * 0.3f), bx = blX + blW - 10 - bw;
            String name = m.type().name();
            layout.setText(font, name);
            float room = bx - 6 - (blX + 7 + BATTLE_ROW);
            if (layout.width > room) font.getData().setScale(CON_SCALE * Math.max(0.6f, room / layout.width));
            text(batch, name, blX + 7 + BATTLE_ROW, yy - 2);
            font.getData().setScale(CON_SCALE);
            batch.setColor(0, 0, 0, 0.7f);
            batch.draw(art.white(), bx - 1, rowY + 5, bw + 2, 6);
            float f = m.healthFraction();
            batch.setColor(f > 0.6f ? new Color(0.2f, 0.8f, 0.2f, 1f) : f > 0.3f ? new Color(0.9f, 0.75f, 0.15f, 1f) : new Color(0.9f, 0.2f, 0.15f, 1f));
            batch.draw(art.white(), bx, rowY + 6, bw * f, 4);
            batch.setColor(Color.WHITE);
            yy -= BATTLE_ROW;
        }
        font.getData().setScale(1f);
    }

    private Monster currentTarget;

    // ------------------------------------------------------------------ staly panel ekwipunku pod minimapa

    /** Uklad 5x2 na niskich ekranach: {kolumna, wiersz} dla EQ_SLOTS. */
    private static final int[][] EQ_COMPACT = {{1, 0}, {0, 0}, {0, 1}, {2, 0}, {1, 1}, {2, 1}, {3, 0}, {3, 1}, {4, 0}};
    private float epX, epY, epW, epH, epCell;
    /** true = okno jak w Tibii (naglowek, 3x4), false = zwarty pasek 5x2 bez naglowka. */
    private boolean epTibia;

    /** Ekwipunek w panelu pod minimapa: od top w dol, zostawiajac miejsce do bottom (lista walki). */
    private void layoutEquipPanel(float top, float bottom, float maxW) {
        float room = top - bottom;
        epTibia = room >= 23 + 4 * 24 + 3 * 3 + 8 && (maxW - 18) / 3f >= 24;
        if (epTibia) {
            epCell = Math.min(36, Math.min((room - 23 - 9 - 8) / 4f, (maxW - 18) / 3f));
            epW = epCell * 3 + 3 * 2 + 12;
            epH = 23 + epCell * 4 + 3 * 3 + 8;
        } else {
            epCell = Math.max(16, Math.min(Math.min(32, (maxW - 8 - 8) / 5f), (room - 2 - 8) / 2f));
            epW = epCell * 5 + 2 * 4 + 8;
            epH = epCell * 2 + 2 + 8;
        }
        epX = panelX + (w - panelX - epW) / 2f;
        epY = top - epH;
    }

    private boolean equipPanelVisible() {
        return !charOpen && !windowOverPanel();
    }

    /** Gniazdo plecaka w panelu: jak w Tibii prawy gorny rog lalki, w ukladzie 5x2 ostatnie pole. */
    private float[] epBackpackPos() {
        if (epTibia) return new float[] {epX + 6 + 2 * (epCell + 3), epY + epH - 23 - 4 - epCell};
        return new float[] {epX + 4 + 4 * (epCell + 2), epY + epH - 4 - epCell - (epCell + 2)};
    }

    private float[] epCellPos(int i) {
        if (epTibia) {
            return new float[] {epX + 6 + EQ_CELLS[i][0] * (epCell + 3), epY + epH - 23 - 4 - epCell - EQ_CELLS[i][1] * (epCell + 3)};
        }
        return new float[] {epX + 4 + EQ_COMPACT[i][0] * (epCell + 2), epY + epH - 4 - epCell - EQ_COMPACT[i][1] * (epCell + 2)};
    }

    /** Pierwsze dotkniecie zalozonego przedmiotu opisuje go w konsoli, drugie (do 2 s) zdejmuje do plecaka. */
    private int eqTapSlot = -1;
    private long eqTapMs;

    private void equipTap(int i, GameSession session, Actions actions) {
        ItemStack st = session.character().equipment().get(EQ_SLOTS[i]);
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
        if (epTibia) text(batch, "Ekwipunek", epX + 6, epY + epH - 6);
        for (int i = 0; i < EQ_SLOTS.length; i++) {
            float[] c = epCellPos(i);
            drawEquipSlot(batch, session.character().equipment().get(EQ_SLOTS[i]), EQ_NAMES[i], c[0], c[1], epCell);
        }
        float[] bp = epBackpackPos();
        drawBackpackSlot(batch, bp[0], bp[1], epCell);
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

    /** Pole ekwipunku: tlo z OTClientV8, przedmiot albo szara nazwa gniazda. */
    private void drawEquipSlot(SpriteBatch batch, ItemStack st, String name, float x, float y, float cell) {
        TextureRegion frame = slotFrame(st, art.itemRegistry());
        if (frame != null) batch.draw(frame, x, y, cell, cell);
        TextureRegion r = st == null ? null : art.item(st.itemId());
        if (r != null) {
            float pad = slotPad(cell);
            batch.draw(r, x + pad, y + pad, cell - pad * 2, cell - pad * 2);
            font.getData().setScale(Math.min(0.7f, cell / 50f));
            if (st.count() > 1) text(batch, String.valueOf(st.count()), x + 2, y + cell * 0.32f);
            if (st.upgrade() > 0) text(batch, "+" + st.upgrade(), x + cell * 0.55f, y + cell - 2);
            font.getData().setScale(1f);
        } else {
            font.getData().setScale(Math.min(0.6f, cell / 60f));
            font.setColor(0.6f, 0.6f, 0.6f, 1f);
            layout.setText(font, name);
            if (layout.width < cell - 2) centered(batch, name, x + cell / 2f, y + cell / 2f);
            font.setColor(Color.WHITE);
            font.getData().setScale(1f);
        }
    }

    // ------------------------------------------------------------------ okna ekwipunku i umiejetnosci

    /** Uklad lalki ekwipunku: {kolumna, wiersz} od gory. */
    private static final EquipSlot[] EQ_SLOTS = {EquipSlot.AMULET, EquipSlot.HEAD, EquipSlot.WEAPON, EquipSlot.ARMOR,
            EquipSlot.SHIELD, EquipSlot.RING, EquipSlot.LEGS, EquipSlot.AMMO, EquipSlot.FEET};
    private static final int[][] EQ_CELLS = {{0, 0}, {1, 0}, {0, 1}, {1, 1}, {2, 1}, {0, 2}, {1, 2}, {2, 2}, {1, 3}};
    private static final String[] EQ_NAMES = {"Amulet", "Hełm", "Broń", "Zbroja", "Tarcza", "Pierśc.", "Nogi", "Amun.", "Buty"};
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
        miniWindow(shapes, bagX - 8, bagY - 50 - extra, slotSize * 5 + 16, slotSize * 4 + 80 + extra);
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
            if (r != null) batch.draw(r, sx + ip, sy + ip, slotSize - ip * 2, slotSize - ip * 2);
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

    /** Dane do panelu diagnostyki. */
    public interface DiagnosticsInfo {
        List<String> lines();
    }
}
