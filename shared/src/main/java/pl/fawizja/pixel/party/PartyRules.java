package pl.fawizja.pixel.party;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

/**
 * Wspolne doswiadczenie druzyny (jak w Tibii). Dzielone, gdy co najmniej dwoch zywych czlonkow jest na pietrze
 * zabojcy w promieniu {@link #SHARE_RANGE} i poziomy sa zblizone (najnizszy >= 2/3 najwyzszego - bez "holowania"
 * nowych postaci przez wysokie poziomy). Gdy ktorys z czlonkow obok ma za niski poziom, druzyna nie dzieli
 * doswiadczenia wcale (kazdy dostaje swoje zabicia sam), tak jak w Tibii.
 *
 * <p>Premia druzyny = premia za liczbe osob + premia za rozne profesje, najwyzej {@link #MAX_BONUS_PERCENT}%
 * (licza sie tylko czlonkowie, ktorzy dziela to zabicie):
 * <pre>
 *   osoby (n)        2     3     4     5+
 *   premia za n    +20%  +30%  +35%  +40%
 *
 *   rozne profesje   1     2     3     4      5
 *   premia          +0%  +20%  +50%  +100%  +160%
 *
 *   przyklady (n / profesje -> premia -> udzial kazdego z dosw., jakie dostalby sam):
 *     2 / 1 ->  +20% -> 60%      2 / 2 ->  +40% -> 70%
 *     3 / 1 ->  +30% -> 43%      3 / 3 ->  +80% -> 60%
 *     4 / 1 ->  +35% -> 34%      4 / 4 -> +135% -> 59%
 *     5 / 1 ->  +40% -> 28%      5 / 5 -> +200% -> 60%  (Knight, Paladin, Sorcerer, Druid, Monk)
 * </pre>
 * Pule (doswiadczenie, ktore kazdy czlonek dostalby sam, razy 1 + premia) dzieli sie po rowno, jak w Tibii,
 * a nie mnozy u kazdego: inaczej piec osob bijacych jednego potwora dostawaloby 15x wiecej doswiadczenia niz
 * jeden gracz i druzyna "na doczepke" bylaby obowiazkowa nawet na slabych potworach. Przy podziale pelna,
 * rozna druzyna dostaje za kazde zabicie 60% tego, co solo; poniewaz piec osob zabija potwory kilka razy
 * szybciej (a gesciejsze stada w lepszych lowiskach sa dla jednej postaci za trudne), na godzine kazdy
 * zarabia mniej wiecej 2-3x wiecej niz sam w tym samym miejscu. Druzyna jednej profesji zarabia malo
 * wiecej niz solo - liczy sie roznorodnosc.
 */
public final class PartyRules {

    public static final int SHARE_RANGE = 30;
    /** Premia za liczbe osob w procentach, indeks = liczba osob (0..5, wiecej liczy sie jak 5). */
    static final int[] SIZE_BONUS_PERCENT = {0, 0, 20, 30, 35, 40};
    /** Premia za rozne profesje w procentach, indeks = liczba roznych profesji (0..5). */
    static final int[] VOCATION_BONUS_PERCENT = {0, 0, 20, 50, 100, 160};
    public static final int MAX_BONUS_PERCENT = 200;

    private PartyRules() {}

    public static boolean inShareRange(Position killer, Position member) {
        return killer.z() == member.z() && killer.distanceTo(member) <= SHARE_RANGE;
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

    /** Premia druzyny w procentach dla liczby osob i roznych profesji (0 dla jednej osoby). */
    public static int bonusPercent(int members, int distinctVocations) {
        if (members < 2) return 0;
        int size = SIZE_BONUS_PERCENT[Math.min(members, SIZE_BONUS_PERCENT.length - 1)];
        int voc = VOCATION_BONUS_PERCENT[Math.max(0, Math.min(Math.min(distinctVocations, members), VOCATION_BONUS_PERCENT.length - 1))];
        return Math.min(MAX_BONUS_PERCENT, size + voc);
    }

    /** Premia druzyny w procentach dla profesji jej czlonkow (po jednej na osobe; null pomijane w profesjach). */
    public static int bonusPercent(Collection<Vocation> vocations) {
        EnumSet<Vocation> distinct = EnumSet.noneOf(Vocation.class);
        for (Vocation v : vocations) if (v != null) distinct.add(v);
        return bonusPercent(vocations.size(), distinct.size());
    }

    /** Udzial jednego czlonka z doswiadczenia, ktore dostalby sam (wg wlasnego poziomu), przy danej premii. */
    public static long share(long soloExperience, int members, int bonusPercent) {
        if (members <= 1) return soloExperience;
        return Math.round(soloExperience * (100.0 + bonusPercent) / (100.0 * members));
    }

    /** Udzial przy druzynie jednej profesji (sama premia za liczbe osob). */
    public static long share(long soloExperience, int members) {
        return share(soloExperience, members, bonusPercent(members, 1));
    }
}
