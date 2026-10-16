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
    private final HudButton target = new HudButton("target", "CEL");
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
    /** Strona listy osiagniec (gdy nie mieszcza sie w dwoch kolumnach). */
    private int achPage;
    private int npcQty = 1;
    private static final int[] QUANTITIES = {1, 5, 25, 100};
    private boolean charOpen;
    private final HudButton potionHp = new HudButton("potion_hp", "HP");
    private final HudButton potionMp = new HudButton("potion_mp", "MP");
    private final HudButton[] spellButtons = new HudButton[4];
    private final String[] barSpells = new String[4];
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
    private float bagX, bagY, slotSize;
    private final HudButton bagEquip = new HudButton("bag_equip", "ZAŁÓŻ");
    private final HudButton bagDrop = new HudButton("bag_drop", "WYRZUĆ");
    private final HudButton bagUse = new HudButton("bag_use", "UŻYJ");
    private final HudButton bagClose = new HudButton("bag_close", "X");
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

    /** Znaczniki gracza, bossa tygodnia (gdy znane jest jego miejsce) i miejsca ostatniej smierci. */
    public List<pl.fawizja.pixel.settings.MapMarkers.Marker> allMarkers(GameSession session) {
        List<pl.fawizja.pixel.settings.MapMarkers.Marker> out = new ArrayList<>(markers.all());
        pl.fawizja.pixel.world.Position boss = session.weeklyBossPlace();
        if (boss != null && session.weeklyBoss() != null) {
            out.add(new pl.fawizja.pixel.settings.MapMarkers.Marker("Boss tygodnia: " + session.weeklyBoss().name(), boss));
        }
        pl.fawizja.pixel.world.Position died = session.combat().lastDeathPlace();
        if (died != null) out.add(new pl.fawizja.pixel.settings.MapMarkers.Marker("Miejsce śmierci", died));
        return out;
    }

    private List<pl.fawizja.pixel.world.Position> markerPositions(GameSession session) {
        List<pl.fawizja.pixel.world.Position> out = new ArrayList<>();
        for (pl.fawizja.pixel.settings.MapMarkers.Marker m : allMarkers(session)) out.add(m.at());
        return out;
    }
    private static final int MINIMAP_TILES = 60;
    /** Ustawienia gracza pokazywane pod diagnostyka (klikalne wiersze "settings:*"). */
    private pl.fawizja.pixel.settings.GameSettings settings;

    public Hud(TileArt art, BitmapFont font) {
        this.art = art;
        this.font = font;
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
        buttons.add(potionHp);
        buttons.add(potionMp);
        for (int i = 0; i < spellButtons.length; i++) {
            spellButtons[i] = new HudButton("spell:" + i, "");
            buttons.add(spellButtons[i]);
        }
    }

    /** Przycisk czatu (tylko w grze online). */
    public void setChatEnabled(boolean enabled) { chatEnabled = enabled; }

    public void resize(int width, int height) {
        float density = Gdx.graphics.getDensity();
        uiScale = Math.max(1f, Math.min(3.5f, density));
        viewport.setUnitsPerPixel(1f / uiScale);
        viewport.update(width, height, true);
        w = viewport.getWorldWidth();
        h = viewport.getWorldHeight();
        float margin = 16;
        float jr = Math.min(70, Math.min(w, h) * 0.16f);
        joystick.layout(margin + jr * 1.3f, margin + jr * 1.3f, jr);
        float br = Math.max(26, jr * 0.48f);
        float bx = w - margin - br;
        float by = margin + br;
        run.x = bx; run.y = by; run.r = br;
        use.x = bx - br * 2.4f; use.y = by; use.r = br;
        torch.x = bx; torch.y = by + br * 2.4f; torch.r = br;
        bag.x = bx - br * 2.4f; bag.y = by + br * 2.4f; bag.r = br;
        pick.x = bx - br * 4.8f; pick.y = by; pick.r = br;
        target.x = bx - br * 4.8f; target.y = by + br * 2.4f; target.r = br;
        defensive.x = bx; defensive.y = by + br * 4.8f; defensive.r = br;
        book.x = bx - br * 2.4f; book.y = by + br * 4.8f; book.r = br;
        fish.x = bx - br * 4.8f; fish.y = by + br * 4.8f; fish.r = br;
        talk.x = bx; talk.y = by + br * 7.2f; talk.r = br;
        chat.x = bx - br * 2.4f; chat.y = by + br * 7.2f; chat.r = br;
        mount.x = bx - br * 4.8f; mount.y = by + br * 7.2f; mount.r = br;
        // minimapa pod przyciskiem diagnostyki, nad najwyzszym rzedem przyciskow
        mmSize = Math.min(120, h - margin - 34 - (talk.y + br) - 12);
        mmX = w - margin - mmSize;
        mmY = h - margin - 34 - mmSize;
        // pasek czarow i mikstur: rzad nad joystickiem/przyciskami, od lewej
        float sr = Math.max(20, br * 0.72f);
        float sx0 = margin + jr * 2.6f + sr + 12;
        float sy = margin + sr;
        for (int i = 0; i < spellButtons.length; i++) {
            spellButtons[i].x = sx0 + i * sr * 2.3f;
            spellButtons[i].y = sy;
            spellButtons[i].r = sr;
        }
        potionHp.x = sx0 + 4 * sr * 2.3f; potionHp.y = sy; potionHp.r = sr;
        potionMp.x = sx0 + 5 * sr * 2.3f; potionMp.y = sy; potionMp.r = sr;
        if (potionMp.x + sr > pick.x - br - 6) {
            // waski ekran: pasek w drugim rzedzie nad joystickiem
            float sy2 = margin + jr * 2.6f + sr + 8;
            for (int i = 0; i < spellButtons.length; i++) { spellButtons[i].x = margin + sr + i * sr * 2.3f; spellButtons[i].y = sy2; }
            potionHp.x = margin + sr + 4 * sr * 2.3f; potionHp.y = sy2;
            potionMp.x = margin + sr + 5 * sr * 2.3f; potionMp.y = sy2;
        }
        menuW = Math.min(w - 40, 620);
        menuH = Math.min(h - 40, 400);
        menuX = (w - menuW) / 2f;
        menuY = (h - menuH) / 2f;
        rowH = Math.max(26, Math.min(36, (menuH - 50) / 10f));
        menuClose.x = menuX + menuW - 16; menuClose.y = menuY + menuH - 16; menuClose.r = 14;        diag.x = w - margin - 14; diag.y = h - margin - 14; diag.r = 14;
        slotSize = Math.min(44, Math.min(w, h) / 9f);
        bagX = w / 2f - slotSize * 2.5f;
        bagY = h / 2f - slotSize * 2f;
        float bbr = slotSize * 0.55f;
        bagEquip.x = bagX + slotSize * 1.0f; bagEquip.y = bagY - bbr * 1.4f; bagEquip.r = bbr;
        bagDrop.x = bagX + slotSize * 4.0f; bagDrop.y = bagY - bbr * 1.4f; bagDrop.r = bbr;
        bagUse.x = bagX + slotSize * 2.5f; bagUse.y = bagY - bbr * 1.4f; bagUse.r = bbr;
        bagClose.x = bagX + slotSize * 5f + 4; bagClose.y = bagY + slotSize * 4f + 18; bagClose.r = 14;
    }

    public VirtualJoystick joystick() { return joystick; }
    public boolean bagOpen() { return bagOpen; }
    public void toggleBag() { bagOpen = !bagOpen; selectedSlot = -1; if (bagOpen) menuOpen = false; }
    public void toggleBook() {
        if (trade != null) return; // okno handlu ma pierwszenstwo
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
    public String barSpell(int i) { return i >= 0 && i < barSpells.length ? barSpells[i] : null; }

    /**
     * Automatyczny pasek: najlepsze leczenie, najlepszy atak na cel, atak obszarowy (lub drugi atak),
     * poswiata. Gracz nie musi niczego ustawiac na telefonie.
     */
    public void updateBar(GameSession session) {
        List<SpellType> known = session.spells().knownSpells();
        SpellType heal = null, attack = null, attack2 = null, area = null, extra = null;
        for (SpellType sp : known) {
            switch (sp.kind()) {
                case HEAL_SELF: heal = sp; break;
                case ATTACK_TARGET: attack2 = attack; attack = sp; break;
                case ATTACK_AREA: area = sp; break;
                case LIGHT: extra = sp; break;
                default:
            }
        }
        SpellType[] bar = {heal, attack, area != null ? area : attack2, extra};
        String[] auto = new String[4];
        for (int i = 0; i < 4; i++) auto[i] = bar[i] == null ? null : bar[i].key();
        java.util.Set<String> knownKeys = new java.util.HashSet<>();
        for (SpellType sp : known) knownKeys.add(sp.key());
        String[] resolved = customBar.resolve(auto, knownKeys);
        for (int i = 0; i < 4; i++) {
            SpellType sp = resolved[i] == null ? null : session.spells().registry().get(resolved[i]);
            barSpells[i] = sp == null ? null : sp.key();
            spellButtons[i].visible = sp != null;
            spellButtons[i].label = sp == null ? "" : shortName(sp.name());
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
        for (SpellType sp : session.spells().registry().forVocation(pc.vocation())) {
            boolean ok = pc.level() >= sp.minLevel() && pc.skills().level(Skill.MAGIC) >= sp.minMagic();
            String detail = sp.mana() + " many, poz. " + sp.minLevel() + (sp.minMagic() > 0 ? ", magia " + sp.minMagic() : "");
            menuRows.add(new MenuRow("cast:" + sp.key(), sp.name(), detail, ok));
        }
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
        int perPage = Math.max(3, (int) ((menuH - 70) / rowH) - 2);
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
            boolean left = row.action.startsWith("cast:");
            int idx = left ? li++ : ri++;
            row.x = menuX + 10 + (left ? 0 : colW + 10);
            row.y = menuY + menuH - 40 - (idx + 1) * rowH;
            row.w = colW;
            row.h = rowH - 3;
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
        rows.add(new MenuRow("journal:guide", "Zadanie dnia", dd, true));
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
        if (rows.size() == 1) rows.add(new MenuRow("ach:none", "Brak zadań w toku", "przewodnicy w miastach mają zlecenia", false));
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
        int perCol = Math.max(5, (rows.size() + 1) / 2);
        for (int i = 0; i < rows.size(); i++) {
            pl.fawizja.pixel.ranking.RankingView.Row r = rows.get(i);
            MenuRow row = new MenuRow("ranking:none", r.place() + ". " + r.name() + (r.place() == v.mine() ? (guilds ? "  (twoja)" : "  (ty)") : ""),
                    r.vocation() + (guilds ? ", suma poziomów " : ", poziom ") + r.level(),
                    true);
            row.x = menuX + 10 + (i / perCol) * (colW + 10);
            row.y = menuY + menuH - 40 - (i % perCol + 1) * rowH;
            row.w = colW;
            row.h = rowH - 3;
            menuRows.add(row);
        }
        String mine = guilds ? (v.mine() > 0 ? "Twoja gildia: " + v.mine() + ". miejsce z " + v.total() : "Nie należysz do gildii z rankingu")
                : v.mine() > 0 ? "Twoje miejsce: " + v.mine() + " z " + v.total() : "Nie ma cię w tym rankingu";
        MenuRow me = new MenuRow("ranking:none", mine, "/ranking druid, paladin, mag, mnich, gildie", true);
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
                    left.add(new MenuRow("npc:buy:" + o.itemId() + ":" + npcQty, session.items().get(o.itemId()).name(),
                            o.buyPrice() + " gp" + (npcQty > 1 ? ", razem " + cost + " gp" : ""), gold >= cost));
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
                left.add(new MenuRow("npc:depot", "Depozyt przedmiotów", (depot.size() - depot.freeSlots()) + " / " + depot.size() + " miejsc, wspólny dla miast", true));
                long bal = pc.bank().balance();
                left.add(new MenuRow("npc:none", "Saldo: " + String.format("%,d gp", bal), "konto wspólne dla wszystkich miast", false));
                left.add(new MenuRow("npc:deposit", "Wpłać wszystkie monety", String.format("w plecaku: %,d gp", session.gold()), session.gold() > 0));
                for (long a : new long[] {1_000, 10_000, 100_000, 1_000_000}) {
                    left.add(new MenuRow("npc:withdraw:" + a, String.format("Wypłać %,d gp", a), "monety trafią do plecaka", bal >= a));
                }
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
            case CARRIER: {
                long gold = session.gold();
                for (pl.fawizja.pixel.npc.Travel.Route r : pl.fawizja.pixel.npc.Travel.routes(session.npcs(), npc)) {
                    left.add(new MenuRow("npc:travel:" + r.to().town(), "Jedź do " + r.to().town(),
                            String.format("%,d gp", r.price()) + (gold < r.price() ? " - za mało monet w plecaku" : ""), gold >= r.price()));
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
                pl.fawizja.pixel.quest.HuntTask hunt = pc.hunt();
                if (!hunt.active()) {
                    left.add(new MenuRow("npc:hunt", "Zlecenie łowcy", "50 potworów dla twojego poziomu, bez limitu dziennego", true));
                } else {
                    left.add(new MenuRow("npc:hunt", hunt.done() ? "Zlecenie łowcy: ODDAJ" : "Zlecenie: " + monsterNames.apply(hunt.monster()),
                            hunt.done() ? "odbierz nagrodę" : "pokonano " + hunt.progress() + " / " + hunt.target(), hunt.done()));
                    if (!hunt.done()) left.add(new MenuRow("npc:huntdrop", "Porzuć zlecenie", "możesz wziąć nowe", true));
                }
                left.add(new MenuRow("npc:bosses", "Bossowie", "kto żyje, kto odrodzi się i kiedy (/bossowie)", true));
                pl.fawizja.pixel.quest.QuestSystem qs = session.quests();
                if (qs == null) break;
                for (pl.fawizja.pixel.quest.QuestType q : qs.registry().byGiver(npc.key())) {
                    // dalsze etapy lancucha widac dopiero po ukonczeniu poprzedniego
                    if (pc.quests().state(q.key()) == pl.fawizja.pixel.quest.QuestLog.State.AVAILABLE && !qs.unlocked(pc, q)) continue;
                    String reward = qs.rewardText(q);
                    String detail;
                    boolean ok;
                    switch (pc.quests().state(q.key())) {
                        case DONE: detail = "ukończone"; ok = false; break;
                        case ACTIVE:
                            ok = qs.ready(pc, q);
                            detail = ok ? "ODDAJ - nagroda: " + reward : "w toku: " + qs.progress(pc, q) + " / " + q.amount();
                            break;
                        default:
                            ok = pc.level() >= q.minLevel();
                            detail = "przyjmij (poz. " + q.minLevel() + "): " + reward;
                    }
                    MenuRow row = new MenuRow("npc:quest:" + q.key(), q.name(), detail, ok);
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

    private boolean minimapVisible() {
        return mmSize >= 56 && !diagnosticsOpen && !menuOpen && !bagOpen && !bigMapOpen;
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
        if (p.x <= Math.min(240, w * 0.45f) && p.y >= h - 62) {
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
                    } else if (row.action.equals("book:journal")) {
                        openJournal();
                    } else if (row.action.equals("book:assign")) {
                        assignSlot = assignSlot >= 3 ? -1 : assignSlot + 1;
                    } else if (assignSlot >= 0 && row.action.startsWith("cast:")) {
                        customBar.assign(assignSlot, row.action.substring(5));
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
            int slot = slotAt(p.x, p.y);
            if (slot >= 0) {
                if (selectedSlot < 0) {
                    if (session.character().backpack().get(slot) != null) selectedSlot = slot;
                } else if (selectedSlot == slot) {
                    selectedSlot = -1;
                } else {
                    actions.onButton("slot_move:" + selectedSlot + ":" + slot);
                    selectedSlot = -1;
                }
                return true;
            }
        }
        for (HudButton b : buttons) {
            if (b.hit(p.x, p.y)) {
                actions.onButton(b.id);
                return true;
            }
        }
        if (bagOpen && p.x >= bagX - 8 && p.x <= bagX + slotSize * 5 + 8 && p.y >= bagY - 50 && p.y <= bagY + slotSize * 4 + 30) {
            return true;
        }
        return joystick.touchDown(p.x, p.y, pointer);
    }

    public boolean touchDragged(int sx, int sy, int pointer) {
        Vector2 p = toUi(sx, sy);
        return joystick.drag(p.x, p.y, pointer);
    }

    public boolean touchUp(int sx, int sy, int pointer) {
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
        pick.visible = session.itemsAtFeet();
        diag.active = diagnosticsOpen;
        defensive.active = pc.isDefensive();
        book.active = menuOpen && !npcMode && trade == null && playerMenu == null && rankingView == null && friendsView == null && marketView == null && !achievementsView;
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
        updateBar(session);
        int hpCount = pc.backpack().count(session.items().byKey(session.quickPotion(false)).id());
        int mpCount = pc.backpack().count(session.items().byKey(session.quickPotion(true)).id());
        potionHp.label = "HP " + hpCount;
        potionMp.label = "MP " + mpCount;
        Monster tgt = session.combat().target();
        target.active = tgt != null;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // paski HP / MP / EXP
        float barW = Math.min(220, w * 0.42f);
        float x = 12, y = h - 22;
        bar(shapes, x, y, barW, 12, pc.health() / (float) pc.maxHealth(), new Color(0.85f, 0.15f, 0.2f, 1));
        bar(shapes, x, y - 17, barW, 12, pc.mana() / (float) Math.max(1, pc.maxMana()), new Color(0.2f, 0.35f, 0.95f, 1));
        bar(shapes, x, y - 31, barW, 6, (float) pc.levelProgress(), new Color(0.95f, 0.8f, 0.2f, 1));

        // joystick
        shapes.setColor(1, 1, 1, joystick.active() ? 0.22f : 0.12f);
        shapes.circle(joystick.baseX(), joystick.baseY(), joystick.radius(), 40);
        shapes.setColor(1, 1, 1, joystick.active() ? 0.55f : 0.35f);
        shapes.circle(joystick.knob().x, joystick.knob().y, joystick.radius() * 0.45f, 30);

        for (HudButton b : buttons) {
            if (!b.visible) continue;
            shapes.setColor(b.active ? new Color(0.15f, 0.7f, 0.25f, 0.85f) : new Color(0.08f, 0.08f, 0.1f, 0.6f));
            shapes.circle(b.x, b.y, b.r, 32);
        }
        for (int i = 0; i < spellButtons.length; i++) {
            HudButton b = spellButtons[i];
            if (!b.visible || barSpells[i] == null) continue;
            SpellType sp = session.spells().registry().get(barSpells[i]);
            shapes.setColor(sp.kind() == SpellKind.HEAL_SELF ? new Color(0.2f, 0.6f, 0.3f, 0.55f)
                    : sp.kind().isOffensive() ? new Color(0.65f, 0.2f, 0.15f, 0.55f) : new Color(0.3f, 0.3f, 0.6f, 0.55f));
            shapes.circle(b.x, b.y, b.r * 0.92f, 28);
            long rem = session.spells().cooldowns().remaining(sp, serverTimeMs);
            if (rem > 0) {
                float frac = Math.min(1f, rem / (float) Math.max(sp.cooldownMs(), sp.group().groupCooldownMs));
                shapes.setColor(0, 0, 0, 0.6f);
                shapes.arc(b.x, b.y, b.r * 0.92f, 90, 360 * frac, 28);
            }
            if (pc.mana() < sp.mana()) {
                shapes.setColor(0.1f, 0.1f, 0.4f, 0.55f);
                shapes.circle(b.x, b.y, b.r * 0.92f, 28);
            }
        }

        if (tgt != null) {
            float tw = Math.min(200, w * 0.36f);
            shapes.setColor(0, 0, 0, 0.55f);
            shapes.rect(w / 2f - tw / 2f - 6, h - 44, tw + 12, 38);
            bar(shapes, w / 2f - tw / 2f, h - 38, tw, 10, tgt.healthFraction(), new Color(0.85f, 0.15f, 0.2f, 1));
        }
        if (charOpen) {
            shapes.setColor(0.06f, 0.06f, 0.08f, 0.88f);
            shapes.rect(10, h - 420, 270, 300);
            float yy = h - 250;
            for (Skill sk : Skill.values()) {
                double prog = (double) pc.skills().tries(sk) / Math.max(1, Skills.triesForNext(pc.skills().level(sk)));
                bar(shapes, 150, yy - 10, 110, 6, (float) prog, new Color(0.3f, 0.75f, 0.4f, 1));
                yy -= 22;
            }
        }
        if (bagOpen) drawBagBackground(shapes, session);
        if (menuOpen) {
            rebuildMenu(session);
            shapes.setColor(0.08f, 0.07f, 0.1f, 0.92f);
            shapes.rect(menuX, menuY, menuW, menuH);
            for (MenuRow row : menuRows) {
                shapes.setColor(row.enabled ? new Color(0.22f, 0.3f, 0.25f, 1f) : new Color(0.2f, 0.18f, 0.18f, 1f));
                shapes.rect(row.x, row.y, row.w, row.h);
            }
            shapes.setColor(0.6f, 0.15f, 0.15f, 0.95f);
            shapes.circle(menuClose.x, menuClose.y, menuClose.r, 20);
        }
        if (minimapVisible()) {
            shapes.setColor(0.05f, 0.05f, 0.07f, 0.85f);
            shapes.rect(mmX - 3, mmY - 3, mmSize + 6, mmSize + 6);
        }
        if (bigMapOpen) {
            shapes.setColor(0.04f, 0.04f, 0.06f, 0.94f);
            shapes.rect(menuX, menuY, menuW, menuH);
        }
        if (diagnosticsOpen) {
            shapes.setColor(0, 0, 0, 0.6f);
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
        font.setColor(Color.WHITE);
        text(batch, pc.health() + " / " + pc.maxHealth(), x + 4, y + 10);
        text(batch, pc.mana() + " / " + pc.maxMana(), x + 4, y - 7);
        int minute = clock.minuteOfDay(serverTimeMs);
        String floor = pc.position().z() == 0 ? "0" : (pc.position().z() > 0 ? "+" + pc.position().z() : String.valueOf(pc.position().z()));
        text(batch, pc.name() + "  " + pc.vocation().displayName() + "  Lv " + pc.level(), x, y - 38);
        text(batch, String.format("%02d:%02d  Piętro %s  %,d gp", minute / 60, minute % 60, floor, session.gold()), x, y - 54);

        float ly = y - 76;
        // sledzenie zadan pod stanem postaci, dziennik zdarzen nizej
        font.setColor(0.85f, 0.9f, 1f, 1f);
        for (String q : pl.fawizja.pixel.quest.QuestTracker.lines(pc, session.quests(), session.today(),
                session.monsterName(pc.daily().monster() == null ? "" : pc.daily().monster()),
                session.monsterName(pc.hunt().monster() == null ? "" : pc.hunt().monster()))) {
            text(batch, q, x, ly + 6);
            ly -= 16;
        }
        font.setColor(Color.WHITE);
        List<EventLog.Entry> entries = session.log().latest(4);
        for (EventLog.Entry e : entries) {
            float age = (serverTimeMs - e.timeMs()) / 1000f;
            if (age > 8f) continue;
            float a = Math.max(0f, Math.min(1f, (8f - age) / 2f));
            font.setColor(e.kind() == EventLog.Kind.WARNING ? new Color(1f, 0.6f, 0.4f, a)
                    : e.kind() == EventLog.Kind.LOOT ? new Color(0.6f, 1f, 0.6f, a) : new Color(1f, 1f, 1f, a));
            text(batch, e.text(), x, ly);
            ly -= 16;
        }
        font.setColor(Color.WHITE);

        for (HudButton b : buttons) {
            if (!b.visible) continue;
            layout.setText(font, b.label);
            float fit = layout.width > b.r * 1.8f ? b.r * 1.8f / layout.width : 1f;
            font.getData().setScale(fit);
            centered(batch, b.label, b.x, b.y);
            font.getData().setScale(1f);
        }
        if (tgt != null) {
            centered(batch, tgt.type().name() + " (Lv " + tgt.type().level() + ")", w / 2f, h - 16);
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
                text(batch, bestiaryMode ? "Bestiariusz - " + me.name()
                        : "Osiągnięcia " + me.achievements().count() + " / " + pl.fawizja.pixel.achievement.Achievement.values().length,
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

    private void drawCharacterSheet(SpriteBatch batch, GameSession session) {
        PlayerCharacter pc = session.character();
        float x = 20, y = h - 130;
        text(batch, pc.name() + " - " + pc.vocation().displayName() + ", poziom " + pc.level(), x, y);
        long next = pl.fawizja.pixel.balance.Balance.experienceForLevel(Math.min(1000, pc.level() + 1));
        text(batch, String.format("Exp: %,d / %,d (%d%%)", pc.experience(), next,
                pl.fawizja.pixel.balance.Balance.percentToNextLevel(pc.experience(), pc.level())), x, y - 18);
        text(batch, "Szybkość " + pc.speed() + "   Udźwig " + pc.capacity() + "   Miasto: " + capitalized(pc.homeTown())
                + (pc.isBlessed() ? "   Błogosławieństwo" : "")
                + (pc.title() == null ? "" : "   Tytuł: " + pl.fawizja.pixel.achievement.Achievement.titleOf(pc.title())), x, y - 36);
        text(batch, "Sytość: " + (pc.fedMs() / 60_000) + " min   Pochodnia: " + (pc.isTorchLit() ? (pc.torchBurnMs() + 59_999) / 60_000 + " min" : "zgaszona") + "   Pokonane: " + pc.bestiary().total() + "   Osiągnięcia: " + pc.achievements().count() + " (O)", x, y - 54);
        float yy = h - 250;
        for (Skill sk : Skill.values()) {
            text(batch, sk.label() + "  " + pc.skills().level(sk) + "  (" + pc.skills().percentToNext(sk) + "%)", x, yy);
            yy -= 22;
        }
        font.getData().setScale(0.8f);
        text(batch, "Dotknij pasków HP/MP, aby zamknąć", x, h - 404);
        font.getData().setScale(1f);
    }

    private float bagTipW() { return Math.min(w - 20, Math.max(slotSize * 5 + 16, 420)); }
    private float bagTipX() { return bagX + slotSize * 2.5f - bagTipW() / 2f; }

    private void drawBagBackground(ShapeRenderer shapes, GameSession session) {
        shapes.setColor(0.1f, 0.08f, 0.06f, 0.88f);
        shapes.rect(bagX - 8, bagY - 50, slotSize * 5 + 16, slotSize * 4 + 80);
        if (selectedSlot >= 0 && session.character().backpack().get(selectedSlot) != null) {
            shapes.setColor(0.06f, 0.05f, 0.04f, 0.9f);
            shapes.rect(bagTipX(), bagY + slotSize * 4 + 34, bagTipW(), 38);
        }
        for (int i = 0; i < 20; i++) {
            float sx = bagX + (i % 5) * slotSize;
            float sy = bagY + (3 - i / 5) * slotSize;
            shapes.setColor(i == selectedSlot ? new Color(0.9f, 0.75f, 0.2f, 1f) : new Color(0.35f, 0.3f, 0.25f, 1f));
            shapes.rect(sx + 2, sy + 2, slotSize - 4, slotSize - 4);
            shapes.setColor(0.18f, 0.15f, 0.12f, 1f);
            shapes.rect(sx + 4, sy + 4, slotSize - 8, slotSize - 8);
        }
        if (selectedSlot >= 0) {
            for (HudButton b : new HudButton[] {bagEquip, bagDrop, bagUse}) {
                shapes.setColor(0.25f, 0.45f, 0.3f, 0.95f);
                shapes.rect(b.x - b.r * 1.3f, b.y - b.r * 0.6f, b.r * 2.6f, b.r * 1.2f);
            }
        }
        shapes.setColor(0.6f, 0.15f, 0.15f, 0.95f);
        shapes.circle(bagClose.x, bagClose.y, bagClose.r, 20);
    }

    private void drawBagContents(SpriteBatch batch, GameSession session) {
        PlayerCharacter pc = session.character();
        text(batch, "Plecak (" + (20 - pc.backpack().freeSlots()) + "/20)", bagX, bagY + slotSize * 4 + 22);
        centered(batch, "X", bagClose.x, bagClose.y);
        for (int i = 0; i < 20; i++) {
            ItemStack s = pc.backpack().get(i);
            if (s == null) continue;
            float sx = bagX + (i % 5) * slotSize;
            float sy = bagY + (3 - i / 5) * slotSize;
            TextureRegion r = art.item(s.itemId());
            if (r != null) batch.draw(r, sx + 5, sy + 5, slotSize - 10, slotSize - 10);
            if (s.count() > 1) text(batch, String.valueOf(s.count()), sx + 5, sy + 15);
            if (s.upgrade() > 0) text(batch, "+" + s.upgrade(), sx + slotSize - 24, sy + slotSize - 6);
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
            }
        }
    }

    private void bar(ShapeRenderer s, float x, float y, float width, float height, float fill, Color c) {
        s.setColor(0, 0, 0, 0.55f);
        s.rect(x - 1, y - 1, width + 2, height + 2);
        s.setColor(c);
        s.rect(x, y, width * Math.max(0f, Math.min(1f, fill)), height);
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
