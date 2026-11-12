package pl.fawizja.pixel.training;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.game.Actions;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.npc.Npc;
import pl.fawizja.pixel.npc.NpcRole;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.training.OfflineTraining.Choice;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.FloorMap;
import pl.fawizja.pixel.world.Layer;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.Town;
import pl.fawizja.pixel.world.WorldMap;

/** Posagi treningowe w swiatyniach (mapgen trainers.py) i ich uzycie: wybor, premium, wylogowanie. */
class TrainingStatueTest {

    static Position statueNear(WorldMap world, Position temple, int radius) {
        int tid = world.tiles().byKey("training_statue").id();
        FloorMap f = world.floor(temple.z());
        Position best = null;
        for (int y = temple.y() - radius; y <= temple.y() + radius; y++) {
            for (int x = temple.x() - radius; x <= temple.x() + radius; x++) {
                if (f.get(Layer.OBJECT, x, y) != tid) continue;
                Position p = new Position(x, y, temple.z());
                if (best == null || p.distanceTo(temple) < best.distanceTo(temple)) best = p;
            }
        }
        return best;
    }

    static Position standBeside(WorldMap world, Position statue) {
        for (Direction d : Direction.values()) {
            Position n = statue.translate(d.dx, d.dy);
            if (!d.diagonal() && world.isWalkable(n)) return n;
        }
        return null;
    }

    /** Czy z pola start da sie dojsc (osiem kierunkow nie, tylko cztery - jak ruch gracza) do pola celu w kwadracie. */
    static boolean reachable(WorldMap world, Position from, Position to, int radius) {
        Set<Position> seen = new HashSet<>();
        ArrayDeque<Position> q = new ArrayDeque<>();
        q.add(from);
        seen.add(from);
        while (!q.isEmpty()) {
            Position p = q.poll();
            if (p.equals(to)) return true;
            for (Direction d : Direction.values()) {
                if (d.diagonal()) continue;
                Position n = p.translate(d.dx, d.dy);
                if (Math.abs(n.x() - from.x()) > radius || Math.abs(n.y() - from.y()) > radius) continue;
                if (world.isWalkable(n) && seen.add(n)) q.add(n);
            }
        }
        return false;
    }

    @Test
    void everyTownTempleHasAReachableTrainingStatue() {
        WorldMap world = TestData.world();
        assertFalse(world.towns().isEmpty());
        for (Town t : world.towns()) {
            Position statue = statueNear(world, t.temple(), 25);
            assertNotNull(statue, "training statue in the temple of " + t.name());
            Position stand = standBeside(world, statue);
            assertNotNull(stand, t.name() + ": a free tile beside the statue");
            assertTrue(reachable(world, t.temple(), stand, 40), t.name() + ": the statue can be reached from the temple point");
        }
    }

    @Test
    void statueIsAnObstacle() {
        WorldMap world = TestData.world();
        Town t = world.towns().get(0);
        Position statue = statueNear(world, t.temple(), 25);
        assertFalse(world.isWalkable(statue));
    }

    GameSession sessionBeside(PlayerCharacter pc, Town t) {
        WorldMap world = TestData.world();
        Position stand = standBeside(world, statueNear(world, t.temple(), 25));
        pc.setPosition(stand);
        GameSession s = new GameSession(TestData.ITEMS, world, pc, null, new Random(1), 1);
        try {
            s.setTownServices(pl.fawizja.pixel.npc.NpcRegistry.load(pl.fawizja.pixel.world.WorldLayout.WORLD_MAP, world, ShopRegistry.loadDefault(TestData.ITEMS)),
                    ShopRegistry.loadDefault(TestData.ITEMS), null);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return s;
    }

    @Test
    void touchingTheStatueExposesATrainerWindowForPremiumAccounts() {
        PlayerCharacter pc = TestData.newCharacterNamed("Trener");
        pc.setPremium(true);
        GameSession s = sessionBeside(pc, TestData.world().towns().get(0));
        Npc n = s.npcNearby();
        assertNotNull(n);
        assertEquals(Npc.TRAINING_STATUE, n.key());
        assertEquals(NpcRole.TRAINER, n.role());
        assertSame(n, s.npcNearby(), "stable instance keeps the window open");
    }

    @Test
    void choosingASkillLogsTheCharacterOutOnlyWithPremium() {
        PlayerCharacter pc = TestData.newCharacterNamed("Darmowy");
        pc.setPremium(false);
        GameSession s = sessionBeside(pc, TestData.world().towns().get(0));
        assertTrue(Actions.apply(s, "offtrain:SWORD", 1000), "the action is handled");
        assertNull(s.takeTrainingLogout(), "no premium: no logout");
        assertNull(pc.offlineTraining().choice());
        assertTrue(s.log().latest(1).get(0).text().toLowerCase().contains("premium"));

        pc.setPremium(true);
        assertTrue(Actions.apply(s, "offtrain:AXE", 2000));
        assertEquals(Choice.AXE, pc.offlineTraining().choice());
        assertEquals(Choice.AXE, s.takeTrainingLogout());
        assertNull(s.takeTrainingLogout(), "read once");
    }

    @Test
    void awayFromTheStatueOrInCombatNothingHappens() {
        PlayerCharacter pc = TestData.newCharacterNamed("Daleko");
        pc.setPremium(true);
        GameSession s = sessionBeside(pc, TestData.world().towns().get(0));
        pc.setLogoutBlockedUntil(60_000);
        Actions.apply(s, "offtrain:CLUB", 1000);
        assertNull(s.takeTrainingLogout(), "combat lock refuses");
        pc.setLogoutBlockedUntil(0);
        pc.setPosition(pc.position().translate(15, 0));
        Actions.apply(s, "offtrain:CLUB", 2000);
        assertNull(s.takeTrainingLogout(), "too far from the statue");
    }

    @Test
    void offlineSettlementOnLoginPutsTheReportInTheLog() {
        PlayerCharacter pc = TestData.newCharacterNamed("Wraca");
        pc.setPremium(true);
        GameSession s = sessionBeside(pc, TestData.world().towns().get(0));
        pc.offlineTraining().restore("43200,SWORD,1000000");
        long before = pc.skills().tries(Skill.SWORD);
        int lvl = pc.skills().level(Skill.SWORD);
        OfflineTraining.Report r = s.settleOfflineTraining(1_000_000L + 5 * 3_600_000L, 5000);
        assertTrue(r.trained());
        assertEquals(5 * 3_600_000L, r.trainedMs());
        assertTrue(pc.skills().level(Skill.SWORD) > lvl || pc.skills().tries(Skill.SWORD) > before);
        assertFalse(s.log().latest(5).isEmpty());
    }

    @Test
    void theBarRegeneratesWhileThePlayerIsOnline() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        pc.offlineTraining().setBarMs(0);
        GameSession s = new GameSession(TestData.ITEMS, TestData.varden(), pc);
        s.teleport(new Position(10, 12, 0), 0);
        for (int i = 1; i <= 10; i++) s.update(i * 1000L, null);
        long bar = pc.offlineTraining().barMs();
        assertTrue(bar >= 8_000 && bar <= 12_000, "about 10 s regenerated, was " + bar);
    }
}
