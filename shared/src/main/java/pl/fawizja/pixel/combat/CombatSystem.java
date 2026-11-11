package pl.fawizja.pixel.combat;

import pl.fawizja.pixel.i18n.Messages;
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

    /** Przedmioty i ciala na ziemi (runa ozywienia zwlok). */
    public GroundItems ground() { return ground; }
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
     * Potwory ze stad (spawn "stado") daja pelne doswiadczenie - w Tibii nie ma zadnej obnizki za gestosc.
     */
    public interface ExperienceSharer { boolean share(MonsterType killed, long now); }

    private ExperienceSharer experienceSharer;

    public void setExperienceSharer(ExperienceSharer sharer) { this.experienceSharer = sharer; }

    /** Lup z szansa bazowa najwyzej 1% jest rzadki (ogloszenie w dzienniku, na serwerze dla wszystkich). */
    public static final int RARE_PERMILLE = 10;

    public interface RareDropListener { void rareDrop(MonsterType from, ItemStack item, long now); }

    private RareDropListener rareDropListener;
    /** Rzadkie przedmioty z ostatniego {@link #rollLoot}. */
    private final List<ItemStack> rareRolled = new ArrayList<>();

    public void setRareDropListener(RareDropListener listener) { this.rareDropListener = listener; }

    /** Szansa bazowa wpisu lupu (potwory pod wpływem i diaboliczne maja te sama tabele co zwykle, jak w Canary). */
    static boolean isRare(MonsterType t, int chancePermille) {
        return chancePermille <= RARE_PERMILLE;
    }

    /** Klucz potwora dnia ({@link pl.fawizja.pixel.daily.BoostedCreature}); null = brak premii. */
    private String boostedKey;

    public void setBoostedKey(String key) { this.boostedKey = key; }
    public String boostedKey() { return boostedKey; }

    /** Potwor dnia (takze jego odmiana elitarna, bo ma ten sam klucz). */
    public boolean isBoosted(MonsterType t) { return boostedKey != null && t != null && boostedKey.equals(t.key()); }

    /**
     * Doswiadczenie za potwora dla poziomu postaci: mnoznik etapu serwera (Balance.expRate), premia zwoju nauki i potwora dnia,
     * wydarzenie serwera i pupil chodzacy za postacia (+{@link PlayerCharacter#PET_EXP_PERCENT}%, po etapach).
     */
    public long killExperience(MonsterType t, int level) {
        long exp = pc.enchantments().experience(Balance.applyRate(Balance.experienceReward(t.experience(), t.level(), level), Balance.expRate(level)));
        if (isBoosted(t)) exp = pl.fawizja.pixel.daily.BoostedCreature.boostExp(exp);
        if (bonusExpPercent > 0) exp = exp * (100 + bonusExpPercent) / 100;
        int pet = pc.petExpPercent();
        return pet > 0 ? exp * (100 + pet) / 100 : exp;
    }

    /** Premia doswiadczenia z wydarzenia serwera (procent, 0 = brak). */
    private int bonusExpPercent;
    public void setBonusExpPercent(int percent) { bonusExpPercent = Math.max(0, percent); }
    public int bonusExpPercent() { return bonusExpPercent; }

    /** Dopisek do wpisu o pokonaniu potwora przez zabojce (jego cialo, jego lup): {@link #staminaNote(PlayerCharacter, boolean)}. */
    public static String staminaNote(PlayerCharacter pc) { return staminaNote(pc, true); }

    /**
     * Dopisek do wpisu o pokonaniu potwora: stamina premiowa, niska albo pusta (jak kolor paska w Tibii). Przy pustej
     * staminie zabojca nie dostaje ani doswiadczenia, ani lupu (cialo puste); czlonek druzyny tylko doswiadczenia,
     * bo lup lezy w ciele zabojcy.
     */
    public static String staminaNote(PlayerCharacter pc, boolean killer) {
        switch (pc.stamina().zone()) {
            case EMPTY: return killer ? ", brak staminy - bez expa i łupu" : Messages.get("combat_system.brak_staminy_bez_expa");
            case LOW: return Messages.get("combat_system.niska_stamina_50_exp");
            case BONUS: return pc.isPremium() ? ", premia staminy (150% exp)" : "";
            default: return "";
        }
    }

    /** Przyznaje doswiadczenie z wpisem w dzienniku i zdarzeniem awansu. */
    public void awardExperience(long exp, String what, long now) {
        int levels = pc.addExperience(exp);
        log.add(now, EventLog.Kind.INFO, what + " (+" + exp + " exp)");
        if (exp > 0) push(new CombatEvent(now, pc.position(), (int) Math.min(Integer.MAX_VALUE, exp), CombatEvent.Kind.EXP));
        if (levels > 0) {
            log.add(now, EventLog.Kind.SYSTEM, Messages.get("combat_system.awans_na_poziom") + pc.level() + "!");
            push(new CombatEvent(now, pc.position(), pc.level(), CombatEvent.Kind.LEVEL_UP));
        }
    }
    public boolean autoLoot() { return autoLoot; }
    public void setAutoLoot(boolean v) { autoLoot = v; }

    public void setTarget(Monster m) {
        target = m != null && !m.isDead() && !m.isSummon() && m.position().z() == pc.position().z() ? m : null;
    }

    /** Wybiera nastepny najblizszy widoczny cel (przycisk CEL). */
    /** Czy zywy potwor stoi najwyzej radius pol od postaci, na tym samym pietrze i w zasiegu wzroku (automatyczny cel). */
    public boolean threatNear(int radius) {
        for (Monster m : monsters.near(pc.position(), radius)) {
            if (!m.isDead() && !m.isSummon() && m.position().z() == pc.position().z() && pc.position().distanceTo(m.position()) <= radius
                    && world.hasLineOfSight(pc.position(), m.position())) return true;
        }
        return false;
    }

    public Monster cycleTarget() {
        List<Monster> near = monsters.near(pc.position(), 8);
        near.removeIf(m -> m.isDead() || m.isSummon() || !world.hasLineOfSight(pc.position(), m.position()));
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

    private long lastMonsterAttackMs = Long.MIN_VALUE / 2;

    /**
     * Ostatni atak gracza na potwora (bron, czar, runa); Long.MIN_VALUE / 2 = nigdy. Wspolne doswiadczenie
     * w druzynie wymaga aktywnosci w ostatnich 2 min ({@link pl.fawizja.pixel.party.PartyRules#ACTIVITY_MS}).
     */
    public long lastMonsterAttackMs() { return lastMonsterAttackMs; }

    /** Zapis aktywnosci w druzynie (atak na potwora z innego miejsca niz bron i czar; testy). */
    public void noteMonsterAttack(long now) { lastMonsterAttackMs = now; }

    /** Postac zaatakowala bronia w ostatnim odstepie ataku (TFS: lastAttack) - wtedy tryb walki oslabia obrone. */
    public boolean attackedRecently(long now) { return now < nextPlayerAttackMs; }

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
        if (pc.breakInvisibility()) log.add(now, EventLog.Kind.INFO, Messages.get("combat_system.atakujesz_niewidzialnosc_znika"));
        // jak w Tibii: jeden rzut na krytyk na cios (baza 5% / +10%, przedmioty, zaklecie kowala)
        boolean crit = rollCritical(target.type());
        int raw = critical(CombatFormulas.roll(ap.maxDamage(), rng), crit, target.type());
        int dmg = mitigateWeaponHit(target.type(), raw, ap.type());
        if (ap.range() > 1) push(CombatEvent.projectile(now, pc.position(), target.position(), ap.type().name()));
        if (ap.range() > 1) useAmmunition(now);
        int dealt = target.damage(dmg);
        lastMonsterAttackMs = now;
        Leech.apply(pc, dealt, 1, target.type());
        monsters.provoke(target);
        // jak w Tibii kazdy atak (takze chybiony albo zablokowany) to proba; Balance.HUNT_TRY_FACTOR wyrownuje krotkie walki
        if (!isTrivial(target.type())) pc.skills().train(ap.skill(), Balance.HUNT_TRY_FACTOR);
        // wrecz: "skad" = gracz (animacja wypadu na kliencie)
        push(new CombatEvent(now, target.position(), dealt, dealt > 0 ? CombatEvent.Kind.HIT_MONSTER : CombatEvent.Kind.BLOCKED,
                ap.range() > 1 ? null : pc.position(), hitEffect(crit, dealt)));
        Monster hit = target;
        charmsAfterHit(hit, dealt, now);
        if (hit.isDead()) onMonsterDeath(hit, now);
    }

    /**
     * Potwor zbyt slaby, by czegokolwiek uczyc (ten sam prog co pelne doswiadczenie):
     * poziom 100 nie wytrenuje umiejetnosci na wilkach.
     */
    public boolean isTrivial(MonsterType t) {
        return pc.level() > 4 * t.level() + 20;
    }

    /** Obrazenia z czaru lub runy w jeden cel, z wlasnym rzutem na krytyk. Zwraca zadane HP. */
    public int damageMonster(Monster m, int raw, DamageType type, long now) {
        if (m.isDead()) return 0;
        return damageMonster(m, raw, type, now, rollCritical(), 1);
    }

    /** Rzut na cios krytyczny postaci ({@link CriticalHit}): raz na cios, czar albo rune. */
    public boolean rollCritical() { return CriticalHit.roll(pc, rng); }

    /** Rzut na krytyk przeciw potworowi: z charmem Niski cios drugi rzut na brakujaca szanse. */
    private boolean rollCritical(MonsterType t) {
        return rollCritical() || pl.fawizja.pixel.charm.CharmEffects.lowBlowReroll(pc, t, CriticalHit.chance(pc), rng);
    }

    /**
     * Charmy ofensywne po trafieniu potwora bronia, czarem albo runa ({@link pl.fawizja.pixel.charm.CharmEffects#onHit}):
     * obrazenia charmu (po odpornosciach, bez pancerza), paraliz, zakaz ucieczki, Rzez po zabiciu. Obrazenia z samego
     * charmu nie wyzwalaja kolejnych charmow ani kradziezy (Canary: damage.extension). Smierc trafionego potwora
     * obsluguje wolajacy; tu tylko smierc innych potworow (Rzez).
     */
    private void charmsAfterHit(Monster m, int dealt, long now) {
        if (dealt <= 0 || pc.charms().charmsOn(m.type().key()).isEmpty()) return;
        for (pl.fawizja.pixel.charm.CharmEffects.Proc p : pl.fawizja.pixel.charm.CharmEffects.onHit(pc, m, monsters::monsterAt, rng, now)) {
            Monster tm = p.target();
            int d = p.amount() > 0 ? tm.damage(pl.fawizja.pixel.charm.CharmEffects.resisted(tm.type(), p.amount(), p.type())) : 0;
            push(new CombatEvent(now, tm.position(), d, d > 0 ? CombatEvent.Kind.HIT_MONSTER : CombatEvent.Kind.BLOCKED, null,
                    pl.fawizja.pixel.charm.CharmEffects.eventEffect(p.charm())));
            log.add(now, EventLog.Kind.INFO, "Charm " + p.charm().label() + ": " + tm.type().name() + (d > 0 ? " -" + d + " HP" : "") + ".");
            if (d > 0 && tm != m) monsters.provoke(tm);
            if (tm != m && tm.isDead()) onMonsterDeath(tm, now);
        }
    }

    /**
     * Obrazenia z czaru lub runy: mikstura sily, krytyk (wylosowany raz dla calego rzutu), odpornosci, kradziez
     * zycia/many wg liczby celow rzutu (targets), zdarzenie, prowokacja, smierc. Zwraca zadane HP.
     */
    public int damageMonster(Monster m, int raw, DamageType type, long now, boolean crit, int targets) {
        if (m.isDead()) return 0;
        lastMonsterAttackMs = now;
        crit = crit || pl.fawizja.pixel.charm.CharmEffects.lowBlowReroll(pc, m.type(), CriticalHit.chance(pc), rng);
        int dmg = CombatFormulas.mitigateOnMonster(m.type(), critical(raw, crit, m.type()), type, rng);
        int dealt = m.damage(dmg);
        Leech.apply(pc, dealt, targets, m.type());
        push(new CombatEvent(now, m.position(), dealt, dealt > 0 ? CombatEvent.Kind.HIT_MONSTER : CombatEvent.Kind.BLOCKED,
                null, hitEffect(crit, dealt)));
        monsters.provoke(m);
        charmsAfterHit(m, dealt, now);
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
            log.add(now, EventLog.Kind.INFO, Messages.get("combat_system.amunicja_uzupelniona_z_plecaka") + spare + ").");
        } else {
            log.add(now, EventLog.Kind.SYSTEM, Messages.get("combat_system.skonczyla_sie_amunicja") + items.get(a.itemId()).name() + ".");
        }
    }

    /**
     * Mikstura sily, potem cios krytyczny (raw * (1 + obrazenia krytyczne + charm Dziki cios)), gdy wypadl,
     * potem Natarcie z Kuzni.
     */
    private int critical(int raw, boolean crit, MonsterType t) {
        int v = CriticalHit.apply(pc, pc.enchantments().outgoing(pl.fawizja.pixel.wheel.WheelEffects.outgoing(pc, raw)), crit,
                pl.fawizja.pixel.charm.CharmEffects.critDamageBonus(pc, t)); // Kolo Przeznaczenia: premie i plaskie obrazenia
        double spell = pc.outgoingFactor(); // Obronca, Szybka stopa: slabsze ciosy (Canary BUFF_DAMAGEDEALT)
        if (spell != 1.0) v = (int) Math.round(v * spell);
        lastOnslaught = v > 0 && rollOnslaught();
        if (lastOnslaught) v += (int) Math.round(v * pl.fawizja.pixel.item.Forge.ONSLAUGHT_BONUS);
        return v;
    }

    // ------------------------------------------------------------------ Kuznia i nasycenia (male haki, ForgeEffects)

    /** Efekt zdarzenia trafienia: Natarcie z Kuzni (+60%). */
    public static final String ONSLAUGHT_EFFECT = "FATAL";
    /** Efekt zdarzenia BLOCKED: Unik z Kuzni (Ruse) - cios potwora ominiety. */
    public static final String DODGE_EFFECT = "DODGE";
    /** Walka (jak tibijskie skrzyzowane miecze): tyle po ostatnim ataku lub trafieniu; wtedy plynie czas nasycen. */
    public static final long BATTLE_MS = 60_000;

    private boolean lastOnslaught;
    private long lastHitTakenMs = Long.MIN_VALUE / 2;

    private String hitEffect(boolean crit, int dealt) {
        if (dealt <= 0) return null;
        if (lastOnslaught) return ONSLAUGHT_EFFECT;
        return crit ? CriticalHit.EVENT_EFFECT : null;
    }

    /** Szansa Natarcia (bron z tierem, wzmocnienie butow); 0 bez tieru - bez losowania. */
    private boolean rollOnslaught() {
        double c = pl.fawizja.pixel.item.ForgeEffects.onslaughtChance(pc);
        return c > 0 && rng.nextInt(10_000) < (int) Math.round(c * 100);
    }

    /** Postac walczy: atakowala potwora albo byla trafiona w ostatniej minucie. */
    public boolean inBattle(long now) {
        return now - lastMonsterAttackMs < BATTLE_MS || now - lastHitTakenMs < BATTLE_MS;
    }

    /** Agresywna akcja (atak bronia, czar, runa) w ostatnich ms (Transcendencja: 2 s). */
    public boolean aggressiveWithin(long now, long ms) { return now - lastMonsterAttackMs <= ms; }

    /**
     * Pancerz i odpornosci przy ciosie bronia; nasycenie zywiolowe broni (Zar, Jad, Mroz, Elektryzacja, Zniwo)
     * zamienia czesc obrazen fizycznych na zywiol (osobno przez odpornosc potwora).
     */
    int mitigateWeaponHit(MonsterType t, int raw, DamageType type) {
        pl.fawizja.pixel.item.ItemStack w = pc.equipment().get(pl.fawizja.pixel.item.EquipSlot.WEAPON);
        pl.fawizja.pixel.item.Imbuements.Slot el = w == null || type != DamageType.PHYSICAL ? null : w.imbuements().elementDamage();
        if (el == null || raw <= 0) return CombatFormulas.mitigateOnMonster(t, raw, type, rng);
        int elem = (int) Math.round(raw * el.kind().value(el.level()) / 100.0);
        return CombatFormulas.mitigateOnMonster(t, raw - elem, type, rng)
                + CombatFormulas.mitigateOnMonster(t, elem, el.kind().element(), rng);
    }

    /** Pyl Kuzni za potwora pod wpływem / diabolicznego (s..3s), z komunikatem; serwer wola go dla kazdego z druzyny. */
    public int awardForgeDust(MonsterType t, long now) {
        if (t.forgeStack() <= 0) return 0;
        int amount = pl.fawizja.pixel.item.Forge.rollDust(t.forgeStack(), rng);
        int added = pc.addDust(amount);
        if (added > 0) {
            log.add(now, EventLog.Kind.LOOT, Messages.get("combat_system.pyl_kuzni") + added + " (" + pc.forgeDust() + "/" + pc.dustLimit() + ").");
        } else {
            log.add(now, EventLog.Kind.INFO, Messages.get("combat_system.nie_otrzymano") + amount + Messages.get("combat_system.pylu_kuzni_masz_juz") + pc.dustLimit()
                    + Messages.get("combat_system.podnies_go_u_kowala"));
        }
        return added;
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
        // stamina 0:00 jak w Tibii: bez lupu (strefa sprzed zuzycia za to zabicie - ostatnia minuta jeszcze daje lup)
        boolean noStamina = pc.stamina().zone() == pl.fawizja.pixel.character.Stamina.Zone.EMPTY;
        boolean lootElsewhere = lootOwner != null && lootOwner.test(m);   // przed sluchaczem: on konczy wyprawe
        if (killListener != null) killListener.accept(t);
        if (deathListener != null) deathListener.accept(m);
        boolean shared = experienceSharer != null && experienceSharer.share(t, now); // druzyna: pyl rozdaje serwer
        if (!shared) awardForgeDust(t, now);
        if (!shared) {
            long exp = killExperience(t, pc.level());
            boolean rested = pc.useRested();
            if (rested) {
                exp = exp * (100 + PlayerCharacter.RESTED_EXP_PERCENT) / 100;
                pc.stats().recordRestedKill();
            }
            String note = staminaNote(pc); // strefa sprzed zuzycia minuty za to zabicie
            exp = pc.stamina().hunt(exp, pc.isPremium(), now);
            awardExperience(exp, Messages.get("server_world.pokonano") + t.name() + (t.isElite() ? " " + t.forgeLabel() : "") + (isBoosted(t) ? Messages.get("combat_system.potwor_dnia") : "") + (rested ? Messages.get("combat_system.wypoczety") : "")
                    + note, now);
        }
        Position corpse = m.position();
        List<ItemStack> drop;
        if (lootElsewhere || noStamina) {
            rareRolled.clear();
            drop = new ArrayList<>();
        } else {
            drop = rollLoot(t);
            if (t.isFiendish() && items.containsKey(pl.fawizja.pixel.item.Forge.SLIVER_KEY)) {
                // tylko diaboliczny zostawia odlamki: 3..7 w ciele (Canary forgeMinSlivers / forgeMaxSlivers)
                drop.add(new ItemStack(items.byKey(pl.fawizja.pixel.item.Forge.SLIVER_KEY).id(), CombatFormulas.rollBetween(
                        pl.fawizja.pixel.item.Forge.FIENDISH_MIN_SLIVERS, pl.fawizja.pixel.item.Forge.FIENDISH_MAX_SLIVERS, rng)));
            }
            // klejnoty Kola Przeznaczenia: pod wplywem, diaboliczni i silni bossowie (Canary generateGemAtelierLoot)
            for (java.util.Map.Entry<String, Integer> g : pl.fawizja.pixel.wheel.GemDrops.roll(t, rng).entrySet()) {
                if (!items.containsKey(g.getKey())) continue;
                ItemStack gem = new ItemStack(items.byKey(g.getKey()).id(), g.getValue());
                drop.add(gem);
                log.add(now, EventLog.Kind.LOOT, Messages.get("combat_system.klejnot_w_lupie") + describe(gem) + "!");
            }
        }
        List<String> dropped = new ArrayList<>();
        for (ItemStack s : drop) dropped.add(describe(s));
        lootLog.record(now, t.name(), dropped, !rareRolled.isEmpty());
        for (ItemStack r : rareRolled) {
            log.add(now, EventLog.Kind.LOOT, Messages.get("combat_system.rzadki_lup") + describe(r) + "!");
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
        if (!looted.isEmpty()) log.add(now, EventLog.Kind.LOOT, Messages.get("combat_system.lup") + String.join(", ", looted));
        if (!left.isEmpty()) {
            log.add(now, EventLog.Kind.LOOT, Messages.get("combat_system.czesc_lupu_zostala_w"));
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
                chance = pl.fawizja.pixel.charm.CharmEffects.lootChance(pc, t, e, items, chance); // Patroszenie, Skorowanie
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

    /**
     * Tarcza rosnie jak w Tibii tylko z tarcza (albo ksiega czarow) w rece; mnich walczy bez tarczy,
     * wiec u niego licza sie wszystkie odparte ciosy.
     */
    public static boolean trainsShielding(PlayerCharacter pc) {
        return pc.equipment().shieldDefense() > 0 || pc.vocation() == pl.fawizja.pixel.vocation.Vocation.MONK;
    }

    /**
     * Atak, ktory tarcza moze odeprzec (proba tarczy jak w Tibii): kazdy cios wrecz, takze zywiolowy, i fizyczny
     * pocisk; czary potworow na dystans tarczy nie ucza.
     */
    public static boolean blockable(MonsterType t) {
        return t.range() <= 1 || t.damageType() == DamageType.PHYSICAL;
    }

    @Override
    public void monsterAttacks(Monster m, long now) {
        if (pc.isDead() || world.isProtectionZone(pc.position())) return;
        MonsterType t = m.type();
        MonsterType.Strike st = t.rollStrike(rng);
        lastHitTakenMs = now;
        if (pl.fawizja.pixel.item.ForgeEffects.rollRuse(pc, rng)) {
            // Unik (Ruse, zbroja z tierem): caly cios ominiety, jak "You dodged an attack." w Tibii
            push(new CombatEvent(now, pc.position(), 0, CombatEvent.Kind.BLOCKED, t.range() > 1 ? null : m.position(), DODGE_EFFECT));
            return;
        }
        int dmg = pl.fawizja.pixel.item.ForgeEffects.avatarIncoming(pc,
                pc.enchantments().incoming(CombatFormulas.mitigateOnPlayer(pc, st.raw(), st.type(), attackedRecently(now), rng)));
        if (t.range() > 1) push(CombatEvent.projectile(now, m.position(), pc.position(), st.type().name()));
        pl.fawizja.pixel.charm.CharmEffects.Defense charm = pl.fawizja.pixel.charm.CharmEffects.onMonsterAttack(pc, m, st.type(), dmg, rng, now);
        for (pl.fawizja.pixel.charm.Charm c : charm.triggered()) log.add(now, EventLog.Kind.INFO, "Charm " + c.label() + " (" + t.name() + ").");
        if (charm.noDamage()) {
            // Unik albo Oczyszczenie: atak bez obrazen
            push(new CombatEvent(now, pc.position(), 0, CombatEvent.Kind.BLOCKED, t.range() > 1 ? null : m.position(),
                    pl.fawizja.pixel.charm.CharmEffects.eventEffect(charm.prevented())));
            return;
        }
        if (st.type() == DamageType.MANA_DRAIN && charm.invertManaDrain()) {
            // Odwrocenie pustki: wyssanie many daje mane
            int got = pc.restoreMana(dmg);
            push(new CombatEvent(now, pc.position(), got, CombatEvent.Kind.HEAL, null, MANA_EFFECT));
            return;
        }
        if (st.type() == DamageType.MANA_DRAIN) {
            // wyssanie many jak w Tibii: zabiera mane (najwyzej tyle, ile jest), zdrowia nie rusza
            int drained = Math.min(dmg, pc.mana());
            pc.spendMana(drained);
            push(new CombatEvent(now, pc.position(), drained, drained > 0 ? CombatEvent.Kind.HIT_PLAYER : CombatEvent.Kind.BLOCKED,
                    t.range() > 1 ? null : m.position(), drained > 0 ? MANA_EFFECT : null));
            return;
        }
        if (pc.incomingFactor() != 1.0) dmg = (int) Math.round(dmg * pc.incomingFactor()); // Krwawy szal: +15%
        int dealt = pc.damage(dmg);
        showShield(now);
        pl.fawizja.pixel.wheel.WheelEffects.onMonsterStrike(pc); // Kolo Przeznaczenia: Leczenie w boju
        recap.record(t.name(), dealt, now);
        if (dealt > 0) applyMonsterCondition(m, now);
        if (blockable(t) && !isTrivial(t) && trainsShielding(pc)) pc.skills().train(Skill.SHIELDING, Balance.BLOCK_TRY_FACTOR);
        push(new CombatEvent(now, pc.position(), dealt, dealt > 0 ? CombatEvent.Kind.HIT_PLAYER : CombatEvent.Kind.BLOCKED,
                t.range() > 1 ? null : m.position(), null));
        if (charm.parry() != null && dealt > 0 && !m.isDead()) {
            // Riposta: otrzymane obrazenia wracaja do atakujacego (neutralne, bez odpornosci)
            int back = m.damage(dealt);
            push(new CombatEvent(now, m.position(), back, CombatEvent.Kind.HIT_MONSTER, null,
                    pl.fawizja.pixel.charm.CharmEffects.eventEffect(charm.parry())));
            if (m.isDead()) onMonsterDeath(m, now);
        }
        if (pc.isDead()) onPlayerDeath(m, now);
    }

    /** Tarcza many zabrala mane zamiast zdrowia: niebieska liczba nad postacia. */
    private void showShield(long now) {
        int a = pc.lastShieldAbsorbed();
        if (a > 0) push(new CombatEvent(now, pc.position(), a, CombatEvent.Kind.HIT_PLAYER, null, MANA_EFFECT));
    }

    /**
     * Stan od ciosu potwora (data/monster_conditions.csv): pajaki zatruwaja, zywiolaki ognia podpalaja... Jak w Tibii
     * kazde trafienie z obrazeniami moze odnowic stan; odmiana elitarna zadaje wiecej.
     */
    private void applyMonsterCondition(Monster m, long now) {
        pl.fawizja.pixel.monster.MonsterConditions.Entry e = pl.fawizja.pixel.monster.MonsterConditions.defaults().of(m.type());
        if (e == null || pc.isDead() || rng.nextInt(100) >= e.chance()) return;
        boolean had = pc.conditions().has(e.type());
        int total = e.rollTotal(rng, m.type().isElite() ? 1.3 : 1.0);
        if (pc.conditions().addTotal(e.type(), total, e.intervalMs(), now, m.type().name()) && !had) {
            log.add(now, EventLog.Kind.WARNING, e.type().label() + " (" + m.type().name() + ")"
                    + (e.type().cure() != null ? Messages.get("combat_system.wylecz_czarem") + e.type().cure() + "." : "."));
        }
    }

    /**
     * Tik stanu w postac (zatrucie, podpalenie...): ochrona przed zywiolem, tarcza many, liczba nad postacia, wpis
     * w podsumowaniu smierci; stan moze zabic jak w Tibii. Zwraca odjete HP.
     */
    public int conditionHit(Conditions.Tick tick, long now) {
        if (pc.isDead() || tick.amount() <= 0) return 0;
        int dmg = CombatFormulas.conditionOnPlayer(pc, tick.amount(), tick.type().damage);
        int dealt = pc.damage(dmg);
        showShield(now);
        String source = (tick.source() == null ? "" : tick.source() + ": ") + tick.type().label().toLowerCase(java.util.Locale.ROOT);
        recap.record(source, dealt, now);
        push(new CombatEvent(now, pc.position(), dealt, dealt > 0 ? CombatEvent.Kind.HIT_PLAYER : CombatEvent.Kind.BLOCKED,
                null, CONDITION_EFFECT_PREFIX + tick.type().name()));
        if (pc.isDead()) onPlayerDeath(source, null, now);
        return dealt;
    }

    /** Efekt zdarzenia tiku stanu: "COND_" + nazwa stanu (klient: kolor liczby i efekt). */
    public static final String CONDITION_EFFECT_PREFIX = "COND_";

    /** Tik stanu albo pola w potwora (utori, runa ognia duszy, pola): odpornosci bez pancerza, smierc z lupem. */
    public int conditionHitMonster(Monster m, int raw, ConditionType type, long now) {
        if (m.isDead() || raw <= 0) return 0;
        lastMonsterAttackMs = now;
        int dealt = m.damage(CombatFormulas.conditionOnMonster(m.type(), raw, type.damage));
        push(new CombatEvent(now, m.position(), dealt, dealt > 0 ? CombatEvent.Kind.HIT_MONSTER : CombatEvent.Kind.BLOCKED,
                null, CONDITION_EFFECT_PREFIX + type.name()));
        if (!m.isSummon()) monsters.provoke(m);
        if (m.isDead()) onMonsterDeath(m, now);
        return dealt;
    }

    /**
     * Cios slugi gracza w potwora (albo odwet potwora na sludze): rzut ataku napastnika, pancerz i odpornosci celu.
     * Zabity przez sluge potwor daje doswiadczenie i lup wlascicielowi, jak w Tibii.
     */
    public int creatureHit(Monster attacker, Monster victim, long now) {
        if (attacker.isDead() || victim.isDead()) return 0;
        MonsterType.Strike st = attacker.type().rollStrike(rng);
        if (st.type() == DamageType.MANA_DRAIN) st = new MonsterType.Strike(DamageType.DEATH, st.raw());
        int dealt = victim.damage(CombatFormulas.mitigateOnMonster(victim.type(), st.raw(), st.type(), rng));
        if (attacker.type().range() > 1) push(CombatEvent.projectile(now, attacker.position(), victim.position(), st.type().name()));
        push(new CombatEvent(now, victim.position(), dealt, dealt > 0 ? CombatEvent.Kind.HIT_MONSTER : CombatEvent.Kind.BLOCKED,
                attacker.type().range() > 1 ? null : attacker.position(), null));
        if (victim.isDead()) {
            if (victim.isSummon()) {
                log.add(now, EventLog.Kind.WARNING, Messages.get("combat_system.twoj_sluga") + victim.type().name() + " ginie.");
                monsters.dismiss(victim);
            } else {
                lastMonsterAttackMs = now;
                onMonsterDeath(victim, now);
            }
        } else if (!victim.isSummon()) {
            monsters.provoke(victim);
        }
        return dealt;
    }

    /** Efekt zdarzenia HIT_PLAYER: liczba to utracona mana (wyssanie many), nie zdrowie. */
    public static final String MANA_EFFECT = "MANA";

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
        onPlayerDeath(killer.type().name(), killer.type(), now);
    }

    /** Smierc od potwora albo od stanu (killerType = null: zatrucie, podpalenie...). */
    private void onPlayerDeath(String killerName, MonsterType killerType, long now) {
        long before = pc.experience();
        lastDeathPlace = pc.position();
        int blessed = pc.applyDeathPenalty(minLevel, killerType == null ? 0 : pl.fawizja.pixel.charm.CharmEffects.blessReduction(pc, killerType), false, false);
        Position temple = world.templeOf(pc.homeTown());
        pc.revive(temple);
        target = null;
        push(new CombatEvent(now, temple, 0, CombatEvent.Kind.DEATH));
        lastDeath = recap.build(killerName, before - pc.experience(), blessed, now);
        log.add(now, EventLog.Kind.WARNING, Messages.get("combat_system.zginales") + killerName + Messages.get("combat_system.strata")
                + (before - pc.experience()) + " exp" + DeathRecap.blessingNote(blessed)
                + Messages.get("combat_system.odrodzenie_w_swiatyni"));
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
            // czerwona/czarna czaszka: jak w Tibii przepada wszystko, takze ekwipunek
            for (pl.fawizja.pixel.item.EquipSlot slot : pl.fawizja.pixel.item.EquipSlot.values()) {
                ItemStack worn = pc.equipment().get(slot);
                if (worn == null) continue;
                pc.equipment().setDirect(slot, null);
                ground.place(at, worn);
                dropped++;
            }
        }
        long before = pc.experience();
        int blessed = pc.applyDeathPenalty(minLevel, 0, true, dropBackpack);
        Position temple = world.templeOf(pc.homeTown());
        pc.revive(temple);
        target = null;
        push(new CombatEvent(now, temple, 0, CombatEvent.Kind.DEATH));
        lastDeath = recap.build(killer + Messages.get("combat_system.gracz"), before - pc.experience(), blessed, now);
        log.add(now, EventLog.Kind.WARNING, Messages.get("combat_system.zginales_z_reki_gracza") + killer + Messages.get("combat_system.strata_2")
                + (before - pc.experience()) + " exp" + DeathRecap.blessingNote(blessed) + (dropped > 0 ? Messages.get("combat_system.plecak_i_ekwipunek_zostaly") : "")
                + Messages.get("combat_system.odrodzenie_w_swiatyni"));
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
