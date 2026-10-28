package pl.fawizja.pixel.social;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.friends.FriendList;
import pl.fawizja.pixel.friends.FriendsView;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.party.PartyManager;
import pl.fawizja.pixel.party.PartyView;

/**
 * Strona klienta dla druzyny, znajomych i informacji o graczach - jedno API dla HUD (okno po dotknieciu gracza),
 * polecen czatu i trybu offline. Online kazda akcja idzie do serwera przez {@link Link} (serwer jest zrodlem
 * prawdy, odpowiedz przychodzi w dzienniku i w komunikatach PARTY / PARTYINFO / PARTYINV / PINFO / FRIENDS, ktore
 * klient przekazuje do {@link #handle}). Offline nie ma innych graczy: druzyna zwraca {@link PartyView#NONE}
 * i komunikat "dziala online", a znajomi zapisuja sie w postaci (bez stanu w grze).
 *
 * <p>Metody akcji zwracaja true, gdy prosba zostala wyslana (online) albo wykonana (offline), false, gdy od razu
 * wiadomo, ze sie nie uda (powod trafia do dziennika). {@link #version()} rosnie przy kazdej zmianie danych,
 * aby okno moglo sie odswiezyc.
 */
public final class SocialClient {

    /** Polaczenie z serwerem (klient online). */
    public interface Link {
        void send(String... fields);

        /** Czas swiata (wpisy w dzienniku). */
        long now();
    }

    /** Po tylu ms informacja o graczu jest pobierana z serwera ponownie. */
    public static final long INFO_REFRESH_MS = 10_000L;

    private final PlayerCharacter pc;
    private final EventLog log;
    private final LongSupplier offlineNow;
    private Link link;
    private PartyView party = PartyView.NONE;
    /** lider (oryginalna pisownia) -> kiedy przyszlo zaproszenie */
    private final Map<String, Long> invites = new LinkedHashMap<>();
    private final Map<String, PlayerInfo> infos = new HashMap<>();
    private final Map<String, Long> infoAsked = new HashMap<>();
    private FriendsView friends;
    /** Ile odpowiedzi FRIENDS ma przyjsc "po cichu" (bez otwierania okna znajomych). */
    private int silentFriends;
    private long version;

    public SocialClient(PlayerCharacter pc, EventLog log, LongSupplier offlineNow) {
        this.pc = pc;
        this.log = log;
        this.offlineNow = offlineNow;
    }

    public void setLink(Link link) {
        this.link = link;
        changed();
    }

    public boolean online() { return link != null; }

    public long version() { return version; }

    private void changed() { version++; }

    private long now() { return link != null ? link.now() : offlineNow.getAsLong(); }

    private static String key(String name) { return name == null ? "" : name.trim().toLowerCase(Locale.ROOT); }

    private boolean blank(String name) {
        if (name != null && !name.isBlank()) return false;
        log.add(now(), EventLog.Kind.WARNING, "Podaj imię gracza.");
        return true;
    }

    private boolean offlineParty() {
        log.add(now(), EventLog.Kind.INFO, PartyManager.Result.ONLINE_ONLY.message);
        return false;
    }

    // ---------------------------------------------------------------- druzyna

    /** Zaproszenie gracza do druzyny (ja jestem liderem albo zakladam druzyne). */
    public boolean invitePlayerToParty(String name) {
        if (blank(name)) return false;
        if (key(name).equals(key(pc.name()))) {
            log.add(now(), EventLog.Kind.WARNING, PartyManager.Result.SELF.message);
            return false;
        }
        if (link == null) return offlineParty();
        if (party.inParty() && !party.isLeader(pc.name())) {
            log.add(now(), EventLog.Kind.WARNING, PartyManager.Result.NOT_LEADER.message);
            return false;
        }
        link.send("PARTY", "INVITE", name.trim());
        return true;
    }

    /** Przyjecie zaproszenia od lidera. */
    public boolean acceptPartyInvite(String leader) {
        if (blank(leader)) return false;
        if (link == null) return offlineParty();
        link.send("PARTY", "ACCEPT", leader.trim());
        return true;
    }

