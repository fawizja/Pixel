package pl.fawizja.pixel;

import pl.fawizja.pixel.character.CharacterFactory;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.config.StartingConfig;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.MapLoader;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.TileRegistry;
import pl.fawizja.pixel.world.WorldMap;

/** Wspolne dane testowe ladowane raz. */
public final class TestData {
    public static final ItemRegistry ITEMS = ItemRegistry.loadDefault();
    public static final TileRegistry TILES = TileRegistry.loadDefault();
    private static WorldMap varden;

    private TestData() {}

    public static synchronized WorldMap varden() {
        if (varden == null) {
            try {
                varden = new MapLoader(TILES).load("varden");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return varden;
    }

    public static PlayerCharacter newCharacterNamed(String name) {
        return new CharacterFactory(StartingConfig.loadDefault(), ITEMS).create(name, Vocation.PALADIN, new Position(31, 16, 0));
    }

    public static PlayerCharacter newCharacter(Vocation v) {
        return new CharacterFactory(StartingConfig.loadDefault(), ITEMS).create("Test Hero", v, new Position(31, 16, 0));
    }
}
