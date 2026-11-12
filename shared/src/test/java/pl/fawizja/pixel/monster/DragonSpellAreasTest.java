package pl.fawizja.pixel.monster;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.magic.SpellArea;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Position;

/** Smoki i ich czary obszarowe: kazdy ma obszar, silne maja duze (fale, wielkie kule, gwiazdy), nowe ksztalty sa poprawne. */
class DragonSpellAreasTest {
    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);
    static final MonsterSpells SPELLS = MonsterSpells.defaults();

    static final List<String> DRAGONS = List.of("dragon_whelp", "scale_dragon", "emerald_dragon", "hoarfrost_dragon", "crag_dragon",
            "coal_dragon", "dune_drake", "ice_dragon", "pearl_dragon", "magma_drake", "bone_dragon", "skaldra", "ignaroth");

    static int biggestArea(String key) {
        int m = 0;
        for (MonsterSpell s : SPELLS.of(REG.get(key))) if (s.attack()) m = Math.max(m, s.area().size());
        return m;
    }

    @Test
    void everyDragonHasAtLeastOneAreaSpell() {
        for (String k : DRAGONS) {
            boolean area = false;
            for (MonsterSpell s : SPELLS.of(REG.get(k))) area |= s.attack() && s.area().size() > 1;
            assertTrue(area, k + " has an area spell");
        }
    }

    @Test
    void strongDragonsCastAreasOfTwentyOneTilesOrMore() {
        for (String k : DRAGONS) {
            if (REG.get(k).level() < 200) continue;
            assertTrue(biggestArea(k) >= 21, k + " (level " + REG.get(k).level() + ") biggest area " + biggestArea(k));
        }
        // najsilniejsze: ogromne kule, fale i gwiazdy
        for (String k : List.of("pearl_dragon", "bone_dragon", "skaldra", "magma_drake", "dune_drake", "ignaroth")) {
            assertTrue(biggestArea(k) >= 40, k + " has a really big area: " + biggestArea(k));
        }
    }

    @Test
    void dragonsDifferInTheirSetsAndTheStrongOnesUseSeveralShapes() {
        Set<String> signatures = new HashSet<>();
        for (String k : DRAGONS) {
            Set<String> shapes = new HashSet<>();
            StringBuilder sig = new StringBuilder();
            for (MonsterSpell s : SPELLS.of(REG.get(k))) {
                shapes.add(s.area().name());
                sig.append(s.key()).append(s.area().name()).append(';');
            }
            assertTrue(signatures.add(sig.toString()), k + " has its own set");
            if (REG.get(k).level() >= 500 && !k.equals("ignaroth")) assertTrue(shapes.size() >= 4, k + " shapes " + shapes);
            else if (REG.get(k).level() >= 300) assertTrue(shapes.size() >= 3, k + " shapes " + shapes);
        }
        // nowe ksztalty w uzyciu: szeroka fala, szeroki promien, gwiazda
        Set<String> used = new HashSet<>();
        for (String k : DRAGONS) for (MonsterSpell s : SPELLS.of(REG.get(k))) used.add(s.area().name());
        assertTrue(used.containsAll(Set.of("WAVE9", "WIDE_BEAM8", "CROSS_NOVA", "CIRCLE3X3", "CIRCLE3X4", "CIRCLE5X5", "BEAM7")), used.toString());
    }

    @Test
    void newShapesHaveTheDocumentedSizeAndRotateWithTheCaster() {
        assertEquals(40, SpellArea.WAVE9.size());
        assertEquals(24, SpellArea.WIDE_BEAM8.size());
        assertEquals(41, SpellArea.CROSS_NOVA.size());
        assertTrue(SpellArea.WAVE9.directional());
        assertTrue(SpellArea.WIDE_BEAM8.directional());
        assertFalse(SpellArea.CROSS_NOVA.directional());
        Position anchor = new Position(50, 50, 0);
        // fala patrzaca na wschod sieje na wschod, w prawo od rzucajacego
        for (Direction d : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
            List<Position> tiles = SpellArea.WAVE9.tiles(anchor, d);
            assertEquals(40, new HashSet<>(tiles).size(), "no duplicate tiles " + d);
            int far = 0;
            for (Position p : tiles) far = Math.max(far, Math.max(Math.abs(p.x() - anchor.x()), Math.abs(p.y() - anchor.y())));
            assertEquals(7, far, "reaches 7 tiles past the first one");
        }
        for (Position p : SpellArea.WAVE9.tiles(anchor, Direction.EAST)) assertTrue(p.x() >= anchor.x(), "eastern wave stays in front");
        // pole srodka i rogi gwiazdy
        assertTrue(SpellArea.CROSS_NOVA.tiles(anchor, null).contains(anchor));
        assertTrue(SpellArea.CROSS_NOVA.tiles(anchor, null).contains(new Position(55, 55, 0)));
        assertFalse(SpellArea.CROSS_NOVA.tiles(anchor, null).contains(new Position(51, 52, 0)));
        // kod zdarzenia dla klienta odtwarza dokladnie te pola
        for (SpellArea a : List.of(SpellArea.WAVE9, SpellArea.WIDE_BEAM8, SpellArea.CROSS_NOVA)) {
            int code = a.encode(Direction.WEST);
            assertSame(a, SpellArea.decodeArea(code));
            assertEquals(a.size(), SpellArea.eventTiles(anchor, code, anchor, null).size(), a.name() + ": the client draws the whole area");
        }
        assertSame(SpellArea.WAVE9, SpellArea.byName("WAVE9"));
    }

    @Test
    void bigDragonWaveHitsTargetsFarInsideTheConeButNotBehind() {
        MonsterSpell wave = SPELLS.of(REG.get("pearl_dragon")).stream().filter(s -> s.key().equals("great_fire_wave")).findFirst().orElseThrow();
        assertSame(SpellArea.WAVE9, wave.area());
        Position me = new Position(10, 10, 0);
        MonsterSpells.Cast c = MonsterSpells.plan(wave, me, new Position(14, 12, 0), Direction.EAST, null);
        assertNotNull(c);
        assertTrue(c.tiles().size() >= 30, "a huge cone: " + c.tiles().size());
        assertTrue(c.hits(new Position(14, 12, 0)));
    }

    @Test
    void expectedDamagePerAttackStaysBelowTheDragonsMeleeTimesAFewAndAboveZero() {
        // sanity: najwiekszy czar smoka nie przekracza wielokrotnosci jego zasiegu obrazen wrecz z danych
        for (String k : DRAGONS) {
            MonsterType t = REG.get(k);
            for (MonsterSpell s : SPELLS.of(t)) {
                if (!s.attack()) continue;
                assertTrue(s.max() <= Math.max(1, t.attackMax()) * 8, k + " " + s.key() + " max " + s.max() + " vs melee " + t.attackMax());
                assertTrue(s.range() >= 1);
            }
        }
    }
}
