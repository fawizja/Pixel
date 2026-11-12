package pl.fawizja.pixel.client.screen;

import pl.fawizja.pixel.i18n.Messages;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.client.PixelGame;
import pl.fawizja.pixel.client.render.HarmonyPips;
import pl.fawizja.pixel.client.render.LightRenderer;
import pl.fawizja.pixel.client.render.TileArt;
import pl.fawizja.pixel.client.render.WorldRenderer;
import pl.fawizja.pixel.client.ui.Hud;
import pl.fawizja.pixel.combat.CombatEvent;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.perf.GuardMode;
import pl.fawizja.pixel.perf.Resource;
import pl.fawizja.pixel.perf.ResourceGuard;
import pl.fawizja.pixel.time.WorldClock;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Lighting;
import pl.fawizja.pixel.world.Weather;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.game.Actions;
import pl.fawizja.pixel.game.MovePredictor;
import pl.fawizja.pixel.net.NetClient;
import pl.fawizja.pixel.net.RemoteEntity;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Ekran gry: petla symulacji, kamera, swiat, swiatlo, HUD, autozapis i ochrona zasobow.
 * W trybie online (z {@link NetClient}) nie symuluje swiata: wysyla zamiary gracza na serwer
 * i odwzorowuje jego klatki stanu (postac, potwory, inni gracze, przedmioty, dziennik).
 */
public final class GameScreen extends ScreenAdapter {

    /**
     * Widoczny obszar w kafelkach, jak okno gry Tibii: 15 x 11, postac w srodku (7 pol w bok, 5 w pionie).
     * Reszta ramki widoku gry jest czarna.
     */
    static final float VIEW_TILES_W = 15f;
    static final float VIEW_TILES_H = 11f;
    private static final long AUTOSAVE_MS = 60_000L;
    private static final long GUARD_SAMPLE_MS = 500L;
    private static final long WARMUP_MS = 5_000L;

    private final PixelGame game;
    private final GameSession session;
    private final OrthographicCamera camera = new OrthographicCamera();
    private final ExtendViewport viewport = new ExtendViewport(VIEW_TILES_W, VIEW_TILES_H, camera);
    private final WorldRenderer worldRenderer;
    /** Slady na sniegu (gracz, inni gracze, potwory). */
    private final pl.fawizja.pixel.client.render.Footprints footprints;
    /** Miekka kropka w miejscu dotkniecia mapy (zamiast X) i mala kropka celu marszu. */
    private final pl.fawizja.pixel.client.render.TapMarker tapMarker = new pl.fawizja.pixel.client.render.TapMarker();
    /** Towary kupcow na ladach obok nich. */
    private final pl.fawizja.pixel.client.render.ShopDisplays shopDisplays;
    private final pl.fawizja.pixel.client.render.Roofs roofs;
    private final LightRenderer lightRenderer;
    private final Hud hud;
    private final ResourceGuard guard = new ResourceGuard();
    private final Map<Resource, Float> sample = new EnumMap<>(Resource.class);
    private final long startMs = TimeUtils.millis();
    /**
     * -Dpixel.frameMs=N: staly krok czasu sesji na klatke zamiast zegara (domyslnie 50 ms przy -Dpixel.hour, tryb lokalny).
     * Zrzut regresji GUI powstaje po stalej liczbie klatek; z zegarem rzeczywistym liczba podpowiedzi i komunikatow
     * na ekranie zalezala od szybkosci maszyny (CI vs lokalnie).
     */
    static final long FIXED_FRAME_MS = Long.getLong("pixel.frameMs", System.getProperty("pixel.hour") != null ? 50L : 0L);
    private long fixedFrames;
    private long lastSaveMs;
    private long lastGuardSampleMs;
    private float frameTimeAvg = 1f / 60f;
    /** Okresowy wpis do logu o plynnosci (pomiar na telefonie / emulatorze: logcat, tag Pixel). */
    private static final long FRAME_LOG_MS = 30_000L;
    private final pl.fawizja.pixel.perf.FrameStats frameStats = new pl.fawizja.pixel.perf.FrameStats(8192);
    private long frameLogAtMs = TimeUtils.millis() + FRAME_LOG_MS;
    private final pl.fawizja.pixel.settings.GameSettings settings;
    private final pl.fawizja.pixel.audio.SoundCues soundCues = new pl.fawizja.pixel.audio.SoundCues();
    /** Wypady przy ciosach wrecz: pozycja atakujacego -> przesuniecie sprite'a w kafelkach mapy (dx, dy). */
    private final java.util.Map<Position, float[]> lunges = new java.util.HashMap<>();
    private final com.badlogic.gdx.Preferences settingsPrefs;
    private final com.badlogic.gdx.Preferences barPrefs;
    private final com.badlogic.gdx.Preferences markerPrefs;
    /** Ignorowani gracze (na urzadzeniu, per postac, w tych samych Preferences co znaczniki). */
    private final pl.fawizja.pixel.settings.IgnoreList ignored;
    private final pl.fawizja.pixel.settings.MapMarkers markers;

    private static String barKey(PlayerCharacter pc) {
        return pc.name().trim().toLowerCase(java.util.Locale.ROOT);
    }
    private GuardMode lastMode = GuardMode.NORMAL;
    private long frameStartNs;
    /**
     * Licznik wywolan OpenGL (etap 18): wlaczony przy otwartej diagnostyce albo z -Dpixel.profile=true,
     * ktore co 300 klatek wypisuje srednie (wywolania rysowania, wiazania tekstur, wierzcholki, czas CPU klatki).
     */
    private com.badlogic.gdx.graphics.profiling.GLProfiler profiler;
    private final boolean profileLog = Boolean.getBoolean("pixel.profile");
    private int lastDrawCalls, lastTextureBinds, worldDrawCalls;
    private long profFrames, profCalls, profWorld, profBinds, profVerts, profCpuNs;
    private boolean paused;
    private final List<Monster> visibleMonsters = new ArrayList<>();
    private final Matrix4 screenMatrix = new Matrix4();
    private final Vector3 projected = new Vector3();

    /** Inny gracz widziany w trybie online (interpolowany miedzy klatkami). */
    /** Ostrzezenia o czerwonych czaszkach w widoku (online). */
    private final pl.fawizja.pixel.pvp.SkullWatch skullWatch = new pl.fawizja.pixel.pvp.SkullWatch();

    private static final class RemotePlayer {
        String name;
        Vocation vocation;
        Position from;
        Position to;
        Direction facing = Direction.SOUTH;
        long stepStart;
        int healthPercent;
        String skull = "NONE";
        boolean pvp;
        String guild;
        boolean mounted;
        String pet;
        String title;
        /** Znacznik poziomu dostepu z serwera ("GOD", "GM", "TUTOR") albo null. */
        String access;
        int outfit;
        /** Harmonia mnicha z serwera (0..5, -1 = nie mnich) i spokoj - kropki pod paskami. */
        int harmony = -1;
        boolean serene;
        final pl.fawizja.pixel.client.render.PetFollower petFollower = new pl.fawizja.pixel.client.render.PetFollower();
        boolean seen;
    }

    /** Kolor imienia personelu: GOD zloty, GM niebieski, tutor zielony; null dla zwyklego gracza. */
    private static float[] staffTint(pl.fawizja.pixel.admin.AccessLevel l) {
        switch (l) {
            case GOD: return new float[] {1f, 0.82f, 0.2f};
            case GAMEMASTER: return new float[] {0.4f, 0.85f, 1f};
            case TUTOR: return new float[] {0.55f, 1f, 0.55f};
            default: return null;
        }
    }

    /** Wlasny pupil i czas ostatniej klatki (plynne podazanie). */
    private final pl.fawizja.pixel.client.render.PetFollower myPet = new pl.fawizja.pixel.client.render.PetFollower();
    private long lastPetMs = -1;

    private static final long REMOTE_STEP_MS = 300L;
    /** Odbicie poziome potwora wybrane przez {@code TileArt.monster(klucz, kierunek, klatka, odbicie)}. */
    private final boolean[] monsterFlip = new boolean[1];

    /** Dymki czatu nad postaciami: imie (male litery) -> {tekst, do kiedy (ms zegara)}. */
    private final Map<String, Object[]> bubbles = new java.util.HashMap<>();
    private final com.badlogic.gdx.graphics.g2d.GlyphLayout bubbleLayout = new com.badlogic.gdx.graphics.g2d.GlyphLayout();

    /** Dymek nad postacia na kilka sekund (dluzszy tekst - dluzej, najwyzej 9 s). */
    public void say(String name, String text) {
        String t = text.length() > 90 ? text.substring(0, 87) + "..." : text;
        bubbles.put(name.toLowerCase(java.util.Locale.ROOT), new Object[] {t, uiMs() + Math.min(9_000, 4_000 + 60L * t.length())});
    }

    /** Podskok albo przykucniecie postaci w trakcie emotki z jej dymka (kafelki, dodatni = w gore). */
    private float emoteRise(String name) {
        Object[] b = bubbles.get(name.toLowerCase(java.util.Locale.ROOT));
        if (b == null || (Long) b[1] <= uiMs()) return 0f;
        return pl.fawizja.pixel.net.Emotes.rise((String) b[0], uiMs());
    }

    private final com.badlogic.gdx.graphics.g2d.GlyphLayout nameLayout = new com.badlogic.gdx.graphics.g2d.GlyphLayout();

    /** Napis nad postacia z ciemnym obrysem (4 przesuniecia o ok. 0.6 dp), czytelny na jasnej podlodze. */
    /** Kolor imion graczy nad postaciami (zielony jak w Tibii, czytelny na jasnej i ciemnej podlodze). */
    private static final float[] PLAYER_NAME = {0.35f, 0.95f, 0.35f};

    private static void outlined(SpriteBatch batch, BitmapFont font, String text, float x, float y, float r, float g, float b, float ui) {
        float o = Math.max(1f, Math.round(ui * 0.6f));
        x = Math.round(x);
        y = Math.round(y);
        font.setColor(0f, 0f, 0f, 0.95f);
        font.draw(batch, text, x - o, y);
        font.draw(batch, text, x + o, y);
        font.draw(batch, text, x, y - o);
        font.draw(batch, text, x, y + o);
        font.setColor(r, g, b, 1f);
        font.draw(batch, text, x, y);
    }

    /** Jak {@link #outlined}, ale znikajacy (alpha) - liczba ciosu krytycznego. */
    private static void outlinedFade(SpriteBatch batch, BitmapFont font, String text, float x, float y, float r, float g, float b,
                                     float alpha, float ui) {
        float a = Math.max(0f, Math.min(1f, alpha));
        float o = Math.max(1f, Math.round(ui * 0.6f));
        x = Math.round(x);
        y = Math.round(y);
        font.setColor(0f, 0f, 0f, 0.95f * a);
        font.draw(batch, text, x - o, y);
        font.draw(batch, text, x + o, y);
        font.draw(batch, text, x, y - o);
        font.draw(batch, text, x, y + o);
        font.setColor(r, g, b, a);
        font.draw(batch, text, x, y);
    }

    private void drawBubble(SpriteBatch batch, BitmapFont font, float scale, String name, float wx, float wy) {
        Object[] b = bubbles.get(name.toLowerCase(java.util.Locale.ROOT));
        if (b == null) return;
        long left = (Long) b[1] - uiMs();
        if (left <= 0) {
            bubbles.remove(name.toLowerCase(java.util.Locale.ROOT));
            return;
        }
        projected.set(wx + 0.5f, wy + 1.75f, 0);
        camera.project(projected, viewport.getScreenX(), viewport.getScreenY(), viewport.getScreenWidth(), viewport.getScreenHeight());
        float alpha = Math.min(1f, left / 500f);
        float width = 230 * scale;
        String text = (String) b[0];
        // dymek: ciemne tlo pod tekstem, kilka linii rosnie w gore
        float oldScale = font.getData().scaleX;
        font.getData().setScale(scale);
        com.badlogic.gdx.graphics.Color ink = pl.fawizja.pixel.net.Emotes.isEmote(text)
                ? new com.badlogic.gdx.graphics.Color(0.6f, 0.9f, 1f, alpha) : new com.badlogic.gdx.graphics.Color(1f, 0.95f, 0.6f, alpha);
        bubbleLayout.setText(font, text, ink, width, com.badlogic.gdx.utils.Align.center, true);
        float pad = 4 * scale;
        float top = projected.y + bubbleLayout.height;
        float bx = projected.x - bubbleLayout.width / 2 - pad;
        com.badlogic.gdx.graphics.Color old = batch.getColor().cpy();
        batch.setColor(0.05f, 0.05f, 0.08f, 0.72f * alpha);
        batch.draw(game.art().white(), bx, projected.y - pad, bubbleLayout.width + 2 * pad, bubbleLayout.height + 2 * pad);
        batch.draw(game.art().white(), projected.x - 3 * scale, projected.y - pad - 4 * scale, 6 * scale, 4 * scale);
        batch.setColor(old);
        font.draw(batch, bubbleLayout, projected.x - width / 2, top);
        font.getData().setScale(oldScale);
    }
    /** Imiona czlonkow mojej druzyny (male litery) - z komunikatu PARTY serwera. */
    private final java.util.Set<String> party = new java.util.HashSet<>();
    /** Ci sami czlonkowie w kolejnosci z serwera (lider pierwszy), z oryginalna pisownia - panel druzyny. */
    private final List<String> partyNames = new ArrayList<>();
    private final NetClient net;
    private final Map<Integer, RemotePlayer> remotePlayers = new java.util.HashMap<>();
    private long clockOffset;
    private boolean clockSynced;
    private String lastSelf;
    private NetClient.Frame lastFrame;
    private Direction sentMove;
    private boolean disconnectShown;
    private final MovePredictor predictor = new MovePredictor();
    private long disconnectAtMs;
    private int pvpTargetId = -1;
    /** Wpisywana wiadomosc czatu (desktop: Enter otwiera, Enter wysyla, Esc anuluje) albo null. */
    private StringBuilder chatDraft;
    private final pl.fawizja.pixel.settings.ChatHistory chatHistory = new pl.fawizja.pixel.settings.ChatHistory();
    private String mySkull = "NONE";

    public GameScreen(PixelGame game, PlayerCharacter pc) {
        this(game, pc, null);
    }

    public GameScreen(PixelGame game, PlayerCharacter pc, NetClient net) {
        this.game = game;
        this.net = net;
        long seed = Long.getLong("pixel.seed", TimeUtils.millis()); // stale ziarno: regresja GUI
        MonsterManager monsters = new MonsterManager(game.world(), game.monsters(), game.spawns(), new Random(seed));
        if (net == null) monsters.populate(pc.position()); // online: potwory przychodza z serwera
        this.session = new GameSession(game.items(), game.world(), pc, monsters, new Random(seed + 1),
                game.startingConfig().startLevel());
        // gra lokalna jako GOD: tylko na zyczenie wlasciciela (-Dpixel.god=true, PIXEL_GOD=1 albo plik god.enabled); online decyduje serwer
        if (net == null) pc.setAccessLevel(pl.fawizja.pixel.admin.LocalGod.level(game.platform().saveDirectory()));
        if (net != null) {
            // druzyna, znajomi i informacje o graczach: API sesji wysyla prosby do serwera
            session.setSocialLink(new pl.fawizja.pixel.social.SocialClient.Link() {
                @Override
                public void send(String... fields) { net.send(fields); }

                @Override
                public long now() { return GameScreen.this.now(); }
            });
        }
        session.setTownServices(game.npcs(), game.shops(), game.quests());
        session.setHouses(game.houses(), null);
        session.setChests(game.chests());
        // online pochodnie nad oltarzami zadan zapala serwer; offline -Dpixel.altarGhosts=KNIGHT,DRUID (albo ALL)
        // stawia "duchy" tych profesji na ich polach przed oltarzami (test i zrzuty bez pieciu graczy)
        session.setAltarsLocal(net == null);
        String ghosts = System.getProperty("pixel.altarGhosts");
        if (net == null && ghosts != null) {
            java.util.List<pl.fawizja.pixel.vocation.Vocation> list = new java.util.ArrayList<>();
            for (String g : ghosts.split(",")) {
                if (g.trim().equalsIgnoreCase("ALL")) list.addAll(java.util.Arrays.asList(pl.fawizja.pixel.vocation.Vocation.values()));
                else if (!g.isBlank()) list.add(pl.fawizja.pixel.vocation.Vocation.valueOf(g.trim().toUpperCase(java.util.Locale.ROOT)));
            }
            session.setAltarGhosts(list);
        }
        // offline -Dpixel.bossGhosts=N (1..4): duchy uzupelniaja druzyne na polach przy dzwigni wyprawy na bossa
        Integer bossGhosts = Integer.getInteger("pixel.bossGhosts");
        if (net == null && bossGhosts != null) session.setBossGhosts(bossGhosts);
        // -Dpixel.day=N ustala dzien (potwor dnia, zadanie dnia); przy -Dpixel.hour staly dzien dla regresji GUI
        Long day = Long.getLong("pixel.day", System.getProperty("pixel.hour") != null ? 20_000L : null);
        if (day != null) {
            long d = day;
            session.setToday(() -> d);
        }
        this.worldRenderer = new WorldRenderer(game.world(), game.art());
        this.footprints = new pl.fawizja.pixel.client.render.Footprints(game.world());
        this.shopDisplays = new pl.fawizja.pixel.client.render.ShopDisplays(game.world(), session.npcs().all(), game.shops());
        this.roofs = new pl.fawizja.pixel.client.render.Roofs(game.world());
        this.lightRenderer = new LightRenderer(game.art().lightTexture());
        this.hud = new Hud(game.art(), game.font());
        hud.setMonsterNames(k -> {
            try {
                return game.monsters() == null ? k : game.monsters().get(k).name();
            } catch (IllegalArgumentException e) {
                return k; // potwor z nowszej wersji danych
            }
        });
        if (game.monsters() != null) hud.setMonsterSpecies(game.monsters().all().size());
        java.util.function.Supplier<java.util.Collection<pl.fawizja.pixel.monster.MonsterType>> types =
                () -> game.monsters() == null ? java.util.Collections.emptyList() : game.monsters().all();
        hud.setBestiaryTypes(types);
        this.bestiaryWindows = new pl.fawizja.pixel.client.ui.BestiaryWindows(hud, session, types, this::act);
        this.wheelWindows = new pl.fawizja.pixel.client.ui.WheelWindows(hud, session, this::act);
        Gdx.input.setCatchKey(Input.Keys.BACK, true); // Wstecz na Androidzie zamyka okna zamiast konczyc gre
        this.settingsPrefs = Gdx.app.getPreferences("pixel-settings");
        this.settings = pl.fawizja.pixel.settings.GameSettings.fromMap(settingsPrefs.get());
        settings.setLanguage(pl.fawizja.pixel.i18n.Language.byCode(System.getProperty("pixel.lang"), settings.language()));
        // zrzuty ekranu: -Dpixel.controls=dpad|float4|hold|classic, -Dpixel.sens=1..5 (bez zapisu)
        settings.setControlScheme(pl.fawizja.pixel.movement.ControlScheme.byKey(System.getProperty("pixel.controls"), settings.controlScheme()));
        Integer sens = Integer.getInteger("pixel.sens");
        if (sens != null) settings.setSensitivity(sens);
        // -Dpixel.ui=classic|new: wyglad interfejsu do porownania zrzutow (bez zapisu)
        String ui = System.getProperty("pixel.ui");
        if (ui != null && settings.newUi() != !"classic".equals(ui)) settings.toggleNewUi();
        hud.setSettings(settings);
        // wlasny uklad paska czarow, osobno dla kazdej postaci
        this.barPrefs = Gdx.app.getPreferences("pixel-bar");
        // pasek akcji w postaci (zapis v9; online z serwera w SELF) - dawny uklad z Preferences przenosi syncBar()
        hud.setSpellBar(pl.fawizja.pixel.settings.SpellBar.decode(pc.actionBar()));
        barSeen = pc.actionBar();
        this.markerPrefs = Gdx.app.getPreferences("pixel-markers");
        this.markers = pl.fawizja.pixel.settings.MapMarkers.decode(markerPrefs.getString(barKey(pc), System.getProperty("pixel.markers")));
        this.ignored = pl.fawizja.pixel.settings.IgnoreList.decode(markerPrefs.getString("ignore:" + barKey(pc), ""));
        hud.setMarkers(markers);
        // po aktualizacji gry raz pokaz "Co nowego" (pierwsze uruchomienie tylko zapamietuje wersje)
        String changelog = pl.fawizja.pixel.game.WhatsNew.load();
        String latest = pl.fawizja.pixel.game.WhatsNew.latestVersion(changelog);
        String seenVersion = settingsPrefs.getString("whatsnew", null);
        if (latest != null && !latest.equals(seenVersion)) {
            if (seenVersion != null) showWhatsNew();
            settingsPrefs.putString("whatsnew", latest);
            settingsPrefs.flush();
        }
        session.log().add(now(), EventLog.Kind.SYSTEM, Messages.get("game_screen.witaj_w_varden") + pc.name() + "! "
                + (net == null ? Messages.get("game_screen.tryb_lokalny") : Messages.get("game_screen.online_serwer") + game.platform().serverAddress() + ")"));
        String wake = game.takeLoginNote();                     // pobudka po snie w lozku (PixelGame.playLocal)
        if (wake != null) session.log().add(now(), EventLog.Kind.SYSTEM, wake);
        if (net != null) lastSelf = net.selfSave();
        hud.setChatEnabled(net != null); // offline przycisk czatu pojawia sie tylko obok NPC (rozmowa)
        lastSaveMs = now();
    }

    /** Czas animacji interfejsu (dymki, kursor): zegar, a przy stalym kroku klatki ({@link #FIXED_FRAME_MS}) - klatki. */
    private long uiMs() {
        return FIXED_FRAME_MS > 0 && net == null ? fixedFrames * FIXED_FRAME_MS : TimeUtils.millis();
    }

    /** Czas swiata: w trybie lokalnym liczony od startu klienta, w trybie online - zgrany z serwerem. */
    private long now() {
        if (FIXED_FRAME_MS > 0 && net == null) return fixedFrames * FIXED_FRAME_MS + clockOffset;
        return TimeUtils.millis() - startMs + clockOffset; // czas sesji 0 = 09:00 czasu gry (zegar w GameSession)
    }

    public boolean online() { return net != null; }

    /** Czas swiata uzywany przy rysowaniu (narzedzia deweloperskie, zrzuty ekranu). */
    public long worldTime() { return now(); }

    /** Akcja gracza: lokalnie wykonana od razu, online - wyslana do serwera (jedyne zrodlo prawdy). */
    private void act(String action, long t) {
        if (net != null) net.send("ACT", action);
        else Actions.apply(session, action, t);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int sx, int sy, int pointer, int button) {
                if (hud.touchDown(sx, sy, pointer, session, GameScreen.this::onButton)) return true;
                if (button == Input.Buttons.RIGHT) {
                    if (aimAction != null) { // prawy przycisk anuluje celownik (jak w Tibii)
                        cancelAim();
                        return true;
                    }
                    return lookWorld(sx, sy); // mysz: prawy przycisk = podglad pola
                }
                if (worldPointer >= 0) return tapWorld(sx, sy); // drugi palec na mapie: od razu dotkniecie
                // dotkniecie mapy rozstrzyga sie pozniej: puszczenie = dotkniecie pola, przytrzymanie = podglad,
                // przeciagniecie = joystick pod kciukiem
                worldPointer = pointer;
                worldDownX = sx;
                worldDownY = sy;
                worldCurX = sx;
                worldCurY = sy;
                worldDownMs = TimeUtils.millis();
                worldGesture = 0;
                // wariant "Przytrzymanie na mapie": krotkie dotkniecie = pole, dluzsze = marsz w strone palca
                if (hud.controlScheme() == pl.fawizja.pixel.movement.ControlScheme.HOLD) hold.press(worldDownMs);
                return true;
            }

            @Override
            public boolean touchDragged(int sx, int sy, int pointer) {
                if (pointer == worldPointer) {
                    worldCurX = sx;
                    worldCurY = sy;
                }
                if (pointer == worldPointer && worldGesture == 0
                        && Math.abs(sx - worldDownX) + Math.abs(sy - worldDownY) > DRAG_SLOP_DP * Gdx.graphics.getDensity()) {
                    worldGesture = hud.floatJoystick(worldDownX, worldDownY, pointer) ? 1 : 3;
                }
                return hud.touchDragged(sx, sy, pointer);
            }

            @Override
            public boolean touchUp(int sx, int sy, int pointer, int button) {
                if (pointer == worldPointer) {
                    worldPointer = -1;
                    boolean holdScheme = hold.down();
                    boolean tap = hold.release(TimeUtils.millis());
                    hud.holdIndicator(false, 0, 0, 0f, null);
                    // przytrzymanie: dotknieciem pola jest tylko krotkie dotkniecie bez przeciagania
                    if (worldGesture == 0 && (!holdScheme || tap)) return tapWorld(worldDownX, worldDownY);
                }
                return hud.touchUp(sx, sy, pointer);
            }

            @Override
            public boolean scrolled(float amountX, float amountY) {
                return hud.scrolled(Gdx.input.getX(), Gdx.input.getY(), amountY);
            }

            @Override
            public boolean keyTyped(char c) {
                if (chatDraft == null && hud.houseTyping()) {
                    if (c != '\r' && c != '\n') hud.houseType(c);
                    return true;
                }
                if (chatDraft == null) return false;
                if (c == '\b') {
                    if (chatDraft.length() > 0) chatDraft.setLength(chatDraft.length() - 1);
                } else if (c >= 32 && c != 127 && chatDraft.length() < 200) {
                    chatDraft.append(c);
                }
                return true;
            }

