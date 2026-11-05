package pl.fawizja.pixel.combat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Stamina;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

/** Stamina jak w Tibii (TibiaWiki): 42:00, zuzycie przy polowaniu, premia 39-42 h, polowa ponizej 14:00, odnowienie offline. */
class StaminaTest {

    static final long MIN = 60_000L;

    @Test
    void startsFullAt42HoursAndShowsHoursAndMinutes() {
        Stamina s = new Stamina();
        assertEquals(42 * 60, s.minutes());
        assertEquals("42:00", s.display());
        assertEquals(Stamina.Zone.BONUS, s.zone());
        s.setMinutes(13 * 60 + 5);
        assertEquals("13:05", s.display());
        assertEquals(Stamina.Zone.LOW, s.zone());
        s.setMinutes(0);
        assertEquals("00:00", s.display());
        assertEquals(Stamina.Zone.EMPTY, s.zone());
        s.setMinutes(99_999);
        assertEquals(Stamina.MAX_MINUTES, s.minutes(), "capped at 42:00");
    }

    @Test
    void experienceModifiersByZone() {
        Stamina s = new Stamina();
        assertEquals(150, s.modify(100, true), "happy hour: premium 150%");
        assertEquals(100, s.modify(100, false), "free account: no bonus");
        s.setMinutes(Stamina.BONUS_ABOVE_MINUTES);
        assertEquals(100, s.modify(100, true), "39:00 itself is already normal");
        s.setMinutes(Stamina.LOW_AT_MINUTES + 1);
        assertEquals(100, s.modify(100, true));
        s.setMinutes(Stamina.LOW_AT_MINUTES);
        assertEquals(50, s.modify(100, true), "14:00 and below: half");
        s.setMinutes(1);
        assertEquals(50, s.modify(100, false));
        s.setMinutes(0);
        assertEquals(0, s.modify(100, true), "no stamina - no experience");
    }

    @Test
    void huntingDrainsOneMinutePerMinute() {
        Stamina s = new Stamina();
        long t = 0;
        // 10 minut polowania, potwor co 10 s: 10 minut staminy, nie 60
        for (int i = 0; i < 60; i++, t += 10_000) s.hunt(50, true, t);
        assertEquals(42 * 60 - 10, s.minutes());
        // bez doswiadczenia (np. trening) stamina stoi
        s.hunt(0, true, t + 10 * MIN);
        assertEquals(42 * 60 - 10, s.minutes());
        s.setMinutes(1);
        assertEquals(50, s.hunt(100, false, t + 20 * MIN), "the last minute still counts as low stamina");
        assertEquals(0, s.minutes());
        assertEquals(0, s.hunt(100, false, t + 30 * MIN));
        assertEquals(0, s.minutes(), "never below zero");
    }

    @Test
    void offlineRegenerationIsAThirdFasterThanTibia() {
        long sec = 1000L;
        assertEquals(135 * sec, Stamina.REGEN_MS_PER_MINUTE, "Tibia 3 min x 3/4 = 2 min 15 s");
        assertEquals(270 * sec, Stamina.BONUS_REGEN_MS_PER_MINUTE, "Tibia 6 min x 3/4 = 4 min 30 s");
        Stamina s = new Stamina();
        s.setMinutes(30 * 60);
        assertEquals(0, s.regenerateOffline(10 * MIN), "nothing for the first 10 minutes");
        assertEquals(0, s.regenerateOffline(10 * MIN + 135 * sec - 1));
        assertEquals(1, s.regenerateOffline(10 * MIN + 135 * sec), "then 1 minute per 2 min 15 s offline");
        s.setMinutes(30 * 60);
        assertEquals(40, s.regenerateOffline(10 * MIN + 90 * MIN), "90 min offline = 40 minutes (Tibia: 30)");
        s.setMinutes(Stamina.BONUS_ABOVE_MINUTES);
        assertEquals(20, s.regenerateOffline(10 * MIN + 90 * MIN), "above 39:00: 1 minute per 4 min 30 s (Tibia: 15)");
        // 39:00 -> 42:00 zajmuje 13 h 40 min offline (Tibia: 18 h 10 min)
        s.setMinutes(Stamina.BONUS_ABOVE_MINUTES);
        s.regenerateOffline(13 * 60 * MIN + 40 * MIN - 1);
        assertEquals(Stamina.MAX_MINUTES - 1, s.minutes());
        s.regenerateOffline(13 * 60 * MIN + 40 * MIN);
        assertEquals(Stamina.MAX_MINUTES, s.minutes());
        // przez prog 39:00: najpierw szybsze tempo, reszta czasu wolniejsze
        s.setMinutes(Stamina.BONUS_ABOVE_MINUTES - 10);
        assertEquals(10 + 5, s.regenerateOffline(10 * MIN + 10 * 135 * sec + 5 * 270 * sec));
        // 0:00 -> 42:00: 101 h 25 min offline (Tibia: 135 h 10 min)
        s.setMinutes(0);
        s.regenerateOffline(101 * 60 * MIN + 25 * MIN - 1);
        assertEquals(Stamina.MAX_MINUTES - 1, s.minutes());
        s.setMinutes(0);
        s.regenerateOffline(101 * 60 * MIN + 25 * MIN);
        assertEquals(Stamina.MAX_MINUTES, s.minutes());
        s.setMinutes(0);
        s.regenerateOffline(1000L * 24 * 60 * MIN);
        assertEquals(Stamina.MAX_MINUTES, s.minutes(), "capped");
    }

