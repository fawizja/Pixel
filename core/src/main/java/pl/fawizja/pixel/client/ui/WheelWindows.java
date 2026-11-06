package pl.fawizja.pixel.client.ui;

import java.util.List;
import java.util.function.BiConsumer;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.wheel.WheelDomain;
import pl.fawizja.pixel.wheel.WheelSlice;
import pl.fawizja.pixel.wheel.WheelView;

/**
 * Okna Kola Przeznaczenia (komputer i telefon - wspolne okno informacyjne HUD): przeglad z 4 kaflami cwiartek,
 * cwiartka (9 pol), karta pola, premie, zestawy, potwierdzenie resetu; klejnoty ({@link pl.fawizja.pixel.wheel.GemView}):
 * Pracownia, karta klejnotu, naczynie domeny i Warsztat okruchow. Akcje zmieniajace stan (wheel:add, remove,
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
            case "gems": case "card": case "gemask": case "vessel": case "frag": case "rev":
                show(id, true);
                return true;
            case "gem": {
                // klejnoty: akcja w sesji (online serwer), potem widok, z ktorego ja wywolano
                pl.fawizja.pixel.wheel.WheelGem g = f.length > 3 && f[3].matches("\\d+")
                        ? session.character().wheel().gems().gem(Integer.parseInt(f[3])) : null;
                act.accept(id, t);
                switch (f[2]) {
                    case "lock": case "switch": show("wheel:card:" + f[3], true); break;
                    case "socket": show(g == null ? "wheel:gems:0" : "wheel:vessel:" + vesselOf(g), true); break;
                    case "unsocket": show("wheel:vessel:" + f[3], true); break;
                    default: show("wheel:gems:0", true);
                }
                return true;
            }
            case "grade":
                act.accept(id, t);
                show(view != null && view.startsWith("wheel:frag:") ? view : "wheel:frag:" + f[2] + ":0", true);
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
                rows = WheelView.quadrant(session.character(), d, this::spellName);
                break;
            }
            case "rev": {
                WheelDomain d = f.length > 2 ? WheelDomain.ofCode(f[2].charAt(0)) : null;
                if (d == null) return;
                title = WheelView.revelationTitle(d);
                rows = WheelView.revelation(session.character(), d, this::spellName);
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
            case "gems":
                title = pl.fawizja.pixel.wheel.GemView.atelierTitle();
                rows = pl.fawizja.pixel.wheel.GemView.atelier(session.character(), num(f, 2), this::have, this::spellName);
                break;
            case "card":
                title = pl.fawizja.pixel.wheel.GemView.atelierTitle();
                rows = pl.fawizja.pixel.wheel.GemView.card(session.character(), num(f, 2), temple, this::spellName);
                break;
            case "gemask":
                title = pl.fawizja.pixel.wheel.GemView.atelierTitle() + " - rozbicie";
                rows = pl.fawizja.pixel.wheel.GemView.destroyConfirm(session.character(), num(f, 2));
                break;
            case "vessel": {
                WheelDomain d = f.length > 2 ? WheelDomain.ofCode(f[2].charAt(0)) : null;
                if (d == null) return;
                title = pl.fawizja.pixel.wheel.GemView.vesselTitle(d);
                rows = pl.fawizja.pixel.wheel.GemView.vessel(session.character(), d, temple, this::spellName);
                break;
            }
            case "frag":
                title = pl.fawizja.pixel.wheel.GemView.fragTitle();
                rows = pl.fawizja.pixel.wheel.GemView.fragments(session.character(), f.length > 2 && "s".equals(f[2]), num(f, 3), this::have,
                        this::spellName);
                break;
            case "presets": title += ": zestawy"; rows = WheelView.presets(session.character(), temple); break;
            case "resetask": title += ": czyszczenie"; rows = WheelView.resetConfirm(session.character(), temple); break;
            default: rows = WheelView.overview(session.character(), temple, this::have);
        }
        view = id;
        viewTitle = title;
        if (open || !title.equals(currentTitle)) hud.openInfo(title, rows);
        else hud.updateInfo(title, rows);
        currentTitle = title;
    }

    private String currentTitle;

    private static int num(String[] f, int i) {
        try {
            return f.length > i ? Integer.parseInt(f[i]) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static char vesselOf(pl.fawizja.pixel.wheel.WheelGem g) { return g.domain().code; }

    /** Ile sztuk przedmiotu o kluczu jest w plecaku (klejnoty do odkrycia, okruchy). */
    private int have(String key) {
        if (!session.items().containsKey(key)) return 0;
        return session.character().backpack().count(session.items().byKey(key).id());
    }

    private String spellName(String key) {
        pl.fawizja.pixel.magic.SpellType t = session.spells().registry().find(key);
        return t == null ? key : t.name();
    }
}
