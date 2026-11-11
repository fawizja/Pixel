package pl.fawizja.pixel.character;

import java.util.ArrayList;
import java.util.List;

/** Wygladu stanu blogoslawienstw: wiersz w oknie postaci i okno /blogoslawienstwa (jak okno Blessings w Tibii). */
public final class BlessingStatus {
    private BlessingStatus() {}

    /** Zmniejszenie straty doswiadczenia w procentach (z uwzglednieniem progu poziomu 24). */
    public static int reductionPercent(PlayerCharacter pc) {
        int pct = Math.min(100, pc.blessingCount() * Blessing.REDUCTION_EACH_PERCENT);
        return pc.level() < 24 && pct >= 40 ? 50 : pct;
    }

    /** Krotki opis do okna postaci: "3 z 7 (-24% straty)", "Poszukiwacza (-40%)" albo "brak". */
    public static String summary(PlayerCharacter pc) {
        int n = pc.blessingCount();
        if (n == 0) return "brak";
        String base = pc.adventurerBlessing() && pc.blessingMask() == 0 ? "Poszukiwacza" : n + " z 7";
        return base + " (-" + reductionPercent(pc) + "% straty)";
    }

    /** Wiersze okna {etykieta, stan}. */
    public static List<String[]> rows(PlayerCharacter pc) {
        List<String[]> out = new ArrayList<>();
        out.add(new String[] {"Strata po śmierci", pl.fawizja.pixel.balance.Balance.deathLossLabel(pc.blessingCount(), pc.level())});
        if (pc.adventurerBlessing()) {
            out.add(new String[] {"Poszukiwacza", "do poziomu " + Blessing.ADVENTURER_LEVEL + " za darmo, nie znika"});
        }
        for (Blessing b : Blessing.values()) {
            String state;
            if (pc.boughtBlessing(b)) state = "masz";
            else if (pc.hasBlessing(b)) state = "z Poszukiwacza";
            else state = "brak";
            out.add(new String[] {b.label(), state + (b.kind() == Blessing.Kind.PVP ? " (PvP)" : "")});
        }
        out.add(new String[] {"Zrządzenie losu", "chroni inne przy śmierci z ręki gracza"});
        out.add(new String[] {"Kupno", "kapłan w świątyni; górskie tylko w " + Blessing.MOUNTAIN_TOWN});
        return out;
    }
}
