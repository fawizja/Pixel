package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.monster.SpawnTable;
import pl.fawizja.pixel.movement.MoveResult;
import pl.fawizja.pixel.movement.MovementSystem;
import pl.fawizja.pixel.npc.Npc;
import pl.fawizja.pixel.npc.NpcRegistry;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Twierdza Krwawego Kla (orki, pietra -3..+3, drzwi poziomu 50 do skarbca) i Elandor, osada elfow (pietra -2..+3)
 * z tools/mapgen/strongholds.py: wszystko osiagalne pieszo z Varden wg logiki ruchu (kroki, drzwi, schody, drabiny).
 */
class StrongholdsTest {

    static WorldMap world;
    static List<SpawnPoint> spawns;
    static final MonsterRegistry MONSTERS = MonsterRegistry.loadDefault(TestData.ITEMS);
    static Set<Position> lv49, lv50;
    static Region fortress, fortressUnder, village, cellars;

    @BeforeAll
    static void load() throws Exception {
        world = TestData.world();
        spawns = SpawnTable.load(WorldLayout.WORLD_MAP, world, MONSTERS);
        fortress = region("Twierdza Krwawego Kła");
        fortressUnder = region("Podziemia Twierdzy Krwawego Kła");
        village = region("Elandor, osada elfów");
        cellars = region("Piwnice Elandoru");
        lv49 = explore(world.spawn(), 49);
        lv50 = explore(world.spawn(), 50);
    }

    static Region region(String name) {
        return world.regions().stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow(() -> new AssertionError(name));
    }

    static Set<Position> explore(Position start, int level) {
        MovementSystem ms = new MovementSystem(world, TestData.ITEMS.byKey("rope").id());
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        if (level > pc.level()) pc.addExperience(Balance.experienceForLevel(level) - pc.experience());
        assertEquals(level, pc.level());
        Set<Position> seen = new HashSet<>();
        ArrayDeque<Position> q = new ArrayDeque<>();
        seen.add(start);
        q.add(start);
        while (!q.isEmpty()) {
            Position p = q.poll();
            for (Direction d : Direction.values()) {
                pc.setPosition(p);
                if (TestData.stepOpeningDoors(world, ms, pc, d).moved() && seen.add(pc.position())) q.add(pc.position());
            }
            pc.setPosition(p);
            if (ms.use(pc) == MoveResult.CHANGED_FLOOR && seen.add(pc.position())) q.add(pc.position());
        }
        return seen;
    }

