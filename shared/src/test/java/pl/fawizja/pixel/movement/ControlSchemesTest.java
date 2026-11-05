package pl.fawizja.pixel.movement;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.world.Direction;

/** Warianty sterowania (2026-11-05): krzyzyk, plywajacy joystick 4 kierunki, przytrzymanie na mapie, czulosc. */
class ControlSchemesTest {

    private static float[] at(double deg, double len) {
        double r = Math.toRadians(deg);
        return new float[] {(float) (Math.cos(r) * len), (float) (Math.sin(r) * len)};
    }

    private static Direction push(FourWayStick f, double deg, double len, long t, boolean stepping) {
        float[] v = at(deg, len);
        return f.update(v[0], v[1], t, stepping);
    }

    // ------------------------------------------------------------------ krzyzyk

    @Test
    void dpadArmsCornersAndCenter() {
        float r = 54;
        assertNull(DpadInput.classify(0, 0, r), "srodek = postoj");
        assertNull(DpadInput.classify(5, 5, r));
        assertEquals(Direction.NORTH, DpadInput.classify(0, 40, r));
        assertEquals(Direction.SOUTH, DpadInput.classify(10, -40, r), "ramie ma 2/3 promienia szerokosci");
        assertEquals(Direction.EAST, DpadInput.classify(40, 17, r));
        assertEquals(Direction.WEST, DpadInput.classify(-40, -17, r));
        assertEquals(Direction.NORTH_EAST, DpadInput.classify(30, 30, r), "rog miedzy ramionami = skos");
        assertEquals(Direction.SOUTH_WEST, DpadInput.classify(-25, -25, r));
        assertEquals(Direction.NORTH_WEST, DpadInput.classify(-20, 30, r));
        assertEquals(Direction.SOUTH_EAST, DpadInput.classify(30, -20, r));
        // tuz przy granicy ramienia: jeszcze prosto
        assertEquals(Direction.EAST, DpadInput.classify(40, r / 3f - 0.5f, r));
        // palec zsuwa sie poza krzyzyk: kierunek dalej dziala
        assertEquals(Direction.EAST, DpadInput.classify(120, 0, r));
    }

    @Test
    void dpadTouchAreaIsLargerThanTheDrawnCircle() {
        float r = 54;
        assertTrue(DpadInput.hit(0, 0, r));
        assertTrue(DpadInput.hit(r * 1.3f, 0, r));
        assertFalse(DpadInput.hit(r * 1.5f, 0, r));
        assertTrue(2 * r * DpadInput.ARM >= 36, "ramie krzyzyka co najmniej 36 dp (najmniejsze pole dotyku)");
    }

    // ------------------------------------------------------------------ plywajacy joystick, 4 kierunki

    @Test
    void floatingStickDeadZoneDependsOnSensitivity() {
        FourWayStick f = new FourWayStick();
        assertNull(push(f, 0, 0.44, 0, false));
        assertEquals(Direction.EAST, push(f, 0, 0.5, 10, false));
        assertEquals(Direction.EAST, push(f, 0, 0.32, 20, false), "histereza: lekkie cofniecie nie zatrzymuje");
        assertNull(push(f, 0, 0.2, 30, false));

        f.setSensitivity(1);
        assertNull(push(f, 0, 0.55, 0, false), "niska czulosc: wieksza martwa strefa");
        assertEquals(Direction.EAST, push(f, 0, 0.65, 10, false));
        f.reset();
        f.setSensitivity(5);
        assertEquals(Direction.EAST, push(f, 0, 0.32, 0, false), "wysoka czulosc: mala martwa strefa");
    }

