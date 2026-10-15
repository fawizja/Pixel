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
        g.create("Kuba", "Nocna Straz");
        GuildRegistry back = GuildRegistry.decode(g.encode() + "zle;\n;;\nX;Ktos\nWilcze Kly;Duplikat\n");
        assertEquals(2, back.count());
        assertEquals(List.of("Ola", "Ula"), back.guildOf("ula").members());
        assertEquals("Nocna Straz", back.guildOf("Kuba").name());
        assertEquals(0, GuildRegistry.decode(null).count());
    }

    @Test
    void guildChatCommands() {
        assertArrayEquals(new String[] {"GUILD", "INFO"}, ChatCommands.toMessage("/gildia"));
        assertArrayEquals(new String[] {"GUILD", "CREATE", "Wilcze Kly"}, ChatCommands.toMessage("/gildia zaloz Wilcze Kly"));
        assertArrayEquals(new String[] {"GUILD", "INVITE", "Ula"}, ChatCommands.toMessage("/gildia zapros Ula"));
        assertArrayEquals(new String[] {"GUILD", "ACCEPT", "Wilcze Kly"}, ChatCommands.toMessage("/gildia przyjmij Wilcze Kly"));
        assertArrayEquals(new String[] {"GUILD", "KICK", "Ula"}, ChatCommands.toMessage("/gildia wyrzuc Ula"));
        assertArrayEquals(new String[] {"GUILD", "LEAVE"}, ChatCommands.toMessage("/gildia opusc"));
        assertArrayEquals(new String[] {"GCHAT", "zbiorka w Varden"}, ChatCommands.toMessage("/g zbiorka w Varden"));
        assertNull(ChatCommands.toMessage("/gildia zaloz"));
        assertNull(ChatCommands.toMessage("/gildia cos"));
    }
}
