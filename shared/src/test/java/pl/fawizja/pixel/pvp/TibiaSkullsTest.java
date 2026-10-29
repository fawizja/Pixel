package pl.fawizja.pixel.pvp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.combat.DamageType;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.magic.CastResult;
import pl.fawizja.pixel.pvp.PvpRules.Outcome;
import pl.fawizja.pixel.pvp.PvpRules.Skull;
import pl.fawizja.pixel.pvp.PvpRules.Status;
import pl.fawizja.pixel.pvp.PvpRules.Verdict;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/** Czaszki, blokady i progi zabojstw jak w Tibii (zrodla w docs/GAME_SYSTEMS.md, "PvP jak w Tibii"). */
class TibiaSkullsTest {

    static final WorldMap W = TestData.varden();
    static final Position FIELD = new Position(48, 6, 0);
    static final long MIN = 60_000L, HOUR = 60 * MIN, DAY = 24 * HOUR;

    private static GameSession player(String name, int level, Position p, boolean pvp) {
        PlayerCharacter pc = TestData.newCharacterNamed(name);
        pc.addExperience(Balance.experienceForLevel(level) - pc.experience());
        pc.setPosition(p);
        pc.heal(pc.maxHealth());
        pc.setPvpMode(pvp);
        return new GameSession(TestData.ITEMS, W, pc);
    }

    private static Outcome fight(GameSession a, GameSession b, long from) {
        Outcome o = Outcome.HIT;
        Random rng = new Random(from);
        for (long t = from; t < from + 900_000 && (o == Outcome.HIT || o == Outcome.NOT_READY); t += 2000) {
            o = PvpRules.attack(a, a.pvpStatus(), b, b.pvpStatus(), W, t, rng);
        }
        return o;
    }

    /** Zapis nieusprawiedliwionych zabojstw w podanych chwilach i wynik progow. */
    private static Skull killsAt(Status s, long... times) {
        Skull out = Skull.NONE;
        for (long t : times) {
            s.unjustKills.addLast(t);
            out = PvpRules.applyThresholds(s, t);
        }
        return out;
    }

    @Test
    void redSkullThresholdsAreThreePerDayFivePerWeekTenPerMonth() {
        assertArrayEquals(new int[] {3, 5, 10}, PvpRules.RED_SKULL_KILLS);
        assertArrayEquals(new int[] {6, 10, 20}, PvpRules.BLACK_SKULL_KILLS);
        assertArrayEquals(new long[] {DAY, 7 * DAY, 30 * DAY}, PvpRules.KILL_WINDOWS_MS);

        assertEquals(Skull.NONE, killsAt(new Status(), 0, HOUR));
        assertEquals(Skull.RED, killsAt(new Status(), 0, HOUR, 2 * HOUR), "3 in 24 h");
        assertEquals(Skull.NONE, killsAt(new Status(), 0, 13 * HOUR, DAY + HOUR), "3 over more than 24 h: not yet");

        // 5 w tygodniu, po 1 dziennie (nigdy 3 na dobe)
        Status week = new Status();
        assertEquals(Skull.NONE, killsAt(week, 0, DAY + 1, 2 * DAY + 2, 3 * DAY + 3));
        assertEquals(Skull.RED, killsAt(week, 4 * DAY + 4), "5 in 7 days");
        assertEquals(4 * DAY + 4 + PvpRules.RED_SKULL_MS, week.skullUntil(4 * DAY + 4), "30 days from the kill over the limit");

        // 10 w 30 dniach, co 3 dni (nigdy 5 na tydzien)
        Status month = new Status();
        long[] every3 = new long[10];
        for (int i = 0; i < 10; i++) every3[i] = i * 3 * DAY;
        for (int i = 0; i < 9; i++) assertEquals(Skull.NONE, killsAt(month, every3[i]), "kill " + (i + 1));
        assertEquals(Skull.RED, killsAt(month, every3[9]), "10 in 30 days");

        // stare zabojstwa wypadaja z okien
        Status old = new Status();
        killsAt(old, 0, HOUR);
        assertEquals(Skull.NONE, killsAt(old, 31 * DAY), "kills older than 30 days no longer count");
        assertEquals(1, old.unjustKills(31 * DAY));
        assertEquals(1, old.unjustKills(31 * DAY, DAY));
    }

