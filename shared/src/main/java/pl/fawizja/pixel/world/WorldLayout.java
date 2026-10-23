package pl.fawizja.pixel.world;

/**
 * Stale ukladu swiata (maps/world, generator tools/mapgen/generate_continent.py).
 * Region Varden jest kopia mapy maps/varden przesunieta o VARDEN_OFFSET - potrzebne do
 * migracji zapisow z czasow, gdy gra miala tylko mape Varden (format zapisu v2), i do testu zgodnosci.
 */
public final class WorldLayout {
    public static final String WORLD_MAP = "world";
    /** Varden na kontynencie 448x448 (od 2026-10-23, zapis v4). */
    public static final int VARDEN_OFFSET_X = 139;
    public static final int VARDEN_OFFSET_Y = 340;
    /** Varden w poprzednim swiecie 352x320 (zapisy v3). */
    public static final int V3_VARDEN_OFFSET_X = 48;
    public static final int V3_VARDEN_OFFSET_Y = 64;

    /**
     * Fragmenty swiata v3 przeniesione na kontynent: x0, y0, x1, y1 (stary uklad), przesuniecie dx, dy
     * (BLOCKS w generate_continent.py). Pierwszy (Varden) obejmuje tez podziemia, pozostale powierzchnie i pietra.
     */
    private static final int[][] V3_BLOCKS = {
            {46, 64, 157, 111, 91, 276},     // Varden z dzielnica krolewska
            {60, 4, 100, 47, 40, 92},        // Frostholm
            {98, 112, 159, 176, 220, -18},   // Selvaran
            {111, 177, 159, 181, 220, -18},
            {54, 177, 110, 222, 135, 51},    // Qasirah
            {113, 198, 136, 216, 163, 82},   // piramida
            {219, 195, 241, 215, 81, 75},    // ruiny
            {170, 78, 188, 95, 112, 160},    // oboz
            {12, 101, 32, 111, 146, 83},     // gniazdo wiwern
            {95, 244, 146, 283, 137, 160},   // Wyspa Koralowa
            {184, 242, 197, 256, -136, -198},
            {276, 103, 298, 119, -214, 109},
            {26, 255, 70, 300, -18, 141},    // Wyspa Piratow
            {222, 255, 280, 304, 112, 75},   // Wyspa Mgiel
            {274, 18, 336, 58, 42, -12},     // Lodowa Wyspa
            {279, 168, 337, 212, 109, 40},   // Wyspa Wulkaniczna
    };

    private WorldLayout() {}

    /**
     * Pozycja z zapisu v3 (swiat 352x320) na kontynencie albo null, gdy tego miejsca juz nie ma
     * (dzicz, lochy poza Varden) - postac wraca wtedy do swiatyni.
     */
    public static Position fromV3(int x, int y, int z) {
        for (int i = 0; i < V3_BLOCKS.length; i++) {
            int[] b = V3_BLOCKS[i];
            if (z < 0 && i > 0) break;
            if (x >= b[0] && x <= b[2] && y >= b[1] && y <= b[3]) return new Position(x + b[4], y + b[5], z);
        }
        return null;
    }
}
