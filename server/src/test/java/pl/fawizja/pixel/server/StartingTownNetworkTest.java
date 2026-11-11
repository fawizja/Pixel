package pl.fawizja.pixel.server;

import static org.junit.jupiter.api.Assertions.*;
import static pl.fawizja.pixel.server.NetworkSyncTest.await;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.net.NetClient;
import pl.fawizja.pixel.world.Town;

/** Miasto startowe nowego konta wysylane w HELLO: serwer sprawdza nazwe, stara postac i stary klient bez zmian. */
class StartingTownNetworkTest {

    PixelServer server;
    File saves;

    @BeforeEach
    void start() throws Exception {
        saves = Files.createTempDirectory("pixel-saves").toFile();
        server = new PixelServer(0, saves);
        server.world().maxNewAccountsPerAddress = 100; // kilka nowych kont z localhost w jednym tescie
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

    /** Loguje (miasto null = stary klient bez szostego pola) i zwraca postac z serwera albo null z bledem w err[0]. */
    PlayerCharacter login(String name, String town, String[] err) throws Exception {
        try (NetClient c = connect()) {
            if (town == null) c.hello(name, "KNIGHT", "tajne-" + name);
            else c.send("HELLO", String.valueOf(pl.fawizja.pixel.net.Protocol.VERSION), name, "KNIGHT", "tajne-" + name, town);
            await("answer", () -> c.selfSave() != null || c.error() != null);
            if (err != null) err[0] = c.error();
            PlayerCharacter pc = c.selfSave() == null ? null : NetworkSyncTest.self(c);
            c.close();
            await("left", () -> server.world().find(name) == null);
            return pc;
        }
    }

    @Test
    void aNewAccountStartsInTheChosenTownForEveryTown() throws Exception {
        String[] names = {"Alfa", "Beta", "Gamma", "Delta", "Epsilon", "Zeta", "Eta", "Theta"};
        int i = 0;
        for (Town t : server.world().world.towns()) {
            if (!t.starting()) continue;                  // Durnkar: tylko do zamieszkania
            PlayerCharacter pc = login(names[i++], t.name(), null);
            assertNotNull(pc, t.name());
            assertEquals(t.name().toLowerCase(Locale.ROOT), pc.homeTown());
            assertTrue(pc.position().distanceTo(t.temple()) <= 2, t.name() + " temple " + t.temple() + " vs " + pc.position());
        }
    }

    @Test
    void aPremiumTownIsRefusedLikeAnUnknownOne() throws Exception {
        for (String premium : new String[] {"Nerys", "Valmir", "Selvaran", "Durnkar"}) {
            String[] err = new String[1];
            assertNull(login("Premium" + premium, premium, err), premium);
            assertNotNull(err[0], premium);
            assertFalse(new File(new File(saves, "accounts"), "premium" + premium.toLowerCase(Locale.ROOT) + ".acc").exists(), premium);
        }
    }

    @Test
    void anUnknownTownIsRejectedAndCreatesNoAccount() throws Exception {
        String[] err = new String[1];
        assertNull(login("Zbloto", "Atlantyda", err));
        assertNotNull(err[0]);
        assertTrue(err[0].contains("miasto"), err[0]);
        assertFalse(new File(new File(saves, "accounts"), "zbloto.acc").exists(), "no account for a refused town");
    }

    @Test
    void oldClientsAndExistingCharactersKeepTheirTown() throws Exception {
        String def = pl.fawizja.pixel.config.StartingConfig.loadDefault().startTown();
        PlayerCharacter old = login("Stary", null, null);
        assertEquals(def, old.homeTown(), "old client without a town: default town");
        assertEquals(def, login("Kreska", "-", null).homeTown(), "\"-\" = default town");
        // istniejaca postac: miasto w HELLO jest pomijane (nawet nieznane)
        PlayerCharacter again = login("Stary", "frostholm", null);
        assertEquals(def, again.homeTown());
        assertNotNull(login("Stary", "Atlantyda", null), "existing account ignores the town field");
    }
}
