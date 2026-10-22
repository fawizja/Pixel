package pl.fawizja.pixel.npc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.economy.Money;
import pl.fawizja.pixel.game.Actions;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/** Statki: kapitanowie w portach i na wyspach, trasy z ships.csv w obie strony, rejs za monety z plecaka. */
class ShipTravelTest {

    static final WorldMap WORLD = TestData.world();

    static NpcRegistry npcs() throws Exception {
        return NpcRegistry.load("world", WORLD, ShopRegistry.loadDefault(TestData.ITEMS));
    }

    @Test
    void portsAndIslandsHaveCaptainsWithRoutesBothWays() throws Exception {
        NpcRegistry npcs = npcs();
        List<Npc> captains = npcs.all().stream().filter(n -> n.role() == NpcRole.CAPTAIN).toList();
        Set<String> places = captains.stream().map(Npc::town).collect(Collectors.toSet());
        for (String p : List.of("Selvaran", "Qasirah", "Przystań Wschodnia", "Wyspa Koralowa", "Wyspa Piratów",
                "Wyspa Mgieł", "Lodowa Wyspa", "Wyspa Wulkaniczna")) {
            assertTrue(places.contains(p), p);
        }
        for (Npc c : captains) {
            List<Travel.Route> routes = Travel.routes(npcs, c);
            assertFalse(routes.isEmpty(), c.key() + " has no route");
            for (Travel.Route r : routes) {
                assertEquals(NpcRole.CAPTAIN, r.to().role());
                assertTrue(Travel.routes(npcs, r.to()).stream().anyMatch(b -> b.to() == c), "no way back " + r.to().key());
                assertTrue(r.price() >= Travel.MIN_PRICE);
            }
        }
        // wyspy wschodu tylko z Przystani Wschodniej, Selvaran laczy zachod
        assertTrue(Travel.routes(npcs, npcs.get("ice_captain")).stream().allMatch(r -> r.to().key().equals("east_captain")));
        assertTrue(Travel.routes(npcs, npcs.get("selv_captain")).stream().anyMatch(r -> r.to().key().equals("qas_captain")));
    }

    @Test
    void shipsFileRejectsRoutesToNonCaptains() throws Exception {
        NpcRegistry npcs = npcs();
        assertThrows(java.io.IOException.class, () -> npcs.withShips(List.of("selv_captain;selv_banker")));
        assertThrows(java.io.IOException.class, () -> npcs.withShips(List.of("selv_captain;nobody")));
        assertThrows(java.io.IOException.class, () -> npcs.withShips(List.of("selv_captain;qas_captain", "qas_captain;selv_captain")));
    }

    @Test
    void sailingToAnIslandAndBackCostsCoinsFromTheBackpack() throws Exception {
        NpcRegistry npcs = npcs();
        Npc selv = npcs.get("selv_captain");
        Npc coral = npcs.get("coral_captain");
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        MonsterManager mm = new MonsterManager(WORLD, MonsterRegistry.loadDefault(TestData.ITEMS), List.of(), new Random(1));
        GameSession s = new GameSession(TestData.ITEMS, WORLD, pc, mm, new Random(1), 8);
        s.setTownServices(npcs, ShopRegistry.loadDefault(TestData.ITEMS), null);
        s.teleport(selv.position().translate(0, -1), 0);
        long price = Travel.price(selv.position(), coral.position());
        long before = Money.totalValue(pc.backpack());
        assertTrue(before >= price, "starter coins " + before);

        assertTrue(Actions.apply(s, "npc:travel:Wyspa Koralowa", 1000));
        assertTrue(pc.position().distanceTo(coral.position()) <= 1, "at the coral dock " + pc.position());
        assertEquals(before - price, Money.totalValue(pc.backpack()));

        assertFalse(s.travel("Lodowa Wyspa", 2000), "no ship from the coral island to the ice island");
        assertTrue(s.travel("Selvaran", 3000));
        assertTrue(pc.position().distanceTo(selv.position()) <= 1);
        Position harbour = pc.position();
        assertEquals(0, harbour.z());
    }
}
