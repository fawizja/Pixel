package pl.fawizja.pixel.combat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LowHealthAlarmTest {
    @Test
    void beepsWhenHealthDropsAndRepeatsWhileLow() {
        LowHealthAlarm a = new LowHealthAlarm();
        assertFalse(a.update(100, 100, 0));
        assertFalse(a.update(25, 100, 100), "exactly 25% is not low");
        assertTrue(a.update(24, 100, 200));
        assertTrue(a.low());
        assertFalse(a.update(20, 100, 1000));
        assertTrue(a.update(20, 100, 200 + LowHealthAlarm.REPEAT_MS));
        assertFalse(a.update(60, 100, 5000));
        assertFalse(a.low());
        assertTrue(a.update(10, 100, 5100), "a new drop beeps at once");
        assertFalse(a.update(0, 100, 5200), "dead - no alarm");
        assertFalse(LowHealthAlarm.isLow(5, 0));
    }
}
