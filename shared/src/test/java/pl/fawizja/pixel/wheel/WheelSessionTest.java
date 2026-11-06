package pl.fawizja.pixel.wheel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.game.Actions;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.magic.ConsumableRegistry;
import pl.fawizja.pixel.magic.RecipeRegistry;
import pl.fawizja.pixel.magic.SpellRegistry;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.npc.NpcRegistry;
import pl.fawizja.pixel.quest.QuestRegistry;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldLayout;
import pl.fawizja.pixel.world.WorldMap;

/** Zmiany kola tylko w swiatyni (strefa ochronna przy kaplanie), akcje tekstowe jak online (ACT -> Actions.apply). */
class WheelSessionTest {
    static WorldMap world;
    static NpcRegistry npcs;
    static ShopRegistry shops;
    static QuestRegistry quests;
    /** Swiatynia Varden: kaplan Benedykt stoi na 501,498,0. */
    static final Position TEMPLE = new Position(498, 499, 0);

    @BeforeAll
    static void load() throws Exception {
        world = TestData.world();
        shops = ShopRegistry.loadDefault(TestData.ITEMS);
        npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, world, shops);
        quests = QuestRegistry.loadDefault(TestData.ITEMS, MonsterRegistry.loadDefault(TestData.ITEMS));
    }

    static GameSession session(PlayerCharacter pc, Position at) {
        MonsterManager mm = new MonsterManager(world, MonsterRegistry.loadDefault(TestData.ITEMS), Collections.emptyList(), new Random(3));
        GameSession s = new GameSession(TestData.ITEMS, world, pc, mm, new Random(5), 8, SpellRegistry.loadDefault(TestData.ITEMS),
                ConsumableRegistry.loadDefault(TestData.ITEMS), RecipeRegistry.loadDefault(TestData.ITEMS));
        s.setTownServices(npcs, shops, quests);
        s.teleport(at, 0);
        return s;
    }

    static String lastLog(GameSession s) {
        java.util.List<EventLog.Entry> e = s.log().latest(1);
        return e.isEmpty() ? "" : e.get(e.size() - 1).text();
    }

    @Test
    void pointsMoveOnlyInATemple() {
        PlayerCharacter pc = WheelFixtures.hero(Vocation.PALADIN, 300);
        GameSession field = session(pc, new Position(470, 483, 0));
        assertFalse(field.inTemple());
        assertTrue(Actions.apply(field, "wheel:add:G50:10", 1000));
        assertEquals(0, pc.wheel().spent());
        assertEquals(DestinyWheel.Result.NOT_IN_TEMPLE.message, lastLog(field));

        GameSession temple = session(pc, TEMPLE);
        assertTrue(temple.inTemple());
        assertTrue(Actions.apply(temple, "wheel:add:G50:50", 1000));
        assertTrue(Actions.apply(temple, "wheel:add:GT75:20", 1000));
        assertEquals(70, pc.wheel().spent());
        assertTrue(Actions.apply(temple, "wheel:remove:GT75:5", 1000));
        assertEquals(15, pc.wheel().points(WheelSlice.GREEN_TOP_75));
        assertTrue(Actions.apply(temple, "wheel:save:0", 1000));
        assertTrue(Actions.apply(temple, "wheel:reset", 1000));
        assertTrue(pc.wheel().isEmpty());
        assertTrue(Actions.apply(temple, "wheel:load:0", 1000));
        assertEquals(65, pc.wheel().spent());
        assertFalse(Actions.apply(temple, "wheel:bogus", 1000));
        assertTrue(Actions.apply(temple, "wheel:add:ZZ:1", 1000), "unknown slice is ignored, not a crash");
    }

    @Test
    void unpromotedCharactersGetARefusal() {
        PlayerCharacter pc = WheelFixtures.hero(Vocation.KNIGHT, 300);
        pc.setPromoted(false);
        GameSession temple = session(pc, TEMPLE);
        assertFalse(temple.wheelAdd("R50", 5, 1000));
        assertEquals(DestinyWheel.Result.NOT_ELIGIBLE.message, lastLog(temple));
    }

    @Test
    void wheelViewRowsAreInPolishAndMatchTheState() {
        PlayerCharacter pc = WheelFixtures.hero(Vocation.MONK, 400);
        WheelFixtures.fill(pc, "G");
        java.util.List<String[]> rows = WheelView.overview(pc, true);
        assertTrue(rows.stream().anyMatch(r -> r[0].startsWith("Zielona ćwiartka: 350")));
        assertTrue(rows.stream().anyMatch(r -> r[3].equals("wheel:q:G")));
        java.util.List<String[]> q = WheelView.quadrant(pc, WheelDomain.GREEN);
        assertTrue(q.stream().anyMatch(r -> r[3].equals("wheel:sel:G50")));
        java.util.List<String[]> card = WheelView.slice(pc, WheelSlice.GREEN_TOP_150, false, k -> k);
        assertTrue(card.stream().filter(r -> r[3].startsWith("wheel:add:")).allMatch(r -> r.length > 4 && r[4].equals("off")),
                "outside a temple adding is greyed out");
        for (java.util.List<String[]> l : java.util.List.of(rows, q, card, WheelView.perks(pc, k -> k), WheelView.presets(pc, true))) {
            for (String[] r : l) assertTrue(r[1].length() <= 70, "fits a row: " + r[1]);
        }
    }
}
