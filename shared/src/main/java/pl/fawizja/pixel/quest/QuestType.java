package pl.fawizja.pixel.quest;

import pl.fawizja.pixel.i18n.Messages;

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

    /** Nazwa zadania w biezacym jezyku. */
    @Override
    public String name() { return Messages.data("quest", key, name); }

    /** Opis w biezacym jezyku (EN/ES z i18n/data_*.csv, pole {@code desc}; brak wiersza = polski opis z danych). */
    public String description() { return Messages.data("quest", key, "desc", description); }

    public QuestType(String key, String name, String giver, QuestKind kind, String target, int amount,
                     long rewardExp, long rewardGold, int minLevel, String description) {
        this(key, name, giver, kind, target, amount, rewardExp, rewardGold, minLevel, description, null, null, 0);
    }

    QuestType withChain(String requires, String rewardItem, int rewardItemCount) {
        return new QuestType(key, name, giver, kind, target, amount, rewardExp, rewardGold, minLevel, description,
                requires, rewardItem, rewardItemCount);
    }
}
