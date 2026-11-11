package pl.fawizja.pixel.server;

import static org.junit.jupiter.api.Assertions.*;
import static pl.fawizja.pixel.server.NetworkSyncTest.await;

import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.net.NetClient;
import pl.fawizja.pixel.net.Protocol;

/** Jezyk klienta w HELLO (siódme pole): serwer odpowiada w tym jezyku, brak pola = polski (stary klient). */
class LanguageNetworkTest {

    PixelServer server;

    @BeforeEach
    void start() throws Exception {
        File saves = Files.createTempDirectory("pixel-saves").toFile();
        server = new PixelServer(0, saves);
        server.world().maxNewAccountsPerAddress = 100;
        server.start();
    }

    @AfterEach
    void stop() throws Exception {
        server.close();
    }

    /** Odmowa dla nieznanego miasta w podanym jezyku (lang null = bez siodmego pola). */
    String refusal(String name, String lang) throws Exception {
        String expected = server.fingerprint();
        try (NetClient c = new NetClient("127.0.0.1", server.port(), 3000, fp -> fp.equals(expected))) {
            if (lang == null) c.send("HELLO", String.valueOf(Protocol.VERSION), name, "KNIGHT", "tajne-" + name, "Atlantyda");
            else c.send("HELLO", String.valueOf(Protocol.VERSION), name, "KNIGHT", "tajne-" + name, "Atlantyda", lang);
            await("answer", () -> c.error() != null);
            return c.error();
        }
    }

    @Test
    void refusalsFollowTheLanguageSentInHello() throws Exception {
        String pl = refusal("Ala", null);
        String en = refusal("Ben", "en");
        String es = refusal("Cid", "es");
        assertTrue(pl.contains("miasto"), pl);
        assertTrue(en.toLowerCase().contains("town"), en);
        assertTrue(es.toLowerCase().contains("ciudad"), es);
        assertEquals(pl, refusal("Dan", "pl"));
        assertEquals(pl, refusal("Eli", "xx"), "unknown language code = Polish");
    }
}
