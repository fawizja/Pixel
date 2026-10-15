package pl.fawizja.pixel.party;

import java.util.List;
import pl.fawizja.pixel.world.Position;

/**
 * Wspolne doswiadczenie druzyny. Dzielone, gdy co najmniej dwoch zywych czlonkow jest na pietrze zabojcy
 * w promieniu {@link #SHARE_RANGE} i poziomy sa zblizone (najnizszy >= 2/3 najwyzszego - bez "holowania"
 * nowych postaci przez wysokie poziomy). Premia +20% za kazdego kolejnego czlonka, dzielona po rowno:
 * 2 osoby dostaja po 60%, 4 po 40% doswiadczenia, jakie zdobylyby same.
 */
public final class PartyRules {

    public static final int SHARE_RANGE = 30;
    public static final double BONUS_PER_MEMBER = 0.2;

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

    /** Udzial jednego czlonka z doswiadczenia, ktore dostalby sam (wg wlasnego poziomu). */
    public static long share(long soloExperience, int members) {
        if (members <= 1) return soloExperience;
        return Math.round(soloExperience * (1.0 + BONUS_PER_MEMBER * (members - 1)) / members);
    }
}
