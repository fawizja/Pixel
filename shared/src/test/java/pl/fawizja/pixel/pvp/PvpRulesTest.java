package pl.fawizja.pixel.pvp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.pvp.PvpRules.Outcome;
import pl.fawizja.pixel.pvp.PvpRules.Skull;
import pl.fawizja.pixel.pvp.PvpRules.Status;
import pl.fawizja.pixel.pvp.PvpRules.Verdict;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

class PvpRulesTest {

    static final WorldMap W = TestData.varden();
    static final Position FIELD = new Position(48, 6, 0);

    private static GameSession at(String name, int level, Position p) {
        PlayerCharacter pc = TestData.newCharacterNamed(name);
        pc.addExperience(Balance.experienceForLevel(level) - pc.experience());
        pc.setPosition(p);
        pc.heal(pc.maxHealth());
        return new GameSession(TestData.ITEMS, W, pc);
    }

    /** Ataki co 2 s az do wyniku innego niz HIT / NOT_READY albo limitu. */
    private static Outcome fight(GameSession a, Status as, GameSession b, Status bs, long from, Random rng) {
        Outcome o = Outcome.HIT;
        for (long t = from; t < from + 600_000 && (o == Outcome.HIT || o == Outcome.NOT_READY); t += 2000) {
            o = PvpRules.attack(a, as, b, bs, W, t, rng);
        }
        return o;
    }

    @Test
    void newCharactersAndProtectionZonesAreSafe() {
        assertFalse(W.isProtectionZone(FIELD));
        GameSession rookie = at("Rookie", 8, FIELD.translate(1, 0));
        GameSession vet = at("Vet", 40, FIELD);
        GameSession rookie2 = at("Rookie Two", 8, FIELD.translate(0, 1));
        assertEquals(Verdict.TARGET_PROTECTED, PvpRules.check(vet.character(), rookie.character(), new Status(), W, 0));
        assertEquals(Verdict.ATTACKER_PROTECTED, PvpRules.check(rookie.character(), rookie2.character(), new Status(), W, 0));
        assertEquals(Outcome.NOT_ALLOWED, PvpRules.attack(vet, new Status(), rookie, new Status(), W, 0, new Random(1)));
        assertEquals(rookie.character().maxHealth(), rookie.character().health());

        Position temple = new Position(31, 16, 0);
        assertTrue(W.isProtectionZone(temple));
        GameSession a = at("Alpha", 40, temple);
        GameSession b = at("Bravo", 40, temple.translate(1, 0));
        assertEquals(Verdict.PROTECTION_ZONE, PvpRules.check(a.character(), b.character(), new Status(), W, 0));
        assertEquals(Verdict.SELF, PvpRules.check(a.character(), a.character(), new Status(), W, 0));
    }

    @Test
    void muchStrongerPlayersCannotHuntWeakOnesUnlessMarked() {
        GameSession strong = at("Strong", 100, FIELD);
        GameSession weak = at("Weak", 40, FIELD.translate(1, 0));
        Status weakStatus = new Status();
        assertEquals(Verdict.LEVEL_GAP, PvpRules.check(strong.character(), weak.character(), weakStatus, W, 0));
        // slabszy atakuje silniejszego - dostaje czaszke i wtedy moze zostac zaatakowany
        assertNotEquals(Outcome.NOT_ALLOWED, PvpRules.attack(weak, weakStatus, strong, new Status(), W, 0, new Random(2)));
        assertEquals(Skull.WHITE, weakStatus.skull(1000));
        assertEquals(Verdict.OK, PvpRules.check(strong.character(), weak.character(), weakStatus, W, 1000));
        assertEquals(Skull.NONE, weakStatus.skull(PvpRules.WHITE_SKULL_MS + 1));
    }

