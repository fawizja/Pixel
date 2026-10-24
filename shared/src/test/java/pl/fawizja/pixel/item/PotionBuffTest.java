package pl.fawizja.pixel.item;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.movement.MovementSystem;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Direction;

/** Mikstury wzmacniajace z paczki ikon WsT i rzadkosc ramek pol. */
class PotionBuffTest {

    @Test
    void everyBuffPotionExistsAndIsSold() {
        for (Enchantments.Kind k : Enchantments.Kind.values()) {
            if (k.potion() == null) continue;
            assertNotNull(TestData.ITEMS.byKey(k.potion()), k.potion());
            assertEquals(ItemCategory.POTION, TestData.ITEMS.byKey(k.potion()).category());
            assertEquals(k, Enchantments.Kind.ofPotion(k.potion()));
            assertEquals(Enchantments.POTION_MS, k.durationMs());
        }
        assertNull(Enchantments.Kind.ofPotion("health_potion"));
        assertEquals(Enchantments.DURATION_MS, Enchantments.Kind.CRIT.durationMs(), "smith stays 1 h");
    }

    @Test
    void buffsChangeDamageSpeedAndLoot() throws Exception {
        Enchantments e = new Enchantments();
        assertEquals(100, e.outgoing(100));
        assertEquals(100, e.incoming(100));
        assertEquals(100, e.lootChance(100));
        e.extend(Enchantments.Kind.STRENGTH);
        e.extend(Enchantments.Kind.STONESKIN);
        e.extend(Enchantments.Kind.LUCK);
        assertEquals(115, e.outgoing(100));
        assertEquals(85, e.incoming(100));
        assertEquals(130, e.lootChance(100));
        assertEquals(Enchantments.POTION_MS, e.remainingMs(Enchantments.Kind.STRENGTH));
        e.extend(Enchantments.Kind.STRENGTH);
        e.extend(Enchantments.Kind.STRENGTH);
        assertFalse(e.canExtend(Enchantments.Kind.STRENGTH), "at most 30 minutes in reserve");
        assertNull(e.tick(Enchantments.POTION_MS - 1));
        assertNotNull(e.tick(1));
        assertFalse(e.active(Enchantments.Kind.STONESKIN));
        assertTrue(e.active(Enchantments.Kind.STRENGTH));

        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.setRunning(false);
        long normal = MovementSystem.stepDurationMs(pc, Direction.NORTH);
        pc.enchantments().extend(Enchantments.Kind.SWIFTNESS);
        assertTrue(MovementSystem.stepDurationMs(pc, Direction.NORTH) < normal, "swiftness shortens steps");
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        assertTrue(codec.decode(codec.encode(pc)).enchantments().active(Enchantments.Kind.SWIFTNESS), "saved");
    }

    @Test
    void drinkingFromTheActionBarStartsTheBuffAndUsesOnePotion() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        int id = TestData.ITEMS.byKey("strength_potion").id();
        pc.addExperience(pl.fawizja.pixel.balance.Balance.experienceForLevel(40) - pc.experience());
        pc.backpack().add(new ItemStack(id, 2));
        pl.fawizja.pixel.game.GameSession s = new pl.fawizja.pixel.game.GameSession(TestData.ITEMS, TestData.varden(), pc,
                null, new java.util.Random(1), 8);
        s.usePotion("strength_potion", 1000);
        assertTrue(pc.enchantments().active(Enchantments.Kind.STRENGTH));
        assertEquals(1, pc.backpack().count(id));
    }

    @Test
    void rarityFollowsPrice() {
        assertEquals(0, Rarity.tier(TestData.ITEMS.byKey("health_potion")));
        assertEquals(0, Rarity.tier(TestData.ITEMS.byKey("gold_coin")));
        assertEquals(5, Rarity.tier(TestData.ITEMS.byKey("dawn_bow")));
        assertTrue(Rarity.tier(TestData.ITEMS.byKey("obsidian_shield")) >= 3);
    }
}
