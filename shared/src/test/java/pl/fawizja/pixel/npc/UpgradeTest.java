package pl.fawizja.pixel.npc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.combat.CombatFormulas;
import pl.fawizja.pixel.economy.Money;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.inventory.Container;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.item.Upgrade;
import pl.fawizja.pixel.vocation.Vocation;

class UpgradeTest {
    static int sword;

    @BeforeAll
    static void load() throws Exception {
        TownServicesTest.load();
        sword = TestData.ITEMS.byKey("iron_sword").id();
    }

    @Test
    void upgradedStacksKeepTheirLevel() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new ItemStack(sword, 2, 1), "upgraded items are single");
        assertThrows(IllegalArgumentException.class, () -> new ItemStack(sword, 1, Upgrade.MAX + 1));

        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        Container bag = pc.backpack();
        bag.add(new ItemStack(sword, 1, 3));
        bag.add(new ItemStack(sword, 1));
        assertEquals(2, bag.count(sword));
        assertEquals(1, bag.countPlain(sword));
        assertTrue(bag.contents().stream().anyMatch(s -> s.itemId() == sword && s.upgrade() == 3), "add keeps the upgrade");

        Container copy = bag.copy();
        assertTrue(copy.remove(sword, 1).ok());
        assertEquals(0, copy.countPlain(sword), "the plain copy goes first");
        assertEquals(1, copy.count(sword));

        pc.equipment().setDirect(EquipSlot.WEAPON, new ItemStack(sword, 1, 2));
        pc.depot().add(new ItemStack(sword, 1, 5));
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertEquals(2, back.equipment().get(EquipSlot.WEAPON).upgrade());
        assertEquals(1, back.backpack().countPlain(sword));
        assertEquals(2, back.backpack().count(sword));
        assertEquals(0, back.depot().countPlain(sword));
        assertEquals(1, back.depot().count(sword));
    }

    @Test
    void statsGrowWithTheLevel() {
        ItemType t = TestData.ITEMS.get(sword);
        assertTrue(Upgrade.upgradable(t));
        assertFalse(Upgrade.upgradable(TestData.ITEMS.byKey("torch")));
        int prev = t.attack();
        for (int u = 1; u <= Upgrade.MAX; u++) {
            assertTrue(Upgrade.attack(t, u) > prev, "+" + u + " adds attack");
            prev = Upgrade.attack(t, u);
        }
        assertTrue(Upgrade.name(t, 2).endsWith(" +2"));
        assertEquals(t.name(), Upgrade.name(t, 0));
        assertTrue(Upgrade.chancePercent(1) > Upgrade.chancePercent(Upgrade.MAX));
        assertTrue(Upgrade.price(t, 1) >= 200);
        assertTrue(Upgrade.price(t, Upgrade.MAX) >= 5_000, "the last level is a real gold sink");
    }

    @Test
    void merchantUpgradesWornItemsAndKeepsThemFromBulkSale() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        pc.addExperience(pl.fawizja.pixel.balance.Balance.experienceForLevel(40) - pc.experience());
        GameSession s = new TownServicesTest().session(pc, List.of());
        Npc merchant = TownServicesTest.npcs.get("varden_merchant");
        s.teleport(TownServicesTest.talkSpot(merchant), 1000);
        pc.equipment().setDirect(EquipSlot.WEAPON, new ItemStack(sword, 1));
        assertTrue(Money.add(pc.backpack(), 2_000_000).ok());
        int base = CombatFormulas.attackProfile(pc, TestData.ITEMS).maxDamage();
        long gold = s.gold();

        long t = 2000;
        for (int i = 0; i < 200 && pc.equipment().get(EquipSlot.WEAPON).upgrade() < Upgrade.MAX; i++) {
            s.upgradeEquipped(EquipSlot.WEAPON, t++);
        }
        assertEquals(Upgrade.MAX, pc.equipment().get(EquipSlot.WEAPON).upgrade());
        assertFalse(s.upgradeEquipped(EquipSlot.WEAPON, t++), "no level beyond +5");
        assertTrue(s.gold() < gold, "upgrades cost gold");
        assertTrue(CombatFormulas.attackProfile(pc, TestData.ITEMS).maxDamage() > base, "the upgraded weapon hits harder");
        pc.equipment().setDirect(EquipSlot.RING, null);
        assertFalse(s.upgradeEquipped(EquipSlot.RING, t++), "nothing worn there");

        // hurtowa sprzedaz pomija ulepszony egzemplarz
        pc.backpack().add(new ItemStack(sword, 1, 4));
        pc.backpack().add(new ItemStack(sword, 1));
        assertTrue(s.sellAll(sword, t++).ok());
        assertEquals(1, pc.backpack().count(sword));
        assertEquals(0, pc.backpack().countPlain(sword));
    }
}
