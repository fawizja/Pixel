package pl.fawizja.pixel.chat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import pl.fawizja.pixel.game.EventLog;

/**
 * Zakladki konsoli czatu jak w Tibii (2026-10-28, Darek: "w czacie daj zakladki jak w tibii"): Lokalny, Serwer,
 * Handel, a na zadanie Gildia, Druzyna i rozmowy prywatne. Kazda wiadomosc trafia do swojej zakladki; zakladka z
 * nowa cudza wiadomoscia, ktora nie jest wybrana, jest "nieprzeczytana" (HUD pisze jej nazwe na czerwono).
 * Tekst wpisany w wybranej zakladce idzie na jej kanal ({@link #outgoing}).
 *
 * <p>Zrodla: pola CHAT z serwera ({@link #receive}: nadawca, tekst i opcjonalny kanal) oraz dziennik zdarzen sesji
 * ({@link #sync}): wpisy CHAT (NPC, wlasne slowa, wiesci gildii) do Lokalnego/Gildii, cala reszta do Serwera.
 */
public final class ChatTabs {

    /** Kanal zakladki; PRIVATE ma rozmowce (peer). */
    public enum Channel {
        LOCAL("Lokalny"), SERVER("Serwer"), TRADE("Handel"), GUILD("Gildia"), PARTY("Drużyna"), PRIVATE(null);

        public final String title;

        Channel(String title) { this.title = title; }
    }

    /** Rodzaj wiersza - HUD dobiera po nim kolor. */
    public enum Style { SAY, OWN, NPC, PRIVATE_IN, PRIVATE_OUT, CHANNEL, BROADCAST, INFO, WARNING, LOOT, SYSTEM }

    public record Line(long timeMs, String text, Style style) {}

    /** Gdzie trafia wiadomosc: kanal, rozmowca (dla PRIVATE), tekst wiersza, styl, czy to wlasna wiadomosc. */
    public record Route(Channel channel, String peer, String text, Style style, boolean own) {}

    /** Zakladka: kanal, rozmowca, ostatnie wiersze i znacznik nieprzeczytanych. */
    public static final class Tab {
        private final Channel channel;
        private final String peer;
        private final ArrayDeque<Line> lines = new ArrayDeque<>();
        private boolean unread;

        Tab(Channel channel, String peer) {
            this.channel = channel;
            this.peer = peer;
        }

        public Channel channel() { return channel; }
        public String peer() { return peer; }
        public String title() { return channel == Channel.PRIVATE ? peer : channel.title; }
        public boolean unread() { return unread; }
        public List<Line> lines() { return new ArrayList<>(lines); }
        public int size() { return lines.size(); }
        /** Rozmowy prywatne zamyka sie jak w Tibii (krzyzyk na zakladce); stalych kanalow nie. */
        public boolean closable() { return channel == Channel.PRIVATE; }

        boolean is(Channel c, String p) {
            return channel == c && (c != Channel.PRIVATE || peer.equalsIgnoreCase(p));
        }
    }

    public static final int MAX_LINES = 200;
    public static final int MAX_PRIVATE = 8;

    private final List<Tab> tabs = new ArrayList<>();
    private int active;
    private String self = "";
    private long seenLog;

    public ChatTabs() {
        tabs.add(new Tab(Channel.LOCAL, null));
        tabs.add(new Tab(Channel.SERVER, null));
        tabs.add(new Tab(Channel.TRADE, null));
    }

    /** Imie wlasnej postaci (wlasne wiadomosci nie oznaczaja zakladki jako nieprzeczytanej). */
    public void setSelf(String name) { self = name == null ? "" : name; }

    public List<Tab> tabs() { return java.util.Collections.unmodifiableList(tabs); }
    public int activeIndex() { return active; }
    public Tab active() { return tabs.get(active); }

    public void select(int i) {
        if (i < 0 || i >= tabs.size()) return;
        active = i;
        tabs.get(i).unread = false;
    }

    /** Nastepna / poprzednia zakladka (klawisz Tab na komputerze). */
    public void cycle(int dir) {
        select(Math.floorMod(active + dir, tabs.size()));
    }

