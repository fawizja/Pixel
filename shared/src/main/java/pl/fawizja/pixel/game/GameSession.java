package pl.fawizja.pixel.game;

import java.util.Collections;
import java.util.Random;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.combat.CombatSystem;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.economy.Money;
import pl.fawizja.pixel.inventory.InventoryResult;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.magic.CastResult;
import pl.fawizja.pixel.magic.ConsumableRegistry;
import pl.fawizja.pixel.magic.CraftingSystem;
import pl.fawizja.pixel.magic.Recipe;
import pl.fawizja.pixel.magic.RecipeRegistry;
import pl.fawizja.pixel.magic.SpellRegistry;
import pl.fawizja.pixel.magic.SpellSystem;
import pl.fawizja.pixel.movement.MoveResult;
import pl.fawizja.pixel.movement.MovementSystem;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.GroundItems;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Lokalna sesja gry (tryb offline): laczy postac, swiat i systemy w jedna petle
 * aktualizacji niezalezna od renderowania. W trybie online te same akcje beda
 * wysylane do serwera, ktory jest zrodlem prawdy.
 */
public final class GameSession {

    private final ItemRegistry items;
    private final WorldMap world;
    private final PlayerCharacter pc;
    private final MovementSystem movement;
    private final GroundItems ground = new GroundItems();
    private final EventLog log = new EventLog(50);
    private final int torchId;
    private final MonsterManager monsters;
    private final CombatSystem combat;
    private final MonsterManager.Target playerTarget;
    private final SpellSystem spells;
    private final RecipeRegistry recipes;

    private Position stepFrom;
    private long stepStartMs;
    private long stepDurationMs;
    private boolean moving;
    private Direction facing = Direction.SOUTH;
    private long lastUpdateMs = -1;

    /** Sesja bez potworow (testy, edytor). */
    public GameSession(ItemRegistry items, WorldMap world, PlayerCharacter pc) {
        this(items, world, pc, null, new Random(1), 8);
    }

    public GameSession(ItemRegistry items, WorldMap world, PlayerCharacter pc, MonsterManager monsters,
                       Random rng, int minLevel) {
        this(items, world, pc, monsters, rng, minLevel, SpellRegistry.loadDefault(items),
                ConsumableRegistry.loadDefault(items), RecipeRegistry.loadDefault(items));
    }

    public GameSession(ItemRegistry items, WorldMap world, PlayerCharacter pc, MonsterManager monsters,
                       Random rng, int minLevel, SpellRegistry spellRegistry, ConsumableRegistry consumables,
                       RecipeRegistry recipes) {
        this.items = items;
        this.recipes = recipes;
        this.world = world;
        this.pc = pc;
        this.movement = new MovementSystem(world, items.byKey("rope").id());
        this.torchId = items.byKey("torch").id();
        if (!world.isWalkable(pc.position())) {
            pc.setPosition(world.spawn());
        }
        this.stepFrom = pc.position();
        this.monsters = monsters != null ? monsters
                : new MonsterManager(world, null, Collections.emptyList(), rng);
        this.combat = new CombatSystem(pc, world, items, this.monsters, ground, log, rng, minLevel);
        this.spells = new SpellSystem(pc, world, items, spellRegistry, consumables, combat, this.monsters, log, rng);
        this.movement.setOccupancy(p -> this.monsters.monsterAt(p) != null);
        this.playerTarget = new MonsterManager.Target() {
            @Override
            public Position position() { return pc.position(); }

            @Override
            public boolean attackable() { return !pc.isDead(); }
        };
    }

    public MonsterManager monsters() { return monsters; }
    public CombatSystem combat() { return combat; }
    public SpellSystem spells() { return spells; }
    public RecipeRegistry recipes() { return recipes; }

    public CastResult castSpell(String key, long nowMs) {
        return spells.cast(key, nowMs);
    }

    /** Uzycie przedmiotu z plecaka (mikstura, jedzenie, runa). */
    public CastResult useSlot(int slot, long nowMs) {
        if (slot < 0 || slot >= pc.backpack().size()) return CastResult.MISSING_ITEM;
        return spells.useItem(slot, nowMs);
    }

