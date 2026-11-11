package pl.fawizja.pixel.character;

import pl.fawizja.pixel.i18n.Messages;

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
        if (n == 0) return Messages.get("bless.none");
        String base = pc.adventurerBlessing() && pc.blessingMask() == 0 ? Messages.get("bless.adv_short") : Messages.get("bless.n_of_7", n);
        return base + Messages.get("bless.summary_loss", reductionPercent(pc));
    }

    /** Wiersze okna {etykieta, stan}. */
    public static List<String[]> rows(PlayerCharacter pc) {
        List<String[]> out = new ArrayList<>();
        out.add(new String[] {Messages.get("bless.loss_title"), pl.fawizja.pixel.balance.Balance.deathLossLabel(pc.blessingCount(), pc.level())});
        if (pc.adventurerBlessing()) {
            out.add(new String[] {Messages.get("bless.adventurer"), Messages.get("bless.adv_free", Blessing.ADVENTURER_LEVEL)});
        }
        for (Blessing b : Blessing.values()) {
            String state;
            if (pc.boughtBlessing(b)) state = Messages.get("bless.have");
            else if (pc.hasBlessing(b)) state = Messages.get("bless.from_adv");
            else state = Messages.get("bless.none");
            out.add(new String[] {b.label(), state + (b.kind() == Blessing.Kind.PVP ? " (PvP)" : "")});
        }
        out.add(new String[] {Messages.get("bless.n_twist"), Messages.get("bless.twist_desc")});
        out.add(new String[] {Messages.get("bless.buy_label"), Messages.get("bless.buy_desc", Blessing.MOUNTAIN_TOWN)});
        return out;
    }
}
