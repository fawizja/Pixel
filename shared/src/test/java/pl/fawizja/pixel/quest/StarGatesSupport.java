package pl.fawizja.pixel.quest;

import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.world.QuestGate;
import pl.fawizja.pixel.world.WorldMap;

/** Pomoc testow: postac, ktora spelnia wszystkie wymagania bram zadan mapy (ukonczona wyprawa Pieczeci Gasnacej Gwiazdy). */
public final class StarGatesSupport {
    private StarGatesSupport() {}

    public static void openAll(PlayerCharacter pc, WorldMap world) {
        for (QuestGate g : world.questGates().values()) {
            switch (g.kind()) {
                case QUEST: pc.quests().finish(g.target()); break;
                case BOSS: pc.setQuestFlag(QuestGate.bossFlag(g.target())); break;
                default: pc.setQuestFlag(g.target());
            }
        }
    }
}
