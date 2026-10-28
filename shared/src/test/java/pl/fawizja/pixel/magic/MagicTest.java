package pl.fawizja.pixel.magic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.item.ItemCategory;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Lighting;
import pl.fawizja.pixel.world.Position;

class MagicTest {

    static final MonsterRegistry MONSTERS = MonsterRegistry.loadDefault(TestData.ITEMS);
    static final SpellRegistry SPELLS = SpellRegistry.loadDefault(TestData.ITEMS);
    static final ConsumableRegistry CONSUMABLES = ConsumableRegistry.loadDefault(TestData.ITEMS);
    static final RecipeRegistry RECIPES = RecipeRegistry.loadDefault(TestData.ITEMS);

    /** Postac poza miastem (10,12,0) z opcjonalnym potworem obok. */
    static GameSession session(PlayerCharacter pc, String monster, Position monsterAt) {
        List<SpawnPoint> sp = monster == null ? Collections.emptyList()
                : List.of(new SpawnPoint(monster, monsterAt, 0, 1, 600));
        MonsterManager mm = new MonsterManager(TestData.varden(), MONSTERS, sp, new Random(3));
        mm.populate(null);
        GameSession s = new GameSession(TestData.ITEMS, TestData.varden(), pc, mm, new Random(5), 8, SPELLS, CONSUMABLES, RECIPES);
        s.teleport(new Position(10, 12, 0), 0);
        return s;
    }

    static int id(String key) { return TestData.ITEMS.byKey(key).id(); }

    @Test
    void dataIsConsistent() {
        for (Vocation v : Vocation.values()) {
            List<SpellType> known = SPELLS.forVocation(v);
            assertTrue(known.stream().anyMatch(s -> s.minLevel() <= 8 && s.kind() == SpellKind.ATTACK_TARGET), v + " has a starting attack spell");
            assertTrue(known.stream().anyMatch(s -> s.minLevel() <= 8 && s.kind() == SpellKind.HEAL_SELF), v + " can heal at start");
            assertTrue(known.stream().anyMatch(s -> s.kind() == SpellKind.ATTACK_AREA), v + " gets an area attack");
        }
        for (ItemType it : TestData.ITEMS.all()) {
            if (it.category() == ItemCategory.RUNE && !it.key().equals("blank_rune")) {
                assertTrue(SPELLS.find(it.key()) != null && SPELLS.get(it.key()).kind().isRune(), "rune effect for " + it.key());
            }
            if ((it.category() == ItemCategory.POTION || it.category() == ItemCategory.FOOD)
                    && pl.fawizja.pixel.item.Enchantments.Kind.ofPotion(it.key()) == null) { // wzmacniajace: PotionBuffTest
                assertNotNull(CONSUMABLES.get(it.id()), "consumable data for " + it.key());
            }
        }
        assertTrue(RECIPES.all().size() >= 6);
    }

