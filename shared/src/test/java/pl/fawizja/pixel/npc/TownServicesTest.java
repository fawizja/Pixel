package pl.fawizja.pixel.npc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.economy.Money;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.inventory.InventoryResult;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.magic.Recipe;
import pl.fawizja.pixel.magic.RecipeRegistry;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.quest.QuestKind;
import pl.fawizja.pixel.quest.QuestLog;
import pl.fawizja.pixel.quest.QuestRegistry;
import pl.fawizja.pixel.quest.QuestResult;
import pl.fawizja.pixel.quest.QuestType;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldLayout;
import pl.fawizja.pixel.world.WorldMap;

/** Etap 12: NPC w 4 miastach, handel, bank, kaplan i zadania. */
class TownServicesTest {

    static WorldMap world;
    static ShopRegistry shops;
    static NpcRegistry npcs;
    static QuestRegistry quests;
    static Set<Position> reach;
    static final MonsterRegistry MONSTERS = MonsterRegistry.loadDefault(TestData.ITEMS);

    @BeforeAll
    static void load() throws Exception {
        world = TestData.world();
        shops = ShopRegistry.loadDefault(TestData.ITEMS);
        npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, world, shops);
        quests = QuestRegistry.loadDefault(TestData.ITEMS, MONSTERS);
        reach = surfaceFromVarden();
    }

    static int id(String key) { return TestData.ITEMS.byKey(key).id(); }

    /** Osiagalny z Varden kafelek, z ktorego gracz moze rozmawiac z NPC (bez sciany pomiedzy). */
    static Position talkSpot(Npc n) {
        Position c = n.position();
        for (int r = 1; r <= Npc.TALK_RANGE; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    Position p = c.translate(dx, dy);
                    if (p.distanceTo(c) != r || !reach.contains(p)) continue;
                    if (npcs.nearest(p, world) == n) return p;
                }
            }
        }
        return null;
    }

    static Set<Position> surfaceFromVarden() {
        Set<Position> seen = new HashSet<>();
        ArrayDeque<Position> q = new ArrayDeque<>();
        seen.add(world.spawn());
        q.add(world.spawn());
        while (!q.isEmpty()) {
            Position p = q.poll();
            for (Direction d : Direction.values()) {
                if (d.diagonal()) continue;
                Position n = p.translate(d.dx, d.dy);
                if (world.isWalkable(n) && npcs.at(n) == null && world.transitionAt(n) == null && seen.add(n)) q.add(n);
            }
        }
        return seen;
    }

    GameSession session(PlayerCharacter pc, List<SpawnPoint> spawns) {
        MonsterManager mm = new MonsterManager(world, MONSTERS, spawns, new Random(5));
        mm.populate(null);
        GameSession s = new GameSession(TestData.ITEMS, world, pc, mm, new Random(6), 8);
        s.setTownServices(npcs, shops, quests);
        return s;
    }

    @Test
    void everyTownHasAllServicesReachableFromVarden() {
        for (String town : new String[] {"Varden", "Frostholm", "Selvaran", "Qasirah"}) {
            for (NpcRole role : NpcRole.values()) {
                assertTrue(npcs.all().stream().anyMatch(n -> n.town().equals(town) && n.role() == role), town + " " + role);
            }
        }
        for (Npc n : npcs.all()) {
            Position spot = talkSpot(n);
            assertNotNull(spot, "no reachable place to talk to " + n.key());
        }
        // NPC nie blokuja swiatyn ani wejsc do miast
        for (pl.fawizja.pixel.world.Town t : world.towns()) assertNull(npcs.at(t.temple()), t.name());
    }

    @Test
    void shopsNeverLetPlayersPrintMoney() {
        for (Shop a : shops.all()) {
            for (ShopOffer o : a.offers()) {
                if (!o.bought()) continue;
                for (Shop b : shops.all()) {
                    ShopOffer p = b.offer(o.itemId());
                    if (p != null && p.sold()) assertTrue(p.buyPrice() > o.sellPrice(), a.key() + "/" + b.key() + " item " + o.itemId());
                }
            }
        }
        // receptury z kupowanych skladnikow nie daja zysku przy sprzedazy wyniku
        for (Recipe r : RecipeRegistry.loadDefault(TestData.ITEMS).all()) {
            long cost = r.goldCost();
            boolean buyable = true;
            for (Map.Entry<Integer, Integer> e : r.ingredients().entrySet()) {
                long cheapest = cheapestBuy(e.getKey());
                if (cheapest == 0) { buyable = false; break; }
                cost += cheapest * e.getValue();
            }
            if (buyable) assertTrue(bestSell(r.resultItemId()) * r.resultCount() < cost, "recipe arbitrage " + r.key());
        }
        // zadan na przynoszenie nie da sie wykonac zakupami
        for (QuestType q : quests.all()) {
            if (q.kind() == QuestKind.COLLECT) assertEquals(0, cheapestBuy(id(q.target())), q.key() + " target is sold");
        }
    }

    static long cheapestBuy(int itemId) {
        long best = 0;
        for (Shop s : shops.all()) {
            ShopOffer o = s.offer(itemId);
            if (o != null && o.sold() && (best == 0 || o.buyPrice() < best)) best = o.buyPrice();
        }
        return best;
    }

    static long bestSell(int itemId) {
        long best = 0;
        for (Shop s : shops.all()) {
            ShopOffer o = s.offer(itemId);
            if (o != null) best = Math.max(best, o.sellPrice());
        }
        return best;
    }

    @Test
    void buyingAndSellingIsAtomic() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        Shop general = shops.get("varden_general");
        long gold = Money.totalValue(pc.backpack());
        assertEquals(TradeResult.OK, TradeSystem.buy(pc.backpack(), TestData.ITEMS, general, id("health_potion"), 20));
        assertEquals(gold - 20 * 50, Money.totalValue(pc.backpack()));
        assertEquals(TradeResult.NOT_BOUGHT_HERE, TradeSystem.sellAll(pc.backpack(), general, id("health_potion")));
        assertEquals(TradeResult.NOT_SOLD_HERE, TradeSystem.buy(pc.backpack(), TestData.ITEMS, general, id("frost_blade"), 1));
        assertEquals(TradeResult.INVALID_AMOUNT, TradeSystem.buy(pc.backpack(), TestData.ITEMS, general, id("torch"), 0));
        // za malo pieniedzy: nic sie nie zmienia
        PlayerCharacter poor = TestData.newCharacter(Vocation.DRUID);
        Money.pay(poor.backpack(), Money.totalValue(poor.backpack()) - 30);
        String before = poor.backpack().contents().toString();
        assertEquals(TradeResult.NO_MONEY, TradeSystem.buy(poor.backpack(), TestData.ITEMS, general, id("rope"), 1));
        assertEquals(before, poor.backpack().contents().toString());
        // pelny plecak: nic sie nie zmienia, pieniadze zostaja
        PlayerCharacter full = TestData.newCharacter(Vocation.MONK);
        for (int i = 0; full.backpack().freeSlots() > 0; i++) full.backpack().add(new ItemStack(id("leather_armor"), 1));
        long fullGold = Money.totalValue(full.backpack());
        assertEquals(TradeResult.NO_SPACE, TradeSystem.buy(full.backpack(), TestData.ITEMS, general, id("training_sword"), 1));
        assertEquals(fullGold, Money.totalValue(full.backpack()));
        // sprzedaz wszystkich sztuk naraz
        assertEquals(TradeResult.OK, TradeSystem.sellAll(full.backpack(), general, id("leather_armor")));
        assertEquals(0, full.backpack().count(id("leather_armor")));
        assertTrue(Money.totalValue(full.backpack()) > fullGold);
    }

    @Test
    void sessionTradesBanksHealsAndSettlesOnlyNextToTheRightNpc() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        GameSession s = session(pc, List.of());
        long t = 1000;
        assertEquals(TradeResult.TOO_FAR, s.buy(id("rope"), 1, t), "not next to a merchant");
        s.teleport(talkSpot(npcs.get("varden_merchant")), t);
        assertEquals(npcs.get("varden_merchant"), s.npcNearby());
        assertEquals(TradeResult.OK, s.buy(id("rope"), 1, t));
        pc.backpack().add(new ItemStack(id("wolf_pelt"), 5));
        long g = s.gold();
        assertEquals(TradeResult.OK, s.sellAll(id("wolf_pelt"), t));
        assertEquals(g + 5 * 12, s.gold());

        s.teleport(talkSpot(npcs.get("varden_banker")), t);
        long cash = s.gold();
        assertEquals(InventoryResult.OK, s.depositAll(t));
        assertEquals(0, s.gold());
        assertEquals(cash, pc.bank().balance());
        assertEquals(InventoryResult.OK, s.withdraw(1000, t));
        assertEquals(1000, s.gold());

        s.teleport(talkSpot(npcs.get("frost_priest")), t);
        pc.damage(50);
        s.priestHeal(t);
        assertEquals(pc.maxHealth(), pc.health());
        s.settleHere(t);
        assertEquals("frostholm", pc.homeTown());
        assertEquals(world.town("Frostholm").temple(), world.templeOf(pc.homeTown()));
    }

    @Test
    void depotStoresItemsOnlyAtABankerKeepsCoinsOutAndSurvivesSaving() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        GameSession s = session(pc, List.of());
        long t = 1000;
        pc.backpack().add(new ItemStack(id("wolf_pelt"), 7));
        assertEquals(0, s.depotStore(id("wolf_pelt"), t), "not next to a banker");
        assertEquals(7, pc.backpack().count(id("wolf_pelt")));

        s.teleport(talkSpot(npcs.get("frost_banker")), t);
        assertEquals(7, s.depotStore(id("wolf_pelt"), t));
        assertEquals(0, pc.backpack().count(id("wolf_pelt")));
        assertEquals(7, pc.depot().count(id("wolf_pelt")));
        int coin = pl.fawizja.pixel.economy.Currency.GOLD.itemId();
        long gold = s.gold();
        assertEquals(0, s.depotStore(coin, t), "coins go to the bank account");
        assertEquals(gold, s.gold());

        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertEquals(7, back.depot().count(id("wolf_pelt")));
        assertEquals(PlayerCharacter.DEPOT_SLOTS, back.depot().size());

        // depozyt jest wspolny: wyjmujemy w innym miescie
        s.teleport(talkSpot(npcs.get("varden_banker")), t);
        assertEquals(7, s.depotTake(id("wolf_pelt"), t));
        assertEquals(7, pc.backpack().count(id("wolf_pelt")));
        assertEquals(0, pc.depot().count(id("wolf_pelt")));
        assertEquals(0, s.depotTake(id("wolf_pelt"), t));
    }

    @Test
    void theDailyTaskIsGivenOncePerDayCountsKillsAndPaysToTheBank() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        Position field = new Position(58, 72, 0);
        GameSession s = session(pc, List.of(new SpawnPoint("wolf", field, 6, 40, 600)));
        long[] day = {20_000};
        s.setToday(() -> day[0]);
        long t = 1000;
        s.dailyAction(t);
        assertFalse(pc.daily().activeOn(day[0]), "only at a guide");
        s.teleport(talkSpot(npcs.get("varden_guide")), t);
        s.dailyAction(t);
        assertTrue(pc.daily().activeOn(day[0]));
        assertEquals("wolf", pc.daily().monster(), "the only monster around fits level 8");
        int target = pc.daily().target();
        assertTrue(target >= 20 && target <= 40);
        s.dailyAction(t);
        assertEquals(target, pc.daily().target(), "asking again keeps the task");
        for (Monster m : List.copyOf(s.monsters().monsters())) {
            if (pc.daily().doneOn(day[0])) break;
            s.combat().damageMonster(m, 100_000, pl.fawizja.pixel.combat.DamageType.FIRE, t++);
        }
        assertTrue(pc.daily().doneOn(day[0]));
        assertEquals(target, pc.daily().progress());
        long bank = pc.bank().balance();
        long exp = pc.experience();
        s.dailyAction(t);
        assertTrue(pc.daily().claimed());
        MonsterRegistry reg = MONSTERS;
        assertEquals(bank + pl.fawizja.pixel.daily.DailyTask.rewardGold(reg.get("wolf"), target), pc.bank().balance());
        assertTrue(pc.experience() > exp);
        s.dailyAction(t);
        assertEquals(bank + pl.fawizja.pixel.daily.DailyTask.rewardGold(reg.get("wolf"), target), pc.bank().balance(), "paid once");

        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertTrue(back.daily().claimed());
        assertEquals("wolf", back.daily().monster());

        day[0]++;
        s.dailyAction(t);
        assertTrue(pc.daily().activeOn(day[0]), "a new day, a new task");
        assertFalse(pc.daily().claimed());
    }

    @Test
    void aSaddleFromTheMerchantTamesAHorseThatIsFasterButStaysAboveGround() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        GameSession s = session(pc, List.of());
        long t = 1000;
        s.toggleMount(t);
        assertFalse(pc.isMounted(), "no horse yet");
        s.teleport(talkSpot(npcs.get("varden_merchant")), t);
        pl.fawizja.pixel.economy.Money.add(pc.backpack(), 50_000);
        assertEquals(TradeResult.OK, s.buy(id("saddle"), 1, t));
        int slot = -1;
        for (int i = 0; i < pc.backpack().size(); i++) {
            if (pc.backpack().get(i) != null && pc.backpack().get(i).itemId() == id("saddle")) slot = i;
        }
        int walk = pc.speed();
        s.useSlot(slot, t);
        assertTrue(pc.hasMount());
        assertEquals(0, pc.backpack().count(id("saddle")), "the saddle is used up");
        s.toggleMount(t);
        assertTrue(pc.isMounted());
        assertEquals(walk + PlayerCharacter.MOUNT_SPEED, pc.speed());
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertTrue(back.hasMount() && back.isMounted());
        s.toggleMount(t);
        assertFalse(pc.isMounted());
        assertEquals(walk, pc.speed());
        s.toggleMount(t);
        pc.setPosition(new Position(pc.position().x(), pc.position().y(), -1));
        s.update(t + 100, null);
        assertFalse(pc.isMounted(), "no riding underground");
        s.toggleMount(t + 200);
        assertFalse(pc.isMounted());
    }

    @Test
    void killQuestCountsKillsAndPaysOnce() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        Position field = new Position(58, 72, 0);
        GameSession s = session(pc, List.of(new SpawnPoint("wolf", field, 3, 10, 600)));
        long t = 1000;
        Npc guide = npcs.get("varden_guide");
        s.teleport(talkSpot(guide), t);
        assertEquals(QuestResult.NOT_FINISHED, s.questAction("frost_yeti", t), "other town's quest");
        assertEquals(QuestResult.TOO_LOW_LEVEL, s.questAction("varden_king", t));
        assertEquals(QuestResult.ACCEPTED, s.questAction("varden_wolves", t));
        assertEquals(QuestResult.NOT_FINISHED, s.questAction("varden_wolves", t));
        int killed = 0;
        for (Monster m : List.copyOf(s.monsters().monsters())) {
            s.combat().damageMonster(m, 100_000, pl.fawizja.pixel.combat.DamageType.FIRE, t++);
            killed++;
        }
        assertEquals(10, killed);
        assertEquals(10, pc.bestiary().kills("wolf"), "the bestiary counts every kill");
        assertEquals(1, pc.bestiary().species());
        assertEquals(10, pc.quests().progress("varden_wolves"));
        long exp = pc.experience();
        long gold = s.gold();
        assertEquals(QuestResult.COMPLETED, s.questAction("varden_wolves", t));
        assertEquals(exp + 2000, pc.experience());
        assertEquals(gold + 300, s.gold());
        assertEquals(QuestLog.State.DONE, pc.quests().state("varden_wolves"));
        assertEquals(QuestResult.ALREADY_DONE, s.questAction("varden_wolves", t));
    }

    @Test
    void collectQuestTakesItemsAndQuestsSurviveSaving() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        pc.addExperience(pl.fawizja.pixel.balance.Balance.experienceForLevel(13) - pc.experience());
        GameSession s = session(pc, List.of());
        s.teleport(talkSpot(npcs.get("varden_guide")), 1000);
        assertEquals(QuestResult.ACCEPTED, s.questAction("varden_ears", 1000));
        assertEquals(QuestResult.ACCEPTED, s.questAction("varden_wolves", 1000));
        pc.backpack().add(new ItemStack(id("goblin_ear"), 12));
        pc.setHomeTown("qasirah");
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertEquals(QuestLog.State.ACTIVE, back.quests().state("varden_ears"));
        assertEquals("qasirah", back.homeTown());
        assertEquals(QuestResult.COMPLETED, s.questAction("varden_ears", 1000));
        assertEquals(2, pc.backpack().count(id("goblin_ear")));
        PlayerCharacter again = codec.decode(codec.encode(pc));
        assertEquals(QuestLog.State.DONE, again.quests().state("varden_ears"));
        assertEquals(QuestLog.State.ACTIVE, again.quests().state("varden_wolves"));
    }

    @Test
    void theCampBehindTheBridgeTradesBanksAndGivesQuestsButHasNoTemple() {
        List<Npc> camp = npcs.all().stream().filter(n -> n.town().equals("Oboz Zwiadowcow")).toList();
        assertEquals(Set.of(NpcRole.MERCHANT, NpcRole.BANKER, NpcRole.GUIDE),
                camp.stream().map(Npc::role).collect(java.util.stream.Collectors.toSet()));
        for (Npc n : camp) {
            assertTrue(n.position().x() >= 168, n.key() + " stands behind the guarded bridge");
            assertTrue(world.requiredLevelAt(n.position()) == 0 && world.isProtectionZone(n.position()), n.key());
        }
        for (QuestType q : quests.byGiver("camp_guide")) assertTrue(q.minLevel() >= 230, q.key());
    }

    @Test
    void endgameGearIsForgedFromItsOwnTierMaterialsAndNeverPaysForItself() throws Exception {
        RecipeRegistry recipes = RecipeRegistry.loadDefault(TestData.ITEMS);
        for (int itemId = 92; itemId <= 120; itemId++) {
            if (itemId == 116) continue; // korona Otchlani tylko od Mor'gatha
            final int target = itemId;
            Recipe r = recipes.all().stream().filter(x -> x.resultItemId() == target).findFirst().orElse(null);
            assertNotNull(r, "no recipe for " + TestData.ITEMS.get(itemId).key());
            assertEquals(TestData.ITEMS.get(itemId).minLevel(), r.minLevel(), r.key());
            long materials = 0;
            for (Map.Entry<Integer, Integer> e : r.ingredients().entrySet()) materials += bestSell(e.getKey()) * e.getValue();
            assertTrue(bestSell(itemId) < r.goldCost() + materials, r.key() + " forging and selling must lose money");
        }
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        pc.addExperience(pl.fawizja.pixel.balance.Balance.experienceForLevel(250) - pc.experience());
        Recipe spear = recipes.all().stream().filter(x -> x.key().equals("magma_spear")).findFirst().orElseThrow();
        pc.backpack().add(new ItemStack(id("obsidian_shard"), 20));
        pc.backpack().add(new ItemStack(id("platinum_coin"), 300));
        assertEquals(pl.fawizja.pixel.magic.CastResult.OK, pl.fawizja.pixel.magic.CraftingSystem.craft(pc, spear));
        assertEquals(1, pc.backpack().count(id("magma_spear")));
        assertEquals(0, pc.backpack().count(id("obsidian_shard")));
    }

    @Test
    void questChainsUnlockStageByStageAndPayItems() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        pc.addExperience(pl.fawizja.pixel.balance.Balance.experienceForLevel(22) - pc.experience());
        GameSession s = session(pc, List.of());
        long t = 1000;
        s.teleport(talkSpot(npcs.get("varden_guide")), t);
        assertEquals(QuestResult.LOCKED, s.questAction("varden_smug2", t), "stage 2 needs stage 1");
        assertEquals(QuestLog.State.AVAILABLE, pc.quests().state("varden_smug2"));
        assertEquals(QuestResult.ACCEPTED, s.questAction("varden_smug1", t));
        for (int i = 0; i < 8; i++) s.quests().onKill(pc, "bandit");
        assertEquals(QuestResult.COMPLETED, s.questAction("varden_smug1", t));

        assertEquals(QuestResult.ACCEPTED, s.questAction("varden_smug2", t));
        for (int i = 0; i < 6; i++) s.quests().onKill(pc, "goblin_shaman");
        int potions = pc.backpack().count(id("health_potion"));
        assertEquals(QuestResult.COMPLETED, s.questAction("varden_smug2", t));
        assertEquals(potions + 10, pc.backpack().count(id("health_potion")), "item reward");

        assertEquals(QuestResult.ACCEPTED, s.questAction("varden_smug3", t));
        for (int i = 0; i < 12; i++) s.quests().onKill(pc, "skeleton");
        // pelny plecak: nagroda czeka, zadanie zostaje aktywne
        while (pc.backpack().freeSlots() > 0) pc.backpack().add(new ItemStack(id("leather_helmet"), 1));
        assertEquals(QuestResult.NO_SPACE, s.questAction("varden_smug3", t));
        assertEquals(QuestLog.State.ACTIVE, pc.quests().state("varden_smug3"));
        pc.backpack().remove(id("leather_helmet"), 1);
        assertEquals(QuestResult.COMPLETED, s.questAction("varden_smug3", t));
        assertEquals(1, pc.backpack().count(id("miner_helmet")));
        assertTrue(s.log().latest(1).get(0).text().contains("Hełm górnika"), s.log().latest(1).get(0).text());
    }

    @Test
    void everyChainStartsFromAQuestWithoutPrerequisites() {
        int chained = 0;
        for (QuestType q : quests.all()) {
            if (q.requires() == null) continue;
            chained++;
            QuestType prev = quests.get(q.requires());
            assertEquals(q.giver(), prev.giver());
            assertTrue(prev.minLevel() <= q.minLevel(), q.key() + " is easier than its prerequisite");
            if (q.rewardItem() != null) {
                assertTrue(TestData.ITEMS.byKey(q.rewardItem()).minLevel() <= q.minLevel() + 40,
                        q.key() + " reward would skip tiers");
            }
        }
        assertTrue(chained >= 4);
    }

    @Test
    void talkingToANearbyNpcAnswersInTheLogAndFarAwayNobodyListens() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        GameSession s = session(pc, List.of());
        s.teleport(talkSpot(npcs.get("varden_guide")), 1000);
        String line = s.askNpc("Jakie masz zadania?", 1000);
        assertTrue(line.startsWith(npcs.get("varden_guide").name() + ": Poniżej masz"), line);
        assertTrue(s.askNpc("pogoda", 1000).contains("Zapytaj o: przemytnicy"), "unknown word lists topics");
        assertTrue(pl.fawizja.pixel.game.Actions.apply(s, "npc:ask:wieza", 1000));
        assertTrue(s.log().latest(1).get(0).text().contains("pięć pięter"));
        s.teleport(TestData.world().spawn().translate(0, 6), 1000);
        if (s.npcNearby() == null) assertNull(s.askNpc("czesc", 1000));
    }

    @Test
    void theFirstStepOfTheDayPaysTheLoginBonusOnceAndTheStreakGrows() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        GameSession s = session(pc, List.of());
        long[] day = {500};
        s.setToday(() -> day[0]);
        long before = pc.bank().balance();
        s.update(1000, null);
        assertEquals(before + pl.fawizja.pixel.daily.LoginStreak.rewardGold(1, pc.level()), pc.bank().balance());
        assertTrue(s.log().latest(5).stream().anyMatch(e -> e.text().startsWith("Nagroda za wejście do gry (dzień 1 z 7)")));
        s.update(70_000, null);
        assertEquals(before + 300, pc.bank().balance(), "once a day");
        day[0] = 501;
        s.update(140_000, null);
        assertEquals(2, pc.loginStreak().streak(), "next day continues the streak");
        assertEquals(before + 300 + 500, pc.bank().balance());
    }
}
