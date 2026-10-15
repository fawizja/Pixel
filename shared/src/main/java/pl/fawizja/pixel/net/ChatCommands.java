package pl.fawizja.pixel.net;

/**
 * Polecenia wpisywane w okno czatu, zamieniane na komunikaty protokolu (zamiast wysylania tekstu innym):
 * "/haslo stare nowe" -> PASSWD, "/usun-konto haslo" -> DELETE. Zwykly tekst -> CHAT.
 * Druzyna: "/zapros Imie" i "/przyjmij Imie" -> PARTY INVITE|ACCEPT imie, "/opusc" -> PARTY LEAVE,
 * "/d tekst" -> PCHAT (czat druzyny). Handel: "/handel Imie" -> TRADE REQUEST, "/oferuj 5 health potion"
 * albo "/oferuj 1000 gp" -> TRADE OFFER, "/akceptuj" -> TRADE ACCEPT, "/anuluj" -> TRADE CANCEL.
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
        switch (w[0].toLowerCase(java.util.Locale.ROOT)) {
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
            case "/d":
                return w.length >= 2 ? new String[] {"PCHAT", rest(t)} : null;
            default:
                return null;
        }
    }

    /** Tekst po pierwszym slowie (imiona moga miec spacje). */
    private static String rest(String t) {
        int i = t.indexOf(' ');
        return i < 0 ? "" : t.substring(i + 1).trim();
    }

    /** Podpowiedz dla gracza, gdy polecenie jest nieznane lub niepelne. */
    public static final String HELP = "Polecenia: /handel Imie, /oferuj 5 health potion | 1000 gp, /akceptuj, /anuluj, /zapros Imie, /przyjmij Imie, /opusc, /d tekst (druzyna), /haslo stare nowe, /usun-konto haslo";
}