    /** Przyjecie najnowszego zaproszenia (przycisk "Przyjmij" bez wybierania lidera). */
    public boolean acceptPartyInvite() {
        List<String> pending = partyInvites();
        if (pending.isEmpty()) {
            log.add(now(), EventLog.Kind.WARNING, PartyManager.Result.NO_INVITE.message);
            return false;
        }
        return acceptPartyInvite(pending.get(pending.size() - 1));
    }

    public boolean leaveParty() {
        if (link == null) return offlineParty();
        if (!party.inParty()) {
            log.add(now(), EventLog.Kind.WARNING, PartyManager.Result.NOT_IN_PARTY.message);
            return false;
        }
        link.send("PARTY", "LEAVE");
        return true;
    }

    /** Lider usuwa gracza z druzyny. */
    public boolean kickFromParty(String name) {
        if (blank(name)) return false;
        if (link == null) return offlineParty();
        if (party.inParty() && !party.isLeader(pc.name())) {
            log.add(now(), EventLog.Kind.WARNING, "Tylko lider drużyny może wyrzucać.");
            return false;
        }
        link.send("PARTY", "KICK", name.trim());
        return true;
    }

    /** Moja druzyna (lider pierwszy, poziomy, profesje, premia) albo {@link PartyView#NONE}. */
    public PartyView party() { return party; }

    /** Liderzy, ktorzy mnie zaprosili (zaproszenia jeszcze waznie), najstarsze pierwsze. */
    public List<String> partyInvites() {
        long t = now();
        invites.values().removeIf(at -> t - at > PartyManager.INVITE_MS || t < at - PartyManager.INVITE_MS);
        return Collections.unmodifiableList(new ArrayList<>(invites.keySet()));
    }

    /** Czy gracz moze dostac ode mnie zaproszenie (dla przycisku w oknie gracza). */
    public boolean canInvite(String name) {
        return name != null && !key(name).equals(key(pc.name())) && !party.contains(name)
                && (!party.inParty() || party.isLeader(pc.name()));
    }

    // ---------------------------------------------------------------- informacje o graczu

    /**
     * Co wiadomo o graczu: online z serwera (pierwsze wywolanie wysyla prosbe i zwraca to, co juz wiadomo -
     * {@link PlayerInfo#known()} = false, dopoki serwer nie odpowie; {@link #version()} wzrosnie po odpowiedzi).
     * Offline znamy tylko siebie.
     */
    public PlayerInfo playerInfo(String name) {
        if (name == null || name.isBlank()) return PlayerInfo.unknown("");
        String k = key(name);
        if (k.equals(key(pc.name()))) return new PlayerInfo(pc.name(), pc.level(), pc.vocation(), null, link != null);
        PlayerInfo cached = infos.get(k);
        if (link != null) {
            Long asked = infoAsked.get(k);
            long t = now();
            if (asked == null || t - asked >= INFO_REFRESH_MS || t < asked) {
                infoAsked.put(k, t);
                link.send("PINFO", name.trim());
            }
        }
        if (cached != null) return cached;
        for (PartyView.Member m : party.members()) {
            if (key(m.name()).equals(k)) return new PlayerInfo(m.name(), m.level(), m.vocation(), null, m.online());
        }
        return PlayerInfo.unknown(name.trim());
    }

    // ---------------------------------------------------------------- znajomi

    public boolean addFriend(String name) {
        if (blank(name)) return false;
        if (link != null) {
            link.send("FRIEND", "ADD", name.trim());
            return true;
        }
        FriendList.Result r = pc.friends().add(pc.name(), name.trim());
        if (r != FriendList.Result.OK) {
            log.add(now(), EventLog.Kind.WARNING, r.message);
            return false;
        }
        log.add(now(), EventLog.Kind.INFO, name.trim() + " dodany do znajomych.");
        changed();
        return true;
    }

    public boolean removeFriend(String name) {
        if (blank(name)) return false;
        if (link != null) {
            link.send("FRIEND", "REMOVE", name.trim());
            return true;
        }
        FriendList.Result r = pc.friends().remove(name);
        if (r != FriendList.Result.OK) {
            log.add(now(), EventLog.Kind.WARNING, r.message);
            return false;
        }
        log.add(now(), EventLog.Kind.INFO, name.trim() + " usunięty ze znajomych.");
        changed();
        return true;
    }

