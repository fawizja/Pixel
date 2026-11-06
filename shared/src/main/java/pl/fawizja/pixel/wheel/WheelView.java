package pl.fawizja.pixel.wheel;

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

    public static String quadrantTitle(WheelDomain d) { return TITLE + ": " + d.label.toLowerCase(java.util.Locale.ROOT) + " ćwiartka"; }

    public static String sliceTitle(WheelSlice s) { return TITLE + ": " + s.label(); }

    /** Postep objawienia domeny, np. "etap 1 (312/500)" albo "312/250 do etapu 1". */
    public static String revelationProgress(int points) {
        int st = WheelDomain.stage(points);
        int next = WheelDomain.nextStagePoints(points);
        if (st == 0) return points + "/" + next + " do 1. etapu objawienia";
        if (next <= 0) return "objawienie: etap 3 (" + points + " pkt, maks.)";
        return "objawienie: etap " + st + " (" + points + "/" + next + " do etapu " + (st + 1) + ")";
    }

    private static String head(PlayerCharacter pc) {
        int av = pc.wheelPoints();
        int used = pc.wheel().spent();
        return String.format("%,d użyte z %,d, wolne: %,d", used, av, Math.max(0, av - used));
    }

    /** Okno glowne: punkty, miejsce, 4 cwiartki (kafle), premie, zestawy, reset. */
    public static List<String[]> overview(PlayerCharacter pc, boolean inTemple) {
        List<String[]> rows = new ArrayList<>();
        boolean eligible = pc.wheelPoints() > 0;
        if (!eligible) {
            rows.add(new String[] {"Niedostępne", "wymaga promocji, konta premium i poziomu " + DestinyWheel.MIN_LEVEL
                    + " (1 punkt za każdy poziom od " + DestinyWheel.MIN_LEVEL + ")", "", "ach:none"});
        } else {
            rows.add(new String[] {"Punkty promocji", head(pc), "", "ach:none"});
        }
        rows.add(new String[] {"Świątynia", inTemple ? "jesteś w świątyni - możesz przestawiać punkty"
                : "punkty przestawisz tylko w świątyni, przy kapłanie", "", "ach:none"});
        Vocation v = pc.vocation();
        for (WheelDomain d : WheelDomain.values()) {
            int pts = pc.wheel().domainPoints(d);
            WheelRevelation r = WheelRevelation.of(d, v);
            rows.add(new String[] {d.label + " ćwiartka: " + pts + " pkt", r.label + " - " + revelationProgress(pts), "", "wheel:q:" + d.code});
        }
        String av = avatarLine(pc);
        if (av != null) rows.add(new String[] {"Awatar", av, "", "ach:none"});
        rows.add(new String[] {"Aktywne premie", "oddanie, przekonanie i objawienia", "", "wheel:perks"});
        rows.add(new String[] {"Zestawy", DestinyWheel.PRESETS + " zapisane ustawienia koła", "", "wheel:presets"});
        rows.add(new String[] {"Wyczyść koło", "wszystkie punkty wracają do puli (bez opłaty)", "", "wheel:resetask",
                pc.wheel().isEmpty() ? "off" : ""});
        return rows;
    }

    /** Stan awatara (aktywny / odnowienie) albo null. */
    public static String avatarLine(PlayerCharacter pc) {
        DestinyWheel w = pc.wheel();
        if (w.avatarActive()) return "aktywny jeszcze " + secs(w.avatarMs()) + " (etap " + w.avatarStage() + ")";
        if (w.avatarCooldownMs() > 0) return "odnowienie: " + minutes(w.avatarCooldownMs());
        return null;
    }

    /** Cwiartka: 9 pol od srodka na zewnatrz i objawienie domeny. */
    public static List<String[]> quadrant(PlayerCharacter pc, WheelDomain d) {
        List<String[]> rows = new ArrayList<>();
        Vocation v = pc.vocation();
        int av = pc.wheelPoints();
        DestinyWheel w = pc.wheel();
        WheelRevelation r = WheelRevelation.of(d, v);
        int pts = w.domainPoints(d);
        int st = WheelDomain.stage(pts);
        rows.add(new String[] {"Objawienie: " + r.label, revelationProgress(pts), "", "ach:none"});
        wrapped(rows, st > 0 ? "Etap " + st : "Etap 1 da", r.stage(Math.max(1, st)));
        for (WheelSlice s : WheelSlice.of(d)) {
            int p = w.points(s);
            String state;
            if (p >= s.capacity()) state = "pełne";
            else if (w.open(s, av)) state = "otwarte";
            else if (av < s.pos.threshold) state = "od " + s.pos.threshold + " pkt promocji";
            else state = "zamknięte";
            rows.add(new String[] {cap(s.pos.label) + ": " + p + "/" + s.capacity(), shortConviction(s, v), state, "wheel:sel:" + s.code()});
        }
        rows.add(new String[] {"Wróć", TITLE, "", OPEN});
        return rows;
    }

    /** Karta pola: oddanie, przekonanie, przyciski +1/+10/do pelna, -1/-10/oproznij. */
    public static List<String[]> slice(PlayerCharacter pc, WheelSlice s, boolean inTemple, Function<String, String> names) {
        List<String[]> rows = new ArrayList<>();
        Vocation v = pc.vocation();
        int av = pc.wheelPoints();
        DestinyWheel w = pc.wheel();
        int p = w.points(s);
        int free = Math.max(0, av - w.spent());
        rows.add(new String[] {"Punkty w polu", p + "/" + s.capacity() + ", wolne: " + free, "", "ach:none"});
        rows.add(new String[] {"Oddanie", WheelPerks.dedication(s, v) + " (teraz " + dedicationNow(s, v, p) + ")", "", "ach:none"});
        wrapped(rows, p >= s.capacity() ? "Przekonanie (aktywne)" : "Przekonanie (gdy pole pełne)", WheelPerks.conviction(s, v));
        int g = s.conviction.spellGroup();
        if (g >= 0) {
            WheelPerks.Upgrade u = WheelPerks.upgrade(v, g);
            int grade = pc.wheelBonus().grade(g);
            String note = u.note.isEmpty() ? "" : " (" + u.note + ")";
            rows.add(new String[] {"Czar: " + spellNames(u, names) + (grade > 0 ? " (stopień " + grade + ")" : ""),
                    u.tibia + " w Tibii", "", "ach:none"});
            wrapped(rows, "Stopnie", "1: " + u.grade1.describe() + "; 2: dodatkowo " + u.grade2.describe() + note);
        }
        if (s.pos.threshold > 0) {
            rows.add(new String[] {"Wymaga", "pełne pole bliżej środka i " + s.pos.threshold + " pkt promocji",
                    w.open(s, av) || p > 0 ? "spełnione" : "niespełnione", "ach:none"});
        }
        boolean canAdd = inTemple && av > 0 && p < s.capacity() && free > 0 && w.open(s, av);
        boolean canRemove = inTemple && p > 0;
        String c = s.code();
        int toFull = Math.min(free, s.capacity() - p);
        rows.add(new String[] {"Dodaj 1", "", "", "wheel:add:" + c + ":1", canAdd ? "" : "off"});
        rows.add(new String[] {"Dodaj 10", "", "", "wheel:add:" + c + ":10", canAdd ? "" : "off"});
        rows.add(new String[] {"Dodaj do pełna", "+" + Math.max(0, toFull), "", "wheel:add:" + c + ":" + Math.max(1, toFull), canAdd ? "" : "off"});
        rows.add(new String[] {"Odejmij 1", "", "", "wheel:remove:" + c + ":1", canRemove ? "" : "off"});
        rows.add(new String[] {"Odejmij 10", "", "", "wheel:remove:" + c + ":10", canRemove ? "" : "off"});
        rows.add(new String[] {"Opróżnij pole", "", "", "wheel:remove:" + c + ":" + Math.max(1, p), canRemove ? "" : "off"});
        rows.add(new String[] {"Wróć", s.domain.label + " ćwiartka", "", "wheel:q:" + s.domain.code});
        return rows;
    }

    /** Przekonanie w wierszu cwiartki: przy pasywce sama nazwa (opis w karcie pola). */
    static String shortConviction(WheelSlice s, Vocation v) {
        String c = WheelPerks.conviction(s, v);
        int colon = c.indexOf(':');
        return s.conviction == WheelSlice.Conv.PASSIVE_A || s.conviction == WheelSlice.Conv.PASSIVE_B ? c.substring(0, colon) : c;
    }

    private static String dedicationNow(WheelSlice s, Vocation v, int p) {
        switch (s.stat) {
            case HP: return "+" + p * WheelPerks.hpPerPoint(v) + " HP";
            case MANA: return "+" + p * WheelPerks.manaPerPoint(v) + " many";
            case HP_MANA: return "+" + p * WheelPerks.hpPerPoint(v) + " HP, +" + p * WheelPerks.manaPerPoint(v) + " many";
            case CAP: return "+" + p * WheelPerks.capPerPoint(v) + " udźwigu";
            default: return "+" + SpellAugment.num(p * WheelPerks.MITIGATION_PER_POINT) + "% mitygacji";
        }
    }

    /** Aktywne premie: sumy oddania, przekonania, ulepszenia czarow, pasywki i objawienia. */
    public static List<String[]> perks(PlayerCharacter pc, Function<String, String> names) {
        List<String[]> rows = new ArrayList<>();
        WheelBonus b = pc.wheelBonus();
        Vocation v = pc.vocation();
        if (b == WheelBonus.NONE) {
            rows.add(new String[] {"Brak premii", pc.wheelPoints() > 0 ? "rozdaj punkty w świątyni" : "koło nieaktywne", "", "ach:none"});
        } else {
            rows.add(new String[] {"Oddanie", String.format("+%d HP, +%d many, +%d udźwigu, +%s%% mitygacji", b.health, b.mana, b.capacity,
                    SpellAugment.num(b.mitigation)), "", "ach:none"});
            StringBuilder conv = new StringBuilder();
            if (b.skill > 0) conv.append("+").append(b.skill).append(" ").append(WheelPerks.skillLabel(v));
            if (b.lifeLeech > 0) append(conv, "+" + SpellAugment.num(b.lifeLeech) + "% kradzieży życia");
            if (b.manaLeech > 0) append(conv, "+" + SpellAugment.num(b.manaLeech) + "% kradzieży many");
            rows.add(new String[] {"Przekonanie", conv.length() == 0 ? "brak pełnych pól" : conv.toString(), "", "ach:none"});
            for (int g = 0; g < 5; g++) {
                int grade = b.grade(g);
                if (grade <= 0) continue;
                WheelPerks.Upgrade u = WheelPerks.upgrade(v, g);
                wrapped(rows, spellNames(u, names) + " (st. " + grade + ")", u.at(grade).describe());
            }
            for (WheelPassive p : b.passives()) wrapped(rows, p.label, p.description);
            for (WheelDomain d : WheelDomain.values()) {
                int st = b.stage(d);
                if (st <= 0) continue;
                WheelRevelation r = WheelRevelation.of(d, v);
                wrapped(rows, r.label + " (etap " + st + ")", r.stage(st));
            }
            if (b.flatDamage > 0) {
                rows.add(new String[] {"Moc objawień", "+" + b.flatDamage + " obrażeń i +" + b.flatHealing + " leczenia", "", "ach:none"});
            }
            if (pc.wheel().giftCooldownMs() > 0) {
                rows.add(new String[] {"Dar Życia", "odnowienie: " + minutes(pc.wheel().giftCooldownMs()), "", "ach:none"});
            }
            String av = avatarLine(pc);
            if (av != null) rows.add(new String[] {"Awatar teraz", av, "", "ach:none"});
        }
        rows.add(new String[] {"Wróć", TITLE, "", OPEN});
        return rows;
    }

    /** Zestawy: zapis wszedzie, wczytanie w swiatyni. */
    public static List<String[]> presets(PlayerCharacter pc, boolean inTemple) {
        List<String[]> rows = new ArrayList<>();
        DestinyWheel w = pc.wheel();
        int av = pc.wheelPoints();
        for (int i = 0; i < DestinyWheel.PRESETS; i++) {
            boolean has = w.hasPreset(i);
            rows.add(new String[] {"Zapisz w zestawie " + (i + 1), has ? "nadpisze (" + w.presetPoints(i) + " pkt)" : "pusty",
                    "", "wheel:save:" + i, av > 0 ? "" : "off"});
            rows.add(new String[] {"Wczytaj zestaw " + (i + 1), has ? w.presetPoints(i) + " pkt" : "pusty", "",
                    "wheel:load:" + i, has && inTemple && w.presetPoints(i) <= av ? "" : "off"});
        }
        rows.add(new String[] {"Wróć", TITLE, "", OPEN});
        return rows;
    }

    public static List<String[]> resetConfirm(PlayerCharacter pc, boolean inTemple) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Na pewno?", "zwrot " + pc.wheel().spent() + " pkt; zestawy zostają", "", "ach:none"});
        rows.add(new String[] {"Tak, wyczyść koło", inTemple ? "" : "tylko w świątyni", "", "wheel:reset", inTemple ? "" : "off"});
        rows.add(new String[] {"Wróć", TITLE, "", OPEN});
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
        return m >= 60 ? m / 60 + " h " + m % 60 + " min" : m + " min";
    }
}
