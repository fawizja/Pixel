package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.Blessing;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.movement.MoveResult;
import pl.fawizja.pixel.movement.MovementSystem;
import pl.fawizja.pixel.npc.Npc;
import pl.fawizja.pixel.npc.NpcRegistry;
import pl.fawizja.pixel.npc.NpcRole;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Kaplice blogoslawienstw (tools/mapgen/chapels.py): szesc malych kaplic z mnichami - trzy na kontynencie glownym
 * (dojscie pieszo z Varden, konto darmowe) i trzy w ziemiach premium (osiagalne tylko z tych ziem, nie z Varden).
 */
class BlessingChapelsTest {

    static final String[] MAIN = {"Varden", "Frostholm", "Qasirah"};
    static final String[] PREMIUM = {"Valmir", "Selvaran", "Nerys"};
    static WorldMap world;
    static NpcRegistry npcs;

    @BeforeAll
    static void load() throws Exception {
        world = TestData.world();
        npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, world, ShopRegistry.loadDefault(TestData.ITEMS));
    }

    /** Wszystko, co postac na poziomie 1000 osiaga pieszo (drzwi, schody, drabiny, liny) - bez statkow i wozow. */
    static Set<Position> explore(Position start) {
        MovementSystem ms = new MovementSystem(world, TestData.ITEMS.byKey("rope").id());
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.addExperience(Balance.experienceForLevel(1000) - pc.experience());
        Set<Position> seen = new HashSet<>();
        ArrayDeque<Position> q = new ArrayDeque<>();
        seen.add(start);
        q.add(start);
        while (!q.isEmpty()) {
            Position p = q.poll();
            for (Direction d : Direction.values()) {
                pc.setPosition(p);
                if (TestData.stepOpeningDoors(world, ms, pc, d).moved() && seen.add(pc.position())) q.add(pc.position());
            }
            pc.setPosition(p);
            if (ms.use(pc) == MoveResult.CHANGED_FLOOR && seen.add(pc.position())) q.add(pc.position());
        }
        return seen;
    }

    /** Mnich obok jest do dotkniecia: ktores z czterech pol obok niego osiagalne (NPC blokuje wlasne pole). */
    private static boolean reachable(Set<Position> seen, Npc n) {
        Position p = n.position();
        return seen.contains(p.translate(1, 0)) || seen.contains(p.translate(-1, 0)) || seen.contains(p.translate(0, 1)) || seen.contains(p.translate(0, -1));
    }

    private static Npc monk(String town) {
        for (Blessing b : Blessing.values()) {
            Npc n = npcs.get(b.sellerKey());
            if (n != null && n.town().equals(town)) return n;
        }
        fail("no monk in " + town);
        return null;
    }

    @Test
    void sixMonksOneNextToEachTownAndEachSellingADifferentBlessing() {
        long count = npcs.all().stream().filter(n -> n.role() == NpcRole.BLESSER).count();
        assertEquals(6, count);
        Set<Blessing> sold = new HashSet<>();
        for (String t : MAIN) sold.add(Blessing.soldBy(monk(t).key()));
        for (String t : PREMIUM) sold.add(Blessing.soldBy(monk(t).key()));
        assertEquals(6, sold.size(), "all five regular blessings and Twist of Fate, one per monk");
        for (String t : MAIN) assertFalse(Blessing.soldBy(monk(t).key()).premium(), t + ": free account");
        for (String t : PREMIUM) assertTrue(Blessing.soldBy(monk(t).key()).premium(), t + ": premium account");
    }

    @Test
    void threeMonksOnTheMainContinentAreReachableOnFootFromVarden() {
        Set<Position> fromVarden = explore(world.town("Varden").temple());
        for (String t : MAIN) {
            Npc n = monk(t);
            assertTrue(reachable(fromVarden, n), t + " monk " + n.key() + " at " + n.position() + " must be reachable on foot from Varden");
            assertFalse(world.town(t).premium());
        }
    }

    @Test
    void threeMonksInThePremiumLandsAreCutOffFromTheMainContinent() {
        Set<Position> fromVarden = explore(world.town("Varden").temple());
        Set<Position> fromValmir = explore(world.town("Valmir").temple());
        Set<Position> fromNerys = explore(world.town("Nerys").temple());
        for (String t : PREMIUM) {
            Npc n = monk(t);
            assertTrue(world.town(t).premium(), t);
            assertFalse(reachable(fromVarden, n), n.key() + " must not be walkable from Varden (premium separation)");
            assertTrue(reachable(t.equals("Nerys") ? fromNerys : fromValmir, n), n.key() + " must be reachable from its own land");
        }
        assertFalse(fromVarden.contains(world.town("Valmir").temple()), "the chapels did not bridge the premium lands");
    }

    @Test
    void everyChapelIsASmallProtectedTempleNearItsTown() {
        for (String t : MAIN) check(t);
        for (String t : PREMIUM) check(t);
    }

    private static void check(String t) {
        Npc n = monk(t);
        Position temple = world.town(t).temple();
        int d = Math.max(Math.abs(n.position().x() - temple.x()), Math.abs(n.position().y() - temple.y()));
        assertTrue(d >= 15 && d <= 50, t + ": chapel " + d + " tiles from the temple");
        assertEquals(0, n.position().z());
        assertTrue(world.isProtectionZone(n.position()), t + ": protected chapel");
        // wewnatrz budynku: nad nim sciana w czterech kierunkach w zasiegu 5 pol, altarz w poblizu
        FloorMap f = world.floor(0);
        boolean altar = false;
        int walls = 0;
        for (int dy = -5; dy <= 5; dy++) {
            for (int dx = -5; dx <= 5; dx++) {
                int obj = f.get(Layer.OBJECT, n.position().x() + dx, n.position().y() + dy);
                if (obj > 0 && TestData.TILES.get(obj).key().equals("altar")) altar = true;
                int wall = f.get(Layer.WALL, n.position().x() + dx, n.position().y() + dy);
                if (wall > 0) walls++;
            }
        }
        assertTrue(altar, t + ": an altar in the chapel");
        assertTrue(walls >= 18, t + ": walls around the monk: " + walls);
        assertTrue(n.greeting().length() > 20);
    }

    @Test
    void chapelsStayApartFromEachOtherAndFromTownNpcs() {
        for (Npc a : npcs.all()) {
            if (a.role() != NpcRole.BLESSER) continue;
            for (Npc b : npcs.all()) {
                if (a == b || b.position().z() != 0) continue;
                int d = Math.max(Math.abs(a.position().x() - b.position().x()), Math.abs(a.position().y() - b.position().y()));
                assertTrue(d >= 6, a.key() + " too close to " + b.key() + ": " + d);
            }
        }
    }
}
