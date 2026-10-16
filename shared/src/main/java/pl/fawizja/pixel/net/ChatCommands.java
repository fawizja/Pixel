package pl.fawizja.pixel.net;

/**
 * Polecenia wpisywane w okno czatu, zamieniane na komunikaty protokolu (zamiast wysylania tekstu innym):
 * "/haslo stare nowe" -> PASSWD, "/usun-konto haslo" -> DELETE. Zwykly tekst -> CHAT.
 * Druzyna: "/zapros Imie" i "/przyjmij Imie" -> PARTY INVITE|ACCEPT imie, "/opusc" -> PARTY LEAVE,
 * "/d tekst" -> PCHAT (czat druzyny). Handel: "/handel Imie" -> TRADE REQUEST, "/oferuj 5 mikstura zdrowia"
 * albo "/oferuj 1000 gp" -> TRADE OFFER, "/akceptuj" -> TRADE ACCEPT, "/anuluj" -> TRADE CANCEL.
 * Gildie: "/gildia" -> GUILD INFO, "/gildia zaloz|zapros|przyjmij|wyrzuc X" -> GUILD CREATE|INVITE|ACCEPT|KICK X,
 * "/gildia opusc" -> GUILD LEAVE, "/g tekst" -> GCHAT. Prywatnie: "/w Imie, tekst" -> PM imie tekst.
 * Administrator: "/ogloszenie tekst", "/kick Imie", "/ban Imie [godziny]", "/unban Imie", "/wycisz Imie [minuty]",
 * "/kto", "/inwazja [miasto]" -> ADMIN SAY|KICK|BAN|UNBAN|MUTE|WHO|INVASION. Ranking: "/ranking [profesja|gildie]" -> RANK [profesja].
 * Znajomi: "/znajomi" -> FRIEND LIST, "/znajomi dodaj|usun Imie" -> FRIEND ADD|REMOVE imie.
 * Rynek: "/rynek [szukaj]" -> MARKET LIST [tekst], "/rynek wystaw 5 wilcze futro 300" -> MARKET SELL,
 * "/rynek kup|wycofaj 12" -> MARKET BUY|CANCEL 12. Poczta: "/wyslij Imie, 5 wilcze futro" -> MAIL SEND imie tekst.
 * Polecenia mozna pisac z polskimi znakami albo bez ("/zaproś" = "/zapros").
 * Hasla nigdy nie trafiaja na czat - bledne polecenie zwraca null (nic nie jest wysylane).
 */
public final class ChatCommands {

    private ChatCommands() {}

