package pl.fawizja.pixel.pvp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class BountiesTest {

    @Test
    void amountsAddUpAndAreClaimedOnce() {
        Bounties b = new Bounties();
        assertEquals(5000, b.add("Jan", 5000));
        assertEquals(7000, b.add("jan", 2000), "case does not matter");
        b.add("Ola", 9000);
        assertEquals(List.of("Ola", "Jan"), b.top(10).stream().map(Bounties.Bounty::name).toList());
        assertEquals(7000, b.claim("JAN"));
        assertEquals(0, b.claim("Jan"), "paid only once");
        assertEquals(0, b.amount("Jan"));
        assertThrows(IllegalArgumentException.class, () -> b.add("Jan", 0));
    }

    @Test
    void survivesTheFileRoundTrip() {
        Bounties b = new Bounties();
        b.add("Stary Wilk", 12_000);
        b.add("Ola", Long.MAX_VALUE);
        b.add("Ola", 5);
        Bounties back = Bounties.decode(b.encode() + "zle;linia;x\n;5\nKto;abc\n");
        assertEquals(12_000, back.amount("stary wilk"));
        assertEquals(Long.MAX_VALUE, back.amount("Ola"), "no overflow");
        assertEquals(2, back.top(10).size());
        assertEquals(0, Bounties.decode(null).top(10).size());
    }
}
