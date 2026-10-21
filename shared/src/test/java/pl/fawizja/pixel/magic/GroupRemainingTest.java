package pl.fawizja.pixel.magic;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GroupRemainingTest {
    @Test
    void potionGroupCountsDownForTheBarOverlay() {
        Cooldowns c = new Cooldowns();
        assertEquals(0, c.groupRemaining(SpellGroup.POTION, 100));
        c.triggerGroup(SpellGroup.POTION, 1000, 1000);
        assertEquals(400, c.groupRemaining(SpellGroup.POTION, 1600));
        assertEquals(0, c.groupRemaining(SpellGroup.POTION, 2500));
    }
}