    @Test
    void healingCostsManaAndRespectsCooldown() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        GameSession s = session(pc, null, null);
        pc.damage(100);
        int hp = pc.health(), mana = pc.mana();
        assertEquals(CastResult.OK, s.castSpell("mend", 1000));
        assertTrue(pc.health() > hp);
        assertEquals(mana - 20, pc.mana());
        assertEquals(CastResult.COOLDOWN, s.castSpell("mend", 1500));
        assertEquals(mana - 20, pc.mana(), "failed cast is free");
        assertEquals(CastResult.OK, s.castSpell("mend", 2000));
    }

    @Test
    void autoHealPicksTheStrongestHealingSpellThatCanBeCastNow() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        GameSession s = session(pc, null, null);
        assertEquals("mend", s.spells().bestHeal(0), "greater mend needs level 20");
        assertEquals(CastResult.OK, s.castSpell("mend", 1000));
        assertNull(s.spells().bestHeal(1500), "on cooldown");
        pc.addExperience(pl.fawizja.pixel.balance.Balance.experienceForLevel(20) - pc.experience());
        pc.restoreMana(pc.maxMana());
        assertEquals("greater_mend", s.spells().bestHeal(5000));
        pc.spendMana(pc.mana() - 30);
        assertEquals("mend", s.spells().bestHeal(5000), "not enough mana for greater mend");
        pc.spendMana(pc.mana());
        assertNull(s.spells().bestHeal(5000), "no mana - the potion is used instead");
    }

    @Test
    void druidHealsMoreThanSorcerer() {
        PlayerCharacter d = TestData.newCharacter(Vocation.DRUID);
        PlayerCharacter so = TestData.newCharacter(Vocation.SORCERER);
        assertTrue(SpellSystem.baseValue(SPELLS.get("mend"), d) > SpellSystem.baseValue(SPELLS.get("mend"), so) * 1.5);
    }

    @Test
    void vocationLevelAndManaGates() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        GameSession s = session(pc, null, null);
        int mana = pc.mana();
        assertEquals(CastResult.WRONG_VOCATION, s.castSpell("frost_bolt", 0));
        assertEquals(CastResult.LEVEL_TOO_LOW, s.castSpell("flame_ring", 0));
        assertEquals(CastResult.NO_TARGET, s.castSpell("ember_bolt", 0));
        pc.spendMana(pc.mana() - 5);
        assertEquals(CastResult.NOT_ENOUGH_MANA, s.castSpell("mend", 0));
        assertEquals(5, pc.mana());
        assertTrue(mana > 5);
        assertTrue(s.spells().knownSpells().stream().noneMatch(x -> x.key().equals("flame_ring")));
    }

    @Test
    void attackSpellHitsTargetInRangeOnly() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        GameSession s = session(pc, "bandit", new Position(12, 12, 0));
        Monster m = s.monsters().monsters().get(0);
        s.combat().setTarget(m);
        assertEquals(CastResult.OK, s.castSpell("ember_bolt", 0));
        assertTrue(m.health() < m.type().maxHealth());
        pl.fawizja.pixel.combat.CombatEvent bolt = s.combat().latestEvents(64).stream()
                .filter(e -> e.kind() == pl.fawizja.pixel.combat.CombatEvent.Kind.PROJECTILE).findFirst().orElseThrow();
        assertEquals(pc.position(), bolt.from(), "the bolt flies from the caster");
        assertEquals(m.position(), bolt.position());
        assertEquals("FIRE", bolt.effect());
        PlayerCharacter far = TestData.newCharacter(Vocation.SORCERER);
        GameSession s2 = session(far, "bandit", new Position(17, 12, 0));
        s2.teleport(new Position(9, 12, 0), 0);   // 8 pol: poza zasiegiem 7
        s2.combat().setTarget(s2.monsters().monsters().get(0));
        int mana = far.mana();
        assertEquals(CastResult.OUT_OF_RANGE, s2.castSpell("ember_bolt", 0));
        assertEquals(mana, far.mana());
    }

    @Test
    void noOffensiveMagicInProtectionZone() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        GameSession s = session(pc, null, null);
        s.teleport(new Position(31, 16, 0), 0);
        pc.addExperience(500_000);
        assertEquals(CastResult.PROTECTION_ZONE, s.castSpell("whirlwind_kick", 0));
        pc.damage(50);
        assertEquals(CastResult.OK, s.castSpell("mend", 0), "healing is allowed in town");
    }

    @Test
    void areaSpellHitsAllAdjacentMonsters() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        pc.addExperience(200_000); // poziom ~ 25
        MonsterManager mm = new MonsterManager(TestData.varden(), MONSTERS, List.of(
                new SpawnPoint("boar", new Position(11, 12, 0), 0, 1, 600),
                new SpawnPoint("boar", new Position(9, 12, 0), 0, 1, 600),
                new SpawnPoint("boar", new Position(14, 12, 0), 0, 1, 600)), new Random(1));
        mm.populate(null);
        GameSession s = new GameSession(TestData.ITEMS, TestData.varden(), pc, mm, new Random(2), 8, SPELLS, CONSUMABLES, RECIPES);
        s.teleport(new Position(10, 12, 0), 0);
        assertEquals(CastResult.OK, s.castSpell("whirlwind_kick", 0));
        int hurt = 0;
        for (Monster m : mm.monsters()) if (m.health() < m.type().maxHealth()) hurt++;
        assertEquals(2, hurt, "only the two adjacent boars");
        assertTrue(s.combat().latestEvents(64).stream().anyMatch(e -> e.kind() == pl.fawizja.pixel.combat.CombatEvent.Kind.AREA
                && e.position().equals(pc.position()) && e.amount() == 1), "area effect around the monk");
    }

    @Test
    void enchantingRunesIsAtomic() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        pc.addExperience(100_000);
        GameSession s = session(pc, null, null);
        assertEquals(CastResult.MAGIC_TOO_LOW, s.castSpell("enchant_fireburst", 0));
        pc.skills().set(Skill.MAGIC, 4, 0);
        pc.backpack().clear();
        assertEquals(CastResult.MISSING_ITEM, s.castSpell("enchant_fireburst", 0));
        pc.backpack().add(new ItemStack(id("blank_rune"), 2));
        int mana = pc.mana();
        assertEquals(CastResult.OK, s.castSpell("enchant_fireburst", 0));
        assertEquals(3, pc.backpack().count(id("fireburst_rune")));
        assertEquals(1, pc.backpack().count(id("blank_rune")));
        assertEquals(mana - 80, pc.mana());
        // pelny plecak: nic nie znika, mana zostaje
        pc.backpack().add(new ItemStack(id("blank_rune"), 1)); // stos 2 - zuzycie jednej nie zwolni slotu
        for (int i = 0; pc.backpack().freeSlots() > 0; i++) pc.backpack().add(new ItemStack(id("leather_helmet"), 1));
        pc.backpack().add(new ItemStack(id("fireburst_rune"), 97)); // stos runy pelny (100)
        int manaBefore = pc.mana();
        assertEquals(CastResult.BACKPACK_FULL, s.castSpell("enchant_fireburst", 5000));
        assertEquals(2, pc.backpack().count(id("blank_rune")));
        assertEquals(manaBefore, pc.mana());
    }

    @Test
    void runesNeedMagicLevelAndAreConsumed() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        GameSession s = session(pc, "boar", new Position(12, 12, 0));
        Monster boar = s.monsters().monsters().get(0);
        s.combat().setTarget(boar);
        pc.backpack().clear();
        pc.backpack().add(new ItemStack(id("fireburst_rune"), 2));
        assertEquals(CastResult.MAGIC_TOO_LOW, s.useSlot(0, 0));
        assertEquals(2, pc.backpack().count(id("fireburst_rune")));
        pc.skills().set(Skill.MAGIC, 4, 0);
        assertEquals(CastResult.OK, s.useSlot(0, 0));
        assertEquals(1, pc.backpack().count(id("fireburst_rune")));
        assertTrue(boar.health() < boar.type().maxHealth());
        assertEquals(CastResult.COOLDOWN, s.useSlot(0, 500));
    }

    @Test
    void potionsHealAreLimitedAndNotWasted() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        GameSession s = session(pc, null, null);
        pc.backpack().add(new ItemStack(id("health_potion"), 3));
        pc.backpack().add(new ItemStack(id("great_health_potion"), 1));
        int before = pc.backpack().count(id("health_potion"));
        assertEquals(CastResult.FULL, s.usePotion("health_potion", 0), "full HP - nothing used");
        assertEquals(before, pc.backpack().count(id("health_potion")));
        pc.damage(120);
        int hp = pc.health();
        assertEquals(CastResult.OK, s.usePotion("health_potion", 0));
        assertTrue(pc.health() - hp >= 60 && pc.health() - hp <= 90);
        assertEquals(CastResult.COOLDOWN, s.usePotion("health_potion", 400));
        assertEquals(CastResult.LEVEL_TOO_LOW, s.usePotion("great_health_potion", 2000));
        PlayerCharacter sorc = TestData.newCharacter(Vocation.SORCERER);
        GameSession s2 = session(sorc, null, null);
        sorc.addExperience(10_000_000);
        sorc.damage(100);
        sorc.backpack().add(new ItemStack(id("great_health_potion"), 1));
        assertEquals(CastResult.WRONG_VOCATION, s2.usePotion("great_health_potion", 0));
    }

    @Test
    void quickPotionButtonsUseTheStrongestAllowedPotionCarried() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        GameSession s = session(pc, null, null);
        pc.backpack().add(new ItemStack(id("health_potion"), 3));
        pc.backpack().add(new ItemStack(id("great_health_potion"), 2));
        int basic = pc.backpack().count(id("health_potion"));
        int mana = pc.backpack().count(id("mana_potion"));
        assertEquals("health_potion", s.quickPotion(false), "level too low for the great potion");
        pc.addExperience(10_000_000);
        assertEquals("great_health_potion", s.quickPotion(false));
        pc.damage(pc.maxHealth() / 2);
        assertEquals(CastResult.OK, s.usePotion("health_potion", 0));
        assertEquals(1, pc.backpack().count(id("great_health_potion")), "the HP button drank the great potion");
        assertEquals(basic, pc.backpack().count(id("health_potion")));
        assertEquals("mana_potion", s.quickPotion(true), mana + " basic mana potions, no stronger ones");
        pc.backpack().add(new ItemStack(id("strong_health_potion"), 1));
        while (pc.level() < 100) pc.addExperience(50_000_000);
        assertEquals("strong_health_potion", s.quickPotion(false), "level " + pc.level() + ": the level 100 tier");
    }

    @Test
    void restingInAProtectionZoneTriplesRegeneration() {
        PlayerCharacter resting = TestData.newCharacter(Vocation.MONK);
        PlayerCharacter out = TestData.newCharacter(Vocation.MONK);
        resting.damage(100);
        out.damage(100);
        resting.tickRegeneration(60_000, true);
        out.tickRegeneration(60_000, false);
        assertEquals((out.health() - 85) * 3, resting.health() - 85);
    }

    @org.junit.jupiter.api.Test
    void foodDoublesRegenerationAndCannotOverfill() {
        PlayerCharacter fed = TestData.newCharacter(Vocation.MONK);
        PlayerCharacter hungry = TestData.newCharacter(Vocation.MONK);
        GameSession s = session(fed, null, null);
        fed.backpack().add(new ItemStack(id("meat"), 10));
        assertEquals(CastResult.OK, s.usePotion("meat", 0));
        fed.damage(100);
        hungry.damage(100);
        fed.tickRegeneration(60_000);
        hungry.tickRegeneration(60_000);
        assertEquals((hungry.health() - 85) * 2, fed.health() - 85);
        int used = 0;
        while (s.usePotion("meat", 0) == CastResult.OK) used++;
        assertTrue(used < 7, "20 minutes cap");
        assertTrue(fed.fedMs() <= PlayerCharacter.MAX_FED_MS);
    }

    @Test
    void lightSpellLastsFiveMinutes() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        GameSession s = session(pc, null, null);
        float before = Lighting.playerLightRadius(pc, TestData.ITEMS);
        assertEquals(CastResult.OK, s.castSpell("glow", 0));
        assertTrue(Lighting.playerLightRadius(pc, TestData.ITEMS) >= 4);
        pc.tickRegeneration(SpellSystem.LIGHT_SPELL_MS + 1);
        assertEquals(before, Lighting.playerLightRadius(pc, TestData.ITEMS));
    }

    @Test
    void castingTrainsMagicFasterForMages() {
        PlayerCharacter druid = TestData.newCharacter(Vocation.DRUID);
        PlayerCharacter monk = TestData.newCharacter(Vocation.MONK);
        GameSession sd = session(druid, null, null);
        GameSession sm = session(monk, null, null);
        for (int i = 0; i < 20; i++) {
            druid.restoreMana(1000);
            monk.restoreMana(1000);
            sd.castSpell("glow", i * 3000L);
            sm.castSpell("glow", i * 3000L);
        }
        assertTrue(druid.skills().level(Skill.MAGIC) > monk.skills().level(Skill.MAGIC),
                druid.skills().level(Skill.MAGIC) + " vs " + monk.skills().level(Skill.MAGIC));
    }

    @Test
    void craftingIsAtomic() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        GameSession s = session(pc, null, null);
        pc.backpack().add(new ItemStack(id("wolf_pelt"), 3));
        pc.backpack().add(new ItemStack(id("spider_silk"), 1));
        assertEquals(CastResult.MISSING_ITEM, s.craft("leather_armor", 0));
        assertEquals(3, pc.backpack().count(id("wolf_pelt")));
        pc.backpack().add(new ItemStack(id("wolf_pelt"), 1));
        assertEquals(CastResult.OK, s.craft("leather_armor", 0));
        assertEquals(0, pc.backpack().count(id("wolf_pelt")));
        assertEquals(0, pc.backpack().count(id("spider_silk")));
        assertTrue(pc.backpack().count(id("leather_armor")) >= 1);
        // koszt w zlocie
        long gold = s.gold();
        pc.backpack().add(new ItemStack(id("iron_ore"), 5));
        assertEquals(CastResult.LEVEL_TOO_LOW, s.craft("iron_sword", 0));
        pc.addExperience(50_000);
        assertEquals(CastResult.OK, s.craft("iron_sword", 0));
        assertEquals(gold - 100, s.gold());
        // pelny plecak - nic nie znika
        pc.backpack().add(new ItemStack(id("iron_ore"), 5));
        while (pc.backpack().freeSlots() > 0) pc.backpack().add(new ItemStack(id("leather_helmet"), 1));
        int ore = pc.backpack().count(id("iron_ore"));
        CastResult r = s.craft("iron_sword", 0);
        // ruda zwalnia slot, wiec miecz sie miesci - wtedy OK; przy wiekszym stosie rudy - BACKPACK_FULL
        if (r != CastResult.OK) {
            assertEquals(CastResult.BACKPACK_FULL, r);
            assertEquals(ore, pc.backpack().count(id("iron_ore")));
        }
    }
}
