package pl.fawizja.pixel.client.screen;

import pl.fawizja.pixel.i18n.Messages;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import pl.fawizja.pixel.client.PixelGame;
import pl.fawizja.pixel.net.Protocol;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Direction;

/**
 * Logowanie na serwer: adres, imie, haslo i profesja (uzywana tylko przy zakladaniu konta).
 * Pola obsluguja klawiature fizyczna i ekranowa. Adres i imie sa zapamietywane, haslo nigdy.
 */
public final class LoginScreen extends ScreenAdapter {

    private static final int SERVER = 0, NAME = 1, PASSWORD = 2;
    private static final String[] LABELS = {"Serwer (adres:port)", "Imię postaci", "Hasło (min. 6 znaków)"};
    private static final int[] MAX_LEN = {60, 20, 64};

    private final PixelGame game;
    private final ScreenViewport viewport = new ScreenViewport();
    private final GlyphLayout layout = new GlyphLayout();
    private final Vector2 tmp = new Vector2();
    private final StringBuilder[] values = {new StringBuilder(), new StringBuilder(), new StringBuilder()};
    private final Rectangle[] fields = {new Rectangle(), new Rectangle(), new Rectangle()};
    private final Rectangle[] vocations = new Rectangle[Vocation.values().length];
    private final Rectangle connect = new Rectangle();
    private final Rectangle back = new Rectangle();
    private final Preferences prefs;
    private int focus = SERVER;
    private Vocation vocation = Vocation.PALADIN;
    private volatile boolean connecting;
    private String message = "";
    private boolean certificateChanged;
    private final Rectangle forget = new Rectangle();
    /** Miasto startowe nowego konta (jak profesja: istniejaca postac je pomija). */
    private final java.util.List<pl.fawizja.pixel.character.StartingTowns.Info> towns;
    private int town;
    private final Rectangle townPrev = new Rectangle();
    private final Rectangle townNext = new Rectangle();
    private final Rectangle townBox = new Rectangle();
    private final pl.fawizja.pixel.client.ui.LanguageBar languageBar = new pl.fawizja.pixel.client.ui.LanguageBar();

    public LoginScreen(PixelGame game) {
        this.game = game;
        for (int i = 0; i < vocations.length; i++) vocations[i] = new Rectangle();
        prefs = Gdx.app.getPreferences("pixel-login");
        String defaultServer = game.platform().serverAddress();
        values[SERVER].append(prefs.getString("server",
                defaultServer != null ? defaultServer : "127.0.0.1:" + Protocol.DEFAULT_PORT));
        values[NAME].append(prefs.getString("name", ""));
        try {
            vocation = Vocation.valueOf(prefs.getString("vocation", "PALADIN"));
        } catch (IllegalArgumentException ignored) {
            // zostaje domyslna
        }
        if (values[NAME].length() > 0) focus = PASSWORD;
        towns = pl.fawizja.pixel.character.StartingTowns.list(game.world());
        String saved = prefs.getString("town", game.defaultStartTown());
        for (int i = 0; i < towns.size(); i++) if (towns.get(i).key().equalsIgnoreCase(saved)) town = i;
    }

    private String townKey() { return towns.isEmpty() ? null : towns.get(town).key(); }

