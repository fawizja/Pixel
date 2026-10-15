package pl.fawizja.pixel.trade;

/**
 * Stan wymiany widziany przez klienta (komunikat serwera "TS partner mojaOferta jegoOferta 0|1 0|1",
 * "TS -" = brak wymiany). Oferty sa juz opisane tekstem przez serwer.
 */
public record TradeView(String partner, String mine, String theirs, boolean mineAccepted, boolean theirsAccepted) {

    /** Pola komunikatu do wyslania (bez nazwy "TS"). */
    public String[] encode() {
        return new String[] {partner, mine, theirs, mineAccepted ? "1" : "0", theirsAccepted ? "1" : "0"};
    }

    /** Odczyt komunikatu; null dla "TS -" albo blednej linii. */
    public static TradeView parse(String[] m) {
        if (m == null || m.length < 6 || !"TS".equals(m[0])) return null;
        return new TradeView(m[1], m[2], m[3], "1".equals(m[4]), "1".equals(m[5]));
    }
}
