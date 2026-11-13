package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.magic.SpellRegistry;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.npc.Npc;
import pl.fawizja.pixel.npc.NpcRegistry;
import pl.fawizja.pixel.npc.ShipAccess;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.quest.QuestKind;
import pl.fawizja.pixel.quest.QuestRegistry;
import pl.fawizja.pixel.quest.QuestType;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Piec wysp (tools/mapgen/islands5.py): Wyspa Szeptow (darmowa, po zadaniu przewoznika) i cztery wyspy premium (kazda
 * z wlasna zgoda kapitana). Zadne z ich nabrzezy nie jest osiagalne pieszo z Varden, rejs bramkuje znacznik.
 */
class IslandsFiveTest {

    record Isle(String key, String captain, String flag, String grantedBy, boolean premium, int x0, int y0, int x1, int y1, int minLevel, int maxLevel) {
        boolean in(int x, int y) { return x >= x0 && x <= x1 && y >= y0 && y <= y1; }
    }

    static final List<Isle> ISLES = List.of(
            new Isle("whisper", "whisper_captain", "ship.whisper", "ferry_m4", false, 894, 94, 926, 155, 8, 60),
            new Isle("ice", "ice5_captain", "ship.ice5", "ice_m4", true, 891, 905, 977, 963, 60, 400),
            new Isle("desert", "desert5_captain", "ship.desert5", "desert_m4", true, 302, 901, 356, 974, 60, 400),
            new Isle("tropic", "tropic5_captain", "ship.tropic5", "tropic_m4", true, 49, 879, 119, 936, 60, 400),
            new Isle("oak", "oak5_captain", "ship.oak5", "oak_m4", true, 161, 836, 234, 889, 60, 400));

    static WorldMap world;
    static NpcRegistry npcs;
    static QuestRegistry quests;
    static MonsterRegistry monsters;
    static Set<Position> fromVarden;
    static final List<String[]> spawns = new ArrayList<>();

