package pl.fawizja.pixel.npc;

import java.util.Map;
import pl.fawizja.pixel.inventory.ItemSearch;
import pl.fawizja.pixel.world.Position;

/** /idź: najblizszy NPC na tym pietrze po roli ("kupiec", "bank") albo fragmencie imienia. */
public final class NpcFinder {

    private static final Map<String, NpcRole> ROLES = Map.ofEntries(
            Map.entry("kupiec", NpcRole.MERCHANT), Map.entry("sklep", NpcRole.MERCHANT), Map.entry("kowal", NpcRole.MERCHANT),
            Map.entry("bank", NpcRole.BANKER), Map.entry("bankier", NpcRole.BANKER), Map.entry("depozyt", NpcRole.BANKER),
            Map.entry("rynek", NpcRole.BANKER), Map.entry("kaplan", NpcRole.PRIEST), Map.entry("swiatynia", NpcRole.PRIEST),
            Map.entry("przewodnik", NpcRole.GUIDE), Map.entry("zadania", NpcRole.GUIDE), Map.entry("woznica", NpcRole.CARRIER),
            Map.entry("podroz", NpcRole.CARRIER), Map.entry("dom", NpcRole.HOUSING), Map.entry("domy", NpcRole.HOUSING));

    private NpcFinder() {}

    public static Npc find(NpcRegistry npcs, String query, Position from) {
        String q = ItemSearch.fold(query);
        if (q.length() < 2) return null;
        NpcRole role = ROLES.get(q);
        Npc best = null;
        for (Npc n : npcs.all()) {
            if (n.position().z() != from.z()) continue;
            boolean match = role != null ? n.role() == role : ItemSearch.fold(n.name()).contains(q);
            if (!match) continue;
            if (best == null || n.position().distanceTo(from) < best.position().distanceTo(from)) best = n;
        }
        return best;
    }
}
