package pl.fawizja.pixel.magic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.combat.ConditionType;
import pl.fawizja.pixel.combat.Conditions;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterConditions;
import pl.fawizja.pixel.monster.SummonTable;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Fields;
import pl.fawizja.pixel.world.Position;

/** 0.240: czary, runy, stany i pola jak w Tibii. */
class TibiaSpellsTest {

    static PlayerCharacter at(Vocation v, int level) {
        PlayerCharacter pc = TestData.newCharacter(v);
        pc.addExperience(Balance.experienceForLevel(level) - pc.experience());
        pc.skills().set(Skill.MAGIC, 30, 0);
        pc.restoreMana(pc.maxMana());
        return pc;
    }

    static void tick(GameSession s, long from, long to) {
        for (long t = from; t <= to; t += 100) s.update(t, null);
    }

    @Test
    void conditionDamageListFollowsCanary() {
        List<Integer> l = Conditions.damageList(100, 5);
        assertEquals(5, l.get(0).intValue());
        int sum = l.stream().mapToInt(Integer::intValue).sum();
        assertTrue(Math.abs(sum - 100) <= 5, "sum " + sum);
        for (int i = 1; i < l.size(); i++) assertTrue(l.get(i) <= l.get(i - 1), "decreasing");
        Conditions c = new Conditions();
        assertTrue(c.add(ConditionType.POISON, Conditions.constant(3, 10), 2000, true, 0, "test"));
        assertTrue(c.tick(1999).isEmpty(), "delayed: first tick after the interval");
        assertEquals(1, c.tick(2000).size());
        assertFalse(c.add(ConditionType.POISON, Conditions.constant(1, 5), 2000, false, 2000, "weak"), "weaker poison does not replace");
        assertEquals(2, c.tick(6000).size());
        assertFalse(c.has(ConditionType.POISON));
    }

    @Test
    void poisonTicksAndExanaPoxCuresIt() {
        PlayerCharacter pc = at(Vocation.KNIGHT, 20);
        GameSession s = MagicTest.session(pc, null, null);
        pc.conditions().addTotal(ConditionType.POISON, 200, 2000, 0, "Pająk leśny");
        int hp = pc.health();
        tick(s, 0, 4100);
        assertTrue(pc.health() < hp, "poison ticks");
        assertEquals(CastResult.OK, s.castSpell(s.spells().registry().byWords("exana pox"), 5000));
        assertFalse(pc.conditions().has(ConditionType.POISON));
        assertEquals(CastResult.NO_CONDITION, s.castSpell("cure_poison", 12_000), "nothing to cure");
        // leczenie podpalenia tylko druid (Tibia)
        assertEquals(CastResult.WRONG_VOCATION, s.castSpell("cure_burning", 12_000));
    }

    @Test
    void spidersPoisonAndConditionsCanKill() {
        MonsterConditions.Entry e = MonsterConditions.defaults().of(MagicTest.MONSTERS.get("forest_spider"));
        assertNotNull(e);
        assertEquals(ConditionType.POISON, e.type());

        PlayerCharacter pc = at(Vocation.SORCERER, 8);
        GameSession s = MagicTest.session(pc, null, null);
        pc.damage(pc.health() - 5);
        pc.conditions().add(ConditionType.BURNING, Conditions.constant(3, 10), 1000, false, 0, "Żywiołak ognia");
        tick(s, 0, 300);
        assertTrue(s.combat().lastDeath() != null, "a condition can kill like in Tibia");
    }

    @Test
    void summonTableAndMonsterConditionKeysExist() {
        for (String k : SummonTable.defaults().all().keySet()) assertTrue(MagicTest.MONSTERS.contains(k), "summon " + k);
        for (String k : MonsterConditions.defaults().all().keySet()) assertTrue(MagicTest.MONSTERS.contains(k), "condition " + k);
    }

