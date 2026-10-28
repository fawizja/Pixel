package pl.fawizja.pixel.social;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.party.PartyView;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

class SocialClientTest {

    private static PlayerCharacter me() {
        return new PlayerCharacter("p1", "Ola", Vocation.KNIGHT, 30, new Position(10, 10, 0), TestData.ITEMS, 0, "varden");
    }

    @Test
    void offlinePartiesSayOnlineOnlyButFriendsAreKeptInTheCharacter() {
        PlayerCharacter pc = me();
        EventLog log = new EventLog(20);
        SocialClient s = new SocialClient(pc, log, () -> 5L);
        assertFalse(s.invitePlayerToParty("Ula"));
        assertTrue(log.latest(1).get(0).text().contains("online"));
        assertFalse(s.acceptPartyInvite("Ula"));
        assertFalse(s.leaveParty());
        assertSame(PartyView.NONE, s.party());
        assertTrue(s.addFriend("Ula"));
        assertFalse(s.addFriend("ula"), "already a friend");
        assertFalse(s.addFriend("Ola"), "not myself");
        assertEquals(1, s.friends().rows().size());
        assertFalse(s.friends().rows().get(0).online());
        assertTrue(pc.friends().contains("Ula"));
        assertTrue(s.removeFriend("ULA"));
        PlayerInfo self = s.playerInfo("ola");
        assertEquals(30, self.level());
        assertEquals(Vocation.KNIGHT, self.vocation());
        assertFalse(s.playerInfo("Ktos").known());
    }

    @Test
    void onlineActionsGoToTheServerAndRepliesFillTheState() {
        List<String> sent = new ArrayList<>();
        long[] now = {1_000};
        SocialClient s = new SocialClient(me(), new EventLog(20), () -> 0L);
        s.setLink(new SocialClient.Link() {
            @Override
            public void send(String... fields) { sent.add(String.join(" ", fields)); }

            @Override
            public long now() { return now[0]; }
        });
        assertTrue(s.invitePlayerToParty("Ula"));
        assertEquals("PARTY INVITE Ula", sent.get(sent.size() - 1));
        assertFalse(s.invitePlayerToParty("ola"), "not myself");

        s.handle(new String[] {"PARTYINV", "Ela"});
        assertEquals(List.of("Ela"), s.partyInvites());
        assertTrue(s.acceptPartyInvite());
        assertEquals("PARTY ACCEPT Ela", sent.get(sent.size() - 1));

        long v = s.version();
        s.handle(PartyView.of(List.of(new PartyView.Member("Ela", 33, Vocation.DRUID, true),
                new PartyView.Member("Ola", 30, Vocation.KNIGHT, true))).encode());
        assertTrue(s.version() > v);
        assertEquals(40, s.party().bonusPercent());
        assertTrue(s.partyInvites().isEmpty());
        assertFalse(s.invitePlayerToParty("Kuba"), "only the leader invites");
        assertTrue(s.leaveParty());
        assertEquals("PARTY LEAVE", sent.get(sent.size() - 1));
        s.handle(new String[] {"PARTY", "-"});
        assertFalse(s.party().inParty());

        // informacja o graczu: prosba do serwera, odpowiedz w pamieci, bez ponawiania przez 10 s
        assertFalse(s.playerInfo("Kuba").known());
        assertEquals("PINFO Kuba", sent.get(sent.size() - 1));
        int asked = sent.size();
        s.handle(new PlayerInfo("Kuba", 77, Vocation.MONK, "Wilki", true).encode());
        PlayerInfo k = s.playerInfo("kuba");
        assertEquals(77, k.level());
        assertEquals("Wilki", k.guild());
        assertEquals(asked, sent.size(), "cached for a while");
        now[0] += SocialClient.INFO_REFRESH_MS;
        s.playerInfo("kuba");
        assertEquals(asked + 1, sent.size());

        // znajomi: pierwsza prosba po cichu (bez otwierania okna)
        s.friends();
        assertEquals("FRIEND LIST", sent.get(sent.size() - 1));
        assertTrue(s.handle(new String[] {"FRIENDS", "Kuba;1;Monk;77;-1"}));
        assertTrue(s.friends().rows().get(0).online());
        assertFalse(s.handle(new String[] {"FRIENDS", "Kuba;0;Monk;77;5"}), "an answer to /znajomi opens the window");
        assertTrue(s.addFriend("Ela"));
        assertEquals("FRIEND ADD Ela", sent.get(sent.size() - 1));
        assertTrue(s.command(new String[] {"PARTY", "KICK", "Ela"}));
        assertEquals("PARTY KICK Ela", sent.get(sent.size() - 1));
    }

    @Test
    void playerInfoSurvivesTheWire() {
        PlayerInfo i = new PlayerInfo("Stary Wilk", 42, Vocation.DRUID, null, false);
        assertEquals(i, PlayerInfo.parse(i.encode()));
        assertEquals("Druid, poziom 42, poza grą", i.describe());
        assertFalse(PlayerInfo.parse(PlayerInfo.unknown("X").encode()).known());
    }
}
