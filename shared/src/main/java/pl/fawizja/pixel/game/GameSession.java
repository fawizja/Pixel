package pl.fawizja.pixel.game;

import pl.fawizja.pixel.i18n.Messages;
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
    private final pl.fawizja.pixel.gathering.BerryBushes berries;
    private final ConsumableRegistry consumables;
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
    /** Podpowiedz przy zamknietych drzwiach (najwyzej raz na {@link #DOOR_HINT_MS}). */
    private long lastDoorHintMs = Long.MIN_VALUE / 2;
    static final long DOOR_HINT_MS = 30_000;
    public static final String DOOR_HINT = "Dotknij drzwi, aby je otworzyć.";
    /** Drzwi poziomu, w ktorych stoi postac: zamykaja sie za nia, gdy z nich zejdzie (jak w Tibii). */
    private Position inLevelDoor;
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
    /** Zapis sprzed mgly mapy: odkryty prostokat wokol swiatyni miasta postaci (w kazda strone). */
    public static final int LEGACY_REVEAL_X = 24, LEGACY_REVEAL_Y = 18;

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
        if (pc.explored().takeLegacy()) {
            // zapis sprzed mgly mapy (v6 i starsze): postac zna okolice swiatyni swojego miasta
            Position temple = world.templeOf(pc.homeTown());
            if (temple != null) {
                pc.explored().reveal(temple.x() - LEGACY_REVEAL_X, temple.y() - LEGACY_REVEAL_Y,
                        temple.x() + LEGACY_REVEAL_X, temple.y() + LEGACY_REVEAL_Y, temple.z());
            }
        }
        pc.explored().revealView(pc.position());
        this.monsters = monsters != null ? monsters
                : new MonsterManager(world, null, Collections.emptyList(), rng);
        this.combat = new CombatSystem(pc, world, items, this.monsters, ground, log, rng, minLevel);
        this.fishing = new FishingSystem(world, items, rng);
        this.herbs = new pl.fawizja.pixel.gathering.HerbGathering(world);
        this.berries = new pl.fawizja.pixel.gathering.BerryBushes(world);
        this.spells = new SpellSystem(pc, world, items, spellRegistry, consumables, combat, this.monsters, log, rng);
        this.consumables = consumables;
        spells.setFacing(() -> facing);
        spells.setMover(new SpellMover());
        this.movement.setOccupancy(p -> this.monsters.monsterAt(p) != null || npcs.at(p) != null || dummies.at(p) || !mayEnter(p)
                || (!world.isProtectionZone(p) && otherPlayers.test(p)));
        this.combat.setKillListener(t -> {
            pc.bestiary().record(t.key(), t.boss());
            if (t.isElite()) pc.bestiary().recordElite();
            bestiaryProgress(t, lastUpdateMs);
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
            public boolean attackable() { return !pc.isDead() && !pc.isInvisible(); }
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
                log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.dzis_nie_mam_dla"));
                return;
            }
            log.add(nowMs, EventLog.Kind.SYSTEM, Messages.get("game_session.zadanie_dnia_pokonaj") + d.target() + "x " + monsterName(d.monster()) + whereToHunt(d.monster()) + ".");
            return;
        }
        if (d.claimed()) {
            log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.dzisiejsze_zadanie_juz_wykonan"));
            return;
        }
        if (!d.doneOn(day)) {
            log.add(nowMs, EventLog.Kind.INFO, Messages.get("hud.zadanie_dnia_2") + monsterName(d.monster()) + " " + d.progress() + " / " + d.target() + ".");
            return;
        }
        pl.fawizja.pixel.monster.MonsterType t = monsterType(d.monster());
        long gold = t == null ? 0 : pl.fawizja.pixel.daily.DailyTask.rewardGold(t, d.target());
        long exp = t == null ? 0 : pl.fawizja.pixel.daily.DailyTask.rewardExp(t, d.target());
        if (gold > 0 && !pc.bank().credit(gold).ok()) return;
        d.claim();
        log.add(nowMs, EventLog.Kind.SYSTEM, Messages.get("game_session.nagroda_za_zadanie_dnia") + String.format("%,d gp", gold) + Messages.get("game_session.na_konto"));
        // nagroda liczona z doswiadczenia potwora, wiec dostaje ten sam mnoznik etapu co zabicie (Balance.expRate)
        exp = pl.fawizja.pixel.balance.Balance.applyRate(exp, pl.fawizja.pixel.balance.Balance.expRate(pc.level()));
        if (exp > 0) combat.awardExperience(exp, Messages.get("hud.zadanie_dnia"), nowMs);
    }

    /** Zmiana zadania dnia u przewodnika (raz dziennie, za monety z plecaka). */
    public boolean dailyReroll(long nowMs) {
        if (talkTo(NpcRole.GUIDE, nowMs) == null) return false;
        pl.fawizja.pixel.daily.DailyTask d = pc.daily();
        long day = today();
        if (!d.canReroll(day)) {
            log.add(nowMs, EventLog.Kind.INFO, d.activeOn(day) && !d.claimed() ? Messages.get("game_session.zadanie_dnia_mozna_zmienic")
                    : Messages.get("game_session.najpierw_wez_zadanie_dnia"));
            return false;
        }
        long price = pl.fawizja.pixel.daily.DailyTask.rerollPrice(pc.level());
        pl.fawizja.pixel.inventory.Container tx = pc.backpack().copy();
        if (Money.totalValue(tx) < price || !Money.pay(tx, price).ok()) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.zmiana_zadania_dnia_kosztuje") + String.format("%,d gp", price) + Messages.get("server_world.monety_w_plecaku"));
            return false;
        }
        if (!d.reroll(pc.name(), day, pc.level(), monsters.spawnedTypes())) {
            log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.nie_mam_dzis_dla"));
            return false;
        }
        pc.backpack().commitFrom(tx);
        log.add(nowMs, EventLog.Kind.SYSTEM, Messages.get("game_session.nowe_zadanie_dnia_pokonaj") + d.target() + "x " + monsterName(d.monster())
                + whereToHunt(d.monster()) + " (" + String.format("%,d gp", price) + ").");
        return true;
    }

    /** Zabicie liczone do zadan: zadanie dnia, zlecenie lowcy i zadania na potwory. */
    private void taskKill(pl.fawizja.pixel.monster.MonsterType t) {
        if (pc.daily().onKill(t.key(), today())) {
            log.add(lastUpdateMs, EventLog.Kind.SYSTEM, Messages.get("game_session.zadanie_dnia_wykonane_wroc"));
        }
        if (pc.hunt().onKill(t.key())) {
            log.add(lastUpdateMs, EventLog.Kind.SYSTEM, Messages.get("game_session.zlecenie_lowcy_wykonane_wroc"));
        }
        QuestType done = quests == null ? null : quests.onKill(pc, t.key());
        if (done != null) {
            log.add(lastUpdateMs, EventLog.Kind.SYSTEM, Messages.get("game_session.zadanie") + done.name() + Messages.get("game_session.cel_osiagniety_wroc_po"));
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
                log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.nie_mam_teraz_dla"));
                return;
            }
            log.add(nowMs, EventLog.Kind.SYSTEM, Messages.get("game_session.zlecenie_lowcy_pokonaj") + h.target() + "x " + monsterName(h.monster()) + whereToHunt(h.monster()) + Messages.get("game_session.droga_idz_lowisko"));
            return;
        }
        if (!h.done()) {
            log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.zlecenie_lowcy") + monsterName(h.monster()) + " " + h.progress() + " / " + h.target()
                    + Messages.get("game_session.porzucic_mozesz_u_przewodnika"));
            return;
        }
        pl.fawizja.pixel.monster.MonsterType t = monsterType(h.monster());
        long gold = t == null ? 0 : pl.fawizja.pixel.quest.HuntTask.rewardGold(t);
        long exp = t == null ? 0 : pl.fawizja.pixel.quest.HuntTask.rewardExp(t);
        if (gold > 0 && !pc.bank().credit(gold).ok()) return;
        h.complete();
        log.add(nowMs, EventLog.Kind.SYSTEM, Messages.get("game_session.nagroda_za_zlecenie_lowcy") + String.format("%,d gp", gold) + Messages.get("game_session.na_konto_2")
                + h.completed() + Messages.get("game_session.oddane_zlecenie"));
        exp = pl.fawizja.pixel.balance.Balance.applyRate(exp, pl.fawizja.pixel.balance.Balance.expRate(pc.level())); // jak przy zadaniu dnia
        if (exp > 0) combat.awardExperience(exp, Messages.get("hud.zlecenie_lowcy"), nowMs);
    }

    public void huntDrop(long nowMs) {
        if (talkTo(NpcRole.GUIDE, nowMs) == null || !pc.hunt().active()) return;
        log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.porzucono_zlecenie") + monsterName(pc.hunt().monster()) + ".");
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
        log.add(nowMs, EventLog.Kind.SYSTEM, Messages.get("game_session.pokonano_bossa_tygodnia_nagrod") + String.format("%,d gp", gold) + Messages.get("game_session.na_konto"));
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
        log.add(nowMs, EventLog.Kind.SYSTEM, Messages.get("game_screen.potwor_dnia") + pl.fawizja.pixel.daily.BoostedCreature.describe(b) + ".");
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
        log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.podpowiedzi_wylaczone"));
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
        return !pc.isDead() && nowMs >= pc.pzBlockedUntil() && world.isProtectionZone(pc.position());
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
        log.add(nowMs, EventLog.Kind.SYSTEM, Messages.get("game_session.nagroda_za_wejscie_do") + day + Messages.get("common.z") + pl.fawizja.pixel.daily.LoginStreak.DAYS
                + "): " + String.format("%,d gp", gold) + Messages.get("game_session.na_konto") + (day < pl.fawizja.pixel.daily.LoginStreak.DAYS
                ? Messages.get("game_session.wroc_jutro_po_wieksza") : Messages.get("game_session.pelny_tydzien")));
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
                if (bag || pc.depot().add(st).ok()) extra = " i " + n + "x " + items.get(st.itemId()).name() + (bag ? "" : Messages.get("game_session.w_depozycie"));
            }
            log.add(nowMs, EventLog.Kind.SYSTEM, Messages.get("events_report.nagroda_za_poziom") + m + ": " + String.format("%,d gp", gold) + Messages.get("game_session.na_konto_3") + extra + ".");
        }
        pc.setRewardedLevel(pc.level());
    }

    /** Nowe osiagniecia: wpis w dzienniku (sprawdzane przy zabiciu potwora i co sekunde). */
    public void checkAchievements(long nowMs) {
        achievementsCheckedMs = nowMs;
        achievementsChecked = true;
        for (pl.fawizja.pixel.achievement.Achievement a : pc.achievements().check(pc)) {
            log.add(nowMs, EventLog.Kind.SYSTEM, Messages.get("game_session.osiagniecie_zdobyte") + a.title() + " (" + a.goal() + ")!");
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

    /**
     * Trening przy manekinie: co TRAIN_MS cios = Balance.DUMMY_TRY_FACTOR prob umiejetnosci broni i (z tarcza
     * albo mnich) Balance.DUMMY_BLOCK_TRY_FACTOR prob tarczy, z mnoznikiem etapu; ok. 2/3 tempa polowania, bez doswiadczenia.
     */
    public static final long TRAIN_MS = pl.fawizja.pixel.balance.Balance.DUMMY_STRIKE_MS;
    private Position trainingAt;
    private Position trainingFrom;
    private long trainNextMs;

    public boolean training() { return trainingAt != null; }
    /** Manekin, przy ktorym postac trenuje (albo null). */
    public Position trainingDummy() { return trainingAt; }
    /** Chwila ostatniego ciosu w manekin (animacja na kliencie); ujemna przed pierwszym. */
    public long lastTrainingStrikeMs() { return trainNextMs - TRAIN_MS; }

    private boolean toggleTraining(long nowMs) {
        Position d = dummies.near(pc.position());
        if (d == null) return false;
        if (trainingAt != null) {
            stopTraining(nowMs, Messages.get("game_session.konczysz_trening"));
            return true;
        }
        trainingAt = d;
        trainingFrom = pc.position();
        trainNextMs = nowMs + TRAIN_MS;
        Direction toDummy = Direction.fromDelta(Integer.signum(d.x() - pc.position().x()), Integer.signum(d.y() - pc.position().y()));
        if (toDummy != null) facing = toDummy;
        log.add(nowMs, EventLog.Kind.INFO, trainingMessage());
        return true;
    }

    /** Komunikat na start treningu: co i jak szybko (proby na minute z etapem serwera, ok. 2/3 polowania). */
    String trainingMessage() {
        Skill sk = CombatFormulas.attackProfile(pc, items).skill();
        long perMin = 60_000 / TRAIN_MS;
        StringBuilder sb = new StringBuilder(Messages.get("game_session.trenujesz_przy_manekinie")).append(sk.label()).append(" ")
                .append(perMin * pl.fawizja.pixel.balance.Balance.DUMMY_TRY_FACTOR).append(Messages.get("game_session.prob_min"));
        if (pl.fawizja.pixel.combat.CombatSystem.trainsShielding(pc)) {
            sb.append(", ").append(Skill.SHIELDING.label()).append(" ")
                    .append(perMin * pl.fawizja.pixel.balance.Balance.DUMMY_BLOCK_TRY_FACTOR).append(Messages.get("game_session.prob_min"));
        }
        sb.append(Messages.get("game_session.x_etap_serwera_ok"));
        if (sk == Skill.MAGIC) sb.append(Messages.get("game_session.magie_szybciej_trenujesz_czara"));
        return sb.append(Messages.get("game_session.ruch_albo_ponowne_dotkniecie")).toString();
    }

    private void stopTraining(long nowMs, String why) {
        trainingAt = null;
        log.add(nowMs, EventLog.Kind.INFO, why);
    }

    private void updateTraining(long nowMs) {
        if (trainingAt == null) return;
        if (!pc.position().equals(trainingFrom) || pc.isDead()) {
            stopTraining(nowMs, Messages.get("game_session.trening_przerwany"));
            return;
        }
        while (nowMs >= trainNextMs && trainingAt != null) {
            trainNextMs += TRAIN_MS;
            Skill sk = CombatFormulas.attackProfile(pc, items).skill();
            trainSkill(nowMs, sk, pl.fawizja.pixel.balance.Balance.DUMMY_TRY_FACTOR);
            if (pl.fawizja.pixel.combat.CombatSystem.trainsShielding(pc)) {
                trainSkill(nowMs, Skill.SHIELDING, pl.fawizja.pixel.balance.Balance.DUMMY_BLOCK_TRY_FACTOR);
            }
        }
    }

    private void trainSkill(long nowMs, Skill sk, int tries) {
        if (pc.skills().train(sk, tries) > 0) {
            log.add(nowMs, EventLog.Kind.SYSTEM, sk.label() + Messages.get("game_session.poziom") + pc.skills().level(sk) + ".");
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
                return lockers.computeIfAbsent(p.translate(dx, dy), q -> new Npc(Npc.DEPOT_LOCKER, Messages.get("game_session.skrzynia_depozytu"),
                        NpcRole.BANKER, nearestTown(q), q, null, Messages.get("game_session.twoj_depozyt_jest_wspolny"), "#7a5230"));
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
        if (h == null) msg = Messages.get("server_world.nie_masz_domu");
        else if (who.isEmpty()) msg = Messages.get("game_session.podaj_imie_dom_wpusc");
        else if (who.equalsIgnoreCase(pc.name())) msg = Messages.get("game_session.to_twoj_dom");
        else if (now == level) msg = who + Messages.get("game_session.juz_ma") + level.label() + Messages.get("game_session.do_domu");
        else if (now == pl.fawizja.pixel.house.HouseAccess.NONE
                && owners().guests(h.id()).size() >= pl.fawizja.pixel.house.HouseOwners.MAX_GUESTS) {
            msg = Messages.get("game_session.dom_moze_miec_najwyzej") + pl.fawizja.pixel.house.HouseOwners.MAX_GUESTS + Messages.get("game_session.gosci");
        }
        if (msg == null && !owners().addGuest(h.id(), who, level)) msg = Messages.get("game_session.nie_udalo_sie_dodac");
        if (msg != null) {
            log.add(nowMs, EventLog.Kind.WARNING, msg);
            return false;
        }
        log.add(nowMs, EventLog.Kind.SYSTEM, level == pl.fawizja.pixel.house.HouseAccess.FULL
                ? who + Messages.get("game_session.moze_teraz_wchodzic_do") + h.name() + Messages.get("game_session.i_korzystac_ze_skrzyn")
                : who + Messages.get("game_session.moze_teraz_wchodzic_do") + h.name() + Messages.get("game_session.tylko_wejscie_bez_skrzyn"));
        return true;
    }

    /** Wlasciciel odbiera gosciowi wstep. */
    public boolean removeHouseGuest(String name, long nowMs) {
        pl.fawizja.pixel.house.House h = houses.get(pc.house());
        if (h == null || !owners().removeGuest(h.id(), name)) {
            log.add(nowMs, EventLog.Kind.WARNING, h == null ? Messages.get("server_world.nie_masz_domu") : name + Messages.get("game_session.nie_jest_gosciem_twojego"));
            return false;
        }
        log.add(nowMs, EventLog.Kind.SYSTEM, name.trim() + Messages.get("game_session.nie_moze_juz_wchodzic") + h.name() + ".");
        return true;
    }

    /** Opis domu postaci: nazwa, miasto, czynsz, goscie. */
    public String houseInfo() {
        pl.fawizja.pixel.house.House h = houses.get(pc.house());
        if (h == null) return Messages.get("game_session.nie_masz_domu_kupisz");
        java.util.List<String> g = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, pl.fawizja.pixel.house.HouseAccess> e : owners().guestLevels(h.id()).entrySet()) {
            g.add(e.getKey() + (e.getValue() == pl.fawizja.pixel.house.HouseAccess.ENTRY ? Messages.get("game_session.wejscie") : Messages.get("game_session.pelny")));
        }
        long until = owners().rentPaidUntil(h.id());
        String due = until > 0 ? Messages.get("game_session.nastepna_platnosc_za") + pl.fawizja.pixel.house.HouseRent.describeIn(until - wallClock.getAsLong()) : "";
        return String.format(Messages.get("game_session.twoj_dom_s_s"), h.name(), h.town(),
                h.size(), h.price(), pl.fawizja.pixel.house.HouseRent.rent(h), due,
                g.isEmpty() ? Messages.get("game_session.nikt_dom_wpusc_imie") : String.join(", ", g));
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
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.ten_zarzadca_nie_sprzedaje"));
            return false;
        }
        return claimHouse(h, nowMs);
    }

    /** Przy drzwiach wolnego domu (okno domu, przycisk Kup): kupno za zloto z banku. */
    public boolean buyHouseAtDoor(String id, long nowMs) {
        pl.fawizja.pixel.house.House h = houses.get(id);
        if (h == null) return false;
        if (pc.position().z() != h.z() || pc.position().distanceTo(h.door()) > 1) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.podejdz_do_drzwi_domu") + h.name() + ".");
            return false;
        }
        return claimHouse(h, nowMs);
    }

    /** Zloto, ktorym postac moze zaplacic za dom: konto w banku plus monety w plecaku. */
    public long houseFunds() {
        return pc.bank().balance() + Money.totalValue(pc.backpack());
    }

    /**
     * Kupno domu: najpierw z konta w banku, brakujaca reszta monetami z plecaka (jak w Tibii, gdzie dom placi sie
     * z konta, a gracz z gotowka w plecaku nie musi isc do bankiera). Nic sie nie zmienia, gdy zlota jest za malo.
     */
    private boolean claimHouse(pl.fawizja.pixel.house.House h, long nowMs) {
        String msg = null;
        long bank = pc.bank().balance();
        long cash = Money.totalValue(pc.backpack());
        if (pc.house() != null) msg = Messages.get("game_session.masz_juz_dom_najpierw");
        else if (owners().ownerOf(h.id()) != null || takenHouses.contains(h.id())) msg = Messages.get("game_session.ten_dom_ma_juz");
        else if (bank + cash < h.price()) {
            msg = String.format(Messages.get("game_session.dom_kosztuje_d_gp"), h.price(), bank + cash, bank, cash);
        }
        if (msg != null) {
            log.add(nowMs, EventLog.Kind.WARNING, msg);
            return false;
        }
        long fromBank = Math.min(bank, h.price());
        long fromPack = h.price() - fromBank;
        pl.fawizja.pixel.inventory.Container pack = pc.backpack().copy();
        if (fromPack > 0 && !Money.pay(pack, fromPack).ok()) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.nie_udalo_sie_odliczyc"));
            return false;
        }
        if (!owners().claim(h.id(), pc.name())) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.ten_dom_ma_juz"));
            return false;
        }
        if (fromBank > 0) pc.bank().debit(fromBank);
        if (fromPack > 0) pc.backpack().commitFrom(pack);
        pc.setHouse(h.id());
        owners().setRent(h.id(), wallClock.getAsLong() + pl.fawizja.pixel.house.HouseRent.period(), 0);   // pierwszy tydzien w cenie
        rentLowWarned = false;
        String paid = fromPack == 0 ? Messages.get("game_session.z_konta") : fromBank == 0 ? Messages.get("hud.z_plecaka_2")
                : String.format(Messages.get("game_session.d_gp_z_konta"), fromBank, fromPack);
        log.add(nowMs, EventLog.Kind.SYSTEM, String.format(Messages.get("game_session.kupiono_dom_s_za")
                + Messages.get("game_session.automatycznie_z_banku_gosci"),
                h.name(), h.price(), paid, pl.fawizja.pixel.house.HouseRent.rent(h)));
        return true;
    }

    /** Online: domy z wlascicielem (wiadomosc HACC z serwera); offline zawsze pusto - wlascicielem jest tylko ta postac. */
    private java.util.Set<String> takenHouses = java.util.Set.of();

    public void setTakenHouses(java.util.Collection<String> ids) {
        takenHouses = ids == null ? java.util.Set.of() : java.util.Set.copyOf(ids);
    }

    /** Czy dom jest na sprzedaz (wolny) - znak "Na sprzedaż" nad drzwiami i przycisk Kup. */
    public boolean houseForSale(pl.fawizja.pixel.house.House h) {
        return h != null && owners().ownerOf(h.id()) == null && !takenHouses.contains(h.id());
    }

    /** Lozka we wnetrzu domu (pola z obiektem "bed"). */
    public int houseBeds(pl.fawizja.pixel.house.House h) {
        return bedsIn(h).size();
    }

    public java.util.List<Position> bedsIn(pl.fawizja.pixel.house.House h) {
        java.util.List<Position> out = new java.util.ArrayList<>();
        if (h == null) return out;
        int[] b = {h.x0(), h.y0(), h.x1(), h.y1()};
        int[] w = h.wings();
        for (int i = 0; i < w.length; i += 4) {
            b[0] = Math.min(b[0], w[i]);
            b[1] = Math.min(b[1], w[i + 1]);
            b[2] = Math.max(b[2], w[i + 2]);
            b[3] = Math.max(b[3], w[i + 3]);
        }
        for (int y = b[1]; y <= b[3]; y++) {
            for (int x = b[0]; x <= b[2]; x++) {
                Position p = new Position(x, y, h.z());
                pl.fawizja.pixel.world.TileType o = h.contains(p) ? objectAt(p) : null;
                if (o != null && o.key().equals("bed")) out.add(p);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ lozka (jak w Tibii: sen = wylogowanie)

    /** Lozko, w ktorym postac sie polozyla (klient pokazuje sen i wylogowuje); null = nie spi. */
    private Position sleepBed;

    /** Lozko, w ktorym postac wlasnie sie polozyla; odczyt zeruje (klient albo serwer wylogowuje postac). */
    public Position takeSleep() {
        Position b = sleepBed;
        sleepBed = null;
        return b;
    }

    /**
     * Dotkniecie lozka w cudzym domu (bez wstepu): od razu "To nie twoje łóżko." zamiast marszu pod okno.
     * True, gdy odmowiono.
     */
    public boolean refuseBed(Position p, long nowMs) {
        pl.fawizja.pixel.world.TileType o = objectAt(p);
        if (o == null || !o.key().equals("bed")) return false;
        pl.fawizja.pixel.house.House h = houses.at(p);
        if (h == null || houseAccess(h).mayEnter()) return false;
        log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.to_nie_twoje_lozko"));
        return true;
    }

    /** Czy postac lezy w lozku (przed wylogowaniem). */
    public Position sleepingIn() { return sleepBed; }

    /**
     * Dotkniecie lozka obok: w domu, do ktorego postac ma wstep (wlasciciel albo gosc), postac kladzie sie spac
     * i wylogowuje (zapis z chwila zasniecia - po powrocie odnowione zycie i mana, patrz {@link
     * pl.fawizja.pixel.house.BedRest}). W cudzym domu "To nie twoje łóżko.", w walce odmowa jak przy wylogowaniu.
     */
    private boolean useBed(Position p, long nowMs) {
        pl.fawizja.pixel.house.House h = houses.at(p);
        if (h == null) {
            log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.wygodne_lozko_ale_nie"));
            return true;
        }
        if (!houseAccess(h).mayEnter()) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.to_nie_twoje_lozko"));
            return true;
        }
        long block = pc.logoutBlockedMs(nowMs);
        if (block > 0) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.nie_mozesz_zasnac_w") + (block + 999) / 1000 + " s.");
            return true;
        }
        if (pc.isDead() || sleepBed != null) return true;
        sleepBed = p;
        pc.setSleptAt(wallClock.getAsLong());
        moving = false;
        log.add(nowMs, EventLog.Kind.SYSTEM, Messages.get("game_session.kladziesz_sie_do_lozka") + h.name()
                + Messages.get("game_session.i_zasypiasz_postac_wypocznie"));
        return true;
    }

    /** Miasto odkupuje dom za tyle procent ceny. */
    public static final int HOUSE_SELLBACK_PERCENT = 50;

    /** U zarzadcy domow: sprzedaz domu miastu (polowa ceny na konto); postac stojaca w srodku wychodzi przez drzwi. */
    public boolean leaveHouse(long nowMs) {
        if (talkTo(NpcRole.HOUSING, nowMs) == null) return false;
        pl.fawizja.pixel.house.House h = houses.get(pc.house());
        if (h == null) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("server_world.nie_masz_domu"));
            return false;
        }
        return abandonHouse(h, nowMs);
    }

    /** Okno wlasciciela przy drzwiach: "Opusc dom" - jak sprzedaz miastu u zarzadcy. */
    public boolean leaveHouseAtDoor(long nowMs) {
        pl.fawizja.pixel.house.House h = houses.get(pc.house());
        if (h == null) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("server_world.nie_masz_domu"));
            return false;
        }
        if (!h.contains(pc.position()) && (pc.position().z() != h.z() || pc.position().distanceTo(h.door()) > 1)) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.podejdz_do_drzwi_swojego"));
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
        log.add(nowMs, EventLog.Kind.SYSTEM, String.format(Messages.get("game_session.sprzedano_dom_s_miastu"), h.name(), refund)
                + (moved > 0 ? Messages.get("game_session.rzeczy_z_domu") + moved + Messages.get("game_session.sa_w_depozycie_u") : ""));
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
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.czynsz_za_dom") + h.name() + Messages.get("game_session.nie_byl_placony") + pl.fawizja.pixel.house.HouseRent.MAX_MISSED
                    + Messages.get("game_session.razy_dom_wrocil_do") + (moved > 0 ? Messages.get("game_session.rzeczy_z_domu") + moved + Messages.get("game_session.sa_w_depozycie_u") : ""));
            return;
        }
        if (r != null) {
            if (r.changed(until, missed)) owners().setRent(h.id(), r.paidUntil(), r.missed());
            if (r.charged() > 0) {
                log.add(nowMs, EventLog.Kind.SYSTEM, String.format(Messages.get("game_session.pobrano_czynsz_za_dom"),
                        h.name(), r.charged(), pc.bank().balance()));
            }
            if (r.newlyMissed() > 0) {
                log.add(nowMs, EventLog.Kind.WARNING, String.format(Messages.get("game_session.brak_zlota_na_czynsz")
                        + Messages.get("game_session.banku_kolejna_nieoplacona_plat"), h.name(),
                        rent * (r.missed() + 1), pc.bank().balance()));
                rentLowWarned = true;
            }
        }
        if (pc.bank().balance() >= rent * (owners().rentMissed(h.id()) + 1)) {
            rentLowWarned = false;
        } else if (!rentLowWarned) {
            rentLowWarned = true;
            log.add(nowMs, EventLog.Kind.WARNING, String.format(Messages.get("game_session.na_koncie_masz_d"),
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
                r.ok() ? Messages.get("game_session.kupiono") + count + "x " + items.get(itemId).name() : r.message());
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
            log.add(nowMs, EventLog.Kind.LOOT, Messages.get("game_session.kupiono") + String.join(", ", bought) + (poor ? Messages.get("game_session.zabraklo_monet_na_reszte") : "."));
        } else {
            log.add(nowMs, poor ? EventLog.Kind.WARNING : EventLog.Kind.INFO,
                    poor ? Messages.get("game_session.za_malo_monet_w") : Messages.get("game_session.masz_juz_pelne_zapasy") + REFILL_TO + Messages.get("game_session.mikstur_i_run")
                            + REFILL_AMMO_TO + Messages.get("game_session.sztuk_amunicji"));
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
                r.ok() ? Messages.get("game_session.sprzedano") + count + "x " + items.get(itemId).name() + " za " + value + " gp" : r.message());
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
            log.add(nowMs, EventLog.Kind.INFO, n.name() + Messages.get("game_session.nie_masz_lupu_ktory") + (kept ? Messages.get("game_session.przedmioty_do_zadan_i") : "."));
        } else {
            log.add(nowMs, EventLog.Kind.LOOT, Messages.get("game_session.sprzedano_lup") + sold + Messages.get("game_session.szt_za") + String.format("%,d gp", total)
                    + (kept ? Messages.get("game_session.przedmioty_do_zadan_i_2") : "."));
        }
        return sold;
    }

    public InventoryResult depositAll(long nowMs) {
        if (talkTo(NpcRole.BANKER, nowMs) == null) return InventoryResult.INVALID_AMOUNT;
        long before = gold();
        InventoryResult r = pc.bank().depositAll(pc.backpack());
        log.add(nowMs, r.ok() ? EventLog.Kind.INFO : EventLog.Kind.WARNING,
                r.ok() ? Messages.get("game_session.wplacono") + before + Messages.get("game_session.gp_saldo") + pc.bank().balance() + " gp." : Messages.get("game_session.nie_masz_monet_do"));
        return r;
    }

    public InventoryResult withdraw(long amount, long nowMs) {
        if (talkTo(NpcRole.BANKER, nowMs) == null) return InventoryResult.INVALID_AMOUNT;
        InventoryResult r = pc.bank().withdraw(pc.backpack(), amount);
        log.add(nowMs, r.ok() ? EventLog.Kind.INFO : EventLog.Kind.WARNING,
                r.ok() ? Messages.get("game_session.wyplacono") + amount + Messages.get("game_session.gp_saldo") + pc.bank().balance() + " gp."
                        : r == InventoryResult.INSUFFICIENT_FUNDS ? Messages.get("game_session.za_malo_pieniedzy_na") : Messages.get("server_world.brak_miejsca_w_plecaku"));
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
            log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.nie_masz_lupu_do") + (keep.isEmpty() ? "." : Messages.get("game_session.przedmioty_do_zadan_i")));
            return 0;
        }
        int moved = 0;
        for (int id : ids) moved += moveAll(pc.backpack(), pc.depot(), id, nowMs, Messages.get("game_session.odlozono_do_depozytu"), Messages.get("game_session.depozyt_jest_pelny"));
        return moved;
    }

    public int depotStore(int itemId, long nowMs) {
        if (talkTo(NpcRole.BANKER, nowMs) == null) return 0;
        if (pl.fawizja.pixel.economy.Currency.isCoin(itemId)) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.monety_wplac_na_konto"));
            return 0;
        }
        return moveAll(pc.backpack(), pc.depot(), itemId, nowMs, Messages.get("game_session.odlozono_do_depozytu"), Messages.get("game_session.depozyt_jest_pelny"));
    }

    /** Depozyt: wyjmuje do plecaka wszystkie sztuki przedmiotu (ile sie zmiesci). */
    public int depotTake(int itemId, long nowMs) {
        if (talkTo(NpcRole.BANKER, nowMs) == null) return 0;
        return moveAll(pc.depot(), pc.backpack(), itemId, nowMs, Messages.get("game_session.wyjeto_z_depozytu"), Messages.get("server_world.brak_miejsca_w_plecaku"));
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
        log.add(nowMs, EventLog.Kind.INFO, done + ": " + n + "x " + name + (n < have ? Messages.get("game_session.reszta_sie_nie_zmiescila") : "") + ".");
        return n;
    }

    private static final java.util.Set<String> HEAL_WORDS = java.util.Set.of("help", "heal", "ulecz", "pomocy", "pomoc", "lecz");

    /** Kaplan: pelne leczenie i mana (tylko w swiatyni, wiec poza walka). */
    public void priestHeal(long nowMs) {
        if (talkTo(NpcRole.PRIEST, nowMs) == null) return;
        pc.heal(pc.maxHealth());
        pc.restoreMana(pc.maxMana());
        log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.kaplan_cie_uleczyl"));
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
        if (ship && !pc.canSail()) {
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + Messages.get("game_session.rejsy_tylko_dla_kont")
                    + premiumHint(n.town()));
            return false;
        }
        pl.fawizja.pixel.npc.Travel.Route route = null;
        for (pl.fawizja.pixel.npc.Travel.Route r : pl.fawizja.pixel.npc.Travel.routes(npcs, n)) {
            if (r.to().town().equalsIgnoreCase(town)) route = r;
        }
        if (route == null) {
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + (ship ? Messages.get("game_session.tam_nie_plywam") : Messages.get("game_session.tam_nie_jezdze")));
            return false;
        }
        if (nowMs < pc.pzBlockedUntil()) {
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + Messages.get("game_session.po_walce_z_graczem")
                    + ((pc.pzBlockedUntil() - nowMs + 999) / 1000) + " s.");
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
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + ": " + route.to().town() + Messages.get("game_session.jest_dla_postaci_od")
                    + world.requiredLevelAt(dest) + ".");
            return false;
        }
        pl.fawizja.pixel.inventory.Container tx = pc.backpack().copy();
        if (Money.totalValue(tx) < route.price() || !Money.pay(tx, route.price()).ok()) {
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + (ship ? Messages.get("game_session.rejs_do") : Messages.get("game_session.przejazd_do")) + route.to().town() + " kosztuje "
                    + route.price() + Messages.get("game_session.gp_monety_w_plecaku"));
            return false;
        }
        pc.backpack().commitFrom(tx);
        teleport(dest, nowMs);
        log.add(nowMs, EventLog.Kind.SYSTEM, (ship ? Messages.get("game_session.po_rejsie") : Messages.get("game_session.po_podrozy")) + route.price() + Messages.get("game_session.gp_jestes")
                + route.to().town() + ".");
        return true;
    }

    /** Cena konta premium i promocji u krola (z konta w banku). */
    public static final long PREMIUM_PRICE = 500_000, PROMOTION_PRICE = 1_000_000;
    /** Promocja od tego poziomu (jak w Tibii od 20). */
    public static final int PROMOTION_LEVEL = 20;
    private static final java.util.Set<String> PREMIUM_WORDS = java.util.Set.of("premium", "konto premium");
    private static final java.util.Set<String> PROMOTION_WORDS = java.util.Set.of("promocja", "promocję", "promocje", "promotion", "promote");

    /**
     * Bestiariusz jak w Tibii (Canary IOBestiary::addBestiaryKill): przy kazdym nowym etapie wpisu komunikat
     * "odblokowano szczegoly", przy ukonczeniu punkty charmow.
     */
    private void bestiaryProgress(pl.fawizja.pixel.monster.MonsterType t, long nowMs) {
        pl.fawizja.pixel.achievement.BestiaryEntry e = pl.fawizja.pixel.achievement.BestiaryData.of(t);
        if (e == null) return;
        int kills = pc.bestiary().kills(t.key());
        int stage = e.stage(kills);
        if (stage == e.stage(kills - 1)) return;
        if (stage == pl.fawizja.pixel.achievement.BestiaryEntry.STAGES) {
            pc.charms().addPoints(e.charmPoints());
            log.add(nowMs, EventLog.Kind.SYSTEM, String.format(Messages.get("game_session.bestiariusz_ukonczono_wpis_s"),
                    t.name(), e.charmPoints(), pc.charms().points()));
        } else {
            log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.bestiariusz_odblokowano_szczeg") + t.name() + Messages.get("game_session.etap") + stage + "/"
                    + pl.fawizja.pixel.achievement.BestiaryEntry.STAGES + ").");
        }
    }

    /** Charmy: kupno albo ulepszenie o stopien (akcja "charm:buy:NAZWA"). */
    public boolean buyCharm(pl.fawizja.pixel.charm.Charm c, long nowMs) {
        if (c == null) return false;
        pl.fawizja.pixel.charm.Charms.Result r = pc.charms().buy(c);
        log.add(nowMs, r.ok() ? EventLog.Kind.SYSTEM : EventLog.Kind.WARNING, r.message());
        return r.ok();
    }

    /** Charmy: zalozenie na potwora (akcja "charm:assign:NAZWA:klucz"). */
    public boolean assignCharm(pl.fawizja.pixel.charm.Charm c, String monsterKey, long nowMs) {
        if (c == null || monsterKey == null) return false;
        pl.fawizja.pixel.charm.Charms.Result r = pc.charms().assign(c, monsterKey, pc.bestiary(), pc.isPremium(), monsterName(monsterKey));
        log.add(nowMs, r.ok() ? EventLog.Kind.SYSTEM : EventLog.Kind.WARNING, r.message());
        return r.ok();
    }

    /** Charmy: zdjecie z potwora za {@link pl.fawizja.pixel.charm.CharmPrices#removal} (akcja "charm:remove:NAZWA"). */
    public boolean removeCharm(pl.fawizja.pixel.charm.Charm c, long nowMs) {
        if (c == null || pc.charms().assignedTo(c) == null) return false;
        long fee = pl.fawizja.pixel.charm.CharmPrices.removal(pc.level());
        String from = monsterName(pc.charms().assignedTo(c));
        if (!payGold(fee)) {
            log.add(nowMs, EventLog.Kind.WARNING, String.format(Messages.get("game_session.zdjecie_charmu_kosztuje_d"), fee));
            return false;
        }
        pc.charms().remove(c);
        log.add(nowMs, EventLog.Kind.SYSTEM, String.format(Messages.get("game_session.s_zdjety_z_s"), c.label(), from, fee));
        return true;
    }

    /** Charmy: reset wszystkich za {@link pl.fawizja.pixel.charm.CharmPrices#resetAll} (akcja "charm:reset"). */
    public boolean resetCharms(long nowMs) {
        long fee = pl.fawizja.pixel.charm.CharmPrices.resetAll(pc.level());
        if (!payGold(fee)) {
            log.add(nowMs, EventLog.Kind.WARNING, String.format(Messages.get("game_session.reset_charmow_kosztuje_d"), fee));
            return false;
        }
        pc.charms().resetAll(pc.isPromoted());
        log.add(nowMs, EventLog.Kind.SYSTEM, String.format(Messages.get("game_session.charmy_zresetowane_d_gp"),
                fee, pc.charms().points(), pc.charms().echoes()));
        return true;
    }

    // ------------------------------------------------------------------ Kolo Przeznaczenia

    /** Promien wokol kaplana, w ktorym (w strefie ochronnej) mozna przestawiac punkty kola. */
    public static final int WHEEL_TEMPLE_RANGE = 8;

    /**
     * Swiatynia: strefa ochronna z kaplanem najwyzej {@link #WHEEL_TEMPLE_RANGE} pol dalej na tym samym pietrze
     * (w Tibii kolo zmienia sie tylko w strefie ochronnej; u nas dodatkowo przy kaplanie - jak "swiatynia").
     */
    public boolean inTemple() {
        Position p = pc.position();
        if (pc.isDead() || !world.isProtectionZone(p)) return false;
        for (Npc n : npcs.all()) {
            if (n.role() != NpcRole.PRIEST || n.position().z() != p.z()) continue;
            if (Math.max(Math.abs(n.position().x() - p.x()), Math.abs(n.position().y() - p.y())) <= WHEEL_TEMPLE_RANGE) return true;
        }
        return false;
    }

    private pl.fawizja.pixel.wheel.DestinyWheel.Result wheelGate() {
        if (pc.wheelPoints() <= 0) return pl.fawizja.pixel.wheel.DestinyWheel.Result.NOT_ELIGIBLE;
        if (!inTemple()) return pl.fawizja.pixel.wheel.DestinyWheel.Result.NOT_IN_TEMPLE;
        return pl.fawizja.pixel.wheel.DestinyWheel.Result.OK;
    }

    private boolean wheelDone(pl.fawizja.pixel.wheel.DestinyWheel.Result r, String okText, long nowMs) {
        if (r == pl.fawizja.pixel.wheel.DestinyWheel.Result.OK) {
            pc.refreshWheel();
            if (okText != null) log.add(nowMs, EventLog.Kind.SYSTEM, okText);
            return true;
        }
        log.add(nowMs, EventLog.Kind.WARNING, r.message());
        return false;
    }

    /** Kolo: n punktow do pola (akcja "wheel:add:KOD:n"). */
    public boolean wheelAdd(String code, int n, long nowMs) {
        pl.fawizja.pixel.wheel.WheelSlice sl = pl.fawizja.pixel.wheel.WheelSlice.ofCode(code);
        if (sl == null || n <= 0) return false;
        pl.fawizja.pixel.wheel.DestinyWheel.Result r = wheelGate();
        if (r == pl.fawizja.pixel.wheel.DestinyWheel.Result.OK) r = pc.wheel().add(sl, n, pc.wheelPoints());
        return wheelDone(r, null, nowMs);
    }

    /** Kolo: n punktow z pola (akcja "wheel:remove:KOD:n"). */
    public boolean wheelRemove(String code, int n, long nowMs) {
        pl.fawizja.pixel.wheel.WheelSlice sl = pl.fawizja.pixel.wheel.WheelSlice.ofCode(code);
        if (sl == null || n <= 0) return false;
        pl.fawizja.pixel.wheel.DestinyWheel.Result r = wheelGate();
        if (r == pl.fawizja.pixel.wheel.DestinyWheel.Result.OK) r = pc.wheel().remove(sl, n, pc.wheelPoints());
        return wheelDone(r, null, nowMs);
    }

    /** Kolo: zwrot wszystkich punktow, bez oplaty jak w Tibii (akcja "wheel:reset"). */
    public boolean wheelReset(long nowMs) {
        pl.fawizja.pixel.wheel.DestinyWheel.Result r = wheelGate();
        if (r == pl.fawizja.pixel.wheel.DestinyWheel.Result.OK) r = pc.wheel().reset();
        return wheelDone(r, Messages.get("game_session.kolo_przeznaczenia_wyczyszczon"), nowMs);
    }

    /** Kolo: zapis biezacego ustawienia do zestawu i (akcja "wheel:save:i"); zapis dziala wszedzie. */
    public boolean wheelPresetSave(int i, long nowMs) {
        if (i < 0 || i >= pl.fawizja.pixel.wheel.DestinyWheel.PRESETS) return false;
        pl.fawizja.pixel.wheel.DestinyWheel.Result r = pc.wheelPoints() <= 0
                ? pl.fawizja.pixel.wheel.DestinyWheel.Result.NOT_ELIGIBLE : pc.wheel().savePreset(i);
        return wheelDone(r, Messages.get("game_session.zapisano_zestaw_kola") + (i + 1) + ".", nowMs);
    }

    /** Kolo: wczytanie zestawu i (akcja "wheel:load:i"), tylko w swiatyni. */
    public boolean wheelPresetLoad(int i, long nowMs) {
        if (i < 0 || i >= pl.fawizja.pixel.wheel.DestinyWheel.PRESETS) return false;
        pl.fawizja.pixel.wheel.DestinyWheel.Result r = wheelGate();
        if (r == pl.fawizja.pixel.wheel.DestinyWheel.Result.OK) r = pc.wheel().loadPreset(i, pc.wheelPoints());
        return wheelDone(r, Messages.get("game_session.wczytano_zestaw_kola") + (i + 1) + ".", nowMs);
    }

    // ------------------------------------------------------------------ Klejnoty Kola Przeznaczenia

    private boolean gemDone(pl.fawizja.pixel.wheel.GemAtelier.Result r, String okText, long nowMs) {
        if (r == pl.fawizja.pixel.wheel.GemAtelier.Result.OK) {
            pc.refreshWheel();
            if (okText != null) log.add(nowMs, EventLog.Kind.SYSTEM, okText);
            return true;
        }
        log.add(nowMs, EventLog.Kind.WARNING, r.message());
        return false;
    }

    private boolean canPay(long gold) { return Money.totalValue(pc.backpack()) >= gold || pc.bank().balance() >= gold; }

    private int itemId(String key) { return items.containsKey(key) ? items.byKey(key).id() : -1; }

    /** Pracownia: odkrycie klejnotu swojej profesji z plecaka za zloto (akcja "wheel:gem:reveal:L|R|G"). */
    public boolean gemReveal(pl.fawizja.pixel.wheel.GemQuality q, long nowMs) {
        if (q == null) return false;
        pl.fawizja.pixel.wheel.GemAtelier a = pc.wheel().gems();
        pl.fawizja.pixel.wheel.GemAtelier.Result r = pc.wheelPoints() <= 0 ? pl.fawizja.pixel.wheel.GemAtelier.Result.NOT_ELIGIBLE : a.canReveal();
        int gemId = itemId(pl.fawizja.pixel.wheel.GemAtelier.gemKey(pc.vocation(), q));
        if (r == pl.fawizja.pixel.wheel.GemAtelier.Result.OK && (gemId < 0 || pc.backpack().count(gemId) <= 0)) r = pl.fawizja.pixel.wheel.GemAtelier.Result.NO_GEM;
        if (r == pl.fawizja.pixel.wheel.GemAtelier.Result.OK && !canPay(q.revealCost)) r = pl.fawizja.pixel.wheel.GemAtelier.Result.NO_GOLD;
        if (r != pl.fawizja.pixel.wheel.GemAtelier.Result.OK) return gemDone(r, null, nowMs);
        if (!payGold(q.revealCost)) return gemDone(pl.fawizja.pixel.wheel.GemAtelier.Result.NO_GOLD, null, nowMs);
        pc.backpack().remove(gemId, 1);
        pl.fawizja.pixel.wheel.WheelGem g = a.reveal(q, pc.vocation(), rng);
        return gemDone(r, String.format(Messages.get("game_session.pracownia_klejnotow_odkryto_s"), g.title(), q.revealCost), nowMs);
    }

    /** Pracownia: blokada / odblokowanie klejnotu (akcja "wheel:gem:lock:ID"). */
    public boolean gemLock(int id, long nowMs) {
        pl.fawizja.pixel.wheel.GemAtelier a = pc.wheel().gems();
        pl.fawizja.pixel.wheel.GemAtelier.Result r = a.toggleLock(id);
        pl.fawizja.pixel.wheel.WheelGem g = a.gem(id);
        return gemDone(r, g == null ? null : g.title() + (g.locked() ? Messages.get("game_session.zablokowany") : Messages.get("game_session.odblokowany")), nowMs);
    }

    /** Pracownia: rozbicie klejnotu na okruchy (akcja "wheel:gem:destroy:ID"); okruchy trafiaja do plecaka. */
    public boolean gemDestroy(int id, long nowMs) {
        pl.fawizja.pixel.wheel.GemAtelier a = pc.wheel().gems();
        pl.fawizja.pixel.wheel.GemAtelier.Result r = a.canChange(id);
        if (r != pl.fawizja.pixel.wheel.GemAtelier.Result.OK) return gemDone(r, null, nowMs);
        pl.fawizja.pixel.wheel.WheelGem g = a.gem(id);
        String key = pl.fawizja.pixel.wheel.GemAtelier.fragmentKey(g.quality.greaterFragments);
        int fragId = itemId(key);
        int n = pl.fawizja.pixel.wheel.GemAtelier.rollFragments(g.quality, rng);
        pl.fawizja.pixel.inventory.Container tx = pc.backpack().copy();
        if (fragId < 0 || !tx.add(new ItemStack(fragId, n)).ok()) return gemDone(pl.fawizja.pixel.wheel.GemAtelier.Result.NO_ROOM, null, nowMs);
        pc.backpack().commitFrom(tx);
        a.destroy(id);
        return gemDone(r, String.format(Messages.get("game_session.rozbito_s_d_s"), g.title(), n, items.byKey(key).name().toLowerCase(java.util.Locale.ROOT)), nowMs);
    }

    /** Pracownia: zmiana domeny klejnotu na nastepna za zloto (akcja "wheel:gem:switch:ID"). */
    public boolean gemSwitch(int id, long nowMs) {
        pl.fawizja.pixel.wheel.GemAtelier a = pc.wheel().gems();
        pl.fawizja.pixel.wheel.GemAtelier.Result r = a.canChange(id);
        pl.fawizja.pixel.wheel.WheelGem g = a.gem(id);
        if (r == pl.fawizja.pixel.wheel.GemAtelier.Result.OK && !canPay(g.quality.switchCost)) r = pl.fawizja.pixel.wheel.GemAtelier.Result.NO_GOLD;
        if (r == pl.fawizja.pixel.wheel.GemAtelier.Result.OK && !payGold(g.quality.switchCost)) r = pl.fawizja.pixel.wheel.GemAtelier.Result.NO_GOLD;
        if (r != pl.fawizja.pixel.wheel.GemAtelier.Result.OK) return gemDone(r, null, nowMs);
        a.switchDomain(id);
        return gemDone(r, String.format(Messages.get("game_session.s_nowa_domena_s"), Messages.get("game_session.klejnot") + id, g.domain().label().toLowerCase(java.util.Locale.ROOT),
                g.quality.switchCost), nowMs);
    }

    /** Naczynie: wlozenie klejnotu do naczynia jego domeny, tylko w swiatyni (akcja "wheel:gem:socket:ID"). */
    public boolean gemSocket(int id, long nowMs) {
        pl.fawizja.pixel.wheel.GemAtelier a = pc.wheel().gems();
        pl.fawizja.pixel.wheel.GemAtelier.Result r = gemTempleGate();
        if (r == pl.fawizja.pixel.wheel.GemAtelier.Result.OK) r = a.socket(id);
        pl.fawizja.pixel.wheel.WheelGem g = a.gem(id);
        return gemDone(r, g == null ? null : g.title() + Messages.get("game_session.w_naczyniu") + g.domain().label().toLowerCase(java.util.Locale.ROOT) + Messages.get("game_session.domena_rezonans")
                + pc.wheel().resonance(g.domain()) + "/3).", nowMs);
    }

    /** Naczynie: wyjecie klejnotu z naczynia domeny, tylko w swiatyni (akcja "wheel:gem:unsocket:G|R|P|B"). */
    public boolean gemUnsocket(pl.fawizja.pixel.wheel.WheelDomain d, long nowMs) {
        if (d == null) return false;
        pl.fawizja.pixel.wheel.GemAtelier.Result r = gemTempleGate();
        if (r == pl.fawizja.pixel.wheel.GemAtelier.Result.OK) r = pc.wheel().gems().unsocket(d);
        return gemDone(r, Messages.get("game_session.naczynie_oproznione") + d.label().toLowerCase(java.util.Locale.ROOT) + " domena.", nowMs);
    }

    private pl.fawizja.pixel.wheel.GemAtelier.Result gemTempleGate() {
        if (pc.wheelPoints() <= 0) return pl.fawizja.pixel.wheel.GemAtelier.Result.NOT_ELIGIBLE;
        if (!inTemple()) return pl.fawizja.pixel.wheel.GemAtelier.Result.NOT_IN_TEMPLE;
        return pl.fawizja.pixel.wheel.GemAtelier.Result.OK;
    }

    /** Warsztat okruchow: stopien modu podstawowego +1 za zloto i mniejsze okruchy (akcja "wheel:grade:b:MOD"). */
    public boolean gemGradeBasic(pl.fawizja.pixel.wheel.BasicGemMod m, long nowMs) {
        if (m == null) return false;
        long[] cost = pc.wheel().gems().basicGradeCost(m);
        return gradeUp(cost, false, () -> pc.wheel().gems().gradeUp(m), m.shortLabel(), pc.wheel().gems().grade(m) + 1, nowMs);
    }

    /** Warsztat okruchow: stopien modu najwyzszego +1 za zloto i wieksze okruchy (akcja "wheel:grade:s:MOD"). */
    public boolean gemGradeSupreme(pl.fawizja.pixel.wheel.SupremeGemMod m, long nowMs) {
        if (m == null) return false;
        if (!m.allowed(pc.vocation())) return gemDone(pl.fawizja.pixel.wheel.GemAtelier.Result.WRONG_VOCATION, null, nowMs);
        long[] cost = pc.wheel().gems().supremeGradeCost(m);
        return gradeUp(cost, true, () -> pc.wheel().gems().gradeUp(m, pc.vocation()), m.shortLabel(this::spellName),
                pc.wheel().gems().grade(m) + 1, nowMs);
    }

    private String spellName(String key) {
        pl.fawizja.pixel.magic.SpellType t = spells == null ? null : spells.registry().find(key);
        return t == null ? key : t.name();
    }

    private boolean gradeUp(long[] cost, boolean greater, java.util.function.Supplier<pl.fawizja.pixel.wheel.GemAtelier.Result> apply,
                            String label, int newGrade, long nowMs) {
        pl.fawizja.pixel.wheel.GemAtelier.Result r = pc.wheelPoints() <= 0 ? pl.fawizja.pixel.wheel.GemAtelier.Result.NOT_ELIGIBLE
                : cost == null ? pl.fawizja.pixel.wheel.GemAtelier.Result.MAX_GRADE : pl.fawizja.pixel.wheel.GemAtelier.Result.OK;
        int fragId = itemId(pl.fawizja.pixel.wheel.GemAtelier.fragmentKey(greater));
        if (r == pl.fawizja.pixel.wheel.GemAtelier.Result.OK && (fragId < 0 || pc.backpack().count(fragId) < cost[1])) {
            r = pl.fawizja.pixel.wheel.GemAtelier.Result.NO_FRAGMENTS;
        }
        if (r == pl.fawizja.pixel.wheel.GemAtelier.Result.OK && !canPay(cost[0])) r = pl.fawizja.pixel.wheel.GemAtelier.Result.NO_GOLD;
        if (r == pl.fawizja.pixel.wheel.GemAtelier.Result.OK && !payGold(cost[0])) r = pl.fawizja.pixel.wheel.GemAtelier.Result.NO_GOLD;
        if (r != pl.fawizja.pixel.wheel.GemAtelier.Result.OK) return gemDone(r, null, nowMs);
        pc.backpack().remove(fragId, (int) cost[1]);
        r = apply.get();
        return gemDone(r, String.format(Messages.get("game_session.warsztat_okruchow_s_stopien"), label,
                pl.fawizja.pixel.wheel.GemAtelier.roman(newGrade), cost[0], cost[1]), nowMs);
    }

    /** Potwory na 8 polach wokol postaci (Instynkt bitewny, Taktyka pozycyjna). */
    private int adjacentMonsters() {
        Position p = pc.position();
        int n = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                pl.fawizja.pixel.monster.Monster m = monsters.monsterAt(p.translate(dx, dy));
                if (m != null && !m.isDead()) n++;
            }
        }
        return n;
    }

    /** Co klatke: tlum wokol postaci i zdarzenie Daru Zycia (log, skrocenie odnowien o minute jak w Canary). */
    private void updateWheel(long nowMs) {
        if (pc.wheelPoints() <= 0 && pc.wheel().isEmpty()) return;
        pc.wheel().setAdjacentMonsters(monsters == null ? 0 : adjacentMonsters());
        if (pc.wheel().consumeGift()) {
            spells.cooldowns().cutAll(60_000, nowMs);
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.dar_zycia_cie_uratowal")
                    + pl.fawizja.pixel.house.HouseRent.describeIn(pc.wheel().giftCooldownMs()) + ".");
        }
    }

    /** Oplata jak removeMoney w Tibii: monety z plecaka, a gdy ich za malo - konto w banku. */
    private boolean payGold(long price) {
        if (price <= 0) return true;
        pl.fawizja.pixel.inventory.Container tx = pc.backpack().copy();
        if (Money.totalValue(tx) >= price && Money.pay(tx, price).ok()) {
            pc.backpack().commitFrom(tx);
            return true;
        }
        return pc.bank().balance() >= price && pc.bank().debit(price).ok();
    }

    /** Wladca miasta (rola KING) w danym miescie albo null (wyspy, wioski). */
    public Npc rulerOf(String town) {
        if (npcs == null || town == null) return null;
        for (Npc n : npcs.all()) if (n.role() == NpcRole.KING && town.equalsIgnoreCase(n.town())) return n;
        return null;
    }

    /** Podpowiedz, gdzie kupic premium: wladca tego miasta albo kazdego miasta. */
    public String premiumHint(String town) {
        Npc r = rulerOf(town);
        return r != null ? Messages.get("game_session.premium_sprzedaje") + r.name() + Messages.get("game_session.wladca") + r.town() + ")."
                : Messages.get("game_session.premium_sprzedaje_wladca_kazde");
    }

    /** Wladca miasta (krol, krolowa, lord): konto premium za zloto z banku - rejsy duzymi statkami miedzy portami. */
    public boolean buyPremium(long nowMs) {
        Npc n = talkTo(NpcRole.KING, nowMs);
        if (n == null) return false;
        if (pc.isPremium()) {
            log.add(nowMs, EventLog.Kind.INFO, n.name() + Messages.get("game_session.masz_juz_konto_premium"));
            return false;
        }
        if (!pc.bank().debit(PREMIUM_PRICE).ok()) {
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + String.format(Messages.get("game_session.konto_premium_kosztuje_d"), PREMIUM_PRICE));
            return false;
        }
        pc.setPremium(true);
        log.add(nowMs, EventLog.Kind.SYSTEM, n.name() + String.format(Messages.get("game_session.od_dzis_masz_konto"), PREMIUM_PRICE));
        return true;
    }

    /** Wladca miasta: promocja profesji (premium, poziom 20+) - znak przy postaci i szybsza regeneracja (u kazdego wladcy tak samo). */
    public boolean buyPromotion(long nowMs) {
        Npc n = talkTo(NpcRole.KING, nowMs);
        if (n == null) return false;
        if (pc.isPromoted()) {
            log.add(nowMs, EventLog.Kind.INFO, n.name() + Messages.get("game_session.juz_jestes") + pc.vocationTitle() + ".");
            return false;
        }
        if (!pc.isPremium()) {
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + Messages.get("game_session.promocje_daje_tylko_posiadaczo"));
            return false;
        }
        if (pc.level() < PROMOTION_LEVEL) {
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + Messages.get("game_session.wroc_od_poziomu") + PROMOTION_LEVEL + ".");
            return false;
        }
        if (!pc.bank().debit(PROMOTION_PRICE).ok()) {
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + String.format(Messages.get("game_session.promocja_kosztuje_d_gp"), PROMOTION_PRICE));
            return false;
        }
        pc.setPromoted(true);
        log.add(nowMs, EventLog.Kind.SYSTEM, n.name() + Messages.get("game_session.mianuje_cie") + pc.vocationTitle()
                + String.format(Messages.get("game_session.d_gp_z_banku"), PROMOTION_PRICE));
        return true;
    }

    /** Kaplan: blogoslawienstwo za monety z plecaka - najblizsza smierc zabierze polowe straty doswiadczenia. */
    public boolean priestBless(long nowMs) {
        Npc n = talkTo(NpcRole.PRIEST, nowMs);
        if (n == null) return false;
        if (pc.isBlessed()) {
            log.add(nowMs, EventLog.Kind.INFO, n.name() + Messages.get("game_session.juz_jestes_poblogoslawiony"));
            return false;
        }
        long price = pl.fawizja.pixel.balance.Balance.blessingPrice(pc.level());
        pl.fawizja.pixel.inventory.Container tx = pc.backpack().copy();
        if (Money.totalValue(tx) < price || !Money.pay(tx, price).ok()) {
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + Messages.get("game_session.blogoslawienstwo_kosztuje") + price + Messages.get("game_session.gp_monety_w_plecaku"));
            return false;
        }
        pc.backpack().commitFrom(tx);
        pc.setBlessed(true);
        log.add(nowMs, EventLog.Kind.SYSTEM, Messages.get("game_session.otrzymujesz_blogoslawienstwo") + price
                + Messages.get("game_session.gp_najblizsza_smierc_zabierze"));
        return true;
    }

    /**
     * Sklep z gornej ramki ({@link Store}): ta sama usluga co u krola, kaplana, kowala albo kupca i w tej samej cenie,
     * ale bez chodzenia do NPC - zawsze zlotem z konta w banku. Zwraca, czy zakup sie udal.
     */
    public boolean storeBuy(String key, long nowMs) {
        Store.Offer o = Store.find(this, key);
        if (o == null) return false;
        if (!o.available()) {
            log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_screen.sklep") + o.name() + " - " + o.blocked() + ".");
            return false;
        }
        if (pc.bank().balance() < o.price()) {
            log.add(nowMs, EventLog.Kind.WARNING, String.format(Messages.get("game_session.sklep_s_kosztuje_d"),
                    o.name(), o.price(), pc.bank().balance()));
            return false;
        }
        pl.fawizja.pixel.inventory.Container tx = null;
        if (items.containsKey(key)) {
            tx = pc.backpack().copy();
            if (!tx.add(new pl.fawizja.pixel.item.ItemStack(items.byKey(key).id(), 1)).ok()) {
                log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.sklep_plecak_jest_pelny"));
                return false;
            }
        }
        if (!pc.bank().debit(o.price()).ok()) return false;
        String done;
        switch (key) {
            case "premium":
                pc.setPremium(true);
                done = Messages.get("game_session.masz_konto_premium_kapitanowie");
                break;
            case "promote":
                pc.setPromoted(true);
                done = Messages.get("game_session.jestes_teraz") + pc.vocationTitle() + Messages.get("game_session.regenerujesz_sie_o_polowe");
                break;
            case "bless":
                pc.setBlessed(true);
                done = Messages.get("game_session.otrzymujesz_blogoslawienstwo_n");
                break;
            default:
                if (key.startsWith("enchant_")) {
                    pl.fawizja.pixel.item.Enchantments.Kind k = pl.fawizja.pixel.item.Enchantments.Kind.valueOf(key.substring(8).toUpperCase(java.util.Locale.ROOT));
                    pc.enchantments().extend(k);
                    done = k.label() + " na " + pl.fawizja.pixel.item.Enchantments.describe(pc.enchantments().remainingMs(k)) + " polowania.";
                } else {
                    pc.backpack().commitFrom(tx);
                    done = o.name() + Messages.get("game_session.jest_w_plecaku_dotknij");
                }
        }
        log.add(nowMs, EventLog.Kind.SYSTEM, String.format(Messages.get("game_session.sklep_kupiono_s_za"), o.name(), o.price(), pc.bank().balance()) + done);
        return true;
    }

    /** Kaplan: zamieszkanie w miescie - tu postac odrodzi sie po smierci. */
    public void settleHere(long nowMs) {
        Npc n = talkTo(NpcRole.PRIEST, nowMs);
        if (n == null) return;
        pc.setHomeTown(n.town().toLowerCase(java.util.Locale.ROOT));
        log.add(nowMs, EventLog.Kind.SYSTEM, Messages.get("game_session.mieszkasz_teraz_w") + n.town() + Messages.get("game_session.tu_bedziesz_sie_odradzac"));
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
            String line = n.name() + Messages.get("game_session.niech_swiatlo_cie_uleczy");
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
            // bez pogawedek: na powitanie i kazde inne slowo NPC mowi tylko swoje powitanie (i tematy, jesli ma)
            java.util.List<String> topics = d.topics(n);
            answer = topics.isEmpty() ? n.greeting()
                    : n.greeting() + Messages.get("game_session.zapytaj_o") + String.join(", ", topics.subList(0, Math.min(4, topics.size()))) + ".";
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
            text = Messages.get("game_session.ukonczono") + q.name() + Messages.get("game_session.nagroda") + quests.rewardText(q, quests.lastRewardExp()) + ".";
        } else if (r == QuestResult.ACCEPTED) {
            text = Messages.get("game_session.przyjeto_zadanie") + q.name() + "\".";
        } else if (r == QuestResult.LOCKED) {
            text = Messages.get("game_session.najpierw_ukoncz") + quests.registry().get(q.requires()).name() + "\".";
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
                log.add(nowMs, EventLog.Kind.LOOT, Messages.get("game_session.zlowiono") + items.get(fishing.lastCatchId()).name());
                break;
            case NOTHING:
                log.add(nowMs, EventLog.Kind.INFO, r.message());
                break;
            case COOLDOWN:
                break;
            default:
                log.add(nowMs, EventLog.Kind.WARNING, r.message());
        }
        if (r == FishingResult.CAUGHT || r == FishingResult.NOTHING) {
            int left = fishing.bait(pc);
            if (left == 0) log.add(nowMs, EventLog.Kind.SYSTEM, Messages.get("game_session.skonczyly_sie_robaki_na"));
            else if (left == 5) log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.zostalo_5_robakow_w"));
        }
        return r;
    }

    public CastResult castSpell(String key, long nowMs) {
        return spells.cast(key, nowMs);
    }

    /** Pole combo paska: pierwszy z czarow, ktory mozna teraz rzucic (odnowienia i grupy jak przy osobnych klawiszach). */
    public CastResult castCombo(java.util.List<String> keys, long nowMs) {
        return spells.castCombo(keys, nowMs);
    }

    /**
     * Runa (albo inny przedmiot) z paska na wskazane pole: celownik albo "na siebie" (pole postaci). Pole paska
     * z mikstura: tylko na siebie, jak zwykle uzycie.
     */
    public CastResult useItemAt(String itemKey, Position tile, long nowMs) {
        if (!items.containsKey(itemKey)) return CastResult.NOT_USABLE;
        pl.fawizja.pixel.item.ItemType t = items.byKey(itemKey);
        if (t.category() != pl.fawizja.pixel.item.ItemCategory.RUNE) {
            if (!tile.equals(pc.position())) {
                log.add(nowMs, EventLog.Kind.WARNING, CastResult.NOT_USABLE.message());
                return CastResult.NOT_USABLE;
            }
            return usePotion(itemKey, nowMs);
        }
        return spells.useFirstAt(itemKey, tile, nowMs);
    }

    /** "Uzyj z..." z plecaka: runa na pole (celownik); inny przedmiot tylko na siebie, jak UZYJ. */
    public CastResult useSlotAt(int slot, Position tile, long nowMs) {
        if (slot < 0 || slot >= pc.backpack().size()) return CastResult.MISSING_ITEM;
        pl.fawizja.pixel.item.ItemStack st = pc.backpack().get(slot);
        if (st != null && items.get(st.itemId()).category() == pl.fawizja.pixel.item.ItemCategory.RUNE) {
            return spells.useItemAt(slot, tile, nowMs);
        }
        if (!tile.equals(pc.position())) {
            log.add(nowMs, EventLog.Kind.WARNING, CastResult.NOT_USABLE.message());
            return CastResult.NOT_USABLE;
        }
        return useSlot(slot, nowMs);
    }

    /** Czar z dopiskiem: utevo res "wilk", utevo res ina "szkielet", exura sio "imie". */
    public CastResult castSpell(String key, String param, long nowMs) {
        return spells.cast(key, param, nowMs);
    }

    // ------------------------------------------------------------------ 0.240: lewitacja, stany, pola

    /** Pole (id), na ktorym ostatnio stala postac - pole rani przy wejsciu albo gdy powstanie pod postacia. */
    private long lastFieldId;

    /** Kierunek prosty do lewitacji (skos = poziomo, jak patrzenie postaci w Tibii). */
    private Direction straightFacing() {
        return facing.diagonal() ? (facing.dx > 0 ? Direction.EAST : Direction.WEST) : facing;
    }

    /** Pole, na ktore postac moze sie przeniesc czarem: przechodnie, wolne, bez przejscia, poziomu, domu i wypraw. */
    private boolean levitationTarget(Position p) {
        return world.isWalkable(p) && world.transitionAt(p) == null && world.requiredLevelAt(p) <= pc.level()
                && world.levelDoorAt(p) <= pc.level() && mayEnter(p) && houses.at(p) == null
                && monsters.monsterAt(p) == null && npcs.at(p) == null && !dummies.at(p)
                && !world.inAltarQuestRooms(p) && !world.inBossQuestRooms(p) && !world.openings().isClosedDoor(p);
    }

    /** Pustka nad/przed postacia: brak podloza albo dziura (przejscie HOLE). */
    private boolean openAir(Position p) {
        pl.fawizja.pixel.world.Transition t = world.transitionAt(p);
        return !world.hasGround(p) || t != null && t.type() == pl.fawizja.pixel.world.TransitionType.HOLE;
    }

    /**
     * Gdzie exani hur zabierze postac (null = nie tutaj). W gore: nad postacia pustka (brak podloza albo dziura),
     * a pietro wyzej przed postacia pole do stania. W dol: przed postacia pustka (krawedz, dziura), pod nia pole.
     */
    public Position levitationDestination(boolean up) {
        Position me = pc.position();
        Direction d = straightFacing();
        if (up) {
            if (me.z() >= Position.MAX_FLOOR) return null;
            Position above = me.withFloor(me.z() + 1);
            Position dest = above.translate(d.dx, d.dy);
            return openAir(above) && levitationTarget(dest) ? dest : null;
        }
        if (me.z() <= Position.MIN_FLOOR) return null;
        Position front = me.translate(d.dx, d.dy);
        Position dest = front.withFloor(me.z() - 1);
        return openAir(front) && levitationTarget(dest) ? dest : null;
    }

    private final class SpellMover implements pl.fawizja.pixel.magic.SpellSystem.Mover {
        @Override
        public boolean levitate(boolean up, long now) {
            Position dest = levitationDestination(up);
            if (dest == null) return false;
            if (now < pc.pzBlockedUntil() && !world.isProtectionZone(pc.position()) && world.isProtectionZone(dest)) {
                log.add(now, EventLog.Kind.WARNING, pzBlockedMessage(now));
                return false;
            }
            combat.addEvent(pl.fawizja.pixel.combat.CombatEvent.area(now, pc.position(), 0, "MAGIC"));
            pc.setPosition(dest);
            onFloorChanged(now);
            checkRegion(now);
            combat.addEvent(pl.fawizja.pixel.combat.CombatEvent.area(now, dest, 0, "MAGIC"));
            return true;
        }

        @Override
        public boolean magicRope(long now) {
            Position me = pc.position();
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    pl.fawizja.pixel.world.Transition t = world.transitionAt(me.translate(dx, dy));
                    if (t == null || t.type() != pl.fawizja.pixel.world.TransitionType.ROPE_SPOT) continue;
                    pc.setPosition(t.to());
                    onFloorChanged(now);
                    checkRegion(now);
                    combat.addEvent(pl.fawizja.pixel.combat.CombatEvent.area(now, t.to(), 0, "MAGIC"));
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Stany i pola co klatke: tiki zatrucia/podpalenia... w postac, pole pod postacia (wejscie albo nowe pole),
     * efekty czarow w czasie (stany na potworach, sludzy).
     */
    private void updateConditionsAndFields(long nowMs) {
        if (!pc.isDead()) {
            pl.fawizja.pixel.world.Fields.Field f = world.fields().at(pc.position());
            if (f == null) {
                lastFieldId = 0;
            } else if (f.id() != lastFieldId) {
                lastFieldId = f.id();
                pl.fawizja.pixel.combat.ConditionType type = pl.fawizja.pixel.world.Fields.condition(f.kind());
                if (type != null && !world.isProtectionZone(pc.position())) {
                    boolean had = pc.conditions().has(type);
                    pc.conditions().add(type, pl.fawizja.pixel.world.Fields.ticks(f.kind()), pl.fawizja.pixel.world.Fields.intervalMs(f.kind()),
                            false, nowMs, f.kind().label());
                    if (!had) log.add(nowMs, EventLog.Kind.WARNING, f.kind().label() + ": " + type.label().toLowerCase(java.util.Locale.ROOT)
                            + (type.cure() != null ? Messages.get("combat_system.wylecz_czarem") + type.cure() + "." : "."));
                }
            }
        }
        if (pc.conditions().any()) {
            for (pl.fawizja.pixel.combat.Conditions.Tick t : pc.conditions().tick(nowMs)) {
                combat.conditionHit(t, nowMs);
                if (pc.conditions().types().isEmpty()) break;
            }
        }
        spells.updateEffects(nowMs);
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
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.masz_juz") + PlayerCharacter.MAX_EXTRA_BAGS + Messages.get("game_session.dodatkowe_plecaki"));
            return CastResult.NOT_USABLE;
        }
        pc.backpack().takeFromSlot(slot, 1);
        pc.attachBag();
        log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.plecak_dolaczony") + pc.backpack().size() + Messages.get("game_session.miejsc_strony") + pc.bagPages() + ").");
        return CastResult.OK;
    }

    /** Odpina ostatni dodatkowy plecak (musi byc pusty) i kladzie go do plecaka glownego. */
    public boolean detachBag(long nowMs) {
        if (pc.extraBags() == 0) return false;
        if (!pc.detachBag()) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.najpierw_oproznij_ostatni_plec"));
            return false;
        }
        if (!pc.backpack().add(ItemStack.of(items.byKey("backpack").id())).ok()) {
            pc.attachBag();
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.brak_miejsca_na_odpiety"));
            return false;
        }
        log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.plecak_odpiety") + pc.backpack().size() + " miejsc.");
        return true;
    }

    /** Uzycie monet: monety lacza sie i wymieniaja same, wiec tylko opis wartosci. */
    private CastResult exchangeCoins(int slot, long nowMs) {
        pc.backpack().normalizeCoins();
        log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.monety_wymieniaja_sie_same")
                + String.format("%,d", Money.totalValue(pc.backpack())) + " gp.");
        return CastResult.OK;
    }

    /** Zwoj powrotu: 3 s skupienia (ruch albo obrazenia przerywaja), potem swiatynia miasta postaci; zwoj znika na koncu. */
    public static final long RECALL_MS = 3_000;
    private long recallAtMs = -1;
    private Position recallFrom;
    private int recallHealth;

    public boolean recalling() { return recallAtMs >= 0; }

    private CastResult startRecall(long nowMs) {
        if (nowMs < pc.pzBlockedUntil()) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.po_walce_z_graczem_2")
                    + ((pc.pzBlockedUntil() - nowMs + 999) / 1000) + " s.");
            return CastResult.OK;
        }
        if (world.templeOf(pc.homeTown()) == null) return CastResult.OK;
        recallAtMs = nowMs + RECALL_MS;
        recallFrom = pc.position();
        recallHealth = pc.health();
        log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.czytasz_zwoj_powrotu_nie"));
        return CastResult.OK;
    }

    private void updateRecall(long nowMs) {
        if (recallAtMs < 0) return;
        if (!pc.position().equals(recallFrom) || pc.health() < recallHealth || pc.isDead() || nowMs < pc.pzBlockedUntil()) {
            recallAtMs = -1;
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.skupienie_przerwane_zwoj_nie"));
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
        log.add(nowMs, EventLog.Kind.SYSTEM, Messages.get("game_session.zwoj_rozsypuje_sie_w") + pc.homeTown() + ".");
    }

    /** Wyzwanie ze zwoju (Challenge); null, gdy nie trwa. */
    private Challenge challenge;

    public Challenge challenge() { return challenge; }

    private CastResult startChallenge(int slot, long nowMs) {
        if (challenge != null) {
            log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.wyzwanie_juz_trwa_fala") + (challenge.wave + 1) + Messages.get("common.z") + Challenge.WAVES.length + ".");
            return CastResult.OK;
        }
        if (world.isProtectionZone(pc.position())) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.zwoju_wyzwania_nie_da"));
            return CastResult.OK;
        }
        pl.fawizja.pixel.monster.MonsterType t = pl.fawizja.pixel.quest.HuntTask.pick(pc.level(), monsters.spawnedTypes(), rng);
        if (t == null) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.zwoj_milczy_w_tej"));
            return CastResult.OK;
        }
        if (pc.backpack().takeFromSlot(slot, 1) == null) return CastResult.MISSING_ITEM;
        challenge = new Challenge(t, pc.position(), nowMs + Challenge.TIME_MS);
        log.add(nowMs, EventLog.Kind.SYSTEM, Messages.get("game_session.wyzwanie") + Challenge.WAVES.length + Messages.get("game_session.fale") + t.name()
                + Messages.get("game_session.pokonaj_je_w") + (Challenge.TIME_MS / 60_000) + Messages.get("game_session.min_i_nie_oddalaj"));
        spawnWave(nowMs);
        return CastResult.OK;
    }

    private void spawnWave(long nowMs) {
        Challenge c = challenge;
        c.ids.clear();
        for (pl.fawizja.pixel.monster.Monster m : monsters.spawnEvent(c.type.key(), c.center, Challenge.RADIUS, Challenge.WAVES[c.wave], nowMs)) {
            c.ids.add(m.id());
        }
        log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.fala") + (c.wave + 1) + Messages.get("common.z") + Challenge.WAVES.length + ": " + c.ids.size() + "x " + c.type.name() + "!");
    }

    private void updateChallenge(long nowMs) {
        Challenge c = challenge;
        if (c == null) return;
        boolean left = pc.position().z() != c.center.z()
                || Math.max(Math.abs(pc.position().x() - c.center.x()), Math.abs(pc.position().y() - c.center.y())) > Challenge.LEAVE;
        if (pc.isDead() || left || nowMs >= c.untilMs) {
            monsters.clearEvent(c.ids);
            challenge = null;
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.wyzwanie_przepadlo") + (left ? Messages.get("game_session.za_daleko_od_miejsca") : nowMs >= c.untilMs ? Messages.get("game_session.koniec_czasu") : "."));
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
        log.add(nowMs, EventLog.Kind.SYSTEM, Messages.get("game_session.wyzwanie_ukonczone") + String.format("%,d gp", gold) + Messages.get("game_session.na_konto"));
        combat.awardExperience(pl.fawizja.pixel.balance.Balance.applyRate(Challenge.rewardExp(c.type), pl.fawizja.pixel.balance.Balance.expRate(pc.level())), Messages.get("game_session.wyzwanie_2"), nowMs);
    }

    /** Siodlo: oswaja konia na stale (przedmiot znika). */
    /** Zwoj nauki: godzina +30% doswiadczenia za potwory (do 3 godzin zapasu). */
    private CastResult readExpScroll(int slot, long nowMs) {
        pl.fawizja.pixel.item.Enchantments e = pc.enchantments();
        pl.fawizja.pixel.item.Enchantments.Kind k = pl.fawizja.pixel.item.Enchantments.Kind.EXP;
        if (!e.canExtend(k)) {
            log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.wiedzy_ze_zwojow_starczy") + pl.fawizja.pixel.item.Enchantments.describe(e.remainingMs(k)) + ".");
            return CastResult.OK;
        }
        if (pc.backpack().takeFromSlot(slot, 1) == null) return CastResult.MISSING_ITEM;
        e.extend(k);
        log.add(nowMs, EventLog.Kind.SYSTEM, Messages.get("game_session.zwoj_nauki") + pl.fawizja.pixel.item.Enchantments.EXP_BONUS_PERCENT
                + Messages.get("game_session.doswiadczenia_za_potwory_przez") + pl.fawizja.pixel.item.Enchantments.describe(e.remainingMs(k)) + " polowania.");
        return CastResult.OK;
    }

    /** Mikstura wzmacniajaca: 10 minut polowania (czas plynie poza strefa ochronna), do 30 minut zapasu. */
    private CastResult drinkBuff(int slot, long nowMs) {
        pl.fawizja.pixel.item.Enchantments e = pc.enchantments();
        pl.fawizja.pixel.item.ItemType potion = items.get(pc.backpack().get(slot).itemId());
        pl.fawizja.pixel.item.Enchantments.Kind k = pl.fawizja.pixel.item.Enchantments.Kind.ofPotion(potion.key());
        if (pc.level() < potion.minLevel()) {
            log.add(nowMs, EventLog.Kind.WARNING, potion.name() + Messages.get("game_session.od_poziomu") + potion.minLevel() + ".");
            return CastResult.LEVEL_TOO_LOW;
        }
        if (!e.canExtend(k)) {
            log.add(nowMs, EventLog.Kind.INFO, k.label() + Messages.get("game_session.dziala_jeszcze") + pl.fawizja.pixel.item.Enchantments.describe(e.remainingMs(k)) + ".");
            return CastResult.OK;
        }
        if (pc.backpack().takeFromSlot(slot, 1) == null) return CastResult.MISSING_ITEM;
        e.extend(k);
        log.add(nowMs, EventLog.Kind.SYSTEM, k.label() + ": " + k.effect() + Messages.get("game_session.przez")
                + pl.fawizja.pixel.item.Enchantments.describe(e.remainingMs(k)) + " polowania.");
        return CastResult.OK;
    }

    private CastResult useSaddle(int slot, long nowMs) {
        if (pc.hasMount()) {
            log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.masz_juz_wierzchowca_wsiadasz"));
            return CastResult.OK;
        }
        if (pc.backpack().takeFromSlot(slot, 1) == null) return CastResult.MISSING_ITEM;
        pc.setHasMount(true);
        log.add(nowMs, EventLog.Kind.SYSTEM, Messages.get("game_session.oswojono_konia_wsiadaj_przycis")
                + PlayerCharacter.MOUNT_SPEED + Messages.get("game_session.szybkosci"));
        return CastResult.OK;
    }

    private final Random rng;

    /**
     * Ulepszenie zalozonego przedmiotu u kupca: platnosc z plecaka, szansa wg {@link pl.fawizja.pixel.item.Upgrade};
     * nieudana proba zabiera zloto, przedmiot zostaje bez zmian.
     */
    public boolean upgradeEquipped(pl.fawizja.pixel.item.EquipSlot slot, long nowMs) {
        Npc n = smithNearby(nowMs);
        if (n == null) return false;
        ItemStack worn = pc.equipment().get(slot);
        if (worn == null) {
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + Messages.get("game_session.nic_tam_nie_nosisz"));
            return false;
        }
        pl.fawizja.pixel.item.ItemType t = items.get(worn.itemId());
        if (!pl.fawizja.pixel.item.Upgrade.upgradable(t)) {
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + Messages.get("game_session.tego_nie_da_sie"));
            return false;
        }
        int next = worn.upgrade() + 1;
        if (next > pl.fawizja.pixel.item.Upgrade.MAX) {
            log.add(nowMs, EventLog.Kind.INFO, n.name() + ": " + pl.fawizja.pixel.item.Upgrade.name(t, worn.upgrade())
                    + Messages.get("game_session.jest_juz_ulepszony_do"));
            return false;
        }
        long price = pl.fawizja.pixel.item.Upgrade.price(t, next);
        pl.fawizja.pixel.inventory.Container tx = pc.backpack().copy();
        if (Money.totalValue(tx) < price || !Money.pay(tx, price).ok()) {
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + Messages.get("game_session.ulepszenie_do") + next + " kosztuje " + price + Messages.get("game_session.gp_monety_w_plecaku"));
            return false;
        }
        pc.backpack().commitFrom(tx);
        if (rng.nextInt(100) >= pl.fawizja.pixel.item.Upgrade.chancePercent(next)) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.nie_udalo_sie") + price + " gp). " + pl.fawizja.pixel.item.Upgrade.name(t, worn.upgrade())
                    + Messages.get("game_session.zostaje_bez_zmian"));
            return false;
        }
        pc.equipment().setDirect(slot, worn.withUpgrade(next));
        log.add(nowMs, EventLog.Kind.SYSTEM, Messages.get("game_session.udalo_sie") + pl.fawizja.pixel.item.Upgrade.name(t, next) + " (" + price + " gp).");
        return true;
    }

    /** Zaklecie u kowala (kupca): godzina polowania, placi z plecaka, najwyzej 3 godziny zapasu. */
    public boolean buyEnchantment(pl.fawizja.pixel.item.Enchantments.Kind kind, long nowMs) {
        Npc n = smithNearby(nowMs);
        if (n == null) return false;
        pl.fawizja.pixel.item.Enchantments e = pc.enchantments();
        if (!e.canExtend(kind)) {
            log.add(nowMs, EventLog.Kind.INFO, n.name() + ": " + kind.label().toLowerCase(java.util.Locale.ROOT)
                    + Messages.get("game_session.starczy_jeszcze_na") + pl.fawizja.pixel.item.Enchantments.describe(e.remainingMs(kind)) + Messages.get("game_session.wroc_pozniej"));
            return false;
        }
        long price = pl.fawizja.pixel.item.Enchantments.price(kind, pc.level());
        pl.fawizja.pixel.inventory.Container tx = pc.backpack().copy();
        if (Money.totalValue(tx) < price || !Money.pay(tx, price).ok()) {
            log.add(nowMs, EventLog.Kind.WARNING, n.name() + Messages.get("game_session.zaklecie_kosztuje") + price + Messages.get("game_session.gp_monety_w_plecaku"));
            return false;
        }
        pc.backpack().commitFrom(tx);
        e.extend(kind);
        log.add(nowMs, EventLog.Kind.SYSTEM, kind.label() + " na " + pl.fawizja.pixel.item.Enchantments.describe(e.remainingMs(kind))
                + Messages.get("game_session.polowania") + price + " gp).");
        return true;
    }

    /** Kowal obok postaci (ulepszenia, zaklecia, Kuznia, nasycenia); inaczej ostrzezenie i null. */
    public Npc smithNearby(long nowMs) {
        Npc n = npcNearby();
        if (n == null || !n.isSmith()) {
            log.add(nowMs, EventLog.Kind.WARNING, n != null && n.role() == NpcRole.MERCHANT
                    ? n.name() + Messages.get("game_session.tym_zajmuje_sie_kowal") : TradeResult.TOO_FAR.message());
            return null;
        }
        return n;
    }

    private ForgeService forge;

    /** Kuznia i nasycenia ({@link ForgeService}). */
    public ForgeService forge() {
        if (forge == null) forge = new ForgeService(this, rng);
        return forge;
    }

    public boolean forgeFuse(int slotA, int slotB, boolean coreChance, boolean coreProtect, long nowMs) {
        return smithNearby(nowMs) != null && forge().fuse(slotA, slotB, coreChance, coreProtect, nowMs);
    }

    public boolean forgeTransfer(int donorSlot, int receiverSlot, long nowMs) {
        return smithNearby(nowMs) != null && forge().transfer(donorSlot, receiverSlot, nowMs);
    }

    public boolean forgeConvert(String kind, long nowMs) {
        return smithNearby(nowMs) != null && forge().convert(kind, nowMs);
    }

    public boolean forgeRaiseLimit(long nowMs) {
        return smithNearby(nowMs) != null && forge().raiseLimit(nowMs);
    }

    /** Kowal albo zaklinacz obok postaci (nasycenia); inaczej ostrzezenie i null. */
    public Npc imbuerNearby(long nowMs) {
        Npc n = npcNearby();
        if (n == null || !n.imbues()) {
            log.add(nowMs, EventLog.Kind.WARNING, n != null ? n.name() + Messages.get("game_session.nasycenia_robi_zaklinacz_albo")
                    : TradeResult.TOO_FAR.message());
            return null;
        }
        return n;
    }

    public boolean imbue(pl.fawizja.pixel.item.EquipSlot slot, pl.fawizja.pixel.item.Imbuement kind, int level, long nowMs) {
        return imbuerNearby(nowMs) != null && forge().imbue(slot, kind, level, nowMs);
    }

    public boolean clearImbuement(pl.fawizja.pixel.item.EquipSlot slot, int index, long nowMs) {
        return imbuerNearby(nowMs) != null && forge().clearImbuement(slot, index, nowMs);
    }

    /** Klient online: polozenia diabolicznych potworow z serwera (komunikat FIEND); null = gra lokalna. */
    private List<Position> remoteFiendish;

    public void setRemoteFiendish(List<Position> where) { remoteFiendish = where == null ? null : new java.util.ArrayList<>(where); }

    /** Diaboliczne potwory na swiecie (minimapa): online z serwera, offline z wlasnych potworow. */
    public List<Position> fiendishPositions() {
        if (remoteFiendish != null) return java.util.Collections.unmodifiableList(remoteFiendish);
        List<Position> out = new java.util.ArrayList<>();
        for (pl.fawizja.pixel.monster.Monster m : monsters.fiendishMonsters()) out.add(m.position());
        return out;
    }

    /** Tytul z osiagniecia: key null albo "-" zdejmuje tytul; tylko zdobyte osiagniecia. */
    public boolean chooseTitle(String key, long nowMs) {
        if (key == null || key.equals("-")) {
            if (pc.title() != null) log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.zdejmujesz_tytul"));
            pc.setTitle(null);
            return true;
        }
        pl.fawizja.pixel.achievement.Achievement a = null;
        for (pl.fawizja.pixel.achievement.Achievement x : pl.fawizja.pixel.achievement.Achievement.values()) if (x.name().equals(key)) a = x;
        if (a == null || !pc.achievements().has(a)) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.ten_tytul_trzeba_najpierw"));
            return false;
        }
        pc.setTitle(a.name());
        log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.twoj_tytul") + a.title() + ".");
        return true;
    }

    /** Kolor stroju (Outfit); false dla nieznanego koloru. */
    public boolean chooseOutfit(String color, long nowMs) {
        int i = pl.fawizja.pixel.character.Outfit.parse(color);
        if (i < 0) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.nie_ma_takiego_koloru") + String.join(", ", pl.fawizja.pixel.character.Outfit.NAMES) + ".");
            return false;
        }
        pc.setOutfit(i);
        log.add(nowMs, EventLog.Kind.INFO, i == 0 ? Messages.get("game_session.stroj_w_kolorze_profesji") : Messages.get("game_session.stroj") + pl.fawizja.pixel.character.Outfit.name(i) + ".");
        return true;
    }

    /** Pupil: przywolanie albo odeslanie (przedmiot zostaje w plecaku; bez niego pupil odchodzi). */
    private CastResult togglePet(pl.fawizja.pixel.item.ItemType pet, long nowMs) {
        if (pet.key().equals(pc.pet())) {
            pc.setPet(null);
            log.add(nowMs, EventLog.Kind.INFO, pet.name() + Messages.get("game_session.odpoczywa_w_plecaku"));
        } else {
            pc.setPet(pet.key());
            log.add(nowMs, EventLog.Kind.INFO, pet.name() + Messages.get("game_session.chodzi_teraz_za_toba") + PlayerCharacter.PET_EXP_PERCENT
                    + Messages.get("game_session.doswiadczenia_z_potworow"));
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
        if (t != null) log.add(nowMs, EventLog.Kind.INFO, t.name() + Messages.get("game_session.odchodzi_bo_nie_masz"));
    }

    /** Wsiadanie i zsiadanie z wierzchowca (pod ziemia nie mozna jechac). */
    public void toggleMount(long nowMs) {
        if (!pc.hasMount()) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.nie_masz_wierzchowca_kup"));
            return;
        }
        if (pc.isMounted()) {
            pc.setMounted(false);
            log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.zsiadasz_z_konia"));
            return;
        }
        if (pc.position().z() < 0) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.pod_ziemia_kon_sie"));
            return;
        }
        pc.setMounted(true);
        log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.wsiadasz_na_konia") + PlayerCharacter.MOUNT_SPEED + Messages.get("game_session.szybkosci_2"));
    }

    /** Mikstury szybkich przyciskow od najsilniejszej; przycisk HP/MP uzywa najsilniejszej dozwolonej, ktora masz. */
    /** Od najsilniejszej; mikstury ducha paladyna (zdrowie i mana naraz) sa na obu listach. */
    static final String[] HP_TIERS = {"ultimate_health_potion", "supreme_health_potion", "ultimate_spirit_potion",
            "strong_health_potion", "great_spirit_potion", "great_health_potion", "health_potion"};
    static final String[] MP_TIERS = {"ultimate_mana_potion", "supreme_mana_potion", "ultimate_spirit_potion",
            "great_spirit_potion", Messages.get("character_factory.strong_mana_potion"), Messages.get("game_session.great_mana_potion"), "mana_potion"};

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
            log.add(nowMs, EventLog.Kind.LOOT, Messages.get("game_session.wytworzono") + r.resultCount() + "x " + items.get(r.resultItemId()).name());
        } else {
            log.add(nowMs, EventLog.Kind.WARNING, res.message());
        }
        return res;
    }

    private pl.fawizja.pixel.social.SocialClient social;

    /** Druzyna, znajomi i informacje o graczach (API dla okna gracza i polecen czatu). */
    public pl.fawizja.pixel.social.SocialClient social() {
        if (social == null) social = new pl.fawizja.pixel.social.SocialClient(pc, log, () -> Math.max(0L, lastUpdateMs));
        return social;
    }

    /** Klient online: akcje druzyny i znajomych ida do serwera (null = gra offline). */
    public void setSocialLink(pl.fawizja.pixel.social.SocialClient.Link link) { social().setLink(link); }

    /** Zaproszenie do druzyny (okno gracza: "Zaproś do drużyny"). Online wysyla prosbe do serwera. */
    public boolean invitePlayerToParty(String name) { return social().invitePlayerToParty(name); }

    /** Przyjecie zaproszenia od lidera. */
    public boolean acceptPartyInvite(String leader) { return social().acceptPartyInvite(leader); }

    /** Przyjecie najnowszego zaproszenia. */
    public boolean acceptPartyInvite() { return social().acceptPartyInvite(); }

    public boolean leaveParty() { return social().leaveParty(); }

    /** Lider usuwa czlonka z druzyny. */
    public boolean kickFromParty(String name) { return social().kickFromParty(name); }

    /** Moja druzyna: czlonkowie (lider pierwszy) z poziomem i profesja, premia doswiadczenia. */
    public pl.fawizja.pixel.party.PartyView party() { return social().party(); }

    /** Poziom, profesja i gildia gracza, jesli wiadomo (online: serwer odpowiada asynchronicznie). */
    public pl.fawizja.pixel.social.PlayerInfo playerInfo(String name) { return social().playerInfo(name); }

    public boolean addFriend(String name) { return social().addFriend(name); }

    public boolean removeFriend(String name) { return social().removeFriend(name); }

    /** Znajomi ze stanem w grze (offline: bez stanu). */
    public pl.fawizja.pixel.friends.FriendsView friends() { return social().friends(); }

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
        syncPvp(nowMs);
        pc.explored().revealView(pc.position()); // mgla mapy: odkryte to, co postac widzi (tani test bez ruchu)
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
        updateWheel(nowMs);
        updateTraining(nowMs);
        updateChallenge(nowMs);
        if (pc.isMounted() && pc.position().z() < 0) {
            pc.setMounted(false);
            log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.zostawiasz_konia_przy_zejsciu"));
        }
        if (lastUpdateMs >= 0) {
            long dt = Math.min(1000, nowMs - lastUpdateMs);
            pc.tickRegeneration(dt, isResting(nowMs));
            spells.updateSerenity(dt, nowMs); // mnich: spokoj (Serene) wg druzyny i tlumu wokol
            pc.stats().addPlayTime(dt);
            forge().tick(nowMs, dt, world.isProtectionZone(pc.position()));
            if (!world.isProtectionZone(pc.position())) {
                pl.fawizja.pixel.item.Enchantments.Kind gone = pc.enchantments().tick(dt);
                if (gone != null) log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.zaklecie_wygaslo") + gone.label().toLowerCase(java.util.Locale.ROOT) + ".");
            }
            if (pc.tickTorch(dt, torchId) == pl.fawizja.pixel.character.PlayerCharacter.TorchTick.BURNED_OUT) {
                log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.pochodnia_sie_wypalila"));
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
            } else if (r == MoveResult.DOOR_CLOSED && nowMs - lastDoorHintMs > DOOR_HINT_MS) {
                lastDoorHintMs = nowMs;
                log.add(nowMs, EventLog.Kind.INFO, Messages.tr(DOOR_HINT));
            } else if (r == MoveResult.LEVEL_TOO_LOW && nowMs - lastGateWarningMs > 3000) {
                lastGateWarningMs = nowMs;
                Position gate = pc.position().translate(requested.dx, requested.dy);
                if (world.levelDoorAt(gate) > 0) {
                    log.add(nowMs, EventLog.Kind.WARNING, levelDoorRefusal(world.levelDoorAt(gate)));
                } else {
                    log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.straznik_dalej_wpuszczamy_tylk")
                            + world.requiredLevelAt(gate) + ".");
                }
            }
        }
        closeLevelDoorBehind();
        ground.decay(nowMs);
        if (!sharedWorld && altarsLocal) updateAltarTorches();
        boolean night = isNight(nowMs);
        if (wasNight != null && night != wasNight) {
            log.add(nowMs, EventLog.Kind.INFO, night ? Messages.get("game_session.zapada_noc_nocne_drapiezniki")
                    : Messages.get("game_session.wstaje_dzien"));
        }
        wasNight = night;
        monsters.setNight(night);
        Position before = pc.position();
        if (!sharedWorld) monsters.update(nowMs, playerTarget, combat);
        combat.update(nowMs);
        updateConditionsAndFields(nowMs);
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
        log.add(nowMs, EventLog.Kind.INFO, r.name() + Messages.get("game_session.zalecany_poziom") + r.levelMin() + "-" + r.levelMax() + ")");
        if (r.levelMin() > pc.level() + 15) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.uwaga_ten_region_jest") + pc.level() + ".");
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
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.uwaga_niebezpieczny_obszar_pot") + danger + ").");
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

    /** Przycisk trybu walki: ofensywny -> zbalansowany -> defensywny -> ofensywny. */
    public void cycleFightMode(long nowMs) {
        setFightMode(pc.fightMode().next(), nowMs);
    }

    /** Tryb walki (akcja "fightmode:NAZWA" od klienta). */
    public void setFightMode(pl.fawizja.pixel.combat.FightMode m, long nowMs) {
        if (m == null) return;
        pc.setFightMode(m);
        log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.tryb_walki") + m.label().toLowerCase(java.util.Locale.ROOT) + ".");
    }

    public void cycleTarget(long nowMs) {
        Monster m = combat.cycleTarget();
        if (m == null) log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.brak_celu_w_poblizu"));
    }

    /** Wybor celu dotknieciem kafelka. Zwraca true, jesli trafiono potwora. */
    public boolean targetAt(Position p) {
        Monster m = monsters.monsterAt(p);
        if (m == null || m.isSummon()) return false;
        combat.setTarget(m);
        return true;
    }

    /**
     * Klient online: przewidywany krok (bez czekania na serwer). Tylko zwykly krok po wolnym kafelku -
     * schody, dziury i inne przejscia czekaja na serwer. Zwraca true, gdy krok sie zaczal.
     */
    /** Po walce z graczem: do tego czasu nie mozna wejsc do strefy ochronnej (mozna z niej wyjsc). */
    public void lockProtectionZonesUntil(long untilMs) { pc.blockPzUntil(untilMs); }

    public long protectionZoneLockedUntil() { return pc.pzBlockedUntil(); }

    // ------------------------------------------------------------------ PvP: tryb i czaszka
    /** Stan PvP (czaszki, blokada PZ). Serwer podmienia go na stan trzymany po imieniu (przetrwa wylogowanie). */
    private pl.fawizja.pixel.pvp.PvpRules.Status pvpStatus = new pl.fawizja.pixel.pvp.PvpRules.Status();

    public pl.fawizja.pixel.pvp.PvpRules.Status pvpStatus() { return pvpStatus; }

    public void setPvpStatus(pl.fawizja.pixel.pvp.PvpRules.Status status) {
        pvpStatus = java.util.Objects.requireNonNull(status);
        syncPvp(lastUpdateMs < 0 ? 0 : lastUpdateMs);
    }

    /** Tryb PvP (przelacznik czaszki); po zalogowaniu zawsze wylaczony. */
    public boolean pvpMode() { return pc.pvpMode(); }
    public boolean isPvpMode() { return pc.pvpMode(); }
    public void setPvpMode(boolean on) { pc.setPvpMode(on); }

    /** Przelacza tryb PvP; zwraca nowy stan. */
    public boolean togglePvpMode() {
        pc.setPvpMode(!pc.pvpMode());
        return pc.pvpMode();
    }

    /** Przelacza tryb PvP z komunikatem w dzienniku (przycisk czaszki, /pvp). */
    public boolean togglePvpMode(long nowMs) {
        boolean on = togglePvpMode();
        log.add(nowMs, on ? EventLog.Kind.WARNING : EventLog.Kind.INFO, on
                ? Messages.get("game_session.tryb_pvp_wlaczony_tryb")
                : Messages.get("game_session.tryb_pvp_wylaczony_tryb"));
        return on;
    }

    /** Przepisuje czaszke i blokade PZ ze stanu PvP do postaci (HUD, inne systemy). */
    private void syncPvp(long nowMs) {
        pl.fawizja.pixel.pvp.PvpRules.Skull sk = pvpStatus.skull(nowMs);
        pc.setSkull(sk, pvpStatus.skullUntil(nowMs));
        pc.blockPzUntil(pvpStatus.fightUntil());
        pc.setLogoutBlockedUntil(pvpStatus.logoutUntil());
    }

    /** Komunikat przy probie wejscia do PZ z blokada (czaszka po ataku na gracza). */
    public String pzBlockedMessage(long nowMs) {
        long left = (pc.pzBlockedUntil() - nowMs + 999) / 1000;
        String time = left >= 60 ? (left / 60) + Messages.get("server_world.min_3") + (left % 60) + " s" : left + " s";
        return Messages.get("game_session.walczysz_z_graczem_nie") + time + ".";
    }

    private boolean pzLocked(Position from, Direction d, long nowMs) {
        if (nowMs >= pc.pzBlockedUntil() || world.isProtectionZone(from)) return false;
        Position to = from.translate(d.dx, d.dy);
        pl.fawizja.pixel.world.Transition t = world.transitionAt(to);
        if (!world.isProtectionZone(to) && (t == null || !world.isProtectionZone(t.to()))) return false;
        if (nowMs - lastPzWarningMs > 3000) {
            lastPzWarningMs = nowMs;
            log.add(nowMs, EventLog.Kind.WARNING, pzBlockedMessage(nowMs));
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
        if (z == 0) return Messages.get("game_session.jestes_na_powierzchni");
        return z > 0 ? Messages.get("game_session.pietro") + z + "." : Messages.get("game_session.podziemia_poziom") + z + ".";
    }

    // ------------------------------------------------------------------ zwykle skrzynie (2026-10-28)

    /** Otwarta skrzynia (schowek) obok postaci albo null. */
    private Position openCrate;
    private final java.util.Map<Position, Npc> crateWindows = new java.util.HashMap<>();
    private long lastPushNoteMs = Long.MIN_VALUE / 2;

    public Position openCrate() { return openCrate; }

    private static String crateName(String key, boolean accusative) {
        switch (key) {
            case "barrel": return accusative ? "beczkę" : Messages.get("game_session.beczka");
            case "sack": return accusative ? "worek" : Messages.get("game_session.worek");
            case "chest": return accusative ? "kufer" : Messages.get("game_session.kufer");
            case "wardrobe": return accusative ? "szafę" : Messages.get("game_session.szafa");
            default: return accusative ? Messages.get("game_session.skrzynie") : Messages.get("game_session.skrzynia");
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
            log.add(nowMs, EventLog.Kind.WARNING, mayEnter(p) ? Messages.get("game_session.masz_w_tym_domu")
                    : Messages.get("game_session.to_schowek_w_cudzym"));
            return true;
        }
        String key = objectKey(p);
        if (p.equals(openCrate)) {
            openCrate = null;
            log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.zamykasz") + crateName(key, true) + ".");
            return true;
        }
        openCrate = p;
        world.crates().open(p, items);
        int n = world.crates().peek(p).size();
        log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.otwierasz") + crateName(key, true) + (n == 0 ? Messages.get("game_session.pusto_mozesz_tu_cos")
                : Messages.get("game_session.w_srodku") + n + " " + (n == 1 ? "rzecz" : n < 5 ? "rzeczy" : "rzeczy") + "."));
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
                nearestTown(q), q, null, Messages.get("game_session.schowek_na") + pl.fawizja.pixel.world.Crates.SLOTS + " rzeczy.", "#8a6a3a"));
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
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.monety_trzymaj_w_banku"));
            return 0;
        }
        int n = moveAll(pc.backpack(), c, itemId, nowMs, Messages.get("game_session.schowano"), Messages.get("game_session.skrzynia_jest_pelna"));
        if (n > 0) cratesChanged(openCrate);
        return n;
    }

    /** Wszystkie sztuki przedmiotu z otwartej skrzyni do plecaka (ile sie zmiesci). */
    public int crateTake(int itemId, long nowMs) {
        pl.fawizja.pixel.inventory.Container c = openCrateContainer(nowMs);
        if (c == null) return 0;
        int n = moveAll(c, pc.backpack(), itemId, nowMs, Messages.get("game_session.wyjeto"), Messages.get("server_world.brak_miejsca_w_plecaku"));
        if (n > 0) cratesChanged(openCrate);
        return n;
    }

    private pl.fawizja.pixel.inventory.Container openCrateContainer(long nowMs) {
        if (crateNearby() == null || !mayHandle(openCrate)) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.podejdz_do_skrzyni_i"));
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
                log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.masz_w_tym_domu_2"));
            }
            return false;
        }
        java.util.function.Predicate<Position> blocked = q -> monsters.monsterAt(q) != null || npcs.at(q) != null
                || dummies.at(q) || !mayEnter(q) || otherPlayers.test(q) || q.equals(pc.position())
                || !java.util.Objects.equals(houses.at(q), houses.at(at));   // meble z domu nie wyjezdzaja na ulice
        if (!c.canPush(at, d, blocked)) {
            if (nowMs - lastPushNoteMs > 4000) {
                lastPushNoteMs = nowMs;
                log.add(nowMs, EventLog.Kind.INFO, crateName(objectKey(at), false) + Messages.get("game_session.nie_przesunie_sie_dalej"));
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
                log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.potrzebujesz_liny"));
                break;
            default:
                if (toggleTraining(nowMs)) break;
                if (openChest(nowMs)) break;
                if (searchCorpse(nowMs)) break;
                if (useCrateNearby(nowMs)) break;
                if (pickBerriesNearby(nowMs)) break;
                if (!gatherHerbs(nowMs)) log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.nie_ma_tu_nic"));
        }
    }

    public pl.fawizja.pixel.gathering.HerbGathering herbs() { return herbs; }

    private long lastTrapMs = Long.MIN_VALUE / 2;

    /** Wejscie na pulapke z kolcami (lochy -2..-5): rani, ale nie zabija; raz na 1,5 s. */
    private void stepOnTrap(long nowMs) {
        if (chests == null || !chests.trapAt(pc.position()) || nowMs - lastTrapMs < 1500) return;
        lastTrapMs = nowMs;
        int dmg = pl.fawizja.pixel.treasure.TreasureChests.trapDamage(pc.maxHealth(), pc.health(), pc.position().z());
        int dealt = combat.environmentHit(Messages.get("game_session.pulapka_z_kolcami"), dmg, nowMs);
        log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.pulapka_z_kolcami_2") + dealt + Messages.get("game_session.hp_omijaj_metalowe_plyty"));
    }

    /** Krzak obok: ziela lecznicze do plecaka (krzak odrasta 10 min). false = nie ma krzaka obok. */
    public boolean gatherHerbs(long nowMs) {
        if (!items.containsKey("healing_herb")) return false;
        int herbId = items.byKey("healing_herb").id();
        if (herbs.readyNear(pc.position(), nowMs) && !pc.backpack().canAdd(herbId, 2)) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.brak_miejsca_w_plecaku"));
            return true;
        }
        return herbResult(herbs.gather(pc.position(), rng, nowMs), nowMs);
    }

    /** Dotkniety krzak z ziolami obok postaci. false = to nie jest krzak z ziolami. */
    private boolean gatherHerbsAt(Position b, long nowMs) {
        if (!items.containsKey("healing_herb") || !herbs.isHerbBush(b)) return false;
        if (!pc.backpack().canAdd(items.byKey("healing_herb").id(), 2)) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.brak_miejsca_w_plecaku"));
            return true;
        }
        return herbResult(herbs.gatherAt(b, rng, nowMs), nowMs);
    }

    private boolean herbResult(pl.fawizja.pixel.gathering.HerbGathering.Result r, long nowMs) {
        switch (r.outcome()) {
            case NO_BUSH:
                return false;
            case REGROWING:
                log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.ten_krzak_jest_juz") + Math.max(1, (r.readyInMs() + 59_999) / 60_000) + Messages.get("server_world.min_2"));
                return true;
            default:
                int id = items.byKey("healing_herb").id();
                if (!pc.backpack().add(new ItemStack(id, r.count())).ok()) {
                    log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.brak_miejsca_w_plecaku"));
                    return true;
                }
                pc.stats().recordHerbs(r.count());
                log.add(nowMs, EventLog.Kind.LOOT, Messages.get("game_session.zebrano") + r.count() + "x " + items.get(id).name()
                        + Messages.get("game_session.masz") + pc.backpack().count(id) + Messages.get("game_session.4_ziela_mikstura_zdrowia"));
                return true;
        }
    }

    public pl.fawizja.pixel.gathering.BerryBushes berries() { return berries; }

    /** Krzak z jagodami albo malinami obok: kilka owocow do plecaka, po kilku zbiorach krzak odrasta godzine. */
    private boolean pickBerries(Position b, long nowMs) {
        pl.fawizja.pixel.gathering.BerryBushes.Kind kind = berries.kindAt(b);
        if (kind == null || !items.containsKey(kind.itemKey)) return false;
        pl.fawizja.pixel.item.ItemType fruit = items.byKey(kind.itemKey);
        String bush = kind == pl.fawizja.pixel.gathering.BerryBushes.Kind.BLUEBERRY ? Messages.get("game_session.krzak_jagod") : Messages.get("game_session.krzak_malin");
        if (berries.isEmpty(b, nowMs)) {
            pl.fawizja.pixel.gathering.BerryBushes.Result r = berries.pick(b, nowMs);
            log.add(nowMs, EventLog.Kind.INFO, bush + Messages.get("game_session.jest_pusty_owoce_odrosna")
                    + Math.max(1, (r.readyInMs() + 59_999) / 60_000) + Messages.get("server_world.min_2"));
            return true;
        }
        if (!pc.backpack().canAdd(fruit.id(), pl.fawizja.pixel.gathering.BerryBushes.PER_PICK)) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.brak_miejsca_w_plecaku_2"));
            return true;
        }
        pl.fawizja.pixel.gathering.BerryBushes.Result r = berries.pick(b, nowMs);
        if (!pc.backpack().add(new ItemStack(fruit.id(), r.count())).ok()) {
            berries.undo(b);
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.brak_miejsca_w_plecaku_2"));
            return true;
        }
        pl.fawizja.pixel.magic.Consumable c = consumables == null ? null : consumables.get(fruit.id());
        int food = c == null ? 0 : c.foodSeconds();
        log.add(nowMs, EventLog.Kind.LOOT, Messages.get("game_session.zrywasz") + r.count() + "x " + fruit.name() + Messages.get("game_session.masz") + pc.backpack().count(fruit.id())
                + (food > 0 ? Messages.get("game_session.kazda_syci") + food + " s" : "") + "). "
                + (r.left() > 0 ? Messages.get("game_session.na_krzaku_zostalo_jeszcze") : bush + Messages.get("game_session.jest_teraz_pusty_odrosnie")));
        return true;
    }

    /** Krzak z owocami obok postaci (przycisk UZYJ, klawisz E): najpierw krzak, ktory ma jeszcze owoce. */
    private boolean pickBerriesNearby(long nowMs) {
        Position me = pc.position(), found = null;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                Position q = me.translate(dx, dy);
                if (berries.kindAt(q) == null) continue;
                if (!berries.isEmpty(q, nowMs)) return pickBerries(q, nowMs);
                if (found == null) found = q;
            }
        }
        return found != null && pickBerries(found, nowMs);
    }

    /** Drogowskaz: kraina i droga do najblizszego miasta (strona swiata i liczba pol). */
    private String signText(Position p) {
        StringBuilder b = new StringBuilder(Messages.get("game_session.na_drogowskazie_napisano"));
        Region r = world.regionAt(p);
        if (r != null) b.append('"').append(r.name()).append('"');
        pl.fawizja.pixel.world.Town best = null;
        int bestD = Integer.MAX_VALUE;
        for (pl.fawizja.pixel.world.Town t : world.towns()) {
            if (t.temple().z() != p.z()) continue;
            int d = t.temple().distanceTo(p);
            if (d < bestD) { bestD = d; best = t; }
        }
        if (best != null) {
            if (r != null) b.append(". ");
            if (bestD <= 12) b.append(Messages.get("game_session.witamy_w_miescie")).append(best.name()).append('!');
            else b.append(best.name()).append(" - ").append(compass(best.temple().x() - p.x(), best.temple().y() - p.y()))
                    .append(", ").append(bestD).append(Messages.get("game_session.pol"));
        } else if (r == null) {
            b.append(Messages.get("game_session.napis_jest_zatarty"));
        }
        return b.toString();
    }

    /** Strona swiata po polsku (y rosnie na poludnie). */
    static String compass(int dx, int dy) {
        double a = Math.toDegrees(Math.atan2(-dy, dx));        // 0 = wschod, 90 = polnoc
        String[] names = {Messages.get("game_session.na_wschod"), Messages.get("game_session.na_polnocny_wschod"), Messages.get("game_session.na_polnoc"), Messages.get("game_session.na_polnocny_zachod"), Messages.get("game_session.na_zachod"),
                Messages.get("game_session.na_poludniowy_zachod"), Messages.get("game_session.na_poludnie"), Messages.get("game_session.na_poludniowy_wschod")};
        int k = (int) Math.round(a / 45.0);
        return names[Math.floorMod(k, 8)];
    }

    /** Uzycie obiektu na mapie (drabina, krzak, drogowskaz, studnia, lozko); false = nic do uzycia. */
    private boolean useWorldObject(Position p, long nowMs) {
        Position me = pc.position();
        // manekin: dotkniecie z sasiedniego pola zaczyna albo konczy trening (nie ma juz przycisku Uzyj)
        if (dummies.at(p)) return toggleTraining(nowMs);
        pl.fawizja.pixel.world.Transition tr = world.transitionAt(p);
        if (tr != null && !tr.type().triggeredByWalking()) {
            if (tr.type().requiresRope() && pc.backpack().count(items.byKey("rope").id()) == 0) {
                log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.potrzebujesz_liny"));
                return true;
            }
            if (pc.isDead()) return true;
            pc.setPosition(tr.to());
            onFloorChanged(nowMs);
            checkRegion(nowMs);
            return true;
        }
        if (pickBerries(p, nowMs)) return true;
        if (gatherHerbsAt(p, nowMs)) return true;
        pl.fawizja.pixel.world.TileType obj = objectAt(p);
        if (obj == null) return false;
        switch (obj.key()) {
            case "signpost":
                log.add(nowMs, EventLog.Kind.INFO, signText(p));
                return true;
            case "well":
                log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.zagladasz_do_studni_woda"));
                return true;
            case "bed":
                return useBed(p, nowMs);
            case "lectern":
                log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.na_pulpicie_lezy_otwarta"));
                return true;
            default:
                return false;
        }
    }

    private pl.fawizja.pixel.world.TileType objectAt(Position p) {
        pl.fawizja.pixel.world.FloorMap f = world.floor(p.z());
        if (f == null || !f.inBounds(p.x(), p.y())) return null;
        int id = f.get(pl.fawizja.pixel.world.Layer.OBJECT, p.x(), p.y());
        return id == 0 ? null : world.tiles().get(id);
    }

    /**
     * Czy dotkniecie pola ma cos uzyc (jak "Uzyj" w Tibii): skrzynia, drzwi, okno, dzwignia, pochodnia, drabina,
     * miejsce na line, krzak z owocami albo ziolami, drogowskaz, studnia, lozko. Klient na tej podstawie podchodzi
     * do pola i go uzywa zamiast wejsc na nie.
     */
    public boolean usableAt(Position p) {
        if (chestAt(p) || world.openings().isDoor(p) || world.openings().isWindow(p) || world.crates().isStorage(p)
                || world.wallLights().isWallLight(p) || world.altarQuestByLever(p) != null || world.altarAt(p) != null
                || world.bossQuestByLever(p) != null || dummies.at(p)) return true;
        pl.fawizja.pixel.world.Transition tr = world.transitionAt(p);
        if (tr != null && !tr.type().triggeredByWalking()) return true;
        if (berries.kindAt(p) != null || herbs.isHerbBush(p)) return true;
        pl.fawizja.pixel.world.TileType obj = objectAt(p);
        return obj != null && USABLE_OBJECTS.contains(obj.key());
    }

    private static final java.util.Set<String> USABLE_OBJECTS = java.util.Set.of("signpost", "well", "bed", "lectern");

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

        @Override
        public boolean noStamina() { return pc.stamina().zone() == pl.fawizja.pixel.character.Stamina.Zone.EMPTY; }
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
            log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.dzwignia_wyprawy") + q.name() + Messages.get("game_session.o_wszystkim_rozstrzyga_serwer"));
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

    /**
     * Drzwi poziomu sa otwarte tylko, gdy ktos w nich stoi: postac na polu drzwi je otwiera, a po zejsciu (krok,
     * schody, teleport, smierc) zamyka za soba - chyba ze w drzwiach stoi juz ktos inny. Dziala osobno dla kazdej
     * sesji (offline i na serwerze, gdzie drzwi sa wspolne dla wszystkich graczy).
     */
    private void closeLevelDoorBehind() {
        Position me = pc.position();
        if (inLevelDoor != null && !inLevelDoor.equals(me)) {
            Position d = inLevelDoor;
            inLevelDoor = null;
            if (!otherPlayers.test(d) && (monsters == null || monsters.monsterAt(d) == null)) world.openings().set(d, false);
        }
        if (inLevelDoor == null && world.levelDoorAt(me) > 0) {
            inLevelDoor = me;
            world.openings().set(me, true);
        }
    }

    /** Na polu stoi ktos inny (gracz, potwor, NPC). */
    private boolean occupiedByOther(Position p) {
        return otherPlayers.test(p) || (monsters != null && monsters.monsterAt(p) != null) || (npcs != null && npcs.at(p) != null);
    }

    static String levelDoorRefusal(int level) {
        return Messages.get("game_session.na_drzwiach_napis") + WorldMap.levelDoorSign(level) + Messages.get("game_session.drzwi_nie_ustepuja_wejdziesz") + level + ".";
    }

    /** Dzwignia sali oltarzy: z piecioma profesjami na miejscach przenosi je do skarbca, inaczej mowi, kogo brakuje. */
    private boolean pullLever(pl.fawizja.pixel.world.AltarQuest q, long nowMs) {
        java.util.List<pl.fawizja.pixel.world.AltarQuest.Slot> missing = q.missing(altarParty);
        if (!missing.isEmpty()) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.dzwignia_ani_drgnie_przed")
                    + pl.fawizja.pixel.world.AltarQuest.names(missing) + Messages.get("game_session.pali_sie") + (q.slots().size() - missing.size())
                    + Messages.get("common.z") + q.slots().size() + Messages.get("game_session.pochodni_nawias"));
            return true;
        }
        log.add(nowMs, EventLog.Kind.SYSTEM, Messages.get("game_session.przeciagasz_dzwignie_oltarze_r") + q.name() + Messages.get("game_session.otwiera_skarbiec"));
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
            log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.na_oltarzu_lezy") + itemName(altar.itemKey()) + Messages.get("game_session.jest_przytwierdzony")
                    + Messages.get("game_session.i_nie_da_sie") + altar.vocation().displayName() + ".");
            return true;
        }
        pl.fawizja.pixel.world.WallLights wl = world.wallLights();
        if (wl.isWallLight(p) && world.isQuestTorch(p)) {
            log.add(nowMs, EventLog.Kind.INFO, wl.isLit(p) ? Messages.get("game_session.pochodnia_plonie_bo_przed")
                    : Messages.get("game_session.pochodnia_zapali_sie_sama"));
            return true;
        }
        int doorLevel = world.levelDoorAt(p);
        if (doorLevel > 0 && pc.level() < doorLevel) {
            log.add(nowMs, EventLog.Kind.WARNING, levelDoorRefusal(doorLevel));
            return true;
        }
        if (doorLevel > 0) {
            // jak w Tibii: uzycie drzwi poziomu wprowadza postac w drzwi, po zejsciu zamykaja sie za nia
            if (p.equals(me)) {
                log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.stoisz_w_drzwiach_zamkna"));
                return true;
            }
            if (occupiedByOther(p)) {
                log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.ktos_stoi_w_drzwiach"));
                return true;
            }
            if (pc.isDead() || isStepping(nowMs)) return true;
            log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.na_drzwiach_napis") + WorldMap.levelDoorSign(doorLevel)
                    + Messages.get("game_session.masz_poziom") + pc.level() + Messages.get("game_session.drzwi_sie_otwieraja"));
            world.openings().set(p, true);
            pc.setPosition(p);
            stepFrom = me;
            stepStartMs = nowMs;
            Direction d = Direction.fromDelta(p.x() - me.x(), p.y() - me.y());
            stepDurationMs = d == null ? 0 : MovementSystem.stepDurationMs(pc, d);
            moving = true;
            if (d != null) facing = d;
            closeLevelDoorBehind();
            return true;
        }
        if (wl.isWallLight(p)) {
            // pochodnia albo latarnia na murze: zapala sie i gasi na miejscu, nie trafia do plecaka
            boolean lit = wl.toggle(p);
            boolean torch = wl.isTorch(p);
            log.add(nowMs, EventLog.Kind.INFO, torch ? (lit ? Messages.get("game_session.zapalasz_pochodnie") : Messages.get("game_session.gasisz_pochodnie"))
                    : (lit ? Messages.get("game_session.zapalasz_latarnie") : Messages.get("game_session.gasisz_latarnie")));
            if (!sharedWorld) pc.setWallLights(wl.encode());
            return true;
        }
        pl.fawizja.pixel.world.Openings o = world.openings();
        boolean door = o.isDoor(p);
        if (!door && !o.isWindow(p)) return useWorldObject(p, nowMs);
        if (!mayUseOpening(p)) {
            pl.fawizja.pixel.house.House h = houses.ofOpening(p);
            log.add(nowMs, EventLog.Kind.WARNING, houseAccess(h) == pl.fawizja.pixel.house.HouseAccess.ENTRY
                    ? Messages.get("game_session.masz_w_domu") + h.name() + Messages.get("game_session.tylko_wstep") + (door ? Messages.get("game_session.drzwi") : Messages.get("game_session.okna")) + Messages.get("game_session.otwiera_wlasciciel_albo_gosc")
                    : (door ? Messages.get("game_session.to_drzwi") : Messages.get("game_session.to_okno")) + Messages.get("game_session.domu") + h.name() + Messages.get("game_session.otwiera_je_wlasciciel_albo"));
            return true;
        }
        if (door && o.isOpen(p) && (p.equals(me) || occupiedByOther(p))) {
            log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.ktos_stoi_w_drzwiach"));
            return true;
        }
        boolean opened = o.toggle(p);
        log.add(nowMs, EventLog.Kind.INFO, door ? (opened ? Messages.get("game_session.otwierasz_drzwi") : Messages.get("game_session.zamykasz_drzwi"))
                : (opened ? Messages.get("game_session.otwierasz_okno") : Messages.get("game_session.zamykasz_okno")));
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
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.ta_skrzynia_nalezy_do") + c.vocation().displayName()
                    + Messages.get("game_session.nie_mozesz_z_niej"));
            return true;
        }
        if (pc.openedChests().contains(c.id())) {
            log.add(nowMs, EventLog.Kind.INFO, c.vocation() != null
                    ? Messages.get("game_session.ta_skrzynia_jest_pusta") : Messages.get("game_session.ta_skrzynia_jest_juz"));
            return true;
        }
        // nagroda wg trudnosci miejsca (ChestRewards): zloto, przedmiot dla profesji i mikstury
        pl.fawizja.pixel.treasure.ChestRewards.Reward r = chestReward(c);
        pl.fawizja.pixel.inventory.Container tx = pc.backpack().copy();
        boolean ok = pl.fawizja.pixel.economy.Money.add(tx, r.gold()).ok();
        for (pl.fawizja.pixel.item.ItemStack st : r.items()) ok = ok && tx.add(st).ok();
        if (!ok) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.skrzynia_pelna_skarbow_ale"));
            return true;
        }
        pc.backpack().commitFrom(tx);
        pc.openedChests().add(c.id());
        for (String line : pl.fawizja.pixel.treasure.ChestRewards.lines(r, items)) log.add(nowMs, EventLog.Kind.LOOT, line);
        return true;
    }

    /** Nagroda ze skrzyni dla tej postaci (bez otwierania; podglad i testy). */
    public pl.fawizja.pixel.treasure.ChestRewards.Reward chestReward(pl.fawizja.pixel.treasure.TreasureChests.Chest c) {
        return pl.fawizja.pixel.treasure.ChestRewards.reward(c, world, monsters, items, pc.vocation());
    }

    public void toggleRun(long nowMs) {
        pc.setRunning(!pc.isRunning());
        log.add(nowMs, EventLog.Kind.INFO, pc.isRunning() ? Messages.get("game_session.podazanie_wlaczone_postac_idzi") : Messages.get("game_session.podazanie_wylaczone"));
    }

    public void toggleTorch(long nowMs) {
        boolean want = !pc.isTorchLit();
        if (!pc.setTorchLit(want, torchId)) {
            log.add(nowMs, EventLog.Kind.WARNING, pc.backpack().count(torchId) > 0
                    ? Messages.get("game_session.nie_masz_zalozonej_pochodni")
                    : Messages.get("game_session.nie_masz_zalozonej_pochodni_2"));
        } else {
            long left = pc.torchRemainingMs(torchId);
            log.add(nowMs, EventLog.Kind.INFO, want ? Messages.get("game_session.zapalasz_pochodnie_zostalo") + torchTime(left) + ")." : Messages.get("game_session.gasisz_pochodnie"));
        }
    }

    /** Pozostaly czas pochodni do opisu: "18 min" albo "45 s". */
    public static String torchTime(long ms) {
        long sec = (ms + 999) / 1000;
        return sec >= 60 ? (sec + 59) / 60 + Messages.get("hud.min") : sec + " s";
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
            log.add(nowMs, EventLog.Kind.LOOT, Messages.get("game_session.z_ciala") + b);
        }
        if (ground.corpseAt(p) != null && ground.corpseAt(p).hasLoot()) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.plecak_jest_pelny_reszta"));
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
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.masz_w_tym_domu_3"));
            return;
        }
        ItemStack top = ground.at(p).get(ground.at(p).size() - 1);
        InventoryResult r = ground.pickUp(p, ground.at(p).size() - 1, pc.backpack());
        if (r.ok()) {
            log.add(nowMs, EventLog.Kind.LOOT, Messages.get("game_session.podniesiono") + describe(top));
        } else {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.plecak_jest_pelny"));
        }
    }

    public void dropFromSlot(int slot, long nowMs) {
        ItemStack s = pc.backpack().get(slot);
        if (s == null) return;
        if (pc.isKept(s.itemId())) {
            log.add(nowMs, EventLog.Kind.WARNING, items.get(s.itemId()).name() + Messages.get("game_session.jest_oznaczony_jako_zachowany"));
            return;
        }
        if (!mayHandle(pc.position())) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.masz_w_tym_domu_4"));
            return;
        }
        InventoryResult r = ground.dropFrom(pc.backpack(), slot, s.count(), pc.position(), world);
        if (r.ok()) log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.wyrzucono") + describe(s));
    }

    /** Przelacza "Zachowaj" dla rodzaju przedmiotu w slocie plecaka. */
    public boolean toggleKeepSlot(int slot, long nowMs) {
        ItemStack s = pc.backpack().get(slot);
        if (s == null) return false;
        boolean on = pc.toggleKept(s.itemId());
        String name = items.get(s.itemId()).name();
        log.add(nowMs, EventLog.Kind.INFO, on
                ? Messages.get("game_session.zachowany") + name + Messages.get("game_session.nie_sprzedasz_go_hurtem")
                : Messages.get("game_session.odblokowano") + name + ".");
        return on;
    }

    public void equipFromSlot(int slot, long nowMs) {
        ItemStack st = slot >= 0 && slot < pc.backpack().size() ? pc.backpack().get(slot) : null;
        boolean torch = st != null && st.itemId() == torchId;
        if (torch && pc.equipment().get(pl.fawizja.pixel.item.EquipSlot.LIGHT) != null) {
            log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.masz_juz_zalozona_pochodnie"));
            return;
        }
        InventoryResult r = pc.equipment().equipFromBackpack(pc.backpack(), slot, pc.vocation(), pc.level());
        switch (r) {
            case OK: log.add(nowMs, EventLog.Kind.INFO, torch
                    ? Messages.get("game_session.zalozono_pochodnie_dotknij_jej") : Messages.get("game_session.zalozono_przedmiot")); break;
            case WRONG_VOCATION: log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.twoja_profesja_nie_moze")); break;
            case LEVEL_TOO_LOW: log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.za_niski_poziom")); break;
            case WRONG_SLOT_TYPE: log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.tego_nie_da_sie_2")); break;
            default: log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.nie_udalo_sie_2") + r);
        }
    }

    /** Zdejmuje przedmiot z gniazda ekwipunku do plecaka (okno ekwipunku). */
    public InventoryResult unequip(pl.fawizja.pixel.item.EquipSlot slot, long nowMs) {
        if (slot == pl.fawizja.pixel.item.EquipSlot.NONE) return InventoryResult.EMPTY_SLOT;
        InventoryResult r = pc.equipment().unequipToBackpack(slot, pc.backpack());
        if (r.ok() && slot == pl.fawizja.pixel.item.EquipSlot.LIGHT && pc.isTorchLit()) pc.setTorchLit(false, torchId);
        switch (r) {
            case OK: log.add(nowMs, EventLog.Kind.INFO, Messages.get("game_session.zdjeto_przedmiot_do_plecaka")); break;
            case EMPTY_SLOT: break;
            default: log.add(nowMs, EventLog.Kind.WARNING, Messages.get("game_session.plecak_jest_pelny_nie"));
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
        boolean exchanged = pc.backpack().normalizeCoins();
        boolean moved = pc.backpack().sort() || exchanged;
        log.add(nowMs, EventLog.Kind.INFO, moved ? Messages.get("game_session.plecak_uporzadkowany") : Messages.get("game_session.plecak_jest_juz_uporzadkowany"));
        return moved;
    }

    public long gold() { return Money.totalValue(pc.backpack()); }

    /** Opisy rol NPC do podgladu pola (dlugie dotkniecie, prawy przycisk myszy). */
    private static String roleLabel(pl.fawizja.pixel.npc.NpcRole r) {
        switch (r) {
            case MERCHANT: return "kupiec";
            case BANKER: return "bankier";
            case PRIEST: return Messages.get("game_session.kaplan");
            case GUIDE: return "przewodnik";
            case CARRIER: return Messages.get("game_session.woznica");
            case CAPTAIN: return Messages.get("game_session.kapitan_statku");
            case KING: return Messages.get("game_session.wladca_miasta");
            case IMBUER: return "zaklinacz";
            case GUARD: return Messages.get("game_session.straznik");
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
        if (p.equals(pc.position())) parts.add(Messages.get("game_session.siebie") + pc.name() + Messages.get("pixel_game.poziom") + pc.level() + ")");
        Monster m = monsters == null ? null : monsters.monsterAt(p);
        if (m != null && !m.isDead()) {
            parts.add(m.type().name() + Messages.get("game_session.poziom_2") + m.type().level() + Messages.get("game_session.zdrowie") + Math.round(m.healthFraction() * 100) + "%)");
        }
        pl.fawizja.pixel.npc.Npc npc = npcs == null ? null : npcs.at(p);
        if (npc != null) parts.add(npc.name() + " (" + roleLabel(npc.role()) + ")");
        if (dummies != null && dummies.at(p)) parts.add(Messages.get("game_session.manekin_treningowy"));
        GroundItems.Corpse c = ground.corpseAt(p);
        if (c != null) {
            String who = monsters != null && monsters.registry() != null && monsters.registry().contains(c.monsterKey())
                    ? monsters.registry().get(c.monsterKey()).name() : c.monsterKey();
            parts.add(Messages.get("game_session.cialo") + who + (c.hasLoot() ? Messages.get("game_session.jest_lup") : Messages.get("game_session.puste_nawias")));
        }
        java.util.List<ItemStack> lying = ground.at(p);
        for (int i = lying.size() - 1, shown = 0; i >= 0 && shown < 3; i--, shown++) parts.add(describe(lying.get(i)));
        pl.fawizja.pixel.world.AltarQuest.Slot altar = world.altarAt(p);
        if (altar != null) parts.add(Messages.get("game_session.oltarz_profesji") + altar.vocation().displayName() + " (" + itemName(altar.itemKey()) + ")");
        if (world.altarQuestByLever(p) != null) parts.add(Messages.get("game_session.dzwignia"));
        pl.fawizja.pixel.world.BossQuest bq = world.bossQuestByLever(p);
        if (bq != null) parts.add(Messages.get("game_session.dzwignia_wyprawy_2") + bq.name() + Messages.get("game_session.5_postaci_na_polach") + bq.minLevel() + ")");
        bq = world.bossQuestBySquare(p);
        if (bq != null) parts.add(Messages.get("game_session.oznaczone_pole_wyprawy") + bq.name() + Messages.get("game_session.tu_staje_jedna_z"));
        bq = world.bossQuestByPortal(p);
        if (bq != null) parts.add(Messages.get("game_session.portal_wyjscia_z_areny"));
        if (world.levelDoorAt(p) > 0) parts.add(Messages.get("game_session.drzwi_z_napisem") + WorldMap.levelDoorSign(world.levelDoorAt(p)));
        pl.fawizja.pixel.world.Transition tr = world.transitionAt(p);
        if (tr != null) {
            switch (tr.type()) {
                case STAIRS: parts.add("schody"); break;
                case HOLE: parts.add(Messages.get("game_session.dziura_w_ziemi")); break;
                case TUNNEL: parts.add(Messages.get("game_session.przejscie")); break;
                case LADDER: parts.add("drabina"); break;
                case ROPE_SPOT: parts.add(Messages.get("game_session.miejsce_na_line")); break;
                case GRATE: parts.add(Messages.get("game_session.kratka_sciekowa_dotknij_by")); break;
                default: break;
            }
        }
        if (parts.isEmpty()) parts.add(world.isWalkable(p) ? Messages.get("game_session.wolne_pole") : "przeszkoda");
        StringBuilder b = new StringBuilder(Messages.get("game_session.widzisz")).append(String.join(", ", parts)).append('.');
        int dist = p.z() == pc.position().z() ? p.distanceTo(pc.position()) : -1;
        if (dist > 1) {
            boolean few = dist % 10 >= 2 && dist % 10 <= 4 && (dist % 100 < 12 || dist % 100 > 14);
            b.append(Messages.get("game_session.odleglosc")).append(dist).append(few ? " pola." : Messages.get("game_session.pol"));
        }
        if (world.isProtectionZone(p)) b.append(Messages.get("game_session.strefa_ochronna"));
        int req = world.requiredLevelAt(p);
        if (req > 0) b.append(Messages.get("game_session.wymagany_poziom")).append(req).append('.');
        log.add(nowMs, EventLog.Kind.INFO, b.toString());
        return b.toString();
    }

    public String describe(ItemStack s) {
        String name = items.get(s.itemId()).name();
        return s.count() > 1 ? s.count() + "x " + name : name;
    }
}
