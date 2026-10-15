package pl.fawizja.pixel.quest;

import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.economy.Money;
import pl.fawizja.pixel.inventory.Container;
import pl.fawizja.pixel.item.ItemRegistry;

/** Przyjmowanie, liczenie i oddawanie zadan (bez stanu - stan jest w QuestLog postaci). */
public final class QuestSystem {
    private final QuestRegistry registry;
    private final ItemRegistry items;

    public QuestSystem(QuestRegistry registry, ItemRegistry items) {
        this.registry = registry;
        this.items = items;
    }

    public QuestRegistry registry() { return registry; }

    /** Przyjecie zadania albo, gdy jest wykonane, oddanie go. */
    public QuestResult interact(PlayerCharacter pc, QuestType q) {
        QuestLog log = pc.quests();
        switch (log.state(q.key())) {
            case DONE: return QuestResult.ALREADY_DONE;
            case ACTIVE: return turnIn(pc, q);
            default:
                if (!unlocked(pc, q)) return QuestResult.LOCKED;
                if (pc.level() < q.minLevel()) return QuestResult.TOO_LOW_LEVEL;
                log.start(q.key());
                return QuestResult.ACCEPTED;
        }
    }

    /** Czy poprzedni etap lancucha jest ukonczony (albo zadanie nie ma poprzednika). */
    public boolean unlocked(PlayerCharacter pc, QuestType q) {
        return q.requires() == null || pc.quests().state(q.requires()) == QuestLog.State.DONE;
    }

    /** Opis nagrody do okna rozmowy i komunikatow. */
    public String rewardText(QuestType q) {
        StringBuilder b = new StringBuilder(q.rewardExp() + " exp");
        if (q.rewardGold() > 0) b.append(", ").append(q.rewardGold()).append(" gp");
        if (q.rewardItem() != null) {
            b.append(", ").append(q.rewardItemCount() > 1 ? q.rewardItemCount() + "x " : "").append(items.byKey(q.rewardItem()).name());
        }
        return b.toString();
    }

    /** Ile celu juz zebrano: zabicia z licznika albo sztuki w plecaku. */
    public int progress(PlayerCharacter pc, QuestType q) {
        if (pc.quests().state(q.key()) == QuestLog.State.DONE) return q.amount();
        if (q.kind() == QuestKind.KILL) return pc.quests().progress(q.key());
        return Math.min(q.amount(), pc.backpack().count(items.byKey(q.target()).id()));
    }

    public boolean ready(PlayerCharacter pc, QuestType q) {
        return pc.quests().state(q.key()) == QuestLog.State.ACTIVE && progress(pc, q) >= q.amount();
    }

    private QuestResult turnIn(PlayerCharacter pc, QuestType q) {
        if (!ready(pc, q)) return QuestResult.NOT_FINISHED;
        Container tx = pc.backpack().copy();
        if (q.kind() == QuestKind.COLLECT && !tx.remove(items.byKey(q.target()).id(), q.amount()).ok()) {
            return QuestResult.NOT_FINISHED;
        }
        if (!Money.add(tx, q.rewardGold()).ok()) return QuestResult.NO_SPACE;
        if (q.rewardItem() != null
                && !tx.add(new pl.fawizja.pixel.item.ItemStack(items.byKey(q.rewardItem()).id(), q.rewardItemCount())).ok()) {
            return QuestResult.NO_SPACE;
        }
        pc.backpack().commitFrom(tx);
        pc.addExperience(q.rewardExp());
        pc.quests().finish(q.key());
        return QuestResult.COMPLETED;
    }

    /** Wywolywane po zabiciu potwora; zwraca zadanie, ktorego cel wlasnie osiagnieto (albo null). */
    public QuestType onKill(PlayerCharacter pc, String monsterKey) {
        QuestType completedNow = null;
        for (String key : pc.quests().active().keySet().toArray(new String[0])) {
            QuestType q = registry.get(key);
            if (q == null || q.kind() != QuestKind.KILL || !q.target().equals(monsterKey)) continue;
            int before = pc.quests().progress(key);
            pc.quests().advance(key, 1, q.amount());
            if (before < q.amount() && pc.quests().progress(key) >= q.amount()) completedNow = q;
        }
        return completedNow;
    }
}