    @Test
    void blackSkullThresholdsAndFortyFiveDays() {
        Status day = new Status();
        assertEquals(Skull.RED, killsAt(day, 0, 1, 2, 3, 4));
        assertEquals(Skull.BLACK, killsAt(day, 5), "6 in 24 h");
        assertEquals(5 + PvpRules.BLACK_SKULL_MS, day.skullUntil(10));
        assertEquals(Skull.BLACK, day.skull(PvpRules.BLACK_SKULL_MS));
        assertEquals(Skull.NONE, day.skull(5 + PvpRules.BLACK_SKULL_MS), "a black skull lasts 45 days");

        Status week = new Status();
        long[] t = new long[10];
        for (int i = 0; i < 10; i++) t[i] = i * 15 * HOUR; // ponizej 6 na dobe, 10 w ~6 dni
        assertEquals(Skull.BLACK, killsAt(week, t), "10 in 7 days");

        Status month = new Status();
        long[] m = new long[20];
        for (int i = 0; i < 20; i++) m[i] = i * 36 * HOUR; // < 10 na tydzien, 20 w ~28,5 dnia
        assertEquals(Skull.BLACK, killsAt(month, m), "20 in 30 days");
    }

    @Test
    void attackOneMinuteBlockUnjustifiedKillFifteenJustifiedKillOne() {
        GameSession a = player("Napastnik", 60, FIELD, true);
        GameSession b = player("Przechodzien", 45, FIELD.translate(1, 0), false);
        assertEquals(Outcome.HIT, PvpRules.attack(a, a.pvpStatus(), b, b.pvpStatus(), W, 0, new Random(1)));
        assertEquals(PvpRules.PZ_BLOCK_MS, a.pvpStatus().pzBlockedMs(0));
        assertEquals(60_000, PvpRules.PZ_BLOCK_MS);
        assertEquals(Skull.WHITE, a.pvpStatus().skull(PvpRules.WHITE_SKULL_MS - 1));
        assertEquals(Skull.NONE, a.pvpStatus().skull(PvpRules.WHITE_SKULL_MS), "white after an attack: 1 min");

        // nieusprawiedliwione zabojstwo: 15 min
        assertEquals(Outcome.KILLED, fight(a, b, 120_000));
        long until = a.pvpStatus().fightUntil();
        assertTrue(until > 120_000 + PvpRules.KILL_BLOCK_MS - 1 && until <= 120_000 + 900_000 + PvpRules.KILL_BLOCK_MS);
        assertEquals(15 * MIN, PvpRules.KILL_BLOCK_MS);
        assertEquals(until, a.pvpStatus().logoutUntil());
        assertEquals(until, a.pvpStatus().skullUntil(until - 1), "white skull for the same 15 min");

        // usprawiedliwione zabojstwo (cel z biala czaszka): tylko 1 min, bez zabojstwa na koncie
        GameSession hunter = player("Lowca", 60, FIELD.translate(-1, 0), false);
        long start = until - 10 * MIN; // napastnik ma jeszcze biala czaszke
        assertEquals(Outcome.KILLED, fight(hunter, a, start));
        assertEquals(0, hunter.pvpStatus().unjustKills(start + 900_000));
        assertEquals(Skull.NONE, hunter.pvpStatus().skull(start + 900_000));
        long hb = hunter.pvpStatus().fightUntil();
        assertTrue(hb - start <= 900_000 + PvpRules.PZ_BLOCK_MS && hb > start, "justified kill: 1 min after the last hit");
    }

