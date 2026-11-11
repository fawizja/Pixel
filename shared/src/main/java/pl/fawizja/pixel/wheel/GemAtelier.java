package pl.fawizja.pixel.wheel;

import pl.fawizja.pixel.i18n.Messages;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import pl.fawizja.pixel.combat.DamageType;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Pracownia klejnotow (Gem Atelier) i Warsztat okruchow Kola Przeznaczenia - jak Canary PlayerWheel (revealGem,
 * destroyGem, switchGemDomain, toggleGemLock, setActiveGem, improveGemGrade, processActiveGems):
 * <ul>
 *   <li>odkrycie klejnotu swojej profesji za zloto ({@link GemQuality#revealCost}) losuje domene (4) i mody: mod 1
 *       z {@link BasicGemMod#SLOT1}, mod 2 (od zwyklego) z {@link BasicGemMod#SLOT2} inny niz 1, mod najwyzszy
 *       (wiekszy) z listy profesji {@link SupremeGemMod#of}; najwyzej {@link #MAX_GEMS} odkrytych klejnotow,</li>
 *   <li>blokada chroni przed rozbiciem i zmiana domeny; rozbicie daje okruchy, zmiana domeny (za zloto) przesuwa
 *       klejnot do nastepnej domeny (zielona - czerwona - fioletowa - niebieska),</li>
 *   <li>w kazdej domenie jedno naczynie: wlozony klejnot tej domeny dziala wg rezonansu naczynia, czyli liczby pelnych
 *       pol naczyn w domenie ({@link #resonance}): 1 - mod 1, 2 - mod 2 (od zwyklego), 3 - mod najwyzszy (wiekszy),</li>
 *   <li>stopnie modow I-IV sa wspolne dla wszystkich klejnotow postaci; podnosi je sie okruchami i zlotem
 *       ({@link #basicGradeCost}, {@link #supremeGradeCost}); kazdy mod na stopniu IV daje +1 punkt promocji.</li>
 * </ul>
 * Zloto i przedmioty pobiera {@code GameSession}; ta klasa trzyma stan, sprawdza zasady i liczy premie.
 * Zapis osobnym polem "gems" ({@link #encode}).
 */
public final class GemAtelier {
    /** Najwiecej odkrytych klejnotow na postac (TibiaWiki: 225). */
    public static final int MAX_GEMS = 225;
    public static final int MAX_GRADE = 3;
    public static final String LESSER_FRAGMENT_KEY = "lesser_gem_fragment";
    public static final String GREATER_FRAGMENT_KEY = "greater_gem_fragment";
    /** Koszt stopni II, III, IV modu podstawowego: {zloto, mniejsze okruchy} (Canary getLesserGradeCost). */
    private static final long[][] BASIC_COST = {{500_000, 5}, {1_250_000, 15}, {7_500_000, 30}};
    /** Koszt stopni II, III, IV modu najwyzszego: {zloto, wieksze okruchy} (Canary getGreaterGradeCost). */
    private static final long[][] SUPREME_COST = {{1_250_000, 5}, {3_000_000, 15}, {18_750_000, 30}};

    public enum Result {
        OK(""),
        NOT_ELIGIBLE("Klejnoty działają z Kołem Przeznaczenia: promocja, konto premium i poziom 51."),
        NO_GEM("Nie masz takiego klejnotu do odkrycia."),
        NO_GOLD("Za mało złota (plecak albo konto)."),
        FULL("Masz już {0} odkrytych klejnotów - rozbij któryś."),
        UNKNOWN("Nie ma takiego klejnotu."),
        LOCKED("Klejnot jest zablokowany - najpierw go odblokuj."),
        WRONG_DOMAIN("Klejnot pasuje tylko do naczynia swojej domeny."),
        NOT_SOCKETED("To naczynie jest puste."),
        MAX_GRADE("Ten modyfikator ma już najwyższy stopień (IV)."),
        WRONG_VOCATION("Ten modyfikator nie jest dla twojej profesji."),
        NO_FRAGMENTS("Za mało okruchów klejnotów."),
        NO_ROOM("Brak miejsca w plecaku na okruchy."),
        NOT_IN_TEMPLE("Klejnoty w naczyniach zmienisz tylko w świątyni (przy kapłanie).");

        private final String message;

        public String message() { return Messages.tr(message).replace("{0}", String.valueOf(MAX_GEMS)); }

        Result(String m) { message = m; }
    }

    private final List<WheelGem> gems = new ArrayList<>();
    /** Klejnot w naczyniu domeny (id albo 0). */
    private final int[] active = new int[4];
    private final int[] basicGrades = new int[BasicGemMod.values().length];
    private final int[] supremeGrades = new int[SupremeGemMod.values().length];
    private int nextId = 1;
    private int version;

    public int version() { return version; }

    public List<WheelGem> gems() { return Collections.unmodifiableList(gems); }

    public int count() { return gems.size(); }

    public WheelGem gem(int id) {
        for (WheelGem g : gems) if (g.id == id) return g;
        return null;
    }

    /** Klejnot w naczyniu domeny albo null. */
    public WheelGem socketed(WheelDomain d) {
        return active[d.ordinal()] == 0 ? null : gem(active[d.ordinal()]);
    }

    public boolean isSocketed(WheelGem g) { return g != null && active[g.domain.ordinal()] == g.id; }

    public int grade(BasicGemMod m) { return basicGrades[m.ordinal()]; }
    public int grade(SupremeGemMod m) { return supremeGrades[m.ordinal()]; }

    /** Mody na stopniu IV - kazdy daje +1 punkt promocji (Canary m_modsMaxGrade). */
    public int maxedMods() {
        int n = 0;
        for (int g : basicGrades) if (g >= MAX_GRADE) n++;
        for (int g : supremeGrades) if (g >= MAX_GRADE) n++;
        return n;
    }

    // ------------------------------------------------------------------ przedmioty

    /** Rdzen nazwy klejnotu profesji: straznik, strzelec, medrzec, mistyk, spirytualista (jak w Tibii guardian itd.). */
    public static String gemFamily(Vocation v) {
        switch (v) {
            case KNIGHT: return "guardian";
            case PALADIN: return "marksman";
            case SORCERER: return "sage";
            case DRUID: return "mystic";
            default: return "spiritualist";
        }
    }

    /** Klucz przedmiotu klejnotu: "lesser_guardian_gem", "guardian_gem", "greater_guardian_gem". */
    public static String gemKey(Vocation v, GemQuality q) {
        String base = gemFamily(v) + "_gem";
        switch (q) {
            case LESSER: return "lesser_" + base;
            case GREATER: return "greater_" + base;
            default: return base;
        }
    }

    /** Nazwa przedmiotu klejnotu po polsku: "Mniejszy klejnot strażnika". */
    public static String gemName(Vocation v, GemQuality q) {
        String who;
        switch (v) {
            case KNIGHT: who = Messages.get("gem_atelier.straznika"); break;
            case PALADIN: who = "strzelca"; break;
            case SORCERER: who = Messages.get("gem_atelier.medrca"); break;
            case DRUID: who = "mistyka"; break;
            default: who = "spirytualisty";
        }
        return q.title() + " " + who;
    }

    public static String fragmentKey(boolean greater) { return greater ? GREATER_FRAGMENT_KEY : LESSER_FRAGMENT_KEY; }

    // ------------------------------------------------------------------ Pracownia

    /** Odkrywa klejnot (przedmiot i zloto pobiera wolajacy po sprawdzeniu {@link #canReveal}). */
    public WheelGem reveal(GemQuality q, Vocation v, Random rng) {
        if (canReveal() != Result.OK) return null;
        WheelDomain d = WheelDomain.values()[rng.nextInt(4)];
        BasicGemMod b1 = BasicGemMod.SLOT1.get(rng.nextInt(BasicGemMod.SLOT1.size()));
        BasicGemMod b2 = null;
        if (q.basicMods() > 1) {
            do {
                b2 = BasicGemMod.SLOT2.get(rng.nextInt(BasicGemMod.SLOT2.size()));
            } while (b2 == b1);
        }
        SupremeGemMod s = null;
        if (q.hasSupreme()) {
            List<SupremeGemMod> list = SupremeGemMod.of(v);
            s = list.get(rng.nextInt(list.size()));
        }
        WheelGem g = new WheelGem(nextId++, q, d, b1, b2, s, false);
        gems.add(g);
        version++;
        return g;
    }

    public Result canReveal() { return gems.size() >= MAX_GEMS ? Result.FULL : Result.OK; }

    public Result toggleLock(int id) {
        WheelGem g = gem(id);
        if (g == null) return Result.UNKNOWN;
        g.locked = !g.locked;
        version++;
        return Result.OK;
    }

    /** Czy klejnot mozna rozbic albo zmienic mu domene. */
    public Result canChange(int id) {
        WheelGem g = gem(id);
        if (g == null) return Result.UNKNOWN;
        return g.locked ? Result.LOCKED : Result.OK;
    }

    /** Liczba okruchow z rozbicia (rozklad jak normal_random w Canary: srodek przedzialu najczestszy). */
    public static int rollFragments(GemQuality q, Random rng) {
        int span = q.fragMax - q.fragMin;
        return q.fragMin + (rng.nextInt(span + 1) + rng.nextInt(span + 1) + 1) / 2;
    }

    /** Usuwa klejnot (rozbicie); wyjmuje go z naczynia. */
    public Result destroy(int id) {
        Result r = canChange(id);
        if (r != Result.OK) return r;
        WheelGem g = gem(id);
        if (isSocketed(g)) active[g.domain.ordinal()] = 0;
        gems.remove(g);
        version++;
        return Result.OK;
    }

    /** Zmiana domeny na nastepna (Canary convertWheelGemAffinityToDomain); klejnot wypada z naczynia. */
    public Result switchDomain(int id) {
        Result r = canChange(id);
        if (r != Result.OK) return r;
        WheelGem g = gem(id);
        if (isSocketed(g)) active[g.domain.ordinal()] = 0;
        g.domain = WheelDomain.values()[(g.domain.ordinal() + 1) % 4];
        version++;
        return Result.OK;
    }

    /** Wklada klejnot do naczynia jego domeny (poprzedni wraca do pracowni). */
    public Result socket(int id) {
        WheelGem g = gem(id);
        if (g == null) return Result.UNKNOWN;
        active[g.domain.ordinal()] = g.id;
        version++;
        return Result.OK;
    }

    public Result unsocket(WheelDomain d) {
        if (active[d.ordinal()] == 0) return Result.NOT_SOCKETED;
        active[d.ordinal()] = 0;
        version++;
        return Result.OK;
    }

    // ------------------------------------------------------------------ Warsztat okruchow (stopnie)

    /** Koszt nastepnego stopnia modu podstawowego {zloto, mniejsze okruchy} albo null przy stopniu IV. */
    public long[] basicGradeCost(BasicGemMod m) {
        int g = grade(m);
        return g >= MAX_GRADE ? null : BASIC_COST[g].clone();
    }

    /** Koszt nastepnego stopnia modu najwyzszego {zloto, wieksze okruchy} albo null przy stopniu IV. */
    public long[] supremeGradeCost(SupremeGemMod m) {
        int g = grade(m);
        return g >= MAX_GRADE ? null : SUPREME_COST[g].clone();
    }

    public Result gradeUp(BasicGemMod m) {
        if (grade(m) >= MAX_GRADE) return Result.MAX_GRADE;
        basicGrades[m.ordinal()]++;
        version++;
        return Result.OK;
    }

    public Result gradeUp(SupremeGemMod m, Vocation v) {
        if (!m.allowed(v)) return Result.WRONG_VOCATION;
        if (grade(m) >= MAX_GRADE) return Result.MAX_GRADE;
        supremeGrades[m.ordinal()]++;
        version++;
        return Result.OK;
    }

    /** Stopien rzymski: 0 -> "I", 3 -> "IV". */
    public static String roman(int grade) {
        return new String[] {"I", "II", "III", "IV"}[Math.max(0, Math.min(3, grade))];
    }

    // ------------------------------------------------------------------ premie

    /** Rezonans naczynia domeny: liczba pelnych pol naczyn (0..3) - Canary IOWheel::addVesselResonance. */
    public static int resonance(int[] points, WheelDomain d) {
        int n = 0;
        for (WheelSlice s : WheelSlice.of(d)) {
            if (s.conviction == WheelSlice.Conv.VESSEL && points[s.ordinal()] >= s.capacity()) n++;
        }
        return n;
    }

    /** Ile modow klejnotu dziala przy rezonansie: 1 - mod 1, 2 - mod 2, 3 - mod najwyzszy (o ile jakosc je ma). */
    public static int activeMods(WheelGem g, int resonance) {
        if (g == null) return 0;
        int n = Math.min(resonance, 1);
        if (resonance >= 2 && g.basic2 != null) n++;
        if (resonance >= 3 && g.supreme != null) n++;
        return n;
    }

    /** Premie z klejnotow w naczyniach (liczone raz przy zmianie kola albo klejnotow; nie zmieniac z zewnatrz). */
    public static final class Effects {
        public static final Effects NONE = new Effects();

        public int health;
        public int mana;
        public int capacity;
        /** Mitygacja w procentach wzglednych (jak {@link WheelBonus#mitigation}). */
        public double mitigation;
        /** Odpornosci w procentach (ujemne = slabosc). */
        final double[] resist = new double[DamageType.values().length];
        public double dodge;
        public double critDamage;
        public double lifeLeech;
        public double manaLeech;
        /** Punkty Mistrzostwa objawienia na domene (dodawane przy liczeniu etapu). */
        final int[] revelation = new int[4];
        final Map<String, SpellAugment> spells = new HashMap<>();

        public double resist(DamageType t) { return resist[t.ordinal()]; }
        public int revelation(WheelDomain d) { return revelation[d.ordinal()]; }
        public SpellAugment spell(String key) { return spells.getOrDefault(key, SpellAugment.NONE); }
        public Map<String, SpellAugment> spells() { return Collections.unmodifiableMap(spells); }
    }

    /** Premie dzialajacych modow dla profesji i ulozenia punktow kola. */
    public Effects effects(Vocation v, int[] points) {
        Effects e = new Effects();
        for (WheelDomain d : WheelDomain.values()) {
            WheelGem g = socketed(d);
            if (g == null) continue;
            int n = activeMods(g, resonance(points, d));
            if (n >= 1) applyBasic(e, g.basic1, v);
            if (n >= 2 && g.basic2 != null) applyBasic(e, g.basic2, v);
            if (g.supreme != null && resonance(points, d) >= 3 && g.supreme.allowed(v)) applySupreme(e, g.supreme);
        }
        return e;
    }

    private void applyBasic(Effects e, BasicGemMod m, Vocation v) {
        int grade = grade(m);
        for (BasicGemMod.Part p : m.parts()) {
            double val = BasicGemMod.value(p, v, grade);
            switch (p.kind()) {
                case HEALTH: e.health += (int) Math.round(val); break;
                case MANA: e.mana += (int) Math.round(val); break;
                case CAPACITY: e.capacity += (int) Math.round(val); break;
                case MITIGATION: e.mitigation += val / 100.0; break;
                default: e.resist[p.type().ordinal()] += val / 100.0;
            }
        }
    }

    private void applySupreme(Effects e, SupremeGemMod m) {
        double val = m.value(grade(m));
        switch (m.kind) {
            case DODGE: e.dodge += val; break;
            case CRIT_DAMAGE: e.critDamage += val; break;
            case LIFE_LEECH: e.lifeLeech += val; break;
            case MANA_LEECH: e.manaLeech += val; break;
            case REVELATION: e.revelation[m.domain.ordinal()] += (int) Math.round(val); break;
            default: e.spells.merge(m.spell, m.augment(grade(m)), SpellAugment::plus);
        }
    }

    // ------------------------------------------------------------------ zapis

    /**
     * Tekst pola "gems": "n=13;g=1,L,G,FIRE_RES,-,-,0/2,...;a=1,0,0,2;bg=FIRE_RES:2,...;sg=DODGE:1". Pusta pracownia
     * bez stopni = "".
     */
    public String encode() {
        StringBuilder b = new StringBuilder();
        if (gems.isEmpty() && maxedMods() == 0 && allZero()) return "";
        b.append("n=").append(nextId);
        if (!gems.isEmpty()) {
            b.append(";g=");
            for (int i = 0; i < gems.size(); i++) {
                if (i > 0) b.append('/');
                b.append(gems.get(i).encode());
            }
        }
        b.append(";a=").append(active[0]).append(',').append(active[1]).append(',').append(active[2]).append(',').append(active[3]);
        StringBuilder bg = new StringBuilder();
        for (BasicGemMod m : BasicGemMod.values()) {
            if (basicGrades[m.ordinal()] > 0) bg.append(bg.length() > 0 ? "," : "").append(m.name()).append(':').append(basicGrades[m.ordinal()]);
        }
        if (bg.length() > 0) b.append(";bg=").append(bg);
        StringBuilder sg = new StringBuilder();
        for (SupremeGemMod m : SupremeGemMod.values()) {
            if (supremeGrades[m.ordinal()] > 0) sg.append(sg.length() > 0 ? "," : "").append(m.name()).append(':').append(supremeGrades[m.ordinal()]);
        }
        if (sg.length() > 0) b.append(";sg=").append(sg);
        return b.toString();
    }

    private boolean allZero() {
        for (int g : basicGrades) if (g != 0) return false;
        for (int g : supremeGrades) if (g != 0) return false;
        for (int a : active) if (a != 0) return false;
        return true;
    }

    /** Odczyt; null albo "" = pusta pracownia. Bledne wpisy sa pomijane. */
    public void decode(String text) {
        gems.clear();
        Arrays.fill(active, 0);
        Arrays.fill(basicGrades, 0);
        Arrays.fill(supremeGrades, 0);
        nextId = 1;
        version++;
        if (text == null || text.isEmpty()) return;
        for (String part : text.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String k = part.substring(0, eq);
            String v = part.substring(eq + 1);
            try {
                switch (k) {
                    case "n": nextId = Math.max(1, Integer.parseInt(v)); break;
                    case "g":
                        for (String gs : v.split("/")) {
                            WheelGem g = WheelGem.decode(gs);
                            if (g != null && gem(g.id) == null && gems.size() < MAX_GEMS) gems.add(g);
                        }
                        break;
                    case "a": {
                        String[] f = v.split(",");
                        for (int i = 0; i < 4 && i < f.length; i++) active[i] = Integer.parseInt(f[i].trim());
                        break;
                    }
                    case "bg": grades(v, true); break;
                    case "sg": grades(v, false); break;
                    default:
                }
            } catch (NumberFormatException e) {
                // pomijamy uszkodzony wpis
            }
        }
        for (WheelGem g : gems) nextId = Math.max(nextId, g.id + 1);
        // naczynie wskazuje tylko istniejacy klejnot swojej domeny
        for (WheelDomain d : WheelDomain.values()) {
            WheelGem g = active[d.ordinal()] == 0 ? null : gem(active[d.ordinal()]);
            if (g == null || g.domain != d) active[d.ordinal()] = 0;
        }
    }

    private void grades(String v, boolean basic) {
        for (String e : v.split(",")) {
            int c = e.indexOf(':');
            if (c <= 0) continue;
            int g = Math.max(0, Math.min(MAX_GRADE, Integer.parseInt(e.substring(c + 1).trim())));
            if (basic) {
                BasicGemMod m = BasicGemMod.parse(e.substring(0, c));
                if (m != null) basicGrades[m.ordinal()] = g;
            } else {
                SupremeGemMod m = SupremeGemMod.parse(e.substring(0, c));
                if (m != null) supremeGrades[m.ordinal()] = g;
            }
        }
    }

    /** Klient online: stan z serwera (SELF). */
    public void copyFrom(GemAtelier o) {
        gems.clear();
        for (WheelGem g : o.gems) gems.add(g.copy());
        System.arraycopy(o.active, 0, active, 0, active.length);
        System.arraycopy(o.basicGrades, 0, basicGrades, 0, basicGrades.length);
        System.arraycopy(o.supremeGrades, 0, supremeGrades, 0, supremeGrades.length);
        nextId = o.nextId;
        version++;
    }

    /** Klejnoty domeny (do wyboru do naczynia), najpierw wlozony, potem od najlepszej jakosci. */
    public List<WheelGem> ofDomain(WheelDomain d) {
        List<WheelGem> out = new ArrayList<>();
        for (WheelGem g : gems) if (g.domain == d) out.add(g);
        out.sort((a, b) -> {
            if (isSocketed(a) != isSocketed(b)) return isSocketed(a) ? -1 : 1;
            if (a.quality != b.quality) return b.quality.ordinal() - a.quality.ordinal();
            return a.id - b.id;
        });
        return out;
    }

    /** Liczba odkrytych klejnotow wg domeny. */
    public Map<WheelDomain, Integer> countByDomain() {
        Map<WheelDomain, Integer> out = new EnumMap<>(WheelDomain.class);
        for (WheelDomain d : WheelDomain.values()) out.put(d, 0);
        for (WheelGem g : gems) out.merge(g.domain, 1, Integer::sum);
        return out;
    }
}
