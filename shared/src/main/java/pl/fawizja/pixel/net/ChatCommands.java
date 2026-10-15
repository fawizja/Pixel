package pl.fawizja.pixel.net;

/**
 * Polecenia wpisywane w okno czatu, zamieniane na komunikaty protokolu (zamiast wysylania tekstu innym):
 * "/haslo stare nowe" -> PASSWD, "/usun-konto haslo" -> DELETE. Zwykly tekst -> CHAT.
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
            default:
                return null;
        }
    }

    /** Podpowiedz dla gracza, gdy polecenie jest nieznane lub niepelne. */
    public static final String HELP = "Polecenia: /haslo stare nowe, /usun-konto haslo";
}
