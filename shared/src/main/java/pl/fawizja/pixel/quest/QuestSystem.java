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
        if (q.requires() != null && pc.quests().state(q.requires()) != QuestLog.State.DONE) return false;
        for (String r : registry.prereqs(q.key())) {
            if (r.startsWith("!")) {
                if (pc.quests().state(r.substring(1)) != QuestLog.State.AVAILABLE) return false;   // wybrano inna frakcje
            } else if (pc.quests().state(r) != QuestLog.State.DONE) {
                return false;
            }
        }
        return true;
    }

    /**
     * Doswiadczenie za zadanie dla postaci: stala nagroda z quests.csv razy mnoznik etapu serwera dla jej
     * biezacego poziomu (Balance.expRate) - tak samo jak za zabicie potwora (Darek, 2026-11-04).
     */
    private long lastRewardExp;

    /** Doswiadczenie przyznane przy ostatnim oddaniu zadania (komunikat po awansie liczylby juz z nowego poziomu). */
    public long lastRewardExp() { return lastRewardExp; }

    public static long rewardExp(PlayerCharacter pc, QuestType q) {
        return pl.fawizja.pixel.balance.Balance.applyRate(q.rewardExp(), pl.fawizja.pixel.balance.Balance.expRate(pc.level()));
    }

    /** Opis nagrody do okna rozmowy i komunikatow (doswiadczenie bez mnoznika etapu). */
    public String rewardText(QuestType q) {
        return rewardText(q, q.rewardExp());
    }

    /** Opis nagrody z doswiadczeniem, jakie dostanie ta postac (z mnoznikiem etapu). */
    public String rewardText(QuestType q, PlayerCharacter pc) {
        return rewardText(q, rewardExp(pc, q));
    }

    /** Opis nagrody z podanym doswiadczeniem (np. przyznanym przy oddaniu). */
    public String rewardText(QuestType q, long exp) {
        StringBuilder b = new StringBuilder(exp + " exp");
        if (q.rewardGold() > 0) b.append(", ").append(q.rewardGold()).append(" gp");
        if (q.rewardItem() != null) {
            b.append(", ").append(q.rewardItemCount() > 1 ? q.rewardItemCount() + "x " : "").append(items.byKey(q.rewardItem()).name());
        }
        return b.toString();
    }

    /** Ile celu juz zebrano: zabicia z licznika albo sztuki w plecaku. */
    public int progress(PlayerCharacter pc, QuestType q) {
        if (pc.quests().state(q.key()) == QuestLog.State.DONE) return q.amount();
        if (q.kind() != QuestKind.COLLECT) return pc.quests().progress(q.key());
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
        lastRewardExp = rewardExp(pc, q);
        pc.addExperience(lastRewardExp);
        pc.quests().finish(q.key());
        StarQuest.onCompleted(pc, q.key());
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

    /** Uzycie skrzynki pocztowej (nazwa z mailboxes.csv): zalicza aktywne zadania VISIT o tym celu, jak {@link #onEnter}. */
    public QuestType onMailbox(PlayerCharacter pc, String mailboxName) {
        return onEnter(pc, mailboxName);
    }

    /** Wejscie do regionu mapy (nazwa z mapy): zalicza aktywne zadania typu VISIT; zwraca pierwsze zaliczone albo null. */
    public QuestType onEnter(PlayerCharacter pc, String regionName) {
        QuestType completedNow = null;
        for (String key : pc.quests().active().keySet().toArray(new String[0])) {
            QuestType q = registry.get(key);
            if (q == null || q.kind() != QuestKind.VISIT || !q.target().equals(regionName)) continue;
            if (pc.quests().progress(key) >= q.amount()) continue;
            pc.quests().advance(key, 1, q.amount());
            completedNow = q;
        }
        return completedNow;
    }
}
