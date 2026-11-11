package pl.fawizja.pixel.magic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.Actions;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.settings.SpellBar;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

/** Paski akcji jak w Tibii 12+: runa na cel / na siebie / z celownikiem, combo czarow, zapis w postaci. */
class ActionBarTest {

    static final Position ME = new Position(10, 12, 0);

    static GameSession sorcerer(int level, String monster, Position at) {
        PlayerCharacter pc = TibiaSpellsTest.at(Vocation.SORCERER, level);
        GameSession s = MagicTest.session(pc, monster, at);
        return s;
    }

    static int runes(GameSession s, String key) { return s.character().backpack().count(MagicTest.id(key)); }

    static boolean hurt(Monster m) { return m.health() < m.type().maxHealth(); }

    @Test
    void targetModeIsTheDefaultAndUsesTheAttackTarget() {
        GameSession s = sorcerer(40, "boar", new Position(13, 12, 0));
        Monster boar = s.monsters().monsters().get(0);
        s.character().backpack().add(new ItemStack(MagicTest.id("great_fireball_rune"), 2));
        assertEquals(SpellBar.MODE_TARGET, SpellBar.mode("item:great_fireball_rune"), "area runes default to the target");
        assertTrue(Actions.apply(s, "item:great_fireball_rune", 1000));
        assertFalse(hurt(boar), "no target: the rune is not thrown");
        assertEquals(2, runes(s, "great_fireball_rune"), "a refused rune is not used up");
        s.combat().setTarget(boar);
        assertTrue(Actions.apply(s, "item:great_fireball_rune", 1000));
        assertTrue(hurt(boar));
        assertEquals(1, runes(s, "great_fireball_rune"));
    }

    @Test
    void crosshairThrowsTheRuneOnTheChosenTileWithoutChangingTheTarget() {
        GameSession s = sorcerer(40, "boar", new Position(14, 12, 0));
        Monster boar = s.monsters().monsters().get(0);
        s.character().backpack().add(new ItemStack(MagicTest.id("great_fireball_rune"), 3));
        // pole obok dzika (bez dzika na nim): promien 3 i tak go trafia, cel walki zostaje pusty
        assertTrue(Actions.apply(s, "item:great_fireball_rune:at:13:13:0", 1000));
        assertTrue(hurt(boar), "GFB centred on the chosen tile");
        assertNull(s.combat().target(), "using a rune with the crosshair does not pick a target");
        assertEquals(2, runes(s, "great_fireball_rune"));
        assertTrue(s.combat().latestEvents(64).stream().anyMatch(e -> e.kind() == pl.fawizja.pixel.combat.CombatEvent.Kind.AREA
                && e.position().equals(new Position(13, 13, 0))), "explosion drawn on the chosen tile");
        // zasieg runy 7 pol jak w Tibii
        assertEquals(CastResult.OUT_OF_RANGE, s.useItemAt("great_fireball_rune", new Position(18, 12, 0), 5000));
        assertEquals(CastResult.OUT_OF_RANGE, s.useItemAt("great_fireball_rune", new Position(12, 12, 1), 5000), "other floor");
        assertEquals(2, runes(s, "great_fireball_rune"), "refused throws cost nothing");
    }

    @Test
    void crosshairNeedsLineOfSight() {
        GameSession s = sorcerer(40, null, null);
        s.character().backpack().add(new ItemStack(MagicTest.id("great_fireball_rune"), 1));
        Position hidden = null;
        for (int dx = -7; dx <= 7 && hidden == null; dx++) {
            for (int dy = -7; dy <= 7 && hidden == null; dy++) {
                Position p = ME.translate(dx, dy);
                if (s.world().isWalkable(p) && !s.world().hasLineOfSight(ME, p)) hidden = p;
            }
        }
        assertNotNull(hidden, "test map has a tile behind a wall near the start");
        assertFalse(s.spells().aimReachable(s.spells().registry().get("great_fireball_rune"), hidden));
        assertEquals(CastResult.NOT_VISIBLE, s.useItemAt("great_fireball_rune", hidden, 1000));
        assertEquals(1, runes(s, "great_fireball_rune"));
    }

