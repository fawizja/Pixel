package pl.fawizja.pixel.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.net.NetClient;
import pl.fawizja.pixel.net.RemoteEntity;
import pl.fawizja.pixel.world.Position;

/** Prawdziwa synchronizacja przez TCP na localhost: dwoch klientow, jeden serwer. */
class NetworkSyncTest {

    PixelServer server;
    File saves;
    static final CharacterCodec CODEC = new CharacterCodec(ItemRegistry.loadDefault());

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

    static void await(String what, BooleanSupplier cond) throws InterruptedException {
        long end = System.currentTimeMillis() + 10_000;
        while (!cond.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("timeout waiting for " + what);
            Thread.sleep(20);
        }
    }

    static PlayerCharacter self(NetClient c) throws Exception {
        return CODEC.decode(c.selfSave());
    }

    static RemoteEntity seen(NetClient c, String name) {
        NetClient.Frame f = c.frame();
        if (f == null) return null;
        for (RemoteEntity e : f.players()) if (e.name().equals(name)) return e;
        return null;
    }

    @Test
    void twoPlayersSeeEachOtherMoveAndChat() throws Exception {
        try (NetClient alice = new NetClient("127.0.0.1", server.port(), 3000);
             NetClient bob = new NetClient("127.0.0.1", server.port(), 3000)) {
            alice.hello("Alice", "PALADIN");
            bob.hello("Bob", "DRUID");
            await("welcome", () -> alice.playerId() > 0 && bob.playerId() > 0);
            await("self state", () -> alice.selfSave() != null && bob.selfSave() != null);
            assertEquals("Alice", self(alice).name());
            assertEquals(8, self(alice).level());
            await("bob sees alice", () -> seen(bob, "Alice") != null);
            Position start = seen(bob, "Alice").position();

            // Alice idzie na poludnie (ze swiatyni do drzwi); Bob widzi zmiane pozycji z serwera
            alice.send("MOVE", "SOUTH");
            await("alice moved", () -> {
                RemoteEntity a = seen(bob, "Alice");
                return a != null && a.position().y() >= start.y() + 2;
            });
            alice.send("MOVE", "-");
            await("alice self matches", () -> {
                try {
                    return self(alice).position().equals(seen(bob, "Alice").position());
                } catch (Exception e) {
                    return false;
                }
            });

            bob.send("CHAT", "Czesc Alice!");
            await("chat relayed", () -> {
                String[] m;
                while ((m = alice.poll()) != null) {
                    if (m[0].equals("CHAT") && m[1].equals("Bob") && m[2].equals("Czesc Alice!")) return true;
                }
                return false;
            });
        }
        await("players left and saved", () -> server.world().players().isEmpty());
        assertTrue(server.world().saved("Alice") && server.world().saved("Bob"));
    }

    @Test
    void serverRejectsDuplicateLoginsAndBadVersionsAndRestoresSavedCharacters() throws Exception {
        try (NetClient a = new NetClient("127.0.0.1", server.port(), 3000)) {
            a.hello("Carol", "SORCERER");
            await("welcome", () -> a.playerId() > 0);
            try (NetClient dup = new NetClient("127.0.0.1", server.port(), 3000)) {
                dup.hello("carol", "MONK");
                await("duplicate rejected", () -> dup.error() != null);
                assertTrue(dup.error().contains("juz w grze"));
            }
            try (NetClient old = new NetClient("127.0.0.1", server.port(), 3000)) {
                old.send("HELLO", "0", "Dave", "MONK");
                await("version rejected", () -> old.error() != null);
            }
            a.send("ACT", "torch");
            await("torch lit", () -> {
                try {
                    return self(a).isTorchLit();
                } catch (Exception e) {
                    return false;
                }
            });
        }
        await("carol left", () -> server.world().players().isEmpty());
        try (NetClient again = new NetClient("127.0.0.1", server.port(), 3000)) {
            again.hello("Carol", "MONK"); // profesja z zapisu, nie z prosby
            await("restored", () -> again.selfSave() != null);
            assertEquals("SORCERER", self(again).vocation().name());
        }
    }

    @Test
    void monstersAreSharedBetweenPlayers() throws Exception {
        try (NetClient a = new NetClient("127.0.0.1", server.port(), 3000);
             NetClient b = new NetClient("127.0.0.1", server.port(), 3000)) {
            a.hello("Erin", "PALADIN");
            b.hello("Finn", "MONK");
            await("welcome", () -> a.playerId() > 0 && b.playerId() > 0);
            // obaj teleportowani (na serwerze) do pola z wilkami: widza te same potwory o tych samych id
            Position field = new Position(58, 75, 0);
            server.enqueueTest(() -> {
                for (ServerPlayer p : server.world().players()) p.session.teleport(field, server.now());
            });
            await("both see monsters", () -> a.frame() != null && b.frame() != null
                    && !a.frame().monsters().isEmpty() && !b.frame().monsters().isEmpty()
                    && seen(a, "Finn") != null);
            RemoteEntity ma = a.frame().monsters().get(0);
            assertTrue(b.frame().monsters().stream().anyMatch(m -> m.id() == ma.id() && m.key().equals(ma.key())));
        }
    }
}
