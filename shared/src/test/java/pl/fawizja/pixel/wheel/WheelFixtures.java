package pl.fawizja.pixel.wheel;

import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

/** Postacie do testow kola: promocja + premium na danym poziomie, rozdanie punktow po cwiartkach. */
final class WheelFixtures {
    private WheelFixtures() {}

    static PlayerCharacter hero(Vocation v, int level) {
        PlayerCharacter pc = new PlayerCharacter("wheel-" + v, "Test Hero", v, level, new Position(10, 12, 0), TestData.ITEMS, 0, "Varden");
        pc.setPromoted(true);
        pc.setPremium(true);
        return pc;
    }

    /** Najkrotsza droga do pola 200 cwiartki (50, gorne 75, gorne 100, gorne 150, 200) - 575 punktow. */
    static void path(PlayerCharacter pc, WheelDomain d) {
        int av = pc.wheelPoints();
        for (WheelSlice.Pos p : new WheelSlice.Pos[] {WheelSlice.Pos.S50, WheelSlice.Pos.T75, WheelSlice.Pos.T100, WheelSlice.Pos.T150, WheelSlice.Pos.S200}) {
            WheelSlice s = WheelSlice.of(d, p);
            if (pc.wheel().add(s, s.capacity(), av) != DestinyWheel.Result.OK) throw new IllegalStateException(s.code());
        }
        pc.refreshWheel();
    }

    /** Wypelnia otwarte pola cwiartek w kolejnosci (np. "PRBG"), cwiartka po cwiartce, az skoncza sie punkty. */
    static void fill(PlayerCharacter pc, String order) {
        int av = pc.wheelPoints();
        boolean progress = true;
        while (progress && pc.wheel().unused(av) > 0) {
            progress = false;
            for (char c : order.toCharArray()) {
                WheelDomain d = WheelDomain.ofCode(c);
                for (WheelSlice s : WheelSlice.of(d)) {
                    int n = Math.min(pc.wheel().unused(av), s.capacity() - pc.wheel().points(s));
                    if (n > 0 && pc.wheel().add(s, n, av) == DestinyWheel.Result.OK) progress = true;
                }
                if (progress) break;
            }
        }
        pc.refreshWheel();
    }
}
