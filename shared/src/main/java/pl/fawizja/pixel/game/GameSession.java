package pl.fawizja.pixel.game;

import java.util.Collections;
import java.util.List;
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
    private long lastGateWarningMs = -10_000;
    private long pzLockedUntil;
    private long lastPzWarningMs = -10_000;

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
            pc.bestiary().record(t.key(), t.boss());
            if (pc.daily().onKill(t.key(), today())) {
                log.add(lastUpdateMs, EventLog.Kind.SYSTEM, "Zadanie dnia wykonane! Wróć do przewodnika po nagrodę.");
            }
            checkAchievements(lastUpdateMs);
            QuestType done = quests == null ? null : quests.onKill(pc, t.key());
            if (done != null) {
                log.add(lastUpdateMs, EventLog.Kind.SYSTEM, "Zadanie \"" + done.name() + "\": cel osiągnięty, wróć po nagrodę.");
            }
        });
        this.playerTarget = new MonsterManager.Target() {
            @Override
            public Position position() { return pc.position(); }

            @Override
            public boolean attackable() { return !pc.isDead(); }
        };
    }

    /** Dzisiejsza data (dzien od 1970); w testach podmieniana. */
    private java.util.function.LongSupplier today = () -> java.time.LocalDate.now().toEpochDay();

    public void setToday(java.util.function.LongSupplier today) { this.today = today; }

    public long today() { return today.getAsLong(); }

    /** Zadanie dnia u przewodnika: przyjecie, postep albo odbior nagrody. */
    public void dailyAction(long nowMs) {
        if (talkTo(NpcRole.GUIDE, nowMs) == null) return;
        pl.fawizja.pixel.daily.DailyTask d = pc.daily();
        long day = today();
        if (!d.activeOn(day)) {
            d.assign(pc.name(), day, pc.level(), monsters.spawnedTypes());
            if (d.monster() == null) {
                log.add(nowMs, EventLog.Kind.WARNING, "Dziś nie mam dla ciebie zadania.");
                return;
            }
            log.add(nowMs, EventLog.Kind.SYSTEM, "Zadanie dnia: pokonaj " + d.target() + "x " + monsterName(d.monster()) + ".");
            return;
        }
        if (d.claimed()) {
            log.add(nowMs, EventLog.Kind.INFO, "Dzisiejsze zadanie już wykonane. Wróć jutro.");
            return;
        }
        if (!d.doneOn(day)) {
            log.add(nowMs, EventLog.Kind.INFO, "Zadanie dnia: " + monsterName(d.monster()) + " " + d.progress() + " / " + d.target() + ".");
            return;
        }
        pl.fawizja.pixel.monster.MonsterType t = monsterType(d.monster());
        long gold = t == null ? 0 : pl.fawizja.pixel.daily.DailyTask.rewardGold(t, d.target());
        long exp = t == null ? 0 : pl.fawizja.pixel.daily.DailyTask.rewardExp(t, d.target());
        if (gold > 0 && !pc.bank().credit(gold).ok()) return;
        d.claim();
        log.add(nowMs, EventLog.Kind.SYSTEM, "Nagroda za zadanie dnia: " + String.format("%,d gp", gold) + " na konto.");
        if (exp > 0) combat.awardExperience(exp, "Zadanie dnia", nowMs);
    }

    private pl.fawizja.pixel.monster.MonsterType monsterType(String key) {
        for (pl.fawizja.pixel.monster.MonsterType t : monsters.spawnedTypes()) if (t.key().equals(key)) return t;
        return null;
    }

    /** Nazwa potwora do napisow (klucz, gdy rejestr nieznany). */
    public String monsterName(String key) {
        pl.fawizja.pixel.monster.MonsterType t = monsterType(key);
        return t == null ? key : t.name();
    }

    private long achievementsCheckedMs;
    private boolean achievementsChecked;

    /** Nagroda za codzienne wejscie: sprawdzana przy pierwszym kroku i potem co minute (zmiana dnia w grze). */
    private long loginCheckedMs = Long.MIN_VALUE / 2;

    public void checkLoginReward(long nowMs) {
        loginCheckedMs = nowMs;
        if (pc.isDead()) return;
        pl.fawizja.pixel.daily.LoginStreak s = pc.loginStreak();
        long prevDay = s.lastDay();
        int prevStreak = s.streak();
        int day = s.claim(today());
        if (day == 0) return;
        long gold = pl.fawizja.pixel.daily.LoginStreak.rewardGold(day, pc.level());
        if (!pc.bank().credit(gold).ok()) {
            s.restoreState(prevDay, prevStreak); // pelne konto - sprobujemy pozniej
            return;
        }
        log.add(nowMs, EventLog.Kind.SYSTEM, "Nagroda za wejście do gry (dzień " + day + " z " + pl.fawizja.pixel.daily.LoginStreak.DAYS
                + "): " + String.format("%,d gp", gold) + " na konto." + (day < pl.fawizja.pixel.daily.LoginStreak.DAYS
                ? " Wróć jutro po większą." : " Pełny tydzień!"));
    }

    /** Nowe osiagniecia: wpis w dzienniku (sprawdzane przy zabiciu potwora i co sekunde). */
    public void checkAchievements(long nowMs) {
        achievementsCheckedMs = nowMs;
        achievementsChecked = true;
        for (pl.fawizja.pixel.achievement.Achievement a : pc.achievements().check(pc)) {
            log.add(nowMs, EventLog.Kind.SYSTEM, "Osiągnięcie zdobyte: " + a.title() + " (" + a.goal() + ")!");
        }
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
                r.ok() ? "Wpłacono " + before + " gp. Saldo: " + pc.bank().balance() + " gp." : "Nie masz monet do wpłacenia.");
        return r;
    }

    public InventoryResult withdraw(long amount, long nowMs) {
        if (talkTo(NpcRole.BANKER, nowMs) == null) return InventoryResult.INVALID_AMOUNT;
        InventoryResult r = pc.bank().withdraw(pc.backpack(), amount);
        log.add(nowMs, r.ok() ? EventLog.Kind.INFO : EventLog.Kind.WARNING,
                r.ok() ? "Wypłacono " + amount + " gp. Saldo: " + pc.bank().balance() + " gp."
                        : r == InventoryResult.INSUFFICIENT_FUNDS ? "Za mało pieniędzy na koncie." : "Brak miejsca w plecaku.");
        return r;
    }

    /**
     * Depozyt u bankiera: odklada wszystkie sztuki przedmiotu z plecaka (ile sie zmiesci). Monety ida na konto,
     * nie do depozytu. Zwraca liczbe przeniesionych sztuk.
     */
    public int depotStore(int itemId, long nowMs) {
        if (talkTo(NpcRole.BANKER, nowMs) == null) return 0;
        if (pl.fawizja.pixel.economy.Currency.isCoin(itemId)) {
            log.add(nowMs, EventLog.Kind.WARNING, "Monety wpłać na konto, nie do depozytu.");
            return 0;
        }
        return moveAll(pc.backpack(), pc.depot(), itemId, nowMs, "Odłożono do depozytu", "Depozyt jest pełny.");
    }

    /** Depozyt: wyjmuje do plecaka wszystkie sztuki przedmiotu (ile sie zmiesci). */
    public int depotTake(int itemId, long nowMs) {
        if (talkTo(NpcRole.BANKER, nowMs) == null) return 0;
        return moveAll(pc.depot(), pc.backpack(), itemId, nowMs, "Wyjęto z depozytu", "Brak miejsca w plecaku.");
    }

    private int moveAll(pl.fawizja.pixel.inventory.Container from, pl.fawizja.pixel.inventory.Container to, int itemId,
                        long nowMs, String done, String full) {
        int have = from.count(itemId);
        if (have <= 0) return 0;
        int n = (int) Math.min(have, to.spaceFor(itemId));
        if (n <= 0) {
            log.add(nowMs, EventLog.Kind.WARNING, full);
            return 0;
        }
        // atomowo: najpierw zdejmujemy, potem dodajemy (miejsce sprawdzone wyzej)
        if (!from.remove(itemId, n).ok()) return 0;
        if (!to.add(new ItemStack(itemId, n)).ok()) {
            from.add(new ItemStack(itemId, n)); // nie powinno sie zdarzyc - oddajemy
            return 0;
        }
        String name = items.get(itemId).name();
        log.add(nowMs, EventLog.Kind.INFO, done + ": " + n + "x " + name + (n < have ? " (reszta się nie zmieściła)" : "") + ".");
        return n;
    }

    /** Kaplan: pelne leczenie i mana (tylko w swiatyni, wiec poza walka). */
    public void priestHeal(long nowMs) {
        if (talkTo(NpcRole.PRIEST, nowMs) == null) return;
        pc.heal(pc.maxHealth());
        pc.restoreMana(pc.maxMana());
        log.add(nowMs, EventLog.Kind.INFO, "Kapłan cię uleczył.");
    }

    /** Kaplan: zamieszkanie w miescie - tu postac odrodzi sie po smierci. */
    public void settleHere(long nowMs) {
        Npc n = talkTo(NpcRole.PRIEST, nowMs);
        if (n == null) return;
        pc.setHomeTown(n.town().toLowerCase(java.util.Locale.ROOT));
        log.add(nowMs, EventLog.Kind.SYSTEM, "Mieszkasz teraz w " + n.town() + ". Tu będziesz się odradzać.");
    }

    /**
     * Rozmowa slowami kluczowymi z najblizszym NPC (data/dialogues.csv). Zwraca odpowiedz albo null,
     * gdy nikogo nie ma w poblizu.
     */
    public String askNpc(String text, long nowMs) {
        Npc n = npcNearby();
        if (n == null) {
            log.add(nowMs, EventLog.Kind.WARNING, TradeResult.TOO_FAR.message());
            return null;
        }
        pl.fawizja.pixel.npc.DialogueRegistry d = pl.fawizja.pixel.npc.DialogueRegistry.defaults();
        String answer = d.answer(n, text);
        if (answer == null) {
            java.util.List<String> topics = d.topics(n);
            answer = "Nie rozumiem. Zapytaj o: " + String.join(", ", topics.subList(0, Math.min(4, topics.size()))) + ".";
        }
        String line = n.name() + ": " + answer;
        log.add(nowMs, EventLog.Kind.INFO, line);
        return line;
    }

    public QuestResult questAction(String questKey, long nowMs) {
        Npc n = talkTo(NpcRole.GUIDE, nowMs);
        QuestType q = quests == null ? null : quests.registry().get(questKey);
        if (n == null || q == null || !q.giver().equals(n.key())) return QuestResult.NOT_FINISHED;
        QuestResult r = quests.interact(pc, q);
        String text = r.message();
        if (r == QuestResult.COMPLETED) {
            text = "Ukończono \"" + q.name() + "\". Nagroda: " + quests.rewardText(q) + ".";
        } else if (r == QuestResult.ACCEPTED) {
            text = "Przyjęto zadanie \"" + q.name() + "\".";
        } else if (r == QuestResult.LOCKED) {
            text = "Najpierw ukończ \"" + quests.registry().get(q.requires()).name() + "\".";
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
                log.add(nowMs, EventLog.Kind.LOOT, "Złowiono: " + items.get(fishing.lastCatchId()).name());
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
        pl.fawizja.pixel.item.ItemStack st = pc.backpack().get(slot);
        if (st != null && items.get(st.itemId()).key().equals("saddle")) return useSaddle(slot, nowMs);
        if (st != null && items.get(st.itemId()).key().equals("return_scroll")) return startRecall(nowMs);
        return spells.useItem(slot, nowMs);
    }

    /** Zwoj powrotu: 3 s skupienia (ruch albo obrazenia przerywaja), potem swiatynia miasta postaci; zwoj znika na koncu. */
    public static final long RECALL_MS = 3_000;
    private long recallAtMs = -1;
    private Position recallFrom;
    private int recallHealth;

    public boolean recalling() { return recallAtMs >= 0; }

    private CastResult startRecall(long nowMs) {
        if (nowMs < pzLockedUntil) {
            log.add(nowMs, EventLog.Kind.WARNING, "Po walce z graczem zwój nie działa jeszcze przez "
                    + ((pzLockedUntil - nowMs + 999) / 1000) + " s.");
            return CastResult.OK;
        }
        if (world.templeOf(pc.homeTown()) == null) return CastResult.OK;
        recallAtMs = nowMs + RECALL_MS;
        recallFrom = pc.position();
        recallHealth = pc.health();
        log.add(nowMs, EventLog.Kind.INFO, "Czytasz zwój powrotu... nie ruszaj się przez 3 s.");
        return CastResult.OK;
    }

    private void updateRecall(long nowMs) {
        if (recallAtMs < 0) return;
        if (!pc.position().equals(recallFrom) || pc.health() < recallHealth || pc.isDead() || nowMs < pzLockedUntil) {
            recallAtMs = -1;
            log.add(nowMs, EventLog.Kind.WARNING, "Skupienie przerwane - zwój nie zadziałał.");
            return;
        }
        recallHealth = Math.max(recallHealth, pc.health());
        if (nowMs < recallAtMs) return;
        recallAtMs = -1;
        int scroll = items.byKey("return_scroll").id();
        int slot = -1;
        for (int i = 0; i < pc.backpack().size(); i++) {
            if (pc.backpack().get(i) != null && pc.backpack().get(i).itemId() == scroll) slot = i;
        }
        if (slot < 0 || pc.backpack().takeFromSlot(slot, 1) == null) return;
        teleport(world.templeOf(pc.homeTown()), nowMs);
        log.add(nowMs, EventLog.Kind.SYSTEM, "Zwój rozsypuje się w pył. Jesteś w świątyni " + pc.homeTown() + ".");
    }

    /** Siodlo: oswaja konia na stale (przedmiot znika). */
    private CastResult useSaddle(int slot, long nowMs) {
        if (pc.hasMount()) {
            log.add(nowMs, EventLog.Kind.INFO, "Masz już wierzchowca. Wsiadasz przyciskiem KOŃ (klawisz M).");
            return CastResult.OK;
        }
        if (pc.backpack().takeFromSlot(slot, 1) == null) return CastResult.MISSING_ITEM;
        pc.setHasMount(true);
        log.add(nowMs, EventLog.Kind.SYSTEM, "Oswojono konia! Wsiadaj przyciskiem KOŃ (klawisz M): +"
                + PlayerCharacter.MOUNT_SPEED + " szybkości.");
        return CastResult.OK;
    }

    /** Wsiadanie i zsiadanie z wierzchowca (pod ziemia nie mozna jechac). */
    public void toggleMount(long nowMs) {
        if (!pc.hasMount()) {
            log.add(nowMs, EventLog.Kind.WARNING, "Nie masz wierzchowca - kup siodło u kupca i użyj go.");
            return;
        }
        if (pc.isMounted()) {
            pc.setMounted(false);
            log.add(nowMs, EventLog.Kind.INFO, "Zsiadasz z konia.");
            return;
        }
        if (pc.position().z() < 0) {
            log.add(nowMs, EventLog.Kind.WARNING, "Pod ziemią koń się nie zmieści.");
            return;
        }
        pc.setMounted(true);
        log.add(nowMs, EventLog.Kind.INFO, "Wsiadasz na konia (+" + PlayerCharacter.MOUNT_SPEED + " szybkości).");
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
        if (!achievementsChecked || nowMs - achievementsCheckedMs >= 1000 || nowMs < achievementsCheckedMs) checkAchievements(nowMs);
        if (nowMs - loginCheckedMs >= 60_000 || nowMs < loginCheckedMs) checkLoginReward(nowMs);
        updateRecall(nowMs);
        if (pc.isMounted() && pc.position().z() < 0) {
            pc.setMounted(false);
            log.add(nowMs, EventLog.Kind.INFO, "Zostawiasz konia przy zejściu pod ziemię.");
        }
        if (lastUpdateMs >= 0) {
            long dt = Math.min(1000, nowMs - lastUpdateMs);
            pc.tickRegeneration(dt);
            switch (pc.tickTorch(dt, torchId)) {
                case NEXT_LIT: log.add(nowMs, EventLog.Kind.INFO, "Pochodnia się wypaliła. Zapalasz następną."); break;
                case BURNED_OUT: log.add(nowMs, EventLog.Kind.WARNING, "Pochodnia się wypaliła. Nie masz kolejnej."); break;
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
            MoveResult r = pzLocked(before, requested, nowMs) ? MoveResult.BLOCKED : movement.step(pc, requested);
            if (r == MoveResult.MOVED || r == MoveResult.CHANGED_FLOOR) checkRegion(nowMs);
            if (r == MoveResult.MOVED) {
                stepFrom = before;
                stepStartMs = nowMs;
                stepDurationMs = MovementSystem.stepDurationMs(pc, requested);
                moving = true;
            } else if (r == MoveResult.CHANGED_FLOOR) {
                onFloorChanged(nowMs);
            } else if (r == MoveResult.LEVEL_TOO_LOW && nowMs - lastGateWarningMs > 3000) {
                lastGateWarningMs = nowMs;
                log.add(nowMs, EventLog.Kind.WARNING, "Strażnik: dalej wpuszczamy tylko od poziomu "
                        + world.requiredLevelAt(pc.position().translate(requested.dx, requested.dy)) + ".");
            }
        }
        ground.decay(nowMs);
        boolean night = isNight(nowMs);
        if (wasNight != null && night != wasNight) {
            log.add(nowMs, EventLog.Kind.INFO, night ? "Zapada noc. Nocne drapieżniki są groźniejsze."
                    : "Wstaje dzień.");
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
        if (m == null) log.add(nowMs, EventLog.Kind.INFO, "Brak celu w pobliżu.");
    }

    /** Wybor celu dotknieciem kafelka. Zwraca true, jesli trafiono potwora. */
    public boolean targetAt(Position p) {
        Monster m = monsters.monsterAt(p);
        if (m == null) return false;
        combat.setTarget(m);
        return true;
    }

    /**
     * Klient online: przewidywany krok (bez czekania na serwer). Tylko zwykly krok po wolnym kafelku -
     * schody, dziury i inne przejscia czekaja na serwer. Zwraca true, gdy krok sie zaczal.
     */
    /** Po walce z graczem: do tego czasu nie mozna wejsc do strefy ochronnej (mozna z niej wyjsc). */
    public void lockProtectionZonesUntil(long untilMs) { pzLockedUntil = Math.max(pzLockedUntil, untilMs); }

    public long protectionZoneLockedUntil() { return pzLockedUntil; }

    private boolean pzLocked(Position from, Direction d, long nowMs) {
        if (nowMs >= pzLockedUntil || world.isProtectionZone(from)) return false;
        Position to = from.translate(d.dx, d.dy);
        pl.fawizja.pixel.world.Transition t = world.transitionAt(to);
        if (!world.isProtectionZone(to) && (t == null || !world.isProtectionZone(t.to()))) return false;
        if (nowMs - lastPzWarningMs > 3000) {
            lastPzWarningMs = nowMs;
            log.add(nowMs, EventLog.Kind.WARNING, "Walczysz z graczem - strefa ochronna zamknięta jeszcze "
                    + ((pzLockedUntil - nowMs + 999) / 1000) + " s.");
        }
        return true;
    }

    public boolean predictStep(Direction d, long nowMs) {
        if (moving && nowMs - stepStartMs < stepDurationMs) return false;
        if (pc.isDead()) return false;
        facing = d;
        Position from = pc.position();
        if (!movement.canStep(from, d)) return false;
        Position to = from.translate(d.dx, d.dy);
        if (world.transitionAt(to) != null || world.requiredLevelAt(to) > pc.level()) return false;
        pc.setPosition(to);
        stepFrom = from;
        stepStartMs = nowMs;
        stepDurationMs = MovementSystem.stepDurationMs(pc, d);
        moving = true;
        return true;
    }

    /** Czy trwa krok (wg czasu, takze bez wywolan update - tryb online). */
    public boolean isStepping(long nowMs) { return moving && nowMs - stepStartMs < stepDurationMs; }

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
        if (z == 0) return "Jesteś na powierzchni.";
        return z > 0 ? "Piętro +" + z + "." : "Podziemia, poziom " + z + ".";
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
                if (openChest(nowMs)) break;
                if (!searchCorpse(nowMs)) log.add(nowMs, EventLog.Kind.WARNING, "Nie ma tu nic do użycia.");
        }
    }

    private pl.fawizja.pixel.treasure.TreasureChests chests;

    /** Skrzynie skarbow swiata (PixelGame, serwer, testy). */
    public void setChests(pl.fawizja.pixel.treasure.TreasureChests chests) { this.chests = chests; }

    public pl.fawizja.pixel.treasure.TreasureChests chests() { return chests; }

    /** Skrzynia obok gracza: zloto i przedmioty do plecaka, raz na postac. false = nie ma skrzyni obok. */
    public boolean openChest(long nowMs) {
        pl.fawizja.pixel.treasure.TreasureChests.Chest c = chests == null ? null : chests.near(pc.position());
        if (c == null) return false;
        if (pc.openedChests().contains(c.id())) {
            log.add(nowMs, EventLog.Kind.INFO, "Ta skrzynia jest już pusta.");
            return true;
        }
        int itemId = items.byKey(c.itemKey()).id();
        pl.fawizja.pixel.inventory.Container tx = pc.backpack().copy();
        boolean ok = pl.fawizja.pixel.economy.Money.add(tx, c.gold()).ok()
                && tx.add(new pl.fawizja.pixel.item.ItemStack(itemId, c.count())).ok();
        if (!ok) {
            log.add(nowMs, EventLog.Kind.WARNING, "Skrzynia pełna skarbów, ale plecak nie ma miejsca. Zrób miejsce i spróbuj znowu.");
            return true;
        }
        pc.backpack().commitFrom(tx);
        pc.openedChests().add(c.id());
        log.add(nowMs, EventLog.Kind.LOOT, "Skrzynia skarbów! " + String.format("%,d gp", c.gold()) + " i " + c.count() + "x "
                + items.get(itemId).name() + ".");
        return true;
    }

    public void toggleRun(long nowMs) {
        pc.setRunning(!pc.isRunning());
        log.add(nowMs, EventLog.Kind.INFO, pc.isRunning() ? "Bieganie włączone." : "Bieganie wyłączone.");
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
        return !ground.at(pc.position()).isEmpty() || corpseToSearch() != null;
    }

    /** Najblizsze cialo z lupem na kafelku gracza albo obok (to samo pietro). */
    public Position corpseToSearch() {
        Position me = pc.position();
        GroundItems.Corpse here = ground.corpseAt(me);
        if (here != null && here.hasLoot()) return me;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                Position p = me.translate(dx, dy);
                GroundItems.Corpse c = ground.corpseAt(p);
                if (c != null && c.hasLoot()) return p;
            }
        }
        return null;
    }

    /** Przeszukanie ciala obok: lup do plecaka (co sie zmiesci). Zwraca false, gdy nie ma czego szukac. */
    public boolean searchCorpse(long nowMs) {
        Position p = corpseToSearch();
        if (p == null) return false;
        List<ItemStack> taken = ground.lootCorpse(p, pc.backpack());
        if (!taken.isEmpty()) {
            StringBuilder b = new StringBuilder();
            for (ItemStack s : taken) {
                if (b.length() > 0) b.append(", ");
                b.append(describe(s));
            }
            log.add(nowMs, EventLog.Kind.LOOT, "Z ciała: " + b);
        }
        if (ground.corpseAt(p) != null && ground.corpseAt(p).hasLoot()) {
            log.add(nowMs, EventLog.Kind.WARNING, "Plecak jest pełny - reszta zostaje w ciele.");
        }
        return true;
    }

    public void pickUpHere(long nowMs) {
        Position p = pc.position();
        if (ground.at(p).isEmpty()) {
            searchCorpse(nowMs);
            return;
        }
        ItemStack top = ground.at(p).get(ground.at(p).size() - 1);
        InventoryResult r = ground.pickUp(p, ground.at(p).size() - 1, pc.backpack());
        if (r.ok()) {
            log.add(nowMs, EventLog.Kind.LOOT, "Podniesiono: " + describe(top));
        } else {
            log.add(nowMs, EventLog.Kind.WARNING, "Plecak jest pełny.");
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
            case OK: log.add(nowMs, EventLog.Kind.INFO, "Założono przedmiot."); break;
            case WRONG_VOCATION: log.add(nowMs, EventLog.Kind.WARNING, "Twoja profesja nie może tego używać."); break;
            case LEVEL_TOO_LOW: log.add(nowMs, EventLog.Kind.WARNING, "Za niski poziom."); break;
            case WRONG_SLOT_TYPE: log.add(nowMs, EventLog.Kind.WARNING, "Tego nie da się założyć."); break;
            default: log.add(nowMs, EventLog.Kind.WARNING, "Nie udało się: " + r);
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