    public CastResult usePotion(String itemKey, long nowMs) {
        return spells.useFirst(itemKey, nowMs);
    }

    public CastResult craft(String recipeKey, long nowMs) {
        Recipe r = recipes.get(recipeKey);
        CastResult res = CraftingSystem.craft(pc, r);
        if (res == CastResult.OK) {
            log.add(nowMs, EventLog.Kind.LOOT, "Wytworzono: " + r.resultCount() + "x " + items.get(r.resultItemId()).name());
        } else {
            log.add(nowMs, EventLog.Kind.WARNING, res.message);
        }
        return res;
    }

    public PlayerCharacter character() { return pc; }
    public WorldMap world() { return world; }
    public GroundItems ground() { return ground; }
    public EventLog log() { return log; }
    public ItemRegistry items() { return items; }
    public Direction facing() { return facing; }
    public boolean isMoving() { return moving; }

    /** Krok symulacji. requested = kierunek z joysticka/klawiatury albo null. */
    public void update(long nowMs, Direction requested) {
        if (lastUpdateMs >= 0) pc.tickRegeneration(Math.min(1000, nowMs - lastUpdateMs));
        lastUpdateMs = nowMs;
        if (moving && nowMs - stepStartMs >= stepDurationMs) {
            moving = false;
        }
        if (!moving && requested != null) {
            facing = requested;
            Position before = pc.position();
            MoveResult r = movement.step(pc, requested);
            if (r == MoveResult.MOVED) {
                stepFrom = before;
                stepStartMs = nowMs;
                stepDurationMs = MovementSystem.stepDurationMs(pc, requested);
                moving = true;
            } else if (r == MoveResult.CHANGED_FLOOR) {
                onFloorChanged(nowMs);
            }
        }
        Position before = pc.position();
        monsters.update(nowMs, playerTarget, combat);
        combat.update(nowMs);
        if (!pc.position().equals(before)) {
            // smierc i odrodzenie w swiatyni
            stepFrom = pc.position();
            moving = false;
        }
    }

    private void onFloorChanged(long nowMs) {
        stepFrom = pc.position();
        moving = false;
        combat.setTarget(null);
        log.add(nowMs, EventLog.Kind.INFO, floorMessage(pc.position().z()));
        int danger = monsters.maxMonsterLevelOnFloor(pc.position().z());
        if (danger > pc.level() * 2 + 10) {
            log.add(nowMs, EventLog.Kind.WARNING, "Uwaga: niebezpieczny obszar (potwory do poziomu " + danger + ").");
        }
    }

    /** Natychmiastowe przeniesienie postaci (swiatynia, narzedzia deweloperskie, w przyszlosci teleporty NPC). */
    public void teleport(Position p, long nowMs) {
        monsters.evict(p, nowMs);
        pc.setPosition(p);
        stepFrom = p;
        moving = false;
        combat.setTarget(null);
        onFloorChanged(nowMs);
    }

    public void toggleDefensive(long nowMs) {
        pc.setDefensive(!pc.isDefensive());
        log.add(nowMs, EventLog.Kind.INFO, pc.isDefensive() ? "Postawa obronna." : "Postawa ofensywna.");
    }

    public void cycleTarget(long nowMs) {
        Monster m = combat.cycleTarget();
        if (m == null) log.add(nowMs, EventLog.Kind.INFO, "Brak celu w poblizu.");
    }

    /** Wybor celu dotknieciem kafelka. Zwraca true, jesli trafiono potwora. */
    public boolean targetAt(Position p) {
        Monster m = monsters.monsterAt(p);
        if (m == null) return false;
        combat.setTarget(m);
        return true;
    }

    /** Pozycja do rysowania (plynna interpolacja miedzy kafelkami). */
    public float renderX(long nowMs) {
        return lerp(stepFrom.x(), pc.position().x(), progress(nowMs));
    }

    public float renderY(long nowMs) {
        return lerp(stepFrom.y(), pc.position().y(), progress(nowMs));
    }

