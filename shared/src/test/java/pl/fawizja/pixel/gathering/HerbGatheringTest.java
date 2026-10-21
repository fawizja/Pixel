package pl.fawizja.pixel.gathering;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.Actions;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Layer;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

class HerbGatheringTest {

    /** Pierwszy krzak swiata i wolne pole obok niego. */
    private static Position[] bushAndSpot(WorldMap w) {
        int bush = w.tiles().byKey("bush").id();
        var f = w.floor(0);
        for (int y = 1; y < f.height() - 1; y++) {
            for (int x = 1; x < f.width() - 1; x++) {
                if (f.get(Layer.OBJECT, x, y) != bush) continue;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (f.isWalkable(x + dx, y + dy)) return new Position[] {new Position(x, y, 0), new Position(x + dx, y + dy, 0)};
                    }
                }
            }
        }
        throw new AssertionError("no bush");
    }

    @Test
    void useNextToABushGivesHerbsThenTheBushRegrows() {
        WorldMap w = TestData.world();
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        GameSession s = new GameSession(TestData.ITEMS, w, pc);
        Position[] bs = bushAndSpot(w);
        pc.setPosition(bs[1]);
        int herb = TestData.ITEMS.byKey("healing_herb").id();

        assertTrue(Actions.apply(s, "use", 1000));
        int got = pc.backpack().count(herb);
        assertTrue(got >= 1 && got <= 2, "1-2 herbs, got " + got);

        Actions.apply(s, "use", 2000);
        assertEquals(got, pc.backpack().count(herb), "the bush must regrow first");
        var last = s.log().latest(1); assertTrue(last.get(last.size() - 1).text().contains("odrośnie"), last.toString());

        Actions.apply(s, "use", 1000 + HerbGathering.REGROW_MS);
        assertTrue(pc.backpack().count(herb) > got, "regrown after 10 minutes");
        assertEquals(pc.backpack().count(herb), pc.stats().herbs(), "herbs are counted for the Zielarz achievement");
        pl.fawizja.pixel.character.CharacterStats back = new pl.fawizja.pixel.character.CharacterStats();
        back.restore(pc.stats().encode());
        assertEquals(pc.stats().herbs(), back.herbs());
        back.recordHerbs(50);
        pc.stats().copyFrom(back);
        assertTrue(pl.fawizja.pixel.achievement.Achievement.HERBS_50.reached(pc));
    }

    @Test
    void noBushNearbyIsNotAHerbAction() {
        WorldMap w = TestData.world();
        HerbGathering h = new HerbGathering(w);
        assertEquals(HerbGathering.Outcome.NO_BUSH, h.gather(w.spawn(), new java.util.Random(1), 0).outcome());
    }

    @Test
    void fourHerbsMakeAHealthPotion() {
        var r = pl.fawizja.pixel.magic.RecipeRegistry.loadDefault(TestData.ITEMS).get("herbal_health_potion");
        assertEquals(TestData.ITEMS.byKey("health_potion").id(), r.resultItemId());
        assertEquals(4, r.ingredients().get(TestData.ITEMS.byKey("healing_herb").id()));
    }
}
