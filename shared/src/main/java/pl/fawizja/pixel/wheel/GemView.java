package pl.fawizja.pixel.wheel;

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

    public static String atelierTitle() { return WheelView.TITLE + ": Pracownia klejnotów"; }
    public static String vesselTitle(WheelDomain d) { return WheelView.TITLE + ": naczynie (" + d.label.toLowerCase(java.util.Locale.ROOT) + ")"; }
    public static String fragTitle() { return WheelView.TITLE + ": Warsztat okruchów"; }

    private static String gold(long g) { return String.format("%,d gp", g).replace(',', ' ').replace(' ', ' '); }

    // ------------------------------------------------------------------ wiersze w oknach kola

    /** Wiersze przegladu kola: Pracownia i Warsztat. */
    static void overviewRows(List<String[]> rows, PlayerCharacter pc, ToIntFunction<String> have) {
        GemAtelier a = pc.wheel().gems();
        int inVessels = 0;
        for (WheelDomain d : WheelDomain.values()) if (a.socketed(d) != null) inVessels++;
        int unrevealed = 0;
        for (GemQuality q : GemQuality.values()) unrevealed += have.applyAsInt(GemAtelier.gemKey(pc.vocation(), q));
        rows.add(new String[] {"Klejnoty i naczynia", a.count() + " odkrytych, " + inVessels + "/4 w naczyniach"
                + (unrevealed > 0 ? ", do odkrycia: " + unrevealed : ""), "", "wheel:gems:0"});
    }

    /** Wiersz naczynia w oknie cwiartki. */
    static String[] vesselRow(PlayerCharacter pc, WheelDomain d) {
        WheelGem g = pc.wheel().gems().socketed(d);
        int res = pc.wheel().resonance(d);
        String what = g == null ? "puste" : g.quality.title + " #" + g.id + ", działa " + GemAtelier.activeMods(g, res) + "/" + (g.quality.basicMods()
                + (g.supreme != null ? 1 : 0)) + " mod.";
        return new String[] {"Naczynie klejnotu", what + " (rezonans " + res + "/3)", "", "wheel:vessel:" + d.code};
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
            rows.add(new String[] {"Odkryj: " + GemAtelier.gemName(v, q) + " (" + n + ")", gold(q.revealCost), "",
                    "wheel:gem:reveal:" + q.code, n > 0 && eligible && a.count() < GemAtelier.MAX_GEMS ? "" : "off"});
        }
        List<WheelGem> list = new ArrayList<>(a.gems());
        java.util.Collections.reverse(list);
        int pages = Math.max(1, (list.size() + PAGE - 1) / PAGE);
        int p = Math.max(0, Math.min(page, pages - 1));
        rows.add(new String[] {"Odkryte klejnoty: " + a.count() + "/" + GemAtelier.MAX_GEMS, list.isEmpty()
                ? "klejnoty wypadają z potworów pod wpływem, diabolicznych i bossów" : "strona " + (p + 1) + "/" + pages, "", "ach:none"});
        for (int i = p * PAGE; i < Math.min(list.size(), (p + 1) * PAGE); i++) {
            WheelGem g = list.get(i);
            String state = a.isSocketed(g) ? "w naczyniu" : g.locked() ? "zablokowany" : "";
            rows.add(new String[] {g.title(), modsShort(g, names), state, "wheel:card:" + g.id});
        }
        if (pages > 1) {
            rows.add(new String[] {"Następna strona", "", "", "wheel:gems:" + (p + 1 < pages ? p + 1 : 0)});
        }
        rows.add(new String[] {"Warsztat okruchów", "stopnie modów: " + have.applyAsInt(GemAtelier.LESSER_FRAGMENT_KEY) + " mn. / "
                + have.applyAsInt(GemAtelier.GREATER_FRAGMENT_KEY) + " wk. okruchów", "", "wheel:frag:b:0"});
        rows.add(new String[] {"Wróć", WheelView.TITLE, "", WheelView.OPEN});
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
            rows.add(new String[] {"Brak klejnotu", "rozbity albo nieznany", "", "ach:none"});
            rows.add(new String[] {"Wróć", "Pracownia klejnotów", "", "wheel:gems:0"});
            return rows;
        }
        Vocation v = pc.vocation();
        int res = pc.wheel().resonance(g.domain);
        boolean in = a.isSocketed(g);
        rows.add(new String[] {"Jakość i domena", g.quality.label + ", " + g.domain.label.toLowerCase(java.util.Locale.ROOT) + " domena", "", "ach:none"});
        modRow(rows, "Mod 1 (" + GemAtelier.roman(a.grade(g.basic1)) + ")", g.basic1.describe(v, a.grade(g.basic1)), in, res >= 1, 1);
        if (g.basic2 != null) modRow(rows, "Mod 2 (" + GemAtelier.roman(a.grade(g.basic2)) + ")", g.basic2.describe(v, a.grade(g.basic2)), in, res >= 2, 2);
        if (g.supreme != null) {
            modRow(rows, "Najwyższy (" + GemAtelier.roman(a.grade(g.supreme)) + ")", g.supreme.describe(a.grade(g.supreme), names), in, res >= 3, 3);
        }
        WheelGem cur = a.socketed(g.domain);
        rows.add(new String[] {in ? "W naczyniu" : "Włóż do naczynia", in ? "rezonans " + res + "/3" : cur == null ? "naczynie puste"
                : "zamiast " + cur.title(), inTemple ? "" : "tylko w świątyni", in ? "ach:none" : "wheel:gem:socket:" + g.id,
                !in && inTemple ? "" : "off"});
        rows.add(new String[] {g.locked() ? "Odblokuj" : "Zablokuj", "blokada chroni przed rozbiciem i zmianą domeny", "", "wheel:gem:lock:" + g.id});
        WheelDomain next = WheelDomain.values()[(g.domain.ordinal() + 1) % 4];
        rows.add(new String[] {"Zmień domenę: " + next.label.toLowerCase(java.util.Locale.ROOT), gold(g.quality.switchCost), "",
                "wheel:gem:switch:" + g.id, g.locked() ? "off" : ""});
        rows.add(new String[] {"Rozbij na okruchy", g.quality.fragMin + "-" + g.quality.fragMax + (g.quality.greaterFragments ? " większych" : " mniejszych"),
                "", "wheel:gemask:" + g.id, g.locked() ? "off" : ""});
        rows.add(new String[] {"Wróć", "Pracownia klejnotów", "", "wheel:gems:0"});
        return rows;
    }

    private static void modRow(List<String[]> rows, String label, String detail, boolean in, boolean active, int need) {
        String state = !in ? "" : active ? "działa" : "rezonans " + need + "+";
        rows.add(new String[] {label, detail, state, "ach:none"});
    }

    public static List<String[]> destroyConfirm(PlayerCharacter pc, int id) {
        List<String[]> rows = new ArrayList<>();
        WheelGem g = pc.wheel().gems().gem(id);
        rows.add(new String[] {"Na pewno?", g == null ? "brak klejnotu" : g.title() + " przepadnie", "", "ach:none"});
        rows.add(new String[] {"Tak, rozbij", g == null ? "" : g.quality.fragMin + "-" + g.quality.fragMax
                + (g.quality.greaterFragments ? " większych" : " mniejszych") + " okruchów", "", "wheel:gem:destroy:" + id,
                g == null || g.locked() ? "off" : ""});
        rows.add(new String[] {"Wróć", "karta klejnotu", "", "wheel:card:" + id});
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
            full.append(s.pos.label).append(pc.wheel().points(s) >= s.capacity() ? " (pełne)" : " (" + pc.wheel().points(s) + "/" + s.capacity() + ")");
        }
        wrapped(rows, "Rezonans " + res + "/3", "pola naczyń: " + full);
        WheelGem g = a.socketed(d);
        if (g == null) {
            rows.add(new String[] {"W naczyniu", "pusto - włóż klejnot tej domeny", "", "ach:none"});
        } else {
            rows.add(new String[] {"W naczyniu", g.title(), "", "wheel:card:" + g.id});
            Vocation v = pc.vocation();
            modRow(rows, "Mod 1", g.basic1.describe(v, a.grade(g.basic1)), true, res >= 1, 1);
            if (g.basic2 != null) modRow(rows, "Mod 2", g.basic2.describe(v, a.grade(g.basic2)), true, res >= 2, 2);
            if (g.supreme != null) modRow(rows, "Najwyższy", g.supreme.describe(a.grade(g.supreme), names), true, res >= 3, 3);
            rows.add(new String[] {"Wyjmij klejnot", inTemple ? "" : "tylko w świątyni", "", "wheel:gem:unsocket:" + d.code, inTemple ? "" : "off"});
        }
        int shown = 0;
        for (WheelGem o : a.ofDomain(d)) {
            if (a.isSocketed(o)) continue;
            if (shown++ >= 5) break;
            rows.add(new String[] {"Włóż: " + o.title(), modsShort(o, names), "", "wheel:gem:socket:" + o.id, inTemple ? "" : "off"});
        }
        if (shown == 0 && g == null) rows.add(new String[] {"Brak klejnotów tej domeny", "odkryj klejnoty w Pracowni", "", "wheel:gems:0"});
        rows.add(new String[] {"Wróć", d.label + " ćwiartka", "", "wheel:q:" + d.code});
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
        rows.add(new String[] {"Okruchy", lesser + " mniejszych, " + greater + " większych; stopień IV: +1 pkt promocji (" + a.maxedMods() + ")", "", "ach:none"});
        rows.add(new String[] {supreme ? "Pokaż mody podstawowe" : "Pokaż mody najwyższe", supreme ? "mniejsze okruchy" : "większe okruchy", "",
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
            rows.add(new String[] {"Następna strona", (p + 1) + "/" + pages, "", "wheel:frag:" + (supreme ? "s" : "b") + ":" + (p + 1 < pages ? p + 1 : 0)});
        }
        rows.add(new String[] {"Wróć", WheelView.TITLE, "", WheelView.OPEN});
        return rows;
    }

    private static String[] gradeRow(String label, int grade, long[] cost, int frags, boolean eligible, String action) {
        if (cost == null) return new String[] {label + " (IV)", "najwyższy stopień", "", "ach:none"};
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
            rows.add(new String[] {"Klejnoty", "brak działających modyfikatorów", "", "ach:none"});
            return;
        }
        StringBuilder s = new StringBuilder(active + " mod.: ");
        if (e.health > 0) s.append("+").append(e.health).append(" HP, ");
        if (e.mana > 0) s.append("+").append(e.mana).append(" many, ");
        if (e.capacity > 0) s.append("+").append(e.capacity).append(" udźwigu, ");
        if (e.mitigation > 0) s.append("+").append(SupremeGemMod.num(e.mitigation)).append("% mitygacji, ");
        if (e.dodge > 0) s.append("+").append(SupremeGemMod.num(e.dodge)).append("% uniku, ");
        if (e.critDamage > 0) s.append("+").append(SupremeGemMod.num(e.critDamage)).append("% obr. kryt., ");
        if (e.lifeLeech > 0) s.append(SupremeGemMod.num(e.lifeLeech)).append("% kradzieży życia, ");
        if (e.manaLeech > 0) s.append(SupremeGemMod.num(e.manaLeech)).append("% kradzieży many, ");
        for (WheelDomain d : WheelDomain.values()) {
            if (e.revelation(d) > 0) s.append("+").append(e.revelation(d)).append(" pkt objawienia (").append(d.label.toLowerCase(java.util.Locale.ROOT)).append("), ");
        }
        for (java.util.Map.Entry<String, SpellAugment> sp : e.spells().entrySet()) {
            s.append(names == null ? sp.getKey() : names.apply(sp.getKey())).append(": ").append(sp.getValue().describe()).append(", ");
        }
        String text = s.toString().replaceAll(", $", "");
        WheelView.wrapped(rows, "Klejnoty", text);
        if (!parts.isEmpty()) WheelView.wrapped(rows, "Odporności z klejnotów", String.join(", ", parts));
    }
}
