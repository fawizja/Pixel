package pl.fawizja.pixel.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

/** Etap 17: podglad pola (dlugie dotkniecie na telefonie, prawy przycisk myszy) opisuje to, co na nim jest. */
class LookAtTest {

    private static GameSession session(MonsterManager mm, Position at) {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        GameSession s = new GameSession(TestData.ITEMS, TestData.varden(), pc, mm, new Random(1), 8);
        s.teleport(at, 0);
        return s;
    }

    @Test
    void describesMonsterWithLevelHealthAndDistance() {
        MonsterManager mm = new MonsterManager(TestData.varden(), MonsterRegistry.loadDefault(TestData.ITEMS),
                Collections.emptyList(), new Random(2));
        Position me = new Position(10, 12, 0);
        GameSession s = session(mm, me);
        List<Monster> spawned = mm.spawnEvent("wolf", new Position(14, 12, 0), 0, 1, 0);
        assertEquals(1, spawned.size());
        Monster wolf = spawned.get(0);
        wolf.damage(wolf.type().maxHealth() / 2);
        String text = s.lookAt(wolf.position(), 0);
        assertTrue(text.startsWith("Widzisz: Wilk (poziom 8, zdrowie "), text);
        assertTrue(text.contains("Odległość: " + wolf.position().distanceTo(me)), text);
        assertTrue(s.log().latest(1).get(0).text().contains("Wilk"), "the look goes to the console");
    }

    @Test
    void describesSelfItemsAndEmptyTiles() {
        Position me = new Position(10, 12, 0);
        GameSession s = session(null, me);
        assertTrue(s.lookAt(me, 0).contains("siebie"));
        Position next = me.translate(1, 0);
        s.ground().place(next, new ItemStack(TestData.ITEMS.byKey("rope").id(), 1));
        String items = s.lookAt(next, 0);
        assertTrue(items.contains(TestData.ITEMS.byKey("rope").name()), items);
        assertFalse(items.contains("Odległość"), "no distance for an adjacent tile");
        String empty = s.lookAt(me.translate(0, 1), 0);
        assertTrue(empty.contains("wolne pole") || empty.contains("przeszkoda"), empty);
    }

    @Test
    void describesNpcWithRole() throws Exception {
        pl.fawizja.pixel.world.WorldMap world = TestData.world();
        pl.fawizja.pixel.npc.ShopRegistry shops = pl.fawizja.pixel.npc.ShopRegistry.loadDefault(TestData.ITEMS);
        pl.fawizja.pixel.npc.NpcRegistry npcs = pl.fawizja.pixel.npc.NpcRegistry.load(
                pl.fawizja.pixel.world.WorldLayout.WORLD_MAP, world, shops);
        GameSession s = new GameSession(TestData.ITEMS, world, TestData.newCharacter(Vocation.DRUID), null, new Random(1), 8);
        s.setTownServices(npcs, shops, null);
        pl.fawizja.pixel.npc.Npc banker = npcs.all().stream()
                .filter(n -> n.role() == pl.fawizja.pixel.npc.NpcRole.BANKER).findFirst().orElseThrow();
        String text = s.lookAt(banker.position(), 0);
        assertTrue(text.contains(banker.name() + " (bankier)"), text);
    }
}
