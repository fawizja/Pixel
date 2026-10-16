package pl.fawizja.pixel.ranking;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import pl.fawizja.pixel.guild.GuildRegistry;

/** Ranking gildii: suma poziomow czlonkow (takze offline, z rankingu postaci). */
public final class GuildRanking {
    public static final String TITLE = "Ranking gildii (suma poziomów)";

    private GuildRanking() {}

    public record Score(String guild, int members, long levels) {}

    public static List<Score> scores(Collection<GuildRegistry.Guild> guilds, Ranking characters) {
        List<Score> out = new ArrayList<>();
        for (GuildRegistry.Guild g : guilds) {
            long sum = 0;
            for (String m : g.members()) {
                Ranking.Entry e = characters.get(m);
                if (e != null) sum += e.level();
            }
            out.add(new Score(g.name(), g.members().size(), sum));
        }
        out.sort((a, b) -> a.levels() != b.levels() ? Long.compare(b.levels(), a.levels()) : a.guild().compareToIgnoreCase(b.guild()));
        return out;
    }

    /** Widok dla gracza: pierwsze {@link Ranking#SHOWN} gildii i miejsce jego gildii (0 = brak). */
    public static RankingView view(List<Score> scores, String myGuild) {
        List<RankingView.Row> rows = new ArrayList<>();
        int mine = 0;
        for (int i = 0; i < scores.size(); i++) {
            Score s = scores.get(i);
            if (myGuild != null && s.guild().equalsIgnoreCase(myGuild)) mine = i + 1;
            if (i < Ranking.SHOWN) {
                rows.add(new RankingView.Row(i + 1, s.guild(), s.members() + (s.members() == 1 ? " członek" : " członków"),
                        (int) Math.min(Integer.MAX_VALUE, s.levels())));
            }
        }
        return new RankingView(TITLE, mine, scores.size(), rows);
    }

    /** Czy argument /ranking oznacza ranking gildii. */
    public static boolean isGuildArg(String arg) {
        if (arg == null) return false;
        String a = java.text.Normalizer.normalize(arg.trim().toLowerCase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return a.equals("gildie") || a.equals("gildia") || a.equals("gildii") || a.equals("guilds");
    }
}
