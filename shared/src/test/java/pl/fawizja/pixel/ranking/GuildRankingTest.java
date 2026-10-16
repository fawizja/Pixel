package pl.fawizja.pixel.ranking;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.guild.GuildRegistry;
import pl.fawizja.pixel.vocation.Vocation;

class GuildRankingTest {
    @Test
    void guildsAreRankedByTheSumOfTheirMembersLevels() {
        GuildRegistry g = new GuildRegistry();
        g.create("Ola", "Wilcze Kly");
        g.invite("Ola", "Ula", 0);
        g.accept("Ula", "Wilcze Kly", 1);
        g.create("Kuba", "Smoki");
        g.create("Nowy", "Puste Sakwy");
        Ranking r = new Ranking();
        r.put(new Ranking.Entry("Ola", Vocation.PALADIN, 40, 0));
        r.put(new Ranking.Entry("Ula", Vocation.DRUID, 30, 0));
        r.put(new Ranking.Entry("Kuba", Vocation.MONK, 90, 0));
        // "Nowy" nie ma wpisu w rankingu postaci: liczy sie 0

        List<GuildRanking.Score> s = GuildRanking.scores(g.all(), r);
        assertEquals(List.of("Smoki", "Wilcze Kly", "Puste Sakwy"), s.stream().map(GuildRanking.Score::guild).toList());
        assertEquals(70, s.get(1).levels());
        assertEquals(2, s.get(1).members());

        RankingView v = GuildRanking.view(s, "wilcze kly");
        assertEquals(2, v.mine());
        assertEquals(3, v.total());
        assertEquals(GuildRanking.TITLE, RankingView.parse(prepend(v.encode())).title(), "survives the message format");
        assertEquals("2 członków", v.rows().get(1).vocation());
        assertEquals(0, GuildRanking.view(s, null).mine());

        assertTrue(GuildRanking.isGuildArg(" Gildie "));
        assertFalse(GuildRanking.isGuildArg("druid"));
        assertFalse(GuildRanking.isGuildArg(null));
    }

    private static String[] prepend(String[] f) {
        String[] m = new String[f.length + 1];
        m[0] = "RANK";
        System.arraycopy(f, 0, m, 1, f.length);
        return m;
    }
}
