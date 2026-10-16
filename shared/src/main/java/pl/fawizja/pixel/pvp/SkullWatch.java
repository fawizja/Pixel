package pl.fawizja.pixel.pvp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ostrzezenie o graczach z czerwona czaszka w zasiegu wzroku: kazdy raz, gdy pojawi sie w widoku
 * (albo dostanie czaszke na naszych oczach). Gracz, ktory zniknie z widoku, przy powrocie ostrzega znowu.
 */
public final class SkullWatch {

    private final Set<String> warned = new HashSet<>();

    /** visible: imie gracza w widoku -> czaszka (NONE, WHITE, RED). Zwraca komunikaty do dziennika. */
    public List<String> update(Map<String, String> visible) {
        List<String> out = new ArrayList<>();
        warned.retainAll(visible.keySet());
        for (Map.Entry<String, String> e : visible.entrySet()) {
            boolean red = "RED".equals(e.getValue());
            if (red && warned.add(e.getKey())) out.add("Uwaga: " + e.getKey() + " z czerwoną czaszką w pobliżu!");
            if (!red) warned.remove(e.getKey());
        }
        return out;
    }
}
