package pl.fawizja.pixel.house;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stan domu do okna przy drzwiach (jak w Tibii): wlasciciel, dostep ogladajacego, czynsz i goscie (goscie tylko
 * dla wlasciciela). Nazwe, miasto, wielkosc i cene klient bierze z houses.csv. Online serwer wysyla go jako
 * {"HOUSEINFO", id, wlasciciel|-, dostep, termin_za_ms, zalegle, goscie}.
 *
 * @param dueInMs ile ms do nastepnej platnosci czynszu (-1, gdy dom jest wolny albo okres jeszcze nie ruszyl)
 */
public record HouseView(String houseId, String owner, HouseAccess access, long dueInMs, int missed,
                        Map<String, HouseAccess> guests) {

    public HouseView {
        guests = guests == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(guests));
        if (access == null) access = HouseAccess.NONE;
    }

    public boolean free() { return owner == null; }

    public String[] toMessage() {
        return new String[] {"HOUSEINFO", houseId, owner == null ? "-" : owner, access.name(), String.valueOf(dueInMs),
                String.valueOf(missed), HouseOwnersMap.encodeGuests(guests)};
    }

    /** Odwrotnosc {@link #toMessage()}; null przy zlym komunikacie. */
    public static HouseView parse(String[] m) {
        if (m == null || m.length < 6 || !m[0].equals("HOUSEINFO")) return null;
        try {
            return new HouseView(m[1], m[2].equals("-") ? null : m[2], HouseAccess.parse(m[3]), Long.parseLong(m[4]),
                    Integer.parseInt(m[5]), HouseOwnersMap.decodeGuests(m.length > 6 ? m[6] : ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
