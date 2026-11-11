package pl.fawizja.pixel.charm;

import pl.fawizja.pixel.i18n.Messages;
import java.util.ArrayList;
import java.util.List;
import pl.fawizja.pixel.achievement.Bestiary;
import pl.fawizja.pixel.achievement.BestiaryData;
import pl.fawizja.pixel.achievement.BestiaryEntry;

/**
 * Charmy postaci jak w Tibii (Canary IOBestiary::sendBuyCharmRune, Player charmsArray):
 * <ul>
 *   <li>punkty charmow przybywaja za kazdy ukonczony wpis bestiariusza; za nie kupuje sie i ulepsza charmy Major,</li>
 *   <li>kazdy kupiony stopien Major daje echa charmow: 25t^2 + 25t + 50 (50 / 100 / 200 za stopnie I / II / III);
 *       za echa kupuje sie charmy Minor (100 / 150 / 225),</li>
 *   <li>na jednego potwora 1 Major i 1 Minor; Major tylko na potwora z ukonczonym wpisem, Minor na kazdego
 *       poznanego (Canary sprawdza ukonczenie tylko dla Major),</li>
 *   <li>sloty (przypisane charmy razem, Major i Minor): konto darmowe {@link #FREE_SLOTS}, premium
 *       {@link #PREMIUM_SLOTS} (w Tibii "Charm Expansion" ze sklepu daje wszystkie - u nas go nie ma),</li>
 *   <li>pelny reset przywraca wszystkie zdobyte punkty, a echa ustawia na 100 dla awansowanej postaci, inaczej 0.</li>
 * </ul>
 * Oplaty w zlocie (zdjecie charmu, reset) liczy {@link CharmPrices}; pobiera je sesja gry.
 */
public final class Charms {
    public static final int FREE_SLOTS = 2;
    public static final int PREMIUM_SLOTS = 6;
    /** Echa po resecie dla postaci awansowanej (Canary: kv "promoted"). */
    public static final int PROMOTED_RESET_ECHOES = 100;

    private static final Charm[] ALL = Charm.values();

    private int points;
    private int maxPoints;
    private int echoes;
    private int maxEchoes;
    private final int[] tiers = new int[ALL.length];
    private final String[] assigned = new String[ALL.length];

    public int points() { return points; }
    /** Wszystkie zdobyte punkty (wraca do nich reset). */
    public int maxPoints() { return maxPoints; }
    public int echoes() { return echoes; }
    public int maxEchoes() { return maxEchoes; }

    public int tier(Charm c) { return tiers[c.ordinal()]; }
    public boolean unlocked(Charm c) { return tier(c) > 0; }

    /** Klucz potwora, na ktorego zalozono charm, albo null. */
    public String assignedTo(Charm c) { return assigned[c.ordinal()]; }

    public static int slots(boolean premium) { return premium ? PREMIUM_SLOTS : FREE_SLOTS; }

    /** Zajete sloty: wszystkie przypisane charmy. */
    public int used() {
        int n = 0;
        for (String a : assigned) if (a != null) n++;
        return n;
    }

    /** Charm danej kategorii zalozony na potwora albo null. */
    public Charm on(String monsterKey, Charm.Category category) {
        if (monsterKey == null) return null;
        for (Charm c : ALL) if (c.category() == category && monsterKey.equals(assigned[c.ordinal()])) return c;
        return null;
    }

    /** Stopien charmu dzialajacego przeciw temu potworowi (0 = nie dziala). */
    public int active(Charm c, String monsterKey) {
        return monsterKey != null && monsterKey.equals(assigned[c.ordinal()]) ? tiers[c.ordinal()] : 0;
    }

    /** Charmy zalozone na tego potwora (Major, potem Minor). */
    public List<Charm> charmsOn(String monsterKey) {
        List<Charm> out = new ArrayList<>(2);
        Charm a = on(monsterKey, Charm.Category.MAJOR);
        Charm b = on(monsterKey, Charm.Category.MINOR);
        if (a != null) out.add(a);
        if (b != null) out.add(b);
        return out;
    }

    /** Punkty za ukonczony wpis (zwieksza tez sume zdobytych). */
    public void addPoints(int n) {
        if (n <= 0) return;
        points = sat(points, n);
        maxPoints = sat(maxPoints, n);
    }

