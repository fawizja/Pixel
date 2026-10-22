package pl.fawizja.pixel.magic;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterFactory;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.config.StartingConfig;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

/** Etap 13: czary 150-300, inkantacje w stylu Tibii, nowe runy i mikstury. */
class HighLevelMagicTest {

    static final SpellRegistry SPELLS = MagicTest.SPELLS;

    static PlayerCharacter veteran(Vocation v) {
        return new CharacterFactory(StartingConfig.defaults(), TestData.ITEMS).create("Mag", v, new Position(31, 16, 0), 300);
    }

    @Test
    void everyVocationGetsNewSpellsBetweenLevel150And300() {
        for (Vocation v : Vocation.values()) {
            long attacks = SPELLS.forVocation(v).stream()
                    .filter(s -> s.minLevel() >= 150 && s.kind().isOffensive()).count();
            assertTrue(attacks >= 3, v + " has " + attacks + " attack spells at 150+");
            assertTrue(SPELLS.forVocation(v).stream().anyMatch(s -> s.minLevel() >= 120 && s.kind() == SpellKind.HEAL_SELF),
                    v + " has a strong heal");
            assertTrue(SPELLS.forVocation(v).stream().anyMatch(s -> s.minLevel() == 300), v + " has a level 300 spell");
        }
    }

    @Test
    void newTopSpellsHitHarderThanTheLevel100OnesButCoolDownLonger() {
        PlayerCharacter sorc = veteran(Vocation.SORCERER);
        SpellType meteor = SPELLS.get("meteor_bolt"), strike = SPELLS.get("energy_strike_max");
        assertTrue(SpellSystem.baseValue(strike, sorc) > 1.5 * SpellSystem.baseValue(meteor, sorc));
        assertTrue(strike.cooldownMs() > meteor.cooldownMs());
        assertTrue(SPELLS.get("rage_of_skies").cooldownMs() >= 30_000);
    }

    @Test
    void everyCastableSpellHasUniqueTibiaStyleWords() {
        for (SpellType s : SPELLS.all()) {
            if (s.kind().isRune()) {
                assertNull(SPELLS.words(s.key()), "rune effects are used, not spoken: " + s.key());
                continue;
            }
            String w = SPELLS.words(s.key());
            assertNotNull(w, "words for " + s.key());
            assertEquals(s.key(), SPELLS.byWords(w));
        }
        assertEquals("haste", SPELLS.byWords("utani hur"));
        assertEquals("strong_haste", SPELLS.byWords("  Utani   GRAN hur "));
        assertEquals("mend", SPELLS.byWords("exura"));
        assertNull(SPELLS.byWords("utani"));
        assertNull(SPELLS.byWords("cześć wszystkim"));
    }

    @Test
    void sorcererMakesAndUsesSuddenDeathRunes() {
        PlayerCharacter pc = veteran(Vocation.SORCERER);
        GameSession s = MagicTest.session(pc, "wolf", new Position(12, 12, 0));
        pc.backpack().clear();
        pc.backpack().add(new ItemStack(MagicTest.id("blank_rune"), 1));
        assertEquals(CastResult.OK, s.castSpell(SPELLS.byWords("adori gran mort"), 1000));
        assertEquals(3, pc.backpack().count(MagicTest.id("sudden_death_rune")));
        Monster wolf = s.monsters().monsters().get(0);
        s.combat().setTarget(wolf);
        int slot = -1;
        for (int i = 0; i < pc.backpack().size(); i++) {
            ItemStack st = pc.backpack().get(i);
            if (st != null && st.itemId() == MagicTest.id("sudden_death_rune")) slot = i;
        }
        assertEquals(CastResult.OK, s.useSlot(slot, 5000));
        assertTrue(wolf.isDead() || wolf.health() < wolf.type().maxHealth());
        assertEquals(2, pc.backpack().count(MagicTest.id("sudden_death_rune")));
    }

    @Test
    void newPotionsRespectVocationAndRestoreBothForPaladins() {
        PlayerCharacter pal = veteran(Vocation.PALADIN);
        GameSession s = MagicTest.session(pal, null, null);
        pal.damage(1000);
        pal.spendMana(1000);
        int hp = pal.health(), mana = pal.mana();
        pal.backpack().add(new ItemStack(MagicTest.id("ultimate_spirit_potion"), 1));
        pal.backpack().add(new ItemStack(MagicTest.id("ultimate_mana_potion"), 1));
        assertEquals(CastResult.OK, s.usePotion("ultimate_spirit_potion", 0));
        assertTrue(pal.health() > hp && pal.mana() > mana, "spirit potion restores health and mana");
        assertEquals(CastResult.WRONG_VOCATION, s.usePotion("ultimate_mana_potion", 2000));
    }

    @Test
    void veteransStartWithTheNewSupplies() {
        assertTrue(veteran(Vocation.SORCERER).backpack().count(MagicTest.id("great_fireball_rune")) > 0);
        assertTrue(veteran(Vocation.DRUID).backpack().count(MagicTest.id("ultimate_healing_rune")) > 0);
        assertTrue(veteran(Vocation.PALADIN).backpack().count(MagicTest.id("great_spirit_potion")) > 0);
        assertTrue(veteran(Vocation.MONK).backpack().count(MagicTest.id("ultimate_health_potion")) > 0);
    }
}
