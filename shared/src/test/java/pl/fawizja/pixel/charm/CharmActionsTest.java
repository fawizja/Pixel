package pl.fawizja.pixel.charm;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.Actions;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.vocation.Vocation;

/** Akcje charmow (te same lokalnie i na serwerze): kupno, zalozenie, zdjecie za zloto, reset. */
class CharmActionsTest {

    @Test
    void buyAssignRemoveAndResetThroughActions() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        for (int i = 0; i < 500; i++) pc.bestiary().record("boar", false);
        pc.charms().addPoints(1000);
        pc.bank().credit(50_000_000L);
        GameSession s = new GameSession(TestData.ITEMS, TestData.varden(), pc);
        assertTrue(Actions.apply(s, "charm:buy:WOUND", 0));
        assertEquals(1, pc.charms().tier(Charm.WOUND));
        assertTrue(Actions.apply(s, "charm:assign:WOUND:boar", 0));
        assertEquals("boar", pc.charms().assignedTo(Charm.WOUND));

        long money = s.gold() + pc.bank().balance();
        assertTrue(Actions.apply(s, "charm:remove:WOUND", 0));
        assertNull(pc.charms().assignedTo(Charm.WOUND));
        assertEquals(money - CharmPrices.removal(pc.level()), s.gold() + pc.bank().balance());

        assertTrue(Actions.apply(s, "charm:reset", 0));
        assertEquals(1000, pc.charms().points());
        assertEquals(0, pc.charms().tier(Charm.WOUND));
        assertTrue(Actions.apply(s, "charm:buy:NOPE", 0), "unknown charm is ignored");
    }

    @Test
    void charmViewsListAllCharmsAndAssignableCreatures() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        for (int i = 0; i < 500; i++) pc.bestiary().record("boar", false);
        pc.charms().addPoints(1000);
        pc.charms().buy(Charm.DODGE);
        java.util.Collection<pl.fawizja.pixel.monster.MonsterType> types =
                pl.fawizja.pixel.monster.MonsterRegistry.loadDefault(TestData.ITEMS).all();
        java.util.List<String[]> list = CharmView.list(pc, 0, 100, k -> k);
        assertEquals(1 + 25 + 1, list.size(), "header, 25 charms, reset");
        java.util.List<String[]> detail = CharmView.detail(pc, Charm.DODGE, types, 0, 30);
        boolean boar = false;
        for (String[] r : detail) boar |= "charm:assign:DODGE:boar".equals(r[3]);
        assertTrue(boar, "completed boar can take a Major charm");
    }
}
