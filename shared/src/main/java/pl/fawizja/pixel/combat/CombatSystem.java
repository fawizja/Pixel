package pl.fawizja.pixel.combat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.inventory.InventoryResult;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.monster.LootEntry;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterState;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.world.GroundItems;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Walka PvM: automatyczny atak wybranego celu, ataki potworow, smierc, doswiadczenie
 * i lupy. Kazda akcja zmienia stan gry (HP, exp, umiejetnosci, przedmioty).
 */
public final class CombatSystem implements MonsterManager.AttackHandler {

    private final PlayerCharacter pc;
    private final WorldMap world;
    private final ItemRegistry items;
    private final MonsterManager monsters;
    private final GroundItems ground;
    private final EventLog log;
    private final Random rng;
    private final int minLevel;
    private final ArrayDeque<CombatEvent> events = new ArrayDeque<>();
    private long eventTotal;
    private Monster target;
    private long nextPlayerAttackMs;
    private boolean autoLoot = true;
    private int kills;

    public CombatSystem(PlayerCharacter pc, WorldMap world, ItemRegistry items, MonsterManager monsters,
                        GroundItems ground, EventLog log, Random rng, int minLevel) {
        this.pc = pc;
        this.world = world;
        this.items = items;
        this.monsters = monsters;
        this.ground = ground;
        this.log = log;
        this.rng = rng;
        this.minLevel = minLevel;
    }

    public Monster target() { return target; }
    public int kills() { return kills; }

    private java.util.function.Consumer<MonsterType> killListener;

    /** Powiadomienie o zabiciu potwora przez gracza (np. licznik zadan). */
    public void setKillListener(java.util.function.Consumer<MonsterType> listener) { this.killListener = listener; }

    /** Konkretny pokonany potwor (np. serwer liczy najezdzcow inwazji). */
    private java.util.function.Consumer<Monster> deathListener;

    public void setDeathListener(java.util.function.Consumer<Monster> listener) { this.deathListener = listener; }

    /** Potwory, ktorych lup rozdaje kto inny (boss wyprawy w piec postaci): cialo zostaje puste. */
    private java.util.function.Predicate<Monster> lootOwner;

    public void setLootOwner(java.util.function.Predicate<Monster> owner) { this.lootOwner = owner; }

    /**
     * Podzial doswiadczenia w druzynie (serwer); true = doswiadczenie juz rozdane, zabojca nie dostaje go sam.
     * expPercent - czesc doswiadczenia za tego potwora (100, w stadzie {@link #PACK_EXP_PERCENT}).
     */
    public interface ExperienceSharer { boolean share(MonsterType killed, int expPercent, long now); }

    /**
     * Doswiadczenie za potwora ze stada (spawn "stado" w lepszych lowiskach, {@link pl.fawizja.pixel.monster.SpawnPoint#pack()}).
     * Stada maja 3-6 potworow na punkt i dodatkowe stada obok, wiec postac z czarami obszarowymi zabija tam wiecej
     * na godzine niz w zwyklym lowisku; 85% za sztuke trzyma doswiadczenie solo na godzine mniej wiecej jak przed
     * zageszczeniem, a druzyna (premia do +200%) i tak zarabia tam najwiecej.
     */
    public static final int PACK_EXP_PERCENT = 85;

    /** Procent doswiadczenia za tego potwora (stado: {@link #PACK_EXP_PERCENT}). */
    public static int expPercent(Monster m) {
        return m != null && m.spawn() != null && m.spawn().pack() ? PACK_EXP_PERCENT : 100;
    }

    private ExperienceSharer experienceSharer;

    public void setExperienceSharer(ExperienceSharer sharer) { this.experienceSharer = sharer; }

    /** Lup z szansa bazowa najwyzej 1% jest rzadki (ogloszenie w dzienniku, na serwerze dla wszystkich). */
    public static final int RARE_PERMILLE = 10;

