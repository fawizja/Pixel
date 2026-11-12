package pl.fawizja.pixel.monster;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.combat.CombatEvent;
import pl.fawizja.pixel.combat.CombatSystem;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.monster.MonsterProfile.Archetype;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.GroundItems;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/** Style ataku potworow (Tibia): profile wszystkich potworow, dystans strzelcow, przywolywanie, ucieczka, pociski. */
class MonsterStyleTest {

    static final MonsterRegistry REG = MonsterAiTest.REG;
    static final Set<String> MISSILES = Set.of("arrow", "spear", "fire", "ice", "earth", "energy", "stone", "star", "knife", "holy",
            "death", "arrow_red", "bolt", "star_small", "fire_arrow", "poison", "shadow_star");

    /** Atrapa gracza zliczajaca ataki i efekty potworow. */
    static class Probe extends MonsterAiTest.Dummy {
        final List<String> effects = new ArrayList<>();
        Probe(Position p) { super(p); }
        @Override public void monsterEffect(Monster caster, Position at, String effect, long now) { effects.add(effect); }
    }

    @Test
    void everyMonsterHasAValidProfileConsistentWithItsSpells() {
        MonsterProfiles profiles = MonsterProfiles.defaults();
        MonsterSpells spells = MonsterSpells.defaults();
        Map<Archetype, Integer> counts = new EnumMap<>(Archetype.class);
        for (MonsterType t : REG.all()) {
            assertTrue(profiles.has(t.key()), t.key() + " has a behaviour profile");
            MonsterProfile p = profiles.of(t);
            counts.merge(p.archetype(), 1, Integer::sum);
            if (p.missile() != null) assertTrue(MISSILES.contains(p.missile()), t.key() + " missile " + p.missile());
            assertTrue(p.keepDistance() <= MonsterManager.reach(t), t.key() + " keeps no farther than its range");
            boolean bolt = false, area = false, summon = false, heal = false;
            for (MonsterSpell s : spells.of(t)) {
                if (s.missile() != null) assertTrue(MISSILES.contains(s.missile()), t.key() + " spell missile " + s.missile());
                switch (s.mode()) {
                    case BOLT: bolt = true; break;
                    case WAVE: case BALL: case SELF: area = true; break;
                    case SUMMON:
                        summon = true;
                        assertTrue(REG.contains(s.summonKey()), t.key() + " summons a real monster " + s.summonKey());
                        assertTrue(REG.get(s.summonKey()).level() < t.level(), t.key() + " minion is weaker than its master");
                        break;
                    case HEAL: heal = true; break;
                    default:
                }
            }
            switch (p.archetype()) {
                case MELEE: assertFalse(spells.casts(t), t.key() + " pure melee has no attack spells"); assertEquals(0, p.keepDistance()); break;
                case DISTANCE: assertTrue(p.keepDistance() >= 2 && p.missile() != null, t.key() + " shooter keeps distance and shows a missile"); break;
                case CASTER: assertTrue(bolt && p.keepDistance() >= 2, t.key()); break;
                case AREA: assertTrue(area, t.key()); break;
                case SUMMONER: assertTrue(summon, t.key()); break;
                case SUPPORT: assertTrue(heal || t.healAmount() > 0, t.key()); break;
                case MIXED: assertTrue(spells.hasAny(t), t.key()); break;
                default:
            }
        }
        for (Archetype a : Archetype.values()) assertTrue(counts.getOrDefault(a, 0) >= 3, a + " is used: " + counts);
        for (String k : profiles.all().keySet()) assertTrue(REG.contains(k), "profile for unknown monster " + k);
    }

