package pl.fawizja.pixel.server;

import static org.junit.jupiter.api.Assertions.*;
import static pl.fawizja.pixel.server.NetworkSyncTest.await;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.net.NetClient;
import pl.fawizja.pixel.save.SaveStore;
import pl.fawizja.pixel.world.ExploredMap;
import pl.fawizja.pixel.world.Position;

/**
 * Mgla mapy online: serwer odkrywa mape z wlasnych pozycji postaci i zapisuje ja w zapisie postaci; po zalogowaniu
 * wysyla ja raz (FOG), a SELF co 250 ms jej nie zawiera.
 */
class MapFogNetworkTest {

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

    NetClient connect() throws IOException {
        String expected = server.fingerprint();
        return new NetClient("127.0.0.1", server.port(), 3000, fp -> fp.equals(expected));
    }

    @Test
    void theServerPersistsWhatTheCharacterSawAndSendsItAfterLogin() throws Exception {
        Position start;
        Position end;
        try (NetClient c = connect()) {
            c.hello("Wanda", "KNIGHT", "tajne-Wanda");
            await("self", () -> c.selfSave() != null && c.frame() != null && c.frame().self() != null);
            assertFalse(c.selfSave().contains("fog="), "SELF carries no map");
            ExploredMap first = new ExploredMap();
            NetworkSyncTest.awaitMessage(c, "fog", m -> {
                if (m[0].equals("FOG")) first.merge(m[1]);
                return m[0].equals("FOG");
            });
            start = c.frame().self();
            assertTrue(first.isExplored(start), "a new character knows the spawn view");
            c.send("MOVE", "SOUTH");
            await("walked", () -> c.frame().self().y() >= start.y() + 2);
            c.send("MOVE", "-");
            Thread.sleep(300);
            end = c.frame().self();
        }
        await("saved", () -> server.world().players().isEmpty() && server.world().saved("Wanda"));
        PlayerCharacter saved = new SaveStore(saves).read("player_wanda", NetworkSyncTest.CODEC::decode).value();
        ExploredMap fog = saved.explored();
        assertTrue(fog.isExplored(end.x(), end.y() + ExploredMap.VIEW_Y, end.z()), "the server revealed the walk " + end);
        assertTrue(fog.isExplored(start), "and the start");
        assertFalse(fog.isExplored(start.x() + 40, start.y(), start.z()), "nothing far away");

        try (NetClient c = connect()) {
            c.hello("Wanda", "KNIGHT", "tajne-Wanda");
            ExploredMap again = new ExploredMap();
            NetworkSyncTest.awaitMessage(c, "fog again", m -> {
                if (m[0].equals("FOG")) again.merge(m[1]);
                return m[0].equals("FOG") && again.isExplored(end.x(), end.y() + ExploredMap.VIEW_Y, end.z());
            });
            assertEquals(fog.count(start.z()), again.count(start.z()) , "the whole saved map arrives");
        }
    }
}
