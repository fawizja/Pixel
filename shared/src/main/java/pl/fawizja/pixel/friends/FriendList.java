package pl.fawizja.pixel.friends;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Lista znajomych postaci: imiona w kolejnosci dodania, bez powtorzen (wielkosc liter bez znaczenia). */
public final class FriendList {

    public static final int MAX = 30;

    public enum Result {
        OK(null),
        SELF("Nie możesz dodać siebie do znajomych."),
        ALREADY("Ta postać już jest na liście znajomych."),
        FULL("Lista znajomych jest pełna (" + MAX + ")."),
        UNKNOWN("Nie ma takiej postaci."),
        NOT_FRIEND("Tej postaci nie ma na liście znajomych.");

        public final String message;

        Result(String message) { this.message = message; }
    }

    private final List<String> names = new ArrayList<>();

    public Result add(String owner, String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return Result.UNKNOWN;
        if (n.equalsIgnoreCase(owner)) return Result.SELF;
        if (contains(n)) return Result.ALREADY;
        if (names.size() >= MAX) return Result.FULL;
        names.add(n);
        return Result.OK;
    }

    public Result remove(String name) {
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i).equalsIgnoreCase(name == null ? "" : name.trim())) {
                names.remove(i);
                return Result.OK;
            }
        }
        return Result.NOT_FRIEND;
    }

    public boolean contains(String name) {
        for (String n : names) if (n.equalsIgnoreCase(name)) return true;
        return false;
    }

    public List<String> names() { return Collections.unmodifiableList(names); }

    public int size() { return names.size(); }

    public void clear() { names.clear(); }

    /** Przy odczycie zapisu: bez sprawdzania wlasciciela, puste i powtorzone imiona sa pomijane. */
    public void restore(String name) {
        if (name != null && !name.isBlank() && !contains(name.trim()) && names.size() < MAX) names.add(name.trim());
    }

    public void copyFrom(FriendList o) {
        names.clear();
        names.addAll(o.names);
    }
}
