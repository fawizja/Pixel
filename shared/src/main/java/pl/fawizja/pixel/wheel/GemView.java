package pl.fawizja.pixel.wheel;

import pl.fawizja.pixel.i18n.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Okna klejnotow w oknie Kola Przeznaczenia (komputer i telefon, wiersze jak {@link WheelView}): Pracownia klejnotow
 * ("wheel:gems:strona"), karta klejnotu ("wheel:card:ID"), potwierdzenie rozbicia ("wheel:gemask:ID"), naczynie domeny
 * ("wheel:vessel:G") i Warsztat okruchow ("wheel:frag:b|s:strona"). Akcje "wheel:gem:..." i "wheel:grade:..." wykonuje
 * sesja gry (online serwer). have = ile sztuk przedmiotu o kluczu ma postac w plecaku.
 */
public final class GemView {
    /** Klejnotow na strone listy (okno nie przewija wierszy). */
    public static final int PAGE = 8;
    /** Modow na strone Warsztatu okruchow. */
    public static final int FRAG_PAGE = 9;

    private GemView() {}

    public static String atelierTitle() { return WheelView.TITLE + Messages.get("gem_view.pracownia_klejnotow"); }
    public static String vesselTitle(WheelDomain d) { return WheelView.TITLE + Messages.get("gem_view.naczynie") + d.label().toLowerCase(java.util.Locale.ROOT) + ")"; }
    public static String fragTitle() { return WheelView.TITLE + Messages.get("gem_view.warsztat_okruchow"); }

    private static String gold(long g) { return String.format("%,d gp", g).replace(',', ' ').replace(' ', ' '); }

    // ------------------------------------------------------------------ wiersze w oknach kola

    /** Wiersze przegladu kola: Pracownia i Warsztat. */
    static void overviewRows(List<String[]> rows, PlayerCharacter pc, ToIntFunction<String> have) {
        GemAtelier a = pc.wheel().gems();
        int inVessels = 0;
        for (WheelDomain d : WheelDomain.values()) if (a.socketed(d) != null) inVessels++;
        int unrevealed = 0;
        for (GemQuality q : GemQuality.values()) unrevealed += have.applyAsInt(GemAtelier.gemKey(pc.vocation(), q));
        rows.add(new String[] {Messages.get("gem_view.klejnoty_i_naczynia"), a.count() + " odkrytych, " + inVessels + "/4 w naczyniach"
                + (unrevealed > 0 ? Messages.get("gem_view.do_odkrycia") + unrevealed : ""), "", "wheel:gems:0"});
    }

    /** Wiersz naczynia w oknie cwiartki. */
    static String[] vesselRow(PlayerCharacter pc, WheelDomain d) {
        WheelGem g = pc.wheel().gems().socketed(d);
        int res = pc.wheel().resonance(d);
        String what = g == null ? Messages.get("gem_view.puste") : g.quality.title() + " #" + g.id + Messages.get("gem_view.dziala") + GemAtelier.activeMods(g, res) + "/" + (g.quality.basicMods()
                + (g.supreme != null ? 1 : 0)) + " mod.";
        return new String[] {Messages.get("gem_view.naczynie_klejnotu"), what + " (rezonans " + res + "/3)", "", "wheel:vessel:" + d.code};
    }

    // ------------------------------------------------------------------ Pracownia

