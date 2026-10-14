package pl.fawizja.pixel.magic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Receptura craftingu (data/recipes.csv): skladniki (id -> ilosc), wynik, koszt w gp, minimalny poziom. */
public final class Recipe {
    private final String key;
    private final int resultItemId;
    private final int resultCount;
    private final Map<Integer, Integer> ingredients;
    private final long goldCost;
    private final int minLevel;
    private final String description;

    public Recipe(String key, int resultItemId, int resultCount, Map<Integer, Integer> ingredients, long goldCost,
                  int minLevel, String description) {
        if (resultCount <= 0 || ingredients.isEmpty() || goldCost < 0 || minLevel < 1) {
            throw new IllegalArgumentException("bad recipe " + key);
        }
        for (int c : ingredients.values()) if (c <= 0) throw new IllegalArgumentException("bad ingredient count in " + key);
        this.key = key;
        this.resultItemId = resultItemId;
        this.resultCount = resultCount;
        this.ingredients = Collections.unmodifiableMap(new LinkedHashMap<>(ingredients));
        this.goldCost = goldCost;
        this.minLevel = minLevel;
        this.description = description;
    }

    public String key() { return key; }
    public int resultItemId() { return resultItemId; }
    public int resultCount() { return resultCount; }
    public Map<Integer, Integer> ingredients() { return ingredients; }
    public long goldCost() { return goldCost; }
    public int minLevel() { return minLevel; }
    public String description() { return description; }
}
