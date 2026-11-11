package pl.fawizja.pixel.charm;

import pl.fawizja.pixel.i18n.Messages;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import pl.fawizja.pixel.achievement.BestiaryView;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.monster.MonsterType;

/**
 * Okna charmow (komputer i telefon): lista 25 charmow, karta charmu (kupno, ulepszenie, zalozenie na potwora,
 * zdjecie), potwierdzenie resetu. Wiersz = {etykieta, opis, stan, akcja[, "off"]} jak w Hud.openInfo.
 * Akcje "charm:buy|assign|remove|reset" wykonuje sesja gry (online serwer), reszta to nawigacja okien klienta:
 * "charm:open[:strona]", "charm:sel:NAZWA[:strona]", "charm:resetask".
 */
public final class CharmView {
    public static final String TITLE = "Charmy";

    private CharmView() {}

    public static String detailTitle(Charm c) { return TITLE + ": " + c.label() + " (" + c.tibiaName() + ")"; }

    private static String currency(Charm c) { return c.major() ? "pkt" : "ech"; }

    /** Lista charmow: Major, potem Minor; stopien, szansa i potwor. */
    public static List<String[]> list(PlayerCharacter pc, int page, int capacity, java.util.function.Function<String, String> names) {
        Charms ch = pc.charms();
        List<String[]> head = new ArrayList<>();
        String[] info = BestiaryView.charmsRow(pc);
        head.add(new String[] {info[0], info[1] + (pc.isPremium() ? "" : " (premium: " + Charms.PREMIUM_SLOTS + ")"), "", "ach:none"});
        List<String[]> rows = new ArrayList<>();
        for (Charm.Category cat : Charm.Category.values()) {
            for (Charm c : Charm.values()) {
                if (c.category() != cat) continue;
                int t = ch.tier(c);
                String detail;
                if (t == 0) {
                    detail = c.category().label() + Messages.get("charm_view.zablokowany") + String.format("%,d %s", c.cost(1), currency(c));
                } else {
                    String on = ch.assignedTo(c);
                    detail = c.category().label() + ", " + c.valueText(t) + (on != null ? Messages.get("charm_view.na") + names.apply(on) : Messages.get("charm_view.wolny"));
                }
                rows.add(new String[] {c.labelWithTier(t), detail, "", "charm:sel:" + c.name()});
            }
        }
        rows.add(new String[] {Messages.get("charm_view.reset_wszystkich_charmow"), String.format(Messages.get("charm_view.d_gp_punkty_wracaja"), CharmPrices.resetAll(pc.level())),
                "", "charm:resetask"});
        List<String[]> out = new ArrayList<>(head);
        out.addAll(BestiaryView.page(rows, page, capacity - head.size(), "charm:open:"));
        return out;
    }

    /** Karta charmu: opis, stopnie, kupno / ulepszenie, potwor albo lista potworow do wyboru. */
    public static List<String[]> detail(PlayerCharacter pc, Charm c, Collection<MonsterType> types, int page, int capacity) {
        Charms ch = pc.charms();
        int t = ch.tier(c);
        List<String[]> head = new ArrayList<>();
        head.add(new String[] {Messages.get("wheel_view.dzialanie"), c.effect(), "", "ach:none"});
        StringBuilder tiers = new StringBuilder();
        for (int i = 1; i <= Charm.MAX_TIER; i++) {
            if (i > 1) tiers.append("; ");
            tiers.append(Charm.tierName(i)).append(": ").append(c.valueText(i));
        }
        head.add(new String[] {t == 0 ? Messages.get("charm_view.zablokowany_2") : Messages.get("charm_view.stopien") + Charm.tierName(t), tiers.toString(), "", "ach:none"});
        if (t < Charm.MAX_TIER) {
            int cost = c.cost(t + 1);
            int have = c.major() ? ch.points() : ch.echoes();
            String gain = c.major() ? String.format(Messages.get("charm_view.d_ech"), Charms.echoesFor(t)) : "";
            head.add(new String[] {t == 0 ? Messages.get("hud.odblokuj") : Messages.get("charm_view.ulepsz_do") + Charm.tierName(t + 1),
                    String.format(Messages.get("charm_view.d_s_masz_d"), cost, currency(c), have, gain), "", "charm:buy:" + c.name(), have >= cost ? "" : "off"});
        }
        List<String[]> rows = new ArrayList<>();
        String on = ch.assignedTo(c);
        if (on != null) {
            MonsterType mt = find(types, on);
            head.add(new String[] {Messages.get("charm_view.zalozony_na"), mt == null ? on : mt.name(), "", "best:" + on});
            head.add(new String[] {Messages.get("charm_view.zdejmij"), String.format("%,d gp", CharmPrices.removal(pc.level())), "", "charm:remove:" + c.name()});
        } else if (t > 0) {
            boolean free = ch.used() < Charms.slots(pc.isPremium());
            for (MonsterType mt : types) {
                if (mt.boss()) continue;
                if (Charms.canTarget(c, mt.key(), pc.bestiary()) != null) continue;
                Charm other = ch.on(mt.key(), c.category());
                rows.add(new String[] {Messages.get("charm_view.zaloz_na") + mt.name(), other != null ? Messages.get("charm_view.juz_ma") + other.label()
                        : free ? String.format(Messages.get("bestiary_view.pokonano_d"), pc.bestiary().kills(mt.key())) : Messages.get("charm_view.brak_wolnych_slotow"),
                        "", "charm:assign:" + c.name() + ":" + mt.key(), other == null && free ? "" : "off"});
            }
            if (rows.isEmpty()) {
                head.add(new String[] {Messages.get("hunting_advisor.brak_potworow"), c.major() ? Messages.get("charm_view.major_ukoncz_wpis_bestiariusza") : Messages.get("charm_view.minor_pokonaj_potwora"),
                        "", "ach:none"});
            }
        }
        List<String[]> out = new ArrayList<>(head);
        int room = capacity - head.size() - 1;
        out.addAll(BestiaryView.page(rows, page, room, "charm:sel:" + c.name() + ":"));
        out.add(new String[] {Messages.get("bestiary_windows.wroc"), Messages.get("charm_view.lista_charmow"), "", "charm:open"});
        return out;
    }

    /** Potwierdzenie pelnego resetu. */
    public static List<String[]> resetConfirm(PlayerCharacter pc) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {Messages.get("charm_view.zresetowac_wszystkie_charmy"), String.format(Messages.get("charm_view.wraca_d_pkt_charmow"), pc.charms().maxPoints(),
                pc.isPromoted() ? Charms.PROMOTED_RESET_ECHOES : 0), "", "ach:none"});
        rows.add(new String[] {Messages.get("charm_view.tak_zresetuj"), String.format("%,d gp", CharmPrices.resetAll(pc.level())), "", "charm:reset"});
        rows.add(new String[] {Messages.get("house_window.nie"), Messages.get("charm_view.wroc_do_listy"), "", "charm:open"});
        return rows;
    }

    private static MonsterType find(Collection<MonsterType> types, String key) {
        for (MonsterType t : types) if (t.key().equals(key)) return t;
        return null;
    }
}
