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
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.client.PixelGame;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Direction;

/**
 * Tworzenie postaci lokalnej: profesja, miasto startowe (jak w Tibii - tam postac mieszka i zaczyna w swiatyni)
 * i imie. Dotkniecie karty zaznacza, przycisk "Utworz postac" pyta o imie. Klawiatura: 1-5 profesja,
 * strzalki w lewo/prawo miasto, Enter tworzy.
 */
public final class VocationSelectScreen extends ScreenAdapter {

    private static final float GAP = 8;

    private final PixelGame game;
    private final ScreenViewport viewport = new ScreenViewport();
    private final GlyphLayout layout = new GlyphLayout();
    private final Vector2 tmp = new Vector2();
    private final Rectangle[] vocationCards = new Rectangle[Vocation.values().length];
    private final Rectangle create = new Rectangle();
    private final Rectangle back = new Rectangle();
    private final TownPicker towns;
    private Vocation vocation = Vocation.values()[0];
    private float vocLabelY, townLabelY, playstyleY, contentX, contentW;
    private boolean playstyleInCard;
    private String message = "";
    private boolean waitingForName;

    public VocationSelectScreen(PixelGame game) {
        this.game = game;
        for (int i = 0; i < vocationCards.length; i++) vocationCards[i] = new Rectangle();
        // -Dpixel.createTown / -Dpixel.createVocation: podglad ekranu z zaznaczonym wyborem (regresja GUI)
        towns = new TownPicker(game.world(), System.getProperty("pixel.createTown", game.defaultStartTown()));
        try {
            String v = System.getProperty("pixel.createVocation");
            if (v != null) vocation = Vocation.valueOf(v);
        } catch (IllegalArgumentException ignored) {
            // zostaje pierwsza profesja
        }
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int sx, int sy, int pointer, int button) {
                Vector2 p = viewport.unproject(tmp.set(sx, sy));
                for (int i = 0; i < vocationCards.length; i++) {
                    if (vocationCards[i].contains(p)) {
                        vocation = Vocation.values()[i];
                        return true;
                    }
                }
                if (towns.touch(p.x, p.y)) return true;
                if (create.contains(p)) askName();
                else if (back.contains(p)) game.backToMenu(null);
                return true;
            }