    /** Pracownia: klejnoty do odkrycia (swoja profesja), okruchy, lista odkrytych (najnowsze pierwsze), strony. */
    public static List<String[]> atelier(PlayerCharacter pc, int page, ToIntFunction<String> have, Function<String, String> names) {
        List<String[]> rows = new ArrayList<>();
        GemAtelier a = pc.wheel().gems();
        boolean eligible = pc.wheelPoints() > 0;
        Vocation v = pc.vocation();
        for (GemQuality q : GemQuality.values()) {
            int n = have.applyAsInt(GemAtelier.gemKey(v, q));
            if (n <= 0 && q != GemQuality.LESSER) continue;
            rows.add(new String[] {Messages.get("gem_view.odkryj") + GemAtelier.gemName(v, q) + " (" + n + ")", gold(q.revealCost), "",
                    "wheel:gem:reveal:" + q.code, n > 0 && eligible && a.count() < GemAtelier.MAX_GEMS ? "" : "off"});
        }
        List<WheelGem> list = new ArrayList<>(a.gems());
        java.util.Collections.reverse(list);
        int pages = Math.max(1, (list.size() + PAGE - 1) / PAGE);
        int p = Math.max(0, Math.min(page, pages - 1));
        rows.add(new String[] {Messages.get("gem_view.odkryte_klejnoty") + a.count() + "/" + GemAtelier.MAX_GEMS, list.isEmpty()
                ? Messages.get("gem_view.klejnoty_wypadaja_z_potworow") : Messages.get("hud.strona_2") + (p + 1) + "/" + pages, "", "ach:none"});
        for (int i = p * PAGE; i < Math.min(list.size(), (p + 1) * PAGE); i++) {
            WheelGem g = list.get(i);
            String state = a.isSocketed(g) ? Messages.get("gem_view.w_naczyniu") : g.locked() ? "zablokowany" : "";
            rows.add(new String[] {g.title(), modsShort(g, names), state, "wheel:card:" + g.id});
        }
        if (pages > 1) {
            rows.add(new String[] {Messages.get("hud.nastepna_strona"), "", "", "wheel:gems:" + (p + 1 < pages ? p + 1 : 0)});
        }
        rows.add(new String[] {Messages.get("gem_view.warsztat_okruchow_2"), Messages.get("gem_view.stopnie_modow") + have.applyAsInt(GemAtelier.LESSER_FRAGMENT_KEY) + " mn. / "
                + have.applyAsInt(GemAtelier.GREATER_FRAGMENT_KEY) + Messages.get("gem_view.wk_okruchow"), "", "wheel:frag:b:0"});
        rows.add(new String[] {Messages.get("bestiary_windows.wroc"), WheelView.TITLE, "", WheelView.OPEN});
        return rows;
    }

    /** Krotki opis modow: "Ogień + HP + Unik". */
    static String modsShort(WheelGem g, Function<String, String> names) {
        StringBuilder b = new StringBuilder(g.basic1.shortLabel());
        if (g.basic2 != null) b.append(", ").append(g.basic2.shortLabel());
        if (g.supreme != null) b.append(", ").append(g.supreme.shortLabel(names));
        return b.toString();
    }

    /** Karta klejnotu: mody ze stopniami, naczynie, blokada, zmiana domeny, rozbicie. */
    public static List<String[]> card(PlayerCharacter pc, int id, boolean inTemple, Function<String, String> names) {
        List<String[]> rows = new ArrayList<>();
        GemAtelier a = pc.wheel().gems();
        WheelGem g = a.gem(id);
        if (g == null) {
            rows.add(new String[] {Messages.get("gem_view.brak_klejnotu"), Messages.get("gem_view.rozbity_albo_nieznany"), "", "ach:none"});
            rows.add(new String[] {Messages.get("bestiary_windows.wroc"), Messages.get("gem_view.pracownia_klejnotow_2"), "", "wheel:gems:0"});
            return rows;
        }
        Vocation v = pc.vocation();
        int res = pc.wheel().resonance(g.domain);
        boolean in = a.isSocketed(g);
        rows.add(new String[] {Messages.get("gem_view.jakosc_i_domena"), g.quality.label() + ", " + g.domain.label().toLowerCase(java.util.Locale.ROOT) + " domena", "", "ach:none"});
        modRow(rows, "Mod 1 (" + GemAtelier.roman(a.grade(g.basic1)) + ")", g.basic1.describe(v, a.grade(g.basic1)), in, res >= 1, 1);
        if (g.basic2 != null) modRow(rows, "Mod 2 (" + GemAtelier.roman(a.grade(g.basic2)) + ")", g.basic2.describe(v, a.grade(g.basic2)), in, res >= 2, 2);
        if (g.supreme != null) {
            modRow(rows, Messages.get("gem_view.najwyzszy") + GemAtelier.roman(a.grade(g.supreme)) + ")", g.supreme.describe(a.grade(g.supreme), names), in, res >= 3, 3);
        }
        WheelGem cur = a.socketed(g.domain);
        rows.add(new String[] {in ? Messages.get("gem_view.w_naczyniu_2") : Messages.get("gem_view.wloz_do_naczynia"), in ? "rezonans " + res + "/3" : cur == null ? Messages.get("gem_view.naczynie_puste")
                : "zamiast " + cur.title(), inTemple ? "" : Messages.get("wheel_view.tylko_w_swiatyni"), in ? "ach:none" : "wheel:gem:socket:" + g.id,
                !in && inTemple ? "" : "off"});
        rows.add(new String[] {g.locked() ? Messages.get("hud.odblokuj") : Messages.get("gem_view.zablokuj"), Messages.get("gem_view.blokada_chroni_przed_rozbiciem"), "", "wheel:gem:lock:" + g.id});
        WheelDomain next = WheelDomain.values()[(g.domain.ordinal() + 1) % 4];
        rows.add(new String[] {Messages.get("gem_view.zmien_domene") + next.label().toLowerCase(java.util.Locale.ROOT), gold(g.quality.switchCost), "",
                "wheel:gem:switch:" + g.id, g.locked() ? "off" : ""});
        rows.add(new String[] {Messages.get("gem_view.rozbij_na_okruchy"), g.quality.fragMin + "-" + g.quality.fragMax + (g.quality.greaterFragments ? Messages.get("gem_view.wiekszych") : " mniejszych"),
                "", "wheel:gemask:" + g.id, g.locked() ? "off" : ""});
        rows.add(new String[] {Messages.get("bestiary_windows.wroc"), Messages.get("gem_view.pracownia_klejnotow_2"), "", "wheel:gems:0"});
        return rows;
    }

