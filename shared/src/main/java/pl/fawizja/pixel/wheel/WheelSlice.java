package pl.fawizja.pixel.wheel;

import pl.fawizja.pixel.i18n.Messages;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 36 pol Kola Przeznaczenia (4 domeny po 9 pol), dane jak w Canary (wheel_definitions.hpp, player_wheel.cpp
 * canPlayerSelectPointOnSlot / getMaxPointsPerSlot, io_wheel.cpp slotXxx):
 * <ul>
 *   <li>pojemnosc: 50 (srodek), 75 (2 pola), 100 (3), 150 (2), 200 (zewnetrzne) = 1000 na domene,</li>
 *   <li>pole 75 wymaga co najmniej 50 punktow promocji ogolem, 100 - 125, 150 - 225, 200 - 375,</li>
 *   <li>pole 50 jest zawsze dostepne; kazde inne otwiera sie, gdy jedno z jego pol poprzedzajacych jest pelne
 *       (takze pole sasiedniej domeny: 75 i 100 graniczace z druga cwiartka),</li>
 *   <li>oddanie (Dedication): HP, mana, HP i mana, udzwig albo mitygacja za kazdy punkt,</li>
 *   <li>przekonanie (Conviction) po zapelnieniu pola: umiejetnosc, kradziez zycia/many, ulepszenie czaru (grupy 1-5),
 *       pasywka profesji albo naczynie klejnotu.</li>
 * </ul>
 */
public enum WheelSlice {
    // ---- zielona (lewa gora)
    GREEN_50(WheelDomain.GREEN, Pos.S50, Stat.CAP, Conv.VESSEL),
    GREEN_TOP_75(WheelDomain.GREEN, Pos.T75, Stat.MANA, Conv.LIFE_LEECH),
    GREEN_BOTTOM_75(WheelDomain.GREEN, Pos.B75, Stat.MANA, Conv.SKILL),
    GREEN_TOP_100(WheelDomain.GREEN, Pos.T100, Stat.HP, Conv.VESSEL),
    GREEN_MIDDLE_100(WheelDomain.GREEN, Pos.M100, Stat.HP, Conv.SPELL_2),
    GREEN_BOTTOM_100(WheelDomain.GREEN, Pos.B100, Stat.HP, Conv.SPELL_4),
    GREEN_TOP_150(WheelDomain.GREEN, Pos.T150, Stat.MIT, Conv.MANA_LEECH),
    GREEN_BOTTOM_150(WheelDomain.GREEN, Pos.B150, Stat.MIT, Conv.VESSEL),
    GREEN_200(WheelDomain.GREEN, Pos.S200, Stat.HP_MANA, Conv.PASSIVE_A),
    // ---- czerwona (prawa gora)
    RED_50(WheelDomain.RED, Pos.S50, Stat.MIT, Conv.SPELL_5),
    RED_TOP_75(WheelDomain.RED, Pos.T75, Stat.CAP, Conv.VESSEL),
    RED_BOTTOM_75(WheelDomain.RED, Pos.B75, Stat.CAP, Conv.LIFE_LEECH),
    RED_TOP_100(WheelDomain.RED, Pos.T100, Stat.MANA, Conv.SKILL),
    RED_MIDDLE_100(WheelDomain.RED, Pos.M100, Stat.MANA, Conv.SPELL_3),
    RED_BOTTOM_100(WheelDomain.RED, Pos.B100, Stat.MANA, Conv.VESSEL),
    RED_TOP_150(WheelDomain.RED, Pos.T150, Stat.HP, Conv.VESSEL),
    RED_BOTTOM_150(WheelDomain.RED, Pos.B150, Stat.HP, Conv.MANA_LEECH),
    RED_200(WheelDomain.RED, Pos.S200, Stat.HP_MANA, Conv.SPELL_1),
    // ---- fioletowa (prawy dol)
    PURPLE_50(WheelDomain.PURPLE, Pos.S50, Stat.HP, Conv.VESSEL),
    PURPLE_TOP_75(WheelDomain.PURPLE, Pos.T75, Stat.MIT, Conv.SKILL),
    PURPLE_BOTTOM_75(WheelDomain.PURPLE, Pos.B75, Stat.MIT, Conv.MANA_LEECH),
    PURPLE_TOP_100(WheelDomain.PURPLE, Pos.T100, Stat.CAP, Conv.SPELL_2),
    PURPLE_MIDDLE_100(WheelDomain.PURPLE, Pos.M100, Stat.CAP, Conv.SPELL_4),
    PURPLE_BOTTOM_100(WheelDomain.PURPLE, Pos.B100, Stat.CAP, Conv.VESSEL),
    PURPLE_TOP_150(WheelDomain.PURPLE, Pos.T150, Stat.MANA, Conv.VESSEL),
    PURPLE_BOTTOM_150(WheelDomain.PURPLE, Pos.B150, Stat.MANA, Conv.LIFE_LEECH),
    PURPLE_200(WheelDomain.PURPLE, Pos.S200, Stat.HP_MANA, Conv.PASSIVE_B),
    // ---- niebieska (lewy dol)
    BLUE_50(WheelDomain.BLUE, Pos.S50, Stat.MANA, Conv.SPELL_1),
    BLUE_TOP_75(WheelDomain.BLUE, Pos.T75, Stat.HP, Conv.MANA_LEECH),
    BLUE_BOTTOM_75(WheelDomain.BLUE, Pos.B75, Stat.HP, Conv.VESSEL),
    BLUE_TOP_100(WheelDomain.BLUE, Pos.T100, Stat.MIT, Conv.VESSEL),
    BLUE_MIDDLE_100(WheelDomain.BLUE, Pos.M100, Stat.MIT, Conv.SPELL_3),
    BLUE_BOTTOM_100(WheelDomain.BLUE, Pos.B100, Stat.MIT, Conv.SKILL),
    BLUE_TOP_150(WheelDomain.BLUE, Pos.T150, Stat.CAP, Conv.LIFE_LEECH),
    BLUE_BOTTOM_150(WheelDomain.BLUE, Pos.B150, Stat.CAP, Conv.VESSEL),
    BLUE_200(WheelDomain.BLUE, Pos.S200, Stat.HP_MANA, Conv.SPELL_5);