            @Override
            public boolean keyDown(int keycode) {
                if (keycode >= Input.Keys.NUM_1 && keycode < Input.Keys.NUM_1 + Vocation.values().length) {
                    vocation = Vocation.values()[keycode - Input.Keys.NUM_1];
                } else if (keycode == Input.Keys.LEFT) {
                    towns.cycle(-1);
                } else if (keycode == Input.Keys.RIGHT) {
                    towns.cycle(1);
                } else if (keycode == Input.Keys.ENTER) {
                    askName();
                } else if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) {
                    game.backToMenu(null);
                } else {
                    return false;
                }
                return true;
            }
        });
    }

    private void askName() {
        if (waitingForName) return;
        waitingForName = true;
        Vocation v = vocation;
        String town = towns.selectedKey();
        String townName = towns.selected() == null ? "" : ", " + towns.selected().name();
        Gdx.input.getTextInput(new Input.TextInputListener() {
            @Override
            public void input(String text) {
                Gdx.app.postRunnable(() -> create(text, v, town));
            }

            @Override
            public void canceled() {
                waitingForName = false;
            }
        }, "Imię postaci (" + v.displayName() + townName + ")", "", "3-20 liter");
    }

    private void create(String name, Vocation v, String town) {
        waitingForName = false;
        try {
            PlayerCharacter.validateName(name);
        } catch (IllegalArgumentException e) {
            message = "Niepoprawne imię: 3-20 liter, spacje i apostrof.";
            return;
        }
        game.createCharacter(name.trim(), v, town);
    }

    /** Skala interfejsu: gestosc ekranu (telefon 2..3.5), na komputerze -Dpixel.uiScale (podglad telefonu). */
    static float uiScale() {
        String forced = System.getProperty("pixel.uiScale");
        float density = Gdx.graphics.getDensity();
        if (forced != null) {
            try {
                density = Float.parseFloat(forced);
            } catch (NumberFormatException ignored) {
                // gestosc ekranu
            }
        }
        return Math.max(1f, Math.min(3.5f, density));
    }

    @Override
    public void resize(int width, int height) {
        viewport.setUnitsPerPixel(1f / uiScale());
        viewport.update(width, height, true);
        float w = viewport.getWorldWidth(), h = viewport.getWorldHeight();
        contentW = Math.min(1100, w - 2 * 16);
        contentX = (w - contentW) / 2f;
        float label = 18, line = 20, title = 30, buttonH = 44;
        // przyciski na dole (cele dotyku >= 44 dp)
        float bw = Math.min(260, contentW / 2f - GAP);
        create.set(contentX + contentW - bw, GAP, bw, buttonH);
        back.set(contentX, GAP, Math.min(140, bw), buttonH);
        float top = h - title, bottom = GAP + buttonH + GAP;
        float free = top - bottom - 2 * label - 2 * line - 2 * GAP;
        float vocH = Math.max(56, Math.min(190, free * 0.4f));
        playstyleInCard = vocH >= 140;
        if (playstyleInCard) free += line; // opis profesji na karcie, nie pod nia
        float townH = Math.max(80, Math.min(260, free - vocH));
        int nt = Math.max(1, towns.towns().size());
        float townCardW = (contentW - GAP * (nt - 1)) / nt;
        townH = Math.max(80, Math.min(townH, townCardW + 44)); // podglad kwadratowy, bez pustego miejsca pod nim
        int n = vocationCards.length;
        float vw = (contentW - GAP * (n - 1)) / n;
        float used = 2 * label + vocH + (playstyleInCard ? 0 : line) + GAP + townH + line;
        float y = top - Math.max(0, (top - bottom - used) / 2f) - label; // nadmiar miejsca (komputer): blok na srodku
        vocLabelY = y + label / 2f;
        y -= vocH;
        for (int i = 0; i < n; i++) vocationCards[i].set(contentX + i * (vw + GAP), y, vw, vocH);
        if (!playstyleInCard) {
            playstyleY = y - line / 2f;
            y -= line;
        }
        y -= GAP + label;
        townLabelY = y + label / 2f;
        y -= townH + line;
        towns.layout(contentX, y, contentW, townH, line);
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
            Rectangle r = vocationCards[i];
            boolean sel = all[i] == vocation;
            if (sel) {
                shapes.setColor(0.85f, 0.68f, 0.2f, 1f);
                shapes.rect(r.x - 2, r.y - 2, r.width + 4, r.height + 4);
            }
            shapes.setColor(sel ? 0.3f : 0.16f, sel ? 0.25f : 0.15f, sel ? 0.14f : 0.2f, 1f);
            shapes.rect(r.x, r.y, r.width, r.height);
        }
        towns.drawShapes(shapes);
        shapes.setColor(0.15f, 0.4f, 0.25f, 1f);
        shapes.rect(create.x, create.y, create.width, create.height);
        shapes.setColor(0.25f, 0.2f, 0.2f, 1f);
        shapes.rect(back.x, back.y, back.width, back.height);
        shapes.end();

        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        centered(batch, "PIXEL - nowa postać", w / 2f, h - 15, Color.WHITE);
        label(batch, "Profesja", vocLabelY);
        for (int i = 0; i < all.length; i++) {
            Vocation v = all[i];
            Rectangle r = vocationCards[i];
            float s = Math.min(64, Math.min(r.width * 0.4f, r.height - 26));
            if (playstyleInCard) {
                centered(batch, (i + 1) + ". " + v.displayName(), r.x + r.width / 2f, r.y + r.height - 14, v == vocation ? Color.GOLD : Color.WHITE);
                batch.draw(game.art().character(v, Direction.SOUTH), r.x + r.width / 2f - s / 2f, r.y + r.height - 30 - s, s, s);
                game.font().setColor(Color.LIGHT_GRAY);
                game.font().draw(batch, v.playstyle(), r.x + 8, r.y + r.height - 38 - s, r.width - 16, Align.left, true);
                game.font().setColor(Color.WHITE);
            } else {
                batch.draw(game.art().character(v, Direction.SOUTH), r.x + r.width / 2f - s / 2f, r.y + r.height - 3 - s, s, s);
                centered(batch, v.displayName(), r.x + r.width / 2f, r.y + 11, v == vocation ? Color.GOLD : Color.WHITE);
            }
        }
        if (!playstyleInCard) {
            game.font().setColor(Color.LIGHT_GRAY);
            game.font().draw(batch, vocation.displayName() + ": " + vocation.playstyle(), contentX, playstyleY + 6, contentW, Align.center, false);
            game.font().setColor(Color.WHITE);
        }
        label(batch, "Miasto startowe - tu zamieszkasz i zaczniesz w świątyni", townLabelY);
        towns.drawText(batch, game.font(), contentX, contentW);
        centered(batch, "Utwórz postać", create.x + create.width / 2f, create.y + create.height / 2f, Color.WHITE);
        centered(batch, "Wstecz", back.x + back.width / 2f, back.y + back.height / 2f, Color.WHITE);
        String info = !message.isEmpty() ? message
                : "Start: poziom " + Integer.getInteger("pixel.startLevel", game.startingConfig().offlineStartLevel())
                + (towns.selected() != null ? ", " + towns.selected().name() : "");
        float mid = back.x + back.width + GAP, midW = create.x - GAP - mid;
        game.font().setColor(message.isEmpty() ? Color.GRAY : Color.SALMON);
        game.font().draw(batch, info, mid, create.y + create.height / 2f + 6, midW, Align.center, true);
        game.font().setColor(Color.WHITE);
        batch.end();
    }

    private void label(SpriteBatch batch, String s, float cy) {
        game.font().setColor(Color.LIGHT_GRAY);
        layout.setText(game.font(), s);
        game.font().draw(batch, s, contentX, cy + layout.height / 2f);
        game.font().setColor(Color.WHITE);
    }

    private void centered(SpriteBatch batch, String s, float cx, float cy, Color c) {
        game.font().setColor(c);
        layout.setText(game.font(), s);
        game.font().draw(batch, s, cx - layout.width / 2f, cy + layout.height / 2f);
        game.font().setColor(Color.WHITE);
    }

    @Override
    public void hide() {
        towns.dispose(); // ekran nie wraca (Wstecz i nowa postac tworza nowy), wiec podglady mozna zwolnic
    }

    @Override
    public void dispose() {
        towns.dispose();
    }
}
