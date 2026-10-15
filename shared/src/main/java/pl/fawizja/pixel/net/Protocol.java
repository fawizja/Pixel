package pl.fawizja.pixel.net;

import java.util.ArrayList;
import java.util.List;

/**
 * Protokol tekstowy klient-serwer: jedna wiadomosc = jedna linia, pola rozdzielone tabulatorem.
 * Znaki specjalne w polach (\, tab, nowa linia) sa escapowane. Serwer jest zrodlem prawdy:
 * klient wysyla tylko zamiary (ruch, akcje z przyciskow), serwer odsyla stan.
 *
 * <pre>
 * klient -> serwer                      serwer -> klient
 * HELLO  wersja imie PROFESJA           WELCOME idGracza tickMs
 * MOVE   KIERUNEK | -                   ERR     komunikat
 * ACT    akcja (jak w HUD, np. cast:x)  SELF    zapis postaci (CharacterCodec)
 * TARGET idPotwora                      FRAME   czasSerwera            (poczatek klatki stanu)
 * CHAT   tekst                          ME      x y z KIERUNEK         (wlasna pozycja)
 *                                       TG      idPotwora | -          (cel walki)
 *                                       PL      id imie PROFESJA x y z hp% czaszka gildia M|- (inny gracz w zasiegu, M = na koniu)
 * BYE                                   MO      id klucz x y z hp%     (potwor w zasiegu)
 *                                       END                            (koniec klatki)
 *                                       LOG     RODZAJ tekst
 *                                       CHAT    imie tekst
 * </pre>
 */
public final class Protocol {

    public static final int VERSION = 3;
    public static final int DEFAULT_PORT = 7171;
    /** Promien widocznosci innych graczy i potworow w klatce stanu. */
    public static final int VIEW_RADIUS = 12;
    public static final int MAX_LINE = 64 * 1024;

    private Protocol() {}

    public static String line(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append('\t');
            sb.append(escape(fields[i]));
        }
        return sb.toString();
    }

    public static String[] parse(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\t') {
                out.add(cur.toString());
                cur.setLength(0);
            } else if (c == '\\' && i + 1 < line.length()) {
                char n = line.charAt(++i);
                cur.append(n == 't' ? '\t' : n == 'n' ? '\n' : n == 'r' ? '\r' : n);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\t': sb.append("\\t"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
}