    /** Pola komunikatu do wyslania albo null, gdy nie ma czego wyslac. */
    public static String[] toMessage(String text) {
        if (text == null || text.isBlank()) return null;
        String t = text.trim();
        if (!t.startsWith("/")) return new String[] {"CHAT", t};
        String[] w = t.split("\\s+");
        switch (key(w[0])) {
            case "/haslo":
                return w.length == 3 ? new String[] {"PASSWD", w[1], w[2]} : null;
            case "/usun-konto":
                return w.length == 2 ? new String[] {"DELETE", w[1]} : null;
            case "/zapros":
                return w.length >= 2 ? new String[] {"PARTY", "INVITE", rest(t)} : null;
            case "/przyjmij":
                return w.length >= 2 ? new String[] {"PARTY", "ACCEPT", rest(t)} : null;
            case "/opusc":
                return w.length == 1 ? new String[] {"PARTY", "LEAVE"} : null;
            case "/handel":
                return w.length >= 2 ? new String[] {"TRADE", "REQUEST", rest(t)} : null;
            case "/oferuj":
                return w.length >= 2 ? new String[] {"TRADE", "OFFER", rest(t)} : null;
            case "/akceptuj":
                return w.length == 1 ? new String[] {"TRADE", "ACCEPT"} : null;
            case "/anuluj":
                return w.length == 1 ? new String[] {"TRADE", "CANCEL"} : null;
            case "/gildia":
                return guild(t, w);
            case "/g":
                return w.length >= 2 ? new String[] {"GCHAT", rest(t)} : null;
            case "/w": {
                // imie moze miec spacje: "/w Stary Wilk, czesc"; bez przecinka imie = pierwsze slowo
                String r = rest(t);
                int comma = r.indexOf(',');
                String name = comma >= 0 ? r.substring(0, comma).trim() : (w.length >= 3 ? w[1] : "");
                String msg = comma >= 0 ? r.substring(comma + 1).trim() : (w.length >= 3 ? rest(r) : "");
                return name.isEmpty() || msg.isEmpty() ? null : new String[] {"PM", name, msg};
            }
            case "/ogloszenie":
                return w.length >= 2 ? new String[] {"ADMIN", "SAY", rest(t)} : null;
            case "/kick":
                return w.length >= 2 ? new String[] {"ADMIN", "KICK", rest(t)} : null;
            case "/unban":
                return w.length >= 2 ? new String[] {"ADMIN", "UNBAN", rest(t)} : null;
            case "/ban":
                return w.length >= 2 ? prefixed("ADMIN", "BAN", nameAndNumber(rest(t))) : null;
            case "/wycisz":
                return w.length >= 2 ? prefixed("ADMIN", "MUTE", nameAndNumber(rest(t))) : null;
            case "/ranking":
                return w.length == 1 ? new String[] {"RANK"} : new String[] {"RANK", rest(t)};
            case "/bossowie":
                return new String[] {"BOSS"};
            case "/pojedynek":
                if (w.length < 2) return new String[] {"DUEL", "HELP"};
                if (key(w[1]).equals("poddaj")) return new String[] {"DUEL", "SURRENDER"};
                return new String[] {"DUEL", "REQUEST", rest(t)};
            case "/znajomi":
                return friends(t, w);
            case "/rynek":
                return market(t, w);
            case "/wyslij": {
                // "/wyślij Stary Wilk, 5 wilcze futro"
                String r = rest(t);
                int comma = r.indexOf(',');
                if (comma <= 0 || r.substring(comma + 1).isBlank()) return null;
                return new String[] {"MAIL", "SEND", r.substring(0, comma).trim(), r.substring(comma + 1).trim()};
            }
            case "/przelew": {
                // "/przelew Stary Wilk, 5000" (takze 5 000, 5k, 5000 gp)
                String r = rest(t);
                int comma = r.indexOf(',');
                if (comma <= 0) return null;
                long amount = amount(r.substring(comma + 1));
                if (amount <= 0) return null;
                return new String[] {"TRANSFER", r.substring(0, comma).trim(), String.valueOf(amount)};
            }
            case "/inwazja":
                return w.length == 1 ? new String[] {"ADMIN", "INVASION"} : new String[] {"ADMIN", "INVASION", rest(t)};
            case "/kto":
                return w.length == 1 ? new String[] {"ADMIN", "WHO"} : null;
            case "/d":
                return w.length >= 2 ? new String[] {"PCHAT", rest(t)} : null;
            default:
                return null;
        }
    }

    private static String[] market(String t, String[] w) {
        if (w.length == 1) return new String[] {"MARKET", "LIST"};
        String arg = rest(rest(t));
        switch (key(w[1])) {
            case "wystaw": return arg.isEmpty() ? null : new String[] {"MARKET", "SELL", arg};
            case "kup": return arg.matches("#?\\d+") ? new String[] {"MARKET", "BUY", arg.replace("#", "")} : null;
            case "wycofaj": return arg.matches("#?\\d+") ? new String[] {"MARKET", "CANCEL", arg.replace("#", "")} : null;
            default: return new String[] {"MARKET", "LIST", rest(t)};
        }
    }

    private static String[] friends(String t, String[] w) {
        if (w.length == 1) return new String[] {"FRIEND", "LIST"};
        String arg = rest(rest(t));
        if (arg.isEmpty()) return null;
        switch (key(w[1])) {
            case "dodaj": return new String[] {"FRIEND", "ADD", arg};
            case "usun": return new String[] {"FRIEND", "REMOVE", arg};
            default: return null;
        }
    }

    private static String[] guild(String t, String[] w) {
        if (w.length == 1) return new String[] {"GUILD", "INFO"};
        String arg = rest(rest(t));
        switch (key(w[1])) {
            case "zaloz": return arg.isEmpty() ? null : new String[] {"GUILD", "CREATE", arg};
            case "zapros": return arg.isEmpty() ? null : new String[] {"GUILD", "INVITE", arg};
            case "przyjmij": return arg.isEmpty() ? null : new String[] {"GUILD", "ACCEPT", arg};
            case "wyrzuc": return arg.isEmpty() ? null : new String[] {"GUILD", "KICK", arg};
            case "opusc": return w.length == 2 ? new String[] {"GUILD", "LEAVE"} : null;
            case "wiadomosc": return new String[] {"GUILD", "MOTD", arg};
            default: return null;
        }
    }

