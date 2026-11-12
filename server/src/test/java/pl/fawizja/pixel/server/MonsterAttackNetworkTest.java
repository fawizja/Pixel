package pl.fawizja.pixel.server;

import static org.junit.jupiter.api.Assertions.*;
import static pl.fawizja.pixel.server.NetworkSyncTest.await;
import static pl.fawizja.pixel.server.NetworkSyncTest.awaitMessage;

import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.net.NetClient;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/** Atak potwora online: czar obszarowy trafia kazdego gracza w obszarze, a pociski i efekty widza gracze w poblizu. */
class MonsterAttackNetworkTest {

    PixelServer server;

    @BeforeEach
    void start() throws Exception {
        server = new PixelServer(0, Files.createTempDirectory("pixel-mobs").toFile());
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

    /** Wolny, przechodni odcinek 6 pol w poziomie poza strefa ochronna; zwraca jego pierwsze pole. */
    Position openRow() {
        WorldMap w = server.world().world;
        Position t = w.towns().get(0).temple();
        for (int r = 6; r < 80; r++) {
            for (int dx = -r; dx <= r; dx++) {
                Position q = t.translate(dx, r);
                boolean ok = true;
                for (int i = 0; i < 6 && ok; i++) {
                    Position p = q.translate(i, 0);
                    ok = w.isWalkable(p) && !w.isProtectionZone(p) && server.world().monsters.monsterAt(p) == null;
                }
                if (ok) return q;
            }
        }
        throw new IllegalStateException("no open row");
    }

    @Test
    void areaSpellHitsEveryPlayerInTheAreaAndProjectilesReachNeighbours() throws Exception {
        try (NetClient ola = connect(); NetClient ula = connect()) {
            ola.hello("Ola", "KNIGHT", "tajne-Ola");
            ula.hello("Ula", "DRUID", "tajne-Ula");
            await("welcome", () -> ola.playerId() > 0 && ula.playerId() > 0);
            Position row = openRow();
            int[] hp = new int[4];
            boolean[] done = {false};
            server.enqueueTest(() -> {
                ServerPlayer a = server.world().find("Ola"), b = server.world().find("Ula");
                // pajak (fala jadu 3 pola) stoi na wschodzie rzedu, gracze Ola i Ula na zachod od niego w linii fali
                Monster spider = server.world().monsters.spawnEvent("forest_spider", row.translate(4, 0), 0, 1, server.now()).get(0);
                a.session.character().setPosition(row.translate(3, 0));
                b.session.character().setPosition(row.translate(2, 0));
                a.session.character().heal(a.session.character().maxHealth());
                b.session.character().heal(b.session.character().maxHealth());
                hp[0] = a.session.character().health();
                hp[1] = b.session.character().health();
                spider.lookAt(a.session.character().position());
                assertTrue(server.world().monsterAttack(spider, a, server.now(), "web_spit"), "wave planned at the target");
                hp[2] = a.session.character().health();
                hp[3] = b.session.character().health();
                done[0] = true;
            });
            await("cast done", () -> done[0]);
            assertTrue(hp[2] < hp[0], "the target is hit: " + hp[0] + " -> " + hp[2]);
            assertTrue(hp[3] < hp[1], "the second player in the wave is hit too: " + hp[1] + " -> " + hp[3]);
            // oboje widzi efekt obszaru w ramkach stanu serwera (EV AREA)
            awaitMessage(ola, "area effect for the target", m -> m[0].equals("EV") && m[1].equals("AREA"));
            awaitMessage(ula, "area effect for the neighbour", m -> m[0].equals("EV") && m[1].equals("AREA"));

            // pocisk wloczni: strzelec bije Ole, a Ula (w poblizu) tez widzi pocisk z nazwa grafiki
            boolean[] shot = {false};
            server.enqueueTest(() -> {
                ServerPlayer a = server.world().find("Ola");
                Monster spear = server.world().monsters.spawnEvent("orc_spearman", row.translate(5, 0), 0, 1, server.now()).get(0);
                a.session.character().heal(a.session.character().maxHealth());
                spear.lookAt(a.session.character().position());
                server.world().monsterAttack(spear, a, server.now(), null);
                shot[0] = true;
            });
            await("shot", () -> shot[0]);
            awaitMessage(ola, "spear in flight (target)", m -> m[0].equals("EV") && m[1].equals("PROJECTILE") && m[9].equals("PHYSICAL/spear"));
            awaitMessage(ula, "spear in flight (neighbour)", m -> m[0].equals("EV") && m[1].equals("PROJECTILE") && m[9].equals("PHYSICAL/spear"));
        }
    }
}
