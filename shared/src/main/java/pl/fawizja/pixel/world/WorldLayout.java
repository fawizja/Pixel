package pl.fawizja.pixel.world;

/**
 * Stale ukladu swiata (maps/world, generator tools/mapgen/generate_world.py).
 * Region Varden jest kopia mapy maps/varden przesunieta o VARDEN_OFFSET - potrzebne do
 * migracji zapisow z czasow, gdy gra miala tylko mape Varden (format zapisu v2).
 */
public final class WorldLayout {
    public static final String WORLD_MAP = "world";
    public static final int VARDEN_OFFSET_X = 48;
    public static final int VARDEN_OFFSET_Y = 64;

    private WorldLayout() {}
}
