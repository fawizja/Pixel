package pl.fawizja.pixel.quest;

import java.util.ArrayList;
import java.util.List;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.i18n.Messages;
import pl.fawizja.pixel.npc.Npc;
import pl.fawizja.pixel.npc.NpcRegistry;

/**
 * Dziennik zadan (jak w Tibii): lista linii zadan postaci - w toku, nowe (dostepne na jej poziomie) i ukonczone - z
 * misjami, opisem biezacego kroku i miejscem, dokad isc. Czysta logika bez UI (okno w Hud, testy).
 */
public final class QuestJournal {
    private QuestJournal() {}

    public enum Status { ACTIVE, NEW, DONE }

    public enum StepState { DONE, CURRENT, LOCKED }

    /** Krok = misja linii: stan, postep i opis w biezacym jezyku. */
    public record Step(QuestType quest, StepState state, int progress, int amount, boolean ready) {
        public String text() { return quest.description(); }
    }

    /** Wpis dziennika: linia, status, kroki (kolejne misje), indeks biezacego kroku (-1 = brak) i opis miejsca. */
    public record Entry(QuestLines.Line line, Status status, List<Step> steps, int current, String where, String giverName,
                        String reward) {
        public String name() { return line.name(); }
        public Step currentStep() { return current < 0 ? null : steps.get(current); }
        public int doneCount() {
            int n = 0;
            for (Step s : steps) if (s.state() == StepState.DONE) n++;
            return n;
        }
    }

    /**
     * Wszystkie wpisy: najpierw w toku (gotowe do oddania na poczatku), potem nowe (poziom postaci wystarcza na pierwsza
     * misje), na koncu ukonczone. Linie, do ktorych postac nie doszla poziomem i ktorych nie zaczela, sa ukryte.
     */
    public static List<Entry> build(PlayerCharacter pc, QuestSystem quests, QuestLines lines, NpcRegistry npcs) {
        List<Entry> active = new ArrayList<>(), fresh = new ArrayList<>(), done = new ArrayList<>();
        for (QuestLines.Line l : lines.all()) {
            List<Step> steps = new ArrayList<>();
            int current = -1;
            int finished = 0;
            boolean started = false;
            for (QuestType q : l.missions()) {
                QuestLog.State st = pc.quests().state(q.key());
                if (st == QuestLog.State.DONE) {
                    steps.add(new Step(q, StepState.DONE, q.amount(), q.amount(), false));
                    finished++;
                    started = true;
                } else if (st == QuestLog.State.ACTIVE) {
                    if (current < 0) current = steps.size();
                    steps.add(new Step(q, StepState.CURRENT, quests.progress(pc, q), q.amount(), quests.ready(pc, q)));
                    started = true;
                } else {
                    boolean next = current < 0 && quests.unlocked(pc, q);
                    if (next) current = steps.size();
                    steps.add(new Step(q, next ? StepState.CURRENT : StepState.LOCKED, 0, q.amount(), false));
                }
            }
            Status status = finished == l.missions().size() ? Status.DONE : started ? Status.ACTIVE : Status.NEW;
            if (status == Status.NEW && pc.level() < l.minLevel()) continue;
            if (status == Status.NEW && !quests.unlocked(pc, l.first())) continue;   // czeka na inne zadania (quest_requires.csv)
            if (status == Status.DONE) current = -1;
            // linia ukonczona czesciowo, bez misji w toku: nastepna misja czeka u zleceniodawcy (current ustawiony wyzej)
            Npc g = npcs == null ? null : npcs.get(l.giver());
            String giver = g == null ? l.giver() : g.name() + " (" + g.town() + ", " + g.position().x() + ", " + g.position().y() + ")";
            Entry e = new Entry(l, status, List.copyOf(steps), current, l.where(), giver, rewardOf(quests, l));
            (status == Status.ACTIVE ? active : status == Status.NEW ? fresh : done).add(e);
        }
        active.sort((a, b) -> Boolean.compare(ready(b), ready(a)));
        List<Entry> out = new ArrayList<>(active);
        out.addAll(fresh);
        out.addAll(done);
        return out;
    }

    private static boolean ready(Entry e) {
        return e.currentStep() != null && e.currentStep().ready();
    }

    /** Suma nagrod linii: doswiadczenie bez mnoznika etapu i zloto, plus przedmioty lancucha. */
    private static String rewardOf(QuestSystem quests, QuestLines.Line l) {
        long exp = 0, gold = 0;
        for (QuestType q : l.missions()) {
            exp += q.rewardExp();
            gold += q.rewardGold();
        }
        return Messages.get("questlog.reward", exp, gold);
    }

    /** Opis biezacego kroku: co zrobic i dokad isc (wiersz pod tytulem). */
    public static String hint(Entry e, QuestSystem quests, java.util.function.Function<String, String> monsterName,
                              java.util.function.Function<String, String> itemName) {
        Step s = e.currentStep();
        if (e.status() == Status.DONE) return Messages.get("questlog.done_hint");
        if (s == null) return "";
        QuestType q = s.quest();
        if (s.state() == StepState.CURRENT && e.status() == Status.NEW) return Messages.get("questlog.go_talk", e.giverName());
        if (s.ready()) return Messages.get("questlog.turn_in", e.giverName());
        switch (q.kind()) {
            case VISIT: return Messages.get("questlog.visit", Messages.data("place", q.target(), "name", q.target()));
            case COLLECT: return Messages.get("questlog.collect", s.progress(), s.amount(), itemName.apply(q.target()));
            default: return Messages.get("questlog.kill", s.progress(), s.amount(), monsterName.apply(q.target()));
        }
    }
}