    private void cycleTown(int step) {
        if (!towns.isEmpty()) town = Math.floorMod(town + step, towns.size());
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int sx, int sy, int pointer, int button) {
                Vector2 p = viewport.unproject(tmp.set(sx, sy));
                pl.fawizja.pixel.i18n.Language picked = languageBar.hit(p);
                if (picked != null) {
                    game.setLanguage(picked);
                    return true;
                }
                for (int i = 0; i < fields.length; i++) {
                    if (fields[i].contains(p)) {
                        focus = i;
                        Gdx.input.setOnscreenKeyboardVisible(true);
                        return true;
                    }
                }
                for (int i = 0; i < vocations.length; i++) {
                    if (vocations[i].contains(p)) vocation = Vocation.values()[i];
                }
                if (townPrev.contains(p)) cycleTown(-1);
                else if (townNext.contains(p) || townBox.contains(p)) cycleTown(1);
                if (certificateChanged && forget.contains(p)) {
                    prefs.remove("pin." + values[SERVER].toString().trim());
                    prefs.flush();
                    certificateChanged = false;
                    message = Messages.get("login_screen.zapomniano_certyfikat_nastepne");
                    return true;
                }
                if (connect.contains(p)) connect();
                if (back.contains(p)) game.backToMenu(null);
                return true;
            }

            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.TAB || keycode == Input.Keys.DOWN) focus = (focus + 1) % fields.length;
                else if (keycode == Input.Keys.UP) focus = (focus + fields.length - 1) % fields.length;
                else if (keycode == Input.Keys.ENTER) {
                    if (focus < PASSWORD) focus++;
                    else connect();
                } else if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) game.backToMenu(null);
                else return false;
                return true;
            }

            @Override
            public boolean keyTyped(char c) {
                StringBuilder v = values[focus];
                if (c == '\b') {
                    if (v.length() > 0) v.setLength(v.length() - 1);
                } else if (c >= 32 && c != 127 && c != '\t' && v.length() < MAX_LEN[focus]) {
                    v.append(c);
                }
                return true;
            }
        });
    }

    private void connect() {
        if (connecting) return;
        String server = values[SERVER].toString().trim();
        String name = values[NAME].toString().trim();
        String password = values[PASSWORD].toString();
        if (name.length() < 3) {
            message = Messages.get("login_screen.podaj_imie_3_20");
            focus = NAME;
            return;
        }
        if (password.length() < 6) {
            message = Messages.get("login_screen.haslo_musi_miec_co");
            focus = PASSWORD;
            return;
        }
        connecting = true;
        message = Messages.get("login_screen.laczenie_z") + server + "...";
        Gdx.input.setOnscreenKeyboardVisible(false);
        Vocation voc = vocation;
        String startTown = townKey();
        String pinKey = "pin." + server;
        String pinned = prefs.getString(pinKey, null);
        Thread t = new Thread(() -> {
            PixelGame.OnlineLogin login = game.connectOnline(server, name, voc.name(), password, pinned, startTown);
            Gdx.app.postRunnable(() -> {
                connecting = false;
                if (login.problem() != null) {
                    message = login.problem();
                    certificateChanged = login.certificateChanged();
                    return;
                }
                if (pinned == null && login.fingerprint() != null) {
                    prefs.putString(pinKey, login.fingerprint()); // pierwsze polaczenie: zapamietaj certyfikat
                    Gdx.app.log("Pixel", Messages.get("login_screen.zapamietano_certyfikat_serwera") + server + ": " + login.fingerprint());
                }
                prefs.putString("server", server);
                prefs.putString("name", login.character().name());
                prefs.putString("vocation", voc.name());
                if (startTown != null) prefs.putString("town", startTown);
                prefs.flush();
                game.startOnline(login);
            });
        }, "pixel-login");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void resize(int width, int height) {
        viewport.setUnitsPerPixel(1f / VocationSelectScreen.uiScale());
        viewport.update(width, height, true);
        float w = viewport.getWorldWidth(), h = viewport.getWorldHeight();
        // niski ekran (telefon poziomo): pola po lewej, profesja, miasto i przyciski po prawej
        boolean twoColumns = h < 470 && w >= 640;
        float fw = twoColumns ? Math.min(380, (w - 48) / 2f) : Math.min(360, w - 32), fh = twoColumns ? 36 : 30;
        float x = twoColumns ? w / 2f - 8 - fw : (w - fw) / 2f;
        languageBar.layout(w / 2f, h - 52, Math.min(360, w - 32));
        float y = h - 98;
        for (int i = 0; i < fields.length; i++) {
            fields[i].set(x, y - fh, fw, fh);
            y -= fh + 26;
        }
        if (twoColumns) {
            x = w / 2f + 8;
            y = h - 98;
        }
        float vw = (fw - (vocations.length - 1) * 8) / vocations.length;
        for (int i = 0; i < vocations.length; i++) vocations[i].set(x + i * (vw + 8), y - 40, vw, 40);
        y -= 40 + 26;
        townPrev.set(x, y - 44, 48, 44);
        townNext.set(x + fw - 48, y - 44, 48, 44);
        townBox.set(x + 56, y - 44, fw - 112, 44);
        y -= 44 + 44;
        connect.set(x + fw / 2f + 4, y - 40, fw / 2f - 4, 40);
        back.set(x, y - 40, fw / 2f - 4, 40);
        forget.set(x, y - 40 - 70, fw, 34);
    }

    /** Zrzut ekranu (-Dpixel.screenshot, regresja GUI): kursor nie miga, wiec obraz nie zalezy od chwili zrzutu. */
    private static final boolean STEADY_CARET = System.getProperty("pixel.screenshot") != null;

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.07f, 0.08f, 0.1f, 1f);
        viewport.apply();
        float w = viewport.getWorldWidth(), h = viewport.getWorldHeight();
        boolean caret = STEADY_CARET || (TimeUtils.millis() / 500) % 2 == 0;
        ShapeRenderer shapes = game.shapes();
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        languageBar.drawShapes(shapes, Messages.language());
        for (int i = 0; i < fields.length; i++) {
            Rectangle r = fields[i];
            shapes.setColor(i == focus ? 0.3f : 0.18f, i == focus ? 0.28f : 0.17f, i == focus ? 0.38f : 0.22f, 1f);
            shapes.rect(r.x, r.y, r.width, r.height);
        }
        for (int i = 0; i < vocations.length; i++) {
            Rectangle r = vocations[i];
            boolean sel = Vocation.values()[i] == vocation;
            shapes.setColor(sel ? 0.45f : 0.18f, sel ? 0.36f : 0.17f, sel ? 0.12f : 0.22f, 1f);
            shapes.rect(r.x, r.y, r.width, r.height);
        }
        if (!towns.isEmpty()) {
            shapes.setColor(0.25f, 0.22f, 0.3f, 1f);
            shapes.rect(townPrev.x, townPrev.y, townPrev.width, townPrev.height);
            shapes.rect(townNext.x, townNext.y, townNext.width, townNext.height);
            shapes.setColor(0.45f, 0.36f, 0.12f, 1f);
            shapes.rect(townBox.x, townBox.y, townBox.width, townBox.height);
        }
        shapes.setColor(connecting ? 0.2f : 0.15f, connecting ? 0.2f : 0.4f, connecting ? 0.2f : 0.25f, 1f);
        shapes.rect(connect.x, connect.y, connect.width, connect.height);
        shapes.setColor(0.25f, 0.2f, 0.2f, 1f);
        shapes.rect(back.x, back.y, back.width, back.height);
        if (certificateChanged) {
            shapes.setColor(0.45f, 0.15f, 0.12f, 1f);
            shapes.rect(forget.x, forget.y, forget.width, forget.height);
        }
        shapes.end();

        SpriteBatch batch = game.batch();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        centered(batch, Messages.get("login.tytul"), w / 2f, h - 12, Color.GOLD);
        languageBar.drawText(batch, game.font(), Messages.language());
        for (int i = 0; i < fields.length; i++) {
            Rectangle r = fields[i];
            game.font().setColor(Color.LIGHT_GRAY);
            game.font().draw(batch, Messages.tr(LABELS[i]), r.x, r.y + r.height + 16);
            String text = i == PASSWORD ? "*".repeat(values[i].length()) : values[i].toString();
            if (i == focus && caret) text += "_";
            game.font().setColor(Color.WHITE);
            game.font().draw(batch, text, r.x + 8, r.y + r.height / 2f + 6);
        }
        Rectangle first = vocations[0];
        game.font().setColor(Color.LIGHT_GRAY);
        boolean compact = first.width < 110; // waskie przyciski (telefon): sama postac, nazwa w podpisie
        game.font().draw(batch, Messages.get("login_screen.profesja_tylko_dla_nowego") + (compact ? ": " + vocation.displayName() : ""),
                first.x, first.y + first.height + 16);
        for (int i = 0; i < vocations.length; i++) {
            Rectangle r = vocations[i];
            Vocation v = Vocation.values()[i];
            if (compact) {
                batch.draw(game.art().character(v, Direction.SOUTH), r.x + r.width / 2f - 17, r.y + 3, 34, 34);
                continue;
            }
            batch.draw(game.art().character(v, Direction.SOUTH), r.x + 4, r.y + 3, 32, 32);
            game.font().setColor(Color.WHITE);
            game.font().draw(batch, v.displayName(), r.x + 38, r.y + r.height / 2f + 6);
        }
        if (!towns.isEmpty()) {
            pl.fawizja.pixel.character.StartingTowns.Info t = towns.get(town);
            game.font().setColor(Color.LIGHT_GRAY);
            game.font().draw(batch, Messages.get("login_screen.miasto_startowe_tylko_dla"), townPrev.x, townPrev.y + townPrev.height + 16);
            centered(batch, "<", townPrev.x + townPrev.width / 2f, townPrev.y + townPrev.height / 2f, Color.WHITE);
            centered(batch, ">", townNext.x + townNext.width / 2f, townNext.y + townNext.height / 2f, Color.WHITE);
            centered(batch, t.name(), townBox.x + townBox.width / 2f, townBox.y + townBox.height - 13, Color.GOLD);
            centered(batch, t.climate(), townBox.x + townBox.width / 2f, townBox.y + 12, Color.LIGHT_GRAY);
            game.font().setColor(Color.GRAY);
            game.font().draw(batch, t.description(), townPrev.x, townPrev.y - 6, townNext.x + townNext.width - townPrev.x,
                    com.badlogic.gdx.utils.Align.left, true);
            game.font().setColor(Color.WHITE);
        }
        centered(batch, connecting ? Messages.get("login_screen.laczenie") : Messages.get("login_screen.polacz"), connect.x + connect.width / 2f, connect.y + connect.height / 2f, Color.WHITE);
        centered(batch, Messages.get("game_screen.wstecz"), back.x + back.width / 2f, back.y + back.height / 2f, Color.WHITE);
        if (!message.isEmpty()) centered(batch, message, w / 2f, back.y - 20, Color.SALMON);
        if (certificateChanged) {
            centered(batch, Messages.get("login_screen.zapomnij_certyfikat_gdy_serwer"),
                    forget.x + forget.width / 2f, forget.y + forget.height / 2f, Color.WHITE);
        }
        centered(batch, Messages.get("login_screen.pierwsze_logowanie_nowym_imien"),
                w / 2f, 14, Color.GRAY);
        batch.end();
    }

    private void centered(SpriteBatch batch, String s, float cx, float cy, Color c) {
        game.font().setColor(c);
        layout.setText(game.font(), s);
        game.font().draw(batch, s, cx - layout.width / 2f, cy + layout.height / 2f);
        game.font().setColor(Color.WHITE);
    }
}