    /** Polozenie pola w cwiartce (od srodka kola na zewnatrz). */
    public enum Pos {
        S50(50, 0, "50", "środek"), T75(75, 50, "T75", "górne 75"), B75(75, 50, "B75", "dolne 75"),
        T100(100, 125, "T100", "górne 100"), M100(100, 125, "M100", "środkowe 100"), B100(100, 125, "B100", "dolne 100"),
        T150(150, 225, "T150", "górne 150"), B150(150, 225, "B150", "dolne 150"), S200(200, 375, "200", "zewnętrzne 200");

        public final int capacity;
        /** Ile punktow promocji ogolem musi miec postac, zeby wlozyc tu punkt (Canary playerPoints < N). */
        public final int threshold;
        final String code;
        private final String label;
        public String label() { return Messages.tr(label); }

        Pos(int capacity, int threshold, String code, String label) {
            this.capacity = capacity;
            this.threshold = threshold;
            this.code = code;
            this.label = label;
        }
    }

    /** Rodzaj oddania (Dedication): co daje kazdy punkt w polu. */
    public enum Stat { HP, MANA, HP_MANA, CAP, MIT }

    /** Rodzaj przekonania (Conviction): co daje pelne pole. */
    public enum Conv {
        SKILL, LIFE_LEECH, MANA_LEECH, SPELL_1, SPELL_2, SPELL_3, SPELL_4, SPELL_5, PASSIVE_A, PASSIVE_B, VESSEL;

        /** Grupa czaru 0..4 albo -1. */
        public int spellGroup() { return ordinal() >= SPELL_1.ordinal() && ordinal() <= SPELL_5.ordinal() ? ordinal() - SPELL_1.ordinal() : -1; }
    }

    public final WheelDomain domain;
    public final Pos pos;
    public final Stat stat;
    public final Conv conviction;
    private List<WheelSlice> prereqs;

    WheelSlice(WheelDomain domain, Pos pos, Stat stat, Conv conviction) {
        this.domain = domain;
        this.pos = pos;
        this.stat = stat;
        this.conviction = conviction;
    }

    public int capacity() { return pos.capacity; }

    /** Krotki kod do zapisu: "G50", "RT75", "PM100", "B200". */
    public String code() { return domain.code + pos.code; }

    /** Opis po polsku: "Zielona, górne 75". */
    public String label() { return domain.label() + ", " + pos.label(); }

