package pl.fawizja.pixel.client.ui;

import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import pl.fawizja.pixel.achievement.BestiaryCard;
import pl.fawizja.pixel.achievement.BestiaryClasses;
import pl.fawizja.pixel.achievement.BestiaryView;
import pl.fawizja.pixel.charm.Charm;
import pl.fawizja.pixel.charm.CharmView;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.monster.MonsterType;

/**
 * Okna bestiariusza i charmow (komputer i telefon - wspolne okno informacyjne HUD): lista potworow klasy, karta
 * potwora wg etapu, lista charmow, karta charmu, potwierdzenie resetu. Akcje zmieniajace stan (charm:buy, assign,
 * remove, reset) ida przez {@code act} - lokalnie od razu, online do serwera; otwarte okno odswieza sie co pol
 * sekundy, wiec wynik z serwera (SELF) pojawia sie sam.
 */
public final class BestiaryWindows {
    private static final long REFRESH_MS = 500;

    private final Hud hud;
    private final GameSession session;
    private final Supplier<Collection<MonsterType>> types;
    private final BiConsumer<String, Long> act;
    /** Otwarte okno (identyfikator nawigacji) albo null. */
    private String view;
    private String viewTitle;
    private long refreshMs;

    public BestiaryWindows(Hud hud, GameSession session, Supplier<Collection<MonsterType>> types, BiConsumer<String, Long> act) {
        this.hud = hud;
        this.session = session;
        this.types = types;
        this.act = act;
    }

    /** Obsluga wiersza okna; false, gdy identyfikator nie nalezy do bestiariusza ani charmow. */
    public boolean handle(String id, long t) {
        if (id.startsWith(BestiaryView.CLASS_ACTION) || id.equals("charm:open") || id.startsWith("charm:open:")
                || id.startsWith("charm:sel:") || id.equals("charm:resetask")) {
            show(id, true);
            return true;
        }
        if (id.startsWith("charm:")) {
            act.accept(id, t);
            // po zdjeciu/zalozeniu/kupnie zostajemy w karcie charmu, po resecie wracamy do listy
            String[] f = id.split(":");
            show(f.length > 2 && !f[1].equals("reset") ? "charm:sel:" + f[2] : "charm:open", true);
            return true;
        }
        if (id.startsWith("best:")) {
            openCard(id.substring("best:".length()));
            return true;
        }
        return false;
    }

    /** Karta potwora (dane z lokalnego rejestru, dziala tez online). */
    public void openCard(String key) {
        MonsterType mt = find(key);
        if (mt == null) return;
        int kills = session.character().bestiary().kills(key);
        String where = BestiaryCard.habitats(key, session.monsters().spawns(), session.world());
        List<String[]> card = BestiaryCard.rows(mt, kills, session.items(), where, BestiaryView.charmsOn(session.character(), key));
        view = "best:" + key;
        viewTitle = mt.name();
        hud.openInfo(viewTitle, card);
    }

    /** Odswiezanie otwartego okna charmow lub klasy (stan z serwera, nowe zabicia). */
    public void update(long t) {
        if (view == null || t - refreshMs < REFRESH_MS) return;
        refreshMs = t;
        if (viewTitle == null || !hud.infoOpen(viewTitle)) {
            view = null;
            return;
        }
        if (view.startsWith("best:")) return; // karta potwora sie nie odswieza (zmienia sie rzadko)
        show(view, false);
    }

    private void show(String id, boolean open) {
        String title;
        List<String[]> rows;
        int cap = hud.infoCapacity();
        if (id.startsWith(BestiaryView.CLASS_ACTION)) {
            String[] f = id.substring(BestiaryView.CLASS_ACTION.length()).split(":");
            String cls = f[0];
            title = BestiaryView.TITLE + ": " + (BestiaryView.BOSSES.equals(cls) ? "Bossowie" : BestiaryClasses.label(cls));
            List<String[]> all = BestiaryView.speciesRows(session.character(), types.get(), cls);
            rows = new java.util.ArrayList<>(BestiaryView.page(all, f.length > 1 ? parse(f[1]) : 0, cap - 1, BestiaryView.CLASS_ACTION + cls + ":"));
            rows.add(new String[] {"Wróć", "klasy bestiariusza", "", "ach:bestiary"});
        } else if (id.startsWith("charm:sel:")) {
            String[] f = id.substring("charm:sel:".length()).split(":");
            Charm c = Charm.parse(f[0]);
            if (c == null) return;
            title = CharmView.detailTitle(c);
            rows = CharmView.detail(session.character(), c, types.get(), f.length > 1 ? parse(f[1]) : 0, cap);
        } else if (id.equals("charm:resetask")) {
            title = CharmView.TITLE + ": reset";
            rows = CharmView.resetConfirm(session.character());
        } else {
            title = CharmView.TITLE;
            int page = id.startsWith("charm:open:") ? parse(id.substring("charm:open:".length())) : 0;
            rows = CharmView.list(session.character(), page, cap, session::monsterName);
        }
        view = id;
        viewTitle = title;
        if (open) hud.openInfo(title, rows);
        else hud.updateInfo(title, rows);
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private MonsterType find(String key) {
        for (MonsterType t : types.get()) if (t.key().equals(key)) return t;
        return null;
    }
}
