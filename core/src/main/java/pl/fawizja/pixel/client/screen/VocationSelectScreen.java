package pl.fawizja.pixel.client.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.client.PixelGame;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Direction;

/** Wybor profesji i imienia nowej postaci. */
public final class VocationSelectScreen extends ScreenAdapter {

    private final PixelGame game;
    private final ScreenViewport viewport = new ScreenViewport();
    private final GlyphLayout layout = new GlyphLayout();
    private final Vector2 tmp = new Vector2();
    private float cardW, cardH, startX, startY;
    private String message = "";
    private boolean waitingForName;

    public VocationSelectScreen(PixelGame game) {
        this.game = game;
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int sx, int sy, int pointer, int button) {
                Vector2 p = viewport.unproject(tmp.set(sx, sy));
                Vocation v = vocationAt(p.x, p.y);
                if (v != null) askName(v);
                return true;
            }

            @Override
            public boolean keyDown(int keycode) {
                if (keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_4) {
                    askName(Vocation.values()[keycode - Input.Keys.NUM_1]);
                    return true;
                }
                return false;
            }
        });
    }

    private void askName(Vocation v) {
        if (waitingForName) return;
        waitingForName = true;
        Gdx.input.getTextInput(new Input.TextInputListener() {
            @Override
            public void input(String text) {
                Gdx.app.postRunnable(() -> create(text, v));
            }

            @Override
            public void canceled() {
                waitingForName = false;
            }
        }, "Imie postaci (" + v.displayName() + ")", "", "3-20 liter");
    }

    private void create(String name, Vocation v) {
        waitingForName = false;
        try {
            PlayerCharacter.validateName(name);
        } catch (IllegalArgumentException e) {
            message = "Niepoprawne imie: 3-20 liter, spacje i apostrof.";
            return;
        }
        game.createCharacter(name.trim(), v);
    }

    private Vocation vocationAt(float x, float y) {
        Vocation[] all = Vocation.values();
        for (int i = 0; i < all.length; i++) {
            float cx = startX + i * (cardW + 12);
            if (x >= cx && x <= cx + cardW && y >= startY && y <= startY + cardH) return all[i];
        }
        return null;
    }

    @Override
    public void resize(int width, int height) {
        float scale = Math.max(1f, Math.min(3.5f, Gdx.graphics.getDensity()));
        viewport.setUnitsPerPixel(1f / scale);
        viewport.update(width, height, true);
        float w = viewport.getWorldWidth(), h = viewport.getWorldHeight();
        cardW = Math.min(180, (w - 12 * 5) / 4f);
        cardH = Math.min(230, h * 0.62f);
        startX = (w - (cardW * 4 + 12 * 3)) / 2f;
        startY = (h - cardH) / 2f - 10;
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.07f, 0.08f, 0.1f, 1f);
        viewport.apply();
        float w = viewport.getWorldWidth(), h = viewport.getWorldHeight();
        ShapeRenderer shapes = game.shapes();
        SpriteBatch batch = game.batch();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Vocation[] all = Vocation.values();
        for (int i = 0; i < all.length; i++) {
            float cx = startX + i * (cardW + 12);
            shapes.setColor(0.16f, 0.15f, 0.2f, 1f);
            shapes.rect(cx, startY, cardW, cardH);
            shapes.setColor(0.25f, 0.22f, 0.3f, 1f);
            shapes.rect(cx, startY + cardH - 28, cardW, 28);
        }
        shapes.end();

        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        centered(batch, "PIXEL - wybierz profesje", w / 2f, h - 24, Color.WHITE);
        for (int i = 0; i < all.length; i++) {
            Vocation v = all[i];
            float cx = startX + i * (cardW + 12);
            centered(batch, (i + 1) + ". " + v.displayName(), cx + cardW / 2f, startY + cardH - 14, Color.GOLD);
            float s = Math.min(64, cardW * 0.4f);
            batch.draw(game.art().character(v, Direction.SOUTH), cx + cardW / 2f - s / 2f, startY + cardH - 40 - s, s, s);
            game.font().setColor(Color.LIGHT_GRAY);
            game.font().draw(batch, v.playstyle(), cx + 8, startY + cardH - 50 - s, cardW - 16, com.badlogic.gdx.utils.Align.left, true);
            game.font().setColor(Color.WHITE);
        }
        if (!message.isEmpty()) centered(batch, message, w / 2f, startY - 16, Color.SALMON);
        centered(batch, "Start: poziom 8, 10 000 gp w plecaku, miasto Varden", w / 2f, 20, Color.LIGHT_GRAY);
        batch.end();
    }

    private void centered(SpriteBatch batch, String s, float cx, float cy, Color c) {
        game.font().setColor(c);
        layout.setText(game.font(), s);
        game.font().draw(batch, s, cx - layout.width / 2f, cy + layout.height / 2f);
        game.font().setColor(Color.WHITE);
    }
}