    @Test
    void yellowSkullLetsTheMarkedPlayerFightBackWithoutPenalty() {
        GameSession a = player("Agresorka", 60, FIELD, true);
        GameSession b = player("Obronca", 60, FIELD.translate(1, 0), false);
        GameSession c = player("Swiadek", 60, FIELD.translate(-1, 0), false);
        assertEquals(Outcome.HIT, PvpRules.attack(a, a.pvpStatus(), b, b.pvpStatus(), W, 0, new Random(1)));
        assertEquals(Skull.WHITE, PvpRules.skullSeenBy(c.pvpStatus(), "Agresorka", a.pvpStatus(), 0));
        // B (tryb bezpieczny) oddaje: bez czaszki, ale A widzi B z zolta czaszka
        assertEquals(Verdict.OK, PvpRules.check(b.character(), b.pvpStatus(), a.character(), a.pvpStatus(), W, 0));
        assertEquals(Outcome.HIT, PvpRules.attack(b, b.pvpStatus(), a, a.pvpStatus(), W, 1000, new Random(2)));
        assertEquals(Skull.NONE, b.pvpStatus().skull(1000));
        assertEquals(Skull.YELLOW, PvpRules.skullSeenBy(a.pvpStatus(), "Obronca", b.pvpStatus(), 1000));
        assertEquals(Skull.NONE, PvpRules.skullSeenBy(c.pvpStatus(), "Obronca", b.pvpStatus(), 1000), "only A sees it");
        assertEquals(PvpRules.LOGOUT_BLOCK_MS, PvpRules.YELLOW_SKULL_MS);
        // A zabija B, ktory ma dla A zolta czaszke: usprawiedliwione - bez zabojstwa na koncie i bez zemsty
        a.pvpStatus().nextAttackMs = 0;
        Outcome o = Outcome.HIT;
        for (long t = 2000; t < 40_000 && o != Outcome.KILLED; t += 2000) {
            b.character().damage(b.character().health() - 1); // przyspieszenie: B na 1 HP
            o = PvpRules.attack(a, a.pvpStatus(), b, b.pvpStatus(), W, t, new Random(t));
            // B dalej oddaje, wiec zolta czaszka sie odnawia
            if (o != Outcome.KILLED) PvpRules.attack(b, b.pvpStatus(), a, a.pvpStatus(), W, t + 1, new Random(t));
        }
        assertEquals(Outcome.KILLED, o);
        assertEquals(0, a.pvpStatus().unjustKills(40_000), "killing a yellow-skulled attacker is justified");
        assertFalse(b.pvpStatus().revengeOn("Agresorka", 40_000));
        // zolta czaszka wygasa razem z blokada wylogowania
        Status s = new Status();
        s.yellow.put("ktos", 1000L + PvpRules.YELLOW_SKULL_MS);
        assertTrue(s.yellowOf("Ktos", 1000L + PvpRules.YELLOW_SKULL_MS - 1));
        assertFalse(s.yellowOf("Ktos", 1000L + PvpRules.YELLOW_SKULL_MS));
    }

    @Test
    void orangeRevengeSkullForSevenDaysOrUntilAvenged() {
        GameSession k = player("Morderca", 60, FIELD, true);
        GameSession v = player("Pokrzywdzony", 60, FIELD.translate(1, 0), false);
        GameSession other = player("Ktosinny", 60, FIELD.translate(-1, 0), false);
        assertEquals(Outcome.KILLED, fight(k, v, 0));
        long after = k.pvpStatus().fightUntil(); // koniec 15 min: biala czaszka zniknela
        assertEquals(Skull.NONE, k.pvpStatus().skull(after));
        assertEquals(Skull.ORANGE, PvpRules.skullSeenBy(v.pvpStatus(), "Morderca", k.pvpStatus(), after));
        assertEquals(Skull.NONE, PvpRules.skullSeenBy(other.pvpStatus(), "Morderca", k.pvpStatus(), after));
        assertEquals(Skull.ORANGE, PvpRules.skullSeenBy(v.pvpStatus(), "Morderca", k.pvpStatus(),
                after - PvpRules.KILL_BLOCK_MS + PvpRules.REVENGE_MS - 1));
        assertEquals(Skull.NONE, PvpRules.skullSeenBy(v.pvpStatus(), "Morderca", k.pvpStatus(),
                after - PvpRules.KILL_BLOCK_MS + PvpRules.REVENGE_MS), "revenge lasts 7 days");
        assertEquals(7 * DAY, PvpRules.REVENGE_MS);

        // zemsta: ofiara w trybie bezpiecznym moze zaatakowac i zabic zabojce bez kary
        v.character().setPosition(FIELD.translate(1, 0));
        v.character().heal(v.character().maxHealth());
        assertEquals(Verdict.OK, PvpRules.check(v.character(), v.pvpStatus(), k.character(), k.pvpStatus(), W, after));
        assertEquals(Verdict.PVP_OFF, PvpRules.check(other.character(), other.pvpStatus(), k.character(), k.pvpStatus(), W, after));
        assertEquals(Outcome.KILLED, fight(v, k, after));
        assertEquals(Skull.NONE, v.pvpStatus().skull(after + 900_000));
        assertEquals(0, v.pvpStatus().unjustKills(after + 900_000));
        assertFalse(v.pvpStatus().revengeOn("Morderca", after + 900_000), "avenged: the orange skull is gone");
    }