    @Test
    void killingGivesNoExperienceAndThreeUnjustKillsGiveRedSkull() {
        Random rng = new Random(3);
        GameSession killer = at("Killer", 60, FIELD);
        Status ks = new Status();
        long t = 0;
        for (int i = 0; i < PvpRules.RED_SKULL_KILLS; i++) {
            GameSession victim = at("Victim " + (char) ('A' + i), 45, FIELD.translate(1, 0));
            long exp = killer.character().experience();
            long vexp = victim.character().experience();
            assertEquals(Outcome.KILLED, fight(killer, ks, victim, new Status(), t, rng));
            assertEquals(exp, killer.character().experience(), "PvP gives no experience");
            assertTrue(victim.character().experience() < vexp, "victim pays the death penalty");
            assertFalse(victim.character().isDead(), "revived in the temple");
            assertTrue(W.isProtectionZone(victim.character().position()));
            t += 120_000;
        }
        assertEquals(Skull.RED, ks.skull(t));
        assertEquals(PvpRules.RED_SKULL_KILLS, ks.unjustKills(t));
        assertEquals(0, ks.unjustKills(t + PvpRules.KILL_WINDOW_MS + 1), "kills expire");
    }

    @Test
    void redSkullLosesTheBackpackAndKillingMarkedPlayersIsJustified() {
        Random rng = new Random(4);
        GameSession red = at("Red", 50, FIELD.translate(1, 0));
        Status rs = new Status();
        rs.redUntil = 10_000_000L;
        int stacks = 0;
        for (int i = 0; i < red.character().backpack().size(); i++) if (red.character().backpack().get(i) != null) stacks++;
        assertTrue(stacks > 0);
        GameSession hunter = at("Hunter", 50, FIELD);
        Status hs = new Status();
        assertEquals(Outcome.KILLED, fight(hunter, hs, red, rs, 0, rng));
        assertEquals(Skull.NONE, hs.skull(1000), "attacking a skulled player is justified");
        assertEquals(0, hs.unjustKills(1000));
        for (int i = 0; i < red.character().backpack().size(); i++) assertNull(red.character().backpack().get(i));
        assertEquals(stacks, red.ground().at(FIELD.translate(1, 0)).size(), "backpack left at the death spot");
    }

    /**
     * Symulacja: rowne poziomy, kazda para profesji, sam atak podstawowy z ekwipunkiem startowym.
     * Nikt nie ginie w kilka sekund (brak "one-shotow"), a walka sie konczy.
     */
    @Test
    void equalLevelDuelsAreNeitherInstantNorEndless() {
        StringBuilder report = new StringBuilder();
        for (pl.fawizja.pixel.vocation.Vocation va : pl.fawizja.pixel.vocation.Vocation.values()) {
            for (pl.fawizja.pixel.vocation.Vocation vb : pl.fawizja.pixel.vocation.Vocation.values()) {
                long total = 0;
                for (int seed = 0; seed < 5; seed++) {
                    GameSession a = duelist("Duel A", va, FIELD);
                    GameSession b = duelist("Duel B", vb, FIELD.translate(1, 0));
                    Random rng = new Random(seed);
                    Status as = new Status();
                    long t = 0;
                    while (PvpRules.attack(a, as, b, new Status(), W, t, rng) != Outcome.KILLED) {
                        t += 100;
                        assertTrue(t < 30 * 60_000L, va + " vs " + vb + " never ends");
                    }
                    total += t;
                }
                long avg = total / 5;
                report.append(String.format("%s->%s %ds; ", va, vb, avg / 1000));
                assertTrue(avg >= 10_000, va + " kills " + vb + " too fast: " + avg + " ms");
            }
        }
        System.out.println("PvP lvl 50, czas zabicia (sam atak): " + report);
    }

    private static GameSession duelist(String name, pl.fawizja.pixel.vocation.Vocation v, Position p) {
        PlayerCharacter pc = new pl.fawizja.pixel.character.CharacterFactory(
                pl.fawizja.pixel.config.StartingConfig.loadDefault(), TestData.ITEMS).create(name, v, p);
        pc.addExperience(Balance.experienceForLevel(50) - pc.experience());
        pc.heal(pc.maxHealth());
        return new GameSession(TestData.ITEMS, W, pc);
    }
}
