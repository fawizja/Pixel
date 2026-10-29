package pl.fawizja.pixel.party;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.net.ChatCommands;
import pl.fawizja.pixel.party.PartyManager.Result;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

class PartyTest {

    @Test
    void invitationsAcceptanceLeadershipAndDissolving() {
        PartyManager pm = new PartyManager();
        assertEquals(Result.SELF, pm.invite("Ola", "ola", 0));
        assertEquals(Result.NO_INVITE, pm.accept("Ula", "Ola", 0));
        assertEquals(Result.OK, pm.invite("Ola", "Ula", 0));
        assertEquals(Result.NO_INVITE, pm.accept("Ula", "Ola", PartyManager.INVITE_MS + 1), "invitation expired");
        assertEquals(Result.OK, pm.invite("Ola", "Ula", 100_000));
        assertEquals(Result.OK, pm.accept("ULA", "Ola", 100_500));
        assertEquals(List.of("Ola", "ULA"), pm.members("ula"));
        assertTrue(pm.sameParty("Ola", "Ula"));
        assertEquals(Result.NOT_LEADER, pm.invite("Ula", "Ela", 100_600));
        assertEquals(Result.OK, pm.invite("Ola", "Ela", 100_600));
        assertEquals(Result.OK, pm.accept("Ela", "Ola", 100_700));
        assertEquals(Result.TARGET_IN_PARTY, pm.invite("Kuba", "Ela", 100_800));

        // lider wychodzi - przywodztwo przechodzi dalej
        assertEquals(List.of("ULA", "Ela"), pm.leave("Ola"));
        assertEquals("ULA", pm.leaderOf("Ela"));
        assertFalse(pm.sameParty("Ola", "Ela"));
        // zostaje jedna osoba - druzyna sie rozpada
        assertEquals(List.of("ULA"), pm.leave("Ela"));
        assertTrue(pm.members("Ula").isEmpty());
        assertTrue(pm.leave("Nikt").isEmpty());
    }

    @Test
    void partiesAreLimitedToEightMembers() {
        PartyManager pm = new PartyManager();
        for (int i = 1; i < PartyManager.MAX_MEMBERS; i++) {
            assertEquals(Result.OK, pm.invite("Lider", "Gracz" + i, 0));
            assertEquals(Result.OK, pm.accept("Gracz" + i, "Lider", 0));
        }
        assertEquals(PartyManager.MAX_MEMBERS, pm.members("Lider").size());
        assertEquals(Result.FULL, pm.invite("Lider", "Nadmiar", 0));
    }

    /** Tibia: najnizszy poziom >= 2/3 najwyzszego, do 30 pol od lidera i pietro +-1, aktywnosc w 2 min. */
    @Test
    void sharedExperienceActivationLevelsDistanceFloorsAndActivity() {
        assertTrue(PartyRules.levelsCompatible(List.of(60, 90)));
        assertFalse(PartyRules.levelsCompatible(List.of(8, 300)), "a high level cannot power-level a new character");
        assertFalse(PartyRules.levelsCompatible(List.of(50)));
        assertTrue(PartyRules.levelsCompatible(List.of(40, 60)), "exactly 2/3 still shares (tibia.com: 40 with 60)");
        assertFalse(PartyRules.levelsCompatible(List.of(20, 60)), "tibia.com: 60 does not share with 20");
        assertFalse(PartyRules.levelsCompatible(List.of(39, 60)));
        assertTrue(PartyRules.levelsCompatible(List.of(100, 67, 80, 90)));
        assertFalse(PartyRules.levelsCompatible(List.of(100, 66, 80, 90)), "every member counts, not just the killer");
        assertEquals(40, PartyRules.minSharingLevel(60));
        assertEquals(67, PartyRules.minSharingLevel(100));
        assertEquals(150, PartyRules.maxSharingLevel(100));
        assertTrue(PartyRules.levelsCompatible(List.of(PartyRules.minSharingLevel(100), 100)));
        assertFalse(PartyRules.levelsCompatible(List.of(PartyRules.minSharingLevel(100) - 1, 100)));

        Position leader = new Position(100, 100, 0);
        assertEquals(30, PartyRules.SHARE_RANGE);
        assertTrue(PartyRules.inShareRange(leader, new Position(130, 70, 0)));
        assertFalse(PartyRules.inShareRange(leader, new Position(131, 100, 0)));
        assertTrue(PartyRules.inShareRange(leader, new Position(110, 100, -1)), "one floor down still shares");
        assertTrue(PartyRules.inShareRange(leader, new Position(110, 100, 1)), "one floor up still shares");
        assertFalse(PartyRules.inShareRange(leader, new Position(100, 100, -2)), "two floors away");

        assertEquals(120_000, PartyRules.ACTIVITY_MS);
        assertTrue(PartyRules.active(0, 120_000));
        assertFalse(PartyRules.active(0, 120_001), "idle for over 2 minutes");
        assertFalse(PartyRules.active(Long.MIN_VALUE / 2, 5_000), "never attacked");
    }