    @Test
    void fireFieldRuneBurnsAMonsterStandingInIt() {
        PlayerCharacter pc = at(Vocation.SORCERER, 40);
        GameSession s = MagicTest.session(pc, "boar", new Position(12, 12, 0));
        Monster boar = s.monsters().monsters().get(0);
        boar.paralyze(Long.MAX_VALUE);
        s.combat().setTarget(boar);
        pc.backpack().add(new ItemStack(MagicTest.id("fire_field_rune"), 2));
        assertEquals(CastResult.OK, s.spells().useFirst("fire_field_rune", 1000));
        Fields.Field f = s.world().fields().at(boar.position());
        assertNotNull(f, "field under the boar");
        assertEquals(Fields.Kind.FIRE, f.kind());
        s.update(1000, null);
        assertTrue(boar.health() < boar.type().maxHealth(), "first tick at once");
        assertNotNull(s.spells().tibia().conditionsOf(boar));
        // pole wygasa po czasie z Tibii: 200 s, potem slabsze 148 s
        s.world().fields().expire(1000 + Fields.Kind.FIRE.durationMs);
        assertEquals(Fields.Kind.FIRE_WEAK, s.world().fields().at(f.position()).kind());
        s.world().fields().expire(1000 + Fields.Kind.FIRE.durationMs + Fields.Kind.FIRE_WEAK.durationMs);
        assertNull(s.world().fields().at(f.position()));
    }

    @Test
    void walkingIntoAPoisonFieldPoisonsThePlayer() {
        PlayerCharacter pc = at(Vocation.KNIGHT, 20);
        GameSession s = MagicTest.session(pc, null, null);
        Position next = pc.position().translate(1, 0);
        assertNotNull(s.world().fields().place(next, Fields.Kind.POISON, 0, "x", s.world()));
        s.update(0, Direction.EAST);
        assertEquals(next, pc.position());
        s.update(500, null);
        assertTrue(pc.conditions().has(ConditionType.POISON), "poison field poisons");
        s.world().fields().clear();
    }

    @Test
    void magicWallBlocksWalkingAndSight() {
        PlayerCharacter pc = at(Vocation.SORCERER, 40);
        GameSession s = MagicTest.session(pc, null, null);
        s.world().fields().clear();
        pc.backpack().add(new ItemStack(MagicTest.id("magic_wall_rune"), 1));
        Position front = pc.position().translate(Direction.SOUTH.dx, Direction.SOUTH.dy);
        assertTrue(s.world().isWalkable(front));
        assertEquals(CastResult.OK, s.spells().useFirst("magic_wall_rune", 1000), "aims at the tile in front without a target");
        assertFalse(s.world().isWalkable(front), "magic wall blocks");
        assertFalse(s.world().hasLineOfSight(pc.position(), front.translate(0, 2)), "and blocks missiles");
        s.world().fields().expire(1000 + Fields.Kind.MAGIC_WALL.durationMs);
        assertTrue(s.world().isWalkable(front), "gone after 20 s");
    }

    @Test
    void greatFireballHitsEverythingInRadiusThree() {
        PlayerCharacter pc = at(Vocation.SORCERER, 40);
        GameSession s = MagicTest.session(pc, "boar", new Position(15, 12, 0));
        Monster boar = s.monsters().monsters().get(0);
        s.combat().setTarget(boar);
        assertEquals(3, s.spells().registry().get("great_fireball_rune").radius(), "Tibia: GFB circle radius 3");
        assertEquals(3, s.spells().registry().get("avalanche_rune").radius());
        pc.backpack().add(new ItemStack(MagicTest.id("great_fireball_rune"), 1));
        assertEquals(CastResult.OK, s.spells().useFirst("great_fireball_rune", 1000));
        assertTrue(boar.health() < boar.type().maxHealth());
    }

    @Test
    void waveHitsTheConeInFrontOnly() {
        List<Position> w = TibiaSpells.waveTiles(new Position(10, 10, 0), Direction.EAST, 5, 0, null);
        assertTrue(w.contains(new Position(11, 10, 0)));
        assertTrue(w.contains(new Position(15, 12, 0)), "wide at the end");
        assertFalse(w.contains(new Position(9, 10, 0)), "nothing behind");
        assertFalse(w.contains(new Position(11, 11, 0)), "narrow at the start");
    }

    @Test
    void incantationsWithParameters() {
        SpellRegistry r = MagicTest.SPELLS;
        assertEquals(new SpellRegistry.Incantation("summon_creature", "wilk"), r.incantation("utevo res \"wilk\""));
        assertEquals(new SpellRegistry.Incantation("heal_friend", "ula"), r.incantation("exura sio \"Ula\""));
        assertEquals("illusion", r.incantation("utevo res ina \"szkielet\"").key().equals("creature_illusion") ? "illusion" : "x");
        assertNull(r.incantation("utevo"));
    }

