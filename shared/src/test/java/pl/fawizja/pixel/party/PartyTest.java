package pl.fawizja.pixel.party;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.net.ChatCommands;
import pl.fawizja.pixel.party.PartyManager.Result;
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
        assertEquals(600, PartyRules.share(1000, 2));
        assertEquals(400, PartyRules.share(1000, 4));
        assertEquals(1000, PartyRules.share(1000, 1));
        Position at = new Position(100, 100, 0);
        assertTrue(PartyRules.inShareRange(at, new Position(130, 70, 0)));
        assertFalse(PartyRules.inShareRange(at, new Position(131, 100, 0)));
        assertFalse(PartyRules.inShareRange(at, new Position(100, 100, -1)), "other floor");
    }

    @Test
    void partyChatCommands() {
        assertArrayEquals(new String[] {"PARTY", "INVITE", "Stary Wilk"}, ChatCommands.toMessage("/zapros Stary Wilk"));
        assertArrayEquals(new String[] {"PARTY", "ACCEPT", "Ola"}, ChatCommands.toMessage("/PRZYJMIJ Ola"));
        assertArrayEquals(new String[] {"PARTY", "LEAVE"}, ChatCommands.toMessage("/opusc"));
        assertArrayEquals(new String[] {"PCHAT", "na boss za 5 min"}, ChatCommands.toMessage("/d na boss za 5 min"));
        assertNull(ChatCommands.toMessage("/zapros"));
        assertNull(ChatCommands.toMessage("/d"));
    }
}
