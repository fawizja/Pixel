package pl.fawizja.pixel.acceptance;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterFactory;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.config.StartingConfig;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.magic.CastResult;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.monster.SpawnTable;
import pl.fawizja.pixel.npc.Npc;
import pl.fawizja.pixel.npc.NpcRegistry;
import pl.fawizja.pixel.npc.NpcRole;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Town;
import pl.fawizja.pixel.world.WorldLayout;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Etap 19: test odbiorczy briefu "Tibia na wlasnej mapie" - jedno miejsce, ktore sprawdza najwazniejsze wymagania
 * wszystkich etapow na prawdziwych danych (konfiguracja serwera, mapa swiata, NPC, spawny, czary).
 */
class BriefAcceptanceTest {

    static WorldMap world;
    static NpcRegistry npcs;
    static MonsterRegistry monsters;
    static StartingConfig config;

    @BeforeAll
    static void load() throws Exception {
        world = TestData.world();
        npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, world, ShopRegistry.loadDefault(TestData.ITEMS));
        monsters = MonsterRegistry.loadDefault(TestData.ITEMS);
        Properties p = new Properties();
        try (InputStream in = StartingConfig.class.getResourceAsStream("/data/starting.properties")) {
            p.load(in);
        }
        config = StartingConfig.fromProperties(p);
    }

    @Test
    void everyVocationStartsAtLevel300WithTenMillionAndTwoBackpacks() {
        for (Vocation v : Vocation.values()) {
            PlayerCharacter pc = new CharacterFactory(config, TestData.ITEMS).create("Brief", v, world.towns().get(0).temple());
            assertEquals(300, pc.level(), v.name());
            assertEquals(10_000_000L, pc.bank().balance(), v.name());
            assertEquals(pc.maxHealth(), pc.health(), v.name());
            assertEquals(2, pc.bagPages(), v + ": main backpack plus one extra");
            assertEquals(40, pc.backpack().size(), v.name());
        }
    }

    @Test
    void utaniHurIsSpokenAndCastByEveryVocation() {
        for (Vocation v : Vocation.values()) {
            PlayerCharacter pc = new CharacterFactory(config, TestData.ITEMS).create("Brief", v, world.towns().get(0).temple());
            GameSession s = new GameSession(TestData.ITEMS, world, pc, null, new Random(1), 8);
            assertEquals("haste", s.spells().registry().byWords("Utani Hur"), v.name());
            int base = pc.speed();
            assertEquals(CastResult.OK, s.castSpell("haste", 1000), v.name());
            assertTrue(pc.speed() > base, v + " is faster after utani hur");
        }
        assertEquals("strong_haste", new GameSession(TestData.ITEMS, world, TestData.newCharacter(Vocation.DRUID), null,
                new Random(1), 8).spells().registry().byWords("utani gran hur"));
    }

    @Test
    void fourCitiesWithTempleBankShopsAndTransport() {
        List<Town> towns = world.towns();
        assertEquals(4, towns.size(), "four cities");
        for (Town t : towns) {
            Set<NpcRole> roles = npcs.all().stream().filter(n -> n.town().equals(t.name())).map(Npc::role)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(NpcRole.class)));
            assertTrue(roles.contains(NpcRole.PRIEST), t.name() + " has a temple priest");
            assertTrue(roles.contains(NpcRole.BANKER), t.name() + " has a bank");
            assertTrue(roles.contains(NpcRole.MERCHANT), t.name() + " has shops");
            assertTrue(roles.contains(NpcRole.CAPTAIN) || roles.contains(NpcRole.CARRIER), t.name() + " has travel");
            assertTrue(world.isProtectionZone(t.temple()), t.name() + " temple is a protection zone");
        }
        long captains = npcs.all().stream().filter(n -> n.role() == NpcRole.CAPTAIN).count();
        assertTrue(captains >= 4, "ships connect ports and islands: " + captains);
    }

    @Test
    void multiLevelDungeonsAndBossesExist() throws Exception {
        long below = world.floors().keySet().stream().filter(z -> z != 0).count();
        assertTrue(below >= 3, "several floors besides the surface: " + world.floors().keySet());
        List<SpawnPoint> spawns = SpawnTable.load(WorldLayout.WORLD_MAP, world, monsters);
        assertTrue(spawns.size() >= 100, "hunting grounds across the map: " + spawns.size());
        Set<String> bosses = spawns.stream().map(sp -> monsters.get(sp.monsterKey())).filter(MonsterType::boss)
                .map(MonsterType::key).collect(Collectors.toSet());
        assertTrue(bosses.size() >= 5, "bosses placed in the world: " + bosses);
        Set<Integer> spawnFloors = spawns.stream().map(sp -> sp.center().z()).collect(Collectors.toSet());
        assertTrue(spawnFloors.size() >= 3, "monsters live on several floors: " + spawnFloors);
    }
}
