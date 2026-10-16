package pl.fawizja.pixel.npc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.combat.DamageType;
import pl.fawizja.pixel.economy.Money;
import pl.fawizja.pixel.game.Actions;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.item.Enchantments;
import pl.fawizja.pixel.item.Enchantments.Kind;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.vocation.Vocation;

class EnchantmentTest {
    @BeforeAll
    static void load() throws Exception {
        TownServicesTest.load();
    }

    @Test
    void timeRunsOutOnlyOutsideTheProtectionZoneAndIsSaved() throws Exception {
        Enchantments e = new Enchantments();
        assertFalse(e.active(Kind.LIFE));
        e.extend(Kind.LIFE);
        e.extend(Kind.LIFE);
        e.extend(Kind.LIFE);
        assertEquals(Enchantments.MAX_MS, e.remainingMs(Kind.LIFE));
        assertFalse(e.canExtend(Kind.LIFE), "at most 3 hours in stock");
        assertEquals(6, e.leech(Kind.LIFE, 100));
        assertEquals(0, e.leech(Kind.MANA, 100), "inactive enchantment does nothing");
        assertEquals(1, e.leech(Kind.LIFE, 3), "at least 1 HP per hit");
        assertEquals("3 h 00 min", Enchantments.describe(e.remainingMs(Kind.LIFE)));

        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        pc.enchantments().extend(Kind.CRIT);
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        assertEquals(Enchantments.DURATION_MS, codec.decode(codec.encode(pc)).enchantments().remainingMs(Kind.CRIT));

        assertNull(e.tick(Enchantments.MAX_MS - 1));
        assertEquals(Kind.LIFE, e.tick(5), "reports the expiry");
        assertFalse(e.active(Kind.LIFE));
        assertEquals("", e.encode());
    }

    @Test
    void merchantSellsEnchantmentsThatLeechFromHits() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        GameSession s = new TownServicesTest().session(pc,
                List.of(new pl.fawizja.pixel.monster.SpawnPoint("wolf", new pl.fawizja.pixel.world.Position(58, 72, 0), 0, 1, 600)));
        Npc merchant = TownServicesTest.npcs.get("varden_merchant");
        s.teleport(TownServicesTest.talkSpot(merchant), 1000);
        assertTrue(Money.add(pc.backpack(), 100_000).ok());
        long gold = s.gold();
        assertTrue(Actions.apply(s, "npc:enchant:LIFE", 1200));
        assertTrue(pc.enchantments().active(Kind.LIFE));
        assertEquals(gold - Enchantments.price(Kind.LIFE, pc.level()), s.gold());
        assertTrue(s.buyEnchantment(Kind.MANA, 1300));

        // w strefie ochronnej czas stoi
        long before = pc.enchantments().remainingMs(Kind.LIFE);
        for (long t = 2000; t <= 10_000; t += 500) s.update(t, null);
        assertEquals(before, pc.enchantments().remainingMs(Kind.LIFE));

        pc.damage(pc.maxHealth() / 2);
        int hp = pc.health();
        Monster m = s.monsters().monsters().get(0);
        assertTrue(s.combat().damageMonster(m, 200, DamageType.PHYSICAL, 11_000) > 0);
        assertTrue(pc.health() > hp, "life leech heals after a hit");
    }
}
