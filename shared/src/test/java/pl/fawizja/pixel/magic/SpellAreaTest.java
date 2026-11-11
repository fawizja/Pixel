package pl.fawizja.pixel.magic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.combat.CombatEvent;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/** Obszary czarow i run jak w Canary (register_spells.lua): okregi, krzyz, fale, promienie, sciany. */
class SpellAreaTest {

    static final Position O = new Position(50, 50, 0);

    /** Pola obszaru jako przesuniecia "dx,dy" od srodka. */
    static Set<String> offs(SpellArea a, Direction d) {
        Set<String> out = new HashSet<>();
        for (int[] c : a.offsets(d)) out.add(c[0] + "," + c[1]);
        return out;
    }

    static Set<String> set(String... s) { return new HashSet<>(List.of(s)); }

    @Test
    void circlesAreRoundNotSquare() {
        // wielka kula ognia: 37 pol (3+5+7+7+7+5+3), bez rogow kwadratu 7x7
        Set<String> gfb = offs(SpellArea.CIRCLE3X3, null);
        assertEquals(37, gfb.size());
        for (String in : List.of("0,0", "3,0", "-3,0", "0,3", "0,-3", "2,2", "-2,-2", "3,1", "1,3", "-3,-1"))
            assertTrue(gfb.contains(in), in);
        for (String out : List.of("3,3", "3,2", "2,3", "-3,-3", "-3,2", "4,0")) assertFalse(gfb.contains(out), out);
        assertEquals(3, SpellArea.CIRCLE3X3.extent());
        // eksplozja: krzyz
        assertEquals(set("0,0", "1,0", "-1,0", "0,1", "0,-1"), offs(SpellArea.CIRCLE1X1, null));
        // bomby, exori: pelny kwadrat 3x3 (tak tez jest w Tibii)
        assertEquals(9, offs(SpellArea.SQUARE1X1, null).size());
        assertTrue(offs(SpellArea.SQUARE1X1, null).contains("1,1"));
        // Boski granat
        Set<String> c2 = offs(SpellArea.CIRCLE2X2, null);
        assertEquals(21, c2.size());
        assertFalse(c2.contains("2,2"));
        // czary ostateczne: exevo gran mas flam/frigo 61 pol, exevo gran mas vis/tera 85 pol (romb z zaokragleniem)
        Set<String> c5 = offs(SpellArea.CIRCLE5X5, null);
        assertEquals(61, c5.size());
        assertTrue(c5.contains("5,0") && c5.contains("3,2") && c5.contains("4,1"));
        assertFalse(c5.contains("3,3") || c5.contains("5,1") || c5.contains("4,2"));
        Set<String> c6 = offs(SpellArea.CIRCLE6X6, null);
        assertEquals(85, c6.size());
        assertTrue(c6.contains("6,0") && c6.contains("3,3") && c6.contains("4,2"));
        assertFalse(c6.contains("4,4") || c6.contains("6,1"));
        // wybuch ziemi/lodu: pierscien bez pol tuz przy druidzie
        Set<String> ring = offs(SpellArea.RING1_BURST3, null);
        assertFalse(ring.contains("0,0") || ring.contains("1,1") || ring.contains("1,0"));
        assertTrue(ring.contains("2,0") && ring.contains("4,0") && ring.contains("2,2"));
        // czary bez wpisu: okrag Canary wg promienia
        assertEquals(SpellArea.SQUARE1X1, SpellArea.circle(1));
        assertEquals(SpellArea.CIRCLE3X3, SpellArea.circle(3));
        assertFalse(offs(SpellArea.circle(3), null).contains("3,3"));
    }

