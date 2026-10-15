package pl.fawizja.pixel.world;

/**
 * Kolory minimapy: jeden piksel na kafelek, w formacie RGBA8888 (jak Pixmap w libGDX).
 * Sciany ciemne, przeszkody przyciemnione, podloze w kolorze kafelka, przejscia (schody, drabiny) zolte.
 * Puste pola sa przezroczyste.
 */
public final class MinimapPalette {

    public static final int TRANSPARENT = 0;
    public static final int WALL = rgba(0x3a, 0x38, 0x3c);
    public static final int TRANSITION = rgba(0xf2, 0xd2, 0x3c);

    private MinimapPalette() {}

    public static int rgba(int r, int g, int b) {
        return (r & 0xff) << 24 | (g & 0xff) << 16 | (b & 0xff) << 8 | 0xff;
    }

    /** Kolor kafelka (x, y) pietra; TRANSPARENT poza mapa i na pustych polach. */
    public static int color(FloorMap floor, TileRegistry tiles, int x, int y) {
        if (!floor.inBounds(x, y)) return TRANSPARENT;
        if (floor.transitionAt(x, y) != null) return TRANSITION;
        if (floor.get(Layer.WALL, x, y) != TileType.EMPTY) return WALL;
        int ground = floor.get(Layer.GROUND, x, y);
        if (ground == TileType.EMPTY) return TRANSPARENT;
        int rgb = parse(tiles.get(ground).color());
        int object = floor.get(Layer.OBJECT, x, y);
        boolean blocked = object != TileType.EMPTY && !tiles.get(object).walkable();
        float k = blocked ? 0.5f : 0.8f;
        return rgba((int) (((rgb >> 16) & 0xff) * k), (int) (((rgb >> 8) & 0xff) * k), (int) ((rgb & 0xff) * k));
    }

    /** Caly obraz pietra, wierszami od y=0 (polnoc u gory). */
    public static int[] paint(FloorMap floor, TileRegistry tiles) {
        int w = floor.width(), h = floor.height();
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) out[y * w + x] = color(floor, tiles, x, y);
        }
        return out;
    }

    static int parse(String hex) {
        if (hex == null) return 0x808080;
        String s = hex.trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            return Integer.parseInt(s, 16) & 0xffffff;
        } catch (NumberFormatException e) {
            return 0x808080;
        }
    }
}
