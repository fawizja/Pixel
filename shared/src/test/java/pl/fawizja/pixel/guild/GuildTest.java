package pl.fawizja.pixel.guild;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.guild.GuildRegistry.Result;
import pl.fawizja.pixel.net.ChatCommands;

class GuildTest {

    @Test
    void foundInviteAcceptKickAndLeadership() {
        GuildRegistry g = new GuildRegistry();
        assertEquals(Result.BAD_NAME, g.create("Ola", "X1"));
        assertEquals(Result.OK, g.create("Ola", "Zakon Swietlika"));
        assertEquals(Result.NAME_TAKEN, g.create("Ula", "zakon swietlika"));
        assertEquals(Result.ALREADY_MEMBER, g.create("ola", "Inna Gildia"));
        assertEquals(Result.NO_INVITE, g.accept("Ula", "Zakon Swietlika", 0));
        assertEquals(Result.OK, g.invite("Ola", "Ula", 0));
        assertEquals(Result.NO_INVITE, g.accept("Ula", "Zakon Swietlika", GuildRegistry.INVITE_MS + 1));
        assertEquals(Result.OK, g.invite("Ola", "Ula", 1_000_000));
        assertEquals(Result.OK, g.accept("Ula", "zakon swietlika", 1_000_100));
        assertEquals(Result.NOT_LEADER, g.invite("Ula", "Ela", 1_000_200));
        assertEquals(Result.OK, g.invite("Ola", "Ela", 1_000_200));
        assertEquals(Result.OK, g.accept("Ela", "Zakon Swietlika", 1_000_300));
        assertEquals(List.of("Ola", "Ula", "Ela"), g.guildOf("ELA").members());

        assertEquals(Result.NOT_LEADER, g.kick("Ula", "Ela"));
        assertEquals(Result.OK, g.kick("Ola", "Ela"));
        assertNull(g.guildOf("Ela"));
        assertEquals(Result.NOT_IN_THIS_GUILD, g.kick("Ola", "Ela"));
        // lider odchodzi - przywodztwo dla najdluzej obecnego
        g.leave("Ola");
        assertEquals("Ula", g.guildOf("Ula").leader());
        g.leave("Ula");
        assertNull(g.byName("Zakon Swietlika"), "an empty guild disappears");
        assertEquals(0, g.count());
    }

    @Test
    void guildsSurviveSavingAndBrokenLinesAreSkipped() {
        GuildRegistry g = new GuildRegistry();
        g.create("Ola", "Wilcze Kly");
        g.invite("Ola", "Ula", 0);
        g.accept("Ula", "Wilcze Kly", 0);
        g.create("Kuba", "Nocna Straż");
        GuildRegistry back = GuildRegistry.decode(g.encode() + "źle;\n;;\nX;Ktoś\nWilcze Kly;Duplikat\n");
        assertEquals(2, back.count());
        assertEquals(List.of("Ola", "Ula"), back.guildOf("ula").members());
        assertEquals("Nocna Straż", back.guildOf("Kuba").name());
        assertEquals(0, GuildRegistry.decode(null).count());
    }

    @Test
    void leaderSetsAMessageOfTheDayThatIsSaved() {
        GuildRegistry g = new GuildRegistry();
        assertEquals(GuildRegistry.Result.OK, g.create("Ola", "Wilcze Kly"));
        assertEquals(GuildRegistry.Result.OK, g.invite("Ola", "Ula", 0));
        assertEquals(GuildRegistry.Result.OK, g.accept("Ula", "Wilcze Kly", 1));
        assertEquals(GuildRegistry.Result.NOT_LEADER, g.setMotd("Ula", "hej"));
        assertEquals(GuildRegistry.Result.NOT_MEMBER, g.setMotd("Kuba", "hej"));
        assertEquals(GuildRegistry.Result.OK, g.setMotd("Ola", "Wyprawa na smoka o 20:00; zbiórka w Varden\nnowa linia"));
        assertEquals("Wyprawa na smoka o 20:00; zbiórka w Varden nowa linia", g.guildOf("Ula").motd());
        GuildRegistry back = GuildRegistry.decode(g.encode());
        assertEquals(g.guildOf("Ula").motd(), back.guildOf("Ula").motd(), "a semicolon in the message survives");
        assertEquals(2, back.byName("Wilcze Kly").members().size());
        g.setMotd("Ola", "x".repeat(500));
        assertEquals(GuildRegistry.MOTD_MAX, g.guildOf("Ola").motd().length());
        g.setMotd("Ola", "  ");
        assertNull(g.guildOf("Ola").motd());
        assertNull(GuildRegistry.decode(g.encode()).guildOf("Ola").motd());
        assertArrayEquals(new String[] {"GUILD", "MOTD", "zbiórka o 20"}, pl.fawizja.pixel.net.ChatCommands.toMessage("/gildia wiadomość zbiórka o 20"));
    }