    @Test
    void summonCreatureCostsItsManaAndIsLimitedToTwo() {
        PlayerCharacter pc = at(Vocation.DRUID, 40);
        GameSession s = MagicTest.session(pc, null, null);
        int mana = pc.mana();
        assertEquals(CastResult.OK, s.castSpell("summon_creature", "wilk", 1000));
        assertEquals(mana - SummonTable.defaults().mana("wolf"), pc.mana(), "Tibia: wolf costs 255 mana");
        assertEquals(1, s.spells().tibia().summons().size());
        assertTrue(s.spells().tibia().summons().get(0).isSummon());
        pc.restoreMana(pc.maxMana());
        assertEquals(CastResult.NOT_POSSIBLE, s.castSpell("summon_creature", "smok", 3000), "only creatures from summons.csv");
        assertEquals(CastResult.OK, s.castSpell("summon_creature", "Szczur kanałowy", 4000));
        pc.restoreMana(pc.maxMana());
        assertEquals(CastResult.TOO_MANY_SUMMONS, s.castSpell("summon_creature", "wilk", 8000));
        assertFalse(s.targetAt(s.spells().tibia().summons().get(0).position()), "own summon cannot be targeted");
    }

    @Test
    void magicShieldTakesManaInsteadOfHealth() {
        PlayerCharacter pc = at(Vocation.SORCERER, 40);
        GameSession s = MagicTest.session(pc, null, null);
        assertEquals(CastResult.OK, s.castSpell("magic_shield", 1000));
        int hp = pc.health(), mana = pc.mana();
        pc.damage(50);
        assertEquals(hp, pc.health());
        assertEquals(mana - 50, pc.mana());
    }

    @Test
    void invisibilityHidesFromMonstersUntilAttacking() {
        PlayerCharacter pc = at(Vocation.SORCERER, 40);
        GameSession s = MagicTest.session(pc, null, null);
        assertEquals(CastResult.OK, s.castSpell("invisibility", 1000));
        assertFalse(s.playerTarget().attackable());
        pc.breakInvisibility();
        assertTrue(s.playerTarget().attackable());
    }

    @Test
    void exaniHurUpWorksOnlyUnderOpenAirAndDownOnlyOverAnEdge() {
        PlayerCharacter pc = at(Vocation.KNIGHT, 20);
        GameSession s = MagicTest.session(pc, null, null);
        // zwykle pole na powierzchni: nad glowa nic nie ma do stania, przed soba brak krawedzi
        assertEquals(CastResult.NO_ROOM, s.castSpell("levitate_down", 1000), "solid ground in front");
        assertNull(s.levitationDestination(false));
        Position here = pc.position();
        if (s.world().floor(1) == null) {
            assertEquals(CastResult.NO_ROOM, s.castSpell("levitate_up", 3000), "no floor above to stand on");
        }
        assertEquals(here, pc.position());
    }

    @Test
    void sewerGratesNeedATapNotAStep() {
        pl.fawizja.pixel.world.WorldMap w = TestData.world();
        pl.fawizja.pixel.world.Transition grate = null;
        for (pl.fawizja.pixel.world.Transition t : w.floor(0).transitions().values()) {
            if (t.type() == pl.fawizja.pixel.world.TransitionType.GRATE) { grate = t; break; }
        }
        assertNotNull(grate, "town sewer grates are GRATE transitions");
        assertFalse(grate.type().triggeredByWalking(), "walking onto a grate does nothing");
        PlayerCharacter pc = at(Vocation.KNIGHT, 20);
        GameSession s = new GameSession(TestData.ITEMS, w, pc, null, new java.util.Random(1), 8, MagicTest.SPELLS,
                MagicTest.CONSUMABLES, MagicTest.RECIPES);
        s.teleport(grate.from(), 0);
        assertEquals(0, pc.position().z(), "standing on the grate keeps you on the street");
        assertTrue(s.usableAt(grate.from()), "the grate is usable (tap)");
        s.useAt(grate.from(), 100);
        assertEquals(grate.to(), pc.position(), "tapping the grate goes down into the sewer");
    }
}
