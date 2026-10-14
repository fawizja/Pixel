package pl.fawizja.pixel.inventory;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/** Lista przedmiotow, ktorych gracz nie chce automatycznie zbierac. */
public final class AutoLootFilter {
    private final Set<Integer> ignored = new TreeSet<>();

    public void ignore(int itemId) { ignored.add(itemId); }
    public void allow(int itemId) { ignored.remove(itemId); }
    public boolean isIgnored(int itemId) { return ignored.contains(itemId); }
    public Set<Integer> ignoredIds() { return Collections.unmodifiableSet(ignored); }
}