    /**
     * Lista znajomych ze stanem w grze. Online: ostatnia lista z serwera (pierwsze wywolanie po cichu prosi o nia
     * i do odpowiedzi zwraca imiona z zapisu postaci jako "poza gra"). Offline: imiona z postaci, bez stanu.
     */
    public FriendsView friends() {
        if (link != null && friends == null && silentFriends == 0) {
            silentFriends++;
            link.send("FRIEND", "LIST");
        }
        if (link != null && friends != null) return friends;
        List<FriendsView.Row> rows = new ArrayList<>();
        for (String n : pc.friends().names()) {
            PlayerInfo i = infos.get(key(n));
            rows.add(new FriendsView.Row(n, false, i == null || i.vocation() == null ? "-" : i.vocation().displayName(),
                    i == null ? 0 : i.level()));
        }
        return new FriendsView(rows);
    }

    /** Odswiezenie listy znajomych z serwera bez otwierania okna. */
    public void refreshFriends() {
        if (link == null) return;
        silentFriends++;
        link.send("FRIEND", "LIST");
    }

    // ---------------------------------------------------------------- komunikaty

    /**
     * Komunikat serwera dla tej czesci (PARTY, PARTYINFO, PARTYINV, PINFO, FRIENDS). Zwraca true, gdy byl to
     * cichy komunikat, ktorego klient nie musi pokazywac (odpowiedz na {@link #friends()} albo {@link #refreshFriends()}).
     */
    public boolean handle(String[] m) {
        if (m == null || m.length == 0) return false;
        switch (m[0]) {
            case "PARTY":
                if (m.length >= 2 && m[1].equals("-")) {
                    party = PartyView.NONE;
                } else if (m.length >= 2) {
                    // sama lista imion (starszy serwer albo przed PARTYINFO): poziomy z tego, co juz wiadomo
                    List<PartyView.Member> rows = new ArrayList<>();
                    for (String n : m[1].split(",")) {
                        PartyView.Member old = null;
                        for (PartyView.Member x : party.members()) if (x.name().equalsIgnoreCase(n)) old = x;
                        rows.add(old != null ? old : new PartyView.Member(n, 0, null, true));
                    }
                    party = PartyView.of(rows);
                }
                if (party.inParty()) invites.clear();
                changed();
                return false;
            case "PARTYINFO": {
                PartyView v = PartyView.parse(m);
                if (v != null) {
                    party = v;
                    if (v.inParty()) invites.clear();
                    changed();
                }
                return false;
            }
            case "PARTYINV":
                if (m.length >= 2 && !m[1].isBlank()) {
                    invites.remove(m[1]);
                    invites.put(m[1], now());
                    changed();
                }
                return false;
            case "PINFO": {
                PlayerInfo i = PlayerInfo.parse(m);
                if (i != null) {
                    infos.put(key(i.name()), i);
                    changed();
                }
                return false;
            }
            case "FRIENDS": {
                FriendsView v = FriendsView.parse(m);
                if (v != null) {
                    friends = v;
                    changed();
                }
                if (silentFriends > 0) {
                    silentFriends--;
                    return true;
                }
                return false;
            }
            default:
                return false;
        }
    }

    /**
     * Polecenie z czatu zamienione przez {@link pl.fawizja.pixel.net.ChatCommands} (PARTY ... / FRIEND ...),
     * wykonane przez to API. Zwraca false dla innych komunikatow.
     */
    public boolean command(String[] msg) {
        if (msg == null || msg.length < 2) return false;
        String arg = msg.length >= 3 ? msg[2] : null;
        if (msg[0].equals("PARTY")) {
            switch (msg[1]) {
                case "INVITE": invitePlayerToParty(arg); return true;
                case "ACCEPT":
                    if (arg == null) acceptPartyInvite();
                    else acceptPartyInvite(arg);
                    return true;
                case "LEAVE": leaveParty(); return true;
                case "KICK": kickFromParty(arg); return true;
                case "INFO":
                    if (link != null) link.send("PARTY", "INFO");
                    else offlineParty();
                    return true;
                default: return false;
            }
        }
        if (msg[0].equals("FRIEND")) {
            switch (msg[1]) {
                case "ADD": addFriend(arg); return true;
                case "REMOVE": removeFriend(arg); return true;
                default: return false;
            }
        }
        return false;
    }
}
