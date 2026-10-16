package pl.fawizja.pixel.combat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.GroundItems;
import pl.fawizja.pixel.world.Position;

class RareDropTest {
    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);

    /** Kazdy rzut lupu trafia (nextInt zawsze 0). */
    static final class LuckyRandom extends Random {
        @Override public int nextInt(int bound) { return 0; }
    }

    @Test
    void rarityUsesBaseChance() {
        MonsterType golem = REG.get("ice_golem");
        assertTrue(CombatSystem.isRare(golem, 10));
        assertFalse(CombatSystem.isRare(golem, 11));
        assertTrue(CombatSystem.isRare(golem.eliteVariant(), 20), "elite tables double the chance");
    }

    @Test
    void rareLootIsAnnouncedOnce() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        pc.setPosition(new Position(10, 12, 0));
        Random rng = new LuckyRandom();
        MonsterManager mm = new MonsterManager(TestData.varden(), REG,
                List.of(new SpawnPoint("ice_golem", new Position(11, 12, 0), 0, 1, 60)), new Random(3));
        mm.populate(null);
        Monster golem = mm.monsters().get(0);
        EventLog log = new EventLog(50);
        CombatSystem cs = new CombatSystem(pc, TestData.varden(), TestData.ITEMS, mm, new GroundItems(), log, rng, 8);
        List<String> heard = new ArrayList<>();
        cs.setRareDropListener((t, item, now) -> heard.add(t.key() + ":" + TestData.ITEMS.get(item.itemId()).key()));
        cs.damageMonster(golem, 1_000_000, DamageType.FIRE, 1000);
        assertTrue(golem.isDead());
        assertEquals(List.of("ice_golem:frost_blade"), heard);
        assertEquals(1, log.latest(50).stream().filter(e -> e.text().startsWith("Rzadki łup: ")).count());
        List<String[]> loot = cs.lootLog().rows(1000);
        assertEquals(1, loot.size(), "the kill is in the loot window, including what stayed in the corpse");
        assertEquals(golem.type().name() + " (przed chwilą)", loot.get(0)[0]);
        assertTrue(loot.get(0)[1].startsWith("rzadki łup! "), loot.get(0)[1]);
        assertEquals(golem.type().name() + " (3 min temu)", cs.lootLog().rows(1000 + 180_000).get(0)[0]);

        // zwykly lup nie jest ogloszeniem
        List<ItemStack> plain = cs.rollLoot(REG.get("wolf"));
        assertFalse(plain.isEmpty());
        assertEquals(1, heard.size());
    }
}