    public interface RareDropListener { void rareDrop(MonsterType from, ItemStack item, long now); }

    private RareDropListener rareDropListener;
    /** Rzadkie przedmioty z ostatniego {@link #rollLoot}. */
    private final List<ItemStack> rareRolled = new ArrayList<>();

    public void setRareDropListener(RareDropListener listener) { this.rareDropListener = listener; }

    /** Szansa bazowa wpisu lupu (elita ma tabele z podwojona szansa). */
    static boolean isRare(MonsterType t, int chancePermille) {
        int base = t.isElite() ? chancePermille / 2 : chancePermille;
        return base <= RARE_PERMILLE;
    }

    /** Klucz potwora dnia ({@link pl.fawizja.pixel.daily.BoostedCreature}); null = brak premii. */
    private String boostedKey;

    public void setBoostedKey(String key) { this.boostedKey = key; }
    public String boostedKey() { return boostedKey; }

    /** Potwor dnia (takze jego odmiana elitarna, bo ma ten sam klucz). */
    public boolean isBoosted(MonsterType t) { return boostedKey != null && t != null && boostedKey.equals(t.key()); }

    /** Doswiadczenie za potwora dla poziomu postaci, z premia zwoju nauki i potwora dnia. */
    public long killExperience(MonsterType t, int level) {
        long exp = pc.enchantments().experience(Balance.experienceReward(t.experience(), t.level(), level));
        if (isBoosted(t)) exp = pl.fawizja.pixel.daily.BoostedCreature.boostExp(exp);
        return bonusExpPercent > 0 ? exp * (100 + bonusExpPercent) / 100 : exp;
    }

    /** Premia doswiadczenia z wydarzenia serwera (procent, 0 = brak). */
    private int bonusExpPercent;
    public void setBonusExpPercent(int percent) { bonusExpPercent = Math.max(0, percent); }
    public int bonusExpPercent() { return bonusExpPercent; }

    /** Przyznaje doswiadczenie z wpisem w dzienniku i zdarzeniem awansu. */
    public void awardExperience(long exp, String what, long now) {
        int levels = pc.addExperience(exp);
        log.add(now, EventLog.Kind.INFO, what + " (+" + exp + " exp)");
        if (exp > 0) push(new CombatEvent(now, pc.position(), (int) Math.min(Integer.MAX_VALUE, exp), CombatEvent.Kind.EXP));
        if (levels > 0) {
            log.add(now, EventLog.Kind.SYSTEM, "Awans na poziom " + pc.level() + "!");
            push(new CombatEvent(now, pc.position(), pc.level(), CombatEvent.Kind.LEVEL_UP));
        }
    }
    public boolean autoLoot() { return autoLoot; }
    public void setAutoLoot(boolean v) { autoLoot = v; }

    public void setTarget(Monster m) {
        target = m != null && !m.isDead() && m.position().z() == pc.position().z() ? m : null;
    }

    /** Wybiera nastepny najblizszy widoczny cel (przycisk CEL). */
    /** Czy zywy potwor stoi najwyzej radius pol od postaci, na tym samym pietrze i w zasiegu wzroku (automatyczny cel). */
    public boolean threatNear(int radius) {
        for (Monster m : monsters.near(pc.position(), radius)) {
            if (!m.isDead() && m.position().z() == pc.position().z() && pc.position().distanceTo(m.position()) <= radius
                    && world.hasLineOfSight(pc.position(), m.position())) return true;
        }
        return false;
    }

    public Monster cycleTarget() {
        List<Monster> near = monsters.near(pc.position(), 8);
        near.removeIf(m -> m.isDead() || !world.hasLineOfSight(pc.position(), m.position()));
        near.sort((a, b) -> {
            int c = Integer.compare(a.position().distanceTo(pc.position()), b.position().distanceTo(pc.position()));
            return c != 0 ? c : Integer.compare(a.id(), b.id());
        });
        if (near.isEmpty()) {
            target = null;
            return null;
        }
        int idx = target == null ? -1 : near.indexOf(target);
        target = near.get((idx + 1) % near.size());
        return target;
    }