    /** Echa (zakup Major; testy). */
    void addEchoes(int n) {
        echoes = sat(echoes, n);
        maxEchoes = sat(maxEchoes, n);
    }

    private static int sat(int a, int b) { return (int) Math.min(Integer.MAX_VALUE, (long) a + b); }

    /** Echa za kupienie stopnia (z obecnego stopnia t na t+1) charmu Major: 25t^2 + 25t + 50 (Canary). */
    public static int echoesFor(int fromTier) { return 25 * fromTier * fromTier + 25 * fromTier + 50; }

    /** Wynik operacji: ok i komunikat dla gracza. */
    public record Result(boolean ok, String message) {
        static Result fail(String m) { return new Result(false, m); }
        static Result ok(String m) { return new Result(true, m); }
    }

    /** Kupno stopnia I albo ulepszenie o jeden stopien (Major za punkty, Minor za echa). */
    public Result buy(Charm c) {
        int t = tier(c);
        if (t >= Charm.MAX_TIER) return Result.fail(c.label() + Messages.get("charms.ma_juz_najwyzszy_stopien"));
        int cost = c.cost(t + 1);
        if (c.major()) {
            if (points < cost) return Result.fail(String.format(Messages.get("charms.s_s_kosztuje_d"), c.label(),
                    Charm.tierName(t + 1), cost, points));
            points -= cost;
            int got = echoesFor(t);
            addEchoes(got);
            tiers[c.ordinal()] = t + 1;
            return Result.ok(String.format(Messages.get("charms.s_stopien_s_d"), c.label(), Charm.tierName(t + 1), cost, got));
        }
        if (echoes < cost) return Result.fail(String.format(Messages.get("charms.s_s_kosztuje_d_2"),
                c.label(), Charm.tierName(t + 1), cost, echoes));
        echoes -= cost;
        tiers[c.ordinal()] = t + 1;
        return Result.ok(String.format(Messages.get("charms.s_stopien_s_d_2"), c.label(), Charm.tierName(t + 1), cost));
    }

    /** Czy charm mozna zalozyc na potwora (bez sprawdzania slotow i zajetosci). */
    public static String canTarget(Charm c, String monsterKey, Bestiary bestiary) {
        BestiaryEntry e = BestiaryData.get(monsterKey);
        if (e == null || bestiary.isBoss(monsterKey)) return Messages.get("charms.charmy_dzialaja_tylko_na");
        int kills = bestiary.kills(monsterKey);
        if (c.major() && kills < e.complete()) {
            return String.format(Messages.get("charms.charm_major_wymaga_ukonczonego"), kills, e.complete());
        }
        if (kills <= 0) return Messages.get("charms.najpierw_pokonaj_tego_potwora");
        return null;
    }

    /** Zaklada charm na potwora (jak akcja 1 w Canary). */
    public Result assign(Charm c, String monsterKey, Bestiary bestiary, boolean premium, String monsterName) {
        if (!unlocked(c)) return Result.fail(Messages.get("charms.najpierw_odblokuj_charm") + c.label() + ".");
        String why = canTarget(c, monsterKey, bestiary);
        if (why != null) return Result.fail(why);
        String old = assigned[c.ordinal()];
        if (monsterKey.equals(old)) return Result.fail(c.label() + Messages.get("charms.jest_juz_zalozony_na") + monsterName + ".");
        if (old != null) return Result.fail(c.label() + Messages.get("charms.jest_zalozony_na_innego"));
        if (used() >= slots(premium)) {
            return Result.fail(premium ? Messages.get("charms.wszystkie") + PREMIUM_SLOTS + Messages.get("charms.sloty_charmow_sa_zajete")
                    : Messages.get("charms.wszystkie") + FREE_SLOTS + Messages.get("charms.sloty_charmow_sa_zajete_2") + PREMIUM_SLOTS + ").");
        }
        if (on(monsterKey, c.category()) != null) {
            return Result.fail(Messages.get("charms.na_tym_potworze_jest") + c.category().label() + " (" + on(monsterKey, c.category()).label() + ").");
        }
        assigned[c.ordinal()] = monsterKey;
        return Result.ok(c.labelWithTier(tier(c)) + Messages.get("charms.zalozony_na") + monsterName + ".");
    }

