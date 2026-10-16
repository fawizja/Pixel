package pl.fawizja.pixel.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import pl.fawizja.pixel.world.Position;

/**
 * Wlasne znaczniki na mapie (zapisywane na urzadzeniu, osobno dla kazdej postaci): nazwa i pole.
 * Zapis: "nazwa|x,y,z;nazwa|x,y,z".
 */
public final class MapMarkers {
    public static final int MAX = 20;
    public static final int NAME_MAX = 20;

    public record Marker(String name, Position at) {}

    private final List<Marker> markers = new ArrayList<>();

    public List<Marker> all() { return Collections.unmodifiableList(markers); }

    /** Nazwa bez znakow zapisu, przycieta; null, gdy pusta. */
    public static String cleanName(String name) {
        if (name == null) return null;
        String n = name.replace(";", " ").replace("|", " ").replace(",", " ").trim().replaceAll("\\s+", " ");
        if (n.length() > NAME_MAX) n = n.substring(0, NAME_MAX).trim();
        return n.isEmpty() ? null : n;
    }

    /** Dodaje albo przesuwa znacznik o tej nazwie; false, gdy nazwa pusta albo brak miejsca. */
    public boolean put(String name, Position at) {
        String n = cleanName(name);
        if (n == null || at == null) return false;
        int i = indexOf(n);
        if (i >= 0) {
            markers.set(i, new Marker(n, at));
            return true;
        }
        if (markers.size() >= MAX) return false;
        markers.add(new Marker(n, at));
        return true;
    }

    public boolean remove(String name) {
        String n = cleanName(name);
        int i = n == null ? -1 : indexOf(n);
        if (i < 0) return false;
        markers.remove(i);
        return true;
    }

    private int indexOf(String name) {
        for (int i = 0; i < markers.size(); i++) {
            if (markers.get(i).name().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) return i;
        }
        return -1;
    }

    public String encode() {
        StringBuilder sb = new StringBuilder();
        for (Marker m : markers) {
            if (sb.length() > 0) sb.append(';');
            sb.append(m.name()).append('|').append(m.at().x()).append(',').append(m.at().y()).append(',').append(m.at().z());
        }
        return sb.toString();
    }

    public static MapMarkers decode(String text) {
        MapMarkers m = new MapMarkers();
        if (text == null || text.isBlank()) return m;
        for (String part : text.split(";")) {
            String[] f = part.split("\\|");
            if (f.length != 2) continue;
            String[] xyz = f[1].split(",");
            if (xyz.length != 3) continue;
            try {
                m.put(f[0], new Position(Integer.parseInt(xyz[0].trim()), Integer.parseInt(xyz[1].trim()), Integer.parseInt(xyz[2].trim())));
            } catch (IllegalArgumentException ignored) {
                // uszkodzony wpis - pomijamy
            }
        }
        return m;
    }
}
