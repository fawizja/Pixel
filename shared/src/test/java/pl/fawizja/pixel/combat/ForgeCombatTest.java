package pl.fawizja.pixel.combat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.Forge;
import pl.fawizja.pixel.item.ForgeEffects;
import pl.fawizja.pixel.item.Imbuement;
import pl.fawizja.pixel.item.Imbuements;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.GroundItems;
import pl.fawizja.pixel.world.Position;

/** Pyl i odlamki z potworow Kuzni, efekty tierow i nasycenia w walce. */
class ForgeCombatTest {
    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);

    static ItemType firstOfClass(int c, EquipSlot slot) {
        for (ItemType t : TestData.ITEMS.all()) if (Forge.classOf(t) == c && t.slot() == slot) return t;
        throw new AssertionError("no item of class " + c + " in " + slot);
    }

    static CombatSystem combat(PlayerCharacter pc, MonsterManager mm, EventLog log) {
        return new CombatSystem(pc, TestData.varden(), TestData.ITEMS, mm, new GroundItems(), log, new Random(2), 8);
    }

    @Test
    void forgeCreaturesGiveDustAndFiendishOnesDropSlivers() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.setPosition(new Position(10, 12, 0));
        MonsterManager mm = new MonsterManager(TestData.varden(), REG,
                List.of(new SpawnPoint("wolf", new Position(11, 12, 0), 0, 1, 60)), new Random(3));
        mm.populate(null);
        Monster wolf = mm.monsters().get(0);
        wolf.changeType(wolf.type().base().fiendish());
        EventLog log = new EventLog(50);
        CombatSystem cs = combat(pc, mm, log);
        cs.damageMonster(wolf, 1_000_000, DamageType.PHYSICAL, 1000);
        assertTrue(wolf.isDead());
        int dust = pc.forgeDust();
        assertTrue(dust >= 15 && dust <= 45, "fiendish (stack 15): 15..45 dust, got " + dust);
        int slivers = pc.backpack().count(TestData.ITEMS.byKey(Forge.SLIVER_KEY).id());
        assertTrue(slivers >= 3 && slivers <= 7, "3..7 slivers, got " + slivers);

        // zwykly potwor: bez pylu; limit: nadmiar przepada z komunikatem
        assertEquals(0, cs.awardForgeDust(REG.get("wolf"), 2000));
        pc.restoreForge(Forge.DUST_START_LIMIT, Forge.DUST_START_LIMIT);
        assertEquals(0, cs.awardForgeDust(REG.get("wolf").influenced(5), 2000));
        assertTrue(log.latest(5).stream().anyMatch(e -> e.text().contains("limit")));
        pc.restoreForge(0, Forge.DUST_START_LIMIT);
        int got = cs.awardForgeDust(REG.get("wolf").influenced(3), 3000);
        assertTrue(got >= 3 && got <= 9);
    }

    @Test
    void imbuementsFeedLeechCritAndProtection() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        ItemType w = firstOfClass(4, EquipSlot.WEAPON);
        ItemType a = firstOfClass(2, EquipSlot.ARMOR);
        double life = Leech.lifePercent(pc), mana = Leech.manaPercent(pc), crit = CriticalHit.chance(pc), critDmg = CriticalHit.extraDamage(pc);
        int fire = pc.equipment().protection(DamageType.FIRE);
        pc.equipment().setDirect(EquipSlot.WEAPON, new ItemStack(w.id(), 1, 0, 0,
                Imbuements.NONE.with(Imbuement.VAMPIRISM, 3).with(Imbuement.VOID, 2).with(Imbuement.STRIKE, 1)));
        pc.equipment().setDirect(EquipSlot.ARMOR, new ItemStack(a.id(), 1, 0, 0, Imbuements.NONE.with(Imbuement.DRAGON_HIDE, 2)));
        assertEquals(25, Leech.lifePercent(pc) - life - w.bonus().lifeLeech() - a.bonus().lifeLeech(), 1e-9);
        assertEquals(5, Leech.manaPercent(pc) - mana - w.bonus().manaLeech() - a.bonus().manaLeech(), 1e-9);
        assertEquals(Imbuement.STRIKE_CHANCE, CriticalHit.chance(pc) - crit - w.bonus().critChance() - a.bonus().critChance(), 1e-9);
        assertEquals(15, CriticalHit.extraDamage(pc) - critDmg - w.bonus().critDamage() - a.bonus().critDamage(), 1e-9);
        assertEquals(fire + 8 + w.bonus().protection(DamageType.FIRE) + a.bonus().protection(DamageType.FIRE),
                pc.equipment().protection(DamageType.FIRE));

        // drenaz tylko zalozonych: 20 h walki
        assertEquals(List.of(), pc.equipment().tickImbuements(Imbuement.DURATION_MS - 1));
        List<String> gone = pc.equipment().tickImbuements(1);
        assertEquals(4, gone.size());
        assertTrue(pc.equipment().get(EquipSlot.WEAPON).imbuements().isEmpty());
    }

    @Test
    void tierEffectsAndTheAvatar() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        assertEquals(0, ForgeEffects.onslaughtChance(pc));
        assertFalse(ForgeEffects.roll(0, new Random(1)));
        ItemType w = firstOfClass(4, EquipSlot.WEAPON);
        ItemType armor = firstOfClass(3, EquipSlot.ARMOR);
        pc.equipment().setDirect(EquipSlot.WEAPON, new ItemStack(w.id(), 1, 0, 10, Imbuements.NONE));
        pc.equipment().setDirect(EquipSlot.ARMOR, new ItemStack(armor.id(), 1, 0, 3, Imbuements.NONE));
        assertEquals(Forge.onslaught(10), ForgeEffects.onslaughtChance(pc), 1e-9);
        assertEquals(Forge.ruse(3), ForgeEffects.ruseChance(pc), 1e-9);
        ItemType boots = TestData.ITEMS.byKey("abyssal_treads");
        pc.equipment().setDirect(EquipSlot.FEET, new ItemStack(boots.id(), 1, 0, 2, Imbuements.NONE));
        double amp = 1 + Forge.amplification(2) / 100;
        assertEquals(amp, ForgeEffects.amplifier(pc), 1e-9);
        assertEquals(Forge.onslaught(10) * amp, ForgeEffects.onslaughtChance(pc), 1e-9, "boots amplify the other effects");

        int hits = 0;
        Random rng = new Random(5);
        for (int i = 0; i < 100_000; i++) if (ForgeEffects.rollRuse(pc, rng)) hits++;
        assertEquals(ForgeEffects.ruseChance(pc) / 100, hits / 100_000.0, 0.002);

        assertFalse(pc.avatarActive());
        pc.startAvatar(Forge.AVATAR_MS);
        assertEquals(100, CriticalHit.chance(pc));
        assertEquals(85, ForgeEffects.avatarIncoming(pc, 100), "the avatar takes 15% less damage");
        pc.tickAvatar(Forge.AVATAR_MS);
        assertFalse(pc.avatarActive());
        assertEquals(100, ForgeEffects.avatarIncoming(pc, 100));
    }

    @Test
    void elementImbuementsConvertPartOfWeaponHits() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.setPosition(new Position(10, 12, 0));
        MonsterManager mm = new MonsterManager(TestData.varden(), REG,
                List.of(new SpawnPoint("ice_golem", new Position(11, 12, 0), 0, 1, 60)), new Random(3));
        mm.populate(null);
        Monster golem = mm.monsters().get(0);
        ItemType w = null;
        for (ItemType t : TestData.ITEMS.all()) {
            if (Forge.classOf(t) >= 1 && t.slot() == EquipSlot.WEAPON && Imbuement.SCORCH.fits(t)) { w = t; break; }
        }
        assertNotNull(w);
        pc.equipment().setDirect(EquipSlot.WEAPON, new ItemStack(w.id(), 1, 0, 0, Imbuements.NONE.with(Imbuement.ELECTRIFY, 3)));
        CombatSystem cs = combat(pc, mm, new EventLog(20));
        // lodowy golem: slaby na energie - polowa ciosu jako energia bije mocniej niz sam cios fizyczny
        long withImb = 0, plain = 0;
        for (int i = 0; i < 2000; i++) withImb += cs.mitigateWeaponHit(golem.type(), 100, DamageType.PHYSICAL);
        pc.equipment().setDirect(EquipSlot.WEAPON, new ItemStack(w.id(), 1));
        for (int i = 0; i < 2000; i++) plain += cs.mitigateWeaponHit(golem.type(), 100, DamageType.PHYSICAL);
        assertNotEquals(plain, withImb);
    }
}