    @Test
    void creatureRunesNeedACreatureOnTheTile() {
        GameSession s = sorcerer(45, "boar", new Position(13, 12, 0));
        Monster boar = s.monsters().monsters().get(0);
        s.character().backpack().add(new ItemStack(MagicTest.id("sudden_death_rune"), 2));
        assertEquals(CastResult.ONLY_CREATURES, s.useItemAt("sudden_death_rune", new Position(12, 13, 0), 1000));
        assertEquals(2, runes(s, "sudden_death_rune"));
        assertEquals(CastResult.OK, s.useItemAt("sudden_death_rune", boar.position(), 1000));
        assertTrue(hurt(boar));
        assertNull(s.combat().target());
        assertEquals(1, runes(s, "sudden_death_rune"));
        // na siebie: nie ma tu stworzenia do trafienia (jak w Tibii nie atakujesz sam siebie)
        assertEquals(CastResult.ONLY_CREATURES, s.useItemAt("sudden_death_rune", s.character().position(), 5000));
    }

    @Test
    void selfModeThrowsAreaRunesAroundYouAndHealsYou() {
        GameSession s = sorcerer(40, "boar", new Position(12, 12, 0));
        Monster boar = s.monsters().monsters().get(0);
        PlayerCharacter pc = s.character();
        pc.backpack().add(new ItemStack(MagicTest.id("great_fireball_rune"), 1));
        pc.backpack().add(new ItemStack(MagicTest.id("ultimate_healing_rune"), 2));
        assertTrue(Actions.apply(s, "item:great_fireball_rune:self", 1000));
        assertTrue(hurt(boar), "GFB on yourself hits monsters around you");
        assertTrue(s.combat().latestEvents(64).stream().anyMatch(e -> e.kind() == pl.fawizja.pixel.combat.CombatEvent.Kind.AREA
                && e.position().equals(pc.position()) && SpellArea.decodeArea(e.amount()) == SpellArea.CIRCLE3X3));
        pc.damage(pc.health() / 2);
        int hp = pc.health();
        assertTrue(Actions.apply(s, "item:ultimate_healing_rune:self", 2000));
        assertTrue(pc.health() > hp, "UH on yourself");
        assertEquals(CastResult.ONLY_SELF, s.useItemAt("ultimate_healing_rune", new Position(11, 12, 0), 5000));
        assertEquals(1, runes(s, "ultimate_healing_rune"));
    }

    @Test
    void backpackUseWithCrosshair() {
        GameSession s = sorcerer(40, "boar", new Position(13, 12, 0));
        Monster boar = s.monsters().monsters().get(0);
        s.character().backpack().clear();
        s.character().backpack().putInSlot(4, new ItemStack(MagicTest.id("great_fireball_rune"), 1));
        assertTrue(Actions.apply(s, "slot_at:4:13:12:0", 1000));
        assertTrue(hurt(boar));
        assertNull(s.character().backpack().get(4), "the rune from that backpack slot is used");
    }

