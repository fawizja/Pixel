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
    private static WorldMap world;

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

    /** Pelny swiat gry (4 miasta). */
    public static synchronized WorldMap world() {
        if (world == null) {
            try {
                world = new MapLoader(TILES).load("world");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return world;
    }

    public static PlayerCharacter newCharacterNamed(String name) {
        return new CharacterFactory(StartingConfig.loadDefault(), ITEMS).create(name, Vocation.PALADIN, new Position(31, 16, 0));
    }

    public static PlayerCharacter newCharacter(Vocation v) {
        return new CharacterFactory(StartingConfig.loadDefault(), ITEMS).create("Test Hero", v, new Position(31, 16, 0));
    }

    /**
     * Krok w BFS testow dostepnosci: zamkniete drzwi (nie poziomu ponad sily postaci) gracz otwiera dotknieciem
     * i przechodzi - jak w grze, gdzie wejscie w drzwi ich nie otwiera.
     */
    public static pl.fawizja.pixel.movement.MoveResult stepOpeningDoors(WorldMap w, pl.fawizja.pixel.movement.MovementSystem ms,
                                                                       PlayerCharacter pc, pl.fawizja.pixel.world.Direction d) {
        Position to = pc.position().translate(d.dx, d.dy);
        if (!d.diagonal() && w.openings().isClosedDoor(to) && w.requiredLevelAt(to) <= pc.level()) {
            w.openings().set(to, true);
            try {
                return ms.step(pc, d);
            } finally {
                w.openings().set(to, false);
            }
        }
        return ms.step(pc, d);
    }
}
