package pl.fawizja.pixel.game;

import pl.fawizja.pixel.character.PlayerCharacter;

/** Dostep do pomocnika CorpseTest z innych pakietow testow. */
public final class CorpseTestAccess {
    private CorpseTestAccess() {}

    public static GameSession killBoss(PlayerCharacter pc) { return CorpseTest.killBoss(pc, false); }
}