    @Test
    void wavesAreConesThatTurnWithTheCaster() {
        // exevo flam hur na polnoc: pole przed postacia, 3, 3, 5 pol szerokosci
        assertEquals(set("0,0", "-1,-1", "0,-1", "1,-1", "-1,-2", "0,-2", "1,-2", "-2,-3", "-1,-3", "0,-3", "1,-3", "2,-3"),
                offs(SpellArea.WAVE4, Direction.NORTH));
        // ta sama fala na wschod i poludnie to obrot
        Set<String> east = offs(SpellArea.WAVE4, Direction.EAST);
        assertTrue(east.contains("3,2") && east.contains("3,-2") && east.contains("1,0") && east.contains("0,0"));
        assertFalse(east.contains("-1,0") || east.contains("0,1"));
        Set<String> south = offs(SpellArea.WAVE4, Direction.SOUTH);
        assertTrue(south.contains("2,3") && south.contains("0,1"));
        Set<String> west = offs(SpellArea.WAVE4, Direction.WEST);
        assertTrue(west.contains("-3,-2") && west.contains("-1,1"));
        for (Direction d : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
            assertEquals(12, offs(SpellArea.WAVE4, d).size(), "wave4 " + d);
            assertEquals(17, offs(SpellArea.WAVE7, d).size(), "wave7 " + d);
            assertEquals(11, offs(SpellArea.SQUAREWAVE5, d).size(), "squarewave5 " + d);
            assertEquals(7, offs(SpellArea.SHORTWAVE3, d).size(), "shortwave3 " + d);
            assertEquals(3, offs(SpellArea.WAVE6, d).size(), "front sweep " + d);
        }
        // exevo vis hur / exevo tera hur: waska szyjka i kwadrat 3x3 na koncu, 5 pol dlugosci
        assertEquals(set("0,0", "0,-1", "-1,-2", "0,-2", "1,-2", "-1,-3", "0,-3", "1,-3", "-1,-4", "0,-4", "1,-4"),
                offs(SpellArea.SQUAREWAVE5, Direction.NORTH));
        // exori min: trzy pola przed rycerzem
        assertEquals(set("0,0", "0,-1", "0,1"), offs(SpellArea.WAVE6, Direction.EAST));
    }

    @Test
    void beamsAreStraightLinesOfTheRightLength() {
        assertEquals(set("0,0", "1,0", "2,0", "3,0", "4,0"), offs(SpellArea.BEAM5, Direction.EAST));
        assertEquals(set("0,0", "0,1", "0,2", "0,3", "0,4", "0,5", "0,6", "0,7"), offs(SpellArea.BEAM8, Direction.SOUTH));
        assertEquals(SpellArea.BEAM7, SpellArea.beam(7));
        assertEquals(8, SpellArea.beam(8).size());
    }

    @Test
    void wallsLieAcrossTheThrowAndDiagonally() {
        assertEquals(set("-2,0", "-1,0", "0,0", "1,0", "2,0"), offs(SpellArea.WALLFIELD, Direction.NORTH));
        assertEquals(set("0,-2", "0,-1", "0,0", "0,1", "0,2"), offs(SpellArea.WALLFIELD, Direction.EAST));
        assertEquals(7, offs(SpellArea.WALLFIELD_ENERGY, Direction.SOUTH).size(), "energy wall: 7 tiles");
        // po skosie (polnocny zachod): ukosna sciana z Canary AREADIAGONAL_WALLFIELD
        Set<String> nw = offs(SpellArea.WALLFIELD, Direction.NORTH_WEST);
        assertEquals(set("2,-2", "1,-1", "2,-1", "-1,0", "0,0", "1,0", "-2,1", "-1,1", "-2,2"), nw);
        Set<String> ne = offs(SpellArea.WALLFIELD, Direction.NORTH_EAST);
        assertTrue(ne.contains("-2,-2") && ne.contains("2,2"), "mirrored for north-east");
    }