            @Override
            public boolean keyDown(int keycode) {
                if (chatDraft == null && hud.houseTyping()) {
                    // imie goscia w oknie domu: Enter zaprasza, Esc wraca do okna wlasciciela
                    if (keycode == Input.Keys.ENTER) hud.houseSubmit(GameScreen.this::onButton);
                    else if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) hud.closeTop(session);
                    return true;
                }
                if (chatDraft != null) {
                    if (keycode == Input.Keys.ENTER) {
                        chatHistory.add(chatDraft.toString());
                        sendChat(chatDraft.toString());
                        chatDraft = null;
                    } else if (keycode == Input.Keys.UP || keycode == Input.Keys.DOWN) {
                        String h = keycode == Input.Keys.UP ? chatHistory.older() : chatHistory.newer();
                        if (h != null) chatDraft = new StringBuilder(h);
                    } else if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) {
                        chatDraft = null;
                    } else if (keycode == Input.Keys.TAB) {
                        cycleChatTab();
                    }
                    return true; // klawisze ida do czatu, nie do gry
                }
                Direction tapped = keyDirection(keycode);
                if (tapped != null) {
                    queuedDir = tapped;
                    queuedAtMs = TimeUtils.millis();
                    queuedFrom = session.character().position();
                    queuedPending = false;
                    return true;
                }
                switch (keycode) {
                    case Input.Keys.ENTER: onButton("chat"); return true;
                    case Input.Keys.TAB: cycleChatTab(); return true;
                    case Input.Keys.E: onButton("use"); return true;
                    case Input.Keys.R: onButton("run"); return true;
                    case Input.Keys.T: onButton("torch"); return true;
                    case Input.Keys.B:
                    case Input.Keys.I: onButton("bag"); return true;
                    case Input.Keys.G: onButton("pick"); return true;
                    case Input.Keys.F3: onButton("diag"); return true;
                    case Input.Keys.SPACE:
                    case Input.Keys.C: onButton("target"); return true;
                    case Input.Keys.V: onButton("fightmode"); return true;
                    case Input.Keys.NUM_1: onButton("spell:0"); return true;
                    case Input.Keys.NUM_2: onButton("spell:1"); return true;
                    case Input.Keys.NUM_3: onButton("spell:2"); return true;
                    case Input.Keys.NUM_4: onButton("spell:3"); return true;
                    case Input.Keys.NUM_5: onButton("spell:4"); return true;
                    case Input.Keys.NUM_6: onButton("spell:5"); return true;
                    case Input.Keys.NUM_7: onButton("spell:6"); return true;
                    case Input.Keys.NUM_8: onButton("spell:7"); return true;
                    case Input.Keys.NUM_9: onButton("spell:8"); return true;
                    case Input.Keys.NUM_0: onButton("spell:9"); return true;
                    case Input.Keys.Q: onButton("potion_hp"); return true;
                    case Input.Keys.F: onButton("potion_mp"); return true;
                    case Input.Keys.K: onButton("book"); return true;
                    case Input.Keys.N: onButton("talk"); return true;
                    case Input.Keys.P: hud.toggleCharacter(); return true;
                    case Input.Keys.L: onButton("fish"); return true;
                    case Input.Keys.O: hud.openAchievements(false); return true;
                    case Input.Keys.M: onButton("mount"); return true;
                    case Input.Keys.J: hud.toggleMap(); return true;
                    case Input.Keys.Z: hud.openJournal(); return true;
                    case Input.Keys.H: openHunt(); return true;
                    case Input.Keys.U: wheelWindows.toggle(); return true;
                    case Input.Keys.ESCAPE:
                        if (aimAction != null) cancelAim();
                        else hud.closeTop(session);
                        return true;
                    case Input.Keys.BACK: {
                        if (aimAction != null) {
                            cancelAim();
                            return true;
                        }
                        // Android: Wstecz zamyka okna; gdy nic nie jest otwarte - dwa razy w ciagu 2 s wychodzi z gry
                        if (hud.closeTop(session)) return true;
                        long t = System.currentTimeMillis();
                        if (t - backPressedMs <= 2000) {
                            Gdx.app.exit();
                        } else {
                            backPressedMs = t;
                            session.log().add(now(), EventLog.Kind.INFO, Messages.get("game_screen.nacisnij_wstecz_jeszcze_raz"));
                        }
                        return true;
                    }
                    default: return false;
                }
            }
        });
    }

    /** Czas ostatniego Wstecz bez otwartego okna (wyjscie po drugim nacisnieciu). */
    private long backPressedMs = -10_000;

    /** Akcja przycisku/wiersza menu z zewnatrz (desktop: -Dpixel.button=id). */
    public void press(String id) { onButton(id); }

    private void onButton(String id) {
        long t = now();
        if (id.startsWith("house:")) {
            houseAction(id, t);
            return;
        }
        if (id.startsWith("settings:") && settings.apply(id)) {
            // wielkosc joysticka, wyglad (okragla minimapa na telefonie jest wyzsza): nowy uklad HUD-u
            if (id.equals("settings:joysize") || id.equals("settings:ui")) hud.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            if (id.equals("settings:controls")) {
                // nowy wariant sterowania: stary palec nie prowadzi juz postaci
                hud.resetMoveControls();
                hold.cancel();
                session.log().add(t, EventLog.Kind.INFO, Messages.get("game_screen.sterowanie") + settings.controlScheme().label() + ".");
            }
            settingsPrefs.put(settings.toMap());
            settingsPrefs.flush();
            lastGuardSampleMs = 0; // nowy limit klatek od razu
            return;
        }
        switch (id) {
            case "bag": hud.toggleBag(); break;
            // tryb walki: wysylamy tryb docelowy (nie przelaczenie), zeby klient i serwer sie nie rozjechaly
            case "fightmode": act("fightmode:" + session.character().fightMode().next().name(), t); break;
            case "diag": hud.toggleDiagnostics(); break;
            case "bar_saved":
                storeBar(hud.spellBar().encode());
                break;
            case "book": hud.toggleBook(); break;
            case "quest": hud.openJournal(); break;
            case "store": openStore(); break;
            case "wheel": wheelWindows.toggle(); break;
            case "hud:skills": hud.toggleSkills(); break;
            case "hud:vip": hud.toggleVip(); break;
            case "vip:add": openChat("/znajomi dodaj "); break;
            case "logout": {
                // jak w Tibii: nie w trakcie walki z graczem (blokada wylogowania, takze dla zaatakowanego)
                long block = session.character().logoutBlockedMs(t);
                if (block > 0) {
                    session.log().add(t, EventLog.Kind.WARNING, Messages.get("game_screen.nie_mozesz_sie_wylogowac")
                            + (block + 999) / 1000 + " s.");
                } else if (hud.logoutConfirmOpen()) {
                    hud.closeTop(session);
                } else {
                    hud.confirmLogout();
                }
                break;
            }
            case "logout:yes": {
                long block = session.character().logoutBlockedMs(t);
                hud.closeTop(session);
                if (block > 0) {
                    session.log().add(t, EventLog.Kind.WARNING, Messages.get("game_screen.nie_mozesz_sie_wylogowac_2"));
                    break;
                }
                if (net == null) game.saveCharacter(session.character());
                Gdx.app.postRunnable(() -> game.backToMenu(Messages.get("game_screen.wylogowano_postac_zapisana")));
                break;
            }
            case "talk": hud.toggleNpc(session); break;
            case "chat": openChat(); break;
            case "target":
                if (net == null) session.cycleTarget(t);
                else {
                    session.cycleTarget(t); // natychmiastowa odpowiedz na ekranie, serwer potwierdza
                    Monster m = session.combat().target();
                    net.send("TARGET", m == null ? "-" : String.valueOf(m.id()));
                }
                break;
            case "bag_use": {
                // runa z plecaka: "Uzyj z..." jak w Tibii - celownik, nastepne dotkniecie pola mapy ja rzuca
                int sel = hud.selectedSlot();
                ItemStack st = sel >= 0 ? session.character().backpack().get(sel) : null;
                pl.fawizja.pixel.item.ItemType it = st == null ? null : session.items().get(st.itemId());
                if (it != null && it.category() == pl.fawizja.pixel.item.ItemCategory.RUNE && session.spells().registry().find(it.key()) != null) {
                    hud.closeBag();
                    startAim("slot_at:" + sel, it.key(), -1);
                } else {
                    act("slot_use:" + sel, t);
                }
                hud.clearSelection();
                break;
            }
            case "bag_equip":
                act("slot_equip:" + hud.selectedSlot(), t);
                hud.clearSelection();
                break;
            case "bag_keep":
                act("slot_keep:" + hud.selectedSlot(), t);
                break;
            case "bag_sort":
                act("sort", t);
                hud.clearSelection();
                break;
            case "bag_drop":
                act("slot_drop:" + hud.selectedSlot(), t);
                hud.clearSelection();
                break;
            default:
                if (id.startsWith("bplayer:")) {
                    // lista walki: okienko gracza
                    int pid = Integer.parseInt(id.substring(8));
                    RemotePlayer rp = remotePlayers.get(pid);
                    if (rp != null) hud.openPlayerMenu(pid, rp.name, pvpTargetId == pid, party.contains(rp.name.toLowerCase(java.util.Locale.ROOT)));
                } else if (id.startsWith("player:")) {
                    hud.closePlayerMenu();
                    String[] f = id.split(":", 3);
                    if (f.length == 3 && f[1].equals("party")) session.invitePlayerToParty(f[2]);
                    if (f.length == 3 && f[1].equals("friend")) session.addFriend(f[2]);
                    if (f.length == 3 && f[1].equals("unfriend")) session.removeFriend(f[2]);
                    if (f.length == 3 && f[1].equals("pm")) {
                        // rozmowa prywatna: zakladka czatu z graczem i od razu pisanie
                        hud.chatTabs().openPrivate(f[2]);
                        openChat();
                    }
                    if (net == null) break;
                    switch (f[1]) {
                        case "trade": net.send("TRADE", "REQUEST", f[2]); break;
                        case "party":
                        case "friend":
                        case "unfriend":
                        case "pm": break; // wyslane przez API sesji powyzej
                        case "guild": net.send("GUILD", "INVITE", f[2]); break;
                        case "duel": net.send("DUEL", "REQUEST", f[2]); break;
                        case "inspect": net.send("INSPECT", f[2]); break;
                        case "attack": {
                            int pid = Integer.parseInt(f[2]);
                            boolean stop = pvpTargetId == pid;
                            net.send("ATTACK", stop ? "-" : String.valueOf(pid));
                            pvpTargetId = stop ? -1 : pid;
                            break;
                        }
                        default:
                    }
                } else if (id.startsWith("trade:")) {
                    if (net == null) break;
                    if (id.equals("trade:accept")) net.send("TRADE", "ACCEPT");
                    else if (id.equals("trade:cancel")) net.send("TRADE", "CANCEL");
                    else if (id.startsWith("trade:offer:")) net.send("TRADE", "OFFER", id.substring(12));
                } else if (id.equals("npc:market")) {
                    if (net == null) session.log().add(t, EventLog.Kind.INFO, Messages.get("game_screen.rynek_graczy_dziala_online"));
                    else net.send("MARKET", "LIST");
                } else if (id.equals("npc:orders")) {
                    if (net == null) session.log().add(t, EventLog.Kind.INFO, Messages.get("game_screen.zamowienia_kupna_dzialaja_onli"));
                    else net.send("WANT", "LIST");
                } else if (id.startsWith("market:")) {
                    String[] f = id.split(":");
                    if (net != null && f.length == 3 && f[1].equals("buy")) net.send("MARKET", "BUY", f[2]);
                    if (net != null && f.length == 3 && f[1].equals("cancel")) net.send("MARKET", "CANCEL", f[2]);
                } else if (id.equals("npc:hunting")) {
                    huntingGrounds();
                } else if (id.equals("npc:bosses")) {
                    bossReport(t);
                } else if (id.equals("book:hunt")) {
                    openHunt();
                } else if (id.startsWith("hunt:go:")) {
                    hud.closeTop(session);
                    goHunting(id.substring("hunt:go:".length()), t);
                } else if (id.startsWith("cmd:")) {
                    // emotka z okna /emotki: zamknij okno, zeby bylo widac dymek i ruch postaci
                    String typed = id.substring("cmd:".length());
                    if (pl.fawizja.pixel.net.Emotes.text(typed) != null || pl.fawizja.pixel.net.Emotes.diceSides(typed) > 0) hud.closeTop(session);
                    sendChat(id.substring("cmd:".length())); // polecenie z okna /pomoc
                } else if (id.equals("ach:bestiary")) {
                    hud.openAchievements(true);
                } else if (wheelWindows.handle(id, t)) {
                    // Kolo Przeznaczenia: okna w WheelWindows, zmiany punktow przez act (online serwer)
                } else if (bestiaryWindows.handle(id, t)) {
                    // bestiariusz (klasy, karta potwora) i charmy: okna w BestiaryWindows
                } else if (id.startsWith("outfit:")) {
                    chooseOutfit(id.substring("outfit:".length()));
                    if (hud.storeOpen()) openStore(); // zakladka Outfity: nowy "noszony teraz" od razu
                } else if (id.startsWith("ach:title:")) {
                    String key = id.substring("ach:title:".length());
                    if (key.equals(session.character().title())) key = "-"; // ponowne dotkniecie zdejmuje tytul
                    if (net != null) net.send("TITLE", key);
                    else session.chooseTitle(key, t);
                } else if (id.equals("hunt:reset")) {
                    hunt.reset(t, session.character());
                    session.log().add(t, EventLog.Kind.INFO, Messages.get("game_screen.nowe_polowanie_liczniki_od"));
                    hud.updateInfo(HUNT_TITLE, huntRows(t));
                } else if (id.equals("journal:hunt")) {
                    hud.openJournal(); // zamkniecie okna
                    goHunting(t);
                } else if (id.startsWith("journal:")) {
                    // dziennik: marsz do zleceniodawcy (przewodnik = najblizszy przewodnik na pietrze)
                    String key = id.startsWith("journal:goto:") ? id.substring(13) : null;
                    pl.fawizja.pixel.npc.Npc target = key == null ? nearestGuide() : session.npcs().get(key);
                    hud.openJournal(); // zamkniecie okna
                    if (target == null || target.position().z() != session.character().position().z()) {
                        session.log().add(t, EventLog.Kind.INFO, target == null ? Messages.get("game_screen.nie_znam_drogi") : target.name() + Messages.get("game_screen.jest_na_innym_pietrze"));
                    } else if (session.npcNearby() == target) {
                        hud.openNpc(session);
                    } else if (walkTo(target.position(), t, false, true)) {
                        walkNpc = target;
                    }
                } else if (id.startsWith(pl.fawizja.pixel.game.Store.TAB_ACTION)) {
                    storeTab = pl.fawizja.pixel.game.Store.Tab.byKey(id.substring(pl.fawizja.pixel.game.Store.TAB_ACTION.length()));
                    openStore();
                } else if (id.startsWith("store:soon:")) {
                    pl.fawizja.pixel.game.StoreOutfits.Entry e = pl.fawizja.pixel.game.StoreOutfits.find(id.substring("store:soon:".length()));
                    if (e != null) session.log().add(t, EventLog.Kind.INFO, Messages.get("game_screen.sklep_stroj") + e.name() + Messages.get("game_screen.bedzie_dostepny_wkrotce"));
                } else if (id.startsWith("store:ask:")) {
                    storeAsk(id.substring("store:ask:".length()));
                } else if (id.startsWith("store:buy:")) {
                    act("store:" + id.substring("store:buy:".length()), t);
                    openStore();
                } else if (id.startsWith("map:goto:")) {
                    String[] xy = id.substring(9).split(",");
                    walkToMapSpot(Integer.parseInt(xy[0]), Integer.parseInt(xy[1]), t);
                } else if (id.startsWith("friend:pm:")) {
                    hud.closeFriends();
                    openChat("/w " + id.substring(10) + ", ");
                } else if (id.startsWith("friend:")) {
                    // naglowki listy znajomych
                } else if (id.startsWith("ranking:")) {
                    // wiersze rankingu tylko do czytania
                } else if (id.startsWith("battle:")) {
                    // lista walki w konsoli: wybor celu po id potwora
                    int mid = Integer.parseInt(id.substring(7));
                    for (Monster m : session.monsters().monsters()) {
                        if (m.id() != mid || m.isDead()) continue;
                        session.combat().setTarget(m);
                        if (net != null) net.send("TARGET", String.valueOf(m.id()));
                    }
                } else if (id.startsWith("spell:")) {
                    // pole paska: czar, combo, mikstura albo runa (na cel / na siebie / celownik)
                    useBarSlot(Integer.parseInt(id.substring(6)), t);
                } else {
                    // use, run, torch, pick, fish, potion_*, cast:, craft:, slot_move:, npc:*
                    act(id, t);
                }
        }
    }

    private void openChat() { openChat(""); }

    /** Tab (Shift+Tab wstecz): nastepna zakladka czatu. */
    private void cycleChatTab() {
        boolean back = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        hud.chatTabs().cycle(back ? -1 : 1);
    }

    /**
     * Wiadomosc CHAT z serwera (nadawca, tekst, kanal albo null) do zakladki czatu; ogloszenia trafiaja do dziennika
     * (zakladka Serwer i napis na widoku gry). Publiczne dla podgladu zakladek (-Dpixel.open=chattabs).
     */
    public void receiveChat(String from, String text, String channel, long now) {
        pl.fawizja.pixel.chat.ChatTabs.Route r = pl.fawizja.pixel.chat.ChatTabs.route(from, text, channel, session.character().name());
        if (r.style() == pl.fawizja.pixel.chat.ChatTabs.Style.BROADCAST) session.log().add(now, EventLog.Kind.SYSTEM, r.text());
        else hud.chatTabs().add(r, now);
    }

    /** Okno czatu z gotowym poczatkiem tekstu (np. "/w Ula, "). */
    private void openChat(String start) {
        if (Gdx.app.getType() == Application.ApplicationType.Android) {
            Gdx.input.getTextInput(new Input.TextInputListener() {
                @Override
                public void input(String text) {
                    chatHistory.add(text);
                    sendChat(text);
                }

                @Override
                public void canceled() { }
            }, Messages.get("game_screen.czat") + hud.chatTabs().active().title(), start, chatHistory.last() != null ? Messages.get("game_screen.ostatnio") + chatHistory.last()
                    : net == null ? Messages.get("game_screen.rozmowa_z_npc_obok") : Messages.get("game_screen.wiadomosc_albo_slowo_do"));
        } else {
            chatHistory.resetCursor();
            chatDraft = new StringBuilder(start);
        }
    }

    /** Polecenia GOD w grze lokalnej: poziom z {@link pl.fawizja.pixel.admin.LocalGod}, bez serwera. */
    private final class LocalGodHost implements pl.fawizja.pixel.admin.GodCommands.Context {
        @Override public pl.fawizja.pixel.admin.AccessLevel level() { return session.character().accessLevel(); }
        @Override public pl.fawizja.pixel.game.GameSession session() { return session; }
        @Override public long now() { return GameScreen.this.now(); }
        @Override public boolean online() { return false; }

        @Override
        public pl.fawizja.pixel.game.GameSession find(String name) {
            return name != null && name.trim().equalsIgnoreCase(session.character().name()) ? session : null;
        }

        @Override public void audit(String line) { Gdx.app.log("god", line); }

        @Override
        public void broadcast(String text) {
            session.log().add(now(), EventLog.Kind.SYSTEM, "[" + Messages.get("server_world.ogloszenie") + "] " + text);
        }

        @Override public void save() { game.saveCharacter(session.character()); }

        @Override
        public String shutdown(String arg) {
            game.saveCharacter(session.character());
            Gdx.app.exit();
            return Messages.get("god.local_exit");
        }
    }

    private static final int COMMANDS_PER_PAGE = 16;

    /** Okno /komendy: polecenia poziomu dostepu, po 16 na strone (zakladki "1", "2", ... pod lista). */
    private void openCommandList(int page) {
        pl.fawizja.pixel.admin.AccessLevel lv = session.character().accessLevel();
        java.util.List<String[]> all = pl.fawizja.pixel.admin.GodCommands.helpRows(lv);
        int pages = Math.max(1, (all.size() + COMMANDS_PER_PAGE - 1) / COMMANDS_PER_PAGE);
        int p = Math.max(1, Math.min(pages, page));
        java.util.List<String[]> rows = new java.util.ArrayList<>(all.subList((p - 1) * COMMANDS_PER_PAGE, Math.min(all.size(), p * COMMANDS_PER_PAGE)));
        if (pages > 1) {
            for (int i = 1; i <= pages; i++) rows.add(new String[] {String.valueOf(i), "", "", "cmd:/komendy " + i, i == p ? "tab_on" : "tab"});
        }
        hud.openInfo(Messages.get("god.window_title", lv.label()), rows);
    }

    /** Wpisuje tekst w czacie tak jak gracz (narzedzia: -Dpixel.chat="/komendy;/i miecz"). */
    public void chat(String text) { sendChat(text); }

    private void sendChat(String text) {
        if (text == null || text.isBlank()) return;
        // zakladka kanalu (Handel, Gildia, Druzyna, rozmowa prywatna): zwykly tekst idzie na jej kanal
        String[] channelMsg = hud.chatTabs().outgoing(text);
        if (channelMsg != null) {
            if (net != null) net.send(channelMsg);
            else session.log().add(now(), EventLog.Kind.INFO, Messages.get("game_screen.kanal") + hud.chatTabs().active().title() + Messages.get("game_screen.dziala_online_lokalny_czat"));
            return;
        }
        // okna z danymi wlasnej postaci - dzialaja tez bez serwera
        String cmd = java.text.Normalizer.normalize(text.trim().toLowerCase(java.util.Locale.ROOT).replace('ł', 'l'),
                java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        pl.fawizja.pixel.magic.SpellRegistry.Incantation inc = session.spells().registry().incantation(text);
        String spell = inc == null ? null : inc.key();
        if (spell != null) {
            // inkantacja jak w Tibii: slowa nad postacia i czar (online inni gracze widza slowa w czacie);
            // dopisek (utevo res "wilk", exura sio "imie") idzie za dwukropkiem
            String param = inc.param() == null ? "" : inc.param().replaceAll("[:\\t\\n]", " ").trim();
            act("cast:" + spell + (param.isEmpty() ? "" : ":" + param), now());
            if (net != null) {
                net.send("CHAT", text.trim()); // serwer odsyla slowa wszystkim, takze nam (zakladka Lokalny)
            } else {
                say(session.character().name(), text.trim());
                session.log().add(now(), EventLog.Kind.CHAT, session.character().name() + ": " + text.trim());
            }
            return;
        }
        if (cmd.equals("/smierc")) {
            if (net != null) net.send("DEATH");
            else showDeath(session.combat().lastDeath() == null ? List.of()
                    : pl.fawizja.pixel.combat.DeathRecap.rows(session.combat().lastDeath()));
            return;
        }
        if (cmd.equals("/lowiska") || cmd.equals("/lowisko")) {
            huntingGrounds();
            return;
        }
        if (cmd.equals("/analiza") || cmd.equals("/polowanie")) {
            openHunt();
            return;
        }
        if (cmd.matches("/(komendy|commands)( \\d+)?")) {
            // lista polecen poziomu dostepu (GOD/GM/tutor) w oknie ze stronami; serwer i tak sprawdza poziom przy kazdym poleceniu
            openCommandList(cmd.contains(" ") ? Integer.parseInt(cmd.substring(cmd.indexOf(' ') + 1)) : 1);
            return;
        }
        if (cmd.equals("/pomoc") || cmd.equals("/polecenia") || cmd.equals("/help")) {
            hud.openInfo(Messages.get("game_screen.polecenia"), pl.fawizja.pixel.net.ChatCommands.helpRows());
            // personel (GOD/GM/tutor): pod oknem tez lista polecen jego poziomu (/komendy)
            if (cmd.equals("/help") && session.character().accessLevel().staff()) {
                for (String l : pl.fawizja.pixel.admin.GodCommands.helpLines(session.character().accessLevel())) {
                    session.log().add(now(), EventLog.Kind.INFO, l);
                }
            }
            return;
        }
        if (cmd.equals("/nowosci") || cmd.equals("/nowości") || cmd.equals("/zmiany")) {
            showWhatsNew();
            return;
        }
        if (cmd.equals("/wydarzenia") || cmd.equals("/dzis")) {
            if (net != null) net.send("EVENTS");
            else hud.openInfo(Messages.get("game_screen.wydarzenia"), pl.fawizja.pixel.game.EventsReport.rows(session, null));
            return;
        }
        if (cmd.equals("/lupy") || cmd.equals("/lup")) {
            if (net != null) net.send("LOOTLOG");
            else showLoot(session.combat().lootLog().rows(now()));
            return;
        }
        if (cmd.equals("/szukaj") || cmd.startsWith("/szukaj ")) {
            String q = text.trim().length() > 7 ? text.trim().substring(7).trim() : "";
            if (q.length() < pl.fawizja.pixel.inventory.ItemSearch.MIN_QUERY) {
                session.log().add(now(), EventLog.Kind.INFO, Messages.get("game_screen.wpisz_co_najmniej_2"));
                return;
            }
            List<String[]> rows = pl.fawizja.pixel.inventory.ItemSearch.rows(session.character(), session.items(), q);
            if (rows.isEmpty()) session.log().add(now(), EventLog.Kind.INFO, Messages.get("game_screen.nie_masz_przedmiotu") + q + Messages.get("game_screen.w_plecaku_wyposazeniu_ani"));
            else hud.openInfo(Messages.get("game_screen.szukaj") + q, rows);
            return;
        }
        if (cmd.equals("/autorzy") || cmd.equals("/grafiki")) {
            hud.openInfo(Messages.get("game_screen.autorzy_grafik"), pl.fawizja.pixel.game.Credits.rows());
            return;
        }
        if (cmd.equals("/blogoslawienstwa") || cmd.equals("/błogosławieństwa") || cmd.equals("/bless")) {
            hud.openInfo(Messages.get("skill_sheet.blogoslawienstwa"), pl.fawizja.pixel.character.BlessingStatus.rows(session.character(), session.npcs()));
            return;
        }
        if (cmd.equals("/emotki")) {
            hud.openInfo(Messages.get("game_screen.emotki"), pl.fawizja.pixel.net.Emotes.rows());
            return;
        }
        if (cmd.equals("/strój") || cmd.equals("/stroj") || cmd.startsWith("/strój ") || cmd.startsWith("/stroj ")) {
            String color = text.indexOf(' ') < 0 ? "" : text.substring(text.indexOf(' ') + 1).trim();
            if (!color.isEmpty()) {
                chooseOutfit(color);
                return;
            }
            List<String[]> rows = new ArrayList<>();
            int now = session.character().outfit();
            for (int i = 0; i < pl.fawizja.pixel.character.Outfit.count(); i++) {
                rows.add(new String[] {Messages.get("game_screen.kolor") + pl.fawizja.pixel.character.Outfit.name(i), i == now ? Messages.get("game_screen.noszony_teraz") : Messages.get("game_screen.dotknij_aby_zalozyc"), "", "outfit:" + i});
            }
            hud.openInfo(Messages.get("game_screen.stroj"), rows);
            return;
        }
        if (cmd.equals("/podpowiedzi")) {
            act("tips_off", now());
            return;
        }
        if (cmd.startsWith("/idź ") || cmd.startsWith("/idz ")) {
            String q = text.substring(text.indexOf(' ') + 1).trim();
            String fq = pl.fawizja.pixel.inventory.ItemSearch.fold(q);
            if (fq.equals("lowisko") || fq.equals("polowanie") || fq.equals("zlecenie")) {
                goHunting(now());
                return;
            }
            pl.fawizja.pixel.npc.Npc n = pl.fawizja.pixel.npc.NpcFinder.find(session.npcs(), q, session.character().position());
            if (n == null) {
                session.log().add(now(), EventLog.Kind.INFO, Messages.get("game_screen.nie_widze_tu_nikogo"));
            } else if (session.npcNearby() == n) {
                hud.openNpc(session);
            } else if (walkTo(n.position(), now(), true, true)) {
                walkNpc = n; // rozmowa otworzy sie po dojsciu
                session.log().add(now(), EventLog.Kind.INFO, Messages.get("game_screen.idziesz_do") + n.name() + ".");
            } else {
                session.log().add(now(), EventLog.Kind.INFO, Messages.get("game_screen.nie_znam_drogi_do") + n.name() + ".");
            }
            return;
        }
        if (cmd.equals("/cena") || cmd.startsWith("/cena ")) {
            String q = text.indexOf(' ') < 0 ? "" : text.substring(text.indexOf(' ') + 1).trim();
            pl.fawizja.pixel.item.ItemType it = pl.fawizja.pixel.npc.PriceCheck.find(session.items(), q);
            if (it == null) {
                session.log().add(now(), EventLog.Kind.INFO, q.isEmpty() ? Messages.get("game_screen.wpisz_nazwe_przedmiotu_np")
                        : Messages.get("game_screen.nie_znam_przedmiotu") + q + "\".");
                return;
            }
            hud.openInfo(Messages.get("game_screen.cena") + it.name(), pl.fawizja.pixel.npc.PriceCheck.rows(it, session.npcs(), session.shops()));
            if (net != null) net.send("PRICE", it.key()); // ceny z rynku graczy przychodza w dzienniku
            return;
        }
        if (cmd.equals("/ignoruj") || cmd.startsWith("/ignoruj ")) {
            String who = text.indexOf(' ') < 0 ? "" : text.substring(text.indexOf(' ') + 1).trim();
            if (who.isEmpty()) {
                List<String> all = ignored.all();
                session.log().add(now(), EventLog.Kind.INFO, all.isEmpty() ? Messages.get("game_screen.nikogo_nie_ignorujesz_ignoruj")
                        : Messages.get("game_screen.ignorujesz") + String.join(", ", all) + Messages.get("game_screen.ponowne_ignoruj_imie_przywraca"));
                return;
            }
            boolean on = ignored.toggle(who);
            session.log().add(now(), EventLog.Kind.SYSTEM, on ? Messages.get("game_screen.ignorujesz_2") + who + Messages.get("game_screen.wiadomosci_od_tej_osoby")
                    : ignored.all().size() >= pl.fawizja.pixel.settings.IgnoreList.MAX && !ignored.ignores(who) && !who.isEmpty()
                    ? Messages.get("game_screen.lista_ignorowanych_jest_pelna") + pl.fawizja.pixel.settings.IgnoreList.MAX + ")." : Messages.get("game_screen.nie_ignorujesz_juz") + who + ".");
            markerPrefs.putString("ignore:" + barKey(session.character()), ignored.encode());
            markerPrefs.flush();
            return;
        }
        if (cmd.equals("/statystyki") || cmd.equals("/staty")) {
            hud.openInfo(Messages.get("game_screen.statystyki") + session.character().name(), pl.fawizja.pixel.game.StatsReport.rows(session.character()));
            return;
        }
        if (cmd.equals("/gracze") || cmd.equals("/online")) {
            if (net != null) net.send("WHO");
            else session.log().add(now(), EventLog.Kind.INFO, Messages.get("game_screen.lista_graczy_dziala_online"));
            return;
        }
        if (cmd.equals("/potwor") || cmd.equals("/bonus")) {
            session.log().add(now(), EventLog.Kind.SYSTEM, Messages.get("game_screen.potwor_dnia") + pl.fawizja.pixel.daily.BoostedCreature.describe(session.boostedCreature()) + ".");
            return;
        }
        if (cmd.equals("/znaczniki") || cmd.startsWith("/znacznik ") || cmd.equals("/znacznik")) {
            markerCommand(text.trim(), cmd);
            return;
        }
        if (cmd.equals("/kolo") || cmd.equals("/koło")) {
            wheelWindows.open();
            return;
        }
        if (cmd.equals("/bossowie")) {
            bossReport(now());
            return;
        }
        if (cmd.equals("/zadania") || cmd.equals("/dziennik")) {
            hud.openJournal();
            return;
        }
        if (cmd.equals("/osiagniecia") || cmd.equals("/bestiariusz")) {
            hud.openAchievements(cmd.equals("/bestiariusz"));
            return;
        }
        // obok NPC: slowa kluczowe trafiaja do niego (polecenia czatu zaczynaja sie od '/')
        if (!text.startsWith("/") && session.npcNearby() != null) {
            String said = pl.fawizja.pixel.npc.DialogueRegistry.normalize(text);
            boolean known = pl.fawizja.pixel.npc.DialogueRegistry.defaults().answer(session.npcNearby(), said) != null;
            if (!said.isEmpty() && (known || net == null)) {
                act("npc:ask:" + said, now());
                return;
            }
        }
        String emote = pl.fawizja.pixel.net.Emotes.text(text);
        int dice = pl.fawizja.pixel.net.Emotes.diceSides(text);
        if (net == null && dice > 0) emote = pl.fawizja.pixel.net.Emotes.diceText(dice, 1 + com.badlogic.gdx.math.MathUtils.random(dice - 1));
        if (net == null && emote != null) {
            // bez serwera emotka i tak pokazuje sie nad wlasna postacia
            String me = session.character().name();
            session.log().add(now(), EventLog.Kind.CHAT, me + ": " + emote);
            say(me, emote);
            return;
        }
        String[] house = net == null ? pl.fawizja.pixel.net.ChatCommands.toMessage(text) : null;
        if (house != null && house[0].equals("PVP")) {
            // bez serwera tryb PvP i tak dziala (zasady w PvpRules; inni gracze sa tylko online)
            if (house[1].equals("TOGGLE") || house[1].equals("ON") != session.pvpMode()) session.togglePvpMode(now());
            return;
        }
        if (house != null && house[0].equals("HOUSE")) {
            // bez serwera: opis domu i lista gosci (inni gracze wchodza tylko online)
            if (house[1].equals("GUEST")) session.addHouseGuest(house[2], house.length > 3 && house[3].equals("entry")
                    ? pl.fawizja.pixel.house.HouseAccess.ENTRY : pl.fawizja.pixel.house.HouseAccess.FULL, now());
            else if (house[1].equals("UNGUEST")) session.removeHouseGuest(house[2], now());
            else if (house[1].equals("INFO")) session.log().add(now(), EventLog.Kind.SYSTEM, session.houseInfo());
            else session.log().add(now(), EventLog.Kind.INFO, Messages.get("game_screen.sprzedaz_domu_innemu_graczowi"));
            return;
        }
        String[] social = pl.fawizja.pixel.net.ChatCommands.toMessage(text);
        if (net == null && social != null && (social[0].equals("GOD") || social[0].equals("ADMIN"))) {
            // gra lokalna: te same polecenia co na serwerze; poziom z flagi wlasciciela (zwykly gracz dostaje odmowe)
            pl.fawizja.pixel.admin.GodCommands.execute(new LocalGodHost(), social);
            return;
        }
        if (session.social().command(social)) return; // /zaproś, /przyjmij, /drużyna, /znajomi dodaj ... (API sesji)
        if (net == null && social != null && social[0].equals("FRIEND")) {
            hud.openFriends(session.friends()); // offline: lista z zapisu postaci
            return;
        }
        if (net == null) {
            session.log().add(now(), EventLog.Kind.INFO, Messages.get("game_screen.czat_z_graczami_dziala"));
            return;
        }
        String[] msg = social;
        if (msg == null) {
            session.log().add(now(), EventLog.Kind.INFO, Messages.get("game_screen.nieznane_polecenie_lista_pomoc"));
            hud.openInfo(Messages.get("game_screen.polecenia"), pl.fawizja.pixel.net.ChatCommands.helpRows());
            return;
        }
        net.send(msg);
    }

    /** Dotkniecie swiata: wybor potwora jako celu. */
    private boolean tapWorld(int sx, int sy) {
        return tapTile(worldTile(sx, sy));
    }

    private Position worldTile(int sx, int sy) {
        Vector3 w = viewport.unproject(new Vector3(sx, sy, 0));
        int x = (int) Math.floor(w.x);
        int y = game.world().height() - 1 - (int) Math.floor(w.y);
        return new Position(x, y, session.character().position().z());
    }

    /** Dotyk na mapie czekajacy na rozstrzygniecie (-1: brak) i jego gest: 0 jeszcze nie wiadomo, 1 joystick, 2 podglad, 3 nic. */
    private int worldPointer = -1;
    private int worldDownX, worldDownY, worldGesture;
    /** Biezace polozenie palca na mapie (ekran) - kierunek marszu w wariancie "Przytrzymanie na mapie". */
    private int worldCurX, worldCurY;
    private long worldDownMs;
    /** Przytrzymanie palca na polu tak dlugo otwiera podglad pola (jak "Look" w Tibii). */
    static final long LONG_PRESS_MS = 450;
    /** Przesuniecie palca (w dp), od ktorego dotkniecie staje sie przeciaganiem. */
    static final float DRAG_SLOP_DP = 10f;

    private boolean lookWorld(int sx, int sy) {
        lookTile(worldTile(sx, sy));
        return true;
    }

    /** Podglad pola: opis w konsoli i krotki dymek nad postacia (desktop: -Dpixel.look=x,y). */
    public void lookTile(Position p) {
        String text = session.lookAt(p, now());
        if (settings.vibrate()) Gdx.input.vibrate(25);
        say(session.character().name(), text.length() > 90 ? text.substring(0, 87) + "..." : text);
    }

    /** Przytrzymanie palca bez ruchu: podglad pola (sprawdzane co klatke). */
    private void checkLongPress() {
        if (worldPointer < 0 || worldGesture != 0 || TimeUtils.millis() - worldDownMs < LONG_PRESS_MS) return;
        if (hold.down()) return; // przytrzymanie na mapie to marsz (podglad pola: prawy przycisk myszy)
        worldGesture = 2;
        lookWorld(worldDownX, worldDownY);
    }

    /** Dotkniecie pola swiata (desktop: -Dpixel.tap=x,y). */
    public boolean tapTile(Position p) {
        if (sleepBed != null) return true;                      // postac juz spi
        if (aimAction != null) return aimAt(p);                 // celownik runy: to pole jest celem
        if (p.z() == session.character().position().z() && session.refuseBed(p, now())) return true;
        if (net != null) {
            for (Map.Entry<Integer, RemotePlayer> e : remotePlayers.entrySet()) {
                if (e.getValue().to.equals(p)) {
                    // menu gracza zamiast natychmiastowego ataku (bez przypadkowej czaszki na ekranie dotykowym)
                    RemotePlayer rp = e.getValue();
                    hud.openPlayerMenu(e.getKey(), rp.name, pvpTargetId == e.getKey(),
                            party.contains(rp.name.toLowerCase(java.util.Locale.ROOT)));
                    return true;
                }
            }
        }
        pl.fawizja.pixel.npc.Npc npc = session.npcs().at(p);
        if (npc != null) {
            if (session.npcNearby() == npc) hud.openNpc(session);
            else if (walkTo(p, now(), true, true)) {
                walkNpc = npc; // rozmowa otworzy sie po dojsciu
                tapPulse(p, false);
            } else {
                tapPulse(p, true);
                session.log().add(now(), EventLog.Kind.INFO, npc.name() + Messages.get("game_screen.podejdz_blizej_aby_porozmawiac"));
            }
            return true;
        }
        if (p.z() == session.character().position().z() && session.usableAt(p)) {
            // jak "Uzyj" w Tibii: skrzynia, drzwi, okno, pochodnia, dzwignia, drabina, krzak, drogowskaz, studnia...
            // obok = uzycie od razu (drzwi: drugie dotkniecie zamyka), dalej = marsz na pole obok i uzycie
            if (p.distanceTo(session.character().position()) <= 1) useOpening(p);
            else if (walkTo(p, now(), true, true)) {
                walkUse = p;
                tapPulse(p, false);
            } else {
                tapPulse(p, true);
                session.log().add(now(), EventLog.Kind.INFO, Messages.get("game_screen.nie_ma_tam_drogi"));
            }
            return true;
        }
        if (session.dummies().at(p)) {
            // manekin: obok = trening (jak UZYJ), dalej = marsz na pole obok
            if (p.distanceTo(session.character().position()) <= 1) onButton("use");
            else tapPulse(p, !walkTo(p, now(), true, true));
            return true;
        }
        boolean hit = session.targetAt(p);
        if (hit && net != null) {
            Monster m = session.combat().target();
            if (m != null) net.send("TARGET", String.valueOf(m.id()));
        }
        if (!hit) {
            // dotkniecie ciala obok: przeszukanie
            pl.fawizja.pixel.world.GroundItems.Corpse c = session.ground().corpseAt(p);
            if (c != null && c.hasLoot() && p.distanceTo(session.character().position()) <= 1) {
                act("loot", now());
                return true;
            }
            // dotkniecie wolnego pola: marsz tam (jak na duzej mapie, bez komunikatu)
            boolean walking = walkTo(p, now(), true, c != null && c.hasLoot());
            // jasna kropka w miejscu dotkniecia; bez drogi (sciana, woda, za daleko) - krotka czerwonawa
            if (!p.equals(session.character().position())) tapPulse(p, !walking);
            return walking;
        }
        return hit;
    }

    /** Marsz do pola wskazanego na duzej mapie: trasa liczona raz, potem krok po kroku jak joystick. */
    private List<Position> walkPath;
    private int walkIndex;
    private pl.fawizja.pixel.npc.Npc walkNpc;
    /** Online: dom, ktorego okno otworzy sie po odpowiedzi serwera (HOUSEINFO), i czy to okno wlasciciela. */
    private String pendingHouse;
    private boolean pendingHouseOwner;

    /** Przycisk okna domu (house:buy:id, house:invite:poziom:Imie, house:unguest:Imie, house:door, house:leave). */
    private void houseAction(String id, long t) {
        String[] f = id.split(":", 4);
        pl.fawizja.pixel.house.House h = session.houses().get(hud.houseOpenId());
        if (h == null) return;
        switch (f[1]) {
            case "buy":
                if (net == null) session.buyHouseAtDoor(f[2], t);
                else net.send("HOUSE", "BUY", f[2]);
                break;
            case "invite":
                if (f.length < 4) return;
                if (net == null) session.addHouseGuest(f[3], pl.fawizja.pixel.house.HouseAccess.guestLevel(f[2]), t);
                else net.send("HOUSE", "GUEST", f[3], f[2]);
                break;
            case "unguest":
                if (net == null) session.removeHouseGuest(f[2], t);
                else net.send("HOUSE", "UNGUEST", f[2]);
                break;
            case "leave":
                if (net == null) session.leaveHouseAtDoor(t);
                else net.send("HOUSE", "LEAVE");
                break;
            case "door":
                // wlasciciel w srodku: drzwi otwiera i zamyka przycisk okna
                if (net == null) session.useAt(h.door(), t);
                else net.send("ACT", "open:" + h.door().x() + ":" + h.door().y() + ":" + h.door().z()); // stan wraca jako DOOR
                break;
            default:
                return;
        }
        if (net == null) hud.updateHouse(session.houseView(h));  // online: odswiezy odpowiedz serwera
    }

    /** Drzwi, okno albo skrzynia do otwarcia po dojsciu (null = brak). */
    private Position walkUse;

    /** Otwiera albo zamyka drzwi i okna, otwiera skrzynie (online: serwer decyduje, drzwi i okna od razu na ekranie). */
    private void useOpening(Position p) {
        long t = now();
        GameSession.DoorTap tap = session.houseDoorTap(p);
        if (tap != null && tap != GameSession.DoorTap.TOGGLE) {
            // drzwi domu: okno jak w Tibii (wolny/zajety, cena, czynsz, Kup) albo okno wlasciciela w srodku
            pl.fawizja.pixel.house.House h = session.houses().byDoor(p);
            if (net == null) {
                hud.openHouse(h, session.houseView(h), tap == GameSession.DoorTap.OWNER_WINDOW);
            } else {
                pendingHouse = h.id();
                pendingHouseOwner = tap == GameSession.DoorTap.OWNER_WINDOW;
                net.send("HOUSE", "DOOR", h.id());              // wlasciciel i goscie: odpowiedz HOUSEINFO z serwera
            }
            return;
        }
        boolean crate = !session.chestAt(p) && game.world().crates().isStorage(p);
        if (net == null) {
            session.useAt(p, t);
        } else {
            net.send("ACT", "open:" + p.x() + ":" + p.y() + ":" + p.z());
            // drzwi i okna: stan przychodzi z serwera (DOOR); dzwignia, oltarz, drabina, krzak itd. rozstrzyga serwer
            // (komunikat przyjdzie w logu); od razu na ekranie tylko skrzynia, pochodnia i obrany krzak
            if (crate) session.toggleCrateView(p);
            else if (game.world().wallLights().isWallLight(p) && !game.world().isQuestTorch(p)) game.world().wallLights().toggle(p);
            else if (session.berries().kindAt(p) != null && !session.berries().isEmpty(p, t)) session.berries().pick(p, t);
        }
        if (crate && session.openCrate() != null && !hud.npcOpen()) hud.openNpc(session);
    }

    /**
     * Pogoda: -Dpixel.weather=rain|snow|sandstorm|clear wymusza rodzaj (narzedzia, zrzuty); przy -Dpixel.hour
     * bez niej zawsze pogodnie, by regresja GUI nie zalezala od zegara.
     */
    private final Weather.Kind forcedWeather = Weather.parse(System.getProperty("pixel.weather",
            System.getProperty("pixel.hour") != null ? "clear" : null));
    private final pl.fawizja.pixel.client.render.WeatherRenderer weatherRenderer = new pl.fawizja.pixel.client.render.WeatherRenderer();
    /** Przy -Dpixel.hour opady stoja w miejscu: zrzut regresji GUI nie zalezy od czasu klatki. */
    private final boolean frozenWeather = System.getProperty("pixel.hour") != null;
    private Weather.Kind lastWeather;
    private int lastWeatherZ;
    private long lastClimateDay = Long.MIN_VALUE;

    /** Pogoda nad graczem; zmiana rodzaju trafia do dziennika. */
    private Weather.Kind weatherAt(Position p, long wallMs) {
        Weather.Kind k = !settings.weather() ? Weather.Kind.CLEAR
                : forcedWeather != null ? (p.z() < 0 ? Weather.Kind.CLEAR : forcedWeather)
                : Weather.at(wallMs, game.world().regionAt(p), p.z());
        // komunikat tylko przy zmianie pogody na miejscu (nie przy zejsciu pod ziemie i powrocie)
        if (lastWeather != null && k != lastWeather && p.z() >= 0 && lastWeatherZ >= 0) {
            session.log().add(now(), EventLog.Kind.INFO, Weather.describe(k));
        }
        // nowy dzien gry: klimat dnia w krainie (slonecznie / zmiennie / deszczowo)
        long day = Math.floorDiv(wallMs, Weather.DAY_MS);
        if (settings.weather() && forcedWeather == null && p.z() >= 0 && day != lastClimateDay) {
            pl.fawizja.pixel.world.Region region = game.world().regionAt(p);
            session.log().add(now(), EventLog.Kind.INFO, Weather.describe(Weather.climate(wallMs, region), region));
            lastClimateDay = day;
        }
        lastWeatherZ = p.z();
        lastWeather = k;
        return k;
    }

    /** Okno podsumowania smierci; lokalnie otwiera sie samo po nowej smierci (online po komunikacie DEATHLOG). */
    private pl.fawizja.pixel.combat.DeathRecap.Report shownDeath;

    private void showDeath(List<String[]> rows) {
        if (rows.isEmpty()) {
            session.log().add(now(), EventLog.Kind.INFO, Messages.get("game_screen.jeszcze_nie_zginales_w"));
            return;
        }
        hud.openInfo(Messages.get("game_screen.ostatnia_smierc"), rows);
    }

    /** Okno ostatnich lupow (/lupy). */
    private void showLoot(List<String[]> rows) {
        if (rows.isEmpty()) {
            session.log().add(now(), EventLog.Kind.INFO, Messages.get("game_screen.nie_pokonales_jeszcze_potwora"));
            return;
        }
        hud.openInfo(Messages.get("game_screen.ostatnie_lupy"), rows);
    }

    private void checkDeath() {
        pl.fawizja.pixel.combat.DeathRecap.Report d = session.combat().lastDeath();
        if (net != null || d == null || d == shownDeath) return;
        shownDeath = d;
        if (Boolean.getBoolean("pixel.noDeathWindow")) return;   // podglad GUI: zrzut samego ciala, bez okna podsumowania
        showDeath(pl.fawizja.pixel.combat.DeathRecap.rows(d));
    }

    private static final String HUNT_TITLE = "Analiza polowania";
    /** Licznik polowania (od wejscia do gry albo od "Zacznij od nowa"); liczy z danych postaci, dziala tez online. */
    private pl.fawizja.pixel.stats.HuntAnalyzer hunt;
    private long huntRefreshMs;

    /** Kolor stroju: online wybiera serwer (widza go inni gracze), offline sesja. */
    private void chooseOutfit(String color) {
        if (net != null) net.send("OUTFIT", color);
        else session.chooseOutfit(color, now());
    }

    /** /znacznik nazwa (tu), /znacznik usuń nazwa, /znaczniki (lista z marszem, z bossem tygodnia i miejscem śmierci). */
    private void markerCommand(String text, String cmd) {
        long t = now();
        PlayerCharacter pc = session.character();
        int sp = text.indexOf(' ');
        String arg = sp < 0 ? "" : text.substring(sp + 1).trim();
        if (cmd.startsWith("/znacznik ") && (cmd.startsWith("/znacznik usun ") || cmd.startsWith("/znacznik usuń "))) {
            String name = arg.substring(arg.indexOf(' ') + 1);
            session.log().add(t, EventLog.Kind.INFO, markers.remove(name) ? Messages.get("game_screen.usunieto_znacznik") + name + "." : Messages.get("game_screen.nie_ma_znacznika") + name + ".");
        } else if (cmd.startsWith("/znacznik ") && !arg.isEmpty()) {
            if (markers.put(arg, pc.position())) {
                session.log().add(t, EventLog.Kind.SYSTEM, Messages.get("game_screen.znacznik") + pl.fawizja.pixel.settings.MapMarkers.cleanName(arg) + Messages.get("game_screen.zapisany_tutaj_zolta_kropka"));
            } else {
                session.log().add(t, EventLog.Kind.WARNING, Messages.get("game_screen.najwyzej") + pl.fawizja.pixel.settings.MapMarkers.MAX + Messages.get("game_screen.znacznikow_usun_ktorys_znaczni"));
            }
        } else {
            List<String[]> rows = new ArrayList<>();
            for (pl.fawizja.pixel.settings.MapMarkers.Marker m : hud.allMarkers(session)) {
                boolean here = m.at().z() == pc.position().z();
                int dist = here ? Math.max(Math.abs(m.at().x() - pc.position().x()), Math.abs(m.at().y() - pc.position().y())) : -1;
                rows.add(here ? new String[] {m.name(), dist + Messages.get("game_screen.pol_stad_dotknij_aby"), "", "map:goto:" + m.at().x() + "," + m.at().y()}
                        : new String[] {m.name(), Messages.get("game_screen.inne_pietro") + m.at().z() + ")"});
            }
            if (rows.isEmpty()) rows.add(new String[] {Messages.get("game_screen.brak_znacznikow"), Messages.get("game_screen.znacznik_nazwa_zapisuje_miejsc")});
            rows.add(new String[] {Messages.get("game_screen.usuwanie"), Messages.get("game_screen.znacznik_usun_nazwa")});
            hud.openInfo(Messages.get("game_screen.znaczniki"), rows);
            return;
        }
        markerPrefs.putString(barKey(pc), markers.encode());
        markerPrefs.flush();
    }

    private List<String[]> huntRows(long t) {
        List<String[]> rows = pl.fawizja.pixel.stats.HuntAnalyzer.rows(hunt.report(t, session.character()));
        pl.fawizja.pixel.monster.MonsterType boost = session.boostedCreature();
        if (boost != null) rows.add(new String[] {Messages.get("game_screen.potwor_dnia_2"), pl.fawizja.pixel.daily.BoostedCreature.describe(boost)});
        pl.fawizja.pixel.item.Enchantments ench = session.character().enchantments();
        for (pl.fawizja.pixel.item.Enchantments.Kind k : pl.fawizja.pixel.item.Enchantments.Kind.values()) {
            if (ench.active(k)) rows.add(new String[] {k.label(), Messages.get("game_screen.zostalo") + pl.fawizja.pixel.item.Enchantments.describe(ench.remainingMs(k))});
        }
        rows.add(new String[] {Messages.get("game_screen.zacznij_od_nowa"), Messages.get("game_screen.zeruje_liczniki_polowania"), "", "hunt:reset"});
        return rows;
    }

    /** Okno sklepu: tytul z saldem konta, wiersz na kazda oferte (dotkniecie = potwierdzenie zakupu). */
    private void openStore() {
        hud.openInfo(storeTitle(), storeRows());
        storeRefreshMs = now();
    }

    private long storeRefreshMs;
    /** Wybrana zakladka sklepu (pamietana do konca gry: powrot z potwierdzenia zakupu wraca na nia). */
    private pl.fawizja.pixel.game.Store.Tab storeTab = pl.fawizja.pixel.game.Store.Tab.SERVICES;

    private String storeTitle() {
        return String.format(Messages.get("game_screen.s_w_banku_d"), Messages.tr(pl.fawizja.pixel.client.ui.Hud.STORE_TITLE), session.character().bank().balance());
    }

    /** Zakladki (Uslugi / Premium / Outfity) i tresc wybranej - {@link pl.fawizja.pixel.game.Store#rows}. */
    private java.util.List<String[]> storeRows() {
        return pl.fawizja.pixel.game.Store.rows(session, storeTab);
    }

    /** Potwierdzenie zakupu ("Kupić...?"): Kup / Wróć do sklepu; oferta niedostepna - tylko powod w konsoli. */
    private void storeAsk(String key) {
        pl.fawizja.pixel.game.Store.Offer o = pl.fawizja.pixel.game.Store.find(session, key);
        long t = now();
        if (o == null) return;
        if (!o.available()) {
            session.log().add(t, EventLog.Kind.INFO, Messages.get("game_screen.sklep") + o.name() + " - " + o.blocked() + ".");
            return;
        }
        long bank = session.character().bank().balance();
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        rows.add(new String[] {o.name(), o.desc()});
        rows.add(new String[] {String.format(Messages.get("game_screen.cena_d_gp"), o.price()), bank >= o.price()
                ? String.format(Messages.get("game_screen.z_konta_w_banku"), bank - o.price())
                : String.format(Messages.get("game_screen.na_koncie_masz_tylko"), bank)});
        rows.add(new String[] {Messages.get("game_screen.tak_kupuje"), String.format(Messages.get("game_screen.zaplac_d_gp_z"), o.price()), "", "store:buy:" + key});
        rows.add(new String[] {Messages.get("game_screen.nie_wroc"), Messages.get("game_screen.do_listy_sklepu"), "", "store"});
        hud.openInfo(Messages.tr(pl.fawizja.pixel.client.ui.Hud.STORE_TITLE) + Messages.get("game_screen.kupic") + o.name() + "?", rows);
    }

    /** Otwarta lista sklepu odswieza saldo i stan ofert (online zakup potwierdza serwer chwile pozniej). */
    private void updateStore(long t) {
        if (t - storeRefreshMs < 500 || !hud.infoOpen(Messages.tr(pl.fawizja.pixel.client.ui.Hud.STORE_TITLE) + " - ")) return;
        hud.updateInfo(storeTitle(), storeRows());
        storeRefreshMs = t;
    }

    /**
     * Dotkniecie duzej mapy: marsz na wskazane pole, a gdy jest nieprzechodnie (sciana, woda) - na najblizsze
     * przechodnie w promieniu 2 pol (palec trafia z dokladnoscia kilku pol). Bez drogi: "Nie mozna tam dojsc.".
     */
    private void walkToMapSpot(int x, int y, long t) {
        Position me = session.character().position();
        Position goal = new Position(x, y, me.z());
        if (!session.world().isWalkable(goal)) {
            Position best = null;
            for (int r = 1; r <= 2 && best == null; r++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dx = -r; dx <= r; dx++) {
                        Position p = goal.translate(dx, dy);
                        if (session.world().isWalkable(p) && (best == null || p.distanceTo(goal) < best.distanceTo(goal))) best = p;
                    }
                }
            }
            if (best != null) goal = best;
        }
        if (goal.equals(me)) {
            session.log().add(t, EventLog.Kind.INFO, Messages.get("game_screen.juz_tu_jestes"));
            return;
        }
        if (!walkTo(goal, t, true, false)) {
            tapPulse(goal, true);
            session.log().add(t, EventLog.Kind.WARNING, Messages.get("game_screen.nie_mozna_tam_dojsc"));
            return;
        }
        tapPulse(goal, false);
        session.log().add(t, EventLog.Kind.INFO, Messages.get("game_screen.ide_do_zaznaczonego_miejsca") + walkPath.size() + Messages.get("game_screen.pol_joystick_przerywa_marsz"));
    }

    private void openHunt() {
        long t = now();
        updateHunt(t);
        hud.openInfo(HUNT_TITLE, huntRows(t));
        huntRefreshMs = t;
    }

    /** Odswiezanie otwartego okna analizy co sekunde; licznik startuje z pierwsza klatka gry. */
    /** Okna bestiariusza i charmow (klasa, karta, charmy). */
    private pl.fawizja.pixel.client.ui.BestiaryWindows bestiaryWindows;
    /** Okna Kola Przeznaczenia. */
    private pl.fawizja.pixel.client.ui.WheelWindows wheelWindows;

    /** Podglad okien kola (-Dpixel.open=wheel...). */
    public pl.fawizja.pixel.client.ui.WheelWindows wheelWindows() { return wheelWindows; }

    /** Podglad okien bestiariusza i charmow (-Dpixel.open=bestiary_stage / charms / charm). */
    public pl.fawizja.pixel.client.ui.BestiaryWindows bestiaryWindows() { return bestiaryWindows; }

    private void updateHunt(long t) {
        bestiaryWindows.update(t);
        wheelWindows.update(t);
        if (hunt == null) hunt = new pl.fawizja.pixel.stats.HuntAnalyzer(session.items());
        if (!hunt.started()) hunt.reset(t, session.character());
        if (t - huntRefreshMs >= 1000 && hud.infoOpen(HUNT_TITLE)) {
            hud.updateInfo(HUNT_TITLE, huntRows(t));
            huntRefreshMs = t;
        }
    }

    /** Raport bossow: online od serwera (tam zyja potwory), lokalnie z wlasnego swiata. */
    private void huntingGrounds() {
        hud.openInfo(Messages.get("game_screen.gdzie_polowac_poziom") + session.character().level() + ")", pl.fawizja.pixel.game.HuntingAdvisor.rows(session));
    }

    private void bossReport(long t) {
        if (net != null) {
            net.send("BOSS");
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (MonsterManager.BossStatus b : session.monsters().bossReport(t)) rows.add(MonsterManager.row(b, t));
        hud.openInfo(Messages.get("game_screen.bossowie") + rows.size() + ")", rows);
    }

    private pl.fawizja.pixel.npc.Npc nearestGuide() {
        pl.fawizja.pixel.npc.Npc best = null;
        Position me = session.character().position();
        for (pl.fawizja.pixel.npc.Npc n : session.npcs().all()) {
            if (n.role() != pl.fawizja.pixel.npc.NpcRole.GUIDE || n.position().z() != me.z()) continue;
            if (best == null || n.position().distanceTo(me) < best.position().distanceTo(me)) best = n;
        }
        return best;
    }

    /** Automatyczne lupy: cialo z lupem obok postaci przeszukiwane co 0,8 s; najwyzej 2 proby na cialo (pelny plecak). */
    private long autoLootAtMs;
    private final Map<Position, Integer> autoLootTries = new java.util.HashMap<>();

    /** Automatyczne leczenie (ustawienie): najpierw najsilniejszy dostepny czar leczacy, bez many - mikstura; najwyzej raz na sekunde. */
    private long autoHealAtMs;

    private void autoHeal(long t) {
        int pct = settings.autoHeal();
        PlayerCharacter pc = session.character();
        if (pct == 0 || t < autoHealAtMs || pc.isDead() || pc.health() * 100L >= (long) pc.maxHealth() * pct) return;
        String spell = session.spells().bestHeal(t);
        if (spell == null && pc.backpack().count(session.items().byKey("health_potion").id()) == 0) return;
        autoHealAtMs = t + 1000;
        act(spell != null ? "cast:" + spell : "potion_hp", t);
    }

    /** Automatyczna mikstura many (ustawienie): po zdrowiu, najwyzej raz na sekunde i nie w tej samej chwili. */
    private long autoManaAtMs;

    private void autoMana(long t) {
        int pct = settings.autoMana();
        PlayerCharacter pc = session.character();
        if (pct == 0 || t < autoManaAtMs || t < autoHealAtMs - 500 || pc.isDead() || pc.maxMana() <= 0
                || pc.mana() * 100L >= (long) pc.maxMana() * pct) return;
        if (pc.backpack().count(session.items().byKey("mana_potion").id()) == 0) return;
        autoManaAtMs = t + 1000;
        act("potion_mp", t);
    }

    /** Automatyczne jedzenie (ustawienie): pierwsze jedzenie z plecaka, gdy sytosci zostalo mniej niz minuta. */
    private long autoEatAtMs;

    private void autoEat(long t) {
        PlayerCharacter pc = session.character();
        if (!settings.autoEat() || t < autoEatAtMs || pc.isDead() || pc.fedMs() >= 60_000) return;
        autoEatAtMs = t + 2000;
        List<ItemStack> bag = pc.backpack().contents();
        for (int i = 0; i < bag.size(); i++) {
            ItemStack s = bag.get(i);
            if (s != null && session.items().get(s.itemId()).category() == pl.fawizja.pixel.item.ItemCategory.FOOD) {
                act("slot_use:" + i, t);
                return;
            }
        }
    }

    /** Cel sam (ustawienie): bez celu i poza strefa ochronna wybiera potwora stojacego najwyzej 3 pola od postaci. */
    private long autoTargetAtMs;

    private void autoTarget(long t) {
        if (!settings.autoTarget() || t < autoTargetAtMs || session.character().isDead()) return;
        autoTargetAtMs = t + 500;
        if (session.combat().target() != null || session.world().isProtectionZone(session.character().position())) return;
        if (session.combat().threatNear(3)) onButton("target");
    }

    private void autoLoot(long t) {
        if (!settings.autoLoot() || t < autoLootAtMs || session.character().isDead()) return;
        autoLootAtMs = t + 800;
        Position c = session.corpseToSearch();
        if (c == null) {
            autoLootTries.clear();
            return;
        }
        int tries = autoLootTries.getOrDefault(c, 0);
        if (tries >= 2) return;
        autoLootTries.put(c, tries + 1);
        act("loot", t);
    }

    /** Marsz do pola na biezacym pietrze (dotkniecie duzej mapy; desktop: -Dpixel.walkto=x,y). */
    public void walkTo(Position goal) { walkTo(goal, now(), false, false); }

    /**
     * quiet: dotkniecie swiata (bez komunikatow); nextTo: zatrzymanie na polu obok celu (NPC, cialo).
     * Zwraca, czy marsz sie zaczal.
     */
    /** Okno "Co nowego" z dziennika zmian dolaczonego do gry (3 ostatnie wersje). */
    private void showWhatsNew() {
        java.util.List<String[]> rows = pl.fawizja.pixel.game.WhatsNew.rows(pl.fawizja.pixel.game.WhatsNew.load(), 5, 18);
        hud.openInfo(Messages.get("game_screen.co_nowego_w_grze"), rows);
    }

    /** Marsz do najblizszego legowiska potwora ze zlecenia lowcy albo zadania dnia (/idz lowisko, dziennik). */
    private void goHunting(long now) {
        pl.fawizja.pixel.monster.SpawnPoint sp = session.huntingGround();
        if (sp == null) {
            session.log().add(now, EventLog.Kind.INFO, Messages.get("game_screen.brak_zadania_z_potworami"));
        } else if (walkTo(sp.center(), now, true, true)) {
            session.log().add(now, EventLog.Kind.INFO, Messages.get("game_screen.idziesz_na_lowisko") + session.monsterName(sp.monsterKey()) + ".");
        } else {
            session.log().add(now, EventLog.Kind.INFO, Messages.get("game_screen.nie_znam_drogi_na") + session.whereToHunt(sp.monsterKey()) + ".");
        }
    }

    /** Marsz do najblizszego legowiska wybranego potwora (okno "Gdzie polowac"). */
    private void goHunting(String monsterKey, long now) {
        pl.fawizja.pixel.monster.SpawnPoint best = null;
        Position me = session.character().position();
        for (pl.fawizja.pixel.monster.SpawnPoint sp : session.monsters().spawns()) {
            if (!sp.monsterKey().equals(monsterKey) || sp.center().z() != me.z()) continue;
            if (best == null || sp.center().distanceTo(me) < best.center().distanceTo(me)) best = sp;
        }
        if (best != null && walkTo(best.center(), now, true, true)) {
            session.log().add(now, EventLog.Kind.INFO, Messages.get("game_screen.idziesz_na_lowisko") + session.monsterName(monsterKey) + ".");
        } else {
            session.log().add(now, EventLog.Kind.INFO, Messages.get("game_screen.stad_nie_dojdziesz_szukaj") + session.monsterName(monsterKey)
                    + session.whereToHunt(monsterKey) + Messages.get("game_screen.woznice_przy_fontannach_woza"));
        }
    }

    private boolean walkTo(Position goal, long now, boolean quiet, boolean nextTo) {
        Position from = session.character().position();
        if (nextTo && from.distanceTo(goal) <= 1) return false;
        List<Position> path = nextTo ? pathNextTo(from, goal)
                : pl.fawizja.pixel.movement.Pathfinder.pathAvoiding(session.world(), from, goal, WALK_MAX_NODES, this::walkAvoid);
        if (path.isEmpty()) {
            stopWalk();
            if (!quiet) session.log().add(now, EventLog.Kind.INFO, from.equals(goal) ? Messages.get("game_screen.juz_tu_jestes") : Messages.get("game_screen.nie_ma_drogi_do"));
            return false;
        }
        walkPath = path;
        walkIndex = 0;
        walkNpc = null;
        hud.setWalkTarget(path.get(path.size() - 1));
        if (!quiet) session.log().add(now, EventLog.Kind.INFO, Messages.get("game_screen.ide_do_zaznaczonego_miejsca") + path.size() + Messages.get("game_screen.pol_joystick_przerywa_marsz"));
        return true;
    }

    /** Najkrotsza trasa na ktores wolne pole obok celu (cel sam bywa zajety: NPC, fontanna z cialem). */
    private List<Position> pathNextTo(Position from, Position goal) {
        List<Position> best = java.util.Collections.emptyList();
        for (Direction d : Direction.values()) {
            Position n = goal.translate(d.dx, d.dy);
            if (session.npcs().at(n) != null) continue;
            List<Position> p = pl.fawizja.pixel.movement.Pathfinder.pathAvoiding(session.world(), from, n, WALK_MAX_NODES, this::walkAvoid);
            if (!p.isEmpty() && (best.isEmpty() || p.size() < best.size())) best = p;
        }
        return best;
    }

    // ------------------------------------------------------------------ pasek akcji: celownik, combo, zapis

    /** Celownik runy: akcja bez pola ("item:klucz:at" z paska albo "slot_at:N" z plecaka) albo null. */
    private String aimAction;
    /** Efekt runy celownika (zasieg, promien do podgladu) i pole paska, ktore go wlaczylo (-1 = plecak). */
    private pl.fawizja.pixel.magic.SpellType aimRune;
    private int aimSlot = -1;
    /** Pola w zasiegu i polu widzenia runy (podglad celownika), liczone dla pozycji aimFrom. */
    private final java.util.Set<Position> aimReach = new java.util.HashSet<>();
    private Position aimFrom;
    /** Zrzuty ekranu: pole pod kursorem celownika (-Dpixel.aimHover=x,y), bo Xvfb nie ma myszy. */
    private Position aimHoverOverride;

    /** Pole paska: czar, combo, mikstura albo runa wg trybu (celownik czeka na dotkniecie pola mapy). */
    private void useBarSlot(int slot, long t) {
        String entry = hud.barEntry(slot);
        if (entry == null) return;
        if (aimAction != null && aimSlot == slot) { // drugie nacisniecie tego samego pola anuluje celownik
            cancelAim();
            return;
        }
        if (entry.startsWith("item:") && pl.fawizja.pixel.settings.SpellBar.MODE_AIM.equals(pl.fawizja.pixel.settings.SpellBar.mode(entry))) {
            String key = pl.fawizja.pixel.settings.SpellBar.base(entry).substring(5);
            if (!session.items().containsKey(key)) return;
            if (session.character().backpack().count(session.items().byKey(key).id()) == 0) {
                session.log().add(t, EventLog.Kind.WARNING, Messages.get("game_screen.nie_masz_w_plecaku") + session.items().byKey(key).name() + ".");
                return;
            }
            startAim("item:" + key + ":at", key, slot);
            return;
        }
        if (aimAction != null) cancelAim();
        act(entry, t);
    }

    /** Wlacza celownik runy (jak "Use with..." w Tibii): nastepne dotkniecie pola mapy uzyje jej na tym polu. */
    public void startAim(String action, String runeKey, int slot) {
        pl.fawizja.pixel.magic.SpellType sp = session.spells().registry().find(runeKey);
        if (sp == null || !session.items().containsKey(runeKey)) return;
        aimAction = action;
        aimRune = sp;
        aimSlot = slot;
        aimFrom = null;
        boolean phone = hud.mobile();
        hud.setAim(Messages.get("game_screen.celownik") + session.items().byKey(runeKey).name() + " - " + (phone ? Messages.get("game_screen.dotknij_pola") : Messages.get("game_screen.kliknij_pole"))
                + Messages.get("game_screen.anuluj") + (phone ? Messages.get("game_screen.wstecz") : "Esc") + (slot >= 0 ? Messages.get("game_screen.albo_to_samo_pole") : "") + ")", slot);
    }

    public void cancelAim() {
        aimAction = null;
        aimRune = null;
        aimSlot = -1;
        hud.setAim(null, -1);
    }

    public boolean aiming() { return aimAction != null; }

    /** Uzycie runy z celownika na polu p (zasieg i wzrok sprawdza sesja, offline i na serwerze). */
    private boolean aimAt(Position p) {
        String a = aimAction + ":" + p.x() + ":" + p.y() + ":" + p.z();
        boolean ok = session.spells().aimReachable(aimRune, p);
        cancelAim();
        act(a, now());
        tapPulse(p, !ok);
        return true;
    }

    /** Pole pod kursorem myszy dla celownika (telefon: brak - nie ma najechania). */
    private Position aimHover() {
        if (aimHoverOverride != null) return aimHoverOverride;
        if (hud.mobile() || Gdx.app.getType() == Application.ApplicationType.Android) return null;
        return worldTile(Gdx.input.getX(), Gdx.input.getY());
    }

    public void setAimHover(Position p) { aimHoverOverride = p; }

    /**
     * Podglad celownika w rzedzie mapy: lekko rozjasnione pola w zasiegu runy, pod kursorem ramka z krzyzem (zielona
     * w zasiegu, czerwona poza) i obszar runy obszarowej (np. promien 3 wielkiej kuli ognia).
     */
    private void drawAim(SpriteBatch b, int row, int z) {
        if (aimAction == null || aimRune == null) return;
        Position me = session.character().position();
        if (!me.equals(aimFrom)) {
            aimFrom = me;
            aimReach.clear();
            int r = Math.max(1, aimRune.range());
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    Position p = me.translate(dx, dy);
                    if (session.spells().aimReachable(aimRune, p)) aimReach.add(p);
                }
            }
        }
        TextureRegion w = game.art().white();
        float wy = worldRenderer.worldY(row);
        for (Position p : aimReach) {
            if (p.y() != row || p.z() != z) continue;
            b.setColor(1f, 1f, 1f, 0.1f);
            b.draw(w, p.x(), wy, 1f, 1f);
        }
        Position h = aimHover();
        if (h == null || h.z() != z) {
            b.setColor(Color.WHITE);
            return;
        }
        boolean ok = aimReach.contains(h);
        if (ok && (aimRune.kind() == pl.fawizja.pixel.magic.SpellKind.RUNE_AREA || aimRune.kind().targetsTile())) {
            // podglad obszaru runy: te same pola co przy rzucie (okrag wielkiej kuli ognia, krzyz eksplozji, sciana)
            pl.fawizja.pixel.magic.SpellSystem.AreaCast ac = session.spells().areaCast(aimRune, h, 0);
            if (ac != null && ac.tiles().size() > 1) {
                for (Position p : ac.tiles()) {
                    if (p.y() != row || p.z() != z) continue;
                    b.setColor(1f, 0.55f, 0.15f, 0.22f);
                    b.draw(w, p.x(), wy, 1f, 1f);
                }
            }
        }
        if (row == h.y()) {
            float t = 0.07f, x = h.x();
            if (ok) b.setColor(0.45f, 1f, 0.45f, 0.95f);
            else b.setColor(1f, 0.3f, 0.25f, 0.95f);
            b.draw(w, x, wy, 1f, t);
            b.draw(w, x, wy + 1f - t, 1f, t);
            b.draw(w, x, wy, t, 1f);
            b.draw(w, x + 1f - t, wy, t, 1f);
            // krzyz celownika
            b.draw(w, x + 0.5f - t / 2f, wy + 0.18f, t, 0.24f);
            b.draw(w, x + 0.5f - t / 2f, wy + 0.58f, t, 0.24f);
            b.draw(w, x + 0.18f, wy + 0.5f - t / 2f, 0.24f, t);
            b.draw(w, x + 0.58f, wy + 0.5f - t / 2f, 0.24f, t);
        }
        b.setColor(Color.WHITE);
    }

    /** Uklad paska widziany ostatnio w postaci (zmiana z serwera albo zapisu odswieza pasek w HUD). */
    private String barSeen;
    /** Chwila wlasnej zmiany paska (przez 2 s stary SELF z serwera jej nie cofa) i czy przeniesiono stary uklad. */
    private long barEditedMs = Long.MIN_VALUE / 2;
    private boolean barMigrated;

    /**
     * Co klatke: jednorazowo przenosi dawny uklad paska z Preferences "pixel-bar" do postaci (zapis v9; online po
     * pierwszym SELF i wysyla go na serwer), potem pilnuje, by HUD pokazywal pasek zapisany w postaci.
     */
    private void syncBar() {
        PlayerCharacter pc = session.character();
        if (!barMigrated && (net == null || lastSelf != null)) {
            barMigrated = true;
            String legacy = barPrefs.getString(barKey(pc), null);
            if (legacy != null) {
                pl.fawizja.pixel.settings.SpellBar old = pl.fawizja.pixel.settings.SpellBar.decode(legacy);
                if (pc.actionBar().isEmpty() && old.isCustom()) {
                    storeBar(old.encode());
                    hud.setSpellBar(old);
                }
                barPrefs.remove(barKey(pc));
                barPrefs.flush();
            }
        }
        String cur = pc.actionBar();
        if (!cur.equals(barSeen) && TimeUtils.millis() - barEditedMs > 2000) {
            barSeen = cur;
            hud.setSpellBar(pl.fawizja.pixel.settings.SpellBar.decode(cur));
        }
    }

    /** Zapis ukladu paska w postaci (offline: plik zapisu przy autozapisie; online: akcja "bar:" do serwera). */
    private void storeBar(String encoded) {
        session.character().setActionBar(encoded);
        barSeen = session.character().actionBar();
        barEditedMs = TimeUtils.millis();
        if (net != null) net.send("ACT", "bar:" + barSeen);
        else game.saveCharacter(session.character());
    }

    /** Rozbłysk kropki na dotknietym polu (blocked: nie ma tam drogi). */
    private void tapPulse(Position p, boolean blocked) {
        tapMarker.pulse(p, blocked, uiMs());
    }

    /** Cel trwajacego marszu z dotkniecia (mala kropka na ziemi) albo null (brak marszu, poscig za celem). */
    private Position walkGoal() {
        if (walkPath == null || chaseGoal != null || walkPath.isEmpty()) return null;
        return walkPath.get(walkPath.size() - 1);
    }

    private void stopWalk() {
        walkNpc = null;
        walkUse = null;
        if (walkPath == null) return;
        walkPath = null;
        hud.setWalkTarget(null);
    }

    private Direction walkStep() {
        Position pos = session.character().position();
        Position goal = walkPath.get(walkPath.size() - 1);
        if (pos.equals(goal) || pos.z() != goal.z()) {
            pl.fawizja.pixel.npc.Npc npc = walkNpc;
            Position use = walkUse;
            stopWalk();
            if (npc != null && session.npcNearby() == npc && !hud.npcOpen()) hud.openNpc(session);
            if (use != null && use.distanceTo(pos) <= 1) useOpening(use);
            return null;
        }
        // postep: najdalsze pole trasy, na ktorym stoimy (od ostatniego miejsca, z zapasem jednego pola)
        for (int i = Math.max(0, walkIndex - 1); i < walkPath.size(); i++) {
            if (walkPath.get(i).equals(pos)) {
                walkIndex = i + 1;
                break;
            }
        }
        Position next = walkPath.get(walkIndex);
        int dx = next.x() - pos.x(), dy = next.y() - pos.y();
        if (Math.abs(dx) > 1 || Math.abs(dy) > 1) {
            // zepchniety z trasy (np. przez potwora): nowa trasa od biezacego pola
            List<Position> again = pl.fawizja.pixel.movement.Pathfinder.pathAvoiding(session.world(), pos, goal, WALK_MAX_NODES, this::walkAvoid);
            if (again.isEmpty()) {
                stopWalk();
                return null;
            }
            walkPath = again;
            walkIndex = 0;
            next = again.get(0);
            dx = next.x() - pos.x();
            dy = next.y() - pos.y();
        }
        for (Direction d : Direction.values()) {
            if (d.dx != dx || d.dy != dy) continue;
            if (game.world().openings().isClosedDoor(next)) {
                // zamkniete drzwi na trasie: marsz staje przed nimi (jak w Tibii), drzwi otwiera dotkniecie
                stopWalk();
                session.log().add(now(), EventLog.Kind.INFO, Messages.tr(GameSession.DOOR_HINT));
                return null;
            }
            return d;
        }
        stopWalk();
        return null;
    }

    private static final int WALK_MAX_NODES = 120_000;

    /** Pogon za celem (bieg wlaczony): ostatnie pole celu, do ktorego liczono trase. */
    private Position chaseGoal;
    /** Cel, za ktorym gracz przestal gonic joystickiem - pogon wraca po wyborze innego celu. */
    private Monster chaseSkip;
    private long chaseAtMs;

    /** Bieg wlaczony i zaznaczony potwor: trasa na pole obok niego, odswiezana, gdy potwor sie ruszy. */
    private void chase(long nowMs) {
        Monster m = session.combat().target();
        if (m != chaseSkip) chaseSkip = null;
        if (m == null || m == chaseSkip || !pl.fawizja.pixel.movement.ChaseMode.wants(session.character(), m)) {
            if (chaseGoal != null && walkNpc == null) stopWalk();
            chaseGoal = null;
            return;
        }
        if (walkPath != null && chaseGoal == null) return; // marsz do wskazanego miejsca ma pierwszenstwo
        Position goal = m.position();
        if (walkPath != null && goal.equals(chaseGoal)) return;
        if (nowMs - chaseAtMs < 200) return;
        chaseAtMs = nowMs;
        chaseGoal = goal;
        walkTo(goal, now(), true, true);
    }

    /** Kierunek stuknietego klawisza (strzalki i WASD) do kolejki ruchu; null dla innych klawiszy. */
    private static Direction keyDirection(int keycode) {
        switch (keycode) {
            case Input.Keys.UP: case Input.Keys.W: return Direction.NORTH;
            case Input.Keys.DOWN: case Input.Keys.S: return Direction.SOUTH;
            case Input.Keys.LEFT: case Input.Keys.A: return Direction.WEST;
            case Input.Keys.RIGHT: case Input.Keys.D: return Direction.EAST;
            default: return null;
        }
    }

    /** Kolejka jednego kroku z krotkiego stukniecia klawisza (wazna przez QUEUE_MS). */
    private Direction queuedDir;
    private long queuedAtMs;
    private Position queuedFrom;
    private boolean queuedPending;
    private static final long QUEUE_MS = 450;
    /** Filtr joysticka (martwa strefa, histereza, pierwszenstwo kierunkow prostych). */
    private final pl.fawizja.pixel.movement.StickFilter stick = new pl.fawizja.pixel.movement.StickFilter();
    /** Plywajacy joystick z 4 kierunkami, przytrzymanie na mapie i czulosc, z ktora je ustawiono. */
    private final pl.fawizja.pixel.movement.FourWayStick fourWay = new pl.fawizja.pixel.movement.FourWayStick();
    private final pl.fawizja.pixel.movement.HoldToWalk hold = new pl.fawizja.pixel.movement.HoldToWalk();
    private int tunedSensitivity = -1;
    private final Vector3 holdWorld = new Vector3();

    /** Kierunek z dotyku wg wariantu sterowania albo null (krzyzyk, joysticki, przytrzymanie na mapie). */
    private Direction touchDirection(boolean stepping) {
        if (settings.sensitivity() != tunedSensitivity) {
            tunedSensitivity = settings.sensitivity();
            stick.setSensitivity(tunedSensitivity);
            fourWay.setSensitivity(tunedSensitivity);
            hold.setSensitivity(tunedSensitivity);
        }
        pl.fawizja.pixel.movement.ControlScheme scheme = hud.controlScheme();
        long ms = TimeUtils.millis();
        if (scheme != pl.fawizja.pixel.movement.ControlScheme.CLASSIC || !hud.joystick().active()) stick.reset();
        if (scheme != pl.fawizja.pixel.movement.ControlScheme.FLOAT4 || !hud.joystick().active()) fourWay.reset();
        if (scheme != pl.fawizja.pixel.movement.ControlScheme.HOLD && hold.down()) hold.cancel();
        switch (scheme) {
            case DPAD:
                return hud.dpad().active() ? hud.dpad().direction() : null;
            case FLOAT4:
                return hud.joystick().active()
                        ? fourWay.update(hud.joystick().value().x, hud.joystick().value().y, ms, stepping) : null;
            case HOLD: {
                if (worldPointer < 0 || !hold.down()) {
                    hud.holdIndicator(false, 0, 0, 0f, null);
                    return null;
                }
                // palec wzgledem srodka postaci, w polach (x na wschod, y na poludnie)
                long t = now();
                viewport.unproject(holdWorld.set(worldCurX, worldCurY, 0));
                float dx = holdWorld.x - (session.renderX(t) + 0.5f);
                float dy = (worldRenderer.worldY(session.renderY(t)) + 0.5f) - holdWorld.y;
                Direction d = hold.update(dx, dy, ms, stepping);
                float pr = hold.progress(ms);
                hud.holdIndicator(pr >= 0.25f, worldCurX, worldCurY, pr, d);
                return d;
            }
            default:
                // spokojny joystick: wyrazne wychylenie, histereza kierunku, skos tylko przy wyraznym skosie
                return hud.joystick().active() ? stick.update(hud.joystick().value().x, hud.joystick().value().y, ms) : null;
        }
    }

    /** Podglad przytrzymania palca na mapie w punkcie ekranu (zrzuty: -Dpixel.hold=fx,fy), juz po progu marszu. */
    public void simulateHold(int sx, int sy) {
        worldPointer = 9;
        worldDownX = worldCurX = sx;
        worldDownY = worldCurY = sy;
        worldDownMs = TimeUtils.millis() - 1000;
        worldGesture = 3;
        hold.press(worldDownMs);
    }

    private Direction keyboardDirection() {
        int dx = 0, dy = 0;
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A)) dx -= 1;
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D)) dx += 1;
        if (Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W)) dy -= 1;
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S)) dy += 1;
        for (Direction d : Direction.values()) if (d.dx == dx && d.dy == dy) return d;
        return null;
    }

    @Override
    public void render(float delta) {
        frameStartNs = TimeUtils.nanoTime();
        if (FIXED_FRAME_MS > 0 && net == null) {
            fixedFrames++;
            delta = FIXED_FRAME_MS / 1000f;
        }
        if (paused) return;
        startProfiling();
        checkLongPress();
        frameTimeAvg = frameTimeAvg * 0.95f + Math.min(delta, 0.25f) * 0.05f;
        frameStats.add(delta);
        if (TimeUtils.millis() >= frameLogAtMs) {
            Gdx.app.log("Pixel", frameStats.summary() + Messages.get("game_screen.limit") + guard.fpsCap(settings.effectiveFpsCap())
                    + Messages.get("game_screen.tryb") + guard.mode());
            frameStats.reset();
            frameLogAtMs = TimeUtils.millis() + FRAME_LOG_MS;
        }

        Direction dir = chatDraft == null ? keyboardDirection() : null;
        Direction touch = touchDirection(session.isStepping(now()));
        hud.setStickDirection(touch);
        if (dir == null) dir = touch;
        // krotkie stukniecie klawisza w trakcie kroku nie ginie: krok wykona sie zaraz po biezacym
        if (dir == null && queuedDir != null) {
            if (TimeUtils.millis() - queuedAtMs > QUEUE_MS || chatDraft != null) queuedDir = null;
            else dir = queuedDir;
        }
        if (dir != null && queuedDir != null && !session.isMoving() && dir == queuedDir) queuedPending = true;
        if (dir != null) {
            stopWalk(); // reczne sterowanie przerywa marsz do celu
            if (chaseGoal != null) chaseSkip = session.combat().target(); // i pogon, az do wyboru innego celu
            chaseGoal = null;
        } else {
            chase(TimeUtils.millis());
            if (walkPath != null) dir = walkStep();
        }
        if (sleepBed != null) {
            dir = null;                                         // postac lezy w lozku: zadnych krokow
            stopWalk();
        }
        if (net != null) {
            syncWithServer(dir);
            // mgla mapy online: klient odkrywa sam z pozycji (serwer liczy to samo i zapisuje)
            session.character().explored().revealView(session.character().position());
        }
        syncBar();
        final long t = now();
        if (net == null) session.update(t, dir);
        if (net == null && sleepBed == null) {
            Position bed = session.takeSleep();
            if (bed != null) startSleep(bed, t);
        }
        if (sleepBed != null && t - sleepStartMs >= SLEEP_MS) finishSleep();
        if (queuedPending && (session.isMoving() || !session.character().position().equals(queuedFrom))) {
            queuedDir = null;
            queuedPending = false;
        }
        autoLoot(t);
        autoHeal(t);
        autoMana(t);
        autoEat(t);
        autoTarget(t);
        updateHunt(t);
        updateStore(t);
        checkDeath();
        updateGuard(t);

        if (net == null && t - lastSaveMs >= AUTOSAVE_MS) {
            game.saveCharacter(session.character());
            lastSaveMs = t;
        }

        PlayerCharacter pc = session.character();
        float px = session.renderX(t);
        float py = session.renderY(t);
        float camX = px + 0.5f;
        float camY = worldRenderer.worldY(py) + 0.5f;
        // przyciaganie kamery do siatki pikseli - ostre krawedzie kafelkow
        float ppu = Gdx.graphics.getBackBufferWidth() / viewport.getWorldWidth();
        camera.position.set(Math.round((camX - viewOffX) * ppu) / ppu, Math.round((camY - viewOffY) * ppu) / ppu, 0);
        camera.update();

        ScreenUtils.clear(0, 0, 0, 1);
        viewport.apply();
        clipToView(true);
        SpriteBatch batch = game.batch();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        int z = pc.position().z();
        int playerRow = pc.position().y();
        visibleMonsters.clear();
        int radius = (int) Math.ceil(Math.max(viewport.getWorldWidth(), viewport.getWorldHeight()) / 2f) + 2;
        visibleMonsters.addAll(session.monsters().near(pc.position(), radius));
        collectLunges(t);
        long petDt = lastPetMs < 0 ? 0 : t - lastPetMs;
        lastPetMs = t;
        if (pc.pet() != null) myPet.update(pc.position(), petDt);
        for (RemotePlayer rp : remotePlayers.values()) if (rp.pet != null) rp.petFollower.update(rp.to, petDt);
        footprints.track("@me", pc.position(), t);
        for (RemotePlayer rp : remotePlayers.values()) footprints.track("p:" + rp.name, rp.to, t);
        for (int i = 0; i < visibleMonsters.size(); i++) {
            Monster m = visibleMonsters.get(i);
            footprints.track("m:" + m.id(), m.position(), t);
        }
        float[] myLunge = lunges.get(pc.position());
        float lx = myLunge == null ? 0f : myLunge[0], ly = myLunge == null ? 0f : myLunge[1];
        collectSplats(session.combat().recentEvents(t, 1200), t);
        worldRenderer.setBerries(session.berries(), t);
        worldRenderer.render(batch, camera, z, session.ground(), (b, row) -> {
            drawSplats(b, row, z, t);
            drawFields(b, row, z, t);
            drawCorpses(b, row, z);
            drawChests(b, row, z);
            footprints.drawRow(b, game.art().white(), worldRenderer, row, z, t);
            tapMarker.drawRow(b, worldRenderer, row, z, uiMs(), walkGoal());
            drawAim(b, row, z);
            shopDisplays.drawRow(b, game.art(), worldRenderer, row, z);
            for (int i = 0; i < visibleMonsters.size(); i++) {
                Monster m = visibleMonsters.get(i);
                if (m.position().y() != row || m.position().z() != z) continue;
                float mrx = m.renderX(t), mry = m.renderY(t);
                int mframe = TileArt.walkFrame(mrx, mry, m.position().x(), m.position().y());
                TextureRegion big = game.art().bigMonster(m.type().key(), m.facing4(), mframe, monsterFlip);
                boolean isBig = big != null;
                TextureRegion tr = isBig ? big : game.art().monster(m.type().key(), m.facing4(), mframe, monsterFlip);
                if (tr == null) continue;
                float[] lg = lunges.get(m.position());
                float mx = mrx + (lg == null ? 0f : lg[0]);
                float my = worldRenderer.worldY(mry + (lg == null ? 0f : lg[1]));
                boolean flip = monsterFlip[0];
                if (isBig) {
                    // duzy potwor (smok, demon, hydra): 64x64 na 2x2 pola, jego pole to prawy dolny kwadrat (jak w Tibii)
                    boolean tinted = m.type().isFiendish() || m.type().isInfluenced();
                    if (m.type().isFiendish()) b.setColor(1f, 0.55f, 0.75f, 1f);
                    else if (m.type().isInfluenced()) b.setColor(0.85f, 0.75f, 1f, 1f);
                    b.draw(tr, flip ? mx + 2f : mx - 1f, my, flip ? -2f : 2f, 2f);
                    if (tinted) b.setColor(Color.WHITE);
                    continue;
                }
                if (m.type().isFiendish()) {
                    // diaboliczny (Kuznia): wiekszy, czerwono-fioletowy
                    b.setColor(1f, 0.55f, 0.75f, 1f);
                    b.draw(tr, flip ? mx + 1.15f : mx - 0.15f, my, flip ? -1.3f : 1.3f, 1.3f);
                    b.setColor(Color.WHITE);
                } else if (m.type().isInfluenced()) {
                    // pod wpływem: fioletowa poswiata, odrobine wiekszy z kazdym stopniem
                    float sc = 1f + 0.04f * m.type().forgeStack(), off = (sc - 1f) / 2f;
                    b.setColor(0.85f, 0.75f, 1f, 1f);
                    b.draw(tr, flip ? mx + 1f + off : mx - off, my, flip ? -sc : sc, sc);
                    b.setColor(Color.WHITE);
                } else {
                    b.draw(tr, flip ? mx + 1f : mx, my, flip ? -1f : 1f, 1f);
                }
            }
            for (pl.fawizja.pixel.npc.Npc n : session.npcs().all()) {
                Position np = n.position();
                if (np.y() != row || np.z() != z) continue;
                TextureRegion tr = game.art().npc(n.key());
                if (tr != null) b.draw(tr, np.x(), worldRenderer.worldY(np.y()), 1f, 1f);
            }
            drawPet(b, pc.pet(), myPet, row, z);
            for (RemotePlayer rp : remotePlayers.values()) if (rp.pet != null) drawPet(b, rp.pet, rp.petFollower, row, z);
            for (RemotePlayer rp : remotePlayers.values()) {
                if (rp.to.y() != row || rp.to.z() != z) continue;
                float[] lg = lunges.get(rp.to);
                float rise = rp.mounted ? 0f : emoteRise(rp.name);
                if (rp.mounted) {
                    b.draw(game.art().horse(rp.facing), remoteX(rp, t), worldRenderer.worldY(remoteY(rp, t)), 1f, 1f);
                    rise = 0.35f;
                }
                int rf = rp.mounted ? 0 : TileArt.walkFrame(remoteX(rp, t), remoteY(rp, t), rp.to.x(), rp.to.y());
                b.draw(game.art().character(rp.vocation, rp.facing, rp.outfit, rf), remoteX(rp, t) + (lg == null ? 0f : lg[0]),
                        worldRenderer.worldY(remoteY(rp, t) - rise + (lg == null ? 0f : lg[1])), 1f, 1f);
            }
            if (sleepBed != null && row == sleepBed.y() && sleepBed.z() == z) drawSleeper(b, pc);
            if (row == playerRow && sleepBed == null) {
                float rise = pc.isMounted() ? 0f : emoteRise(pc.name());
                if (pc.isMounted()) {
                    b.draw(game.art().horse(session.facing()), px + lx, worldRenderer.worldY(py + ly), 1f, 1f);
                    rise = 0.35f;
                }
                int pf = pc.isMounted() ? 0 : TileArt.walkFrame(px, py, pc.position().x(), pc.position().y());
                // iluzja stworzenia (utevo res ina): postac wyglada jak potwor; niewidzialnosc - polprzezroczysta (dla siebie)
                TextureRegion look = pc.illusion() != null ? game.art().monster(pc.illusion(), pf) : null;
                if (pc.isInvisible()) b.setColor(1f, 1f, 1f, 0.4f);
                b.draw(look != null ? look : game.art().character(pc.vocation(), session.facing(), pc.outfit(), pf),
                        px + lx, worldRenderer.worldY(py + ly - rise), 1f, 1f);
                b.setColor(Color.WHITE);
            }
        });
        worldRenderer.renderAbove(batch, camera, pc.position(), roofs);
        drawSaleSigns(batch, pc.position(), radius);
        batch.end();

        int minute = session.clock().minuteOfDay(t);
        long wallMs = System.currentTimeMillis();
        Weather.Kind weather = weatherAt(pc.position(), wallMs);
        float rain = forcedWeather != null ? 1f : Weather.intensity(wallMs);
        float quality = Math.min(settings.qualityCap(), guard.effectsQuality());
        float ambient = Lighting.ambientForFloor(z, session.clock().ambientLight(minute)) * Weather.ambientFactor(weather, rain);
        lightRenderer.render(batch, camera, game.world().floor(z), game.world().wallLights(), game.world().height(), ambient,
                quality, camX, camY, Lighting.playerLightRadius(pc, game.items()));
        batch.setColor(Color.WHITE);
        if (weather != Weather.Kind.CLEAR) {
            batch.setProjectionMatrix(camera.combined);
            batch.begin();
            float vw = viewport.getWorldWidth();
            float vh = viewport.getWorldHeight();
            weatherRenderer.render(batch, game.art().white(), weather, rain, quality,
                    camera.position.x - vw / 2f, camera.position.y - vh / 2f, vw, vh, frozenWeather ? 0L : t);
            batch.end();
        }
        drawCombatOverlay(batch, game.shapes(), t, z);
        drawLowHealth(game.shapes(), t);
        drawSleepFade(game.shapes(), t);
        clipToView(false);

        if (profiler != null && profiler.isEnabled()) worldDrawCalls = profiler.getDrawCalls();
        feedHud(t);
        hud.render(batch, game.shapes(), session, session.clock(), t, this::diagnosticsLines);
        endProfiling();
        limitFrameRate();
    }

    private void startProfiling() {
        boolean on = profileLog || hud.diagnosticsOpen();
        if (on && profiler == null) profiler = new com.badlogic.gdx.graphics.profiling.GLProfiler(Gdx.graphics);
        if (profiler == null) return;
        if (on && !profiler.isEnabled()) profiler.enable();
        else if (!on && profiler.isEnabled()) profiler.disable();
        profiler.reset();
    }

    private void endProfiling() {
        if (profiler == null || !profiler.isEnabled()) return;
        lastDrawCalls = profiler.getDrawCalls();
        lastTextureBinds = profiler.getTextureBindings();
        if (!profileLog) return;
        profFrames++;
        profCalls += lastDrawCalls;
        profWorld += worldDrawCalls;
        profBinds += lastTextureBinds;
        profVerts += (long) profiler.getVertexCount().total;
        profCpuNs += TimeUtils.nanoTime() - frameStartNs;
        if (profFrames % 300 == 0) {
            Gdx.app.log("Pixel", String.format(java.util.Locale.ROOT,
                    "profil %d klatek: rysowanie %.1f (swiat %.1f), tekstury %.1f, wierzcholki %.0f, CPU klatki %.2f ms",
                    profFrames, profCalls / 300.0, profWorld / 300.0, profBinds / 300.0, profVerts / 300.0, profCpuNs / 300.0 / 1e6));
            profCalls = profWorld = profBinds = profVerts = profCpuNs = 0;
        }
    }

    /**
     * Ciosy wrecz z ostatnich {@link CombatEvent#LUNGE_MS} ms: atakujacy (gracz, potwor, inny gracz) robi krotki
     * wypad w strone celu i wraca. Klucz to pozycja atakujacego z chwili ciosu.
     */
    private void collectLunges(long t) {
        lunges.clear();
        for (CombatEvent e : session.combat().recentEvents(t, CombatEvent.LUNGE_MS)) {
            if (!e.isMeleeStrike()) continue;
            float p = (t - e.timeMs()) / (float) CombatEvent.LUNGE_MS;
            if (p < 0f || p > 1f) continue;
            float amount = 0.22f * (float) Math.sin(Math.PI * p);
            int dx = Integer.signum(e.position().x() - e.from().x());
            int dy = Integer.signum(e.position().y() - e.from().y());
            lunges.put(e.from(), new float[] {dx * amount, dy * amount});
        }
    }

    /** Pole z pulapka z kolcami - marsz po mapie je omija, jesli sie da. */
    /** Pole pod widocznym dachem (dach budynku, do ktorego gracz podszedl, jest zdjety). */
    private boolean underRoof(java.util.Map<Integer, Float> near, int x, int y, int z) {
        return roofs.at(x, y, z) != pl.fawizja.pixel.client.render.Roofs.NONE
                && pl.fawizja.pixel.client.render.Roofs.alpha(near, roofs.buildingAt(x, y, z)) > 0.5f;
    }

    /** Marsz po dotknieciu omija pulapki, skrzynie i zamkniete drzwi (drzwi otwiera dopiero ich dotkniecie). */
    private boolean walkAvoid(Position p) {
        return isTrap(p) || game.world().openings().isClosedDoor(p);
    }

    private boolean isTrap(Position p) {
        return game.chests() != null && (game.chests().trapAt(p) || game.chests().at(p) != null); // na skrzynie sie nie wchodzi
    }

    /** Chwila, w ktorej skrzynia zostala zobaczona jako otwarta (animacja wieka). */
    private final Map<String, Long> chestOpenedAt = new java.util.HashMap<>();
    private final java.util.Set<String> chestSeenClosed = new java.util.HashSet<>();

    /** 0..1: skrzynia otwarta na oczach gracza przechodzi plynnie, otwarta wczesniej jest od razu otwarta. */
    private float chestOpenness(String id) {
        long now = TimeUtils.millis();
        Long at = chestOpenedAt.get(id);
        if (at == null) {
            at = chestSeenClosed.contains(id) ? now : now - 1000;
            chestOpenedAt.put(id, at);
        }
        return Math.min(1f, (now - at) / 350f);
    }

    /** Kolysanie manekina po ciosie podczas treningu (0 = spoczynek, wychylenie w polach do ok. 1). */
    private float trainingHit(Position p) {
        if (!p.equals(session.trainingDummy())) return 0f;
        long since = now() - session.lastTrainingStrikeMs();
        if (since < 0 || since > 450 || session.lastTrainingStrikeMs() <= 0) return 0f;
        float t = since / 450f;
        return (float) (Math.sin(t * Math.PI * 3) * (1 - t));
    }

    /** Skrzynie skarbow w danym wierszu: zamknieta albo otwarta (gdy ta postac juz ja oproznila). */
    private void drawChests(SpriteBatch b, int row, int z) {
        TextureRegion big = game.art().dummyNative();
        for (Position p : session.dummies().all()) {
            if (p.y() != row || p.z() != z) continue;
            float wy = worldRenderer.worldY(p.y());
            if (big == null) {
                b.draw(game.art().dummy(), p.x(), wy, 1f, 1f);
                continue;
            }
            // naturalny rozmiar jak w Tibii (jak skrzynia): od prawego dolnego rogu pola, postac siega pola obok
            float w = big.getRegionWidth() / 32f, h = big.getRegionHeight() / 32f;
            float hit = trainingHit(p);
            b.draw(big, p.x() + 1 - w + hit * 0.06f, wy, w, h);
        }
        if (game.chests() == null) return;
        for (Position p : game.chests().traps()) {
            if (p.y() == row && p.z() == z) b.draw(game.art().trap(), p.x(), worldRenderer.worldY(p.y()), 1f, 1f);
        }
        for (pl.fawizja.pixel.treasure.ChestSigns.Sign sg : session.chestSigns().all()) {
            Position p = sg.at();
            if (p.y() == row && p.z() == z) b.draw(chestSign(), p.x(), worldRenderer.worldY(p.y()), 1f, 1f);
        }
        for (pl.fawizja.pixel.treasure.TreasureChests.Chest c : game.chests().all()) {
            Position p = c.position();
            if (p.y() != row || p.z() != z) continue;
            boolean open = session.character().openedChests().contains(c.id());
            TextureRegion closedImg = game.art().nativeChest(false), openImg = game.art().nativeChest(true);
            float wy = worldRenderer.worldY(p.y());
            if (closedImg == null || openImg == null) {
                b.draw(game.art().chest(open), p.x(), wy, 1f, 1f);
                continue;
            }
            // naturalny rozmiar jak w Tibii; po otwarciu wieko odchyla sie przez chwile (przejscie klatek)
            if (!open) chestSeenClosed.add(c.id());
            float k = open ? chestOpenness(c.id()) : 0f;
            b.draw(closedImg, p.x() - 1, wy, 2f, 2f);
            if (k > 0f) {
                Color old = b.getColor().cpy();
                b.setColor(old.r, old.g, old.b, old.a * k);
                b.draw(openImg, p.x() - 1, wy, 2f, 2f);
                b.setColor(old);
            }
        }
    }

    /**
     * Ciala potworow w danym wierszu: kaluza krwi (albo sluzu) pod cialem, cialo z paczki dla ludzi, goblinow
     * i szkieletow, dla reszty lezacy, przyciemniony sprite potwora (jasniejszy, gdy jest w nim lup).
     */
    private void drawCorpses(SpriteBatch b, int row, int z) {
        Position me = session.character().position();
        session.ground().forEachCorpseNear(me, 16, (p, c) -> {
            if (p.y() != row || p.z() != z) return;
            float wy = worldRenderer.worldY(p.y());
            String fluid = fluidOf(c.monsterKey());
            if (fluid != null) drawDecal(b, fluid + "_pool", p.x(), wy, 1f);
            String body = corpseOf(c.monsterKey());
            TextureRegion[] own = body == null ? null : game.art().effect(body);
            if (own != null) {
                float shade = c.hasLoot() ? 1f : 0.8f;
                b.setColor(shade, shade, shade, 1f);
                drawEffectFrame(b, body, 0, 1, p.x(), wy);
                b.setColor(Color.WHITE);
                return;
            }
            TextureRegion tr = game.art().monster(c.monsterKey());
            if (tr == null) return;
            float shade = c.hasLoot() ? 0.62f : 0.42f;
            b.setColor(shade, shade * 0.9f, shade * 0.85f, 1f);
            // obrot o 90 stopni wokol srodka kafelka, splaszczony
            b.draw(tr, p.x(), wy - 0.12f, 0.5f, 0.5f, 1f, 1f, 0.9f, 0.75f, 90f);
            b.setColor(Color.WHITE);
        });
    }

    /** Krew potwora: "blood" (czerwona), "slime" (zielona: pajaki, weze, skorpiony) albo null (golemy, nieumarli). */
    static String fluidOf(String key) {
        if (key == null) return "blood";
        if (key.matches(".*(golem|elemental|skeleton|wraith|mummy|treant|colossus|titan|stalker|bone|ignaroth|pyrrax).*")) return null;
        if (key.matches(".*(spider|scorpion|serpent|crab|crocodile).*")) return "slime";
        return "blood";
    }

    /** Cialo z paczki OTSP albo null (wtedy lezacy sprite potwora). */
    static String corpseOf(String key) {
        if (key == null) return null;
        if (key.matches(".*(bandit|hunter|pirate|sailor|witch|priest|bloodbeard|morvena|cutthroat|gunner|player_corpse).*")) return "corpse_human";
        if (key.matches(".*(goblin|orc|grimtooth).*")) return "corpse_goblin";
        if (key.matches(".*(skeleton|bone_dragon).*")) return "corpse_bones";
        return null;
    }

    /** Kaluze i rozbryzgi krwi po trafieniach: x, y, z, czas, wariant (rysowane pod postaciami, znikaja po 30 s). */
    private final java.util.ArrayList<long[]> splats = new java.util.ArrayList<>();
    private long lastSplatEventMs = Long.MIN_VALUE;
    static final long SPLAT_MS = 30_000L;

    private void collectSplats(List<CombatEvent> events, long t) {
        long newest = lastSplatEventMs;
        for (CombatEvent e : events) {
            if (e.timeMs() <= lastSplatEventMs) continue;
            newest = Math.max(newest, e.timeMs());
            if ((e.kind() != CombatEvent.Kind.HIT_MONSTER && e.kind() != CombatEvent.Kind.HIT_PLAYER) || e.amount() <= 0) continue;
            String who = null;
            if (e.kind() == CombatEvent.Kind.HIT_MONSTER) {
                for (Monster m : session.monsters().near(e.position(), 0)) if (m.position().equals(e.position())) who = m.type().key();
                if (who == null) who = "";
            }
            String fluid = fluidOf(who);
            if (fluid == null) continue;
            Position p = e.position();
            int variant = (int) Math.floorMod(p.x() * 31L + p.y() * 17L + e.timeMs() / 97, 3) + (fluid.equals("slime") ? 3 : 0);
            splats.add(new long[] {p.x(), p.y(), p.z(), e.timeMs(), variant});
        }
        lastSplatEventMs = newest;
        splats.removeIf(s -> t - s[3] > SPLAT_MS || t < s[3] - 1000);
        while (splats.size() > 300) splats.remove(0);
    }

    private static final String[] SPLAT_NAMES = {"blood_splash", "blood_small", "blood_drops", "slime_splash", "slime_splash", "slime_splash"};

    /**
     * Pola magiczne z run (0.240) z klatek efektow OTSP: ogien - plomien, energia - iskry energii, trucizna - kaluza
     * sluzu, magiczna sciana - magiczna poswiata, dziki wzrost - efekt ziemi (OTSP nie ma tu osobnych grafik pol). Animacja w petli, dogasajace pole bledsze.
     */
    private void drawFields(SpriteBatch b, int row, int z, long t) {
        pl.fawizja.pixel.world.Fields fields = session.world().fields();
        if (fields.size() == 0) return;
        Position me = session.character().position();
        for (pl.fawizja.pixel.world.Fields.Field f : fields.near(me, 16)) {
            Position p = f.position();
            if (p.y() != row || p.z() != z) continue;
            float wy = worldRenderer.worldY(p.y());
            String name;
            float alpha = 1f;
            switch (f.kind()) {
                case FIRE: name = "flame"; break;
                case FIRE_WEAK: name = "flame"; alpha = 0.6f; break;
                case ENERGY: name = "energy"; break;
                case POISON: name = "slime_pool"; break;
                case MAGIC_WALL: name = "magic"; break;
                case WILD_GROWTH: name = "earth"; break;
                default: name = null;
            }
            if (name == null || game.art().effect(name) == null) continue;
            long period = 900L;
            b.setColor(1f, 1f, 1f, alpha);
            drawEffectFrame(b, name, (t + p.x() * 131L + p.y() * 71L) % period, period, p.x(), wy);
            b.setColor(Color.WHITE);
        }
    }

    private void drawSplats(SpriteBatch b, int row, int z, long t) {
        for (long[] s : splats) {
            if (s[1] != row || s[2] != z) continue;
            long age = t - s[3];
            float alpha = age < SPLAT_MS - 6000 ? 1f : Math.max(0f, (SPLAT_MS - age) / 6000f);
            drawDecal(b, SPLAT_NAMES[(int) s[4]], s[0], worldRenderer.worldY((int) s[1]), alpha);
        }
    }

    private void drawDecal(SpriteBatch b, String name, float x, float wy, float alpha) {
        if (game.art().effect(name) == null) return;
        b.setColor(1f, 1f, 1f, alpha);
        drawEffectFrame(b, name, 0, 1, x, wy);
        b.setColor(Color.WHITE);
    }

    /** Zywiol stanu ze zdarzenia "COND_POISON" (kolor liczby i efekt tiku). */
    static String conditionElement(String effect) {
        try {
            return pl.fawizja.pixel.combat.ConditionType.valueOf(effect.substring(
                    pl.fawizja.pixel.combat.CombatSystem.CONDITION_EFFECT_PREFIX.length())).damage.name();
        } catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
            return null;
        }
    }

    private static Color effectColor(String effect) {
        if (effect == null) return new Color(0.9f, 0.85f, 0.75f, 1f);
        switch (effect) {
            case "FIRE": return new Color(1f, 0.5f, 0.12f, 1f);
            case "ICE": return new Color(0.6f, 0.9f, 1f, 1f);
            case "EARTH": return new Color(0.45f, 0.85f, 0.3f, 1f);
            case "ENERGY": return new Color(0.78f, 0.5f, 1f, 1f);
            case "HOLY": return new Color(1f, 0.95f, 0.55f, 1f);
            case "DEATH": return new Color(0.6f, 0.25f, 0.7f, 1f);
            case "DROWN": return new Color(0.3f, 0.55f, 0.95f, 1f);
            case "LIFE_DRAIN": return new Color(0.85f, 0.15f, 0.2f, 1f);
            case "MANA_DRAIN": return new Color(0.35f, 0.4f, 1f, 1f);
            case "HEAL": return new Color(0.55f, 1f, 0.65f, 1f);
            case "HASTE": return new Color(0.5f, 0.8f, 1f, 1f);
            case "MAGIC": return new Color(0.55f, 0.75f, 1f, 1f);
            case "PHYSICAL": return new Color(1f, 0.3f, 0.3f, 1f);
            default: return new Color(0.9f, 0.85f, 0.75f, 1f);
        }
    }

    /**
     * Efekty czarow i atakow dystansowych: pocisk z warkoczem leci od rzucajacego do celu, czar obszarowy
     * rozbiega sie pierscieniem po kafelkach, leczenie - iskry unoszace sie nad postacia.
     * Przy niskiej jakosci (bateria, ochrona zasobow) bez warkocza i iskier.
     */
    /** Czas trwania efektu z paczki (klatki rowno rozlozone). */
    static final long SPRITE_EFFECT_MS = 420L;

    /**
     * Pocisk dla rodzaju obrazen; fizyczny: strzala gracza (belt z kuszy, ognista strzala z magmowego luku, wlocznia
     * przy wloczni w reku), kamien olbrzymow, noz goblinow, czerwona strzala lowcow, wlocznia orkow. Trucizna wezy
     * i pajakow, ciemna gwiazdka czarow smierci.
     */
    private String missileFor(CombatEvent e) {
        String fx = e.effect() == null ? "PHYSICAL" : e.effect();
        String key = "";
        boolean player = e.from().equals(session.character().position());
        if (!player) for (Monster m : session.monsters().near(e.from(), 0)) if (m.position().equals(e.from())) key = m.type().key();
        switch (fx) {
            case "FIRE": return "fire";
            case "ICE": return "ice";
            case "EARTH": return key.matches(".*(serpent|spider|scorpion|wasp|centipede|crocodile|witch).*") ? "poison" : "earth";
            case "ENERGY": return "energy";
            case "HOLY": return "holy";
            case "DEATH": return "shadow_star";
            default:
        }
        if (player) {
            pl.fawizja.pixel.item.ItemStack w = session.character().equipment().get(pl.fawizja.pixel.item.EquipSlot.WEAPON);
            pl.fawizja.pixel.item.ItemType wt = w == null || !session.items().contains(w.itemId()) ? null : session.items().get(w.itemId());
            String k = wt == null ? "" : wt.key();
            if (k.endsWith("crossbow")) return "bolt";
            if (k.startsWith("magma") && k.endsWith("bow")) return "fire_arrow";
            if (k.startsWith("dawn") || k.startsWith("shadow") && k.endsWith("bow")) return "arrow_red";
            return k.contains("spear") || k.contains("harpoon") ? "spear" : "arrow";
        }
        if (key.matches(".*(golem|troll|titan|giant|colossus|yeti|gunner).*")) return "stone";
        if (key.matches(".*(goblin|bandit|cutthroat).*")) return "knife";
        if (key.matches(".*(orc).*")) return "spear";
        if (key.matches(".*(hunter|harpy).*")) return "arrow_red";
        return "arrow";
    }

    /** Efekt trafienia czarem / na koniec lotu pocisku. */
    private static String impactFor(String effect, boolean area) {
        if (effect == null) return area ? "stones" : null;
        switch (effect) {
            case "FIRE": return area ? "fire" : "flame";
            case "ICE": return "ice";
            case "EARTH": return "earth";
            case "ENERGY": return area ? "magic" : "energy";
            case "HOLY": return "holy";
            case "DEATH":
            case "LIFE_DRAIN": return "death";
            case "DROWN":
            case "MANA_DRAIN": return "ice";
            case "HEAL": return "heal";
            case "HASTE": return "sparkle";
            case "MAGIC": return "magic";
            case "POOF": return "poof";
            case "SPARKS": return "sparks";
            case "PHYSICAL": return area ? "stones" : null;
            default: return null;
        }
    }

    /** Pola efektu obszaru z ksztaltem czaru (pamietane na zdarzeniu - linia wzroku liczona raz). */
    private final java.util.Map<CombatEvent, List<Position>> shapedCache = new java.util.IdentityHashMap<>();

    private List<Position> shapedTiles(CombatEvent e) {
        if (shapedCache.size() > 256) shapedCache.clear();
        return shapedCache.computeIfAbsent(e, k -> pl.fawizja.pixel.magic.SpellArea.eventTiles(k.position(), k.amount(),
                k.from(), session.world()));
    }

    private void drawEffectFrame(SpriteBatch b, String name, long age, long duration, float x, float wy) {
        TextureRegion[] frames = name == null ? null : game.art().effect(name);
        if (frames == null || age < 0 || age >= duration) return;
        TextureRegion r = frames[(int) Math.min(frames.length - 1, age * frames.length / duration)];
        if (r == null) return;
        // efekt 64 px jak w Tibii: 2x2 pola, prawy dolny kwadrat na polu celu
        if (r.getRegionWidth() > 32) b.draw(r, x - 1f, wy, 2f, 2f);
        else b.draw(r, x, wy, 1f, 1f);
    }

    /**
     * Grafiki czarow i pociskow z paczki OTSP: strzaly, wlocznie, kule ognia i lodu lecace w strone celu, wybuch
     * na koniec lotu, fala efektu na polach czaru obszarowego, iskry leczenia; krew przy trafieniu, oblok przy bloku.
     */
    private void drawSpriteEffects(SpriteBatch b, List<CombatEvent> events, long t, int z) {
        for (CombatEvent e : events) {
            if (e.position().z() != z) continue;
            long age = t - e.timeMs();
            float x = e.position().x(), wy = worldRenderer.worldY(e.position().y());
            switch (e.kind()) {
                case PROJECTILE: {
                    if (e.from() == null) break;
                    if (age < CombatEvent.PROJECTILE_MS) {
                        int dx = e.position().x() - e.from().x(), dy = e.position().y() - e.from().y();
                        TextureRegion r = game.art().missile(missileFor(e), dx, dy);
                        if (r == null) break;
                        float p = age / (float) CombatEvent.PROJECTILE_MS;
                        float mx = e.from().x() + dx * p;
                        float my = worldRenderer.worldY(e.from().y() + dy * p);
                        b.draw(r, mx, my, 1f, 1f);
                    } else {
                        drawEffectFrame(b, impactFor(e.effect(), false), age - CombatEvent.PROJECTILE_MS, SPRITE_EFFECT_MS, x, wy);
                    }
                    break;
                }
                case AREA: {
                    int r = e.amount();
                    String name = impactFor(e.effect(), true);
                    if (e.isShapedArea()) {
                        // ksztalt czaru z Canary: dokladnie trafione pola, dalsze od rzucajacego zaczynaja pozniej
                        Position o = e.from() != null ? e.from() : e.position();
                        for (Position q : shapedTiles(e)) {
                            if (q.z() != z) continue;
                            drawEffectFrame(b, name, age - o.distanceTo(q) * 60L, SPRITE_EFFECT_MS, q.x(), worldRenderer.worldY(q.y()));
                        }
                        break;
                    }
                    if (r <= 0) {
                        drawEffectFrame(b, name, age, SPRITE_EFFECT_MS, x, wy);
                        break;
                    }
                    // fala: pola dalej od srodka zaczynaja pozniej
                    for (int ddy = -r; ddy <= r; ddy++) {
                        for (int ddx = -r; ddx <= r; ddx++) {
                            int d = Math.max(Math.abs(ddx), Math.abs(ddy));
                            drawEffectFrame(b, name, age - d * 70L, SPRITE_EFFECT_MS, x + ddx, worldRenderer.worldY(e.position().y() + ddy));
                        }
                    }
                    break;
                }
                case HIT_MONSTER:
                case HIT_PLAYER:
                    if (e.effect() != null && e.effect().startsWith(pl.fawizja.pixel.combat.CombatSystem.CONDITION_EFFECT_PREFIX)) {
                        // tik stanu: maly efekt zywiolu zamiast krwi (krwawienie - krew)
                        String el = conditionElement(e.effect());
                        String fx = "PHYSICAL".equals(el) ? "blood" : impactFor(el, false);
                        if (fx != null && e.amount() > 0) drawEffectFrame(b, fx, age, 300L, x, wy);
                        break;
                    }
                    if (e.amount() > 0) drawEffectFrame(b, "blood", age, 300L, x, wy);
                    break;
                case BLOCKED:
                    drawEffectFrame(b, "poof", age, 300L, x, wy);
                    break;
                default:
            }
        }
    }

    private void drawSpellEffects(ShapeRenderer shapes, List<CombatEvent> events, long t, int z) {
        boolean rich = Math.min(settings.qualityCap(), guard.effectsQuality()) > 0.6f;
        boolean begun = false;
        for (CombatEvent e : events) {
            boolean slash = e.isMeleeStrike() && t - e.timeMs() <= CombatEvent.LUNGE_MS;
            if ((!e.isVisualEffect() && !slash) || e.position().z() != z) continue;
            if (e.isVisualEffect() && game.art().effect("explosion") != null) continue; // grafiki z paczki (drawSpriteEffects)
            if (!begun) {
                Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE); // swiecace, addytywne
                shapes.begin(ShapeRenderer.ShapeType.Filled);
                begun = true;
            }
            Color c = effectColor(e.effect());
            long age = t - e.timeMs();
            if (slash) {
                // cios wrecz: jasne ciecie po skosie celu, krotko
                float p = age / (float) CombatEvent.LUNGE_MS;
                float a = (e.kind() == CombatEvent.Kind.BLOCKED ? 0.35f : 0.75f) * (1f - p);
                float tx = e.position().x(), ty = worldRenderer.worldY(e.position().y());
                float len = 0.25f + 0.5f * Math.min(1f, p * 2f);
                shapes.setColor(1f, 0.95f, 0.85f, a);
                shapes.rectLine(tx + 0.2f, ty + 0.8f, tx + 0.2f + len * 0.75f, ty + 0.8f - len * 0.75f, 0.07f);
                if (rich) {
                    shapes.setColor(1f, 0.7f, 0.5f, a * 0.6f);
                    shapes.rectLine(tx + 0.35f, ty + 0.85f, tx + 0.35f + len * 0.6f, ty + 0.85f - len * 0.6f, 0.04f);
                }
                continue;
            }
            if (e.kind() == CombatEvent.Kind.PROJECTILE && e.from() != null) {
                float p = age / (float) CombatEvent.PROJECTILE_MS;
                if (p < 0f || p > 1f) continue;
                int trail = rich ? 4 : 1;
                for (int i = trail - 1; i >= 0; i--) {
                    float q = Math.max(0f, p - i * 0.07f);
                    float x = e.from().x() + (e.position().x() - e.from().x()) * q + 0.5f;
                    float y = worldRenderer.worldY(e.from().y() + (e.position().y() - e.from().y()) * q) + 0.5f;
                    float a = 1f - i / (float) trail;
                    shapes.setColor(c.r, c.g, c.b, 0.85f * a);
                    shapes.circle(x, y, 0.16f * (1f - i * 0.18f), 10);
                }
                shapes.setColor(1f, 1f, 1f, 0.7f);
                float x = e.from().x() + (e.position().x() - e.from().x()) * p + 0.5f;
                float y = worldRenderer.worldY(e.from().y() + (e.position().y() - e.from().y()) * p) + 0.5f;
                shapes.circle(x, y, 0.06f, 8);
            } else if (e.kind() == CombatEvent.Kind.AREA) {
                float p = age / (float) CombatEvent.AREA_MS;
                if (p < 0f || p > 1f) continue;
                int r = e.amount();
                if (e.isShapedArea()) {
                    // ksztalt czaru z Canary: pierscien biegnie od rzucajacego po trafionych polach
                    Position o = e.from() != null ? e.from() : e.position();
                    List<Position> tiles = shapedTiles(e);
                    int far = 1;
                    for (Position q : tiles) far = Math.max(far, o.distanceTo(q));
                    float wave = p * (far + 0.5f);
                    for (Position q : tiles) {
                        float near = 1f - Math.min(1f, Math.abs(o.distanceTo(q) - wave));
                        float a = (0.15f + 0.55f * near) * (1f - p);
                        if (a <= 0.02f) continue;
                        float tx = q.x(), ty = worldRenderer.worldY(q.y());
                        shapes.setColor(c.r, c.g, c.b, a);
                        shapes.rect(tx + 0.08f, ty + 0.08f, 0.84f, 0.84f);
                        if (rich && near > 0.6f) {
                            shapes.setColor(1f, 1f, 1f, a * 0.6f);
                            shapes.rect(tx + 0.4f, ty + 0.4f, 0.2f, 0.2f);
                        }
                    }
                    continue;
                }
                if (r <= 0) {
                    // leczenie: iskry wznoszace sie nad postacia
                    int sparks = rich ? 6 : 3;
                    for (int i = 0; i < sparks; i++) {
                        float sx = e.position().x() + 0.2f + 0.6f * ((i * 37) % 10) / 10f;
                        float sy = worldRenderer.worldY(e.position().y()) + 0.15f + p * 0.9f + (i % 3) * 0.12f;
                        shapes.setColor(c.r, c.g, c.b, 0.9f * (1f - p));
                        shapes.rect(sx, sy, 0.12f, 0.12f);
                    }
                    continue;
                }
                float wave = p * (r + 0.5f); // pierscien biegnie od srodka na zewnatrz
                for (int dy = -r; dy <= r; dy++) {
                    for (int dx = -r; dx <= r; dx++) {
                        int d = Math.max(Math.abs(dx), Math.abs(dy));
                        float near = 1f - Math.min(1f, Math.abs(d - wave));
                        float a = (0.15f + 0.55f * near) * (1f - p);
                        if (a <= 0.02f) continue;
                        float tx = e.position().x() + dx, ty = worldRenderer.worldY(e.position().y() + dy);
                        shapes.setColor(c.r, c.g, c.b, a);
                        shapes.rect(tx + 0.08f, ty + 0.08f, 0.84f, 0.84f);
                        if (rich && near > 0.6f) {
                            shapes.setColor(1f, 1f, 1f, a * 0.6f);
                            shapes.rect(tx + 0.4f, ty + 0.4f, 0.2f, 0.2f);
                        }
                    }
                }
            }
        }
        if (begun) {
            shapes.end();
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    /** Paski zycia potworow, obwodka celu i liczby obrazen (rysowane po swietle, zawsze czytelne). */
    private void drawPet(SpriteBatch b, String key, pl.fawizja.pixel.client.render.PetFollower f, int row, int z) {
        if (key == null || f.row() != row || f.z() != z) return;
        TextureRegion tr = game.art().pet(key);
        if (tr == null) return;
        float y = worldRenderer.worldY(f.y());
        if (f.facingLeft()) b.draw(tr, f.x() + 1f, y, -1f, 1f);
        else b.draw(tr, f.x(), y, 1f, 1f);
    }

    /** Alarm niskiego zdrowia: pulsujaca czerwona ramka i sygnal co kilka sekund. */
    private final pl.fawizja.pixel.combat.LowHealthAlarm lowHealth = new pl.fawizja.pixel.combat.LowHealthAlarm();

    private void drawLowHealth(ShapeRenderer shapes, long t) {
        PlayerCharacter pc = session.character();
        if (lowHealth.update(pc.health(), pc.maxHealth(), t)) {
            game.sound().play(List.of(new pl.fawizja.pixel.audio.SoundCues.Cue(pl.fawizja.pixel.audio.SoundSynth.Sfx.ALARM, 0.6f)),
                    settings.volumeFactor());
            if (settings.vibrate()) Gdx.input.vibrate(150);
        }
        if (!lowHealth.low()) return;
        // czerwona ramka wokol widoku gry
        float[] r = hud.gameView();
        float x0 = r[0], y0 = r[1], w = r[2], h = r[3];
        float b = Math.max(4f, Math.min(w, h) * 0.015f);
        float alpha = 0.25f + 0.25f * (float) Math.abs(Math.sin(t / 300.0));
        screenMatrix.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapes.setProjectionMatrix(screenMatrix);
        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.9f, 0.05f, 0.05f, alpha);
        shapes.rect(x0, y0, w, b);
        shapes.rect(x0, y0 + h - b, w, b);
        shapes.rect(x0, y0 + b, b, h - 2 * b);
        shapes.rect(x0 + w - b, y0 + b, b, h - 2 * b);
        shapes.end();
    }

    /**
     * Maly pasek nad postacia (jednostki swiata, x = lewa krawedz pola): zycie w kolorze wg procentu, opcjonalnie
     * pod nim mana (gracze).
     */
    /** Gorny brzeg paska zycia z czarna obwodka nad y paska (jednostki swiata, jak w overheadBar). */
    private static final float BAR_TOP = 0.075f + 0.02f;

    /**
     * y dla font.draw imienia nad paskiem: dol liter (z ogonkami i obwodka) 1,5 dp nad gornym brzegiem paska, przy
     * kazdej wielkosci pola i czcionki (wczesniej stala liczba pikseli - na telefonie imie wchodzilo na pasek).
     * Ustawia tez projected.x na srodek pola.
     */
    private float nameYAbove(float wx, float barTopWy, BitmapFont font, float ui) {
        projected.set(wx + 0.5f, barTopWy, 0);
        camera.project(projected, viewport.getScreenX(), viewport.getScreenY(), viewport.getScreenWidth(), viewport.getScreenHeight());
        float outline = Math.max(1f, Math.round(ui * 0.6f));
        return projected.y + Math.max(1.5f, 1.5f * ui) + outline + font.getCapHeight() - font.getDescent();
    }

    private static void overheadBar(ShapeRenderer shapes, float x, float y, float hp, Float mana) {
        float bw = 0.72f, bh = 0.075f, bx = x + (1f - bw) / 2f;
        float total = mana != null ? bh * 2 + 0.02f : bh;
        shapes.setColor(0f, 0f, 0f, 0.75f);
        shapes.rect(bx - 0.02f, y - (total - bh) - 0.02f, bw + 0.04f, total + 0.04f);
        float[] c = pl.fawizja.pixel.client.ui.BarColors.health(hp);
        shapes.setColor(c[0], c[1], c[2], 1f);
        shapes.rect(bx, y, bw * Math.max(0f, Math.min(1f, hp)), bh);
        if (mana != null) {
            float[] mc = pl.fawizja.pixel.client.ui.BarColors.mana();
            shapes.setColor(mc[0], mc[1], mc[2], 1f);
            shapes.rect(bx, y - bh - 0.02f, bw * Math.max(0f, Math.min(1f, mana)), bh);
        }
    }

    /**
     * Czaszka PvP (biala/zolta/pomaranczowa/czerwona/czarna) wysrodkowana tuz pod paskami nad postacia; wx = lewa
     * krawedz pola, barBottomWy = dolna krawedz paskow (jednostki swiata). Batch w pikselach ekranu.
     */
    private void drawSkullUnderBars(SpriteBatch batch, BitmapFont font, String skull, float wx, float barBottomWy, float ui) {
        drawSkullUnderBars(batch, font, skull, wx, barBottomWy, ui, 0);
    }

    /** Jak wyzej, ale czaszka nizej o dropPx pikseli ekranu (pod kropkami harmonii mnicha). */
    private void drawSkullUnderBars(SpriteBatch batch, BitmapFont font, String skull, float wx, float barBottomWy, float ui,
                                    float dropPx) {
        if (skull == null || "NONE".equals(skull)) return;
        projected.set(wx + 0.5f, barBottomWy, 0);
        camera.project(projected, viewport.getScreenX(), viewport.getScreenY(), viewport.getScreenWidth(), viewport.getScreenHeight());
        // wielkosc: ok. 1/4 pola, nie mniej niz 12 px i nie wiecej niz 2.2 wielkiej litery imienia
        float is = Math.max(12f, Math.min(font.getCapHeight() * 2.2f, hud.tilePixels() * 0.24f));
        is = Math.round(is);
        float x = Math.round(projected.x - is / 2f), y = Math.round(projected.y - dropPx - is - Math.max(1f, ui));
        com.badlogic.gdx.graphics.g2d.TextureRegion icon = hud.skullIcon(skull);
        if (icon != null) {
            batch.draw(icon, x, y, is, is);
        } else {
            Color c = pl.fawizja.pixel.client.ui.Hud.skullColor(skull);
            outlined(batch, font, "X", x, y + is, c != null ? c.r : 1f, c != null ? c.g : 1f, c != null ? c.b : 1f, ui);
        }
    }

    /**
     * Harmonia mnicha jak w Tibii: rzadek 5 malych kwadratowych kropek tuz pod paskami nad postacia (pelne zlote,
     * puste ciemne, czarna obwodka - piksel-art bez grafik), a przy spokoju (Serene) obok niebieski rombik.
     * points < 0 = nie mnich (nic nie rysuje). Batch w pikselach ekranu; zwraca wysokosc rzadku z odstepem
     * (o tyle nizej idzie czaszka PvP).
     */
    private float drawHarmonyUnderBars(SpriteBatch batch, int points, boolean serene, float wx, float barBottomWy, float ui) {
        if (points < 0) return 0;
        projected.set(wx + 0.5f, barBottomWy, 0);
        camera.project(projected, viewport.getScreenX(), viewport.getScreenY(), viewport.getScreenWidth(), viewport.getScreenHeight());
        HarmonyPips.Layout l = HarmonyPips.layout(hud.tilePixels(), ui);
        float x = Math.round(projected.x - l.width() / 2f), y = Math.round(projected.y - l.gapAbove() - l.height());
        HarmonyPips.draw(batch, hud.whiteRegion(), l, x, y, points, serene);
        return l.gapAbove() + l.height();
    }

    private void drawCombatOverlay(SpriteBatch batch, ShapeRenderer shapes, long t, int z) {
        Monster target = session.combat().target();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        session.boostedCreature(); // online klient nie wola update - ustawia klucz premii w walce
        if (session.character().isPromoted() && session.character().position().z() == z) {
            // znak promocji od krola: zlota gwiazda nad glowa postaci
            float cx = session.renderX(t) + 0.5f, cy = worldRenderer.worldY(session.renderY(t)) + 1.28f;
            shapes.setColor(0.2f, 0.12f, 0f, 0.85f);
            pl.fawizja.pixel.client.ui.Hud.star(shapes, cx, cy - 0.01f, 0.2f);
            shapes.setColor(1f, 0.84f, 0.25f, 1f);
            pl.fawizja.pixel.client.ui.Hud.star(shapes, cx, cy, 0.15f);
        }
        for (Monster m : visibleMonsters) {
            if (m.isDead() || m.position().z() != z) continue;
            float mx = m.renderX(t);
            float my = worldRenderer.worldY(m.renderY(t));
            if (session.combat().isBoosted(m.type())) {
                // potwor dnia: zloty romb nad glowa
                float cx = mx + 0.5f, cy = my + 1.3f;
                shapes.setColor(0.15f, 0.1f, 0f, 0.8f);
                shapes.triangle(cx - 0.17f, cy, cx + 0.17f, cy, cx, cy + 0.17f);
                shapes.triangle(cx - 0.17f, cy, cx + 0.17f, cy, cx, cy - 0.17f);
                shapes.setColor(1f, 0.82f, 0.2f, 1f);
                shapes.triangle(cx - 0.12f, cy, cx + 0.12f, cy, cx, cy + 0.12f);
                shapes.triangle(cx - 0.12f, cy, cx + 0.12f, cy, cx, cy - 0.12f);
            }
            if (m.type().isElite()) {
                // Kuznia: ikona z lewej nad paskiem - pod wpływem fioletowa z kropkami stopnia, diaboliczny czerwona korona
                float cx = mx - 0.08f, cy = my + 1.07f;
                boolean fiend = m.type().isFiendish();
                shapes.setColor(0.08f, 0f, 0.1f, 0.9f);
                shapes.circle(cx, cy, fiend ? 0.17f : 0.14f, 14);
                shapes.setColor(fiend ? 0.95f : 0.62f, fiend ? 0.15f : 0.3f, fiend ? 0.35f : 0.95f, 1f);
                shapes.circle(cx, cy, fiend ? 0.13f : 0.105f, 14);
                if (fiend) {
                    shapes.setColor(1f, 0.85f, 0.3f, 1f);
                    shapes.triangle(cx - 0.09f, cy - 0.03f, cx - 0.03f, cy - 0.03f, cx - 0.06f, cy + 0.09f);
                    shapes.triangle(cx - 0.03f, cy - 0.03f, cx + 0.03f, cy - 0.03f, cx, cy + 0.11f);
                    shapes.triangle(cx + 0.03f, cy - 0.03f, cx + 0.09f, cy - 0.03f, cx + 0.06f, cy + 0.09f);
                } else {
                    int n = m.type().forgeStack();
                    shapes.setColor(1f, 1f, 1f, 1f);
                    for (int i = 0; i < n; i++) shapes.rect(mx + 0.1f + i * 0.09f, my + 1.13f, 0.06f, 0.06f);
                }
            }
            // pasek zycia nad kazdym potworem jak w Tibii (kolor wg procentu)
            overheadBar(shapes, mx, my + 1.02f, m.healthFraction(), null);
        }
        // nad graczami: HP, pod nim mana (wlasna postac; u innych serwer podaje tylko procent zycia)
        PlayerCharacter me0 = session.character();
        if (me0.position().z() == z && !me0.isDead() && sleepBed == null) {
            float ox = session.renderX(t), oy = worldRenderer.worldY(session.renderY(t));
            overheadBar(shapes, ox, oy + 1.06f, me0.health() / (float) Math.max(1, me0.maxHealth()),
                    me0.mana() / (float) Math.max(1, me0.maxMana()));
        }
        for (RemotePlayer rp : remotePlayers.values()) {
            if (rp.to.z() != z) continue;
            overheadBar(shapes, remoteX(rp, t), worldRenderer.worldY(remoteY(rp, t)) + 1.02f, rp.healthPercent / 100f, null);
        }
        shapes.end();
        if (target != null && target.position().z() == z) {
            shapes.begin(ShapeRenderer.ShapeType.Line);
            shapes.setColor(1f, 0.2f, 0.2f, 1f);
            float tx = target.renderX(t), ty = worldRenderer.worldY(target.renderY(t));
            shapes.rect(tx + 0.03f, ty + 0.03f, 0.94f, 0.94f);
            shapes.rect(tx + 0.07f, ty + 0.07f, 0.86f, 0.86f);
            shapes.end();
        }

        RemotePlayer pvpTarget = remotePlayers.get(pvpTargetId);
        if (pvpTarget != null && pvpTarget.to.z() == z) {
            shapes.begin(ShapeRenderer.ShapeType.Line);
            shapes.setColor(1f, 0.2f, 0.2f, 1f);
            float tx = remoteX(pvpTarget, t), ty = worldRenderer.worldY(remoteY(pvpTarget, t));
            shapes.rect(tx + 0.03f, ty + 0.03f, 0.94f, 0.94f);
            shapes.rect(tx + 0.07f, ty + 0.07f, 0.86f, 0.86f);
            shapes.end();
        }

        List<CombatEvent> events = session.combat().recentEvents(t, 1200);
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawSpriteEffects(batch, events, t, z);
        batch.end();
        drawSpellEffects(shapes, events, t, z);
        game.sound().play(soundCues.poll(events, session.character().position(), t), settings.volumeFactor());

        // liczby obrazen w pikselach ekranu
        screenMatrix.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        batch.setProjectionMatrix(screenMatrix);
        batch.begin();
        BitmapFont font = game.font();
        float ui = hud.uiScale();
        float scale = Math.max(1f, ui * 1.1f);
        // imiona nad postaciami: ok. 7 dp wielkiej litery (przeglad z telefonow 2026-10-29), z ciemnym obrysem
        // (duzy monitor przy skali 1: rosnie z polem widoku, zeby nie ginelo na polach 80+ px)
        float nameScale = Math.max(Math.max(0.88f, 0.64f * ui), hud.tilePixels() * 0.0135f);
        font.getData().setScale(nameScale);
        Position me = session.character().position();
        // pod widocznym dachem postaci nie widac (jak w Tibii) - ich imion tez nie
        boolean roofsShown = roofs != null && !roofs.covered(me);
        java.util.Map<Integer, Float> nearRoofs = roofsShown ? worldRenderer.roofFade() : java.util.Map.of();
        float[] gv = hud.gameView();
        float viewTop = gv[1] + gv[3] - 2, viewRight = gv[0] + gv[2];
        for (pl.fawizja.pixel.npc.Npc n : session.npcs().all()) {
            if (n.position().z() != z || n.position().distanceTo(me) > 7) continue;
            if (roofsShown && underRoof(nearRoofs, n.position().x(), n.position().y(), z)) continue;
            projected.set(n.position().x() + 0.5f, worldRenderer.worldY(n.position().y()) + 1.15f, 0);
            camera.project(projected, viewport.getScreenX(), viewport.getScreenY(), viewport.getScreenWidth(), viewport.getScreenHeight());
            nameLayout.setText(font, n.name());
            float nx = projected.x - nameLayout.width / 2f;
            // imie nie wchodzi na gorny pasek ani na prawy panel (ramka widoku gry)
            if (projected.y > viewTop || nx > viewRight - 20) continue;
            outlined(batch, font, n.name(), Math.max(gv[0] + 2, Math.min(nx, viewRight - nameLayout.width - 2)), projected.y,
                    0.75f, 0.95f, 1f, ui);
        }
        for (pl.fawizja.pixel.house.House h : session.houses().all()) {
            // wolny dom obok: napis nad tabliczka (cena po dotknieciu drzwi)
            Position d = h.door();
            if (d.z() != z || d.distanceTo(me) > 6 || !session.houseForSale(h)) continue;
            projected.set(d.x() + 0.5f, worldRenderer.worldY(d.y()) + 1.72f, 0);
            camera.project(projected, viewport.getScreenX(), viewport.getScreenY(), viewport.getScreenWidth(), viewport.getScreenHeight());
            String label = String.format(Messages.get("game_screen.na_sprzedaz_d_gp"), h.price());
            nameLayout.setText(font, label);
            float nx = projected.x - nameLayout.width / 2f;
            if (projected.y > viewTop || nx > viewRight - 20 || nx < gv[0]) continue;
            outlined(batch, font, label, nx, projected.y, 1f, 0.88f, 0.35f, ui);
        }
        if (target != null && !target.isDead() && target.position().z() == z) {
            // imie celu w kolorze trudnosci wzgledem poziomu postaci
            pl.fawizja.pixel.monster.Difficulty d = pl.fawizja.pixel.monster.Difficulty.of(target.type().level(), session.character().level());
            float ny = nameYAbove(target.renderX(t), worldRenderer.worldY(target.renderY(t)) + 1.02f + BAR_TOP, font, ui); // nad paskiem zycia
            projected.y = ny;
            String label = target.type().name() + " (" + d.label() + ")";
            nameLayout.setText(font, label);
            outlined(batch, font, label, projected.x - nameLayout.width / 2f, projected.y, d.r, d.g, d.b, ui);
        }
        for (RemotePlayer rp : remotePlayers.values()) {
            if (rp.to.z() != z) continue;
            if (roofsShown && underRoof(nearRoofs, rp.to.x(), rp.to.y(), z)) continue;
            projected.y = nameYAbove(remoteX(rp, t), worldRenderer.worldY(remoteY(rp, t)) + 1.02f + BAR_TOP, font, ui); // imie nad paskiem zycia (jak w Tibii)
            boolean ally = party.contains(rp.name.toLowerCase(java.util.Locale.ROOT));
            // imie zielone jak w Tibii (druzyna niebieska); czaszka PvP rysowana osobno pod paskami
            if (ally) font.setColor(0.45f, 0.75f, 1f, 1f);
            else font.setColor(PLAYER_NAME[0], PLAYER_NAME[1], PLAYER_NAME[2], 1f);
            pl.fawizja.pixel.admin.AccessLevel rpLevel = pl.fawizja.pixel.admin.AccessLevel.fromTag(rp.access);
            String label = (rpLevel.staff() ? "[" + rpLevel.tag() + "] " : "") + rp.name + (ally ? Messages.get("game_screen.druzyna") : "")
                    + (rp.guild == null ? "" : " <" + rp.guild + ">");
            nameLayout.setText(font, label);
            float lx = projected.x - nameLayout.width / 2f;
            float[] rpTint = staffTint(rpLevel);
            if (rpTint != null) font.setColor(rpTint[0], rpTint[1], rpTint[2], 1f);
            Color nameColor = font.getColor().cpy();
            // ciemny obrys: imie czytelne tez na jasnej podlodze
            outlined(batch, font, label, lx, projected.y, nameColor.r, nameColor.g, nameColor.b, ui);
            // maly czerwony znacznik przed imieniem = gracz z wlaczonym trybem PvP (czaszka - pod paskami)
            if (rp.pvp && "NONE".equals(rp.skull)) {
                batch.setColor(0.9f, 0.15f, 0.15f, 1f);
                batch.draw(hud.whiteRegion(), lx - 6 * scale, projected.y - 7 * scale, 4 * scale, 4 * scale);
                batch.setColor(Color.WHITE);
            }
            if (rp.title != null) {
                // tytul z osiagniecia nad imieniem
                String tl = "\"" + rp.title + "\""; // cudzyslowy ASCII - pewne w kazdej czcionce
                nameLayout.setText(font, tl);
                outlined(batch, font, tl, projected.x - nameLayout.width / 2f, projected.y + nameLayout.height + 5f * ui, 1f, 0.85f, 0.35f, ui);
            }
            float rpBarBottom = worldRenderer.worldY(remoteY(rp, t)) + 1.02f - 0.02f;
            float rpDrop = drawHarmonyUnderBars(batch, rp.harmony, rp.serene, remoteX(rp, t), rpBarBottom, ui);
            drawSkullUnderBars(batch, font, rp.skull, remoteX(rp, t), rpBarBottom, ui, rpDrop);
            drawBubble(batch, font, scale, rp.name, remoteX(rp, t), worldRenderer.worldY(remoteY(rp, t)));
        }
        PlayerCharacter self = session.character();
        if (self.position().z() == z && !self.isDead() && sleepBed == null) {
            // wlasna postac jak w Tibii: imie nad paskami HP/many, czaszka PvP tuz pod nimi
            float ox = session.renderX(t), oy = worldRenderer.worldY(session.renderY(t));
            String sk = self.skull(t).name();
            projected.y = nameYAbove(ox, oy + 1.06f + BAR_TOP, font, ui);
            pl.fawizja.pixel.admin.AccessLevel myLevel = self.accessLevel();
            String label = (myLevel.staff() ? "[" + myLevel.tag() + "] " : "") + self.name();
            nameLayout.setText(font, label);
            float[] myTint = staffTint(myLevel);
            outlined(batch, font, label, projected.x - nameLayout.width / 2f, projected.y,
                    myTint != null ? myTint[0] : PLAYER_NAME[0], myTint != null ? myTint[1] : PLAYER_NAME[1], myTint != null ? myTint[2] : PLAYER_NAME[2], ui);
            float myBarBottom = oy + 1.06f - 0.075f - 0.04f;
            float myDrop = self.vocation() == Vocation.MONK
                    ? drawHarmonyUnderBars(batch, self.harmony().points(), self.harmony().serene(), ox, myBarBottom, ui) : 0;
            drawSkullUnderBars(batch, font, sk, ox, myBarBottom, ui, myDrop);
        }
        if (sleepBed != null && sleepBed.z() == z) {
            // sen: "Zzz" nad poduszka, litery unosza sie po kolei
            String zz = "Z z z".substring(0, Math.min(5, 1 + (int) ((uiMs() / 350) % 3) * 2));
            projected.set(sleepBed.x() + 0.6f, worldRenderer.worldY(sleepBed.y()) + 2.25f, 0);
            camera.project(projected, viewport.getScreenX(), viewport.getScreenY(), viewport.getScreenWidth(), viewport.getScreenHeight());
            outlined(batch, font, zz, projected.x, projected.y, 0.85f, 0.9f, 1f, ui);
        }
        drawBubble(batch, font, scale, session.character().name(), session.renderX(t), worldRenderer.worldY(session.renderY(t)));
        font.getData().setScale(scale);
        for (CombatEvent e : events) {
            if (e.position().z() != z) continue;
            if (!settings.damageNumbers() && (e.kind() == CombatEvent.Kind.HIT_MONSTER || e.kind() == CombatEvent.Kind.HIT_PLAYER)) continue;
            float age = (t - e.timeMs()) / 1200f;
            projected.set(e.position().x() + 0.5f, worldRenderer.worldY(e.position().y()) + 0.9f + age * 0.8f, 0);
            camera.project(projected, viewport.getScreenX(), viewport.getScreenY(), viewport.getScreenWidth(), viewport.getScreenHeight());
            String text;
            pl.fawizja.pixel.charm.Charm charmFx = pl.fawizja.pixel.charm.CharmEffects.fromEvent(e.effect());
            if (charmFx != null && (e.kind() == CombatEvent.Kind.HIT_MONSTER || e.kind() == CombatEvent.Kind.BLOCKED)) {
                // charm: fioletowa liczba (gdy zadal obrazenia) i nazwa charmu pod nia
                if (e.amount() > 0) {
                    text = String.valueOf(e.amount());
                    font.getData().setScale(scale * 1.2f);
                    outlinedFade(batch, font, text, projected.x - text.length() * 3.5f * scale * 1.2f, projected.y + 6 * scale,
                            0.82f, 0.5f, 1f, 1f - age, ui);
                }
                String label = charmFx.label().toLowerCase(java.util.Locale.ROOT);
                font.getData().setScale(scale * 0.8f);
                outlinedFade(batch, font, label, projected.x - label.length() * 3.5f * scale * 0.8f,
                        projected.y + (e.amount() > 0 ? 22 : 8) * scale, 0.9f, 0.7f, 1f, 1f - age, ui);
                font.getData().setScale(scale);
                continue;
            }
            switch (e.kind()) {
                case HIT_MONSTER:
                case HIT_PLAYER:
                    if (pl.fawizja.pixel.combat.CriticalHit.EVENT_EFFECT.equals(e.effect())) {
                        // cios krytyczny: wieksza pomaranczowo-zolta liczba z wykrzyknikiem i napis "krytyk"
                        text = e.amount() + "!";
                        font.getData().setScale(scale * 1.35f);
                        outlinedFade(batch, font, text, projected.x - text.length() * 3.5f * scale * 1.35f, projected.y + 6 * scale,
                                1f, 0.72f, 0.12f, 1f - age, ui);
                        font.getData().setScale(scale * 0.8f);
                        outlinedFade(batch, font, "krytyk", projected.x - 6 * 3.5f * scale * 0.8f, projected.y + 22 * scale,
                                1f, 0.85f, 0.35f, 1f - age, ui);
                        font.getData().setScale(scale);
                        continue;
                    }
                    if (pl.fawizja.pixel.combat.CombatSystem.ONSLAUGHT_EFFECT.equals(e.effect())) {
                        // Natarcie (Kuznia): czerwona liczba i napis "natarcie"
                        text = e.amount() + "!";
                        font.getData().setScale(scale * 1.35f);
                        outlinedFade(batch, font, text, projected.x - text.length() * 3.5f * scale * 1.35f, projected.y + 6 * scale,
                                1f, 0.3f, 0.25f, 1f - age, ui);
                        font.getData().setScale(scale * 0.8f);
                        outlinedFade(batch, font, "natarcie", projected.x - 8 * 3.5f * scale * 0.8f, projected.y + 22 * scale,
                                1f, 0.5f, 0.45f, 1f - age, ui);
                        font.getData().setScale(scale);
                        continue;
                    }
                    if (pl.fawizja.pixel.combat.CombatSystem.MANA_EFFECT.equals(e.effect())) {
                        // wyssanie many: niebieska liczba (utracona mana)
                        font.setColor(0.35f, 0.55f, 1f, 1f - age);
                    } else if (e.effect() != null && e.effect().startsWith(pl.fawizja.pixel.combat.CombatSystem.CONDITION_EFFECT_PREFIX)) {
                        // tik stanu (0.240): kolor zywiolu jak w Tibii - zielona trucizna, pomaranczowy ogien...
                        Color cc = effectColor(conditionElement(e.effect()));
                        font.setColor(cc.r, cc.g, cc.b, 1f - age);
                    } else if (e.kind() == CombatEvent.Kind.HIT_MONSTER) {
                        font.setColor(1f, 1f, 1f, 1f - age);
                    } else {
                        font.setColor(1f, 0.25f, 0.25f, 1f - age);
                    }
                    text = String.valueOf(e.amount());
                    break;
                case HEAL: font.setColor(0.4f, 1f, 0.4f, 1f - age); text = "+" + e.amount(); break;
                case BLOCKED:
                    boolean dodge = pl.fawizja.pixel.combat.CombatSystem.DODGE_EFFECT.equals(e.effect());
                    font.setColor(dodge ? 0.7f : 0.6f, dodge ? 1f : 0.8f, dodge ? 0.7f : 1f, 1f - age);
                    text = dodge ? "unik" : "blok";
                    break;
                case LEVEL_UP: font.setColor(1f, 0.9f, 0.3f, 1f - age); text = Messages.get("game_screen.poziom") + e.amount() + "!"; break;
                case EXP: font.setColor(1f, 1f, 1f, 1f - age); text = e.amount() + " exp"; break; // jak w Tibii: biala liczba doswiadczenia
                default: continue;
            }
            font.draw(batch, text, projected.x - text.length() * 3.5f * scale, projected.y);
        }
        hud.setPartyLine(session.party().inParty() ? session.party().bonusLine() : null);
        drawPartyPanel(batch, font, scale);
        if (chatDraft != null) {
            font.setColor(1f, 1f, 0.6f, 1f);
            boolean caret = (uiMs() / 500) % 2 == 0;
            font.draw(batch, hud.chatTabs().active().title() + ": " + chatDraft + (caret ? "_" : ""), 12 * scale, Gdx.graphics.getHeight() * 0.42f);
        }
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    /**
     * Panel druzyny po lewej stronie: czlonkowie (bez mnie) z HP w procentach, gdy sa w zasiegu wzroku,
     * kolor wg zdrowia - leczacy od razu widzi, kto potrzebuje pomocy.
     */
    private void drawPartyPanel(SpriteBatch batch, BitmapFont font, float scale) {
        if (partyNames.size() < 2) return;
        String me = session.character().name();
        // w lewym gornym rogu okna gry 15x11 (poza nim jest czarna ramka i wycinanie)
        float x = clip[2] > 0 ? clip[0] + 12 * scale : 12 * scale;
        float y = clip[3] > 0 ? clip[1] + clip[3] - 14 * scale : Gdx.graphics.getHeight() * 0.62f;
        if (hud.mobile()) {
            // telefon: widok gry pokrywa ekran - panel pod ramka i wierszem ikon stanu, po lewej
            float ui = hud.uiScale();
            x = 12 * ui;
            y = Gdx.graphics.getHeight() - 70 * ui;
        }
        // tlo dla czytelnosci na jasnej podlodze
        float rows = partyNames.size(); // naglowek + pozostali czlonkowie
        batch.setColor(0f, 0f, 0f, 0.55f);
        batch.draw(game.art().white(), x - 6 * scale, y - rows * 15 * scale - 6 * scale, 190 * scale, rows * 15 * scale + 20 * scale);
        batch.setColor(Color.WHITE);
        font.getData().setScale(scale * 0.85f);
        font.setColor(0.45f, 0.75f, 1f, 1f);
        pl.fawizja.pixel.party.PartyView pv = session.party();
        String head = Messages.get("game_screen.druzyna_2");
        if (pv.inParty()) head += pv.sharing() ? "  +" + pv.bonusPercent() + Messages.get("game_screen.exp") : Messages.get("game_screen.bez_wspolnego_exp");
        font.draw(batch, head, x, y);
        y -= 16 * scale;
        for (String n : partyNames) {
            if (n.equalsIgnoreCase(me)) continue;
            String label = n;
            for (pl.fawizja.pixel.party.PartyView.Member pm : pv.members()) {
                if (pm.name().equalsIgnoreCase(n) && pm.level() > 0) {
                    label = n + " " + pm.level() + (pm.vocation() == null ? "" : " " + pm.vocation().displayName().substring(0, 2).toUpperCase(java.util.Locale.ROOT));
                }
            }
            RemotePlayer rp = null;
            for (RemotePlayer r : remotePlayers.values()) if (r.name != null && r.name.equalsIgnoreCase(n)) rp = r;
            String line;
            if (rp == null) {
                font.setColor(0.6f, 0.6f, 0.65f, 1f);
                line = label + "  (daleko)";
            } else {
                int hp = rp.healthPercent;
                if (hp < 35) font.setColor(1f, 0.35f, 0.3f, 1f);
                else if (hp < 70) font.setColor(1f, 0.85f, 0.35f, 1f);
                else font.setColor(0.55f, 1f, 0.55f, 1f);
                line = label + "  " + hp + "% HP";
            }
            font.draw(batch, line, x, y);
            y -= 15 * scale;
        }
        font.getData().setScale(scale);
        font.setColor(Color.WHITE);
    }

    private static float lerp(int a, int b, float f) { return a + (b - a) * f; }

    private static float remoteProgress(RemotePlayer rp, long t) {
        return Math.max(0f, Math.min(1f, (t - rp.stepStart) / (float) REMOTE_STEP_MS));
    }

    private static float remoteX(RemotePlayer rp, long t) { return lerp(rp.from.x(), rp.to.x(), remoteProgress(rp, t)); }

    private static float remoteY(RemotePlayer rp, long t) { return lerp(rp.from.y(), rp.to.y(), remoteProgress(rp, t)); }

    /**
     * Tryb online: wysyla zmiane kierunku ruchu i odwzorowuje to, co przyszlo z serwera:
     * zapis postaci (SELF), klatke stanu (pozycja, cel, potwory, gracze, ziemia) i komunikaty.
     */
    private void syncWithServer(Direction dir) {
        if (dir != sentMove) {
            net.send("MOVE", dir == null ? "-" : dir.name());
            sentMove = dir;
        }
        if (clockSynced) predictor.tick(session, dir, now());
        String self = net.selfSave();
        if (self != null && !self.equals(lastSelf)) {
            lastSelf = self;
            try {
                session.character().copyStateFrom(game.codec().decode(self));
            } catch (Exception e) {
                Gdx.app.error("Pixel", Messages.get("game_screen.nieczytelny_stan_postaci_z"), e);
            }
        }
        NetClient.Frame f = net.frame();
        if (f != null && f != lastFrame) {
            lastFrame = f;
            long local = TimeUtils.millis() - startMs;
            if (!clockSynced || Math.abs(local + clockOffset - f.serverTime()) > 1_000L) {
                clockOffset = f.serverTime() - local;
                clockSynced = true;
            }
            long now = now();
            if (f.self() != null) {
                Direction facing = null;
                try {
                    if (f.facing() != null) facing = Direction.valueOf(f.facing());
                } catch (IllegalArgumentException ignored) {
                    // nieznany kierunek - zostaje poprzedni
                }
                predictor.onServer(session, f.self(), facing, now);
            }
            session.monsters().syncRemote(f.monsters(), now, REMOTE_STEP_MS);
            session.monsters().faceAdjacent(session.character().position(), now);
            Monster target = null;
            for (Monster m : session.monsters().monsters()) if (m.id() == f.targetId()) target = m;
            session.combat().setTarget(target);
            pvpTargetId = f.playerTargetId();
            if (f.skull() != null && !f.skull().equals(mySkull)) {
                if (!f.skull().equals("NONE")) {
                    session.log().add(now, EventLog.Kind.WARNING, f.skull().equals("BLACK") ? Messages.get("game_screen.masz_czarna_czaszke")
                            : f.skull().equals("RED") ? Messages.get("game_screen.masz_czerwona_czaszke") : Messages.get("game_screen.masz_biala_czaszke_atak"));
                } else if (!mySkull.equals("NONE")) {
                    session.log().add(now, EventLog.Kind.INFO, Messages.get("game_screen.czaszka_zniknela"));
                }
                mySkull = f.skull();
            }
            for (RemotePlayer rp : remotePlayers.values()) rp.seen = false;
            for (RemoteEntity e : f.players()) {
                RemotePlayer rp = remotePlayers.get(e.id());
                if (rp == null) {
                    rp = new RemotePlayer();
                    rp.from = e.position();
                    rp.to = e.position();
                    remotePlayers.put(e.id(), rp);
                } else if (!rp.to.equals(e.position())) {
                    Direction d = Direction.fromDelta(Integer.signum(e.position().x() - rp.to.x()),
                            Integer.signum(e.position().y() - rp.to.y()));
                    boolean step = rp.to.z() == e.position().z() && rp.to.distanceTo(e.position()) == 1;
                    rp.from = step ? rp.to : e.position();
                    rp.to = e.position();
                    rp.stepStart = now;
                    if (d != null) rp.facing = d;
                }
                rp.name = e.name();
                try {
                    rp.vocation = Vocation.valueOf(e.key());
                } catch (IllegalArgumentException ex) {
                    rp.vocation = session.character().vocation();
                }
                rp.healthPercent = e.healthPercent();
                rp.skull = e.mark();
                rp.pvp = e.pvpMode();
                rp.guild = e.guild();
                rp.mounted = e.mounted();
                rp.pet = e.pet();
                rp.title = pl.fawizja.pixel.achievement.Achievement.titleOf(e.title());
                rp.outfit = e.outfit();
                rp.harmony = e.harmony();
                rp.serene = e.serene();
                rp.access = e.access();
                rp.seen = true;
            }
            Map<String, String> skulls = new java.util.HashMap<>();
            for (RemoteEntity e : f.players()) skulls.put(e.name(), e.mark());
            for (String w : skullWatch.update(skulls)) session.log().add(now, EventLog.Kind.WARNING, w);
            remotePlayers.values().removeIf(rp -> !rp.seen);
            session.ground().clear();
            for (NetClient.GroundStack g : f.ground()) session.ground().place(g.position(), new ItemStack(g.itemId(), g.count()));
            for (NetClient.CorpseMark c : f.corpses()) session.ground().placeRemoteCorpse(c.position(), c.monsterKey(), c.hasLoot());
        }
        String[] m;
        while ((m = net.poll()) != null) {
            long now = now();
            boolean quiet = session.social().handle(m); // druzyna, znajomi, PINFO dla API sesji
            switch (m[0]) {
                case "HOUSEINFO": {
                    pl.fawizja.pixel.house.HouseView v = pl.fawizja.pixel.house.HouseView.parse(m);
                    if (v == null) break;
                    if (v.houseId().equals(pendingHouse)) {
                        pl.fawizja.pixel.house.House h = session.houses().get(v.houseId());
                        if (h != null) hud.openHouse(h, v, pendingHouseOwner);
                        pendingHouse = null;
                    } else {
                        hud.updateHouse(v);
                    }
                    break;
                }
                case "PVP": {
                    // PVP 1|0 czaszka czaszkaDo blokadaPzDo (czasy serwera = now() po synchronizacji zegara)
                    if (m.length < 5) break;
                    try {
                        pl.fawizja.pixel.character.PlayerCharacter me = session.character();
                        me.setPvpMode("1".equals(m[1]));
                        me.setSkull(pl.fawizja.pixel.pvp.PvpRules.Skull.valueOf(m[2]), Long.parseLong(m[3]));
                        me.setPzBlockedUntil(Long.parseLong(m[4]));
                        me.setLogoutBlockedUntil(m.length > 5 ? Long.parseLong(m[5]) : 0);
                    } catch (IllegalArgumentException e) {
                        // zly komunikat: pomijamy
                    }
                    break;
                }
                case "HACC": {
                    Map<String, pl.fawizja.pixel.house.HouseAccess> acc = new java.util.HashMap<>();
                    for (String e : (m.length > 1 ? m[1] : "").split(",")) {
                        int c = e.lastIndexOf(':');
                        if (c > 0) acc.put(e.substring(0, c), pl.fawizja.pixel.house.HouseAccess.guestLevel(e.substring(c + 1)));
                    }
                    session.setRemoteHouseAccess(acc);
                    // domy z wlascicielem (bez znaku "Na sprzedaż"); starszy serwer nie wysyla tego pola
                    if (m.length > 2) {
                        java.util.List<String> taken = new java.util.ArrayList<>();
                        for (String id : m[2].split(",")) if (!id.isBlank()) taken.add(id.trim());
                        session.setTakenHouses(taken);
                    }
                    break;
                }
                case "SLEEP": {
                    // serwer polozyl postac w lozku (SLEEP x y z): sen na ekranie, potem wylogowanie
                    if (m.length < 4 || sleepBed != null) break;
                    try {
                        startSleep(new Position(Integer.parseInt(m[1]), Integer.parseInt(m[2]), Integer.parseInt(m[3])), now);
                    } catch (NumberFormatException e) {
                        // zly komunikat: pomijamy
                    }
                    break;
                }
                case "CRATEMV":
                    if (m.length < 7) break;
                    try {
                        session.world().crates().applyMove(
                                new Position(Integer.parseInt(m[1]), Integer.parseInt(m[2]), Integer.parseInt(m[3])),
                                new Position(Integer.parseInt(m[4]), Integer.parseInt(m[5]), Integer.parseInt(m[6])));
                    } catch (NumberFormatException e) {
                        // zly komunikat: pomijamy
                    }
                    break;
                case "DOOR":                                     // drzwi i okna otwarte / zamkniete na serwerze
                    if (m.length < 5) break;
                    try {
                        session.world().openings().apply(
                                new Position(Integer.parseInt(m[1]), Integer.parseInt(m[2]), Integer.parseInt(m[3])), "1".equals(m[4]));
                    } catch (NumberFormatException e) {
                        // zly komunikat: pomijamy
                    }
                    break;
                case "WLIGHT":
                    if (m.length < 5) break;
                    try {
                        session.world().wallLights().apply(
                                new Position(Integer.parseInt(m[1]), Integer.parseInt(m[2]), Integer.parseInt(m[3])), "1".equals(m[4]));
                    } catch (NumberFormatException e) {
                        // zly komunikat: pomijamy
                    }
                    break;
                case "CRATE":
                    if (m.length < 4) break;
                    try {
                        session.world().crates().applyContents(
                                new Position(Integer.parseInt(m[1]), Integer.parseInt(m[2]), Integer.parseInt(m[3])),
                                m.length > 4 ? m[4] : "", session.items());
                    } catch (NumberFormatException e) {
                        // zly komunikat: pomijamy
                    }
                    break;
                case "FOG":
                    if (m.length < 2) break;
                    try {
                        session.character().explored().merge(m[1]);
                    } catch (RuntimeException e) {
                        Gdx.app.error("Pixel", Messages.get("game_screen.nieczytelna_odkryta_mapa_z"), e);
                    }
                    break;
                case "LOG":
                    if (m.length < 3) break;
                    EventLog.Kind kind;
                    try {
                        kind = EventLog.Kind.valueOf(m[1]);
                    } catch (IllegalArgumentException e) {
                        kind = EventLog.Kind.INFO;
                    }
                    session.log().add(now, kind, m[2]);
                    if (Messages.startsWithAny(m[2], Messages.get("ref.zginales"))) net.send("DEATH"); // podsumowanie smierci z serwera
                    break;
                case "CHAL":
                    hud.setOnlineChallenge(m.length > 1 ? m[1] : null);
                    break;
                case "ACCESS": {
                    // ACCESS poziom duch tryb_boga: tylko do wyswietlania (znacznik GOD, przezroczystosc); decyduje serwer
                    if (m.length < 4) break;
                    pl.fawizja.pixel.admin.AccessLevel lv = pl.fawizja.pixel.admin.AccessLevel.parse(m[1]);
                    pl.fawizja.pixel.character.PlayerCharacter me = session.character();
                    me.setAccessLevel(lv);
                    me.setGhost("1".equals(m[2]));
                    me.setGodMode("1".equals(m[3]));
                    break;
                }
                case "LOOTLOG":
                    showLoot(pl.fawizja.pixel.combat.DeathRecap.decode(m, 1));
                    break;
                case "DEATHLOG":
                    showDeath(pl.fawizja.pixel.combat.DeathRecap.decode(m, 1));
                    break;
                case "CHAT":
                    if (m.length >= 3 && !ignored.hides(m[1])) {
                        receiveChat(m[1], m[2], m.length > 3 ? m[3] : null, now);
                        if (!m[1].startsWith("[")) say(m[1], m[2]); // czat ogolny: dymek nad mowiacym
                    }
                    break;
                case "TS":
                    hud.setTrade(pl.fawizja.pixel.trade.TradeView.parse(m));
                    break;
                case "RANK":
                    hud.openRanking(pl.fawizja.pixel.ranking.RankingView.parse(m));
                    break;
                case "EVENTS":
                    hud.openInfo(Messages.get("game_screen.wydarzenia"), pl.fawizja.pixel.game.EventsReport.rows(session, m.length > 1 ? m[1] : null, m.length > 2 ? m[2] : null,
                            m.length > 3 ? m[3] : null));
                    break;
                case "WHO": {
                    List<String[]> rows = new ArrayList<>();
                    for (int i = 1; i < m.length; i++) {
                        String[] r = m[i].split(";", 2);
                        if (r.length == 2) rows.add(r);
                    }
                    hud.openInfo(Messages.get("game_screen.w_grze") + rows.size() + ")", rows);
                    break;
                }
                case "WANT": {
                    List<String[]> rows = new ArrayList<>();
                    for (int i = 1; i < m.length; i++) {
                        String[] r = m[i].split(";", 2);
                        if (r.length == 2) rows.add(r);
                    }
                    if (rows.isEmpty()) rows.add(new String[] {Messages.get("game_screen.brak_zamowien"), Messages.get("game_screen.kupie_20_wilcze_futro")});
                    hud.openInfo(Messages.get("game_screen.zamowienia_kupna") + (m.length - 1) + ")", rows);
                    break;
                }
                case "BOSS": {
                    List<String[]> rows = new ArrayList<>();
                    for (int i = 1; i < m.length; i++) {
                        String[] r = m[i].split(";", 3);
                        if (r.length == 3) rows.add(r);
                    }
                    hud.openInfo(Messages.get("game_screen.bossowie") + rows.size() + ")", rows);
                    break;
                }
                case "MARKET":
                    hud.openMarket(pl.fawizja.pixel.market.MarketView.parse(m));
                    break;
                case "FIEND": {
                    // diaboliczne potwory na minimapie (serwer co 5 s po zmianie): "x,y,z" w kazdym polu
                    List<Position> where = new ArrayList<>();
                    for (int i = 1; i < m.length; i++) {
                        String[] c = m[i].split(",");
                        if (c.length == 3) where.add(new Position(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2])));
                    }
                    session.setRemoteFiendish(where);
                    break;
                }
                case "FRIENDS":
                    if (!quiet) hud.openFriends(pl.fawizja.pixel.friends.FriendsView.parse(m));
                    break;
                case "PARTY":
                    party.clear();
                    partyNames.clear();
                    if (m.length >= 2 && !m[1].equals("-")) {
                        for (String n : m[1].split(",")) {
                            party.add(n.toLowerCase(java.util.Locale.ROOT));
                            partyNames.add(n);
                        }
                    }
                    if (partyNames.size() >= 2) hud.chatTabs().ensure(pl.fawizja.pixel.chat.ChatTabs.Channel.PARTY);
                    break;
                case "EV":
                    if (m.length < 6) break;
                    try {
                        Position from = m.length >= 10 ? new Position(Integer.parseInt(m[6]), Integer.parseInt(m[7]),
                                Integer.parseInt(m[8])) : null;
                        // trafienie z samym efektem (krytyk, wyssanie many) - serwer wysyla "skad" = cel
                        if (from != null && from.equals(new Position(Integer.parseInt(m[2]), Integer.parseInt(m[3]),
                                Integer.parseInt(m[4]))) && !m[1].equals("PROJECTILE")) from = null;
                        session.combat().addRemoteEvent(new CombatEvent(now, new Position(Integer.parseInt(m[2]),
                                Integer.parseInt(m[3]), Integer.parseInt(m[4])), Integer.parseInt(m[5]),
                                CombatEvent.Kind.valueOf(m[1]), from, m.length >= 10 && !m[9].equals("-") ? m[9] : null));
                    } catch (IllegalArgumentException ignored) {
                        // zla linia - pomijamy
                    }
                    break;
                default:
            }
        }
        if (net.isClosed() && !disconnectShown) {
            disconnectShown = true;
            disconnectAtMs = TimeUtils.millis();
            String why = net.error() != null ? net.error() : Messages.get("game_screen.serwer_zamknal_polaczenie");
            session.log().add(now(), EventLog.Kind.WARNING, Messages.get("game_screen.rozlaczono_z_serwerem") + why);
        }
        if (disconnectShown && TimeUtils.millis() - disconnectAtMs > 4_000L) {
            String why = net.error() != null ? net.error() : Messages.get("game_screen.serwer_zamknal_polaczenie");
            Gdx.app.postRunnable(() -> game.backToMenu(Messages.get("game_screen.rozlaczono_z_serwerem") + why));
            disconnectAtMs = Long.MAX_VALUE / 2;
        }
    }

    private void updateGuard(long t) {
        // staly krok klatki (zrzuty regresji GUI): ochrona mierzy prawdziwe zasoby maszyny, wiec jej komunikaty
        // i obnizona jakosc efektow zalezalyby od obciazenia (CI) - zostaje tryb normalny
        if (FIXED_FRAME_MS > 0 && net == null) return;
        if (t - lastGuardSampleMs < GUARD_SAMPLE_MS) return;
        lastGuardSampleMs = t;
        sample.clear();
        game.platform().sampleResources(sample);
        // czas klatki jako wskaznik posredni: udzial budzetu klatki przy docelowym FPS.
        // Pomijany przez pierwsze sekundy (ladowanie i rozgrzewka JIT zawyzaja wynik).
        if (TimeUtils.millis() - startMs > WARMUP_MS) {
            float budget = 1f / Math.max(30, settings.effectiveFpsCap());
            sample.put(Resource.FRAME_TIME, Math.min(1f, frameTimeAvg / (budget / 0.9f)));
        }
        GuardMode mode = guard.update(t, sample);
        if (mode != lastMode) {
            session.log().add(t, EventLog.Kind.SYSTEM, Messages.get("game_screen.ochrona_zasobow") + guard.describe());
            Gdx.app.log("Pixel", "Resource guard -> " + guard.describe());
            lastMode = mode;
        }
        int cap = guard.fpsCap(settings.effectiveFpsCap());
        if (Gdx.app.getType() != Application.ApplicationType.Android) {
            Gdx.graphics.setForegroundFPS(cap);
        }
    }

    /** Programowy limit FPS dla platform bez wbudowanego (Android). */
    private void limitFrameRate() {
        if (!game.platform().needsSoftwareFrameLimit()) return;
        int cap = guard.fpsCap(settings.effectiveFpsCap());
        if (cap <= 0) return;
        long target = 1_000_000_000L / cap;
        long elapsed = TimeUtils.nanoTime() - frameStartNs;
        long sleepMs = (target - elapsed) / 1_000_000L;
        if (sleepMs > 1) {
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private List<String> diagnosticsLines() {
        List<String> l = new ArrayList<>();
        l.add("FPS: " + Gdx.graphics.getFramesPerSecond() + String.format("  (%.1f ms)", frameTimeAvg * 1000));
        l.add(Messages.get("game_screen.rysowanie") + lastDrawCalls + Messages.get("game_screen.tekstury") + lastTextureBinds);
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        l.add(Messages.get("game_screen.pamiec_aplikacji") + used + " / " + rt.maxMemory() / (1024 * 1024) + " MB");
        Map<Resource, Float> last = guard.lastSample();
        for (Resource r : new Resource[] {Resource.SYSTEM_MEMORY, Resource.CPU, Resource.THERMAL, Resource.FRAME_TIME}) {
            Float v = last.get(r);
            l.add(r.label() + ": " + (v == null ? Messages.get("game_screen.niedostepne") : Math.round(v * 100) + "%"));
        }
        String battery = game.platform().batteryInfo();
        l.add(Messages.get("game_screen.bateria") + (battery == null ? Messages.get("game_screen.niedostepne") : battery));
        String thermal = game.platform().thermalInfo();
        if (thermal != null) l.add(Messages.get("game_screen.termika") + thermal);
        l.add(Messages.get("game_screen.tryb") + guard.describe());
        if (net != null) l.add(Messages.get("game_screen.siec_online_poprawki_ruchu") + predictor.corrections() + (net.isClosed() ? Messages.get("game_screen.rozlaczono") : ""));
        l.add("Limit FPS: " + guard.fpsCap(settings.effectiveFpsCap()));
        return l;
    }

    @Override
    public void resize(int width, int height) {
        hud.resize(width, height);
        // skala i kamera tak, by 15x11 pol miescilo sie w ramce widoku gry z HUD-u; swiat rysuje sie tylko
        // w prostokacie 15x11 na srodku ramki (reszta czarna, jak w kliencie Tibii)
        float[] r = hud.gameView();
        // cala liczba pikseli na pole: bez cienkich szwow miedzy kafelkami
        float ppu = hud.tilePixels() > 0 ? hud.tilePixels()
                : Math.max(1f, (float) Math.floor(Math.min(r[2] / VIEW_TILES_W, r[3] / VIEW_TILES_H)));
        viewport.setMinWorldWidth(width / ppu);
        viewport.setMinWorldHeight(height / ppu);
        viewport.update(width, height, false);
        viewOffX = (r[0] + r[2] / 2f - width / 2f) / ppu;
        viewOffY = (r[1] + r[3] / 2f - height / 2f) / ppu;
        // gracz stoi na srodku pola, wiec okno konczy sie rowno 7 pol w bok i 5 w pionie od niego
        float vw = VIEW_TILES_W * ppu, vh = VIEW_TILES_H * ppu;
        clip[0] = Math.round(r[0] + (r[2] - vw) / 2f);
        clip[1] = Math.round(r[1] + (r[3] - vh) / 2f);
        clip[2] = Math.round(vw);
        clip[3] = Math.round(vh);
    }

    /** Lista VIP z podgladu (-Dpixel.open=vip) zamiast session.friends(). */
    private pl.fawizja.pixel.friends.FriendsView vipPreview;
    private long vipFedMs = Long.MIN_VALUE / 2, vipRefreshMs = Long.MIN_VALUE / 2;
    private final List<pl.fawizja.pixel.client.ui.Hud.BattlePlayer> battlePlayers = new ArrayList<>();

    /** Dane panelu HUD z ekranu gry: znajomi (VIP, co 0,5 s; online odswiezani co minute) i gracze w poblizu. */
    private void feedHud(long t) {
        long ui = uiMs();
        if (ui - vipFedMs >= 500 || ui < vipFedMs) {
            vipFedMs = ui;
            hud.setVip(vipPreview != null ? vipPreview : session.friends());
        }
        if (net != null && (ui - vipRefreshMs >= 60_000 || ui < vipRefreshMs)) {
            if (vipRefreshMs != Long.MIN_VALUE / 2) session.social().refreshFriends();
            vipRefreshMs = ui;
        }
        battlePlayers.clear();
        Position me = session.character().position();
        for (Map.Entry<Integer, RemotePlayer> e : remotePlayers.entrySet()) {
            RemotePlayer rp = e.getValue();
            if (rp.to == null || rp.to.z() != me.z() || rp.to.distanceTo(me) > 7) continue;
            battlePlayers.add(new pl.fawizja.pixel.client.ui.Hud.BattlePlayer(e.getKey(), rp.name, rp.healthPercent, rp.skull,
                    party.contains(rp.name.toLowerCase(java.util.Locale.ROOT))));
        }
        battlePlayers.sort(java.util.Comparator.comparingInt(b -> remotePlayers.get(b.id()).to.distanceTo(me)));
        hud.setBattlePlayers(battlePlayers);
    }

    /** Prostokat okna gry 15x11 na ekranie (x, y, szerokosc, wysokosc w pikselach logicznych). */
    private final int[] clip = new int[4];

    private void clipToView(boolean on) {
        if (!on || clip[2] <= 0) {
            Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_SCISSOR_TEST);
            return;
        }
        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_SCISSOR_TEST);
        com.badlogic.gdx.graphics.glutils.HdpiUtils.glScissor(clip[0], clip[1], clip[2], clip[3]);
    }

    /** Przesuniecie srodka ramki widoku gry wzgledem srodka ekranu (w polach). */
    private float viewOffX, viewOffY;

    @Override
    public void pause() {
        // utrata fokusu / blokada ekranu: zapisz postep i wstrzymaj symulacje
        paused = true;
        if (net == null) game.saveCharacter(session.character()); // online zapisuje serwer
    }

    @Override
    public void resume() {
        paused = false;
    }

    @Override
    public void hide() {
        if (net == null) game.saveCharacter(session.character());
    }

    // ------------------------------------------------------------------ sen w lozku i domy na sprzedaz

    /** Lozko, w ktorym lezy postac (sen przed wylogowaniem), i chwila zasniecia. */
    private Position sleepBed;
    private long sleepStartMs;
    private boolean sleepFinished;
    /** Jak dlugo widac postac w lozku przed wylogowaniem (-Dpixel.sleepMs, np. dluzej do zrzutow). */
    private static final long SLEEP_MS = Long.getLong("pixel.sleepMs", 1800L);

    /** Postac kladzie sie w lozku: zamkniete okna, sen na ekranie, po {@link #SLEEP_MS} wylogowanie. */
    private void startSleep(Position bed, long t) {
        sleepBed = bed;
        sleepStartMs = t;
        stopWalk();
        hud.closeHouse();
    }

    private void finishSleep() {
        if (sleepFinished) return;
        sleepFinished = true;
        if (net == null) game.saveCharacter(session.character());
        Gdx.app.postRunnable(() -> game.backToMenu(Messages.get("game_screen.zasypiasz_w_lozku_postac")));
    }

    /** Czy postac lezy w lozku (podglad i testy). */
    public boolean sleeping() { return sleepBed != null; }

    /**
     * Postac w lozku: obrocona jak lezaca, z koldra z grafiki lozka na wierzchu. Lozko pionowe (wezglowie u gory,
     * zajmuje pole lozka i pole nad nim) albo poziome (wezglowie z lewej, pole lozka i pole z lewej).
     */
    private void drawSleeper(SpriteBatch b, PlayerCharacter pc) {
        TextureRegion me = game.art().character(pc.vocation(), Direction.SOUTH, pc.outfit(), 0);
        int bedId = game.world().floor(sleepBed.z()).get(pl.fawizja.pixel.world.Layer.OBJECT, sleepBed.x(), sleepBed.y());
        TextureRegion bed = bedId == 0 ? null : game.art().nativeTile(bedId, sleepBed.x(), sleepBed.y());
        boolean horizontal = bed != null && bed == game.art().nativeVariant(bedId, 1);
        float x = sleepBed.x(), wy = worldRenderer.worldY(sleepBed.y());
        if (horizontal) {
            b.draw(me, x - 0.75f, wy + 0.02f, 0.5f, 0.5f, 1f, 1f, 0.95f, 0.95f, 90f); // glowa na poduszce z lewej
        } else {
            b.draw(me, x + 0.02f, wy + 0.62f, 0.95f, 0.95f);
        }
        if (bed == null) return;
        // koldra: dolna (pionowe) albo prawa (poziome) czesc grafiki lozka na postaci
        int rw = bed.getRegionWidth(), rh = bed.getRegionHeight();
        if (horizontal) {
            TextureRegion quilt = new TextureRegion(bed, rw * 21 / 64, rh * 33 / 64, rw * 40 / 64, rh * 30 / 64);
            b.draw(quilt, x - 1f + 21f / 32f, wy + 1f / 32f, 40f / 32f, 30f / 32f);
        } else {
            TextureRegion quilt = new TextureRegion(bed, rw * 32 / 64, rh * 31 / 64, rw * 32 / 64, rh * 30 / 64);
            b.draw(quilt, x, wy + 3f / 32f, 1f, 30f / 32f);
        }
    }

    /** Ekran gasnie, gdy postac zasypia. */
    private void drawSleepFade(ShapeRenderer shapes, long t) {
        if (sleepBed == null) return;
        float k = Math.min(1f, Math.max(0f, (t - sleepStartMs) / (float) SLEEP_MS));
        float[] r = hud.gameView();
        screenMatrix.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapes.setProjectionMatrix(screenMatrix);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.02f, 0.02f, 0.08f, Math.min(0.55f, 0.15f + 0.6f * k));
        shapes.rect(r[0], r[1], r[2], r[3]);
        shapes.end();
    }

    private TextureRegion saleSign;
    private TextureRegion chestSign;

    /** Tabliczka przy skrzyni zadania (rysowana): palik, jasna deska z liniami napisu i zlotym rogiem skrzyni. */
    private TextureRegion chestSign() {
        if (chestSign != null) return chestSign;
        com.badlogic.gdx.graphics.Pixmap p = new com.badlogic.gdx.graphics.Pixmap(32, 32, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        p.setColor(0.2f, 0.12f, 0.05f, 1f);                      // palik pod deska
        p.fillRectangle(14, 16, 4, 14);
        p.setColor(0.36f, 0.22f, 0.1f, 1f);
        p.fillRectangle(15, 16, 2, 14);
        p.setColor(0.2f, 0.12f, 0.05f, 1f);                      // deska: ciemna ramka
        p.fillRectangle(3, 3, 26, 15);
        p.setColor(0.86f, 0.74f, 0.5f, 1f);
        p.fillRectangle(4, 4, 24, 13);
        p.setColor(0.94f, 0.84f, 0.6f, 1f);
        p.fillRectangle(4, 4, 24, 3);
        p.setColor(0.35f, 0.22f, 0.1f, 1f);                      // linie napisu
        p.fillRectangle(12, 8, 14, 1);
        p.fillRectangle(12, 11, 11, 1);
        p.fillRectangle(12, 14, 13, 1);
        p.setColor(0.6f, 0.38f, 0.12f, 1f);                      // znak skrzyni
        p.fillRectangle(5, 8, 5, 6);
        p.setColor(1f, 0.82f, 0.2f, 1f);
        p.fillRectangle(6, 9, 3, 3);
        chestSign = new TextureRegion(new com.badlogic.gdx.graphics.Texture(p));
        p.dispose();
        return chestSign;
    }

    /** Tabliczka "na sprzedaz" (rysowana, bez grafik z gry): deska na dwoch lancuszkach ze zlota moneta. */
    private TextureRegion saleSign() {
        if (saleSign != null) return saleSign;
        com.badlogic.gdx.graphics.Pixmap p = new com.badlogic.gdx.graphics.Pixmap(32, 32, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        p.setColor(0.25f, 0.22f, 0.2f, 1f);                      // lancuszki
        p.fillRectangle(8, 2, 2, 9);
        p.fillRectangle(22, 2, 2, 9);
        p.setColor(0.24f, 0.14f, 0.06f, 1f);                     // deska z ciemna ramka
        p.fillRectangle(2, 10, 28, 18);
        p.setColor(0.62f, 0.42f, 0.2f, 1f);
        p.fillRectangle(3, 11, 26, 16);
        p.setColor(0.7f, 0.5f, 0.26f, 1f);
        p.fillRectangle(3, 11, 26, 3);
        p.setColor(0.45f, 0.3f, 0.12f, 1f);
        p.fillCircle(16, 19, 6);                                 // moneta
        p.setColor(1f, 0.82f, 0.2f, 1f);
        p.fillCircle(16, 19, 5);
        p.setColor(1f, 0.95f, 0.6f, 1f);
        p.fillCircle(14, 17, 2);
        p.setColor(0.75f, 0.52f, 0.05f, 1f);
        p.fillRectangle(15, 16, 2, 7);
        saleSign = new TextureRegion(new com.badlogic.gdx.graphics.Texture(p));
        p.dispose();
        return saleSign;
    }

    /** Znak "na sprzedaz" nad drzwiami wolnych domow w poblizu (widoczny tez nad dachem, jak tabliczka). */
    private void drawSaleSigns(SpriteBatch b, Position me, int radius) {
        for (pl.fawizja.pixel.house.House h : session.houses().all()) {
            Position d = h.door();
            if (d.z() != me.z() || Math.abs(d.x() - me.x()) > radius || Math.abs(d.y() - me.y()) > radius) continue;
            if (!session.houseForSale(h)) continue;
            float bob = 0.03f * (float) Math.sin(uiMs() / 400.0 + d.x());
            b.draw(saleSign(), d.x() + 0.12f, worldRenderer.worldY(d.y()) + 0.86f + bob, 0.76f, 0.76f);
        }
    }

    @Override
    public void dispose() {
        if (saleSign != null) saleSign.getTexture().dispose();
        if (chestSign != null) chestSign.getTexture().dispose();
        if (net != null) net.close();
        lightRenderer.dispose();
        tapMarker.dispose();
        hud.dispose();
    }

    public GameSession session() { return session; }
    public Hud hud() { return hud; }

    /** Podglad panelu druzyny bez serwera (opcje deweloperskie desktop). */
    public void previewParty(List<String> names) {
        party.clear();
        partyNames.clear();
        for (String n : names) {
            party.add(n.toLowerCase(java.util.Locale.ROOT));
            partyNames.add(n);
        }
        if (partyNames.size() >= 2) hud.chatTabs().ensure(pl.fawizja.pixel.chat.ChatTabs.Channel.PARTY);
    }

    /**
     * Podglad PvP bez serwera (-Dpixel.open=pvp): inni gracze obok postaci - z biala, czerwona, zolta, pomaranczowa
     * i czarna czaszka oraz z wlaczonym trybem PvP - oraz wlasny tryb PvP i biala czaszka z blokada PZ.
     */
    public void previewPvp() {
        Position me = session.character().position();
        Object[][] who = {{"Ula", Vocation.DRUID, 2, 0, "WHITE", false}, {"Kuba", Vocation.KNIGHT, -2, 1, "RED", true},
                {"Mira", Vocation.SORCERER, 1, 2, "NONE", true}, {"Ewa", Vocation.PALADIN, -1, -2, "YELLOW", false},
                {"Olek", Vocation.MONK, 3, -1, "ORANGE", false}, {"Zbir", Vocation.KNIGHT, -3, -1, "BLACK", true}};
        int id = 900;
        for (Object[] w : who) {
            RemotePlayer rp = new RemotePlayer();
            rp.name = (String) w[0];
            rp.vocation = (Vocation) w[1];
            rp.from = rp.to = me.translate((Integer) w[2], (Integer) w[3]);
            rp.healthPercent = 100;
            rp.skull = (String) w[4];
            rp.pvp = (Boolean) w[5];
            rp.seen = true;
            remotePlayers.put(id++, rp);
        }
        session.setPvpMode(true);
        long t = now();
        pl.fawizja.pixel.pvp.PvpRules.markForPreview(session.pvpStatus(), pl.fawizja.pixel.pvp.PvpRules.Skull.WHITE, t);
        session.lockProtectionZonesUntil(session.pvpStatus().fightUntil());
    }

    /**
     * Podglad harmonii innych mnichow (-Dpixel.open=monks, bez serwera): obok postaci mnich z pelna harmonia bez
     * spokoju, mnich z 2 punktami w spokoju (z czerwona czaszka - pod kropkami), mnich bez harmonii i rycerz (bez kropek).
     */
    public void previewMonks() {
        Position me = session.character().position();
        Object[][] who = {{"Olek", Vocation.MONK, 2, 0, 5, false, "NONE"}, {"Ina", Vocation.MONK, -1, -2, 2, true, "RED"},
                {"Tao", Vocation.MONK, 1, -2, 0, false, "NONE"}, {"Kuba", Vocation.KNIGHT, -1, 2, -1, false, "NONE"}};
        int id = 970;
        for (Object[] w : who) {
            RemotePlayer rp = new RemotePlayer();
            rp.name = (String) w[0];
            rp.vocation = (Vocation) w[1];
            rp.from = rp.to = me.translate((Integer) w[2], (Integer) w[3]);
            rp.healthPercent = 100;
            rp.harmony = (Integer) w[4];
            rp.serene = (Boolean) w[5];
            rp.skull = (String) w[6];
            rp.seen = true;
            remotePlayers.put(id++, rp);
        }
    }

    /**
     * Podglad czaszki nad wlasna postacia (-Dpixel.open=skull_red / skull_white): imie nad paskami, czaszka pod
     * nimi; przy bialej obok gracz z czerwona czaszka i gracz bez czaszki.
     */
    public void previewOwnSkull(pl.fawizja.pixel.pvp.PvpRules.Skull skull) {
        long t = now();
        session.setPvpMode(true);
        pl.fawizja.pixel.pvp.PvpRules.markForPreview(session.pvpStatus(), skull, t);
        session.lockProtectionZonesUntil(session.pvpStatus().fightUntil());
        if (skull != pl.fawizja.pixel.pvp.PvpRules.Skull.WHITE) return;
        Position me = session.character().position();
        Object[][] who = {{"Kuba", Vocation.KNIGHT, 2, 0, "RED", true}, {"Ula", Vocation.DRUID, -2, 1, "NONE", false}};
        int id = 950;
        for (Object[] w : who) {
            RemotePlayer rp = new RemotePlayer();
            rp.name = (String) w[0];
            rp.vocation = (Vocation) w[1];
            rp.from = rp.to = me.translate((Integer) w[2], (Integer) w[3]);
            rp.healthPercent = 100;
            rp.skull = (String) w[4];
            rp.pvp = (Boolean) w[5];
            rp.seen = true;
            remotePlayers.put(id++, rp);
        }
    }

    /**
     * Podglad blokady PZ (-Dpixel.open=pzblock): czerwona czaszka po zabiciu gracza, postac staje tuz przed
     * strefa ochronna i probuje do niej wejsc (komunikat w dzienniku).
     */
    public void previewPzBlock() {
        long t = now();
        pl.fawizja.pixel.pvp.PvpRules.Status st = session.pvpStatus();
        session.setPvpMode(true);
        // jak po zabiciu gracza bez czaszki (stan w PvpRules.Status - sesja offline przepisze go do postaci)
        pl.fawizja.pixel.pvp.PvpRules.markForPreview(st, pl.fawizja.pixel.pvp.PvpRules.Skull.RED, t);
        session.lockProtectionZonesUntil(st.fightUntil());
        pl.fawizja.pixel.world.WorldMap w = session.world();
        Position c = session.character().position();
        for (int r = 1; r < 25; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    Position p = c.translate(dx, dy);
                    if (!w.isWalkable(p) || w.isProtectionZone(p) || w.transitionAt(p) != null) continue;
                    for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                        Position q = p.translate(d.dx, d.dy);
                        if (w.isWalkable(q) && w.isProtectionZone(q) && w.transitionAt(q) == null) {
                            session.teleport(p, t);
                            session.update(t + 10, d);
                            return;
                        }
                    }
                }
            }
        }
    }

    /** Podglad listy VIP (-Dpixel.open=vip): znajomi w grze i poza nia, jak z serwera. */
    public void previewVip(pl.fawizja.pixel.friends.FriendsView view) {
        vipPreview = view;
        hud.setVip(view);
    }

    /**
     * Podglad okienka gracza (-Dpixel.open=playerpopup): inni gracze obok (paski HP nad glowami), dane gracza jak
     * z serwera (PINFO) i okienko po dotknieciu.
     */
    public void previewPlayerPopup() {
        previewPvp();
        session.social().handle(new pl.fawizja.pixel.social.PlayerInfo("Ula", 212, Vocation.DRUID, "Wilki", true).encode());
        RemotePlayer ula = remotePlayers.get(900);
        if (ula != null) ula.healthPercent = 64;
        RemotePlayer kuba = remotePlayers.get(901);
        if (kuba != null) kuba.healthPercent = 22;
        hud.openPlayerMenu(900, "Ula", false, false);
    }

    /** Podglad druzyny z poziomami, profesjami i premia (bez serwera; opcje deweloperskie desktop). */
    public void previewParty(pl.fawizja.pixel.party.PartyView view) {
        session.social().handle(view.encode());
        previewParty(view.names());
    }
}
