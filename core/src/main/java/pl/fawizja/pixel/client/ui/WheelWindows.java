package pl.fawizja.pixel.client.ui;

import java.util.List;
import java.util.function.BiConsumer;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.wheel.WheelDomain;
import pl.fawizja.pixel.wheel.WheelSlice;
import pl.fawizja.pixel.wheel.WheelView;

/**
 * Okna Kola Przeznaczenia (komputer i telefon - wspolne okno informacyjne HUD): przeglad z 4 kaflami cwiartek,
 * cwiartka (9 pol), karta pola, premie, zestawy, potwierdzenie resetu. Akcje zmieniajace stan (wheel:add, remove,
 * reset, save, load) ida przez {@code act} - lokalnie od razu, online do serwera (wynik wraca w SELF); otwarte okno
 * odswieza sie co pol sekundy.
 */
public final class WheelWindows {
    private static final long REFRESH_MS = 500;

    private final Hud hud;
    private final GameSession session;
    private final BiConsumer<String, Long> act;
    private String view;
    private String viewTitle;
    private long refreshMs;

    public WheelWindows(Hud hud, GameSession session, BiConsumer<String, Long> act) {
        this.hud = hud;
        this.session = session;
        this.act = act;
    }

    /** Otwiera okno glowne kola. */
    public void open() { show(WheelView.OPEN, true); }

    /** Obsluga wiersza okna; false, gdy identyfikator nie nalezy do kola. */
    public boolean handle(String id, long t) {
        if (!id.startsWith("wheel:")) return false;
        String[] f = id.split(":");
        switch (f[1]) {
            case "open": case "q": case "sel": case "perks": case "presets": case "resetask":
                show(id, true);
                return true;
            case "add": case "remove":
                act.accept(id, t);
                show("wheel:sel:" + f[2], true);
                return true;
            case "save": case "load":
                act.accept(id, t);
                show("wheel:presets", true);
                return true;
            case "reset":
                act.accept(id, t);
                show(WheelView.OPEN, true);
                return true;
            default:
                return false;
        }
    }

    public void update(long t) {
        if (view == null || t - refreshMs < REFRESH_MS) return;
        refreshMs = t;
        if (viewTitle == null || !hud.infoOpen(viewTitle)) {
            view = null;
            return;
        }
        show(view, false);
    }

    private void show(String id, boolean open) {
        String[] f = id.split(":");
        boolean temple = session.inTemple();
        String title = WheelView.TITLE;
        List<String[]> rows;
        switch (f[1]) {
            case "q": {
                WheelDomain d = WheelDomain.ofCode(f[2].charAt(0));
                if (d == null) return;
                title = WheelView.quadrantTitle(d);
                rows = WheelView.quadrant(session.character(), d);
                break;
            }
            case "sel": {
                WheelSlice s = WheelSlice.ofCode(f[2]);
                if (s == null) return;
                title = WheelView.sliceTitle(s);
                rows = WheelView.slice(session.character(), s, temple, this::spellName);
                break;
            }
            case "perks": title += ": premie"; rows = WheelView.perks(session.character(), this::spellName); break;
            case "presets": title += ": zestawy"; rows = WheelView.presets(session.character(), temple); break;
            case "resetask": title += ": czyszczenie"; rows = WheelView.resetConfirm(session.character(), temple); break;
            default: rows = WheelView.overview(session.character(), temple);
        }
        view = id;
        viewTitle = title;
        if (open || !title.equals(currentTitle)) hud.openInfo(title, rows);
        else hud.updateInfo(title, rows);
        currentTitle = title;
    }

    private String currentTitle;

    private String spellName(String key) {
        pl.fawizja.pixel.magic.SpellType t = session.spells().registry().find(key);
        return t == null ? key : t.name();
    }
}
