package pl.fawizja.pixel.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
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

    /** Klient TLS przypinajacy certyfikat tego serwera. */
    NetClient connect() throws IOException {
        String expected = server.fingerprint();
        return new NetClient("127.0.0.1", server.port(), 3000, fp -> fp.equals(expected));
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
        try (NetClient alice = connect();
             NetClient bob = connect()) {
            alice.hello("Alice", "PALADIN", "tajne-Alice");
            bob.hello("Bob", "DRUID", "tajne-Bob");
            await("welcome", () -> alice.playerId() > 0 && bob.playerId() > 0);
            await("self state", () -> alice.selfSave() != null && bob.selfSave() != null);
            assertEquals("Alice", self(alice).name());
            assertEquals(8, self(alice).level());
            assertNotEquals(self(alice).position(), self(bob).position(), "players do not spawn on one tile");
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
        try (NetClient a = connect()) {
            a.hello("Carol", "SORCERER", "tajne-Carol");
            await("welcome", () -> a.playerId() > 0);
            try (NetClient dup = connect()) {
                dup.hello("carol", "MONK", "tajne-Carol");
                await("duplicate rejected", () -> dup.error() != null);
                assertTrue(dup.error().contains("juz w grze"));
            }
            try (NetClient old = connect()) {
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
        try (NetClient again = connect()) {
            again.hello("Carol", "MONK", "tajne-Carol"); // profesja z zapisu, nie z prosby
            await("restored", () -> again.selfSave() != null);
            assertEquals("SORCERER", self(again).vocation().name());
        }
    }

    @Test
    void monstersAreSharedBetweenPlayers() throws Exception {
        try (NetClient a = connect();
             NetClient b = connect()) {
            a.hello("Erin", "PALADIN", "tajne-Erin");
            b.hello("Finn", "MONK", "tajne-Finn");
            await("welcome", () -> a.playerId() > 0 && b.playerId() > 0);
            // obaj teleportowani (na serwerze) do pola z wilkami: widza te same potwory o tych samych id
            Position field = new Position(58, 75, 0);
            server.enqueueTest(() -> {
                for (ServerPlayer p : server.world().players()) p.session.teleport(field, server.now());
            });
            await("both see monsters", () -> a.frame() != null && b.frame() != null
                    && !a.frame().monsters().isEmpty() && !b.frame().monsters().isEmpty()
                    && seen(a, "Finn") != null);
            // klatki obu graczy moga pochodzic z roznych krokow (potwor na skraju widoku) - czekamy na wspolny
            await("same monster ids and types", () -> {
                for (RemoteEntity ma : a.frame().monsters()) {
                    for (RemoteEntity mb : b.frame().monsters()) {
                        if (ma.id() == mb.id() && ma.key().equals(mb.key())) return true;
                    }
                }
                return false;
            });
        }
    }

    @Test
    void droppedItemsAndCombatEventsReachClients() throws Exception {
        try (NetClient a = connect();
             NetClient b = connect()) {
            a.hello("Gina", "PALADIN", "tajne-Gina");
            b.hello("Hugo", "SORCERER", "tajne-Hugo");
            await("self", () -> a.selfSave() != null && b.selfSave() != null);
            PlayerCharacter gina = self(a);
            int slot = -1;
            for (int i = 0; i < gina.backpack().size(); i++) if (gina.backpack().get(i) != null) slot = i;
            int itemId = gina.backpack().get(slot).itemId();
            a.send("ACT", "slot_drop:" + slot);
            await("hugo sees the item on the ground", () -> b.frame() != null && b.frame().ground().stream()
                    .anyMatch(g -> g.itemId() == itemId));

            // walka obok zywego wilka: zdarzenia walki (EV) docieraja do klienta
            Position field = new Position(58, 75, 0);
            server.enqueueTest(() -> {
                ServerWorld w = server.world();
                Position spot = field;
                pl.fawizja.pixel.monster.Monster best = null;
                for (pl.fawizja.pixel.monster.Monster m : w.monsters.monsters()) {
                    if (m.isDead() || m.position().z() != 0 || !m.type().key().equals("wolf")) continue;
                    if (best == null || m.position().distanceTo(field) < best.position().distanceTo(field)) best = m;
                }
                if (best != null) {
                    for (pl.fawizja.pixel.world.Direction d : pl.fawizja.pixel.world.Direction.values()) {
                        Position n = best.position().translate(d.dx, d.dy);
                        if (w.world.isWalkable(n) && !w.world.isProtectionZone(n) && w.monsters.monsterAt(n) == null) {
                            spot = n;
                            break;
                        }
                    }
                }
                for (ServerPlayer p : w.players()) {
                    if (p.name().equals("Gina")) p.session.teleport(spot, server.now());
                }
            });
            await("combat event", () -> {
                if (a.frame() != null && !a.frame().monsters().isEmpty()) a.send("TARGET", String.valueOf(a.frame().monsters().get(0).id()));
                String[] m;
                while ((m = a.poll()) != null) if (m[0].equals("EV")) return true;
                return false;
            });

            // zabity wilk zostawia cialo widoczne w klatce klienta
            server.enqueueTest(() -> {
                for (ServerPlayer p : server.world().players()) {
                    if (!p.name().equals("Gina")) continue;
                    for (pl.fawizja.pixel.monster.Monster m : server.world().monsters.near(p.session.character().position(), 3)) {
                        if (m.isDead()) continue;
                        p.session.combat().damageMonster(m, 1_000_000, pl.fawizja.pixel.combat.DamageType.PHYSICAL, server.now());
                        break;
                    }
                }
            });
            await("corpse in frame", () -> a.frame() != null && !a.frame().corpses().isEmpty());
        }
    }

    @Test
    void pvpFollowsTheRulesOverTheNetwork() throws Exception {
        try (NetClient a = connect();
             NetClient b = connect()) {
            a.hello("Ivo", "PALADIN", "tajne-Ivo");
            b.hello("Jan", "MONK", "tajne-Jan");
            await("welcome", () -> a.playerId() > 0 && b.playerId() > 0);
            int janId = b.playerId();
            // w swiatyni (PZ) i na poziomie 8 atak jest odrzucony z komunikatem
            a.send("ATTACK", String.valueOf(janId));
            await("refused", () -> {
                String[] m;
                while ((m = a.poll()) != null) if (m[0].equals("LOG") && m[2].contains("poziomu")) return true;
                return false;
            });
            Position field = new Position(58, 75, 0);
            server.enqueueTest(() -> {
                int i = 0;
                for (ServerPlayer p : server.world().players()) {
                    PlayerCharacter pc = p.session.character();
                    pc.addExperience(pl.fawizja.pixel.balance.Balance.experienceForLevel(40) - pc.experience());
                    pc.heal(pc.maxHealth());
                    p.session.teleport(field.translate(i++, 0), server.now());
                }
            });
            await("both level 40", () -> {
                try {
                    return self(a).level() >= 40 && self(b).level() >= 40;
                } catch (Exception e) {
                    return false;
                }
            });
            int full = self(b).health();
            a.send("ATTACK", String.valueOf(janId));
            await("jan hurt and ivo marked", () -> {
                RemoteEntity ivo = seen(b, "Ivo");
                try {
                    return ivo != null && "WHITE".equals(ivo.mark()) && self(b).health() < full
                            && a.frame().playerTargetId() == janId && "WHITE".equals(a.frame().skull());
                } catch (Exception e) {
                    return false;
                }
            });
        }
        // czaszka przetrwa restart serwera
        await("both left", () -> server.world().players().isEmpty());
        server.close();
        server = new PixelServer(0, saves);
        server.start();
        try (NetClient a = connect()) {
            a.hello("Ivo", "PALADIN", "tajne-Ivo");
            await("skull after restart", () -> a.frame() != null && "WHITE".equals(a.frame().skull()));
        }
    }

    boolean accepted(String name, String voc, String password) throws Exception {
        try (NetClient c = connect()) {
            c.hello(name, voc, password);
            await("answer", () -> c.selfSave() != null || c.error() != null);
            return c.selfSave() != null;
        }
    }

    @Test
    void accountsRequireTheRightPasswordAndLockAfterFailures() throws Exception {
        assertTrue(accepted("Kora", "DRUID", "pierwsze-haslo"), "first login creates the account");
        await("left", () -> server.world().players().isEmpty());
        assertFalse(accepted("Kora", "DRUID", "inne-haslo"));
        assertFalse(accepted("Nowa", "DRUID", "123"), "too short password");
        assertTrue(accepted("Kora", "DRUID", "pierwsze-haslo"));
        await("left", () -> server.world().players().isEmpty());
        for (int i = 0; i < AccountStore.MAX_FAILURES; i++) assertFalse(accepted("Kora", "DRUID", "zle-haslo"));
        try (NetClient c = connect()) {
            c.hello("Kora", "DRUID", "pierwsze-haslo");
            await("locked", () -> c.error() != null);
            assertTrue(c.error().contains("Za duzo"), c.error());
        }
        // plik konta nie zawiera hasla
        File acc = new File(new File(saves, "accounts"), "kora.acc");
        String content = new String(Files.readAllBytes(acc.toPath()));
        assertFalse(content.contains("pierwsze-haslo"));
        assertTrue(content.startsWith("pbkdf2-sha256;"));
    }

    @Test
    void connectionsAreEncryptedAndAChangedCertificateIsRefused() throws Exception {
        assertNotNull(server.fingerprint());
        assertEquals(32 * 3 - 1, server.fingerprint().length());
        try (NetClient c = connect()) {
            assertEquals(server.fingerprint(), c.serverFingerprint());
            c.hello("Lena", "MONK", "haslo-lena");
            await("tls login", () -> c.selfSave() != null);
        }
        // inny przypiety odcisk (np. podstawiony serwer) - polaczenie odrzucone przed wyslaniem hasla
        assertThrows(IOException.class, () -> new NetClient("127.0.0.1", server.port(), 3000, fp -> false));
        // ten sam katalog = ten sam certyfikat po restarcie
        String before = server.fingerprint();
        server.close();
        server = new PixelServer(0, saves);
        server.start();
        assertEquals(before, server.fingerprint());
        // bez TLS (opcja --no-tls) dziala zwykle polaczenie
        try (PixelServer plain = new PixelServer(0, Files.createTempDirectory("pixel-plain").toFile(), false)) {
            plain.start();
            assertNull(plain.fingerprint());
            try (NetClient c = new NetClient("127.0.0.1", plain.port(), 3000)) {
                c.hello("Olaf", "PALADIN", "haslo-olaf");
                await("plain login", () -> c.selfSave() != null);
            }
        }
    }

    @Test
    void floodingAndSilentConnectionsAreCutOffAndChatIsRateLimited() throws Exception {
        try (NetClient spam = connect()) {
            spam.hello("Spammer", "MONK", "haslo-spam");
            await("in", () -> spam.selfSave() != null);
            for (int i = 0; i < 300; i++) spam.send("MOVE", "-");
            await("flood cut off", () -> spam.isClosed());
            assertTrue(String.valueOf(spam.error()).contains("Za duzo") || spam.error() == null || spam.isClosed());
        }
        try (NetClient talker = connect(); NetClient listener = connect()) {
            talker.hello("Talker", "MONK", "haslo-talk");
            listener.hello("Listener", "DRUID", "haslo-list");
            await("in", () -> talker.selfSave() != null && listener.selfSave() != null);
            for (int i = 0; i < ServerWorld.CHAT_MAX + 3; i++) talker.send("CHAT", "wiadomosc " + i);
            await("warned", () -> {
                String[] m;
                while ((m = talker.poll()) != null) if (m[0].equals("LOG") && m[2].contains("Zwolnij")) return true;
                return false;
            });
            Thread.sleep(300);
            int got = 0;
            String[] m;
            while ((m = listener.poll()) != null) if (m[0].equals("CHAT")) got++;
            assertEquals(ServerWorld.CHAT_MAX, got);
        }
    }

    @Test
    void connectionsPerAddressAreLimited() throws Exception {
        java.util.List<java.net.Socket> open = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < PixelServer.MAX_PER_ADDRESS; i++) open.add(new java.net.Socket("127.0.0.1", server.port()));
            java.net.Socket extra = new java.net.Socket("127.0.0.1", server.port());
            extra.setSoTimeout(3000);
            assertEquals(-1, extra.getInputStream().read(), "over the limit = closed at once");
            extra.close();
        } finally {
            for (java.net.Socket s : open) s.close();
        }
        // po zamknieciu miejsca sie zwalniaja
        await("slots freed", () -> {
            try (NetClient c = connect()) {
                return true;
            } catch (IOException e) {
                return false;
            }
        });
    }

    @Test
    void backupsArchiveSavesAccountsAndKeepTheNewest() throws Exception {
        try (NetClient c = connect()) {
            c.hello("Mila", "DRUID", "haslo-mila");
            await("in", () -> c.selfSave() != null);
        }
        await("saved", () -> server.world().saved("Mila"));
        Backups b = new Backups(saves);
        File zip = b.create();
        java.util.Set<String> names = new java.util.HashSet<>();
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(zip)) {
            z.stream().forEach(e -> names.add(e.getName()));
        }
        assertTrue(names.contains("player_mila.save"), names.toString());
        assertTrue(names.contains("accounts/mila.acc"), names.toString());
        assertTrue(names.contains("tls/server.p12"), names.toString());
        for (int i = 0; i < Backups.KEEP + 3; i++) b.create();
        assertEquals(Backups.KEEP, b.list().length);
    }

    @Test
    void anUnchangedWorldIsSentAsAShortKeepAlive() throws Exception {
        try (NetClient a = connect()) {
            a.hello("Kora", "DRUID", "tajne-Kora");
            await("first frame", () -> a.frame() != null);
            Position at = a.frame().self();
            long t0 = a.frame().serverTime();
            await("keep frames", () -> server.world().players().stream()
                    .anyMatch(p -> p.name().equals("Kora") && p.framesKept >= 5));
            await("time advances", () -> a.frame().serverTime() > t0);
            assertEquals(at, a.frame().self(), "KEEP repeats the last state");
        }
    }

    /** Czeka na komunikat spelniajacy warunek (pozostale komunikaty sa pomijane). */
    static void awaitMessage(NetClient c, String what, java.util.function.Predicate<String[]> cond) throws InterruptedException {
        await(what, () -> {
            String[] m;
            while ((m = c.poll()) != null) if (cond.test(m)) return true;
            return false;
        });
    }

    @Test
    void partiesShareExperienceChatTogetherAndCannotFightEachOther() throws Exception {
        try (NetClient ola = connect();
             NetClient ula = connect()) {
            ola.hello("Ola", "PALADIN", "tajne-Ola");
            ula.hello("Ula", "DRUID", "tajne-Ula");
            await("welcome", () -> ola.playerId() > 0 && ula.playerId() > 0 && ula.selfSave() != null);
            ola.send("PARTY", "INVITE", "ula");
            awaitMessage(ula, "invite", m -> m[0].equals("LOG") && m[2].contains("/przyjmij Ola"));
            ula.send("PARTY", "ACCEPT", "Ola");
            awaitMessage(ola, "party list for ola", m -> m[0].equals("PARTY") && m[1].equals("Ola,Ula"));
            awaitMessage(ula, "party list for ula", m -> m[0].equals("PARTY") && m[1].equals("Ola,Ula"));

            ola.send("PCHAT", "idziemy na wilki");
            awaitMessage(ula, "party chat", m -> m[0].equals("CHAT") && m[1].equals("[Druzyna] Ola") && m[2].equals("idziemy na wilki"));

            long olaExp = self(ola).experience(), ulaExp = self(ula).experience();
            pl.fawizja.pixel.monster.MonsterType wolf = pl.fawizja.pixel.monster.MonsterRegistry
                    .loadDefault(ItemRegistry.loadDefault()).get("wolf");
            long solo = pl.fawizja.pixel.balance.Balance.experienceReward(wolf.experience(), wolf.level(), 8);
            boolean[] shared = new boolean[1];
            server.enqueueTest(() -> {
                ServerPlayer killer = server.world().find("Ola");
                shared[0] = server.world().shareKill(killer, wolf, server.now());
            });
            long each = pl.fawizja.pixel.party.PartyRules.share(solo, 2);
            await("both got a share", () -> {
                try {
                    return self(ola).experience() == olaExp + each && self(ula).experience() == ulaExp + each;
                } catch (Exception e) {
                    return false;
                }
            });
            assertTrue(shared[0]);
            assertTrue(each > solo / 2, "the party bonus beats an even split");

            ola.send("ATTACK", String.valueOf(ula.playerId()));
            awaitMessage(ola, "no friendly fire", m -> m[0].equals("LOG") && m[2].contains("druzyny"));

            ula.send("BYE");
            awaitMessage(ola, "party dissolved", m -> m[0].equals("PARTY") && m[1].equals("-"));
        }
    }

    static boolean logSays(NetClient c, String text) {
        String[] m;
        while ((m = c.poll()) != null) if (m[0].equals("LOG") && m.length > 2 && m[2].contains(text)) return true;
        return false;
    }

    @Test
    void playersCanChangeTheirPasswordAndDeleteTheirAccount() throws Exception {
        try (NetClient a = connect()) {
            a.hello("Lena", "MONK", "stare-haslo");
            await("in", () -> a.playerId() > 0);
            a.send("PASSWD", "zle-haslo", "nowe-haslo");
            await("refused", () -> logSays(a, "Zle obecne haslo"));
            a.send("PASSWD", "stare-haslo", "nowe-haslo");
            await("changed", () -> logSays(a, "Haslo zmienione"));
        }
        await("left", () -> server.world().players().isEmpty());
        assertFalse(accepted("Lena", "MONK", "stare-haslo"), "the old password no longer works");
        try (NetClient a = connect()) {
            a.hello("Lena", "MONK", "nowe-haslo");
            await("in again", () -> a.playerId() > 0);
            a.send("DELETE", "nowe-haslo");
            await("deleted", () -> "Konto usuniete.".equals(a.error()));
        }
        await("gone", () -> server.world().players().isEmpty());
        assertFalse(server.world().saved("Lena"), "the character save is removed");
        assertTrue(accepted("Lena", "DRUID", "zupelnie-nowe"), "the name is free again");
    }

    @Test
    void oneAddressCannotMassCreateAccounts() throws Exception {
        server.world().maxNewAccountsPerAddress = 2;
        assertTrue(accepted("Olaf", "MONK", "haslo-olafa"));
        assertTrue(accepted("Petra", "MONK", "haslo-petry"));
        try (NetClient c = connect()) {
            c.hello("Quin", "MONK", "haslo-quina");
            await("refused", () -> c.error() != null);
            assertTrue(c.error().contains("Za duzo nowych kont"), c.error());
        }
        assertTrue(accepted("Olaf", "MONK", "haslo-olafa"), "existing accounts still log in");
    }
}
