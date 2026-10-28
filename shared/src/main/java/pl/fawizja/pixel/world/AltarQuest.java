package pl.fawizja.pixel.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Zadanie z oltarzami profesji (Darek 2026-10-28, Grobowiec Pieciu Profesji pod pustynia): w sali stoi po jednym
 * oltarzu na profesje, na kazdym lezy przedmiot tej profesji (tylko do ogladania, {@link WorldMap#displayAt}).
 * Gdy na polu przed oltarzem stoi postac wlasciwej profesji, zapala sie pochodnia na murze nad oltarzem
 * ({@link WallLights}, domyslnie zgaszona). Gdy pala sie wszystkie, dzwignia przenosi stojacych do skarbca:
 * kazda profesja ma tam swoja skrzynie z nagroda (raz na postac, {@code TreasureChests}).
 * <p>
 * Dane z pliku mapy altar_quests.csv (zapisuje je generator). Logika nie zna graczy: kto gdzie stoi, mowi
 * {@link Occupants} (gra offline: sama postac, serwer: wszyscy gracze).
 */
public final class AltarQuest {

    /** Oltarz profesji: oltarz, pole przed nim, pochodnia na murze, przedmiot na oltarzu, cel teleportu i skrzynia. */
    public record Slot(Vocation vocation, Position altar, Position stand, Position torch, String itemKey,
                       Position target, Position chest) {}

    /** Kto stoi na polu: czy na p stoi zywa postac danej profesji. */
    public interface Occupants {
        boolean standing(Position p, Vocation v);
    }

    private final String id;
    private final String name;
    private final long gold;
    private final Position lever;
    /** Sala oltarzy i skarbiec: x0, y0, x1, y1, z (wlacznie). */
    private final int[] hall;
    private final int[] vault;
    private final List<Slot> slots = new ArrayList<>();

    public AltarQuest(String id, String name, long gold, Position lever, int[] hall, int[] vault) {
        if (hall.length != 5 || vault.length != 5) throw new IllegalArgumentException("rect needs x0,y0,x1,y1,z");
        this.id = id;
        this.name = name;
        this.gold = gold;
        this.lever = lever;
        this.hall = hall.clone();
        this.vault = vault.clone();
    }

    void addSlot(Slot s) {
        for (Slot o : slots) {
            if (o.vocation() == s.vocation()) throw new IllegalArgumentException("two altars of " + s.vocation() + " in " + id);
        }
        slots.add(s);
    }

    public String id() { return id; }
    public String name() { return name; }
    /** Nagroda w zlocie dla kazdej postaci (raz). */
    public long gold() { return gold; }
    public Position lever() { return lever; }
    public List<Slot> slots() { return Collections.unmodifiableList(slots); }

    /** Identyfikator skrzyni nagrody profesji (zapisywany w otwartych skrzyniach postaci). */
    public String chestId(Vocation v) { return "q_" + id + "_" + v.name().toLowerCase(java.util.Locale.ROOT); }

    public Slot slot(Vocation v) {
        for (Slot s : slots) if (s.vocation() == v) return s;
        return null;
    }

    public Slot slotByAltar(Position p) {
        for (Slot s : slots) if (s.altar().equals(p)) return s;
        return null;
    }

    public Slot slotByTorch(Position p) {
        for (Slot s : slots) if (s.torch().equals(p)) return s;
        return null;
    }

    public Slot slotByStand(Position p) {
        for (Slot s : slots) if (s.stand().equals(p)) return s;
        return null;
    }

    /** Sala oltarzy albo skarbiec (bez losowych skrzyn i pulapek). */
    public boolean inQuestRooms(Position p) { return in(hall, p) || in(vault, p); }

    public boolean inHall(Position p) { return in(hall, p); }

    public boolean inVault(Position p) { return in(vault, p); }

    private static boolean in(int[] r, Position p) {
        return p.z() == r[4] && p.x() >= r[0] && p.x() <= r[2] && p.y() >= r[1] && p.y() <= r[3];
    }

    public boolean satisfied(Slot s, Occupants who) { return who.standing(s.stand(), s.vocation()); }

    /** Oltarze, przed ktorymi brakuje wlasciwej profesji (pusta lista = dzwignie mozna przeciagnac). */
    public List<Slot> missing(Occupants who) {
        List<Slot> out = new ArrayList<>();
        for (Slot s : slots) if (!satisfied(s, who)) out.add(s);
        return out;
    }

    public boolean complete(Occupants who) { return !slots.isEmpty() && missing(who).isEmpty(); }

    /** Pochodnie wg tego, kto stoi przed oltarzami; zwraca liczbe zapalonych. */
    public int updateTorches(WallLights lights, Occupants who) {
        int lit = 0;
        for (Slot s : slots) {
            boolean on = satisfied(s, who);
            lights.setLit(s.torch(), on);
            if (on) lit++;
        }
        return lit;
    }

    /** "Knight, Druid" - nazwy profesji brakujacych przed oltarzami. */
    public static String names(List<Slot> list) {
        List<String> n = new ArrayList<>();
        for (Slot s : list) n.add(s.vocation().displayName());
        return String.join(", ", n);
    }
}
