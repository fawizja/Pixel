package pl.fawizja.pixel.world;

/**
 * Definicja kafelka z data/tiles.csv.
 * Dla podloza "walkable" oznacza, czy mozna na nim stanac; dla obiektow i scian - czy NIE blokuje ruchu.
 */
public record TileType(int id, String key, Layer layer, boolean walkable, int lightRadius,
                       String biome, String color, String pattern) {
    public static final int EMPTY = 0;

    /**
     * Przeszkoda z natury (drzewo, krzak, palma, kaktus, glaz): jak w Tibii mozna sie miedzy dwiema takimi
     * przecisnac po skosie (scinajac rog). Mury, woda, lawa i meble nie - tam skos dalej wymaga wolnych rogow.
     */
    public boolean squeezable() {
        if (layer != Layer.OBJECT || walkable || pattern == null) return false;
        switch (pattern) {
            case "tree": case "pine": case "palm": case "cactus": return true;
            case "bush": return !"potted_plant".equals(key) && !"flower_box".equals(key);
            case "rock": return key.endsWith("rock");
            default: return false;
        }
    }
}