    private static void modRow(List<String[]> rows, String label, String detail, boolean in, boolean active, int need) {
        String state = !in ? "" : active ? Messages.get("gem_view.dziala_2") : "rezonans " + need + "+";
        rows.add(new String[] {label, detail, state, "ach:none"});
    }

    public static List<String[]> destroyConfirm(PlayerCharacter pc, int id) {
        List<String[]> rows = new ArrayList<>();
        WheelGem g = pc.wheel().gems().gem(id);
        rows.add(new String[] {Messages.get("gem_view.na_pewno"), g == null ? Messages.get("gem_view.brak_klejnotu_2") : g.title() + " przepadnie", "", "ach:none"});
        rows.add(new String[] {Messages.get("gem_view.tak_rozbij"), g == null ? "" : g.quality.fragMin + "-" + g.quality.fragMax
                + (g.quality.greaterFragments ? Messages.get("gem_view.wiekszych") : " mniejszych") + Messages.get("gem_view.okruchow"), "", "wheel:gem:destroy:" + id,
                g == null || g.locked() ? "off" : ""});
        rows.add(new String[] {Messages.get("bestiary_windows.wroc"), Messages.get("gem_view.karta_klejnotu"), "", "wheel:card:" + id});
        return rows;
    }

    // ------------------------------------------------------------------ naczynie