    /** Nazwa polecenia bez wielkosci liter i polskich znakow: "/Zaproś" i "/zapros" to to samo. */
    static String key(String word) {
        String t = word.toLowerCase(java.util.Locale.ROOT).replace('ł', 'l');
        return java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /** "Stary Wilk 24" -> [Stary Wilk, 24]; "Ola" -> [Ola] (liczba na koncu = czas). */
    public static String[] nameAndNumber(String rest) {
        String r = rest.trim();
        int sp = r.lastIndexOf(' ');
        if (sp > 0 && r.substring(sp + 1).matches("\\d+")) return new String[] {r.substring(0, sp).trim(), r.substring(sp + 1)};
        return new String[] {r};
    }

    private static String[] prefixed(String a, String b, String[] tail) {
        String[] out = new String[tail.length + 2];
        out[0] = a;
        out[1] = b;
        System.arraycopy(tail, 0, out, 2, tail.length);
        return out;
    }

    /** Tekst po pierwszym slowie (imiona moga miec spacje). */
    /** Kwota przelewu: "5000", "5 000", "5.000", "5k", "5000 gp"; 0, gdy niepoprawna. */
    public static long amount(String text) {
        String a = text.trim().toLowerCase(java.util.Locale.ROOT).replace("gp", "").replace(" ", "").replace(".", "");
        long mult = 1;
        if (a.endsWith("kk")) {
            mult = 1_000_000;
            a = a.substring(0, a.length() - 2);
        } else if (a.endsWith("k")) {
            mult = 1_000;
            a = a.substring(0, a.length() - 1);
        }
        if (!a.matches("\\d{1,10}")) return 0;
        return Long.parseLong(a) * mult;
    }

    private static String rest(String t) {
        int i = t.indexOf(' ');
        return i < 0 ? "" : t.substring(i + 1).trim();
    }

    /** Podpowiedz dla gracza, gdy polecenie jest nieznane lub niepelne. */
    /**
     * Poradnik polecen do okna /pomoc: {polecenie, opis[, "", akcja]}; polecenia bez argumentow
     * maja akcje "cmd:..." (dotkniecie wykonuje je jak wpisane w czacie).
     */
    public static java.util.List<String[]> helpRows() {
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        String[][] runnable = {
            {"/gracze", "kto jest teraz w grze"},
            {"/bossowie", "którzy bossowie żyją i kiedy wrócą"},
            {"/analiza", "doświadczenie, zabicia i złoto na godzinę"},
            {"/wydarzenia", "inwazja, potwór dnia, zadanie dnia, nagrody"},
            {"/śmierć", "kto i czym zadał ostatnie obrażenia"},
            {"/statystyki", "czas gry, zabicia, śmierci (dziennik zadań: /zadania)"},
            {"/osiągnięcia", "osiągnięcia, tytuły i bestiariusz (/bestiariusz)"},
            {"/ranking", "najlepsi gracze (także /ranking gildie)"},
            {"/znajomi", "lista znajomych"},
            {"/rynek", "oferty rynku (u bankiera)"},
            {"/znaczniki", "własne miejsca na mapie (/znacznik nazwa zapisuje)"},
        };
        for (String[] r : runnable) rows.add(new String[] {r[0], r[1] + " (dotknij)", "", "cmd:" + r[0]});
        String[][] typed = {
            {"/w Imię, tekst", "wiadomość prywatna"},
            {"/zaproś Imię", "zaproszenie do drużyny (/przyjmij, /opuść, /d tekst)"},
            {"/handel Imię", "handel (/oferuj, /akceptuj, /anuluj)"},
            {"/pojedynek Imię", "pojedynek do pierwszej krwi"},
            {"/gildia załóż Nazwa", "gildie (zaproś, przyjmij, wyrzuć, opuść, wiadomość; /g tekst)"},
            {"/wyślij Imię, 5 futro", "poczta do depozytu; złoto: /przelew Imię, 5000 (u bankiera)"},
            {"/hasło stare nowe", "zmiana hasła konta"},
        };
        for (String[] r : typed) rows.add(new String[] {r[0], r[1]});
        return rows;
    }

    public static final String HELP = "Polecenia: /w Imię, tekst (prywatnie), /znajomi [dodaj|usuń Imię], /pojedynek Imię | poddaj, /gracze, /wydarzenia, /znacznik nazwa, /znaczniki, /bossowie, /analiza, /potwór, /śmierć, /rynek [nazwa | wystaw 5 wilcze futro 300 | kup nr | wycofaj nr], /wyślij Imię, 5 wilcze futro (u bankiera), /przelew Imię, 5000 (u bankiera), /osiągnięcia, /bestiariusz, /ranking [profesja], /gildia [załóż|zaproś|przyjmij|wyrzuć|opuść|wiadomość tekst], /g tekst, /handel Imię, /oferuj 5 mikstura zdrowia | 1000 gp, /akceptuj, /anuluj, /zaproś Imię, /przyjmij Imię, /opuść, /d tekst (drużyna), /hasło stare nowe, /usuń-konto hasło";
}
