package pl.fawizja.pixel.magic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.Actions;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.settings.SpellBar;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

/** Odnowienia jak w Canary: czar, grupa (atak/leczenie/wsparcie niezalezne), druga grupa, runy i mikstury. */
class TibiaCooldownsTest {

    static GameSession session(Vocation v, int level) {
        PlayerCharacter pc = TibiaSpellsTest.at(v, level);
        pc.skills().set(pl.fawizja.pixel.character.Skill.MAGIC, 5, 0); // slabe czary: cel przezyje kilka trafien
        pc.restoreMana(pc.maxMana());
        GameSession s = MagicTest.session(pc, "obsidian_sentinel", new Position(12, 12, 0));
        s.combat().setTarget(s.monsters().monsters().get(0));
        return s;
    }

    static void refill(GameSession s) { s.character().restoreMana(s.character().maxMana()); }

    @Test
    void attackAndHealingGroupsAreIndependent() {
        GameSession s = session(Vocation.SORCERER, 30);
        s.character().damage(40);
        assertEquals(CastResult.OK, s.castSpell("energy_strike", 1000));
        assertEquals(CastResult.OK, s.castSpell("light_healing", 1000), "heal in the same moment as an attack (Tibia)");
        assertEquals(CastResult.COOLDOWN, s.castSpell("death_strike", 1500), "attack group: 2 s");
        assertEquals(CastResult.OK, s.castSpell("death_strike", 3000), "another attack spell after the 2 s group");
        s.character().damage(40);
        assertEquals(CastResult.COOLDOWN, s.castSpell("intense_healing", 1900), "healing group: 1 s");
        assertEquals(CastResult.OK, s.castSpell("intense_healing", 2000));
        assertEquals(CastResult.COOLDOWN, s.castSpell("energy_strike", 4000), "own cooldown of energy strike is longer than the group");
    }

    @Test
    void strongStrikesAndLightningShareTheSpecialGroup() {
        GameSession s = session(Vocation.SORCERER, 80);
        assertEquals(CastResult.OK, s.castSpell("strong_flame_strike", 1000));
        refill(s);
        assertEquals(CastResult.COOLDOWN, s.castSpell("strong_energy_strike", 3500), "special group 8 s");
        assertEquals(CastResult.COOLDOWN, s.castSpell("lightning", 8500 - 1000), "lightning too");
        assertEquals(CastResult.OK, s.castSpell("energy_strike", 3500), "plain attack spells only wait for the 2 s group");
        refill(s);
        assertEquals(CastResult.OK, s.castSpell("strong_energy_strike", 9000));
        assertEquals("special", SpellSystem.secondaryGroup("lightning"));
    }

    @Test
    void ultimateAreaSpellsBlockTheAttackGroupForFourSeconds() {
        GameSession s = session(Vocation.SORCERER, 70);
        assertEquals(4000, SpellGroups.primaryMs(s.spells().registry().get("hells_core")));
        assertEquals(CastResult.OK, s.castSpell("hells_core", 1000));
        refill(s);
        assertEquals(CastResult.COOLDOWN, s.castSpell("energy_strike", 3500), "Canary groupCooldown(4 s, 40 s)");
        assertEquals(CastResult.OK, s.castSpell("energy_strike", 5000));
        assertEquals(CastResult.COOLDOWN, s.castSpell("rage_of_the_skies", 20000), "focus group 40 s");
    }

    @Test
    void focusGroupIsSharedBySupportSpells() {
        GameSession s = session(Vocation.PALADIN, 60);
        assertEquals(CastResult.OK, s.castSpell("sharpshooter", 1000));
        refill(s);
        assertEquals(CastResult.COOLDOWN, s.castSpell("swift_foot", 4000), "Sharpshooter puts the focus group on 10 s");
        assertEquals(CastResult.OK, s.castSpell("swift_foot", 11000));
    }

    @Test
    void runesUseTheSpellGroupsAndShareOneSecondWithPotions() {
        GameSession s = session(Vocation.SORCERER, 45);
        PlayerCharacter pc = s.character();
        pc.skills().set(pl.fawizja.pixel.character.Skill.MAGIC, 20, 0); // nagla smierc od magii 15
        pc.backpack().add(new ItemStack(MagicTest.id("sudden_death_rune"), 5));
        pc.backpack().add(new ItemStack(MagicTest.id("ultimate_healing_rune"), 5));
        pc.backpack().add(new ItemStack(MagicTest.id("mana_potion"), 5));
        pc.spendMana(100);
        // runa ataku: grupa ataku 2 s - czar ataku musi czekac
        assertEquals(CastResult.OK, s.spells().useFirst("sudden_death_rune", 1000));
        assertEquals(CastResult.COOLDOWN, s.castSpell("energy_strike", 1500), "attack rune triggers the attack group");
        // ... a mikstura i inna runa - 1 s uzycia przedmiotu (Canary nextPotionAction)
        assertEquals(CastResult.COOLDOWN, s.spells().useFirst("mana_potion", 1500), "potion right after a rune: exhausted");
        assertEquals(CastResult.OK, s.spells().useFirst("mana_potion", 2000));
        pc.damage(pc.health() / 2);
        assertEquals(CastResult.COOLDOWN, s.spells().useFirst("ultimate_healing_rune", 2500), "rune right after a potion");
        assertEquals(CastResult.OK, s.spells().useFirst("ultimate_healing_rune", 3000));
        // czar leczenia czeka na grupe leczenia runy, czar ataku nie
        pc.damage(50);
        assertEquals(CastResult.COOLDOWN, s.castSpell("light_healing", 3500), "healing rune triggers the healing group");
        assertEquals(CastResult.OK, s.castSpell("energy_strike", 3500), "attack spell with a healing rune: different groups");
    }

    @Test
    void comboFiresOneSpellPerPress() {
        GameSession s = session(Vocation.SORCERER, 30);
        PlayerCharacter pc = s.character();
        Monster bear = s.monsters().monsters().get(0);
        pc.damage(60);
        String combo = SpellBar.combo(List.of("light_healing", "energy_strike"));
        int hp = pc.health(), mana = pc.mana(), bearHp = bear.health();
        assertTrue(Actions.apply(s, combo, 1000));
        assertTrue(pc.health() > hp, "the heal goes first");
        assertEquals(bearHp, bear.health(), "... and only the heal: one press = one spell");
        assertEquals(mana - s.spells().registry().get("light_healing").mana(), pc.mana(), "mana for one spell");
        // nastepne nacisniecie w tej samej chwili: leczenie w odnowieniu, atak ma wlasna grupe - leci
        assertTrue(Actions.apply(s, combo, 1000));
        assertTrue(bear.health() < bearHp, "second press: the attack (heal and attack groups are independent)");
        int left = pc.mana();
        assertEquals(CastResult.COOLDOWN, s.castCombo(List.of("light_healing", "energy_strike"), 1500), "both cooling down");
        assertEquals(left, pc.mana());
    }
}
