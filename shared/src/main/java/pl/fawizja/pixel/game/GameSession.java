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
import pl.fawizja.pixel.gathering.FishingResult;
import pl.fawizja.pixel.gathering.FishingSystem;
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
import pl.fawizja.pixel.world.Region;
import pl.fawizja.pixel.world.WorldMap;
import pl.fawizja.pixel.npc.Npc;
import pl.fawizja.pixel.npc.NpcRegistry;
import pl.fawizja.pixel.npc.NpcRole;
import pl.fawizja.pixel.npc.Shop;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.npc.TradeResult;
import pl.fawizja.pixel.npc.TradeSystem;
import pl.fawizja.pixel.quest.QuestRegistry;
import pl.fawizja.pixel.quest.QuestResult;
import pl.fawizja.pixel.quest.QuestSystem;
import pl.fawizja.pixel.quest.QuestType;
import pl.fawizja.pixel.time.DayPhase;
import pl.fawizja.pixel.time.WorldClock;

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
    private final GroundItems ground;
    /** Tryb serwera: potwory i przedmioty na ziemi sa wspolne, petla swiata aktualizuje potwory raz. */
    private final boolean sharedWorld;
    private final EventLog log = new EventLog(50);
    private final int torchId;
    private final MonsterManager monsters;
    private final CombatSystem combat;
    private final MonsterManager.Target playerTarget;
    private final SpellSystem spells;
    private final RecipeRegistry recipes;
    private final FishingSystem fishing;
    /** Zegar swiata; czas sesji 0 = 09:00 czasu gry. W trybie online czas poda serwer. */
    private WorldClock clock = new WorldClock(-9L * 150_000L, 60L * 60L * 1000L, 0.38f);
    private Boolean wasNight;
    private NpcRegistry npcs = NpcRegistry.empty();
    private ShopRegistry shops;
    private QuestSystem quests;
    /** Promien, w jakim ostrzezenie o pietrze bierze pod uwage spawny. */
    static final int DANGER_RADIUS = 30;

    private Region currentRegion;
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
        this(items, world, pc, monsters, null, rng, minLevel, spellRegistry, consumables, recipes);
    }

    /**
     * Sesja gracza we wspolnym swiecie (serwer): sharedGround != null oznacza, ze potwory i przedmioty
     * na ziemi naleza do swiata, a {@link #update} nie aktualizuje potworow (robi to petla serwera).
     */
    public GameSession(ItemRegistry items, WorldMap world, PlayerCharacter pc, MonsterManager monsters,
                       GroundItems sharedGround, Random rng, int minLevel, SpellRegistry spellRegistry,
                       ConsumableRegistry consumables, RecipeRegistry recipes) {
        this.sharedWorld = sharedGround != null;
        this.ground = sharedGround != null ? sharedGround : new GroundItems();
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
        this.fishing = new FishingSystem(world, items, rng);
        this.spells = new SpellSystem(pc, world, items, spellRegistry, consumables, combat, this.monsters, log, rng);
        this.movement.setOccupancy(p -> this.monsters.monsterAt(p) != null || npcs.at(p) != null);
        this.combat.setKillListener(t -> {
            QuestType done = quests == null ? null : quests.onKill(pc, t.key());
            if (done != null) {
                log.add(lastUpdateMs, EventLog.Kind.SYSTEM, "Zadanie \"" + done.name() + "\": cel osiagniety, wroc po nagrode.");
            }
        });
        this.playerTarget = new MonsterManager.Target() {
            @Override
            public Position position() { return pc.position(); }

            @Override
            public boolean attackable() { return !pc.isDead(); }
        };
    }

    // ------------------------------------------------------------------ NPC, handel, bank, zadania

    /** Podlacza NPC, sklepy i zadania swiata (PixelGame i testy). */
    public void setTownServices(NpcRegistry npcs, ShopRegistry shops, QuestRegistry quests) {
        this.npcs = java.util.Objects.requireNonNull(npcs);
        this.shops = shops;
        this.quests = quests == null ? null : new QuestSystem(quests, items);
    }

    public NpcRegistry npcs() { return npcs; }
    public ShopRegistry shops() { return shops; }
    public QuestSystem quests() { return quests; }

    /** NPC, z ktorym mozna teraz rozmawiac (albo null). */
    public Npc npcNearby() {
        return pc.isDead() ? null : npcs.nearest(pc.position(), world);
    }

    private Npc talkTo(NpcRole role, long nowMs) {
        Npc n = npcNearby();
        if (n == null || n.role() != role) {
            log.add(nowMs, EventLog.Kind.WARNING, TradeResult.TOO_FAR.message());
            return null;
        }
        return n;
    }

    public Shop shopOf(Npc n) {
        return n == null || n.shopKey() == null || shops == null ? null : shops.get(n.shopKey());
    }

    public TradeResult buy(int itemId, int count, long nowMs) {
        Npc n = talkTo(NpcRole.MERCHANT, nowMs);
        if (n == null) return TradeResult.TOO_FAR;
        TradeResult r = TradeSystem.buy(pc.backpack(), items, shopOf(n), itemId, count);
        log.add(nowMs, r.ok() ? EventLog.Kind.LOOT : EventLog.Kind.WARNING,
                r.ok() ? "Kupiono: " + count + "x " + items.get(itemId).name() : r.message());
        return r;
    }

    public TradeResult sellAll(int itemId, long nowMs) {
        Npc n = talkTo(NpcRole.MERCHANT, nowMs);
        if (n == null) return TradeResult.TOO_FAR;
        long value = TradeSystem.sellValue(pc.backpack(), shopOf(n), itemId);
        int count = pc.backpack().count(itemId);
        TradeResult r = TradeSystem.sellAll(pc.backpack(), shopOf(n), itemId);
        log.add(nowMs, r.ok() ? EventLog.Kind.LOOT : EventLog.Kind.WARNING,
                r.ok() ? "Sprzedano: " + count + "x " + items.get(itemId).name() + " za " + value + " gp" : r.message());
        return r;
    }

    public InventoryResult depositAll(long nowMs) {
        if (talkTo(NpcRole.BANKER, nowMs) == null) return InventoryResult.INVALID_AMOUNT;
        long before = gold();
        InventoryResult r = pc.bank().depositAll(pc.backpack());
        log.add(nowMs, r.ok() ? EventLog.Kind.INFO : EventLog.Kind.WARNING,
                r.ok() ? "Wplacono " + before + " gp. Saldo: " + pc.bank().balance() + " gp." : "Nie masz monet do wplacenia.");
        return r;
    }

    public InventoryResult withdraw(long amount, long nowMs) {
        if (talkTo(NpcRole.BANKER, nowMs) == null) return InventoryResult.INVALID_AMOUNT;
        InventoryResult r = pc.bank().withdraw(pc.backpack(), amount);
        log.add(nowMs, r.ok() ? EventLog.Kind.INFO : EventLog.Kind.WARNING,
                r.ok() ? "Wyplacono " + amount + " gp. Saldo: " + pc.bank().balance() + " gp."
                        : r == InventoryResult.INSUFFICIENT_FUNDS ? "Za malo pieniedzy na koncie." : "Brak miejsca w plecaku.");
        return r;
    }

    /** Kaplan: pelne leczenie i mana (tylko w swiatyni, wiec poza walka). */
    public void priestHeal(long nowMs) {
        if (talkTo(NpcRole.PRIEST, nowMs) == null) return;
        pc.heal(pc.maxHealth());
        pc.restoreMana(pc.maxMana());
        log.add(nowMs, EventLog.Kind.INFO, "Kaplan cie uleczyl.");
    }

    /** Kaplan: zamieszkanie w miescie - tu postac odrodzi sie po smierci. */
    public void settleHere(long nowMs) {
        Npc n = talkTo(NpcRole.PRIEST, nowMs);
        if (n == null) return;
        pc.setHomeTown(n.town().toLowerCase(java.util.Locale.ROOT));
        log.add(nowMs, EventLog.Kind.SYSTEM, "Mieszkasz teraz w " + n.town() + ". Tu bedziesz sie odradzac.");
    }

    public QuestResult questAction(String questKey, long nowMs) {
        Npc n = talkTo(NpcRole.GUIDE, nowMs);
        QuestType q = quests == null ? null : quests.registry().get(questKey);
        if (n == null || q == null || !q.giver().equals(n.key())) return QuestResult.NOT_FINISHED;
        QuestResult r = quests.interact(pc, q);
        String text = r.message();
        if (r == QuestResult.COMPLETED) {
            text = "Ukonczono \"" + q.name() + "\": +" + q.rewardExp() + " exp" + (q.rewardGold() > 0 ? ", +" + q.rewardGold() + " gp" : "");
        } else if (r == QuestResult.ACCEPTED) {
            text = "Przyjeto zadanie \"" + q.name() + "\".";
        }
        log.add(nowMs, r == QuestResult.COMPLETED || r == QuestResult.ACCEPTED ? EventLog.Kind.SYSTEM : EventLog.Kind.WARNING, text);
        return r;
    }

    public MonsterManager monsters() { return monsters; }
    /** Widok gracza dla AI potworow (petla serwera zbiera je ze wszystkich sesji). */
    public MonsterManager.Target playerTarget() { return playerTarget; }
    public boolean sharedWorld() { return sharedWorld; }
    public CombatSystem combat() { return combat; }
    public SpellSystem spells() { return spells; }
    public RecipeRegistry recipes() { return recipes; }

    public FishingSystem fishing() { return fishing; }

    /** Czy pokazac przycisk lowienia (wedka w plecaku i woda w zasiegu). */
    public boolean canFish() {
        return fishing.hasRod(pc) && fishing.waterNearby(pc.position());
    }

    public FishingResult fish(long nowMs) {
        FishingResult r = fishing.fish(pc, nowMs);
        switch (r) {
            case CAUGHT:
                log.add(nowMs, EventLog.Kind.LOOT, "Zlowiono: " + items.get(fishing.lastCatchId()).name());
                break;
            case NOTHING:
                log.add(nowMs, EventLog.Kind.INFO, r.message);
                break;
            case COOLDOWN:
                break;
            default:
                log.add(nowMs, EventLog.Kind.WARNING, r.message);
        }
        return r;
    }

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
    public WorldClock clock() { return clock; }
    public void setClock(WorldClock clock) { this.clock = java.util.Objects.requireNonNull(clock); }

    public boolean isNight(long nowMs) {
        return WorldClock.phase(clock.minuteOfDay(nowMs)) == DayPhase.NIGHT;
    }
    public ItemRegistry items() { return items; }
    public Direction facing() { return facing; }
    public boolean isMoving() { return moving; }

    /** Krok symulacji. requested = kierunek z joysticka/klawiatury albo null. */
    public void update(long nowMs, Direction requested) {
        if (lastUpdateMs >= 0) {
            long dt = Math.min(1000, nowMs - lastUpdateMs);
            pc.tickRegeneration(dt);
            switch (pc.tickTorch(dt, torchId)) {
                case NEXT_LIT: log.add(nowMs, EventLog.Kind.INFO, "Pochodnia sie wypalila. Zapalasz nastepna."); break;
                case BURNED_OUT: log.add(nowMs, EventLog.Kind.WARNING, "Pochodnia sie wypalila. Nie masz kolejnej."); break;
                default:
            }
        }
        lastUpdateMs = nowMs;
        if (moving && nowMs - stepStartMs >= stepDurationMs) {
            moving = false;
        }
        if (!moving && requested != null) {
            facing = requested;
            Position before = pc.position();
            MoveResult r = movement.step(pc, requested);
            if (r == MoveResult.MOVED || r == MoveResult.CHANGED_FLOOR) checkRegion(nowMs);
            if (r == MoveResult.MOVED) {
                stepFrom = before;
                stepStartMs = nowMs;
                stepDurationMs = MovementSystem.stepDurationMs(pc, requested);
                moving = true;
            } else if (r == MoveResult.CHANGED_FLOOR) {
                onFloorChanged(nowMs);
            }
        }
        boolean night = isNight(nowMs);
        if (wasNight != null && night != wasNight) {
            log.add(nowMs, EventLog.Kind.INFO, night ? "Zapada noc. Nocne drapiezniki sa grozniejsze."
                    : "Wstaje dzien.");
        }
        wasNight = night;
        monsters.setNight(night);
        Position before = pc.position();
        if (!sharedWorld) monsters.update(nowMs, playerTarget, combat);
        combat.update(nowMs);
        if (!pc.position().equals(before)) {
            // smierc i odrodzenie w swiatyni
            stepFrom = pc.position();
            moving = false;
        }
    }

    /** Komunikat przy wejsciu do nowego regionu i ostrzezenie, gdy jest zbyt trudny. */
    private void checkRegion(long nowMs) {
        Region r = world.regionAt(pc.position());
        if (r == currentRegion) return;
        currentRegion = r;
        if (r == null) return;
        log.add(nowMs, EventLog.Kind.INFO, r.name() + " (zalecany poziom " + r.levelMin() + "-" + r.levelMax() + ")");
        if (r.levelMin() > pc.level() + 15) {
            log.add(nowMs, EventLog.Kind.WARNING, "Uwaga: ten region jest zbyt niebezpieczny dla poziomu " + pc.level() + ".");
        }
    }

    public Region currentRegion() { return currentRegion; }

    private void onFloorChanged(long nowMs) {
        stepFrom = pc.position();
        moving = false;
        combat.setTarget(null);
        log.add(nowMs, EventLog.Kind.INFO, floorMessage(pc.position().z()));
        int danger = monsters.maxMonsterLevelNear(pc.position(), DANGER_RADIUS);
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
        checkRegion(nowMs);
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

    /**
     * Klient online: pozycja postaci z klatki stanu serwera. Krok na sasiedni kafelek jest
     * interpolowany przez stepMs (jak lokalny ruch), dalszy skok (teleport, schody) - natychmiast.
     */
    public void applyRemotePosition(Position p, Direction facing, long nowMs, long stepMs) {
        Position cur = pc.position();
        if (facing != null) this.facing = facing;
        if (cur.equals(p)) return;
        boolean step = cur.z() == p.z() && cur.distanceTo(p) == 1;
        stepFrom = step ? cur : p;
        pc.setPosition(p);
        stepStartMs = nowMs;
        stepDurationMs = step ? stepMs : 0;
        moving = step;
        if (!step || cur.z() != p.z()) checkRegion(nowMs);
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
