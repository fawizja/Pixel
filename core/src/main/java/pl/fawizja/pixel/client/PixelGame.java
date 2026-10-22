package pl.fawizja.pixel.client;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import java.io.IOException;
import java.util.List;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.CharacterFactory;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.client.platform.DefaultPlatformServices;
import pl.fawizja.pixel.client.platform.PlatformServices;
import pl.fawizja.pixel.client.render.TileArt;
import pl.fawizja.pixel.client.screen.GameScreen;
import pl.fawizja.pixel.client.screen.VocationSelectScreen;
import pl.fawizja.pixel.config.StartingConfig;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.monster.SpawnTable;
import pl.fawizja.pixel.save.SaveStore;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.MapLoader;
import pl.fawizja.pixel.world.TileRegistry;
import pl.fawizja.pixel.world.WorldLayout;
import pl.fawizja.pixel.world.WorldMap;

/** Glowna klasa gry wspolna dla Androida i desktopu. */
public class PixelGame extends Game {

    public static final String SAVE_SLOT = "character";
    private static final String TAG = "Pixel";

    private final PlatformServices platform;
    private ItemRegistry items;
    private TileRegistry tiles;
    private StartingConfig startingConfig;
    private WorldMap world;
    private MonsterRegistry monsters;
    private List<SpawnPoint> spawns;
    private pl.fawizja.pixel.npc.ShopRegistry shops;
    private pl.fawizja.pixel.npc.NpcRegistry npcs;
    private pl.fawizja.pixel.quest.QuestRegistry quests;
    private pl.fawizja.pixel.treasure.TreasureChests chests;
    private TileArt art;
    private pl.fawizja.pixel.client.audio.SoundPlayer sound;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private SaveStore saveStore;
    private CharacterCodec codec;

    public PixelGame() {
        this(new DefaultPlatformServices());
    }

    public PixelGame(PlatformServices platform) {
        this.platform = platform;
    }

    @Override
    public void create() {
        items = ItemRegistry.loadDefault();
        tiles = TileRegistry.loadDefault();
        startingConfig = StartingConfig.loadDefault();
        try {
            world = new MapLoader(tiles).load(WorldLayout.WORLD_MAP);
            monsters = MonsterRegistry.loadDefault(items);
            spawns = SpawnTable.load(WorldLayout.WORLD_MAP, world, monsters);
            shops = pl.fawizja.pixel.npc.ShopRegistry.loadDefault(items);
            npcs = pl.fawizja.pixel.npc.NpcRegistry.load(WorldLayout.WORLD_MAP, world, shops);
            quests = pl.fawizja.pixel.quest.QuestRegistry.loadDefault(items, monsters);
            chests = pl.fawizja.pixel.treasure.TreasureChests.generate(world);
        } catch (IOException e) {
            throw new IllegalStateException("Nie można wczytać mapy", e);
        }
        art = new TileArt(tiles, items, monsters, npcs.all());
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        try {
            font = pl.fawizja.pixel.client.ui.PolishFont.create();
        } catch (RuntimeException e) {
            Gdx.app.error("Pixel", "Czcionka bez polskich liter: " + e.getMessage());
            font = new BitmapFont();
        }
        sound = new pl.fawizja.pixel.client.audio.SoundPlayer();
        font.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        font.setUseIntegerPositions(true);
        codec = new CharacterCodec(items);
        saveStore = new SaveStore(platform.saveDirectory());

        setScreen(new pl.fawizja.pixel.client.screen.MenuScreen(this, null));
    }

    /** Gra lokalna: kontynuacja zapisanej postaci albo wybor profesji nowej. */
    public void playLocal() {
        PlayerCharacter loaded = loadCharacter();
        if (loaded != null) {
            startGame(loaded);
        } else {
            setScreen(new VocationSelectScreen(this));
        }
    }

    /** Krotki opis zapisanej postaci lokalnej (do menu) albo null. */
    public String localSaveSummary() {
        PlayerCharacter pc = loadCharacter();
        return pc == null ? null : pc.name() + ", " + pc.vocation().displayName() + ", poziom " + pc.level();
    }

    /** Powrot do menu (np. po rozlaczeniu z serwerem); poprzedni ekran jest zwalniany. */
    public void backToMenu(String message) {
        com.badlogic.gdx.Screen old = getScreen();
        setScreen(new pl.fawizja.pixel.client.screen.MenuScreen(this, message));
        if (old != null) old.dispose();
    }

    private PlayerCharacter loadCharacter() {
        try {
            SaveStore.LoadResult<PlayerCharacter> r = saveStore.read(SAVE_SLOT, codec::decode);
            if (r.problem() != null) Gdx.app.error(TAG, "Problem z zapisem: " + r.problem());
            return r.value();
        } catch (IOException e) {
            Gdx.app.error(TAG, "Nie można odczytać zapisu", e);
            return null;
        }
    }

    public pl.fawizja.pixel.npc.ShopRegistry shops() { return shops; }
    public pl.fawizja.pixel.npc.NpcRegistry npcs() { return npcs; }
    public pl.fawizja.pixel.quest.QuestRegistry quests() { return quests; }
    public pl.fawizja.pixel.treasure.TreasureChests chests() { return chests; }

