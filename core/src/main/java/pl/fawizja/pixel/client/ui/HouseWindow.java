package pl.fawizja.pixel.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import pl.fawizja.pixel.house.House;
import pl.fawizja.pixel.house.HouseAccess;
import pl.fawizja.pixel.house.HouseRent;
import pl.fawizja.pixel.house.HouseView;

/**
 * Okno domu przy drzwiach, jak w Tibii (Darek 2026-10-28): obcy widzi, czy dom jest wolny, cene, czynsz tygodniowy
 * i przycisk Kup; wlasciciel stojacy w srodku zaprasza gosci (pelny dostep albo samo wejscie), usuwa ich
 * i opuszcza dom (z potwierdzeniem). Tu tylko tresc i przyciski; rysuje {@link Hud}.
 * Akcje na zewnatrz: house:buy:id, house:invite:full|entry:Imie, house:unguest:Imie, house:door, house:leave.
 */
final class HouseWindow {

    enum Mode { INFO, OWNER, INVITE, CONFIRM_LEAVE }

    /** Przycisk okna; x, y, w, h ustawia Hud przy ukladaniu. */
    static final class Button {
        final String id;
        final String label;
        final boolean enabled;
        /** Maly przycisk w wierszu listy gosci (po prawej). */
        final boolean inline;
        float x, y, w, h;

        Button(String id, String label, boolean enabled, boolean inline) {
            this.id = id;
            this.label = label;
            this.enabled = enabled;
            this.inline = inline;
        }
    }

    final House house;
    HouseView view;
    Mode mode;
    HouseAccess inviteLevel = HouseAccess.FULL;
    final StringBuilder name = new StringBuilder();
    /** Saldo konta ogladajacego i czy ma juz dom (do przycisku Kup). */
    long bank;
    boolean hasHouse;
    boolean doorOpen;

    HouseWindow(House house, HouseView view, boolean ownerWindow) {
        this.house = house;
        this.view = view;
        this.mode = ownerWindow ? Mode.OWNER : Mode.INFO;
    }

    String title() {
        switch (mode) {
            case OWNER: return "Twój dom: " + house.name();
            case INVITE: return "Zaproś do domu " + house.name();
            case CONFIRM_LEAVE: return "Opuścić dom?";
            default: return "Dom: " + house.name() + " (" + house.town() + ")";
        }
    }

    static String gp(long v) { return String.format("%,d gp", v); }

    /** Wiersze tekstu nad przyciskami. */
    List<String> lines() {
        List<String> out = new ArrayList<>();
        long rent = HouseRent.rent(house);
        switch (mode) {
            case INFO:
                out.add("Miasto: " + house.town() + "   Wielkość: " + house.size() + " pól");
                if (view.free()) {
                    out.add("Stan: wolny - można kupić");
                    out.add("Cena: " + gp(house.price()) + " (z konta w banku)");
                } else {
                    out.add("Stan: zajęty");
                    out.add("Właściciel: " + view.owner());
                }
                out.add("Czynsz tygodniowy: " + gp(rent) + " (pobierany z konta)");
                if (view.free()) {
                    out.add("Na koncie: " + gp(bank) + (bank < house.price() ? " - za mało złota" : ""));
                    if (hasHouse) out.add("Masz już dom - najpierw go opuść.");
                } else if (view.access() == HouseAccess.OWNER) {
                    out.add("To twój dom. Zaprosisz gości od środka, przy drzwiach.");
                } else if (view.access() == HouseAccess.ENTRY || view.access() == HouseAccess.FULL) {
                    out.add("Twój dostęp: " + view.access().label());
                }
                break;
            case OWNER:
                out.add(house.town() + ", " + house.size() + " pól, czynsz " + gp(rent) + " tygodniowo");
                out.add(view.dueInMs() >= 0 ? "Następna płatność za " + HouseRent.describeIn(view.dueInMs()) + " (z konta)"
                        : "Czynsz pobierany co tydzień z konta");
                if (view.missed() > 0) {
                    out.add("Zaległe płatności: " + view.missed() + " z " + HouseRent.MAX_MISSED + " - wpłać złoto do banku!");
                }
                out.add("Goście (" + view.guests().size() + "/" + pl.fawizja.pixel.house.HouseOwners.MAX_GUESTS + ")"
                        + (view.guests().isEmpty() ? ": nikt" : ":"));
                break;
            case INVITE:
                out.add(inviteLevel == HouseAccess.FULL
                        ? "Pełny dostęp: wejście, skrzynie, drzwi, przedmioty."
                        : "Tylko wejście: bez skrzyń, drzwi i przedmiotów.");
                out.add("Imię postaci:");
                break;
            default:
                out.add("Miasto odkupi dom " + house.name() + " za " + gp(house.price() * pl.fawizja.pixel.game.GameSession.HOUSE_SELLBACK_PERCENT / 100) + ".");
                out.add("Rzeczy ze skrzyń w domu trafią do depozytu.");
                out.add("Goście stracą wstęp.");
        }
        return out;
    }