    @Test
    void rangedMonstersKeepThreeToFourTilesAndShoot() {
        WorldMap w = MonsterChaseTest.field();
        int checked = 0;
        for (MonsterType t : REG.all()) {
            MonsterProfile p = MonsterProfiles.defaults().of(t);
            if (!p.keepsDistance() || t.boss() || p.archetype() == Archetype.SUPPORT) continue;
            Position at = new Position(10, 12, 0);
            MonsterManager mm = MonsterChaseTest.single(w, t.key(), at);
            Monster m = mm.monsters().get(0);
            Probe d = new Probe(at.translate(0, 1));
            MonsterChaseTest.run(mm, d, 0, 25_000);
            int dist = m.position().distanceTo(d.pos);
            assertTrue(dist >= p.keepDistance() && dist <= MonsterManager.holdDistance(t) + 1,
                    t.key() + " holds " + p.keepDistance() + ".." + MonsterManager.holdDistance(t) + " tiles, was " + dist);
            assertTrue(d.attacks > 0, t.key() + " shoots from there");
            checked++;
        }
        assertTrue(checked >= 15, "many ranged monsters checked: " + checked);
    }

    @Test
    void summonerCallsHelpersUpToItsCapAndTheyVanishWithTheMaster() {
        WorldMap w = MonsterChaseTest.field();
        MonsterManager mm = MonsterChaseTest.single(w, "candle_acolyte", new Position(10, 6, 0));
        Monster master = mm.monsters().get(0);
        int cap = MonsterSpells.defaults().support(master.type()).stream().filter(s -> s.mode() == MonsterSpell.Mode.SUMMON)
                .findFirst().orElseThrow().summonCap();
        Probe d = new Probe(new Position(10, 11, 0));
        int most = 0;
        for (long t = 0; t < 400_000 && most < cap; t += 50) {
            mm.update(t, d, d);
            most = Math.max(most, master.minions().size());
            assertTrue(master.minions().size() <= cap, "never above the cap");
        }
        assertEquals(cap, most, "summoner reached its cap");
        assertTrue(d.effects.contains("MAGIC"), "cast effect is shown");
        for (Monster h : master.minions()) {
            assertSame(master, h.master());
            assertEquals("skeleton", h.type().key());
            assertTrue(mm.monsters().contains(h));
        }
        // dalej nie przywoluje ponad limit
        for (long t = 400_000; t < 460_000; t += 50) {
            mm.update(t, d, d);
            assertTrue(master.minions().size() <= cap);
        }
        // zabity pomocnik nie odradza sie
        Monster first = master.minions().get(0);
        mm.kill(first, 460_000);
        assertEquals(cap - 1, master.minions().size());
        assertFalse(mm.monsters().contains(first));
        // smierc przywolujacego usuwa pomocnikow
        List<Monster> helpers = new ArrayList<>(master.minions());
        mm.kill(master, 461_000);
        for (Monster h : helpers) assertFalse(mm.monsters().contains(h), "minion despawned with its master");
        assertTrue(master.minions().isEmpty());
        assertTrue(mm.monsters().stream().noneMatch(Monster::isMinion));
    }

    @Test
    void woundedMonsterFleesAndSelfHealingCasterKeepsRunningAndHeals() {
        WorldMap w = MonsterChaseTest.field();
        MonsterManager mm = MonsterChaseTest.single(w, "elf_scout", new Position(10, 10, 0));
        Monster m = mm.monsters().get(0);
        assertTrue(MonsterManager.runPercent(m.type()) > 0);
        Probe d = new Probe(new Position(10, 14, 0));
        MonsterChaseTest.run(mm, d, 0, 2_000);
        m.damage(m.type().maxHealth() - 3);
        int before = m.position().distanceTo(d.pos);
        MonsterChaseTest.run(mm, d, 2_000, 6_000);
        assertTrue(m.state() == MonsterState.FLEE || m.cornered(), "runs at low health: " + m.state());
        assertTrue(m.position().distanceTo(d.pos) >= before, "keeps away");
        // szaman leczy sie czarem w ucieczce
        MonsterManager mm2 = MonsterChaseTest.single(w, "goblin_shaman", new Position(10, 10, 0));
        Monster sh = mm2.monsters().get(0);
        Probe d2 = new Probe(new Position(10, 14, 0));
        MonsterChaseTest.run(mm2, d2, 0, 2_000);
        sh.damage(sh.type().maxHealth() * 9 / 10);
        int hp = sh.health();
        MonsterChaseTest.run(mm2, d2, 2_000, 60_000);
        assertTrue(sh.health() > hp, "self heal: " + hp + " -> " + sh.health());
        assertTrue(d2.heals > 0);
    }

