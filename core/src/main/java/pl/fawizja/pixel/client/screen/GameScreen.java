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

    /** Minimalny widoczny obszar w kafelkach (reszta zalezy od proporcji ekranu). */
    private static final float VIEW_TILES_W = 15f;
    private static final float VIEW_TILES_H = 11f;
    private static final long AUTOSAVE_MS = 60_000L;
    private static final long GUARD_SAMPLE_MS = 500L;
    private static final long WARMUP_MS = 5_000L;

    private final PixelGame game;
    private final GameSession session;
    private final OrthographicCamera camera = new OrthographicCamera();
    private final ExtendViewport viewport = new ExtendViewport(VIEW_TILES_W, VIEW_TILES_H, camera);
    private final WorldRenderer worldRenderer;
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
        bubbleLayout.setText(font, text, new com.badlogic.gdx.graphics.Color(1f, 0.95f, 0.6f, alpha), width, com.badlogic.gdx.utils.Align.center, true);
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
        session.setChests(game.chests());
        // -Dpixel.day=N ustala dzien (potwor dnia, zadanie dnia); przy -Dpixel.hour staly dzien dla regresji GUI
        Long day = Long.getLong("pixel.day", System.getProperty("pixel.hour") != null ? 20_000L : null);
        if (day != null) {
            long d = day;
            session.setToday(() -> d);
        }
        this.worldRenderer = new WorldRenderer(game.world(), game.art());
        this.lightRenderer = new LightRenderer(game.art().lightTexture());
        this.hud = new Hud(game.art(), game.font());
        hud.setMonsterNames(k -> {
            try {
                return game.monsters() == null ? k : game.monsters().get(k).name();
            } catch (IllegalArgumentException e) {
                return k; // potwor z nowszej wersji danych
            }
        });
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
                return tapWorld(sx, sy);
            }

            @Override
            public boolean touchDragged(int sx, int sy, int pointer) {
                return hud.touchDragged(sx, sy, pointer);
            }

            @Override
            public boolean touchUp(int sx, int sy, int pointer, int button) {
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
                    } else if (keycode == Input.Keys.ESCAPE) {
                        chatDraft = null;
                    }
                    return true; // klawisze ida do czatu, nie do gry
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
                        if (hud.mapOpen()) hud.toggleMap();
                        else if (hud.npcOpen()) hud.toggleNpc(session);
                        else if (hud.bookOpen()) hud.toggleBook();
                        else if (hud.bagOpen()) hud.toggleBag();
                        return true;
                    default: return false;
                }
            }
        });
    }

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
            case "settings:page":
                settings.apply(id);
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
                } else if (id.startsWith("market:")) {
                    String[] f = id.split(":");
                    if (net != null && f.length == 3 && f[1].equals("buy")) net.send("MARKET", "BUY", f[2]);
                    if (net != null && f.length == 3 && f[1].equals("cancel")) net.send("MARKET", "CANCEL", f[2]);
                } else if (id.equals("npc:bosses")) {
                    bossReport(t);
                } else if (id.equals("book:hunt")) {
                    openHunt();
                } else if (id.startsWith("cmd:")) {
                    sendChat(id.substring("cmd:".length())); // polecenie z okna /pomoc
                } else if (id.startsWith("best:")) {
                    // karta potwora z bestiariusza (dane z lokalnego rejestru, dziala tez online)
                    String key = id.substring("best:".length());
                    pl.fawizja.pixel.monster.MonsterType mt = game.monsters().contains(key) ? game.monsters().get(key) : null;
                    if (mt != null) hud.openInfo(mt.name(), pl.fawizja.pixel.achievement.BestiaryCard.rows(mt,
                            session.character().bestiary().kills(key), session.items()));
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
                } else if (id.startsWith("spell:")) {
                    String key = hud.barSpell(Integer.parseInt(id.substring(6)));
                    if (key != null) act("cast:" + key, t);
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
        if (cmd.equals("/smierc")) {
            if (net != null) net.send("DEATH");
            else showDeath(session.combat().lastDeath() == null ? List.of()
                    : pl.fawizja.pixel.combat.DeathRecap.rows(session.combat().lastDeath()));
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
        Vector3 w = viewport.unproject(new Vector3(sx, sy, 0));
        int x = (int) Math.floor(w.x);
        int y = game.world().height() - 1 - (int) Math.floor(w.y);
        return tapTile(new Position(x, y, session.character().position().z()));
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

    /** Pogoda nad graczem; zmiana rodzaju trafia do dziennika. */
    private Weather.Kind weatherAt(Position p, long wallMs) {
        Weather.Kind k = !settings.weather() ? Weather.Kind.CLEAR
                : forcedWeather != null ? (p.z() < 0 ? Weather.Kind.CLEAR : forcedWeather)
                : Weather.at(wallMs, game.world().regionAt(p), p.z());
        // komunikat tylko przy zmianie pogody na miejscu (nie przy zejsciu pod ziemie i powrocie)
        if (lastWeather != null && k != lastWeather && p.z() >= 0 && lastWeatherZ >= 0) {
            session.log().add(now(), EventLog.Kind.INFO, Weather.describe(k));
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
    private boolean walkTo(Position goal, long now, boolean quiet, boolean nextTo) {
        Position from = session.character().position();
        if (nextTo && from.distanceTo(goal) <= 1) return false;
        List<Position> path = nextTo ? pathNextTo(from, goal)
                : pl.fawizja.pixel.movement.Pathfinder.path(session.world(), from, goal, WALK_MAX_NODES);
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
            List<Position> p = pl.fawizja.pixel.movement.Pathfinder.path(session.world(), from, n, WALK_MAX_NODES);
            if (!p.isEmpty() && (best.isEmpty() || p.size() < best.size())) best = p;
        }
        return best;
    }

    private void stopWalk() {
        walkNpc = null;
        if (walkPath == null) return;
        walkPath = null;
        hud.setWalkTarget(null);
    }

    private Direction walkStep() {
        Position pos = session.character().position();
        Position goal = walkPath.get(walkPath.size() - 1);
        if (pos.equals(goal) || pos.z() != goal.z()) {
            pl.fawizja.pixel.npc.Npc npc = walkNpc;
            stopWalk();
            if (npc != null && session.npcNearby() == npc && !hud.npcOpen()) hud.openNpc(session);
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
            List<Position> again = pl.fawizja.pixel.movement.Pathfinder.path(session.world(), pos, goal, WALK_MAX_NODES);
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
            dir = Direction.fromVector(hud.joystick().value().x, hud.joystick().value().y, 0.25f);
        }
        if (dir != null) stopWalk(); // reczne sterowanie przerywa marsz do celu
        else if (walkPath != null) dir = walkStep();
        if (net != null) syncWithServer(dir);
        final long t = now();
        if (net == null) session.update(t, dir);
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
        camera.position.set(Math.round(camX * ppu) / ppu, Math.round(camY * ppu) / ppu, 0);
        camera.update();

        ScreenUtils.clear(0, 0, 0, 1);
        viewport.apply();
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
        float[] myLunge = lunges.get(pc.position());
        float lx = myLunge == null ? 0f : myLunge[0], ly = myLunge == null ? 0f : myLunge[1];
        worldRenderer.render(batch, camera, z, session.ground(), (b, row) -> {
            drawCorpses(b, row, z);
            drawChests(b, row, z);
            for (int i = 0; i < visibleMonsters.size(); i++) {
                Monster m = visibleMonsters.get(i);
                if (m.position().y() != row || m.position().z() != z) continue;
                TextureRegion tr = game.art().monster(m.type().key());
                if (tr == null) continue;
                float[] lg = lunges.get(m.position());
                float mx = m.renderX(t) + (lg == null ? 0f : lg[0]);
                float my = worldRenderer.worldY(m.renderY(t) + (lg == null ? 0f : lg[1]));
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
                float rise = 0f;
                if (rp.mounted) {
                    b.draw(game.art().horse(rp.facing), remoteX(rp, t), worldRenderer.worldY(remoteY(rp, t)), 1f, 1f);
                    rise = 0.35f;
                }
                b.draw(game.art().character(rp.vocation, rp.facing, rp.outfit), remoteX(rp, t) + (lg == null ? 0f : lg[0]),
                        worldRenderer.worldY(remoteY(rp, t) - rise + (lg == null ? 0f : lg[1])), 1f, 1f);
            }
            if (row == playerRow) {
                float rise = 0f;
                if (pc.isMounted()) {
                    b.draw(game.art().horse(session.facing()), px + lx, worldRenderer.worldY(py + ly), 1f, 1f);
                    rise = 0.35f;
                }
                b.draw(game.art().character(pc.vocation(), session.facing(), pc.outfit()), px + lx, worldRenderer.worldY(py + ly - rise), 1f, 1f);
            }
        });
        batch.end();

        int minute = session.clock().minuteOfDay(t);
        long wallMs = System.currentTimeMillis();
        Weather.Kind weather = weatherAt(pc.position(), wallMs);
        float rain = forcedWeather != null ? 1f : Weather.intensity(wallMs);
        float quality = Math.min(settings.qualityCap(), guard.effectsQuality());
        float ambient = Lighting.ambientForFloor(z, session.clock().ambientLight(minute)) * Weather.ambientFactor(weather, rain);
        lightRenderer.render(batch, camera, game.world().floor(z), game.world().height(), ambient,
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

        hud.render(batch, game.shapes(), session, session.clock(), t, this::diagnosticsLines);
        limitFrameRate();
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

    /** Skrzynie skarbow w danym wierszu: zamknieta albo otwarta (gdy ta postac juz ja oproznila). */
    private void drawChests(SpriteBatch b, int row, int z) {
        for (Position p : session.dummies().all()) {
            if (p.y() == row && p.z() == z) b.draw(game.art().dummy(), p.x(), worldRenderer.worldY(p.y()), 1f, 1f);
        }
        if (game.chests() == null) return;
        for (pl.fawizja.pixel.treasure.TreasureChests.Chest c : game.chests().all()) {
            Position p = c.position();
            if (p.y() != row || p.z() != z) continue;
            boolean open = session.character().openedChests().contains(c.id());
            b.draw(game.art().chest(open), p.x(), worldRenderer.worldY(p.y()), 1f, 1f);
        }
    }

    /** Ciala potworow w danym wierszu: lezacy, przyciemniony sprite (jasniejszy, gdy jest w nim lup). */
    private void drawCorpses(SpriteBatch b, int row, int z) {
        Position me = session.character().position();
        session.ground().forEachCorpseNear(me, 16, (p, c) -> {
            if (p.y() != row || p.z() != z) return;
            TextureRegion tr = game.art().monster(c.monsterKey());
            if (tr == null) return;
            float shade = c.hasLoot() ? 0.62f : 0.42f;
            b.setColor(shade, shade * 0.9f, shade * 0.85f, 1f);
            // obrot o 90 stopni wokol srodka kafelka, splaszczony
            b.draw(tr, p.x(), worldRenderer.worldY(p.y()) - 0.12f, 0.5f, 0.5f, 1f, 1f, 0.9f, 0.75f, 90f);
            b.setColor(Color.WHITE);
        });
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
            default: return new Color(0.9f, 0.85f, 0.75f, 1f);
        }
    }

    /**
     * Efekty czarow i atakow dystansowych: pocisk z warkoczem leci od rzucajacego do celu, czar obszarowy
     * rozbiega sie pierscieniem po kafelkach, leczenie - iskry unoszace sie nad postacia.
     * Przy niskiej jakosci (bateria, ochrona zasobow) bez warkocza i iskier.
     */
    private void drawSpellEffects(ShapeRenderer shapes, List<CombatEvent> events, long t, int z) {
        boolean rich = Math.min(settings.qualityCap(), guard.effectsQuality()) > 0.6f;
        boolean begun = false;
        for (CombatEvent e : events) {
            boolean slash = e.isMeleeStrike() && t - e.timeMs() <= CombatEvent.LUNGE_MS;
            if ((!e.isVisualEffect() && !slash) || e.position().z() != z) continue;
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
        }
        if (!lowHealth.low()) return;
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        float b = Math.max(4f, Math.min(w, h) * 0.015f);
        float alpha = 0.25f + 0.25f * (float) Math.abs(Math.sin(t / 300.0));
        screenMatrix.setToOrtho2D(0, 0, w, h);
        shapes.setProjectionMatrix(screenMatrix);
        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.9f, 0.05f, 0.05f, alpha);
        shapes.rect(0, 0, w, b);
        shapes.rect(0, h - b, w, b);
        shapes.rect(0, b, b, h - 2 * b);
        shapes.rect(w - b, b, b, h - 2 * b);
        shapes.end();
    }

    private void drawCombatOverlay(SpriteBatch batch, ShapeRenderer shapes, long t, int z) {
        Monster target = session.combat().target();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        session.boostedCreature(); // online klient nie wola update - ustawia klucz premii w walce
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
        for (pl.fawizja.pixel.npc.Npc n : session.npcs().all()) {
            if (n.position().z() != z || n.position().distanceTo(me) > 7) continue;
            projected.set(n.position().x() + 0.5f, worldRenderer.worldY(n.position().y()) + 1.15f, 0);
            camera.project(projected, viewport.getScreenX(), viewport.getScreenY(), viewport.getScreenWidth(), viewport.getScreenHeight());
            font.setColor(0.75f, 0.95f, 1f, 1f);
            font.draw(batch, n.name(), projected.x - n.name().length() * 2.8f * scale, projected.y);
        }
        for (RemotePlayer rp : remotePlayers.values()) {
            if (rp.to.z() != z) continue;
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
                case "LOOTLOG":
                    showLoot(pl.fawizja.pixel.combat.DeathRecap.decode(m, 1));
                    break;
                case "DEATHLOG":
                    showDeath(pl.fawizja.pixel.combat.DeathRecap.decode(m, 1));
                    break;
                case "CHAT":
                    if (m.length >= 3 && !ignored.hides(m[1])) {
                        session.log().add(now, EventLog.Kind.INFO, m[1] + ": " + m[2]);
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
                    hud.openInfo("Wydarzenia", pl.fawizja.pixel.game.EventsReport.rows(session, m.length > 1 ? m[1] : null));
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
        viewport.update(width, height, false);
        hud.resize(width, height);
    }

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
