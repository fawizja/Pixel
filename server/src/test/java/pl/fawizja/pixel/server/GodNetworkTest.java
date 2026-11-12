package pl.fawizja.pixel.server;

import static org.junit.jupiter.api.Assertions.*;
import static pl.fawizja.pixel.server.NetworkSyncTest.await;
import static pl.fawizja.pixel.server.NetworkSyncTest.awaitMessage;
import static pl.fawizja.pixel.server.NetworkSyncTest.seen;

import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.net.NetClient;
import pl.fawizja.pixel.world.Position;

/** Poziomy dostepu i polecenia GOD przez prawdziwa siec: serwer sam liczy poziom, klient go nie przesyla. */
class GodNetworkTest {

    PixelServer server;
    File saves;

    @BeforeEach
    void start() throws Exception {
        saves = Files.createTempDirectory("pixel-god").toFile();
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

    String console(String... cmd) throws Exception {
        String[] answer = new String[1];
        server.enqueueTest(() -> answer[0] = server.world().moderate(cmd, "konsola", server.now()));
        await("console answer", () -> answer[0] != null);
        return answer[0];
    }

    static void said(NetClient c, String what, String contains) throws Exception {
        awaitMessage(c, what, m -> m[0].equals("LOG") && m[2].contains(contains));
    }

    String adminLog() {
        File f = new File(saves, "admin.log");
        try {
            return f.isFile() ? Files.readString(f.toPath()) : "";
        } catch (java.io.IOException e) {
            return "";
        }
    }

    /** Pole przechodnie poza strefa ochronna (potwory nie wchodza do swiatyn), z wolnym sasiadem. */
    Position outsideProtection() {
        pl.fawizja.pixel.world.WorldMap w = server.world().world;
        Position t = w.towns().get(0).temple();
        for (int r = 4; r < 60; r++) {
            for (int dx = -r; dx <= r; dx++) {
                Position q = t.translate(dx, r);
                if (w.isWalkable(q) && !w.isProtectionZone(q) && w.isWalkable(q.translate(1, 0)) && !w.isProtectionZone(q.translate(1, 0))
                        && w.isWalkable(q.translate(0, 1)) && !w.isProtectionZone(q.translate(0, 1))
                        && server.world().monsters.monsterAt(q) == null) return q;
            }
        }
        throw new IllegalStateException("no free tile");
    }

    @Test
    void playersAndGameMastersAreRefusedGodCommandsAndNothingChanges() throws Exception {
        try (NetClient ola = connect(); NetClient ula = connect()) {
            ola.hello("Ola", "KNIGHT", "tajne-Ola");
            ula.hello("Ula", "DRUID", "tajne-Ula");
            await("welcome", () -> ola.playerId() > 0 && ula.playerId() > 0);
            ola.send("GOD", "ITEM", "rope");
            said(ola, "refused item", "Brak uprawnień");
            ola.send("GOD", "LEVEL", "500");
            said(ola, "refused level", "Brak uprawnień");
            ola.send("GOD", "GHOST");
            said(ola, "refused ghost", "Brak uprawnień");
            ola.send("GOD", "NIE_ISTNIEJE");
            said(ola, "unknown", "Nieznane polecenie");
            assertEquals(8, server.world().find("Ola").session.character().level());
            assertFalse(server.world().find("Ola").session.character().isGhost());
            await("refusals logged", () -> adminLog().contains("Ola") && adminLog().contains("REFUSED"));

            // game master z admins.txt: moderacja tak, polecenia GOD nie
            assertTrue(console("ADMIN", "Ula").contains("Ula"));
            ula.send("GOD", "MONEY", "100k");
            said(ula, "gm refused money", "Brak uprawnień");
            ula.send("GOD", "HELP");
            said(ula, "gm help", "/ban Imię");
            assertFalse(server.world().find("Ula").session.character().isGodMode());
            ula.send("ADMIN", "GOD", "Ula");
            said(ula, "gm cannot grant god", "konsola");
            ula.send("ADMIN", "SHUTDOWN", "5");
            said(ula, "gm cannot shut down", "konsol");
            assertFalse(new File(saves, "gods.txt").exists(), "nobody was granted GOD");
        }
    }

    @Test
    void consoleGrantsGodAndEveryMainCommandWorksOnline() throws Exception {
        try (NetClient ola = connect(); NetClient ula = connect()) {
            ola.hello("Ola", "KNIGHT", "tajne-Ola");
            ula.hello("Ula", "DRUID", "tajne-Ula");
            await("welcome", () -> ola.playerId() > 0 && ula.playerId() > 0);
            await("both see each other", () -> seen(ula, "Ola") != null && seen(ola, "Ula") != null);
            assertNull(seen(ula, "Ola").access(), "a regular player has no tag");

            assertTrue(console("GOD", "Ola").contains("GOD"));
            assertEquals("Ola", Files.readAllLines(new File(saves, "gods.txt").toPath()).get(0));
            awaitMessage(ola, "access line", m -> m[0].equals("ACCESS") && m[1].equals("GOD") && m[2].equals("0"));
            await("tag visible to others", () -> seen(ula, "Ola") != null && "GOD".equals(seen(ula, "Ola").access()));
            ola.send("GOD", "HELP");
            said(ola, "god help", "/flag [Imię,] flaga [off]");

            PlayerCharacter god = server.world().find("Ola").session.character();
            // przedmioty, zloto, poziom, umiejetnosci, leczenie
            int rope = server.world().items.byKey("rope").id();
            int ropes = god.backpack().count(rope);
            ola.send("GOD", "ITEM", "rope 2");
            said(ola, "item created", "Stworzono: 2x");
            await("2 ropes", () -> god.backpack().count(rope) == ropes + 2);
            long gold = pl.fawizja.pixel.economy.Money.totalValue(god.backpack());
            ola.send("GOD", "MONEY", "5k");
            await("gold", () -> pl.fawizja.pixel.economy.Money.totalValue(god.backpack()) >= gold + 5000 - 1);
            ola.send("GOD", "LEVEL", "60");
            await("level 60", () -> god.level() == 60);
            ola.send("GOD", "ADDSKILL", "miecz 10");
            said(ola, "skill", "poziom");
            ola.send("GOD", "SETHP", "20");
            await("hp 20", () -> god.health() == 20);
            ola.send("GOD", "HEAL");
            await("healed", () -> god.health() == god.maxHealth());
            ola.send("GOD", "POS");
            said(ola, "position", "Pozycja: ");

            // potwor: poza strefa ochronna
            Position out = outsideProtection();
            ola.send("GOD", "GOTO", out.x() + "," + out.y() + "," + out.z());
            await("moved outside", () -> god.position().distanceTo(out) <= 3);
            ola.send("GOD", "MONSTER", "wilk");
            said(ola, "monster summoned", "Przywołano");
            await("wolf near", () -> !server.world().monsters.near(god.position(), 6).isEmpty());
            ola.send("GOD", "KILL");
            said(ola, "killed", "Zabito potworów:");
            await("no monsters", () -> server.world().monsters.near(god.position(), 6).isEmpty());

            // znaczniki zadan i blogoslawienstwa dla innego gracza, premium, awans
            PlayerCharacter ulaPc = server.world().find("Ula").session.character();
            ola.send("GOD", "FLAG", "Ula, star_sealed");
            await("flag", () -> ulaPc.hasQuestFlag("star_sealed"));
            ola.send("GOD", "BLESS", "Ula");
            await("blessed", () -> ulaPc.blessingCount() >= 5);
            ola.send("GOD", "PREMIUM", "Ula 30");
            await("premium", () -> ulaPc.isPremium());
            ola.send("GOD", "PROMOTE", "Ula");
            await("promoted", () -> ulaPc.isPromoted());
            said(ula, "target told", "błogosławieństwa");

            // sprowadzenie gracza i teleport do niego
            ola.send("GOD", "BRING", "Ula");
            await("brought", () -> ulaPc.position().distanceTo(god.position()) <= 3);
            Position ulaAt = ulaPc.position();
            ola.send("GOD", "TEMPLE");
            await("temple", () -> god.position().distanceTo(out) > 3);
            ola.send("GOD", "GOTO", "Ula");
            await("goto player", () -> god.position().distanceTo(ulaAt) <= 3);

            // duch: niewidoczny dla graczy, bez obrazen, potwory go nie widza
            ola.send("GOD", "GHOST");
            awaitMessage(ola, "ghost on", m -> m[0].equals("ACCESS") && m[2].equals("1"));
            await("ghost hidden from Ula", () -> seen(ula, "Ola") == null);
            assertNotNull(seen(ola, "Ula"), "a GOD still sees players");
            assertEquals(0, god.damage(100));
            assertFalse(server.world().find("Ola").session.playerTarget().attackable(), "monsters ignore the ghost");
            ola.send("GOD", "GHOST");
            await("visible again", () -> seen(ula, "Ola") != null);

            // sprzatanie i szybkosc
            ola.send("GOD", "CLEAN");
            said(ola, "clean", "Usunięto z ziemi");
            ola.send("GOD", "SPEED", "300");
            await("speed", () -> god.speed() >= 280);

            // log administratora: kazde polecenie z imieniem i wynikiem
            String log = adminLog();
            assertTrue(log.contains("Ola [GOD] GOD ITEM rope 2 -> OK"), log);
            assertTrue(log.contains("GOD Ola -> granted"), log);

            // cofniecie GOD-a: znacznik znika, polecenia znow odmawiane
            assertTrue(console("UNGOD", "Ola").contains("Odebrano"));
            await("tag gone", () -> seen(ula, "Ola") != null && seen(ula, "Ola").access() == null);
            ola.send("GOD", "LEVEL", "300");
            said(ola, "refused again", "Brak uprawnień");
            assertEquals(60, god.level());
            assertTrue(new File(saves, "gods.txt").length() == 0 || !Files.readString(new File(saves, "gods.txt").toPath()).contains("Ola"));
        }
    }

    @Test
    void godsTxtAndAdminsTxtLevelsAreReadOnRestartAndReload() throws Exception {
        Files.writeString(new File(saves, "gods.txt").toPath(), "# GOD-y\nWielki Admin\n");
        Files.writeString(new File(saves, "admins.txt").toPath(), "Helper:tutor\nModerator\nSzef:god\n");
        Moderation m = new Moderation(saves);
        assertEquals(pl.fawizja.pixel.admin.AccessLevel.GOD, m.level("wielki admin"));
        assertEquals(pl.fawizja.pixel.admin.AccessLevel.TUTOR, m.level("Helper"));
        assertEquals(pl.fawizja.pixel.admin.AccessLevel.GAMEMASTER, m.level("moderator"));
        assertEquals(pl.fawizja.pixel.admin.AccessLevel.GOD, m.level("Szef"));
        assertEquals(pl.fawizja.pixel.admin.AccessLevel.PLAYER, m.level("Ktos"));
        assertFalse(m.isAdmin("Helper"), "a tutor is not a game master");
        assertTrue(m.isAdmin("Moderator") && m.isGod("Szef"));
        Files.writeString(new File(saves, "gods.txt").toPath(), "Wielki Admin\nNowy\n");
        m.reload();
        assertTrue(m.isGod("Nowy"));
        assertTrue(m.removeGod("Szef"));
        assertEquals(pl.fawizja.pixel.admin.AccessLevel.PLAYER, m.level("Szef"));
        assertEquals(pl.fawizja.pixel.admin.AccessLevel.TUTOR, m.level("Helper"), "other levels stay");
    }
}
