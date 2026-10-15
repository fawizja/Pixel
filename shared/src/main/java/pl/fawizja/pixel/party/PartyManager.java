package pl.fawizja.pixel.party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Druzyny graczy (serwer): zaproszenie przez lidera, przyjecie przez zaproszonego, opuszczenie.
 * Gracze rozpoznawani po imieniu bez wzgledu na wielkosc liter. Zaproszenie wygasa po {@link #INVITE_MS}.
 * Druzyna, w ktorej zostaje jedna osoba, rozpada sie; po wyjsciu lidera przywodztwo przechodzi na nastepnego.
 */
public final class PartyManager {

    public static final int MAX_MEMBERS = 8;
    public static final long INVITE_MS = 60_000L;

    public enum Result {
        OK("OK"),
        SELF("Nie mozesz zaprosic samego siebie."),
        NOT_LEADER("Tylko lider druzyny moze zapraszac."),
        TARGET_IN_PARTY("Ten gracz jest juz w druzynie."),
        FULL("Druzyna jest pelna (maks. " + MAX_MEMBERS + ")."),
        NO_INVITE("Nie masz zaproszenia od tego gracza (albo wygaslo)."),
        ALREADY_IN_PARTY("Najpierw opusc obecna druzyne (/opusc)."),
        NOT_IN_PARTY("Nie jestes w druzynie.");

        public final String message;

        Result(String message) { this.message = message; }
    }

    private static final class Party {
        final List<String> members = new ArrayList<>(); // [0] = lider, imiona w oryginalnej pisowni
    }

    private final Map<String, Party> byMember = new HashMap<>();
    /** zaproszony -> (lider -> wygasa) */
    private final Map<String, Map<String, Long>> invites = new HashMap<>();

    private static String key(String name) { return name.toLowerCase(Locale.ROOT); }

    public Result invite(String leader, String target, long now) {
        if (key(leader).equals(key(target))) return Result.SELF;
        Party p = byMember.get(key(leader));
        if (p != null && !key(p.members.get(0)).equals(key(leader))) return Result.NOT_LEADER;
        if (byMember.containsKey(key(target))) return Result.TARGET_IN_PARTY;
        if (p != null && p.members.size() >= MAX_MEMBERS) return Result.FULL;
        invites.computeIfAbsent(key(target), k -> new HashMap<>()).put(key(leader), now + INVITE_MS);
        return Result.OK;
    }

    public Result accept(String target, String leader, long now) {
        if (byMember.containsKey(key(target))) return Result.ALREADY_IN_PARTY;
        Map<String, Long> mine = invites.get(key(target));
        Long until = mine == null ? null : mine.get(key(leader));
        if (until == null || now > until) return Result.NO_INVITE;
        Party p = byMember.get(key(leader));
        if (p != null && !key(p.members.get(0)).equals(key(leader))) return Result.NO_INVITE;
        if (p != null && p.members.size() >= MAX_MEMBERS) return Result.FULL;
        if (p == null) {
            p = new Party();
            p.members.add(leader);
            byMember.put(key(leader), p);
        }
        p.members.add(target);
        byMember.put(key(target), p);
        invites.remove(key(target));
        return Result.OK;
    }

    /** Opuszczenie druzyny (takze przy wylogowaniu). Zwraca pozostalych czlonkow (do powiadomienia). */
    public List<String> leave(String name) {
        invites.remove(key(name));
        Party p = byMember.remove(key(name));
        if (p == null) return Collections.emptyList();
        p.members.removeIf(m -> key(m).equals(key(name)));
        List<String> rest = new ArrayList<>(p.members);
        if (p.members.size() < 2) {
            for (String m : p.members) byMember.remove(key(m));
            p.members.clear();
        }
        return rest;
    }

    /** Czlonkowie druzyny gracza (lider pierwszy) albo pusta lista. */
    public List<String> members(String name) {
        Party p = byMember.get(key(name));
        return p == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(p.members));
    }

    public String leaderOf(String name) {
        Party p = byMember.get(key(name));
        return p == null ? null : p.members.get(0);
    }

    public boolean sameParty(String a, String b) {
        Party p = byMember.get(key(a));
        return p != null && p == byMember.get(key(b));
    }
}