    static boolean near(Set<Position> seen, Position p) {
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) if (seen.contains(p.translate(dx, dy))) return true;
        return false;
    }

    static Map.Entry<Position, Integer> questDoor() {
        return world.levelDoors().entrySet().stream().filter(e -> e.getValue() == 50 && fortressUnder.contains(e.getKey()))
                .findFirst().orElseThrow(() -> new AssertionError("no level 50 door in the fortress"));
    }

    @Test
    void everyFortressFloorFromMinus3ToPlus3IsReachableFromVarden() {
        for (int z = 0; z <= 3; z++) {
            final int fz = z;
            assertTrue(lv49.stream().anyMatch(p -> p.z() == fz && fortress.contains(p)), "fortress floor " + z);
        }
        for (int z = -3; z <= -1; z++) {
            final int fz = z;
            assertTrue(lv49.stream().anyMatch(p -> p.z() == fz && fortressUnder.contains(p)), "fortress underground " + z);
        }
    }

    @Test
    void theQuestRoomOpensOnlyFromLevel50AndHoldsTheRewardChest() {
        Position door = questDoor().getKey();
        assertEquals(-3, door.z());
        assertTrue(near(lv49, door), "the gate itself can be reached");
        WorldMap.LootChest reward = world.lootChests().stream().filter(c -> "warlord_helmet".equals(c.itemKey()))
                .findFirst().orElseThrow();
        assertTrue(fortressUnder.contains(reward.position()));
        assertFalse(near(lv49, reward.position()), "level 49 reached the treasury");
        assertTrue(near(lv50, reward.position()), "level 50 reaches the treasury");
        SpawnPoint boss = spawns.stream().filter(s -> s.monsterKey().equals("urgoth")).findFirst().orElseThrow();
        assertTrue(MONSTERS.get("urgoth").boss());
        assertFalse(lv49.contains(boss.center()), "the boss hall is behind the gate");
        assertTrue(lv50.contains(boss.center()));
    }

    @Test
    void manyDifferentOrcsAndTheDeepestFloorsHoldTheStrongest() {
        List<SpawnPoint> mine = spawns.stream().filter(s -> fortress.contains(s.center()) || fortressUnder.contains(s.center())).toList();
        Set<String> kinds = mine.stream().map(SpawnPoint::monsterKey).collect(Collectors.toSet());
        for (String k : new String[] {"orc", "orc_spearman", "orc_warrior", "orc_shaman", "orc_rider", "orc_berserker",
                "orc_marauder", "orc_leader", "orc_cult_priest", "orc_cult_fanatic", "orc_warlord", "urgoth"}) {
            assertTrue(kinds.contains(k), "fortress has " + k);
        }
        assertTrue(mine.stream().allMatch(s -> s.monsterKey().startsWith("orc") || s.monsterKey().equals("urgoth")), "only orcs: " + kinds);
        double prev = 0;
        int prevCount = 0;
        for (int z = 0; z >= -3; z--) {
            final int fz = z;
            List<SpawnPoint> f = mine.stream().filter(s -> s.center().z() == fz && !MONSTERS.get(s.monsterKey()).boss()).toList();
            double avg = f.stream().mapToInt(s -> MONSTERS.get(s.monsterKey()).level()).average().orElse(0);
            int count = f.stream().mapToInt(SpawnPoint::count).sum();
            assertTrue(avg > prev, "floor " + z + " stronger on average (" + avg + " vs " + prev + ")");
            if (z < -1) assertTrue(count >= prevCount * 0.8, "floor " + z + " still crowded");
            prev = avg;
            prevCount = count;
        }
        int all = mine.stream().mapToInt(SpawnPoint::count).sum();
        assertTrue(all >= 150, "lots of orcs: " + all);
        for (SpawnPoint s : mine) assertTrue(lv50.contains(s.center()) || near(lv50, s.center()), "spawn reachable " + s);
    }

    @Test
    void newOrcAndElfTypesAreTibiaLikeAndScaled() {
        String[] order = {"orc", "orc_spearman", "orc_warrior", "orc_rider", "orc_berserker", "orc_marauder", "orc_leader",
                "orc_cult_priest", "orc_cult_fanatic"};
        int prev = 0;
        for (String k : order) {
            MonsterType m = MONSTERS.get(k);
            assertNotNull(m, k);
            assertTrue(m.level() >= prev, k + " rises in level");
            prev = m.level();
        }
        for (String k : new String[] {"elf", "elf_scout", "elf_arcanist"}) assertNotNull(MONSTERS.get(k), k);
        assertTrue(MONSTERS.get("elf").level() < MONSTERS.get("elf_scout").level());
        assertTrue(MONSTERS.get("elf_scout").level() < MONSTERS.get("elf_arcanist").level());
    }

    @Test
    void elandorHasFloorsUpAndDownAndFriendlyNpcs() throws Exception {
        Set<Position> lv8 = explore(world.spawn(), 8);
        for (int z = -2; z <= 3; z++) {
            final int fz = z;
            Region r = z < 0 ? cellars : village;
            assertTrue(lv8.stream().anyMatch(p -> p.z() == fz && r.contains(p) && world.isProtectionZone(p)),
                    "Elandor floor " + z + " reachable and protected");
        }
        NpcRegistry npcs = NpcRegistry.load("world", world, ShopRegistry.loadDefault(TestData.ITEMS));
        List<Npc> elves = npcs.all().stream().filter(n -> n.town().equals("Elandor")).toList();
        assertTrue(elves.size() >= 5, "Elandor NPCs " + elves.size());
        Set<pl.fawizja.pixel.npc.NpcRole> roles = elves.stream().map(Npc::role).collect(Collectors.toSet());
        // kaplan (odrodzenie) jest tylko w miastach; w swiatyni Elandoru kaplanka-przewodniczka
        assertTrue(roles.containsAll(List.of(pl.fawizja.pixel.npc.NpcRole.GUIDE, pl.fawizja.pixel.npc.NpcRole.BANKER,
                pl.fawizja.pixel.npc.NpcRole.MERCHANT)), "roles " + roles);
        assertTrue(elves.stream().anyMatch(n -> n.position().z() == 3), "elder on top of the Great Tree");
        for (Npc n : elves) {
            assertTrue(world.isProtectionZone(n.position()), n.key() + " in PZ");
            assertTrue(lv8.stream().anyMatch(p -> p.z() == n.position().z() && p.distanceTo(n.position()) <= Npc.TALK_RANGE),
                    n.key() + " reachable");
        }
        // w osadzie nie ma potworow, elfy-wygnancy kreca sie w lesie dookola (takze na czatowniach +1)
        for (SpawnPoint s : spawns) assertFalse(world.isProtectionZone(s.center()) && village.contains(s.center()), "monster in Elandor " + s);
        List<SpawnPoint> exiles = spawns.stream().filter(s -> s.monsterKey().startsWith("elf") && village.contains(s.center())).toList();
        assertTrue(exiles.stream().map(SpawnPoint::monsterKey).collect(Collectors.toSet()).containsAll(List.of("elf", "elf_scout", "elf_arcanist")),
                "exiles " + exiles);
        assertTrue(exiles.stream().anyMatch(s -> s.center().z() == 1), "scouts on tree stands");
    }
}
