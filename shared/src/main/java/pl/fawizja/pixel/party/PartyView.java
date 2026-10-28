package pl.fawizja.pixel.party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Druzyna widziana przez klienta: czlonkowie (lider pierwszy) z poziomem i profesja oraz biezaca premia
 * doswiadczenia ({@link PartyRules}). Komunikat serwera: "PARTYINFO premia dzieli imie;poziom;PROFESJA;online ...",
 * gdzie dzieli = 1, gdy poziomy pozwalaja dzielic doswiadczenie, a online = 1 albo 0.
 * Premia i dzielenie licza sie z czlonkow w grze; przy konkretnym zabiciu licza sie tylko ci w zasiegu.
 */
public record PartyView(List<Member> members, int bonusPercent, boolean sharing) {

    /** Czlonek druzyny; vocation null, gdy nie wiadomo. */
    public record Member(String name, int level, Vocation vocation, boolean online) {}

    public static final PartyView NONE = new PartyView(Collections.emptyList(), 0, false);

    public PartyView {
        members = List.copyOf(members);
    }

    /** Widok z listy czlonkow (lider pierwszy): premia i dzielenie liczone z czlonkow w grze. */
    public static PartyView of(List<Member> members) {
        if (members.size() < 2) return NONE;
        List<Vocation> vocs = new ArrayList<>();
        List<Integer> levels = new ArrayList<>();
        for (Member m : members) {
            if (!m.online()) continue;
            vocs.add(m.vocation());
            levels.add(m.level());
        }
        return new PartyView(members, PartyRules.bonusPercent(vocs), PartyRules.levelsCompatible(levels));
    }

    public boolean inParty() { return members.size() >= 2; }

    public String leader() { return members.isEmpty() ? null : members.get(0).name(); }

    public boolean isLeader(String name) { return leader() != null && leader().equalsIgnoreCase(name); }

    public boolean contains(String name) {
        for (Member m : members) if (m.name().equalsIgnoreCase(name)) return true;
        return false;
    }

    public List<String> names() {
        List<String> out = new ArrayList<>();
        for (Member m : members) out.add(m.name());
        return out;
    }

    /** Liczba roznych profesji czlonkow w grze. */
    public int distinctVocations() {
        EnumSet<Vocation> s = EnumSet.noneOf(Vocation.class);
        for (Member m : members) if (m.online() && m.vocation() != null) s.add(m.vocation());
        return s.size();
    }

    /** Wiersz do okna druzyny / zakladki Druzyna, np. "Premia doświadczenia: +200% (5 osób, 5 profesji)". */
    public String bonusLine() {
        if (!inParty()) return "Nie jesteś w drużynie.";
        int online = 0;
        int maxLevel = 0;
        for (Member m : members) {
            if (!m.online()) continue;
            online++;
            maxLevel = Math.max(maxLevel, m.level());
        }
        if (!sharing) {
            return "Wspólne doświadczenie wyłączone: za duża różnica poziomów (najniższy poz. "
                    + PartyRules.minSharingLevel(maxLevel) + ").";
        }
        int voc = distinctVocations();
        return "Premia doświadczenia: +" + bonusPercent + "% (" + online + " " + osob(online) + ", "
                + voc + " " + profesji(voc) + ")";
    }

    private static String osob(int n) { return n == 1 ? "osoba" : n >= 2 && n <= 4 ? "osoby" : "osób"; }

    private static String profesji(int n) { return n == 1 ? "profesja" : n >= 2 && n <= 4 ? "profesje" : "profesji"; }

    /** Pola komunikatu do wyslania, z nazwa "PARTYINFO". */
    public String[] encode() {
        String[] out = new String[3 + members.size()];
        out[0] = "PARTYINFO";
        out[1] = String.valueOf(bonusPercent);
        out[2] = sharing ? "1" : "0";
        for (int i = 0; i < members.size(); i++) {
            Member m = members.get(i);
            out[3 + i] = m.name() + ";" + m.level() + ";" + (m.vocation() == null ? "-" : m.vocation().name()) + ";" + (m.online() ? 1 : 0);
        }
        return out;
    }

    /** Odczyt komunikatu; null dla obcej linii, zle wiersze sa pomijane. */
    public static PartyView parse(String[] m) {
        if (m == null || m.length < 3 || !"PARTYINFO".equals(m[0])) return null;
        try {
            int bonus = Integer.parseInt(m[1]);
            List<Member> rows = new ArrayList<>();
            for (int i = 3; i < m.length; i++) {
                String[] f = m[i].split(";");
                if (f.length != 4) continue;
                rows.add(new Member(f[0], Integer.parseInt(f[1]), vocation(f[2]), "1".equals(f[3])));
            }
            return rows.size() < 2 ? NONE : new PartyView(rows, bonus, "1".equals(m[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Vocation vocation(String s) {
        if (s == null || s.equals("-")) return null;
        try {
            return Vocation.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null; // nieznana profesja (nowszy serwer)
        }
    }
}
