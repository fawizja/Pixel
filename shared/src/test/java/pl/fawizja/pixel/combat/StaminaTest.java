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
    void offlineRegenerationFollowsTibiaRules() {
        Stamina s = new Stamina();
        s.setMinutes(30 * 60);
        assertEquals(0, s.regenerateOffline(10 * MIN), "nothing for the first 10 minutes");
        assertEquals(10, s.regenerateOffline(10 * MIN + 30 * MIN), "then 1 minute per 3 minutes offline");
        s.setMinutes(Stamina.BONUS_ABOVE_MINUTES);
        assertEquals(10, s.regenerateOffline(10 * MIN + 60 * MIN), "above 39:00: 1 minute per 6 minutes");
        // 39:00 -> 42:00 zajmuje 18 h 10 min offline (TibiaWiki)
        s.setMinutes(Stamina.BONUS_ABOVE_MINUTES);
        s.regenerateOffline(18 * 60 * MIN + 10 * MIN - 1);
        assertEquals(Stamina.MAX_MINUTES - 1, s.minutes());
        s.regenerateOffline(18 * 60 * MIN + 10 * MIN);
        assertEquals(Stamina.MAX_MINUTES, s.minutes());
        // przez prog 39:00: najpierw tempo 1/3, reszta czasu 1/6
        s.setMinutes(Stamina.BONUS_ABOVE_MINUTES - 10);
        assertEquals(10 + 5, s.regenerateOffline(10 * MIN + 30 * MIN + 30 * MIN));
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
        // logowanie 1 h 10 min pozniej: 60 min / 3 = 20 minut staminy
        back.stamina().onLogin(1_000_000L + 70 * MIN);
        assertEquals(20 * 60 + 20, back.stamina().minutes());
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

    private static long killWolf(PlayerCharacter pc, long from) {
        pc.setPosition(new Position(10, 12, 0));
        SpawnPoint sp = new SpawnPoint("wolf", new Position(11, 12, 0), 0, 1, 60);
        MonsterManager mm = new MonsterManager(TestData.varden(), CombatTest.REG, List.of(sp), new Random(3));
        mm.populate(null);
        Monster wolf = mm.monsters().get(0);
        CombatSystem cs = CombatTest.system(pc, new Random(5), mm);
        cs.setTarget(wolf);
        long exp = pc.experience();
        for (long t = from; t < from + 120_000 && !wolf.isDead(); t += 100) cs.update(t);
        assertTrue(wolf.isDead());
        return pc.experience() - exp;
    }

    @Test
    void killsUseStaminaAndItsModifier() {
        PlayerCharacter premium = TestData.newCharacter(Vocation.MONK);
        premium.setPremium(true);
        assertEquals(30, killWolf(premium, 0), "premium happy hour: 20 exp * 150%");
        assertEquals(Stamina.MAX_MINUTES - 1, premium.stamina().minutes(), "one minute used");

        PlayerCharacter free = TestData.newCharacter(Vocation.MONK);
        assertEquals(20, killWolf(free, 0));
        free.stamina().setMinutes(Stamina.LOW_AT_MINUTES);
        assertEquals(10, killWolf(free, 0), "low stamina: half");
        free.stamina().setMinutes(0);
        assertEquals(0, killWolf(free, 0), "no stamina: no experience");
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