    /** Tibia: premia zalezy od liczby roznych profesji: 1 = 20%, 2 = 30%, 3 = 60%, 4 albo 5 = 100%. */
    @Test
    void bonusDependsOnlyOnDistinctVocationsAsInTibia() {
        assertEquals(20, PartyRules.bonusPercent(List.of(Vocation.KNIGHT, Vocation.KNIGHT)));
        assertEquals(20, PartyRules.bonusPercent(List.of(Vocation.KNIGHT, Vocation.KNIGHT, Vocation.KNIGHT,
                Vocation.KNIGHT, Vocation.KNIGHT)), "party size alone gives nothing more");
        assertEquals(30, PartyRules.bonusPercent(List.of(Vocation.KNIGHT, Vocation.DRUID)));
        assertEquals(30, PartyRules.bonusPercent(List.of(Vocation.KNIGHT, Vocation.DRUID, Vocation.DRUID)));
        assertEquals(60, PartyRules.bonusPercent(List.of(Vocation.KNIGHT, Vocation.DRUID, Vocation.PALADIN)));
        assertEquals(100, PartyRules.bonusPercent(List.of(Vocation.KNIGHT, Vocation.DRUID, Vocation.PALADIN,
                Vocation.SORCERER)));
        assertEquals(100, PartyRules.bonusPercent(List.of(Vocation.KNIGHT, Vocation.PALADIN, Vocation.SORCERER,
                Vocation.DRUID, Vocation.MONK)), "tibia.com: all 4 or 5 vocations = 100%");
        assertEquals(PartyRules.MAX_BONUS_PERCENT, 100);
        assertEquals(0, PartyRules.bonusPercent(List.of(Vocation.KNIGHT)));
        // premia tylko za potwory dajace bazowo co najmniej 20 doswiadczenia
        List<Vocation> four = List.of(Vocation.KNIGHT, Vocation.DRUID, Vocation.PALADIN, Vocation.SORCERER);
        assertEquals(0, PartyRules.bonusPercent(19, four));
        assertEquals(100, PartyRules.bonusPercent(20, four));
    }

    /** Tibia: kazdy z P czlonkow dostaje ceil(M x (1 + premia) / P). */
    @Test
    void experienceIsSplitEquallyWithTheBonusAndRoundedUp() {
        assertEquals(1000, PartyRules.share(1000, 1, 100), "alone: no split");
        assertEquals(600, PartyRules.share(1000, 2, 20), "2 knights: 1000 x 1.2 / 2");
        assertEquals(650, PartyRules.share(1000, 2, 30), "knight + druid: 1000 x 1.3 / 2");
        assertEquals(534, PartyRules.share(1000, 3, 60), "3 vocations: 1600 / 3 = 533.3 -> rounded up");
        assertEquals(500, PartyRules.share(1000, 4, 100), "4 vocations: 2000 / 4");
        assertEquals(400, PartyRules.share(1000, 5, 100), "5 vocations: 2000 / 5");
        assertEquals(240, PartyRules.share(1000, 5, 20), "5 knights: 1200 / 5");
        assertEquals(1, PartyRules.share(1, 2, 20), "1.2 / 2 = 0.6 -> 1");
        assertEquals(5, PartyRules.share(10, 2, 0), "small monster without the bonus");
    }