    @Test
    void treasuryTakesDepositsAndOnlyTheLeaderWithdraws() {
        GuildRegistry g = new GuildRegistry();
        g.create("Ola", "Wilcze Kly");
        g.invite("Ola", "Ula", 0);
        g.accept("Ula", "Wilcze Kly", 1);
        assertEquals(GuildRegistry.Result.OK, g.deposit("Ula", 5000));
        assertEquals(GuildRegistry.Result.NOT_MEMBER, g.deposit("Kuba", 5000));
        assertEquals(GuildRegistry.Result.BAD_AMOUNT, g.deposit("Ula", 0));
        assertEquals(GuildRegistry.Result.NOT_LEADER, g.withdraw("Ula", 100));
        assertEquals(GuildRegistry.Result.TREASURY_LOW, g.withdraw("Ola", 6000));
        assertEquals(GuildRegistry.Result.OK, g.withdraw("Ola", 1000));
        assertEquals(4000, g.guildOf("Ula").treasury());
        g.setMotd("Ola", "bank=5; zbiórka");
        GuildRegistry back = GuildRegistry.decode(g.encode());
        assertEquals(4000, back.guildOf("Ola").treasury());
        assertEquals("bank=5; zbiórka", back.guildOf("Ola").motd(), "a message that looks like the bank field survives");
        g.setMotd("Ola", "");
        back = GuildRegistry.decode(g.encode());
        assertEquals(4000, back.guildOf("Ola").treasury());
        assertNull(back.guildOf("Ola").motd());
        assertEquals(0, GuildRegistry.decode("Stara Gildia;Ola;Witajcie\n").guildOf("Ola").treasury(), "old saves");
        assertArrayEquals(new String[] {"GUILD", "DEPOSIT", "5000"}, ChatCommands.toMessage("/gildia wpłać 5k"));
        assertArrayEquals(new String[] {"GUILD", "WITHDRAW", "200"}, ChatCommands.toMessage("/gildia wypłać 200"));
        assertNull(ChatCommands.toMessage("/gildia wpłać"));
    }

    @Test
    void guildChatCommands() {
        assertArrayEquals(new String[] {"GUILD", "INFO"}, ChatCommands.toMessage("/gildia"));
        assertArrayEquals(new String[] {"GUILD", "CREATE", "Wilcze Kly"}, ChatCommands.toMessage("/gildia załóż Wilcze Kly"));
        assertArrayEquals(new String[] {"GUILD", "INVITE", "Ula"}, ChatCommands.toMessage("/gildia zaproś Ula"));
        assertArrayEquals(new String[] {"GUILD", "ACCEPT", "Wilcze Kly"}, ChatCommands.toMessage("/gildia przyjmij Wilcze Kly"));
        assertArrayEquals(new String[] {"GUILD", "KICK", "Ula"}, ChatCommands.toMessage("/gildia wyrzuć Ula"));
        assertArrayEquals(new String[] {"GUILD", "LEAVE"}, ChatCommands.toMessage("/gildia opuść"));
        assertArrayEquals(new String[] {"GCHAT", "zbiorka w Varden"}, ChatCommands.toMessage("/g zbiorka w Varden"));
        assertNull(ChatCommands.toMessage("/gildia załóż"));
        assertNull(ChatCommands.toMessage("/gildia cos"));
    }
}