    /** Krok walki gracza: atak celu, gdy jest w zasiegu, widoczny i minal odstep ataku. */
    public void update(long now) {
        if (pc.isDead()) return;
        if (target != null && (target.isDead() || target.position().z() != pc.position().z()
                || target.position().distanceTo(pc.position()) > 10)) {
            target = null;
        }
        if (target == null || now < nextPlayerAttackMs) return;
        if (world.isProtectionZone(pc.position())) return;
        AttackProfile ap = CombatFormulas.attackProfile(pc, items);
        // cel wybiera tylko gracz (Darek, 23.10): poza zasiegiem po prostu nie atakujemy, bez przelaczania na innego
        if (!inReach(target, ap.range())) return;
        nextPlayerAttackMs = now + ap.intervalMs();
        int raw = critical(CombatFormulas.roll(ap.maxDamage(), rng));
        if (pc.isDefensive()) raw = (int) Math.round(raw * CombatFormulas.DEFENSIVE_OUTGOING);
        int dmg = CombatFormulas.mitigateOnMonster(target.type(), raw, ap.type(), rng);
        if (ap.range() > 1) push(CombatEvent.projectile(now, pc.position(), target.position(), ap.type().name()));
        if (ap.range() > 1) useAmmunition(now);
        int dealt = target.damage(dmg);
        leech(dealt);
        monsters.provoke(target);
        if (!isTrivial(target.type())) pc.skills().addTries(ap.skill(), 1);
        // wrecz: "skad" = gracz (animacja wypadu na kliencie)
        push(new CombatEvent(now, target.position(), dealt, dealt > 0 ? CombatEvent.Kind.HIT_MONSTER : CombatEvent.Kind.BLOCKED,
                ap.range() > 1 ? null : pc.position(), null));
        if (target.isDead()) onMonsterDeath(target, now);
    }

    /**
     * Potwor zbyt slaby, by czegokolwiek uczyc (ten sam prog co pelne doswiadczenie):
     * poziom 100 nie wytrenuje umiejetnosci na wilkach.
     */
    public boolean isTrivial(MonsterType t) {
        return pc.level() > 4 * t.level() + 20;
    }

    /** Obrazenia z czaru lub runy: odpornosci, zdarzenie, prowokacja, smierc. Zwraca zadane HP. */
    public int damageMonster(Monster m, int raw, DamageType type, long now) {
        if (m.isDead()) return 0;
        int dmg = CombatFormulas.mitigateOnMonster(m.type(), critical(raw), type, rng);
        int dealt = m.damage(dmg);
        leech(dealt);
        push(new CombatEvent(now, m.position(), dealt, dealt > 0 ? CombatEvent.Kind.HIT_MONSTER : CombatEvent.Kind.BLOCKED));
        monsters.provoke(m);
        if (m.isDead()) onMonsterDeath(m, now);
        return dealt;
    }

    /**
     * Strzal zuzywa sztuke amunicji z gniazda; pusty stos uzupelnia sie tym samym rodzajem z plecaka.
     */
    private void useAmmunition(long now) {
        if (CombatFormulas.ammunition(pc, items) == null) return;
        ItemStack a = pc.equipment().get(pl.fawizja.pixel.item.EquipSlot.AMMO);
        if (a.count() > 1) {
            pc.equipment().setDirect(pl.fawizja.pixel.item.EquipSlot.AMMO, a.withCount(a.count() - 1));
            return;
        }
        pc.equipment().setDirect(pl.fawizja.pixel.item.EquipSlot.AMMO, null);
        int spare = Math.min(pc.backpack().countPlain(a.itemId()), items.get(a.itemId()).maxStack());
        if (spare > 0 && pc.backpack().remove(a.itemId(), spare).ok()) {
            pc.equipment().setDirect(pl.fawizja.pixel.item.EquipSlot.AMMO, new ItemStack(a.itemId(), spare));
            log.add(now, EventLog.Kind.INFO, "Amunicja uzupełniona z plecaka (" + spare + ").");
        } else {
            log.add(now, EventLog.Kind.SYSTEM, "Skończyła się amunicja: " + items.get(a.itemId()).name() + ".");
        }
    }