    @Test
    void hasteSpeedsUpAMonsterAndWearsOff() {
        WorldMap w = MonsterChaseTest.field();
        MonsterManager mm = MonsterChaseTest.single(w, "orc_rider", new Position(10, 6, 0));
        Monster m = mm.monsters().get(0);
        Probe d = new Probe(new Position(10, 14, 0));
        boolean hasted = false;
        for (long t = 0; t < 200_000 && !hasted; t += 50) {
            mm.update(t, d, d);
            hasted = m.hasted(t);
            if (hasted) assertTrue(m.stepScalePercent(t) < 100);
        }
        assertTrue(hasted, "orc rider hastes itself");
        assertEquals(100, m.stepScalePercent(Long.MAX_VALUE / 2));
    }

    @Test
    void spearThrowerAndCasterShowProjectilesWithNamedMissiles() {
        PlayerCharacter pc = new pl.fawizja.pixel.character.CharacterFactory(pl.fawizja.pixel.config.StartingConfig.defaults(), TestData.ITEMS)
                .create("Style Tester", Vocation.KNIGHT, new Position(39, 6, 0), 300);
        pc.setPosition(new Position(39, 6, 0));
        MonsterManager mm = new MonsterManager(TestData.varden(), REG, List.of(
                new SpawnPoint("orc_spearman", new Position(43, 6, 0), 0, 1, 60),
                new SpawnPoint("orc_shaman", new Position(42, 6, 0), 0, 1, 60)), new Random(3));
        mm.populate(null);
        Monster spear = null, shaman = null;
        for (Monster m : mm.monsters()) {
            if (m.type().key().equals("orc_spearman")) spear = m; else shaman = m;
        }
        CombatSystem cs = new CombatSystem(pc, TestData.varden(), TestData.ITEMS, mm, new GroundItems(), new EventLog(50),
                new Random(5), 8);
        pc.heal(pc.maxHealth());
        spear.lookAt(pc.position());
        cs.monsterAttacks(spear, 1000);
        CombatEvent shot = null;
        for (CombatEvent e : cs.latestEvents(10)) if (e.kind() == CombatEvent.Kind.PROJECTILE) shot = e;
        assertNotNull(shot, "spear flies");
        assertEquals("spear", shot.missileName());
        assertEquals("PHYSICAL", shot.effectType());
        assertEquals(spear.position(), shot.from());
        boolean resolved = false;
        for (CombatEvent e : cs.latestEvents(10)) resolved |= e.kind() == CombatEvent.Kind.HIT_PLAYER || e.kind() == CombatEvent.Kind.BLOCKED;
        assertTrue(resolved, "the shot is resolved on the player");
        // czar w jeden cel: pocisk ognia
        assertTrue(cs.castMonsterSpell(shaman, "fire_strike", 2000));
        CombatEvent bolt = null;
        for (CombatEvent e : cs.latestEvents(10)) if (e.kind() == CombatEvent.Kind.PROJECTILE && e.effectType().equals("FIRE")) bolt = e;
        assertNotNull(bolt, "single-target spell is a visible missile");
        assertEquals("fire", bolt.missileName());
        assertNotNull(cs.takeLastCast());
        assertNull(cs.takeLastCast());
    }

    @Test
    void areaCasterWaveHitsEveryTilePlannedNotOnlyTheTarget() {
        MonsterSpell wave = MonsterSpells.defaults().of(REG.get("venom_hydra")).stream().filter(s -> s.key().equals("poison_wave")).findFirst().orElseThrow();
        Position me = new Position(10, 10, 0);
        MonsterSpells.Cast c = MonsterSpells.plan(wave, me, new Position(13, 10, 0), pl.fawizja.pixel.world.Direction.EAST, null);
        assertNotNull(c);
        assertTrue(c.hits(new Position(13, 10, 0)) && c.hits(new Position(12, 10, 0)), "wave covers several tiles");
        assertTrue(c.tiles().size() > 3);
    }
}
