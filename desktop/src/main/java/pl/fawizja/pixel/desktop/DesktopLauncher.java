package pl.fawizja.pixel.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import pl.fawizja.pixel.client.PixelGame;
import pl.fawizja.pixel.client.screen.GameScreen;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

/**
 * Uruchamianie na PC: ./gradlew :desktop:run
 *
 * Opcje deweloperskie (wlasciwosci systemowe, przydatne w CI i do zrzutow ekranu):
 *   -Dpixel.autostart=Imie:PALADIN    pomija menu: gra lokalna (tworzy postac, jesli nie ma zapisu)
 *   -Dpixel.server=host:port          z autostart: logowanie na serwer (haslo -Dpixel.password, domyslnie haslo-dev)
 *                                     bez autostart: domyslny adres w ekranie logowania
 *   -Dpixel.teleport=x,y,z            ustawia postac na pozycji (np. 8,23,-1)
 *   -Dpixel.screenshot=plik.png       zapisuje zrzut ekranu po pixel.frames klatkach i konczy
 *   -Dpixel.frames=120
 *   -Dpixel.hour=22                   godzina gry na starcie sesji (np. noc do zrzutow ekranu)
 *   -Dpixel.open=book|bag|character|npc|diag   otwiera ksiege, plecak, karte postaci, rozmowe z NPC albo diagnostyke
 *   -Dpixel.seed=1                    stale ziarno potworow (testy regresji GUI)
 *   -Dpixel.fx=1                      tuz przed zrzutem pokazuje efekty: pocisk, wybuch ognia, mrozna fale, leczenie
 */
public final class DesktopLauncher {

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Pixel");
        config.setWindowedMode(Integer.getInteger("pixel.width", 1280), Integer.getInteger("pixel.height", 720));
        config.useVsync(true);
        config.setForegroundFPS(60);
        config.setIdleFPS(10);
        new Lwjgl3Application(new DevPixelGame(), config);
    }

    private static final class DevPixelGame extends PixelGame {
        private final String autostart = System.getProperty("pixel.autostart");
        private final String teleport = System.getProperty("pixel.teleport");
        private final String screenshot = System.getProperty("pixel.screenshot");
        private final int frames = Integer.getInteger("pixel.frames", 120);
        private final Integer hour = Integer.getInteger("pixel.hour");
        private boolean clockSet;
        private int frame;
        private boolean teleported;

        @Override
        public void create() {
            super.create();
            if (autostart == null) {
                if ("login".equals(System.getProperty("pixel.open"))) {
                    setScreen(new pl.fawizja.pixel.client.screen.LoginScreen(this));
                }
                return;
            }
            String[] p = autostart.split(":");
            Vocation v = Vocation.valueOf(p.length > 1 ? p[1] : "PALADIN");
            String server = platform().serverAddress();
            if (server != null) {
                OnlineLogin login = connectOnline(server, p[0], v.name(), System.getProperty("pixel.password", "haslo-dev"));
                if (login.problem() == null) {
                    startOnline(login);
                    return;
                }
                Gdx.app.error("Pixel", "Online niedostepne: " + login.problem());
            }
            if (localSaveSummary() != null) playLocal();
            else createCharacter(p[0], v);
        }

        @Override
        public void render() {
            if (!clockSet && hour != null && getScreen() instanceof GameScreen) {
                clockSet = true;
                ((GameScreen) getScreen()).session().setClock(
                        new pl.fawizja.pixel.time.WorldClock(-hour * 150_000L, 60L * 60L * 1000L, 0.38f));
            }
            if (!teleported && teleport != null && getScreen() instanceof GameScreen) {
                String[] p = teleport.split(",");
                ((GameScreen) getScreen()).session().teleport(
                        new Position(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim())), 0L);
                teleported = true;
                String open = System.getProperty("pixel.open");
                if ("book".equals(open)) ((GameScreen) getScreen()).hud().toggleBook();
                if ("bag".equals(open)) ((GameScreen) getScreen()).hud().toggleBag();
                if ("character".equals(open)) ((GameScreen) getScreen()).hud().toggleCharacter();
                if ("diag".equals(open)) ((GameScreen) getScreen()).hud().toggleDiagnostics();
                if ("npc".equals(open)) {
                    GameScreen gs = (GameScreen) getScreen();
                    gs.hud().openNpc(gs.session());
                }
            }
            if (Boolean.getBoolean("pixel.fx") && frame == frames - 8 && getScreen() instanceof GameScreen) {
                GameScreen gs = (GameScreen) getScreen();
                long now = gs.worldTime();
                Position me = gs.session().character().position();
                pl.fawizja.pixel.combat.CombatSystem cs = gs.session().combat();
                cs.addEvent(pl.fawizja.pixel.combat.CombatEvent.projectile(now - 120, me, me.translate(5, -3), "ENERGY"));
                cs.addEvent(pl.fawizja.pixel.combat.CombatEvent.area(now - 100, me.translate(-4, 2), 2, "FIRE"));
                cs.addEvent(pl.fawizja.pixel.combat.CombatEvent.area(now - 220, me.translate(4, 3), 1, "ICE"));
                cs.addEvent(pl.fawizja.pixel.combat.CombatEvent.area(now - 150, me, 0, "HEAL"));
            }
            super.render();
            if (screenshot != null && ++frame == frames) {
                Pixmap pm = Pixmap.createFromFrameBuffer(0, 0,
                        Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
                PixmapIO.writePNG(Gdx.files.absolute(screenshot), pm, -1, true);
                pm.dispose();
                Gdx.app.log("Pixel", "Screenshot saved: " + screenshot);
                Gdx.app.exit();
            }
        }
    }
}