    @Test
    void everyAreaSpellAndRuneHasACanaryArea() {
        for (SpellType s : MagicTest.SPELLS.all()) {
            boolean area = s.kind() == SpellKind.ATTACK_AREA || s.kind() == SpellKind.RUNE_AREA || s.kind() == SpellKind.ATTACK_WAVE
                    || s.kind() == SpellKind.ATTACK_BEAM || s.kind() == SpellKind.RUNE_FIELD || s.kind() == SpellKind.RUNE_WALL
                    || s.kind() == SpellKind.HEAL_PARTY || s.kind() == SpellKind.ATTACK_TARGET && s.radius() > 0;
            if (!area || SpellAreas.chain(s)) continue;
            assertTrue(SpellAreas.table().containsKey(s.key()), "spell_areas.csv has " + s.key());
        }
        for (String k : SpellAreas.table().keySet()) assertNotNull(MagicTest.SPELLS.find(k), "known spell " + k);
        assertEquals(SpellArea.CIRCLE3X3, SpellAreas.table().get("great_fireball_rune"));
        assertEquals(SpellArea.CIRCLE3X3, SpellAreas.table().get("avalanche_rune"));
        assertEquals(SpellArea.CIRCLE3X3, SpellAreas.table().get("thunderstorm_rune"));
        assertEquals(SpellArea.CIRCLE3X3, SpellAreas.table().get("stone_shower_rune"));
        assertEquals(SpellArea.CIRCLE1X1, SpellAreas.table().get("explosion_rune"));
        assertEquals(SpellArea.SQUARE1X1, SpellAreas.table().get("fire_bomb_rune"));
        assertEquals(SpellArea.WAVE4, SpellAreas.table().get("fire_wave"));
        assertEquals(SpellArea.SQUAREWAVE5, SpellAreas.table().get("terra_wave"));
        assertEquals(SpellArea.SQUAREWAVE5, SpellAreas.table().get("energy_wave"));
        assertEquals(SpellArea.SHORTWAVE3, SpellAreas.table().get("strong_ice_wave"));
        assertEquals(SpellArea.CIRCLE6X6, SpellAreas.table().get("rage_of_the_skies"));
        assertEquals(SpellArea.CIRCLE5X5, SpellAreas.table().get("hells_core"));
        assertEquals(SpellArea.CIRCLE3X3, SpellAreas.table().get("mass_healing"));
        assertEquals(SpellArea.SQUARE1X1, SpellAreas.table().get("fierce_berserk"));
        assertEquals(SpellArea.CIRCLE3X3, SpellAreas.table().get("groundshaker"));
        // Wielki promien smierci: 6/7/8 pol na stopniach 1/2/3 objawienia (Canary AREA_BEAM6/7/8)
        SpellType gdb = MagicTest.SPELLS.get("great_death_beam");
        assertEquals(SpellArea.BEAM6, SpellAreas.of(gdb, 0));
        assertEquals(SpellArea.BEAM8, SpellAreas.of(gdb, 2));
    }

    @Test
    void effectCodeCarriesShapeAndDirection() {
        for (int id = 0; SpellArea.byId(id) != null; id++) {
            SpellArea a = SpellArea.byId(id);
            for (Direction d : Direction.values()) {
                int code = a.encode(d);
                assertTrue(code < 0);
                assertSame(a, SpellArea.decodeArea(code));
                assertEquals(d, SpellArea.decodeDirection(code));
            }
        }
        assertNull(SpellArea.decodeArea(3), "positive = old square radius");
        List<Position> t = SpellArea.eventTiles(O, SpellArea.WAVE4.encode(Direction.EAST), O.translate(-1, 0), null);
        assertEquals(new HashSet<>(SpellArea.WAVE4.tiles(O, Direction.EAST)), new HashSet<>(t));
    }

    // ------------------------------------------------------------------ w grze

    /** Otwarte pole na mapie testowej: kwadrat (2r+1)^2 przechodni, poza strefa ochronna. */
    static Position openSpot(WorldMap w, int r) {
        for (int y = r + 1; y < w.height() - r - 1; y++) {
            for (int x = r + 1; x < w.width() - r - 1; x++) {
                boolean ok = true;
                for (int dy = -r; dy <= r && ok; dy++) {
                    for (int dx = -r; dx <= r && ok; dx++) {
                        Position p = new Position(x + dx, y + dy, 0);
                        if (!w.isWalkable(p) || w.isProtectionZone(p) || w.transitionAt(p) != null) ok = false;
                    }
                }
                if (ok) return new Position(x, y, 0);
            }
        }
        throw new AssertionError("no open spot of radius " + r);
    }

