package pl.fawizja.pixel.game;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.combat.CombatFormulas;
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
    private final pl.fawizja.pixel.gathering.HerbGathering herbs;
    /** Zegar swiata; czas sesji 0 = 09:00 czasu gry. W trybie online czas poda serwer. */
    private WorldClock clock = new WorldClock(-9L * WorldClock.GAME_HOUR_MS, WorldClock.REAL_DAY_MS, 0.38f);
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

    /** Czas ostatniego kroku symulacji (ms). */
    public long lastUpdateMs() { return lastUpdateMs; }
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
        if (!sharedWorld) {
            // gra offline: skrzynie przesuniete przez te postac i ich zawartosc
            world.crates().reset();
            world.crates().restore(pc.crates(), items);
            world.wallLights().reset();
            world.wallLights().restore(pc.wallLights());
        }
        this.rng = rng;
        this.ground = sharedGround != null ? sharedGround : new GroundItems();
        this.items = items;
        this.recipes = recipes;
        this.world = world;
        this.pc = pc;
        this.movement = new MovementSystem(world, items.byKey("rope").id());
        this.torchId = items.byKey("torch").id();
        if (!world.isWalkable(pc.position())) {
            // poza mapa (stary zapis) albo w murze: swiatynia miasta postaci
            Position home = world.templeOf(pc.homeTown());
            pc.setPosition(home != null ? home : world.spawn());
        }
        pl.fawizja.pixel.world.BossQuest arenaOf = world.bossQuestByArena(pc.position());
        if (!sharedWorld && arenaOf != null) pc.setPosition(arenaOf.exitTarget()); // arena po wczytaniu jest pusta
        this.stepFrom = pc.position();
        this.monsters = monsters != null ? monsters
                : new MonsterManager(world, null, Collections.emptyList(), rng);
        this.combat = new CombatSystem(pc, world, items, this.monsters, ground, log, rng, minLevel);
        this.fishing = new FishingSystem(world, items, rng);
        this.herbs = new pl.fawizja.pixel.gathering.HerbGathering(world);
        this.spells = new SpellSystem(pc, world, items, spellRegistry, consumables, combat, this.monsters, log, rng);
        this.movement.setOccupancy(p -> this.monsters.monsterAt(p) != null || npcs.at(p) != null || dummies.at(p) || !mayEnter(p)
                || (!world.isProtectionZone(p) && otherPlayers.test(p)));
        this.combat.setKillListener(t -> {
            pc.bestiary().record(t.key(), t.boss());
            if (t.isElite()) pc.bestiary().recordElite();
            taskKill(t);
            checkWeeklyBoss(t, lastUpdateMs);
            checkAchievements(lastUpdateMs);
        });
        if (!sharedWorld) {
            // gra offline: wlasne areny wypraw na bossa (serwer podaje wspolne przez setBossQuests)
            bossRuntime = new BossQuestRuntime(world, items, this.monsters, rng);
            combat.setLootOwner(m -> bossRuntime != null && bossRuntime.ownsLoot(m));
            combat.setDeathListener(m -> {
                if (bossRuntime != null) bossRuntime.onMonsterDeath(m, bossHeroes.get(), lastUpdateMs);
            });
        }
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
            log.add(nowMs, EventLog.Kind.SYSTEM, "Zadanie dnia: pokonaj " + d.target() + "x " + monsterName(d.monster()) + whereToHunt(d.monster()) + ".");
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

    /** Zmiana zadania dnia u przewodnika (raz dziennie, za monety z plecaka). */
    public boolean dailyReroll(long nowMs) {
        if (talkTo(NpcRole.GUIDE, nowMs) == null) return false;
        pl.fawizja.pixel.daily.DailyTask d = pc.daily();
        long day = today();
        if (!d.canReroll(day)) {
            log.add(nowMs, EventLog.Kind.INFO, d.activeOn(day) && !d.claimed() ? "Zadanie dnia można zmienić tylko raz dziennie."
                    : "Najpierw weź zadanie dnia.");
            return false;
        }
        long price = pl.fawizja.pixel.daily.DailyTask.rerollPrice(pc.level());
        pl.fawizja.pixel.inventory.Container tx = pc.backpack().copy();
        if (Money.totalValue(tx) < price || !Money.pay(tx, price).ok()) {
            log.add(nowMs, EventLog.Kind.WARNING, "Zmiana zadania dnia kosztuje " + String.format("%,d gp", price) + " (monety w plecaku).");
            return false;
        }
        if (!d.reroll(pc.name(), day, pc.level(), monsters.spawnedTypes())) {
            log.add(nowMs, EventLog.Kind.INFO, "Nie mam dziś dla ciebie innego zadania.");
            return false;
        }
        pc.backpack().commitFrom(tx);
        log.add(nowMs, EventLog.Kind.SYSTEM, "Nowe zadanie dnia: pokonaj " + d.target() + "x " + monsterName(d.monster())
                + whereToHunt(d.monster()) + " (" + String.format("%,d gp", price) + ").");
        return true;
    }

    /** Zabicie liczone do zadan: zadanie dnia, zlecenie lowcy i zadania na potwory. */
    private void taskKill(pl.fawizja.pixel.monster.MonsterType t) {
        if (pc.daily().onKill(t.key(), today())) {
            log.add(lastUpdateMs, EventLog.Kind.SYSTEM, "Zadanie dnia wykonane! Wróć do przewodnika po nagrodę.");
        }
        if (pc.hunt().onKill(t.key())) {
            log.add(lastUpdateMs, EventLog.Kind.SYSTEM, "Zlecenie łowcy wykonane! Wróć do przewodnika po nagrodę.");
        }
        QuestType done = quests == null ? null : quests.onKill(pc, t.key());
        if (done != null) {
            log.add(lastUpdateMs, EventLog.Kind.SYSTEM, "Zadanie \"" + done.name() + "\": cel osiągnięty, wróć po nagrodę.");
        }
    }

    /** Potwor pokonany przez druzyne obok (serwer): liczy sie do zadan tego gracza jak wlasne zabicie. */
    public void partyKill(pl.fawizja.pixel.monster.MonsterType t) {
        taskKill(t);
    }

    /** Zlecenie lowcy: wziecie nowego, stan albo oddanie wykonanego (z nagroda). */
    public void huntAction(long nowMs) {
        if (talkTo(NpcRole.GUIDE, nowMs) == null) return;
        pl.fawizja.pixel.quest.HuntTask h = pc.hunt();
        if (!h.active()) {
            if (!h.assign(pc.level(), monsters.spawnedTypes(), rng)) {
                log.add(nowMs, EventLog.Kind.WARNING, "Nie mam teraz dla ciebie zlecenia.");
                return;
            }
            log.add(nowMs, EventLog.Kind.SYSTEM, "Zlecenie łowcy: pokonaj " + h.target() + "x " + monsterName(h.monster()) + whereToHunt(h.monster()) + ". Droga: /idź łowisko");
            return;
        }
        if (!h.done()) {
            log.add(nowMs, EventLog.Kind.INFO, "Zlecenie łowcy: " + monsterName(h.monster()) + " " + h.progress() + " / " + h.target()
                    + ". Porzucić możesz u przewodnika.");
            return;
        }
        pl.fawizja.pixel.monster.MonsterType t = monsterType(h.monster());
        long gold = t == null ? 0 : pl.fawizja.pixel.quest.HuntTask.rewardGold(t);
        long exp = t == null ? 0 : pl.fawizja.pixel.quest.HuntTask.rewardExp(t);
        if (gold > 0 && !pc.bank().credit(gold).ok()) return;
        h.complete();
        log.add(nowMs, EventLog.Kind.SYSTEM, "Nagroda za zlecenie łowcy: " + String.format("%,d gp", gold) + " na konto ("
                + h.completed() + ". oddane zlecenie).");
        if (exp > 0) combat.awardExperience(exp, "Zlecenie łowcy", nowMs);
    }

    public void huntDrop(long nowMs) {
        if (talkTo(NpcRole.GUIDE, nowMs) == null || !pc.hunt().active()) return;
        log.add(nowMs, EventLog.Kind.INFO, "Porzucono zlecenie: " + monsterName(pc.hunt().monster()) + ".");
        pc.hunt().drop();
    }

    private pl.fawizja.pixel.monster.MonsterType monsterType(String key) {
        return monsters.spawnedType(key);
    }

    /** Nazwa potwora do napisow (klucz, gdy rejestr nieznany). */
    public String monsterName(String key) {
        pl.fawizja.pixel.monster.MonsterType t = monsterType(key);
        return t == null ? key : t.name();
    }

    private long boostedDay = Long.MIN_VALUE;
    private pl.fawizja.pixel.monster.MonsterType boosted;

    /** Potwor dnia (wspolny dla wszystkich, zmiana o polnocy); ustawia tez premie w walce. */
    private long weeklyBossWeek = Long.MIN_VALUE;
    private pl.fawizja.pixel.monster.MonsterType weeklyBoss;

    /** Boss tygodnia (null bez bossow w swiecie). */
    public pl.fawizja.pixel.monster.MonsterType weeklyBoss() {
        long week = pl.fawizja.pixel.daily.WeeklyBoss.week(today());
        if (week != weeklyBossWeek) {
            weeklyBossWeek = week;
            weeklyBoss = monsters == null ? null : pl.fawizja.pixel.daily.WeeklyBoss.pick(week, monsters.spawnedTypes());
        }
        return weeklyBoss;
    }

    private long weeklyBossPlaceWeek = Long.MIN_VALUE;
    private Position weeklyBossPlace;

    /** Miejsce odradzania bossa tygodnia (znacznik na mapie) albo null. */
    public Position weeklyBossPlace() {
        long week = pl.fawizja.pixel.daily.WeeklyBoss.week(today());
        if (week != weeklyBossPlaceWeek) {
            weeklyBossPlaceWeek = week;
            weeklyBossPlace = null;
            pl.fawizja.pixel.monster.MonsterType boss = weeklyBoss();
            if (boss != null && monsters != null) {
                for (MonsterManager.BossStatus b : monsters.bossReport(lastUpdateMs < 0 ? 0 : lastUpdateMs)) {
                    if (b.name().equals(boss.name())) {
                        weeklyBossPlace = b.where();
                        break;
                    }
                }
            }
        }
        return weeklyBossPlace;
    }

    /** Pierwsze w tygodniu pokonanie bossa tygodnia: zloto na konto. */
    private void checkWeeklyBoss(pl.fawizja.pixel.monster.MonsterType t, long nowMs) {
        pl.fawizja.pixel.monster.MonsterType boss = weeklyBoss();
        long week = pl.fawizja.pixel.daily.WeeklyBoss.week(today());
        if (boss == null || !boss.key().equals(t.key()) || pc.weeklyBossWeek() == week) return;
        long gold = pl.fawizja.pixel.daily.WeeklyBoss.rewardGold(boss);
        if (!pc.bank().credit(gold).ok()) return;
        pc.setWeeklyBossWeek(week);
        log.add(nowMs, EventLog.Kind.SYSTEM, "Pokonano bossa tygodnia! Nagroda: " + String.format("%,d gp", gold) + " na konto.");
    }

    public pl.fawizja.pixel.monster.MonsterType boostedCreature() {
        long day = today();
        if (day != boostedDay) {
            boostedDay = day;
            boosted = monsters == null ? null : pl.fawizja.pixel.daily.BoostedCreature.pick(day, monsters.spawnedTypes());
            combat.setBoostedKey(boosted == null ? null : boosted.key());
        }
        return boosted;
    }

    /** Ogloszenie potwora dnia przy wejsciu i po polnocy. */
    private String announcedBoost;

    private void checkBoost(long nowMs) {
        pl.fawizja.pixel.monster.MonsterType b = boostedCreature();
        String key = b == null ? null : b.key();
        if (key == null || key.equals(announcedBoost)) return;
        announcedBoost = key;
        log.add(nowMs, EventLog.Kind.SYSTEM, "Potwór dnia: " + pl.fawizja.pixel.daily.BoostedCreature.describe(b) + ".");
    }

    private long achievementsCheckedMs;
    private boolean achievementsChecked;

    /** Nagroda za codzienne wejscie: sprawdzana przy pierwszym kroku i potem co minute (zmiana dnia w grze). */
    private long loginCheckedMs = Long.MIN_VALUE / 2;

    /** Ostrzezenia o koncu mikstur, run i amunicji (co 0,5 s). */
    private SupplyWatch supplies;
    private long suppliesCheckedMs = Long.MIN_VALUE / 2;

    private void checkSupplies(long nowMs) {
        if (supplies == null) supplies = new SupplyWatch(items);
        suppliesCheckedMs = nowMs;
        for (String w : supplies.check(pc.backpack(), !world.isProtectionZone(pc.position()))) log.add(nowMs, EventLog.Kind.WARNING, w);
    }

    /** Podpowiedzi dla nowych postaci (co sekunde). */
    private long tipsCheckedMs = Long.MIN_VALUE / 2;

    /** Wylaczenie podpowiedzi (wszystkie oznaczone jako pokazane). */
    public void disableTips(long nowMs) {
        for (Tips.Tip t : Tips.Tip.values()) pc.markTip(t.ordinal());
        log.add(nowMs, EventLog.Kind.INFO, "Podpowiedzi wyłączone.");
    }

    private void checkTips(long nowMs) {
        tipsCheckedMs = nowMs;
        if (pc.level() >= Tips.MAX_LEVEL || pc.isDead()) return;
        Tips.Situation s = new Tips.Situation(combat.threatNear(4), pc.bestiary().total(),
                100 * pc.health() / Math.max(1, pc.maxHealth()), pc.backpack().freeSlots(), ammoIdle(), isResting(nowMs));
        for (String t : Tips.due(pc, s)) log.add(nowMs, EventLog.Kind.INFO, t);
    }

    /** Bron dystansowa bez amunicji w gniezdzie, choc w plecaku lezy pasujaca (podpowiedz AMMO). */
    private boolean ammoIdle() {
        pl.fawizja.pixel.item.ItemStack w = pc.equipment().get(pl.fawizja.pixel.item.EquipSlot.WEAPON);
        pl.fawizja.pixel.item.ItemType weapon = w == null ? null : items.get(w.itemId());
        if (weapon == null || weapon.range() <= 1 || weapon.damageType() != pl.fawizja.pixel.combat.DamageType.PHYSICAL) return false;
        if (pl.fawizja.pixel.combat.CombatFormulas.usesNoAmmo(weapon)) return false;
        if (pc.equipment().get(pl.fawizja.pixel.item.EquipSlot.AMMO) != null) return false;
        for (pl.fawizja.pixel.item.ItemType t : items.all()) {
            if (t.category() == pl.fawizja.pixel.item.ItemCategory.AMMUNITION && pc.backpack().count(t.id()) > 0
                    && pc.equipment().canWear(t.id(), pc.vocation(), pc.level()).ok()) return true;
        }
        return false;
    }

    /**
     * Najblizsze legowisko potwora z niedokonczonego zlecenia lowcy, a bez niego z zadania dnia (to samo pietro);
     * null, gdy nie ma czego szukac albo legowisko jest na innym pietrze.
     */
    public pl.fawizja.pixel.monster.SpawnPoint huntingGround() {
        String key = pc.hunt().active() && !pc.hunt().done() ? pc.hunt().monster()
                : pc.daily().activeOn(today()) && !pc.daily().doneOn(today()) ? pc.daily().monster() : null;
        if (key == null) return null;
        pl.fawizja.pixel.monster.SpawnPoint best = null;
        Position me = pc.position();
        for (pl.fawizja.pixel.monster.SpawnPoint sp : monsters.spawns()) {
            if (!sp.monsterKey().equals(key) || sp.center().z() != me.z()) continue;
            if (best == null || sp.center().distanceTo(me) < best.center().distanceTo(me)) best = sp;
        }
        return best;
    }

    /** Odpoczynek: postac w strefie ochronnej, bez blokady po walce - szybsza regeneracja. */
    public boolean isResting(long nowMs) {
        return !pc.isDead() && nowMs >= pzLockedUntil && world.isProtectionZone(pc.position());
    }

    /** " (Krainy Varden)" - gdzie szukac potwora z zadania; pusty tekst, gdy nie ma go na mapie. */
    public String whereToHunt(String monsterKey) {
        String where = pl.fawizja.pixel.achievement.BestiaryCard.habitats(monsterKey, monsters.spawns(), world);
        return where.isEmpty() ? "" : " (" + where + ")";
    }

    /** Komunikaty o odrodzeniu bossow (co BossWatch.CHECK_MS; online liczy serwer). */
    private final BossWatch bossWatch = new BossWatch();
    private long bossesCheckedMs = Long.MIN_VALUE / 2;

    private void checkBosses(long nowMs) {
        bossesCheckedMs = nowMs;
        for (pl.fawizja.pixel.monster.MonsterManager.BossStatus b : bossWatch.update(monsters.bossReport(nowMs))) {
            log.add(nowMs, EventLog.Kind.SYSTEM, BossWatch.message(b, nowMs));
        }
    }

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

    /** Nagrody za progi poziomu: zloto na konto, zwoje nauki do plecaka (albo do depozytu, gdy brak miejsca). */
    public void checkLevelRewards(long nowMs) {
        if (pc.isDead()) return;
        int m;
        while ((m = pl.fawizja.pixel.character.LevelRewards.next(pc.rewardedLevel(), pc.level())) > 0) {
            long gold = pl.fawizja.pixel.character.LevelRewards.gold(m);
            if (!pc.bank().credit(gold).ok()) return; // pelne konto - sprobujemy pozniej
            pc.setRewardedLevel(m);
            String extra = "";
            int n = pl.fawizja.pixel.character.LevelRewards.scrolls(m);
            if (n > 0) {
                pl.fawizja.pixel.item.ItemStack st = new pl.fawizja.pixel.item.ItemStack(items.byKey("exp_scroll").id(), n);
                boolean bag = pc.backpack().canAdd(st.itemId(), n) && pc.backpack().add(st).ok();
                if (bag || pc.depot().add(st).ok()) extra = " i " + n + "x " + items.get(st.itemId()).name() + (bag ? "" : " (w depozycie)");
            }
            log.add(nowMs, EventLog.Kind.SYSTEM, "Nagroda za poziom " + m + ": " + String.format("%,d gp", gold) + " na konto" + extra + ".");
        }
        pc.setRewardedLevel(pc.level());
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
        List<Position> npcSpots = new java.util.ArrayList<>();
        for (pl.fawizja.pixel.npc.Npc n : npcs.all()) npcSpots.add(n.position());
        this.dummies = pl.fawizja.pixel.training.TrainingDummies.generate(world, npcSpots);
    }

    /** Manekiny treningowe w miastach (liczone z mapy i miejsc NPC; puste bez uslug miejskich). */
    private pl.fawizja.pixel.training.TrainingDummies dummies = new pl.fawizja.pixel.training.TrainingDummies(List.of());

    public pl.fawizja.pixel.training.TrainingDummies dummies() { return dummies; }

    /** Trening przy manekinie: co TRAIN_MS proba umiejetnosci broni, co druga takze tarczy; bez doswiadczenia. */
    public static final long TRAIN_MS = 3_000;
    private Position trainingAt;
    private Position trainingFrom;
    private long trainNextMs;
    private int trainTicks;

    public boolean training() { return trainingAt != null; }

    private boolean toggleTraining(long nowMs) {
        Position d = dummies.near(pc.position());
        if (d == null) return false;
        if (trainingAt != null) {
            stopTraining(nowMs, "Kończysz trening.");
            return true;
        }
        trainingAt = d;
        trainingFrom = pc.position();
        trainNextMs = nowMs + TRAIN_MS;
        trainTicks = 0;
        Skill sk = CombatFormulas.attackProfile(pc, items).skill();
        log.add(nowMs, EventLog.Kind.INFO, "Trenujesz przy manekinie: " + sk.label()
                + " (bez doświadczenia). Ruch albo UŻYJ kończy trening.");
        return true;
    }

    private void stopTraining(long nowMs, String why) {
        trainingAt = null;
        log.add(nowMs, EventLog.Kind.INFO, why);
    }

    private void updateTraining(long nowMs) {
        if (trainingAt == null) return;
        if (!pc.position().equals(trainingFrom) || pc.isDead()) {
            stopTraining(nowMs, "Trening przerwany.");
            return;
        }
        while (nowMs >= trainNextMs && trainingAt != null) {
            trainNextMs += TRAIN_MS;
            trainTicks++;
            Skill sk = CombatFormulas.attackProfile(pc, items).skill();
            trainSkill(nowMs, sk);
            if (trainTicks % 2 == 0) trainSkill(nowMs, Skill.SHIELDING);
        }
    }

    private void trainSkill(long nowMs, Skill sk) {
        if (pc.skills().addTries(sk, 1) > 0) {
            log.add(nowMs, EventLog.Kind.SYSTEM, sk.label() + ": poziom " + pc.skills().level(sk) + ".");
        }
    }

    public NpcRegistry npcs() { return npcs; }
    public ShopRegistry shops() { return shops; }
    public QuestSystem quests() { return quests; }

    /** NPC, z ktorym mozna teraz rozmawiac (albo null); skrzynia depozytu obok postaci ma pierwszenstwo. */
    public Npc npcNearby() {
        if (pc.isDead()) return null;
        Npc crate = crateNearby();
        if (crate != null) return crate;
        Npc locker = depotLockerNearby();
        return locker != null ? locker : npcs.nearest(pc.position(), world);
    }

    /** Skrzynia depozytu (jak w Tibii) na sasiednim polu: dziala jak bankier otwarty w widoku depozytu. */
    public Npc depotLockerNearby() {
        Position p = pc.position();
        pl.fawizja.pixel.world.FloorMap f = world.floor(p.z());
        if (lockerTile == null) lockerTile = tileId("depot_locker");
        if (f == null || lockerTile < 0) return null;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (f.get(pl.fawizja.pixel.world.Layer.OBJECT, p.x() + dx, p.y() + dy) != lockerTile) continue;
                // ta sama instancja dla tej samej skrzyni: okno rozmowy porownuje NPC przez ==
                return lockers.computeIfAbsent(p.translate(dx, dy), q -> new Npc(Npc.DEPOT_LOCKER, "Skrzynia depozytu",
                        NpcRole.BANKER, nearestTown(q), q, null, "Twój depozyt jest wspólny dla wszystkich miast.", "#7a5230"));
            }
        }
        return null;
    }

    /** Miasto najblizszego NPC (dzielnice poza murami, np. krolewska Varden), a bez NPC - najblizszej swiatyni. */
    private String nearestTown(Position p) {
        String best = "";
        int bd = Integer.MAX_VALUE;
        for (Npc n : npcs.all()) {
            int d = n.position().distanceTo(p);
            if (n.position().z() == p.z() && d < bd) { bd = d; best = n.town(); }
        }
        if (!best.isEmpty()) return best;
        for (pl.fawizja.pixel.world.Town t : world.towns()) {
            int d = t.temple().distanceTo(p);
            if (d < bd) { bd = d; best = t.name(); }
        }
        return best;
    }

    private Integer lockerTile;
    private final java.util.Map<Position, Npc> lockers = new java.util.HashMap<>();

    private int tileId(String key) {
        pl.fawizja.pixel.world.TileType t = world.tiles().byKeyOrNull(key);
        return t == null ? -1 : t.id();
    }

    // ------------------------------------------------------------------ domy

    private pl.fawizja.pixel.house.HouseRegistry houses = pl.fawizja.pixel.house.HouseRegistry.empty();
    /** Offline i klient online: wlasciciel to ta postac; serwer podaje wspolna liste (setHouses). */
    private pl.fawizja.pixel.house.HouseOwners houseOwners;
    /** Czas rzeczywisty (ms od 1970) do czynszu; testy i serwer podaja wlasny zegar. */
    private java.util.function.LongSupplier wallClock = System::currentTimeMillis;
    private long rentCheckedMs = Long.MIN_VALUE / 2;
    private boolean rentLowWarned;
    /** Co ile ms czasu gry sprawdzac czynsz. */
    static final long RENT_CHECK_MS = 60_000;

    public void setWallClock(java.util.function.LongSupplier clock) {
        wallClock = clock == null ? System::currentTimeMillis : clock;
    }

    private pl.fawizja.pixel.house.HouseOwners owners() {
        if (houseOwners == null) houseOwners = new pl.fawizja.pixel.house.CharacterHouseOwners(pc);
        return houseOwners;
    }

    /** Domy swiata i ich wlasciciele (serwer podaje wspolna liste; offline wlascicielem jest tylko ta postac). */
    public void setHouses(pl.fawizja.pixel.house.HouseRegistry houses, pl.fawizja.pixel.house.HouseOwners owners) {
        this.houses = java.util.Objects.requireNonNull(houses);
        if (owners != null) this.houseOwners = owners;
        if (pc.house() != null && (houses.get(pc.house()) == null || !pc.name().equals(owners().ownerOf(pc.house())))) {
            pc.setHouse(null);                                   // dom zniknal z mapy albo zostal odebrany
        }
        leaveForeignHouse();
    }

    public pl.fawizja.pixel.house.HouseRegistry houses() { return houses; }

    public String houseOwner(pl.fawizja.pixel.house.House h) { return owners().ownerOf(h.id()); }

    public java.util.List<String> houseGuests(pl.fawizja.pixel.house.House h) { return owners().guests(h.id()); }

    /** Klient online: cudze domy, do ktorych serwer wpuszcza te postac (id -> poziom dostepu). */
    public void setRemoteHouseAccess(java.util.Map<String, pl.fawizja.pixel.house.HouseAccess> access) {
        if (owners() instanceof pl.fawizja.pixel.house.CharacterHouseOwners c) c.setRemoteAccess(access);
    }

    /** Dostep tej postaci do domu. */
    public pl.fawizja.pixel.house.HouseAccess houseAccess(pl.fawizja.pixel.house.House h) {
        return h == null ? pl.fawizja.pixel.house.HouseAccess.NONE : owners().access(h.id(), pc.name());
    }

    /** Czy postac moze ruszac rzeczy na polu (wyrzucac, podnosic, pchac, otwierac skrzynie). Poza domami zawsze. */
    public boolean mayHandle(Position p) {
        pl.fawizja.pixel.house.House h = houses.at(p);
        return h == null || houseAccess(h).mayHandle();
    }

    /** Czy postac moze otworzyc albo zamknac drzwi lub okno (drzwi i okna domow: wlasciciel i pelny dostep). */
    public boolean mayUseOpening(Position p) {
        pl.fawizja.pixel.house.House h = houses.ofOpening(p);
        return h == null || houseAccess(h).mayHandle();
    }

    /** Co robi dotkniecie drzwi wejsciowych domu. */
    public enum DoorTap {
        /** Otwiera albo zamyka (wlasciciel na zewnatrz, gosc z pelnym dostepem). */
        TOGGLE,
        /** Okno z informacja o domu: wolny czy zajety, cena, czynsz, przycisk Kup (wszyscy inni). */
        INFO,
        /** Okno wlasciciela stojacego w domu: zapros, goscie, opusc dom. */
        OWNER_WINDOW
    }

    /** Dotkniecie pola p, gdy to drzwi wejsciowe domu; null, gdy to nie drzwi domu. */
    public DoorTap houseDoorTap(Position p) {
        pl.fawizja.pixel.house.House h = houses.byDoor(p);
        if (h == null) return null;
        pl.fawizja.pixel.house.HouseAccess a = houseAccess(h);
        if (a == pl.fawizja.pixel.house.HouseAccess.OWNER) return h.contains(pc.position()) ? DoorTap.OWNER_WINDOW : DoorTap.TOGGLE;
        return a == pl.fawizja.pixel.house.HouseAccess.FULL ? DoorTap.TOGGLE : DoorTap.INFO;
    }

    /** Stan domu do okna przy drzwiach (czynsz i goscie tylko dla wlasciciela). */
    public pl.fawizja.pixel.house.HouseView houseView(pl.fawizja.pixel.house.House h) {
        String owner = owners().ownerOf(h.id());
        pl.fawizja.pixel.house.HouseAccess a = houseAccess(h);
        boolean mine = a == pl.fawizja.pixel.house.HouseAccess.OWNER;
        long until = mine ? owners().rentPaidUntil(h.id()) : 0;
        return new pl.fawizja.pixel.house.HouseView(h.id(), owner, a, until > 0 ? Math.max(0, until - wallClock.getAsLong()) : -1,
                mine ? owners().rentMissed(h.id()) : 0, mine ? owners().guestLevels(h.id()) : java.util.Map.of());
    }

    /** Wlasciciel wpuszcza goscia z pelnym dostepem. */
    public boolean addHouseGuest(String name, long nowMs) {
        return addHouseGuest(name, pl.fawizja.pixel.house.HouseAccess.FULL, nowMs);
    }

    /** Wlasciciel wpuszcza goscia (pelny dostep albo samo wejscie) albo zmienia jego poziom. */
    public boolean addHouseGuest(String name, pl.fawizja.pixel.house.HouseAccess level, long nowMs) {
        pl.fawizja.pixel.house.House h = houses.get(pc.house());
        String who = name == null ? "" : name.trim();
        String msg = null;
        pl.fawizja.pixel.house.HouseAccess now = h == null ? null : owners().guestAccess(h.id(), who);
        if (h == null) msg = "Nie masz domu.";
        else if (who.isEmpty()) msg = "Podaj imię: /dom wpuść Imię.";
        else if (who.equalsIgnoreCase(pc.name())) msg = "To twój dom.";
        else if (now == level) msg = who + " już ma " + level.label() + " do domu.";
        else if (now == pl.fawizja.pixel.house.HouseAccess.NONE
                && owners().guests(h.id()).size() >= pl.fawizja.pixel.house.HouseOwners.MAX_GUESTS) {
            msg = "Dom może mieć najwyżej " + pl.fawizja.pixel.house.HouseOwners.MAX_GUESTS + " gości.";
        }
        if (msg == null && !owners().addGuest(h.id(), who, level)) msg = "Nie udało się dodać gościa.";
        if (msg != null) {
            log.add(nowMs, EventLog.Kind.WARNING, msg);
            return false;
        }
        log.add(nowMs, EventLog.Kind.SYSTEM, level == pl.fawizja.pixel.house.HouseAccess.FULL
                ? who + " może teraz wchodzić do domu " + h.name() + " i korzystać ze skrzyń, drzwi i przedmiotów (pełny dostęp)."
                : who + " może teraz wchodzić do domu " + h.name() + " (tylko wejście: bez skrzyń, drzwi i przedmiotów).");
        return true;
    }

    /** Wlasciciel odbiera gosciowi wstep. */
    public boolean removeHouseGuest(String name, long nowMs) {
        pl.fawizja.pixel.house.House h = houses.get(pc.house());
        if (h == null || !owners().removeGuest(h.id(), name)) {
            log.add(nowMs, EventLog.Kind.WARNING, h == null ? "Nie masz domu." : name + " nie jest gościem twojego domu.");
            return false;
        }
        log.add(nowMs, EventLog.Kind.SYSTEM, name.trim() + " nie może już wchodzić do domu " + h.name() + ".");
        return true;
    }

    /** Opis domu postaci: nazwa, miasto, czynsz, goscie. */
    public String houseInfo() {
        pl.fawizja.pixel.house.House h = houses.get(pc.house());
        if (h == null) return "Nie masz domu. Kupisz go przy drzwiach wolnego domu albo u zarządcy domów (za złoto z banku).";
        java.util.List<String> g = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, pl.fawizja.pixel.house.HouseAccess> e : owners().guestLevels(h.id()).entrySet()) {
            g.add(e.getKey() + (e.getValue() == pl.fawizja.pixel.house.HouseAccess.ENTRY ? " (wejście)" : " (pełny)"));
        }
        long until = owners().rentPaidUntil(h.id());
        String due = until > 0 ? ", następna płatność za " + pl.fawizja.pixel.house.HouseRent.describeIn(until - wallClock.getAsLong()) : "";
        return String.format("Twój dom: %s (%s, %d pól, cena %,d gp, czynsz %,d gp tygodniowo%s). Goście: %s.", h.name(), h.town(),
                h.size(), h.price(), pl.fawizja.pixel.house.HouseRent.rent(h), due,
                g.isEmpty() ? "nikt (/dom wpuść Imię)" : String.join(", ", g));
    }

    /** Ponowne sprawdzenie wstepu (po wyrzuceniu goscia albo zmianie wlasciciela): kto nie moze, wychodzi przez drzwi. */
    public void checkHouseAccess() { leaveForeignHouse(); }

    /** Dom po sprzedazy innemu graczowi (serwer). */
    public void setOwnHouse(String id) {
        pc.setHouse(id);
        leaveForeignHouse();
    }

    /** Czy postac moze wejsc na pole (wnetrza domow tylko dla wlasciciela). */
    /** Pola zajete przez innych graczy (serwer). Poza strefa ochronna gracze sie blokuja, w PZ mozna przejsc, jak w Tibii. */
    private java.util.function.Predicate<Position> otherPlayers = p -> false;

    public void setOtherPlayers(java.util.function.Predicate<Position> occupied) {
        otherPlayers = occupied == null ? p -> false : occupied;
    }

    public boolean mayEnter(Position p) {
        pl.fawizja.pixel.house.House h = houses.at(p);
        return h == null || owners().mayEnter(h.id(), pc.name());
    }

    private void leaveForeignHouse() {
        pl.fawizja.pixel.house.House h = houses.at(pc.position());
        if (h != null && !mayEnter(pc.position())) pc.setPosition(h.door());
    }

    /** U zarzadcy domow: kupno domu w jego miescie za zloto z banku (jeden dom na postac). */
    public boolean buyHouse(String id, long nowMs) {
        Npc n = talkTo(NpcRole.HOUSING, nowMs);
        if (n == null) return false;
        pl.fawizja.pixel.house.House h = houses.get(id);
        if (h == null || !h.town().equalsIgnoreCase(n.town())) {
            log.add(nowMs, EventLog.Kind.WARNING, "Ten zarządca nie sprzedaje takiego domu.");
            return false;
        }
        return claimHouse(h, nowMs);
    }

    /** Przy drzwiach wolnego domu (okno domu, przycisk Kup): kupno za zloto z banku. */
    public boolean buyHouseAtDoor(String id, long nowMs) {
        pl.fawizja.pixel.house.House h = houses.get(id);
        if (h == null) return false;
        if (pc.position().z() != h.z() || pc.position().distanceTo(h.door()) > 1) {
            log.add(nowMs, EventLog.Kind.WARNING, "Podejdź do drzwi domu " + h.name() + ".");
            return false;
        }
        return claimHouse(h, nowMs);
    }

    private boolean claimHouse(pl.fawizja.pixel.house.House h, long nowMs) {
        String msg = null;
        if (pc.house() != null) msg = "Masz już dom. Najpierw go opuść.";
        else if (owners().ownerOf(h.id()) != null) msg = "Ten dom ma już właściciela.";
        else if (pc.bank().balance() < h.price()) msg = String.format("Dom kosztuje %,d gp, a na koncie masz %,d gp.", h.price(), pc.bank().balance());
        if (msg != null) {
            log.add(nowMs, EventLog.Kind.WARNING, msg);
            return false;
        }
        if (!owners().claim(h.id(), pc.name())) {
            log.add(nowMs, EventLog.Kind.WARNING, "Ten dom ma już właściciela.");
            return false;
        }
        pc.bank().debit(h.price());
        pc.setHouse(h.id());
        owners().setRent(h.id(), wallClock.getAsLong() + pl.fawizja.pixel.house.HouseRent.period(), 0);   // pierwszy tydzien w cenie
        rentLowWarned = false;
        log.add(nowMs, EventLog.Kind.SYSTEM, String.format("Kupiono dom %s za %,d gp z konta. Czynsz %,d gp tygodniowo pobierany "
                + "automatycznie z banku. Gości zaprosisz przy drzwiach od środka.", h.name(), h.price(), pl.fawizja.pixel.house.HouseRent.rent(h)));
        return true;
    }

    /** Miasto odkupuje dom za tyle procent ceny. */
    public static final int HOUSE_SELLBACK_PERCENT = 50;

    /** U zarzadcy domow: sprzedaz domu miastu (polowa ceny na konto); postac stojaca w srodku wychodzi przez drzwi. */
    public boolean leaveHouse(long nowMs) {
        if (talkTo(NpcRole.HOUSING, nowMs) == null) return false;
        pl.fawizja.pixel.house.House h = houses.get(pc.house());
        if (h == null) {
            log.add(nowMs, EventLog.Kind.WARNING, "Nie masz domu.");
            return false;
        }
        return abandonHouse(h, nowMs);
    }

    /** Okno wlasciciela przy drzwiach: "Opusc dom" - jak sprzedaz miastu u zarzadcy. */
    public boolean leaveHouseAtDoor(long nowMs) {
        pl.fawizja.pixel.house.House h = houses.get(pc.house());
        if (h == null) {
            log.add(nowMs, EventLog.Kind.WARNING, "Nie masz domu.");
            return false;
        }
        if (!h.contains(pc.position()) && (pc.position().z() != h.z() || pc.position().distanceTo(h.door()) > 1)) {
            log.add(nowMs, EventLog.Kind.WARNING, "Podejdź do drzwi swojego domu.");
            return false;
        }
        return abandonHouse(h, nowMs);
    }

    private boolean abandonHouse(pl.fawizja.pixel.house.House h, long nowMs) {
        int moved = vacate(h, pc);
        owners().release(h.id());
        pc.setHouse(null);
        leaveForeignHouse();
        long refund = h.price() * HOUSE_SELLBACK_PERCENT / 100;
        pc.bank().credit(refund);
        log.add(nowMs, EventLog.Kind.SYSTEM, String.format("Sprzedano dom %s miastu za %,d gp (na konto).", h.name(), refund)
                + (moved > 0 ? " Rzeczy z domu (" + moved + ") są w depozycie u bankiera." : ""));
        return true;
    }

    /** Rzeczy ze skrzyn i podlogi domu do depozytu wlasciciela. */
    private int vacate(pl.fawizja.pixel.house.House h, PlayerCharacter owner) {
        if (openCrate != null && h.contains(openCrate)) openCrate = null;
        int moved = pl.fawizja.pixel.house.HouseRent.moveBelongings(h, world.crates(), ground, owner.depot(), owner.bank(), items);
        crateWindows.clear();
        if (moved > 0 && !sharedWorld) pc.setCrates(world.crates().encode());
        return moved;
    }

    /**
     * Czynsz za dom (co tydzien czasu rzeczywistego, z konta): pobranie, ostrzezenie przy braku zlota, po dwoch
     * nieoplaconych platnosciach dom wraca do miasta, a rzeczy z niego ida do depozytu. Wolane co minute w update().
     */
    public void checkHouseRent(long nowMs) {
        pl.fawizja.pixel.house.House h = houses.get(pc.house());
        if (h == null || !pc.name().equals(owners().ownerOf(h.id()))) return;
        long now = wallClock.getAsLong();
        long until = owners().rentPaidUntil(h.id());
        int missed = owners().rentMissed(h.id());
        long rent = pl.fawizja.pixel.house.HouseRent.rent(h);
        pl.fawizja.pixel.house.HouseRent.Result r = until > 0 && now < until && missed == 0 ? null
                : pl.fawizja.pixel.house.HouseRent.settle(until, missed, now, pl.fawizja.pixel.house.HouseRent.period(), rent, pc.bank());
        if (r != null && r.evicted()) {
            int moved = vacate(h, pc);
            owners().release(h.id());
            pc.setHouse(null);
            leaveForeignHouse();
            log.add(nowMs, EventLog.Kind.WARNING, "Czynsz za dom " + h.name() + " nie był płacony " + pl.fawizja.pixel.house.HouseRent.MAX_MISSED
                    + " razy - dom wrócił do miasta." + (moved > 0 ? " Rzeczy z domu (" + moved + ") są w depozycie u bankiera." : ""));
            return;
        }
        if (r != null) {
            if (r.changed(until, missed)) owners().setRent(h.id(), r.paidUntil(), r.missed());
            if (r.charged() > 0) {
                log.add(nowMs, EventLog.Kind.SYSTEM, String.format("Pobrano czynsz za dom %s: %,d gp z konta. Saldo: %,d gp.",
                        h.name(), r.charged(), pc.bank().balance()));
            }
            if (r.newlyMissed() > 0) {
                log.add(nowMs, EventLog.Kind.WARNING, String.format("Brak złota na czynsz za dom %s (%,d gp, na koncie %,d gp)! Wpłać złoto do "
                        + "banku - kolejna nieopłacona płatność i dom wróci do miasta, a rzeczy trafią do depozytu.", h.name(),
                        rent * (r.missed() + 1), pc.bank().balance()));
                rentLowWarned = true;
            }
        }
        if (pc.bank().balance() >= rent * (owners().rentMissed(h.id()) + 1)) {
            rentLowWarned = false;
        } else if (!rentLowWarned) {
            rentLowWarned = true;
            log.add(nowMs, EventLog.Kind.WARNING, String.format("Na koncie masz %,d gp, a czynsz za dom %s to %,d gp (termin za %s). Wpłać złoto do banku.",
                    pc.bank().balance(), h.name(), rent, pl.fawizja.pixel.house.HouseRent.describeIn(owners().rentPaidUntil(h.id()) - now)));
        }
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

    /** Docelowa liczba mikstur zdrowia i many po uzupelnieniu u kupca. */
    public static final int REFILL_TO = 20;

    /**
     * Uzupelnienie zapasow jednym dotknieciem (tyle, na ile starczy monet): mikstury zdrowia i many oraz noszone runy
     * do {@link #REFILL_TO} sztuk, noszona amunicja do {@link #REFILL_AMMO_TO}.
     * Zwraca liczbe kupionych sztuk.
     */
    /** Docelowa liczba sztuk amunicji po uzupelnieniu. */
    public static final int REFILL_AMMO_TO = 100;

    private static boolean isRefillable(pl.fawizja.pixel.item.ItemType t) {
        return t != null && (t.category() == pl.fawizja.pixel.item.ItemCategory.POTION || t.category() == pl.fawizja.pixel.item.ItemCategory.RUNE
                || t.category() == pl.fawizja.pixel.item.ItemCategory.AMMUNITION);
    }

    static int refillTarget(pl.fawizja.pixel.item.ItemType t) {
        return t.category() == pl.fawizja.pixel.item.ItemCategory.AMMUNITION ? REFILL_AMMO_TO : REFILL_TO;
    }

    public int refillSupplies(long nowMs) {
        Npc n = talkTo(NpcRole.MERCHANT, nowMs);
        if (n == null) return 0;
        Shop shop = shopOf(n);
        List<String> bought = new java.util.ArrayList<>();
        int total = 0;
        boolean poor = false;
        // mikstury zdrowia i many zawsze (najsilniejsze dozwolone z tego sklepu), a do tego kazde mikstury, runy
        // i amunicja, ktore postac ma przy sobie
        java.util.Set<Integer> ids = new java.util.LinkedHashSet<>();
        for (String[] tiers : new String[][] {HP_TIERS, MP_TIERS}) {
            for (String key : tiers) {
                if (!items.containsKey(key)) continue;
                pl.fawizja.pixel.item.ItemType t = items.byKey(key);
                boolean allowed = pc.level() >= t.minLevel() && t.vocations().contains(pc.vocation());
                if (allowed && shop != null && shop.offer(t.id()) != null && shop.offer(t.id()).sold()) {
                    ids.add(t.id());
                    break;
                }
            }
        }
        for (ItemStack st : pc.backpack().contents()) if (st != null && isRefillable(items.get(st.itemId()))) ids.add(st.itemId());
        ItemStack ammo = pc.equipment().get(pl.fawizja.pixel.item.EquipSlot.AMMO);
        if (ammo != null && isRefillable(items.get(ammo.itemId()))) ids.add(ammo.itemId());
        for (int id : ids) {
            pl.fawizja.pixel.item.ItemType t = items.get(id);
            if (t == null || shop == null || shop.offer(t.id()) == null || !shop.offer(t.id()).sold()) continue;
            int carried = pc.backpack().count(t.id()) + (ammo != null && ammo.itemId() == t.id() ? ammo.count() : 0);
            int want = refillTarget(t) - carried;
            if (want <= 0) continue;
            long price = shop.offer(t.id()).buyPrice();
            int afford = (int) Math.min(want, price <= 0 ? want : pl.fawizja.pixel.economy.Money.totalValue(pc.backpack()) / price);
            if (afford < want) poor = true;
            if (afford > 0 && TradeSystem.buy(pc.backpack(), items, shop, t.id(), afford).ok()) {
                bought.add(afford + "x " + t.name());
                total += afford;
            } else if (afford > 0) {
                poor = true;
            }
        }
        if (total > 0) {
            log.add(nowMs, EventLog.Kind.LOOT, "Kupiono: " + String.join(", ", bought) + (poor ? " (zabrakło monet na resztę)." : "."));
        } else {
            log.add(nowMs, poor ? EventLog.Kind.WARNING : EventLog.Kind.INFO,
                    poor ? "Za mało monet w plecaku na zapasy." : "Masz już pełne zapasy (" + REFILL_TO + " mikstur i run, "
                            + REFILL_AMMO_TO + " sztuk amunicji).");
        }
        return total;
    }

    public TradeResult sellAll(int itemId, long nowMs) {
        Npc n = talkTo(NpcRole.MERCHANT, nowMs);
        if (n == null) return TradeResult.TOO_FAR;
        long value = TradeSystem.sellValue(pc.backpack(), shopOf(n), itemId);
        int count = pc.backpack().countPlain(itemId);
        TradeResult r = TradeSystem.sellAll(pc.backpack(), shopOf(n), itemId);
        log.add(nowMs, r.ok() ? EventLog.Kind.LOOT : EventLog.Kind.WARNING,
                r.ok() ? "Sprzedano: " + count + "x " + items.get(itemId).name() + " za " + value + " gp" : r.message());
        return r;
    }

    /**
     * Sprzedaz calego lupu (materialy skupowane przez tego kupca) jednym dotknieciem. Pomija przedmioty
     * potrzebne do aktywnych zadan zbierania; zwraca liczbe sprzedanych sztuk.
     */
    public int sellLoot(long nowMs) {
        Npc n = talkTo(NpcRole.MERCHANT, nowMs);
        if (n == null) return 0;
        java.util.Set<Integer> keep = questItems();
        java.util.Set<Integer> ids = new java.util.LinkedHashSet<>();
        for (ItemStack s : pc.backpack().contents()) {
            if (s != null && items.get(s.itemId()).category() == pl.fawizja.pixel.item.ItemCategory.MATERIAL) ids.add(s.itemId());
        }
        int sold = 0;
        long total = 0;
        boolean kept = false;
        for (int id : ids) {
            if (keep.contains(id)) {
                kept = true;
                continue;
            }
            long value = TradeSystem.sellValue(pc.backpack(), shopOf(n), id);
            int count = pc.backpack().countPlain(id);
            if (value <= 0 || count <= 0) continue;
            if (TradeSystem.sellAll(pc.backpack(), shopOf(n), id).ok()) {
                sold += count;
                total += value;
            }
        }
        if (sold == 0) {
            log.add(nowMs, EventLog.Kind.INFO, n.name() + ": nie masz łupu, który skupuję" + (kept ? " (przedmioty do zadań i zachowane zostają)." : "."));
        } else {
            log.add(nowMs, EventLog.Kind.LOOT, "Sprzedano łup: " + sold + " szt. za " + String.format("%,d gp", total)
                    + (kept ? ". Przedmioty do zadań i zachowane zostają w plecaku." : "."));
        }
        return sold;
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
    /** Przedmioty potrzebne do aktywnych zadan zbierania i oznaczone "Zachowaj" (nie sprzedajemy ich ani nie odkladamy hurtem). */
    private java.util.Set<Integer> questItems() {
        java.util.Set<Integer> keep = new java.util.HashSet<>(pc.keptItems());
        if (quests == null) return keep;
        for (String key : pc.quests().active().keySet()) {
            pl.fawizja.pixel.quest.QuestType q = quests.registry().get(key);
            if (q != null && q.kind() == pl.fawizja.pixel.quest.QuestKind.COLLECT && items.byKey(q.target()) != null) {
                keep.add(items.byKey(q.target()).id());
            }
        }
        return keep;
    }

    /** U bankiera: caly lup (materialy) z plecaka do depozytu jednym dotknieciem, bez przedmiotow do zadan. */
    public int depotStoreLoot(long nowMs) {
        if (talkTo(NpcRole.BANKER, nowMs) == null) return 0;
        java.util.Set<Integer> keep = questItems();
        java.util.Set<Integer> ids = new java.util.LinkedHashSet<>();
        for (ItemStack s : pc.backpack().contents()) {
            if (s != null && items.get(s.itemId()).category() == pl.fawizja.pixel.item.ItemCategory.MATERIAL && !keep.contains(s.itemId())) {
                ids.add(s.itemId());
            }
        }
        if (ids.isEmpty()) {
            log.add(nowMs, EventLog.Kind.INFO, "Nie masz łupu do odłożenia" + (keep.isEmpty() ? "." : " (przedmioty do zadań i zachowane zostają)."));
            return 0;
        }
        int moved = 0;
        for (int id : ids) moved += moveAll(pc.backpack(), pc.depot(), id, nowMs, "Odłożono do depozytu", "Depozyt jest pełny.");
        return moved;
    }

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
        // stos po stosie (zachowuje ulepszenia); miejsce sprawdzone wyzej
        int moved = 0;
        for (int i = 0; i < from.size() && moved < n; i++) {
            ItemStack s = from.get(i);
            if (s == null || s.itemId() != itemId) continue;
            ItemStack part = from.takeFromSlot(i, Math.min(s.count(), n - moved));
            if (!to.add(part).ok()) {
                from.add(part); // nie powinno sie zdarzyc - oddajemy
                break;
            }
            moved += part.count();
        }
        if (moved <= 0) return 0;
        n = moved;
        String name = items.get(itemId).name();
        log.add(nowMs, EventLog.Kind.INFO, done + ": " + n + "x " + name + (n < have ? " (reszta się nie zmieściła)" : "") + ".");
        return n;
    }

    private static final java.util.Set<String> HEAL_WORDS = java.util.Set.of("help", "heal", "ulecz", "pomocy", "pomoc", "lecz");

    /** Kaplan: pelne leczenie i mana (tylko w swiatyni, wiec poza walka). */
    public void priestHeal(long nowMs) {
        if (talkTo(NpcRole.PRIEST, nowMs) == null) return;
        pc.heal(pc.maxHealth());
        pc.restoreMana(pc.maxMana());
        log.add(nowMs, EventLog.Kind.INFO, "Kapłan cię uleczył.");
    }

    /**
     * Woznica lub kapitan: przejazd do woznicy w innym miescie albo rejs do portu z trasy statku, za monety
     * z plecaka. Nie w blokadzie po walce z graczem. Postac staje na wolnym polu obok NPC docelowego.
     */
    public boolean travel(String town, long nowMs) {
        Npc n = npcNearby();
        if (n == null || (n.role() != NpcRole.CARRIER && n.role() != NpcRole.CAPTAIN)) {
            log.add(nowMs, EventLog.Kind.WARNING, TradeResult.TOO_FAR.message());
            return false;
        }
        boolean ship = n.role() == NpcRole.CAPTAIN;
        if (ship && !pc.isPremium()) {
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + ": rejsy tylko dla kont premium. Premium kupisz u króla w zamku Varden.");
            return false;
        }
        pl.fawizja.pixel.npc.Travel.Route route = null;
        for (pl.fawizja.pixel.npc.Travel.Route r : pl.fawizja.pixel.npc.Travel.routes(npcs, n)) {
            if (r.to().town().equalsIgnoreCase(town)) route = r;
        }
        if (route == null) {
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + (ship ? ": tam nie pływam." : ": tam nie jeżdżę."));
            return false;
        }
        if (nowMs < pzLockedUntil) {
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + ": po walce z graczem nie zabiorę cię jeszcze przez "
                    + ((pzLockedUntil - nowMs + 999) / 1000) + " s.");
            return false;
        }
        Position dest = null;
        for (int[] d : new int[][] {{0, 1}, {-1, 0}, {1, 0}, {0, -1}}) {
            Position q = route.to().position().translate(d[0], d[1]);
            if (world.isWalkable(q) && npcs.at(q) == null && !dummies.at(q) && world.transitionAt(q) == null) {
                dest = q;
                break;
            }
        }
        if (dest == null) return false;
        if (world.requiredLevelAt(dest) > pc.level()) {
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + ": " + route.to().town() + " jest dla postaci od poziomu "
                    + world.requiredLevelAt(dest) + ".");
            return false;
        }
        pl.fawizja.pixel.inventory.Container tx = pc.backpack().copy();
        if (Money.totalValue(tx) < route.price() || !Money.pay(tx, route.price()).ok()) {
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + (ship ? ": rejs do " : ": przejazd do ") + route.to().town() + " kosztuje "
                    + route.price() + " gp (monety w plecaku).");
            return false;
        }
        pc.backpack().commitFrom(tx);
        teleport(dest, nowMs);
        log.add(nowMs, EventLog.Kind.SYSTEM, (ship ? "Po rejsie (" : "Po podróży (") + route.price() + " gp) jesteś: "
                + route.to().town() + ".");
        return true;
    }

    /** Cena konta premium i promocji u krola (z konta w banku). */
    public static final long PREMIUM_PRICE = 500_000, PROMOTION_PRICE = 1_000_000;
    /** Promocja od tego poziomu (jak w Tibii od 20). */
    public static final int PROMOTION_LEVEL = 20;
    private static final java.util.Set<String> PREMIUM_WORDS = java.util.Set.of("premium", "konto premium");
    private static final java.util.Set<String> PROMOTION_WORDS = java.util.Set.of("promocja", "promocję", "promocje", "promotion", "promote");

    /** Krol: konto premium za zloto z banku - rejsy duzymi statkami miedzy portami. */
    public boolean buyPremium(long nowMs) {
        Npc n = talkTo(NpcRole.KING, nowMs);
        if (n == null) return false;
        if (pc.isPremium()) {
            log.add(nowMs, EventLog.Kind.INFO, n.name() + ": masz już konto premium.");
            return false;
        }
        if (!pc.bank().debit(PREMIUM_PRICE).ok()) {
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + String.format(": konto premium kosztuje %,d gp z banku.", PREMIUM_PRICE));
            return false;
        }
        pc.setPremium(true);
        log.add(nowMs, EventLog.Kind.SYSTEM, n.name() + String.format(": od dziś masz konto premium (%,d gp z banku). Kapitanowie zabiorą cię na rejs.", PREMIUM_PRICE));
        return true;
    }

    /** Krol: promocja profesji (premium, poziom 20+) - znak przy postaci i szybsza regeneracja. */
    public boolean buyPromotion(long nowMs) {
        Npc n = talkTo(NpcRole.KING, nowMs);
        if (n == null) return false;
        if (pc.isPromoted()) {
            log.add(nowMs, EventLog.Kind.INFO, n.name() + ": już jesteś " + pc.vocationTitle() + ".");
            return false;
        }
        if (!pc.isPremium()) {
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + ": promocję daję tylko posiadaczom konta premium.");
            return false;
        }
        if (pc.level() < PROMOTION_LEVEL) {
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + ": wróć od poziomu " + PROMOTION_LEVEL + ".");
            return false;
        }
        if (!pc.bank().debit(PROMOTION_PRICE).ok()) {
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + String.format(": promocja kosztuje %,d gp z banku.", PROMOTION_PRICE));
            return false;
        }
        pc.setPromoted(true);
        log.add(nowMs, EventLog.Kind.SYSTEM, n.name() + ": mianuję cię " + pc.vocationTitle()
                + String.format("! (%,d gp z banku) Regenerujesz się o połowę szybciej.", PROMOTION_PRICE));
        return true;
    }

    /** Kaplan: blogoslawienstwo za monety z plecaka - najblizsza smierc zabierze polowe straty doswiadczenia. */
    public boolean priestBless(long nowMs) {
        Npc n = talkTo(NpcRole.PRIEST, nowMs);
        if (n == null) return false;
        if (pc.isBlessed()) {
            log.add(nowMs, EventLog.Kind.INFO, n.name() + ": już jesteś pobłogosławiony.");
            return false;
        }
        long price = pl.fawizja.pixel.balance.Balance.blessingPrice(pc.level());
        pl.fawizja.pixel.inventory.Container tx = pc.backpack().copy();
        if (Money.totalValue(tx) < price || !Money.pay(tx, price).ok()) {
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + ": błogosławieństwo kosztuje " + price + " gp (monety w plecaku).");
            return false;
        }
        pc.backpack().commitFrom(tx);
        pc.setBlessed(true);
        log.add(nowMs, EventLog.Kind.SYSTEM, "Otrzymujesz błogosławieństwo (" + price
                + " gp). Najbliższa śmierć zabierze połowę doświadczenia mniej.");
        return true;
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
        if (n.role() == NpcRole.PRIEST && HEAL_WORDS.contains(text.trim().toLowerCase(java.util.Locale.ROOT))) {
            // jak w Tibii: "help" (albo "heal", "ulecz", "pomocy") do kaplana leczy i odnawia mane
            String line = n.name() + ": Niech światło cię uleczy.";
            log.add(nowMs, EventLog.Kind.CHAT, line);
            priestHeal(nowMs);
            return line;
        }
        String said = text.trim().toLowerCase(java.util.Locale.ROOT);
        if (n.role() == NpcRole.KING && (PREMIUM_WORDS.contains(said) || PROMOTION_WORDS.contains(said))) {
            if (PREMIUM_WORDS.contains(said)) buyPremium(nowMs);
            else buyPromotion(nowMs);
            return n.name() + ": " + (PREMIUM_WORDS.contains(said) ? "premium" : "promocja");
        }
        pl.fawizja.pixel.npc.DialogueRegistry d = pl.fawizja.pixel.npc.DialogueRegistry.defaults();
        String answer = d.answer(n, text);
        if (answer == null) {
            java.util.List<String> topics = d.topics(n);
            answer = "Nie rozumiem. Zapytaj o: " + String.join(", ", topics.subList(0, Math.min(4, topics.size()))) + ".";
        }
        String line = n.name() + ": " + answer;
        log.add(nowMs, EventLog.Kind.CHAT, line);
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

    /** Czy pokazac przycisk lowienia (wedka w plecaku albo w rece i woda w zasiegu; brak robakow wyjasnia komunikat). */
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
        if (r == FishingResult.CAUGHT || r == FishingResult.NOTHING) {
            if (fishing.lastRefill() > 0) {
                log.add(nowMs, EventLog.Kind.INFO, "Robaki uzupełnione z plecaka (" + fishing.lastRefill() + ").");
            } else if (fishing.baitInSlot(pc) == 0) {
                log.add(nowMs, EventLog.Kind.SYSTEM, "Skończyły się robaki na przynętę.");
            }
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
        if (st != null && items.get(st.itemId()).key().equals("exp_scroll")) return readExpScroll(slot, nowMs);
        if (st != null && pl.fawizja.pixel.item.Enchantments.Kind.ofPotion(items.get(st.itemId()).key()) != null) return drinkBuff(slot, nowMs);
        if (st != null && items.get(st.itemId()).key().equals("challenge_scroll")) return startChallenge(slot, nowMs);
        if (st != null && items.get(st.itemId()).key().startsWith("pet_")) return togglePet(items.get(st.itemId()), nowMs);
        if (st != null && items.get(st.itemId()).key().equals("backpack")) return attachBag(slot, nowMs);
        if (st != null && pl.fawizja.pixel.economy.Currency.isCoin(st.itemId())) return exchangeCoins(slot, nowMs);
        return spells.useItem(slot, nowMs);
    }

    /** Uzycie plecaka z plecaka: staje sie kolejna strona (+20 slotow), jak plecak w plecaku w Tibii. */
    private CastResult attachBag(int slot, long nowMs) {
        if (pc.extraBags() >= PlayerCharacter.MAX_EXTRA_BAGS) {
            log.add(nowMs, EventLog.Kind.WARNING, "Masz już " + PlayerCharacter.MAX_EXTRA_BAGS + " dodatkowe plecaki.");
            return CastResult.NOT_USABLE;
        }
        pc.backpack().takeFromSlot(slot, 1);
        pc.attachBag();
        log.add(nowMs, EventLog.Kind.INFO, "Plecak dołączony: " + pc.backpack().size() + " miejsc (strony " + pc.bagPages() + ").");
        return CastResult.OK;
    }

    /** Odpina ostatni dodatkowy plecak (musi byc pusty) i kladzie go do plecaka glownego. */
    public boolean detachBag(long nowMs) {
        if (pc.extraBags() == 0) return false;
        if (!pc.detachBag()) {
            log.add(nowMs, EventLog.Kind.WARNING, "Najpierw opróżnij ostatni plecak.");
            return false;
        }
        if (!pc.backpack().add(ItemStack.of(items.byKey("backpack").id())).ok()) {
            pc.attachBag();
            log.add(nowMs, EventLog.Kind.WARNING, "Brak miejsca na odpięty plecak.");
            return false;
        }
        log.add(nowMs, EventLog.Kind.INFO, "Plecak odpięty: " + pc.backpack().size() + " miejsc.");
        return true;
    }

    /** Uzycie monet jak w Tibii: 100 sztuk zamienia na monete wyzszego nominalu, pojedyncza wyzsza - rozmienia na 100. */
    private CastResult exchangeCoins(int slot, long nowMs) {
        pl.fawizja.pixel.economy.Currency cur = pl.fawizja.pixel.economy.Currency.byItemId(pc.backpack().get(slot).itemId());
        int count = pc.backpack().get(slot).count();
        InventoryResult r;
        if (count >= pl.fawizja.pixel.economy.Currency.EXCHANGE_RATE && cur != pl.fawizja.pixel.economy.Currency.CRYSTAL) {
            r = Money.exchangeUp(pc.backpack(), cur);
        } else if (cur != pl.fawizja.pixel.economy.Currency.GOLD) {
            r = Money.exchangeDown(pc.backpack(), cur);
        } else {
            log.add(nowMs, EventLog.Kind.INFO, "100 złotych monet = 1 platynowa, 100 platynowych = 1 kryształowa.");
            return CastResult.NOT_USABLE;
        }
        if (!r.ok()) {
            log.add(nowMs, EventLog.Kind.WARNING, "Brak miejsca w plecaku na wymianę monet.");
            return CastResult.BACKPACK_FULL;
        }
        log.add(nowMs, EventLog.Kind.INFO, "Monety wymienione.");
        return CastResult.OK;
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

    /** Wyzwanie ze zwoju (Challenge); null, gdy nie trwa. */
    private Challenge challenge;

    public Challenge challenge() { return challenge; }

    private CastResult startChallenge(int slot, long nowMs) {
        if (challenge != null) {
            log.add(nowMs, EventLog.Kind.INFO, "Wyzwanie już trwa: fala " + (challenge.wave + 1) + " z " + Challenge.WAVES.length + ".");
            return CastResult.OK;
        }
        if (world.isProtectionZone(pc.position())) {
            log.add(nowMs, EventLog.Kind.WARNING, "Zwoju wyzwania nie da się odczytać w strefie ochronnej.");
            return CastResult.OK;
        }
        pl.fawizja.pixel.monster.MonsterType t = pl.fawizja.pixel.quest.HuntTask.pick(pc.level(), monsters.spawnedTypes(), rng);
        if (t == null) {
            log.add(nowMs, EventLog.Kind.WARNING, "Zwój milczy: w tej krainie nie ma z kim walczyć.");
            return CastResult.OK;
        }
        if (pc.backpack().takeFromSlot(slot, 1) == null) return CastResult.MISSING_ITEM;
        challenge = new Challenge(t, pc.position(), nowMs + Challenge.TIME_MS);
        log.add(nowMs, EventLog.Kind.SYSTEM, "Wyzwanie: " + Challenge.WAVES.length + " fale - " + t.name()
                + ". Pokonaj je w " + (Challenge.TIME_MS / 60_000) + " min i nie oddalaj się.");
        spawnWave(nowMs);
        return CastResult.OK;
    }

    private void spawnWave(long nowMs) {
        Challenge c = challenge;
        c.ids.clear();
        for (pl.fawizja.pixel.monster.Monster m : monsters.spawnEvent(c.type.key(), c.center, Challenge.RADIUS, Challenge.WAVES[c.wave], nowMs)) {
            c.ids.add(m.id());
        }
        log.add(nowMs, EventLog.Kind.WARNING, "Fala " + (c.wave + 1) + " z " + Challenge.WAVES.length + ": " + c.ids.size() + "x " + c.type.name() + "!");
    }

    private void updateChallenge(long nowMs) {
        Challenge c = challenge;
        if (c == null) return;
        boolean left = pc.position().z() != c.center.z()
                || Math.max(Math.abs(pc.position().x() - c.center.x()), Math.abs(pc.position().y() - c.center.y())) > Challenge.LEAVE;
        if (pc.isDead() || left || nowMs >= c.untilMs) {
            monsters.clearEvent(c.ids);
            challenge = null;
            log.add(nowMs, EventLog.Kind.WARNING, "Wyzwanie przepadło" + (left ? " (za daleko od miejsca zwoju)." : nowMs >= c.untilMs ? " (koniec czasu)." : "."));
            return;
        }
        for (pl.fawizja.pixel.monster.Monster m : monsters.monsters()) {
            if (!m.isDead() && c.ids.contains(m.id())) return; // fala jeszcze walczy
        }
        c.wave++;
        if (c.wave < Challenge.WAVES.length) {
            spawnWave(nowMs);
            return;
        }
        challenge = null;
        pc.stats().recordChallenge();
        long gold = Challenge.rewardGold(c.type);
        pc.bank().credit(gold);
        log.add(nowMs, EventLog.Kind.SYSTEM, "Wyzwanie ukończone! " + String.format("%,d gp", gold) + " na konto.");
        combat.awardExperience(Challenge.rewardExp(c.type), "Wyzwanie", nowMs);
    }

    /** Siodlo: oswaja konia na stale (przedmiot znika). */
    /** Zwoj nauki: godzina +30% doswiadczenia za potwory (do 3 godzin zapasu). */
    private CastResult readExpScroll(int slot, long nowMs) {
        pl.fawizja.pixel.item.Enchantments e = pc.enchantments();
        pl.fawizja.pixel.item.Enchantments.Kind k = pl.fawizja.pixel.item.Enchantments.Kind.EXP;
        if (!e.canExtend(k)) {
            log.add(nowMs, EventLog.Kind.INFO, "Wiedzy ze zwojów starczy jeszcze na " + pl.fawizja.pixel.item.Enchantments.describe(e.remainingMs(k)) + ".");
            return CastResult.OK;
        }
        if (pc.backpack().takeFromSlot(slot, 1) == null) return CastResult.MISSING_ITEM;
        e.extend(k);
        log.add(nowMs, EventLog.Kind.SYSTEM, "Zwój nauki: +" + pl.fawizja.pixel.item.Enchantments.EXP_BONUS_PERCENT
                + "% doświadczenia za potwory przez " + pl.fawizja.pixel.item.Enchantments.describe(e.remainingMs(k)) + " polowania.");
        return CastResult.OK;
    }

    /** Mikstura wzmacniajaca: 10 minut polowania (czas plynie poza strefa ochronna), do 30 minut zapasu. */
    private CastResult drinkBuff(int slot, long nowMs) {
        pl.fawizja.pixel.item.Enchantments e = pc.enchantments();
        pl.fawizja.pixel.item.ItemType potion = items.get(pc.backpack().get(slot).itemId());
        pl.fawizja.pixel.item.Enchantments.Kind k = pl.fawizja.pixel.item.Enchantments.Kind.ofPotion(potion.key());
        if (pc.level() < potion.minLevel()) {
            log.add(nowMs, EventLog.Kind.WARNING, potion.name() + ": od poziomu " + potion.minLevel() + ".");
            return CastResult.LEVEL_TOO_LOW;
        }
        if (!e.canExtend(k)) {
            log.add(nowMs, EventLog.Kind.INFO, k.label() + " działa jeszcze " + pl.fawizja.pixel.item.Enchantments.describe(e.remainingMs(k)) + ".");
            return CastResult.OK;
        }
        if (pc.backpack().takeFromSlot(slot, 1) == null) return CastResult.MISSING_ITEM;
        e.extend(k);
        log.add(nowMs, EventLog.Kind.SYSTEM, k.label() + ": " + k.effect() + " przez "
                + pl.fawizja.pixel.item.Enchantments.describe(e.remainingMs(k)) + " polowania.");
        return CastResult.OK;
    }

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

    private final Random rng;

    /**
     * Ulepszenie zalozonego przedmiotu u kupca: platnosc z plecaka, szansa wg {@link pl.fawizja.pixel.item.Upgrade};
     * nieudana proba zabiera zloto, przedmiot zostaje bez zmian.
     */
    public boolean upgradeEquipped(pl.fawizja.pixel.item.EquipSlot slot, long nowMs) {
        Npc n = talkTo(NpcRole.MERCHANT, nowMs);
        if (n == null) return false;
        ItemStack worn = pc.equipment().get(slot);
        if (worn == null) {
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + ": nic tam nie nosisz.");
            return false;
        }
        pl.fawizja.pixel.item.ItemType t = items.get(worn.itemId());
        if (!pl.fawizja.pixel.item.Upgrade.upgradable(t)) {
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + ": tego nie da się ulepszyć.");
            return false;
        }
        int next = worn.upgrade() + 1;
        if (next > pl.fawizja.pixel.item.Upgrade.MAX) {
            log.add(nowMs, EventLog.Kind.INFO, n.name() + ": " + pl.fawizja.pixel.item.Upgrade.name(t, worn.upgrade())
                    + " jest już ulepszony do końca.");
            return false;
        }
        long price = pl.fawizja.pixel.item.Upgrade.price(t, next);
        pl.fawizja.pixel.inventory.Container tx = pc.backpack().copy();
        if (Money.totalValue(tx) < price || !Money.pay(tx, price).ok()) {
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + ": ulepszenie do +" + next + " kosztuje " + price + " gp (monety w plecaku).");
            return false;
        }
        pc.backpack().commitFrom(tx);
        if (rng.nextInt(100) >= pl.fawizja.pixel.item.Upgrade.chancePercent(next)) {
            log.add(nowMs, EventLog.Kind.WARNING, "Nie udało się (" + price + " gp). " + pl.fawizja.pixel.item.Upgrade.name(t, worn.upgrade())
                    + " zostaje bez zmian.");
            return false;
        }
        pc.equipment().setDirect(slot, worn.withUpgrade(next));
        log.add(nowMs, EventLog.Kind.SYSTEM, "Udało się! " + pl.fawizja.pixel.item.Upgrade.name(t, next) + " (" + price + " gp).");
        return true;
    }

    /** Zaklecie u kowala (kupca): godzina polowania, placi z plecaka, najwyzej 3 godziny zapasu. */
    public boolean buyEnchantment(pl.fawizja.pixel.item.Enchantments.Kind kind, long nowMs) {
        Npc n = talkTo(NpcRole.MERCHANT, nowMs);
        if (n == null) return false;
        pl.fawizja.pixel.item.Enchantments e = pc.enchantments();
        if (!e.canExtend(kind)) {
            log.add(nowMs, EventLog.Kind.INFO, n.name() + ": " + kind.label().toLowerCase(java.util.Locale.ROOT)
                    + " starczy jeszcze na " + pl.fawizja.pixel.item.Enchantments.describe(e.remainingMs(kind)) + ". Wróć później.");
            return false;
        }
        long price = pl.fawizja.pixel.item.Enchantments.price(kind, pc.level());
        pl.fawizja.pixel.inventory.Container tx = pc.backpack().copy();
        if (Money.totalValue(tx) < price || !Money.pay(tx, price).ok()) {
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + ": zaklęcie kosztuje " + price + " gp (monety w plecaku).");
            return false;
        }
        pc.backpack().commitFrom(tx);
        e.extend(kind);
        log.add(nowMs, EventLog.Kind.SYSTEM, kind.label() + " na " + pl.fawizja.pixel.item.Enchantments.describe(e.remainingMs(kind))
                + " polowania (" + price + " gp).");
        return true;
    }

    /** Tytul z osiagniecia: key null albo "-" zdejmuje tytul; tylko zdobyte osiagniecia. */
    public boolean chooseTitle(String key, long nowMs) {
        if (key == null || key.equals("-")) {
            if (pc.title() != null) log.add(nowMs, EventLog.Kind.INFO, "Zdejmujesz tytuł.");
            pc.setTitle(null);
            return true;
        }
        pl.fawizja.pixel.achievement.Achievement a = null;
        for (pl.fawizja.pixel.achievement.Achievement x : pl.fawizja.pixel.achievement.Achievement.values()) if (x.name().equals(key)) a = x;
        if (a == null || !pc.achievements().has(a)) {
            log.add(nowMs, EventLog.Kind.WARNING, "Ten tytuł trzeba najpierw zdobyć (osiągnięcia, klawisz O).");
            return false;
        }
        pc.setTitle(a.name());
        log.add(nowMs, EventLog.Kind.INFO, "Twój tytuł: " + a.title() + ".");
        return true;
    }

    /** Kolor stroju (Outfit); false dla nieznanego koloru. */
    public boolean chooseOutfit(String color, long nowMs) {
        int i = pl.fawizja.pixel.character.Outfit.parse(color);
        if (i < 0) {
            log.add(nowMs, EventLog.Kind.WARNING, "Nie ma takiego koloru stroju. Kolory: " + String.join(", ", pl.fawizja.pixel.character.Outfit.NAMES) + ".");
            return false;
        }
        pc.setOutfit(i);
        log.add(nowMs, EventLog.Kind.INFO, i == 0 ? "Strój w kolorze profesji." : "Strój: " + pl.fawizja.pixel.character.Outfit.name(i) + ".");
        return true;
    }

    /** Pupil: przywolanie albo odeslanie (przedmiot zostaje w plecaku; bez niego pupil odchodzi). */
    private CastResult togglePet(pl.fawizja.pixel.item.ItemType pet, long nowMs) {
        if (pet.key().equals(pc.pet())) {
            pc.setPet(null);
            log.add(nowMs, EventLog.Kind.INFO, pet.name() + " odpoczywa w plecaku.");
        } else {
            pc.setPet(pet.key());
            log.add(nowMs, EventLog.Kind.INFO, pet.name() + " chodzi teraz za tobą.");
        }
        return CastResult.OK;
    }

    /** Pupil odchodzi, gdy jego przedmiotu nie ma juz w plecaku (sprzedany, oddany, wyrzucony). */
    private void checkPet(long nowMs) {
        String key = pc.pet();
        if (key == null) return;
        pl.fawizja.pixel.item.ItemType t;
        try {
            t = items.byKey(key);
        } catch (IllegalArgumentException e) {
            t = null; // pupil z nowszej wersji danych
        }
        if (t != null && pc.backpack().count(t.id()) > 0) return;
        pc.setPet(null);
        if (t != null) log.add(nowMs, EventLog.Kind.INFO, t.name() + " odchodzi, bo nie masz go już w plecaku.");
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

    /** Mikstury szybkich przyciskow od najsilniejszej; przycisk HP/MP uzywa najsilniejszej dozwolonej, ktora masz. */
    /** Od najsilniejszej; mikstury ducha paladyna (zdrowie i mana naraz) sa na obu listach. */
    static final String[] HP_TIERS = {"ultimate_health_potion", "supreme_health_potion", "ultimate_spirit_potion",
            "strong_health_potion", "great_spirit_potion", "great_health_potion", "health_potion"};
    static final String[] MP_TIERS = {"ultimate_mana_potion", "supreme_mana_potion", "ultimate_spirit_potion",
            "great_spirit_potion", "strong_mana_potion", "great_mana_potion", "mana_potion"};

    /** Klucz mikstury dla przycisku HP (mana = false) albo MP; bez zadnej w plecaku - zwykla. */
    public String quickPotion(boolean mana) {
        String[] tiers = mana ? MP_TIERS : HP_TIERS;
        for (String k : tiers) {
            if (!items.containsKey(k)) continue;
            pl.fawizja.pixel.item.ItemType t = items.byKey(k);
            if (pc.level() < t.minLevel() || !t.vocations().contains(pc.vocation())) continue;
            if (pc.backpack().count(t.id()) > 0) return k;
        }
        return tiers[tiers.length - 1];
    }

    /** "health_potion"/"mana_potion" (przyciski HP/MP) biora najsilniejsza dozwolona miksture z plecaka. */
    public CastResult usePotion(String itemKey, long nowMs) {
        if (itemKey.equals("health_potion")) itemKey = quickPotion(false);
        else if (itemKey.equals("mana_potion")) itemKey = quickPotion(true);
        if (pl.fawizja.pixel.item.Enchantments.Kind.ofPotion(itemKey) != null && items.containsKey(itemKey)) {
            int id = items.byKey(itemKey).id();
            for (int i = 0; i < pc.backpack().size(); i++) {
                pl.fawizja.pixel.item.ItemStack st = pc.backpack().get(i);
                if (st != null && st.itemId() == id) return useSlot(i, nowMs);
            }
        }
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
        checkBoost(nowMs);
        if (pc.level() > pc.rewardedLevel()) checkLevelRewards(nowMs);
        pc.stats().noteLevel(pc.level());
        checkPet(nowMs);
        if (nowMs - tipsCheckedMs >= 1000 || nowMs < tipsCheckedMs) checkTips(nowMs);
        if (nowMs - suppliesCheckedMs >= 500 || nowMs < suppliesCheckedMs) checkSupplies(nowMs);
        if (nowMs - bossesCheckedMs >= BossWatch.CHECK_MS || nowMs < bossesCheckedMs) checkBosses(nowMs);
        if (nowMs - rentCheckedMs >= RENT_CHECK_MS || nowMs < rentCheckedMs) {
            rentCheckedMs = nowMs;
            checkHouseRent(nowMs);
        }
        updateRecall(nowMs);
        updateTraining(nowMs);
        updateChallenge(nowMs);
        if (pc.isMounted() && pc.position().z() < 0) {
            pc.setMounted(false);
            log.add(nowMs, EventLog.Kind.INFO, "Zostawiasz konia przy zejściu pod ziemię.");
        }
        if (lastUpdateMs >= 0) {
            long dt = Math.min(1000, nowMs - lastUpdateMs);
            pc.tickRegeneration(dt, isResting(nowMs));
            pc.stats().addPlayTime(dt);
            if (!world.isProtectionZone(pc.position())) {
                pl.fawizja.pixel.item.Enchantments.Kind gone = pc.enchantments().tick(dt);
                if (gone != null) log.add(nowMs, EventLog.Kind.WARNING, "Zaklęcie wygasło: " + gone.label().toLowerCase(java.util.Locale.ROOT) + ".");
            }
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
            MoveResult r = pzLocked(before, requested, nowMs) || chestAt(before.translate(requested.dx, requested.dy))
                    ? MoveResult.BLOCKED : movement.step(pc, requested);
            if (r == MoveResult.BLOCKED && !pzLocked(before, requested, nowMs) && pushCrate(before, requested, nowMs)) {
                r = movement.step(pc, requested);                  // skrzynia odjechala - postac idzie za nia
            }
            if (r == MoveResult.BLOCKED && requested.diagonal()) {
                // slizg po murze: skos w sciane idzie wzdluz niej (najpierw poziomo, potem pionowo)
                for (Direction slide : new Direction[] {Direction.fromDelta(requested.dx, 0), Direction.fromDelta(0, requested.dy)}) {
                    if (pzLocked(before, slide, nowMs)) continue;
                    MoveResult s2 = movement.step(pc, slide);
                    if (s2 != MoveResult.BLOCKED) {
                        r = s2;
                        requested = slide;
                        facing = slide;
                        break;
                    }
                }
            }
            if (r == MoveResult.MOVED || r == MoveResult.CHANGED_FLOOR) checkRegion(nowMs);
            if (r == MoveResult.MOVED) {
                stepOnTrap(nowMs);
                stepFrom = before;
                stepStartMs = nowMs;
                stepDurationMs = MovementSystem.stepDurationMs(pc, requested);
                moving = true;
            } else if (r == MoveResult.CHANGED_FLOOR) {
                onFloorChanged(nowMs);
            } else if (r == MoveResult.LEVEL_TOO_LOW && nowMs - lastGateWarningMs > 3000) {
                lastGateWarningMs = nowMs;
                Position gate = pc.position().translate(requested.dx, requested.dy);
                if (world.levelDoorAt(gate) > 0) {
                    log.add(nowMs, EventLog.Kind.WARNING, levelDoorRefusal(world.levelDoorAt(gate)));
                } else {
                    log.add(nowMs, EventLog.Kind.WARNING, "Strażnik: dalej wpuszczamy tylko od poziomu "
                            + world.requiredLevelAt(gate) + ".");
                }
            }
        }
        ground.decay(nowMs);
        if (!sharedWorld && altarsLocal) updateAltarTorches();
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
        if (!sharedWorld && bossRuntime != null) bossRuntime.update(bossHeroes.get(), nowMs);
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
        // skos jak w Tibii: postac przesuwa sie w zwyklym tempie (x1.4 dlugosci), a reszta kroku to postoj
        boolean diagonal = stepFrom.x() != pc.position().x() && stepFrom.y() != pc.position().y();
        float anim = diagonal ? stepDurationMs * 1.4f / MovementSystem.DIAGONAL_STEP_FACTOR : stepDurationMs;
        return Math.min(1f, (nowMs - stepStartMs) / Math.max(1f, anim));
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    public static String floorMessage(int z) {
        if (z == 0) return "Jesteś na powierzchni.";
        return z > 0 ? "Piętro +" + z + "." : "Podziemia, poziom " + z + ".";
    }

    // ------------------------------------------------------------------ zwykle skrzynie (2026-10-28)

    /** Otwarta skrzynia (schowek) obok postaci albo null. */
    private Position openCrate;
    private final java.util.Map<Position, Npc> crateWindows = new java.util.HashMap<>();
    private long lastPushNoteMs = Long.MIN_VALUE / 2;

    public Position openCrate() { return openCrate; }

    private static String crateName(String key, boolean accusative) {
        switch (key) {
            case "barrel": return accusative ? "beczkę" : "Beczka";
            case "sack": return accusative ? "worek" : "Worek";
            case "chest": return accusative ? "kufer" : "Kufer";
            case "wardrobe": return accusative ? "szafę" : "Szafa";
            default: return accusative ? "skrzynię" : "Skrzynia";
        }
    }

    private String objectKey(Position p) {
        pl.fawizja.pixel.world.FloorMap f = world.floor(p.z());
        int id = f == null ? 0 : f.get(pl.fawizja.pixel.world.Layer.OBJECT, p.x(), p.y());
        pl.fawizja.pixel.world.TileType t = id == 0 ? null : world.tiles().get(id);
        return t == null ? "" : t.key();
    }

    /**
     * Skrzynia, beczka albo worek obok: otwiera schowek, drugie dotkniecie zamyka. W domu tylko dla wlasciciela
     * i gosci (jak wejscie), na ulicy dla kazdego.
     */
    public boolean useCrate(Position p, long nowMs) {
        if (p.z() != pc.position().z() || pc.position().distanceTo(p) > 1 || !world.crates().isStorage(p)) return false;
        if (!mayHandle(p)) {
            log.add(nowMs, EventLog.Kind.WARNING, mayEnter(p) ? "Masz w tym domu tylko wstęp - schowków nie otworzysz."
                    : "To schowek w cudzym domu.");
            return true;
        }
        String key = objectKey(p);
        if (p.equals(openCrate)) {
            openCrate = null;
            log.add(nowMs, EventLog.Kind.INFO, "Zamykasz " + crateName(key, true) + ".");
            return true;
        }
        openCrate = p;
        world.crates().open(p, items);
        int n = world.crates().peek(p).size();
        log.add(nowMs, EventLog.Kind.INFO, "Otwierasz " + crateName(key, true) + (n == 0 ? ". Pusto - możesz tu coś schować."
                : ". W środku: " + n + " " + (n == 1 ? "rzecz" : n < 5 ? "rzeczy" : "rzeczy") + "."));
        return true;
    }

    /** Klient online: okno skrzyni otwiera sie od razu, serwer otwiera ja u siebie i odsyla zawartosc. */
    public void toggleCrateView(Position p) {
        if (p.equals(openCrate)) openCrate = null;
        else if (world.crates().isStorage(p) && mayHandle(p)) openCrate = p;
    }

    /** UZYJ obok skrzyni (najpierw ta, w ktora postac patrzy). */
    private boolean useCrateNearby(long nowMs) {
        Position me = pc.position();
        if (facing != null && world.crates().isStorage(me.translate(facing.dx, facing.dy))) {
            return useCrate(me.translate(facing.dx, facing.dy), nowMs);
        }
        for (Direction d : Direction.values()) {
            Position q = me.translate(d.dx, d.dy);
            if (world.crates().isStorage(q) && !chestAt(q)) return useCrate(q, nowMs);
        }
        return false;
    }

    /** Okno otwartej skrzyni (jak depozyt u bankiera); null, gdy zadnej nie ma obok. */
    private Npc crateNearby() {
        Position p = openCrate;
        if (p == null) return null;
        if (p.z() != pc.position().z() || pc.position().distanceTo(p) > 1 || !world.crates().isStorage(p)) {
            openCrate = null;
            return null;
        }
        String key = objectKey(p);
        return crateWindows.computeIfAbsent(p, q -> new Npc(Npc.CRATE, crateName(key, false), NpcRole.BANKER,
                nearestTown(q), q, null, "Schowek na " + pl.fawizja.pixel.world.Crates.SLOTS + " rzeczy.", "#8a6a3a"));
    }

    /** Zawartosc otwartej skrzyni (do okna); pusta lista, gdy nic nie jest otwarte. */
    public java.util.List<ItemStack> crateContents() {
        return openCrate == null ? java.util.List.of() : world.crates().peek(openCrate);
    }

    /** Wolne miejsca w otwartej skrzyni. */
    public long crateSpaceFor(int itemId) {
        if (openCrate == null) return 0;
        pl.fawizja.pixel.inventory.Container c = world.crates().open(openCrate, items);
        return c == null ? 0 : c.spaceFor(itemId);
    }

    /** Wszystkie sztuki przedmiotu z plecaka do otwartej skrzyni. */
    public int cratePut(int itemId, long nowMs) {
        pl.fawizja.pixel.inventory.Container c = openCrateContainer(nowMs);
        if (c == null) return 0;
        if (pl.fawizja.pixel.economy.Currency.isCoin(itemId)) {
            log.add(nowMs, EventLog.Kind.WARNING, "Monety trzymaj w banku, nie w skrzyni.");
            return 0;
        }
        int n = moveAll(pc.backpack(), c, itemId, nowMs, "Schowano", "Skrzynia jest pełna.");
        if (n > 0) cratesChanged(openCrate);
        return n;
    }

    /** Wszystkie sztuki przedmiotu z otwartej skrzyni do plecaka (ile sie zmiesci). */
    public int crateTake(int itemId, long nowMs) {
        pl.fawizja.pixel.inventory.Container c = openCrateContainer(nowMs);
        if (c == null) return 0;
        int n = moveAll(c, pc.backpack(), itemId, nowMs, "Wyjęto", "Brak miejsca w plecaku.");
        if (n > 0) cratesChanged(openCrate);
        return n;
    }

    private pl.fawizja.pixel.inventory.Container openCrateContainer(long nowMs) {
        if (crateNearby() == null || !mayHandle(openCrate)) {
            log.add(nowMs, EventLog.Kind.WARNING, "Podejdź do skrzyni i otwórz ją.");
            return null;
        }
        return world.crates().open(openCrate, items);
    }

    private void cratesChanged(Position p) {
        if (p != null) world.crates().changed(p);
        if (!sharedWorld) pc.setCrates(world.crates().encode());
    }

    /** Wejscie w skrzynie, beczke albo worek pcha je o pole dalej (gdy tam wolno). */
    private boolean pushCrate(Position before, Direction d, long nowMs) {
        Position at = before.translate(d.dx, d.dy);
        pl.fawizja.pixel.world.Crates c = world.crates();
        if (d.diagonal() || chestAt(at) || !c.isMovable(at) || !mayEnter(at)) return false;
        if (!mayHandle(at)) {
            if (nowMs - lastPushNoteMs > 4000) {
                lastPushNoteMs = nowMs;
                log.add(nowMs, EventLog.Kind.INFO, "Masz w tym domu tylko wstęp - nie przesuniesz mebli.");
            }
            return false;
        }
        java.util.function.Predicate<Position> blocked = q -> monsters.monsterAt(q) != null || npcs.at(q) != null
                || dummies.at(q) || !mayEnter(q) || otherPlayers.test(q) || q.equals(pc.position())
                || !java.util.Objects.equals(houses.at(q), houses.at(at));   // meble z domu nie wyjezdzaja na ulice
        if (!c.canPush(at, d, blocked)) {
            if (nowMs - lastPushNoteMs > 4000) {
                lastPushNoteMs = nowMs;
                log.add(nowMs, EventLog.Kind.INFO, crateName(objectKey(at), false) + " nie przesunie się dalej.");
            }
            return false;
        }
        c.push(at, d);
        crateWindows.clear();
        if (at.equals(openCrate)) openCrate = null;
        if (!sharedWorld) pc.setCrates(c.encode());
        return true;
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
                if (toggleTraining(nowMs)) break;
                if (openChest(nowMs)) break;
                if (searchCorpse(nowMs)) break;
                if (useCrateNearby(nowMs)) break;
                if (!gatherHerbs(nowMs)) log.add(nowMs, EventLog.Kind.WARNING, "Nie ma tu nic do użycia.");
        }
    }

    public pl.fawizja.pixel.gathering.HerbGathering herbs() { return herbs; }

    private long lastTrapMs = Long.MIN_VALUE / 2;

    /** Wejscie na pulapke z kolcami (lochy -2..-5): rani, ale nie zabija; raz na 1,5 s. */
    private void stepOnTrap(long nowMs) {
        if (chests == null || !chests.trapAt(pc.position()) || nowMs - lastTrapMs < 1500) return;
        lastTrapMs = nowMs;
        int dmg = pl.fawizja.pixel.treasure.TreasureChests.trapDamage(pc.maxHealth(), pc.health(), pc.position().z());
        int dealt = combat.environmentHit("Pułapka z kolcami", dmg, nowMs);
        log.add(nowMs, EventLog.Kind.WARNING, "Pułapka z kolcami! -" + dealt + " HP. Omijaj metalowe płyty w lochach.");
    }

    /** Krzak obok: ziela lecznicze do plecaka (krzak odrasta 10 min). false = nie ma krzaka obok. */
    public boolean gatherHerbs(long nowMs) {
        if (!items.containsKey("healing_herb")) return false;
        int herbId = items.byKey("healing_herb").id();
        if (herbs.readyNear(pc.position(), nowMs) && !pc.backpack().canAdd(herbId, 2)) {
            log.add(nowMs, EventLog.Kind.WARNING, "Brak miejsca w plecaku na zioła.");
            return true;
        }
        pl.fawizja.pixel.gathering.HerbGathering.Result r = herbs.gather(pc.position(), rng, nowMs);
        switch (r.outcome()) {
            case NO_BUSH:
                return false;
            case REGROWING:
                log.add(nowMs, EventLog.Kind.INFO, "Ten krzak jest już obrany. Ziele odrośnie za " + Math.max(1, (r.readyInMs() + 59_999) / 60_000) + " min.");
                return true;
            default:
                int id = items.byKey("healing_herb").id();
                if (!pc.backpack().add(new ItemStack(id, r.count())).ok()) {
                    log.add(nowMs, EventLog.Kind.WARNING, "Brak miejsca w plecaku na zioła.");
                    return true;
                }
                pc.stats().recordHerbs(r.count());
                log.add(nowMs, EventLog.Kind.LOOT, "Zebrano: " + r.count() + "x " + items.get(id).name()
                        + " (masz " + pc.backpack().count(id) + "; 4 ziela = mikstura zdrowia w księdze).");
                return true;
        }
    }

    private pl.fawizja.pixel.treasure.TreasureChests chests;

    /** Skrzynie skarbow swiata (PixelGame, serwer, testy). */
    public void setChests(pl.fawizja.pixel.treasure.TreasureChests chests) { this.chests = chests; }

    public pl.fawizja.pixel.treasure.TreasureChests chests() { return chests; }

    /** Na skrzynie skarbow nie mozna wejsc - otwiera sie ja dotknieciem z sasiedniego pola. */
    public boolean chestAt(Position p) {
        return chests != null && chests.at(p) != null;
    }

    // ------------------------------------------------------------------ wyprawy na bossa w piec postaci
    /** Areny wypraw: offline wlasne, na serwerze wspolne (setBossQuests). */
    private BossQuestRuntime bossRuntime;
    private final BossQuestRuntime.Hero bossHero = new BossQuestRuntime.Hero() {
        @Override
        public String id() { return pc.name(); }

        @Override
        public Position position() { return pc.position(); }

        @Override
        public boolean dead() { return pc.isDead(); }

        @Override
        public boolean completed(String questId) { return pc.completedBossQuests().contains(questId); }

        @Override
        public void markCompleted(String questId) { pc.completedBossQuests().add(questId); }

        @Override
        public void teleport(Position to, long nowMs) { GameSession.this.teleport(to, nowMs); }

        @Override
        public void message(EventLog.Kind kind, String text, long nowMs) { log.add(nowMs, kind, text); }

        @Override
        public boolean toBackpack(ItemStack st) { return pc.backpack().add(st).ok(); }

        @Override
        public boolean toDepot(ItemStack st) { return pc.depot().add(st).ok(); }

        @Override
        public void drop(ItemStack st, long nowMs) { ground.place(pc.position(), st); }
    };
    /** Kto bierze udzial w wyprawach: offline sama postac, na serwerze wszyscy gracze. */
    private java.util.function.Supplier<java.util.List<BossQuestRuntime.Hero>> bossHeroes = () -> java.util.List.of(bossHero);
    /** Tryb testowy offline (-Dpixel.bossGhosts=N): tylu "duchow" stoi na wolnych polach przedsionka przy dzwigni. */
    private int bossGhosts;

    /** Ta postac widziana przez wyprawy (serwer zbiera je od wszystkich graczy). */
    public BossQuestRuntime.Hero bossHero() { return bossHero; }

    public BossQuestRuntime bossQuests() { return bossRuntime; }

    /** Serwer: wspolne areny i wszyscy gracze swiata. */
    public void setBossQuests(BossQuestRuntime runtime, java.util.function.Supplier<java.util.List<BossQuestRuntime.Hero>> everyone) {
        bossRuntime = runtime;
        bossHeroes = everyone == null ? () -> java.util.List.of(bossHero) : everyone;
    }

    /** Tryb testowy offline: n (0..4) duchow uzupelnia druzyne na polach przedsionka (nie walcza i nie dostaja lupu). */
    public void setBossGhosts(int n) { bossGhosts = Math.max(0, Math.min(BossQuestRuntime.PARTY_GHOSTS_MAX, n)); }

    /**
     * Tryb testowy offline (-Dpixel.bossWin): boss toczacej sie walki ginie od jednego ciosu tej postaci - lup
     * i komunikaty jak po prawdziwej walce. Zwraca false, gdy zadna walka nie trwa.
     */
    public boolean debugDefeatQuestBoss(long nowMs) {
        if (sharedWorld || bossRuntime == null) return false;
        for (pl.fawizja.pixel.world.BossQuest q : world.bossQuests()) {
            int id = bossRuntime.bossId(q.id());
            if (id < 0) continue;
            for (Monster m : new java.util.ArrayList<>(monsters.monsters())) {
                if (m.id() == id) {
                    combat.damageMonster(m, m.type().maxHealth() * 20, pl.fawizja.pixel.combat.DamageType.HOLY, nowMs);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean pullBossLever(pl.fawizja.pixel.world.BossQuest q, long nowMs) {
        if (bossRuntime == null) {
            log.add(nowMs, EventLog.Kind.INFO, "Dźwignia wyprawy " + q.name() + " - o wszystkim rozstrzyga serwer.");
            return true;
        }
        java.util.List<BossQuestRuntime.Hero> everyone = new java.util.ArrayList<>(bossHeroes.get());
        if (!sharedWorld) everyone.addAll(BossQuestRuntime.ghosts(q, bossGhosts, everyone));
        bossRuntime.pull(q, bossHero, everyone, nowMs);
        return true;
    }

    // ------------------------------------------------------------------ zadania z oltarzami profesji
    /**
     * Kto stoi przed oltarzami i jak przeniesc druzyne do skarbca. Gra offline: sama postac (i "duchy" z
     * {@link #setAltarGhosts} do testow i zrzutow); serwer podstawia wszystkich graczy swiata.
     */
    public interface AltarParty extends pl.fawizja.pixel.world.AltarQuest.Occupants {
        /** Przenosi postac danej profesji stojaca na polu from na pole to. */
        void teleport(Position from, pl.fawizja.pixel.vocation.Vocation v, Position to, long nowMs);
    }

    private java.util.Set<pl.fawizja.pixel.vocation.Vocation> altarGhosts =
            java.util.EnumSet.noneOf(pl.fawizja.pixel.vocation.Vocation.class);
    private final AltarParty soloParty = new AltarParty() {
        @Override
        public boolean standing(Position p, pl.fawizja.pixel.vocation.Vocation v) {
            if (!pc.isDead() && pc.position().equals(p) && pc.vocation() == v) return true;
            if (!altarGhosts.contains(v) || pc.position().equals(p)) return false;
            // duch (tryb testowy offline) stoi na swoim polu, jesli nikt go nie zajal
            for (pl.fawizja.pixel.world.AltarQuest q : world.altarQuests()) {
                pl.fawizja.pixel.world.AltarQuest.Slot s = q.slotByStand(p);
                if (s != null && s.vocation() == v) return true;
            }
            return false;
        }

        @Override
        public void teleport(Position from, pl.fawizja.pixel.vocation.Vocation v, Position to, long nowMs) {
            if (pc.position().equals(from) && pc.vocation() == v) GameSession.this.teleport(to, nowMs);
        }
    };
    private AltarParty altarParty = soloParty;
    /** false: pochodnie oltarzy ustawia serwer (klient online), true: sesja sama (gra offline). */
    private boolean altarsLocal = true;

    /** Serwer: wszyscy gracze swiata (null = sama postac). */
    public void setAltarParty(AltarParty party) { altarParty = party == null ? soloParty : party; }

    /** Klient online: pochodnie oltarzy przychodza z serwera, sesja ich nie zmienia. */
    public void setAltarsLocal(boolean local) { altarsLocal = local; }

    /** Tryb testowy offline (-Dpixel.altarGhosts): te profesje "stoja" na swoich polach przed oltarzami. */
    public void setAltarGhosts(java.util.Collection<pl.fawizja.pixel.vocation.Vocation> ghosts) {
        altarGhosts = ghosts == null || ghosts.isEmpty() ? java.util.EnumSet.noneOf(pl.fawizja.pixel.vocation.Vocation.class)
                : java.util.EnumSet.copyOf(ghosts);
    }

    /** Pochodnie nad oltarzami wg tego, kto stoi na polach (offline: co klatke, gdy postac jest blisko sali). */
    public void updateAltarTorches() {
        for (pl.fawizja.pixel.world.AltarQuest q : world.altarQuests()) {
            if (q.lever().z() != pc.position().z() || q.lever().distanceTo(pc.position()) > 40) continue;
            q.updateTorches(world.wallLights(), altarParty);
        }
    }

    private String itemName(String key) {
        pl.fawizja.pixel.item.ItemType t = items.byKeyOrNull(key);
        return t == null ? key : t.name();
    }

    static String levelDoorRefusal(int level) {
        return "Na drzwiach napis: " + WorldMap.levelDoorSign(level) + ". Drzwi nie ustępują - wejdziesz od poziomu " + level + ".";
    }

    /** Dzwignia sali oltarzy: z piecioma profesjami na miejscach przenosi je do skarbca, inaczej mowi, kogo brakuje. */
    private boolean pullLever(pl.fawizja.pixel.world.AltarQuest q, long nowMs) {
        java.util.List<pl.fawizja.pixel.world.AltarQuest.Slot> missing = q.missing(altarParty);
        if (!missing.isEmpty()) {
            log.add(nowMs, EventLog.Kind.WARNING, "Dźwignia ani drgnie. Przed ołtarzami brakuje: "
                    + pl.fawizja.pixel.world.AltarQuest.names(missing) + " (pali się " + (q.slots().size() - missing.size())
                    + " z " + q.slots().size() + " pochodni).");
            return true;
        }
        log.add(nowMs, EventLog.Kind.SYSTEM, "Przeciągasz dźwignię. Ołtarze rozbłyskują - " + q.name() + " otwiera skarbiec!");
        for (pl.fawizja.pixel.world.AltarQuest.Slot s : q.slots()) altarParty.teleport(s.stand(), s.vocation(), s.target(), nowMs);
        if (!sharedWorld && altarsLocal) updateAltarTorches();
        return true;
    }

    /**
     * Dotkniecie pola obok postaci: skrzynia (otwiera), drzwi albo okno (otwiera, drugie dotkniecie zamyka).
     * Zwraca false, gdy pole jest za daleko albo nie ma tam nic do otwarcia.
     */
    public boolean useAt(Position p, long nowMs) {
        Position me = pc.position();
        if (p.z() != me.z() || me.distanceTo(p) > 1) return false;
        if (chests != null && chests.at(p) != null) {
            pl.fawizja.pixel.treasure.TreasureChests.Chest c = chests.at(p);
            return openChestAt(c, nowMs);
        }
        if (world.crates().isStorage(p)) return useCrate(p, nowMs);
        pl.fawizja.pixel.world.BossQuest bossLever = world.bossQuestByLever(p);
        if (bossLever != null) return pullBossLever(bossLever, nowMs);
        pl.fawizja.pixel.world.AltarQuest leverOf = world.altarQuestByLever(p);
        if (leverOf != null) return pullLever(leverOf, nowMs);
        pl.fawizja.pixel.world.AltarQuest.Slot altar = world.altarAt(p);
        if (altar != null) {
            // przedmiot na oltarzu jest tylko do ogladania: nie trafia do plecaka i nie da sie go przesunac
            log.add(nowMs, EventLog.Kind.INFO, "Na ołtarzu leży " + itemName(altar.itemKey()) + " - jest przytwierdzony"
                    + " i nie da się go podnieść. Przed ołtarzem ma stanąć " + altar.vocation().displayName() + ".");
            return true;
        }
        pl.fawizja.pixel.world.WallLights wl = world.wallLights();
        if (wl.isWallLight(p) && world.isQuestTorch(p)) {
            log.add(nowMs, EventLog.Kind.INFO, wl.isLit(p) ? "Pochodnia płonie, bo przed ołtarzem stoi właściwa profesja."
                    : "Pochodnia zapali się sama, gdy przed ołtarzem stanie właściwa profesja.");
            return true;
        }
        int doorLevel = world.levelDoorAt(p);
        if (doorLevel > 0 && pc.level() < doorLevel) {
            log.add(nowMs, EventLog.Kind.WARNING, levelDoorRefusal(doorLevel));
            return true;
        }
        if (doorLevel > 0 && !world.openings().isOpen(p)) {
            log.add(nowMs, EventLog.Kind.INFO, "Na drzwiach napis: " + WorldMap.levelDoorSign(doorLevel)
                    + ". Masz poziom " + pc.level() + " - drzwi się otwierają.");
            world.openings().set(p, true);
            return true;
        }
        if (wl.isWallLight(p)) {
            // pochodnia albo latarnia na murze: zapala sie i gasi na miejscu, nie trafia do plecaka
            boolean lit = wl.toggle(p);
            boolean torch = wl.isTorch(p);
            log.add(nowMs, EventLog.Kind.INFO, torch ? (lit ? "Zapalasz pochodnię." : "Gasisz pochodnię.")
                    : (lit ? "Zapalasz latarnię." : "Gasisz latarnię."));
            if (!sharedWorld) pc.setWallLights(wl.encode());
            return true;
        }
        pl.fawizja.pixel.world.Openings o = world.openings();
        boolean door = o.isDoor(p);
        if (!door && !o.isWindow(p)) return false;
        if (!mayUseOpening(p)) {
            pl.fawizja.pixel.house.House h = houses.ofOpening(p);
            log.add(nowMs, EventLog.Kind.WARNING, houseAccess(h) == pl.fawizja.pixel.house.HouseAccess.ENTRY
                    ? "Masz w domu " + h.name() + " tylko wstęp - " + (door ? "drzwi" : "okna") + " otwiera właściciel albo gość z pełnym dostępem."
                    : (door ? "To drzwi" : "To okno") + " domu " + h.name() + ". Otwiera je właściciel albo gość z pełnym dostępem.");
            return true;
        }
        if (door && o.isOpen(p) && (p.equals(me) || monsters != null && monsters.monsterAt(p) != null)) {
            log.add(nowMs, EventLog.Kind.INFO, "Ktoś stoi w drzwiach.");
            return true;
        }
        boolean opened = o.toggle(p);
        log.add(nowMs, EventLog.Kind.INFO, door ? (opened ? "Otwierasz drzwi." : "Zamykasz drzwi.")
                : (opened ? "Otwierasz okno." : "Zamykasz okno."));
        return true;
    }

    /** Skrzynia obok gracza: zloto i przedmioty do plecaka, raz na postac. false = nie ma skrzyni obok. */
    public boolean openChest(long nowMs) {
        pl.fawizja.pixel.treasure.TreasureChests.Chest c = chests == null ? null : chests.near(pc.position());
        if (c == null) return false;
        return openChestAt(c, nowMs);
    }

    private boolean openChestAt(pl.fawizja.pixel.treasure.TreasureChests.Chest c, long nowMs) {
        if (c.vocation() != null && c.vocation() != pc.vocation()) {
            log.add(nowMs, EventLog.Kind.WARNING, "Ta skrzynia należy do profesji " + c.vocation().displayName()
                    + ". Nie możesz z niej nic wziąć.");
            return true;
        }
        if (pc.openedChests().contains(c.id())) {
            log.add(nowMs, EventLog.Kind.INFO, c.vocation() != null
                    ? "Ta skrzynia jest pusta. Ta postać już odebrała nagrodę za to zadanie." : "Ta skrzynia jest już pusta.");
            return true;
        }
        if (c.itemKey() == null) {
            // nagroda zadania: samo zloto
            pl.fawizja.pixel.inventory.Container gold = pc.backpack().copy();
            if (!pl.fawizja.pixel.economy.Money.add(gold, c.gold()).ok()) {
                log.add(nowMs, EventLog.Kind.WARNING, "Plecak nie ma miejsca na nagrodę. Zrób miejsce i spróbuj znowu.");
                return true;
            }
            pc.backpack().commitFrom(gold);
            pc.openedChests().add(c.id());
            log.add(nowMs, EventLog.Kind.LOOT, "Nagroda za zadanie: " + String.format("%,d gp", c.gold()) + "!");
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
        log.add(nowMs, EventLog.Kind.INFO, pc.isRunning() ? "Bieg włączony: postać sama idzie do zaznaczonego potwora." : "Bieg wyłączony.");
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
        if (!mayHandle(p)) {
            log.add(nowMs, EventLog.Kind.WARNING, "Masz w tym domu tylko wstęp - nie możesz tu podnosić przedmiotów.");
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
        if (pc.isKept(s.itemId())) {
            log.add(nowMs, EventLog.Kind.WARNING, items.get(s.itemId()).name() + " jest oznaczony jako zachowany. Najpierw go odblokuj.");
            return;
        }
        if (!mayHandle(pc.position())) {
            log.add(nowMs, EventLog.Kind.WARNING, "Masz w tym domu tylko wstęp - nie możesz tu wyrzucać przedmiotów.");
            return;
        }
        if (s.itemId() == torchId && pc.isTorchLit() && pc.backpack().count(torchId) == 1) {
            pc.setTorchLit(false, torchId);
        }
        InventoryResult r = ground.dropFrom(pc.backpack(), slot, s.count(), pc.position(), world);
        if (r.ok()) log.add(nowMs, EventLog.Kind.INFO, "Wyrzucono: " + describe(s));
    }

    /** Przelacza "Zachowaj" dla rodzaju przedmiotu w slocie plecaka. */
    public boolean toggleKeepSlot(int slot, long nowMs) {
        ItemStack s = pc.backpack().get(slot);
        if (s == null) return false;
        boolean on = pc.toggleKept(s.itemId());
        String name = items.get(s.itemId()).name();
        log.add(nowMs, EventLog.Kind.INFO, on
                ? "Zachowany: " + name + ". Nie sprzedasz go hurtem, nie odłożysz hurtem do depozytu i nie wyrzucisz przypadkiem."
                : "Odblokowano: " + name + ".");
        return on;
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

    /** Zdejmuje przedmiot z gniazda ekwipunku do plecaka (okno ekwipunku). */
    public InventoryResult unequip(pl.fawizja.pixel.item.EquipSlot slot, long nowMs) {
        if (slot == pl.fawizja.pixel.item.EquipSlot.NONE) return InventoryResult.EMPTY_SLOT;
        InventoryResult r = pc.equipment().unequipToBackpack(slot, pc.backpack());
        switch (r) {
            case OK: log.add(nowMs, EventLog.Kind.INFO, "Zdjęto przedmiot do plecaka."); break;
            case EMPTY_SLOT: break;
            default: log.add(nowMs, EventLog.Kind.WARNING, "Plecak jest pełny - nie da się zdjąć przedmiotu.");
        }
        return r;
    }

    public InventoryResult moveSlot(int from, int to) {
        ItemStack s = pc.backpack().get(from);
        return s == null ? InventoryResult.EMPTY_SLOT : pc.backpack().move(from, to, s.count());
    }

    /** Porzadkuje plecak (przycisk SORTUJ, akcja "sort"). */
    public boolean sortBackpack(long nowMs) {
        // porzadkowanie wymienia tez pelne setki monet na wyzszy nominal (mniej zajetych pol)
        boolean exchanged = false;
        for (pl.fawizja.pixel.economy.Currency cur : new pl.fawizja.pixel.economy.Currency[] {
                pl.fawizja.pixel.economy.Currency.GOLD, pl.fawizja.pixel.economy.Currency.PLATINUM}) {
            while (pc.backpack().count(cur.itemId()) >= pl.fawizja.pixel.economy.Currency.EXCHANGE_RATE
                    && Money.exchangeUp(pc.backpack(), cur).ok()) exchanged = true;
        }
        boolean moved = pc.backpack().sort() || exchanged;
        log.add(nowMs, EventLog.Kind.INFO, moved ? "Plecak uporządkowany." : "Plecak jest już uporządkowany.");
        return moved;
    }

    public long gold() { return Money.totalValue(pc.backpack()); }

    /** Opisy rol NPC do podgladu pola (dlugie dotkniecie, prawy przycisk myszy). */
    private static String roleLabel(pl.fawizja.pixel.npc.NpcRole r) {
        switch (r) {
            case MERCHANT: return "kupiec";
            case BANKER: return "bankier";
            case PRIEST: return "kapłan";
            case GUIDE: return "przewodnik";
            case CARRIER: return "woźnica";
            case CAPTAIN: return "kapitan statku";
            case KING: return "król";
            case GUARD: return "strażnik";
            default: return r.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * Podglad pola jak "Look" w Tibii (etap 17: dlugie dotkniecie na telefonie, prawy przycisk myszy):
     * potwor z poziomem i zdrowiem, NPC, cialo, przedmioty na ziemi, przejscia i strefa ochronna.
     * Wynik trafia do konsoli i jest zwracany (testy, dymek).
     */
    public String lookAt(Position p, long nowMs) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (p.equals(pc.position())) parts.add("siebie (" + pc.name() + ", poziom " + pc.level() + ")");
        Monster m = monsters == null ? null : monsters.monsterAt(p);
        if (m != null && !m.isDead()) {
            parts.add(m.type().name() + " (poziom " + m.type().level() + ", zdrowie " + Math.round(m.healthFraction() * 100) + "%)");
        }
        pl.fawizja.pixel.npc.Npc npc = npcs == null ? null : npcs.at(p);
        if (npc != null) parts.add(npc.name() + " (" + roleLabel(npc.role()) + ")");
        if (dummies != null && dummies.at(p)) parts.add("manekin treningowy");
        GroundItems.Corpse c = ground.corpseAt(p);
        if (c != null) {
            String who = monsters != null && monsters.registry() != null && monsters.registry().contains(c.monsterKey())
                    ? monsters.registry().get(c.monsterKey()).name() : c.monsterKey();
            parts.add("ciało: " + who + (c.hasLoot() ? " (jest łup)" : " (puste)"));
        }
        java.util.List<ItemStack> lying = ground.at(p);
        for (int i = lying.size() - 1, shown = 0; i >= 0 && shown < 3; i--, shown++) parts.add(describe(lying.get(i)));
        pl.fawizja.pixel.world.AltarQuest.Slot altar = world.altarAt(p);
        if (altar != null) parts.add("ołtarz profesji " + altar.vocation().displayName() + " (" + itemName(altar.itemKey()) + ")");
        if (world.altarQuestByLever(p) != null) parts.add("dźwignia");
        pl.fawizja.pixel.world.BossQuest bq = world.bossQuestByLever(p);
        if (bq != null) parts.add("dźwignia wyprawy " + bq.name() + " (5 postaci na polach, od poziomu " + bq.minLevel() + ")");
        bq = world.bossQuestBySquare(p);
        if (bq != null) parts.add("oznaczone pole wyprawy " + bq.name() + " - tu staje jedna z pięciu postaci");
        bq = world.bossQuestByPortal(p);
        if (bq != null) parts.add("portal wyjścia z areny (otwiera się po pokonaniu bossa)");
        if (world.levelDoorAt(p) > 0) parts.add("drzwi z napisem " + WorldMap.levelDoorSign(world.levelDoorAt(p)));
        pl.fawizja.pixel.world.Transition tr = world.transitionAt(p);
        if (tr != null) {
            switch (tr.type()) {
                case STAIRS: parts.add("schody"); break;
                case HOLE: parts.add("dziura w ziemi"); break;
                case TUNNEL: parts.add("przejście"); break;
                case LADDER: parts.add("drabina"); break;
                case ROPE_SPOT: parts.add("miejsce na linę"); break;
                default: break;
            }
        }
        if (parts.isEmpty()) parts.add(world.isWalkable(p) ? "wolne pole" : "przeszkoda");
        StringBuilder b = new StringBuilder("Widzisz: ").append(String.join(", ", parts)).append('.');
        int dist = p.z() == pc.position().z() ? p.distanceTo(pc.position()) : -1;
        if (dist > 1) {
            boolean few = dist % 10 >= 2 && dist % 10 <= 4 && (dist % 100 < 12 || dist % 100 > 14);
            b.append(" Odległość: ").append(dist).append(few ? " pola." : " pól.");
        }
        if (world.isProtectionZone(p)) b.append(" Strefa ochronna.");
        int req = world.requiredLevelAt(p);
        if (req > 0) b.append(" Wymagany poziom: ").append(req).append('.');
        log.add(nowMs, EventLog.Kind.INFO, b.toString());
        return b.toString();
    }

    public String describe(ItemStack s) {
        String name = items.get(s.itemId()).name();
        return s.count() > 1 ? s.count() + "x " + name : name;
    }
}
