package pl.fawizja.pixel.gathering;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.magic.ConsumableRegistry;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Layer;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/** Krzaki z jagodami i malinami: kilka zbiorow, potem pusty krzak odrasta godzine; jedzenie jak w Tibii. */
class BerryBushesTest {

    /** Krzak z owocami i wolne pole obok. */
    static Position[] berryBushAndSpot(WorldMap w, BerryBushes.Kind kind) {
        var f = w.floor(0);
        for (int y = 1; y < f.height() - 1; y++) {
            for (int x = 1; x < f.width() - 1; x++) {
                Position b = new Position(x, y, 0);
                if (BerryBushes.kindAt(w, b) != kind) continue;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++)
                        if (f.isWalkable(x + dx, y + dy) && w.transitionAt(b.translate(dx, dy)) == null)
                            return new Position[] {b, b.translate(dx, dy)};
            }
        }
        throw new AssertionError("no berry bush " + kind);
    }

    @Test
    void aboutOneBushInFiveBearsFruitOfBothKinds() {
        WorldMap w = TestData.world();
        int bush = w.tiles().byKey("bush").id();
        var f = w.floor(0);
        int all = 0, blue = 0, red = 0;
        for (int y = 0; y < f.height(); y++) {
            for (int x = 0; x < f.width(); x++) {
                if (f.get(Layer.OBJECT, x, y) != bush) continue;
                all++;
                BerryBushes.Kind k = BerryBushes.kindAt(w, new Position(x, y, 0));
                if (k == BerryBushes.Kind.BLUEBERRY) blue++;
                if (k == BerryBushes.Kind.RASPBERRY) red++;
            }
        }
        assertTrue(all > 100, "bushes: " + all);
        double share = (blue + red) / (double) all;
        assertTrue(share > 0.1 && share < 0.3, "share " + share);
        assertTrue(blue > red && red > 0, blue + " / " + red);
        // tylko krzaki: drzewo ani pole bez obiektu nie ma owocow
        assertNull(BerryBushes.kindAt(w, w.spawn()));
    }

    @Test
    void tappingABushGivesBerriesUntilEmptyThenItRegrowsAfterAnHour() {
        WorldMap w = TestData.world();
        Position[] bs = berryBushAndSpot(w, BerryBushes.Kind.BLUEBERRY);
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        pc.setPosition(bs[1]);
        GameSession s = new GameSession(TestData.ITEMS, w, pc);
        int berry = TestData.ITEMS.byKey("blueberry").id();
        assertTrue(s.usableAt(bs[0]));
        for (int i = 1; i <= BerryBushes.PICKS; i++) {
            assertTrue(s.useAt(bs[0], 1000L * i));
            assertEquals(i * BerryBushes.PER_PICK, pc.backpack().count(berry));
        }
        var last = s.log().latest(1).get(0).text();
        assertTrue(last.contains("pusty"), last);
        assertTrue(s.berries().isEmpty(bs[0], 5000));
        assertTrue(s.useAt(bs[0], 6000));
        assertEquals(BerryBushes.PICKS * BerryBushes.PER_PICK, pc.backpack().count(berry), "empty bush gives nothing");
        assertTrue(s.log().latest(1).get(0).text().contains("odrosną za 60 min"), s.log().latest(1).get(0).text());
        assertTrue(s.berries().isEmpty(bs[0], 3000 + BerryBushes.REGROW_MS - 1));
        assertFalse(s.berries().isEmpty(bs[0], 3000 + BerryBushes.REGROW_MS), "regrown after an hour");
        assertTrue(s.useAt(bs[0], 3000 + BerryBushes.REGROW_MS));
        assertEquals((BerryBushes.PICKS + 1) * BerryBushes.PER_PICK, pc.backpack().count(berry));
    }

    @Test
    void raspberriesAndTheUseButtonToo() {
        WorldMap w = TestData.world();
        Position[] bs = berryBushAndSpot(w, BerryBushes.Kind.RASPBERRY);
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.setPosition(bs[1]);
        GameSession s = new GameSession(TestData.ITEMS, w, pc);
        s.useNearby(0);
        int got = pc.backpack().count(TestData.ITEMS.byKey("raspberry").id())
                + pc.backpack().count(TestData.ITEMS.byKey("blueberry").id());
        assertEquals(BerryBushes.PER_PICK, got, "the Use button picks a fruit bush next to the character");
    }

    @Test
    void aBushTwoFieldsAwayIsOutOfReach() {
        WorldMap w = TestData.world();
        Position[] bs = berryBushAndSpot(w, BerryBushes.Kind.BLUEBERRY);
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        pc.setPosition(bs[0].translate(2 * (bs[1].x() - bs[0].x()), 2 * (bs[1].y() - bs[0].y())));
        GameSession s = new GameSession(TestData.ITEMS, w, pc);
        if (pc.position().distanceTo(bs[0]) < 2) return;          // pole obok to ten sam kierunek
        assertFalse(s.useAt(bs[0], 0));
        assertEquals(0, pc.backpack().count(TestData.ITEMS.byKey("blueberry").id()));
    }

    @Test
    void foodTimesFollowTibia() {
        ConsumableRegistry c = ConsumableRegistry.loadDefault(TestData.ITEMS);
        java.util.function.ToIntFunction<String> sec = k -> c.get(TestData.ITEMS.byKey(k).id()).foodSeconds();
        assertEquals(180, sec.applyAsInt("meat"), "meat 180 s like Tibia");
        assertEquals(120, sec.applyAsInt("bread"));
        assertEquals(144, sec.applyAsInt("fish"));
        assertEquals(12, sec.applyAsInt("blueberry"), "a blueberry feeds 12 s");
        assertEquals(12, sec.applyAsInt("raspberry"));
        // pelna garsc z krzaka syci krocej niz kawalek miesa; rzadkie ryby dluzej niz miesso, krocej niz szynka (360 s)
        assertTrue(BerryBushes.PER_PICK * sec.applyAsInt("blueberry") < sec.applyAsInt("meat"));
        for (String fish : new String[] {"ice_cod", "swamp_eel", "oasis_carp"}) {
            assertTrue(sec.applyAsInt(fish) > sec.applyAsInt("meat") && sec.applyAsInt(fish) < 360, fish);
        }
    }

    @Test
    void eatingBerriesFeedsTwelveSecondsEach() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        pc.setPosition(TestData.world().spawn());
        GameSession s = new GameSession(TestData.ITEMS, TestData.world(), pc);
        int berry = TestData.ITEMS.byKey("blueberry").id();
        assertTrue(pc.backpack().add(new pl.fawizja.pixel.item.ItemStack(berry, 3)).ok());
        long before = pc.fedMs();
        int slot = -1;
        for (int i = 0; i < pc.backpack().size(); i++) {
            if (pc.backpack().get(i) != null && pc.backpack().get(i).itemId() == berry) slot = i;
        }
        s.useSlot(slot, 0);
        assertEquals(before + 12_000, pc.fedMs());
        assertEquals(2, pc.backpack().count(berry));
    }
}
