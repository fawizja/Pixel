package pl.fawizja.pixel.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.monster.LootRules;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.world.BossQuest;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Przebieg wypraw na bossa w piec postaci ({@link BossQuest}): stan kazdej areny, dzwignia, boss, lup i reset.
 * Ta sama klasa dziala w grze offline (sesja z jedna postacia i "duchami" do testow) i na serwerze (wszyscy gracze);
 * nie zna graczy - widzi ich przez {@link Hero}.
 * <ul>
 *   <li>Dzwignia: arena wolna, ciagnacy nie ukonczyl wyprawy, na pieciu polach stoi piec zywych postaci, z ktorych
 *       zadna nie ukonczyla wyprawy. Wtedy wszystkie trafiaja na arene, a boss sie pojawia (bez odradzania).</li>
 *   <li>Smierc bossa: kazdy uczestnik, ktory zyje i stoi na arenie, dostaje wlasny lup
 *       ({@link LootRules#rollBossLootPerPlayer}) prosto do plecaka (gdy pelny - do depozytu) i zapis ukonczenia.
 *       Cialo bossa jest puste. W rogu areny otwiera sie portal do przedsionka.</li>
 *   <li>Reset: gdy podczas walki na arenie nie ma juz zadnego zywego uczestnika, boss znika i dzwignia jest wolna.
 *       Po walce arena zwalnia sie, gdy wszyscy wyjda (albo po {@link #CLEARED_MAX_MS} - wtedy portal sam ich wynosi).</li>
 * </ul>
 */
public final class BossQuestRuntime {

    /** Postac widziana przez wyprawe (gracz albo duch w trybie testowym). */
    public interface Hero {
        /** Unikalny identyfikator (imie postaci). */
        String id();

        Position position();

        boolean dead();

        boolean completed(String questId);

        void markCompleted(String questId);

        void teleport(Position to, long nowMs);

        void message(EventLog.Kind kind, String text, long nowMs);

        /** Do plecaka; false = brak miejsca. */
        boolean toBackpack(ItemStack s);

        /** Do depozytu; false = brak miejsca. */
        boolean toDepot(ItemStack s);

        /** Ostatecznosc: przedmiot na ziemi pod postacia. */
        void drop(ItemStack s, long nowMs);
    }

    public enum Phase { IDLE, FIGHT, CLEARED }

    /** Po tylu ms od zabicia bossa portal sam wynosi maruderow, a arena sie zwalnia. */
    public static final long CLEARED_MAX_MS = 10 * 60_000L;

    private static final class Arena {
        final BossQuest quest;
        Phase phase = Phase.IDLE;
        int bossId = -1;
        final List<String> party = new ArrayList<>();
        long since;

        Arena(BossQuest quest) { this.quest = quest; }
    }

    private final ItemRegistry items;
    private final MonsterManager monsters;
    private final Random rng;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public BossQuestRuntime(WorldMap world, ItemRegistry items, MonsterManager monsters, Random rng) {
        this.items = items;
        this.monsters = monsters;
        this.rng = rng;
        for (BossQuest q : world.bossQuests()) arenas.put(q.id(), new Arena(q));
    }

    public Phase phase(String questId) {
        Arena a = arenas.get(questId);
        return a == null ? Phase.IDLE : a.phase;
    }

    /** Numer potwora-bossa toczacej sie walki albo -1. */
    public int bossId(String questId) {
        Arena a = arenas.get(questId);
        return a == null || a.phase != Phase.FIGHT ? -1 : a.bossId;
    }

    /** Uczestnicy biezacej walki (identyfikatory). */
    public List<String> party(String questId) {
        Arena a = arenas.get(questId);
        return a == null ? List.of() : List.copyOf(a.party);
    }

    /** Postacie stojace na polach przedsionka (zywe, kazde pole raz). */
    public static List<Hero> onSquares(BossQuest q, Collection<? extends Hero> everyone) {
        List<Hero> out = new ArrayList<>();
        for (BossQuest.Square s : q.squares()) {
            for (Hero h : everyone) {
                if (!h.dead() && s.stand().equals(h.position())) {
                    out.add(h);
                    break;
                }
            }
        }
        return out;
    }

    /**
     * Dzwignia przedsionka. Zwraca true, gdy wyprawa sie zaczela; w kazdym przypadku ciagnacy dostaje komunikat.
     */
    public boolean pull(BossQuest q, Hero puller, Collection<? extends Hero> everyone, long nowMs) {
        Arena a = arenas.get(q.id());
        if (a == null) return false;
        if (puller.completed(q.id())) {
            puller.message(EventLog.Kind.WARNING, "Dźwignia ani drgnie. Ta postać już ukończyła wyprawę " + q.name()
                    + " - każdą wyprawę na bossa można przejść tylko raz.", nowMs);
            return false;
        }
        if (a.phase != Phase.IDLE) {
            puller.message(EventLog.Kind.WARNING, a.phase == Phase.FIGHT
                    ? "Dźwignia jest zablokowana - na arenie trwa walka innej drużyny. Spróbuj później."
                    : "Dźwignia jest zablokowana - drużyna jeszcze nie opuściła areny po walce.", nowMs);
            return false;
        }
        List<Hero> standing = onSquares(q, everyone);
        int missing = BossQuest.PARTY - standing.size();
        if (missing > 0) {
            puller.message(EventLog.Kind.WARNING, "Dźwignia ani drgnie. Na oznaczonych polach stoi " + standing.size()
                    + " z " + BossQuest.PARTY + " postaci - brakuje jeszcze " + missing + ".", nowMs);
            return false;
        }
        List<String> done = new ArrayList<>();
        for (Hero h : standing) if (h.completed(q.id())) done.add(h.id());
        if (!done.isEmpty()) {
            puller.message(EventLog.Kind.WARNING, "Dźwignia ani drgnie. Wyprawę " + q.name() + " już ukończył(a): "
                    + String.join(", ", done) + ". Na polach mogą stanąć tylko postacie, które jej jeszcze nie przeszły.", nowMs);
            return false;
        }
        List<Monster> boss = monsters.spawnEvent(q.bossKey(), q.bossSpawn(), 1, 1, nowMs);
        if (boss.isEmpty()) {
            puller.message(EventLog.Kind.WARNING, "Dźwignia zgrzyta, ale arena jest zablokowana. Spróbuj za chwilę.", nowMs);
            return false;
        }
        a.phase = Phase.FIGHT;
        a.bossId = boss.get(0).id();
        a.party.clear();
        a.since = nowMs;
        List<BossQuest.Square> squares = q.squares();
        for (int i = 0; i < standing.size(); i++) {
            Hero h = standing.get(i);
            a.party.add(h.id());
            h.teleport(target(q, h, squares), nowMs);
        }
        String who = String.join(", ", a.party);
        for (Hero h : standing) {
            h.message(EventLog.Kind.SYSTEM, (h.id().equals(puller.id()) ? "Przeciągasz dźwignię" : puller.id() + " przeciąga dźwignię")
                    + ". Arena " + q.name() + " zamyka się za wami - " + boss.get(0).type().name() + " się budzi! Drużyna: " + who + ".", nowMs);
        }
        if (!a.party.contains(puller.id())) {
            puller.message(EventLog.Kind.SYSTEM, "Przeciągasz dźwignię - drużyna (" + who + ") trafia na arenę.", nowMs);
        }
        return true;
    }

    private static Position target(BossQuest q, Hero h, List<BossQuest.Square> squares) {
        for (BossQuest.Square s : squares) if (s.stand().equals(h.position())) return s.target();
        return squares.get(0).target();
    }

    /** Najwiecej duchow w trybie testowym offline (piata postac to gracz). */
    public static final int PARTY_GHOSTS_MAX = BossQuest.PARTY - 1;

    /**
     * Tryb testowy offline (-Dpixel.bossGhosts=N): n "duchow" stoi na wolnych polach przedsionka. Licza sie tylko
     * przy dzwigni - nie trafiaja na arene, nie walcza i nie dostaja lupu.
     */
    public static List<Hero> ghosts(BossQuest q, int n, Collection<? extends Hero> real) {
        List<Hero> out = new ArrayList<>();
        for (BossQuest.Square s : q.squares()) {
            if (out.size() >= n) break;
            boolean taken = false;
            for (Hero h : real) if (!h.dead() && s.stand().equals(h.position())) taken = true;
            if (!taken) out.add(new Ghost("Duch " + (out.size() + 1), s.stand()));
        }
        return out;
    }

    private record Ghost(String id, Position position) implements Hero {
        @Override
        public boolean dead() { return false; }

        @Override
        public boolean completed(String questId) { return false; }

        @Override
        public void markCompleted(String questId) {}

        @Override
        public void teleport(Position to, long nowMs) {}

        @Override
        public void message(EventLog.Kind kind, String text, long nowMs) {}

        @Override
        public boolean toBackpack(ItemStack s) { return true; }

        @Override
        public boolean toDepot(ItemStack s) { return true; }

        @Override
        public void drop(ItemStack s, long nowMs) {}
    }

    /** Czy lup tego potwora rozdaje wyprawa (cialo bossa zostaje puste). */
    public boolean ownsLoot(Monster m) {
        for (Arena a : arenas.values()) if (a.phase == Phase.FIGHT && a.bossId == m.id()) return true;
        return false;
    }

    /**
     * Smierc potwora: gdy to boss wyprawy, kazdy zywy uczestnik na arenie dostaje wlasny lup i zapis ukonczenia.
     * Zwraca wyprawe, gdy to byl jej boss (uczestnicy: {@link #party}), inaczej null.
     */
    public BossQuest onMonsterDeath(Monster m, Collection<? extends Hero> everyone, long nowMs) {
        for (Arena a : arenas.values()) {
            if (a.phase != Phase.FIGHT || a.bossId != m.id()) continue;
            BossQuest q = a.quest;
            List<Hero> winners = new ArrayList<>();
            for (Hero h : everyone) {
                if (a.party.contains(h.id()) && !h.dead() && q.inArena(h.position())) winners.add(h);
            }
            List<String> ids = new ArrayList<>();
            for (Hero h : winners) ids.add(h.id());
            MonsterType boss = m.type();
            Map<String, List<ItemStack>> loot = LootRules.rollBossLootPerPlayer(boss, items, ids, rng);
            for (Hero h : winners) {
                h.markCompleted(q.id());
                h.message(EventLog.Kind.SYSTEM, "Wyprawa " + q.name() + " ukończona! W rogu areny otworzył się portal"
                        + " z powrotem do przedsionka.", nowMs);
                give(h, boss, loot.get(h.id()), nowMs);
            }
            a.phase = Phase.CLEARED;
            a.bossId = -1;
            a.since = nowMs;
            return q;
        }
        return null;
    }

    /** Lup jednego gracza: do plecaka, gdy pelny - do depozytu, w ostatecznosci na ziemie. Komunikat z lista. */
    private void give(Hero h, MonsterType boss, List<ItemStack> loot, long nowMs) {
        List<String> parts = new ArrayList<>();
        List<String> rare = new ArrayList<>();
        for (ItemStack s : loot) {
            ItemType t = items.get(s.itemId());
            String d = (s.count() > 1 ? s.count() + "x " : "") + t.name();
            if (LootRules.isBossOnly(t)) {
                d = "*" + d + "* (tylko z bossa!)";   // czcionka gry nie ma znakow << >>
                rare.add(t.name());
            }
            if (h.toBackpack(s)) {
                parts.add(d);
            } else if (h.toDepot(s)) {
                parts.add(d + " (do depozytu - plecak pełny)");
            } else {
                h.drop(s, nowMs);
                parts.add(d + " (na ziemi - brak miejsca)");
            }
        }
        if (parts.isEmpty()) {
            h.message(EventLog.Kind.LOOT, "Łup z bossa " + boss.name() + ": tym razem nic dla ciebie - tylko chwała.", nowMs);
        } else {
            h.message(EventLog.Kind.LOOT, "Twój łup z bossa " + boss.name() + ": " + String.join(", ", parts) + ".", nowMs);
        }
        if (!rare.isEmpty()) {
            h.message(EventLog.Kind.LOOT, "Rzadki łup tylko z bossa: *" + String.join("*, *", rare) + "*!", nowMs);
        }
    }

    /**
     * Co klatke: reset areny bez zywych uczestnikow, portal wyjscia po walce, zwolnienie areny po wyjsciu druzyny.
     */
    public void update(Collection<? extends Hero> everyone, long nowMs) {
        for (Arena a : arenas.values()) {
            BossQuest q = a.quest;
            if (a.phase == Phase.IDLE) continue;
            List<Hero> inside = new ArrayList<>();
            for (Hero h : everyone) {
                if (a.party.contains(h.id()) && !h.dead() && q.inArena(h.position())) inside.add(h);
            }
            if (a.phase == Phase.FIGHT) {
                boolean bossGone = true;
                for (Monster m : monsters.monsters()) if (m.id() == a.bossId && !m.isDead()) bossGone = false;
                if (inside.isEmpty() || bossGone) reset(a, everyone, nowMs, bossGone);
                continue;
            }
            // po walce: portal przenosi do przedsionka; po czasie wynosi wszystkich
            boolean timeout = nowMs - a.since >= CLEARED_MAX_MS || nowMs < a.since;
            for (Hero h : inside) {
                if (timeout || h.position().equals(q.exitPortal())) {
                    h.teleport(q.exitTarget(), nowMs);
                    h.message(EventLog.Kind.INFO, timeout ? "Portal areny gaśnie i wynosi cię do przedsionka."
                            : "Portal przenosi cię z powrotem do przedsionka.", nowMs);
                }
            }
            boolean anyone = false;
            for (Hero h : everyone) if (a.party.contains(h.id()) && !h.dead() && q.inArena(h.position())) anyone = true;
            if (!anyone) {
                a.phase = Phase.IDLE;
                a.party.clear();
            }
        }
    }

    private void reset(Arena a, Collection<? extends Hero> everyone, long nowMs, boolean bossGone) {
        if (!bossGone) monsters.clearEvent(List.of(a.bossId));
        for (Hero h : everyone) {
            if (!a.party.contains(h.id())) continue;
            h.message(EventLog.Kind.WARNING, "Arena " + a.quest.name() + " się zamyka - drużyna poległa albo opuściła walkę."
                    + " Dźwignia znów działa dla postaci, które nie ukończyły wyprawy.", nowMs);
        }
        a.phase = Phase.IDLE;
        a.bossId = -1;
        a.party.clear();
    }

    /**
     * Postac na arenie, ktora nie bierze udzialu w biezacej walce (np. wylogowala sie w trakcie i wraca po resecie):
     * pole wyjscia z przedsionka, inaczej null.
     */
    public Position evictTarget(Hero h) {
        for (Arena a : arenas.values()) {
            if (!a.quest.inArena(h.position())) continue;
            if (a.phase != Phase.IDLE && a.party.contains(h.id())) return null;
            return a.quest.exitTarget();
        }
        return null;
    }
}
