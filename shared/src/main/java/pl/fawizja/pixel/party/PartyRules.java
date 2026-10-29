package pl.fawizja.pixel.party;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

/**
 * Wspolne doswiadczenie druzyny dokladnie jak w Tibii (tibia.com, "Shared Experience"; TibiaWiki "Party").
 *
 * <p>Dziala tylko, gdy WSZYSCY czlonkowie druzyny spelniaja warunki - inaczej kazdy dostaje doswiadczenie
 * za swoje zabicia sam:
 * <ul>
 *   <li>poziom najnizszego czlonka &gt;= 2/3 poziomu najwyzszego ({@link #levelsCompatible}),</li>
 *   <li>kazdy jest najwyzej {@link #SHARE_RANGE} pol od lidera, na jego pietrze albo pietro wyzej/nizej,</li>
 *   <li>kazdy byl aktywny w ostatnich {@link #ACTIVITY_MS} (2 min): zaatakowal potwora (w Tibii takze
 *       uleczenie czlonka druzyny - u nas nie ma czarow leczacych innych).</li>
 * </ul>
 *
 * <p>Premia zalezy tylko od liczby roznych profesji (nie od liczby osob):
 * <pre>
 *   rozne profesje   1     2     3     4     5 (z Monkiem)
 *   premia         +20%  +30%  +60%  +100% +100%
 * </pre>
 * Premia jest tylko za potwory dajace bazowo co najmniej {@link #MIN_BONUS_EXPERIENCE} doswiadczenia.
 * Podzial: kazdy z P czlonkow dostaje ceil(M x (1 + premia) / P) x C, gdzie M - doswiadczenie potwora,
 * C - premie postaci (u nas: poziom wzgledem potwora, zwoj nauki, potwor dnia, wypoczynek).
 * Przyklad: 4 profesje, 4 osoby, potwor za 1000 - kazdy 1000 x 2,0 / 4 = 500.
 */
public final class PartyRules {

    /** Najwieksza odleglosc czlonka od lidera (pola, "zasieg krzyku"). */
    public static final int SHARE_RANGE = 30;
    /** Ile pieter od lidera (w gore albo w dol) wolno byc. */
    public static final int FLOOR_RANGE = 1;
    /** Czlonek musi byc aktywny (atak na potwora) w tym czasie. */
    public static final long ACTIVITY_MS = 2 * 60_000L;
    /** Potwory z mniejszym bazowym doswiadczeniem nie daja premii (podzial nadal dziala). */
    public static final int MIN_BONUS_EXPERIENCE = 20;
    /** Premia w procentach, indeks = liczba roznych profesji (0..5; 4 i 5 = +100%). */
    static final int[] VOCATION_BONUS_PERCENT = {0, 20, 30, 60, 100, 100};
    public static final int MAX_BONUS_PERCENT = 100;

    private PartyRules() {}

    /** Czy czlonek jest dosc blisko lidera: do 30 pol i najwyzej pietro wyzej albo nizej. */
    public static boolean inShareRange(Position leader, Position member) {
        if (Math.abs(leader.z() - member.z()) > FLOOR_RANGE) return false;
        return Math.max(Math.abs(leader.x() - member.x()), Math.abs(leader.y() - member.y())) <= SHARE_RANGE;
    }

    /** Czy czlonek byl aktywny w ostatnich 2 minutach (lastActionMs = ostatni atak na potwora). */
    public static boolean active(long lastActionMs, long now) {
        return now - lastActionMs <= ACTIVITY_MS;
    }

    public static boolean levelsCompatible(List<Integer> levels) {
        if (levels.size() < 2) return false;
        int min = Integer.MAX_VALUE, max = 0;
        for (int l : levels) {
            min = Math.min(min, l);
            max = Math.max(max, l);
        }
        return (long) min * 3 >= (long) max * 2;
    }

    /** Najnizszy poziom, ktory moze dzielic doswiadczenie z postacia na poziomie max (2/3, w gore). */
    public static int minSharingLevel(int maxLevel) {
        return (maxLevel * 2 + 2) / 3;
    }

    /** Najwyzszy poziom, z ktorym moze dzielic doswiadczenie postac na poziomie min (3/2, w dol). */
    public static int maxSharingLevel(int minLevel) {
        return minLevel * 3 / 2;
    }

    /** Premia w procentach dla liczby roznych profesji (druzyna ma zawsze co najmniej dwie osoby). */
    public static int bonusPercent(int distinctVocations) {
        return VOCATION_BONUS_PERCENT[Math.max(0, Math.min(distinctVocations, VOCATION_BONUS_PERCENT.length - 1))];
    }

    /** Premia druzyny w procentach dla profesji jej czlonkow (po jednej na osobe; 0 dla jednej osoby). */
    public static int bonusPercent(Collection<Vocation> vocations) {
        if (vocations.size() < 2) return 0;
        EnumSet<Vocation> distinct = EnumSet.noneOf(Vocation.class);
        for (Vocation v : vocations) if (v != null) distinct.add(v);
        return bonusPercent(Math.max(1, distinct.size()));
    }

    /** Premia za konkretnego potwora: 0, gdy daje bazowo mniej niz {@link #MIN_BONUS_EXPERIENCE}. */
    public static int bonusPercent(long monsterBaseExperience, Collection<Vocation> vocations) {
        return monsterBaseExperience < MIN_BONUS_EXPERIENCE ? 0 : bonusPercent(vocations);
    }

    /** Udzial jednego czlonka: ceil(exp x (100 + premia) / (100 x czlonkowie)). */
    public static long share(long experience, int members, int bonusPercent) {
        if (members <= 1) return experience;
        long num = experience * (100L + bonusPercent);
        long den = 100L * members;
        return (num + den - 1) / den;
    }
}
