package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.monster.SpawnTable;
import pl.fawizja.pixel.npc.Npc;
import pl.fawizja.pixel.npc.NpcRegistry;
import pl.fawizja.pixel.npc.ShopRegistry;

/** Caly swiat jest spojny: z pierwszej swiatyni da sie dojsc do kazdej swiatyni, NPC, spawnu i z kazdego przejscia wrocic. */
class WorldConnectivityTest {

    @Test
    void everyTownNpcSpawnAndTransitionIsReachable() throws Exception {
        WorldMap w = TestData.world();
        Reachability r = Reachability.from(w, w.spawn(), List.of());
        List<String> missing = new ArrayList<>();
        for (Town t : w.towns()) if (!r.reached(t.temple())) missing.add("swiatynia " + t.name());
        NpcRegistry npcs = NpcRegistry.load("world", w, ShopRegistry.loadDefault(TestData.ITEMS));
        for (Npc n : npcs.all()) if (!r.reachedWithin(n.position(), Npc.TALK_RANGE)) missing.add("NPC " + n.key() + " " + n.position());
        for (SpawnPoint s : SpawnTable.load("world", w, MonsterRegistry.loadDefault(TestData.ITEMS))) {
            if (!r.reachedNear(s.center())) missing.add("spawn " + s.monsterKey() + " " + s.center());
        }
        for (FloorMap f : w.floors().values()) {
            for (Transition t : f.transitions().values()) {
                if (!r.reached(t.from())) missing.add("przejscie " + t.from());
            }
        }
        assertTrue(missing.isEmpty(), "nieosiagalne: " + missing);
    }
}
