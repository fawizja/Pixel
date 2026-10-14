package pl.fawizja.pixel.gathering;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.inventory.Container;
import pl.fawizja.pixel.inventory.InventoryResult;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.world.FloorMap;
import pl.fawizja.pixel.world.Layer;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Lowienie ryb: wedka w plecaku, robak zuzywany tylko przy zlowieniu, kafelek wody w zasiegu 3.
 * Szansa rosnie z umiejetnoscia FISHING. Kafelek, z ktorego zlowiono rybe, "odpoczywa" przez
 * {@link #TILE_REST_MS} - nie da sie bez konca lowic z jednego miejsca.
 */
public final class FishingSystem {

    public static final int RANGE = 3;
    public static final long ATTEMPT_COOLDOWN_MS = 1500;
    public static final long TILE_REST_MS = 120_000;
    public static final double BASE_CHANCE = 0.10;
    public static final double CHANCE_PER_SKILL = 0.006;
    public static final double MAX_CHANCE = 0.70;
    /** Szansa na perle (na zlowienie) od umiejetnosci 30. */
    public static final int PEARL_PERMILLE = 30;
    public static final int PEARL_MIN_SKILL = 30;

    private final WorldMap world;
    private final ItemRegistry items;
    private final Random rng;
    private final int waterTile;
    private final int rodId, wormId, fishId, pearlId;
    private final Map<Position, Long> restingUntil = new HashMap<>();
    private long nextAttemptMs;

    public FishingSystem(WorldMap world, ItemRegistry items, Random rng) {
        this.world = world;
        this.items = items;
        this.rng = rng;
        this.waterTile = world.tiles().byKey("water").id();
        this.rodId = items.byKey("fishing_rod").id();
        this.wormId = items.byKey("worm").id();
        this.fishId = items.byKey("fish").id();
        this.pearlId = items.byKey("pearl").id();
    }

    public static double catchChance(int fishingSkill) {
        return Math.min(MAX_CHANCE, BASE_CHANCE + Math.max(0, fishingSkill) * CHANCE_PER_SKILL);
    }

    public boolean isWater(Position p) {
        FloorMap f = world.floor(p.z());
        return f != null && f.inBounds(p.x(), p.y()) && f.get(Layer.GROUND, p.x(), p.y()) == waterTile;
    }

    /** Czy w zasiegu jest jakakolwiek woda (do pokazania przycisku LOW). */
    public boolean waterNearby(Position from) {
        return findSpot(from, Long.MIN_VALUE, false) != null;
    }

    public boolean hasRod(PlayerCharacter pc) {
        return pc.backpack().count(rodId) > 0;
    }

    /** Najblizszy kafelek wody w zasiegu z linia wzroku; z onlyRested pomija "odpoczywajace". */
    Position findSpot(Position from, long now, boolean onlyRested) {
        Position best = null;
        int bestD = Integer.MAX_VALUE;
        for (int dy = -RANGE; dy <= RANGE; dy++) {
            for (int dx = -RANGE; dx <= RANGE; dx++) {
                Position p = from.translate(dx, dy);
                if (!isWater(p)) continue;
                if (onlyRested && restingUntil.getOrDefault(p, Long.MIN_VALUE) > now) continue;
                if (!world.hasLineOfSight(from, p)) continue;
                int d = Math.abs(dx) + Math.abs(dy);
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
        }
        return best;
    }

    public FishingResult fish(PlayerCharacter pc, long now) {
        if (now < nextAttemptMs) return FishingResult.COOLDOWN;
        if (!hasRod(pc)) return FishingResult.NO_ROD;
        if (pc.backpack().count(wormId) <= 0) return FishingResult.NO_BAIT;
        if (findSpot(pc.position(), now, false) == null) return FishingResult.NO_WATER;
        Position spot = findSpot(pc.position(), now, true);
        if (spot == null) return FishingResult.DEPLETED;
        nextAttemptMs = now + ATTEMPT_COOLDOWN_MS;
        pc.skills().addTries(Skill.FISHING, 1);
        if (rng.nextDouble() >= catchChance(pc.skills().level(Skill.FISHING))) return FishingResult.NOTHING;

        int caught = pc.skills().level(Skill.FISHING) >= PEARL_MIN_SKILL && rng.nextInt(1000) < PEARL_PERMILLE ? pearlId : fishId;
        Container copy = pc.backpack().copy();
        if (copy.remove(wormId, 1) != InventoryResult.OK) return FishingResult.NO_BAIT;
        if (copy.add(new ItemStack(caught, 1)) != InventoryResult.OK) return FishingResult.BACKPACK_FULL;
        pc.backpack().commitFrom(copy);
        pc.skills().addTries(Skill.FISHING, 2);
        restingUntil.put(spot, now + TILE_REST_MS);
        lastCatchId = caught;
        return FishingResult.CAUGHT;
    }

    private int lastCatchId;

    /** Id ostatnio zlowionego przedmiotu (do komunikatu). */
    public int lastCatchId() { return lastCatchId; }
}