    /** Wiersze listy gosci (tylko okno wlasciciela): {imie, opis poziomu}. */
    List<String[]> guestRows() {
        List<String[]> out = new ArrayList<>();
        if (mode != Mode.OWNER) return out;
        for (Map.Entry<String, HouseAccess> e : view.guests().entrySet()) out.add(new String[] {e.getKey(), e.getValue().label()});
        return out;
    }

    /** Przyciski na dole (i "Usuń" przy gosciach). */
    List<Button> buttons() {
        List<Button> out = new ArrayList<>();
        switch (mode) {
            case INFO:
                if (view.free()) out.add(new Button("house:buy:" + house.id(), "Kup", !hasHouse && bank >= house.price(), false));
                out.add(new Button("house:close", "Zamknij", true, false));
                break;
            case OWNER:
                for (String[] g : guestRows()) out.add(new Button("house:unguest:" + g[0], "Usuń", true, true));
                out.add(new Button("house:invitefull", "Zaproś (pełny dostęp)", view.guests().size() < pl.fawizja.pixel.house.HouseOwners.MAX_GUESTS, false));
                out.add(new Button("house:inviteentry", "Zaproś (wejście)", view.guests().size() < pl.fawizja.pixel.house.HouseOwners.MAX_GUESTS, false));
                out.add(new Button("house:door", doorOpen ? "Zamknij drzwi" : "Otwórz drzwi", true, false));
                out.add(new Button("house:leaveask", "Opuść dom", true, false));
                out.add(new Button("house:close", "Zamknij", true, false));
                break;
            case INVITE:
                out.add(new Button("house:invitesend", "Zaproś", name.toString().trim().length() >= 3, false));
                out.add(new Button("house:back", "Anuluj", true, false));
                break;
            default:
                out.add(new Button("house:leave", "Tak, opuść dom", true, false));
                out.add(new Button("house:back", "Nie", true, false));
        }
        return out;
    }

    /**
     * Dotkniecie przycisku: zmiany widoku obsluguje okno samo (null), akcje gry wraca do wolajacego.
     * "house:close" zamyka okno.
     */
    String press(String id) {
        switch (id) {
            case "house:invitefull":
            case "house:inviteentry":
                inviteLevel = id.endsWith("full") ? HouseAccess.FULL : HouseAccess.ENTRY;
                name.setLength(0);
                mode = Mode.INVITE;
                return null;
            case "house:leaveask":
                mode = Mode.CONFIRM_LEAVE;
                return null;
            case "house:back":
                mode = Mode.OWNER;
                return null;
            case "house:invitesend": {
                String who = name.toString().trim();
                if (who.length() < 3) return null;
                mode = Mode.OWNER;
                name.setLength(0);
                return "house:invite:" + inviteLevel.code() + ":" + who;
            }
            default:
                return id;
        }
    }

    /** Wpisywanie imienia (desktop: klawiatura). */
    void type(char c) {
        if (mode != Mode.INVITE) return;
        if (c == '\b') {
            if (name.length() > 0) name.setLength(name.length() - 1);
        } else if ((Character.isLetter(c) || c == ' ' || c == '\'') && name.length() < 20) {
            name.append(c);
        }
    }
}