    @Test
    void floatingStickIsFourWayUntilTheDiagonalIsHeld() {
        FourWayStick f = new FourWayStick();
        long hold = ControlTuning.float4DiagonalHoldMs(ControlTuning.DEFAULT);
        assertEquals(Direction.NORTH, push(f, 50, 1, 0, false), "na poczatku prosto, nawet w klinie skosu");
        assertEquals(Direction.NORTH, push(f, 46, 1, hold - 1, false));
        assertEquals(Direction.NORTH_EAST, push(f, 44, 1, hold, false), "skos po chwili trzymania w klinie");
        // poza klinem skosu nigdy skos, nawet po dlugim trzymaniu
        f.reset();
        for (long t = 0; t < 2000; t += 16) assertEquals(Direction.EAST, push(f, 30, 1, t, false));
        // wyjscie z klina zeruje odliczanie
        f.reset();
        push(f, 45, 1, 0, false);
        push(f, 20, 1, hold / 2, false);
        assertEquals(Direction.EAST, push(f, 45, 1, hold, false), "odliczanie skosu od nowa");
        assertEquals(Direction.NORTH_EAST, push(f, 45, 1, hold + hold, false));
        // szerszy klin przy wyzszej czulosci
        assertTrue(ControlTuning.float4Diagonal(5) > ControlTuning.float4Diagonal(1));
        assertTrue(ControlTuning.float4DiagonalHoldMs(5) < ControlTuning.float4DiagonalHoldMs(1));
    }

    @Test
    void floatingStickHasHysteresisBetweenStraightDirections() {
        FourWayStick f = new FourWayStick();
        assertEquals(Direction.EAST, push(f, 40, 1, 0, false));
        long t = 0;
        for (int i = 0; i < 30; i++) {
            t += 16;
            // kciuk drga wokol granicy 45 stopni (poza klinem skosu po przekroczeniu) - wschod zostaje
            assertEquals(Direction.EAST, push(f, i % 2 == 0 ? 28 : 52, 1, t, false));
        }
        assertEquals(Direction.NORTH, push(f, 70, 1, t + 16, false), "wyrazny skret zmienia kierunek");
    }

    @Test
    void floatingStickLocksTheDirectionForTheWholeStep() {
        FourWayStick f = new FourWayStick();
        assertEquals(Direction.EAST, push(f, 0, 1, 0, false));
        // w trakcie kroku kciuk ucieka na polnoc - krok idzie dalej na wschod
        assertEquals(Direction.EAST, push(f, 90, 1, 50, true));
        assertEquals(Direction.EAST, push(f, 90, 1, 120, true));
        assertEquals(Direction.NORTH, push(f, 90, 1, 200, false), "nastepny krok w nowym kierunku");
        // puszczenie w trakcie kroku: bez kolejnego kroku
        assertNull(push(f, 0, 0.05, 250, true));
    }

    // ------------------------------------------------------------------ przytrzymanie na mapie

    @Test
    void shortTouchStaysATapAndHoldWalksAfterTheThreshold() {
        HoldToWalk h = new HoldToWalk();
        assertEquals(200, h.delayMs());
        h.press(1000);
        assertNull(h.update(3, 0, 1100, false), "przed progiem - jeszcze nie marsz");
        assertTrue(h.release(1150), "krotkie dotkniecie = dotkniecie pola (cel, uzycie, marsz)");
        assertNull(h.update(3, 0, 1200, false), "po puszczeniu nic");

        h.press(2000);
        assertNull(h.update(3, 0, 2199, false));
        assertEquals(Direction.EAST, h.update(3, 0, 2200, false));
        assertTrue(h.walking());
        assertEquals(1f, h.progress(2300));
        assertFalse(h.release(2300), "puszczenie po marszu to nie dotkniecie pola");
        assertFalse(h.walking());
    }

    @Test
    void holdDelayFollowsSensitivity() {
        HoldToWalk h = new HoldToWalk();
        h.setSensitivity(1);
        assertEquals(300, h.delayMs());
        h.press(0);
        assertNull(h.update(0, -3, 250, false));
        assertEquals(Direction.NORTH, h.update(0, -3, 300, false));
        h.setSensitivity(5);
        assertEquals(120, h.delayMs());
        HoldToWalk d = new HoldToWalk();
        d.press(0);
        assertEquals(0.5f, d.progress(100), 1e-6, "pierscien pod palcem: polowa progu");
    }

