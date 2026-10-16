package pl.fawizja.pixel.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.monster.MonsterManager.BossStatus;
import pl.fawizja.pixel.world.Position;

class BossWatchTest {

    static BossStatus grim(boolean alive) {
        return new BossStatus("Grimtooth", new Position(12, 28, -5), "Varden", alive, alive ? 0 : 5000);
    }

    @Test
    void announcesOnlyARespawnSeenAfterTheFirstReport() {
        BossWatch w = new BossWatch();
        assertTrue(w.update(List.of(grim(false))).isEmpty(), "the first report only primes the watch");
        assertTrue(w.update(List.of(grim(false))).isEmpty());
        List<BossStatus> back = w.update(List.of(grim(true)));
        assertEquals(1, back.size());
        assertTrue(BossWatch.message(back.get(0), 0).contains("Grimtooth"), BossWatch.message(back.get(0), 0));
        assertTrue(w.update(List.of(grim(true))).isEmpty(), "announced once");
    }

    @Test
    void aBossAliveAtLoginIsNotAnnounced() {
        BossWatch w = new BossWatch();
        assertTrue(w.update(List.of(grim(true))).isEmpty());
        assertTrue(w.update(List.of(grim(true))).isEmpty());
    }
}
