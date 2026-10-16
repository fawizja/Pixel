package pl.fawizja.pixel.guild;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gildie (trwale, zapisywane przez serwer): zalozenie za {@link #FOUNDING_COST} gp, zaproszenia lidera,
 * przyjecie, wyjscie, wyrzucenie. Gracz moze nalezec do jednej gildii. Po wyjsciu lidera przywodztwo
 * przechodzi na najdluzej obecnego czlonka; gildia bez czlonkow znika. Imiona bez wzgledu na wielkosc liter.
 */
public final class GuildRegistry {

    public static final long FOUNDING_COST = 50_000L;
    public static final int MAX_MEMBERS = 50;
    public static final long INVITE_MS = 120_000L;

    public enum Result {
        OK("OK"),
        BAD_NAME("Nazwa gildii: 3-20 liter i spacji."),
        NAME_TAKEN("Gildia o tej nazwie już istnieje."),
        ALREADY_MEMBER("Należysz już do gildii (/gildia opuść)."),
        TARGET_IN_GUILD("Ten gracz należy już do gildii."),
        NOT_MEMBER("Nie należysz do gildii."),
        NOT_LEADER("Tylko lider gildii może to zrobić."),
        NO_INVITE("Nie masz zaproszenia do tej gildii (albo wygasło)."),
        FULL("Gildia jest pełna (maks. " + MAX_MEMBERS + ")."),
        NOT_IN_THIS_GUILD("Ten gracz nie należy do twojej gildii."),
        SELF("Nie możesz tego zrobić samemu sobie.");

        public final String message;

        Result(String message) { this.message = message; }
    }

    /** Gildia: nazwa i czlonkowie w kolejnosci dolaczenia (pierwszy = lider). */
    public static final class Guild {
        private final String name;
        private final List<String> members = new ArrayList<>();
        /** Wiadomosc dnia od lidera (null = brak). */
        private String motd;

        Guild(String name) { this.name = name; }

        public String motd() { return motd; }

        public String name() { return name; }
        public String leader() { return members.get(0); }
        public List<String> members() { return Collections.unmodifiableList(members); }
    }

    private final Map<String, Guild> byName = new LinkedHashMap<>();
    private final Map<String, Guild> byMember = new HashMap<>();
    /** zaproszony -> (gildia -> waznosc) */
    private final Map<String, Map<String, Long>> invites = new HashMap<>();

    private static String key(String s) { return s.trim().toLowerCase(Locale.ROOT); }

    public static boolean validName(String name) {
        if (name == null) return false;
        String t = name.trim();
        return t.length() >= 3 && t.length() <= 20 && t.chars().allMatch(c -> Character.isLetter(c) || c == ' ')
                && !t.contains("  ");
    }

    /** Sprawdzenie przed pobraniem oplaty; {@link #create} sprawdza ponownie. */
    public Result canCreate(String founder, String name) {
        if (!validName(name)) return Result.BAD_NAME;
        if (byName.containsKey(key(name))) return Result.NAME_TAKEN;
        if (byMember.containsKey(key(founder))) return Result.ALREADY_MEMBER;
        return Result.OK;
    }

    public Result create(String founder, String name) {
        Result r = canCreate(founder, name);
        if (r != Result.OK) return r;
        Guild g = new Guild(name.trim());
        g.members.add(founder);
        byName.put(key(name), g);
        byMember.put(key(founder), g);
        invites.remove(key(founder));
        return Result.OK;
    }

    public Result invite(String leader, String target, long now) {
        Guild g = byMember.get(key(leader));
        if (g == null) return Result.NOT_MEMBER;
        if (!key(g.leader()).equals(key(leader))) return Result.NOT_LEADER;
        if (key(leader).equals(key(target))) return Result.SELF;
        if (byMember.containsKey(key(target))) return Result.TARGET_IN_GUILD;
        if (g.members.size() >= MAX_MEMBERS) return Result.FULL;
        invites.computeIfAbsent(key(target), k -> new HashMap<>()).put(key(g.name), now + INVITE_MS);
        return Result.OK;
    }

    public Result accept(String target, String guildName, long now) {
        if (byMember.containsKey(key(target))) return Result.ALREADY_MEMBER;
        Map<String, Long> mine = invites.get(key(target));
        Long until = mine == null ? null : mine.get(key(guildName));
        Guild g = byName.get(key(guildName));
        if (until == null || now > until || g == null) return Result.NO_INVITE;
        if (g.members.size() >= MAX_MEMBERS) return Result.FULL;
        g.members.add(target);
        byMember.put(key(target), g);
        invites.remove(key(target));
        return Result.OK;
    }

    /** Wyjscie z gildii; zwraca gildie (do powiadomienia pozostalych) albo null. */
    public Guild leave(String name) {
        Guild g = byMember.remove(key(name));
        if (g == null) return null;
        g.members.removeIf(m -> key(m).equals(key(name)));
        if (g.members.isEmpty()) byName.remove(key(g.name));
        return g;
    }

    public Result kick(String leader, String target) {
        Guild g = byMember.get(key(leader));
        if (g == null) return Result.NOT_MEMBER;
        if (!key(g.leader()).equals(key(leader))) return Result.NOT_LEADER;
        if (key(leader).equals(key(target))) return Result.SELF;
        if (byMember.get(key(target)) != g) return Result.NOT_IN_THIS_GUILD;
        leave(target);
        return Result.OK;
    }

    public static final int MOTD_MAX = 120;

    /** Wiadomosc dnia gildii (tylko lider); pusty tekst ja usuwa, dluga jest przycinana. */
    public Result setMotd(String leader, String text) {
        Guild g = byMember.get(key(leader));
        if (g == null) return Result.NOT_MEMBER;
        if (!key(g.leader()).equals(key(leader))) return Result.NOT_LEADER;
        String t = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ').trim();
        if (t.length() > MOTD_MAX) t = t.substring(0, MOTD_MAX);
        g.motd = t.isEmpty() ? null : t;
        return Result.OK;
    }

    public Guild guildOf(String name) { return byMember.get(key(name)); }

    public Guild byName(String name) { return byName.get(key(name)); }

    /** Usuniecie konta: gracz znika z gildii. */
    public void forget(String name) {
        leave(name);
        invites.remove(key(name));
    }

    public int count() { return byName.size(); }

    public java.util.Collection<Guild> all() { return Collections.unmodifiableCollection(byName.values()); }

    /** Zapis tekstowy: linia na gildie "Nazwa;Lider,Czlonek,...[;wiadomosc dnia]". */
    public String encode() {
        StringBuilder sb = new StringBuilder();
        for (Guild g : byName.values()) {
            sb.append(g.name).append(';').append(String.join(",", g.members));
            if (g.motd != null) sb.append(';').append(g.motd);
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Odczyt zapisu; bledne linie i powtorzone imiona sa pomijane. */
    public static GuildRegistry decode(String text) {
        GuildRegistry r = new GuildRegistry();
        if (text == null) return r;
        for (String line : text.split("\n")) {
            String[] f = line.split(";", 3);
            if (f.length < 2 || !validName(f[0]) || r.byName.containsKey(key(f[0]))) continue;
            Guild g = new Guild(f[0].trim());
            for (String m : f[1].split(",")) {
                String n = m.trim();
                if (n.isEmpty() || r.byMember.containsKey(key(n))) continue;
                g.members.add(n);
                r.byMember.put(key(n), g);
            }
            if (f.length == 3 && !f[2].isBlank()) g.motd = f[2].trim();
            if (!g.members.isEmpty()) r.byName.put(key(g.name), g);
        }
        return r;
    }
}
