package pl.fawizja.pixel.client.screen;

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
    /** Towary kupcow na ladach obok nich. */
    private final pl.fawizja.pixel.client.render.ShopDisplays shopDisplays;
    private final pl.fawizja.pixel.client.render.Roofs roofs;
    private final LightRenderer lightRenderer;
    private final Hud hud;
    private final ResourceGuard guard = new ResourceGuard();
    private final Map<Resource, Float> sample = new EnumMap<>(Resource.class);
    private final long startMs = TimeUtils.millis();
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
        String guild;
        boolean mounted;
        String pet;
        String title;
        int outfit;
        final pl.fawizja.pixel.client.render.PetFollower petFollower = new pl.fawizja.pixel.client.render.PetFollower();
        boolean seen;
    }

    /** Wlasny pupil i czas ostatniej klatki (plynne podazanie). */
    private final pl.fawizja.pixel.client.render.PetFollower myPet = new pl.fawizja.pixel.client.render.PetFollower();
    private long lastPetMs = -1;

    private static final long REMOTE_STEP_MS = 300L;

    /** Dymki czatu nad postaciami: imie (male litery) -> {tekst, do kiedy (ms zegara)}. */
    private final Map<String, Object[]> bubbles = new java.util.HashMap<>();
    private final com.badlogic.gdx.graphics.g2d.GlyphLayout bubbleLayout = new com.badlogic.gdx.graphics.g2d.GlyphLayout();

    /** Dymek nad postacia na kilka sekund (dluzszy tekst - dluzej, najwyzej 9 s). */
    public void say(String name, String text) {
        String t = text.length() > 90 ? text.substring(0, 87) + "..." : text;
        bubbles.put(name.toLowerCase(java.util.Locale.ROOT), new Object[] {t, TimeUtils.millis() + Math.min(9_000, 4_000 + 60L * t.length())});
    }

    /** Podskok albo przykucniecie postaci w trakcie emotki z jej dymka (kafelki, dodatni = w gore). */
    private float emoteRise(String name) {
        Object[] b = bubbles.get(name.toLowerCase(java.util.Locale.ROOT));
        if (b == null || (Long) b[1] <= TimeUtils.millis()) return 0f;
        return pl.fawizja.pixel.net.Emotes.rise((String) b[0], TimeUtils.millis());
    }

    private void drawBubble(SpriteBatch batch, BitmapFont font, float scale, String name, float wx, float wy) {
        Object[] b = bubbles.get(name.toLowerCase(java.util.Locale.ROOT));
        if (b == null) return;
        long left = (Long) b[1] - TimeUtils.millis();
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
        Gdx.input.setCatchKey(Input.Keys.BACK, true); // Wstecz na Androidzie zamyka okna zamiast konczyc gre
        this.settingsPrefs = Gdx.app.getPreferences("pixel-settings");
        this.settings = pl.fawizja.pixel.settings.GameSettings.fromMap(settingsPrefs.get());
        hud.setSettings(settings);
        // wlasny uklad paska czarow, osobno dla kazdej postaci
        this.barPrefs = Gdx.app.getPreferences("pixel-bar");
        hud.setSpellBar(pl.fawizja.pixel.settings.SpellBar.decode(barPrefs.getString(barKey(pc), null)));
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
        session.log().add(now(), EventLog.Kind.SYSTEM, "Witaj w Varden, " + pc.name() + "! "
                + (net == null ? "(tryb lokalny)" : "(online, serwer: " + game.platform().serverAddress() + ")"));
        if (net != null) lastSelf = net.selfSave();
        hud.setChatEnabled(net != null); // offline przycisk czatu pojawia sie tylko obok NPC (rozmowa)
        lastSaveMs = now();
    }

    /** Czas swiata: w trybie lokalnym liczony od startu klienta, w trybie online - zgrany z serwerem. */
    private long now() {
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
                if (button == Input.Buttons.RIGHT) return lookWorld(sx, sy); // mysz: prawy przycisk = podglad pola
                if (worldPointer >= 0) return tapWorld(sx, sy); // drugi palec na mapie: od razu dotkniecie
                // dotkniecie mapy rozstrzyga sie pozniej: puszczenie = dotkniecie pola, przytrzymanie = podglad,
                // przeciagniecie = joystick pod kciukiem
                worldPointer = pointer;
                worldDownX = sx;
                worldDownY = sy;
                worldDownMs = TimeUtils.millis();
                worldGesture = 0;
                return true;
            }

            @Override
            public boolean touchDragged(int sx, int sy, int pointer) {
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
                    if (worldGesture == 0) return tapWorld(worldDownX, worldDownY);
                }
                return hud.touchUp(sx, sy, pointer);
            }

            @Override
            public boolean keyTyped(char c) {
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
                    case Input.Keys.E: onButton("use"); return true;
                    case Input.Keys.R: onButton("run"); return true;
                    case Input.Keys.T: onButton("torch"); return true;
                    case Input.Keys.B:
                    case Input.Keys.I: onButton("bag"); return true;
                    case Input.Keys.G: onButton("pick"); return true;
                    case Input.Keys.F3: onButton("diag"); return true;
                    case Input.Keys.SPACE:
                    case Input.Keys.C: onButton("target"); return true;
                    case Input.Keys.V: onButton("defensive"); return true;
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
                    case Input.Keys.ESCAPE:
                        hud.closeTop(session);
                        return true;
                    case Input.Keys.BACK: {
                        // Android: Wstecz zamyka okna; gdy nic nie jest otwarte - dwa razy w ciagu 2 s wychodzi z gry
                        if (hud.closeTop(session)) return true;
                        long t = System.currentTimeMillis();
                        if (t - backPressedMs <= 2000) {
                            Gdx.app.exit();
                        } else {
                            backPressedMs = t;
                            session.log().add(now(), EventLog.Kind.INFO, "Naciśnij Wstecz jeszcze raz, aby wyjść z gry.");
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
        switch (id) {
            case "bag": hud.toggleBag(); break;
            case "diag": hud.toggleDiagnostics(); break;
            case "bar_saved":
                barPrefs.putString(barKey(session.character()), hud.spellBar().encode());
                barPrefs.flush();
                break;
            case "settings:fps":
            case "settings:battery":
            case "settings:light":
            case "settings:numbers":
            case "settings:sound":
            case "settings:loot":
            case "settings:weather":
            case "settings:heal":
            case "settings:mana":
            case "settings:eat":
            case "settings:target":
            case "settings:vibrate":
            case "settings:joyfloat":
            case "settings:joysize":
            case "settings:page":
                settings.apply(id);
                if (id.equals("settings:joysize")) hud.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                settingsPrefs.put(settings.toMap());
                settingsPrefs.flush();
                lastGuardSampleMs = 0; // nowy limit klatek od razu
                break;
            case "book": hud.toggleBook(); break;
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
            case "bag_use":
                act("slot_use:" + hud.selectedSlot(), t);
                hud.clearSelection();
                break;
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
                if (id.startsWith("player:")) {
                    hud.closePlayerMenu();
                    if (net == null) break;
                    String[] f = id.split(":", 3);
                    switch (f[1]) {
                        case "trade": net.send("TRADE", "REQUEST", f[2]); break;
                        case "party": net.send("PARTY", "INVITE", f[2]); break;
                        case "guild": net.send("GUILD", "INVITE", f[2]); break;
                        case "friend": net.send("FRIEND", "ADD", f[2]); break;
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
                    if (net == null) session.log().add(t, EventLog.Kind.INFO, "Rynek graczy działa online.");
                    else net.send("MARKET", "LIST");
                } else if (id.equals("npc:orders")) {
                    if (net == null) session.log().add(t, EventLog.Kind.INFO, "Zamówienia kupna działają online.");
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
                } else if (id.startsWith("best:")) {
                    // karta potwora z bestiariusza (dane z lokalnego rejestru, dziala tez online)
                    String key = id.substring("best:".length());
                    pl.fawizja.pixel.monster.MonsterType mt = game.monsters().contains(key) ? game.monsters().get(key) : null;
                    if (mt != null) {
                        java.util.List<String[]> card = pl.fawizja.pixel.achievement.BestiaryCard.rows(mt,
                                session.character().bestiary().kills(key), session.items());
                        String where = pl.fawizja.pixel.achievement.BestiaryCard.habitats(key, session.monsters().spawns(), session.world());
                        if (!where.isEmpty() && session.character().bestiary().kills(key) > 0) card.add(1, new String[] {"Występuje", where});
                        hud.openInfo(mt.name(), card);
                    }
                } else if (id.startsWith("outfit:")) {
                    chooseOutfit(id.substring("outfit:".length()));
                } else if (id.startsWith("ach:title:")) {
                    String key = id.substring("ach:title:".length());
                    if (key.equals(session.character().title())) key = "-"; // ponowne dotkniecie zdejmuje tytul
                    if (net != null) net.send("TITLE", key);
                    else session.chooseTitle(key, t);
                } else if (id.equals("hunt:reset")) {
                    hunt.reset(t, session.character());
                    session.log().add(t, EventLog.Kind.INFO, "Nowe polowanie: liczniki od zera.");
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
                        session.log().add(t, EventLog.Kind.INFO, target == null ? "Nie znam drogi." : target.name() + " jest na innym piętrze.");
                    } else if (session.npcNearby() == target) {
                        hud.openNpc(session);
                    } else if (walkTo(target.position(), t, false, true)) {
                        walkNpc = target;
                    }
                } else if (id.startsWith("map:goto:")) {
                    String[] xy = id.substring(9).split(",");
                    walkTo(new Position(Integer.parseInt(xy[0]), Integer.parseInt(xy[1]), session.character().position().z()), t, false, false);
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
                    // pole paska: czar, mikstura albo runa - akcja gotowa do wyslania
                    String entry = hud.barEntry(Integer.parseInt(id.substring(6)));
                    if (entry != null) act(entry, t);
                } else {
                    // use, run, torch, pick, defensive, fish, potion_*, cast:, craft:, slot_move:, npc:*
                    act(id, t);
                }
        }
    }

    private void openChat() { openChat(""); }

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
            }, "Czat", start, chatHistory.last() != null ? "Ostatnio: " + chatHistory.last()
                    : net == null ? "Rozmowa z NPC obok (np. praca, handel)" : "Wiadomość albo słowo do NPC obok");
        } else {
            chatHistory.resetCursor();
            chatDraft = new StringBuilder(start);
        }
    }

    private void sendChat(String text) {
        if (text == null || text.isBlank()) return;
        // okna z danymi wlasnej postaci - dzialaja tez bez serwera
        String cmd = java.text.Normalizer.normalize(text.trim().toLowerCase(java.util.Locale.ROOT).replace('ł', 'l'),
                java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String spell = session.spells().registry().byWords(text);
        if (spell != null) {
            // inkantacja jak w Tibii: slowa nad postacia i czar (online inni gracze widza slowa w czacie)
            act("cast:" + spell, now());
            if (net != null) net.send("CHAT", text.trim());
            else say(session.character().name(), text.trim());
            session.log().add(now(), EventLog.Kind.CHAT, session.character().name() + ": " + text.trim());
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
        if (cmd.equals("/pomoc") || cmd.equals("/polecenia") || cmd.equals("/help")) {
            hud.openInfo("Polecenia", pl.fawizja.pixel.net.ChatCommands.helpRows());
            return;
        }
        if (cmd.equals("/nowosci") || cmd.equals("/nowości") || cmd.equals("/zmiany")) {
            showWhatsNew();
            return;
        }
        if (cmd.equals("/wydarzenia") || cmd.equals("/dzis")) {
            if (net != null) net.send("EVENTS");
            else hud.openInfo("Wydarzenia", pl.fawizja.pixel.game.EventsReport.rows(session, null));
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
                session.log().add(now(), EventLog.Kind.INFO, "Wpisz co najmniej 2 litery nazwy, np. /szukaj futro.");
                return;
            }
            List<String[]> rows = pl.fawizja.pixel.inventory.ItemSearch.rows(session.character(), session.items(), q);
            if (rows.isEmpty()) session.log().add(now(), EventLog.Kind.INFO, "Nie masz przedmiotu \"" + q + "\" w plecaku, wyposażeniu ani depozycie.");
            else hud.openInfo("Szukaj: " + q, rows);
            return;
        }
        if (cmd.equals("/autorzy") || cmd.equals("/grafiki")) {
            hud.openInfo("Autorzy grafik", pl.fawizja.pixel.game.Credits.rows());
            return;
        }
        if (cmd.equals("/emotki")) {
            hud.openInfo("Emotki", pl.fawizja.pixel.net.Emotes.rows());
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
                rows.add(new String[] {"Kolor " + pl.fawizja.pixel.character.Outfit.name(i), i == now ? "noszony teraz" : "dotknij, aby założyć", "", "outfit:" + i});
            }
            hud.openInfo("Strój", rows);
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
                session.log().add(now(), EventLog.Kind.INFO, "Nie widzę tu nikogo takiego. Spróbuj: /idź kupiec, bank, kapłan, przewodnik, woźnica albo imię.");
            } else if (session.npcNearby() == n) {
                hud.openNpc(session);
            } else if (walkTo(n.position(), now(), true, true)) {
                walkNpc = n; // rozmowa otworzy sie po dojsciu
                session.log().add(now(), EventLog.Kind.INFO, "Idziesz do: " + n.name() + ".");
            } else {
                session.log().add(now(), EventLog.Kind.INFO, "Nie znam drogi do: " + n.name() + ".");
            }
            return;
        }
        if (cmd.equals("/cena") || cmd.startsWith("/cena ")) {
            String q = text.indexOf(' ') < 0 ? "" : text.substring(text.indexOf(' ') + 1).trim();
            pl.fawizja.pixel.item.ItemType it = pl.fawizja.pixel.npc.PriceCheck.find(session.items(), q);
            if (it == null) {
                session.log().add(now(), EventLog.Kind.INFO, q.isEmpty() ? "Wpisz nazwę przedmiotu, np. /cena wilcze futro."
                        : "Nie znam przedmiotu \"" + q + "\".");
                return;
            }
            hud.openInfo("Cena: " + it.name(), pl.fawizja.pixel.npc.PriceCheck.rows(it, session.npcs(), session.shops()));
            if (net != null) net.send("PRICE", it.key()); // ceny z rynku graczy przychodza w dzienniku
            return;
        }
        if (cmd.equals("/ignoruj") || cmd.startsWith("/ignoruj ")) {
            String who = text.indexOf(' ') < 0 ? "" : text.substring(text.indexOf(' ') + 1).trim();
            if (who.isEmpty()) {
                List<String> all = ignored.all();
                session.log().add(now(), EventLog.Kind.INFO, all.isEmpty() ? "Nikogo nie ignorujesz. /ignoruj Imię ukrywa wiadomości gracza."
                        : "Ignorujesz: " + String.join(", ", all) + " (ponowne /ignoruj Imię przywraca).");
                return;
            }
            boolean on = ignored.toggle(who);
            session.log().add(now(), EventLog.Kind.SYSTEM, on ? "Ignorujesz " + who + ": wiadomości od tej osoby będą ukryte."
                    : ignored.all().size() >= pl.fawizja.pixel.settings.IgnoreList.MAX && !ignored.ignores(who) && !who.isEmpty()
                    ? "Lista ignorowanych jest pełna (" + pl.fawizja.pixel.settings.IgnoreList.MAX + ")." : "Nie ignorujesz już " + who + ".");
            markerPrefs.putString("ignore:" + barKey(session.character()), ignored.encode());
            markerPrefs.flush();
            return;
        }
        if (cmd.equals("/statystyki") || cmd.equals("/staty")) {
            hud.openInfo("Statystyki: " + session.character().name(), pl.fawizja.pixel.game.StatsReport.rows(session.character()));
            return;
        }
        if (cmd.equals("/gracze") || cmd.equals("/online")) {
            if (net != null) net.send("WHO");
            else session.log().add(now(), EventLog.Kind.INFO, "Lista graczy działa online.");
            return;
        }
        if (cmd.equals("/potwor") || cmd.equals("/bonus")) {
            session.log().add(now(), EventLog.Kind.SYSTEM, "Potwór dnia: " + pl.fawizja.pixel.daily.BoostedCreature.describe(session.boostedCreature()) + ".");
            return;
        }
        if (cmd.equals("/znaczniki") || cmd.startsWith("/znacznik ") || cmd.equals("/znacznik")) {
            markerCommand(text.trim(), cmd);
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
        if (house != null && house[0].equals("HOUSE")) {
            // bez serwera: opis domu i lista gosci (inni gracze wchodza tylko online)
            if (house[1].equals("GUEST")) session.addHouseGuest(house[2], now());
            else if (house[1].equals("UNGUEST")) session.removeHouseGuest(house[2], now());
            else if (house[1].equals("INFO")) session.log().add(now(), EventLog.Kind.SYSTEM, session.houseInfo());
            else session.log().add(now(), EventLog.Kind.INFO, "Sprzedaż domu innemu graczowi działa online. Miastu sprzedasz go u zarządcy domów.");
            return;
        }
        if (net == null) {
            session.log().add(now(), EventLog.Kind.INFO, "Czat z graczami działa online. Podejdź do NPC, aby z nim porozmawiać.");
            return;
        }
        String[] msg = pl.fawizja.pixel.net.ChatCommands.toMessage(text);
        if (msg == null) {
            session.log().add(now(), EventLog.Kind.INFO, "Nieznane polecenie. Lista: /pomoc");
            hud.openInfo("Polecenia", pl.fawizja.pixel.net.ChatCommands.helpRows());
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
        worldGesture = 2;
        lookWorld(worldDownX, worldDownY);
    }

    /** Dotkniecie pola swiata (desktop: -Dpixel.tap=x,y). */
    public boolean tapTile(Position p) {
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
            else if (walkTo(p, now(), true, true)) walkNpc = npc; // rozmowa otworzy sie po dojsciu
            else session.log().add(now(), EventLog.Kind.INFO, npc.name() + ": podejdź bliżej, aby porozmawiać.");
            return true;
        }
        if (session.chestAt(p) || game.world().openings().isDoor(p) || game.world().openings().isWindow(p)
                || game.world().crates().isStorage(p) || game.world().wallLights().isWallLight(p)
                || game.world().altarQuestByLever(p) != null || game.world().altarAt(p) != null) {
            // skrzynia, drzwi, okno, pochodnia na murze: obok = otwarcie (drugie dotkniecie zamyka), dalej = marsz na pole obok i otwarcie
            if (p.distanceTo(session.character().position()) <= 1) useOpening(p);
            else if (walkTo(p, now(), true, true)) walkUse = p;
            else session.log().add(now(), EventLog.Kind.INFO, "Nie ma tam drogi.");
            return true;
        }
        if (session.dummies().at(p)) {
            // manekin: obok = trening (jak UZYJ), dalej = marsz na pole obok
            if (p.distanceTo(session.character().position()) <= 1) onButton("use");
            else walkTo(p, now(), true, true);
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
            if (c != null && c.hasLoot()) return walkTo(p, now(), true, true);
            return walkTo(p, now(), true, false);
        }
        return hit;
    }

    /** Marsz do pola wskazanego na duzej mapie: trasa liczona raz, potem krok po kroku jak joystick. */
    private List<Position> walkPath;
    private int walkIndex;
    private pl.fawizja.pixel.npc.Npc walkNpc;
    /** Drzwi, okno albo skrzynia do otwarcia po dojsciu (null = brak). */
    private Position walkUse;

    /** Otwiera albo zamyka drzwi i okna, otwiera skrzynie (online: serwer decyduje, drzwi i okna od razu na ekranie). */
    private void useOpening(Position p) {
        long t = now();
        boolean crate = !session.chestAt(p) && game.world().crates().isStorage(p);
        if (net == null) {
            session.useAt(p, t);
        } else {
            net.send("ACT", "open:" + p.x() + ":" + p.y() + ":" + p.z());
            // dzwignia, oltarz, pochodnia zadania i drzwi poziomu: rozstrzyga tylko serwer (komunikat przyjdzie w logu)
            boolean serverOnly = game.world().altarQuestByLever(p) != null || game.world().altarAt(p) != null
                    || game.world().isQuestTorch(p) || game.world().levelDoorAt(p) > session.character().level();
            if (serverOnly) return;
            if (crate) session.toggleCrateView(p);
            else if (game.world().wallLights().isWallLight(p)) game.world().wallLights().toggle(p);
            else if (!session.chestAt(p)) game.world().openings().toggle(p);
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
            session.log().add(now(), EventLog.Kind.INFO, "Jeszcze nie zginąłeś w tej sesji.");
            return;
        }
        hud.openInfo("Ostatnia śmierć", rows);
    }

    /** Okno ostatnich lupow (/lupy). */
    private void showLoot(List<String[]> rows) {
        if (rows.isEmpty()) {
            session.log().add(now(), EventLog.Kind.INFO, "Nie pokonałeś jeszcze potwora w tej sesji.");
            return;
        }
        hud.openInfo("Ostatnie łupy", rows);
    }

    private void checkDeath() {
        pl.fawizja.pixel.combat.DeathRecap.Report d = session.combat().lastDeath();
        if (net != null || d == null || d == shownDeath) return;
        shownDeath = d;
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
            session.log().add(t, EventLog.Kind.INFO, markers.remove(name) ? "Usunięto znacznik " + name + "." : "Nie ma znacznika " + name + ".");
        } else if (cmd.startsWith("/znacznik ") && !arg.isEmpty()) {
            if (markers.put(arg, pc.position())) {
                session.log().add(t, EventLog.Kind.SYSTEM, "Znacznik " + pl.fawizja.pixel.settings.MapMarkers.cleanName(arg) + " zapisany tutaj (żółta kropka na mapie).");
            } else {
                session.log().add(t, EventLog.Kind.WARNING, "Najwyżej " + pl.fawizja.pixel.settings.MapMarkers.MAX + " znaczników. Usuń któryś: /znacznik usuń nazwa");
            }
        } else {
            List<String[]> rows = new ArrayList<>();
            for (pl.fawizja.pixel.settings.MapMarkers.Marker m : hud.allMarkers(session)) {
                boolean here = m.at().z() == pc.position().z();
                int dist = here ? Math.max(Math.abs(m.at().x() - pc.position().x()), Math.abs(m.at().y() - pc.position().y())) : -1;
                rows.add(here ? new String[] {m.name(), dist + " pól stąd (dotknij, aby iść)", "", "map:goto:" + m.at().x() + "," + m.at().y()}
                        : new String[] {m.name(), "inne piętro (" + m.at().z() + ")"});
            }
            if (rows.isEmpty()) rows.add(new String[] {"Brak znaczników", "/znacznik nazwa zapisuje miejsce, w którym stoisz"});
            rows.add(new String[] {"Usuwanie", "/znacznik usuń nazwa"});
            hud.openInfo("Znaczniki", rows);
            return;
        }
        markerPrefs.putString(barKey(pc), markers.encode());
        markerPrefs.flush();
    }

    private List<String[]> huntRows(long t) {
        List<String[]> rows = pl.fawizja.pixel.stats.HuntAnalyzer.rows(hunt.report(t, session.character()));
        pl.fawizja.pixel.monster.MonsterType boost = session.boostedCreature();
        if (boost != null) rows.add(new String[] {"Potwór dnia", pl.fawizja.pixel.daily.BoostedCreature.describe(boost)});
        pl.fawizja.pixel.item.Enchantments ench = session.character().enchantments();
        for (pl.fawizja.pixel.item.Enchantments.Kind k : pl.fawizja.pixel.item.Enchantments.Kind.values()) {
            if (ench.active(k)) rows.add(new String[] {k.label(), "zostało " + pl.fawizja.pixel.item.Enchantments.describe(ench.remainingMs(k))});
        }
        rows.add(new String[] {"Zacznij od nowa", "zeruje liczniki polowania", "", "hunt:reset"});
        return rows;
    }

    private void openHunt() {
        long t = now();
        updateHunt(t);
        hud.openInfo(HUNT_TITLE, huntRows(t));
        huntRefreshMs = t;
    }

    /** Odswiezanie otwartego okna analizy co sekunde; licznik startuje z pierwsza klatka gry. */
    private void updateHunt(long t) {
        if (hunt == null) hunt = new pl.fawizja.pixel.stats.HuntAnalyzer(session.items());
        if (!hunt.started()) hunt.reset(t, session.character());
        if (t - huntRefreshMs >= 1000 && hud.infoOpen(HUNT_TITLE)) {
            hud.updateInfo(HUNT_TITLE, huntRows(t));
            huntRefreshMs = t;
        }
    }

    /** Raport bossow: online od serwera (tam zyja potwory), lokalnie z wlasnego swiata. */
    private void huntingGrounds() {
        hud.openInfo("Gdzie polować (poziom " + session.character().level() + ")", pl.fawizja.pixel.game.HuntingAdvisor.rows(session));
    }

    private void bossReport(long t) {
        if (net != null) {
            net.send("BOSS");
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (MonsterManager.BossStatus b : session.monsters().bossReport(t)) rows.add(MonsterManager.row(b, t));
        hud.openInfo("Bossowie (" + rows.size() + ")", rows);
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
        hud.openInfo("Co nowego w grze", rows);
    }

    /** Marsz do najblizszego legowiska potwora ze zlecenia lowcy albo zadania dnia (/idz lowisko, dziennik). */
    private void goHunting(long now) {
        pl.fawizja.pixel.monster.SpawnPoint sp = session.huntingGround();
        if (sp == null) {
            session.log().add(now, EventLog.Kind.INFO, "Brak zadania z potworami na tym piętrze. Zlecenie da przewodnik (/idź przewodnik).");
        } else if (walkTo(sp.center(), now, true, true)) {
            session.log().add(now, EventLog.Kind.INFO, "Idziesz na łowisko: " + session.monsterName(sp.monsterKey()) + ".");
        } else {
            session.log().add(now, EventLog.Kind.INFO, "Nie znam drogi na łowisko" + session.whereToHunt(sp.monsterKey()) + ".");
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
            session.log().add(now, EventLog.Kind.INFO, "Idziesz na łowisko: " + session.monsterName(monsterKey) + ".");
        } else {
            session.log().add(now, EventLog.Kind.INFO, "Stąd nie dojdziesz - szukaj: " + session.monsterName(monsterKey)
                    + session.whereToHunt(monsterKey) + ". Woźnice przy fontannach wożą między miastami.");
        }
    }

    private boolean walkTo(Position goal, long now, boolean quiet, boolean nextTo) {
        Position from = session.character().position();
        if (nextTo && from.distanceTo(goal) <= 1) return false;
        List<Position> path = nextTo ? pathNextTo(from, goal)
                : pl.fawizja.pixel.movement.Pathfinder.pathAvoiding(session.world(), from, goal, WALK_MAX_NODES, this::isTrap);
        if (path.isEmpty()) {
            stopWalk();
            if (!quiet) session.log().add(now, EventLog.Kind.INFO, from.equals(goal) ? "Już tu jesteś." : "Nie ma drogi do tego miejsca.");
            return false;
        }
        walkPath = path;
        walkIndex = 0;
        walkNpc = null;
        hud.setWalkTarget(path.get(path.size() - 1));
        if (!quiet) session.log().add(now, EventLog.Kind.INFO, "Idę do zaznaczonego miejsca (" + path.size() + " pól). Joystick przerywa marsz.");
        return true;
    }

    /** Najkrotsza trasa na ktores wolne pole obok celu (cel sam bywa zajety: NPC, fontanna z cialem). */
    private List<Position> pathNextTo(Position from, Position goal) {
        List<Position> best = java.util.Collections.emptyList();
        for (Direction d : Direction.values()) {
            Position n = goal.translate(d.dx, d.dy);
            if (session.npcs().at(n) != null) continue;
            List<Position> p = pl.fawizja.pixel.movement.Pathfinder.pathAvoiding(session.world(), from, n, WALK_MAX_NODES, this::isTrap);
            if (!p.isEmpty() && (best.isEmpty() || p.size() < best.size())) best = p;
        }
        return best;
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
            List<Position> again = pl.fawizja.pixel.movement.Pathfinder.pathAvoiding(session.world(), pos, goal, WALK_MAX_NODES, this::isTrap);
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
        for (Direction d : Direction.values()) if (d.dx == dx && d.dy == dy) return d;
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
        if (paused) return;
        startProfiling();
        checkLongPress();
        frameTimeAvg = frameTimeAvg * 0.95f + Math.min(delta, 0.25f) * 0.05f;
        frameStats.add(delta);
        if (TimeUtils.millis() >= frameLogAtMs) {
            Gdx.app.log("Pixel", frameStats.summary() + " limit=" + guard.fpsCap(settings.effectiveFpsCap())
                    + " tryb=" + guard.mode());
            frameStats.reset();
            frameLogAtMs = TimeUtils.millis() + FRAME_LOG_MS;
        }

        Direction dir = chatDraft == null ? keyboardDirection() : null;
        if (dir == null && hud.joystick().active()) {
            dir = Direction.fromStick(hud.joystick().value().x, hud.joystick().value().y, 0.25f);
        }
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
        if (net != null) syncWithServer(dir);
        final long t = now();
        if (net == null) session.update(t, dir);
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
        worldRenderer.render(batch, camera, z, session.ground(), (b, row) -> {
            drawSplats(b, row, z, t);
            drawCorpses(b, row, z);
            drawChests(b, row, z);
            footprints.drawRow(b, game.art().white(), worldRenderer, row, z, t);
            shopDisplays.drawRow(b, game.art(), worldRenderer, row, z);
            for (int i = 0; i < visibleMonsters.size(); i++) {
                Monster m = visibleMonsters.get(i);
                if (m.position().y() != row || m.position().z() != z) continue;
                float mrx = m.renderX(t), mry = m.renderY(t);
                TextureRegion tr = game.art().monster(m.type().key(),
                        TileArt.walkFrame(mrx, mry, m.position().x(), m.position().y()));
                if (tr == null) continue;
                float[] lg = lunges.get(m.position());
                float mx = mrx + (lg == null ? 0f : lg[0]);
                float my = worldRenderer.worldY(mry + (lg == null ? 0f : lg[1]));
                boolean flip = m.facing() != null && m.facing().dx < 0;
                if (m.type().isElite()) {
                    // elita: wiekszy i zlocisty
                    b.setColor(1f, 0.85f, 0.45f, 1f);
                    b.draw(tr, flip ? mx + 1.15f : mx - 0.15f, my, flip ? -1.3f : 1.3f, 1.3f);
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
            if (row == playerRow) {
                float rise = pc.isMounted() ? 0f : emoteRise(pc.name());
                if (pc.isMounted()) {
                    b.draw(game.art().horse(session.facing()), px + lx, worldRenderer.worldY(py + ly), 1f, 1f);
                    rise = 0.35f;
                }
                int pf = pc.isMounted() ? 0 : TileArt.walkFrame(px, py, pc.position().x(), pc.position().y());
                b.draw(game.art().character(pc.vocation(), session.facing(), pc.outfit(), pf), px + lx, worldRenderer.worldY(py + ly - rise), 1f, 1f);
            }
        });
        worldRenderer.renderAbove(batch, camera, pc.position(), roofs);
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
        clipToView(false);

        if (profiler != null && profiler.isEnabled()) worldDrawCalls = profiler.getDrawCalls();
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

    /** Skrzynie skarbow w danym wierszu: zamknieta albo otwarta (gdy ta postac juz ja oproznila). */
    private void drawChests(SpriteBatch b, int row, int z) {
        for (Position p : session.dummies().all()) {
            if (p.y() == row && p.z() == z) b.draw(game.art().dummy(), p.x(), worldRenderer.worldY(p.y()), 1f, 1f);
        }
        if (game.chests() == null) return;
        for (Position p : game.chests().traps()) {
            if (p.y() == row && p.z() == z) b.draw(game.art().trap(), p.x(), worldRenderer.worldY(p.y()), 1f, 1f);
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
        if (key.matches(".*(bandit|hunter|pirate|sailor|witch|priest|bloodbeard|morvena|cutthroat|gunner).*")) return "corpse_human";
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

    private static Color effectColor(String effect) {
        if (effect == null) return new Color(0.9f, 0.85f, 0.75f, 1f);
        switch (effect) {
            case "FIRE": return new Color(1f, 0.5f, 0.12f, 1f);
            case "ICE": return new Color(0.6f, 0.9f, 1f, 1f);
            case "EARTH": return new Color(0.45f, 0.85f, 0.3f, 1f);
            case "ENERGY": return new Color(0.78f, 0.5f, 1f, 1f);
            case "HOLY": return new Color(1f, 0.95f, 0.55f, 1f);
            case "DEATH": return new Color(0.6f, 0.25f, 0.7f, 1f);
            case "HEAL": return new Color(0.55f, 1f, 0.65f, 1f);
            case "HASTE": return new Color(0.5f, 0.8f, 1f, 1f);
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
            case "DEATH": return "death";
            case "HEAL": return "heal";
            case "HASTE": return "sparkle";
            case "PHYSICAL": return area ? "stones" : null;
            default: return null;
        }
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
            boolean hurt = m.healthFraction() < 1f;
            if (!hurt && m != target) continue;
            float f = m.healthFraction();
            shapes.setColor(0f, 0f, 0f, 0.7f);
            shapes.rect(mx + 0.1f, my + 1.02f, 0.8f, 0.12f);
            shapes.setColor(f > 0.6f ? new Color(0.2f, 0.85f, 0.2f, 1f)
                    : f > 0.3f ? new Color(0.95f, 0.8f, 0.15f, 1f) : new Color(0.9f, 0.15f, 0.15f, 1f));
            shapes.rect(mx + 0.12f, my + 1.04f, 0.76f * f, 0.08f);
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
        float scale = Math.max(1f, Gdx.graphics.getDensity() * 1.1f);
        // imiona NPC w poblizu
        font.getData().setScale(scale * 0.8f);
        Position me = session.character().position();
        // pod widocznym dachem postaci nie widac (jak w Tibii) - ich imion tez nie
        boolean roofsShown = roofs != null && !roofs.covered(me);
        float[] gv = hud.gameView();
        float viewTop = gv[1] + gv[3] - 2, viewRight = gv[0] + gv[2];
        for (pl.fawizja.pixel.npc.Npc n : session.npcs().all()) {
            if (n.position().z() != z || n.position().distanceTo(me) > 7) continue;
            if (roofsShown && roofs.at(n.position().x(), n.position().y(), z) != pl.fawizja.pixel.client.render.Roofs.NONE) continue;
            projected.set(n.position().x() + 0.5f, worldRenderer.worldY(n.position().y()) + 1.15f, 0);
            camera.project(projected, viewport.getScreenX(), viewport.getScreenY(), viewport.getScreenWidth(), viewport.getScreenHeight());
            float nx = projected.x - n.name().length() * 2.8f * scale;
            // imie nie wchodzi na gorny pasek ani na prawy panel (ramka widoku gry)
            if (projected.y > viewTop || nx > viewRight - 20) continue;
            font.setColor(0.75f, 0.95f, 1f, 1f);
            font.draw(batch, n.name(), Math.max(gv[0] + 2, Math.min(nx, viewRight - n.name().length() * 5.6f * scale - 2)), projected.y);
        }
        if (target != null && !target.isDead() && target.position().z() == z) {
            // imie celu w kolorze trudnosci wzgledem poziomu postaci
            pl.fawizja.pixel.monster.Difficulty d = pl.fawizja.pixel.monster.Difficulty.of(target.type().level(), session.character().level());
            projected.set(target.renderX(t) + 0.5f, worldRenderer.worldY(target.renderY(t)) + 1.3f, 0);
            camera.project(projected, viewport.getScreenX(), viewport.getScreenY(), viewport.getScreenWidth(), viewport.getScreenHeight());
            String label = target.type().name() + " (" + d.label + ")";
            font.setColor(d.r, d.g, d.b, 1f);
            font.draw(batch, label, projected.x - label.length() * 2.8f * scale, projected.y);
        }
        for (RemotePlayer rp : remotePlayers.values()) {
            if (rp.to.z() != z) continue;
            if (roofsShown && roofs.at(rp.to.x(), rp.to.y(), z) != pl.fawizja.pixel.client.render.Roofs.NONE) continue;
            projected.set(remoteX(rp, t) + 0.5f, worldRenderer.worldY(remoteY(rp, t)) + 1.15f, 0);
            camera.project(projected, viewport.getScreenX(), viewport.getScreenY(), viewport.getScreenWidth(), viewport.getScreenHeight());
            boolean ally = party.contains(rp.name.toLowerCase(java.util.Locale.ROOT));
            if (ally) font.setColor(0.45f, 0.75f, 1f, 1f);
            else if ("RED".equals(rp.skull)) font.setColor(1f, 0.3f, 0.3f, 1f);
            else if ("WHITE".equals(rp.skull)) font.setColor(1f, 1f, 1f, 1f);
            else font.setColor(0.55f, 1f, 0.55f, 1f);
            String label = rp.name + (ally ? " [drużyna]" : "") + ("NONE".equals(rp.skull) ? "" : " [czaszka]")
                    + (rp.healthPercent < 100 ? " " + rp.healthPercent + "%" : "") + (rp.guild == null ? "" : " <" + rp.guild + ">");
            font.draw(batch, label, projected.x - label.length() * 2.8f * scale, projected.y);
            if (rp.title != null) {
                // tytul z osiagniecia nad imieniem
                String tl = "\"" + rp.title + "\""; // cudzyslowy ASCII - pewne w kazdej czcionce
                font.setColor(1f, 0.85f, 0.35f, 1f);
                font.draw(batch, tl, projected.x - tl.length() * 2.8f * scale, projected.y + 14f * scale);
            }
            drawBubble(batch, font, scale, rp.name, remoteX(rp, t), worldRenderer.worldY(remoteY(rp, t)));
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
            switch (e.kind()) {
                case HIT_MONSTER: font.setColor(1f, 1f, 1f, 1f - age); text = String.valueOf(e.amount()); break;
                case HIT_PLAYER: font.setColor(1f, 0.25f, 0.25f, 1f - age); text = String.valueOf(e.amount()); break;
                case HEAL: font.setColor(0.4f, 1f, 0.4f, 1f - age); text = "+" + e.amount(); break;
                case BLOCKED: font.setColor(0.6f, 0.8f, 1f, 1f - age); text = "blok"; break;
                case LEVEL_UP: font.setColor(1f, 0.9f, 0.3f, 1f - age); text = "POZIOM " + e.amount() + "!"; break;
                case EXP: font.setColor(1f, 1f, 1f, 1f - age); text = e.amount() + " exp"; break; // jak w Tibii: biala liczba doswiadczenia
                default: continue;
            }
            font.draw(batch, text, projected.x - text.length() * 3.5f * scale, projected.y);
        }
        drawPartyPanel(batch, font, scale);
        if (chatDraft != null) {
            font.setColor(1f, 1f, 0.6f, 1f);
            boolean caret = (TimeUtils.millis() / 500) % 2 == 0;
            font.draw(batch, "Czat: " + chatDraft + (caret ? "_" : ""), 12 * scale, Gdx.graphics.getHeight() * 0.42f);
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
        float x = 12 * scale;
        float y = Gdx.graphics.getHeight() * 0.62f;
        // tlo dla czytelnosci na jasnej podlodze
        float rows = partyNames.size(); // naglowek + pozostali czlonkowie
        batch.setColor(0f, 0f, 0f, 0.55f);
        batch.draw(game.art().white(), x - 6 * scale, y - rows * 15 * scale - 6 * scale, 170 * scale, rows * 15 * scale + 20 * scale);
        batch.setColor(Color.WHITE);
        font.getData().setScale(scale * 0.85f);
        font.setColor(0.45f, 0.75f, 1f, 1f);
        font.draw(batch, "Drużyna", x, y);
        y -= 16 * scale;
        for (String n : partyNames) {
            if (n.equalsIgnoreCase(me)) continue;
            RemotePlayer rp = null;
            for (RemotePlayer r : remotePlayers.values()) if (r.name != null && r.name.equalsIgnoreCase(n)) rp = r;
            String line;
            if (rp == null) {
                font.setColor(0.6f, 0.6f, 0.65f, 1f);
                line = n + "  (daleko)";
            } else {
                int hp = rp.healthPercent;
                if (hp < 35) font.setColor(1f, 0.35f, 0.3f, 1f);
                else if (hp < 70) font.setColor(1f, 0.85f, 0.35f, 1f);
                else font.setColor(0.55f, 1f, 0.55f, 1f);
                line = n + "  " + hp + "% HP";
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
                Gdx.app.error("Pixel", "Nieczytelny stan postaci z serwera", e);
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
            Monster target = null;
            for (Monster m : session.monsters().monsters()) if (m.id() == f.targetId()) target = m;
            session.combat().setTarget(target);
            pvpTargetId = f.playerTargetId();
            if (f.skull() != null && !f.skull().equals(mySkull)) {
                if (!f.skull().equals("NONE")) {
                    session.log().add(now, EventLog.Kind.WARNING, f.skull().equals("RED")
                            ? "Masz czerwoną czaszkę!" : "Masz biała czaszkę (atak na gracza).");
                } else if (!mySkull.equals("NONE")) {
                    session.log().add(now, EventLog.Kind.INFO, "Czaszka zniknęła.");
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
                rp.guild = e.guild();
                rp.mounted = e.mounted();
                rp.pet = e.pet();
                rp.title = pl.fawizja.pixel.achievement.Achievement.titleOf(e.title());
                rp.outfit = e.outfit();
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
            switch (m[0]) {
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
                case "LOG":
                    if (m.length < 3) break;
                    EventLog.Kind kind;
                    try {
                        kind = EventLog.Kind.valueOf(m[1]);
                    } catch (IllegalArgumentException e) {
                        kind = EventLog.Kind.INFO;
                    }
                    session.log().add(now, kind, m[2]);
                    if (m[2].startsWith("Zginąłeś")) net.send("DEATH"); // podsumowanie smierci z serwera
                    break;
                case "CHAL":
                    hud.setOnlineChallenge(m.length > 1 ? m[1] : null);
                    break;
                case "LOOTLOG":
                    showLoot(pl.fawizja.pixel.combat.DeathRecap.decode(m, 1));
                    break;
                case "DEATHLOG":
                    showDeath(pl.fawizja.pixel.combat.DeathRecap.decode(m, 1));
                    break;
                case "CHAT":
                    if (m.length >= 3 && !ignored.hides(m[1])) {
                        session.log().add(now, EventLog.Kind.CHAT, m[1] + ": " + m[2]);
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
                    hud.openInfo("Wydarzenia", pl.fawizja.pixel.game.EventsReport.rows(session, m.length > 1 ? m[1] : null, m.length > 2 ? m[2] : null,
                            m.length > 3 ? m[3] : null));
                    break;
                case "WHO": {
                    List<String[]> rows = new ArrayList<>();
                    for (int i = 1; i < m.length; i++) {
                        String[] r = m[i].split(";", 2);
                        if (r.length == 2) rows.add(r);
                    }
                    hud.openInfo("W grze (" + rows.size() + ")", rows);
                    break;
                }
                case "WANT": {
                    List<String[]> rows = new ArrayList<>();
                    for (int i = 1; i < m.length; i++) {
                        String[] r = m[i].split(";", 2);
                        if (r.length == 2) rows.add(r);
                    }
                    if (rows.isEmpty()) rows.add(new String[] {"Brak zamówień", "/kupię 20 wilcze futro 2000 u bankiera"});
                    hud.openInfo("Zamówienia kupna (" + (m.length - 1) + ")", rows);
                    break;
                }
                case "BOSS": {
                    List<String[]> rows = new ArrayList<>();
                    for (int i = 1; i < m.length; i++) {
                        String[] r = m[i].split(";", 3);
                        if (r.length == 3) rows.add(r);
                    }
                    hud.openInfo("Bossowie (" + rows.size() + ")", rows);
                    break;
                }
                case "MARKET":
                    hud.openMarket(pl.fawizja.pixel.market.MarketView.parse(m));
                    break;
                case "FRIENDS":
                    hud.openFriends(pl.fawizja.pixel.friends.FriendsView.parse(m));
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
                    break;
                case "EV":
                    if (m.length < 6) break;
                    try {
                        Position from = m.length >= 10 ? new Position(Integer.parseInt(m[6]), Integer.parseInt(m[7]),
                                Integer.parseInt(m[8])) : null;
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
            String why = net.error() != null ? net.error() : "serwer zamknął połączenie";
            session.log().add(now(), EventLog.Kind.WARNING, "Rozłączono z serwerem: " + why);
        }
        if (disconnectShown && TimeUtils.millis() - disconnectAtMs > 4_000L) {
            String why = net.error() != null ? net.error() : "serwer zamknął połączenie";
            Gdx.app.postRunnable(() -> game.backToMenu("Rozłączono z serwerem: " + why));
            disconnectAtMs = Long.MAX_VALUE / 2;
        }
    }

    private void updateGuard(long t) {
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
            session.log().add(t, EventLog.Kind.SYSTEM, "Ochrona zasobów: " + guard.describe());
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
        l.add("Rysowanie: " + lastDrawCalls + ", tekstury: " + lastTextureBinds);
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        l.add("Pamięć aplikacji: " + used + " / " + rt.maxMemory() / (1024 * 1024) + " MB");
        Map<Resource, Float> last = guard.lastSample();
        for (Resource r : new Resource[] {Resource.SYSTEM_MEMORY, Resource.CPU, Resource.THERMAL, Resource.FRAME_TIME}) {
            Float v = last.get(r);
            l.add(r.label() + ": " + (v == null ? "niedostepne" : Math.round(v * 100) + "%"));
        }
        String battery = game.platform().batteryInfo();
        l.add("Bateria: " + (battery == null ? "niedostepne" : battery));
        String thermal = game.platform().thermalInfo();
        if (thermal != null) l.add("Termika: " + thermal);
        l.add("Tryb: " + guard.describe());
        if (net != null) l.add("Sieć: online, poprawki ruchu: " + predictor.corrections() + (net.isClosed() ? " (rozłączono)" : ""));
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
        float ppu = Math.max(1f, (float) Math.floor(Math.min(r[2] / VIEW_TILES_W, r[3] / VIEW_TILES_H)));
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

    @Override
    public void dispose() {
        if (net != null) net.close();
        lightRenderer.dispose();
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
    }
}