    public static WheelSlice of(WheelDomain d, Pos p) { return values()[d.ordinal() * 9 + p.ordinal()]; }

    public static WheelSlice ofCode(String code) {
        for (WheelSlice s : values()) if (s.code().equals(code)) return s;
        return null;
    }

    /** Pola, z ktorych jedno musi byc pelne, zeby otworzyc to pole (puste dla pola 50). */
    public List<WheelSlice> prereqs() {
        if (prereqs == null) prereqs = Collections.unmodifiableList(buildPrereqs());
        return prereqs;
    }

    private List<WheelSlice> buildPrereqs() {
        List<WheelSlice> out = new ArrayList<>();
        // w obrebie cwiartki - dokladnie jak listy canSelectSlotFullOrPartial w Canary
        switch (pos) {
            case S200: add(out, Pos.T150, Pos.B150); break;
            case T150: add(out, Pos.T100, Pos.M100, Pos.B150); break;
            case B150: add(out, Pos.M100, Pos.B100, Pos.T150); break;
            case T100: add(out, Pos.M100, Pos.T75); break;
            case M100: add(out, Pos.T100, Pos.T75, Pos.B75, Pos.B100); break;
            case B100: add(out, Pos.M100, Pos.B75, Pos.B150); break;
            case T75: add(out, Pos.S50, Pos.B75, Pos.M100, Pos.T100); break;
            case B75: add(out, Pos.S50, Pos.T75, Pos.M100, Pos.B100); break;
            default:
        }
        // granice cwiartek: gora zielonej z czerwona, dol czerwonej z fioletowa, dol fioletowej z niebieska, gora niebieskiej z zielona
        WheelDomain cross = null;
        boolean top = pos == Pos.T75 || pos == Pos.T100;
        boolean bottom = pos == Pos.B75 || pos == Pos.B100;
        if (top || bottom) {
            switch (domain) {
                case GREEN: cross = top ? WheelDomain.RED : WheelDomain.BLUE; break;
                case RED: cross = top ? WheelDomain.GREEN : WheelDomain.PURPLE; break;
                case PURPLE: cross = top ? WheelDomain.RED : WheelDomain.BLUE; break;
                default: cross = top ? WheelDomain.GREEN : WheelDomain.PURPLE; break;
            }
            Pos other = crossPos(domain, cross, pos);
            out.add(of(cross, other));
        }
        return out;
    }

    /** Pole sasiedniej cwiartki na granicy (np. zielone gorne 100 graniczy z czerwonym gornym 100, fioletowe gorne z czerwonym dolnym). */
    private static Pos crossPos(WheelDomain from, WheelDomain to, Pos p) {
        boolean hundred = p == Pos.T100 || p == Pos.B100;
        // ta sama strona nazwy przy GREEN<->RED i BLUE<->PURPLE ... wg Canary:
        // GREEN_T <-> RED_T, RED_B <-> PURPLE_T, PURPLE_B <-> BLUE_B, BLUE_T <-> GREEN_B
        boolean otherTop;
        if ((from == WheelDomain.GREEN && to == WheelDomain.RED) || (from == WheelDomain.RED && to == WheelDomain.GREEN)) otherTop = true;
        else if (from == WheelDomain.RED && to == WheelDomain.PURPLE) otherTop = true;   // RED_B -> PURPLE_T
        else if (from == WheelDomain.PURPLE && to == WheelDomain.RED) otherTop = false;  // PURPLE_T -> RED_B
        else if ((from == WheelDomain.PURPLE && to == WheelDomain.BLUE) || (from == WheelDomain.BLUE && to == WheelDomain.PURPLE)) otherTop = false;
        else if (from == WheelDomain.GREEN && to == WheelDomain.BLUE) otherTop = true;   // GREEN_B -> BLUE_T
        else otherTop = false;                                                           // BLUE_T -> GREEN_B
        if (hundred) return otherTop ? Pos.T100 : Pos.B100;
        return otherTop ? Pos.T75 : Pos.B75;
    }

    private void add(List<WheelSlice> out, Pos... ps) {
        for (Pos p : ps) out.add(of(domain, p));
    }

    /** Pola danej domeny w kolejnosci od srodka. */
    public static List<WheelSlice> of(WheelDomain d) {
        List<WheelSlice> out = new ArrayList<>(9);
        for (Pos p : Pos.values()) out.add(of(d, p));
        return out;
    }
}