    @Test
    void comboCastsTheFirstReadySpellAndRespectsCooldowns() {
        GameSession s = sorcerer(20, "boar", new Position(12, 12, 0));
        Monster boar = s.monsters().monsters().get(0);
        PlayerCharacter pc = s.character();
        s.combat().setTarget(boar);
        String combo = SpellBar.combo(List.of("energy_strike", "light_healing"));
        assertEquals("combo:energy_strike+light_healing", combo);
        int hp0 = boar.health();
        int mana = pc.mana();
        assertTrue(Actions.apply(s, combo, 1000));
        assertTrue(boar.health() < hp0, "1st press: the attack");
        assertTrue(s.spells().cooldowns().remaining(s.spells().registry().get("energy_strike"), 1000) > 0);
        assertEquals(0, s.spells().cooldowns().remaining(s.spells().registry().get("light_healing"), 1000), "one spell per press");
        int hp1 = boar.health();
        Actions.apply(s, combo, 1100);
        assertEquals(hp1, boar.health(), "2nd press: attack still cooling down");
        assertTrue(s.spells().cooldowns().remaining(s.spells().registry().get("light_healing"), 1100) > 0, "... so the heal goes");
        int manaAfter = pc.mana();
        assertTrue(manaAfter < mana);
        assertEquals(CastResult.COOLDOWN, s.castCombo(List.of("energy_strike", "light_healing"), 1200), "nothing ready: no cast");
        assertEquals(manaAfter, pc.mana(), "and no mana spent");
        assertFalse(s.spells().ready("energy_strike", 1500));
        assertTrue(s.spells().ready("energy_strike", 3100));
        assertEquals(CastResult.OK, s.castCombo(List.of("energy_strike", "light_healing"), 3100));
        assertTrue(boar.health() < hp1, "attack again once its cooldown is over");
    }

    @Test
    void comboSkipsSpellsThatCannotBeCastNow() {
        GameSession s = sorcerer(20, null, null);
        // brak celu: atak odpada, leci leczenie
        assertEquals(CastResult.OK, s.castCombo(List.of("energy_strike", "light_healing"), 1000));
        assertTrue(s.spells().cooldowns().remaining(s.spells().registry().get("light_healing"), 1000) > 0);
        // grupa leczenia (1 s) blokuje tez inne czary leczace - drugie leczenie z combo czeka
        assertEquals(CastResult.COOLDOWN, s.castCombo(List.of("magic_patch", "intense_healing"), 1100));
    }

    @Test
    void barIsSavedInTheCharacterAndOldSavesLoad() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        GameSession s = MagicTest.session(pc, null, null);
        SpellBar bar = new SpellBar();
        bar.set(0, "combo:energy_strike+light_healing");
        bar.set(1, "item:great_fireball_rune:aim");
        bar.set(2, "item:ultimate_healing_rune:self");
        bar.set(3, "item:sudden_death_rune");
        bar.set(4, SpellBar.NONE);
        assertTrue(Actions.apply(s, "bar:" + bar.encode(), 0));
        assertEquals(bar.encode(), pc.actionBar());
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        String text = codec.encode(pc);
        assertTrue(text.startsWith("PIXELSAVE 9\n"));
        PlayerCharacter back = codec.decode(text);
        assertEquals(bar.encode(), back.actionBar(), "round trip");
        assertEquals(SpellBar.MODE_AIM, SpellBar.mode(SpellBar.decode(back.actionBar()).custom(1)));
        // klient online dostaje pasek w SELF
        PlayerCharacter client = TestData.newCharacter(Vocation.SORCERER);
        client.copyStateFrom(codec.decode(codec.encode(pc, false)));
        assertEquals(bar.encode(), client.actionBar());
        // zapis v8 (bez paska) czyta sie z pustym paskiem
        String v8body = text.substring(text.indexOf('\n') + 1, text.lastIndexOf("crc=")).replaceAll("(?m)^bar=.*\n", "");
        java.util.zip.CRC32 c = new java.util.zip.CRC32();
        c.update(v8body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        PlayerCharacter old = codec.decode("PIXELSAVE 8\n" + v8body + "crc=" + Long.toHexString(c.getValue()) + "\n");
        assertEquals("", old.actionBar());
        // smieci od klienta nie trafiaja do zapisu
        Actions.apply(s, "bar:DROP TABLE,item:x y,combo:a+b+c+d+e", 0);
        assertEquals("", pc.actionBar());
        Actions.apply(s, "bar:-,-,-,-,-,-,-,-,-,-", 0);
        assertEquals("", pc.actionBar(), "empty layout = automatic bar");
    }
}
