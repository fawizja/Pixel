package pl.fawizja.pixel.character;

import pl.fawizja.pixel.i18n.Messages;
import pl.fawizja.pixel.npc.Npc;
import pl.fawizja.pixel.npc.NpcRegistry;

import java.util.ArrayList;
import java.util.List;

/** Wyglad stanu blogoslawienstw: wiersz w oknie postaci i informacyjne okno /blogoslawienstwa (jak okno Blessings w Tibii). */
public final class BlessingStatus {
    private BlessingStatus() {}

    /** Zmniejszenie straty bazowej w procentach (piec blogoslawienstw: 40%, ponizej poziomu 24: 50%; promocja +30%). */
    public static int reductionPercent(PlayerCharacter pc) {
        int pct = Math.min(100, pc.blessingCount() * Blessing.REDUCTION_EACH_PERCENT);
        if (pc.level() < 24 && pct >= 40) pct = 50;
        return Math.min(100, pct + (pc.isPromoted() ? 30 : 0));
    }

    /** Krotki opis do okna postaci: "3 z 5 (-24% straty)", "Poszukiwacza (-40%)" albo "brak". */
    public static String summary(PlayerCharacter pc) {
        int n = pc.blessingCount();
        if (n == 0) return Messages.get("bless.none");
        String base = pc.adventurerBlessing() && pc.blessingMask() == 0 ? Messages.get("bless.adv_short") : Messages.get("bless.n_of_5", n);
        return base + Messages.get("bless.summary_loss", reductionPercent(pc));
    }

    /** "Varden, kaplica pod miastem (500, 520)" - gdzie stoi mnich; bez rejestru NPC tylko nazwa miasta z klucza. */
    public static String where(Blessing b, NpcRegistry npcs) {
        if (npcs != null) {
            for (Npc n : npcs.all()) {
                if (n.key().equals(b.sellerKey())) {
                    return Messages.get("bless.where_fmt", n.name(), n.town(), n.position().x(), n.position().y());
                }
            }
        }
        return b.sellerKey();
    }

    /** Wiersze okna {etykieta, stan}: tylko informacja - co masz, ile tracisz i gdzie kupic (nic sie tu nie kupuje). */
    public static List<String[]> rows(PlayerCharacter pc, NpcRegistry npcs) {
        List<String[]> out = new ArrayList<>();
        int held = pc.blessingCount();
        out.add(new String[] {Messages.get("bless.loss_title"),
                pl.fawizja.pixel.balance.Balance.deathLossLabel(held, pc.level(), pc.experience(), pc.isPromoted())});
        out.add(new String[] {Messages.get("bless.items_title"), held >= Blessing.REGULAR_COUNT ? Messages.get("bless.items_none")
                : Messages.get("bless.items_fmt", percent(DeathLoss.backpackChance(held) * 100), percent(DeathLoss.itemChance(held) * 100))});
        if (pc.adventurerBlessing()) {
            out.add(new String[] {Messages.get("bless.adventurer"), Messages.get("bless.adv_free", Blessing.ADVENTURER_LEVEL)});
        }
        for (Blessing b : Blessing.values()) {
            String state;
            if (pc.boughtBlessing(b)) state = Messages.get("bless.have");
            else if (pc.hasBlessing(b)) state = Messages.get("bless.from_adv");
            else state = Messages.get("bless.none") + ": " + where(b, npcs) + ", " + Messages.get("bless.price_fmt", b.price(pc.level()));
            String tag = b.kind() == Blessing.Kind.PVP ? " (PvP)" : Messages.get(b.premium() ? "bless.acc_premium" : "bless.acc_free");
            out.add(new String[] {b.label() + tag, state});
        }
        out.add(new String[] {Messages.get("bless.twist_title"), Messages.get("bless.twist_desc")});
        out.add(new String[] {Messages.get("bless.amulet_title"), Messages.get("bless.amulet_desc")});
        out.add(new String[] {Messages.get("bless.buy_label"), Messages.get("bless.buy_desc")});
        return out;
    }

    /** 100 -> "100", 4,5 -> "4,5". */
    static String percent(double v) {
        return v == Math.rint(v) ? Long.toString(Math.round(v)) : Messages.decimal(v, 1);
    }
}
