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
    /** Nagroda za pierwsze wejscie dnia (nowa postac, poziom 8) - trafia na konto przy pierwszym kroku serwera. */
    static final long LOGIN_BONUS = pl.fawizja.pixel.daily.LoginStreak.rewardGold(1, 8);
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
        await(what, cond, 10_000);
    }

    static void await(String what, BooleanSupplier cond, long timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
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

    /**
     * Wolny kafelek obok zywego wilka najblizszego punktowi near (watek petli swiata). Stale wspolrzedne pola nie
     * wystarcza: na mapie 1000x1000 przy (149,351) nie ma wilkow w zasiegu widoku, a potwory z wezszym wzrokiem
     * (0.209.0) rzadko same podchodza - test zalezal od przypadkowego zblakania sie wilka.
     */
    static Position besideLiveWolf(ServerWorld w, Position near) {
        pl.fawizja.pixel.monster.Monster best = null;
        for (pl.fawizja.pixel.monster.Monster m : w.monsters.monsters()) {
            if (m.isDead() || m.position().z() != near.z() || !m.type().key().equals("wolf")) continue;
            if (best == null || m.position().distanceTo(near) < best.position().distanceTo(near)) best = m;
        }
        assertNotNull(best, "no live wolf on the map");
        for (pl.fawizja.pixel.world.Direction d : pl.fawizja.pixel.world.Direction.values()) {
            Position n = best.position().translate(d.dx, d.dy);
            if (w.world.isWalkable(n) && !w.world.isProtectionZone(n) && w.monsters.monsterAt(n) == null) return n;
        }
        fail("no free tile next to the wolf at " + best.position());
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

            bob.send("CHAT", "Cześć Alice!");
            await("chat relayed", () -> {
                String[] m;
                while ((m = alice.poll()) != null) {
                    if (m[0].equals("CHAT") && m[1].equals("Bob") && m[2].equals("Cześć Alice!")) return true;
                }
                return false;
            });

            bob.send("ROLL", "20");
            await("dice roll from the server", () -> {
                String[] m;
                while ((m = alice.poll()) != null) {
                    if (m[0].equals("CHAT") && m[1].equals("Bob") && m[2].matches("\\* rzuca kostką k20: ([1-9]|1[0-9]|20) \\*")) return true;
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
                assertTrue(dup.error().contains("już w grze"));
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
            // obaj teleportowani (na serwerze) obok zywego wilka: widza te same potwory o tych samych id
            java.util.concurrent.CompletableFuture<Position> spot = new java.util.concurrent.CompletableFuture<>();
            server.enqueueTest(() -> {
                try {
                    Position field = besideLiveWolf(server.world(), new Position(149, 351, 0));
                    for (ServerPlayer p : server.world().players()) p.session.teleport(field, server.now());
                    spot.complete(field);
                } catch (Throwable t) {
                    spot.completeExceptionally(t);
                }
            });
            spot.get(10, java.util.concurrent.TimeUnit.SECONDS); // blad wyboru miejsca widoczny w tescie, nie w logu serwera
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
            server.enqueueTest(() -> {
                ServerWorld w = server.world();
                Position spot = besideLiveWolf(w, new Position(149, 351, 0));
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
            Position field = new Position(149, 351, 0);
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
            // tryb PvP wylaczony (domyslnie): atak odrzucony; po PVP ON inni widza znacznik trybu
            a.send("ATTACK", String.valueOf(janId));
            awaitMessage(a, "pvp mode needed", m -> m[0].equals("LOG") && m[2].contains("Włącz tryb PvP"));
            a.send("PVP", "ON");
            awaitMessage(a, "pvp mode echoed", m -> m[0].equals("PVP") && m[1].equals("1") && m[2].equals("NONE"));
            await("jan sees ivo in pvp mode", () -> seen(b, "Ivo") != null && seen(b, "Ivo").pvpMode());
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

    /**
     * Trzech graczy: krag ognia bez trybu PvP omija graczy; w trybie PvP rani obu sasiadow i daje biala
     * czaszke (widoczna dla innych), a wtedy gracz bez trybu PvP moze bic agresora bez wlasnej czaszki.
     */
    @Test
    void areaSpellsSkullsAndAggressorsWithThreePlayers() throws Exception {
        try (NetClient a = connect();
             NetClient b = connect();
             NetClient c = connect()) {
            a.hello("Ivo", "SORCERER", "tajne-Ivo");
            b.hello("Jan", "MONK", "tajne-Jan");
            c.hello("Ola", "DRUID", "tajne-Ola");
            await("welcome", () -> a.playerId() > 0 && b.playerId() > 0 && c.playerId() > 0);
            int ivoId = a.playerId();
            Position field = new Position(149, 351, 0);
            java.util.concurrent.atomic.AtomicBoolean ready = new java.util.concurrent.atomic.AtomicBoolean();
            server.enqueueTest(() -> {
                String[] order = {"Jan", "Ivo", "Ola"}; // Ivo w srodku: obaj sasiedzi w kregu ognia
                for (int i = 0; i < order.length; i++) {
                    ServerPlayer p = server.world().find(order[i]);
                    PlayerCharacter pc = p.session.character();
                    pc.addExperience(pl.fawizja.pixel.balance.Balance.experienceForLevel(40) - pc.experience());
                    pc.heal(pc.maxHealth());
                    pc.restoreMana(pc.maxMana());
                    p.session.teleport(field.translate(i, 0), server.now());
                }
                ready.set(true);
            });
            await("ready", ready::get);
            await("all level 40", () -> {
                try {
                    return self(a).level() >= 40 && self(b).level() >= 40 && self(c).level() >= 40
                            && seen(b, "Ivo") != null && seen(c, "Ivo") != null;
                } catch (Exception e) {
                    return false;
                }
            });
            int janFull = self(b).health(), olaFull = self(c).health();

            // bez trybu PvP: obszar omija graczy (nie ma w co trafic)
            a.send("ACT", "cast:flame_ring");
            awaitMessage(a, "no target", m -> m[0].equals("LOG") && m[2].contains(pl.fawizja.pixel.magic.CastResult.NO_TARGET.message));
            assertEquals(janFull, self(b).health());
            assertEquals(olaFull, self(c).health());
            assertEquals("NONE", seen(b, "Ivo").mark());

            // w trybie PvP: krag ognia rani Jana i Ole, Ivo dostaje biala czaszke i blokade PZ
            a.send("ACT", "pvp");
            awaitMessage(a, "pvp on", m -> m[0].equals("PVP") && m[1].equals("1"));
            a.send("ACT", "cast:flame_ring");
            awaitMessage(a, "white skull and pz block", m -> m[0].equals("PVP") && m[2].equals("WHITE")
                    && Long.parseLong(m[4]) > 0);
            await("both hurt, everyone sees the skull", () -> {
                try {
                    return self(b).health() < janFull && self(c).health() < olaFull
                            && "WHITE".equals(seen(b, "Ivo").mark()) && "WHITE".equals(seen(c, "Ivo").mark());
                } catch (Exception e) {
                    return false;
                }
            });
            assertFalse(seen(c, "Jan").pvpMode());

            // Jan bez trybu PvP bije agresora: wolno, bez czaszki dla Jana
            int ivoHp = self(a).health();
            b.send("ATTACK", String.valueOf(ivoId));
            await("jan fights ivo", () -> {
                try {
                    return b.frame().playerTargetId() == ivoId && self(a).health() < ivoHp;
                } catch (Exception e) {
                    return false;
                }
            }, 20_000);
            assertEquals("NONE", b.frame().skull(), "attacking an aggressor is free");
            assertEquals("NONE", seen(c, "Jan").mark());
        }
    }

    @Test
    void bountiesArePaidToTheKiller() throws Exception {
        try (NetClient a = connect();
             NetClient b = connect()) {
            a.hello("Ivo", "PALADIN", "tajne-Ivo");
            b.hello("Jan", "MONK", "tajne-Jan");
            await("welcome", () -> a.playerId() > 0 && b.playerId() > 0);
            int janId = b.playerId();
            a.send("BOUNTY", "Jan", "500");
            awaitMessage(a, "too small", m -> m[0].equals("LOG") && m[2].contains("Najmniejsza nagroda"));
            a.send("BOUNTY", "Ivo", "5000");
            awaitMessage(a, "own head", m -> m[0].equals("LOG") && m[2].contains("własną głowę"));
            Position field = new Position(149, 351, 0);
            java.util.concurrent.atomic.AtomicBoolean ready = new java.util.concurrent.atomic.AtomicBoolean();
            server.enqueueTest(() -> {
                int i = 0;
                for (ServerPlayer p : server.world().players()) {
                    PlayerCharacter pc = p.session.character();
                    pc.addExperience(pl.fawizja.pixel.balance.Balance.experienceForLevel(40) - pc.experience());
                    pc.heal(pc.maxHealth());
                    pc.bank().credit(10_000);
                    p.session.teleport(field.translate(i++, 0), server.now());
                }
                ready.set(true);
            });
            await("ready", ready::get);
            a.send("BOUNTY", "jan", "5000");
            awaitMessage(b, "announced", m -> m[0].equals("LOG") && m[2].startsWith("Nagroda za głowę Jan wynosi teraz 5"));
            b.send("BOUNTY");
            awaitMessage(b, "listed", m -> m[0].equals("LOG") && m[2].startsWith("Nagrody za głowy: Jan"));
            b.send("WHO");
            awaitMessage(b, "bounty in player list", m -> m[0].equals("WHO")
                    && java.util.Arrays.stream(m).anyMatch(r -> r.startsWith("Jan;") && r.contains("nagroda 5")));
            java.util.concurrent.atomic.AtomicBoolean weak = new java.util.concurrent.atomic.AtomicBoolean();
            server.enqueueTest(() -> {
                PlayerCharacter jan = server.world().find("Jan").session.character();
                jan.damage(jan.health() - 1);
                weak.set(true);
            });
            await("weak", weak::get);
            a.send("PVP", "ON");
            a.send("ATTACK", String.valueOf(janId));
            // Jan regeneruje sie miedzy ciosami (paladyn moze chybic) - oslabiamy go ponownie, az padnie
            long[] lastWeaken = {System.currentTimeMillis()};
            await("claimed", () -> {
                if (logged(a, "Ivo odbiera nagrodę za głowę Jan")) return true;
                if (System.currentTimeMillis() - lastWeaken[0] > 400) {
                    lastWeaken[0] = System.currentTimeMillis();
                    server.enqueueTest(() -> {
                        ServerPlayer j = server.world().find("Jan");
                        if (j != null && !j.session.character().isDead()) {
                            j.session.character().damage(j.session.character().health() - 1);
                        }
                    });
                }
                return false;
            }, 30_000); // trafienie zalezy od rzutu (paladyn moze chybiac), a CI bywa obciazone
            b.send("BOUNTY");
            awaitMessage(b, "none left", m -> m[0].equals("LOG") && m[2].startsWith("Nie ma nagród"));
        }
    }

    @Test
    void theServerReportsItsBosses() throws Exception {
        try (NetClient a = connect()) {
            a.hello("Ewa", "DRUID", "tajne-Ewa");
            await("welcome", () -> a.selfSave() != null);
            a.send("BOSS");
            awaitMessage(a, "boss list", m -> m[0].equals("BOSS") && m.length >= 4
                    && java.util.Arrays.stream(m).skip(1).allMatch(r -> r.split(";").length == 3 && r.endsWith(";żyje")));
        }
    }

    @Test
    void theServerSendsTheLastDeathRecap() throws Exception {
        try (NetClient a = connect()) {
            a.hello("Zosia", "DRUID", "tajne-Zosia");
            await("welcome", () -> a.selfSave() != null);
            a.send("DEATH");
            awaitMessage(a, "no death yet", m -> m[0].equals("DEATHLOG") && m.length == 1);
            server.enqueueTest(() -> {
                for (ServerPlayer p : server.world().players()) {
                    p.session.combat().recordDamage("Wilk", 40, server.now());
                    p.session.combat().diedFromPlayer("Kuba", false, server.now());
                }
            });
            awaitMessage(a, "death log", m -> m[0].equals("LOG") && m[2].startsWith("Zginąłeś"));
            a.send("DEATH");
            awaitMessage(a, "recap", m -> m[0].equals("DEATHLOG")
                    && pl.fawizja.pixel.combat.DeathRecap.decode(m, 1).get(0)[1].equals("Kuba (gracz)")
                    && m[3].startsWith("Wilk;40 obrażeń"));
        }
    }

    static boolean logged(NetClient c, String text) {
        String[] m;
        while ((m = c.poll()) != null) if (m[0].equals("LOG") && m.length > 2 && m[2].contains(text)) return true;
        return false;
    }

    @Test
    void duelEndsAtFirstBloodWithoutSkullsEvenBelowProtectionLevel() throws Exception {
        try (NetClient a = connect();
             NetClient b = connect()) {
            a.hello("Ola", "PALADIN", "tajne-Ola");
            b.hello("Piotr", "MONK", "tajne-Piotr");
            await("welcome", () -> a.playerId() > 0 && b.playerId() > 0);
            int piotrId = b.playerId();
            Position field = new Position(149, 351, 0);
            server.enqueueTest(() -> {
                // po imieniu - kolejnosc graczy na serwerze nie jest stala
                for (ServerPlayer p : server.world().players()) {
                    p.session.teleport(field.translate(p.name().equals("Ola") ? 0 : 1, 0), server.now());
                }
            });
            await("on the field", () -> {
                RemoteEntity o = seen(b, "Ola");
                return o != null && o.position().equals(field);
            });
            a.send("DUEL", "REQUEST", "Piotr");
            await("challenge reaches piotr", () -> logged(b, "wyzywa cię na pojedynek"));
            b.send("DUEL", "REQUEST", "Ola");
            await("duel starts", () -> logged(a, "Pojedynek z Piotr"));
            // szybszy koniec: Piotr ma juz malo zdrowia; potwory z okolicy usuniete, by nie dobily go przy 1 HP
            server.enqueueTest(() -> {
                for (pl.fawizja.pixel.monster.Monster m : server.world().monsters.near(field, 25)) {
                    server.world().monsters.kill(m, server.now());
                }
                for (ServerPlayer p : server.world().players()) {
                    PlayerCharacter pc = p.session.character();
                    if (pc.name().equals("Piotr")) pc.damage(pc.health() - 3);
                }
            });
            a.send("ATTACK", String.valueOf(piotrId)); // poziom 8 < 30: bez pojedynku byloby odrzucone
            await("ola wins", () -> logged(a, "Wygrywasz pojedynek"));
            await("win counted", () -> server.world().find("Ola").session.character().stats().duelsWon() == 1);
            // przegrany zostal przy 1 HP (potem regeneracja dodaje troche zdrowia) i nie zginal - nadal na polu walki
            await("piotr survived on the field", () -> {
                try {
                    PlayerCharacter pc = self(b);
                    return !pc.isDead() && pc.health() < pc.maxHealth() / 2 && pc.position().distanceTo(field) <= 1;
                } catch (Exception e) {
                    return false;
                }
            });
            await("no skull, attack stopped", () -> a.frame() != null && "NONE".equals(a.frame().skull())
                    && a.frame().playerTargetId() != piotrId);
            // po pojedynku zwykle zasady: poziom 8 nie atakuje graczy
            a.send("ATTACK", String.valueOf(piotrId));
            await("refused again", () -> logged(a, "poziomu"));
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
        assertTrue(accepted("Kora", "DRUID", "pierwsze-hasło"), "first login creates the account");
        await("left", () -> server.world().players().isEmpty());
        assertFalse(accepted("Kora", "DRUID", "inne-hasło"));
        assertFalse(accepted("Nowa", "DRUID", "123"), "too short password");
        assertTrue(accepted("Kora", "DRUID", "pierwsze-hasło"));
        await("left", () -> server.world().players().isEmpty());
        for (int i = 0; i < AccountStore.MAX_FAILURES; i++) assertFalse(accepted("Kora", "DRUID", "źle-hasło"));
        try (NetClient c = connect()) {
            c.hello("Kora", "DRUID", "pierwsze-hasło");
            await("locked", () -> c.error() != null);
            assertTrue(c.error().contains("Za dużo"), c.error());
        }
        // plik konta nie zawiera hasla
        File acc = new File(new File(saves, "accounts"), "kora.acc");
        String content = new String(Files.readAllBytes(acc.toPath()));
        assertFalse(content.contains("pierwsze-hasło"));
        assertTrue(content.startsWith("pbkdf2-sha256;"));
    }

    @Test
    void connectionsAreEncryptedAndAChangedCertificateIsRefused() throws Exception {
        assertNotNull(server.fingerprint());
        assertEquals(32 * 3 - 1, server.fingerprint().length());
        try (NetClient c = connect()) {
            assertEquals(server.fingerprint(), c.serverFingerprint());
            c.hello("Lena", "MONK", "hasło-lena");
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
                c.hello("Olaf", "PALADIN", "hasło-olaf");
                await("plain login", () -> c.selfSave() != null);
            }
        }
    }

    @Test
    void floodingAndSilentConnectionsAreCutOffAndChatIsRateLimited() throws Exception {
        try (NetClient spam = connect()) {
            spam.hello("Spammer", "MONK", "hasło-spam");
            await("in", () -> spam.selfSave() != null);
            for (int i = 0; i < 300; i++) spam.send("MOVE", "-");
            await("flood cut off", () -> spam.isClosed());
            assertTrue(String.valueOf(spam.error()).contains("Za dużo") || spam.error() == null || spam.isClosed());
        }
        try (NetClient talker = connect(); NetClient listener = connect()) {
            talker.hello("Talker", "MONK", "hasło-talk");
            listener.hello("Listener", "DRUID", "hasło-list");
            await("in", () -> talker.selfSave() != null && listener.selfSave() != null);
            for (int i = 0; i < ServerWorld.CHAT_MAX + 3; i++) talker.send("CHAT", "wiadomość " + i);
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
            c.hello("Mila", "DRUID", "hasło-mila");
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

    @Test
    void bossQuestForFivePlayersOnline() throws Exception {
        pl.fawizja.pixel.world.BossQuest q = server.world().world.bossQuest("frost");
        pl.fawizja.pixel.game.BossQuestRuntime rt = server.world().bossQuests;
        String[] names = {"Lodka", "Szron", "Mroz", "Sopel", "Zima"};
        pl.fawizja.pixel.vocation.Vocation[] vocs = pl.fawizja.pixel.vocation.Vocation.values();
        java.util.List<NetClient> cs = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < 5; i++) {
                NetClient c = connect();
                cs.add(c);
                c.hello(names[i], vocs[i % vocs.length].name(), "tajne-" + names[i]);
            }
            await("welcome", () -> cs.stream().allMatch(c -> c.playerId() > 0));
            // czterech na polach, Mroz (srodkowe pole) ciagnie: serwer odmawia i mowi, ilu brakuje
            server.enqueueTest(() -> {
                for (ServerPlayer p : server.world().players()) {
                    // wytrzymali bohaterowie (poziom 120), zeby Ysolde nie zabila ich przed testowym ciosem
                    pl.fawizja.pixel.character.PlayerCharacter pc = p.session.character();
                    pc.addExperience(pl.fawizja.pixel.balance.Balance.experienceForLevel(120) - pc.experience());
                    pc.heal(pc.maxHealth());
                }
                for (int i = 0; i < 4; i++) {
                    int sq = new int[] {2, 0, 1, 3}[i];
                    server.world().find(new String[] {"Mroz", "Lodka", "Szron", "Sopel"}[i]).session
                            .teleport(q.squares().get(sq).stand(), server.now());
                }
            });
            await("four on squares", () -> pl.fawizja.pixel.game.BossQuestRuntime.onSquares(q, server.world().players().stream()
                    .map(p -> p.session.bossHero()).toList()).size() == 4);
            Position lever = q.lever();
            cs.get(2).send("ACT", "open:" + lever.x() + ":" + lever.y() + ":" + lever.z());
            awaitMessage(cs.get(2), "missing one", m -> m[0].equals("LOG") && m.length > 2 && m[2].contains("brakuje jeszcze 1"));
            assertEquals(pl.fawizja.pixel.game.BossQuestRuntime.Phase.IDLE, rt.phase(q.id()));
            // piaty staje na polu: dzwignia przenosi wszystkich na arene i budzi Ysolde
            server.enqueueTest(() -> server.world().find("Zima").session.teleport(q.squares().get(4).stand(), server.now()));
            await("five on squares", () -> pl.fawizja.pixel.game.BossQuestRuntime.onSquares(q, server.world().players().stream()
                    .map(p -> p.session.bossHero()).toList()).size() == 5);
            cs.get(2).send("ACT", "open:" + lever.x() + ":" + lever.y() + ":" + lever.z());
            await("all five in the arena", () -> server.world().players().stream()
                    .allMatch(p -> q.inArena(p.session.character().position())));
            await("boss spawned", () -> rt.bossId(q.id()) >= 0);
            for (NetClient c : cs) awaitMessage(c, "arena message", m -> m[0].equals("LOG") && m.length > 2 && m[2].contains("Ysolde, Królowa Lodu się budzi"));
            // boss ginie (cios gracza Mroz): kazdy dostaje swoj lup, zapis ukonczenia i komunikat
            server.enqueueTest(() -> {
                for (pl.fawizja.pixel.monster.Monster m : new java.util.ArrayList<>(server.world().monsters.monsters())) {
                    if (m.id() == rt.bossId(q.id())) {
                        server.world().find("Mroz").session.combat().damageMonster(m, m.type().maxHealth() * 20,
                                pl.fawizja.pixel.combat.DamageType.FIRE, server.now());
                    }
                }
            });
            await("cleared", () -> rt.phase(q.id()) == pl.fawizja.pixel.game.BossQuestRuntime.Phase.CLEARED);
            for (int i = 0; i < 5; i++) {
                awaitMessage(cs.get(i), "own loot " + names[i], m -> m[0].equals("LOG") && m.length > 2 && m[2].startsWith("Twój łup z bossa"));
                assertTrue(server.world().find(names[i]).session.character().completedBossQuests().contains(q.id()), names[i]);
            }
            awaitMessage(cs.get(0), "broadcast", m -> m[0].equals("LOG") && m.length > 2 && m[2].contains("pokonuje Ysolde"));
            // portal: wszyscy wracaja do przedsionka, arena wolna; ponowna dzwignia - odmowa (raz na postac)
            server.enqueueTest(() -> {
                for (ServerPlayer p : server.world().players()) p.session.teleport(q.exitPortal(), server.now());
            });
            await("back in the lobby", () -> server.world().players().stream()
                    .allMatch(p -> q.inLobby(p.session.character().position())));
            await("arena free", () -> rt.phase(q.id()) == pl.fawizja.pixel.game.BossQuestRuntime.Phase.IDLE);
            server.enqueueTest(() -> server.world().find("Mroz").session.teleport(q.squares().get(2).stand(), server.now()));
            await("on the square", () -> server.world().find("Mroz").session.character().position().equals(q.squares().get(2).stand()));
            cs.get(2).send("ACT", "open:" + lever.x() + ":" + lever.y() + ":" + lever.z());
            awaitMessage(cs.get(2), "only once", m -> m[0].equals("LOG") && m.length > 2 && m[2].contains("już ukończyła"));
        } finally {
            for (NetClient c : cs) c.close();
        }
    }

    /** Czeka na komunikat spelniajacy warunek (pozostale komunikaty sa pomijane). */
    @Test
    void altarQuestTorchesAndLeverWorkForFivePlayersOnline() throws Exception {
        pl.fawizja.pixel.world.AltarQuest q = server.world().world.altarQuests().get(0);
        String[] names = {"Pala", "Sora", "Kniga", "Drui", "Monka"};
        pl.fawizja.pixel.vocation.Vocation[] vocs = {pl.fawizja.pixel.vocation.Vocation.PALADIN,
                pl.fawizja.pixel.vocation.Vocation.SORCERER, pl.fawizja.pixel.vocation.Vocation.KNIGHT,
                pl.fawizja.pixel.vocation.Vocation.DRUID, pl.fawizja.pixel.vocation.Vocation.MONK};
        java.util.List<NetClient> cs = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < 5; i++) {
                NetClient c = connect();
                cs.add(c);
                c.hello(names[i], vocs[i].name(), "tajne-" + names[i]);
            }
            await("welcome", () -> cs.stream().allMatch(c -> c.playerId() > 0));
            // czterech na swoich polach: cztery pochodnie (WLIGHT do klientow), dzwignia nie dziala
            server.enqueueTest(() -> {
                for (ServerPlayer p : server.world().players()) {
                    if (p.session.character().vocation() != pl.fawizja.pixel.vocation.Vocation.MONK) {
                        p.session.teleport(q.slot(p.session.character().vocation()).stand(), server.now());
                    }
                }
            });
            Position knightTorch = q.slot(pl.fawizja.pixel.vocation.Vocation.KNIGHT).torch();
            awaitMessage(cs.get(4), "torch lit for everybody", m -> m[0].equals("WLIGHT") && m[1].equals("" + knightTorch.x())
                    && m[2].equals("" + knightTorch.y()) && m[4].equals("1"));
            await("4 torches on the server", () -> q.slots().stream()
                    .filter(sl -> server.world().world.wallLights().isLit(sl.torch())).count() == 4);
            Position lever = q.lever();
            cs.get(2).send("ACT", "open:" + lever.x() + ":" + lever.y() + ":" + lever.z());
            Thread.sleep(300);
            await("nobody moved", () -> server.world().players().stream()
                    .allMatch(p -> !q.inVault(p.session.character().position())));
            // piaty na miejscu: dzwignia przenosi wszystkich do skarbca
            server.enqueueTest(() -> server.world().find("Monka").session.teleport(
                    q.slot(pl.fawizja.pixel.vocation.Vocation.MONK).stand(), server.now()));
            await("5 torches", () -> q.slots().stream().allMatch(sl -> server.world().world.wallLights().isLit(sl.torch())));
            cs.get(2).send("ACT", "open:" + lever.x() + ":" + lever.y() + ":" + lever.z());
            await("all five in the vault", () -> server.world().players().stream().allMatch(p ->
                    p.session.character().position().equals(q.slot(p.session.character().vocation()).target())));
            await("torches out again", () -> q.slots().stream().noneMatch(sl -> server.world().world.wallLights().isLit(sl.torch())));
        } finally {
            for (NetClient c : cs) c.close();
        }
    }

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
            awaitMessage(ula, "party chat", m -> m[0].equals("CHAT") && m[1].equals("[Drużyna] Ola") && m[2].equals("idziemy na wilki"));

            long olaExp = self(ola).experience(), ulaExp = self(ula).experience();
            pl.fawizja.pixel.monster.MonsterType wolf = pl.fawizja.pixel.monster.MonsterRegistry
                    .loadDefault(ItemRegistry.loadDefault()).get("wolf");
            boolean[] shared = new boolean[1];
            long[] soloExp = {-1};
            int[] ulaHunt = {-1};
            server.enqueueTest(() -> {
                ServerPlayer killer = server.world().find("Ola");
                server.world().find("Ula").session.character().hunt().restore("wolf;3;0");
                // z premia potwora dnia, gdy dzis sa nim wilki
                soloExp[0] = killer.session.combat().killExperience(wolf, 8);
                shared[0] = server.world().shareKill(killer, wolf, server.now());
                ulaHunt[0] = server.world().find("Ula").session.character().hunt().progress();
            });
            await("kill shared", () -> soloExp[0] >= 0 && ulaHunt[0] >= 0);
            assertEquals(4, ulaHunt[0], "the party kill counts for Ula's hunting task");
            long solo = soloExp[0];
            // paladyn + druid: +20% za dwie osoby, +20% za dwie profesje
            long each = pl.fawizja.pixel.party.PartyRules.share(solo, 2, 40);
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
            awaitMessage(ola, "no friendly fire", m -> m[0].equals("LOG") && m[2].contains("drużyny"));

            ula.send("BYE");
            awaitMessage(ola, "party dissolved", m -> m[0].equals("PARTY") && m[1].equals("-"));
        }
    }

    @Test
    void fullDiversePartyGetsTwoHundredPercentAndTheLeaderCanKick() throws Exception {
        String[] names = {"Lider", "Pala", "Sora", "Drui", "Monka"};
        pl.fawizja.pixel.vocation.Vocation[] vocs = {pl.fawizja.pixel.vocation.Vocation.KNIGHT,
                pl.fawizja.pixel.vocation.Vocation.PALADIN, pl.fawizja.pixel.vocation.Vocation.SORCERER,
                pl.fawizja.pixel.vocation.Vocation.DRUID, pl.fawizja.pixel.vocation.Vocation.MONK};
        java.util.List<NetClient> cs = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < 5; i++) {
                NetClient c = connect();
                cs.add(c);
                c.hello(names[i], vocs[i].name(), "tajne-" + names[i]);
            }
            await("welcome", () -> cs.stream().allMatch(c -> c.playerId() > 0 && c.selfSave() != null));
            NetClient lead = cs.get(0);
            for (int i = 1; i < 5; i++) {
                lead.send("PARTY", "INVITE", names[i]);
                final int k = i;
                awaitMessage(cs.get(i), "invite card " + names[i], m -> m[0].equals("PARTYINV") && m[1].equals("Lider"));
                cs.get(k).send("PARTY", "ACCEPT", "Lider");
            }
            // kazdy dostaje PARTYINFO z poziomami, profesjami i premia +200%
            for (int i = 0; i < 5; i++) {
                awaitMessage(cs.get(i), "party info " + names[i], m -> {
                    pl.fawizja.pixel.party.PartyView v = pl.fawizja.pixel.party.PartyView.parse(m);
                    return v != null && v.members().size() == 5 && v.bonusPercent() == 200 && v.sharing()
                            && v.leader().equals("Lider") && v.distinctVocations() == 5;
                });
            }
            // wspolne zabicie: pula x3.0 dzielona na piec (kazdy 60% tego, co dostalby sam)
            pl.fawizja.pixel.monster.MonsterType wolf = pl.fawizja.pixel.monster.MonsterRegistry
                    .loadDefault(ItemRegistry.loadDefault()).get("wolf");
            long[] before = new long[5];
            long[] solo = new long[5];
            long[] after = {-1, -1, -1, -1, -1};
            boolean[] shared = new boolean[1];
            server.enqueueTest(() -> {
                for (int i = 0; i < 5; i++) {
                    ServerPlayer p = server.world().find(names[i]);
                    before[i] = p.session.character().experience();
                    solo[i] = p.session.combat().killExperience(wolf, p.session.character().level());
                }
                shared[0] = server.world().shareKill(server.world().find("Lider"), wolf, server.now());
                for (int i = 0; i < 5; i++) after[i] = server.world().find(names[i]).session.character().experience();
            });
            await("shared kill", () -> after[4] >= 0);
            assertTrue(shared[0]);
            for (int i = 0; i < 5; i++) {
                assertEquals(pl.fawizja.pixel.party.PartyRules.share(solo[i], 5, 200), after[i] - before[i], names[i]);
                assertEquals(Math.round(solo[i] * 0.6), after[i] - before[i], names[i]);
            }
            // za daleko: Monka nie dzieli, czterech dzieli z premia 4 osob / 4 profesji
            long[] far = {-1, -1};
            server.enqueueTest(() -> {
                ServerPlayer monk = server.world().find("Monka");
                monk.session.character().setPosition(server.world().find("Lider").session.character().position()
                        .translate(pl.fawizja.pixel.party.PartyRules.SHARE_RANGE + 1, 0));
                long m0 = monk.session.character().experience();
                ServerPlayer pala = server.world().find("Pala");
                long p0 = pala.session.character().experience();
                long ps = pala.session.combat().killExperience(wolf, pala.session.character().level());
                server.world().shareKill(server.world().find("Lider"), wolf, server.now());
                far[0] = monk.session.character().experience() - m0;
                far[1] = pala.session.character().experience() - p0 - pl.fawizja.pixel.party.PartyRules.share(ps, 4, 135);
            });
            await("far kill", () -> far[0] >= 0);
            assertEquals(0, far[0], "out of range: no share");
            assertEquals(0, far[1], "four in range share with 4 people / 4 vocations");

            // informacja o graczu do okna po dotknieciu
            cs.get(1).send("PINFO", "monka");
            awaitMessage(cs.get(1), "player info", m -> {
                pl.fawizja.pixel.social.PlayerInfo pi = pl.fawizja.pixel.social.PlayerInfo.parse(m);
                return pi != null && pi.name().equals("Monka") && pi.vocation() == pl.fawizja.pixel.vocation.Vocation.MONK
                        && pi.online() && pi.level() > 0;
            });
            // tylko lider wyrzuca; wyrzucony dostaje PARTY -, reszta nowa liste
            cs.get(1).send("PARTY", "KICK", "Sora");
            awaitMessage(cs.get(1), "not leader", m -> m[0].equals("LOG") && m[2].contains("Tylko lider"));
            lead.send("PARTY", "KICK", "Sora");
            awaitMessage(cs.get(2), "kicked", m -> m[0].equals("PARTY") && m[1].equals("-"));
            awaitMessage(cs.get(3), "new list", m -> m[0].equals("PARTY") && m[1].equals("Lider,Pala,Drui,Monka"));
            awaitMessage(cs.get(3), "new bonus", m -> {
                pl.fawizja.pixel.party.PartyView v = pl.fawizja.pixel.party.PartyView.parse(m);
                return v != null && v.members().size() == 4 && v.bonusPercent() == 135;
            });
        } finally {
            for (NetClient c : cs) c.close();
        }
    }

    @Test
    void playersTradeSafelyFaceToFace() throws Exception {
        try (NetClient ola = connect();
             NetClient ula = connect()) {
            ola.hello("Ola", "PALADIN", "tajne-Ola");
            ula.hello("Ula", "DRUID", "tajne-Ula");
            await("welcome", () -> ola.selfSave() != null && ula.selfSave() != null);
            long olaGold = pl.fawizja.pixel.economy.Money.totalValue(self(ola).backpack());
            long ulaGold = pl.fawizja.pixel.economy.Money.totalValue(self(ula).backpack());
            int ulaPotions = self(ula).backpack().count(30);
            server.enqueueTest(() -> {
                server.world().find("Ola").session.character().backpack().add(new pl.fawizja.pixel.item.ItemStack(30, 7));
                server.world().find("Ula").session.teleport(server.world().find("Ola").session.character().position().translate(1, 0), server.now());
            });
            ola.send("TRADE", "REQUEST", "Ula");
            awaitMessage(ula, "trade request", m -> m[0].equals("LOG") && m[2].contains("/handel Ola"));
            ula.send("TRADE", "REQUEST", "Ola");
            awaitMessage(ola, "trade open", m -> m[0].equals("LOG") && m[2].contains("otwarta"));
            ola.send("TRADE", "OFFER", "7 health potion");
            ula.send("TRADE", "OFFER", "700 gp");
            awaitMessage(ola, "both offers", m -> m[0].equals("LOG") && m[2].contains("Oferta Ula: 700 gp"));
            awaitMessage(ula, "trade window state", m -> m[0].equals("TS") && m.length == 6 && m[1].equals("Ola")
                    && m[2].equals("700 gp") && m[3].equals("7x Mikstura zdrowia"));
            ola.send("TRADE", "ACCEPT");
            ula.send("TRADE", "ACCEPT");
            await("goods swapped", () -> {
                try {
                    return pl.fawizja.pixel.economy.Money.totalValue(self(ola).backpack()) == olaGold + 700
                            && pl.fawizja.pixel.economy.Money.totalValue(self(ula).backpack()) == ulaGold - 700
                            && self(ula).backpack().count(30) == ulaPotions + 7;
                } catch (Exception e) {
                    return false;
                }
            });
            ola.send("TRADE", "OFFER", "1 rope");
            awaitMessage(ola, "trade is closed", m -> m[0].equals("LOG") && m[2].contains("Nie masz otwartej wymiany"));
        }
    }

    @Test
    void guildsCostGoldShowUpOverPlayersAndSurviveARestart() throws Exception {
        try (NetClient ola = connect();
             NetClient ula = connect()) {
            ola.hello("Ola", "PALADIN", "tajne-Ola");
            ula.hello("Ula", "DRUID", "tajne-Ula");
            await("welcome", () -> ola.selfSave() != null && ula.selfSave() != null);
            ola.send("GUILD", "CREATE", "Wilcze Kly");
            awaitMessage(ola, "too poor", m -> m[0].equals("LOG") && m[2].contains("kosztuje"));
            server.enqueueTest(() -> pl.fawizja.pixel.economy.Money.add(
                    server.world().find("Ola").session.character().backpack(), 50_000));
            long before = 50_000 + pl.fawizja.pixel.economy.Money.totalValue(self(ola).backpack());
            await("gold arrived", () -> {
                try {
                    return pl.fawizja.pixel.economy.Money.totalValue(self(ola).backpack()) == before;
                } catch (Exception e) {
                    return false;
                }
            });
            ola.send("GUILD", "CREATE", "Wilcze Kly");
            awaitMessage(ola, "founded", m -> m[0].equals("LOG") && m[2].contains("Założono gildię"));
            await("founding fee paid", () -> {
                try {
                    return pl.fawizja.pixel.economy.Money.totalValue(self(ola).backpack())
                            == before - pl.fawizja.pixel.guild.GuildRegistry.FOUNDING_COST;
                } catch (Exception e) {
                    return false;
                }
            });
            ola.send("GUILD", "INVITE", "Ula");
            awaitMessage(ula, "invite", m -> m[0].equals("LOG") && m[2].contains("/gildia przyjmij Wilcze Kly"));
            ula.send("GUILD", "ACCEPT", "Wilcze Kly");
            await("ula shown with the guild tag", () -> {
                RemoteEntity u = seen(ola, "Ula");
                return u != null && "Wilcze Kly".equals(u.guild());
            });
            ula.send("GCHAT", "hej");
            awaitMessage(ola, "guild chat", m -> m[0].equals("CHAT") && m[1].equals("[Wilcze Kly] Ula") && m[2].equals("hej"));
            ula.send("GUILD", "MOTD", "ja tu rządzę");
            awaitMessage(ula, "only the leader", m -> m[0].equals("LOG") && m[2].contains("Tylko lider"));
            ola.send("GUILD", "MOTD", "Zbiórka o 20:00");
            awaitMessage(ula, "motd broadcast", m -> m[0].equals("LOG") && m[2].contains("Wiadomość gildii: Zbiórka o 20:00"));
            java.util.concurrent.atomic.AtomicBoolean rich = new java.util.concurrent.atomic.AtomicBoolean();
            server.enqueueTest(() -> {
                server.world().find("Ula").session.character().bank().credit(1000);
                rich.set(true);
            });
            await("ula has gold", rich::get);
            ula.send("GUILD", "DEPOSIT", "200");
            awaitMessage(ola, "deposit", m -> m[0].equals("LOG") && m[2].startsWith("[Gildia] Ula wpłaca 200 gp do skarbca"));
            ula.send("GUILD", "WITHDRAW", "100");
            awaitMessage(ula, "only the leader withdraws", m -> m[0].equals("LOG") && m[2].contains("Tylko lider"));
            ola.send("GUILD", "WITHDRAW", "500");
            awaitMessage(ola, "not enough", m -> m[0].equals("LOG") && m[2].contains("nie ma tyle"));
            ola.send("GUILD", "WITHDRAW", "150");
            awaitMessage(ula, "withdrawn", m -> m[0].equals("LOG") && m[2].startsWith("[Gildia] Ola wypłaca 150 gp ze skarbca (w skarbcu: 50 gp)"));
            ola.send("GUILD", "BANNER");
            awaitMessage(ola, "banner too expensive", m -> m[0].equals("LOG") && m[2].startsWith("Sztandar kosztuje"));
            java.util.concurrent.atomic.AtomicBoolean funded = new java.util.concurrent.atomic.AtomicBoolean();
            server.enqueueTest(() -> {
                server.world().find("Ula").session.character().bank().credit(ServerWorld.BANNER_COST);
                funded.set(true);
            });
            await("funded", funded::get);
            ula.send("GUILD", "DEPOSIT", String.valueOf(ServerWorld.BANNER_COST));
            awaitMessage(ola, "big deposit", m -> m[0].equals("LOG") && m[2].startsWith("[Gildia] Ula wpłaca"));
            ola.send("GUILD", "BANNER");
            awaitMessage(ula, "banner raised", m -> m[0].equals("LOG") && m[2].startsWith("[Gildia] Ola wznosi sztandar gildii: +10%"));
            await("members get the bonus", () -> server.world().find("Ula").session.combat().bonusExpPercent() == ServerWorld.BANNER_PERCENT);
            ula.send("EVENTS");
            awaitMessage(ula, "banner in events", m -> m[0].equals("EVENTS") && m.length > 2 && m[2].startsWith("sztandar gildii +10%, 60 min"));
            ula.send("GUILD", "MEMBERS");
            awaitMessage(ula, "member list", m -> m[0].equals("LOG") && m[2].startsWith("Członkowie Wilcze Kly (2, w grze 2): ")
                    && m[2].contains("Ola [lider] (poz. ") && m[2].contains("w grze"));
            ula.send("RANK", "gildie");
            String[][] rank = new String[1][];
            awaitMessage(ula, "guild ranking", m -> m[0].equals("RANK") && (rank[0] = m) != null);
            pl.fawizja.pixel.ranking.RankingView gv = pl.fawizja.pixel.ranking.RankingView.parse(rank[0]);
            assertEquals(pl.fawizja.pixel.ranking.GuildRanking.TITLE, gv.title());
            assertEquals(1, gv.mine());
            assertEquals("Wilcze Kly", gv.rows().get(0).name());
        }
        await("both left", () -> server.world().players().isEmpty());
        server.close();
        server = new PixelServer(0, saves);
        server.start();
        try (NetClient a = connect();
             NetClient b = connect()) {
            a.hello("Ola", "PALADIN", "tajne-Ola");
            b.hello("Ula", "DRUID", "tajne-Ula");
            await("guild remembered after restart", () -> {
                RemoteEntity u = seen(a, "Ula");
                return u != null && "Wilcze Kly".equals(u.guild());
            });
            awaitMessage(b, "motd on login", m -> m[0].equals("LOG") && m[2].contains("Wiadomość gildii Wilcze Kly: Zbiórka o 20:00"));
        }
    }

    @Test
    void privateMessagesAndModerationBansMutesAndKicks() throws Exception {
        assertArrayEquals(new String[] {"BAN", "Stary Wilk", "24"}, PixelServer.consoleCommand("ban Stary Wilk 24"));
        assertArrayEquals(new String[] {"SAY", "restart za 5 min"}, PixelServer.consoleCommand("say restart za 5 min"));
        assertArrayEquals(new String[] {"WHO"}, PixelServer.consoleCommand(" who "));
        assertNull(PixelServer.consoleCommand("   "));
        try (NetClient ola = connect();
             NetClient ula = connect()) {
            ola.hello("Ola", "PALADIN", "tajne-Ola");
            ula.hello("Ula", "DRUID", "tajne-Ula");
            await("welcome", () -> ola.selfSave() != null && ula.selfSave() != null);
            ula.send("PM", "ola", "psst");
            awaitMessage(ola, "private message", m -> m[0].equals("CHAT") && m[1].equals("[od Ula]") && m[2].equals("psst"));
            awaitMessage(ula, "echo", m -> m[0].equals("CHAT") && m[1].equals("[do Ola]") && m.length > 3 && m[3].equals("pm"));
            // kanal Handel (zakladka czatu): wszyscy dostaja wiadomosc z etykieta i polem kanalu
            ula.send("TCHAT", "sprzedam futro");
            awaitMessage(ola, "trade channel", m -> m[0].equals("CHAT") && m[1].equals("[Handel] Ula") && m[2].equals("sprzedam futro")
                    && m.length > 3 && m[3].equals("trade"));

            ula.send("ADMIN", "KICK", "Ola");
            awaitMessage(ula, "not an admin", m -> m[0].equals("LOG") && m[2].contains("Brak uprawnień"));
            String[] answer = new String[1];
            server.enqueueTest(() -> answer[0] = server.world().moderate(new String[] {"ADMIN", "Ola"}, "konsola", server.now()));
            await("admin added", () -> answer[0] != null);
            assertTrue(new java.io.File(saves, "admins.txt").isFile());

            ola.send("ADMIN", "MUTE", "Ula", "10");
            awaitMessage(ula, "muted", m -> m[0].equals("LOG") && m[2].contains("Wyciszono"));
            ula.send("CHAT", "spam");
            awaitMessage(ula, "chat blocked", m -> m[0].equals("LOG") && m[2].contains("wyciszony"));

            ola.send("ADMIN", "SAY", "restart o 20:00");
            awaitMessage(ula, "broadcast", m -> m[0].equals("CHAT") && m[1].equals("[Ogłoszenie]"));

            ola.send("ADMIN", "BAN", "Ula", "2");
            await("ula kicked", () -> ula.error() != null && ula.error().contains("zablokowane"));
        }
        await("left", () -> server.world().players().isEmpty());
        try (NetClient again = connect()) {
            again.hello("Ula", "DRUID", "tajne-Ula");
            await("ban survives relog", () -> again.error() != null && again.error().contains("zablokowane"));
        }
        // ban przetrwa restart serwera, a po uplywie czasu wygasa
        server.close();
        server = new PixelServer(0, saves);
        server.start();
        try (NetClient again = connect()) {
            again.hello("Ula", "DRUID", "tajne-Ula");
            await("ban after restart", () -> again.error() != null && again.error().contains("zablokowane"));
        }
        server.world().wallClock = () -> System.currentTimeMillis() + 3 * 3_600_000L;
        try (NetClient later = connect()) {
            later.hello("Ula", "DRUID", "tajne-Ula");
            await("ban expired", () -> later.selfSave() != null);
        }
    }

    @Test
    void rankingListsOfflineCharactersAfterARestartAndFiltersByVocation() throws Exception {
        try (NetClient ola = connect();
             NetClient ula = connect()) {
            ola.hello("Ola", "PALADIN", "tajne-Ola");
            ula.hello("Ula", "DRUID", "tajne-Ula");
            await("welcome", () -> ola.selfSave() != null && ula.selfSave() != null);
            server.enqueueTest(() -> server.world().find("Ula").session.character().addExperience(500_000));
            ola.send("RANK");
            String[][] got = new String[1][];
            awaitMessage(ola, "ranking", m -> m[0].equals("RANK") && (got[0] = m) != null);
            pl.fawizja.pixel.ranking.RankingView v = pl.fawizja.pixel.ranking.RankingView.parse(got[0]);
            assertEquals("Ula", v.rows().get(0).name(), "more experience = first place");
            assertEquals(2, v.mine());
            assertEquals(2, v.total());
        }
        await("left", () -> server.world().players().isEmpty());
        server.close();
        server = new PixelServer(0, saves);
        server.start();
        try (NetClient kuba = connect()) {
            kuba.hello("Kuba", "MONK", "tajne-Kuba");
            await("welcome", () -> kuba.selfSave() != null);
            kuba.send("RANK", "druid");
            String[][] got = new String[1][];
            awaitMessage(kuba, "druid ranking", m -> m[0].equals("RANK") && (got[0] = m) != null);
            pl.fawizja.pixel.ranking.RankingView v = pl.fawizja.pixel.ranking.RankingView.parse(got[0]);
            assertEquals("Ranking: Druid", v.title());
            assertEquals(1, v.total(), "offline druid from the saves");
            assertEquals("Ula", v.rows().get(0).name());
            assertEquals(0, v.mine(), "a monk is not in the druid ranking");
            kuba.send("RANK", "krasnolud");
            awaitMessage(kuba, "unknown vocation", m -> m[0].equals("LOG") && m[2].contains("Nie znam profesji"));
            kuba.send("RANK", "pięści");
            String[][] skill = new String[1][];
            awaitMessage(kuba, "skill ranking", m -> m[0].equals("RANK") && (skill[0] = m) != null);
            pl.fawizja.pixel.ranking.RankingView sv = pl.fawizja.pixel.ranking.RankingView.parse(skill[0]);
            assertEquals("Umiejętność: Pięści", sv.title());
            assertEquals(3, sv.rows().size(), "online and offline characters");
            assertTrue(sv.rows().get(0).level() > 0, "offline entries keep their skills");
            assertTrue(sv.mine() > 0);
        }
    }

    @Test
    void friendsAreToldWhenSomeoneComesAndGoesAndTheListIsSaved() throws Exception {
        try (NetClient ola = connect()) {
            ola.hello("Ola", "PALADIN", "tajne-Ola");
            await("welcome", () -> ola.selfSave() != null);
            try (NetClient ula = connect()) {
                ula.hello("Ula", "DRUID", "tajne-Ula");
                await("welcome", () -> ula.selfSave() != null);
                ola.send("FRIEND", "ADD", "ula");
                String[][] got = new String[1][];
                awaitMessage(ola, "friend list", m -> m[0].equals("FRIENDS") && (got[0] = m) != null);
                pl.fawizja.pixel.friends.FriendsView v = pl.fawizja.pixel.friends.FriendsView.parse(got[0]);
                assertEquals(1, v.rows().size());
                assertEquals("Ula", v.rows().get(0).name(), "the name as the character has it");
                assertTrue(v.rows().get(0).online());
                assertEquals("Druid", v.rows().get(0).vocation());
                ola.send("FRIEND", "ADD", "Ola");
                awaitMessage(ola, "self", m -> m[0].equals("LOG") && m[2].contains("siebie"));
                ola.send("FRIEND", "ADD", "Nikt Taki");
                awaitMessage(ola, "unknown", m -> m[0].equals("LOG") && m[2].contains("Nie ma takiej postaci"));
            }
            awaitMessage(ola, "friend left", m -> m[0].equals("LOG") && m[2].contains("Znajomy Ula wyszedł z gry."));
            try (NetClient ula = connect()) {
                ula.hello("Ula", "DRUID", "tajne-Ula");
                awaitMessage(ola, "friend came", m -> m[0].equals("LOG") && m[2].contains("Znajomy Ula jest teraz w grze."));
            }
            // dopiero gdy serwer obsluzy wyjscie, lista pokaze Ule jako offline (inaczej wyscig z rozlaczeniem)
            awaitMessage(ola, "friend left again", m -> m[0].equals("LOG") && m[2].contains("Znajomy Ula wyszedł z gry."));
            ola.send("FRIEND", "LIST");
            String[][] got = new String[1][];
            awaitMessage(ola, "offline friend", m -> m[0].equals("FRIENDS") && (got[0] = m) != null
                    && !pl.fawizja.pixel.friends.FriendsView.parse(m).rows().get(0).online());
            assertEquals(8, pl.fawizja.pixel.friends.FriendsView.parse(got[0]).rows().get(0).level(), "offline: from the ranking");
            assertEquals(0, pl.fawizja.pixel.friends.FriendsView.parse(got[0]).rows().get(0).minutesAgo(), "just saved on leaving");
            await("saved friends", () -> {
                try {
                    return self(ola).friends().contains("Ula");
                } catch (Exception e) {
                    return false;
                }
            });
            ola.send("FRIEND", "REMOVE", "ULA");
            awaitMessage(ola, "empty list", m -> m[0].equals("FRIENDS") && m.length == 1);
        }
    }

    @Test
    void privateMessagesToOfflinePlayersWaitAsLetters() throws Exception {
        try (NetClient ula = connect()) {
            ula.hello("Ula", "DRUID", "tajne-Ula");
            await("welcome", () -> ula.selfSave() != null);
        }
        await("ula left", () -> server.world().find("Ula") == null);
        try (NetClient ola = connect()) {
            ola.hello("Ola", "PALADIN", "tajne-Ola");
            await("welcome", () -> ola.selfSave() != null);
            ola.send("PM", "Nikt", "halo");
            awaitMessage(ola, "no such character", m -> m[0].equals("LOG") && m[2].startsWith("Nie ma postaci Nikt"));
            ola.send("PM", "Ula", "spotkajmy się przy moście");
            awaitMessage(ola, "letter left", m -> m[0].equals("LOG") && m[2].startsWith("Ula nie jest w grze"));
        }
        try (NetClient ula = connect()) {
            ula.hello("Ula", "DRUID", "tajne-Ula");
            awaitMessage(ula, "letter delivered", m -> m[0].equals("CHAT") && m[1].equals("[list] Ola") && m[2].equals("spotkajmy się przy moście"));
        }
    }

    @Test
    void buyOrdersLockGoldAndDeliverItemsToTheBuyersDepot() throws Exception {
        Position banker = new Position(489, 497, 0);
        int pelt = ItemRegistry.loadDefault().byKey("wolf_pelt").id();
        try (NetClient ola = connect();
             NetClient ula = connect()) {
            ola.hello("Ola", "PALADIN", "tajne-Ola");
            ula.hello("Ula", "DRUID", "tajne-Ula");
            await("welcome", () -> ola.selfSave() != null && ula.selfSave() != null);
            ola.send("WANT", "ADD", "5 wilcze futro 2000");
            awaitMessage(ola, "not at a banker", m -> m[0].equals("LOG") && m[2].contains("bankier"));
            long[] banks = new long[2];
            java.util.concurrent.atomic.AtomicBoolean ready = new java.util.concurrent.atomic.AtomicBoolean();
            server.enqueueTest(() -> {
                for (ServerPlayer p : server.world().players()) p.session.teleport(banker, server.now());
                PlayerCharacter o = server.world().find("Ola").session.character();
                o.bank().credit(5_000);
                server.world().find("Ula").session.character().backpack().add(new pl.fawizja.pixel.item.ItemStack(pelt, 10));
                banks[0] = o.bank().balance();
                banks[1] = server.world().find("Ula").session.character().bank().balance();
                ready.set(true);
            });
            await("ready", ready::get);
            ola.send("WANT", "ADD", "5 wilcze futro 2000");
            awaitMessage(ola, "order listed", m -> m[0].equals("WANT") && m.length == 2 && m[1].startsWith("#1 5x Wilcze futro;")
                    && m[1].contains("Twoje"));
            ula.send("WANT", "FILL", "1");
            awaitMessage(ola, "delivered", m -> m[0].equals("LOG") && m[2].startsWith("Poczta: Ula przysyła 5x Wilcze futro"));
            awaitMessage(ula, "paid", m -> m[0].equals("LOG") && m[2].startsWith("Zrealizowano zamówienie #1"));
            ula.send("PRICE", "wolf_pelt");
            awaitMessage(ula, "price", m -> m[0].equals("LOG") && m[2].startsWith("Rynek graczy, Wilcze futro: ostatnia sprzedaż 400"));
            ola.send("WANT", "ADD", "1 wilcze futro 700");
            awaitMessage(ola, "second order", m -> m[0].equals("WANT") && m.length == 2 && m[1].startsWith("#2 "));
            ola.send("WANT", "CANCEL", "2");
            awaitMessage(ola, "cancelled", m -> m[0].equals("LOG") && m[2].startsWith("Wycofano zamówienie #2"));
            long[] after = new long[3];
            java.util.concurrent.atomic.AtomicBoolean checked = new java.util.concurrent.atomic.AtomicBoolean();
            server.enqueueTest(() -> {
                PlayerCharacter o = server.world().find("Ola").session.character();
                after[0] = o.bank().balance();
                after[1] = server.world().find("Ula").session.character().bank().balance();
                after[2] = o.depot().count(pelt);
                checked.set(true);
            });
            await("checked", checked::get);
            assertEquals(banks[0] - 2000, after[0], "buyer paid once, cancelled order refunded");
            assertEquals(banks[1] + 2000, after[1], "seller paid without fee");
            assertEquals(5, after[2], "items in the buyer's depot");
            ola.send("WANT", "ADD", "2 wilcze futro 900");
            awaitMessage(ola, "third order", m -> m[0].equals("WANT") && m.length == 2 && m[1].startsWith("#3 "));
            long base = server.world().wallClock.getAsLong();
            server.enqueueTest(() -> {
                server.world().wallClock = () -> base + pl.fawizja.pixel.market.BuyOrders.EXPIRE_MS + 60_000;
                server.world().expireOrders(server.now());
            });
            awaitMessage(ola, "expired", m -> m[0].equals("LOG") && m[2].startsWith("Zamówienie #3") && m[2].contains("wygasło"));
        }
    }

    @Test
    void theMarketSellsToOthersAndPaysSellersEvenWhenTheyAreOffline() throws Exception {
        java.util.function.Function<NetClient, pl.fawizja.pixel.market.MarketView> view = c -> {
            String[][] got = new String[1][];
            try {
                awaitMessage(c, "market", m -> m[0].equals("MARKET") && (got[0] = m) != null);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return pl.fawizja.pixel.market.MarketView.parse(got[0]);
        };
        Position banker = new Position(489, 497, 0);
        int pelt = ItemRegistry.loadDefault().byKey("wolf_pelt").id();
        try (NetClient ula = connect()) {
            ula.hello("Ula", "DRUID", "tajne-Ula");
            await("welcome", () -> ula.selfSave() != null);
            int[] sold = new int[1];
            try (NetClient ola = connect()) {
                ola.hello("Ola", "PALADIN", "tajne-Ola");
                await("welcome", () -> ola.selfSave() != null);
                ola.send("MARKET", "SELL", "5 wilcze futro 300");
                awaitMessage(ola, "not at a banker", m -> m[0].equals("LOG") && m[2].contains("bankier"));
                server.enqueueTest(() -> {
                    for (ServerPlayer p : server.world().players()) {
                        p.session.teleport(banker, server.now());
                        p.session.character().backpack().add(new pl.fawizja.pixel.item.ItemStack(pelt, 10));
                    }
                });
                await("at the banker", () -> {
                    try {
                        return self(ola).position().equals(banker) && self(ula).position().equals(banker);
                    } catch (Exception e) {
                        return false;
                    }
                });
                ola.send("MARKET", "SELL", "5 wilcze futro 300");
                pl.fawizja.pixel.market.MarketView v = view.apply(ola);
                assertEquals(1, v.rows().size());
                assertTrue(v.rows().get(0).mine());
                assertEquals("Wilcze futro", v.rows().get(0).item());
                int id = v.rows().get(0).id();
                ola.send("MARKET", "BUY", String.valueOf(id));
                awaitMessage(ola, "own offer", m -> m[0].equals("LOG") && m[2].contains("Twoja oferta"));
                ula.send("MARKET", "BUY", String.valueOf(id));
                awaitMessage(ola, "seller told", m -> m[0].equals("LOG") && m[2].contains("Ula kupił 5x Wilcze futro"));
                await("goods and money moved", () -> {
                    try {
                        return self(ula).backpack().count(pelt) == 15 && self(ola).bank().balance() == 294 + LOGIN_BONUS
                                && self(ola).backpack().count(pelt) == 5;
                    } catch (Exception e) {
                        return false;
                    }
                });
                ola.send("MARKET", "SELL", "5 wilcze 1000");
                String[][] mine = new String[1][];
                awaitMessage(ola, "second offer", m -> m[0].equals("MARKET") && m.length > 3 && m[3].contains(";1000;") && (mine[0] = m) != null);
                sold[0] = pl.fawizja.pixel.market.MarketView.parse(mine[0]).rows().get(0).id();
            }
            await("ola left", () -> server.world().find("Ola") == null);
            ula.send("MARKET", "LIST", "futro");
            String[][] list = new String[1][];
            awaitMessage(ula, "search", m -> m[0].equals("MARKET") && m[2].equals("futro") && (list[0] = m) != null);
            pl.fawizja.pixel.market.MarketView found = pl.fawizja.pixel.market.MarketView.parse(list[0]);
            assertEquals("futro", found.search());
            assertEquals(1, found.rows().size(), "the offline seller's offer is still there");
            ula.send("MARKET", "BUY", String.valueOf(sold[0]));
            await("bought from an offline seller", () -> {
                try {
                    return self(ula).backpack().count(pelt) == 20;
                } catch (Exception e) {
                    return false;
                }
            });
        }
        try (NetClient ola = connect()) {
            ola.hello("Ola", "PALADIN", "tajne-Ola");
            awaitMessage(ola, "paid on return", m -> m[0].equals("LOG") && m[2].contains("Pod Twoją nieobecność"));
            await("balance", () -> {
                try {
                    return self(ola).bank().balance() == 294 + 980 + LOGIN_BONUS; // drugie wejscie tego dnia: bez nowej nagrody
                } catch (Exception e) {
                    return false;
                }
            });
        }
    }

    @Test
    void anInvasionIsAnnouncedFoughtOffAndRewarded() throws Exception {
        try (NetClient ola = connect()) {
            ola.hello("Ola", "PALADIN", "tajne-Ola");
            await("welcome", () -> ola.selfSave() != null);
            ola.send("ADMIN", "INVASION", "Varden");
            awaitMessage(ola, "admins only", m -> m[0].equals("LOG") && m[2].contains("Brak uprawnień"));
            String[] answer = new String[1];
            server.enqueueTest(() -> answer[0] = server.world().moderate(new String[] {"INVASION", "Varden"}, "konsola", server.now()));
            await("started", () -> answer[0] != null);
            assertTrue(answer[0].startsWith("Inwazja na Varden rozpoczęta"), answer[0]);
            String[] status = new String[2];
            server.enqueueTest(() -> {
                status[0] = server.world().moderate(new String[] {"STATUS"}, "konsola", server.now());
                status[1] = server.world().moderate(new String[] {"SAVE"}, "konsola", server.now());
            });
            await("status", () -> status[1] != null);
            assertTrue(status[0].contains("graczy: 1") && status[0].contains("inwazja: Varden"), status[0]);
            assertEquals("Zapisano 1 postaci.", status[1]);
            awaitMessage(ola, "announced", m -> m[0].equals("LOG") && m[2].startsWith("Inwazja! Bandyci atakują Varden"));
            int[] total = new int[1];
            server.enqueueTest(() -> {
                ServerWorld w = server.world();
                total[0] = w.invasion().total();
                assertEquals("Trwa już inwazja na Varden.", w.startInvasion(null, server.now()));
                ServerPlayer p = w.find("Ola");
                for (pl.fawizja.pixel.monster.Monster m : new java.util.ArrayList<>(w.monsters.monsters())) {
                    if (w.invasion() != null && w.invasion().aliveIds().contains(m.id())) {
                        p.session.combat().damageMonster(m, 1_000_000, pl.fawizja.pixel.combat.DamageType.FIRE, server.now());
                    }
                }
            });
            awaitMessage(ola, "defended", m -> m[0].equals("LOG") && m[2].startsWith("Varden obronione! Najwięcej pokonał(a): Ola"));
            long expected = total[0] * pl.fawizja.pixel.event.Invasion.REWARD_PER_KILL + pl.fawizja.pixel.event.Invasion.LEADER_BONUS;
            await("reward on the account", () -> {
                try {
                    return self(ola).bank().balance() == expected + LOGIN_BONUS;
                } catch (Exception e) {
                    return false;
                }
            });
            assertNull(server.world().invasion());
        }
    }

    @Test
    void otherPlayersSeeWhoRidesAHorse() throws Exception {
        try (NetClient ola = connect();
             NetClient ula = connect()) {
            ola.hello("Ola", "PALADIN", "tajne-Ola");
            ula.hello("Ula", "DRUID", "tajne-Ula");
            await("both see each other", () -> seen(ula, "Ola") != null);
            assertFalse(seen(ula, "Ola").mounted());
            server.enqueueTest(() -> server.world().find("Ola").session.character().setHasMount(true));
            ola.send("ACT", "mount");
            await("ola rides", () -> seen(ula, "Ola") != null && seen(ula, "Ola").mounted());
            ola.send("ACT", "mount");
            await("ola walks", () -> seen(ula, "Ola") != null && !seen(ula, "Ola").mounted());
        }
    }

    @Test
    void anyoneCanListPlayersInGame() throws Exception {
        try (NetClient ola = connect();
             NetClient ula = connect()) {
            ola.hello("Ola", "PALADIN", "tajne-Ola");
            ula.hello("Ula", "DRUID", "tajne-Ula");
            await("both see each other", () -> seen(ula, "Ola") != null);
            server.enqueueTest(() -> server.world().find("Ula").session.character().addExperience(
                    pl.fawizja.pixel.balance.Balance.experienceForLevel(20)));
            ula.send("WHO");
            awaitMessage(ula, "player list", m -> m[0].equals("WHO") && m.length == 3
                    && m[1].startsWith("Ula;") && m[1].contains("poziom 2") && m[2].startsWith("Ola;Paladin, poziom 8"));
            ola.send("LFG");
            awaitMessage(ola, "lfg on", m -> m[0].equals("LOG") && m[2].startsWith("Szukasz drużyny") && m[2].contains("Powiadomieni gracze o podobnym poziomie: 0"));
            ula.send("WHO");
            awaitMessage(ula, "lfg in list", m -> m[0].equals("WHO") && m.length == 3 && m[2].startsWith("Ola;") && m[2].contains("szuka drużyny"));
            ola.send("LFG");
            awaitMessage(ola, "lfg off", m -> m[0].equals("LOG") && m[2].startsWith("Nie szukasz już drużyny"));
        }
    }

    @Test
    void otherPlayersSeePetsAndTitles() throws Exception {
        try (NetClient ola = connect();
             NetClient ula = connect()) {
            ola.hello("Ola", "PALADIN", "tajne-Ola");
            ula.hello("Ula", "DRUID", "tajne-Ula");
            await("both see each other", () -> seen(ula, "Ola") != null);
            assertNull(seen(ula, "Ola").pet());
            assertNull(seen(ula, "Ola").title());
            ola.send("TITLE", "LEVEL_20");
            awaitMessage(ola, "not earned yet", m -> m[0].equals("LOG") && m[2].startsWith("Ten tytuł trzeba najpierw zdobyć"));
            server.enqueueTest(() -> {
                pl.fawizja.pixel.character.PlayerCharacter pc = server.world().find("Ola").session.character();
                pc.achievements().restore("LEVEL_20");
                pc.backpack().add(new pl.fawizja.pixel.item.ItemStack(ItemRegistry.loadDefault().byKey("pet_raven").id(), 1));
                pc.setPet("pet_raven");
            });
            ola.send("TITLE", "LEVEL_20");
            await("ula sees the pet and the title", () -> seen(ula, "Ola") != null
                    && "pet_raven".equals(seen(ula, "Ola").pet()) && "LEVEL_20".equals(seen(ula, "Ola").title()));
            ola.send("TITLE", "-");
            await("title removed", () -> seen(ula, "Ola") != null && seen(ula, "Ola").title() == null);
            assertEquals(0, seen(ula, "Ola").outfit());
            ola.send("OUTFIT", "złoty");
            await("ula sees the gold outfit", () -> seen(ula, "Ola") != null && seen(ula, "Ola").outfit() == 7);
            server.enqueueTest(() -> {
                pl.fawizja.pixel.character.PlayerCharacter pc = server.world().find("Ola").session.character();
                server.world().find("Ola").session.teleport(new pl.fawizja.pixel.world.Position(149, 348, 0), server.now());
                pc.backpack().add(new pl.fawizja.pixel.item.ItemStack(ItemRegistry.loadDefault().byKey("challenge_scroll").id(), 1));
                int slot = -1;
                for (int i = 0; i < pc.backpack().size(); i++) {
                    if (pc.backpack().get(i) != null && pc.backpack().get(i).itemId() == ItemRegistry.loadDefault().byKey("challenge_scroll").id()) slot = i;
                }
                server.world().find("Ola").session.useSlot(slot, server.now());
            });
            awaitMessage(ola, "challenge line sent", m -> m[0].equals("CHAL") && m[1].startsWith("Wyzwanie: fala 1/3"));
        }
    }

    @Test
    void aScheduledShutdownWarnsSavesAndStopsTheServer() throws Exception {
        try (NetClient ola = connect()) {
            ola.hello("Ola", "PALADIN", "tajne-Ola");
            await("welcome", () -> ola.selfSave() != null);
            java.util.concurrent.atomic.AtomicReference<String> r = new java.util.concurrent.atomic.AtomicReference<>();
            server.enqueueTest(() -> r.set(server.world().moderate(new String[] {"SHUTDOWN", "12s"}, "Ola", server.now())));
            await("refused for players", () -> r.get() != null);
            assertTrue(r.get().contains("tylko konsola"), r.get());
            server.enqueueTest(() -> server.world().moderate(new String[] {"SHUTDOWN", "4s"}, "konsola", server.now()));
            awaitMessage(ola, "warning", m -> m[0].equals("CHAT") && m[2].contains("wyłączony za 4 s"));
            await("kicked", () -> ola.error() != null && ola.error().contains("Serwer jest wyłączany"));
        }
        await("world stopped", () -> server.world().shutdownDone());
        assertTrue(server.world().saved("Ola"));
        assertEquals("1 min 30 s", ServerWorld.describeLeft(90_000));
        assertEquals("5 min", ServerWorld.describeLeft(300_000));
    }

    @Test
    void playersCanInspectEachOthersEquipment() throws Exception {
        try (NetClient ola = connect(); NetClient ula = connect()) {
            ola.hello("Ola", "PALADIN", "tajne-Ola");
            ula.hello("Ula", "DRUID", "tajne-Ula");
            await("welcome", () -> ola.selfSave() != null && ula.selfSave() != null);
            ola.send("INSPECT", "ula");
            awaitMessage(ola, "header", m -> m[0].equals("LOG") && m[2].startsWith("Ula: Druid, poziom "));
            awaitMessage(ola, "gear", m -> m[0].equals("LOG") && (m[2].startsWith("Założone: ") || m[2].startsWith("Nic nie ma")));
            awaitMessage(ula, "told", m -> m[0].equals("LOG") && m[2].contains("Ola ogląda twój ekwipunek."));
            ola.send("INSPECT", "Nikt");
            awaitMessage(ola, "missing", m -> m[0].equals("LOG") && m[2].contains("Nie ma takiego gracza"));
        }
    }

    @Test
    void reportsReachOnlineAdminsAndAreSaved() throws Exception {
        try (NetClient ola = connect(); NetClient ula = connect()) {
            ola.hello("Ola", "PALADIN", "tajne-Ola");
            ula.hello("Ula", "DRUID", "tajne-Ula");
            await("welcome", () -> ola.selfSave() != null && ula.selfSave() != null);
            java.util.concurrent.atomic.AtomicBoolean admin = new java.util.concurrent.atomic.AtomicBoolean();
            server.enqueueTest(() -> {
                server.world().moderate(new String[] {"ADMIN", "Ola"}, "konsola", server.now());
                admin.set(true);
            });
            await("admin", admin::get);
            ula.send("REPORT", "Ola", "test zgłoszenia");
            awaitMessage(ola, "admin warned", m -> m[0].equals("LOG") && m[2].contains("[Zgłoszenie] Ula zgłasza Ola: test zgłoszenia"));
            awaitMessage(ula, "confirmed", m -> m[0].equals("LOG") && m[2].startsWith("Zgłoszenie wysłane do administracji (w grze: 1)"));
            ula.send("REPORT", "Ola", "drugie");
            awaitMessage(ula, "rate limited", m -> m[0].equals("LOG") && m[2].contains("za minutę"));
            assertTrue(Files.readString(new File(saves, "reports.txt").toPath()).contains(";Ula;Ola;test zgłoszenia"));
            ola.send("ADMIN", "REPORTS");
            awaitMessage(ola, "admin reads reports", m -> m[0].equals("LOG") && m[2].startsWith("Zgłoszenia (1, ostatnie 1): ")
                    && m[2].contains(" Ula -> Ola: test zgłoszenia"));
        }
    }

    @Test
    void theWeeklyRankingCountsExperienceGainedThisWeek() throws Exception {
        try (NetClient ola = connect()) {
            ola.hello("Ola", "PALADIN", "tajne-Ola");
            await("welcome", () -> ola.selfSave() != null);
            ola.send("RANK", "tydzień");
            awaitMessage(ola, "empty week", m -> m[0].equals("RANK") && m[1].equals("Ranking tygodnia") && m[3].equals("0"));
            server.enqueueTest(() -> server.world().find("Ola").session.character().addExperience(5_000));
            ola.send("RANK", "tydzien");
            String[][] got = new String[1][];
            awaitMessage(ola, "gained", m -> m[0].equals("RANK") && m[1].equals("Ranking tygodnia") && m.length > 4 && (got[0] = m) != null);
            pl.fawizja.pixel.ranking.RankingView v = pl.fawizja.pixel.ranking.RankingView.parse(got[0]);
            assertEquals(1, v.mine());
            assertEquals("Ola", v.rows().get(0).name());
            assertEquals(5_000, v.rows().get(0).level());
            ola.send("EVENTS");
            awaitMessage(ola, "events line", m -> m[0].equals("EVENTS") && m.length > 3 && m[3].startsWith("1. miejsce z 1 (+5,000 exp), nagrody za "));
        }
    }

    @Test
    void theWeeklyPodiumIsPaidWhenTheWeekEnds() throws Exception {
        long monday = java.time.Instant.parse("2026-10-19T08:00:00Z").toEpochMilli();
        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(monday);
        server.world().wallClock = clock::get;
        try (NetClient ola = connect()) {
            ola.hello("Ola", "PALADIN", "tajne-Ola");
            await("welcome", () -> ola.selfSave() != null);
            ola.send("RANK", "tydzień");
            awaitMessage(ola, "week tracked", m -> m[0].equals("RANK") && m[1].equals("Ranking tygodnia"));
            long[] bank = new long[1];
            server.enqueueTest(() -> {
                PlayerCharacter pc = server.world().find("Ola").session.character();
                pc.addExperience(7_000);
                bank[0] = pc.bank().balance();
            });
            ola.send("RANK", "tydzień");
            awaitMessage(ola, "gained", m -> m[0].equals("RANK") && m[1].equals("Ranking tygodnia") && m.length > 4);
            clock.set(monday + 7 * 24 * 3600_000L);
            ola.send("RANK", "tydzień");
            awaitMessage(ola, "podium announced", m -> m[0].equals("CHAT") && m[2].contains("Koniec tygodnia! Najwięcej doświadczenia: 1. Ola (+7,000 exp)"));
            awaitMessage(ola, "prize paid", m -> m[0].equals("LOG") && m[2].contains("Ranking tygodnia: 1. miejsce, 100,000 gp na Twoje konto."));
            // + 100 000 za podium (nowy dzien moze dorzucic tez nagrode za wejscie)
            assertTrue(server.world().find("Ola").session.character().bank().balance() >= bank[0] + 100_000);
        }
    }

    @Test
    void messageOfTheDayGreetsJoiningPlayers() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> set = new java.util.concurrent.atomic.AtomicReference<>();
        server.enqueueTest(() -> set.set(server.world().moderate(new String[] {"MOTD", "Event w sobotę"}, "konsola", server.now())));
        await("motd set", () -> set.get() != null);
        assertEquals("Event w sobotę", server.world().motd());
        try (NetClient ola = connect()) {
            ola.hello("Ola", "PALADIN", "tajne-Ola");
            awaitMessage(ola, "motd shown", m -> m[0].equals("LOG") && m[2].contains("Wiadomość dnia: Event w sobotę"));
        }
        server.enqueueTest(() -> server.world().moderate(new String[] {"MOTD", "-"}, "konsola", server.now()));
        await("motd cleared", () -> server.world().motd().isEmpty());
    }

    @Test
    void anExperienceEventBoostsEveryoneUntilItEnds() throws Exception {
        try (NetClient ola = connect()) {
            ola.hello("Ola", "PALADIN", "tajne-Ola");
            await("welcome", () -> ola.selfSave() != null);
            java.util.concurrent.atomic.AtomicReference<String> answer = new java.util.concurrent.atomic.AtomicReference<>();
            server.enqueueTest(() -> answer.set(server.world().moderate(new String[] {"EXPEVENT", "50 30"}, "konsola", server.now())));
            awaitMessage(ola, "announced", m -> m[0].equals("LOG") && m[2].startsWith("Wydarzenie: +50% doświadczenia"));
            assertTrue(answer.get().contains("30 min"), answer.get());
            await("bonus applied", () -> server.world().find("Ola").session.combat().bonusExpPercent() == 50);
            ola.send("EVENTS");
            awaitMessage(ola, "event in the report", m -> m[0].equals("EVENTS") && m.length >= 3 && m[2].startsWith("+50% za potwory, jeszcze 30 min"));
            server.enqueueTest(() -> answer.set(server.world().moderate(new String[] {"EXPEVENT", "abc"}, "konsola", server.now())));
            await("usage", () -> answer.get().startsWith("Użycie"));
            server.enqueueTest(() -> server.world().moderate(new String[] {"EXPEVENT", "0"}, "konsola", server.now()));
            awaitMessage(ola, "ended", m -> m[0].equals("LOG") && m[2].contains("dobiegło końca"));
            await("bonus removed", () -> server.world().find("Ola").session.combat().bonusExpPercent() == 0);
        }
        assertArrayEquals(new String[] {"ADMIN", "EXPEVENT", "50 60"}, pl.fawizja.pixel.net.ChatCommands.toMessage("/bonusexp 50 60"));
    }

    @Test
    void parcelsReachTheDepotOfOnlineAndOfflinePlayers() throws Exception {
        int pelt = ItemRegistry.loadDefault().byKey("wolf_pelt").id();
        try (NetClient kuba = connect()) {
            kuba.hello("Kuba", "MONK", "tajne-Kuba");
            await("welcome", () -> kuba.selfSave() != null);
        }
        await("kuba left", () -> server.world().find("Kuba") == null);
        Position banker = new Position(489, 497, 0);
        try (NetClient ola = connect();
             NetClient ula = connect()) {
            ola.hello("Ola", "PALADIN", "tajne-Ola");
            ula.hello("Ula", "DRUID", "tajne-Ula");
            await("welcome", () -> ola.selfSave() != null && ula.selfSave() != null);
            ola.send("MAIL", "SEND", "Ula", "5 wilcze futro");
            awaitMessage(ola, "not at a banker", m -> m[0].equals("LOG") && m[2].contains("bankier"));
            server.enqueueTest(() -> {
                ServerPlayer p = server.world().find("Ola");
                p.session.teleport(banker, server.now());
                p.session.character().backpack().add(new pl.fawizja.pixel.item.ItemStack(pelt, 12));
            });
            await("at the banker", () -> {
                try {
                    return self(ola).position().equals(banker);
                } catch (Exception e) {
                    return false;
                }
            });
            ola.send("MAIL", "SEND", "ula", "5 wilcze futro");
            awaitMessage(ula, "parcel arrived", m -> m[0].equals("LOG") && m[2].startsWith("Poczta: Ola przysyła 5x Wilcze futro"));
            ola.send("MAIL", "SEND", "Kuba", "7 wilcze futro");
            awaitMessage(ola, "sent to offline", m -> m[0].equals("LOG") && m[2].contains("odbierze po wejściu"));
            ola.send("MAIL", "SEND", "Nikt", "1 wilcze futro");
            awaitMessage(ola, "unknown", m -> m[0].equals("LOG") && m[2].contains("Nie ma takiej postaci"));
            await("items moved", () -> {
                try {
                    return self(ula).depot().count(pelt) == 5 && self(ola).backpack().count(pelt) == 0;
                } catch (Exception e) {
                    return false;
                }
            });
        }
        try (NetClient kuba = connect()) {
            kuba.hello("Kuba", "MONK", "tajne-Kuba");
            awaitMessage(kuba, "parcel on login", m -> m[0].equals("LOG") && m[2].contains("Ola przysyła 7x Wilcze futro"));
            await("in kuba's depot", () -> {
                try {
                    return self(kuba).depot().count(pelt) == 7;
                } catch (Exception e) {
                    return false;
                }
            });
            assertTrue(server.world().mail.waiting("Kuba").isEmpty());
        }
    }

    @Test
    void goldCanBeTransferredBetweenBankAccounts() throws Exception {
        try (NetClient kuba = connect()) {
            kuba.hello("Kuba", "MONK", "tajne-Kuba");
            await("welcome", () -> kuba.selfSave() != null);
        }
        await("kuba left", () -> server.world().find("Kuba") == null);
        Position banker = new Position(489, 497, 0);
        try (NetClient ola = connect();
             NetClient ula = connect()) {
            ola.hello("Ola", "PALADIN", "tajne-Ola");
            ula.hello("Ula", "DRUID", "tajne-Ula");
            await("welcome", () -> ola.selfSave() != null && ula.selfSave() != null);
            awaitMessage(ula, "login bonus first", m -> m[0].equals("LOG") && m[2].startsWith("Nagroda za wejście"));
            ola.send("TRANSFER", "Ula", "100");
            awaitMessage(ola, "not at a banker", m -> m[0].equals("LOG") && m[2].contains("bankier"));
            long[] before = new long[2];
            server.enqueueTest(() -> {
                ServerPlayer p = server.world().find("Ola");
                p.session.teleport(banker, server.now());
                p.session.character().bank().credit(10_000);
                before[0] = p.session.character().bank().balance();
                before[1] = server.world().find("Ula").session.character().bank().balance();
            });
            await("at the banker", () -> {
                try {
                    return self(ola).position().equals(banker) && before[0] > 0;
                } catch (Exception e) {
                    return false;
                }
            });
            ola.send("TRANSFER", "ula", "3000");
            awaitMessage(ula, "money arrived", m -> m[0].equals("LOG") && m[2].startsWith("Przelew: Ola przesyła"));
            ola.send("TRANSFER", "Kuba", "2000");
            awaitMessage(ola, "sent to offline", m -> m[0].equals("LOG") && m[2].contains("po wejściu do gry"));
            ola.send("TRANSFER", "Ula", "99999999");
            awaitMessage(ola, "not enough", m -> m[0].equals("LOG") && m[2].contains("Na koncie masz tylko"));
            ola.send("TRANSFER", "Ola", "10");
            awaitMessage(ola, "not to self", m -> m[0].equals("LOG") && m[2].contains("samemu sobie"));
            await("balances moved", () -> {
                try {
                    return self(ola).bank().balance() == before[0] - 5_000 && self(ula).bank().balance() == before[1] + 3_000;
                } catch (Exception e) {
                    return false;
                }
            });
        }
        try (NetClient kuba = connect()) {
            kuba.hello("Kuba", "MONK", "tajne-Kuba");
            awaitMessage(kuba, "paid on login", m -> m[0].equals("LOG") && m[2].contains("Pod Twoją nieobecność") && m[2].contains("przelewy"));
        }
    }

    @Test
    void theEventsWindowShowsTheNextInvasion() throws Exception {
        try (NetClient ola = connect()) {
            ola.hello("Ola", "PALADIN", "tajne-Ola");
            await("welcome", () -> ola.selfSave() != null);
            ola.send("EVENTS");
            awaitMessage(ola, "events", m -> m[0].equals("EVENTS") && m.length > 1 && m[1].startsWith("następna za"));
        }
    }

    @Test
    void housesHaveGuestsAndCanBeSoldToAnotherPlayer() throws Exception {
        pl.fawizja.pixel.house.House h = server.world().houses().inTown("Frostholm").get(0);
        try (NetClient ola = connect();
             NetClient ula = connect()) {
            ola.hello("Ola", "PALADIN", "tajne-Ola");
            ula.hello("Ula", "DRUID", "tajne-Ula");
            await("welcome", () -> ola.selfSave() != null && ula.selfSave() != null);
            boolean[] ready = new boolean[1];
            long[] ulaBank = new long[1], olaBank = new long[1];
            server.enqueueTest(() -> {
                assertTrue(server.world().houseOwners().claim(h.id(), "Ola"));
                server.world().find("Ola").session.setOwnHouse(h.id());
                server.world().find("Ula").session.character().bank().credit(50_000);
                ulaBank[0] = server.world().find("Ula").session.character().bank().balance();
                olaBank[0] = server.world().find("Ola").session.character().bank().balance();
                ready[0] = true;
            });
            await("house owned", () -> ready[0]);
            ola.send("HOUSE", "GUEST", "ula");
            awaitMessage(ula, "let in", m -> m[0].equals("LOG") && m[2].contains("wpuszcza cię do domu"));
            assertEquals(java.util.List.of("Ula"), server.world().houseOwners().guests(h.id()));
            ola.send("HOUSE", "UNGUEST", "Ula");
            awaitMessage(ola, "kicked", m -> m[0].equals("LOG") && m[2].contains("nie może już wchodzić"));
            ola.send("HOUSE", "SELL", "Ula", "20000");
            awaitMessage(ula, "offer", m -> m[0].equals("LOG") && m[2].contains("oferuje ci dom"));
            ula.send("HOUSE", "ACCEPT");
            awaitMessage(ula, "bought", m -> m[0].equals("LOG") && m[2].startsWith("Kupiono dom"));
            assertEquals("Ula", server.world().houseOwners().ownerOf(h.id()));
            PlayerCharacter ulaPc = server.world().find("Ula").session.character();
            PlayerCharacter olaPc = server.world().find("Ola").session.character();
            assertEquals(h.id(), ulaPc.house());
            assertNull(olaPc.house());
            assertEquals(ulaBank[0] - 20_000, ulaPc.bank().balance());
            assertEquals(olaBank[0] + 20_000, olaPc.bank().balance());
            ula.send("HOUSE", "ACCEPT");
            awaitMessage(ula, "no second sale", m -> m[0].equals("LOG") && m[2].contains("Nikt nie oferuje"));
        }
        String saved = new String(Files.readAllBytes(new File(saves, "houses.txt").toPath()), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(saved.contains(h.id() + "=Ula"), saved);
    }

    @Test
    void houseDoorWindowBuyGuestLevelsAndRentOfAnOfflineOwner() throws Exception {
        pl.fawizja.pixel.house.House h = server.world().houses().inTown("Selvaran").get(2);
        try (NetClient ola = connect();
             NetClient ula = connect()) {
            ola.hello("Ola", "PALADIN", "tajne-Ola");
            ula.hello("Ula", "DRUID", "tajne-Ula");
            await("welcome", () -> ola.selfSave() != null && ula.selfSave() != null);
            boolean[] ready = new boolean[1];
            server.enqueueTest(() -> {
                PlayerCharacter pc = server.world().find("Ola").session.character();
                pc.bank().debit(pc.bank().balance());
                pc.bank().credit(h.price());                     // dokladnie na dom, nic na czynsz
                for (int[] d : new int[][] {{0, 1}, {0, -1}, {1, 0}, {-1, 0}}) {
                    Position q = h.door().translate(d[0], d[1]);
                    if (server.world().world.isWalkable(q) && !h.contains(q)) {
                        server.world().find("Ola").session.teleport(q, 0);
                        break;
                    }
                }
                ready[0] = true;
            });
            await("ready", () -> ready[0]);
            ola.send("HOUSE", "DOOR", h.id());
            awaitMessage(ola, "free house", m -> m[0].equals("HOUSEINFO") && m[1].equals(h.id()) && m[2].equals("-"));
            ola.send("HOUSE", "BUY", h.id());
            awaitMessage(ola, "bought", m -> m[0].equals("HOUSEINFO") && m[2].equals("Ola") && m[3].equals("OWNER"));
            assertEquals("Ola", server.world().houseOwners().ownerOf(h.id()));
            assertTrue(server.world().houseOwners().rentPaidUntil(h.id()) > System.currentTimeMillis());
            ola.send("HOUSE", "GUEST", "Ula", "entry");
            awaitMessage(ula, "access", m -> m[0].equals("HACC") && m.length > 1 && m[1].equals(h.id() + ":entry"));
            assertEquals(pl.fawizja.pixel.house.HouseAccess.ENTRY, server.world().houseOwners().guestAccess(h.id(), "Ula"));
            ula.send("HOUSE", "DOOR", h.id());
            awaitMessage(ula, "guest view", m -> m[0].equals("HOUSEINFO") && m[2].equals("Ola") && m[3].equals("ENTRY"));
        }
        await("logged out", () -> server.world().find("Ola") == null);
        String saved = new String(Files.readAllBytes(new File(saves, "houses.txt").toPath()), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(saved.contains("guests:" + h.id() + "=Ula:entry"), saved);

        // dwa tygodnie bez zlota na koncie: dom wraca do miasta, choc wlascicielka jest offline
        boolean[] swept = new boolean[1];
        server.enqueueTest(() -> {
            server.world().wallClock = () -> System.currentTimeMillis() + 2 * pl.fawizja.pixel.house.HouseRent.WEEK_MS + 60_000;
            server.world().sweepHouseRent(0);
            swept[0] = true;
        });
        await("swept", () -> swept[0]);
        assertNull(server.world().houseOwners().ownerOf(h.id()));
        try (NetClient ola = connect()) {
            ola.hello("Ola", "PALADIN", "tajne-Ola");
            await("welcome back", () -> ola.selfSave() != null);
            assertNull(self(ola).house());
        }
    }

    @Test
    void pushedCratesAndTheirContentsReachEveryClientAndSurviveARestart() throws Exception {
        pl.fawizja.pixel.world.WorldMap w = server.world().world;
        pl.fawizja.pixel.world.Crates c = w.crates();
        Position crate = null;
        for (int y = 1; y < w.floor(0).height() - 1 && crate == null; y++) {
            for (int x = 1; x < w.floor(0).width() - 2 && crate == null; x++) {
                Position p = new Position(x, y, 0);
                if (c.isMovable(p) && server.world().houses().at(p) == null && c.canPush(p, pl.fawizja.pixel.world.Direction.EAST, null)) crate = p;
            }
        }
        assertNotNull(crate);
        Position from = crate, to = crate.translate(1, 0);
        try (NetClient ola = connect()) {
            ola.hello("Ola", "PALADIN", "tajne-Ola");
            await("welcome", () -> ola.selfSave() != null);
            server.enqueueTest(() -> {
                c.push(from, pl.fawizja.pixel.world.Direction.EAST);
                c.open(to, ItemRegistry.loadDefault()).add(new pl.fawizja.pixel.item.ItemStack(ItemRegistry.loadDefault().byKey("health_potion").id(), 1));
                c.changed(to);
            });
            awaitMessage(ola, "crate moved", m -> m[0].equals("CRATEMV") && m[4].equals(String.valueOf(to.x())));
            awaitMessage(ola, "crate contents", m -> m[0].equals("CRATE") && m[1].equals(String.valueOf(to.x())));
        }
        try (NetClient kuba = connect()) {
            kuba.hello("Kuba", "MONK", "tajne-Kuba");
            awaitMessage(kuba, "state on login", m -> m[0].equals("CRATEMV") && m[1].equals(String.valueOf(from.x()))
                    && m[4].equals(String.valueOf(to.x())));
        }
        assertTrue(new File(saves, "crates.txt").isFile());
        server.close();
        server = new PixelServer(0, saves);
        server.start();
        assertTrue(server.world().world.crates().isStorage(to), "crate stays where it was pushed");
        assertFalse(server.world().world.crates().peek(to).isEmpty());
    }

    static boolean logSays(NetClient c, String text) {
        String[] m;
        while ((m = c.poll()) != null) if (m[0].equals("LOG") && m.length > 2 && m[2].contains(text)) return true;
        return false;
    }

    @Test
    void playersCanChangeTheirPasswordAndDeleteTheirAccount() throws Exception {
        try (NetClient a = connect()) {
            a.hello("Lena", "MONK", "stare-hasło");
            await("in", () -> a.playerId() > 0);
            a.send("PASSWD", "źle-hasło", "nowe-hasło");
            await("refused", () -> logSays(a, "Złe obecne hasło"));
            a.send("PASSWD", "stare-hasło", "nowe-hasło");
            await("changed", () -> logSays(a, "Hasło zmienione"));
        }
        await("left", () -> server.world().players().isEmpty());
        assertFalse(accepted("Lena", "MONK", "stare-hasło"), "the old password no longer works");
        try (NetClient a = connect()) {
            a.hello("Lena", "MONK", "nowe-hasło");
            await("in again", () -> a.playerId() > 0);
            a.send("DELETE", "nowe-hasło");
            await("deleted", () -> "Konto usunięte.".equals(a.error()));
        }
        await("gone", () -> server.world().players().isEmpty());
        assertFalse(server.world().saved("Lena"), "the character save is removed");
        assertTrue(accepted("Lena", "DRUID", "zupelnie-nowe"), "the name is free again");
    }

    @Test
    void oneAddressCannotMassCreateAccounts() throws Exception {
        server.world().maxNewAccountsPerAddress = 2;
        assertTrue(accepted("Olaf", "MONK", "hasło-olafa"));
        assertTrue(accepted("Petra", "MONK", "hasło-petry"));
        try (NetClient c = connect()) {
            c.hello("Quin", "MONK", "hasło-quina");
            await("refused", () -> c.error() != null);
            assertTrue(c.error().contains("Za dużo nowych kont"), c.error());
        }
        assertTrue(accepted("Olaf", "MONK", "hasło-olafa"), "existing accounts still log in");
    }
}
