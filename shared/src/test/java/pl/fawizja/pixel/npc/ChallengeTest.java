package pl.fawizja.pixel.npc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.Challenge;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

class ChallengeTest {
    @BeforeAll
    static void load() throws Exception {
        TownServicesTest.load();
    }

    static int scrollSlot(PlayerCharacter pc) {
        int id = TestData.ITEMS.byKey("challenge_scroll").id();
        for (int i = 0; i < pc.backpack().size(); i++) if (pc.backpack().get(i) != null && pc.backpack().get(i).itemId() == id) return i;
        return -1;
    }

    static void killChallengers(GameSession s, long t) {
        for (Monster m : List.copyOf(s.monsters().monsters())) {
            if (m.position().distanceTo(s.challenge().center()) <= Challenge.RADIUS + 1) {
                s.combat().damageMonster(m, 1_000_000, pl.fawizja.pixel.combat.DamageType.FIRE, t);
            }
        }
    }

    @Test
    void threeWavesAreSpawnedOneAfterAnotherAndPayAtTheEnd() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        // jedyny potwor w okolicy (daleko), zeby zwoj mial z czego wybierac
        GameSession s = new TownServicesTest().session(pc, List.of(new SpawnPoint("wolf", new Position(40, 72, 0), 1, 1, 600)));
        Position field = new Position(58, 72, 0);
        s.teleport(field, 0);
        pc.backpack().add(new ItemStack(TestData.ITEMS.byKey("challenge_scroll").id(), 2));
        long t = 1000;
        s.useSlot(scrollSlot(pc), t);
        assertNotNull(s.challenge());
        assertEquals("wolf", s.challenge().type().key());
        assertEquals(1, pc.backpack().count(TestData.ITEMS.byKey("challenge_scroll").id()), "the scroll is used up");
        assertEquals("Wyzwanie: fala 1/3 (" + s.challenge().type().name() + "), 5:00", s.challenge().line(t));
        long bank = pc.bank().balance();
        for (int w = 0; w < Challenge.WAVES.length; w++) {
            assertEquals(w, s.challenge().wave());
            killChallengers(s, t);
            s.update(t += 100, null);
        }
        assertNull(s.challenge(), "finished");
        assertEquals(1, pc.stats().challenges());
        long reward = Challenge.rewardGold(pl.fawizja.pixel.monster.MonsterRegistry.loadDefault(TestData.ITEMS).get("wolf"));
        assertTrue(pc.bank().balance() - bank >= reward, "paid (the first update may also add the login bonus)");
        assertTrue(s.log().latest(50).stream().anyMatch(e -> e.text().startsWith("Wyzwanie ukończone")));

        // drugi zwoj: odejscie konczy wyzwanie i zabiera jego potwory
        s.useSlot(scrollSlot(pc), t);
        assertNotNull(s.challenge());
        int before = s.monsters().monsters().size();
        s.teleport(new Position(field.x() + Challenge.LEAVE + 5, field.y(), 0), t);
        s.update(t += 100, null);
        assertNull(s.challenge());
        assertEquals(before - Challenge.WAVES[0], s.monsters().monsters().size());
    }

    @Test
    void cannotBeReadInTown() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        GameSession s = new TownServicesTest().session(pc, List.of(new SpawnPoint("wolf", new Position(40, 72, 0), 1, 1, 600)));
        s.teleport(TownServicesTest.talkSpot(TownServicesTest.npcs.get("varden_guide")), 0);
        pc.backpack().add(new ItemStack(TestData.ITEMS.byKey("challenge_scroll").id(), 1));
        s.useSlot(scrollSlot(pc), 1000);
        assertNull(s.challenge());
        assertEquals(1, pc.backpack().count(TestData.ITEMS.byKey("challenge_scroll").id()));
    }
}