    /** Zdejmuje charm z potwora (oplate pobiera sesja). */
    public boolean remove(Charm c) {
        if (assigned[c.ordinal()] == null) return false;
        assigned[c.ordinal()] = null;
        return true;
    }

    /** Pelny reset: wszystkie punkty wracaja, stopnie i przypisania znikaja, echa 100 (awans) albo 0. */
    public void resetAll(boolean promoted) {
        for (int i = 0; i < ALL.length; i++) {
            tiers[i] = 0;
            assigned[i] = null;
        }
        points = maxPoints;
        echoes = promoted ? PROMOTED_RESET_ECHOES : 0;
        maxEchoes = echoes;
    }

    /**
     * Stary zapis (sprzed charmow): punkty za wpisy, ktore juz sa ukonczone - jakby przyznano je przy ukonczeniu.
     */
    public void migrateFrom(Bestiary bestiary) {
        int total = 0;
        for (String key : bestiary.keys()) total += completionPoints(key, bestiary);
        addPoints(total);
    }

    /** Punkty, ktore dal ukonczony wpis (0, gdy nieukonczony, boss albo potwor bez wpisu). */
    static int completionPoints(String key, Bestiary bestiary) {
        BestiaryEntry e = BestiaryData.get(key);
        if (e == null || bestiary.isBoss(key)) return 0;
        return bestiary.kills(key) >= e.complete() ? e.charmPoints() : 0;
    }

    public boolean isDefault() {
        if (points != 0 || maxPoints != 0 || echoes != 0 || maxEchoes != 0) return false;
        for (int i = 0; i < ALL.length; i++) if (tiers[i] != 0 || assigned[i] != null) return false;
        return true;
    }

    /** Zapis: "punkty,suma,echa,sumaEch" i po | wpisy "CHARM:stopien:potwor" (potwor "-" = nieprzypisany). */
    public String encode() {
        StringBuilder sb = new StringBuilder();
        sb.append(points).append(',').append(maxPoints).append(',').append(echoes).append(',').append(maxEchoes);
        for (int i = 0; i < ALL.length; i++) {
            if (tiers[i] == 0 && assigned[i] == null) continue;
            sb.append('|').append(ALL[i].name()).append(':').append(tiers[i]).append(':').append(assigned[i] == null ? "-" : assigned[i]);
        }
        return sb.toString();
    }

    /** Odczyt zapisu; nieznane charmy (nowsze dane) i bledne liczby sa pomijane. */
    public void decode(String s) {
        resetFields();
        if (s == null || s.isEmpty()) return;
        String[] parts = s.split("\\|");
        String[] head = parts[0].split(",");
        if (head.length >= 4) {
            points = nonNegative(head[0]);
            maxPoints = Math.max(points, nonNegative(head[1]));
            echoes = nonNegative(head[2]);
            maxEchoes = Math.max(echoes, nonNegative(head[3]));
        }
        for (int i = 1; i < parts.length; i++) {
            String[] f = parts[i].split(":");
            if (f.length != 3) continue;
            Charm c = Charm.parse(f[0]);
            if (c == null) continue;
            tiers[c.ordinal()] = Math.max(0, Math.min(Charm.MAX_TIER, nonNegative(f[1])));
            assigned[c.ordinal()] = f[2].equals("-") || tiers[c.ordinal()] == 0 ? null : f[2];
        }
    }

    private static int nonNegative(String v) {
        try {
            return Math.max(0, Integer.parseInt(v.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void resetFields() {
        points = maxPoints = echoes = maxEchoes = 0;
        for (int i = 0; i < ALL.length; i++) {
            tiers[i] = 0;
            assigned[i] = null;
        }
    }

    public void copyFrom(Charms o) {
        points = o.points;
        maxPoints = o.maxPoints;
        echoes = o.echoes;
        maxEchoes = o.maxEchoes;
        System.arraycopy(o.tiers, 0, tiers, 0, tiers.length);
        System.arraycopy(o.assigned, 0, assigned, 0, assigned.length);
    }
}
