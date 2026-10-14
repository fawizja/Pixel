package pl.fawizja.pixel.world;

/**
 * Definicja kafelka z data/tiles.csv.
 * Dla podloza "walkable" oznacza, czy mozna na nim stanac; dla obiektow i scian - czy NIE blokuje ruchu.
 */
public record TileType(int id, String key, Layer layer, boolean walkable, int lightRadius,
                       String biome, String color, String pattern) {
    public static final int EMPTY = 0;
}
