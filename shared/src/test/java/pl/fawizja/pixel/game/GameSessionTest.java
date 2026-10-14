package pl.fawizja.pixel.game;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Position;

class GameSessionTest {

    @Test
    void movementIsTimedAndInterpolated() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        GameSession s = new GameSession(TestData.ITEMS, TestData.varden(), pc);
        Position start = pc.position();
        s.update(0, Direction.SOUTH);
        assertEquals(start.translate(0, 1), pc.position());
        assertTrue(s.isMoving());
        float mid = s.renderY(100);
        assertTrue(mid > start.y() && mid < start.y() + 1, "interpolated " + mid);
        s.update(50, Direction.SOUTH); // krok w trakcie - drugi nie startuje
        assertEquals(start.translate(0, 1), pc.position());
        s.update(10_000, Direction.SOUTH);
        assertEquals(start.translate(0, 2), pc.position());
    }

    @Test
    void dropAndPickUpThroughSession() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        GameSession s = new GameSession(TestData.ITEMS, TestData.varden(), pc);
        long gold = s.gold();
        int coinSlot = -1;
        for (int i = 0; i < pc.backpack().size(); i++) {
            if (pc.backpack().get(i) != null && pc.backpack().get(i).itemId() == 3) coinSlot = i;
        }
        s.dropFromSlot(coinSlot, 0);
        assertEquals(0, s.gold());
        assertTrue(s.itemsAtFeet());
        s.pickUpHere(1);
        assertEquals(gold, s.gold());
        assertFalse(s.itemsAtFeet());
        assertTrue(s.log().size() >= 2);
    }

    @Test
    void torchToggleNeedsTorch() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        GameSession s = new GameSession(TestData.ITEMS, TestData.varden(), pc);
        s.toggleTorch(0);
        assertTrue(pc.isTorchLit());
        s.toggleTorch(1);
        assertFalse(pc.isTorchLit());
        pc.backpack().remove(TestData.ITEMS.byKey("torch").id(), 1);
        s.toggleTorch(2);
        assertFalse(pc.isTorchLit());
    }

    @Test
    void invalidSavedPositionFallsBackToSpawn() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        pc.setPosition(new Position(18, 15, 0)); // sciana
        new GameSession(TestData.ITEMS, TestData.varden(), pc);
        assertEquals(TestData.varden().spawn(), pc.position());
    }
}
