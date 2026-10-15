package pl.fawizja.pixel.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.combat.DamageType;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.magic.ConsumableRegistry;
import pl.fawizja.pixel.magic.RecipeRegistry;
import pl.fawizja.pixel.magic.SpellRegistry;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.GroundItems;
import pl.fawizja.pixel.world.Position;

class CorpseTest {

    static final MonsterRegistry MONSTERS = MonsterRegistry.loadDefault(TestData.ITEMS);
    static final Position AT = new Position(12, 12, 0);

    static GameSession killBoss(PlayerCharacter pc, boolean autoLoot) {
        MonsterManager mm = new MonsterManager(TestData.varden(), MONSTERS, List.of(new SpawnPoint("grimtooth", AT, 0, 1, 600)), new Random(3));
        mm.populate(null);
        GameSession s = new GameSession(TestData.ITEMS, TestData.varden(), pc, mm, new Random(5), 8,
                SpellRegistry.loadDefault(TestData.ITEMS), ConsumableRegistry.loadDefault(TestData.ITEMS), RecipeRegistry.loadDefault(TestData.ITEMS));
        s.teleport(new Position(10, 12, 0), 0);
        s.combat().setAutoLoot(autoLoot);
        Monster boss = mm.monsters().get(0);
        s.combat().damageMonster(boss, 1_000_000, DamageType.PHYSICAL, 1000);
        assertTrue(boss.isDead());
        return s;
    }

    @Test
    void lootWaitsInTheCorpseUntilSearchedFromNextToIt() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        GameSession s = killBoss(pc, false);
        GroundItems.Corpse c = s.ground().corpseAt(AT);
        assertNotNull(c, "a body stays where the boss died");
        assertEquals("grimtooth", c.monsterKey());
        assertTrue(c.hasLoot());
        assertTrue(s.ground().at(AT).isEmpty(), "loot is inside the body, not scattered on the ground");
        assertNull(s.corpseToSearch(), "two tiles away is too far");
        assertFalse(s.searchCorpse(1000));

        s.teleport(new Position(11, 12, 0), 1000);
        assertEquals(AT, s.corpseToSearch());
        assertTrue(s.itemsAtFeet(), "PICK button shows next to a body with loot");
        int platinum = pc.backpack().count(TestData.ITEMS.byKey("platinum_coin").id());
        assertTrue(Actions.apply(s, "loot", 1000));
        assertTrue(pc.backpack().count(TestData.ITEMS.byKey("platinum_coin").id()) >= platinum + 10);
        assertFalse(s.ground().corpseAt(AT).hasLoot());
        assertNull(s.corpseToSearch(), "an empty body is not searched again");

        s.update(1000 + GroundItems.CORPSE_MS + 1500, null);
        assertNull(s.ground().corpseAt(AT), "the body decays");
    }

    @Test
    void autolootTakesWhatFitsAndAFullBackpackLeavesTheRestInTheBody() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        while (pc.backpack().freeSlots() > 0) pc.backpack().add(new ItemStack(TestData.ITEMS.byKey("leather_helmet").id(), 1));
        GameSession s = killBoss(pc, true);
        assertTrue(s.ground().corpseAt(AT).hasLoot());
        assertTrue(s.log().latest(3).stream().anyMatch(e -> e.text().contains("przeszukaj")));
        s.teleport(new Position(11, 12, 0), 1000);
        assertTrue(s.searchCorpse(1000));
        assertTrue(s.ground().corpseAt(AT).hasLoot(), "nothing fits, nothing is lost");
        assertTrue(s.log().latest(1).get(0).text().contains("Plecak jest pelny"));
    }

    @Test
    void groundItemsDecayAfterHalfAnHourButNotBeforeTheClockIsKnown() {
        GroundItems g = new GroundItems();
        Position p = new Position(5, 5, 0);
        g.place(p, new ItemStack(1, 5)); // przed pierwszym decay: czas liczony od pierwszego wywolania
        g.decay(10_000_000L);
        assertFalse(g.at(p).isEmpty());
        g.decay(10_000_000L + GroundItems.GROUND_MS - 5_000);
        assertFalse(g.at(p).isEmpty());
        g.decay(10_000_000L + GroundItems.GROUND_MS + 1);
        assertTrue(g.at(p).isEmpty());
        g.addCorpse(p, "wolf", List.of(), 20_000_000L);
        assertFalse(g.corpseAt(p).hasLoot());
        assertEquals(1, g.corpseCount());
    }
}