    private float progress(long nowMs) {
        if (!moving || stepFrom.z() != pc.position().z()) return 1f;
        return Math.min(1f, (nowMs - stepStartMs) / (float) Math.max(1, stepDurationMs));
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    public static String floorMessage(int z) {
        if (z == 0) return "Jestes na powierzchni.";
        return z > 0 ? "Pietro +" + z + "." : "Podziemia, poziom " + z + ".";
    }

    public void useNearby(long nowMs) {
        MoveResult r = movement.use(pc);
        switch (r) {
            case CHANGED_FLOOR:
                onFloorChanged(nowMs);
                break;
            case NEEDS_ROPE:
                log.add(nowMs, EventLog.Kind.WARNING, "Potrzebujesz liny.");
                break;
            default:
                log.add(nowMs, EventLog.Kind.WARNING, "Nie ma tu nic do uzycia.");
        }
    }

    public void toggleRun(long nowMs) {
        pc.setRunning(!pc.isRunning());
        log.add(nowMs, EventLog.Kind.INFO, pc.isRunning() ? "Bieganie wlaczone." : "Bieganie wylaczone.");
    }

    public void toggleTorch(long nowMs) {
        boolean want = !pc.isTorchLit();
        if (!pc.setTorchLit(want, torchId)) {
            log.add(nowMs, EventLog.Kind.WARNING, "Nie masz pochodni.");
        } else {
            log.add(nowMs, EventLog.Kind.INFO, want ? "Zapalasz pochodnie." : "Gasisz pochodnie.");
        }
    }

    public boolean itemsAtFeet() {
        return !ground.at(pc.position()).isEmpty();
    }

    public void pickUpHere(long nowMs) {
        Position p = pc.position();
        if (ground.at(p).isEmpty()) return;
        ItemStack top = ground.at(p).get(ground.at(p).size() - 1);
        InventoryResult r = ground.pickUp(p, ground.at(p).size() - 1, pc.backpack());
        if (r.ok()) {
            log.add(nowMs, EventLog.Kind.LOOT, "Podniesiono: " + describe(top));
        } else {
            log.add(nowMs, EventLog.Kind.WARNING, "Plecak jest pelny.");
        }
    }

    public void dropFromSlot(int slot, long nowMs) {
        ItemStack s = pc.backpack().get(slot);
        if (s == null) return;
        if (s.itemId() == torchId && pc.isTorchLit() && pc.backpack().count(torchId) == 1) {
            pc.setTorchLit(false, torchId);
        }
        InventoryResult r = ground.dropFrom(pc.backpack(), slot, s.count(), pc.position(), world);
        if (r.ok()) log.add(nowMs, EventLog.Kind.INFO, "Wyrzucono: " + describe(s));
    }

    public void equipFromSlot(int slot, long nowMs) {
        InventoryResult r = pc.equipment().equipFromBackpack(pc.backpack(), slot, pc.vocation(), pc.level());
        switch (r) {
            case OK: log.add(nowMs, EventLog.Kind.INFO, "Zalozono przedmiot."); break;
            case WRONG_VOCATION: log.add(nowMs, EventLog.Kind.WARNING, "Twoja profesja nie moze tego uzywac."); break;
            case LEVEL_TOO_LOW: log.add(nowMs, EventLog.Kind.WARNING, "Za niski poziom."); break;
            case WRONG_SLOT_TYPE: log.add(nowMs, EventLog.Kind.WARNING, "Tego nie da sie zalozyc."); break;
            default: log.add(nowMs, EventLog.Kind.WARNING, "Nie udalo sie: " + r);
        }
    }

    public InventoryResult moveSlot(int from, int to) {
        ItemStack s = pc.backpack().get(from);
        return s == null ? InventoryResult.EMPTY_SLOT : pc.backpack().move(from, to, s.count());
    }

    public long gold() { return Money.totalValue(pc.backpack()); }

    public String describe(ItemStack s) {
        String name = items.get(s.itemId()).name();
        return s.count() > 1 ? s.count() + "x " + name : name;
    }
}