    @Test
    void holdDirectionIsFromTheCharacterToTheFingerWithNarrowDiagonals() {
        assertNull(HoldToWalk.classify(0.3f, -0.4f), "palec na polu postaci - postoj");
        assertEquals(Direction.EAST, HoldToWalk.classify(4, 0));
        assertEquals(Direction.SOUTH, HoldToWalk.classify(0.2f, 3), "y rosnie na poludnie");
        assertEquals(Direction.NORTH_EAST, HoldToWalk.classify(3, -3));
        assertEquals(Direction.NORTH_EAST, HoldToWalk.classify(3, -2.5f), "okolo 40 stopni: jeszcze skos");
        assertEquals(Direction.EAST, HoldToWalk.classify(3, -1.5f), "okolo 27 stopni: prosto (waski klin skosu)");
        assertEquals(Direction.SOUTH_WEST, HoldToWalk.classify(-2, 2));
        assertEquals(Direction.WEST, HoldToWalk.classify(-5, 2));
    }

    @Test
    void holdLocksTheDirectionDuringAStep() {
        HoldToWalk h = new HoldToWalk();
        h.press(0);
        assertEquals(Direction.EAST, h.update(3, 0, 300, false));
        assertEquals(Direction.EAST, h.update(0, 3, 350, true), "palec przesuniety w trakcie kroku");
        assertEquals(Direction.SOUTH, h.update(0, 3, 500, false));
        h.cancel();
        assertNull(h.update(3, 0, 600, false));
        assertFalse(h.down());
    }

    // ------------------------------------------------------------------ klasyczny joystick i czulosc

    @Test
    void classicStickKeepsItsDefaultsAndFollowsSensitivity() {
        assertEquals(StickFilter.ENGAGE, ControlTuning.engage(ControlTuning.DEFAULT), 1e-6);
        assertEquals(StickFilter.RELEASE, ControlTuning.release(ControlTuning.DEFAULT), 1e-6);
        assertEquals(StickFilter.DIAGONAL_ENTER, ControlTuning.classicDiagonal(ControlTuning.DEFAULT), 1e-9);
        assertEquals(StickFilter.DIAGONAL_PUSH, ControlTuning.classicDiagonalPush(ControlTuning.DEFAULT), 1e-6);
        StickFilter f = new StickFilter();
        f.setSensitivity(1);
        assertNull(f.update(0.5f, 0, 0), "niska czulosc: wieksze wychylenie");
        assertEquals(Direction.EAST, f.update(0.7f, 0, 10));
        f.reset();
        float[] v = at(52, 1);
        assertEquals(Direction.NORTH, f.update(v[0], v[1], 0), "niska czulosc: waski klin skosu");
        f.reset();
        f.setSensitivity(5);
        assertEquals(Direction.NORTH_EAST, f.update(v[0], v[1], 0), "wysoka czulosc: szerszy klin skosu");
        f.reset();
        assertEquals(Direction.EAST, f.update(0.32f, 0, 0));
        // czulosc spoza zakresu
        assertEquals(ControlTuning.engage(1), ControlTuning.engage(-4), 1e-6);
        assertEquals(ControlTuning.holdDelayMs(5), ControlTuning.holdDelayMs(99));
    }

    @Test
    void schemesHaveKeysAndCycle() {
        assertEquals(ControlScheme.DPAD, ControlScheme.DEFAULT);
        assertEquals(ControlScheme.FLOAT4, ControlScheme.DPAD.next());
        assertEquals(ControlScheme.DPAD, ControlScheme.CLASSIC.next());
        for (ControlScheme s : ControlScheme.values()) assertEquals(s, ControlScheme.byKey(s.key, null));
        assertEquals(ControlScheme.HOLD, ControlScheme.byKey("HOLD", null));
        assertEquals(ControlScheme.CLASSIC, ControlScheme.byKey("??", ControlScheme.CLASSIC));
        assertTrue(ControlScheme.FLOAT4.stick() && ControlScheme.CLASSIC.stick());
        assertFalse(ControlScheme.DPAD.stick() || ControlScheme.HOLD.stick());
    }
}
