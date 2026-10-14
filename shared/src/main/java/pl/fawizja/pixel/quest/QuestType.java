package pl.fawizja.pixel.quest;

/** Definicja zadania z data/quests.csv. target = klucz potwora (KILL) albo przedmiotu (COLLECT). */
public record QuestType(String key, String name, String giver, QuestKind kind, String target, int amount,
                        long rewardExp, long rewardGold, int minLevel, String description) {
    public QuestType {
        if (amount < 1 || rewardExp < 0 || rewardGold < 0 || minLevel < 1) throw new IllegalArgumentException("bad quest " + key);
    }
}
