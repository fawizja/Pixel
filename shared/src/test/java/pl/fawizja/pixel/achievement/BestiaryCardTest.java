package pl.fawizja.pixel.achievement;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.MonsterType;

class BestiaryCardTest {
    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);

    private static String find(List<String[]> rows, String label) {
        for (String[] r : rows) if (r[0].equals(label)) return r[1];
        return null;
    }

    @Test
    void detailsUnlockByTibiaStages() {
        // wilk = Tibia Wolf (1 gwiazdka, Trivial): etapy od 1, 10, 100 zabic, ukonczony 250
        MonsterType wolf = REG.get("wolf");
        List<String[]> none = BestiaryCard.rows(wolf, 0, TestData.ITEMS);
        assertEquals(3, none.size(), "unknown monster: kills, class, 'unknown'");
        assertNotNull(find(none, "Nieznany"));

        List<String[]> s1 = BestiaryCard.rows(wolf, 3, TestData.ITEMS, "Krainy Varden", null);
        assertEquals("3 / 250 (etap 1/4)", find(s1, "Pokonano"));
        assertEquals("Ssaki, *---- banalny, pospolity", find(s1, "Klasa i trudność"));
        assertEquals("pokonaj jeszcze 7: statystyki, łupy pospolite i niepospolite", find(s1, "Etap 2"));
        assertNull(find(s1, "Zdrowie"), "stats from stage 2");
        assertNull(find(s1, "Występuje"), "locations from stage 3");
        assertNull(find(s1, TestData.ITEMS.byKey("gold_coin").name()), "stage 1 shows loot only as ?");
        assertNotNull(find(s1, "?"));

        List<String[]> s2 = BestiaryCard.rows(wolf, 10, TestData.ITEMS, "Krainy Varden", null);
        assertEquals("40 HP, 18 exp, poziom 8", find(s2, "Zdrowie"));
        assertEquals("wręcz, szybkość 250, pancerz 2", find(s2, "Walka"));
        assertEquals("5 (za ukończenie)", find(s2, "Punkty charmów"));
        assertNull(find(s2, "Odporności"));
        assertEquals("pospolity, 3-23 szt.", find(s2, TestData.ITEMS.byKey("gold_coin").name()), "rarity instead of %");
        assertEquals("niepospolity", find(s2, TestData.ITEMS.byKey("wolf_pelt").name()));

        List<String[]> s3 = BestiaryCard.rows(wolf, 100, TestData.ITEMS, "Krainy Varden", "Okaleczenie I");
        assertEquals("lód -10%, święte +30%, śmierć -5%, pancerz 2", find(s3, "Odporności"));
        assertEquals("Krainy Varden", find(s3, "Występuje"));
        assertEquals("Okaleczenie I", find(s3, "Charmy"));

        List<String[]> done = BestiaryCard.rows(wolf, 250, TestData.ITEMS);
        assertEquals("250 / 250 (etap 4/4)", find(done, "Pokonano"));
        assertEquals("5 (zdobyte)", find(done, "Punkty charmów"));
        assertNull(find(done, "?"), "completed entry shows all loot");
    }

    @Test
    void lootRarityCategoriesFollowTibia() {
        assertEquals(LootRarity.COMMON, LootRarity.of(250));
        assertEquals(LootRarity.UNCOMMON, LootRarity.of(249));
        assertEquals(LootRarity.UNCOMMON, LootRarity.of(50));
        assertEquals(LootRarity.SEMI_RARE, LootRarity.of(49));
        assertEquals(LootRarity.SEMI_RARE, LootRarity.of(10));
        assertEquals(LootRarity.RARE, LootRarity.of(9));
        assertEquals(LootRarity.RARE, LootRarity.of(2));
        assertEquals(LootRarity.VERY_RARE, LootRarity.of(1));
        assertEquals(2, LootRarity.UNCOMMON.revealStage());
        assertEquals(3, LootRarity.SEMI_RARE.revealStage());
        assertEquals(4, LootRarity.RARE.revealStage());
    }

    @Test
    void bossesHaveNoBestiaryEntry() {
        MonsterType boss = REG.get("grimtooth");
        assertNull(BestiaryData.of(boss));
        assertEquals("bossowie nie mają wpisu ani charmów", find(BestiaryCard.rows(boss, 1, TestData.ITEMS), "Bestiariusz"));
    }

    @Test
    void percentsAndResistances() {
        assertEquals("0,5%", BestiaryCard.percent(5));
        assertEquals("100%", BestiaryCard.percent(1000));
        assertEquals("pancerz 6", BestiaryCard.resistances(REG.get("boar")), "no resistances, only armor (Tibia boar)");
        assertEquals("lód -10%, święte +30%, śmierć -5%, pancerz 2", BestiaryCard.resistances(REG.get("wolf")), "Tibia wolf");
        String golem = BestiaryCard.resistances(REG.get("ice_golem"));
        assertFalse(golem.isEmpty());
        assertTrue(golem.contains("lód") || golem.contains("pancerz"), golem);
    }

    @Test
    void habitatsNameTheRegionsWhereTheMonsterSpawns() {
        pl.fawizja.pixel.world.WorldMap world = pl.fawizja.pixel.TestData.world();
        java.util.List<pl.fawizja.pixel.monster.SpawnPoint> spawns = java.util.List.of(
                new pl.fawizja.pixel.monster.SpawnPoint("wolf", new pl.fawizja.pixel.world.Position(477, 491, 0), 4, 2, 60),
                new pl.fawizja.pixel.monster.SpawnPoint("wolf", new pl.fawizja.pixel.world.Position(481, 513, 0), 3, 2, 60),
                new pl.fawizja.pixel.monster.SpawnPoint("wolf", new pl.fawizja.pixel.world.Position(600, 165, 0), 3, 2, 60, true),
                new pl.fawizja.pixel.monster.SpawnPoint("bat", new pl.fawizja.pixel.world.Position(760, 100, -1), 3, 2, 60));
        assertEquals("Krainy Varden, Mrozne Pustkowia nocą", BestiaryCard.habitats("wolf", spawns, world));
        assertEquals("Lodowe Jaskinie", BestiaryCard.habitats("bat", spawns, world));
        assertEquals("", BestiaryCard.habitats("dragon", spawns, world));
    }
}
