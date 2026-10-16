package pl.fawizja.pixel.npc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.economy.Money;
import pl.fawizja.pixel.game.Actions;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.quest.QuestRegistry;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldLayout;
import pl.fawizja.pixel.world.WorldMap;

class TravelTest {

    static WorldMap world;
    static NpcRegistry npcs;
    static ShopRegistry shops;
    static QuestRegistry quests;

    @BeforeAll
    static void load() throws Exception {
        world = TestData.world();
        shops = ShopRegistry.loadDefault(TestData.ITEMS);
        npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, world, shops);
        quests = QuestRegistry.loadDefault(TestData.ITEMS, MonsterRegistry.loadDefault(TestData.ITEMS));
    }

    GameSession session(PlayerCharacter pc) {
        GameSession s = new GameSession(TestData.ITEMS, world, pc,
                new MonsterManager(world, null, List.of(), new Random(1)), new Random(2), 8);
        s.setTownServices(npcs, shops, quests);
        return s;
    }

    @Test
    void priceGrowsWithDistanceAndHasAMinimum() {
        assertEquals(Travel.MIN_PRICE, Travel.price(new Position(0, 0, 0), new Position(3, 0, 0)));
        assertEquals(300, Travel.price(new Position(0, 0, 0), new Position(60, 40, 0)));
        Npc varden = npcs.get("varden_carrier");
        List<Travel.Route> routes = Travel.routes(npcs, varden);
        assertEquals(3, routes.size(), "the other three towns");
        for (Travel.Route r : routes) assertNotEquals("Varden", r.to().town());
    }

    @Test
    void travelChargesTheBackpackAndArrivesNextToTheOtherCarrier() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        GameSession s = session(pc);
        Npc varden = npcs.get("varden_carrier");
        Npc frost = npcs.get("frost_carrier");
        s.teleport(varden.position().translate(-1, 0), 0);
        assertEquals(varden, s.npcNearby());
        long gold = s.gold();
        long price = Travel.price(varden.position(), frost.position());
        assertTrue(Actions.apply(s, "npc:travel:Frostholm", 0));
        assertEquals(gold - price, s.gold());
        assertTrue(pc.position().distanceTo(frost.position()) <= 1, pc.position().toString());
        assertTrue(world.isWalkable(pc.position()));
        // bez monet w plecaku nie jedzie (pieniadze w banku sie nie licza)
        Money.pay(pc.backpack(), s.gold());
        pc.bank().credit(10_000);
        Position here = pc.position();
        assertFalse(s.travel("Varden", 1));
        assertEquals(here, pc.position());
        assertFalse(s.travel("Frostholm", 1), "not to its own town");
    }

    @Test
    void noTravelRightAfterAFightWithAPlayer() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        GameSession s = session(pc);
        Npc varden = npcs.get("varden_carrier");
        s.teleport(varden.position().translate(-1, 0), 0);
        s.lockProtectionZonesUntil(60_000);
        assertFalse(s.travel("Qasirah", 1_000));
        assertTrue(s.travel("Qasirah", 61_000));
    }
}
