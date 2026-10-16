package pl.fawizja.pixel.settings;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.world.Position;

class MapMarkersTest {
    @Test
    void markersAreNamedMovedRemovedAndSaved() {
        MapMarkers m = new MapMarkers();
        assertTrue(m.put("Kopalnia", new Position(60, 70, 0)));
        assertTrue(m.put("  smocze;legowisko|z,przecinkiem  ", new Position(10, 20, -2)));
        assertEquals("smocze legowisko z przecinkiem".substring(0, MapMarkers.NAME_MAX).trim(), m.all().get(1).name());
        assertTrue(m.put("KOPALNIA", new Position(61, 71, 0)), "the same name moves the marker");
        assertEquals(2, m.all().size());
        assertEquals(new Position(61, 71, 0), m.all().get(0).at());
        assertFalse(m.put("   ", new Position(1, 1, 0)));

        MapMarkers back = MapMarkers.decode(m.encode() + ";zepsuty;bez|1,2;x|a,b,c");
        assertEquals(m.all(), back.all());
        assertTrue(back.remove("kopalnia"));
        assertFalse(back.remove("kopalnia"));
        assertEquals(1, back.all().size());

        for (int i = 0; i < MapMarkers.MAX + 5; i++) m.put("m" + i, new Position(i, i, 0));
        assertEquals(MapMarkers.MAX, m.all().size(), "at most " + MapMarkers.MAX);
        assertEquals(0, MapMarkers.decode(null).all().size());
    }
}
