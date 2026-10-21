package pl.fawizja.pixel.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.vocation.Vocation;

class HuntingAdvisorTest {
    @Test
    void suggestsMonstersCloseToTheCharactersLevel() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        pl.fawizja.pixel.world.WorldMap world = new pl.fawizja.pixel.world.MapLoader(pl.fawizja.pixel.world.TileRegistry.loadDefault())
                .load(pl.fawizja.pixel.world.WorldLayout.WORLD_MAP);
        pl.fawizja.pixel.monster.MonsterRegistry reg = pl.fawizja.pixel.monster.MonsterRegistry.loadDefault(TestData.ITEMS);
        pl.fawizja.pixel.monster.MonsterManager mm = new pl.fawizja.pixel.monster.MonsterManager(world, reg,
                pl.fawizja.pixel.monster.SpawnTable.load(pl.fawizja.pixel.world.WorldLayout.WORLD_MAP, world, reg), new java.util.Random(1));
        GameSession s = new GameSession(TestData.ITEMS, world, pc, mm, new java.util.Random(2), 8);
        List<String[]> low = HuntingAdvisor.rows(s);
        assertFalse(low.isEmpty());
        assertTrue(low.size() <= HuntingAdvisor.SHOWN);
        for (String[] r : low) assertTrue(level(r) <= 40, "a level " + pc.level() + " character gets easy monsters: " + r[0] + " " + r[1]);
        while (pc.level() < 300) pc.addExperience(500_000_000L);
        List<String[]> high = HuntingAdvisor.rows(s);
        assertTrue(high.stream().anyMatch(r -> level(r) >= 200), "level " + pc.level());
        assertTrue(high.stream().noneMatch(r -> r[0].startsWith("Mor'gath")), "bosses are not hunting grounds");
    }

    private static int level(String[] r) {
        String d = r[1].substring("poz. ".length());
        return Integer.parseInt(d.substring(0, d.indexOf(',')));
    }
}