    /** Naczynie domeny: rezonans (pelne pola naczyn), wlozony klejnot i klejnoty domeny do wlozenia. */
    public static List<String[]> vessel(PlayerCharacter pc, WheelDomain d, boolean inTemple, Function<String, String> names) {
        List<String[]> rows = new ArrayList<>();
        GemAtelier a = pc.wheel().gems();
        int res = pc.wheel().resonance(d);
        StringBuilder full = new StringBuilder();
        for (WheelSlice s : WheelSlice.of(d)) {
            if (s.conviction != WheelSlice.Conv.VESSEL) continue;
            if (full.length() > 0) full.append(", ");
            full.append(s.pos.label()).append(pc.wheel().points(s) >= s.capacity() ? Messages.get("gem_view.pelne") : " (" + pc.wheel().points(s) + "/" + s.capacity() + ")");
        }
        wrapped(rows, Messages.get("gem_view.rezonans") + res + "/3", Messages.get("gem_view.pola_naczyn") + full);
        WheelGem g = a.socketed(d);
        if (g == null) {
            rows.add(new String[] {Messages.get("gem_view.w_naczyniu_2"), Messages.get("gem_view.pusto_wloz_klejnot_tej"), "", "ach:none"});
        } else {
            rows.add(new String[] {Messages.get("gem_view.w_naczyniu_2"), g.title(), "", "wheel:card:" + g.id});
            Vocation v = pc.vocation();
            modRow(rows, Messages.get("gem_view.mod_1"), g.basic1.describe(v, a.grade(g.basic1)), true, res >= 1, 1);
            if (g.basic2 != null) modRow(rows, Messages.get("gem_view.mod_2"), g.basic2.describe(v, a.grade(g.basic2)), true, res >= 2, 2);
            if (g.supreme != null) modRow(rows, Messages.get("gem_view.najwyzszy_2"), g.supreme.describe(a.grade(g.supreme), names), true, res >= 3, 3);
            rows.add(new String[] {Messages.get("gem_view.wyjmij_klejnot"), inTemple ? "" : Messages.get("wheel_view.tylko_w_swiatyni"), "", "wheel:gem:unsocket:" + d.code, inTemple ? "" : "off"});
        }
        int shown = 0;
        for (WheelGem o : a.ofDomain(d)) {
            if (a.isSocketed(o)) continue;
            if (shown++ >= 5) break;
            rows.add(new String[] {Messages.get("gem_view.wloz") + o.title(), modsShort(o, names), "", "wheel:gem:socket:" + o.id, inTemple ? "" : "off"});
        }
        if (shown == 0 && g == null) rows.add(new String[] {Messages.get("gem_view.brak_klejnotow_tej_domeny"), Messages.get("gem_view.odkryj_klejnoty_w_pracowni"), "", "wheel:gems:0"});
        rows.add(new String[] {Messages.get("bestiary_windows.wroc"), d.label() + Messages.get("wheel_windows.cwiartka"), "", "wheel:q:" + d.code});
        return rows;
    }

    private static void wrapped(List<String[]> rows, String l, String d) { WheelView.wrapped(rows, l, d); }

    // ------------------------------------------------------------------ Warsztat okruchow

    /** Warsztat: mody podstawowe (b) albo najwyzsze profesji (s) ze stopniem i kosztem nastepnego, strony. */
    public static List<String[]> fragments(PlayerCharacter pc, boolean supreme, int page, ToIntFunction<String> have, Function<String, String> names) {
        List<String[]> rows = new ArrayList<>();
        GemAtelier a = pc.wheel().gems();
        boolean eligible = pc.wheelPoints() > 0;
        int lesser = have.applyAsInt(GemAtelier.LESSER_FRAGMENT_KEY);
        int greater = have.applyAsInt(GemAtelier.GREATER_FRAGMENT_KEY);
        rows.add(new String[] {Messages.get("gem_view.okruchy"), lesser + " mniejszych, " + greater + Messages.get("gem_view.wiekszych_stopien_iv_1") + a.maxedMods() + ")", "", "ach:none"});
        rows.add(new String[] {supreme ? Messages.get("gem_view.pokaz_mody_podstawowe") : Messages.get("gem_view.pokaz_mody_najwyzsze"), supreme ? Messages.get("gem_view.mniejsze_okruchy") : Messages.get("gem_view.wieksze_okruchy"), "",
                "wheel:frag:" + (supreme ? "b" : "s") + ":0"});
        List<String[]> mods = new ArrayList<>();
        if (supreme) {
            for (SupremeGemMod m : SupremeGemMod.of(pc.vocation())) {
                long[] c = a.supremeGradeCost(m);
                mods.add(gradeRow(m.shortLabel(names), a.grade(m), c, greater, eligible, "wheel:grade:s:" + m.name()));
            }
        } else {
            for (BasicGemMod m : BasicGemMod.values()) {
                long[] c = a.basicGradeCost(m);
                mods.add(gradeRow(m.shortLabel(), a.grade(m), c, lesser, eligible, "wheel:grade:b:" + m.name()));
            }
        }
        int pages = Math.max(1, (mods.size() + FRAG_PAGE - 1) / FRAG_PAGE);
        int p = Math.max(0, Math.min(page, pages - 1));
        rows.addAll(mods.subList(p * FRAG_PAGE, Math.min(mods.size(), (p + 1) * FRAG_PAGE)));
        if (pages > 1) {
            rows.add(new String[] {Messages.get("hud.nastepna_strona"), (p + 1) + "/" + pages, "", "wheel:frag:" + (supreme ? "s" : "b") + ":" + (p + 1 < pages ? p + 1 : 0)});
        }
        rows.add(new String[] {Messages.get("bestiary_windows.wroc"), WheelView.TITLE, "", WheelView.OPEN});
        return rows;
    }

