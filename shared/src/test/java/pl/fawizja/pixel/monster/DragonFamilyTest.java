package pl.fawizja.pixel.monster;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.combat.CombatEvent;
import pl.fawizja.pixel.combat.CombatSystem;
import pl.fawizja.pixel.combat.ConditionType;
import pl.fawizja.pixel.combat.DamageType;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.magic.SpellArea;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.GroundItems;
import pl.fawizja.pixel.world.Position;

/** Rod pieciu smokow: kolejnosc sily, czary (obszary i stany), rzut czaru w gracza, rzadki bialy smok. */
class DragonFamilyTest {
    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);
    static final String[] ORDER = {"emerald_dragon", "hoarfrost_dragon", "coal_dragon", "dune_drake", "pearl_dragon"};

    @Test
    void fiveColoursGetStrongerInOrderGreenIceBlackGoldWhite() {
        for (int i = 0; i + 1 < ORDER.length; i++) {
            MonsterType a = REG.get(ORDER[i]), b = REG.get(ORDER[i + 1]);
            assertTrue(a.level() < b.level(), a.key() + " < " + b.key() + " level");
            assertTrue(a.maxHealth() < b.maxHealth(), "hp");
            assertTrue(a.experience() < b.experience(), "exp");
            assertTrue(a.attackMax() < b.attackMax(), "melee");
            assertTrue(a.armor() <= b.armor(), "armor");
        }
        for (String k : ORDER) {
            MonsterType t = REG.get(k);
            assertEquals("Dragon", t.race(), k);
            assertEquals(MonsterBehavior.MELEE, t.behavior(), k + " bites in melee and walks up");
            assertEquals(DamageType.PHYSICAL, t.damageType(), k + " melee is physical");
            assertTrue(t.range() >= MonsterSpells.defaults().maxRange(t), k + " range covers its spells");
        }
        assertFalse(REG.get("pearl_dragon").boss());
        assertTrue(REG.get("pearl_dragon").maxHealth() >= 2 * REG.get("dune_drake").maxHealth(), "white is a boss-tier top dragon");
        // kolory: pliki grafiki od zielonego do bialego i nazwy w trzech jezykach
        assertEquals("Złocisty smok", REG.get("dune_drake").polishName());
    }

    @Test
    void everyDragonHasItsOwnSpellsWithShapedAreasAndConditions() {
        MonsterSpells ms = MonsterSpells.defaults();
        // zielony: ognista fala + mala kula; bez lodu
        assertSpells(ms, "emerald_dragon", Set3.of("fire_wave", "fireball", "venom_breath"), DamageType.FIRE);
        MonsterSpell wave = spell(ms, "emerald_dragon", "fire_wave");
        assertSame(SpellArea.WAVE4, wave.area());
        assertEquals(MonsterSpell.Mode.WAVE, wave.mode());
        assertEquals(ConditionType.BURNING, wave.condition().type());
        assertSame(SpellArea.CIRCLE2X2, spell(ms, "emerald_dragon", "fireball").area());
        // lodowy: fala, oddech (promien), spowalniajace spojrzenie; stan zamrozenia
        assertSpells(ms, "hoarfrost_dragon", Set3.of("ice_wave", "ice_breath", "chilling_gaze"), DamageType.ICE);
        assertEquals(ConditionType.FREEZING, spell(ms, "hoarfrost_dragon", "ice_wave").condition().type());
        assertSame(SpellArea.BEAM6, spell(ms, "hoarfrost_dragon", "ice_breath").area());
        assertTrue(spell(ms, "hoarfrost_dragon", "chilling_gaze").slowPercent() >= 30, "slows");
        // czarny: wielka fala ognia i wielka kula ognia (obszar GFB z Canary)
        assertSpells(ms, "coal_dragon", Set3.of("great_fire_wave", "great_fireball", "ash_breath"), DamageType.FIRE);
        assertSame(SpellArea.WAVE7, spell(ms, "coal_dragon", "great_fire_wave").area());
        assertSame(SpellArea.CIRCLE3X3, spell(ms, "coal_dragon", "great_fireball").area());
        // zloty: ogien i piorun
        assertEquals(DamageType.ENERGY, spell(ms, "dune_drake", "energy_wave").type());
        assertEquals(ConditionType.ELECTRIFIED, spell(ms, "dune_drake", "energy_wave").condition().type());
        assertEquals(ConditionType.BURNING, spell(ms, "dune_drake", "great_fire_wave").condition().type());
        // bialy: ogien, lod i piorun, wybuch wokol siebie
        List<DamageType> types = new ArrayList<>();
        for (MonsterSpell s : ms.of(REG.get("pearl_dragon"))) types.add(s.type());
        assertTrue(types.containsAll(List.of(DamageType.FIRE, DamageType.ICE, DamageType.ENERGY)));
        assertEquals(MonsterSpell.Mode.SELF, spell(ms, "pearl_dragon", "ice_nova").mode());
        // im silniejszy smok, tym mocniejszy najmocniejszy czar
        int prev = 0;
        for (String k : ORDER) {
            int max = 0;
            for (MonsterSpell s : ms.of(REG.get(k))) max = Math.max(max, s.max());
            assertTrue(max > prev, k + " spells stronger: " + max + " > " + prev);
            prev = max;
            for (MonsterSpell s : ms.of(REG.get(k))) assertTrue(s.range() <= REG.get(k).range(), k + " " + s.key());
        }
    }

    private static void assertSpells(MonsterSpells ms, String key, java.util.Set<String> names, DamageType main) {
        java.util.Set<String> have = new java.util.HashSet<>();
        for (MonsterSpell s : ms.of(REG.get(key))) have.add(s.key());
        assertEquals(names, have, key);
        assertTrue(ms.of(REG.get(key)).stream().anyMatch(s -> s.type() == main), key);
    }

    private static MonsterSpell spell(MonsterSpells ms, String monster, String key) {
        return ms.of(REG.get(monster)).stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow();
    }

    /** Skrot na zbior nazw. */
    private static final class Set3 {
        static java.util.Set<String> of(String... a) { return new java.util.HashSet<>(List.of(a)); }
    }

    @Test
    void waveHitsOnlyTheShapeInFrontAndBallCoversTheTargetNeighbourhood() {
        MonsterSpell wave = spell(MonsterSpells.defaults(), "coal_dragon", "great_fire_wave");
        Position me = new Position(10, 10, 0);
        // fala patrzy na wschod: cel 3 pola przed smokiem w obszarze, za plecami i daleko z boku - nie
        MonsterSpells.Cast front = MonsterSpells.plan(wave, me, new Position(13, 10, 0), Direction.EAST, null);
        assertNotNull(front);
        assertEquals(Direction.EAST, front.dir());
        assertEquals(new Position(11, 10, 0), front.anchor());
        assertTrue(front.hits(new Position(13, 10, 0)));
        assertTrue(front.tiles().size() >= 15, "great fire wave is a wide cone: " + front.tiles().size());
        assertNull(MonsterSpells.plan(wave, me, new Position(10, 20, 0), Direction.EAST, null), "out of reach: no cast");
        assertNull(MonsterSpells.plan(wave, me, new Position(18, 10, 0), Direction.EAST, null), "beyond the wave");
        // kula (GFB): obszar 3x3 wokol celu, nie wokol smoka
        MonsterSpell gfb = spell(MonsterSpells.defaults(), "coal_dragon", "great_fireball");
        MonsterSpells.Cast ball = MonsterSpells.plan(gfb, me, new Position(14, 12, 0), Direction.EAST, null);
        assertEquals(new Position(14, 12, 0), ball.anchor());
        assertTrue(ball.hits(new Position(15, 13, 0)) && ball.hits(new Position(14, 12, 0)));
        assertFalse(ball.hits(me));
        // wybuch wokol bialego smoka obejmuje tylko pobliskich
        MonsterSpell nova = spell(MonsterSpells.defaults(), "pearl_dragon", "ice_nova");
        assertNotNull(MonsterSpells.plan(nova, me, new Position(11, 11, 0), Direction.EAST, null));
        assertNull(MonsterSpells.plan(nova, me, new Position(17, 10, 0), Direction.EAST, null));
    }

    /** Rycerz poziomu 400: wytrzymuje kilka czarow smoka (obrazenia widac, a postac nie ginie i nie wraca do swiatyni). */
    private static PlayerCharacter strongKnight() {
        return new pl.fawizja.pixel.character.CharacterFactory(pl.fawizja.pixel.config.StartingConfig.defaults(), TestData.ITEMS)
                .create("Dragon Tester", Vocation.KNIGHT, new Position(39, 6, 0), 400);
    }

    private static CombatSystem combat(PlayerCharacter pc, MonsterManager mm) {
        return new CombatSystem(pc, TestData.varden(), TestData.ITEMS, mm, new GroundItems(), new EventLog(50), new Random(5), 8);
    }

    @Test
    void aDragonSpellHitsThePlayerWithFxAndAppliesBurningOrSlow() {
        PlayerCharacter pc = strongKnight();
        pc.setPosition(new Position(39, 6, 0));
        MonsterManager mm = new MonsterManager(TestData.varden(), REG,
                List.of(new SpawnPoint("coal_dragon", new Position(42, 6, 0), 0, 1, 60)), new Random(3));
        mm.populate(null);
        Monster dragon = mm.monsters().get(0);
        assertEquals(new Position(42, 6, 0), dragon.position());
        CombatSystem cs = combat(pc, mm);
        pc.heal(pc.maxHealth());
        int hp = pc.health();
        assertTrue(cs.castMonsterSpell(dragon, "great_fire_wave", 1000), "player stands in the wave");
        assertTrue(pc.health() < hp, "wave hurts");
        CombatEvent shaped = null;
        for (CombatEvent e : cs.latestEvents(10)) if (e.isShapedArea()) shaped = e;
        assertNotNull(shaped, "shaped area effect for the client (offline and online)");
        assertEquals("FIRE", shaped.effect());
        assertSame(SpellArea.WAVE7, SpellArea.decodeArea(shaped.amount()));
        assertEquals(Direction.WEST, SpellArea.decodeDirection(shaped.amount()), "dragon turned towards the player");
        // GFB: pocisk lata, wybuch na graczu
        assertTrue(cs.castMonsterSpell(dragon, "great_fireball", 2000));
        boolean projectile = false;
        for (CombatEvent e : cs.latestEvents(10)) projectile |= e.kind() == CombatEvent.Kind.PROJECTILE;
        assertTrue(projectile, "fireball flies");
        // brak takiego czaru / cel poza obszarem
        assertFalse(cs.castMonsterSpell(dragon, "ice_wave", 3000));
        pc.setPosition(new Position(25, 6, 0));
        assertFalse(cs.castMonsterSpell(dragon, "great_fire_wave", 4000), "out of area");
    }

    @Test
    void burningFreezingAndSlowComeFromTheSpellNotFromMelee() {
        PlayerCharacter pc = strongKnight();
        pc.setPosition(new Position(39, 6, 0));
        MonsterManager mm = new MonsterManager(TestData.varden(), REG, List.of(
                new SpawnPoint("emerald_dragon", new Position(42, 6, 0), 0, 1, 60),
                new SpawnPoint("hoarfrost_dragon", new Position(41, 6, 0), 0, 1, 60)), new Random(3));
        mm.populate(null);
        Monster green = mm.monsters().get(0), ice = mm.monsters().get(1);
        // zawsze 0: szansa na stan i na kazdy czar wypada
        CombatSystem cs = new CombatSystem(pc, TestData.varden(), TestData.ITEMS, mm, new GroundItems(), new EventLog(50),
                new Random() { @Override public int nextInt(int b) { return 0; } }, 8);
        pc.heal(pc.maxHealth());
        assertTrue(cs.castMonsterSpell(green, "fire_wave", 1000));
        assertTrue(pc.conditions().has(ConditionType.BURNING), "fire wave burns");
        int speed = pc.speed();
        assertTrue(cs.castMonsterSpell(ice, "chilling_gaze", 2000));
        assertTrue(pc.slowed());
        assertTrue(pc.speed() < speed, "slowed: " + pc.speed() + " < " + speed);
        pc.tickRegeneration(6000, false);
        assertFalse(pc.slowed(), "slow wears off");
        pc.heal(pc.maxHealth());
        assertTrue(cs.castMonsterSpell(ice, "ice_wave", 9000));
        assertTrue(pc.conditions().has(ConditionType.FREEZING), "ice wave freezes");
    }

    @Test
    void whiteDragonRareSpawnChanceCooldownAndSingleInstanceWithSeededRng() {
        Position grounds = new Position(42, 6, 0);
        Position hunter = new Position(34, 6, 0);
        long hits = 0;
        int trials = 4000;
        for (int i = 0; i < trials; i++) {
            MonsterManager mm = rareWorld(grounds, new Random(i), 15);
            mm.setRareRandom(new Random(1000 + i));
            Monster d = mm.monsters().get(0);
            mm.kill(d, 0);
            mm.update(70_000, new Tgt(hunter), IGNORE);
            for (Monster m : mm.monsters()) if (m.type().key().equals("pearl_dragon")) hits++;
        }
        double rate = hits / (double) trials;
        assertTrue(rate > 0.008 && rate < 0.025, "about 1.5% of dragon respawns become white: " + rate);
    }

    private static final MonsterManager.AttackHandler IGNORE = new MonsterManager.AttackHandler() {
        public void monsterAttacks(Monster m, long now) {}
        public void monsterHeals(Monster h, Monster t, int amount, long now) {}
    };

    private record Tgt(Position p) implements MonsterManager.Target {
        public Position position() { return p; }
        public boolean attackable() { return true; }
    }

    private static MonsterManager rareWorld(Position at, Random rng) { return rareWorld(at, rng, 1000); }

    private static MonsterManager rareWorld(Position at, Random rng, int permille) {
        MonsterManager mm = new MonsterManager(TestData.varden(), REG,
                List.of(new SpawnPoint("coal_dragon", at, 1, 1, 60)), rng);
        mm.setRareSpawns(new RareSpawns("pearl_dragon", permille, 3_600_000L, 32, 100)); // 1000 = zawsze: sprawdza przerwe i limit
        mm.populate(null);
        return mm;
    }

    @Test
    void rareDragonNeedsAPlayerOnTheGroundHasACooldownAndOnlyOneLives() {
        Position grounds = new Position(42, 6, 0);
        MonsterManager mm = rareWorld(grounds, new Random(1));
        List<Monster> told = new ArrayList<>();
        mm.setRareListener((m, t) -> told.add(m));
        // gracz daleko od lowiska: zwykly smok
        mm.kill(mm.monsters().get(0), 0);
        mm.update(70_000, new Tgt(new Position(200, 200, 0)), IGNORE);
        assertEquals("coal_dragon", mm.monsters().get(0).type().key());
        assertTrue(told.isEmpty());
        // gracz na lowisku (szansa 100%): bialy smok i komunikat
        mm.kill(mm.monsters().get(0), 100_000);
        mm.update(170_000, new Tgt(new Position(34, 6, 0)), IGNORE);
        Monster white = mm.monsters().get(0);
        assertEquals("pearl_dragon", white.type().key());
        assertEquals(1, told.size());
        assertSame(white, mm.rareAlive());
        assertEquals(170_000 + 3_600_000L, mm.rareCooldownUntil());
        // zabity: przerwa - nastepny odradzajacy sie smok jest zwykly, mimo 100% szansy
        mm.kill(white, 200_000);
        assertNull(mm.rareAlive());
        mm.update(270_000, new Tgt(new Position(34, 6, 0)), IGNORE);
        assertEquals("coal_dragon", mm.monsters().get(0).type().key(), "cooldown");
        // po przerwie znowu, ale nigdy dwa naraz
        MonsterManager two = new MonsterManager(TestData.varden(), REG, List.of(
                new SpawnPoint("coal_dragon", grounds, 1, 1, 60), new SpawnPoint("emerald_dragon", grounds.translate(2, 0), 1, 1, 60)),
                new Random(2));
        two.setRareSpawns(new RareSpawns("pearl_dragon", 1000, 0L, 32, 100)); // bez przerwy: tylko limit jednego
        two.populate(null);
        for (Monster m : new ArrayList<>(two.monsters())) two.kill(m, 0);
        two.update(70_000, new Tgt(new Position(34, 6, 0)), IGNORE);
        long whites = two.monsters().stream().filter(m -> m.type().key().equals("pearl_dragon")).count();
        assertEquals(1, whites, "at most one white dragon alive");
        assertEquals(2, two.monsters().size());
    }

    @Test
    void hatchlingsAndNonDragonsDoNotTriggerTheRareSpawn() {
        RareSpawns r = RareSpawns.standard();
        assertTrue(r.isDragonGround(REG.get("emerald_dragon")));
        assertTrue(r.isDragonGround(REG.get("dune_drake")));
        assertFalse(r.isDragonGround(REG.get("dragon_whelp")));
        assertFalse(r.isDragonGround(REG.get("wolf")));
        assertFalse(r.isDragonGround(REG.get("pearl_dragon")));
        assertEquals(15, r.chancePermille(), "1.5% per respawn");
    }
}
