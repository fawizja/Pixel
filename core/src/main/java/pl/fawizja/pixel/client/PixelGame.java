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
    private TileArt art;
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
            world = new MapLoader(tiles).load("varden");
            monsters = MonsterRegistry.loadDefault(items);
            spawns = SpawnTable.load("varden", world, monsters);
        } catch (IOException e) {
            throw new IllegalStateException("Nie mozna wczytac mapy", e);
        }
        art = new TileArt(tiles, items, monsters);
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = new BitmapFont();
        font.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        font.setUseIntegerPositions(true);
        codec = new CharacterCodec(items);
        saveStore = new SaveStore(platform.saveDirectory());

        PlayerCharacter loaded = loadCharacter();
        if (loaded != null) {
            startGame(loaded);
        } else {
            setScreen(new VocationSelectScreen(this));
        }
    }

    private PlayerCharacter loadCharacter() {
        try {
            SaveStore.LoadResult<PlayerCharacter> r = saveStore.read(SAVE_SLOT, codec::decode);
            if (r.problem() != null) Gdx.app.error(TAG, "Problem z zapisem: " + r.problem());
            return r.value();
        } catch (IOException e) {
            Gdx.app.error(TAG, "Nie mozna odczytac zapisu", e);
            return null;
        }
    }

    public void createCharacter(String name, Vocation vocation) {
        PlayerCharacter pc = new CharacterFactory(startingConfig, items).create(name, vocation, world.spawn());
        saveCharacter(pc);
        startGame(pc);
    }

    public void startGame(PlayerCharacter pc) {
        Gdx.app.log(TAG, "Start: " + pc.name() + " (" + pc.vocation() + ", lvl " + pc.level() + ")");
        setScreen(new GameScreen(this, pc));
    }

    /** Bezpieczny zapis (plik tymczasowy + kopia zapasowa). Bledy sa logowane, nie przerywaja gry. */
    public boolean saveCharacter(PlayerCharacter pc) {
        try {
            saveStore.write(SAVE_SLOT, codec.encode(pc));
            return true;
        } catch (IOException e) {
            Gdx.app.error(TAG, "Zapis nie powiodl sie", e);
            return false;
        }
    }

    public PlatformServices platform() { return platform; }
    public ItemRegistry items() { return items; }
    public WorldMap world() { return world; }
    public MonsterRegistry monsters() { return monsters; }
    public List<SpawnPoint> spawns() { return spawns; }
    public StartingConfig startingConfig() { return startingConfig; }
    public TileArt art() { return art; }
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
    }
}
