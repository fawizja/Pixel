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
            Map.entry("podroz", NpcRole.CARRIER), Map.entry("kapitan", NpcRole.CAPTAIN), Map.entry("statek", NpcRole.CAPTAIN),
            Map.entry("rejs", NpcRole.CAPTAIN), Map.entry("dom", NpcRole.HOUSING), Map.entry("domy", NpcRole.HOUSING),
            Map.entry("krol", NpcRole.KING), Map.entry("krolowa", NpcRole.KING), Map.entry("lord", NpcRole.KING),
            Map.entry("wladca", NpcRole.KING), Map.entry("premium", NpcRole.KING), Map.entry("zaklinacz", NpcRole.IMBUER),
            Map.entry("nasycenia", NpcRole.IMBUER), Map.entry("imbuement", NpcRole.IMBUER));

    /** Opiekun zwierzat (kupiec z pupilem miasta). */
    private static final java.util.Set<String> PETS = java.util.Set.of("pupil", "pupile", "zwierzeta", "opiekun");

    private NpcFinder() {}

    public static Npc find(NpcRegistry npcs, String query, Position from) {
        String q = ItemSearch.fold(query);
        if (q.length() < 2) return null;
        NpcRole role = ROLES.get(q);
        boolean pets = PETS.contains(q);
        Npc best = null;
        for (Npc n : npcs.all()) {
            if (n.position().z() != from.z()) continue;
            boolean match = pets ? n.isPetKeeper() : role != null ? n.role() == role : ItemSearch.fold(n.name()).contains(q);
            if (!match) continue;
            if (best == null || n.position().distanceTo(from) < best.position().distanceTo(from)) best = n;
        }
        return best;
    }
}