    @Test
    void partyViewShowsMembersAndBonusAndSurvivesTheWire() {
        PartyView v = PartyView.of(List.of(
                new PartyView.Member("Ola", 60, Vocation.KNIGHT, true),
                new PartyView.Member("Ula", 58, Vocation.PALADIN, true),
                new PartyView.Member("Ela", 61, Vocation.SORCERER, true),
                new PartyView.Member("Kuba", 55, Vocation.DRUID, true),
                new PartyView.Member("Ada", 57, Vocation.MONK, true)));
        assertEquals(100, v.bonusPercent());
        assertTrue(v.sharing());
        assertEquals("Ola", v.leader());
        assertEquals("Premia doświadczenia: +100% (5 osób, 5 profesji)", v.bonusLine());
        PartyView back = PartyView.parse(v.encode());
        assertEquals(v, back);
        // czlonek poza gra nie liczy sie do premii; za niski poziom wylacza dzielenie
        PartyView off = PartyView.of(List.of(new PartyView.Member("Ola", 60, Vocation.KNIGHT, true),
                new PartyView.Member("Ula", 58, Vocation.PALADIN, false), new PartyView.Member("Ela", 20, Vocation.DRUID, true)));
        assertFalse(off.sharing());
        assertEquals(30, off.bonusPercent());
        assertTrue(off.bonusLine().contains("najniższy poz. 40"), off.bonusLine());
        assertSame(PartyView.NONE, PartyView.of(List.of(new PartyView.Member("Ola", 1, Vocation.KNIGHT, true))));
    }

    @Test
    void leaderKicksMembersAndInvitesAreListed() {
        PartyManager pm = new PartyManager();
        pm.invite("Ola", "Ula", 0);
        pm.invite("Ela", "Ula", 0);
        assertEquals(2, pm.invitesFor("ula", 10).size());
        assertTrue(pm.invitesFor("ula", PartyManager.INVITE_MS + 1).isEmpty(), "expired");
        pm.accept("Ula", "Ola", 10);
        pm.invite("Ola", "Ela", 20);
        pm.accept("Ela", "Ola", 30);
        assertEquals(Result.NOT_LEADER, pm.kick("Ula", "Ela"));
        assertEquals(Result.SELF, pm.kick("Ola", "ola"));
        assertEquals(Result.NOT_MEMBER, pm.kick("Ola", "Kuba"));
        assertEquals(Result.OK, pm.kick("Ola", "ELA"));
        assertEquals(List.of("Ola", "Ula"), pm.members("Ola"));
        assertTrue(pm.members("Ela").isEmpty());
        assertEquals(Result.OK, pm.kick("Ola", "Ula"));
        assertTrue(pm.members("Ola").isEmpty(), "a party of one dissolves");
        assertEquals(Result.NOT_IN_PARTY, pm.kick("Ola", "Ula"));
    }

    @Test
    void partyChatCommands() {
        assertArrayEquals(new String[] {"PARTY", "INFO"}, ChatCommands.toMessage("/drużyna"));
        assertArrayEquals(new String[] {"PARTY", "INVITE", "Stary Wilk"}, ChatCommands.toMessage("/party invite Stary Wilk"));
        assertArrayEquals(new String[] {"PARTY", "KICK", "Ola"}, ChatCommands.toMessage("/drużyna wyrzuć Ola"));
        assertArrayEquals(new String[] {"PARTY", "LEAVE"}, ChatCommands.toMessage("/party leave"));
        assertArrayEquals(new String[] {"PARTY", "ACCEPT"}, ChatCommands.toMessage("/przyjmij"));
        assertArrayEquals(new String[] {"PARTY", "ACCEPT", "Ola"}, ChatCommands.toMessage("/party accept Ola"));
        assertNull(ChatCommands.toMessage("/party kick"));
        assertArrayEquals(new String[] {"PARTY", "INVITE", "Stary Wilk"}, ChatCommands.toMessage("/zapros Stary Wilk"));
        assertArrayEquals(new String[] {"PARTY", "ACCEPT", "Ola"}, ChatCommands.toMessage("/PRZYJMIJ Ola"));
        assertArrayEquals(new String[] {"PARTY", "LEAVE"}, ChatCommands.toMessage("/opusc"));
        assertArrayEquals(new String[] {"PCHAT", "na boss za 5 min"}, ChatCommands.toMessage("/d na boss za 5 min"));
        assertNull(ChatCommands.toMessage("/zapros"));
        assertNull(ChatCommands.toMessage("/d"));
    }
}