    /** Sesja z postacia na otwartym polu i dzikami na podanych przesunieciach. */
    static GameSession crowd(Vocation v, int level, Position[] me, int[]... at) {
        WorldMap w = TestData.world();
        Position c = openSpot(w, 7);
        me[0] = c;
        List<SpawnPoint> sp = new ArrayList<>();
        for (int[] d : at) sp.add(new SpawnPoint("boar", c.translate(d[0], d[1]), 0, 1, 600));
        MonsterManager mm = new MonsterManager(w, MagicTest.MONSTERS, sp, new Random(3));
        mm.populate(null);
        PlayerCharacter pc = TibiaSpellsTest.at(v, level);
        pc.skills().set(pl.fawizja.pixel.character.Skill.MAGIC, 60, 0);
        pc.restoreMana(pc.maxMana());
        GameSession s = new GameSession(TestData.ITEMS, w, pc, mm, new Random(5), 8, MagicTest.SPELLS, MagicTest.CONSUMABLES, MagicTest.RECIPES);
        s.teleport(c, 0);
        java.util.Map<Position, Monster> byPos = new java.util.HashMap<>();
        for (Monster m : s.monsters().monsters()) byPos.put(m.position(), m);
        PLACED.put(s, byPos);
        return s;
    }

    /** Dziki z chwili ustawienia (zabite znikaja z listy potworow). */
    static final java.util.Map<GameSession, java.util.Map<Position, Monster>> PLACED = new java.util.WeakHashMap<>();

    static Monster at(GameSession s, Position p) {
        Monster m = PLACED.get(s).get(p);
        if (m == null) throw new AssertionError("no boar at " + p);
        return m;
    }

    static boolean hurt(Monster m) { return m.isDead() || m.health() < m.type().maxHealth(); }

    @Test
    void greatFireballHitsACircleNotTheSquareCorners() {
        Position[] me = new Position[1];
        // GFB rzucona na pole 4 na wschod od postaci: dziki w okregu i w rogach kwadratu 7x7
        GameSession s = crowd(Vocation.SORCERER, 60, me, new int[] {4, 0}, new int[] {7, 0}, new int[] {6, 2}, new int[] {7, 3},
                new int[] {1, -3}, new int[] {4, -3});
        Position c = me[0].translate(4, 0);
        s.character().backpack().add(new ItemStack(MagicTest.id("great_fireball_rune"), 1));
        assertEquals(CastResult.OK, s.spells().useItemAt(itemSlot(s, "great_fireball_rune"), c, 1000));
        assertTrue(hurt(at(s, c)), "centre");
        assertTrue(hurt(at(s, c.translate(3, 0))), "3 tiles east");
        assertTrue(hurt(at(s, c.translate(2, 2))), "2,2 is inside the Tibia circle");
        assertFalse(hurt(at(s, c.translate(3, 3))), "3,3 corner of the square is outside");
        assertFalse(hurt(at(s, c.translate(-3, -3))), "-3,-3 corner is outside");
        assertTrue(hurt(at(s, c.translate(0, -3))), "0,-3 tip of the circle");
        CombatEvent fx = s.combat().latestEvents(64).stream().filter(CombatEvent::isShapedArea).findFirst().orElseThrow();
        assertEquals(SpellArea.CIRCLE3X3, SpellArea.decodeArea(fx.amount()), "the client draws the same circle");
        assertEquals(c, fx.position());
    }

    static int itemSlot(GameSession s, String key) {
        for (int i = 0; i < s.character().backpack().size(); i++) {
            ItemStack st = s.character().backpack().get(i);
            if (st != null && st.itemId() == MagicTest.id(key)) return i;
        }
        throw new AssertionError(key);
    }

    @Test
    void fireWaveHitsTheConeInFront() {
        Position[] me = new Position[1];
        GameSession s = crowd(Vocation.SORCERER, 40, me, new int[] {1, 0}, new int[] {4, 2}, new int[] {2, 1}, new int[] {1, 1},
                new int[] {-1, 0}, new int[] {5, 0});
        s.spells().setFacing(() -> Direction.EAST);
        assertEquals(CastResult.OK, s.castSpell("fire_wave", 1000));
        Position o = me[0];
        assertTrue(hurt(at(s, o.translate(1, 0))), "tile in front");
        assertTrue(hurt(at(s, o.translate(2, 1))), "second row is 3 wide");
        assertTrue(hurt(at(s, o.translate(4, 2))), "last row is 5 wide");
        assertFalse(hurt(at(s, o.translate(1, 1))), "first row is 1 wide");
        assertFalse(hurt(at(s, o.translate(-1, 0))), "behind");
        assertFalse(hurt(at(s, o.translate(5, 0))), "4 tiles long");
    }