    private static String[] gradeRow(String label, int grade, long[] cost, int frags, boolean eligible, String action) {
        if (cost == null) return new String[] {label + " (IV)", Messages.get("gem_view.najwyzszy_stopien"), "", "ach:none"};
        boolean ok = eligible && frags >= cost[1];
        return new String[] {label + " (" + GemAtelier.roman(grade) + ")", "na " + GemAtelier.roman(grade + 1) + ": " + gold(cost[0]) + ", "
                + cost[1] + " okr.", "", action, ok ? "" : "off"};
    }

    // ------------------------------------------------------------------ premie

    /** Wiersze premii klejnotow w oknie "Aktywne premie". */
    static void perkRows(List<String[]> rows, PlayerCharacter pc, Function<String, String> names) {
        WheelBonus b = pc.wheelBonus();
        GemAtelier a = pc.wheel().gems();
        GemAtelier.Effects e = b.gems();
        List<String> parts = new ArrayList<>();
        for (pl.fawizja.pixel.combat.DamageType t : pl.fawizja.pixel.combat.DamageType.values()) {
            double r = e.resist(t);
            if (r != 0) parts.add(t.label() + " " + (r > 0 ? "+" : "") + SupremeGemMod.num(r) + "%");
        }
        int active = 0;
        for (WheelDomain d : WheelDomain.values()) {
            WheelGem g = a.socketed(d);
            if (g != null) active += GemAtelier.activeMods(g, pc.wheel().resonance(d));
        }
        if (active == 0) {
            rows.add(new String[] {Messages.get("wheel_panel.klejnoty"), Messages.get("gem_view.brak_dzialajacych_modyfikatoro"), "", "ach:none"});
            return;
        }
        StringBuilder s = new StringBuilder(active + " mod.: ");
        if (e.health > 0) s.append("+").append(e.health).append(" HP, ");
        if (e.mana > 0) s.append("+").append(e.mana).append(" many, ");
        if (e.capacity > 0) s.append("+").append(e.capacity).append(Messages.get("gem_view.udzwigu"));
        if (e.mitigation > 0) s.append("+").append(SupremeGemMod.num(e.mitigation)).append(Messages.get("gem_view.mitygacji"));
        if (e.dodge > 0) s.append("+").append(SupremeGemMod.num(e.dodge)).append(Messages.get("gem_view.uniku"));
        if (e.critDamage > 0) s.append("+").append(SupremeGemMod.num(e.critDamage)).append(Messages.get("gem_view.obr_kryt"));
        if (e.lifeLeech > 0) s.append(SupremeGemMod.num(e.lifeLeech)).append(Messages.get("gem_view.kradziezy_zycia"));
        if (e.manaLeech > 0) s.append(SupremeGemMod.num(e.manaLeech)).append(Messages.get("gem_view.kradziezy_many"));
        for (WheelDomain d : WheelDomain.values()) {
            if (e.revelation(d) > 0) s.append("+").append(e.revelation(d)).append(Messages.get("gem_view.pkt_objawienia")).append(d.label().toLowerCase(java.util.Locale.ROOT)).append("), ");
        }
        for (java.util.Map.Entry<String, SpellAugment> sp : e.spells().entrySet()) {
            s.append(names == null ? sp.getKey() : names.apply(sp.getKey())).append(": ").append(sp.getValue().describe()).append(", ");
        }
        String text = s.toString().replaceAll(", $", "");
        WheelView.wrapped(rows, Messages.get("wheel_panel.klejnoty"), text);
        if (!parts.isEmpty()) WheelView.wrapped(rows, Messages.get("gem_view.odpornosci_z_klejnotow"), String.join(", ", parts));
    }
}
