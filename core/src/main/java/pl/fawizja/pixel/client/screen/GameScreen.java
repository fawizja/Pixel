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
        boolean seen;
    }

    private static final long REMOTE_STEP_MS = 300L;
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
        this.worldRenderer = new WorldRenderer(game.world(), game.art());
        this.lightRenderer = new LightRenderer(game.art().lightTexture());
        this.hud = new Hud(game.art(), game.font());
        this.settingsPrefs = Gdx.app.getPreferences("pixel-settings");
        this.settings = pl.fawizja.pixel.settings.GameSettings.fromMap(settingsPrefs.get());
        hud.setSettings(settings);
        // wlasny uklad paska czarow, osobno dla kazdej postaci
        this.barPrefs = Gdx.app.getPreferences("pixel-bar");
        hud.setSpellBar(pl.fawizja.pixel.settings.SpellBar.decode(barPrefs.getString(barKey(pc), null)));
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
                        sendChat(chatDraft.toString());
                        chatDraft = null;
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
                    case Input.Keys.ESCAPE:
                        if (hud.npcOpen()) hud.toggleNpc(session);
                        else if (hud.bookOpen()) hud.toggleBook();
                        else if (hud.bagOpen()) hud.toggleBag();
                        return true;
                    default: return false;
                }
            }
        });
    }

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
                } else if (id.startsWith("spell:")) {
                    String key = hud.barSpell(Integer.parseInt(id.substring(6)));
                    if (key != null) act("cast:" + key, t);
                } else {
                    // use, run, torch, pick, defensive, fish, potion_*, cast:, craft:, slot_move:, npc:*
                    act(id, t);
                }
        }
    }

    private void openChat() {
        if (Gdx.app.getType() == Application.ApplicationType.Android) {
            Gdx.input.getTextInput(new Input.TextInputListener() {
                @Override
                public void input(String text) { sendChat(text); }

                @Override
                public void canceled() { }
            }, "Czat", "", net == null ? "Rozmowa z NPC obok (np. praca, handel)" : "Wiadomosc albo slowo do NPC obok");
        } else {
            chatDraft = new StringBuilder();
        }
    }

    private void sendChat(String text) {
        if (text == null || text.isBlank()) return;
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
            session.log().add(now(), EventLog.Kind.INFO, "Czat z graczami dziala online. Podejdz do NPC, aby z nim porozmawiac.");
            return;
        }
        String[] msg = pl.fawizja.pixel.net.ChatCommands.toMessage(text);
        if (msg == null) {
            session.log().add(now(), EventLog.Kind.INFO,
                    pl.fawizja.pixel.net.ChatCommands.HELP);
            return;
        }
        net.send(msg);
    }

    /** Dotkniecie swiata: wybor potwora jako celu. */
    private boolean tapWorld(int sx, int sy) {
        Vector3 w = viewport.unproject(new Vector3(sx, sy, 0));
        int x = (int) Math.floor(w.x);
        int y = game.world().height() - 1 - (int) Math.floor(w.y);
        Position p = new Position(x, y, session.character().position().z());
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
            else session.log().add(now(), EventLog.Kind.INFO, npc.name() + ": podejdz blizej, aby porozmawiac.");
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
        }
        return hit;
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
        if (net != null) syncWithServer(dir);
        final long t = now();
        if (net == null) session.update(t, dir);
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
        float[] myLunge = lunges.get(pc.position());
        float lx = myLunge == null ? 0f : myLunge[0], ly = myLunge == null ? 0f : myLunge[1];
        worldRenderer.render(batch, camera, z, session.ground(), (b, row) -> {
            drawCorpses(b, row, z);
            for (int i = 0; i < visibleMonsters.size(); i++) {
                Monster m = visibleMonsters.get(i);
                if (m.position().y() != row || m.position().z() != z) continue;
                TextureRegion tr = game.art().monster(m.type().key());
                if (tr == null) continue;
                float[] lg = lunges.get(m.position());
                float mx = m.renderX(t) + (lg == null ? 0f : lg[0]);
                float my = worldRenderer.worldY(m.renderY(t) + (lg == null ? 0f : lg[1]));
                boolean flip = m.facing() != null && m.facing().dx < 0;
                b.draw(tr, flip ? mx + 1f : mx, my, flip ? -1f : 1f, 1f);
            }
            for (pl.fawizja.pixel.npc.Npc n : session.npcs().all()) {
                Position np = n.position();
                if (np.y() != row || np.z() != z) continue;
                TextureRegion tr = game.art().npc(n.key());
                if (tr != null) b.draw(tr, np.x(), worldRenderer.worldY(np.y()), 1f, 1f);
            }
            for (RemotePlayer rp : remotePlayers.values()) {
                if (rp.to.y() != row || rp.to.z() != z) continue;
                float[] lg = lunges.get(rp.to);
                b.draw(game.art().character(rp.vocation, rp.facing), remoteX(rp, t) + (lg == null ? 0f : lg[0]),
                        worldRenderer.worldY(remoteY(rp, t) + (lg == null ? 0f : lg[1])), 1f, 1f);
            }
            if (row == playerRow) {
                b.draw(game.art().character(pc.vocation(), session.facing()), px + lx, worldRenderer.worldY(py + ly), 1f, 1f);
            }
        });
        batch.end();

        int minute = session.clock().minuteOfDay(t);
        float ambient = Lighting.ambientForFloor(z, session.clock().ambientLight(minute));
        lightRenderer.render(batch, camera, game.world().floor(z), game.world().height(), ambient,
                Math.min(settings.qualityCap(), guard.effectsQuality()), camX, camY, Lighting.playerLightRadius(pc, game.items()));
        batch.setColor(Color.WHITE);
        drawCombatOverlay(batch, game.shapes(), t, z);

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
    private void drawCombatOverlay(SpriteBatch batch, ShapeRenderer shapes, long t, int z) {
        Monster target = session.combat().target();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Monster m : visibleMonsters) {
            if (m.isDead() || m.position().z() != z) continue;
            float mx = m.renderX(t);
            float my = worldRenderer.worldY(m.renderY(t));
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
            String label = rp.name + (ally ? " [druzyna]" : "") + ("NONE".equals(rp.skull) ? "" : " [czaszka]")
                    + (rp.healthPercent < 100 ? " " + rp.healthPercent + "%" : "") + (rp.guild == null ? "" : " <" + rp.guild + ">");
            font.draw(batch, label, projected.x - label.length() * 2.8f * scale, projected.y);
        }
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
        font.draw(batch, "Druzyna", x, y);
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
                            ? "Masz czerwona czaszke!" : "Masz biala czaszke (atak na gracza).");
                } else if (!mySkull.equals("NONE")) {
                    session.log().add(now, EventLog.Kind.INFO, "Czaszka zniknela.");
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
                rp.seen = true;
            }
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
                    break;
                case "CHAT":
                    if (m.length >= 3) session.log().add(now, EventLog.Kind.INFO, m[1] + ": " + m[2]);
                    break;
                case "TS":
                    hud.setTrade(pl.fawizja.pixel.trade.TradeView.parse(m));
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
            String why = net.error() != null ? net.error() : "serwer zamknal polaczenie";
            session.log().add(now(), EventLog.Kind.WARNING, "Rozlaczono z serwerem: " + why);
        }
        if (disconnectShown && TimeUtils.millis() - disconnectAtMs > 4_000L) {
            String why = net.error() != null ? net.error() : "serwer zamknal polaczenie";
            Gdx.app.postRunnable(() -> game.backToMenu("Rozlaczono z serwerem: " + why));
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
            session.log().add(t, EventLog.Kind.SYSTEM, "Ochrona zasobow: " + guard.describe());
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
        l.add("Pamiec aplikacji: " + used + " / " + rt.maxMemory() / (1024 * 1024) + " MB");
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
        if (net != null) l.add("Siec: online, poprawki ruchu: " + predictor.corrections() + (net.isClosed() ? " (rozlaczono)" : ""));
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
