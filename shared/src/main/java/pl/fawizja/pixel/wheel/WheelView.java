package pl.fawizja.pixel.wheel;

import pl.fawizja.pixel.i18n.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Okna Kola Przeznaczenia (komputer i telefon, wspolne okno informacyjne HUD). Wiersz = {etykieta, opis, stan,
 * akcja[, "off"]} jak w Hud.openInfo. Akcje "wheel:add|remove|reset|save|load" wykonuje sesja gry (online serwer),
 * reszta to nawigacja okien klienta: "wheel:open", "wheel:q:G" (cwiartka), "wheel:sel:KOD" (pole), "wheel:perks",
 * "wheel:presets", "wheel:resetask".
 */
public final class WheelView {
    public static final String TITLE = "Koło Przeznaczenia";
    public static final String OPEN = "wheel:open";

    private WheelView() {}

    public static String quadrantTitle(WheelDomain d) { return TITLE + ": " + d.label().toLowerCase(java.util.Locale.ROOT) + Messages.get("wheel_windows.cwiartka"); }

    public static String sliceTitle(WheelSlice s) { return TITLE + ": " + s.label(); }

    /** Postep objawienia domeny, np. "etap 1 (312/500)" albo "312/250 do etapu 1". */
    public static String revelationProgress(int points) {
        int st = WheelDomain.stage(points);
        int next = WheelDomain.nextStagePoints(points);
        if (st == 0) return points + "/" + next + Messages.get("wheel_view.do_1_etapu_objawienia");
        if (next <= 0) return Messages.get("wheel_view.objawienie_etap_3") + points + Messages.get("wheel_view.pkt_maks");
        return Messages.get("wheel_view.objawienie_etap") + st + " (" + points + "/" + next + Messages.get("wheel_view.do_etapu") + (st + 1) + ")";
    }

    private static String head(PlayerCharacter pc) {
        int av = pc.wheelPoints();
        int used = pc.wheel().spent();
        return String.format(Messages.get("wheel_view.d_uzyte_z_d"), used, av, Math.max(0, av - used));
    }

    /** Okno glowne: punkty, miejsce, 4 cwiartki (kafle), premie, zestawy, reset. */
    public static List<String[]> overview(PlayerCharacter pc, boolean inTemple) { return overview(pc, inTemple, k -> 0); }

    /** Okno glowne z wierszami klejnotow (have = ile sztuk przedmiotu o kluczu jest w plecaku). */
    public static List<String[]> overview(PlayerCharacter pc, boolean inTemple, java.util.function.ToIntFunction<String> have) {
        List<String[]> rows = new ArrayList<>();
        boolean eligible = pc.wheelPoints() > 0;
        // swiatynia jako stan wiersza punktow (okno miesci 9 wierszy obok rysunku kola)
        String temple = inTemple ? Messages.get("wheel_view.w_swiatyni") : Messages.get("wheel_view.zmiany_tylko_w_swiatyni");
        if (!eligible) {
            rows.add(new String[] {Messages.get("wheel_view.niedostepne"), Messages.get("wheel_view.wymaga_promocji_konta_premium") + DestinyWheel.MIN_LEVEL
                    + Messages.get("wheel_view.1_punkt_za_kazdy") + DestinyWheel.MIN_LEVEL + ")", "", "ach:none"});
        } else {
            rows.add(new String[] {Messages.get("wheel_view.punkty_promocji"), head(pc), temple, "ach:none"});
        }
        Vocation v = pc.vocation();
        for (WheelDomain d : WheelDomain.values()) {
            int pts = pc.wheel().domainPoints(d);
            WheelRevelation r = WheelRevelation.of(d, v);
            int bonus = pc.wheelBonus().gems().revelation(d);
            rows.add(new String[] {d.label() + Messages.get("wheel_view.cwiartka") + pts + " pkt" + (bonus > 0 ? " +" + bonus : ""), r.label() + " - "
                    + revelationProgress(pts + bonus), "", "wheel:q:" + d.code});
        }
        String av = avatarLine(pc);
        if (av != null) rows.add(new String[] {Messages.get("wheel_view.awatar"), av, "", "ach:none"});
        rows.add(new String[] {Messages.get("wheel_view.aktywne_premie"), Messages.get("wheel_view.oddanie_przekonanie_objawienia"), "", "wheel:perks"});
        GemView.overviewRows(rows, pc, have);
        rows.add(new String[] {Messages.get("wheel_panel.zestawy"), DestinyWheel.PRESETS + Messages.get("wheel_view.zapisane_ustawienia_kola"), "", "wheel:presets"});
        rows.add(new String[] {Messages.get("wheel_view.wyczysc_kolo"), Messages.get("wheel_view.wszystkie_punkty_wracaja_do"), "", "wheel:resetask",
                pc.wheel().isEmpty() ? "off" : ""});
        return rows;
    }

