package pl.fawizja.pixel.quest;

/**
 * Definicja zadania z data/quests.csv. target = klucz potwora (KILL) albo przedmiotu (COLLECT).
 * Z data/quest_chains.csv: requires - zadanie, ktore trzeba wczesniej ukonczyc (lancuch etapow) albo null;
 * rewardItem / rewardItemCount - przedmiot w nagrodzie (null, gdy brak).
 */
public record QuestType(String key, String name, String giver, QuestKind kind, String target, int amount,
                        long rewardExp, long rewardGold, int minLevel, String description,
                        String requires, String rewardItem, int rewardItemCount) {
    public QuestType {
        if (amount < 1 || rewardExp < 0 || rewardGold < 0 || minLevel < 1) throw new IllegalArgumentException("bad quest " + key);
        if ((rewardItem == null) != (rewardItemCount == 0) || rewardItemCount < 0) throw new IllegalArgumentException("bad item reward " + key);
    }

    public QuestType(String key, String name, String giver, QuestKind kind, String target, int amount,
                     long rewardExp, long rewardGold, int minLevel, String description) {
        this(key, name, giver, kind, target, amount, rewardExp, rewardGold, minLevel, description, null, null, 0);
    }

    QuestType withChain(String requires, String rewardItem, int rewardItemCount) {
        return new QuestType(key, name, giver, kind, target, amount, rewardExp, rewardGold, minLevel, description,
                requires, rewardItem, rewardItemCount);
    }
}
