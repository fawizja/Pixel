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

    @Test
    void sharedExperienceNeedsCloseLevelsAndRangeAndRewardsTeamwork() {
        assertTrue(PartyRules.levelsCompatible(List.of(60, 90)));
        assertFalse(PartyRules.levelsCompatible(List.of(8, 300)), "a high level cannot power-level a new character");
        assertFalse(PartyRules.levelsCompatible(List.of(50)));
        assertTrue(PartyRules.levelsCompatible(List.of(40, 60)), "exactly 2/3 still shares");
        assertFalse(PartyRules.levelsCompatible(List.of(39, 60)));
        assertEquals(40, PartyRules.minSharingLevel(60));
        assertEquals(67, PartyRules.minSharingLevel(100));
        assertTrue(PartyRules.levelsCompatible(List.of(PartyRules.minSharingLevel(100), 100)));
        assertFalse(PartyRules.levelsCompatible(List.of(PartyRules.minSharingLevel(100) - 1, 100)));
        assertEquals(600, PartyRules.share(1000, 2));
        assertEquals(338, PartyRules.share(1000, 4));
        assertEquals(1000, PartyRules.share(1000, 1));
        Position at = new Position(100, 100, 0);
        assertTrue(PartyRules.inShareRange(at, new Position(130, 70, 0)));
        assertFalse(PartyRules.inShareRange(at, new Position(131, 100, 0)));
        assertFalse(PartyRules.inShareRange(at, new Position(100, 100, -1)), "other floor");
    }

    @Test
    void bonusTableGrowsWithSizeAndMuchMoreWithVocationVariety() {
        // {osoby, rozne profesje, premia %}
        int[][] table = {
            {1, 1, 0},
            {2, 1, 20}, {2, 2, 40},
            {3, 1, 30}, {3, 2, 50}, {3, 3, 80},
            {4, 1, 35}, {4, 4, 135},
            {5, 1, 40}, {5, 2, 60}, {5, 3, 90}, {5, 4, 140}, {5, 5, 200},
            {8, 5, 200}, {8, 1, 40},
        };
        for (int[] r : table) {
            assertEquals(r[2], PartyRules.bonusPercent(r[0], r[1]), r[0] + " members, " + r[1] + " vocations");
        }
        assertEquals(PartyRules.MAX_BONUS_PERCENT, PartyRules.bonusPercent(List.of(Vocation.KNIGHT, Vocation.PALADIN,
                Vocation.SORCERER, Vocation.DRUID, Vocation.MONK)), "full diverse party = +200%");
        assertEquals(20, PartyRules.bonusPercent(List.of(Vocation.KNIGHT, Vocation.KNIGHT)));
        assertEquals(0, PartyRules.bonusPercent(List.of(Vocation.KNIGHT)));
        // wiecej osob -> wieksza premia; wiecej profesji -> duzo wieksza
        for (int n = 2; n < 5; n++) assertTrue(PartyRules.bonusPercent(n + 1, 1) > PartyRules.bonusPercent(n, 1));
        for (int v = 1; v < 5; v++) {
            int step = PartyRules.bonusPercent(5, v + 1) - PartyRules.bonusPercent(5, v);
            assertTrue(step >= PartyRules.bonusPercent(5, 1) - PartyRules.bonusPercent(4, 1), "variety step " + v);
        }
        // pula x3.0 dzielona na 5: kazdy dostaje 60% exp solo za zabicie; diverse zawsze lepsze od jednej profesji
        assertEquals(600, PartyRules.share(1000, 5, PartyRules.bonusPercent(5, 5)));
        assertEquals(280, PartyRules.share(1000, 5, PartyRules.bonusPercent(5, 1)));
        assertEquals(3.0, 5 * PartyRules.share(1000, 5, 200) / 1000.0, 1e-9, "the pool is x3.0 of the base exp");
        // na godzine: piec osob zabija co najmniej 2x szybciej niz jedna -> kazdy zarabia wiecej niz solo
        long soloPerHour = 1000L * 100;
        long partyEachPerHour = PartyRules.share(1000, 5, 200) * 100 * 2;
        assertTrue(partyEachPerHour > soloPerHour);
    }

    @Test
    void partyViewShowsMembersAndBonusAndSurvivesTheWire() {
        PartyView v = PartyView.of(List.of(
                new PartyView.Member("Ola", 60, Vocation.KNIGHT, true),
                new PartyView.Member("Ula", 58, Vocation.PALADIN, true),
                new PartyView.Member("Ela", 61, Vocation.SORCERER, true),
                new PartyView.Member("Kuba", 55, Vocation.DRUID, true),
                new PartyView.Member("Ada", 57, Vocation.MONK, true)));
        assertEquals(200, v.bonusPercent());
        assertTrue(v.sharing());
        assertEquals("Ola", v.leader());
        assertEquals("Premia doświadczenia: +200% (5 osób, 5 profesji)", v.bonusLine());
        PartyView back = PartyView.parse(v.encode());
        assertEquals(v, back);
        // czlonek poza gra nie liczy sie do premii; za niski poziom wylacza dzielenie
        PartyView off = PartyView.of(List.of(new PartyView.Member("Ola", 60, Vocation.KNIGHT, true),
                new PartyView.Member("Ula", 58, Vocation.PALADIN, false), new PartyView.Member("Ela", 20, Vocation.DRUID, true)));
        assertFalse(off.sharing());
        assertEquals(40, off.bonusPercent());
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
