package pl.fawizja.pixel.client.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import pl.fawizja.pixel.client.PixelGame;

/** Menu startowe: gra lokalna (zapis na urzadzeniu) albo online (logowanie na serwer). */
public final class MenuScreen extends ScreenAdapter {

    private final PixelGame game;
    private final ScreenViewport viewport = new ScreenViewport();
    private final GlyphLayout layout = new GlyphLayout();
    private final Vector2 tmp = new Vector2();
    private final Rectangle local = new Rectangle();
    private final Rectangle online = new Rectangle();
    private final String message;
    private final String saveSummary;

    public MenuScreen(PixelGame game, String message) {
        this.game = game;
        this.message = message;
        this.saveSummary = game.localSaveSummary();
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int sx, int sy, int pointer, int button) {
                Vector2 p = viewport.unproject(tmp.set(sx, sy));
                if (local.contains(p)) game.playLocal();
                else if (online.contains(p)) game.setScreen(new LoginScreen(game));
                return true;
            }

            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.NUM_1 || keycode == Input.Keys.L) game.playLocal();
                else if (keycode == Input.Keys.NUM_2 || keycode == Input.Keys.O) game.setScreen(new LoginScreen(game));
                else return false;
                return true;
            }
        });
    }

    @Override
    public void resize(int width, int height) {
        float scale = Math.max(1f, Math.min(3.5f, Gdx.graphics.getDensity()));
        viewport.setUnitsPerPixel(1f / scale);
        viewport.update(width, height, true);
        float w = viewport.getWorldWidth(), h = viewport.getWorldHeight();
        float bw = Math.min(400, w - 32), bh = 54;
        local.set((w - bw) / 2f, h / 2f + 6, bw, bh);
        online.set((w - bw) / 2f, h / 2f - 6 - bh, bw, bh);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.07f, 0.08f, 0.1f, 1f);
        viewport.apply();
        float w = viewport.getWorldWidth(), h = viewport.getWorldHeight();
        ShapeRenderer shapes = game.shapes();
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.2f, 0.18f, 0.26f, 1f);
        shapes.rect(local.x, local.y, local.width, local.height);
        shapes.setColor(0.16f, 0.24f, 0.22f, 1f);
        shapes.rect(online.x, online.y, online.width, online.height);
        shapes.end();

        SpriteBatch batch = game.batch();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        centered(batch, "PIXEL", w / 2f, h - 30, Color.GOLD);
        centered(batch, "1. Graj lokalnie", w / 2f, local.y + local.height - 18, Color.WHITE);
        centered(batch, saveSummary != null ? "Kontynuuj: " + saveSummary : "Nowa postac na tym urzadzeniu",
                w / 2f, local.y + 16, Color.LIGHT_GRAY);
        centered(batch, "2. Graj online", w / 2f, online.y + online.height - 18, Color.WHITE);
        centered(batch, "Wspolny swiat z innymi graczami (serwer, imie, haslo)", w / 2f, online.y + 16, Color.LIGHT_GRAY);
        if (message != null) centered(batch, message, w / 2f, online.y - 24, Color.SALMON);
        batch.end();
    }

    private void centered(SpriteBatch batch, String s, float cx, float cy, Color c) {
        game.font().setColor(c);
        layout.setText(game.font(), s);
        game.font().draw(batch, s, cx - layout.width / 2f, cy + layout.height / 2f);
        game.font().setColor(Color.WHITE);
    }
}