    @BeforeAll
    static void load() throws Exception {
        world = TestData.world();
        npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, world, ShopRegistry.loadDefault(TestData.ITEMS));
        monsters = MonsterRegistry.loadDefault(TestData.ITEMS);
        quests = QuestRegistry.loadDefault(TestData.ITEMS, monsters);
        RealmsTest.world = world;
        fromVarden = RealmsTest.explore(world.town("Varden").temple());
        for (String l : SpellRegistry.readLines("/maps/world/spawns.csv")) {
            if (l.isBlank() || l.startsWith("#")) continue;
            spawns.add(l.split(";"));
        }
    }

    static List<String[]> spawnsOf(Isle i) {
        List<String[]> out = new ArrayList<>();
        for (String[] f : spawns) if (i.in(Integer.parseInt(f[1]), Integer.parseInt(f[2]))) out.add(f);
        return out;
    }

    @Test
    void noIslandIsReachableOnFootFromVarden() {
        for (Isle i : ISLES) {
            Npc cap = npcs.get(i.captain());
            assertNotNull(cap, i.captain());
            for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) {
                assertFalse(fromVarden.contains(cap.position().translate(dx, dy)), i.key() + " dock " + dx + "," + dy);
            }
            for (Position p : fromVarden) assertFalse(i.in(p.x(), p.y()), i.key() + " walkable from Varden at " + p);
        }
    }

    @Test
    void theQuayFerrymanIsOnFootFromVardenAndSailsOnlyToTheFreeIsland() throws Exception {
        Npc quay = npcs.get("quay_captain");
        assertTrue(fromVarden.contains(quay.position().translate(0, 1)) || fromVarden.contains(quay.position().translate(1, 0))
                || fromVarden.contains(quay.position().translate(-1, 0)) || fromVarden.contains(quay.position().translate(0, -1)),
                "the quay is reachable from Varden");
        assertFalse(world.town("Varden").temple().distanceTo(quay.position()) < 20, "outside the town");
        assertTrue(ShipAccess.freeFerry("quay_captain"));
        assertTrue(ShipAccess.freeFerry("whisper_captain"));
        assertFalse(ShipAccess.freeFerry("ice5_captain"));
    }

    @Test
    void shipsAreGatedByTheIslandFlagAndPremiumForPremiumIslands() throws Exception {
        for (Isle i : ISLES) {
            assertEquals(i.flag(), ShipAccess.rule(i.captain()).flag(), i.key());
            assertEquals(i.premium(), ShipAccess.rule(i.captain()).premium(), i.key());
            assertEquals(i.flag(), ShipAccess.grantedFlag(i.grantedBy()), i.key());
            assertFalse(ShipAccess.hint(i.captain()).isBlank(), "captain hint " + i.key());
            PlayerCharacter free = TestData.newCharacter(Vocation.KNIGHT);
            PlayerCharacter prem = TestData.newCharacter(Vocation.KNIGHT);
            prem.setPremium(true);
            String from = i.premium() ? "varden_captain" : "quay_captain";
            if (i.premium()) {
                assertEquals(ShipAccess.Block.PREMIUM, ShipAccess.check(free, from, i.captain()), i.key());
                free.setQuestFlag(i.flag());
                assertEquals(ShipAccess.Block.PREMIUM, ShipAccess.check(free, from, i.captain()), "flag alone is not enough: " + i.key());
            }
            assertEquals(ShipAccess.Block.FLAG, ShipAccess.check(prem, from, i.captain()), i.key());
            prem.setQuestFlag(i.flag());
            assertEquals(ShipAccess.Block.NONE, ShipAccess.check(prem, from, i.captain()), i.key());
            if (!i.premium()) {
                PlayerCharacter f2 = TestData.newCharacter(Vocation.KNIGHT);
                assertEquals(ShipAccess.Block.FLAG, ShipAccess.check(f2, from, i.captain()));
                f2.setQuestFlag(i.flag());
                assertEquals(ShipAccess.Block.NONE, ShipAccess.check(f2, from, i.captain()), "the free island needs no premium");
            }
            // powrot z wyspy nie wymaga znacznika celu
            assertEquals(ShipAccess.Block.NONE, ShipAccess.check(prem, i.captain(), from));
        }
    }

    @Test
    void theCaptainRefusesInGameUntilTheFlagIsSet() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        pc.setPremium(true);
        MonsterManager mm = new MonsterManager(world, monsters, List.of(), new Random(1));
        GameSession s = new GameSession(TestData.ITEMS, world, pc, mm, new Random(1), 8);
        s.setTownServices(npcs, ShopRegistry.loadDefault(TestData.ITEMS), null);
        Npc cap = npcs.get("oak5_captain").town() == null ? null : npcs.get("varden_captain");
        s.teleport(cap.position().translate(0, -1), 0);
        Position dock = pc.position();
        String town = npcs.get("oak5_captain").town();
        assertFalse(s.travel(town, 100), "no flag, no passage");
        assertEquals(dock, pc.position());
        pc.setQuestFlag("ship.oak5");
        assertTrue(s.travel(town, 200));
        assertTrue(pc.position().distanceTo(npcs.get("oak5_captain").position()) <= 1);
        assertFalse(fromVarden.contains(pc.position()));
    }

    @Test
    void islandsHaveFloorsAndSpawnsInTheirLevelBands() {
        for (Isle i : ISLES) {
            List<String[]> sp = spawnsOf(i);
            assertTrue(sp.size() >= 30, i.key() + " spawns " + sp.size());
            Set<Integer> floors = new HashSet<>();
            for (String[] f : sp) floors.add(Integer.parseInt(f[3]));
            assertTrue(floors.contains(0), i.key() + " surface");
            long under = floors.stream().filter(z -> z < 0).count();
            assertTrue(under >= (i.premium() ? 3 : 2), i.key() + " underground floors with spawns " + floors);
            int lo = Integer.MAX_VALUE, hi = 0;
            for (String[] f : sp) {
                int lvl = monsters.get(f[0]).level();
                lo = Math.min(lo, lvl);
                hi = Math.max(hi, lvl);
                assertTrue(monsters.get(f[0]) != null, f[0]);
            }
            assertTrue(lo >= i.minLevel() - 45 && hi <= i.maxLevel() + 200, i.key() + " levels " + lo + ".." + hi);
            // pola spawnu sa chodliwe na swojej kondygnacji
            for (String[] f : sp) {
                Position p = new Position(Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]));
                assertTrue(world.floorWalkable(p), i.key() + " spawn centre " + p + " " + f[0]);
            }
        }
    }

    @Test
    void islandMonstersExistWithLootAndAreOnlyHandPlaced() {
        for (String k : List.of("shore_scuttler", "whisper_shade", "cairn_wight", "cairn_lord", "magpie_king", "frost_lurker",
                "rimecloak_pilgrim", "rime_jarl", "dune_skulker", "obelisk_priest", "sand_regent", "canopy_lurker", "orchid_mimic",
                "vine_empress", "briar_boar", "moor_crone", "oak_sovereign")) {
            assertNotNull(monsters.get(k), k);
            assertFalse(monsters.get(k).loot().isEmpty(), k + " loot");
            boolean placed = false;
            for (String[] f : spawns) if (f[0].equals(k)) placed = true;
            assertTrue(placed, k + " has a spawn");
        }
    }

    @Test
    void questChainsCompleteInOrderAndAccessQuestsSetTheFlags() throws Exception {
        for (Isle i : ISLES) {
            QuestType grant = quests.get(i.grantedBy());
            assertNotNull(grant, i.grantedBy());
            // lancuch wstecz do korzenia, bez cykli, kazdy wymog istnieje
            Set<String> seen = new HashSet<>();
            List<String> todo = new ArrayList<>(List.of(i.grantedBy()));
            while (!todo.isEmpty()) {
                String k = todo.remove(todo.size() - 1);
                if (!seen.add(k)) continue;
                assertNotNull(quests.get(k), "missing quest " + k);
                for (String r : quests.prereqs(k)) todo.add(r);
                if (quests.get(k).requires() != null && !quests.get(k).requires().isBlank()) todo.add(quests.get(k).requires());
            }
            assertTrue(seen.size() >= 4, i.key() + " access chain " + seen);
            assertTrue(grant.description().length() > 20);
        }
        long visit = quests.all().stream().filter(q -> q.kind() == QuestKind.VISIT).count();
        assertTrue(visit > 0);
        // zadania dostepowe: min. jedno z wymogiem drużyny (party:N) w star_quest.csv
        boolean party = false;
        for (String l : SpellRegistry.readLines("/maps/world/star_quest.csv")) if (l.startsWith("gate;") && l.contains("party:")) party = true;
        assertTrue(party, "party door exists");
    }

    @Test
    void everyKillTargetOfAnIslandLineLivesOnThatIslandOrIsKnown() {
        for (QuestType q : quests.all()) {
            if (q.kind() != QuestKind.KILL || !q.key().matches("(ferry|ice|desert|tropic|oak)_m\\d+")) continue;
            assertNotNull(monsters.get(q.target()), q.key() + " target " + q.target());
            boolean spawned = false;
            for (String[] f : spawns) if (f[0].equals(q.target())) spawned = true;
            assertTrue(spawned, q.key() + " target " + q.target() + " has a spawn");
        }
    }

    @Test
    void theBarbarianCampsExistWithTowersABossAndIrregularShapes() {
        // duzy oboz na wyspie lodowej (premium): pietra 0, +1, +2
        int x0 = 905, y0 = 925, x1 = 945, y1 = 962;
        for (int z : new int[] {1, 2}) {
            int n = 0, minX = 9999, minY = 9999, maxX = 0, maxY = 0;
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
                if (!world.floorWalkable(new Position(x, y, z))) continue;
                n++;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            }
            assertTrue(n >= 12, "camp floor +" + z + " cells " + n);
            // wiele osobnych wiez, nie jeden prostokat: wypelnienie prostokata otaczajacego jest niskie
            double fill = n / (double) ((maxX - minX + 1) * (maxY - minY + 1));
            assertTrue(fill < 0.6, "camp floor +" + z + " is not a rectangle, fill " + fill);
        }
        List<String[]> inCamp = new ArrayList<>();
        for (String[] f : spawns) if (Integer.parseInt(f[1]) >= x0 && Integer.parseInt(f[1]) <= x1 && Integer.parseInt(f[2]) >= y0 && Integer.parseInt(f[2]) <= y1) inCamp.add(f);
        Set<String> kinds = new HashSet<>();
        for (String[] f : inCamp) if (f[3].equals("0") || f[3].equals("1") || f[3].equals("2")) kinds.add(f[0]);
        assertTrue(kinds.containsAll(Set.of("blizzard_warchief", "icefang_hunter", "bonewrap_tamer", "ironjaw_splitter", "rimeblood_walker")), "camp monsters " + kinds);
        assertTrue(inCamp.stream().anyMatch(f -> f[3].equals("2") && f[0].equals("icefang_hunter")), "archers on the +2 platform");
        assertTrue(monsters.get("blizzard_warchief").boss());
        assertTrue(world.regions().stream().anyMatch(r -> r.name().equals("Obóz Wodza Zamieci")));
        // nagroda: wybor jednej z pieciu skrzyn po pokonaniu wodza (z tabliczkami)
        assertTrue(world.rewardChoices().stream().filter(c -> c.position().z() == 0 && c.position().x() >= x0 && c.position().x() <= x1
                && c.position().y() >= y0 && c.position().y() <= y1).count() >= 5, "reward chests of the warchief");
        // maly darmowy oboz na kontynencie: osiagalny pieszo z Varden
        String[] hetman = null;
        for (String[] f : spawns) if (f[0].equals("skarn_hetman")) hetman = f;
        assertNotNull(hetman);
        Position hp = new Position(Integer.parseInt(hetman[1]), Integer.parseInt(hetman[2]), 0);
        assertTrue(fromVarden.contains(hp), "the small camp is free land, reachable on foot");
        assertTrue(world.regions().stream().anyMatch(r -> r.name().equals("Obóz Hetmana Skarna")));
        assertFalse(monsters.get("skarn_hetman").level() > 60);
    }
}
