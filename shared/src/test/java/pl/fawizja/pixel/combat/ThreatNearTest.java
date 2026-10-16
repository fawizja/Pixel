package pl.fawizja.pixel.combat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.GroundItems;
import pl.fawizja.pixel.world.Position;

class ThreatNearTest {
    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);

    @Test
    void onlyMonstersCloseByCountForTheAutomaticTarget() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        pc.setPosition(new Position(10, 12, 0));
        MonsterManager mm = new MonsterManager(TestData.varden(), REG,
                List.of(new SpawnPoint("wolf", new Position(15, 12, 0), 0, 1, 60)), new Random(3));
        mm.populate(null);
        CombatSystem cs = new CombatSystem(pc, TestData.varden(), TestData.ITEMS, mm, new GroundItems(), new EventLog(10), new Random(1), 8);
        assertFalse(cs.threatNear(3), "5 tiles away");
        assertTrue(cs.threatNear(5));
        pc.setPosition(new Position(13, 12, 0));
        assertTrue(cs.threatNear(3));
        cs.damageMonster(mm.monsters().get(0), 1_000_000, DamageType.PHYSICAL, 1000);
        assertFalse(cs.threatNear(3), "dead monsters do not count");
    }
}
