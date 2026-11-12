package pl.fawizja.pixel.treasure;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.monster.SpawnTable;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.WorldLayout;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Audyt skrzyn zadan (analizy/questy-nagrody.md): kazda skrzynia zadania oplaca sie wzgledem trudnosci miejsca i jest
 * lepsza od zwyklej skrzyni lupu na tym samym poziomie; wartosc nagrody (zloto + przedmioty wg value) rosnie z trudnoscia.
 * Ze zmienna srodowiska QUEST_AUDIT_OUT=plik zapisuje tabele (kategoria, trudnosc, zloto, wartosc przedmiotow wg profesji).
 */
class QuestRewardAuditTest {

    @Test
    void everyQuestChestPaysMoreThanALootChestAtItsDifficulty() throws Exception {
        WorldMap world = TestData.world();
        MonsterRegistry monsters = MonsterRegistry.loadDefault(TestData.ITEMS);
        MonsterManager mm = new MonsterManager(world, monsters, SpawnTable.load(WorldLayout.WORLD_MAP, world, monsters), new Random(1));
        TreasureChests chests = TreasureChests.generate(world);
        List<String> rows = new ArrayList<>();
        int quest = 0;
        for (TreasureChests.Chest c : chests.all()) {
            ChestRewards.Kind kind = ChestRewards.kind(c, world, mm, TestData.ITEMS);
            boolean choice = world.rewardChoiceAt(c.position()) != null;
            int d = ChestRewards.difficulty(c, world, mm);
            long minValue = Long.MAX_VALUE;
            long maxValue = 0;
            long gold = 0;
            for (Vocation v : Vocation.values()) {
                if (c.vocation() != null && c.vocation() != v) continue;
                ChestRewards.Reward r = ChestRewards.reward(c, world, mm, TestData.ITEMS, v);
                long value = r.gold();
                for (ItemStack s : r.items()) value += (long) TestData.ITEMS.get(s.itemId()).value() * s.count();
                minValue = Math.min(minValue, value);
                maxValue = Math.max(maxValue, value);
                gold = r.gold();
                if (kind == ChestRewards.Kind.QUEST && !choice) {
                    assertTrue(r.gold() >= ChestRewards.questGold(d), c.id() + " quest gold");
                    assertTrue(value > ChestRewards.lootGold(d) * 3, c.id() + " " + v + " quest chest beats a loot chest: " + value + " at difficulty " + d);
                }
            }
            if (kind == ChestRewards.Kind.QUEST) quest++;
            rows.add(String.join(";", c.id(), choice ? "CHOICE" : kind.name(), String.valueOf(d), String.valueOf(c.position().z()),
                    String.valueOf(gold), String.valueOf(minValue), String.valueOf(maxValue)));
        }
        assertTrue(quest >= 60, "quest chests: " + quest);
        String out = System.getenv("QUEST_AUDIT_OUT");
        if (out != null) Files.write(Path.of(out), rows);
    }
}
