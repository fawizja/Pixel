package pl.fawizja.pixel.client.screen;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.client.PixelGame;
import pl.fawizja.pixel.client.render.LightRenderer;
import pl.fawizja.pixel.client.render.WorldRenderer;
import pl.fawizja.pixel.client.ui.Hud;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.perf.GuardMode;
import pl.fawizja.pixel.perf.Resource;
import pl.fawizja.pixel.perf.ResourceGuard;
import pl.fawizja.pixel.time.WorldClock;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Lighting;

/** Ekran gry: petla symulacji, kamera, swiat, swiatlo, HUD, autozapis i ochrona zasobow. */
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
    private final WorldClock clock = WorldClock.standard();
    private final ResourceGuard guard = new ResourceGuard();
    private final Map<Resource, Float> sample = new EnumMap<>(Resource.class);
    private final long startMs = TimeUtils.millis();
    private long lastSaveMs;
    private long lastGuardSampleMs;
    private float frameTimeAvg = 1f / 60f;
    private int userFpsCap = 60;
    private GuardMode lastMode = GuardMode.NORMAL;
    private long frameStartNs;
    private boolean paused;

    public GameScreen(PixelGame game, PlayerCharacter pc) {
        this.game = game;
        this.session = new GameSession(game.items(), game.world(), pc);
        this.worldRenderer = new WorldRenderer(game.world(), game.art());
        this.lightRenderer = new LightRenderer(game.art().lightTexture());
        this.hud = new Hud(game.art(), game.font());
        session.log().add(now(), EventLog.Kind.SYSTEM, "Witaj w Varden, " + pc.name() + "! (tryb lokalny)");
        lastSaveMs = now();
    }

    /** Czas swiata: w trybie lokalnym liczony od startu klienta (w trybie online - z serwera). */
    private long now() {
        return TimeUtils.millis() - startMs + 9L * 150_000L; // start o 09:00 czasu gry
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int sx, int sy, int pointer, int button) {
                return hud.touchDown(sx, sy, pointer, session, GameScreen.this::onButton);
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
            public boolean keyDown(int keycode) {
                switch (keycode) {
                    case Input.Keys.E: onButton("use"); return true;
                    case Input.Keys.R: onButton("run"); return true;
                    case Input.Keys.T: onButton("torch"); return true;
                    case Input.Keys.B:
                    case Input.Keys.I: onButton("bag"); return true;
                    case Input.Keys.G: onButton("pick"); return true;
                    case Input.Keys.F3: onButton("diag"); return true;
                    default: return false;
                }
            }
        });
    }

    private void onButton(String id) {
        long t = now();
        switch (id) {
            case "use": session.useNearby(t); break;
            case "run": session.toggleRun(t); break;
            case "torch": session.toggleTorch(t); break;
            case "bag": hud.toggleBag(); break;
            case "pick": session.pickUpHere(t); break;
            case "diag": hud.toggleDiagnostics(); break;
            case "bag_equip":
                session.equipFromSlot(hud.selectedSlot(), t);
                hud.clearSelection();
                break;
            case "bag_drop":
                session.dropFromSlot(hud.selectedSlot(), t);
                hud.clearSelection();
                break;
            default:
        }
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
        long t = now();
        if (paused) return;
        frameTimeAvg = frameTimeAvg * 0.95f + Math.min(delta, 0.25f) * 0.05f;

        Direction dir = keyboardDirection();
        if (dir == null && hud.joystick().active()) {
            dir = Direction.fromVector(hud.joystick().value().x, hud.joystick().value().y, 0.25f);
        }
        session.update(t, dir);
        updateGuard(t);

        if (t - lastSaveMs >= AUTOSAVE_MS) {
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
        worldRenderer.render(batch, camera, z, session.ground(), (b, row) -> {
            if (row == playerRow) {
                b.draw(game.art().character(pc.vocation(), session.facing()), px, worldRenderer.worldY(py), 1f, 1f);
            }
        });
        batch.end();

        int minute = clock.minuteOfDay(t);
        float ambient = Lighting.ambientForFloor(z, clock.ambientLight(minute));
        lightRenderer.render(batch, camera, game.world().floor(z), game.world().height(), ambient,
                guard.effectsQuality(), camX, camY, Lighting.playerLightRadius(pc, game.items()));
        batch.setColor(Color.WHITE);

        hud.render(batch, game.shapes(), session, clock, t, this::diagnosticsLines);
        limitFrameRate();
    }

    private void updateGuard(long t) {
        if (t - lastGuardSampleMs < GUARD_SAMPLE_MS) return;
        lastGuardSampleMs = t;
        sample.clear();
        game.platform().sampleResources(sample);
        // czas klatki jako wskaznik posredni: udzial budzetu klatki przy docelowym FPS.
        // Pomijany przez pierwsze sekundy (ladowanie i rozgrzewka JIT zawyzaja wynik).
        if (TimeUtils.millis() - startMs > WARMUP_MS) {
            float budget = 1f / Math.max(30, userFpsCap);
            sample.put(Resource.FRAME_TIME, Math.min(1f, frameTimeAvg / (budget / 0.9f)));
        }
        GuardMode mode = guard.update(t, sample);
        if (mode != lastMode) {
            session.log().add(t, EventLog.Kind.SYSTEM, "Ochrona zasobow: " + guard.describe());
            Gdx.app.log("Pixel", "Resource guard -> " + guard.describe());
            lastMode = mode;
        }
        int cap = guard.fpsCap(userFpsCap);
        if (Gdx.app.getType() != Application.ApplicationType.Android) {
            Gdx.graphics.setForegroundFPS(cap);
        }
    }

    /** Programowy limit FPS dla platform bez wbudowanego (Android). */
    private void limitFrameRate() {
        if (!game.platform().needsSoftwareFrameLimit()) return;
        int cap = guard.fpsCap(userFpsCap);
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
        l.add("Limit FPS: " + guard.fpsCap(userFpsCap));
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
        game.saveCharacter(session.character());
    }

    @Override
    public void resume() {
        paused = false;
    }

    @Override
    public void hide() {
        game.saveCharacter(session.character());
    }

    @Override
    public void dispose() {
        lightRenderer.dispose();
    }

    public GameSession session() { return session; }
}
