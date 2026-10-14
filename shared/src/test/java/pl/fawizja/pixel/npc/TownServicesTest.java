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
}