    @Test
    void savedWithLogoutTimeAndRegeneratedOnLogin() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.stamina().setMinutes(20 * 60);
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        codec.setWallClock(() -> 1_000_000L);
        String save = codec.encode(pc);
        assertTrue(save.contains("stamina=1200,1000000"), save);
        PlayerCharacter back = codec.decode(save);
        assertEquals(20 * 60, back.stamina().minutes(), "decoding alone does not regenerate");
        // re-zapis postaci offline (np. czynsz domu) zachowuje czas wylogowania
        codec.setWallClock(() -> 5_000_000L);
        assertTrue(codec.encode(back).contains("stamina=1200,1000000"));
        // logowanie 1 h 10 min pozniej: 60 min / 2 min 15 s = 26 minut staminy
        back.stamina().onLogin(1_000_000L + 70 * MIN);
        assertEquals(20 * 60 + 26, back.stamina().minutes());
        assertEquals(0, back.stamina().onLogin(1_000_000L + 700 * MIN), "only once per login");
    }

    @Test
    void oldSavesWithoutStaminaStartFull() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        String save = SaveEdit.dropLines(codec.encode(pc), "stamina=");
        assertEquals(Stamina.MAX_MINUTES, codec.decode(save).stamina().minutes());
        String garbage = SaveEdit.replaceLine(codec.encode(pc), "stamina=", "stamina=abc");
        assertEquals(Stamina.MAX_MINUTES, codec.decode(garbage).stamina().minutes(), "bad data is tolerated");
    }

    /** Wilk (18 exp, jak w Tibii) dla postaci poziomu 8 z mnoznikiem etapu serwera. */
    private static final long WOLF = pl.fawizja.pixel.balance.Balance.applyRate(18, pl.fawizja.pixel.balance.Balance.expRate(8));

    private static long killWolf(PlayerCharacter pc, long from) {
        return hunt(pc, from, 3, 5).exp;
    }

    /** Wynik zabicia wilka: doswiadczenie, lup w ciele (autoloot wylaczony) i wpisy dziennika. */
    private record Kill(long exp, List<pl.fawizja.pixel.item.ItemStack> corpse, List<String> log) {}

    private static Kill hunt(PlayerCharacter pc, long from, long spawnSeed, long seed) {
        pc.setPosition(new Position(10, 12, 0));
        SpawnPoint sp = new SpawnPoint("wolf", new Position(11, 12, 0), 0, 1, 60);
        MonsterManager mm = new MonsterManager(TestData.varden(), CombatTest.REG, List.of(sp), new Random(spawnSeed));
        mm.populate(null);
        Monster wolf = mm.monsters().get(0);
        pl.fawizja.pixel.world.GroundItems ground = new pl.fawizja.pixel.world.GroundItems();
        pl.fawizja.pixel.game.EventLog log = new pl.fawizja.pixel.game.EventLog(50);
        Random rng = new Random(seed);
        CombatSystem cs = new CombatSystem(pc, TestData.varden(), TestData.ITEMS, mm, ground, log, rng, 8);
        cs.setAutoLoot(false);
        cs.setTarget(wolf);
        long exp = pc.experience();
        for (long t = from; t < from + 120_000 && !wolf.isDead(); t += 100) cs.update(t);
        assertTrue(wolf.isDead());
        pl.fawizja.pixel.world.GroundItems.Corpse c = ground.corpseAt(wolf.position());
        assertNotNull(c, "the body always stays");
        List<String> lines = new java.util.ArrayList<>();
        for (pl.fawizja.pixel.game.EventLog.Entry e : log.latest(50)) lines.add(e.text());
        return new Kill(pc.experience() - exp, c.loot(), lines);
    }

    @Test
    void killsUseStaminaAndItsModifier() {
        PlayerCharacter premium = TestData.newCharacter(Vocation.MONK);
        premium.setPremium(true);
        assertEquals(WOLF * 3 / 2, killWolf(premium, 0), "premium happy hour: 18 exp x stage * 150%");
        assertEquals(Stamina.MAX_MINUTES - 1, premium.stamina().minutes(), "one minute used");

        PlayerCharacter free = TestData.newCharacter(Vocation.MONK);
        assertEquals(WOLF, killWolf(free, 0));
        free.stamina().setMinutes(Stamina.LOW_AT_MINUTES);
        assertEquals(WOLF / 2, killWolf(free, 0), "low stamina: half");
        free.stamina().setMinutes(0);
        assertEquals(0, killWolf(free, 0), "no stamina: no experience");
    }

    @Test
    void noStaminaMeansNoLootAndAClearMessage() {
        int withLoot = 0;
        for (long seed = 1; seed <= 20; seed++) {
            PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
            Kill k = hunt(pc, 0, 3, seed);
            if (!k.corpse().isEmpty()) withLoot++;
            assertFalse(k.log().stream().anyMatch(l -> l.contains("brak staminy")), k.log().toString());
        }
        assertTrue(withLoot > 0, "wolves drop loot with stamina (otherwise this test proves nothing)");
        for (long seed = 1; seed <= 20; seed++) {
            PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
            pc.stamina().setMinutes(0);
            Kill k = hunt(pc, 0, 3, seed);
            assertEquals(0, k.exp());
            assertTrue(k.corpse().isEmpty(), "0:00 stamina: empty corpse, no gold, no items " + k.corpse());
            assertTrue(k.log().stream().anyMatch(l -> l.startsWith("Pokonano: Wilk") && l.contains("brak staminy - bez expa i łupu")),
                    k.log().toString());
        }
        assertEquals(", brak staminy - bez expa", CombatSystem.staminaNote(zeroStamina(), false), "party member: corpse is the killer's");
    }

    @Test
    void theLastMinuteStillGivesLootAndHalfExperience() {
        int withLoot = 0;
        for (long seed = 1; seed <= 20; seed++) {
            PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
            pc.stamina().setMinutes(1);
            Kill k = hunt(pc, 0, 3, seed);
            assertEquals(WOLF / 2, k.exp());
            assertEquals(0, pc.stamina().minutes());
            if (!k.corpse().isEmpty()) withLoot++;
            assertTrue(k.log().stream().anyMatch(l -> l.contains("niska stamina")), k.log().toString());
            assertFalse(k.log().stream().anyMatch(l -> l.contains("brak staminy")), "note uses the zone before this kill");
        }
        assertTrue(withLoot > 0);
    }

    private static PlayerCharacter zeroStamina() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.stamina().setMinutes(0);
        return pc;
    }

    /** Edycja tekstu zapisu z poprawiona suma kontrolna (symulacja starych zapisow). */
    static final class SaveEdit {
        static String dropLines(String save, String prefix) {
            return rebuild(save, l -> l.startsWith(prefix) ? null : l);
        }

        static String replaceLine(String save, String prefix, String line) {
            return rebuild(save, l -> l.startsWith(prefix) ? line : l);
        }

        static String rebuild(String save, java.util.function.UnaryOperator<String> f) {
            int headerEnd = save.indexOf('\n');
            int crcStart = save.lastIndexOf("crc=");
            StringBuilder body = new StringBuilder();
            for (String l : save.substring(headerEnd + 1, crcStart).split("\n")) {
                if (l.isEmpty()) continue;
                String n = f.apply(l);
                if (n != null) body.append(n).append('\n');
            }
            java.util.zip.CRC32 c = new java.util.zip.CRC32();
            c.update(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return save.substring(0, headerEnd + 1) + body + "crc=" + Long.toHexString(c.getValue()) + "\n";
        }
    }
}
