package pl.fawizja.pixel.world;

/**
 * Stale ukladu swiata (maps/world, generator tools/mapgen/generate_continent.py).
 * Region Varden jest kopia mapy maps/varden przesunieta o VARDEN_OFFSET - potrzebne do
 * migracji zapisow z czasow, gdy gra miala tylko mape Varden (format zapisu v2), i do testu zgodnosci.
 */
public final class WorldLayout {
    public static final String WORLD_MAP = "world";
    /** Varden w swiecie 1000x1000 z mapy Darka (od 2026-10-27, zapis v5). */
    public static final int VARDEN_OFFSET_X = 467;
    public static final int VARDEN_OFFSET_Y = 483;
    /** Varden w swiecie 352x320 (zapisy v3). */
    public static final int V3_VARDEN_OFFSET_X = 48;
    public static final int V3_VARDEN_OFFSET_Y = 64;

    private WorldLayout() {}
}