    /** Zamyka zakladke rozmowy prywatnej; wybrana staje sie poprzednia. */
    public boolean close(int i) {
        if (i < 0 || i >= tabs.size() || !tabs.get(i).closable()) return false;
        tabs.remove(i);
        if (active >= i) active = Math.max(0, active - 1);
        tabs.get(active).unread = false;
        return true;
    }

    /** Otwiera (albo wybiera istniejaca) rozmowe z graczem. */
    public Tab openPrivate(String name) {
        int i = index(Channel.PRIVATE, name.trim(), true);
        select(i);
        return tabs.get(i);
    }

    /** Pokazuje zakladke kanalu (np. Druzyna po dolaczeniu), bez wybierania jej. */
    public void ensure(Channel c) {
        if (c != Channel.PRIVATE) index(c, null, true);
    }

    public int indexOf(Channel c, String peer) { return index(c, peer, false); }

    /** Indeks zakladki; stale kanaly trzymaja kolejnosc Lokalny, Serwer, Handel, Gildia, Druzyna, potem prywatne. */
    private int index(Channel c, String peer, boolean create) {
        for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).is(c, peer)) return i;
        if (!create) return -1;
        Tab t = new Tab(c, peer);
        int at = tabs.size();
        if (c != Channel.PRIVATE) {
            at = 0;
            while (at < tabs.size() && tabs.get(at).channel != Channel.PRIVATE && tabs.get(at).channel.ordinal() < c.ordinal()) at++;
        } else {
            dropOldPrivate();
            at = tabs.size();
        }
        tabs.add(at, t);
        if (at <= active && tabs.size() > 1) active++;
        return at;
    }

    /** Za duzo rozmow: zamyka najstarsza przeczytana (nie wybrana). */
    private void dropOldPrivate() {
        int count = 0;
        for (Tab t : tabs) if (t.channel == Channel.PRIVATE) count++;
        if (count < MAX_PRIVATE) return;
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).channel == Channel.PRIVATE && i != active && !tabs.get(i).unread) {
                close(i);
                return;
            }
        }
    }

    /** Dopisuje wiersz do zakladki; cudza wiadomosc w niewybranej zakladce zapala znacznik nieprzeczytanych. */
    public int add(Route r, long timeMs) {
        int i = index(r.channel(), r.peer(), true);
        Tab t = tabs.get(i);
        t.lines.addLast(new Line(timeMs, r.text(), r.style()));
        while (t.lines.size() > MAX_LINES) t.lines.removeFirst();
        if (i != active && !r.own()) t.unread = true;
        return i;
    }

    /** Wiadomosc CHAT z serwera: nadawca (pole 1), tekst (pole 2) i kanal (pole 3, opcjonalne). */
    public int receive(String from, String text, String channel, long timeMs) {
        return add(route(from, text, channel, self), timeMs);
    }

    /**
     * Kanal wiadomosci CHAT. Serwer podaje kanal ("local", "pm", "letter", "guild", "party", "trade", "broadcast");
     * starszy serwer go nie podaje - wtedy decyduje nadawca: "[od X]"/"[do X]" prywatne, "[list] X" list,
     * "[Drużyna] X", "[Handel] X", "[Ogłoszenie]", inny "[...] X" to gildia, samo imie to czat lokalny.
     */
    public static Route route(String from, String text, String channel, String self) {
        String f = from == null ? "" : from.trim();
        String ch = channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT);
        String sender = senderOf(f);
        if (f.startsWith("[od ") && f.endsWith("]") && (ch.isEmpty() || ch.equals("pm"))) {
            String who = f.substring(4, f.length() - 1);
            return new Route(Channel.PRIVATE, who, who + ": " + text, Style.PRIVATE_IN, false);
        }
        if (f.startsWith("[do ") && f.endsWith("]") && (ch.isEmpty() || ch.equals("pm"))) {
            String who = f.substring(4, f.length() - 1);
            String me = self == null || self.isEmpty() ? "Ty" : self;
            return new Route(Channel.PRIVATE, who, me + ": " + text, Style.PRIVATE_OUT, true);
        }
        if (ch.equals("letter") || ch.isEmpty() && f.startsWith("[list] ")) {
            return new Route(Channel.PRIVATE, sender, sender + " (list): " + text, Style.PRIVATE_IN, false);
        }
        if (ch.equals("broadcast") || ch.isEmpty() && f.equals("[Ogłoszenie]")) {
            return new Route(Channel.SERVER, null, "Ogłoszenie: " + text, Style.BROADCAST, false);
        }
        boolean own = sender != null && self != null && sender.equalsIgnoreCase(self);
        if (ch.equals("party") || ch.isEmpty() && f.startsWith("[Drużyna] ")) {
            return new Route(Channel.PARTY, null, sender + ": " + text, own ? Style.OWN : Style.CHANNEL, own);
        }
        if (ch.equals("trade") || ch.isEmpty() && f.startsWith("[Handel] ")) {
            return new Route(Channel.TRADE, null, sender + ": " + text, own ? Style.OWN : Style.CHANNEL, own);
        }
        if (ch.equals("guild") || ch.isEmpty() && f.startsWith("[") && sender != null) {
            return new Route(Channel.GUILD, null, sender + ": " + text, own ? Style.OWN : Style.CHANNEL, own);
        }
        return new Route(Channel.LOCAL, null, f + ": " + text, own ? Style.OWN : Style.SAY, own);
    }

    private static String senderOf(String from) {
        if (from.startsWith("[")) {
            int end = from.indexOf("] ");
            return end < 0 ? null : from.substring(end + 2);
        }
        return from;
    }

    /** Wpis dziennika sesji: CHAT do Lokalnego (NPC, wlasne slowa) albo Gildii, reszta do Serwera. */
    public static Route route(EventLog.Entry e, String self) {
        String t = e.text();
        switch (e.kind()) {
            case CHAT:
                if (t.startsWith("[Gildia] ")) return new Route(Channel.GUILD, null, t.substring(9), Style.SYSTEM, false);
                boolean own = self != null && !self.isEmpty() && t.startsWith(self + ":");
                return new Route(Channel.LOCAL, null, t, own ? Style.OWN : Style.NPC, own);
            case WARNING: return new Route(Channel.SERVER, null, t, Style.WARNING, false);
            case LOOT: return new Route(Channel.SERVER, null, t, Style.LOOT, false);
            case SYSTEM: return new Route(Channel.SERVER, null, t, Style.SYSTEM, false);
            default: return new Route(Channel.SERVER, null, t, Style.INFO, false);
        }
    }

    /** Przenosi nowe wpisy dziennika (od ostatniego wywolania) do zakladek. */
    public void sync(EventLog log, long timeMs) {
        long total = log.total();
        int n = (int) Math.min(total - seenLog, log.size());
        seenLog = total;
        if (n <= 0) return;
        for (EventLog.Entry e : log.latest(n)) add(route(e, self), e.timeMs());
    }

    /**
     * Komunikat protokolu dla tekstu wpisanego w wybranej zakladce albo null, gdy tekst ma isc zwykla droga
     * (polecenie "/...", zakladka Lokalny lub Serwer: mowa obok, slowa do NPC, inkantacje).
     */
    public String[] outgoing(String text) {
        if (text == null || text.isBlank()) return null;
        String t = text.trim();
        if (t.startsWith("/")) return null;
        Tab tab = active();
        switch (tab.channel) {
            case TRADE: return new String[] {"TCHAT", t};
            case GUILD: return new String[] {"GCHAT", t};
            case PARTY: return new String[] {"PCHAT", t};
            case PRIVATE: return new String[] {"PM", tab.peer, t};
            default: return null;
        }
    }

    /** Czy zakladka mowy (Lokalny/Serwer): tam trafiaja slowa do NPC i inkantacje. */
    public boolean localActive() {
        Channel c = active().channel;
        return c == Channel.LOCAL || c == Channel.SERVER;
    }
}
