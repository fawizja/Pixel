package pl.fawizja.pixel.gathering;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.inventory.Container;
import pl.fawizja.pixel.inventory.InventoryResult;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.magic.SpellRegistry;
import pl.fawizja.pixel.world.FloorMap;
import pl.fawizja.pixel.world.Layer;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.Region;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Lowienie ryb jak w Tibii: wedka w rece (pole broni) albo w plecaku, robaki w plecaku - niczego nie trzeba
 * zakladac; kazde zarzucenie zuzywa jednego robaka z plecaka (oprocz proby, w ktorej zlowiona ryba nie
 * zmiescilaby sie w plecaku); kafelek wody w zasiegu 3.
 * Szansa rosnie z umiejetnoscia FISHING. Kafelek, z ktorego zlowiono rybe, "odpoczywa" przez
 * {@link #TILE_REST_MS} - nie da sie bez konca lowic z jednego miejsca.
 * W regionach z data/fishing.csv czesc polowu to ryby danego biomu (od progu umiejetnosci).
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
    /** Bagna Selvaran tez sa lowiskiem (w dzungli nie ma otwartej wody). */
    private final int swampTile;
    private final int rodId, wormId, fishId, pearlId;
    private final List<BiomeFish> biomeFish;
    private final Map<Position, Long> restingUntil = new HashMap<>();

    /** Wiersz data/fishing.csv. */
    public record BiomeFish(String region, int itemId, int permille, int minSkill) { }
    private long nextAttemptMs;

    public FishingSystem(WorldMap world, ItemRegistry items, Random rng) {
        this.world = world;
        this.items = items;
        this.rng = rng;
        this.waterTile = world.tiles().byKey("water").id();
        this.swampTile = world.tiles().byKey("swamp").id();
        this.rodId = items.byKey("fishing_rod").id();
        this.wormId = items.byKey("worm").id();
        this.fishId = items.byKey("fish").id();
        this.pearlId = items.byKey("pearl").id();
        this.biomeFish = loadBiomeFish(items);
    }

    public static List<BiomeFish> loadBiomeFish(ItemRegistry items) {
        try {
            return parseBiomeFish(SpellRegistry.readLines("/data/fishing.csv"), items);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read fishing.csv", e);
        }
    }

    public static List<BiomeFish> parseBiomeFish(List<String> lines, ItemRegistry items) throws IOException {
        List<BiomeFish> out = new ArrayList<>();
        int n = 0;
        for (String line : lines) {
            n++;
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] f = t.split(";");
            if (f.length != 4) throw new IOException("fishing.csv line " + n + ": expected 4 fields");
            ItemType type = items.byKey(f[1].trim());
            if (type == null) throw new IOException("fishing.csv line " + n + ": unknown item " + f[1]);
            try {
                int permille = Integer.parseInt(f[2].trim());
                int minSkill = Integer.parseInt(f[3].trim());
                if (permille <= 0 || permille > 1000 || minSkill < 0) throw new IOException("fishing.csv line " + n + ": bad numbers");
                out.add(new BiomeFish(f[0].trim(), type.id(), permille, minSkill));
            } catch (NumberFormatException e) {
                throw new IOException("fishing.csv line " + n + ": " + e.getMessage(), e);
            }
        }
        return out;
    }

    public List<BiomeFish> biomeFish() { return biomeFish; }

    public static double catchChance(int fishingSkill) {
        return Math.min(MAX_CHANCE, BASE_CHANCE + Math.max(0, fishingSkill) * CHANCE_PER_SKILL);
    }

    public boolean isWater(Position p) {
        FloorMap f = world.floor(p.z());
        if (f == null || !f.inBounds(p.x(), p.y())) return false;
        int g = f.get(Layer.GROUND, p.x(), p.y());
        return g == waterTile || g == swampTile;
    }

    /** Czy w zasiegu jest jakakolwiek woda (do pokazania przycisku LOW). */
    public boolean waterNearby(Position from) {
        return findSpot(from, Long.MIN_VALUE, false) != null;
    }

    /** Wedka w plecaku albo w rece (gniazdo broni). */
    public boolean hasRod(PlayerCharacter pc) {
        ItemStack hand = pc.equipment().get(EquipSlot.WEAPON);
        return pc.backpack().count(rodId) > 0 || (hand != null && hand.itemId() == rodId);
    }

    /** Robaki na przynete w plecaku (jak w Tibii: wedka bierze je z plecaka, nic sie nie zaklada). */
    public int bait(PlayerCharacter pc) {
        return pc.backpack().count(wormId);
    }

    public int wormId() { return wormId; }

    /** Zuzywa jednego robaka z plecaka. */
    void useWorm(PlayerCharacter pc) {
        pc.backpack().remove(wormId, 1);
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
        if (bait(pc) <= 0) return FishingResult.NO_BAIT;
        if (findSpot(pc.position(), now, false) == null) return FishingResult.NO_WATER;
        Position spot = findSpot(pc.position(), now, true);
        if (spot == null) return FishingResult.DEPLETED;
        nextAttemptMs = now + ATTEMPT_COOLDOWN_MS;
        pc.skills().addTries(Skill.FISHING, 1);
        if (rng.nextDouble() >= catchChance(pc.skills().level(Skill.FISHING))) {
            useWorm(pc);
            return FishingResult.NOTHING;
        }

        int skill = pc.skills().level(Skill.FISHING);
        int caught = skill >= PEARL_MIN_SKILL && rng.nextInt(1000) < PEARL_PERMILLE ? pearlId : biomeCatch(spot, skill);
        Container copy = pc.backpack().copy();
        if (copy.add(new ItemStack(caught, 1)) != InventoryResult.OK) return FishingResult.BACKPACK_FULL;
        pc.backpack().commitFrom(copy);
        useWorm(pc);
        pc.skills().addTries(Skill.FISHING, 2);
        restingUntil.put(spot, now + TILE_REST_MS);
        lastCatchId = caught;
        return FishingResult.CAUGHT;
    }

    /** Ryba biomu dla wody w regionie (losowanie tylko tam, gdzie region ma wpis) albo zwykla ryba. */
    private int biomeCatch(Position spot, int skill) {
        Region region = world.regionAt(spot);
        if (region == null) return fishId;
        for (BiomeFish b : biomeFish) {
            if (b.region().equals(region.name()) && skill >= b.minSkill()) {
                return rng.nextInt(1000) < b.permille() ? b.itemId() : fishId;
            }
        }
        return fishId;
    }

    private int lastCatchId;

    /** Id ostatnio zlowionego przedmiotu (do komunikatu). */
    public int lastCatchId() { return lastCatchId; }
}