    /** Zaklecie ciosu krytycznego: 10% szans na +50% obrazen. */
    private int critical(int raw) {
        pl.fawizja.pixel.item.Enchantments e = pc.enchantments();
        raw = e.outgoing(raw); // mikstura sily
        if (raw <= 0 || !e.active(pl.fawizja.pixel.item.Enchantments.Kind.CRIT)) return raw;
        if (rng.nextInt(100) >= pl.fawizja.pixel.item.Enchantments.CRIT_CHANCE) return raw;
        return raw + raw * pl.fawizja.pixel.item.Enchantments.CRIT_BONUS_PERCENT / 100;
    }

    /** Zaklecia kradziezy zycia i many po trafieniu potwora. */
    private void leech(int dealt) {
        pl.fawizja.pixel.item.Enchantments e = pc.enchantments();
        pc.heal(e.leech(pl.fawizja.pixel.item.Enchantments.Kind.LIFE, dealt));
        if (!pc.isDead()) pc.restoreMana(e.leech(pl.fawizja.pixel.item.Enchantments.Kind.MANA, dealt));
    }

    /** Zdarzenie do wyswietlenia (leczenie z czaru, mikstury). */
    public void addEvent(CombatEvent e) { push(e); }

    /** Trafienia postaci do podsumowania smierci i ostatnie podsumowanie (null = jeszcze nie zginela). */
    private final DeathRecap recap = new DeathRecap();
    private DeathRecap.Report lastDeath;

    public void recordDamage(String source, int amount, long now) { recap.record(source, amount, now); }

    public DeathRecap.Report lastDeath() { return lastDeath; }
    /** Miejsce ostatniej smierci w tej sesji (znacznik na mapie); null, gdy postac nie ginela. */
    private Position lastDeathPlace;
    public Position lastDeathPlace() { return lastDeathPlace; }

    private boolean inReach(Monster m, int range) {
        return m.position().z() == pc.position().z() && pc.position().distanceTo(m.position()) <= range
                && world.hasLineOfSight(pc.position(), m.position());
    }

    private final LootLog lootLog = new LootLog();

    /** Ostatnie lupy (okno /lupy). */
    public LootLog lootLog() { return lootLog; }

