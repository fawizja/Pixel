package pl.fawizja.pixel.pvp;

import pl.fawizja.pixel.i18n.Messages;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ostrzezenie o graczach z czerwona albo czarna czaszka w zasiegu wzroku: kazdy raz, gdy pojawi sie w widoku
 * (albo dostanie czaszke na naszych oczach). Gracz, ktory zniknie z widoku, przy powrocie ostrzega znowu.
 */
public final class SkullWatch {

    private final Set<String> warned = new HashSet<>();

    /** visible: imie gracza w widoku -> czaszka (NONE, WHITE, YELLOW, ORANGE, RED, BLACK). Zwraca komunikaty do dziennika. */
    public List<String> update(Map<String, String> visible) {
        List<String> out = new ArrayList<>();
        warned.retainAll(visible.keySet());
        for (Map.Entry<String, String> e : visible.entrySet()) {
            boolean black = "BLACK".equals(e.getValue());
            boolean red = black || "RED".equals(e.getValue());
            if (red && warned.add(e.getKey())) {
                out.add(Messages.get("skull_watch.uwaga") + e.getKey() + (black ? Messages.get("skull_watch.z_czarna") : Messages.get("skull_watch.z_czerwona")) + Messages.get("skull_watch.czaszka_w_poblizu"));
            }
            if (!red) warned.remove(e.getKey());
        }
        return out;
    }
}