    @Test
    void waveTurnsToTheTargetWhenItStandsInAnotherDirection() {
        Position[] me = new Position[1];
        GameSession s = crowd(Vocation.SORCERER, 40, me, new int[] {0, 2});
        s.spells().setFacing(() -> Direction.EAST);
        Monster boar = at(s, me[0].translate(0, 2));
        s.combat().setTarget(boar);
        assertEquals(CastResult.OK, s.castSpell("fire_wave", 1000));
        assertTrue(hurt(boar), "the wave goes south to the chosen target");
    }

    @Test
    void energyBeamIsALineOfFiveTiles() {
        Position[] me = new Position[1];
        GameSession s = crowd(Vocation.SORCERER, 40, me, new int[] {1, 0}, new int[] {5, 0}, new int[] {6, 0}, new int[] {3, 1});
        s.spells().setFacing(() -> Direction.EAST);
        assertEquals(CastResult.OK, s.castSpell("energy_beam", 1000));
        Position o = me[0];
        assertTrue(hurt(at(s, o.translate(1, 0))));
        assertTrue(hurt(at(s, o.translate(5, 0))), "5th tile");
        assertFalse(hurt(at(s, o.translate(6, 0))), "6th tile is out of reach");
        assertFalse(hurt(at(s, o.translate(3, 1))), "a beam is one tile wide");
    }

    @Test
    void hellsCoreIsABigCircleAroundTheSorcerer() {
        Position[] me = new Position[1];
        GameSession s = crowd(Vocation.SORCERER, 70, me, new int[] {5, 0}, new int[] {3, 2}, new int[] {3, 3}, new int[] {0, -5},
                new int[] {-5, 1});
        assertEquals(CastResult.OK, s.castSpell("hells_core", 1000));
        Position o = me[0];
        assertTrue(hurt(at(s, o.translate(5, 0))));
        assertTrue(hurt(at(s, o.translate(3, 2))));
        assertTrue(hurt(at(s, o.translate(0, -5))));
        assertFalse(hurt(at(s, o.translate(3, 3))), "outside the Canary circle 5x5");
        assertFalse(hurt(at(s, o.translate(-5, 1))));
    }

    @Test
    void explosionRuneIsACross() {
        Position[] me = new Position[1];
        GameSession s = crowd(Vocation.SORCERER, 40, me, new int[] {3, 0}, new int[] {4, 0}, new int[] {4, 1}, new int[] {3, 1});
        s.character().backpack().add(new ItemStack(MagicTest.id("explosion_rune"), 1));
        Position c = me[0].translate(3, 0);
        assertEquals(CastResult.OK, s.spells().useItemAt(itemSlot(s, "explosion_rune"), c, 1000));
        assertTrue(hurt(at(s, c)));
        assertTrue(hurt(at(s, c.translate(1, 0))));
        assertFalse(hurt(at(s, c.translate(1, 1))), "no diagonal in a cross");
    }

    @Test
    void fireWallLiesAcrossTheThrow() {
        Position[] me = new Position[1];
        GameSession s = crowd(Vocation.SORCERER, 45, me);
        s.character().backpack().add(new ItemStack(MagicTest.id("energy_wall_rune"), 1));
        Position c = me[0].translate(3, 0);
        assertEquals(CastResult.OK, s.spells().useItemAt(itemSlot(s, "energy_wall_rune"), c, 1000));
        for (int dy = -3; dy <= 3; dy++) assertNotNull(s.world().fields().at(c.translate(0, dy)), "energy wall tile " + dy);
        assertNull(s.world().fields().at(c.translate(1, 0)));
        assertNull(s.world().fields().at(c.translate(0, 4)));
    }
}
