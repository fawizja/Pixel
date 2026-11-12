package pl.fawizja.pixel.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.net.NetClient;
import pl.fawizja.pixel.net.PingMeter;
import pl.fawizja.pixel.save.SaveStore;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.FloorMap;
import pl.fawizja.pixel.world.Layer;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/** Przez prawdziwy TCP: PING/PONG (kropka pingu), depozyt przy skrzyni i wylogowanie przy posagu treningowym. */
class TibiaUiNetworkTest {

    PixelServer server;
    File saves;

    @BeforeEach
    void start() throws Exception {
        saves = Files.createTempDirectory("pixel-saves").toFile();
        server = new PixelServer(0, saves);
        server.start();
    }

    @AfterEach
    void stop() throws Exception {
        server.close();
    }

    NetClient connect() throws Exception {
        String expected = server.fingerprint();
        return new NetClient("127.0.0.1", server.port(), 3000, fp -> fp.equals(expected));
    }

    static Position objectNear(WorldMap world, Position from, String key, int radius) {
        int tid = world.tiles().byKey(key).id();
        FloorMap f = world.floor(from.z());
        Position best = null;
        for (int y = from.y() - radius; y <= from.y() + radius; y++) {
            for (int x = from.x() - radius; x <= from.x() + radius; x++) {
                if (f.get(Layer.OBJECT, x, y) != tid) continue;
                Position p = new Position(x, y, from.z());
                if (best == null || p.distanceTo(from) < best.distanceTo(from)) best = p;
            }
        }
        return best;
    }

    static Position standBeside(WorldMap world, Position target) {
        for (Direction d : Direction.values()) {
            Position n = target.translate(d.dx, d.dy);
            if (world.isWalkable(n)) return n;
        }
        return null;
    }

    @Test
    void pingGetsAnImmediatePongAndFeedsTheMeter() throws Exception {
        try (NetClient c = connect()) {
            c.hello("Pinger", "PALADIN", "tajne-Pinger");
            NetworkSyncTest.await("welcome", () -> c.playerId() > 0);
            assertEquals(-1, c.pingMeter().millis());
            c.ping();
            NetworkSyncTest.await("pong", () -> c.pingMeter().millis() >= 0);
            assertEquals(PingMeter.Quality.GOOD, c.pingMeter().quality(System.currentTimeMillis()), "localhost is well under 100 ms");
            long first = c.pingMeter().millis();
            assertTrue(first < PingMeter.GOOD_BELOW_MS, "rtt " + first);
            // pingIfDue nie spamuje: tuz po wyslaniu nic nowego
            c.pingIfDue();
            assertFalse(c.pingMeter().due(System.currentTimeMillis()));
        }
    }

    @Test
    void depotItemsMoveOnTheServerAndStayAfterReconnect() throws Exception {
        WorldMap world = server.world().world;
        Position start;
        Position locker;
        try (NetClient c = connect()) {
            c.hello("Skarb", "PALADIN", "tajne-Skarb");
            NetworkSyncTest.await("self state", () -> c.selfSave() != null);
            start = NetworkSyncTest.self(c).position();
            locker = objectNear(world, start, "depot_locker", 120);
            assertNotNull(locker);
            Position stand = standBeside(world, locker);
            server.enqueueTest(() -> {
                for (ServerPlayer p : server.world().players()) {
                    if (p.name().equals("Skarb")) p.session.teleport(stand, server.now());
                }
            });
            NetworkSyncTest.await("teleported", () -> {
                try {
                    return NetworkSyncTest.self(c).position().equals(stand);
                } catch (Exception e) {
                    return false;
                }
            });
            PlayerCharacter me = NetworkSyncTest.self(c);
            int slot = -1;
            for (int i = 0; i < me.backpack().size(); i++) if (me.backpack().get(i) != null) { slot = i; break; }
            assertTrue(slot >= 0, "the starter kit has something to store");
            int itemId = me.backpack().get(slot).itemId();
            int count = me.backpack().get(slot).count();
            c.send("ACT", "depot_move:B:" + slot + ":D:42");
            final int fSlot = slot;
            NetworkSyncTest.await("moved to the depot", () -> {
                try {
                    PlayerCharacter s = NetworkSyncTest.self(c);
                    return s.depot().get(42) != null && s.backpack().get(fSlot) == null;
                } catch (Exception e) {
                    return false;
                }
            });
            assertEquals(itemId, NetworkSyncTest.self(c).depot().get(42).itemId());
            assertEquals(count, NetworkSyncTest.self(c).depot().get(42).count());
        }
        NetworkSyncTest.await("saved", () -> server.world().players().isEmpty() && server.world().saved("Skarb"));
        PlayerCharacter saved = new SaveStore(saves).read("player_skarb", NetworkSyncTest.CODEC::decode).value();
        assertNotNull(saved.depot().get(42), "the depot is part of the save");
        try (NetClient c = connect()) {
            c.hello("Skarb", "PALADIN", "tajne-Skarb");
            NetworkSyncTest.await("self again", () -> c.selfSave() != null);
            assertNotNull(NetworkSyncTest.self(c).depot().get(42), "and comes back after logging in again");
        }
    }

    @Test
    void statueChoiceNeedsPremiumThenLogsOutWithASave() throws Exception {
        WorldMap world = server.world().world;
        try (NetClient c = connect()) {
            c.hello("Biegacz", "KNIGHT", "tajne-Biegacz");
            NetworkSyncTest.await("self state", () -> c.selfSave() != null);
            Position start = NetworkSyncTest.self(c).position();
            Position statue = objectNear(world, start, "training_statue", 40);
            assertNotNull(statue, "a statue in the starting temple");
            Position stand = standBeside(world, statue);
            server.enqueueTest(() -> {
                for (ServerPlayer p : server.world().players()) {
                    if (p.name().equals("Biegacz")) p.session.teleport(stand, server.now());
                }
            });
            NetworkSyncTest.await("teleported", () -> {
                try {
                    return NetworkSyncTest.self(c).position().equals(stand);
                } catch (Exception e) {
                    return false;
                }
            });
            // bez premium: komunikat odmowy, brak wylogowania
            c.send("ACT", "offtrain:SWORD");
            NetworkSyncTest.awaitMessage(c, "refusal", m -> m[0].equals("LOG") && m[2].toLowerCase().contains("premium"));
            server.enqueueTest(() -> {
                for (ServerPlayer p : server.world().players()) {
                    if (p.name().equals("Biegacz")) p.session.character().setPremium(true);
                }
            });
            NetworkSyncTest.await("premium", () -> {
                try {
                    return NetworkSyncTest.self(c).isPremium();
                } catch (Exception e) {
                    return false;
                }
            });
            c.send("ACT", "offtrain:SWORD");
            NetworkSyncTest.awaitMessage(c, "logout order", m -> m[0].equals("TRAINLOGOUT") && m[1].equals("SWORD"));
        }
        NetworkSyncTest.await("saved", () -> server.world().players().isEmpty() && server.world().saved("Biegacz"));
        PlayerCharacter saved = new SaveStore(saves).read("player_biegacz", NetworkSyncTest.CODEC::decode).value();
        assertEquals(pl.fawizja.pixel.training.OfflineTraining.Choice.SWORD, saved.offlineTraining().choice(), "the choice is saved for the next login");
    }
}