    @Test
    void pvpDamageIsHalvedButFullAgainstABlackSkull() {
        GameSession a = player("Mag", 60, FIELD, true);
        GameSession normal = player("Zwykly", 60, FIELD.translate(1, 0), false);
        GameSession black = player("Czarny", 60, FIELD.translate(2, 0), false);
        PvpRules.markForPreview(black.pvpStatus(), Skull.BLACK, 0);
        assertEquals(0.5, PvpRules.PVP_DAMAGE);
        int hp = normal.character().health();
        PvpRules.spellHit(a, a.pvpStatus(), normal, normal.pvpStatus(), 100, DamageType.FIRE, W, 1000, new Random(1));
        assertEquals(50, hp - normal.character().health(), "50% between players");
        int bhp = black.character().health();
        assertEquals(Verdict.OK, PvpRules.check(a.character(), a.pvpStatus(), black.character(), black.pvpStatus(), W, 1000));
        PvpRules.spellHit(a, a.pvpStatus(), black, black.pvpStatus(), 100, DamageType.FIRE, W, 1000, new Random(1));
        assertEquals(100, bhp - black.character().health(), "100% against a black skull");
    }

    @Test
    void blackSkullCannotAttackUnmarkedPlayersNorUseAreaSpellsAndRespawnsWeak() {
        GameSession bk = player("Czarnoczaszki", 60, FIELD, true);
        GameSession clean = player("Niewinny", 60, FIELD.translate(1, 0), false);
        GameSession white = player("Bialy", 60, FIELD.translate(-1, 0), true);
        PvpRules.markForPreview(bk.pvpStatus(), Skull.BLACK, 0);
        PvpRules.markForPreview(white.pvpStatus(), Skull.WHITE, 0);
        assertEquals(Verdict.BLACK_SKULL, PvpRules.check(bk.character(), bk.pvpStatus(), clean.character(), clean.pvpStatus(), W, 0));
        assertEquals(Verdict.BLACK_SKULL, PvpRules.checkArea(bk.character(), bk.pvpStatus(), clean.character(), clean.pvpStatus(), W, 0));
        assertEquals(Verdict.OK, PvpRules.check(bk.character(), bk.pvpStatus(), white.character(), white.pvpStatus(), W, 0),
                "marked players are still allowed");
        // czary obszarowe zablokowane (takze na potwory)
        bk.spells().setPlayerTargeting(new PvpTargeting(bk, () -> null, java.util.List::of, null, W, new Random(1), null));
        bk.character().skills().set(pl.fawizja.pixel.character.Skill.MAGIC, 30, 0);
        bk.character().backpack().add(new pl.fawizja.pixel.item.ItemStack(TestData.ITEMS.byKey("fireburst_rune").id(), 1));
        assertEquals(CastResult.BLACK_SKULL, bk.spells().useFirst("fireburst_rune", 1000));

        // smierc z czarna czaszka: wszystko na ziemi, odrodzenie z 40 HP i 0 many
        int worn = bk.character().equipment().all().size();
        assertEquals(Outcome.KILLED, fight(white, bk, 2000));
        assertTrue(bk.character().equipment().all().isEmpty());
        assertTrue(worn > 0);
        assertEquals(PvpRules.BLACK_SKULL_RESPAWN_HP, bk.character().health());
        assertEquals(0, bk.character().mana());
        assertEquals(0, white.pvpStatus().unjustKills(1_000_000), "killing a black skull is justified");
    }
}