    /** Komunikat, gdy kolo jest nieaktywne: czego brakuje postaci (poziom, promocja, premium); null, gdy aktywne. */
    public static String requirementsMessage(PlayerCharacter pc) {
        if (pc.wheelPoints() > 0) return null;
        List<String> miss = new ArrayList<>();
        if (pc.level() < DestinyWheel.MIN_LEVEL) miss.add(Messages.get("wheel_view.poziomu") + DestinyWheel.MIN_LEVEL + Messages.get("game_session.masz") + pc.level() + ")");
        if (!pc.isPromoted()) miss.add("promocji");
        if (!pc.isPremium()) miss.add(Messages.get("wheel_view.konta_premium"));
        return TITLE + " wymaga " + String.join(", ", miss) + ".";
    }

    /** Wiersze wymagan nieaktywnego kola: poziom, promocja, premium (stan: spelnione / brak). */
    public static List<String[]> requirements(PlayerCharacter pc) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {Messages.get("wheel_panel.kolo_nieaktywne"), Messages.get("wheel_view.punkty_promocji_1_za") + DestinyWheel.MIN_LEVEL
                + Messages.get("wheel_view.z_promocja_i_kontem"), "", "ach:none"});
        boolean lv = pc.level() >= DestinyWheel.MIN_LEVEL;
        rows.add(new String[] {Messages.get("wheel_view.poziom") + DestinyWheel.MIN_LEVEL, Messages.get("wheel_view.masz_poziom") + pc.level(), lv ? Messages.get("wheel_view.spelnione") : Messages.get("server_world.brak"), "ach:none"});
        rows.add(new String[] {Messages.get("wheel_view.promocja"), pc.isPromoted() ? Messages.get("wheel_view.masz_promocje") : Messages.get("wheel_view.kup_w_sklepie_albo"), pc.isPromoted() ? Messages.get("wheel_view.spelnione") : Messages.get("server_world.brak"),
                "ach:none"});
        rows.add(new String[] {Messages.get("store.konto_premium"), pc.isPremium() ? Messages.tr("aktywne") : Messages.get("wheel_view.kup_w_sklepie"), pc.isPremium() ? Messages.get("wheel_view.spelnione") : Messages.get("server_world.brak"), "ach:none"});
        rows.add(new String[] {Messages.get("wheel_view.podglad"), Messages.get("wheel_view.dotknij_pola_kola_aby"), "", "ach:none"});
        return rows;
    }

    /**
     * Karta okna kola bez wybranego pola (duze kolo w ramce): punkty, swiatynia, 4 objawienia (do karty objawienia),
     * awatar, klejnoty i wskazowka; nieaktywne kolo - wymagania.
     */
    public static List<String[]> summary(PlayerCharacter pc, boolean inTemple, java.util.function.ToIntFunction<String> have) {
        if (pc.wheelPoints() <= 0) return requirements(pc);
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {Messages.get("wheel_view.punkty_promocji"), head(pc), "", "ach:none"});
        rows.add(new String[] {Messages.get("wheel_windows.swiatynia"), inTemple ? Messages.get("wheel_view.jestes_w_swiatyni_mozesz") : Messages.get("wheel_windows.punkty_rozdasz_tylko_w"),
                inTemple ? Messages.tr("tak") : Messages.tr("nie"), "ach:none"});
        Vocation v = pc.vocation();
        for (WheelDomain d : WheelDomain.values()) {
            int pts = pc.wheel().domainPoints(d);
            int bonus = pc.wheelBonus().gems().revelation(d);
            int st = WheelDomain.stage(pts + bonus);
            WheelRevelation r = WheelRevelation.of(d, v);
            rows.add(new String[] {d.label() + ": " + r.label(), revelationProgress(pts + bonus), st > 0 ? Messages.tr("etap ") + st : "", "wheel:rev:" + d.code});
        }
        String av = avatarLine(pc);
        if (av != null) rows.add(new String[] {Messages.get("wheel_view.awatar"), av, "", "ach:none"});
        GemView.overviewRows(rows, pc, have);
        rows.add(new String[] {Messages.get("wheel_view.wskazowka"), Messages.get("wheel_view.dotknij_pola_kola_aby_2"), "", "ach:none"});
        return rows;
    }

    /** Stan awatara (aktywny / odnowienie) albo null. */
    public static String avatarLine(PlayerCharacter pc) {
        DestinyWheel w = pc.wheel();
        if (w.avatarActive()) return Messages.get("wheel_view.aktywny_jeszcze") + secs(w.avatarMs()) + " (etap " + w.avatarStage() + ")";
        if (w.avatarCooldownMs() > 0) return "odnowienie: " + minutes(w.avatarCooldownMs());
        return null;
    }

    /** Cwiartka: objawienie domeny (karta etapow), 9 pol od srodka na zewnatrz z krotkim efektem, naczynie. */
    public static List<String[]> quadrant(PlayerCharacter pc, WheelDomain d) { return quadrant(pc, d, null); }

    /** Cwiartka z polskimi nazwami czarow (names: klucz czaru -> nazwa). */
    public static List<String[]> quadrant(PlayerCharacter pc, WheelDomain d, Function<String, String> names) {
        List<String[]> rows = new ArrayList<>();
        Vocation v = pc.vocation();
        int av = pc.wheelPoints();
        DestinyWheel w = pc.wheel();
        WheelRevelation r = WheelRevelation.of(d, v);
        int bonus = pc.wheelBonus().gems().revelation(d); // Mistrzostwo objawienia z klejnotu
        int pts = w.domainPoints(d) + bonus;
        int st = WheelDomain.stage(pts);
        rows.add(new String[] {Messages.get("wheel_windows.objawienie") + r.label(), revelationProgress(pts) + (bonus > 0 ? Messages.get("wheel_view.w_tym") + bonus + Messages.get("wheel_view.z_klejnotu") : ""),
                st > 0 ? Messages.tr("etap ") + st : "", "wheel:rev:" + d.code});
        for (WheelSlice s : WheelSlice.of(d)) {
            int p = w.points(s);
            // stan w etykiecie: opis efektu ma caly wiersz (okno przycina dlugi tekst)
            rows.add(new String[] {cap(s.pos.label()) + ": " + p + "/" + s.capacity() + ", " + state(w, s, av), shortEffect(s, v, names), "",
                    "wheel:sel:" + s.code()});
        }
        rows.add(GemView.vesselRow(pc, d));
        rows.add(new String[] {Messages.get("bestiary_windows.wroc"), TITLE, "", OPEN});
        return rows;
    }

    private static String state(DestinyWheel w, WheelSlice s, int av) {
        int p = w.points(s);
        if (p >= s.capacity()) return Messages.get("wheel_view.pelne");
        if (w.open(s, av)) return Messages.tr("otwarte");
        if (av < s.pos.threshold) return "od " + s.pos.threshold + Messages.get("wheel_view.pkt_promocji");
        return Messages.get("wheel_view.zamkniete");
    }

    /** Najdluzszy krotki opis pola w wierszu cwiartki. */
    static final int SHORT_MAX = 50;

    /** Krotki efekt pola z danych: "+3 HP/pkt; pełne: +1 walka wręcz", przy czarze premia stopnia I. */
    public static String shortEffect(WheelSlice s, Vocation v, Function<String, String> names) {
        String head = WheelPerks.perPoint(s, v) + Messages.get("wheel_view.pkt_pelne");
        String conv;
        switch (s.conviction) {
            case SKILL: conv = "+1 " + WheelPerks.skillShort(v); break;
            case LIFE_LEECH: conv = "+" + SpellAugment.num(WheelPerks.LIFE_LEECH) + Messages.get("spell_augment.kradziezy_zycia"); break;
            case MANA_LEECH: conv = "+" + SpellAugment.num(WheelPerks.MANA_LEECH) + Messages.get("spell_augment.kradziezy_many"); break;
            case PASSIVE_A: conv = WheelPassive.of(v, true).label(); break;
            case PASSIVE_B: conv = WheelPassive.of(v, false).label(); break;
            case VESSEL: conv = Messages.get("wheel_view.naczynie_klejnotu"); break;
            default: {
                WheelPerks.Upgrade u = WheelPerks.upgrade(v, s.conviction.spellGroup());
                String n = plain(spellNames(u, names));
                conv = n + " I: " + u.grade1.describe();
                if (head.length() + conv.length() > SHORT_MAX) conv = Messages.get("wheel_view.czar") + n;
                if (head.length() + conv.length() > SHORT_MAX) conv = u.spells.size() > 1 ? "ulepszenie " + u.spells.size() + Messages.get("wheel_view.czarow") : Messages.get("wheel_view.ulepszenie_czaru");
            }
        }
        String out = head + conv;
        return out.length() <= SHORT_MAX ? out : out.substring(0, SHORT_MAX - 3).trim() + "...";
    }

    /** Nazwa bez formuly w nawiasie: "Silne oczyszczenie ran (exura gran ico)" -> "Silne oczyszczenie ran". */
    static String plain(String name) {
        int i = name.indexOf(" (");
        return i > 0 ? name.substring(0, i) : name;
    }

    /** Nazwa czaru po polsku z nazwa z Tibii, gdy inna: "Szał (Tibia: Front Sweep)". */
    static String spellLabel(WheelPerks.Upgrade u, Function<String, String> names) {
        String pl = spellNames(u, names);
        return pl.equalsIgnoreCase(u.tibia) ? pl : pl + Messages.get("wheel_view.tibia") + u.tibia + ")";
    }

    /** Karta pola jak dymek w Tibii: oddanie (punkt / teraz / pelne), przekonanie, stopnie czaru, wymagania, przyciski. */
    public static List<String[]> slice(PlayerCharacter pc, WheelSlice s, boolean inTemple, Function<String, String> names) {
        List<String[]> rows = new ArrayList<>();
        Vocation v = pc.vocation();
        int av = pc.wheelPoints();
        DestinyWheel w = pc.wheel();
        int p = w.points(s);
        int free = Math.max(0, av - w.spent());
        boolean full = p >= s.capacity();
        rows.add(new String[] {Messages.get("wheel_view.punkty_w_polu"), p + "/" + s.capacity() + Messages.get("wheel_view.wolne") + free, state(w, s, av), "ach:none"});
        rows.add(new String[] {Messages.get("wheel_view.oddanie"), WheelPerks.perPoint(s, v) + Messages.get("wheel_view.za_punkt"), "", "ach:none"});
        rows.add(new String[] {Messages.get("wheel_view.oddanie_teraz"), WheelPerks.dedicationAt(s, v, p) + Messages.get("wheel_view.pelne_pole") + WheelPerks.dedicationAt(s, v, s.capacity()) + ")",
                "", "ach:none"});
        if (!full) {
            rows.add(new String[] {Messages.get("wheel_view.nastepny_punkt"), WheelPerks.perPoint(s, v) + (p + 1 == s.capacity() ? Messages.get("wheel_view.i_przekonanie") : "")
                    + Messages.get("wheel_view.do_pelna") + (s.capacity() - p) + " pkt", "", "ach:none"});
        }
        String convState = full ? Messages.tr("aktywne") : Messages.get("wheel_view.gdy_pole_pelne");
        int g = s.conviction.spellGroup();
        switch (s.conviction) {
            case PASSIVE_A: case PASSIVE_B: {
                WheelPassive wp = WheelPassive.of(v, s.conviction == WheelSlice.Conv.PASSIVE_A);
                rows.add(new String[] {Messages.get("wheel_view.przekonanie"), wp.label() + Messages.get("wheel_view.tibia") + wp.tibia() + ")", convState, "ach:none"});
                wrapped(rows, Messages.get("wheel_view.dzialanie"), wp.description());
                break;
            }
            case VESSEL: {
                rows.add(new String[] {Messages.get("wheel_view.przekonanie"), Messages.get("wheel_perks.naczynie_klejnotu_1_rezonansu"), convState, "ach:none"});
                int res = pc.wheel().resonance(s.domain);
                rows.add(new String[] {Messages.get("wheel_view.rezonans"), res + Messages.get("common.z") + countVessels(s.domain) + Messages.get("wheel_view.naczyn_1_mod_1"),
                        "", "ach:none"});
                rows.add(GemView.vesselRow(pc, s.domain));
                break;
            }
            default:
                if (g >= 0) {
                    WheelPerks.Upgrade u = WheelPerks.upgrade(v, g);
                    int grade = pc.wheelBonus().grade(g);
                    wrappedState(rows, Messages.get("wheel_view.przekonanie_czar"), spellLabel(u, names), convState);
                    wrappedState(rows, Messages.get("wheel_view.stopien_i"), u.grade1.describe(), grade >= 1 ? Messages.tr("aktywny") : Messages.get("wheel_view.1_pelne_pole"));
                    wrappedState(rows, Messages.get("wheel_view.stopien_ii"), Messages.get("wheel_view.razem") + u.at(2).describe(), grade >= 2 ? Messages.tr("aktywny") : Messages.get("wheel_view.oba_pola"));
                    WheelSlice other = otherOfGroup(s, g);
                    if (other != null) {
                        rows.add(new String[] {Messages.get("wheel_view.drugie_pole"), other.label() + ": " + w.points(other) + "/" + other.capacity(),
                                w.points(other) >= other.capacity() ? Messages.get("wheel_view.pelne") : "", "wheel:sel:" + other.code()});
                    }
                    if (!u.note.isEmpty()) wrapped(rows, Messages.get("wheel_view.uwaga"), u.note);
                } else {
                    rows.add(new String[] {Messages.get("wheel_view.przekonanie"), WheelPerks.conviction(s, v), convState, "ach:none"});
                }
        }
        WheelRevelation r = WheelRevelation.of(s.domain, v);
        rows.add(new String[] {Messages.get("wheel_view.objawienie_domeny"), r.label() + ": " + revelationProgress(w.domainPoints(s.domain)
                + pc.wheelBonus().gems().revelation(s.domain)), "", "wheel:rev:" + s.domain.code});
        if (s.pos.threshold > 0) {
            rows.add(new String[] {Messages.get("wheel_view.wymaga"), Messages.get("wheel_view.pelne_pole_blizej_srodka") + s.pos.threshold + Messages.get("wheel_view.pkt_promocji"),
                    w.open(s, av) || p > 0 ? Messages.get("wheel_view.spelnione") : Messages.get("wheel_view.niespelnione"), "ach:none"});
        }
        boolean canAdd = inTemple && av > 0 && p < s.capacity() && free > 0 && w.open(s, av);
        boolean canRemove = inTemple && p > 0;
        String c = s.code();
        int toFull = Math.min(free, s.capacity() - p);
        rows.add(new String[] {Messages.get("wheel_view.dodaj_1"), "", "", "wheel:add:" + c + ":1", canAdd ? "" : "off"});
        rows.add(new String[] {Messages.get("wheel_view.dodaj_10"), "", "", "wheel:add:" + c + ":10", canAdd ? "" : "off"});
        rows.add(new String[] {Messages.get("wheel_view.dodaj_do_pelna"), "+" + Math.max(0, toFull), "", "wheel:add:" + c + ":" + Math.max(1, toFull), canAdd ? "" : "off"});
        rows.add(new String[] {Messages.get("wheel_view.odejmij_1"), "", "", "wheel:remove:" + c + ":1", canRemove ? "" : "off"});
        rows.add(new String[] {Messages.get("wheel_view.oproznij_pole"), "", "", "wheel:remove:" + c + ":" + Math.max(1, p), canRemove ? "" : "off"});
        rows.add(new String[] {Messages.get("bestiary_windows.wroc"), s.domain.label() + Messages.get("wheel_windows.cwiartka"), "", "wheel:q:" + s.domain.code});
        return rows;
    }

    public static String revelationTitle(WheelDomain d) { return TITLE + Messages.get("wheel_view.objawienie") + d.label().toLowerCase(java.util.Locale.ROOT) + ")"; }

    /** Karta objawienia domeny: nazwa (i z Tibii), czar, etapy 1-3 z progami, moc objawien, mistrzostwo z klejnotu. */
    public static List<String[]> revelation(PlayerCharacter pc, WheelDomain d, Function<String, String> names) {
        List<String[]> rows = new ArrayList<>();
        Vocation v = pc.vocation();
        WheelRevelation r = WheelRevelation.of(d, v);
        int bonus = pc.wheelBonus().gems().revelation(d);
        int pts = pc.wheel().domainPoints(d) + bonus;
        int st = WheelDomain.stage(pts);
        rows.add(new String[] {Messages.get("wheel_view.objawienie_2"), r.label() + (r.label().equalsIgnoreCase(r.tibia()) ? "" : Messages.get("wheel_view.tibia") + r.tibia() + ")"),
                st > 0 ? Messages.tr("etap ") + st : Messages.tr("nieaktywne"), "ach:none"});
        String spell = r.spell == null ? null : names == null ? r.spell : plain(names.apply(r.spell));
        if (spell != null && !spell.equalsIgnoreCase(r.label())) rows.add(new String[] {Messages.get("wheel_view.czar_2"), names == null ? r.spell : names.apply(r.spell), "", "ach:none"});
        rows.add(new String[] {Messages.get("wheel_view.punkty_domeny"), revelationProgress(pts), "", "ach:none"});
        for (int i = 1; i <= 3; i++) {
            wrappedState(rows, Messages.get("bestiary_card.etap") + i, r.stage(i), st >= i ? Messages.tr("aktywny") : "od " + WheelDomain.STAGE_POINTS[i - 1] + " pkt");
        }
        int[] f = WheelDomain.STAGE_FLAT;
        rows.add(new String[] {Messages.get("wheel_view.moc_objawien"), "+" + f[0] + " / +" + f[1] + " / +" + f[2] + Messages.get("wheel_view.obrazen_i_leczenia_etap"),
                st > 0 ? "+" + f[st - 1] : "", "ach:none"});
        rows.add(new String[] {Messages.get("wheel_view.mistrzostwo_objawienia"), bonus > 0 ? "+" + bonus + Messages.get("wheel_view.pkt_z_klejnotu_w") : Messages.get("wheel_view.brak_mod_najwyzszy_klejnotu"),
                "", "ach:none"});
        rows.add(new String[] {Messages.get("bestiary_windows.wroc"), d.label() + Messages.get("wheel_windows.cwiartka"), "", "wheel:q:" + d.code});
        return rows;
    }

    private static int countVessels(WheelDomain d) {
        int n = 0;
        for (WheelSlice s : WheelSlice.of(d)) if (s.conviction == WheelSlice.Conv.VESSEL) n++;
        return n;
    }

    private static WheelSlice otherOfGroup(WheelSlice s, int g) {
        for (WheelSlice o : WheelPerks.slicesOfGroup(g)) if (o != s) return o;
        return null;
    }

    /** Jak {@link #wrapped}, ze stanem w etykiecie ("Etap 2 (aktywny)"), zeby opis mial caly wiersz. */
    static void wrappedState(List<String[]> rows, String label, String detail, String state) {
        wrapped(rows, state.isEmpty() ? label : label + " (" + state + ")", detail);
    }

    /** Aktywne premie: sumy oddania, przekonania, ulepszenia czarow, pasywki i objawienia. */
    public static List<String[]> perks(PlayerCharacter pc, Function<String, String> names) {
        List<String[]> rows = new ArrayList<>();
        WheelBonus b = pc.wheelBonus();
        Vocation v = pc.vocation();
        if (b == WheelBonus.NONE) {
            rows.add(new String[] {Messages.get("wheel_view.brak_premii"), pc.wheelPoints() > 0 ? Messages.get("wheel_view.rozdaj_punkty_w_swiatyni") : Messages.get("wheel_view.kolo_nieaktywne"), "", "ach:none"});
        } else {
            rows.add(new String[] {Messages.get("wheel_view.oddanie"), String.format(Messages.get("wheel_view.d_hp_d_many"), b.health, b.mana, b.capacity,
                    SpellAugment.num(b.mitigation)), "", "ach:none"});
            StringBuilder conv = new StringBuilder();
            if (b.skill > 0) conv.append("+").append(b.skill).append(" ").append(WheelPerks.skillLabel(v));
            if (b.lifeLeech > 0) append(conv, "+" + SpellAugment.num(b.lifeLeech) + Messages.get("spell_augment.kradziezy_zycia"));
            if (b.manaLeech > 0) append(conv, "+" + SpellAugment.num(b.manaLeech) + Messages.get("spell_augment.kradziezy_many"));
            rows.add(new String[] {Messages.get("wheel_view.przekonanie"), conv.length() == 0 ? Messages.get("wheel_view.brak_pelnych_pol") : conv.toString(), "", "ach:none"});
            for (int g = 0; g < 5; g++) {
                int grade = b.grade(g);
                if (grade <= 0) continue;
                WheelPerks.Upgrade u = WheelPerks.upgrade(v, g);
                wrapped(rows, spellNames(u, names) + " (st. " + grade + ")", u.at(grade).describe());
            }
            for (WheelPassive p : b.passives()) wrapped(rows, p.label(), p.description());
            for (WheelDomain d : WheelDomain.values()) {
                int st = b.stage(d);
                if (st <= 0) continue;
                WheelRevelation r = WheelRevelation.of(d, v);
                wrapped(rows, r.label() + " (etap " + st + ")", r.stage(st));
            }
            if (b.flatDamage > 0) {
                rows.add(new String[] {Messages.get("wheel_view.moc_objawien"), "+" + b.flatDamage + Messages.get("wheel_view.obrazen_i") + b.flatHealing + " leczenia", "", "ach:none"});
            }
            if (pc.wheel().giftCooldownMs() > 0) {
                rows.add(new String[] {Messages.get("wheel_view.dar_zycia"), "odnowienie: " + minutes(pc.wheel().giftCooldownMs()), "", "ach:none"});
            }
            String av = avatarLine(pc);
            if (av != null) rows.add(new String[] {Messages.get("wheel_view.awatar_teraz"), av, "", "ach:none"});
            GemView.perkRows(rows, pc, names);
        }
        rows.add(new String[] {Messages.get("bestiary_windows.wroc"), TITLE, "", OPEN});
        return rows;
    }

    /** Zestawy: zapis wszedzie, wczytanie w swiatyni. */
    public static List<String[]> presets(PlayerCharacter pc, boolean inTemple) {
        List<String[]> rows = new ArrayList<>();
        DestinyWheel w = pc.wheel();
        int av = pc.wheelPoints();
        for (int i = 0; i < DestinyWheel.PRESETS; i++) {
            boolean has = w.hasPreset(i);
            rows.add(new String[] {Messages.get("wheel_view.zapisz_w_zestawie") + (i + 1), has ? Messages.get("wheel_view.nadpisze") + w.presetPoints(i) + " pkt)" : "pusty",
                    "", "wheel:save:" + i, av > 0 ? "" : "off"});
            rows.add(new String[] {Messages.get("wheel_view.wczytaj_zestaw") + (i + 1), has ? w.presetPoints(i) + " pkt" : "pusty", "",
                    "wheel:load:" + i, has && inTemple && w.presetPoints(i) <= av ? "" : "off"});
        }
        rows.add(new String[] {Messages.get("bestiary_windows.wroc"), TITLE, "", OPEN});
        return rows;
    }

    public static List<String[]> resetConfirm(PlayerCharacter pc, boolean inTemple) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {Messages.get("gem_view.na_pewno"), "zwrot " + pc.wheel().spent() + Messages.get("wheel_view.pkt_zestawy_zostaja"), "", "ach:none"});
        rows.add(new String[] {Messages.get("wheel_view.tak_wyczysc_kolo"), inTemple ? "" : Messages.get("wheel_view.tylko_w_swiatyni"), "", "wheel:reset", inTemple ? "" : "off"});
        rows.add(new String[] {Messages.get("bestiary_windows.wroc"), TITLE, "", OPEN});
        return rows;
    }

    /** Najdluzszy opis w wierszu (okno nie przycina tekstu; reszta idzie do wiersza ciagu dalszego). */
    static final int DETAIL_MAX = 50;

    /** Wiersz informacyjny z dlugim opisem pocietym po slowach na kilka wierszy ("cd."). */
    static void wrapped(List<String[]> rows, String label, String detail) {
        String rest = detail;
        String l = label;
        while (rest.length() > DETAIL_MAX) {
            int cut = rest.lastIndexOf(' ', DETAIL_MAX);
            if (cut <= 0) cut = DETAIL_MAX;
            rows.add(new String[] {l, rest.substring(0, cut).trim(), "", "ach:none"});
            rest = rest.substring(cut).trim();
            l = "  (cd.)";
        }
        rows.add(new String[] {l, rest, "", "ach:none"});
    }

    private static String spellNames(WheelPerks.Upgrade u, Function<String, String> names) {
        StringBuilder b = new StringBuilder();
        for (String k : u.spells) append(b, names == null ? k : names.apply(k));
        return b.toString();
    }

    private static void append(StringBuilder b, String s) {
        if (b.length() > 0) b.append(", ");
        b.append(s);
    }

    private static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    static String secs(long ms) { return (ms + 999) / 1000 + " s"; }

    static String minutes(long ms) {
        long m = (ms + 59_999) / 60_000;
        return m >= 60 ? m / 60 + " h " + m % 60 + Messages.get("hud.min") : m + Messages.get("hud.min");
    }
}