    private void onMonsterDeath(Monster m, long now) {
        MonsterType t = m.type();
        kills++;
        boolean lootElsewhere = lootOwner != null && lootOwner.test(m);   // przed sluchaczem: on konczy wyprawe
        if (killListener != null) killListener.accept(t);
        if (deathListener != null) deathListener.accept(m);
        int expPercent = expPercent(m);
        if (experienceSharer == null || !experienceSharer.share(t, expPercent, now)) {
            long exp = killExperience(t, pc.level()) * expPercent / 100;
            boolean rested = pc.useRested();
            if (rested) {
                exp = exp * (100 + PlayerCharacter.RESTED_EXP_PERCENT) / 100;
                pc.stats().recordRestedKill();
            }
            awardExperience(exp, "Pokonano: " + t.name() + (isBoosted(t) ? ", potwór dnia" : "") + (rested ? ", wypoczęty" : ""), now);
        }
        Position corpse = m.position();
        List<ItemStack> drop;
        if (lootElsewhere) {
            rareRolled.clear();
            drop = new ArrayList<>();
        } else {
            drop = rollLoot(t);
        }
        List<String> dropped = new ArrayList<>();
        for (ItemStack s : drop) dropped.add(describe(s));
        lootLog.record(now, t.name(), dropped, !rareRolled.isEmpty());
        for (ItemStack r : rareRolled) {
            log.add(now, EventLog.Kind.LOOT, "Rzadki łup: " + describe(r) + "!");
            pc.stats().recordRareDrop();
            if (rareDropListener != null) rareDropListener.rareDrop(t, r, now);
        }
        List<String> looted = new ArrayList<>();
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack s : drop) {
            boolean taken = false;
            if (autoLoot && !pc.autoLootFilter().isIgnored(s.itemId()) && pc.position().distanceTo(corpse) <= 8) {
                taken = pc.backpack().add(s) == InventoryResult.OK;
                if (taken) looted.add(describe(s));
            }
            if (!taken) left.add(s);
        }
        // cialo zostaje zawsze (wyglad), a w nim lup, ktorego nie zabral autoloot
        ground.addCorpse(corpse, t.key(), left, now);
        if (!looted.isEmpty()) log.add(now, EventLog.Kind.LOOT, "Łup: " + String.join(", ", looted));
        if (!left.isEmpty()) {
            log.add(now, EventLog.Kind.LOOT, "Część łupu została w ciele - przeszukaj je (UŻYJ albo PODNIEŚ obok).");
        }
        monsters.kill(m, now);
        if (target == m) target = null;
    }

    /** Losuje lup z tabeli potwora. */
    public List<ItemStack> rollLoot(MonsterType t) {
        List<ItemStack> out = new ArrayList<>();
        rareRolled.clear();
        for (LootEntry e : t.loot()) {
            int chance = e.chancePermille();
            // przedmioty tylko z bossow zawsze z gola szansa z tabeli (rzadkie), bez premii potwora dnia i zaklec
            if (!pl.fawizja.pixel.monster.LootRules.isBossOnly(items.get(e.itemId()))) {
                chance = isBoosted(t) ? pl.fawizja.pixel.daily.BoostedCreature.boostChance(chance) : chance;
                chance = pc.enchantments().lootChance(chance);
            }
            if (rng.nextInt(1000) < chance) {
                int count = CombatFormulas.rollBetween(e.min(), e.max(), rng);
                if (isRare(t, e.chancePermille())) rareRolled.add(new ItemStack(e.itemId(), count));
                int max = items.maxStack(e.itemId());
                while (count > 0) {
                    int c = Math.min(count, max);
                    out.add(new ItemStack(e.itemId(), c));
                    count -= c;
                }
            }
        }
        return out;
    }

    @Override
    public void monsterAttacks(Monster m, long now) {
        if (pc.isDead() || world.isProtectionZone(pc.position())) return;
        MonsterType t = m.type();
        int raw = CombatFormulas.rollBetween(t.attackMin(), t.attackMax(), rng);
        int dmg = pc.enchantments().incoming(CombatFormulas.mitigateOnPlayer(pc, raw, t.damageType(), pc.isDefensive(), rng));
        if (t.range() > 1) push(CombatEvent.projectile(now, m.position(), pc.position(), t.damageType().name()));
        int dealt = pc.damage(dmg);
        recap.record(t.name(), dealt, now);
        if (t.damageType() == DamageType.PHYSICAL && !isTrivial(t)) pc.skills().addTries(Skill.SHIELDING, 1);
        push(new CombatEvent(now, pc.position(), dealt, dealt > 0 ? CombatEvent.Kind.HIT_PLAYER : CombatEvent.Kind.BLOCKED,
                t.range() > 1 ? null : m.position(), null));
        if (pc.isDead()) onPlayerDeath(m, now);
    }

    @Override
    public void monsterHeals(Monster healer, Monster healed, int amount, long now) {
        if (amount > 0) push(new CombatEvent(now, healed.position(), amount, CombatEvent.Kind.HEAL));
    }

    /** Obrazenia z otoczenia (pulapka): liczba nad postacia i wpis w podsumowaniu smierci; nie zabijaja. */
    public int environmentHit(String source, int amount, long now) {
        if (pc.isDead() || amount <= 0) return 0;
        int dealt = pc.damage(Math.min(amount, pc.health() - 1));
        recap.record(source, dealt, now);
        push(new CombatEvent(now, pc.position(), dealt, CombatEvent.Kind.HIT_PLAYER, null, null));
        return dealt;
    }

    private void onPlayerDeath(Monster killer, long now) {
        long before = pc.experience();
        lastDeathPlace = pc.position();
        boolean blessed = pc.applyDeathPenalty(minLevel);
        Position temple = world.templeOf(pc.homeTown());
        pc.revive(temple);
        target = null;
        push(new CombatEvent(now, temple, 0, CombatEvent.Kind.DEATH));
        lastDeath = recap.build(killer.type().name(), before - pc.experience(), blessed, now);
        log.add(now, EventLog.Kind.WARNING, "Zginąłeś (" + killer.type().name() + "). Strata: "
                + (before - pc.experience()) + " exp" + (blessed ? " (połowa dzięki błogosławieństwu)" : "")
                + ". Odrodzenie w świątyni.");
    }

    /**
     * Smierc z reki innego gracza (PvP): ta sama kara co od potwora; przy czerwonej czaszce
     * caly plecak zostaje na miejscu smierci. Zwraca liczbe porzuconych stosow.
     */
    public int diedFromPlayer(String killer, boolean dropBackpack, long now) {
        Position at = pc.position();
        lastDeathPlace = at;
        int dropped = 0;
        if (dropBackpack) {
            for (int i = 0; i < pc.backpack().size(); i++) {
                ItemStack s = pc.backpack().get(i);
                if (s == null) continue;
                ItemStack taken = pc.backpack().takeFromSlot(i, s.count());
                if (taken != null) {
                    ground.place(at, taken);
                    dropped++;
                }
            }
        }
        long before = pc.experience();
        boolean blessed = pc.applyDeathPenalty(minLevel);
        Position temple = world.templeOf(pc.homeTown());
        pc.revive(temple);
        target = null;
        push(new CombatEvent(now, temple, 0, CombatEvent.Kind.DEATH));
        lastDeath = recap.build(killer + " (gracz)", before - pc.experience(), blessed, now);
        log.add(now, EventLog.Kind.WARNING, "Zginąłeś z ręki gracza " + killer + ". Strata: "
                + (before - pc.experience()) + " exp" + (blessed ? " (połowa dzięki błogosławieństwu)" : "") + (dropped > 0 ? ", plecak został na ziemi" : "")
                + ". Odrodzenie w świątyni.");
        return dropped;
    }

    private void push(CombatEvent e) {
        eventTotal++;
        events.addLast(e);
        while (events.size() > 64) events.removeFirst();
    }

    /** Liczba wszystkich zdarzen od poczatku (serwer wysyla tylko nowe). */
    public long eventTotal() { return eventTotal; }

    /** Ostatnie n zdarzen (najstarsze pierwsze). */
    public List<CombatEvent> latestEvents(int n) {
        List<CombatEvent> out = new ArrayList<>(events);
        return out.subList(Math.max(0, out.size() - n), out.size());
    }

    /** Klient online: zdarzenie walki odebrane z serwera (do wyswietlenia). */
    public void addRemoteEvent(CombatEvent e) { push(e); }

    public List<CombatEvent> recentEvents(long now, long maxAgeMs) {
        List<CombatEvent> out = new ArrayList<>();
        for (CombatEvent e : events) if (now - e.timeMs() <= maxAgeMs) out.add(e);
        return out;
    }

    private String describe(ItemStack s) {
        String n = items.get(s.itemId()).name();
        return s.count() > 1 ? s.count() + "x " + n : n;
    }
}