    public void createCharacter(String name, Vocation vocation) {
        // gra offline: postac od razu na poziomie start.level.offline (-Dpixel.startLevel nadpisuje, np. regresja GUI)
        int level = Integer.getInteger("pixel.startLevel", startingConfig.offlineStartLevel());
        PlayerCharacter pc = new CharacterFactory(startingConfig, items).create(name, vocation, world.templeOf(startingConfig.startTown()), level);
        saveCharacter(pc);
        startGame(pc);
    }

    public void startGame(PlayerCharacter pc) {
        Gdx.app.log(TAG, "Start: " + pc.name() + " (" + pc.vocation() + ", lvl " + pc.level() + ")");
        setScreen(new GameScreen(this, pc));
    }

    /** Wynik logowania na serwer: polaczenie i postac z serwera albo opis problemu. */
    public record OnlineLogin(pl.fawizja.pixel.net.NetClient net, PlayerCharacter character, String problem,
                              String fingerprint, boolean certificateChanged) {
        static OnlineLogin failed(String problem) { return new OnlineLogin(null, null, problem, null, false); }
    }

    /**
     * Laczy z serwerem, loguje (imie, profesja dla nowego konta, haslo) i czeka na zapis postaci -
     * serwer jest jego wlascicielem. Blokuje do kilku sekund: wywolywac poza watkiem grafiki.
     */
    public OnlineLogin connectOnline(String address, String name, String vocation, String password) {
        return connectOnline(address, name, vocation, password, null);
    }

    /**
     * Jak wyzej, z przypietym odciskiem certyfikatu TLS (null = pierwsze polaczenie z tym serwerem:
     * odcisk zostanie przyjety i zwrocony do zapamietania). Inny odcisk niz przypiety = odmowa.
     */
    public OnlineLogin connectOnline(String address, String name, String vocation, String password, String pinned) {
        int colon = address.lastIndexOf(':');
        String host = (colon > 0 ? address.substring(0, colon) : address).trim();
        int port = pl.fawizja.pixel.net.Protocol.DEFAULT_PORT;
        try {
            if (colon > 0) port = Integer.parseInt(address.substring(colon + 1).trim());
        } catch (NumberFormatException e) {
            return OnlineLogin.failed("Zły port w adresie serwera.");
        }
        if (host.isEmpty()) return OnlineLogin.failed("Podaj adres serwera.");
        pl.fawizja.pixel.net.NetClient net;
        boolean[] changed = new boolean[1];
        try {
            net = new pl.fawizja.pixel.net.NetClient(host, port, 4000, fp -> {
                changed[0] = pinned != null && !pinned.equals(fp);
                return !changed[0];
            });
        } catch (IOException e) {
            if (changed[0]) {
                return new OnlineLogin(null, null, "Certyfikat serwera się zmienił - możliwe podszycie! Hasło nie zostało wysłane.",
                        null, true);
            }
            return OnlineLogin.failed("Brak połączenia z " + host + ":" + port + " (szyfrowane TLS).");
        }
        net.hello(name.trim(), vocation, password);
        long deadline = System.currentTimeMillis() + 6000;
        while (net.selfSave() == null && net.error() == null && !net.isClosed() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (net.selfSave() == null) {
            String err = net.error() != null ? net.error() : "Serwer nie odpowiada.";
            net.close();
            return OnlineLogin.failed(err);
        }
        try {
            return new OnlineLogin(net, codec.decode(net.selfSave()), null, net.serverFingerprint(), false);
        } catch (Exception e) {
            net.close();
            return OnlineLogin.failed("Nieczytelny stan postaci z serwera.");
        }
    }

    /** Start gry online po udanym logowaniu (watek grafiki). */
    public void startOnline(OnlineLogin login) {
        Gdx.app.log(TAG, "Online jako " + login.character().name() + " (id " + login.net().playerId() + ")");
        com.badlogic.gdx.Screen old = getScreen();
        setScreen(new GameScreen(this, login.character(), login.net()));
        if (old != null && !(old instanceof GameScreen)) old.dispose();
    }

    /** Bezpieczny zapis (plik tymczasowy + kopia zapasowa). Bledy sa logowane, nie przerywaja gry. */
    public boolean saveCharacter(PlayerCharacter pc) {
        try {
            saveStore.write(SAVE_SLOT, codec.encode(pc));
            return true;
        } catch (IOException e) {
            Gdx.app.error(TAG, "Zapis nie powiódł się", e);
            return false;
        }
    }

    public PlatformServices platform() { return platform; }
    public CharacterCodec codec() { return codec; }
    public ItemRegistry items() { return items; }
    public WorldMap world() { return world; }
    public MonsterRegistry monsters() { return monsters; }
    public List<SpawnPoint> spawns() { return spawns; }
    public StartingConfig startingConfig() { return startingConfig; }
    public TileArt art() { return art; }
    public pl.fawizja.pixel.client.audio.SoundPlayer sound() { return sound; }
    public SpriteBatch batch() { return batch; }
    public ShapeRenderer shapes() { return shapes; }
    public BitmapFont font() { return font; }

    @Override
    public void dispose() {
        super.dispose();
        if (getScreen() != null) getScreen().dispose();
        if (art != null) art.dispose();
        if (batch != null) batch.dispose();
        if (shapes != null) shapes.dispose();
        if (font != null) font.dispose();
        if (sound != null) sound.dispose();
    }
}
